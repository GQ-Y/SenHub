package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 雷达服务不可用时的 WebSocket 降级处理器。
 * 客户端连接后发送一条「服务不可用」状态并关闭，避免 404 导致连接失败难以排查。
 */
public class RadarWebSocketHandlerFallback extends RadarWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(RadarWebSocketHandlerFallback.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public RadarWebSocketHandlerFallback() {
        super(null);
    }

    @Override
    public void addConnection(String deviceId, Session session) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "status");
            msg.put("connected", false);
            msg.put("error", "radar_unavailable");
            msg.put("deviceId", deviceId);
            msg.put("message", "雷达服务未启动或不可用（如 Livox SDK 未加载）");
            if (session.isOpen()) {
                session.getRemote().sendString(objectMapper.writeValueAsString(msg));
                session.close(1011, "radar_unavailable");
            }
            logger.info("雷达 WebSocket 降级: 已通知客户端雷达不可用, deviceId={}", deviceId);
        } catch (Exception e) {
            logger.warn("发送雷达不可用消息失败: deviceId={}", deviceId, e);
            try {
                if (session.isOpen()) session.close(1011, "radar_unavailable");
            } catch (Exception ignored) { }
        }
    }
}
