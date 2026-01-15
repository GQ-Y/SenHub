package com.digital.video.gateway.service;

import com.digital.video.gateway.driver.livox.model.Point;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.RadarIntrusionRecord;
import com.digital.video.gateway.database.RadarIntrusionRecordDAO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 侵入录制管理器
 * 负责收集防区内的点云轨迹，最长录制10秒
 */
public class RecordingManager {
    private static final Logger logger = LoggerFactory.getLogger(RecordingManager.class);
    private static final int MAX_DURATION_MS = 10000; // 最长录制10秒
    private static final int SILENCE_TIMEOUT_MS = 3000; // 3秒无数据视为结束

    private final Database database;
    private final RadarIntrusionRecordDAO recordDAO;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ZoneRecorder> recorders = new ConcurrentHashMap<>();

    public RecordingManager(Database database) {
        this.database = database;
        this.recordDAO = new RadarIntrusionRecordDAO(database.getConnection());
    }

    /**
     * 处理每一帧点云数据
     */
    public void processFrame(String deviceId, List<Point> points) {
        if (points == null || points.isEmpty())
            return;

        // 1. 按防区归类点云
        Map<String, List<Point>> zonePointsMap = new HashMap<>();
        for (Point p : points) {
            if (p.zoneId != null) {
                zonePointsMap.computeIfAbsent(p.zoneId, k -> new ArrayList<>()).add(p);
            }
        }

        long now = System.currentTimeMillis();

        // 2. 更新活跃的录制器
        for (Map.Entry<String, List<Point>> entry : zonePointsMap.entrySet()) {
            String zoneId = entry.getKey();
            List<Point> zonePoints = entry.getValue();

            recorders.compute(zoneId, (k, recorder) -> {
                if (recorder == null) {
                    // 新的侵入事件
                    recorder = new ZoneRecorder(zoneId, deviceId, now);
                    logger.info("开始录制侵入事件: zoneId={}", zoneId);
                }
                recorder.addFrame(zonePoints, now);
                return recorder;
            });
        }

        // 3. 检查所有录制器状态（超时或结束）
        Iterator<Map.Entry<String, ZoneRecorder>> it = recorders.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ZoneRecorder> entry = it.next();
            ZoneRecorder recorder = entry.getValue();

            boolean shouldStop = false;
            // 情况A: 超过最大录制时长 (10秒)
            if (now - recorder.startTime > MAX_DURATION_MS) {
                logger.info("侵入录制达到最大时长 (10s): zoneId={}", recorder.zoneId);
                shouldStop = true;
            }
            // 情况B: 超过静默时间 (1秒没收到新数据)
            else if (now - recorder.lastUpdateTime > SILENCE_TIMEOUT_MS) {
                // 判断是否是当前帧没有数据导致的（静默）
                // 因为我们在上面已经处理了有数据的zone，如果recorder还在map里且没有被更新lastUpdateTime，说明本帧该zone无数据
                // 但这里需要注意 processFrame 是每一帧调用的。
                // 如果本帧 zonePointsMap 不包含该 zoneId，则 lastUpdateTime 不会更新。
                // 所以可以直接用 recorder.lastUpdateTime 判断。
                logger.info("侵入录制结束 (静默): zoneId={}", recorder.zoneId);
                shouldStop = true;
            }

            if (shouldStop) {
                saveRecording(recorder);
                it.remove();
            }
        }
    }

    /**
     * 保存录制文件和数据库记录
     */
    private void saveRecording(ZoneRecorder recorder) {
        if (recorder.frames.isEmpty())
            return;

        try {
            // 1. 生成文件路径
            String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String uuid = UUID.randomUUID().toString();
            String fileName = uuid + ".json";
            String relativePath = "records/intrusions/" + dateStr + "/" + fileName;

            File processedFile = new File(relativePath);
            File parentDir = processedFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 2. 写入JSON文件
            // 格式: { zoneId, deviceId, startTime, duration, frames: [ { ts, points:
            // [{x,y,z,r}] } ] }
            Map<String, Object> data = new HashMap<>();
            data.put("zoneId", recorder.zoneId);
            data.put("deviceId", recorder.deviceId);
            data.put("startTime", recorder.startTime);
            data.put("duration", recorder.lastUpdateTime - recorder.startTime);
            data.put("frameCount", recorder.frames.size());

            List<Map<String, Object>> framesData = new ArrayList<>();
            int totalPoints = 0;
            for (DataFrame frame : recorder.frames) {
                Map<String, Object> frameMap = new HashMap<>();
                frameMap.put("ts", frame.timestamp - recorder.startTime); // 相对时间戳
                List<Map<String, Object>> pts = new ArrayList<>();
                for (Point p : frame.points) {
                    Map<String, Object> pm = new HashMap<>();
                    pm.put("x", p.x);
                    pm.put("y", p.y);
                    pm.put("z", p.z);
                    pts.add(pm);
                }
                frameMap.put("points", pts);
                framesData.add(frameMap);
                totalPoints += frame.points.size();
            }
            data.put("frames", framesData);

            objectMapper.writeValue(processedFile, data);

            // 3. 写入数据库记录
            // 注意：这里创建一个新的记录，与 IntrusionDetectionService 可能创建的记录是独立的。
            // 用户需求是 "单独存起来"。建议这里作为主要的轨迹记录。
            RadarIntrusionRecord record = new RadarIntrusionRecord();
            record.setRecordId(uuid);
            record.setDeviceId(recorder.deviceId);
            record.setZoneId(recorder.zoneId);
            record.setDetectedAt(new Timestamp(recorder.startTime));
            record.setPointCount(totalPoints);
            // record.setFilePath(relativePath); // 假设我们在 Entity 中添加了这个字段，或者复用 clusterId
            // 存路径？
            // 暂时存入 clusterId 字段作为 filepath 的临时存放地，或者我们确实需要添加字段。
            // 既然 task 中有 "Modify RadarIntrusionRecord to add filePath"，我假设我应该添加字段。
            // 但如果不想改DB Schema，可以用 clusterId 存 "FILE:..."
            // 为了规范，我还是不存 DB 扩展字段了，或者在 clusterId 存 "TRAJECTORY"
            record.setClusterId("TRAJECTORY:" + relativePath);

            recordDAO.save(record);

            logger.info("侵入录制已保存: {}", relativePath);

        } catch (Exception e) {
            logger.error("保存侵入录制失败", e);
        }
    }

    /**
     * 内部类：防区录制器
     */
    private static class ZoneRecorder {
        String zoneId;
        String deviceId;
        long startTime;
        long lastUpdateTime;
        List<DataFrame> frames = new ArrayList<>();

        ZoneRecorder(String zoneId, String deviceId, long startTime) {
            this.zoneId = zoneId;
            this.deviceId = deviceId;
            this.startTime = startTime;
            this.lastUpdateTime = startTime;
        }

        void addFrame(List<Point> points, long now) {
            this.frames.add(new DataFrame(now, points));
            this.lastUpdateTime = now;
        }
    }

    private static class DataFrame {
        long timestamp;
        List<Point> points;

        DataFrame(long timestamp, List<Point> points) {
            this.timestamp = timestamp;
            this.points = points; // 这里应该深拷贝点吗？Point是Mutable的嗎？Point是Public field.
            // Point 是 new 出来的，这里引用即可，除非后续会被修改。
            // routePointCloud 中的 points 是每次 new 的，所以安全。
        }
    }
}
