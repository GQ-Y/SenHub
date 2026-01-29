package com.digital.video.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.database.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 配置管理服务
 * 实现混合模式：数据库优先，YAML作为默认值
 */
public class ConfigService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
    private final Database database;
    private final Config defaultConfig;
    private Config currentConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConfigService(Database database, Config defaultConfig) {
        this.database = database;
        this.defaultConfig = defaultConfig;
        loadConfig();
    }

    /**
     * 加载配置（混合模式）
     */
    private void loadConfig() {
        try {
            // 从数据库加载配置
            Map<String, String> dbConfigs = database.getAllConfigs();

            // 如果数据库为空，将YAML配置写入数据库
            if (dbConfigs.isEmpty()) {
                logger.info("数据库配置为空，从YAML初始化配置到数据库");
                saveConfigToDatabase(defaultConfig);
                currentConfig = defaultConfig;
            } else {
                // 从数据库恢复配置到Config对象
                currentConfig = restoreConfigFromDatabase(dbConfigs, defaultConfig);
                logger.info("从数据库加载配置成功");
            }
        } catch (Exception e) {
            logger.error("加载配置失败，使用默认配置", e);
            currentConfig = defaultConfig;
        }
    }

    /**
     * 将配置保存到数据库
     */
    private void saveConfigToDatabase(Config config) {
        try {
            // MQTT配置
            if (config.getMqtt() != null) {
                saveConfig("mqtt.broker", config.getMqtt().getBroker());
                saveConfig("mqtt.client_id", config.getMqtt().getClientId());
                saveConfig("mqtt.username", config.getMqtt().getUsername());
                saveConfig("mqtt.password", config.getMqtt().getPassword());
                saveConfig("mqtt.status_topic", config.getMqtt().getStatusTopic());
                saveConfig("mqtt.command_topic", config.getMqtt().getCommandTopic());
                saveConfig("mqtt.response_topic", config.getMqtt().getResponseTopic());
                saveConfig("mqtt.gateway_status_topic", config.getMqtt().getGatewayStatusTopic());
                saveConfig("mqtt.report_topic_prefix", config.getMqtt().getReportTopicPrefix());
                saveConfig("mqtt.qos", String.valueOf(config.getMqtt().getQos()));
                saveConfig("mqtt.keep_alive", String.valueOf(config.getMqtt().getKeepAlive()));
            }

            // Scanner配置
            if (config.getScanner() != null) {
                saveConfig("scanner.enabled", String.valueOf(config.getScanner().isEnabled()));
                saveConfig("scanner.interval", String.valueOf(config.getScanner().getInterval()));
                saveConfig("scanner.listen_port", String.valueOf(config.getScanner().getListenPort()));
                saveConfig("scanner.listen_ip", config.getScanner().getListenIp());
                saveConfig("scanner.scan_segment", config.getScanner().getScanSegment());
            }

            // Device配置
            if (config.getDevice() != null) {
                saveConfig("device.default_username", config.getDevice().getDefaultUsername());
                saveConfig("device.default_password", config.getDevice().getDefaultPassword());
                saveConfig("device.default_port", String.valueOf(config.getDevice().getDefaultPort()));
                saveConfig("device.http_port", String.valueOf(config.getDevice().getHttpPort()));
                saveConfig("device.rtsp_port", String.valueOf(config.getDevice().getRtspPort()));
                saveConfig("device.login_timeout", String.valueOf(config.getDevice().getLoginTimeout()));
                saveConfig("device.reconnect_interval", String.valueOf(config.getDevice().getReconnectInterval()));
            }

            // Keeper配置
            if (config.getKeeper() != null) {
                saveConfig("keeper.enabled", String.valueOf(config.getKeeper().isEnabled()));
                saveConfig("keeper.check_interval", String.valueOf(config.getKeeper().getCheckInterval()));
                saveConfig("keeper.offline_threshold", String.valueOf(config.getKeeper().getOfflineThreshold()));
            }

            // OSS配置
            if (config.getOss() != null) {
                saveConfig("oss.enabled", String.valueOf(config.getOss().isEnabled()));
                saveConfig("oss.type", config.getOss().getType());
                saveConfig("oss.endpoint", config.getOss().getEndpoint());
                saveConfig("oss.access_key_id", config.getOss().getAccessKeyId());
                saveConfig("oss.access_key_secret", config.getOss().getAccessKeySecret());
                saveConfig("oss.bucket_name", config.getOss().getBucketName());
                saveConfig("oss.region", config.getOss().getRegion());
                saveConfig("oss.form_data_upload_path", config.getOss().getFormDataUploadPath());
                saveConfig("oss.base64_upload_path", config.getOss().getBase64UploadPath());
            }

            // Log配置
            if (config.getLog() != null) {
                saveConfig("log.level", config.getLog().getLevel());
                saveConfig("log.file", config.getLog().getFile());
                saveConfig("log.max_size", String.valueOf(config.getLog().getMaxSize()));
                saveConfig("log.max_backups", String.valueOf(config.getLog().getMaxBackups()));
                saveConfig("log.max_age", String.valueOf(config.getLog().getMaxAge()));
            }

            // SDK配置
            if (config.getSdk() != null) {
                saveConfig("sdk.lib_path", config.getSdk().getLibPath());
                saveConfig("sdk.log_path", config.getSdk().getLogPath());
                saveConfig("sdk.log_level", String.valueOf(config.getSdk().getLogLevel()));
            }

            // Recorder配置
            if (config.getRecorder() != null) {
                saveConfig("recorder.enabled", String.valueOf(config.getRecorder().isEnabled()));
                saveConfig("recorder.record_path", config.getRecorder().getRecordPath());
                saveConfig("recorder.retention_minutes", String.valueOf(config.getRecorder().getRetentionMinutes()));
            }
            
            // Notification配置
            if (config.getNotification() != null) {
                Config.NotificationConfig notif = config.getNotification();
                if (notif.getWechat() != null) {
                    saveConfig("notification.wechat.enabled", String.valueOf(notif.getWechat().isEnabled()));
                    saveConfig("notification.wechat.webhook_url", notif.getWechat().getWebhookUrl());
                }
                if (notif.getDingtalk() != null) {
                    saveConfig("notification.dingtalk.enabled", String.valueOf(notif.getDingtalk().isEnabled()));
                    saveConfig("notification.dingtalk.webhook_url", notif.getDingtalk().getWebhookUrl());
                    saveConfig("notification.dingtalk.secret", notif.getDingtalk().getSecret());
                }
                if (notif.getFeishu() != null) {
                    saveConfig("notification.feishu.enabled", String.valueOf(notif.getFeishu().isEnabled()));
                    saveConfig("notification.feishu.webhook_url", notif.getFeishu().getWebhookUrl());
                }
            }

            // PTZ监控配置
            if (config.getPtzMonitor() != null) {
                saveConfig("ptz_monitor.enabled", String.valueOf(config.getPtzMonitor().isEnabled()));
                saveConfig("ptz_monitor.interval", String.valueOf(config.getPtzMonitor().getInterval()));
            }

            logger.info("配置已保存到数据库");
        } catch (Exception e) {
            logger.error("保存配置到数据库失败", e);
        }
    }

    /**
     * 从数据库恢复配置
     */
    private Config restoreConfigFromDatabase(Map<String, String> dbConfigs, Config defaultConfig) {
        try {
            Config config = new Config();

            // 创建新的配置对象，从数据库恢复，如果不存在则使用默认值
            if (defaultConfig.getMqtt() != null) {
                Config.MqttConfig mqtt = new Config.MqttConfig();
                mqtt.setBroker(getConfig(dbConfigs, "mqtt.broker", defaultConfig.getMqtt().getBroker()));
                mqtt.setClientId(getConfig(dbConfigs, "mqtt.client_id", defaultConfig.getMqtt().getClientId()));
                mqtt.setUsername(getConfig(dbConfigs, "mqtt.username", defaultConfig.getMqtt().getUsername()));
                mqtt.setPassword(getConfig(dbConfigs, "mqtt.password", defaultConfig.getMqtt().getPassword()));
                mqtt.setStatusTopic(
                        getConfig(dbConfigs, "mqtt.status_topic", defaultConfig.getMqtt().getStatusTopic()));
                mqtt.setCommandTopic(
                        getConfig(dbConfigs, "mqtt.command_topic", defaultConfig.getMqtt().getCommandTopic()));
                mqtt.setResponseTopic(
                        getConfig(dbConfigs, "mqtt.response_topic", defaultConfig.getMqtt().getResponseTopic()));
                mqtt.setGatewayStatusTopic(
                        getConfig(dbConfigs, "mqtt.gateway_status_topic", defaultConfig.getMqtt().getGatewayStatusTopic()));
                mqtt.setReportTopicPrefix(
                        getConfig(dbConfigs, "mqtt.report_topic_prefix", defaultConfig.getMqtt().getReportTopicPrefix()));
                mqtt.setQos(getIntConfig(dbConfigs, "mqtt.qos", defaultConfig.getMqtt().getQos()));
                mqtt.setKeepAlive(getIntConfig(dbConfigs, "mqtt.keep_alive", defaultConfig.getMqtt().getKeepAlive()));
                config.setMqtt(mqtt);
            }

            if (defaultConfig.getScanner() != null) {
                Config.ScannerConfig scanner = new Config.ScannerConfig();
                scanner.setEnabled(getBoolConfig(dbConfigs, "scanner.enabled", defaultConfig.getScanner().isEnabled()));
                scanner.setInterval(
                        getIntConfig(dbConfigs, "scanner.interval", defaultConfig.getScanner().getInterval()));
                scanner.setListenPort(
                        getIntConfig(dbConfigs, "scanner.listen_port", defaultConfig.getScanner().getListenPort()));
                scanner.setListenIp(
                        getConfig(dbConfigs, "scanner.listen_ip", defaultConfig.getScanner().getListenIp()));
                scanner.setScanSegment(
                        getConfig(dbConfigs, "scanner.scan_segment", defaultConfig.getScanner().getScanSegment()));
                config.setScanner(scanner);
            }

            if (defaultConfig.getDevice() != null) {
                Config.DeviceConfig device = new Config.DeviceConfig();
                device.setDefaultUsername(getConfig(dbConfigs, "device.default_username",
                        defaultConfig.getDevice().getDefaultUsername()));
                device.setDefaultPassword(getConfig(dbConfigs, "device.default_password",
                        defaultConfig.getDevice().getDefaultPassword()));
                device.setDefaultPort(
                        getIntConfig(dbConfigs, "device.default_port", defaultConfig.getDevice().getDefaultPort()));
                device.setHttpPort(
                        getIntConfig(dbConfigs, "device.http_port", defaultConfig.getDevice().getHttpPort()));
                device.setRtspPort(
                        getIntConfig(dbConfigs, "device.rtsp_port", defaultConfig.getDevice().getRtspPort()));
                device.setLoginTimeout(
                        getIntConfig(dbConfigs, "device.login_timeout", defaultConfig.getDevice().getLoginTimeout()));
                device.setReconnectInterval(getIntConfig(dbConfigs, "device.reconnect_interval",
                        defaultConfig.getDevice().getReconnectInterval()));
                config.setDevice(device);
            }

            if (defaultConfig.getKeeper() != null) {
                Config.KeeperConfig keeper = new Config.KeeperConfig();
                keeper.setEnabled(getBoolConfig(dbConfigs, "keeper.enabled", defaultConfig.getKeeper().isEnabled()));
                keeper.setCheckInterval(
                        getIntConfig(dbConfigs, "keeper.check_interval", defaultConfig.getKeeper().getCheckInterval()));
                keeper.setOfflineThreshold(getIntConfig(dbConfigs, "keeper.offline_threshold",
                        defaultConfig.getKeeper().getOfflineThreshold()));
                config.setKeeper(keeper);
            }

            if (defaultConfig.getOss() != null) {
                Config.OssConfig oss = new Config.OssConfig();
                oss.setEnabled(getBoolConfig(dbConfigs, "oss.enabled", defaultConfig.getOss().isEnabled()));
                oss.setType(getConfig(dbConfigs, "oss.type", defaultConfig.getOss().getType()));
                oss.setEndpoint(getConfig(dbConfigs, "oss.endpoint", defaultConfig.getOss().getEndpoint()));
                oss.setAccessKeyId(getConfig(dbConfigs, "oss.access_key_id", defaultConfig.getOss().getAccessKeyId()));
                oss.setAccessKeySecret(
                        getConfig(dbConfigs, "oss.access_key_secret", defaultConfig.getOss().getAccessKeySecret()));
                oss.setBucketName(getConfig(dbConfigs, "oss.bucket_name", defaultConfig.getOss().getBucketName()));
                oss.setRegion(getConfig(dbConfigs, "oss.region", defaultConfig.getOss().getRegion()));
                oss.setFormDataUploadPath(getConfig(dbConfigs, "oss.form_data_upload_path", defaultConfig.getOss().getFormDataUploadPath()));
                oss.setBase64UploadPath(getConfig(dbConfigs, "oss.base64_upload_path", defaultConfig.getOss().getBase64UploadPath()));
                config.setOss(oss);
            }

            if (defaultConfig.getLog() != null) {
                Config.LogConfig log = new Config.LogConfig();
                log.setLevel(getConfig(dbConfigs, "log.level", defaultConfig.getLog().getLevel()));
                log.setFile(getConfig(dbConfigs, "log.file", defaultConfig.getLog().getFile()));
                log.setMaxSize(getIntConfig(dbConfigs, "log.max_size", defaultConfig.getLog().getMaxSize()));
                log.setMaxBackups(getIntConfig(dbConfigs, "log.max_backups", defaultConfig.getLog().getMaxBackups()));
                log.setMaxAge(getIntConfig(dbConfigs, "log.max_age", defaultConfig.getLog().getMaxAge()));
                config.setLog(log);
            }

            if (defaultConfig.getSdk() != null) {
                Config.SdkConfig sdk = new Config.SdkConfig();
                sdk.setLibPath(getConfig(dbConfigs, "sdk.lib_path", defaultConfig.getSdk().getLibPath()));
                sdk.setLogPath(getConfig(dbConfigs, "sdk.log_path", defaultConfig.getSdk().getLogPath()));
                sdk.setLogLevel(getIntConfig(dbConfigs, "sdk.log_level", defaultConfig.getSdk().getLogLevel()));
                config.setSdk(sdk);
            }

            if (defaultConfig.getRecorder() != null) {
                Config.RecorderConfig recorder = new Config.RecorderConfig();
                recorder.setEnabled(
                        getBoolConfig(dbConfigs, "recorder.enabled", defaultConfig.getRecorder().isEnabled()));
                recorder.setRecordPath(
                        getConfig(dbConfigs, "recorder.record_path", defaultConfig.getRecorder().getRecordPath()));
                recorder.setRetentionMinutes(getIntConfig(dbConfigs, "recorder.retention_minutes",
                        defaultConfig.getRecorder().getRetentionMinutes()));
                config.setRecorder(recorder);
            }
            
            // 恢复Notification配置
            Config.NotificationConfig notif = new Config.NotificationConfig();
            Config.NotificationChannel wechat = new Config.NotificationChannel();
            wechat.setEnabled(getBoolConfig(dbConfigs, "notification.wechat.enabled", false));
            wechat.setWebhookUrl(getConfig(dbConfigs, "notification.wechat.webhook_url", null));
            notif.setWechat(wechat);
            
            Config.NotificationChannel dingtalk = new Config.NotificationChannel();
            dingtalk.setEnabled(getBoolConfig(dbConfigs, "notification.dingtalk.enabled", false));
            dingtalk.setWebhookUrl(getConfig(dbConfigs, "notification.dingtalk.webhook_url", null));
            dingtalk.setSecret(getConfig(dbConfigs, "notification.dingtalk.secret", null));
            notif.setDingtalk(dingtalk);
            
            Config.NotificationChannel feishu = new Config.NotificationChannel();
            feishu.setEnabled(getBoolConfig(dbConfigs, "notification.feishu.enabled", false));
            feishu.setWebhookUrl(getConfig(dbConfigs, "notification.feishu.webhook_url", null));
            notif.setFeishu(feishu);
            
            config.setNotification(notif);

            // PTZ监控配置
            if (defaultConfig.getPtzMonitor() != null) {
                Config.PtzMonitorConfig ptzMonitor = new Config.PtzMonitorConfig();
                ptzMonitor.setEnabled(getBoolConfig(dbConfigs, "ptz_monitor.enabled", defaultConfig.getPtzMonitor().isEnabled()));
                ptzMonitor.setInterval(getIntConfig(dbConfigs, "ptz_monitor.interval", defaultConfig.getPtzMonitor().getInterval()));
                config.setPtzMonitor(ptzMonitor);
            }

            return config;
        } catch (Exception e) {
            logger.error("从数据库恢复配置失败", e);
            return defaultConfig;
        }
    }

    /**
     * 获取配置值（字符串）
     */
    private String getConfig(Map<String, String> dbConfigs, String key, String defaultValue) {
        String value = dbConfigs.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取配置值（整数）
     */
    private int getIntConfig(Map<String, String> dbConfigs, String key, int defaultValue) {
        String value = dbConfigs.get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warn("配置值格式错误: {} = {}", key, value);
            }
        }
        return defaultValue;
    }

    /**
     * 获取配置值（布尔）
     */
    private boolean getBoolConfig(Map<String, String> dbConfigs, String key, boolean defaultValue) {
        String value = dbConfigs.get(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    /**
     * 保存单个配置项
     */
    private void saveConfig(String key, String value) {
        if (value != null) {
            database.saveOrUpdateConfig(key, value, "string");
        }
    }

    /**
     * 更新配置
     */
    public void updateConfig(Config config) {
        saveConfigToDatabase(config);
        currentConfig = config;
    }

    /**
     * 更新单个配置项
     */
    public void updateConfig(String key, String value) {
        database.saveOrUpdateConfig(key, value, "string");
        // 重新加载配置
        loadConfig();
    }

    /**
     * 获取当前配置
     */
    public Config getConfig() {
        return currentConfig;
    }

    /**
     * 获取配置值
     */
    public String getConfigValue(String key) {
        String value = database.getConfig(key);
        return value;
    }
}
