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

        String path = request.pathInfo();
        
        // 允许登录接口和公开接口
        if (path != null && (path.startsWith("/api/auth/login") || path.equals("/api/auth/login"))) {
            return;
        }

        // 允许静态文件访问（图片、音频、视频等，供前端直接播放/展示）
        if (path != null && path.startsWith("/api/static/")) {
            return;
        }
        
        // 允许WebSocket升级请求（WebSocket连接不需要JWT认证）
        // WebSocket升级请求的路径通常是 /api/radar/stream
        // 检查路径和Upgrade头
        if (path != null && path.contains("/radar/stream")) {
            String upgradeHeader = request.headers("Upgrade");
            String connectionHeader = request.headers("Connection");
            
            // 调试日志
            logger.debug("检查WebSocket请求: path={}, Upgrade={}, Connection={}", 
                    path, upgradeHeader, connectionHeader);
            
            // WebSocket升级请求会有Upgrade: websocket和Connection: Upgrade头
            // 或者直接允许所有到/radar/stream的请求（因为这是WebSocket专用端点）
            if (upgradeHeader != null && upgradeHeader.equalsIgnoreCase("websocket")) {
                logger.info("允许WebSocket升级请求: {}", path);
                return; // 允许WebSocket升级请求通过
            }
            // 如果没有Upgrade头，但路径匹配，也允许（可能是某些客户端实现不同）
            if (path.equals("/api/radar/stream") || path.endsWith("/radar/stream")) {
                logger.info("允许WebSocket路径请求（无Upgrade头）: {}", path);
                return;
            }
        }

        // 允许视频文件访问（免token验证，因为video标签无法携带Authorization header）
        // 注意：这里假设视频文件路径已经通过设备ID验证，具有一定的安全性
        if (path != null && path.contains("/video") && request.queryParams("file") != null) {
            // 视频文件访问免token验证，但需要通过文件名验证设备ID
            return;
        }

        // 验证JWT token
        String authHeader = request.headers("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.status(401);
            response.type("application/json");
            response.body("{\"code\":401,\"errorCode\":\"MISSING_TOKEN\",\"message\":\"Missing or invalid Authorization header\",\"data\":null}");
            logger.warn("未授权的请求: {}", path);
            return;
        }

        String token = authHeader.substring(7);
        String username = JwtUtil.verifyToken(token);

        if (username == null) {
            response.status(401);
            response.type("application/json");
            response.body("{\"code\":401,\"errorCode\":\"TOKEN_INVALID\",\"message\":\"Token invalid or expired\",\"data\":null}");
            logger.warn("无效的token: {}", path);
            return;
        }

        // 将用户名存储到请求属性中，供后续使用
        request.attribute("username", username);
    }
}
