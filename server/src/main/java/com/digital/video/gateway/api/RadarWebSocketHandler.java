package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.driver.livox.model.Point;
import com.digital.video.gateway.service.RadarService;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 雷达WebSocket处理器
 * 实时推送点云数据和侵入检测结果
 */
public class RadarWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(RadarWebSocketHandler.class);
    
    private final RadarService radarService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 设备ID -> WebSocket连接列表
    private final Map<String, List<Session>> connections = new ConcurrentHashMap<>();
    
    public RadarWebSocketHandler(RadarService radarService) {
        this.radarService = radarService;
    }

    /**
     * 添加WebSocket连接
     */
    public void addConnection(String deviceId, Session session) {
        connections.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(session);
        logger.info("添加WebSocket连接: deviceId={}, 当前连接数={}", deviceId, 
                connections.get(deviceId).size());
    }
    
    /**
     * 移除WebSocket连接
     */
    public void removeConnection(Session session) {
        connections.forEach((deviceId, sessions) -> {
            if (sessions.remove(session)) {
                logger.info("移除WebSocket连接: deviceId={}, 剩余连接数={}", deviceId, sessions.size());
            }
        });
    }

    /**
     * 推送点云数据
     */
    public void pushPointCloud(String deviceId, List<Point> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        
        List<Session> conns = connections.get(deviceId);
        if (conns == null || conns.isEmpty()) {
            // 没有连接，不推送（避免日志过多）
            return;
        }
        
        logger.debug("准备推送点云数据: deviceId={}, 点数={}, 连接数={}", 
                deviceId, points.size(), conns.size());

        // 采样：每10个点取1个，减少数据量
        List<Point> sampledPoints = new ArrayList<>();
        for (int i = 0; i < points.size(); i += 10) {
            sampledPoints.add(points.get(i));
        }

        Map<String, Object> message = new HashMap<>();
        message.put("type", "pointcloud");
        message.put("timestamp", System.currentTimeMillis());
        message.put("points", convertPointsToMap(sampledPoints));
        message.put("pointCount", sampledPoints.size());

        sendToAll(deviceId, message);
    }

    /**
     * 推送侵入检测结果
     */
    public void pushIntrusion(String deviceId, Map<String, Object> intrusionData) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "intrusion");
        message.put("timestamp", System.currentTimeMillis());
        message.putAll(intrusionData);

        sendToAll(deviceId, message);
    }

    /**
     * 发送消息给所有连接的客户端
     */
    private void sendToAll(String deviceId, Map<String, Object> message) {
        List<Session> conns = connections.get(deviceId);
        if (conns == null || conns.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            conns.removeIf(session -> {
                try {
                    if (session.isOpen()) {
                        session.getRemote().sendString(json);
                        return false; // 连接正常，不移除
                    } else {
                        return true; // 连接已关闭，移除
                    }
                } catch (IOException e) {
                    logger.error("发送WebSocket消息失败: deviceId={}", deviceId, e);
                    return true; // 发送失败，移除连接
                }
            });
        } catch (Exception e) {
            logger.error("序列化WebSocket消息失败: deviceId={}", deviceId, e);
        }
    }

    /**
     * 转换点列表为Map（用于JSON序列化）
     */
    private List<Map<String, Object>> convertPointsToMap(List<Point> points) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Point point : points) {
            Map<String, Object> map = new HashMap<>();
            map.put("x", point.x);
            map.put("y", point.y);
            map.put("z", point.z);
            map.put("r", point.reflectivity);
            result.add(map);
        }
        return result;
    }
    
    /**
     * 记录推送日志（用于调试）
     */
    public void logPushStats(String deviceId) {
        List<Session> conns = connections.get(deviceId);
        if (conns != null && !conns.isEmpty()) {
            logger.debug("推送点云数据: deviceId={}, 连接数={}", deviceId, conns.size());
        }
    }
}
