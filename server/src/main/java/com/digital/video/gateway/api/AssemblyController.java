package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.Assembly;
import com.digital.video.gateway.database.AssemblyDevice;
import com.digital.video.gateway.service.AssemblyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 装置管理控制器
 */
public class AssemblyController {
    private static final Logger logger = LoggerFactory.getLogger(AssemblyController.class);
    private final AssemblyService assemblyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssemblyController(AssemblyService assemblyService) {
        this.assemblyService = assemblyService;
    }

    /**
     * 获取装置列表
     * GET /api/assemblies
     */
    public String getAssemblies(Request request, Response response) {
        try {
            String search = request.queryParams("search");
            String status = request.queryParams("status");
            List<Assembly> assemblies = assemblyService.getAssemblies(search, status);

            // 转换为Map列表
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Assembly assembly : assemblies) {
                Map<String, Object> map = assembly.toMap();
                // 获取设备数量
                List<AssemblyDevice> devices = assemblyService.getAssemblyDevices(assembly.getAssemblyId());
                map.put("deviceCount", devices.size());
                result.add(map);
            }

            response.status(200);
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取装置列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取装置列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取装置详情
     * GET /api/assemblies/:id
     */
    public String getAssembly(Request request, Response response) {
        try {
            String assemblyId = request.params(":id");
            Assembly assembly = assemblyService.getAssembly(assemblyId);
            if (assembly == null) {
                response.status(404);
                return createErrorResponse(404, "装置不存在");
            }

            Map<String, Object> map = assembly.toMap();
            List<AssemblyDevice> devices = assemblyService.getAssemblyDevices(assemblyId);
            map.put("devices",
                    devices.stream().map(AssemblyDevice::toMap).collect(java.util.stream.Collectors.toList()));

            response.status(200);
            return createSuccessResponse(map);
        } catch (Exception e) {
            logger.error("获取装置详情失败", e);
            response.status(500);
            return createErrorResponse(500, "获取装置详情失败: " + e.getMessage());
        }
    }

    /**
     * 创建装置
     * POST /api/assemblies
     */
    public String createAssembly(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            Assembly assembly = new Assembly();
            assembly.setName((String) body.get("name"));
            assembly.setDescription((String) body.get("description"));
            assembly.setLocation((String) body.get("location"));

            Object statusObj = body.getOrDefault("status", 1);
            if (statusObj instanceof Number) {
                assembly.setStatus(((Number) statusObj).intValue());
            } else if ("active".equals(statusObj)) {
                assembly.setStatus(1);
            } else {
                assembly.setStatus(0);
            }
            Object ptzObj = body.get("ptzLinkageEnabled");
            assembly.setPtzLinkageEnabled(ptzObj instanceof Boolean ? (Boolean) ptzObj : (ptzObj instanceof Number && ((Number) ptzObj).intValue() != 0));

            Assembly created = assemblyService.createAssembly(assembly);
            if (created == null) {
                response.status(500);
                return createErrorResponse(500, "创建装置失败");
            }

            response.status(201);
            return createSuccessResponse(created.toMap());
        } catch (Exception e) {
            logger.error("创建装置失败", e);
            response.status(500);
            return createErrorResponse(500, "创建装置失败: " + e.getMessage());
        }
    }

    /**
     * 更新装置
     * PUT /api/assemblies/:id
     */
    public String updateAssembly(Request request, Response response) {
        try {
            String assemblyId = request.params(":id");
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            Assembly existing = assemblyService.getAssembly(assemblyId);
            Assembly assembly = new Assembly();
            assembly.setName((String) body.get("name"));
            assembly.setDescription((String) body.get("description"));
            assembly.setLocation((String) body.get("location"));

            Object statusObj = body.get("status");
            if (statusObj instanceof Number) {
                assembly.setStatus(((Number) statusObj).intValue());
            } else if ("active".equals(statusObj)) {
                assembly.setStatus(1);
            } else if (statusObj != null) {
                assembly.setStatus(0);
            }
            if (body.containsKey("ptzLinkageEnabled")) {
                Object ptzObj = body.get("ptzLinkageEnabled");
                assembly.setPtzLinkageEnabled(ptzObj instanceof Boolean ? (Boolean) ptzObj : (ptzObj instanceof Number && ((Number) ptzObj).intValue() != 0));
            } else if (existing != null) {
                assembly.setPtzLinkageEnabled(existing.isPtzLinkageEnabled());
            }

            Assembly updated = assemblyService.updateAssembly(assemblyId, assembly);
            if (updated == null) {
                response.status(404);
                return createErrorResponse(404, "装置不存在");
            }

            response.status(200);
            return createSuccessResponse(updated.toMap());
        } catch (Exception e) {
            logger.error("更新装置失败", e);
            response.status(500);
            return createErrorResponse(500, "更新装置失败: " + e.getMessage());
        }
    }

