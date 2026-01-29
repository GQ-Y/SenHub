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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
    
    // 点云发送统计（设备ID -> 统计信息）
    private final Map<String, AtomicLong> pointCloudFrameCount = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> pointCloudPointCount = new ConcurrentHashMap<>();
    private final Map<String, Long> lastLogTime = new ConcurrentHashMap<>();

    /** 点云缓冲：worker 只写入缓冲，由定时任务批量发送，避免 worker 阻塞在 send 导致吞吐骤降 */
    private static final int BATCH_FLUSH_MS = 80;
    private static final int MAX_BUFFER_POINTS = 80000;
    private static final int MAX_POINTS_PER_MESSAGE = 50000;
    private final Map<String, ConcurrentLinkedQueue<Map<String, Object>>> pointCloudBuffer = new ConcurrentHashMap<>();
    private final ScheduledExecutorService batchFlusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PointCloudBatchFlusher");
        t.setDaemon(true);
        return t;
    });

    public RadarWebSocketHandler(RadarService radarService) {
        this.radarService = radarService;
        batchFlusher.scheduleAtFixedRate(this::flushPointCloudBatches, BATCH_FLUSH_MS, BATCH_FLUSH_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 添加WebSocket连接
     * 使用 CopyOnWriteArrayList 保证线程安全
     */
    public void addConnection(String deviceId, Session session) {
        connections.computeIfAbsent(deviceId, k -> new CopyOnWriteArrayList<>()).add(session);
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
                // 如果该设备没有连接了，清理统计信息
                if (sessions.isEmpty()) {
                    pointCloudFrameCount.remove(deviceId);
                    pointCloudPointCount.remove(deviceId);
                    lastLogTime.remove(deviceId);
                    pointCloudBuffer.remove(deviceId);
                }
            }
        });
    }

    /**
     * 推送点云数据（写入缓冲，由定时任务批量发送，避免 worker 阻塞在 send）
     */
    public void pushPointCloud(String deviceId, List<Point> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<Session> conns = connections.get(deviceId);
        if (conns == null || conns.isEmpty()) {
            return;
        }

        pointCloudFrameCount.computeIfAbsent(deviceId, k -> new AtomicLong(0)).incrementAndGet();
        pointCloudPointCount.computeIfAbsent(deviceId, k -> new AtomicLong(0)).addAndGet(points.size());

        ConcurrentLinkedQueue<Map<String, Object>> queue = pointCloudBuffer.computeIfAbsent(deviceId, k -> new ConcurrentLinkedQueue<>());
        List<Map<String, Object>> maps = convertPointsToMap(points);
        for (Map<String, Object> m : maps) {
            queue.offer(m);
        }
        // 防止缓冲无限增长：超过上限时丢弃最旧的一批
        while (queue.size() > MAX_BUFFER_POINTS) {
            for (int i = 0; i < 10000 && queue.poll() != null; i++) { }
        }

        long now = System.currentTimeMillis();
        Long lastLog = lastLogTime.get(deviceId);
        if (lastLog == null || (now - lastLog) >= 5000) {
            long frameCount = pointCloudFrameCount.get(deviceId).get();
            long pointCount = pointCloudPointCount.get(deviceId).get();
            long avgPointsPerFrame = frameCount > 0 ? pointCount / frameCount : 0;
            logger.info("[点云推送统计] deviceId={}, 总帧数={}, 总点数={}, 平均每帧点数={}, 缓冲点数={}, 连接数={}",
                    deviceId, frameCount, pointCount, avgPointsPerFrame, queue.size(), conns.size());
            lastLogTime.put(deviceId, now);
        }
    }

    /**
     * 定时将缓冲中的点云批量发送（单线程执行，不阻塞 worker）
     */
    private void flushPointCloudBatches() {
        for (String deviceId : new ArrayList<>(connections.keySet())) {
            List<Session> conns = connections.get(deviceId);
            if (conns == null || conns.isEmpty()) {
                continue;
            }
            ConcurrentLinkedQueue<Map<String, Object>> queue = pointCloudBuffer.get(deviceId);
            if (queue == null || queue.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> batch = new ArrayList<>();
            Map<String, Object> p;
            while (batch.size() < MAX_POINTS_PER_MESSAGE && (p = queue.poll()) != null) {
                batch.add(p);
            }
            if (batch.isEmpty()) {
                continue;
            }
            Map<String, Object> message = new HashMap<>();
            message.put("type", "pointcloud");
            message.put("timestamp", System.currentTimeMillis());
            message.put("points", batch);
            message.put("pointCount", batch.size());
            sendToAll(deviceId, message);
        }
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
     * 使用线程安全的方式遍历和移除失效连接
     */
    private void sendToAll(String deviceId, Map<String, Object> message) {
        List<Session> conns = connections.get(deviceId);
        if (conns == null || conns.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            List<Session> toRemove = new ArrayList<>();
            
            // 遍历发送，收集需要移除的连接
            for (Session session : conns) {
                try {
                    if (session.isOpen()) {
                        session.getRemote().sendString(json);
                    } else {
                        toRemove.add(session);
                    }
                } catch (IOException e) {
                    logger.debug("发送WebSocket消息失败，将移除连接: deviceId={}", deviceId);
                    toRemove.add(session);
                }
            }
            
            // 移除失效的连接
            if (!toRemove.isEmpty()) {
                conns.removeAll(toRemove);
                logger.debug("已移除 {} 个失效的WebSocket连接: deviceId={}", toRemove.size(), deviceId);
            }
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
            if (point.zoneId != null) {
                map.put("zoneId", point.zoneId);
            }
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
