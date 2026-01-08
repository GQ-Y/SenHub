package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.auth.JwtUtil;
import com.digital.video.gateway.auth.PasswordUtil;
import com.digital.video.gateway.database.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final Database database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthController(Database database) {
        this.database = database;
    }

    /**
     * 登录接口
     * POST /api/auth/login
     */
    public String login(Request request, Response response) {
        try {
            Map<String, String> body = objectMapper.readValue(request.body(), Map.class);
            String username = body.get("username");
            String password = body.get("password");

            if (username == null || password == null) {
                response.status(400);
                return createErrorResponse(400, "用户名和密码不能为空");
            }

            // 验证用户
            String passwordHash = database.getUserPasswordHash(username);
            if (passwordHash == null || !PasswordUtil.verifyPassword(password, passwordHash)) {
                response.status(401);
                return createErrorResponse(401, "用户名或密码错误");
            }

            // 生成JWT token
            String token = JwtUtil.generateToken(username);
            if (token == null) {
                response.status(500);
                return createErrorResponse(500, "生成token失败");
            }

            // 返回token
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("expiresIn", JwtUtil.getExpirationTime());
            data.put("username", username);

            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("登录失败", e);
            response.status(500);
            return createErrorResponse(500, "登录失败: " + e.getMessage());
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
