package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.database.RecordingTask;
import com.digital.video.gateway.service.RecordingTaskService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 录像下载节点
 * 
 * 功能：
 * 1. 以报警时间为中心，取前后N秒的录像
 * 2. 延迟执行：等待afterSeconds秒后再开始下载，确保录像已录制完成
 * 3. 同步等待：等待录像下载和上传完成后才返回，以便后续节点可以使用录像结果
 * 4. 时间范围：[报警时间 - beforeSeconds, 报警时间 + afterSeconds]
 * 5. 针对海康设备自动进行ffmpeg转码（海康IPC下载的是私有PS流格式）
 */
public class RecordHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(RecordHandler.class);
    private final RecordingTaskService recordingTaskService;
    
    // 最大等待时间（秒）
    private static final int MAX_WAIT_SECONDS = 180;  // 3分钟

    public RecordHandler(RecordingTaskService recordingTaskService) {
        this.recordingTaskService = recordingTaskService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) {
        if (recordingTaskService == null) {
            logger.warn("RecordingTaskService 未初始化，跳过录像任务创建");
            return true;
        }
        // 测试模式下跳过录像（模拟设备不存在）
        Map<String, Object> payload = context.getPayload();
        if (payload != null && Boolean.TRUE.equals(payload.get("test"))) {
            logger.info("测试模式，跳过录像节点: deviceId={}", context.getDeviceId());
            return true;
        }
        if (context.getDeviceId() == null) {
            logger.warn("缺少deviceId，无法创建录像任务");
            return false;
        }

        Map<String, Object> cfg = node.getConfig();
        int channel = 1;
        long beforeSeconds = 15;
        long afterSeconds = 15;
        
        if (cfg != null) {
            if (cfg.get("channel") instanceof Number) {
                channel = ((Number) cfg.get("channel")).intValue();
            }
            
            if (cfg.get("duration") instanceof Number) {
                long duration = ((Number) cfg.get("duration")).longValue();
                beforeSeconds = duration / 2;
                afterSeconds = duration - beforeSeconds;
            }
            
            if (cfg.get("beforeSeconds") instanceof Number) {
                beforeSeconds = ((Number) cfg.get("beforeSeconds")).longValue();
            }
            if (cfg.get("afterSeconds") instanceof Number) {
                afterSeconds = ((Number) cfg.get("afterSeconds")).longValue();
            }
            
            if (cfg.get("recordBeforeSeconds") instanceof Number) {
                beforeSeconds = ((Number) cfg.get("recordBeforeSeconds")).longValue();
            }
            if (cfg.get("recordAfterSeconds") instanceof Number) {
                afterSeconds = ((Number) cfg.get("recordAfterSeconds")).longValue();
            }
        }

        final long alarmTime = System.currentTimeMillis();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        final String startTime = sdf.format(new Date(alarmTime - beforeSeconds * 1000));
        final String endTime = sdf.format(new Date(alarmTime + afterSeconds * 1000));
        
        final String deviceId = context.getDeviceId();
        final long delaySeconds = afterSeconds + 5;
        
        logger.info("录像下载节点开始: deviceId={}, channel={}, 报警时间={}, 时间范围=[{} ~ {}], 先延迟{}秒等待录像完成",
                deviceId, channel, sdf.format(new Date(alarmTime)), startTime, endTime, delaySeconds);
        
        // 1. 先延迟等待，确保录像已录制完成
        try {
            logger.info("等待{}秒，确保录像录制完成...", delaySeconds);
            Thread.sleep(delaySeconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("录像延迟等待被中断");
            return false;
        }
        
        // 2. 同步下载录像（带超时等待）
        logger.info("开始下载录像: deviceId={}, 时间范围=[{} ~ {}]", deviceId, startTime, endTime);
        
        RecordingTask task = recordingTaskService.downloadRecordingSync(deviceId, channel, startTime, endTime, MAX_WAIT_SECONDS);
        
        if (task == null) {
            logger.error("录像下载任务创建失败: deviceId={}", deviceId);
            return false;
        }
        
        // 3. 检查下载结果
        if (task.getStatus() == RecordingTaskService.STATUS_COMPLETED) {
            String localPath = task.getLocalFilePath();
            String ossUrl = task.getOssUrl();
            
            logger.info("录像下载完成: taskId={}, localPath={}, ossUrl={}", task.getTaskId(), localPath, ossUrl);
            
            // 4. 检查是否需要转码（海康设备的私有PS流格式）
            String deviceBrand = (String) context.getVariables().get("deviceBrand");
            if (localPath != null && "hikvision".equalsIgnoreCase(deviceBrand)) {
                File videoFile = new File(localPath);
                if (videoFile.exists() && needsTranscoding(videoFile)) {
                    logger.info("检测到海康私有格式，开始ffmpeg转码: taskId={}", task.getTaskId());
                    String transcodedPath = transcodeToMp4(localPath);
                    if (transcodedPath != null) {
                        // 转码成功，删除原始文件，更新路径
                        if (videoFile.delete()) {
                            logger.debug("已删除原始私有格式文件: {}", localPath);
                        }
                        localPath = transcodedPath;
                        task.setLocalFilePath(transcodedPath);
                        logger.info("ffmpeg转码成功: taskId={}, newPath={}", task.getTaskId(), transcodedPath);
                    } else {
                        logger.warn("ffmpeg转码失败，将使用原始文件: taskId={}", task.getTaskId());
                    }
                }
            }
            
            // 保存到context，供后续节点使用
            context.putVariable("recordFilePath", localPath);
            context.putVariable("recordTaskId", task.getTaskId());
            context.putVariable("recordStartTime", startTime);
            context.putVariable("recordEndTime", endTime);
            
            // 如果已经上传到OSS，保存URL
            if (ossUrl != null && !ossUrl.isEmpty()) {
                context.putVariable("recordOssUrl", ossUrl);
                context.putVariable("ossUrl", ossUrl);  // 兼容OssUploadHandler的变量名
            }
            
            return true;
        } else if (task.getStatus() == RecordingTaskService.STATUS_FAILED) {
            logger.error("录像下载失败: taskId={}, error={}", task.getTaskId(), task.getErrorMessage());
            return false;
        } else {
            logger.warn("录像下载超时或状态未知: taskId={}, status={}", task.getTaskId(), task.getStatus());
            return false;
        }
    }
    
    /**
     * 检查视频文件是否需要转码（海康私有格式）
     * 海康私有PS流格式以 "IMKH" 开头，标准MP4以 "ftyp" 开头
     */
    private boolean needsTranscoding(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[4];
            if (fis.read(header) == 4) {
                // 检查是否为海康私有格式 (IMKH)
                if (header[0] == 'I' && header[1] == 'M' && header[2] == 'K' && header[3] == 'H') {
                    return true;
                }
                // 检查是否已经是标准MP4 (ftyp)
                if (header[0] == 0x00 && header[1] == 0x00 && header[2] == 0x00 && 
                    (header[3] == 0x18 || header[3] == 0x1C || header[3] == 0x20)) {
                    byte[] ftypCheck = new byte[4];
                    if (fis.read(ftypCheck) == 4) {
                        if (ftypCheck[0] == 'f' && ftypCheck[1] == 't' && ftypCheck[2] == 'y' && ftypCheck[3] == 'p') {
                            return false; // 已经是标准MP4
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("检查视频格式异常: {}", file.getAbsolutePath(), e);
        }
        return true; // 默认需要转码
    }
    
    /**
     * 使用ffmpeg将视频转码为标准MP4格式
     * @param inputPath 输入文件路径
     * @return 转码后的文件路径，失败返回null
     */
    private String transcodeToMp4(String inputPath) {
        try {
            File inputFile = new File(inputPath);
            String baseName = inputFile.getName();
            String outputName;
            if (baseName.toLowerCase().endsWith(".mp4")) {
                outputName = baseName.substring(0, baseName.length() - 4) + "_converted.mp4";
            } else {
                outputName = baseName + "_converted.mp4";
            }
            String outputPath = inputFile.getParent() + File.separator + outputName;
            
            // 构建ffmpeg命令
            // -y: 覆盖输出文件
            // -c:v copy: 视频流直接复制（不重新编码，速度快）
            // -c:a aac: 音频转为AAC格式
            // -movflags +faststart: 优化流式播放
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-i", inputPath,
                "-c:v", "copy", "-c:a", "aac", 
                "-movflags", "+faststart",
                outputPath
            );
            pb.redirectErrorStream(true);
            
            logger.info("执行ffmpeg转码: {} -> {}", inputPath, outputPath);
            Process process = pb.start();
            
            // 读取ffmpeg输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // 等待进程完成（最多5分钟）
            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.error("ffmpeg转码超时（5分钟），已强制终止");
                return null;
            }
            
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                File outputFile = new File(outputPath);
                if (outputFile.exists() && outputFile.length() > 0) {
                    logger.info("ffmpeg转码完成: exitCode={}, outputSize={}", exitCode, outputFile.length());
                    return outputPath;
                } else {
                    logger.error("ffmpeg转码输出文件不存在或为空: {}", outputPath);
                    return null;
                }
            } else {
                logger.error("ffmpeg转码失败: exitCode={}, output={}", exitCode, 
                        output.length() > 500 ? output.substring(output.length() - 500) : output);
                return null;
            }
        } catch (Exception e) {
            logger.error("ffmpeg转码异常: {}", inputPath, e);
            return null;
        }
    }
}
