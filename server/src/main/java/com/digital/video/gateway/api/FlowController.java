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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
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
     * 异步触发流程测试。支持 multipart/form-data 上传自定义参数：
     *   eventName  - 事件名称（中文），如"反光衣检测"
     *   alarmType  - 报警类型 key，如 VEST_DETECTION
     *   deviceId   - 模拟设备ID
     *   deviceIp   - 模拟设备IP
     *   image      - 自定义抓拍图片文件（可选，未上传则使用默认测试图片）
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

            // 解析 multipart 或 JSON 请求
            String eventName = null;
            String alarmType = null;
            String deviceId = null;
            String deviceIp = null;
            byte[] imageBytes = null;
            String imageFileName = null;

            String contentType = request.contentType();
            if (contentType != null && contentType.contains("multipart/form-data")) {
                request.attribute("org.eclipse.jetty.multipartConfig",
                        new MultipartConfigElement(System.getProperty("java.io.tmpdir"), 10_000_000, 10_000_000, 1_000_000));

                eventName = getPartValue(request, "eventName");
                alarmType = getPartValue(request, "alarmType");
                deviceId = getPartValue(request, "deviceId");
                deviceIp = getPartValue(request, "deviceIp");

                Part imagePart = request.raw().getPart("image");
                if (imagePart != null && imagePart.getSize() > 0) {
                    try (InputStream is = imagePart.getInputStream()) {
                        imageBytes = is.readAllBytes();
                    }
                    String submitted = imagePart.getSubmittedFileName();
                    imageFileName = (submitted != null && !submitted.isBlank()) ? submitted : "upload.jpg";
                }
            } else {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
                    eventName = body.get("eventName") instanceof String ? (String) body.get("eventName") : null;
                    alarmType = body.get("alarmType") instanceof String ? (String) body.get("alarmType") : null;
                    deviceId = body.get("deviceId") instanceof String ? (String) body.get("deviceId") : null;
                    deviceIp = body.get("deviceIp") instanceof String ? (String) body.get("deviceIp") : null;
                } catch (Exception ignored) {
                }
            }

            if (alarmType == null || alarmType.isBlank()) alarmType = "VEST_DETECTION";
            if (eventName == null || eventName.isBlank()) eventName = "反光衣检测";
            if (deviceId == null || deviceId.isBlank()) deviceId = "test-camera-001";
            if (deviceIp == null || deviceIp.isBlank()) deviceIp = "192.168.1.100";

            FlowDefinition definition = flowService.toDefinition(flow);
            FlowContext context = buildTestContext(alarmType, eventName, deviceId, deviceIp);

            if (imageBytes != null) {
                prepareUploadedImage(context, imageBytes, imageFileName);
            } else {
                prepareDefaultTestImage(context);
            }

            final String logAlarmType = alarmType;
            logger.info("异步触发测试流程: flowId={}, alarmType={}, eventName={}, deviceId={}",
                    flowId, alarmType, eventName, deviceId);

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
            result.put("alarmType", logAlarmType);
            result.put("eventName", eventName);

            response.status(200);
            return createSuccess(result);
        } catch (Exception e) {
            logger.error("触发测试流程失败", e);
            response.status(500);
            return createError(500, "触发测试流程失败: " + e.getMessage());
        }
    }

    private String getPartValue(Request request, String name) {
        try {
            Part part = request.raw().getPart(name);
            if (part != null && part.getSize() > 0 && part.getSize() < 10000) {
                try (InputStream is = part.getInputStream()) {
                    return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 构建模拟的报警上下文（支持自定义参数）
     */
    private FlowContext buildTestContext(String alarmType, String eventName, String deviceId, String deviceIp) {
        FlowContext ctx = new FlowContext();
        ctx.setDeviceId(deviceId);
        ctx.setAssemblyId("test-assembly");
        ctx.setAlarmType(alarmType);

        ctx.putVariable("alarmType", alarmType);
        ctx.putVariable("eventKey", alarmType);
        ctx.putVariable("eventNameZh", eventName);
        ctx.putVariable("eventNameEn", alarmType);
        ctx.putVariable("category", "vca");
        ctx.putVariable("originalAlarmType", alarmType);

        Map<String, Object> payload = new HashMap<>();
        payload.put("alarmMessage", "[测试] " + eventName);
        payload.put("channel", 0);
        payload.put("event_key", alarmType);
        payload.put("event_id", 9999);
        payload.put("event_name_zh", eventName);
        payload.put("event_name_en", alarmType);
        payload.put("test", true);

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        payload.put("timestamp", ts);

        Map<String, Object> alarmData = new HashMap<>();
        alarmData.put("deviceId", deviceId);
        alarmData.put("deviceName", "测试摄像头");
        alarmData.put("deviceIp", deviceIp);
        alarmData.put("channel", 0);
        alarmData.put("alarmType", alarmType);
        alarmData.put("alarmMessage", "[测试] " + eventName);
        alarmData.put("timestamp", ts);
        alarmData.put("test", true);
        payload.put("alarmData", alarmData);

        ctx.setPayload(payload);
        return ctx;
    }

    /**
     * 将前端上传的图片保存为临时文件，设置到 context 的 capturePath
     */
    private void prepareUploadedImage(FlowContext context, byte[] imageBytes, String fileName) {
        try {
            String ext = ".jpg";
            if (fileName != null) {
                String lower = fileName.toLowerCase();
                if (lower.endsWith(".png")) ext = ".png";
                else if (lower.endsWith(".gif")) ext = ".gif";
                else if (lower.endsWith(".webp")) ext = ".webp";
                else if (lower.endsWith(".bmp")) ext = ".bmp";
            }
            Path tempFile = Files.createTempFile("test_capture_", ext);
            Files.write(tempFile, imageBytes);
            context.putVariable("capturePath", tempFile.toAbsolutePath().toString());
            context.putVariable("captureFileName", "test_capture" + ext);
            logger.info("上传测试图片已保存: {} ({} bytes)", tempFile, imageBytes.length);
        } catch (Exception e) {
            logger.warn("保存上传测试图片失败: {}", e.getMessage());
        }
    }

    /**
     * 使用默认测试图片（test/fanguangyi.png）
     */
    private void prepareDefaultTestImage(FlowContext context) {
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
            logger.warn("默认测试图片 fanguangyi.png 未找到，抓拍节点将跳过");
            return;
        }

        try {
            Path tempFile = Files.createTempFile("test_capture_", ".png");
            Files.copy(imageFile.toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            context.putVariable("capturePath", tempFile.toAbsolutePath().toString());
            context.putVariable("captureFileName", "test_capture.png");
            logger.info("默认测试图片已准备: {}", tempFile);
        } catch (Exception e) {
            logger.warn("复制默认测试图片失败: {}", e.getMessage());
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
