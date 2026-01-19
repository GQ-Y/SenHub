package com.digital.video.gateway.workflow;

import com.digital.video.gateway.database.AlarmFlow;
import com.digital.video.gateway.database.Database;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

/**
 * 报警流程服务
 */
public class FlowService {
    private static final Logger logger = LoggerFactory.getLogger(FlowService.class);
    private final Database database;
    private final ObjectMapper mapper = new ObjectMapper();

    public FlowService(Database database) {
        this.database = database;
    }

    public List<AlarmFlow> listFlows() {
        List<AlarmFlow> flows = new ArrayList<>();
        String sql = "SELECT * FROM alarm_flows ORDER BY is_default DESC, created_at DESC";
        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                flows.add(AlarmFlow.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("查询流程列表失败", e);
        }
        return flows;
    }

    public AlarmFlow getFlow(String flowId) {
        String sql = "SELECT * FROM alarm_flows WHERE flow_id = ?";
        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, flowId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return AlarmFlow.fromResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("查询流程失败: {}", flowId, e);
        }
        return null;
    }

    public AlarmFlow saveFlow(AlarmFlow flow) {
        if (flow.getFlowId() == null || flow.getFlowId().isEmpty()) {
            flow.setFlowId(UUID.randomUUID().toString());
        }
        String sql = "INSERT INTO alarm_flows (flow_id, name, description, flow_type, nodes, connections, is_default, enabled, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT(flow_id) DO UPDATE SET "
                + "name = excluded.name, "
                + "description = excluded.description, "
                + "flow_type = excluded.flow_type, "
                + "nodes = excluded.nodes, "
                + "connections = excluded.connections, "
                + "is_default = excluded.is_default, "
                + "enabled = excluded.enabled, "
                + "updated_at = CURRENT_TIMESTAMP";

        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, flow.getFlowId());
            pstmt.setString(2, flow.getName());
            pstmt.setString(3, flow.getDescription());
            pstmt.setString(4, flow.getFlowType() != null ? flow.getFlowType() : "alarm");
            pstmt.setString(5, flow.getNodes());
            pstmt.setString(6, flow.getConnections());
            pstmt.setInt(7, flow.isDefault() ? 1 : 0);
            pstmt.setInt(8, flow.isEnabled() ? 1 : 0);
            pstmt.executeUpdate();
            return getFlow(flow.getFlowId());
        } catch (SQLException e) {
            logger.error("保存流程失败", e);
            return null;
        }
    }

    public boolean deleteFlow(String flowId) {
        String sql = "DELETE FROM alarm_flows WHERE flow_id = ?";
        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, flowId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("删除流程失败: {}", flowId, e);
            return false;
        }
    }

    public FlowDefinition toDefinition(AlarmFlow flow) {
        if (flow == null) {
            return null;
        }
        return FlowDefinition.fromJsonStrings(flow.getFlowId(), flow.getName(), flow.getNodes(), flow.getConnections());
    }

    /**
     * 确保默认报警流程存在（若不存在则创建）
     */
    public void ensureDefaultAlarmFlow() {
        try {
            AlarmFlow existing = getFlow("default_alarm_flow");
            if (existing != null) {
                return;
            }

            List<Map<String, Object>> nodes = new ArrayList<>();
            nodes.add(node("start", "event_trigger", Map.of()));
            nodes.add(node("capture", "capture", Map.of("channel", 1)));
            nodes.add(node("mqtt", "mqtt_publish", Map.of("topic", "alarm/report/{deviceId}")));
            nodes.add(node("oss", "oss_upload", Map.of("path", "alarm/{deviceId}/{fileName}")));
            nodes.add(node("speaker", "speaker_play", Map.of("text", "检测到{alarmType}报警")));
            nodes.add(node("webhook", "webhook", Map.of("url", "https://example.com/webhook")));

            List<Map<String, Object>> conns = new ArrayList<>();
            conns.add(conn("start", "capture", null));
            conns.add(conn("capture", "mqtt", "success"));
            conns.add(conn("mqtt", "oss", null));
            conns.add(conn("oss", "speaker", null));
            conns.add(conn("speaker", "webhook", null));

            AlarmFlow flow = new AlarmFlow();
            flow.setFlowId("default_alarm_flow");
            flow.setName("默认报警流程");
            flow.setDescription("事件触发->抓图->MQTT->OSS->播报->Webhook");
            flow.setFlowType("alarm");
            flow.setDefault(true);
            flow.setEnabled(true);
            flow.setNodes(mapper.writeValueAsString(nodes));
            flow.setConnections(mapper.writeValueAsString(conns));
            saveFlow(flow);
            logger.info("已创建默认报警流程: default_alarm_flow");
        } catch (Exception e) {
            logger.error("创建默认报警流程失败", e);
        }
    }

    private Map<String, Object> node(String id, String type, Map<String, Object> cfg) {
        Map<String, Object> m = new HashMap<>();
        m.put("nodeId", id);
        m.put("type", type);
        m.put("config", cfg != null ? cfg : Map.of());
        return m;
    }

    private Map<String, Object> conn(String from, String to, String condition) {
        Map<String, Object> m = new HashMap<>();
        m.put("from", from);
        m.put("to", to);
        if (condition != null) {
            m.put("condition", condition);
        }
        return m;
    }
}
