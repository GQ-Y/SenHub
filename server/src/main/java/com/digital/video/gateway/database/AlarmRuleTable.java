package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 报警规则表管理类
 */
public class AlarmRuleTable {
    private static final Logger logger = LoggerFactory.getLogger(AlarmRuleTable.class);

    /**
     * 创建报警规则相关表
     */
    public static void createTables(Connection connection) throws SQLException {
        // alarm_rules 表
        String createAlarmRulesTable = "CREATE TABLE IF NOT EXISTS alarm_rules (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "rule_id TEXT UNIQUE NOT NULL, " +
            "name TEXT NOT NULL, " +
            "alarm_type TEXT NOT NULL, " +
            "scope TEXT NOT NULL, " +
            "device_id TEXT, " +
            "assembly_id TEXT, " +
            "enabled INTEGER DEFAULT 1, " +
            "priority INTEGER DEFAULT 0, " +
            "actions TEXT NOT NULL, " +
            "conditions TEXT, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

        // 创建索引
        String createIndex = "CREATE INDEX IF NOT EXISTS idx_alarm_rules_rule_id ON alarm_rules(rule_id); " +
            "CREATE INDEX IF NOT EXISTS idx_alarm_rules_alarm_type ON alarm_rules(alarm_type); " +
            "CREATE INDEX IF NOT EXISTS idx_alarm_rules_scope ON alarm_rules(scope); " +
            "CREATE INDEX IF NOT EXISTS idx_alarm_rules_device_id ON alarm_rules(device_id); " +
            "CREATE INDEX IF NOT EXISTS idx_alarm_rules_assembly_id ON alarm_rules(assembly_id); " +
            "CREATE INDEX IF NOT EXISTS idx_alarm_rules_enabled ON alarm_rules(enabled);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createAlarmRulesTable);
            stmt.execute(createIndex);
            logger.info("报警规则表创建成功");
        }
    }
}
