package com.digital.video.gateway.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 配置类
 */
public class Config {
    @JsonProperty("mqtt")
    private MqttConfig mqtt;

    @JsonProperty("scanner")
    private ScannerConfig scanner;

    @JsonProperty("device")
    private DeviceConfig device;

    @JsonProperty("keeper")
    private KeeperConfig keeper;

    @JsonProperty("oss")
    private OssConfig oss;

    @JsonProperty("database")
    private DatabaseConfig database;

    @JsonProperty("log")
    private LogConfig log;

    @JsonProperty("sdk")
    private SdkConfig sdk;

    @JsonProperty("recorder")
    private RecorderConfig recorder;

    // Getters and Setters
    public MqttConfig getMqtt() {
        return mqtt;
    }

    public void setMqtt(MqttConfig mqtt) {
        this.mqtt = mqtt;
    }

    public ScannerConfig getScanner() {
        return scanner;
    }

    public void setScanner(ScannerConfig scanner) {
        this.scanner = scanner;
    }

    public DeviceConfig getDevice() {
        return device;
    }

    public void setDevice(DeviceConfig device) {
        this.device = device;
    }

    public KeeperConfig getKeeper() {
        return keeper;
    }

    public void setKeeper(KeeperConfig keeper) {
        this.keeper = keeper;
    }

    public OssConfig getOss() {
        return oss;
    }

    public void setOss(OssConfig oss) {
        this.oss = oss;
    }

