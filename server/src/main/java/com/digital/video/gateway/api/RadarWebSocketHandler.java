package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.driver.livox.model.Point;
import com.digital.video.gateway.service.RadarService;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
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

    /** 按帧发送：单帧超过此点数时拆成多条消息，避免单条过大；Mid-360 单帧通常几百～几千点 */
    private static final int MAX_POINTS_PER_MESSAGE = 25000;
    /** 点云二进制格式：1 type + 8 timestamp + 4 count + 每点13字节(x,y,z float + r byte)，小端序 */
    private static final int BINARY_HEADER_BYTES = 1 + 8 + 4;
    private static final int BINARY_POINT_BYTES = 13;
    /** 点云发送：多线程编码+发送以跟上 20 万点/秒、约 2000+ 帧/秒（平均每帧约 90 点）；队列满时丢帧 */
    private static final int SEND_QUEUE_CAPACITY = 512;
    private static final int SEND_POOL_SIZE = 8;
    private final ExecutorService pointCloudSendExecutor = new ThreadPoolExecutor(
            SEND_POOL_SIZE, SEND_POOL_SIZE, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(SEND_QUEUE_CAPACITY),
            r -> {
                Thread t = new Thread(r, "PointCloudSend");
                t.setDaemon(true);
                return t;
            },
            (r, e) -> logger.warn("点云发送队列已满，丢弃本帧（按帧实时发送）")
    );

    public RadarWebSocketHandler(RadarService radarService) {
        this.radarService = radarService;
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
                }
            }
        });
    }

    /**
     * 按帧推送点云：收到一帧就提交发送任务，实时传输，不按秒/按点数攒批。
     * 避免整万/整十万的假象，与 Mid-360 非重复扫描的连续点率一致。
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

        final long ts = System.currentTimeMillis();
        final List<Point> copy = new ArrayList<>(points);
        pointCloudSendExecutor.execute(() -> {
            try {
                sendPointCloudFrame(deviceId, ts, copy);
            } catch (Exception e) {
                logger.debug("按帧发送点云失败: deviceId={}", deviceId, e);
            }
        });

        long now = System.currentTimeMillis();
        Long lastLog = lastLogTime.get(deviceId);
        if (lastLog == null || (now - lastLog) >= 5000) {
            long frameCount = pointCloudFrameCount.get(deviceId).get();
            long pointCount = pointCloudPointCount.get(deviceId).get();
            long avgPointsPerFrame = frameCount > 0 ? pointCount / frameCount : 0;
            logger.info("[点云按帧推送] deviceId={}, 总帧数={}, 总点数={}, 平均每帧点数={}, 连接数={}",
                    deviceId, frameCount, pointCount, avgPointsPerFrame, conns.size());
            lastLogTime.put(deviceId, now);
        }
    }

    /**
     * 发送一帧点云（可能拆成多条消息）：按帧传输，不混合多帧。
     */
    private void sendPointCloudFrame(String deviceId, long timestamp, List<Point> points) {
        int n = points.size();
        for (int off = 0; off < n; off += MAX_POINTS_PER_MESSAGE) {
            int len = Math.min(MAX_POINTS_PER_MESSAGE, n - off);
            List<Point> chunk = points.subList(off, off + len);
            sendPointCloudBinaryFromPoints(deviceId, timestamp, chunk);
        }
    }

    /**
     * 从 Point 列表编码并发送二进制点云（单条消息）
     */
    private void sendPointCloudBinaryFromPoints(String deviceId, long timestamp, List<Point> points) {
        List<Session> conns = connections.get(deviceId);
        if (conns == null || conns.isEmpty()) return;

        int n = points.size();
        ByteBuffer buf = ByteBuffer.allocate(BINARY_HEADER_BYTES + n * BINARY_POINT_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0);
        buf.putLong(timestamp);
        buf.putInt(n);
        for (Point p : points) {
            buf.putFloat(p.x).putFloat(p.y).putFloat(p.z).put(p.reflectivity);
        }
        buf.flip();

        List<Session> toRemove = new ArrayList<>();
        for (Session session : conns) {
            try {
                if (session.isOpen()) {
                    session.getRemote().sendBytes(buf.duplicate());
                } else {
                    toRemove.add(session);
                }
            } catch (IOException e) {
                logger.debug("发送点云二进制失败，将移除连接: deviceId={}", deviceId);
                toRemove.add(session);
            }
        }
        if (!toRemove.isEmpty()) {
            conns.removeAll(toRemove);
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
     * 发送 JSON 消息给所有连接的客户端（侵入、状态等）
     */
    private void sendToAll(String deviceId, Map<String, Object> message) {
        List<Session> conns = connections.get(deviceId);
        if (conns == null || conns.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            List<Session> toRemove = new ArrayList<>();
            
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
