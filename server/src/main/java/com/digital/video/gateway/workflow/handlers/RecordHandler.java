package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.database.RecordingTask;
import com.digital.video.gateway.service.OssService;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 录像下载节点
 * 
 * 功能：
 * 1. 以报警时间为中心，取前后N秒的录像
 * 2. 延迟执行：先等待 afterSeconds 秒再下载，否则“后段”录像尚未录完，下载不完整
 * 3. 前后两段分别下载后合并为一个视频，再上传 OSS
 * 4. 时间范围：[报警时间 - beforeSeconds, 报警时间 + afterSeconds]
 * 5. 针对海康设备自动进行ffmpeg转码（海康IPC下载的是私有PS流格式）
 */
public class RecordHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(RecordHandler.class);
    private final RecordingTaskService recordingTaskService;
    private final OssService ossService;
    
    // 最大等待时间（秒）
    private static final int MAX_WAIT_SECONDS = 180;  // 3分钟

    public RecordHandler(RecordingTaskService recordingTaskService) {
        this(recordingTaskService, null);
    }

    public RecordHandler(RecordingTaskService recordingTaskService, OssService ossService) {
        this.recordingTaskService = recordingTaskService;
        this.ossService = ossService;
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
        final String alarmTimeStr = sdf.format(new Date(alarmTime));
        final String startTime = sdf.format(new Date(alarmTime - beforeSeconds * 1000));
        final String endTime = sdf.format(new Date(alarmTime + afterSeconds * 1000));
        final String midTime = alarmTimeStr;  // 报警时刻，用于前后分段
        
        final String deviceId = context.getDeviceId();
        // 后多少秒就必须先等待多少秒再下载，否则“后段”录像未录完，下载不完整
        final long delaySeconds = afterSeconds + 1;
        
        logger.info("录像下载节点开始: deviceId={}, channel={}, 报警时间={}, 时间范围=[{} ~ {}], 先延迟{}秒等待后段录制完成",
                deviceId, channel, alarmTimeStr, startTime, endTime, delaySeconds);
        
        // 1. 先延迟等待，确保“后段”录像已录制完成
        try {
            logger.info("等待{}秒（后段{}秒），再开始下载...", delaySeconds, afterSeconds);
            Thread.sleep(delaySeconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("录像延迟等待被中断");
            return false;
        }
        
        String localPath;
        String ossUrl = null;
        boolean twoSegmentMerge = beforeSeconds > 0 && afterSeconds > 0;
        
        if (twoSegmentMerge) {
            // 2a. 前后两段分别下载（仅下载不上传），再合并为一个视频后上传
            String seg1Start = startTime;
            String seg1End = midTime;
            String seg2Start = midTime;
            String seg2End = endTime;
            logger.info("分段下载: 前段[{} ~ {}], 后段[{} ~ {}]", seg1Start, seg1End, seg2Start, seg2End);
            RecordingTask task1 = recordingTaskService.downloadRecordingSync(deviceId, channel, seg1Start, seg1End, MAX_WAIT_SECONDS, false);
            RecordingTask task2 = recordingTaskService.downloadRecordingSync(deviceId, channel, seg2Start, seg2End, MAX_WAIT_SECONDS, false);
            if (task1 == null || task2 == null || task1.getStatus() != RecordingTaskService.STATUS_COMPLETED || task2.getStatus() != RecordingTaskService.STATUS_COMPLETED) {
                logger.error("分段下载失败: task1={}, task2={}", task1 != null ? task1.getStatus() : null, task2 != null ? task2.getStatus() : null);
                return false;
            }
            String path1 = task1.getLocalFilePath();
            String path2 = task2.getLocalFilePath();
            if (path1 == null || path2 == null || !new File(path1).exists() || !new File(path2).exists()) {
                logger.error("分段文件不存在: path1={}, path2={}", path1, path2);
                return false;
            }
            String deviceBrand = (String) context.getVariables().get("deviceBrand");
            if ("hikvision".equalsIgnoreCase(deviceBrand)) {
                if (needsTranscoding(new File(path1))) path1 = transcodeToMp4(path1);
                if (path1 != null && needsTranscoding(new File(path2))) path2 = transcodeToMp4(path2);
            }
            if (path1 == null || path2 == null) {
                logger.error("分段转码失败，无法合并");
                return false;
            }
            String mergedPath = mergeVideosWithFfmpeg(path1, path2, deviceId, alarmTimeStr);
            if (mergedPath == null) {
                logger.error("前后两段合并失败");
                return false;
            }
            localPath = mergedPath;
            if (ossService != null && ossService.isEnabled()) {
                String ossPath = "recordings/" + deviceId + "/" + new File(mergedPath).getName();
                ossUrl = ossService.uploadFile(mergedPath, ossPath);
            }
        } else {
            // 2b. 单段或仅前/仅后：一次下载并走原有逻辑（含上传）
            RecordingTask task = recordingTaskService.downloadRecordingSync(deviceId, channel, startTime, endTime, MAX_WAIT_SECONDS);
            if (task == null) {
                logger.error("录像下载任务创建失败: deviceId={}", deviceId);
                return false;
            }
            if (task.getStatus() != RecordingTaskService.STATUS_COMPLETED) {
                if (task.getStatus() == RecordingTaskService.STATUS_FAILED) {
                    logger.error("录像下载失败: taskId={}, error={}", task.getTaskId(), task.getErrorMessage());
                } else {
                    logger.warn("录像下载超时或状态未知: taskId={}, status={}", task.getTaskId(), task.getStatus());
                }
                return false;
            }
            localPath = task.getLocalFilePath();
            ossUrl = task.getOssUrl();
            String deviceBrand = (String) context.getVariables().get("deviceBrand");
            if (localPath != null && "hikvision".equalsIgnoreCase(deviceBrand)) {
                File videoFile = new File(localPath);
                if (videoFile.exists() && needsTranscoding(videoFile)) {
                    String transcodedPath = transcodeToMp4(localPath);
                    if (transcodedPath != null) {
                        if (videoFile.delete()) logger.debug("已删除原始私有格式文件: {}", localPath);
                        localPath = transcodedPath;
                    }
                }
            }
        }
        
        context.putVariable("recordFilePath", localPath);
        context.putVariable("recordStartTime", startTime);
        context.putVariable("recordEndTime", endTime);
        if (ossUrl != null && !ossUrl.isEmpty()) {
            context.putVariable("recordOssUrl", ossUrl);
            context.putVariable("ossUrl", ossUrl);
        }
        return true;
    }
    
    /**
     * 使用 ffmpeg concat 将两段视频合并为一个（按顺序拼接）
     */
    private String mergeVideosWithFfmpeg(String path1, String path2, String deviceId, String alarmTimeStr) {
        try {
            File dir = new File("./storage/recordings");
            if (!dir.exists()) dir.mkdirs();
            String safeSuffix = alarmTimeStr.replaceAll("[: -]", "");
            String listPath = dir.getAbsolutePath() + File.separator + "concat_" + deviceId + "_" + safeSuffix + ".txt";
            String outputPath = dir.getAbsolutePath() + File.separator + "merged_" + deviceId + "_" + safeSuffix + ".mp4";
            String abs1 = new File(path1).getAbsolutePath().replace("\\", "/").replace("'", "'\\''");
            String abs2 = new File(path2).getAbsolutePath().replace("\\", "/").replace("'", "'\\''");
            String listContent = "file '" + abs1 + "'\nfile '" + abs2 + "'\n";
            Files.write(java.nio.file.Paths.get(listPath), listContent.getBytes(StandardCharsets.UTF_8));
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", listPath,
                "-c", "copy", outputPath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append("\n");
            }
            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            try { Files.deleteIfExists(java.nio.file.Paths.get(listPath)); } catch (Exception ignored) {}
            if (!finished) {
                process.destroyForcibly();
                logger.error("ffmpeg合并超时");
                return null;
            }
            if (process.exitValue() == 0 && new File(outputPath).exists()) {
                logger.info("前后两段录像合并成功: {}", outputPath);
                return outputPath;
            }
            logger.error("ffmpeg合并失败: exitCode={}, output={}", process.exitValue(), out.length() > 500 ? out.substring(out.length() - 500) : out);
            return null;
        } catch (Exception e) {
            logger.error("合并录像异常", e);
            return null;
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
