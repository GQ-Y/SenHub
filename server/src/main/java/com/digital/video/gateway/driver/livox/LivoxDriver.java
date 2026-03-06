package com.digital.video.gateway.driver.livox;

import com.digital.video.gateway.driver.livox.protocol.SdkPacket;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.RadarDevice;
import com.digital.video.gateway.database.RadarDeviceDAO;
// 使用项目内部的 LivoxJNI（包含设备信息回调支持）
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

/**
 * Livox Driver - 基于官方 SDK (JNI) 的驱动实现
 */
public class LivoxDriver {

    private static final Logger log = LoggerFactory.getLogger(LivoxDriver.class);

    private Database database;
    private LegacyPointCloudCallback legacyCallback; // 兼容旧接口
    private PointCloudCallback jniCallback; // JNI 回调
    private String configFilePath;
    private boolean started = false;

    // 默认配置值
    private static final String DEFAULT_MULTICAST_IP = "224.1.1.5";
    private static final int DEFAULT_CMD_DATA_PORT = 56101;
    private static final int DEFAULT_PUSH_MSG_PORT = 56201;
    private static final int DEFAULT_POINT_DATA_PORT = 56301;
    private static final int DEFAULT_IMU_DATA_PORT = 56401;
    private static final int DEFAULT_LOG_DATA_PORT = 56501;

    public LivoxDriver() {
        // 默认构造函数，需要后续注入 Database
    }

