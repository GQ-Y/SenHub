package com.digital.video.gateway.api;

import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.database.Database;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * 安装向导接口
 * GET  /api/setup/status  — 返回安装状态
 * POST /api/setup/install — 执行初始化（创建管理员用户 + 写 install.lock）
 */
public class SetupController {
    private static final Logger logger = LoggerFactory.getLogger(SetupController.class);
    private final Database database;
    private final Config config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SetupController(Database database, Config config) {
        this.database = database;
        this.config = config;
    }

    private String getLockPath() {
        String vgwHome = System.getenv("VGW_HOME");
        if (vgwHome == null || vgwHome.isEmpty()) vgwHome = System.getProperty("user.dir");
        return vgwHome + "/data/install.lock";
    }

    /**
     * GET /api/setup/status
     * 返回 { "installed": true/false }
     */
    public void getStatus(Context ctx) {
        boolean installed = new File(getLockPath()).exists();
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "ok");
        Map<String, Object> data = new HashMap<>();
        data.put("installed", installed);
        resp.put("data", data);
        ctx.status(200).contentType("application/json");
        try {
            ctx.result(objectMapper.writeValueAsString(resp));
        } catch (Exception e) {
            ctx.result("{\"code\":200,\"message\":\"ok\",\"data\":{\"installed\":" + installed + "}}");
        }
    }

    /**
     * POST /api/setup/install
     * 请求体: { "username": "admin", "password": "xxx", "confirmPassword": "xxx" }
     * 步骤: 校验 → 创建管理员用户 → 初始化默认系统配置 → 写 install.lock
     */
    @SuppressWarnings("unchecked")
    public void install(Context ctx) {
        String lockPath = getLockPath();
        if (new File(lockPath).exists()) {
            ctx.status(409).contentType("application/json");
            ctx.result("{\"code\":409,\"message\":\"系统已经完成初始化，无需重复安装\",\"data\":null}");
            return;
        }

        Map<String, Object> body;
        try {
            body = objectMapper.readValue(ctx.body(), Map.class);
        } catch (Exception e) {
            ctx.status(400).contentType("application/json");
            ctx.result("{\"code\":400,\"message\":\"请求体格式错误\",\"data\":null}");
            return;
        }

        String username = (String) body.getOrDefault("username", "");
        String password = (String) body.getOrDefault("password", "");
        String confirmPassword = (String) body.getOrDefault("confirmPassword", "");

        // 校验
        if (username == null || username.trim().isEmpty()) {
            sendError(ctx, 400, "用户名不能为空");
            return;
        }
        if (username.trim().length() < 2 || username.trim().length() > 32) {
            sendError(ctx, 400, "用户名长度须在 2-32 个字符之间");
            return;
        }
        if (password == null || password.isEmpty()) {
            sendError(ctx, 400, "密码不能为空");
            return;
        }
        if (password.length() < 6) {
            sendError(ctx, 400, "密码长度至少 6 位");
            return;
        }
        if (!password.equals(confirmPassword)) {
            sendError(ctx, 400, "两次输入的密码不一致");
            return;
        }

        try {
            // 创建管理员用户（如已存在则更新密码）
            String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());
            if (database.userExists(username.trim())) {
                database.updateUserPassword(username.trim(), passwordHash);
                logger.info("安装向导：已更新管理员密码: {}", username.trim());
            } else {
                database.createUser(username.trim(), passwordHash);
                logger.info("安装向导：已创建管理员用户: {}", username.trim());
            }

            // 写入 install.lock 文件
            File lockFile = new File(lockPath);
            lockFile.getParentFile().mkdirs();
            try (FileWriter fw = new FileWriter(lockFile)) {
                fw.write(new java.util.Date().toString());
            }
            logger.info("安装向导：已写入安装锁文件: {}", lockPath);

            // 返回成功
            Map<String, Object> resp = new HashMap<>();
            resp.put("code", 200);
            resp.put("message", "安装完成，系统即将重启，请稍候几秒后刷新页面登录");
            resp.put("data", null);
            ctx.status(200).contentType("application/json");
            ctx.result(objectMapper.writeValueAsString(resp));

            // 响应发送后异步重启：systemd Restart=on-failure 会自动拉起进入完整模式
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {}
                logger.info("安装向导完成，触发进程重启以进入完整运行模式...");
                System.exit(0);
            }, "setup-restart").start();

        } catch (Exception e) {
            logger.error("安装向导执行失败", e);
            sendError(ctx, 500, "安装失败: " + e.getMessage());
        }
    }

    private void sendError(Context ctx, int status, String message) {
        ctx.status(status).contentType("application/json");
        try {
            Map<String, Object> resp = new HashMap<>();
            resp.put("code", status);
            resp.put("message", message);
            resp.put("data", null);
            ctx.result(objectMapper.writeValueAsString(resp));
        } catch (Exception e) {
            ctx.result("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
        }
    }
}
