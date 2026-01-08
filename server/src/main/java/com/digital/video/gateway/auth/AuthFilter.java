package com.digital.video.gateway.auth;

import spark.Filter;
import spark.Request;
import spark.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT认证过滤器
 */
public class AuthFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);

    @Override
    public void handle(Request request, Response response) throws Exception {
        // 允许OPTIONS请求（CORS预检）
        if (request.requestMethod().equals("OPTIONS")) {
            return;
        }

        // 允许登录接口和公开接口
        String path = request.pathInfo();
        if (path.startsWith("/api/auth/login") || path.equals("/api/auth/login")) {
            return;
        }

        // 允许视频文件访问（免token验证，因为video标签无法携带Authorization header）
        // 注意：这里假设视频文件路径已经通过设备ID验证，具有一定的安全性
        if (path.contains("/video") && request.queryParams("file") != null) {
            // 视频文件访问免token验证，但需要通过文件名验证设备ID
            return;
        }

        // 验证JWT token
        String authHeader = request.headers("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.status(401);
            response.type("application/json");
            response.body("{\"code\":401,\"message\":\"Unauthorized\",\"data\":null}");
            logger.warn("未授权的请求: {}", path);
            return;
        }

        String token = authHeader.substring(7);
        String username = JwtUtil.verifyToken(token);
        
        if (username == null) {
            response.status(401);
            response.type("application/json");
            response.body("{\"code\":401,\"message\":\"Invalid token\",\"data\":null}");
            logger.warn("无效的token: {}", path);
            return;
        }

        // 将用户名存储到请求属性中，供后续使用
        request.attribute("username", username);
    }
}
