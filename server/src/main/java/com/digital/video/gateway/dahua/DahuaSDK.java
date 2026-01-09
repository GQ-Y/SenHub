package com.digital.video.gateway.dahua;

import com.digital.video.gateway.Common.ArchitectureChecker;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.digital.video.gateway.dahua.lib.ToolKits;
import com.digital.video.gateway.mqtt.MqttClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大华SDK封装类
 */
public class DahuaSDK implements DeviceSDK {
    private static final Logger logger = LoggerFactory.getLogger(DahuaSDK.class);
    private static DahuaSDK instance;

    private NetSDKLib netsdk;
    private boolean initialized = false;
    private Config.SdkConfig sdkConfig;
    private DeviceManager deviceManager;
    private MqttClient mqttClient;

    // 存储登录句柄：userId -> loginHandle
    private final Map<Integer, NetSDKLib.LLong> loginHandles = new ConcurrentHashMap<>();
    // 存储userId映射：loginHandle -> userId（用于回调中查找）
    private final Map<Long, Integer> handleToUserIdMap = new ConcurrentHashMap<>();
    private int nextUserId = 1;

    private DahuaSDK() {
    }

    public static synchronized DahuaSDK getInstance() {
        if (instance == null) {
            instance = new DahuaSDK();
        }
        return instance;
    }

    @Override
    public boolean init(Config.SdkConfig config) {
        if (initialized) {
            logger.debug("大华SDK已经初始化，跳过重复初始化");
            return true;
        }

        this.sdkConfig = config;

        try {
            // 加载大华SDK库（使用绝对路径，避免静态初始化问题）
            if (!loadLibrary()) {
                logger.warn("大华SDK库加载失败（可能原因：库文件不存在或架构不匹配），跳过初始化");
                return false;
            }

            // 调用CLIENT_Init
            boolean initResult = netsdk.CLIENT_Init(null, null);

            if (!initResult) {
                logger.error("大华SDK初始化失败（SDK库加载成功但SDK初始化失败）");
                return false;
            }

            // 设置连接超时
            netsdk.CLIENT_SetConnectTime(10000, 3);

            // 设置消息回调（用于设备状态监听）
            // 注意：此时deviceManager和mqttClient可能还未设置，稍后通过setStatusCallbacks更新
            MessCallBackImpl messCallBack = new MessCallBackImpl();
            netsdk.CLIENT_SetDVRMessCallBack(messCallBack, null);
            logger.debug("大华SDK消息回调已设置");

            initialized = true;
            logger.info("大华SDK初始化成功");
            return true;

        } catch (Exception e) {
            logger.error("大华SDK初始化异常", e);
            return false;
        }
    }

