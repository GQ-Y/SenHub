package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiAnalysisRecordTable {
    private static final Logger logger = LoggerFactory.getLogger(AiAnalysisRecordTable.class);

    public static void createTables(Connection connection) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS ai_analysis_records ("
                + "id TEXT PRIMARY KEY,"
                + "image_url TEXT,"
                + "event_title TEXT,"
                + "event_name TEXT,"
                + "time TEXT,"
                + "verify_result TEXT,"
                + "verify_reason TEXT,"
                + "alert_text TEXT,"
                + "voice_url TEXT,"
                + "created_at TEXT DEFAULT (datetime('now','localtime'))"
                + ")";
        String indexSql = "CREATE INDEX IF NOT EXISTS idx_ai_analysis_time ON ai_analysis_records(time DESC)";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            stmt.execute(indexSql);
        }
    }

    public static void insert(Connection connection, Map<String, Object> record) throws SQLException {
        String sql = "INSERT OR REPLACE INTO ai_analysis_records "
                + "(id, image_url, event_title, event_name, time, verify_result, verify_reason, alert_text, voice_url) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, str(record.get("id")));
            ps.setString(2, str(record.get("imageUrl")));
            ps.setString(3, str(record.get("eventTitle")));
            ps.setString(4, str(record.get("eventName")));
            ps.setString(5, str(record.get("time")));
            ps.setString(6, str(record.get("verifyResult")));
            ps.setString(7, str(record.get("verifyReason")));
            ps.setString(8, str(record.get("alertText")));
            ps.setString(9, str(record.get("voiceUrl")));
            ps.executeUpdate();
        }
    }

    public static void updateField(Connection connection, String id, String field, String value) throws SQLException {
        String column = fieldToColumn(field);
        if (column == null) return;
        String sql = "UPDATE ai_analysis_records SET " + column + " = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * 分页查询，支持事件类型、时间范围筛选
     * @param eventName 事件名称/类型，如 LOITERING、PERIMETER_INTRUSION；null 表示不过滤
     * @param startTime 开始时间（含），格式 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd；null 表示不限制
     * @param endTime   结束时间（含）；null 表示不限制
     */
    public static List<Map<String, Object>> list(Connection connection, int limit, int offset,
                                                  String eventName, String startTime, String endTime) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM ai_analysis_records WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (eventName != null && !eventName.trim().isEmpty()) {
            sql.append(" AND event_name = ?");
            params.add(eventName.trim());
        }
        if (startTime != null && !startTime.trim().isEmpty()) {
            sql.append(" AND time >= ?");
            params.add(startTime.trim());
        }
        if (endTime != null && !endTime.trim().isEmpty()) {
            sql.append(" AND time <= ?");
            params.add(endTime.trim());
        }
        sql.append(" ORDER BY time DESC LIMIT ? OFFSET ?");
        params.add(limit > 0 ? limit : 100);
        params.add(offset >= 0 ? offset : 0);

        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rowToMap(rs));
                }
            }
        }
        return results;
    }

    /**
     * 统计符合条件的记录总数（与 list 使用相同筛选条件）
     */
    public static int count(Connection connection, String eventName, String startTime, String endTime) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ai_analysis_records WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (eventName != null && !eventName.trim().isEmpty()) {
            sql.append(" AND event_name = ?");
            params.add(eventName.trim());
        }
        if (startTime != null && !startTime.trim().isEmpty()) {
            sql.append(" AND time >= ?");
            params.add(startTime.trim());
        }
        if (endTime != null && !endTime.trim().isEmpty()) {
            sql.append(" AND time <= ?");
            params.add(endTime.trim());
        }
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static boolean delete(Connection connection, String id) throws SQLException {
        if (id == null || id.isEmpty()) return false;
        String sql = "DELETE FROM ai_analysis_records WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 批量删除，返回实际删除条数
     */
    public static int deleteByIds(Connection connection, List<String> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) return 0;
        int n = 0;
        for (String id : ids) {
            if (id != null && !id.isEmpty() && delete(connection, id)) n++;
        }
        return n;
    }

    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> m = new HashMap<>();
        m.put("id", rs.getString("id"));
        m.put("imageUrl", rs.getString("image_url"));
        m.put("eventTitle", rs.getString("event_title"));
        m.put("eventName", rs.getString("event_name"));
        m.put("time", rs.getString("time"));
        m.put("verifyResult", rs.getString("verify_result"));
        m.put("verifyReason", rs.getString("verify_reason"));
        m.put("alertText", rs.getString("alert_text"));
        m.put("voiceUrl", rs.getString("voice_url"));
        return m;
    }

    private static String fieldToColumn(String field) {
        switch (field) {
            case "imageUrl": return "image_url";
            case "alertText": return "alert_text";
            case "voiceUrl": return "voice_url";
            case "verifyResult": return "verify_result";
            case "verifyReason": return "verify_reason";
            default: return null;
        }
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
