package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.CaptureService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

/**
 * 抓图节点处理器
 * 异步抓图：提交抓图任务后立即返回，抓图完成后通过回调继续执行后续节点
 */
public class CaptureHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(CaptureHandler.class);
    private final CaptureService captureService;

    public CaptureHandler(CaptureService captureService) {
        this.captureService = captureService;
    }

    @Override
    public boolean isAsync() {
        return true;  // 声明为异步节点
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        if (captureService == null) {
            logger.warn("CaptureService 未初始化，跳过抓图");
            return false;
        }
        if (context.getDeviceId() == null) {
            logger.warn("缺少deviceId，无法抓图");
            return false;
        }
        
        if (context.getExecutor() == null) {
            logger.error("FlowExecutor 未设置，无法执行异步抓图回调");
            return false;
        }

        Map<String, Object> cfg = node.getConfig();
        // 默认通道号：如果没有配置，使用0（天地伟业设备通道号从0开始）
        // CaptureService会根据设备品牌进一步调整
        int channel = 0;
        if (cfg != null && cfg.get("channel") instanceof Number) {
            channel = ((Number) cfg.get("channel")).intValue();
        }
        
        // 创建 final 变量供 lambda 使用
        final int finalChannel = channel;
        final String deviceId = context.getDeviceId();

        // 提交异步抓图任务
        logger.info("提交异步抓图任务: deviceId={}, channel={}", deviceId, finalChannel);
        captureService.captureAsync(deviceId, finalChannel, (path, success) -> {
            try {
                if (success && path != null) {
                    context.putVariable("capturePath", path);
                    context.putVariable("captureFileName", new File(path).getName());
                    logger.info("异步抓图成功: deviceId={}, channel={}, path={}", deviceId, finalChannel, path);
                } else {
                    logger.warn("异步抓图失败: deviceId={}, channel={}", deviceId, finalChannel);
                }
                
                // 通知 FlowExecutor 继续执行后续节点
                context.getExecutor().continueFromAsync(context, success);
            } catch (Exception e) {
                logger.error("异步抓图回调异常: deviceId={}", context.getDeviceId(), e);
                // 即使回调异常，也尝试继续执行后续节点（标记为失败）
                try {
                    context.getExecutor().continueFromAsync(context, false);
                } catch (Exception ex) {
                    logger.error("继续执行后续节点失败", ex);
                }
            }
        });
        
        // 立即返回，不阻塞工作流主线程
        return true;
    }
}
