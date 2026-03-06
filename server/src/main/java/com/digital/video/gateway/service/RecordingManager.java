package com.digital.video.gateway.service;

import com.digital.video.gateway.driver.livox.model.Point;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.RadarIntrusionRecord;
import com.digital.video.gateway.database.RadarIntrusionRecordDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 侵入录制管理器
 * 负责收集防区内的点云轨迹，最长录制10秒。
 *
 * 优化：
 * - 对帧进行采样（每 SAMPLE_INTERVAL_MS 毫秒取一帧），降低内存占用
 * - 保存时流式写入 JSON，避免构建完整对象树导致 OOM
 * - 所有 ZoneRecorder 操作加 synchronized 防止并发修改
 */
public class RecordingManager {
    private static final Logger logger = LoggerFactory.getLogger(RecordingManager.class);
    private static final int MAX_DURATION_MS = 10000;
    private static final int SILENCE_TIMEOUT_MS = 3000;
    /** 采样间隔：仅记录间隔超过此值的帧，减少内存 */
    private static final long SAMPLE_INTERVAL_MS = 100;

    private final Database database;
    private final RadarIntrusionRecordDAO recordDAO;
    private final Map<String, ZoneRecorder> recorders = new ConcurrentHashMap<>();

    public RecordingManager(Database database) {
        this.database = database;
        this.recordDAO = new RadarIntrusionRecordDAO(database);
    }

    public void processFrame(String deviceId, List<Point> points) {
        if (points == null || points.isEmpty()) return;

        Map<String, List<Point>> zonePointsMap = new HashMap<>();
        for (Point p : points) {
            if (p.zoneId != null) {
                zonePointsMap.computeIfAbsent(p.zoneId, k -> new ArrayList<>()).add(p);
            }
        }

        long now = System.currentTimeMillis();

        for (Map.Entry<String, List<Point>> entry : zonePointsMap.entrySet()) {
            String zoneId = entry.getKey();
            List<Point> zonePoints = entry.getValue();

            recorders.compute(zoneId, (k, recorder) -> {
                if (recorder == null) {
                    recorder = new ZoneRecorder(zoneId, deviceId, now);
                    logger.info("开始录制侵入事件: zoneId={}, 首帧侵入点数={}", zoneId, zonePoints.size());
                }
                synchronized (recorder) {
                    recorder.addFrame(zonePoints, now);
                }
                return recorder;
            });
        }

        List<ZoneRecorder> toSave = new ArrayList<>();
        Iterator<Map.Entry<String, ZoneRecorder>> it = recorders.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ZoneRecorder> entry = it.next();
            ZoneRecorder recorder = entry.getValue();

            boolean shouldStop = false;
            synchronized (recorder) {
                if (now - recorder.startTime > MAX_DURATION_MS) {
                    logger.info("侵入录制达到最大时长 (10s): zoneId={}", recorder.zoneId);
                    shouldStop = true;
                } else if (now - recorder.lastUpdateTime > SILENCE_TIMEOUT_MS) {
                    logger.info("侵入录制结束 (静默): zoneId={}", recorder.zoneId);
                    shouldStop = true;
                }
            }

            if (shouldStop) {
                it.remove();
                toSave.add(recorder);
            }
        }

        for (ZoneRecorder rec : toSave) {
            saveRecording(rec);
        }
    }

    /**
     * 流式写入 JSON，避免构建完整对象树导致 OOM
     */
    private void saveRecording(ZoneRecorder recorder) {
        List<DataFrame> snapshot;
        synchronized (recorder) {
            if (recorder.frames.isEmpty()) return;
            snapshot = new ArrayList<>(recorder.frames);
            recorder.frames.clear();
        }

        try {
            String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String uuid = UUID.randomUUID().toString();
            String relativePath = "records/intrusions/" + dateStr + "/" + uuid + ".json";

            File file = new File(relativePath);
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) parentDir.mkdirs();

            int totalPoints = 0;
            int minPts = Integer.MAX_VALUE;
            int maxPts = 0;

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write("{\"zoneId\":\"");
                writer.write(recorder.zoneId);
                writer.write("\",\"deviceId\":\"");
                writer.write(recorder.deviceId);
                writer.write("\",\"startTime\":");
                writer.write(String.valueOf(recorder.startTime));
                long duration = recorder.lastUpdateTime - recorder.startTime;
                writer.write(",\"duration\":");
                writer.write(String.valueOf(duration));
                writer.write(",\"frameCount\":");
                writer.write(String.valueOf(snapshot.size()));
                writer.write(",\"frames\":[");

                for (int fi = 0; fi < snapshot.size(); fi++) {
                    DataFrame frame = snapshot.get(fi);
                    int ptCount = frame.points.size();
                    totalPoints += ptCount;
                    if (ptCount < minPts) minPts = ptCount;
                    if (ptCount > maxPts) maxPts = ptCount;

                    if (fi > 0) writer.write(',');
                    writer.write("{\"ts\":");
                    writer.write(String.valueOf(frame.timestamp - recorder.startTime));
                    writer.write(",\"points\":[");
                    for (int pi = 0; pi < frame.points.size(); pi++) {
                        Point p = frame.points.get(pi);
                        if (pi > 0) writer.write(',');
                        writer.write("{\"x\":");
                        writer.write(String.valueOf(p.x));
                        writer.write(",\"y\":");
                        writer.write(String.valueOf(p.y));
                        writer.write(",\"z\":");
                        writer.write(String.valueOf(p.z));
                        writer.write('}');
                    }
                    writer.write("]}");
                }
                writer.write("]}");
            }

            RadarIntrusionRecord record = new RadarIntrusionRecord();
            record.setRecordId(uuid);
            record.setDeviceId(recorder.deviceId);
            record.setZoneId(recorder.zoneId);
            record.setDetectedAt(new Timestamp(recorder.startTime));
            record.setPointCount(totalPoints);
            long duration = recorder.lastUpdateTime - recorder.startTime;
            record.setDuration(duration);
            record.setClusterId("TRAJECTORY:" + relativePath);
            recordDAO.save(record);

            if (minPts == Integer.MAX_VALUE) minPts = 0;
            double avgPts = snapshot.isEmpty() ? 0 : (double) totalPoints / snapshot.size();
            logger.info("侵入录制已保存: {} frameCount={} totalPoints={} pointsPerFrame min={} avg={} max={} duration={}ms",
                    relativePath, snapshot.size(), totalPoints, minPts,
                    String.format("%.1f", avgPts), maxPts, duration);

        } catch (Exception e) {
            logger.error("保存侵入录制失败", e);
        }
    }

    private static class ZoneRecorder {
        final String zoneId;
        final String deviceId;
        final long startTime;
        volatile long lastUpdateTime;
        final List<DataFrame> frames = new ArrayList<>();
        long lastSampleTime;

        ZoneRecorder(String zoneId, String deviceId, long startTime) {
            this.zoneId = zoneId;
            this.deviceId = deviceId;
            this.startTime = startTime;
            this.lastUpdateTime = startTime;
            this.lastSampleTime = 0;
        }

        void addFrame(List<Point> points, long now) {
            this.lastUpdateTime = now;
            if (now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
                this.frames.add(new DataFrame(now, points));
                this.lastSampleTime = now;
            }
        }
    }

    private static class DataFrame {
        final long timestamp;
        final List<Point> points;

        DataFrame(long timestamp, List<Point> points) {
            this.timestamp = timestamp;
            this.points = new ArrayList<>(points);
        }
    }
}
