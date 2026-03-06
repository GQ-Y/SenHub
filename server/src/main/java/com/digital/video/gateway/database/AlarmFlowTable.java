package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 报警流程定义表
 */
public class AlarmFlowTable {
    private static final Logger logger = LoggerFactory.getLogger(AlarmFlowTable.class);

    public static void createTables(Connection connection) throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS alarm_flows (" +
                "id SERIAL PRIMARY KEY, " +
                "flow_id TEXT UNIQUE NOT NULL, " +
                "name TEXT NOT NULL, " +
                "description TEXT, " +
                "flow_type TEXT DEFAULT 'alarm', " +
                "nodes TEXT NOT NULL, " +
                "connections TEXT NOT NULL, " +
                "is_default INTEGER DEFAULT 0, " +
                "enabled INTEGER DEFAULT 1, " +
                "created_at TIMESTAMP DEFAULT NOW(), " +
                "updated_at TIMESTAMP DEFAULT NOW()" +
                ")";

        String createIndex = "CREATE INDEX IF NOT EXISTS idx_alarm_flows_flow_id ON alarm_flows(flow_id); " +
                "CREATE INDEX IF NOT EXISTS idx_alarm_flows_type ON alarm_flows(flow_type); " +
                "CREATE INDEX IF NOT EXISTS idx_alarm_flows_default ON alarm_flows(is_default); " +
                "CREATE INDEX IF NOT EXISTS idx_alarm_flows_enabled ON alarm_flows(enabled);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
            stmt.execute(createIndex);
            logger.info("报警流程表创建成功");
        }
    }
}
