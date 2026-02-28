package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.CaptureService;
import com.digital.video.gateway.service.PTZService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 雷达入侵事件工作流节点（异步）。
 * 原子操作：PTZ 转向 → 等待到位 → 抓拍。
 * 完成后将 capturePath 写入 FlowContext，并回调 continueFromAsync。
 *
 * FlowContext 中需包含以下变量（由 PointCloudProcessCenter 写入）：
 *   cameraDeviceId, cameraChannel, pan, tilt, zoom, ptzDelay
 *   radarDeviceId, zoneId, zoneName, targetId, targetType
 *   centroidX, centroidY, centroidZ, distance, azimuth
 *   motionState, bboxWidth, bboxHeight, bboxDepth, pointCount
 */
public class RadarIntrusionHandler implements FlowNodeHandler {

    private static final Logger logger = LoggerFactory.getLogger(RadarIntrusionHandler.class);

    private final PTZService ptzService;
    private final CaptureService captureService;

    private static final long DEFAULT_PTZ_DELAY_MS = 1500;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "RadarIntrusion-Capture");
        t.setDaemon(true);
        return t;
    });

    public RadarIntrusionHandler(PTZService ptzService, CaptureService captureService) {
        this.ptzService = ptzService;
        this.captureService = captureService;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        String cameraDeviceId = getStringVar(context, "cameraDeviceId");
        int cameraChannel = getIntVar(context, "cameraChannel", 1);
        float pan = getFloatVar(context, "pan", 0);
        float tilt = getFloatVar(context, "tilt", 0);
        float zoom = getFloatVar(context, "zoom", 1);
        long ptzDelay = getLongVar(context, "ptzDelay", DEFAULT_PTZ_DELAY_MS);

        if (cameraDeviceId == null || cameraDeviceId.isEmpty()) {
            logger.error("雷达入侵Handler缺少 cameraDeviceId，跳过");
            context.getExecutor().continueFromAsync(context, false);
            return true;
        }

        String targetId = getStringVar(context, "targetId");
        String targetType = getStringVar(context, "targetType");
        logger.info("雷达入侵Handler执行: camera={}, ch={}, pan={}, tilt={}, zoom={}, " +
                        "targetId={}, targetType={}, ptzDelay={}ms",
                cameraDeviceId, cameraChannel, pan, tilt, zoom,
                targetId, targetType, ptzDelay);

        boolean ptzOk = ptzService.gotoAngle(cameraDeviceId, cameraChannel, pan, tilt, zoom);
        if (!ptzOk) {
            logger.warn("PTZ转向失败: camera={}, pan={}, tilt={}", cameraDeviceId, pan, tilt);
        }

        final String finalCameraDeviceId = cameraDeviceId;
        final int finalChannel = cameraChannel;

        scheduler.schedule(() -> {
            try {
                String capturePath = captureService.captureSnapshot(finalCameraDeviceId, finalChannel);
                if (capturePath != null) {
                    context.putVariable("capturePath", capturePath);
                    logger.info("雷达入侵抓拍成功: camera={}, path={}, targetId={}",
                            finalCameraDeviceId, capturePath, targetId);
                    context.getExecutor().continueFromAsync(context, true);
                } else {
                    logger.warn("雷达入侵抓拍失败: camera={}, targetId={}", finalCameraDeviceId, targetId);
                    context.getExecutor().continueFromAsync(context, false);
                }
            } catch (Exception e) {
                logger.error("雷达入侵抓拍异常: camera={}", finalCameraDeviceId, e);
                context.getExecutor().continueFromAsync(context, false);
            }
        }, ptzDelay, TimeUnit.MILLISECONDS);

        return true;
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    // ---- 从 context 读取变量的工具方法 ----

    private String getStringVar(FlowContext ctx, String key) {
        Object val = ctx.getVariable(key);
        return val != null ? val.toString() : null;
    }

    private int getIntVar(FlowContext ctx, String key, int defaultVal) {
        Object val = ctx.getVariable(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultVal;
    }

    private float getFloatVar(FlowContext ctx, String key, float defaultVal) {
        Object val = ctx.getVariable(key);
        if (val instanceof Number) return ((Number) val).floatValue();
        if (val instanceof String) {
            try { return Float.parseFloat((String) val); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultVal;
    }

    private long getLongVar(FlowContext ctx, String key, long defaultVal) {
        Object val = ctx.getVariable(key);
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) {
            try { return Long.parseLong((String) val); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultVal;
    }
}
