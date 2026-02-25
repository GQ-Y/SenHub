package com.digital.video.gateway.service;

import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_EVENTS;
import com.aizuda.zlm4j.structure.MK_INI;
import com.digital.video.gateway.config.Config;
import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZLMediaKit 内嵌流媒体服务：直播拉流、回放转码等。
 * 根据 config.zlm.enabled 可选启动，失败时仅禁用 ZLM 能力，不影响现有 SDK 功能。
 */
public class ZlmMediaService {
    private static final Logger logger = LoggerFactory.getLogger(ZlmMediaService.class);

    private final Config.ZlmConfig config;
    private ZLMApi api;
    private MK_INI mkIni;
    private boolean started;

    public ZlmMediaService(Config.ZlmConfig config) {
        this.config = config != null ? config : defaultConfig();
    }

    private static Config.ZlmConfig defaultConfig() {
        Config.ZlmConfig c = new Config.ZlmConfig();
        c.setEnabled(true);  // 未读取到 config 时也默认启动 ZLM
        return c;
    }

    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    public Config.ZlmConfig getConfig() {
        return config;
    }

    public boolean isStarted() {
        return started;
    }

    /**
     * 启动 ZLM 内嵌服务（HTTP/RTSP/RTMP 端口）
     */
    public void start() {
        if (!isEnabled()) {
            logger.info("ZLM 未启用，跳过启动");
            return;
        }
        try {
            api = Native.load("mk_api", ZLMApi.class);
            // mk_env_init2(logLevel, logMask, logFileDay, logDir, maxLogSize, logLevelFile, ...)
            api.mk_env_init2(1, 1, 1, null, 0, 0, null, 0, null, null);
            mkIni = api.mk_ini_default();
            if (config.getMediaServerId() != null && !config.getMediaServerId().isEmpty()) {
                api.mk_ini_set_option(mkIni, "general.mediaServerId", config.getMediaServerId());
            }
            MK_EVENTS events = new MK_EVENTS();
            events.on_mk_media_changed = (regist, sender) ->
                    logger.debug("ZLM 流状态变化: regist={}, sender={}", regist, sender);
            api.mk_events_listen(events);

            short httpPort = api.mk_http_server_start((short) config.getHttpPort(), 0);
            if (httpPort <= 0) {
                logger.warn("ZLM HTTP 服务启动失败，端口可能占用: {}", config.getHttpPort());
                return;
            }
            short rtspPort = api.mk_rtsp_server_start((short) config.getRtspPort(), 0);
            if (rtspPort <= 0) {
                logger.warn("ZLM RTSP 服务启动失败: {}", config.getRtspPort());
            }
            short rtmpPort = api.mk_rtmp_server_start((short) config.getRtmpPort(), 0);
            if (rtmpPort <= 0) {
                logger.warn("ZLM RTMP 服务启动失败: {}", config.getRtmpPort());
            }
            started = true;
            logger.info("ZLM 内嵌服务已启动: http={}, rtsp={}, rtmp={}", httpPort, rtspPort, rtmpPort);
        } catch (Throwable e) {
            logger.error("ZLM 启动失败，将禁用直播/回放转码能力: {}", e.getMessage(), e);
            started = false;
        }
    }

    /**
     * 停止所有 ZLM 服务并释放资源
     */
    public void stop() {
        if (!started || api == null) return;
        try {
            api.mk_stop_all_server();
            if (mkIni != null) {
                api.mk_ini_release(mkIni);
                mkIni = null;
            }
            started = false;
            logger.info("ZLM 内嵌服务已停止");
        } catch (Throwable e) {
            logger.warn("ZLM 停止时异常: {}", e.getMessage());
        }
    }

    public ZLMApi getApi() {
        return api;
    }
}
