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

/**
 * 录像下载节点
 */
public class RecordHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(RecordHandler.class);
    private final RecordingTaskService recordingTaskService;

    public RecordHandler(RecordingTaskService recordingTaskService) {
        this.recordingTaskService = recordingTaskService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) {
        if (recordingTaskService == null) {
            logger.warn("RecordingTaskService 未初始化，跳过录像任务创建");
            return false;
        }
        if (context.getDeviceId() == null) {
            logger.warn("缺少deviceId，无法创建录像任务");
            return false;
        }

        Map<String, Object> cfg = node.getConfig();
        int channel = 1;
        long beforeSeconds = 30;
        long afterSeconds = 30;
        if (cfg != null) {
            if (cfg.get("channel") instanceof Number) {
                channel = ((Number) cfg.get("channel")).intValue();
            }
            if (cfg.get("recordBeforeSeconds") instanceof Number) {
                beforeSeconds = ((Number) cfg.get("recordBeforeSeconds")).longValue();
            }
            if (cfg.get("recordAfterSeconds") instanceof Number) {
                afterSeconds = ((Number) cfg.get("recordAfterSeconds")).longValue();
            }
        }

        long now = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String startTime = sdf.format(new Date(now - beforeSeconds * 1000));
        String endTime = sdf.format(new Date(now + afterSeconds * 1000));

        RecordingTask task = recordingTaskService.downloadRecording(context.getDeviceId(), channel, startTime, endTime);
        if (task != null) {
            context.putVariable("recordTaskId", task.getTaskId());
            logger.info("创建录像下载任务成功: taskId={}", task.getTaskId());
            return true;
        }

        logger.warn("创建录像下载任务失败: deviceId={}", context.getDeviceId());
        return false;
    }
}