    public LivoxDriver(Database database) {
        this.database = database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    /**
     * 启动驱动
     */
    public void start() {
        if (started) {
            log.warn("Livox Driver 已经启动");
            return;
        }

        log.info("启动 Livox Driver (JNI)...");

        // 检查 JNI 库是否已加载
        if (!LivoxJNI.isLibraryLoaded()) {
            String error = LivoxJNI.getLoadError();
            log.error("Livox JNI 库未加载，无法启动驱动");
            if (error != null) {
                log.error("加载错误: {}", error);
            }
            log.error("请检查:");
            log.error("  1. lib/linux/liblivoxjni.so 文件是否存在");
            log.error("  2. lib/linux/liblivox_lidar_sdk_shared.so 依赖库是否存在");
            log.error("  3. 库文件架构是否与系统架构匹配");
            log.error("  4. 是否设置了正确的 LD_LIBRARY_PATH");
            return;
        }

        try {
            // 1. 生成配置文件
            configFilePath = generateLivoxConfig();
            if (configFilePath == null) {
                log.error("生成 Livox 配置文件失败");
                return;
            }

            // 2. 初始化 SDK
            try {
                log.debug("准备初始化 Livox SDK，配置文件: {}", configFilePath);
                boolean initResult = LivoxJNI.init(configFilePath);
                if (!initResult) {
                    log.error("Livox SDK 初始化失败，返回 false");
                    return;
                }
                log.info("Livox SDK 初始化成功");
            } catch (UnsatisfiedLinkError e) {
                log.error("Livox SDK 初始化时发生链接错误: {}", e.getMessage(), e);
                log.error("这通常表示 JNI 库已加载但 native 方法调用失败");
                log.error("可能原因: 依赖库缺失或版本不匹配");
                return;
            } catch (Exception e) {
                log.error("Livox SDK 初始化时发生异常", e);
                return;
            }

            // 4. 设置点云回调
            if (jniCallback != null) {
                LivoxJNI.setPointCloudCallback(jniCallback);
            } else if (legacyCallback != null) {
                // 如果有旧接口回调，创建适配器
                jniCallback = createLegacyAdapter(legacyCallback);
                LivoxJNI.setPointCloudCallback(jniCallback);
            }

            // 5. 启动 SDK
            try {
                log.debug("准备启动 Livox SDK");
                boolean startResult = LivoxJNI.start();
                if (!startResult) {
                    log.error("Livox SDK 启动失败，返回 false");
                    return;
                }
                log.info("Livox SDK 启动成功");
            } catch (Exception e) {
                log.error("Livox SDK 启动时发生异常", e);
                return;
            }

            started = true;
            log.info("Livox Driver 启动成功");

        } catch (Exception e) {
            log.error("启动 Livox Driver 时出错", e);
        }
    }

    /**
     * 停止驱动
     */
    public void stop() {
        if (!started) {
            return;
        }

        log.info("停止 Livox Driver...");
        try {
            LivoxJNI.stop();
            started = false;
            log.info("Livox Driver 已停止");
        } catch (Exception e) {
            log.error("停止 Livox Driver 时出错", e);
        }
    }

    /**
     * 设置点云数据回调（JNI 接口）
     */
    public void setPointCloudCallback(PointCloudCallback callback) {
        this.jniCallback = callback;
        // 如果已经启动，需要重新设置回调
        if (started) {
            LivoxJNI.setPointCloudCallback(callback);
        }
    }

    /**
     * 生成 Livox 配置文件。
     * 支持多雷达：为数据库中每台雷达分配独立的主机端口组，
     * 避免多台 Mid-360 共享同一端口导致 SDK 层数据冲突。
     * 端口分配规则：第 N 台（0-based）雷达的各端口 = 基础端口 + N * 10。
     *
     * @return 配置文件路径，失败返回 null
     */
    private String generateLivoxConfig() {
        try {
            // 从数据库读取配置
            String hostIp = getConfig("livox.host_ip");
            String multicastIp        = getConfig("livox.multicast_ip",    DEFAULT_MULTICAST_IP);
            int baseCmdDataPort       = getIntConfig("livox.cmd_data_port",   DEFAULT_CMD_DATA_PORT);
            int basePushMsgPort       = getIntConfig("livox.push_msg_port",   DEFAULT_PUSH_MSG_PORT);
            int basePointDataPort     = getIntConfig("livox.point_data_port", DEFAULT_POINT_DATA_PORT);
            int baseImuDataPort       = getIntConfig("livox.imu_data_port",   DEFAULT_IMU_DATA_PORT);
            int baseLogDataPort       = getIntConfig("livox.log_data_port",   DEFAULT_LOG_DATA_PORT);

            // 如果 host_ip 为空，自动检测
            if (hostIp == null || hostIp.trim().isEmpty()) {
                hostIp = getLocalHostIp();
                if (hostIp == null) {
                    log.error("无法获取本地 IP 地址，且数据库未配置 livox.host_ip");
                    return null;
                }
                log.info("自动检测到本地 IP: {}", hostIp);
            }

            // 从数据库读取已注册雷达设备列表，用于确定需要分配的端口组数量
            List<RadarDevice> radarDevices = null;
            if (database != null) {
                try {
                    radarDevices = new RadarDeviceDAO(database.getConnection()).getAll();
                } catch (Exception e) {
                    log.warn("读取雷达设备列表失败，退化为单雷达端口配置: {}", e.getMessage());
                }
            }
            int radarCount = (radarDevices != null && !radarDevices.isEmpty()) ? radarDevices.size() : 1;

            // 创建 JSON 配置
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root     = mapper.createObjectNode();
            ObjectNode mid360   = mapper.createObjectNode();
            root.set("MID360", mid360);

            // lidar_net_info：雷达端固定端口（所有型号统一，不区分台数）
            ObjectNode lidarNetInfo = mapper.createObjectNode();
            lidarNetInfo.put("cmd_data_port",   56100);
            lidarNetInfo.put("push_msg_port",   56200);
            lidarNetInfo.put("point_data_port", 56300);
            lidarNetInfo.put("imu_data_port",   56400);
            lidarNetInfo.put("log_data_port",   56500);
            mid360.set("lidar_net_info", lidarNetInfo);

            // host_net_info：每台雷达独立一组端口（偏移量 = index * 10）
            ArrayNode hostNetInfoArray = mapper.createArrayNode();
            for (int i = 0; i < radarCount; i++) {
                int offset = i * 10;
                ObjectNode hostNetInfo = mapper.createObjectNode();
                hostNetInfo.put("host_ip",         hostIp);
                hostNetInfo.put("multicast_ip",    multicastIp);
                hostNetInfo.put("cmd_data_port",   baseCmdDataPort   + offset);
                hostNetInfo.put("push_msg_port",   basePushMsgPort   + offset);
                hostNetInfo.put("point_data_port", basePointDataPort + offset);
                hostNetInfo.put("imu_data_port",   baseImuDataPort   + offset);
                hostNetInfo.put("log_data_port",   baseLogDataPort   + offset);
                hostNetInfoArray.add(hostNetInfo);

                String radarIp = (radarDevices != null && i < radarDevices.size())
                        ? radarDevices.get(i).getRadarIp() : "unknown";
                log.info("多雷达端口分配: 第{}台 radarIp={}, cmd={}, push={}, point={}, imu={}, log={}",
                        i + 1, radarIp,
                        baseCmdDataPort + offset, basePushMsgPort + offset,
                        basePointDataPort + offset, baseImuDataPort + offset,
                        baseLogDataPort + offset);
            }
            mid360.set("host_net_info", hostNetInfoArray);

            // 写入配置文件
            String configDir = "./config";
            File configDirFile = new File(configDir);
            if (!configDirFile.exists()) {
                configDirFile.mkdirs();
            }

            String configPath = configDir + "/livox-config.json";
            try (FileWriter writer = new FileWriter(new File(configPath))) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(writer, root);
            }

            log.info("Livox 配置文件已生成: {}，共 {} 台雷达端口配置", configPath, radarCount);
            return configPath;

        } catch (Exception e) {
            log.error("生成 Livox 配置文件失败", e);
            return null;
        }
    }

