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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final FlowExecutor flowExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FlowController(FlowService flowService) {
        this.flowService = flowService;
        this.flowExecutor = null;
    }

    public FlowController(FlowService flowService, FlowExecutor flowExecutor) {
        this.flowService = flowService;
        this.flowExecutor = flowExecutor;
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
     * 异步触发流程测试：模拟一个「未穿反光衣」(VEST_DETECTION) 报警事件，
     * 使用 test/fanguangyi.png 作为抓拍图片。立即返回，流程在后台执行。
     */
    public String testFlow(Request request, Response response) {
        try {
            String flowId = request.params(":flowId");
            AlarmFlow flow = flowService.getFlow(flowId);
            if (flow == null) {
                response.status(404);
                return createError(404, "流程不存在");
            }
            if (flowExecutor == null) {
                response.status(500);
                return createError(500, "流程执行器未初始化，无法测试");
            }

            FlowDefinition definition = flowService.toDefinition(flow);
            FlowContext context = buildTestContext();
            prepareTestImage(context);

            logger.info("异步触发测试流程: flowId={}, alarmType={}", flowId, context.getAlarmType());

            new Thread(() -> {
                try {
                    flowExecutor.execute(definition, context);
                    logger.info("测试流程执行完成: flowId={}", flowId);
                } catch (Exception e) {
                    logger.error("测试流程后台执行失败: flowId={}", flowId, e);
                }
            }, "FlowTest-" + flowId).start();

            Map<String, Object> result = new HashMap<>();
            result.put("message", "流程测试已触发，正在后台执行");
            result.put("flowId", flowId);
            result.put("alarmType", "VEST_DETECTION");

            response.status(200);
            return createSuccess(result);
        } catch (Exception e) {
            logger.error("触发测试流程失败", e);
            response.status(500);
            return createError(500, "触发测试流程失败: " + e.getMessage());
        }
    }

    /**
     * 构建模拟的报警上下文：未穿反光衣事件
     */
    private FlowContext buildTestContext() {
        FlowContext ctx = new FlowContext();
        ctx.setDeviceId("test-camera-001");
        ctx.setAssemblyId("test-assembly");
        ctx.setAlarmType("VEST_DETECTION");

        ctx.putVariable("alarmType", "VEST_DETECTION");
        ctx.putVariable("eventKey", "VEST_DETECTION");
        ctx.putVariable("eventNameZh", "反光衣检测");
        ctx.putVariable("eventNameEn", "Vest Detection");
        ctx.putVariable("category", "vca");
        ctx.putVariable("originalAlarmType", "VEST_DETECTION");

        Map<String, Object> payload = new HashMap<>();
        payload.put("alarmMessage", "[测试] 检测到未穿反光衣");
        payload.put("channel", 0);
        payload.put("event_key", "VEST_DETECTION");
        payload.put("event_id", 1231);
        payload.put("event_name_zh", "反光衣检测");
        payload.put("event_name_en", "Vest Detection");
        payload.put("test", true);

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        payload.put("timestamp", ts);

        Map<String, Object> alarmData = new HashMap<>();
        alarmData.put("deviceId", "test-camera-001");
        alarmData.put("deviceName", "测试摄像头");
        alarmData.put("deviceIp", "192.168.1.100");
        alarmData.put("channel", 0);
        alarmData.put("alarmType", "VEST_DETECTION");
        alarmData.put("alarmMessage", "[测试] 检测到未穿反光衣");
        alarmData.put("timestamp", ts);
        alarmData.put("test", true);
        payload.put("alarmData", alarmData);

        ctx.setPayload(payload);
        return ctx;
    }

    /**
     * 将 test/fanguangyi.png 复制为临时文件，设置到 context 的 capturePath
     */
    private void prepareTestImage(FlowContext context) {
        String[] searchPaths = {
            "test/fanguangyi.png",
            "server/test/fanguangyi.png",
            "../test/fanguangyi.png",
        };

        File imageFile = null;
        for (String p : searchPaths) {
            File f = new File(p);
            if (f.exists() && f.isFile()) {
                imageFile = f;
                break;
            }
        }

        if (imageFile == null) {
            logger.warn("测试图片 fanguangyi.png 未找到，抓拍节点将跳过");
            return;
        }

        try {
            Path tempFile = Files.createTempFile("test_capture_", ".png");
            Files.copy(imageFile.toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            String tempPath = tempFile.toAbsolutePath().toString();
            context.putVariable("capturePath", tempPath);
            context.putVariable("captureFileName", "test_vest_detection.png");
            logger.info("测试图片已准备: {} -> {}", imageFile.getAbsolutePath(), tempPath);
        } catch (Exception e) {
            logger.warn("复制测试图片失败: {}", e.getMessage());
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
