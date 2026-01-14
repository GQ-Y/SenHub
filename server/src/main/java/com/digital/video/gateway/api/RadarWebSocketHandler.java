package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.driver.livox.model.Point;
import com.digital.video.gateway.service.RadarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 雷达WebSocket处理器
 * 实时推送点云数据和侵入检测结果
 * 
 * 注意：Spark Java框架本身不支持WebSocket，需要使用Jetty WebSocket或SSE
 * 这里提供基础框架，实际实现需要添加WebSocket依赖
 */
public class RadarWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(RadarWebSocketHandler.class);
    
    private final RadarService radarService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 设备ID -> WebSocket连接列表
    private final Map<String, List<WebSocketConnection>> connections = new ConcurrentHashMap<>();
    
    public RadarWebSocketHandler(RadarService radarService) {
        this.radarService = radarService;
    }

    /**
     * 处理WebSocket连接
     * 路径: /api/radar/:deviceId/stream
     */
    public void handleConnection(String deviceId, Object webSocketSession) {
        // TODO: 实现WebSocket连接处理
        // 需要添加Jetty WebSocket依赖
        logger.info("WebSocket连接: deviceId={}", deviceId);
        
        WebSocketConnection conn = new WebSocketConnection(deviceId, webSocketSession);
        connections.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(conn);
    }

    /**
     * 推送点云数据
     */
    public void pushPointCloud(String deviceId, List<Point> points) {
        List<WebSocketConnection> conns = connections.get(deviceId);
        if (conns == null || conns.isEmpty()) {
            return;
        }

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
        List<WebSocketConnection> conns = connections.get(deviceId);
        if (conns == null) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            conns.removeIf(conn -> {
                try {
                    // TODO: 实际发送WebSocket消息
                    // conn.send(json);
                    return false;
                } catch (Exception e) {
                    logger.error("发送WebSocket消息失败", e);
                    return true; // 移除失败的连接
                }
            });
        } catch (Exception e) {
            logger.error("序列化WebSocket消息失败", e);
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
     * WebSocket连接内部类
     */
    private static class WebSocketConnection {
        String deviceId;
        Object session; // WebSocket Session对象

        WebSocketConnection(String deviceId, Object session) {
            this.deviceId = deviceId;
            this.session = session;
        }
    }
}