    /**
     * 获取本地主机 IP 地址
     * 优先选择 192.168.x.x 网段的 IP（局域网 IP）
     * @return IP 地址字符串，失败返回 null
     */
    private String getLocalHostIp() {
        String preferredIp = null; // 优先的 IP（192.168.x.x）
        String fallbackIp = null;  // 备选 IP（其他非回环 IP）
        
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                // 跳过回环接口和未启用的接口
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();

                    // 只处理 IPv4 地址，跳过回环和链路本地地址
                    if (inetAddress instanceof Inet4Address 
                            && !inetAddress.isLoopbackAddress()
                            && !inetAddress.isLinkLocalAddress()) {
                        String hostAddress = inetAddress.getHostAddress();
                        log.debug("检测到本地 IP: {}", hostAddress);
                        
                        // 优先选择 192.168.x.x 网段（局域网 IP）
                        if (hostAddress.startsWith("192.168.")) {
                            preferredIp = hostAddress;
                            log.debug("找到优先 IP (192.168.x.x): {}", preferredIp);
                            // 继续查找，看是否有更合适的（但通常第一个 192.168.x.x 就足够了）
                        } else if (fallbackIp == null) {
                            // 保存第一个非 192.168 的 IP 作为备选
                            fallbackIp = hostAddress;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            log.error("获取网络接口失败", e);
        }

        // 优先返回 192.168.x.x 网段的 IP
        if (preferredIp != null) {
            log.info("使用优先 IP (192.168.x.x): {}", preferredIp);
            return preferredIp;
        }
        
        // 如果没有 192.168.x.x，使用备选 IP
        if (fallbackIp != null) {
            log.warn("未找到 192.168.x.x 网段 IP，使用备选 IP: {}", fallbackIp);
            return fallbackIp;
        }

        log.warn("无法获取本地 IP 地址");
        return null;
    }

    /**
     * 从数据库获取配置值
     */
    private String getConfig(String key) {
        if (database == null) {
            log.warn("Database 未设置，无法读取配置: {}", key);
            return null;
        }
        return database.getConfig(key);
    }

    /**
     * 从数据库获取配置值，如果不存在则返回默认值
     */
    private String getConfig(String key, String defaultValue) {
        String value = getConfig(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 从数据库获取整数配置值，如果不存在则返回默认值
     */
    private int getIntConfig(String key, int defaultValue) {
        String value = getConfig(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("配置值格式错误: {} = {}，使用默认值: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    // ==================== 兼容旧接口 ====================

    /**
     * 兼容旧的 PointCloudCallback 接口（使用 SdkPacket）
     */
    public interface LegacyPointCloudCallback {
        void onPointCloud(SdkPacket packet);
    }

    /**
     * 设置点云回调（兼容旧接口）
     */
    public void setPointCloudCallback(LegacyPointCloudCallback callback) {
        this.legacyCallback = callback;
        // 创建适配器
        if (callback != null) {
            jniCallback = createLegacyAdapter(callback);
        } else {
            jniCallback = null;
        }
        // 如果已经启动，需要重新设置回调
        if (started && jniCallback != null) {
            LivoxJNI.setPointCloudCallback(jniCallback);
        }
    }

    /**
     * 创建旧接口适配器
     * 重要：传递 handle 以支持多雷达场景
     */
    private PointCloudCallback createLegacyAdapter(
            LegacyPointCloudCallback legacyCallback) {
        return new PointCloudCallback() {
            @Override
            public void onPointCloud(int handle, int devType, int pointCount, int dataType, byte[] data) {
                // 创建 SdkPacket 对象
                SdkPacket packet = new SdkPacket();
                packet.handle = handle; // 传递设备句柄，用于多雷达区分
                packet.cmdType = 0x01; // MSG type
                packet.cmdId = dataType;
                packet.payload = data;
                // 调用旧接口
                legacyCallback.onPointCloud(packet);
            }
        };
    }

    /**
     * 占位：通过IP获取设备信息（序列号等）
     * 如果未来JNI层暴露更多能力，可以在此处调用。
     */
    public DeviceInfo getDeviceInfo(String ip) {
        // 当前JNI未暴露设备信息查询接口，返回占位对象，保留扩展点
        log.debug("getDeviceInfo 占位调用，当前未集成SDK查询逻辑, ip={}", ip);
        return null;
    }

    public static class DeviceInfo {
        public String serial;
        public String model;
        public String firmware;
    }
}