    /**
     * 加载SDK库
     * 使用绝对路径直接加载，避免NetSDKLib静态初始化时路径未设置的问题
     */
    private boolean loadLibrary() {
        if (netsdk != null) {
            return true;
        }

        try {
            // 大华SDK库文件在 ./lib/{arch}/dahua/ 目录下
            // 使用架构区分的目录结构
            String libDir = com.digital.video.gateway.Common.LibraryPathHelper.getSDKLibPath("dahua");
            if (libDir == null) {
                logger.error("无法获取大华SDK库路径");
                return false;
            }
            logger.debug("大华SDK库路径: {} (架构: {})", libDir,
                    com.digital.video.gateway.Common.LibraryPathHelper.getArchitectureDir());
            String libPath = libDir + "/libdhnetsdk.so";

            File libFile = new File(libPath);
            if (!libFile.exists()) {
                logger.error("大华SDK库文件不存在: {}", libPath);
                return false;
            }

            // 检查库文件架构是否与系统架构匹配
            if (!ArchitectureChecker.checkArchitecture(libFile)) {
                logger.warn("大华SDK库文件架构不匹配，跳过加载");
                return false;
            }

            // 设置库路径到java.library.path（用于加载依赖库）
            // 使用LibraryPathHelper构建完整的库路径
            String newLibPath = com.digital.video.gateway.Common.LibraryPathHelper.buildLibraryPath();
            System.setProperty("java.library.path", newLibPath);
            logger.debug("设置java.library.path: {}", newLibPath);

            // 使用绝对路径直接加载NetSDKLib，而不是使用静态的NETSDK_INSTANCE
            // 这样可以避免静态初始化时路径未设置的问题
            try {
                netsdk = (NetSDKLib) Native.load(libPath, NetSDKLib.class);
                logger.info("大华SDK库加载成功，库路径: {}", libPath);
                return true;
            } catch (UnsatisfiedLinkError e) {
                logger.error("加载大华SDK库失败，库路径: {}，错误: {}", libPath, e.getMessage());
                // 如果直接加载失败，尝试使用LibraryLoad方式（作为备选）
                try {
                    com.digital.video.gateway.dahua.lib.LibraryLoad.setExtractPath(libDir);
                    String loadPath = com.digital.video.gateway.dahua.lib.LibraryLoad.getLoadLibrary("dhnetsdk");
                    netsdk = (NetSDKLib) Native.load(loadPath, NetSDKLib.class);
                    logger.info("大华SDK库加载成功（使用LibraryLoad方式），库路径: {}", loadPath);
                    return true;
                } catch (Exception e2) {
                    logger.error("使用LibraryLoad方式加载也失败: {}", e2.getMessage());
                    return false;
                }
            }

        } catch (Exception e) {
            logger.error("加载大华SDK库异常", e);
            return false;
        }
    }

    @Override
    public int login(String ip, int port, String username, String password) {
        if (!initialized || netsdk == null) {
            logger.error("大华SDK未初始化");
            return -1;
        }

        try {
            // 创建登录参数
            NetSDKLib.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY pstInParam = new NetSDKLib.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY();
            pstInParam.nPort = port;
            pstInParam.szIP = ip.getBytes();
            pstInParam.szUserName = username.getBytes();
            pstInParam.szPassword = password.getBytes();

            // 创建输出参数
            NetSDKLib.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY pstOutParam = new NetSDKLib.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY();

            logger.info("开始登录大华设备: {}:{}, 用户: {}", ip, port, username);

            // 调用登录接口
            NetSDKLib.LLong loginHandle = netsdk.CLIENT_LoginWithHighLevelSecurity(pstInParam, pstOutParam);

            if (loginHandle.longValue() == 0) {
                String errorMsg = ToolKits.getErrorCodePrint();
                logger.error("大华设备登录失败: {}:{}, 错误: {}", ip, port, errorMsg);
                return -1;
            }

            // 分配一个userId并存储登录句柄
            int userId = nextUserId++;
            loginHandles.put(userId, loginHandle);
            handleToUserIdMap.put(loginHandle.longValue(), userId);

            logger.info("大华设备登录成功: {}:{} (userId: {}, handle: {})", ip, port, userId, loginHandle.longValue());
            return userId;

        } catch (Exception e) {
            logger.error("大华设备登录异常: {}:{}", ip, port, e);
            return -1;
        }
    }

    @Override
    public boolean logout(int userId) {
        if (!initialized || netsdk == null) {
            return false;
        }

        NetSDKLib.LLong loginHandle = loginHandles.get(userId);
        if (loginHandle == null) {
            logger.warn("大华设备登录句柄不存在: {}", userId);
            return false;
        }

        try {
            boolean result = netsdk.CLIENT_Logout(loginHandle);
            if (result) {
                loginHandles.remove(userId);
                handleToUserIdMap.remove(loginHandle.longValue());
                logger.info("大华设备登出成功: {}", userId);
            } else {
                logger.error("大华设备登出失败: {}", userId);
            }
            return result;

        } catch (Exception e) {
            logger.error("大华设备登出异常: {}", userId, e);
            return false;
        }
    }

