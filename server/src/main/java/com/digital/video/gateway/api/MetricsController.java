package com.digital.video.gateway.api;

import com.digital.video.gateway.database.Database;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * 时序监控大屏 API
 * GET /api/metrics/alarm-hourly          — 过去24小时每小时告警统计
 * GET /api/metrics/device-availability   — 近N分钟每分钟设备在线率折线图
 * GET /api/metrics/radar-framerate       — 指定雷达近N分钟点云帧率曲线
 * GET /api/metrics/radar-compare         — 多雷达实时对比仪表盘
 * GET /api/metrics/big-screen-summary    — 大屏汇总数据（设备/告警/工作流/装置）
 * GET /api/metrics/alarm-recent          — 最近N条告警记录（底部滚动栏）
 */
public class MetricsController {
    private static final Logger logger = LoggerFactory.getLogger(MetricsController.class);
    private final Database database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MetricsController(Database database) {
        this.database = database;
    }

    // ==================== 1. 每小时告警统计 ====================

    /**
     * GET /api/metrics/alarm-hourly?hours=24
     * 返回最近 N 小时（默认24）各小时的告警数量
     * Response: { code, message, data: [{hour:"09:00", count:5}, ...] }
     */
    public void getAlarmHourly(Context ctx) {
        int hours = parseIntParam(ctx.queryParam("hours"), 24);
        if (hours < 1 || hours > 168) hours = 24;

        String sql = "SELECT TO_CHAR(recorded_at AT TIME ZONE 'Asia/Shanghai', 'HH24') as hour, " +
                "COUNT(*) as alarm_count " +
                "FROM alarm_records " +
                "WHERE recorded_at >= NOW() - INTERVAL '" + hours + " hours' " +
                "GROUP BY TO_CHAR(recorded_at AT TIME ZONE 'Asia/Shanghai', 'HH24') " +
                "ORDER BY hour";

        try (Connection conn = database.getPoolConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            Map<String, Integer> hourData = new HashMap<>();
            while (rs.next()) {
                hourData.put(rs.getString("hour"), rs.getInt("alarm_count"));
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (int h = 0; h < 24; h++) {
                Map<String, Object> point = new LinkedHashMap<>();
                String hs = String.format("%02d", h);
                point.put("hour", hs + ":00");
                point.put("count", hourData.getOrDefault(hs, 0));
                result.add(point);
            }

            sendSuccess(ctx, result);
        } catch (SQLException e) {
            logger.error("获取每小时告警统计失败", e);
            sendError(ctx, 500, "查询失败: " + e.getMessage());
        }
    }

    // ==================== 2. 每分钟设备在线率 ====================

    /**
     * GET /api/metrics/device-availability?minutes=60
     * 返回最近 N 分钟（默认60）每分钟设备在线率折线图数据
     * Response: { code, message, data: [{time:"14:30", total:10, online:8, rate:80.0}, ...] }
     */
    public void getDeviceAvailability(Context ctx) {
        int minutes = parseIntParam(ctx.queryParam("minutes"), 60);
        if (minutes < 1 || minutes > 1440) minutes = 60;

        String sql = "SELECT " +
                "TO_CHAR(time AT TIME ZONE 'Asia/Shanghai', 'HH24:MI') as minute, " +
                "total_count, online_count " +
                "FROM device_metrics " +
                "WHERE time >= NOW() - INTERVAL '" + minutes + " minutes' " +
                "ORDER BY time ASC";

        try (Connection conn = database.getPoolConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            List<Map<String, Object>> result = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> point = new LinkedHashMap<>();
                int total = rs.getInt("total_count");
                int online = rs.getInt("online_count");
                double rate = total > 0 ? Math.round(online * 1000.0 / total) / 10.0 : 0.0;
                point.put("time", rs.getString("minute"));
                point.put("total", total);
                point.put("online", online);
                point.put("rate", rate);
                result.add(point);
            }

            sendSuccess(ctx, result);
        } catch (SQLException e) {
            logger.error("获取设备在线率时序数据失败", e);
            sendError(ctx, 500, "查询失败: " + e.getMessage());
        }
    }

    // ==================== 3. 点云帧率监控曲线 ====================

