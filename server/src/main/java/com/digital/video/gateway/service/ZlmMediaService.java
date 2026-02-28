package com.digital.video.gateway.service;

import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_EVENTS;
import com.aizuda.zlm4j.structure.MK_INI;
import com.digital.video.gateway.config.Config;
import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;

/**
 * ZLMediaKit 内嵌流媒体服务：直播拉流、回放转码等。
 * 根据 config.zlm.enabled 可选启动，失败时仅禁用 ZLM 能力，不影响现有 SDK 功能。
 */
public class ZlmMediaService {
    private static final Logger logger = LoggerFactory.getLogger(ZlmMediaService.class);

    public static final String RECORD_BASE_PATH = "./storage/zlm_records";
    public static final int RECORD_FILE_SECOND = 30;

    /** 解析后的录像根目录（绝对路径），供 RecorderService 查找分段；在 start() 时设置 */
    private static volatile String recordBasePathAbsolute;

    private final Config.ZlmConfig config;
    private ZLMApi api;
    private MK_INI mkIni;
    /** 保持对 MK_EVENTS 的强引用，防止 JNA 回调被 GC 回收 */
    private MK_EVENTS mkEvents;
    private boolean started;

    public ZlmMediaService(Config.ZlmConfig config) {
        this.config = config != null ? config : defaultConfig();
    }

    private static Config.ZlmConfig defaultConfig() {
        Config.ZlmConfig c = new Config.ZlmConfig();
        c.setEnabled(true);
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
     * 按 zlm4j 示例：先 mk_ini_default + 设置全局协议参数 → 再 mk_env_init1 初始化
     */
    @SuppressWarnings("deprecation")
    public void start() {
        if (!isEnabled()) {
            logger.info("ZLM 未启用，跳过启动");
            return;
        }
        try {
            api = Native.load("mk_api", ZLMApi.class);

            // 1. 先获取默认 INI 并设置全局协议配置（与 zlm4j demo 一致）
            mkIni = api.mk_ini_default();
            if (config.getMediaServerId() != null && !config.getMediaServerId().isEmpty()) {
                api.mk_ini_set_option(mkIni, "general.mediaServerId", config.getMediaServerId());
            }
            api.mk_ini_set_option_int(mkIni, "protocol.enable_rtsp", 1);
            api.mk_ini_set_option_int(mkIni, "protocol.enable_rtmp", 1);
            api.mk_ini_set_option_int(mkIni, "protocol.enable_hls", 1);
            api.mk_ini_set_option_int(mkIni, "protocol.enable_ts", 1);
            api.mk_ini_set_option_int(mkIni, "protocol.enable_fmp4", 1);
            api.mk_ini_set_option_int(mkIni, "protocol.enable_audio", 1);
            api.mk_ini_set_option_int(mkIni, "protocol.auto_close", 0);
            api.mk_ini_set_option_int(mkIni, "general.streamNoneReaderDelayMS", 30000);
            api.mk_ini_set_option_int(mkIni, "general.maxStreamWaitMS", 5000);

            recordBasePathAbsolute = Paths.get(RECORD_BASE_PATH).toAbsolutePath().normalize().toString();
            File recordDir = new File(recordBasePathAbsolute);
            if (!recordDir.exists()) {
                if (recordDir.mkdirs()) {
                    logger.info("ZLM 录像目录已创建: {}", recordBasePathAbsolute);
                } else {
                    logger.warn("ZLM 录像目录创建失败: {}", recordBasePathAbsolute);
                }
            }
            // protocol.mp4_save_path: ZLM 实际写 MP4 分段的目录（[protocol] 节配置）
            // protocol.mp4_max_second: 每个 MP4 分段的最大时长（秒）
            // 注意：record.filePath / record.fileSecond 是"点播"模块配置，不控制实时 MP4 录像
            api.mk_ini_set_option(mkIni, "protocol.mp4_save_path", recordBasePathAbsolute);
            api.mk_ini_set_option_int(mkIni, "protocol.mp4_max_second", RECORD_FILE_SECOND);
            api.mk_ini_set_option_int(mkIni, "protocol.mp4_as_player", 0);
            api.mk_ini_set_option_int(mkIni, "record.fastStart", 1);
            api.mk_ini_set_option_int(mkIni, "record.fileBufSize", 65536);
            logger.info("ZLM 录像配置: mp4_save_path={}, mp4_max_second={}", recordBasePathAbsolute, RECORD_FILE_SECOND);

            // 2. 再调用 mk_env_init1 初始化（使用已设置的 INI 配置）
            api.mk_env_init1(0, 2, 1, null, 1, 0, null, 0, null, null);

            // 3. 注册全局事件回调（保持强引用防止 GC）
            mkEvents = new MK_EVENTS();
            mkEvents.on_mk_media_changed = (regist, sender) ->
                    logger.info("ZLM 流状态变化: regist={}, sender={}", regist, sender);
            api.mk_events_listen(mkEvents);

            // 4. 启动各协议端口
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
            mkEvents = null;
            started = false;
            logger.info("ZLM 内嵌服务已停止");
        } catch (Throwable e) {
            logger.warn("ZLM 停止时异常: {}", e.getMessage());
        }
    }

    public ZLMApi getApi() {
        return api;
    }

    /**
     * 返回 ZLM 录像根目录的绝对路径，供 RecorderService 查找 MP4 分段。
     * 若 ZLM 尚未启动则返回对 RECORD_BASE_PATH 做 getAbsolutePath 的结果。
     */
    public static String getRecordBasePathAbsolute() {
        if (recordBasePathAbsolute != null && !recordBasePathAbsolute.isEmpty()) {
            return recordBasePathAbsolute;
        }
        return Paths.get(RECORD_BASE_PATH).toAbsolutePath().normalize().toString();
    }
}