    public DatabaseConfig getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfig database) {
        this.database = database;
    }

    public LogConfig getLog() {
        return log;
    }

    public void setLog(LogConfig log) {
        this.log = log;
    }

    public SdkConfig getSdk() {
        return sdk;
    }

    public void setSdk(SdkConfig sdk) {
        this.sdk = sdk;
    }

    public RecorderConfig getRecorder() {
        return recorder;
    }

    public void setRecorder(RecorderConfig recorder) {
        this.recorder = recorder;
    }

    public static class MqttConfig {
        private String broker;
        @JsonProperty("client_id")
        private String clientId;
        private String username;
        private String password;
        @JsonProperty("status_topic")
        private String statusTopic;
        @JsonProperty("command_topic")
        private String commandTopic;
        @JsonProperty("response_topic")
        private String responseTopic;
        private int qos;
        @JsonProperty("keep_alive")
        private int keepAlive;

        // Getters and Setters
        public String getBroker() {
            return broker;
        }

        public void setBroker(String broker) {
            this.broker = broker;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getStatusTopic() {
            return statusTopic;
        }

        public void setStatusTopic(String statusTopic) {
            this.statusTopic = statusTopic;
        }

        public String getCommandTopic() {
            return commandTopic;
        }

        public void setCommandTopic(String commandTopic) {
            this.commandTopic = commandTopic;
        }

        public String getResponseTopic() {
            return responseTopic;
        }

        public void setResponseTopic(String responseTopic) {
            this.responseTopic = responseTopic;
        }

        public int getQos() {
            return qos;
        }

        public void setQos(int qos) {
            this.qos = qos;
        }

        public int getKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(int keepAlive) {
            this.keepAlive = keepAlive;
        }
    }

    public static class ScannerConfig {
        private boolean enabled;
        private int interval;
        @JsonProperty("listen_port")
        private int listenPort;
        @JsonProperty("listen_ip")
        private String listenIp;
        @JsonProperty("scan_range_start")
        private int scanRangeStart = 10; // 扫描IP范围起始值，默认10
        @JsonProperty("scan_range_end")
        private int scanRangeEnd = 100; // 扫描IP范围结束值，默认100
        @JsonProperty("scan_segment")
        private String scanSegment; // 扫描网段（例如：192.168.1）

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getInterval() {
            return interval;
        }

        public void setInterval(int interval) {
            this.interval = interval;
        }

        public int getListenPort() {
            return listenPort;
        }

        public void setListenPort(int listenPort) {
            this.listenPort = listenPort;
        }

        public String getListenIp() {
            return listenIp;
        }

        public void setListenIp(String listenIp) {
            this.listenIp = listenIp;
        }

        public int getScanRangeStart() {
            return scanRangeStart;
        }

        public void setScanRangeStart(int scanRangeStart) {
            this.scanRangeStart = scanRangeStart;
        }

        public int getScanRangeEnd() {
            return scanRangeEnd;
        }

        public void setScanRangeEnd(int scanRangeEnd) {
            this.scanRangeEnd = scanRangeEnd;
        }

        public String getScanSegment() {
            return scanSegment;
        }

        public void setScanSegment(String scanSegment) {
            this.scanSegment = scanSegment;
        }
    }

    public static class DeviceConfig {
        @JsonProperty("default_username")
        private String defaultUsername;
        @JsonProperty("default_password")
        private String defaultPassword;
        @JsonProperty("default_port")
        private int defaultPort; // SDK端口，默认8000
        @JsonProperty("http_port")
        private int httpPort = 80; // HTTP端口，默认80
        @JsonProperty("rtsp_port")
        private int rtspPort = 554; // RTSP端口，默认554
        @JsonProperty("login_timeout")
        private int loginTimeout;
        @JsonProperty("reconnect_interval")
        private int reconnectInterval;

        // Getters and Setters
        public String getDefaultUsername() {
            return defaultUsername;
        }

        public void setDefaultUsername(String defaultUsername) {
            this.defaultUsername = defaultUsername;
        }

        public String getDefaultPassword() {
            return defaultPassword;
        }

        public void setDefaultPassword(String defaultPassword) {
            this.defaultPassword = defaultPassword;
        }

        public int getDefaultPort() {
            return defaultPort;
        }

        public void setDefaultPort(int defaultPort) {
            this.defaultPort = defaultPort;
        }

        public int getHttpPort() {
            return httpPort;
        }

        public void setHttpPort(int httpPort) {
            this.httpPort = httpPort;
        }

        public int getRtspPort() {
            return rtspPort;
        }

        public void setRtspPort(int rtspPort) {
            this.rtspPort = rtspPort;
        }

        public int getLoginTimeout() {
            return loginTimeout;
        }

        public void setLoginTimeout(int loginTimeout) {
            this.loginTimeout = loginTimeout;
        }

        public int getReconnectInterval() {
            return reconnectInterval;
        }

        public void setReconnectInterval(int reconnectInterval) {
            this.reconnectInterval = reconnectInterval;
        }
    }

    public static class KeeperConfig {
        private boolean enabled;
        @JsonProperty("check_interval")
        private int checkInterval;
        @JsonProperty("offline_threshold")
        private int offlineThreshold;

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCheckInterval() {
            return checkInterval;
        }

        public void setCheckInterval(int checkInterval) {
            this.checkInterval = checkInterval;
        }

        public int getOfflineThreshold() {
            return offlineThreshold;
        }

        public void setOfflineThreshold(int offlineThreshold) {
            this.offlineThreshold = offlineThreshold;
        }
    }

    public static class OssConfig {
        private boolean enabled;
        private String type; // aliyun or minio
        private String endpoint;
        @JsonProperty("access_key_id")
        private String accessKeyId;
        @JsonProperty("access_key_secret")
        private String accessKeySecret;
        @JsonProperty("bucket_name")
        private String bucketName;
        private String region;
        @JsonProperty("form_data_upload_path")
        private String formDataUploadPath = "/upload/file_upload/formDataUpload";
        @JsonProperty("base64_upload_path")
        private String base64UploadPath = "/upload/file_upload/imgUpload";

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getType() {
            return type != null ? type : "aliyun";
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getFormDataUploadPath() {
            return formDataUploadPath != null ? formDataUploadPath : "/upload/file_upload/formDataUpload";
        }

        public void setFormDataUploadPath(String formDataUploadPath) {
            this.formDataUploadPath = formDataUploadPath;
        }

        public String getBase64UploadPath() {
            return base64UploadPath != null ? base64UploadPath : "/upload/file_upload/imgUpload";
        }

        public void setBase64UploadPath(String base64UploadPath) {
            this.base64UploadPath = base64UploadPath;
        }
    }

    public static class DatabaseConfig {
        private String path;

        // Getters and Setters
        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class LogConfig {
        private String level;
        private String file;
        @JsonProperty("max_size")
        private int maxSize;
        @JsonProperty("max_backups")
        private int maxBackups;
        @JsonProperty("max_age")
        private int maxAge;
        @JsonProperty("debug_mode")
        private boolean debugMode;
        @JsonProperty("sdk_log_file")
        private String sdkLogFile;
        @JsonProperty("pointcloud_log_interval")
        private int pointcloudLogInterval = 60;

        // Getters and Setters
        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getMaxBackups() {
            return maxBackups;
        }

        public void setMaxBackups(int maxBackups) {
            this.maxBackups = maxBackups;
        }

        public int getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(int maxAge) {
            this.maxAge = maxAge;
        }

        public boolean isDebugMode() {
            return debugMode;
        }

        public void setDebugMode(boolean debugMode) {
            this.debugMode = debugMode;
        }

        public String getSdkLogFile() {
            return sdkLogFile != null && !sdkLogFile.isEmpty() ? sdkLogFile : "./logs/sdk.log";
        }

        public void setSdkLogFile(String sdkLogFile) {
            this.sdkLogFile = sdkLogFile;
        }

        public int getPointcloudLogInterval() {
            return pointcloudLogInterval > 0 ? pointcloudLogInterval : 60;
        }

        public void setPointcloudLogInterval(int pointcloudLogInterval) {
            this.pointcloudLogInterval = pointcloudLogInterval;
        }
    }

    public static class SdkConfig {
        @JsonProperty("lib_path")
        private String libPath;
        @JsonProperty("log_path")
        private String logPath;
        @JsonProperty("log_level")
        private int logLevel;

        // Getters and Setters
        public String getLibPath() {
            return libPath;
        }

        public void setLibPath(String libPath) {
            this.libPath = libPath;
        }

        public String getLogPath() {
            return logPath;
        }

        public void setLogPath(String logPath) {
            this.logPath = logPath;
        }

        public int getLogLevel() {
            return logLevel;
        }

        public void setLogLevel(int logLevel) {
            this.logLevel = logLevel;
        }
    }

    public static class RecorderConfig {
        private boolean enabled;
        @JsonProperty("record_path")
        private String recordPath;
        @JsonProperty("retention_minutes")
        private int retentionMinutes; // 保留时长（分钟）

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRecordPath() {
            return recordPath;
        }

        public void setRecordPath(String recordPath) {
            this.recordPath = recordPath;
        }

        public int getRetentionMinutes() {
            return retentionMinutes;
        }

        public void setRetentionMinutes(int retentionMinutes) {
            this.retentionMinutes = retentionMinutes;
        }
    }
}