    /**
     * GET /api/metrics/radar-framerate?deviceId=xxx&minutes=10
     * 返回指定雷达设备近 N 分钟的点云帧率曲线
     * Response: { code, message, data: [{time:"14:30:00", fps:9.8, frameCount:588, pointCount:120000}, ...] }
     */
    public void getRadarFramerate(Context ctx) {
        String deviceId = ctx.queryParam("deviceId");
        int minutes = parseIntParam(ctx.queryParam("minutes"), 10);
        if (minutes < 1 || minutes > 1440) minutes = 10;

        StringBuilder sqlBuf = new StringBuilder(
                "SELECT TO_CHAR(time AT TIME ZONE 'Asia/Shanghai', 'HH24:MI:SS') as ts, " +
                "device_id, fps, frame_count, point_count " +
                "FROM radar_frame_metrics " +
                "WHERE time >= NOW() - INTERVAL '" + minutes + " minutes' ");

        if (deviceId != null && !deviceId.trim().isEmpty()) {
            sqlBuf.append("AND device_id = '").append(deviceId.replace("'", "''")).append("' ");
        }
        sqlBuf.append("ORDER BY time ASC");

        try (Connection conn = database.getPoolConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlBuf.toString())) {

            List<Map<String, Object>> result = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("time", rs.getString("ts"));
                point.put("deviceId", rs.getString("device_id"));
                point.put("fps", rs.getDouble("fps"));
                point.put("frameCount", rs.getLong("frame_count"));
                point.put("pointCount", rs.getLong("point_count"));
                result.add(point);
            }

