package com.digital.video.gateway.api;

import com.digital.video.gateway.database.AlarmFlow;
import com.digital.video.gateway.workflow.FlowDefinition;
import com.digital.video.gateway.workflow.FlowExecutor;
import com.digital.video.gateway.workflow.FlowService;
import com.digital.video.gateway.workflow.FlowContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报警流程管理API
 */
public class FlowController {
    private static final Logger logger = LoggerFactory.getLogger(FlowController.class);
    private final FlowService flowService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FlowController(FlowService flowService) {
        this.flowService = flowService;
    }

    public String listFlows(Request request, Response response) {
        try {
            List<AlarmFlow> flows = flowService.listFlows();
            List<Map<String, Object>> result = flows.stream()
                    .map(AlarmFlow::toMap)
                    .collect(Collectors.toList());
            response.status(200);
            return createSuccess(result);
        } catch (Exception e) {
            logger.error("获取流程列表失败", e);
            response.status(500);
            return createError(500, "获取流程列表失败: " + e.getMessage());
        }
    }

    public String getFlow(Request request, Response response) {
        try {
            String flowId = request.params(":flowId");
            AlarmFlow flow = flowService.getFlow(flowId);
            if (flow == null) {
                response.status(404);
                return createError(404, "流程不存在");
            }
            response.status(200);
            return createSuccess(flow.toMap());
        } catch (Exception e) {
            logger.error("获取流程详情失败", e);
            response.status(500);
            return createError(500, "获取流程详情失败: " + e.getMessage());
        }
    }

    public String createFlow(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            AlarmFlow flow = buildFlowFromBody(body, null);
            AlarmFlow saved = flowService.saveFlow(flow);
            if (saved == null) {
                response.status(500);
                return createError(500, "创建流程失败");
            }
            response.status(201);
            return createSuccess(saved.toMap());
        } catch (Exception e) {
            logger.error("创建流程失败", e);
            response.status(500);
            return createError(500, "创建流程失败: " + e.getMessage());
        }
    }

    public String updateFlow(Request request, Response response) {
        try {
            String flowId = request.params(":flowId");
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            AlarmFlow flow = buildFlowFromBody(body, flowId);
            AlarmFlow saved = flowService.saveFlow(flow);
            if (saved == null) {
                response.status(500);
                return createError(500, "更新流程失败");
            }
            response.status(200);
            return createSuccess(saved.toMap());
        } catch (Exception e) {
            logger.error("更新流程失败", e);
            response.status(500);
            return createError(500, "更新流程失败: " + e.getMessage());
        }
    }

    public String deleteFlow(Request request, Response response) {
        try {
            String flowId = request.params(":flowId");
            boolean removed = flowService.deleteFlow(flowId);
            if (!removed) {
                response.status(404);
                return createError(404, "流程不存在或删除失败");
            }
            response.status(204);
            return "";
        } catch (Exception e) {
            logger.error("删除流程失败", e);
            response.status(500);
            return createError(500, "删除流程失败: " + e.getMessage());
        }
    }

    /**
     * 仅做语法验证的测试执行
     */
    public String testFlow(Request request, Response response) {
        try {
            String flowId = request.params(":flowId");
            AlarmFlow flow = flowService.getFlow(flowId);
            if (flow == null) {
                response.status(404);
                return createError(404, "流程不存在");
            }
            FlowDefinition definition = flowService.toDefinition(flow);
            // 使用空执行器仅验证解析不会抛错
            FlowExecutor executor = new FlowExecutor();
            executor.execute(definition, new FlowContext());
            response.status(200);
            return createSuccess("测试执行完成（未实际调用节点处理器）");
        } catch (Exception e) {
            logger.error("测试流程执行失败", e);
            response.status(500);
            return createError(500, "测试流程执行失败: " + e.getMessage());
        }
    }

    private AlarmFlow buildFlowFromBody(Map<String, Object> body, String flowId) throws Exception {
        AlarmFlow flow = new AlarmFlow();
        if (flowId != null) {
            flow.setFlowId(flowId);
        } else if (body.get("flowId") instanceof String) {
            flow.setFlowId((String) body.get("flowId"));
        }
        flow.setName((String) body.get("name"));
        flow.setDescription((String) body.get("description"));
        flow.setFlowType(body.get("flowType") instanceof String ? (String) body.get("flowType") : "alarm");
        flow.setDefault(body.get("isDefault") != null && (Boolean) body.get("isDefault"));
        flow.setEnabled(body.get("enabled") == null || (Boolean) body.get("enabled"));

        // 序列化节点与连接
        flow.setNodes(objectMapper.writeValueAsString(body.get("nodes")));
        flow.setConnections(objectMapper.writeValueAsString(body.get("connections")));
        return flow;
    }

    private String createSuccess(Object data) throws Exception {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 0);
        res.put("message", "success");
        res.put("data", data);
        return objectMapper.writeValueAsString(res);
    }

    private String createError(int code, String message) {
        try {
            Map<String, Object> res = new HashMap<>();
            res.put("code", code);
            res.put("message", message);
            res.put("data", null);
            return objectMapper.writeValueAsString(res);
        } catch (Exception e) {
            return "{\"code\":" + code + ",\"message\":\"" + message + "\"}";
        }
    }
}