    /**
     * 删除装置
     * DELETE /api/assemblies/:id
     */
    public String deleteAssembly(Request request, Response response) {
        try {
            String assemblyId = request.params(":id");
            boolean deleted = assemblyService.deleteAssembly(assemblyId);
            if (!deleted) {
                response.status(404);
                return createErrorResponse(404, "装置不存在");
            }

            response.status(200);
            return createSuccessResponse(Map.of("message", "删除成功"));
        } catch (Exception e) {
            logger.error("删除装置失败", e);
            response.status(500);
            return createErrorResponse(500, "删除装置失败: " + e.getMessage());
        }
    }

    /**
     * 添加设备到装置
     * POST /api/assemblies/:id/devices
     */
    public String addDeviceToAssembly(Request request, Response response) {
        try {
            String assemblyId = request.params(":id");
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            String deviceId = (String) body.get("deviceId");
            String role = (String) body.get("role");
            String positionInfo = (String) body.get("positionInfo");

            AssemblyDevice ad = assemblyService.addDeviceToAssembly(assemblyId, deviceId, role, positionInfo);
            if (ad == null) {
                response.status(500);
                return createErrorResponse(500, "添加设备到装置失败");
            }

            response.status(201);
            return createSuccessResponse(ad.toMap());
        } catch (Exception e) {
            logger.error("添加设备到装置失败", e);
            response.status(500);
            return createErrorResponse(500, "添加设备到装置失败: " + e.getMessage());
        }
    }

    /**
     * 从装置移除设备
     * DELETE /api/assemblies/:id/devices/:deviceId
     */
    public String removeDeviceFromAssembly(Request request, Response response) {
        try {
            String assemblyId = request.params(":id");
            String deviceId = request.params(":deviceId");
            boolean removed = assemblyService.removeDeviceFromAssembly(assemblyId, deviceId);
            if (!removed) {
                response.status(404);
                return createErrorResponse(404, "设备不在装置中");
            }

            response.status(200);
            return createSuccessResponse(Map.of("message", "移除成功"));
        } catch (Exception e) {
            logger.error("从装置移除设备失败", e);
            response.status(500);
            return createErrorResponse(500, "从装置移除设备失败: " + e.getMessage());
        }
    }

    /**
     * 获取装置的所有设备
     * GET /api/assemblies/:id/devices
     */
    public String getAssemblyDevices(Request request, Response response) {
        try {
            String assemblyId = request.params(":id");
            List<AssemblyDevice> devices = assemblyService.getAssemblyDevices(assemblyId);
            List<Map<String, Object>> result = devices.stream()
                    .map(AssemblyDevice::toMap)
                    .collect(java.util.stream.Collectors.toList());

            response.status(200);
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取装置设备列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取装置设备列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取设备所属的所有装置
     * GET /api/devices/:deviceId/assemblies
     */
    public String getAssembliesByDevice(Request request, Response response) {
        try {
            String deviceId = request.params(":deviceId");
            List<Assembly> assemblies = assemblyService.getAssembliesByDevice(deviceId);
            List<Map<String, Object>> result = assemblies.stream()
                    .map(Assembly::toMap)
                    .collect(java.util.stream.Collectors.toList());

            response.status(200);
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取设备所属装置列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取设备所属装置列表失败: " + e.getMessage());
        }
    }

    private String createSuccessResponse(Object data) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", data);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("创建响应失败", e);
            return "{\"code\":500,\"message\":\"Internal error\",\"data\":null}";
        }
    }

    private String createErrorResponse(int code, String message) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", code);
            response.put("message", message);
            response.put("data", null);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("创建错误响应失败", e);
            return "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}";
        }
    }
}