            sendSuccess(ctx, result);
        } catch (SQLException e) {
            logger.error("获取雷达帧率曲线失败", e);
            sendError(ctx, 500, "查询失败: " + e.getMessage());
        }
    }

    // ==================== 4. 多雷达实时对比仪表盘 ====================

    /**
     * GET /api/metrics/radar-compare
     * 对所有雷达设备，聚合最近一个统计周期（60s）内各自的帧率、点数、侵入次数
     * Response: { code, message, data: [{deviceId, radarName, fps, pointCount, frameCount, intrusionCount, status}, ...] }
     */
    public void getRadarCompare(Context ctx) {
        // 最近60s各雷达的帧率统计
        String fpsSQL = "SELECT device_id, " +
                "SUM(frame_count) as frame_count, " +
                "SUM(point_count) as point_count, " +
                "AVG(fps) as avg_fps " +
                "FROM radar_frame_metrics " +
                "WHERE time >= NOW() - INTERVAL '65 seconds' " +
                "GROUP BY device_id";

        // 雷达设备基础信息
        String radarSQL = "SELECT device_id, radar_name, status FROM radar_devices";

        // 最近60s侵入记录数（按雷达设备分组）
        String intrusionSQL = "SELECT device_id, COUNT(*) as intrusion_count " +
                "FROM radar_intrusion_records " +
                "WHERE created_at >= NOW() - INTERVAL '65 seconds' " +
                "GROUP BY device_id";

        try (Connection conn = database.getPoolConnection()) {
            // 1. 加载雷达基础信息
            Map<String, Map<String, Object>> radarMap = new LinkedHashMap<>();
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(radarSQL)) {
                while (rs.next()) {
                    Map<String, Object> rd = new LinkedHashMap<>();
                    String devId = rs.getString("device_id");
                    rd.put("deviceId", devId);
                    rd.put("radarName", rs.getString("radar_name"));
                    rd.put("status", rs.getInt("status"));
                    rd.put("fps", 0.0);
                    rd.put("frameCount", 0L);
                    rd.put("pointCount", 0L);
                    rd.put("intrusionCount", 0);
                    radarMap.put(devId, rd);
                }
            }

            // 2. 注入帧率数据
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(fpsSQL)) {
                while (rs.next()) {
                    String devId = rs.getString("device_id");
                    Map<String, Object> rd = radarMap.computeIfAbsent(devId, k -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("deviceId", k);
                        m.put("radarName", k);
                        m.put("status", 0);
                        m.put("fps", 0.0);
                        m.put("frameCount", 0L);
                        m.put("pointCount", 0L);
                        m.put("intrusionCount", 0);
                        return m;
                    });
                    rd.put("fps", Math.round(rs.getDouble("avg_fps") * 10.0) / 10.0);
                    rd.put("frameCount", rs.getLong("frame_count"));
                    rd.put("pointCount", rs.getLong("point_count"));
                }
            }

            // 3. 注入侵入次数（表可能不存在时跳过）
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(intrusionSQL)) {
                while (rs.next()) {
                    String devId = rs.getString("device_id");
                    Map<String, Object> rd = radarMap.get(devId);
                    if (rd != null) {
                        rd.put("intrusionCount", rs.getInt("intrusion_count"));
                    }
                }
            } catch (SQLException ignored) {
                // radar_intrusion_records 表可能不存在或列名不同，跳过
            }

            sendSuccess(ctx, new ArrayList<>(radarMap.values()));
        } catch (SQLException e) {
            logger.error("获取多雷达对比数据失败", e);
            sendError(ctx, 500, "查询失败: " + e.getMessage());
        }
    }

    // ==================== 5. 大屏汇总 ====================

    /**
     * GET /api/metrics/big-screen-summary
     * 一次返回大屏所需的设备在线率、雷达运行率、告警处理率、工作流统计、webhook统计
     */
    public void getBigScreenSummary(Context ctx) {
        Map<String, Object> summary = new LinkedHashMap<>();

        try (Connection conn = database.getPoolConnection()) {

            // --- 设备在线率（最近一条 device_metrics 记录）---
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT total_count, online_count FROM device_metrics ORDER BY time DESC LIMIT 1")) {
                if (rs.next()) {
                    int total = rs.getInt("total_count");
                    int online = rs.getInt("online_count");
                    double rate = total > 0 ? Math.round(online * 1000.0 / total) / 10.0 : 0.0;
                    summary.put("deviceTotal", total);
                    summary.put("deviceOnline", online);
                    summary.put("deviceAvailability", rate);
                } else {
                    // 从 devices 表实时计算
                    try (Statement s2 = conn.createStatement();
                         ResultSet r2 = s2.executeQuery(
                                 "SELECT COUNT(*) as total, SUM(CASE WHEN status=1 THEN 1 ELSE 0 END) as online FROM devices")) {
                        if (r2.next()) {
                            int total = r2.getInt("total");
                            int online = r2.getInt("online");
                            double rate = total > 0 ? Math.round(online * 1000.0 / total) / 10.0 : 0.0;
                            summary.put("deviceTotal", total);
                            summary.put("deviceOnline", online);
                            summary.put("deviceAvailability", rate);
                        } else {
                            summary.put("deviceTotal", 0);
                            summary.put("deviceOnline", 0);
                            summary.put("deviceAvailability", 0.0);
                        }
                    }
                }
            } catch (SQLException e) {
                summary.put("deviceTotal", 0);
                summary.put("deviceOnline", 0);
                summary.put("deviceAvailability", 0.0);
            }

            // --- 24h 告警总数 + 已处理数 ---
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) as total, " +
                         "SUM(CASE WHEN status != 'pending' THEN 1 ELSE 0 END) as processed " +
                         "FROM alarm_records " +
                         "WHERE recorded_at >= NOW() - INTERVAL '24 hours'")) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    int processed = rs.getInt("processed");
                    double alarmRate = total > 0 ? Math.round(processed * 1000.0 / total) / 10.0 : 100.0;
                    summary.put("alarmTotal24h", total);
                    summary.put("alarmProcessed24h", processed);
                    summary.put("alarmHandleRate", alarmRate);
                }
            } catch (SQLException e) {
                summary.put("alarmTotal24h", 0);
                summary.put("alarmProcessed24h", 0);
                summary.put("alarmHandleRate", 100.0);
            }

            // --- 雷达运行率（最近65秒有数据即算在线）---
            try {
                int totalRadars = 0;
                int onlineRadars = 0;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM radar_devices")) {
                    if (rs.next()) totalRadars = rs.getInt(1);
                }
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT COUNT(DISTINCT device_id) FROM radar_frame_metrics " +
                             "WHERE time >= NOW() - INTERVAL '65 seconds'")) {
                    if (rs.next()) onlineRadars = rs.getInt(1);
                }
                double radarRate = totalRadars > 0 ? Math.round(onlineRadars * 1000.0 / totalRadars) / 10.0 : 0.0;
                summary.put("radarTotal", totalRadars);
                summary.put("radarOnline", onlineRadars);
                summary.put("radarRate", radarRate);
            } catch (SQLException e) {
                summary.put("radarTotal", 0);
                summary.put("radarOnline", 0);
                summary.put("radarRate", 0.0);
            }

            // --- 工作流 24h 执行统计 ---
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) as total, COUNT(DISTINCT flow_id) as unique_flows " +
                         "FROM workflow_execution_history " +
                         "WHERE executed_at >= NOW() - INTERVAL '24 hours'")) {
                if (rs.next()) {
                    summary.put("workflowExec24h", rs.getInt("total"));
                    summary.put("workflowActive24h", rs.getInt("unique_flows"));
                }
            } catch (SQLException e) {
                summary.put("workflowExec24h", 0);
                summary.put("workflowActive24h", 0);
            }

            // --- OSS 上传 24h 统计（oss_upload_history 表，若存在）---
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) as total, " +
                         "SUM(CASE WHEN status='success' THEN 1 ELSE 0 END) as success " +
                         "FROM oss_upload_history " +
                         "WHERE created_at >= NOW() - INTERVAL '24 hours'")) {
                if (rs.next()) {
                    summary.put("ossUpload24h", rs.getInt("total"));
                    summary.put("ossUploadSuccess24h", rs.getInt("success"));
                }
            } catch (SQLException ignored) {
                // oss_upload_history 表可能不存在
                summary.put("ossUpload24h", 0);
                summary.put("ossUploadSuccess24h", 0);
            }

            sendSuccess(ctx, summary);
        } catch (SQLException e) {
            logger.error("获取大屏汇总数据失败", e);
            sendError(ctx, 500, "查询失败: " + e.getMessage());
        }
    }

    // ==================== 6. 最近告警记录（底部滚动栏）====================

    /**
     * GET /api/metrics/alarm-recent?limit=20
     * 返回最近 N 条告警记录（用于大屏底部告警滚动栏）
     */
    public void getAlarmRecent(Context ctx) {
        int limit = parseIntParam(ctx.queryParam("limit"), 20);
        if (limit < 1 || limit > 100) limit = 20;

        String sql = "SELECT r.alarm_id, r.alarm_type, r.alarm_level, r.recorded_at, " +
                "d.name AS device_name, d.ip AS ip_address " +
                "FROM alarm_records r " +
                "LEFT JOIN devices d ON d.device_id = r.device_id " +
                "ORDER BY r.recorded_at DESC LIMIT " + limit;

        try (Connection conn = database.getPoolConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            List<Map<String, Object>> result = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", rs.getString("alarm_id"));
                item.put("time", rs.getString("recorded_at") != null
                        ? rs.getTimestamp("recorded_at").toLocalDateTime()
                              .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                              .toLocalTime().toString().substring(0, 8)
                        : "--:--:--");
                String deviceName = rs.getString("device_name");
                String alarmType = rs.getString("alarm_type");
                String level = rs.getString("alarm_level");
                item.put("content", (deviceName != null ? deviceName : "未知设备")
                        + " " + (alarmType != null ? alarmType : "告警")
                        + (level != null ? " [" + level + "]" : ""));
                item.put("level", level);
                item.put("deviceName", deviceName);
                result.add(item);
            }

            sendSuccess(ctx, result);
        } catch (SQLException e) {
            logger.error("获取最近告警记录失败", e);
            sendError(ctx, 500, "查询失败: " + e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    private int parseIntParam(String val, int defaultValue) {
        if (val == null || val.trim().isEmpty()) return defaultValue;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private void sendSuccess(Context ctx, Object data) {
        ctx.status(200).contentType("application/json");
        try {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("code", 200);
            resp.put("message", "ok");
            resp.put("data", data);
            ctx.result(objectMapper.writeValueAsString(resp));
        } catch (Exception e) {
            ctx.result("{\"code\":200,\"message\":\"ok\",\"data\":[]}");
        }
    }

    private void sendError(Context ctx, int status, String message) {
        ctx.status(status).contentType("application/json");
        try {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("code", status);
            resp.put("message", message);
            resp.put("data", null);
            ctx.result(objectMapper.writeValueAsString(resp));
        } catch (Exception e) {
            ctx.result("{\"code\":" + status + ",\"message\":\"error\",\"data\":null}");
        }
    }
}
