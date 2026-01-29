package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 设备重启节点：调用设备 SDK 发送重启命令（设备异常时自动重启等）。
 * config: deviceId（可选，默认 context.getDeviceId()）
 */
public class DeviceRebootHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(DeviceRebootHandler.class);
    private final DeviceManager deviceManager;

    public DeviceRebootHandler(DeviceManager deviceManager) {
        this.deviceManager = deviceManager;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) {
        if (deviceManager == null) {
            logger.warn("DeviceManager 未初始化，跳过 device_reboot");
            return false;
        }
        Map<String, Object> cfg = node.getConfig();
        String deviceId = null;
        if (cfg != null && cfg.get("deviceId") instanceof String) {
            String raw = (String) cfg.get("deviceId");
            deviceId = HandlerUtils.renderTemplate(raw, context, context.getVariables() != null ? new java.util.HashMap<>(context.getVariables()) : null);
        }
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = context.getDeviceId();
        }
        if (deviceId == null || deviceId.isBlank()) {
            logger.warn("device_reboot 缺少设备ID，跳过");
            return false;
        }

        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) {
            logger.warn("device_reboot 设备不存在: {}", deviceId);
            return false;
        }
        if (!deviceManager.isDeviceLoggedIn(deviceId)) {
            if (!deviceManager.loginDevice(device)) {
                logger.warn("device_reboot 设备未登录，无法重启: {}", deviceId);
                return false;
            }
        }

        DeviceSDK sdk = deviceManager.getDeviceSDK(deviceId);
        if (sdk == null) {
            logger.warn("device_reboot 无法获取设备 SDK: {}", deviceId);
            return false;
        }
        int userId = deviceManager.getDeviceUserId(deviceId);
        boolean result = sdk.rebootDevice(userId);
        logger.info("device_reboot: deviceId={}, result={}", deviceId, result);
        return result;
    }
}
