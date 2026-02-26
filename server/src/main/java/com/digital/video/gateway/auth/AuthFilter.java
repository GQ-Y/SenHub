package com.digital.video.gateway.auth;

import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT认证过滤器（Javalin before handler）
 */
public class AuthFilter {
    private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);

    public static void handle(Context ctx) {
        if ("OPTIONS".equals(ctx.method().name())) {
            return;
        }

        String path = ctx.path();

        if (path == null || !path.startsWith("/api")) {
            return;
        }

        if (path.startsWith("/api/auth/login") || "/api/auth/login".equals(path)) {
            return;
        }

        if (path != null && path.contains("/radar/stream")) {
            logger.debug("放行雷达 WebSocket 路径（免 Token）: {}", path);
            return;
        }

        String token = null;
        String authHeader = ctx.header("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (path != null && ctx.queryParam("token") != null && isFileServePath(path)) {
            // 图片/文件直连（img src、a href）无法带 Header，支持通过 query token 鉴权
            token = ctx.queryParam("token");
        }
        if (token == null) {
            logger.warn("未授权的请求: {}", path);
            throw new HaltException(401, "{\"code\":401,\"errorCode\":\"MISSING_TOKEN\",\"message\":\"Missing or invalid Authorization header\",\"data\":null}");
        }

        String username = JwtUtil.verifyToken(token);
        if (username == null) {
            logger.warn("无效的token: {}", path);
            throw new HaltException(401, "{\"code\":401,\"errorCode\":\"TOKEN_INVALID\",\"message\":\"Token invalid or expired\",\"data\":null}");
        }

        ctx.attribute("username", username);
        ctx.attribute("token", token);
    }

    /** 是否为通过 query token 鉴权的文件直连路径（img/src、a/href 无法带 Authorization） */
    private static boolean isFileServePath(String path) {
        if (path == null) return false;
        return path.contains("/snapshot/file")
                || path.contains("/playback/file")
                || path.contains("/export/file")
                || (path.contains("recording-tasks") && path.contains("/file"));
    }
}
