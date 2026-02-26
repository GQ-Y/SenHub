package com.digital.video.gateway.auth;

import spark.Filter;
import spark.Request;
import spark.Response;
import spark.Spark;
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

        // 非 /api 请求直接放行（Web 面板、静态资源等，不做 JWT 校验）
        if (path == null || !path.startsWith("/api")) {
            return;
        }

        // 仅允许登录接口免校验
        if (path.startsWith("/api/auth/login") || path.equals("/api/auth/login")) {
            return;
        }

        // WebSocket 升级请求免校验（浏览器 WebSocket 无法携带自定义 Header）
        if (path != null && path.contains("/radar/stream")) {
            String upgradeHeader = request.headers("Upgrade");
            if (upgradeHeader != null && upgradeHeader.equalsIgnoreCase("websocket")) {
                return;
            }
        }

        // 其余 /api 接口均需 JWT 校验
        // 验证JWT token（优先 Header，播放文件接口支持 query 传 token，便于 <video src> 直链）
        String token = null;
        String authHeader = request.headers("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (path != null && path.contains("/playback/file") && request.queryParams("token") != null) {
            token = request.queryParams("token");
        }
        if (token == null) {
            logger.warn("未授权的请求: {}", path);
            response.type("application/json");
            Spark.halt(401, "{\"code\":401,\"errorCode\":\"MISSING_TOKEN\",\"message\":\"Missing or invalid Authorization header\",\"data\":null}");
            return;
        }

        String username = JwtUtil.verifyToken(token);

        if (username == null) {
            logger.warn("无效的token: {}", path);
            response.type("application/json");
            Spark.halt(401, "{\"code\":401,\"errorCode\":\"TOKEN_INVALID\",\"message\":\"Token invalid or expired\",\"data\":null}");
            return;
        }

        // 将用户名存储到请求属性中，供后续使用
        request.attribute("username", username);
    }
}
