package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.service.RadarTestService;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * 雷达控制与测试接口
 */
public class RadarController {
    private final RadarTestService radarTestService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RadarController(RadarTestService radarTestService) {
        this.radarTestService = radarTestService;
    }

    /**
     * 测试雷达连接
     * POST /api/radar/test
     */
    public Object testConnection(Request request, Response response) {
        try {
            String ip = request.queryParams("ip") != null ? request.queryParams("ip") : "192.168.1.115";
            String resultText = radarTestService.testConnection(ip);

            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", resultText);

            response.status(200);
            response.type("application/json");
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            response.status(500);
            return "{\"code\":500,\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
