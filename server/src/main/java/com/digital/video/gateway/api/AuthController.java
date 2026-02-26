package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.auth.JwtUtil;
import com.digital.video.gateway.auth.PasswordUtil;
import com.digital.video.gateway.database.Database;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public void login(Context ctx) {
        try {
            Map<String, String> body = objectMapper.readValue(ctx.body(), Map.class);
            String username = body.get("username");
            String password = body.get("password");

            if (username == null || password == null) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "用户名和密码不能为空"));
                return;
            }

            String passwordHash = database.getUserPasswordHash(username);
            if (passwordHash == null || !PasswordUtil.verifyPassword(password, passwordHash)) {
                ctx.status(401);
                ctx.result(createErrorResponse(401, "用户名或密码错误"));
                return;
            }

            String token = JwtUtil.generateToken(username);
            if (token == null) {
                ctx.status(500);
                ctx.result(createErrorResponse(500, "生成token失败"));
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("expiresIn", JwtUtil.getExpirationTime());
            data.put("username", username);

            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(data));
        } catch (Exception e) {
            logger.error("登录失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "登录失败: " + e.getMessage()));
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
