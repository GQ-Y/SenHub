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

    public static List<Map<String, Object>> list(Connection connection, int limit, int offset) throws SQLException {
        String sql = "SELECT * FROM ai_analysis_records ORDER BY time DESC LIMIT ? OFFSET ?";
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit > 0 ? limit : 100);
            ps.setInt(2, offset >= 0 ? offset : 0);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rowToMap(rs));
                }
            }
        }
        return results;
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
