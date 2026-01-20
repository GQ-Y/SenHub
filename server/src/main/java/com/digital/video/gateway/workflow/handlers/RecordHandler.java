package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.database.RecordingTask;
import com.digital.video.gateway.service.RecordingTaskService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 录像下载节点
 * 
 * 功能：
 * 1. 以报警时间为中心，取前后N秒的录像
 * 2. 延迟执行：等待afterSeconds秒后再开始下载，确保录像已录制完成
 * 3. 同步等待：等待录像下载和上传完成后才返回，以便后续节点可以使用录像结果
 * 4. 时间范围：[报警时间 - beforeSeconds, 报警时间 + afterSeconds]
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
}