    @Override
    public int getLastError() {
        try {
            if (netsdk != null) {
                return netsdk.CLIENT_GetLastError();
            }
            return -1;
        } catch (Exception e) {
            logger.error("获取大华SDK错误码异常", e);
            return -1;
        }
    }

    @Override
    public String getLastErrorString() {
        try {
            return ToolKits.getErrorCodePrint();
        } catch (Exception e) {
            int errorCode = getLastError();
            return "错误码: " + errorCode;
        }
    }

    @Override
    public void cleanup() {
        if (initialized && netsdk != null) {
            try {
                // 登出所有设备
                for (Integer userId : loginHandles.keySet()) {
                    logout(userId);
                }

                // 调用CLIENT_Cleanup
                netsdk.CLIENT_Cleanup();

                initialized = false;
                logger.info("大华SDK清理完成");

            } catch (Exception e) {
                logger.error("大华SDK清理异常", e);
            }
        }
    }

    @Override
    public String getBrand() {
        return "dahua";
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 设置DeviceManager和MqttClient（用于状态回调）
     */
    public void setStatusCallbacks(DeviceManager deviceManager, MqttClient mqttClient) {
        this.deviceManager = deviceManager;
        this.mqttClient = mqttClient;
        // 重新设置消息回调，确保回调可以访问最新的deviceManager和mqttClient
        if (netsdk != null) {
            MessCallBackImpl messCallBack = new MessCallBackImpl();
            netsdk.CLIENT_SetDVRMessCallBack(messCallBack, null);
        }
        logger.debug("已设置状态回调：DeviceManager和MqttClient");
    }

    /**
     * 大华SDK消息回调实现
     * 用于监听设备离线/在线事件
     */
    class MessCallBackImpl implements NetSDKLib.fMessCallBack {
        private final Logger logger = LoggerFactory.getLogger(MessCallBackImpl.class);

        @Override
        public boolean invoke(int lCommand, NetSDKLib.LLong lLoginID, Pointer pBuf, int dwBufLen, String szDeviceIP,
                com.sun.jna.NativeLong nDevicePort, Pointer dwUser) {
            try {
                // 通过loginHandle查找userId
                Integer userId = handleToUserIdMap.get(lLoginID.longValue());
                if (userId == null) {
                    logger.debug("无法通过loginHandle找到userId: {}", lLoginID.longValue());
                    return false;
                }

                logger.debug("大华SDK消息回调 - 命令: 0x{}, userId: {}, IP: {}",
                        Integer.toHexString(lCommand), userId, szDeviceIP);

                // 处理设备状态变化事件
                // 注意：大华SDK的设备状态变化可能通过不同的命令码来通知
                // 由于SDK文档不完整，我们主要依赖定期检查登录状态来判断设备是否离线
                // 如果收到明确的错误或异常消息，可以判断为设备离线
                // 这里先记录日志，后续可以根据实际SDK文档来完善

                return true;
            } catch (Exception e) {
                logger.error("处理大华SDK消息回调失败", e);
                return false;
            }
        }

        /**
         * 处理设备离线事件
         */
        private void handleDeviceOffline(int userId) {
            if (deviceManager == null) {
                logger.debug("DeviceManager未设置，跳过设备离线处理");
                return;
            }

            try {
                // 通过userId查找deviceId
                String deviceId = deviceManager.getDeviceIdByUserId(userId);
                if (deviceId == null) {
                    logger.debug("无法通过userId找到deviceId: {}", userId);
                    return;
                }

                // 更新设备状态为离线并发送MQTT通知
                deviceManager.updateDeviceStatusWithNotification(deviceId, 0);
                logger.info("设备离线事件已处理: {} (userId: {})", deviceId, userId);
            } catch (Exception e) {
                logger.error("处理设备离线事件失败: userId={}", userId, e);
            }
        }
    }
}
