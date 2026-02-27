package com.digital.video.gateway.hikvision;

import com.digital.video.gateway.Common.ArchitectureChecker;
import com.digital.video.gateway.Common.LibraryPathHelper;
import com.digital.video.gateway.Common.osSelect;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.mqtt.MqttClient;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 海康威视SDK封装类
 * 综合性数字视频监控网关系统 - 海康威视设备支持
 */
public class HikvisionSDK implements DeviceSDK {
    private static final Logger logger = LoggerFactory.getLogger(HikvisionSDK.class);
    private static HikvisionSDK instance;
    private HCNetSDK hCNetSDK;
    private boolean initialized = false;
    private Config.SdkConfig sdkConfig;
    private DeviceManager deviceManager;
    private MqttClient mqttClient;
    private com.digital.video.gateway.service.AlarmService alarmService;
    
    // 存储每个设备的布防句柄，key为userId，value为alarmHandle
    private final java.util.concurrent.ConcurrentHashMap<Integer, Integer> alarmHandles = new java.util.concurrent.ConcurrentHashMap<>();
    
    // 保持报警回调的强引用，防止被GC回收导致native回调失效
    private AlarmMessageCallback alarmCallback;
    // 保持异常回调的强引用，防止被GC回收导致native回调野指针 → SIGSEGV
    private FExceptionCallBack_Imp exceptionCallback;

    /** 海康 SDK 调用串行到单线程执行，避免多线程并发导致 native 崩溃；见 server/docs/hikvision-sdk-usage-and-thread-model.md */
    private volatile ExecutorService hikvisionExecutor;

    private static final int LOGIN_TIMEOUT_MS = 30_000;
    private static final int CAPTURE_TIMEOUT_MS = 15_000;
    private static final int DEFAULT_SDK_TIMEOUT_MS = 10_000;

    private HikvisionSDK() {
    }

    public static synchronized HikvisionSDK getInstance() {
        if (instance == null) {
            instance = new HikvisionSDK();
        }
        return instance;
    }

    /**
     * 初始化SDK
     */
    public boolean init() {
        return init(null);
    }

    /**
     * 初始化SDK
     */
    @Override
    public boolean init(Config.SdkConfig config) {
        if (initialized) {
            logger.debug("海康SDK已经初始化，跳过重复初始化");
            return true;
        }

        this.sdkConfig = config;

        try {
            // 加载SDK库
            if (!loadLibrary()) {
                logger.warn("海康SDK库加载失败（可能原因：库文件不存在或架构不匹配），跳过初始化");
                return false;
            }

            // Linux系统需要设置库路径
            if (osSelect.isLinux() && config != null) {
                setupLinuxLibraries(config);
            }

            // SDK初始化
            if (!hCNetSDK.NET_DVR_Init()) {
                logger.error("海康SDK初始化失败，错误码: {}（SDK库加载成功但SDK初始化失败）", getLastError());
                return false;
            }

            // 设置异常回调（保持为字段强引用，防止 GC 导致 SIGSEGV）
            this.exceptionCallback = new FExceptionCallBack_Imp();
            if (!hCNetSDK.NET_DVR_SetExceptionCallBack_V30(0, 0, this.exceptionCallback, null)) {
                logger.warn("设置异常回调失败");
            }

            // 设置日志
            if (config != null && config.getLogPath() != null) {
                hCNetSDK.NET_DVR_SetLogToFile(
                        config.getLogLevel(),
                        config.getLogPath(),
                        false);
            }

            initialized = true;
            hikvisionExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "HikvisionSDK-Worker");
                t.setDaemon(false);
                return t;
            });
            logger.info("海康SDK初始化成功");
            return true;

        } catch (Exception e) {
            logger.error("海康SDK初始化异常", e);
            return false;
        }
    }

    /**
     * 在海康专用单线程执行器上执行任务，带超时与异常隔离；避免多线程并发调用 SDK 导致 native 崩溃。
     */
    private <T> T runOnExecutor(long timeoutMs, Callable<T> task, T timeoutOrErrorFallback) {
        if (hikvisionExecutor == null || hCNetSDK == null) {
            logger.warn("海康SDK未初始化或执行器未就绪");
            return timeoutOrErrorFallback;
        }
        Future<T> future = hikvisionExecutor.submit(task);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.warn("海康SDK调用超时 ({}ms)", timeoutMs);
            return timeoutOrErrorFallback;
        } catch (Exception e) {
            logger.error("海康SDK调用异常", e);
            return timeoutOrErrorFallback;
        }
    }

    /**
     * 加载SDK库
     */
    private boolean loadLibrary() {
        if (hCNetSDK != null) {
            return true;
        }

        try {
            // 使用架构区分的目录结构：lib/{arch}/hikvision/
            String baseLibPath;
            if (sdkConfig != null && sdkConfig.getLibPath() != null) {
                // 如果配置中提供了路径，使用LibraryPathHelper处理
                baseLibPath = LibraryPathHelper.getSDKLibPath(
                        sdkConfig.getLibPath(), "hikvision");
            } else {
                // 默认使用架构区分的路径
                baseLibPath = LibraryPathHelper.getSDKLibPath("hikvision");
            }

            logger.debug("海康SDK库路径: {} (架构: {})", baseLibPath,
                    LibraryPathHelper.getArchitectureDir());

            // 查找库文件（优先查找架构特定文件，然后查找默认文件）
            File libFile = null;
            String libFileName = "libhcnetsdk.so";

            // 首先尝试查找架构特定的库文件（如libhcnetsdk.so.x86_64）
            String osArch = System.getProperty("os.arch");
            if (osArch != null) {
                String normalizedArch = normalizeArchitecture(osArch);
                String archSpecificLib = baseLibPath + "/libhcnetsdk.so." + normalizedArch;
                File archSpecificFile = new File(archSpecificLib);

                if (archSpecificFile.exists()) {
                    libFile = archSpecificFile;
                    libFileName = "libhcnetsdk.so." + normalizedArch;
                    logger.debug("检测到架构特定库文件: {}", libFileName);
                }
            }

            // 如果架构特定库不存在，尝试使用默认库文件
            if (libFile == null || !libFile.exists()) {
                String defaultLibPath = baseLibPath + "/libhcnetsdk.so";
                libFile = new File(defaultLibPath);
                if (!libFile.exists()) {
                    logger.error("SDK库文件不存在: {} (已尝试架构特定文件: {})",
                            defaultLibPath, baseLibPath + "/libhcnetsdk.so." +
                                    (osArch != null ? normalizeArchitecture(osArch) : "unknown"));
                    return false;
                }
                libFileName = "libhcnetsdk.so";
            }

            // 检查库文件架构是否与系统架构匹配
            if (!ArchitectureChecker.checkArchitecture(libFile)) {
                logger.warn("海康SDK库文件架构不匹配，跳过加载");
                return false;
            }

            // 设置库路径到java.library.path（用于JNA查找库文件和依赖库，含 HCNetSDKCom 等）
            String libDir = libFile.getParent();
            String newLibPath = LibraryPathHelper.buildLibraryPath();
            System.setProperty("java.library.path", newLibPath);
            logger.debug("设置java.library.path: {}", newLibPath);

            // 使用绝对路径直接加载库文件，确保能找到正确的架构版本
            // 这样可以避免JNA在java.library.path中查找时的问题
            String absoluteLibPath = libFile.getAbsolutePath();

            // 如果使用的是架构特定的库文件（如libhcnetsdk.so.x86_64），
            // 需要确保libhcnetsdk.so符号链接指向它，或者直接使用绝对路径加载
            try {
                // 尝试使用绝对路径加载
                hCNetSDK = (HCNetSDK) Native.load(absoluteLibPath, HCNetSDK.class);
                logger.debug("使用绝对路径加载库成功: {}", absoluteLibPath);
            } catch (UnsatisfiedLinkError e) {
                // 如果绝对路径加载失败，尝试使用库名加载（JNA会从java.library.path中查找）
                // 提取基础库名：hcnetsdk
                String libraryName = libFileName;
                if (libraryName.startsWith("lib")) {
                    libraryName = libraryName.substring(3);
                }
                if (libraryName.endsWith(".so")) {
                    libraryName = libraryName.substring(0, libraryName.length() - 3);
                }
                // 如果还有架构后缀，去掉
                if (libraryName.contains(".")) {
                    libraryName = libraryName.substring(0, libraryName.indexOf("."));
                }
                logger.debug("尝试使用库名加载: {}", libraryName);
                NativeLibrary.addSearchPath(libraryName, libDir);
                hCNetSDK = (HCNetSDK) Native.load(libraryName, HCNetSDK.class);
            }
            logger.info("SDK库加载成功: {} (系统架构: {}, 库文件名: {})", libFile.getAbsolutePath(), osArch, libFileName);
            return true;

        } catch (Exception e) {
            logger.error("加载SDK库异常", e);
            return false;
        }
    }

    /**
     * 标准化架构名称
     */
    private String normalizeArchitecture(String arch) {
        if (arch == null) {
            return "unknown";
        }

        arch = arch.toLowerCase().trim();

        // ARM架构
        if (arch.contains("aarch64") || arch.equals("arm64")) {
            return "aarch64";
        }
        if (arch.contains("arm")) {
            return "arm";
        }

        // x86架构
        if (arch.contains("x86_64") || arch.contains("x86-64") || arch.equals("amd64")) {
            return "x86_64";
        }
        if (arch.contains("x86") || arch.contains("i386") || arch.contains("i686")) {
            return "x86";
        }

        return arch;
    }

    /**
     * 设置Linux系统库路径
     */
    private void setupLinuxLibraries(Config.SdkConfig config) {
        try {
            // 使用架构区分的目录结构：lib/{arch}/hikvision/
            String libPath;
            if (config.getLibPath() != null) {
                libPath = LibraryPathHelper.getSDKLibPath(
                        config.getLibPath(), "hikvision");
            } else {
                libPath = LibraryPathHelper.getSDKLibPath("hikvision");
            }

            // 确保路径是绝对路径
            File libPathFile = new File(libPath);
            if (!libPathFile.isAbsolute()) {
                libPath = libPathFile.getAbsolutePath();
            }

            logger.debug("设置海康SDK库路径: {}", libPath);

            // 设置crypto和ssl库路径
            String cryptoPath = libPath + "/libcrypto.so.1.1";
            String sslPath = libPath + "/libssl.so.1.1";

            // 检查文件是否存在
            File cryptoFile = new File(cryptoPath);
            File sslFile = new File(sslPath);
            if (!cryptoFile.exists()) {
                logger.warn("libcrypto.so.1.1不存在: {}", cryptoPath);
            }
            if (!sslFile.exists()) {
                logger.warn("libssl.so.1.1不存在: {}", sslPath);
            }

            HCNetSDK.BYTE_ARRAY ptrByteArray1 = new HCNetSDK.BYTE_ARRAY(256);
            HCNetSDK.BYTE_ARRAY ptrByteArray2 = new HCNetSDK.BYTE_ARRAY(256);

            System.arraycopy(cryptoPath.getBytes(), 0, ptrByteArray1.byValue, 0, Math.min(cryptoPath.length(), 255));
            ptrByteArray1.write();
            hCNetSDK.NET_DVR_SetSDKInitCfg(HCNetSDK.NET_SDK_INIT_CFG_LIBEAY_PATH, ptrByteArray1.getPointer());

            System.arraycopy(sslPath.getBytes(), 0, ptrByteArray2.byValue, 0, Math.min(sslPath.length(), 255));
            ptrByteArray2.write();
            hCNetSDK.NET_DVR_SetSDKInitCfg(HCNetSDK.NET_SDK_INIT_CFG_SSLEAY_PATH, ptrByteArray2.getPointer());

            // 设置组件库路径
            // 根据海康SDK文档：NET_SDK_INIT_CFG_SDK_PATH应该设置为包含HCNetSDKCom目录的父目录
            // 例如：如果HCNetSDKCom在 /path/to/lib/hikvision/HCNetSDKCom
            // 则应该设置为 /path/to/lib/hikvision
            String strPathCom = libPath; // lib/hikvision目录
            HCNetSDK.NET_DVR_LOCAL_SDK_PATH struComPath = new HCNetSDK.NET_DVR_LOCAL_SDK_PATH();
            byte[] pathBytes = strPathCom.getBytes();
            int copyLen = Math.min(pathBytes.length, struComPath.sPath.length - 1);
            System.arraycopy(pathBytes, 0, struComPath.sPath, 0, copyLen);
            struComPath.sPath[copyLen] = 0; // 确保以null结尾
            struComPath.write();
            hCNetSDK.NET_DVR_SetSDKInitCfg(HCNetSDK.NET_SDK_INIT_CFG_SDK_PATH, struComPath.getPointer());

            logger.info("Linux库路径设置成功: libPath={}, HCNetSDKCom父目录={}", libPath, strPathCom);
            logger.debug("HCNetSDKCom目录应该在: {}/HCNetSDKCom", strPathCom);

        } catch (Exception e) {
            logger.error("设置Linux库路径异常", e);
        }
    }

    /**
     * 清理SDK资源
     */
    @Override
    public void cleanup() {
        if (hikvisionExecutor != null) {
            hikvisionExecutor.shutdown();
            try {
                if (!hikvisionExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    hikvisionExecutor.shutdownNow();
                    hikvisionExecutor.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                hikvisionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            hikvisionExecutor = null;
        }
        if (initialized && hCNetSDK != null) {
            hCNetSDK.NET_DVR_Cleanup();
            initialized = false;
            logger.info("SDK清理完成");
        }
    }

    /**
     * 获取最后错误码
     */
    @Override
    public int getLastError() {
        if (hCNetSDK == null) {
            return -1;
        }
        return hCNetSDK.NET_DVR_GetLastError();
    }

    /**
     * 获取最后错误信息（字符串描述）
     */
    @Override
    public String getLastErrorString() {
        int errorCode = getLastError();
        if (errorCode == 0) {
            return "没有错误";
        }
        switch (errorCode) {
            case HCNetSDK.NET_DVR_PASSWORD_ERROR:
                return "用户名或密码错误";
            case HCNetSDK.NET_DVR_NOENOUGHPRI:
                return "权限不足";
            case HCNetSDK.NET_DVR_NOINIT:
                return "没有初始化";
            case HCNetSDK.NET_DVR_CHANNEL_ERROR:
                return "通道号错误";
            case HCNetSDK.NET_DVR_OVER_MAXLINK:
                return "连接到DVR的客户端个数超过最大";
            case HCNetSDK.NET_DVR_VERSIONNOMATCH:
                return "版本不匹配";
            case HCNetSDK.NET_DVR_NETWORK_FAIL_CONNECT:
                return "连接服务器失败";
            case HCNetSDK.NET_DVR_NETWORK_SEND_ERROR:
                return "向服务器发送失败";
            case HCNetSDK.NET_DVR_NETWORK_RECV_ERROR:
                return "从服务器接收数据失败";
            case HCNetSDK.NET_DVR_NETWORK_RECV_TIMEOUT:
                return "从服务器接收数据超时";
            default:
                return "未知错误，错误码: " + errorCode;
        }
    }

    /**
     * 获取品牌名称
     */
    @Override
    public String getBrand() {
        return "hikvision";
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 健康探针：执行器是否存活（用于 Keeper/SDK 健康检查）
     */
    public boolean isExecutorAlive() {
        return hikvisionExecutor != null && !hikvisionExecutor.isShutdown();
    }

    /**
     * 登录设备（经单线程执行器，带超时）
     */
    @Override
    public int login(String ip, int port, String username, String password) {
        if (!initialized || hCNetSDK == null) {
            logger.error("SDK未初始化");
            return -1;
        }
        return runOnExecutor(LOGIN_TIMEOUT_MS, () -> {
            int userID = loginImpl(ip, port, username, password);
            if (userID >= 0 && alarmService != null) {
                int alarmHandle = setupAlarmChanImpl(userID);
                if (alarmHandle >= 0) {
                    logger.info("设备已自动布防: {}:{} (userId: {}, alarmHandle: {})", ip, port, userID, alarmHandle);
                } else {
                    logger.warn("设备自动布防失败: {}:{} (userId: {})", ip, port, userID);
                }
            }
            return userID;
        }, -1);
    }

    /** 仅在执行器线程内调用 */
    private int loginImpl(String ip, int port, String username, String password) {
        HCNetSDK.NET_DVR_USER_LOGIN_INFO loginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();
        HCNetSDK.NET_DVR_DEVICEINFO_V40 deviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V40();
        byte[] deviceAddress = new byte[HCNetSDK.NET_DVR_DEV_ADDRESS_MAX_LEN];
        byte[] ipBytes = ip.getBytes();
        System.arraycopy(ipBytes, 0, deviceAddress, 0, Math.min(ipBytes.length, deviceAddress.length));
        loginInfo.sDeviceAddress = deviceAddress;
        byte[] userName = new byte[HCNetSDK.NET_DVR_LOGIN_USERNAME_MAX_LEN];
        byte[] userBytes = username.getBytes();
        System.arraycopy(userBytes, 0, userName, 0, Math.min(userBytes.length, userName.length));
        loginInfo.sUserName = userName;
        byte[] passwordBytes = password.getBytes();
        System.arraycopy(passwordBytes, 0, loginInfo.sPassword, 0,
                Math.min(passwordBytes.length, loginInfo.sPassword.length));
        loginInfo.wPort = (short) port;
        loginInfo.bUseAsynLogin = false;
        loginInfo.byLoginMode = 0;
        loginInfo.byUseTransport = 0;
        loginInfo.byProxyType = 0;
        loginInfo.byHttps = 0;
        hCNetSDK.NET_DVR_SetConnectTime(10000, 3);
        loginInfo.write();
        deviceInfo.write();
        logger.info("开始登录设备: {}:{}, 用户: {}", ip, port, username);
        int userID = hCNetSDK.NET_DVR_Login_V40(loginInfo, deviceInfo);
        deviceInfo.read();
        logger.debug("登录调用返回: userID={}", userID);
        if (deviceInfo.struDeviceV30 != null) {
            int chanNum = deviceInfo.struDeviceV30.byChanNum & 0xFF;
            int startDChan = deviceInfo.struDeviceV30.byStartDChan & 0xFF;
            logger.debug("设备信息: 通道数={}, 起始通道={}", chanNum, startDChan);
        }
        if (userID == -1) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            logger.error("登录失败，错误码: {} (IP: {}:{}, 用户: {})", errorCode, ip, port, username);
            if (errorCode == HCNetSDK.NET_DVR_PASSWORD_ERROR) {
                logger.error("错误原因: 用户名或密码错误");
            } else if (errorCode == HCNetSDK.NET_DVR_NETWORK_FAIL_CONNECT) {
                logger.error("错误原因: 连接服务器失败 (错误码: 7)");
            } else if (errorCode == HCNetSDK.NET_DVR_NETWORK_SEND_ERROR
                    || errorCode == HCNetSDK.NET_DVR_NETWORK_RECV_ERROR) {
                logger.error("错误原因: 网络通信错误，请检查网络连接和端口");
            } else if (errorCode == HCNetSDK.NET_DVR_NETWORK_RECV_TIMEOUT) {
                logger.error("错误原因: 网络接收超时，设备可能未响应");
            }
        } else {
            logger.info("设备登录成功: {}:{} (userId: {})", ip, port, userID);
        }
        return userID;
    }

    /**
     * 登出设备（经单线程执行器，带超时）
     */
    @Override
    public boolean logout(int userID) {
        if (!initialized || hCNetSDK == null) {
            return false;
        }
        return Boolean.TRUE.equals(runOnExecutor(DEFAULT_SDK_TIMEOUT_MS, () -> logoutImpl(userID), false));
    }

    /** 仅在执行器线程内调用 */
    private boolean logoutImpl(int userID) {
        closeAlarmChanImpl(userID);
        boolean result = hCNetSDK.NET_DVR_Logout_V30(userID);
        if (result) {
            logger.info("设备登出成功: {}", userID);
        } else {
            logger.error("设备登出失败，错误码: {}", getLastError());
        }
        return result;
    }

    /**
     * 设置DeviceManager和MqttClient（用于状态回调）
     */
    public void setStatusCallbacks(DeviceManager deviceManager, MqttClient mqttClient) {
        this.deviceManager = deviceManager;
        this.mqttClient = mqttClient;
        // 更新异常回调中的引用（如果回调已创建）
        if (hCNetSDK != null) {
            this.exceptionCallback = new FExceptionCallBack_Imp();
            hCNetSDK.NET_DVR_SetExceptionCallBack_V30(0, 0, this.exceptionCallback, null);
        }
        logger.debug("已设置状态回调：DeviceManager和MqttClient");
    }

    /**
     * 设置报警服务（用于报警自动抓图）
     */
    /** 供异常回调或外部获取 MQTT 客户端（如发布设备离线等状态） */
    public MqttClient getMqttClient() {
        return mqttClient;
    }

    public void setAlarmService(com.digital.video.gateway.service.AlarmService alarmService) {
        this.alarmService = alarmService;
        // 设置报警回调
        if (hCNetSDK != null && alarmService != null) {
            // 保持回调的强引用，防止被GC回收
            this.alarmCallback = new AlarmMessageCallback();
            boolean result = hCNetSDK.NET_DVR_SetDVRMessageCallBack_V30(this.alarmCallback, null);
            logger.info("海康SDK报警回调设置结果: {}", result);
            
            // 对已登录的设备进行布防
            setupAlarmForExistingDevices();
        }
    }
    
    /**
     * 对已登录的设备进行布防
     * 用于在alarmService设置后，对之前已登录但未布防的设备进行补充布防
     */
    public void setupAlarmForExistingDevices() {
        logger.info("开始对已登录设备进行布防检查...");
        
        if (deviceManager == null) {
            logger.warn("deviceManager为null，无法对已登录设备布防");
            return;
        }
        if (alarmService == null) {
            logger.warn("alarmService为null，无法对已登录设备布防");
            return;
        }
        
        try {
            List<DeviceInfo> devices = deviceManager.getAllDevices();
            logger.info("共有 {} 个设备需要检查布防状态", devices.size());
            
            for (DeviceInfo device : devices) {
                logger.debug("检查设备: {}, status={}, brand={}, userId={}", 
                        device.getDeviceId(), device.getStatus(), device.getBrand(), device.getUserId());
                        
                // status: 1=在线, 0=离线
                if (device.getStatus() == 1 && "hikvision".equalsIgnoreCase(device.getBrand())) {
                    int userId = device.getUserId();
                    if (userId >= 0 && !isAlarmSetup(userId)) {
                        logger.info("对设备进行布防: {} (userId: {})", device.getDeviceId(), userId);
                        int alarmHandle = setupAlarmChan(userId);
                        if (alarmHandle >= 0) {
                            logger.info("设备布防成功: {} (userId: {}, alarmHandle: {})", 
                                    device.getDeviceId(), userId, alarmHandle);
                        } else {
                            logger.warn("设备布防失败: {} (userId: {})", device.getDeviceId(), userId);
                        }
                    } else {
                        logger.debug("设备已布防或userId无效: {} (userId: {}, isAlarmSetup: {})", 
                                device.getDeviceId(), userId, isAlarmSetup(userId));
                    }
                }
            }
            logger.info("已登录设备布防检查完成");
        } catch (Exception e) {
            logger.error("对已登录设备布防时异常", e);
        }
    }
    
    /**
     * 对设备进行布防，建立报警通道（经单线程执行器，带超时）
     * @param userId 登录句柄
     * @return 布防句柄，失败返回-1
     */
    public int setupAlarmChan(int userId) {
        if (!initialized || hCNetSDK == null) {
            logger.error("海康SDK未初始化，无法布防");
            return -1;
        }
        return runOnExecutor(DEFAULT_SDK_TIMEOUT_MS, () -> setupAlarmChanImpl(userId), -1);
    }

    /** 仅在执行器线程内调用 */
    private int setupAlarmChanImpl(int userId) {
        Integer existingHandle = alarmHandles.get(userId);
        if (existingHandle != null && existingHandle >= 0) {
            logger.debug("设备已布防: userId={}, alarmHandle={}", userId, existingHandle);
            return existingHandle;
        }
        try {
            HCNetSDK.NET_DVR_SETUPALARM_PARAM_V50 setupParam = new HCNetSDK.NET_DVR_SETUPALARM_PARAM_V50();
            setupParam.dwSize = setupParam.size();
            setupParam.byLevel = 1;
            setupParam.byAlarmInfoType = 1;
            setupParam.byRetAlarmTypeV40 = 1;
            setupParam.byDeployType = 1;
            setupParam.write();
            int alarmHandle = hCNetSDK.NET_DVR_SetupAlarmChan_V50(userId, setupParam, null, 0);
            if (alarmHandle < 0) {
                int errorCode = getLastError();
                logger.warn("V50布防失败，尝试V41: userId={}, errorCode={}", userId, errorCode);
                HCNetSDK.NET_DVR_SETUPALARM_PARAM setupParamV41 = new HCNetSDK.NET_DVR_SETUPALARM_PARAM();
                setupParamV41.dwSize = setupParamV41.size();
                setupParamV41.byLevel = 1;
                setupParamV41.byAlarmInfoType = 1;
                setupParamV41.byDeployType = 1;
                setupParamV41.write();
                alarmHandle = hCNetSDK.NET_DVR_SetupAlarmChan_V41(userId, setupParamV41);
                if (alarmHandle < 0) {
                    errorCode = getLastError();
                    logger.warn("V41布防失败，尝试V30: userId={}, errorCode={}", userId, errorCode);
                    alarmHandle = hCNetSDK.NET_DVR_SetupAlarmChan_V30(userId);
                    if (alarmHandle < 0) {
                        logger.error("布防失败: userId={}, errorCode={}", userId, getLastError());
                        return -1;
                    }
                }
            }
            alarmHandles.put(userId, alarmHandle);
            logger.info("设备布防成功: userId={}, alarmHandle={}", userId, alarmHandle);
            return alarmHandle;
        } catch (Exception e) {
            logger.error("布防异常: userId={}", userId, e);
            return -1;
        }
    }

    /**
     * 撤防（经单线程执行器，带超时）
     */
    public boolean closeAlarmChan(int userId) {
        if (!initialized || hCNetSDK == null) {
            return true;
        }
        return Boolean.TRUE.equals(runOnExecutor(DEFAULT_SDK_TIMEOUT_MS, () -> closeAlarmChanImpl(userId), true));
    }

    /** 仅在执行器线程内调用 */
    private boolean closeAlarmChanImpl(int userId) {
        Integer alarmHandle = alarmHandles.remove(userId);
        if (alarmHandle == null || alarmHandle < 0) {
            return true;
        }
        try {
            boolean result = hCNetSDK.NET_DVR_CloseAlarmChan_V30(alarmHandle);
            if (result) {
                logger.info("设备撤防成功: userId={}, alarmHandle={}", userId, alarmHandle);
            } else {
                logger.warn("设备撤防失败: userId={}, alarmHandle={}, errorCode={}", userId, alarmHandle, getLastError());
            }
            return result;
        } catch (Exception e) {
            logger.error("撤防异常: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 检查设备是否已布防
     */
    public boolean isAlarmSetup(int userId) {
        Integer handle = alarmHandles.get(userId);
        return handle != null && handle >= 0;
    }

    /**
     * 海康SDK报警消息回调实现。回调在 SDK native 线程执行，pAlarmer/pAlarmInfo 可能为 null 且仅在回调内有效；
     * 必须先判空、访问前 pAlarmer.read()，回调内禁止调用任何海康 SDK 接口。
     */
    class AlarmMessageCallback implements HCNetSDK.FMSGCallBack {
        @Override
        public void invoke(int lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen,
                Pointer pUser) {
            try {
                if (pAlarmer == null) {
                    logger.debug("海康报警回调 pAlarmer 为 null, lCommand=0x{}", Integer.toHexString(lCommand));
                    return;
                }
                logger.info("收到海康报警回调: lCommand=0x{}, dwBufLen={}", Integer.toHexString(lCommand), dwBufLen);
                pAlarmer.read();
                String deviceIP = null;
                int userId = -1;
                if (pAlarmer.byDeviceIPValid == 1) {
                    deviceIP = new String(pAlarmer.sDeviceIP, java.nio.charset.StandardCharsets.UTF_8).trim();
                    int nullIndex = deviceIP.indexOf('\0');
                    if (nullIndex >= 0) {
                        deviceIP = deviceIP.substring(0, nullIndex);
                    }
                }
                if (pAlarmer.byUserIDValid == 1) {
                    userId = pAlarmer.lUserID;
                }
                logger.info("报警设备信息: IP={}, userId={}, byDeviceIPValid={}, byUserIDValid={}", 
                        deviceIP, userId, pAlarmer.byDeviceIPValid, pAlarmer.byUserIDValid);

                // 处理报警消息 - 支持更多报警类型
                // 常见的报警类型：
                // COMM_ALARM (0x1100) - 普通报警
                // COMM_ALARM_V30 (0x4000) - V30报警
                // COMM_ALARM_V40 (0x4007) - V40报警（包含移动侦测等）
                // COMM_ALARM_RULE (0x1102) - 行为分析报警
                // COMM_SWITCH_ALARM (0x1108) - 开关量报警
                boolean isAlarmCommand = (lCommand == HCNetSDK.COMM_ALARM ||
                        lCommand == HCNetSDK.COMM_ALARM_V30 ||
                        lCommand == HCNetSDK.COMM_ALARM_V40 ||
                        lCommand == HCNetSDK.COMM_ALARM_RULE ||
                        lCommand == HCNetSDK.COMM_SWITCH_ALARM);
                
                // 也处理其他可能的报警类型
                if (!isAlarmCommand) {
                    // 检查是否是其他类型的报警（如智能报警等）
                    // 0x4000-0x4FFF 范围内的都可能是报警
                    if (lCommand >= 0x4000 && lCommand <= 0x4FFF) {
                        isAlarmCommand = true;
                    }
                }
                
                if (isAlarmCommand && deviceIP != null) {
                    int channel = 1; // 默认通道1
                    String alarmType;
                    // 三种基础报警结构体不同，统一用 parseAlarmInfoBase 解析 dwAlarmType + 通道
                    int[] parsed = parseAlarmInfoBase(lCommand, pAlarmInfo, dwBufLen);
                    if (parsed != null) {
                        int dwAlarmType = parsed[0];
                        channel = parsed[1];
                        alarmType = "Hikvision_Alarm_" + dwAlarmType;
                        logger.info("海康报警解析: lCommand=0x{}, dwAlarmType={}, channel={}", Integer.toHexString(lCommand), dwAlarmType, channel);
                    } else {
                        alarmType = getAlarmTypeName(lCommand);
                    }

                    // 只通过IP匹配设备（userId在同品牌和不同品牌间都可能重复）
                    if (deviceManager != null && alarmService != null) {
                        String deviceId = null;
                        
                        // 通过IP查找设备
                        java.util.List<DeviceInfo> devices = deviceManager.getAllDevices();
                        for (DeviceInfo device : devices) {
                            if (deviceIP.equals(device.getIp())) {
                                deviceId = device.getDeviceId();
                                break;
                            }
                        }

                        if (deviceId != null) {
                            String alarmMessage = "海康报警: " + alarmType;
                            
                            // 特殊处理GIS信息上传
                            java.util.Map<String, Object> alarmData = null;
                            if (lCommand == HCNetSDK.COMM_GISINFO_UPLOAD && pAlarmInfo != null && dwBufLen > 0) {
                                try {
                                    alarmData = parseGisInfo(pAlarmInfo, dwBufLen);
                                    if (alarmData != null) {
                                        alarmMessage = buildGisAlarmMessage(alarmData);
                                        // 打印详细的GIS信息到日志
                                        logger.info("========== GIS信息解析成功 ==========");
                                        logger.info("设备ID: {}", deviceId);
                                        logger.info("经纬度: {} ({}, {})", 
                                            alarmData.get("latitude") + ", " + alarmData.get("longitude"),
                                            alarmData.get("latitudeDecimal"), alarmData.get("longitudeDecimal"));
                                        logger.info("方位角: {}° ({})", 
                                            alarmData.get("azimuth"), alarmData.get("azimuthDescription"));
                                        if (alarmData.containsKey("ptz")) {
                                            @SuppressWarnings("unchecked")
                                            java.util.Map<String, Object> ptz = (java.util.Map<String, Object>) alarmData.get("ptz");
                                            logger.info("PTZ坐标: 水平={}° 垂直={}° 变倍={}x", 
                                                ptz.get("panPos"), ptz.get("tiltPos"), ptz.get("zoomPos"));
                                        }
                                        logger.info("视场角: 水平={}° 垂直={}° 可视半径={}m", 
                                            alarmData.get("horizontalFov"), alarmData.get("verticalFov"), alarmData.get("visibleRadius"));
                                        if (alarmData.containsKey("sensor")) {
                                            @SuppressWarnings("unchecked")
                                            java.util.Map<String, Object> sensor = (java.util.Map<String, Object>) alarmData.get("sensor");
                                            logger.info("传感器: 类型={} 水平宽度={} 垂直宽度={} 焦距={}", 
                                                sensor.get("sensorType"), sensor.get("horWidth"), sensor.get("verWidth"), sensor.get("focalLength"));
                                        }
                                        logger.info("=====================================");
                                    }
                                } catch (Exception e) {
                                    logger.error("解析GIS信息失败", e);
                                }
                            }
                            
                            logger.info("处理报警: deviceId={}, channel={}, alarmType={}", deviceId, channel, alarmType);
                            alarmService.handleAlarm(deviceId, channel, alarmType, alarmMessage, alarmData);
                        } else {
                            logger.warn("报警回调无法找到设备: IP={}, userId={}", deviceIP, userId);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("处理报警回调异常", e);
            }
        }
        
        /**
         * 统一解析“基础报警”的 pAlarmInfo，得到 dwAlarmType 与通道。三种命令对应结构体不同，不能共用同一解析：
         * - COMM_ALARM (0x1100) 8000 系列 → NET_DVR_ALARMINFO（int[] 数组）
         * - COMM_ALARM_V30 (0x4000) 9000 系列 → NET_DVR_ALARMINFO_V30（byte[] 数组）
         * - COMM_ALARM_V40 (0x4007) → NET_DVR_ALARMINFO_V40（固定头 + pAlarmData，只读固定头即可）
         * dwAlarmType: 0=信号量/报警输入, 1=硬盘满, 2=信号丢失, 3=移动侦测, 4=硬盘未格式化, 5=读写硬盘出错, 6=遮挡报警, 7=制式不匹配, 8=非法访问, 0xa=GPS(车载)。
         * @return int[2] = { dwAlarmType, channel }，解析失败返回 null
         */
        private int[] parseAlarmInfoBase(int lCommand, Pointer pAlarmInfo, int dwBufLen) {
            if (pAlarmInfo == null) {
                return null;
            }
            if (lCommand == HCNetSDK.COMM_ALARM_V30) {
                return parseAlarmInfoV30(pAlarmInfo, dwBufLen);
            }
            if (lCommand == HCNetSDK.COMM_ALARM) {
                return parseAlarmInfo8000(pAlarmInfo, dwBufLen);
            }
            if (lCommand == HCNetSDK.COMM_ALARM_V40) {
                return parseAlarmInfoV40(pAlarmInfo, dwBufLen);
            }
            return null;
        }

        private int[] parseAlarmInfoV30(Pointer pAlarmInfo, int dwBufLen) {
            try {
                HCNetSDK.NET_DVR_ALARMINFO_V30 stru = new HCNetSDK.NET_DVR_ALARMINFO_V30();
                if (dwBufLen < stru.size()) {
                    return null;
                }
                byte[] buf = pAlarmInfo.getByteArray(0, stru.size());
                Pointer p = stru.getPointer();
                if (p == null) return null;
                p.write(0, buf, 0, buf.length);
                stru.read();
                int channel = 1;
                for (int i = 0; i < stru.byChannel.length; i++) {
                    if (stru.byChannel[i] != 0) {
                        channel = i + 1;
                        break;
                    }
                }
                return new int[] { stru.dwAlarmType, channel };
            } catch (Exception e) {
                logger.debug("解析 NET_DVR_ALARMINFO_V30 失败: dwBufLen={}", dwBufLen, e);
                return null;
            }
        }

        private int[] parseAlarmInfo8000(Pointer pAlarmInfo, int dwBufLen) {
            try {
                HCNetSDK.NET_DVR_ALARMINFO stru = new HCNetSDK.NET_DVR_ALARMINFO();
                if (dwBufLen < stru.size()) {
                    return null;
                }
                byte[] buf = pAlarmInfo.getByteArray(0, stru.size());
                Pointer p = stru.getPointer();
                if (p == null) return null;
                p.write(0, buf, 0, buf.length);
                stru.read();
                int channel = 1;
                for (int i = 0; i < stru.dwChannel.length; i++) {
                    if (stru.dwChannel[i] != 0) {
                        channel = i + 1;
                        break;
                    }
                }
                return new int[] { stru.dwAlarmType, channel };
            } catch (Exception e) {
                logger.debug("解析 NET_DVR_ALARMINFO(8000) 失败: dwBufLen={}", dwBufLen, e);
                return null;
            }
        }

        private int[] parseAlarmInfoV40(Pointer pAlarmInfo, int dwBufLen) {
            try {
                HCNetSDK.NET_DVR_ALRAM_FIXED_HEADER header = new HCNetSDK.NET_DVR_ALRAM_FIXED_HEADER();
                int size = header.size();
                if (dwBufLen < size) {
                    return null;
                }
                byte[] buf = pAlarmInfo.getByteArray(0, size);
                Pointer p = header.getPointer();
                if (p == null) return null;
                p.write(0, buf, 0, buf.length);
                header.read();
                int ch = header.wDevInfoIvmsChannel & 0xFFFF;
                int channel = (ch > 0) ? ch : 1;
                return new int[] { header.dwAlarmType, channel };
            } catch (Exception e) {
                logger.debug("解析 NET_DVR_ALARMINFO_V40 固定头失败: dwBufLen={}", dwBufLen, e);
                return null;
            }
        }

        /**
         * 获取报警类型名称
         */
        private String getAlarmTypeName(int lCommand) {
            switch (lCommand) {
                case HCNetSDK.COMM_ALARM:
                    return "MOTION_DETECTION"; // 移动侦测
                case HCNetSDK.COMM_ALARM_V30:
                    return "ALARM_V30";
                case HCNetSDK.COMM_ALARM_V40:
                    return "MOTION_DETECTION"; // V40也常用于移动侦测
                case HCNetSDK.COMM_ALARM_RULE:
                    return "BEHAVIOR_ANALYSIS"; // 行为分析
                case HCNetSDK.COMM_SWITCH_ALARM:
                    return "IO_ALARM"; // 开关量报警
                case HCNetSDK.COMM_GISINFO_UPLOAD:
                    return "GIS_INFO_UPLOAD"; // GIS信息上传
                default:
                    return "ALARM_0x" + Integer.toHexString(lCommand);
            }
        }
        
        /**
         * 解析GIS信息结构体
         */
        private java.util.Map<String, Object> parseGisInfo(com.sun.jna.Pointer pAlarmInfo, int dwBufLen) {
            try {
                if (pAlarmInfo == null || dwBufLen < 4) {
                    logger.warn("GIS信息指针为空或长度不足: dwBufLen={}", dwBufLen);
                    return null;
                }
                
                // 创建GIS信息结构体并读取数据
                HCNetSDK.NET_DVR_GIS_UPLOADINFO gisInfo = new HCNetSDK.NET_DVR_GIS_UPLOADINFO();
                // 使用Pointer直接读取数据到结构体
                com.sun.jna.Pointer gisInfoPointer = gisInfo.getPointer();
                if (gisInfoPointer != null) {
                    byte[] data = pAlarmInfo.getByteArray(0, Math.min(dwBufLen, gisInfo.size()));
                    gisInfoPointer.write(0, data, 0, data.length);
                    gisInfo.read();
                } else {
                    logger.warn("无法获取GIS结构体指针");
                    return null;
                }
                
                java.util.Map<String, Object> gisData = new java.util.HashMap<>();
                
                // 1. 经纬度信息
                String latitudeStr = formatLatitude(gisInfo.struLatitude, gisInfo.byLatitudeType);
                String longitudeStr = formatLongitude(gisInfo.struLongitude, gisInfo.byLongitudeType);
                gisData.put("latitude", latitudeStr);
                gisData.put("longitude", longitudeStr);
                gisData.put("latitudeType", gisInfo.byLatitudeType == 0 ? "北纬" : "南纬");
                gisData.put("longitudeType", gisInfo.byLongitudeType == 0 ? "东经" : "西经");
                
                // 转换为十进制度数（用于地图显示）
                double latitudeDecimal = convertToDecimal(gisInfo.struLatitude.byDegree, 
                    gisInfo.struLatitude.byMinute, gisInfo.struLatitude.fSec);
                if (gisInfo.byLatitudeType == 1) latitudeDecimal = -latitudeDecimal; // 南纬为负
                
                double longitudeDecimal = convertToDecimal(gisInfo.struLongitude.byDegree, 
                    gisInfo.struLongitude.byMinute, gisInfo.struLongitude.fSec);
                if (gisInfo.byLongitudeType == 1) longitudeDecimal = -longitudeDecimal; // 西经为负
                
                gisData.put("latitudeDecimal", latitudeDecimal);
                gisData.put("longitudeDecimal", longitudeDecimal);
                
                // 2. 方位角（摄像头朝向）
                gisData.put("azimuth", gisInfo.fAzimuth);
                gisData.put("azimuthDescription", formatAzimuth(gisInfo.fAzimuth));
                
                // 3. PTZ坐标
                if (gisInfo.struPtzPos != null) {
                    java.util.Map<String, Object> ptzData = new java.util.HashMap<>();
                    ptzData.put("panPos", gisInfo.struPtzPos.fPanPos);      // 水平角度
                    ptzData.put("tiltPos", gisInfo.struPtzPos.fTiltPos);    // 垂直角度
                    ptzData.put("zoomPos", gisInfo.struPtzPos.fZoomPos);    // 变倍倍数
                    gisData.put("ptz", ptzData);
                }
                
                // 4. 视场角信息
                gisData.put("horizontalFov", gisInfo.fHorizontalValue);  // 水平视场角
                gisData.put("verticalFov", gisInfo.fVerticalValue);      // 垂直视场角
                gisData.put("visibleRadius", gisInfo.fVisibleRadius);   // 当前可视半径
                gisData.put("maxViewRadius", gisInfo.fMaxViewRadius);  // 最大可视半径
                
                // 5. Sensor信息
                if (gisInfo.struSensorParam != null) {
                    java.util.Map<String, Object> sensorData = new java.util.HashMap<>();
                    sensorData.put("sensorType", gisInfo.struSensorParam.bySensorType == 0 ? "CCD" : "CMOS");
                    sensorData.put("horWidth", gisInfo.struSensorParam.fHorWidth);
                    sensorData.put("verWidth", gisInfo.struSensorParam.fVerWidth);
                    sensorData.put("focalLength", gisInfo.struSensorParam.fFold);
                    gisData.put("sensor", sensorData);
                }
                
                // 6. 设备信息
                if (gisInfo.struDevInfo != null) {
                    java.util.Map<String, Object> devData = new java.util.HashMap<>();
                    devData.put("channel", gisInfo.struDevInfo.byChannel & 0xFF);
                    gisData.put("device", devData);
                }
                
                // 7. 时间信息
                gisData.put("relativeTime", gisInfo.dwRelativeTime);
                gisData.put("absTime", gisInfo.dwAbsTime);
                
                return gisData;
            } catch (Exception e) {
                logger.error("解析GIS信息结构体失败", e);
                return null;
            }
        }
        
        /**
         * 格式化纬度字符串
         */
        private String formatLatitude(HCNetSDK.NET_DVR_LLI_PARAM lat, byte latType) {
            return String.format("%s %d°%d'%.2f\"", 
                latType == 0 ? "N" : "S", 
                lat.byDegree & 0xFF, 
                lat.byMinute & 0xFF, 
                lat.fSec);
        }
        
        /**
         * 格式化经度字符串
         */
        private String formatLongitude(HCNetSDK.NET_DVR_LLI_PARAM lon, byte lonType) {
            return String.format("%s %d°%d'%.2f\"", 
                lonType == 0 ? "E" : "W", 
                lon.byDegree & 0xFF, 
                lon.byMinute & 0xFF, 
                lon.fSec);
        }
        
        /**
         * 将度分秒转换为十进制度数
         */
        private double convertToDecimal(byte degree, byte minute, float second) {
            return (degree & 0xFF) + (minute & 0xFF) / 60.0 + second / 3600.0;
        }
        
        /**
         * 格式化方位角描述
         */
        private String formatAzimuth(float azimuth) {
            if (azimuth >= 0 && azimuth < 22.5 || azimuth >= 337.5) {
                return "正北";
            } else if (azimuth >= 22.5 && azimuth < 67.5) {
                return "东北";
            } else if (azimuth >= 67.5 && azimuth < 112.5) {
                return "正东";
            } else if (azimuth >= 112.5 && azimuth < 157.5) {
                return "东南";
            } else if (azimuth >= 157.5 && azimuth < 202.5) {
                return "正南";
            } else if (azimuth >= 202.5 && azimuth < 247.5) {
                return "西南";
            } else if (azimuth >= 247.5 && azimuth < 292.5) {
                return "正西";
            } else {
                return "西北";
            }
        }
        
        /**
         * 构建GIS报警消息
         */
        private String buildGisAlarmMessage(java.util.Map<String, Object> gisData) {
            StringBuilder msg = new StringBuilder("GIS信息上传: ");
            
            // 经纬度
            if (gisData.containsKey("latitude") && gisData.containsKey("longitude")) {
                msg.append(String.format("位置: %s, %s", 
                    gisData.get("latitude"), gisData.get("longitude")));
            }
            
            // 方位角
            if (gisData.containsKey("azimuth")) {
                msg.append(String.format(" | 朝向: %.2f°(%s)", 
                    gisData.get("azimuth"), gisData.get("azimuthDescription")));
            }
            
            // PTZ坐标
            if (gisData.containsKey("ptz")) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> ptz = (java.util.Map<String, Object>) gisData.get("ptz");
                msg.append(String.format(" | PTZ: 水平%.1f° 垂直%.1f° 变倍%.1fx", 
                    ptz.get("panPos"), ptz.get("tiltPos"), ptz.get("zoomPos")));
            }
            
            return msg.toString();
        }
    }

    /**
     * 获取SDK接口
     */
    public HCNetSDK getSDK() {
        return hCNetSDK;
    }

    // ========== 功能方法实现 ==========

    @Override
    public int startRealPlay(int userId, int channel, int streamType) {
        if (!initialized || hCNetSDK == null) {
            logger.error("海康SDK未初始化");
            return -1;
        }

        try {
            // 参考Recorder.java:260-278的实现
            HCNetSDK.NET_DVR_PREVIEWINFO previewInfo = new HCNetSDK.NET_DVR_PREVIEWINFO();
            previewInfo.lChannel = channel;
            previewInfo.dwStreamType = streamType; // 0=主码流, 1=子码流
            previewInfo.dwLinkMode = 0; // TCP方式
            previewInfo.bBlocked = 1; // 阻塞取流
            previewInfo.byProtoType = 0; // 私有协议
            previewInfo.write();

            // 创建回调函数（可以为null）
            HCNetSDK.FRealDataCallBack_V30 realDataCallback = null;

            // 启动预览
            int realPlayHandle = hCNetSDK.NET_DVR_RealPlay_V40(userId, previewInfo, realDataCallback, null);
            if (realPlayHandle == -1) {
                logger.error("海康预览启动失败: userId={}, channel={}, 错误码={}", userId, channel, getLastError());
                return -1;
            }

            logger.info("海康预览启动成功: userId={}, channel={}, streamType={}, handle={}",
                    userId, channel, streamType, realPlayHandle);
            return realPlayHandle;
        } catch (Exception e) {
            logger.error("海康预览启动异常: userId={}, channel={}", userId, channel, e);
            return -1;
        }
    }

    @Override
    public boolean stopRealPlay(int connectId) {
        if (!initialized || hCNetSDK == null) {
            return false;
        }

        if (connectId < 0) {
            return false;
        }

        try {
            boolean result = hCNetSDK.NET_DVR_StopRealPlay(connectId);
            if (result) {
                logger.info("海康预览停止成功: handle={}", connectId);
                return true;
            } else {
                logger.error("海康预览停止失败: handle={}, 错误码={}", connectId, getLastError());
                return false;
            }
        } catch (Exception e) {
            logger.error("海康预览停止异常: handle={}", connectId, e);
            return false;
        }
    }

    @Override
    public boolean startRecording(int connectId, String filePath) {
        if (!initialized || hCNetSDK == null) {
            logger.error("海康SDK未初始化");
            return false;
        }

        if (connectId < 0) {
            logger.error("无效的预览连接ID: {}", connectId);
            return false;
        }

        try {
            // 参考Recorder.java:289的实现
            // 使用NET_DVR_SaveRealData，直接传文件路径字符串
            boolean saveResult = hCNetSDK.NET_DVR_SaveRealData(connectId, filePath);
            if (saveResult) {
                logger.info("海康录制启动成功: handle={}, filePath={}", connectId, filePath);
                return true;
            } else {
                logger.error("海康录制启动失败: handle={}, filePath={}, 错误码={}", connectId, filePath, getLastError());
                return false;
            }
        } catch (Exception e) {
            logger.error("海康录制启动异常: handle={}, filePath={}", connectId, filePath, e);
            return false;
        }
    }

    @Override
    public boolean stopRecording(int connectId) {
        if (!initialized || hCNetSDK == null) {
            return false;
        }

        if (connectId < 0) {
            return false;
        }

        try {
            // 参考Recorder.java:309-320的实现
            // 先停止保存数据，再停止预览
            hCNetSDK.NET_DVR_StopSaveRealData(connectId);
            Thread.sleep(500); // 等待数据写入完成
            boolean result = hCNetSDK.NET_DVR_StopRealPlay(connectId);
            if (result) {
                logger.info("海康录制停止成功: handle={}", connectId);
                return true;
            } else {
                logger.error("海康录制停止失败: handle={}, 错误码={}", connectId, getLastError());
                return false;
            }
        } catch (Exception e) {
            logger.error("海康录制停止异常: handle={}", connectId, e);
            return false;
        }
    }

    @Override
    public boolean capturePicture(int connectId, int userId, int channel, String filePath, int pictureType) {
        if (!initialized || hCNetSDK == null) {
            logger.error("海康SDK未初始化");
            return false;
        }
        return Boolean.TRUE.equals(runOnExecutor(CAPTURE_TIMEOUT_MS,
                () -> capturePictureImpl(connectId, userId, channel, filePath, pictureType), false));
    }

    /** 仅在执行器线程内调用 */
    private boolean capturePictureImpl(int connectId, int userId, int channel, String filePath, int pictureType) {
        try {
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            String absolutePath = file.getAbsolutePath();
            try {
                absolutePath = new File(absolutePath).getCanonicalPath();
            } catch (java.io.IOException e) {
                logger.warn("无法规范化路径，使用原始路径: {}", e.getMessage());
            }
            logger.debug("海康抓图参数: userId={}, channel={}, filePath={}, absolutePath={}",
                    userId, channel, filePath, absolutePath);
            HCNetSDK.NET_DVR_JPEGPARA jpegPara = new HCNetSDK.NET_DVR_JPEGPARA();
            jpegPara.wPicSize = 0;
            jpegPara.wPicQuality = 2;
            jpegPara.write();
            byte[] fileNameBytes;
            try {
                fileNameBytes = absolutePath.getBytes("GBK");
            } catch (java.io.UnsupportedEncodingException e) {
                logger.warn("GBK编码不支持，使用UTF-8: {}", e.getMessage());
                fileNameBytes = absolutePath.getBytes("UTF-8");
            }
            byte[] fileNameArray = new byte[256];
            int copyLength = Math.min(fileNameBytes.length, fileNameArray.length - 1);
            System.arraycopy(fileNameBytes, 0, fileNameArray, 0, copyLength);
            fileNameArray[copyLength] = 0;
            int actualChannel = channel > 0 ? channel : 1;
            boolean result = hCNetSDK.NET_DVR_CaptureJPEGPicture(userId, actualChannel, jpegPara, fileNameArray);
            if (result) {
                logger.info("海康抓图成功: userId={}, channel={}, filePath={}", userId, actualChannel, absolutePath);
                return true;
            } else {
                logger.error("海康抓图失败: userId={}, channel={}, filePath={}, 错误码={}, 错误信息={}",
                        userId, actualChannel, absolutePath, getLastError(), getLastErrorString());
                return false;
            }
        } catch (Exception e) {
            logger.error("海康抓图异常: userId={}, channel={}, filePath={}", userId, channel, filePath, e);
            return false;
        }
    }

    @Override
    public boolean ptzControl(int userId, int channel, String command, String action, int speed) {
        if (!initialized || hCNetSDK == null) {
            logger.error("海康SDK未初始化");
            return false;
        }
        return Boolean.TRUE.equals(runOnExecutor(DEFAULT_SDK_TIMEOUT_MS,
                () -> ptzControlImpl(userId, channel, command, action, speed), false));
    }

    /** 仅在执行器线程内调用 */
    private boolean ptzControlImpl(int userId, int channel, String command, String action, int speed) {
        try {
            int commandCode = getPtzCommandCode(command);
            if (commandCode == -1) {
                logger.error("未知的云台控制命令: {}", command);
                return false;
            }
            int stopFlag = "stop".equalsIgnoreCase(action) ? 1 : 0;
            boolean result = hCNetSDK.NET_DVR_PTZControlWithSpeed_Other(userId, channel, commandCode, stopFlag, speed);
            if (result) {
                logger.info("海康云台控制成功: userId={}, channel={}, command={}, action={}, speed={}",
                        userId, channel, command, action, speed);
                return true;
            } else {
                logger.error("海康云台控制失败: userId={}, channel={}, command={}, 错误码={}",
                        userId, channel, command, getLastError());
                return false;
            }
        } catch (Exception e) {
            logger.error("海康云台控制异常: userId={}, channel={}, command={}", userId, channel, command, e);
            return false;
        }
    }

    @Override
    public boolean gotoAngle(int userId, int channel, float pan, float tilt, float zoom) {
        if (!initialized || hCNetSDK == null) {
            logger.error("海康SDK未初始化");
            return false;
        }

        try {
            HCNetSDK.NET_DVR_PTZPOS struPos = new HCNetSDK.NET_DVR_PTZPOS();
            // wAction: 1-定位PTZ, 2-定位P, 3-定位T, 4-定位Z, 5-定位PT
            struPos.wAction = 1;
            
            // 海康绝对定位参数使用BCD码格式
            // 例如：48.1° -> 0x0481, 120.5° -> 0x1205
            // 需要将十进制角度*10后转换为BCD码
            struPos.wPanPos = (short) toBCD((int) (pan * 10));
            struPos.wTiltPos = (short) toBCD((int) (tilt * 10));
            struPos.wZoomPos = (short) toBCD((int) (zoom * 10));
            struPos.write();

            logger.info("海康云台绝对定位参数: userId={}, channel={}, pan={}°->BCD:0x{}, tilt={}°->BCD:0x{}, zoom={}x->BCD:0x{}",
                    userId, channel, pan, Integer.toHexString(struPos.wPanPos & 0xFFFF), 
                    tilt, Integer.toHexString(struPos.wTiltPos & 0xFFFF),
                    zoom, Integer.toHexString(struPos.wZoomPos & 0xFFFF));

            boolean result = hCNetSDK.NET_DVR_SetDVRConfig(userId, HCNetSDK.NET_DVR_SET_PTZPOS, channel,
                    struPos.getPointer(), struPos.size());
            if (!result) {
                int errorCode = getLastError();
                logger.error("海康云台绝对定位失败: userId={}, channel={}, 错误码={}", userId, channel, errorCode);
                if (errorCode == 11) {
                    logger.warn("错误码11(无权限): 可能原因: 1.设备不支持绝对定位 2.通道号错误 3.需要管理员权限");
                }
                return false;
            }

            logger.info("海康云台绝对定位成功: userId={}, channel={}, pan={}°, tilt={}°, zoom={}x",
                    userId, channel, pan, tilt, zoom);
            return true;
        } catch (Exception e) {
            logger.error("海康云台绝对定位异常: userId={}, channel={}", userId, channel, e);
            return false;
        }
    }

    @Override
    public boolean gotoPreset(int userId, int channel, int presetIndex) {
        if (!initialized || hCNetSDK == null) {
            logger.error("海康SDK未初始化");
            return false;
        }
        if (presetIndex < 1) {
            logger.warn("预置点号无效: {}", presetIndex);
            return false;
        }
        try {
            boolean result = hCNetSDK.NET_DVR_PTZPreset_Other(userId, channel, HCNetSDK.GOTO_PRESET, presetIndex);
            if (!result) {
                int errorCode = getLastError();
                logger.error("海康云台转预置点失败: userId={}, channel={}, presetIndex={}, 错误码={}", userId, channel, presetIndex, errorCode);
                return false;
            }
            logger.info("海康云台转预置点成功: userId={}, channel={}, presetIndex={}", userId, channel, presetIndex);
            return true;
        } catch (Exception e) {
            logger.error("海康云台转预置点异常: userId={}, channel={}, presetIndex={}", userId, channel, presetIndex, e);
            return false;
        }
    }
    
    /**
     * 将十进制数转换为BCD码
     * 例如：481 -> 0x0481, 1205 -> 0x1205
     * @param decimal 十进制数 (0-9999)
     * @return BCD码
     */
    private int toBCD(int decimal) {
        if (decimal < 0) decimal = 0;
        if (decimal > 9999) decimal = 9999;
        
        int bcd = 0;
        int shift = 0;
        while (decimal > 0) {
            bcd |= (decimal % 10) << shift;
            decimal /= 10;
            shift += 4;
        }
        return bcd;
    }

    /**
     * 将BCD码转换为十进制数
     * 例如：0x0481 -> 481, 0x1205 -> 1205
     * @param bcd BCD码
     * @return 十进制数
     */
    private int fromBCD(int bcd) {
        int result = 0;
        int multiplier = 1;
        while (bcd > 0) {
            result += (bcd & 0x0F) * multiplier;
            bcd >>= 4;
            multiplier *= 10;
        }
        return result;
    }

    @Override
    public PtzPosition getPtzPosition(int userId, int channel) {
        if (!initialized || hCNetSDK == null) {
            logger.error("海康SDK未初始化");
            return null;
        }

        try {
            HCNetSDK.NET_DVR_PTZPOS struPos = new HCNetSDK.NET_DVR_PTZPOS();
            struPos.write();

            IntByReference bytesReturned = new IntByReference(0);
            boolean result = hCNetSDK.NET_DVR_GetDVRConfig(userId, HCNetSDK.NET_DVR_GET_PTZPOS, channel,
                    struPos.getPointer(), struPos.size(), bytesReturned);

            if (!result) {
                int errorCode = getLastError();
                logger.error("海康获取PTZ位置失败: userId={}, channel={}, 错误码={}", userId, channel, errorCode);
                return null;
            }

            struPos.read();

            // BCD码解码
            float pan = fromBCD(struPos.wPanPos & 0xFFFF) / 10.0f;
            float tilt = fromBCD(struPos.wTiltPos & 0xFFFF) / 10.0f;
            float zoom = fromBCD(struPos.wZoomPos & 0xFFFF) / 10.0f;

            logger.debug("海康获取PTZ位置成功: userId={}, channel={}, pan={}°, tilt={}°, zoom={}x",
                    userId, channel, pan, tilt, zoom);

            return new PtzPosition(pan, tilt, zoom);
        } catch (Exception e) {
            logger.error("海康获取PTZ位置异常: userId={}, channel={}", userId, channel, e);
            return null;
        }
    }

    @Override
    public int downloadPlaybackByTimeRange(int userId, int channel, Date startTime, Date endTime,
            String localFilePath, int streamType) {
        if (!initialized || hCNetSDK == null) {
            logger.error("海康SDK未初始化");
            return -1;
        }

        try {
            // 确保目录存在
            File file = new File(localFilePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            HCNetSDK.NET_DVR_PLAYCOND downloadCond = new HCNetSDK.NET_DVR_PLAYCOND();
            downloadCond.dwChannel = channel;
            downloadCond.byStreamType = (byte) streamType;

            // 填充开始时间
            downloadCond.struStartTime = new HCNetSDK.NET_DVR_TIME();
            Calendar calendarStart = Calendar.getInstance();
            calendarStart.setTime(startTime);
            downloadCond.struStartTime.dwYear = calendarStart.get(Calendar.YEAR);
            downloadCond.struStartTime.dwMonth = calendarStart.get(Calendar.MONTH) + 1;
            downloadCond.struStartTime.dwDay = calendarStart.get(Calendar.DAY_OF_MONTH);
            downloadCond.struStartTime.dwHour = calendarStart.get(Calendar.HOUR_OF_DAY);
            downloadCond.struStartTime.dwMinute = calendarStart.get(Calendar.MINUTE);
            downloadCond.struStartTime.dwSecond = calendarStart.get(Calendar.SECOND);

            // 填充结束时间
            downloadCond.struStopTime = new HCNetSDK.NET_DVR_TIME();
            Calendar calendarEnd = Calendar.getInstance();
            calendarEnd.setTime(endTime);
            downloadCond.struStopTime.dwYear = calendarEnd.get(Calendar.YEAR);
            downloadCond.struStopTime.dwMonth = calendarEnd.get(Calendar.MONTH) + 1;
            downloadCond.struStopTime.dwDay = calendarEnd.get(Calendar.DAY_OF_MONTH);
            downloadCond.struStopTime.dwHour = calendarEnd.get(Calendar.HOUR_OF_DAY);
            downloadCond.struStopTime.dwMinute = calendarEnd.get(Calendar.MINUTE);
            downloadCond.struStopTime.dwSecond = calendarEnd.get(Calendar.SECOND);

            downloadCond.write();

            // 执行下载
            int downloadHandle = hCNetSDK.NET_DVR_GetFileByTime_V40(userId, localFilePath, downloadCond);
            if (downloadHandle == -1) {
                logger.error("海康录像下载启动失败: userId={}, channel={}, 路径={}, 错误码={}",
                        userId, channel, localFilePath, getLastError());
                return -1;
            }

            // 设置转封装格式为MP4（必须在PLAYSTART之前设置）
            // NET_DVR_SET_TRANS_TYPE = 32, 参数值: 0-私有格式, 1-TS格式, 2-MP4格式
            // 使用 PlayBackControl_V40，通过 Pointer 传递参数
            IntByReference transTypeValue = new IntByReference(2); // 2 = MP4格式
            IntByReference outLen = new IntByReference(0);
            boolean setTransType = hCNetSDK.NET_DVR_PlayBackControl_V40(
                    downloadHandle, 
                    HCNetSDK.NET_DVR_SET_TRANS_TYPE, 
                    transTypeValue.getPointer(), 
                    4,  // int 大小为 4 字节
                    Pointer.NULL, 
                    outLen);
            if (!setTransType) {
                int errorCode = getLastError();
                // 错误码17表示设备不支持此功能，可以忽略继续下载（会下载私有格式）
                if (errorCode == 17) {
                    logger.warn("海康录像下载: 设备不支持MP4转封装(错误码17)，将下载私有格式");
                } else {
                    logger.warn("海康录像下载: 设置MP4转封装失败，错误码={}，将尝试继续下载", errorCode);
                }
            } else {
                logger.info("海康录像下载: 已设置转封装格式为MP4");
            }

            // 启动下载控制码
            boolean playBackControl = hCNetSDK.NET_DVR_PlayBackControl(downloadHandle, HCNetSDK.NET_DVR_PLAYSTART, 0,
                    null);
            if (!playBackControl) {
                logger.error("海康录像下载控制启动失败: handle={}, 错误码={}", downloadHandle, getLastError());
                hCNetSDK.NET_DVR_StopGetFile(downloadHandle);
                return -1;
            }

            logger.info("海康录像下载已启动: handle={}, channel={}, file={}", downloadHandle, channel, localFilePath);
            return downloadHandle;
        } catch (Exception e) {
            logger.error("海康录像下载启动异常: userId={}, channel={}", userId, channel, e);
            return -1;
        }
    }

    @Override
    public boolean stopDownload(int downloadId) {
        if (!initialized || hCNetSDK == null) {
            return false;
        }
        if (downloadId < 0)
            return false;

        boolean result = hCNetSDK.NET_DVR_StopGetFile(downloadId);
        if (result) {
            logger.info("海康录像下载已停止: handle={}", downloadId);
        } else {
            logger.error("海康录像下载停止失败: handle={}, 错误码={}", downloadId, getLastError());
        }
        return result;
    }

    @Override
    public int getDownloadProgress(int downloadId) {
        if (!initialized || hCNetSDK == null) {
            return -1;
        }
        if (downloadId < 0)
            return -1;

        int progress = hCNetSDK.NET_DVR_GetDownloadPos(downloadId);
        if (progress < 0 || progress > 100) {
            // progress = 100 表示完成，在某些情况下返回可能是别的
            if (progress == 100)
                return 100;
            if (progress == 200) {
                logger.error("海康下载异常: handle={}, 返回进度 200", downloadId);
                return -1;
            }
        }
        return progress;
    }

    /**
     * 获取PTZ命令码
     */
    private int getPtzCommandCode(String command) {
        switch (command.toLowerCase()) {
            case "up":
                return HCNetSDK.TILT_UP;
            case "down":
                return HCNetSDK.TILT_DOWN;
            case "left":
                return HCNetSDK.PAN_LEFT;
            case "right":
                return HCNetSDK.PAN_RIGHT;
            case "zoom_in":
                return HCNetSDK.ZOOM_IN;
            case "zoom_out":
                return HCNetSDK.ZOOM_OUT;
            default:
                return -1;
        }
    }

    @Override
    public List<DeviceSDK.PlaybackFile> queryPlaybackFiles(int userId, int channel, Date startTime, Date endTime) {
        List<DeviceSDK.PlaybackFile> files = new ArrayList<>();

        if (!initialized || hCNetSDK == null) {
            logger.error("海康SDK未初始化");
            return files;
        }

        try {
            // 参考VideoDemo.java:403-536的实现
            // 使用NET_DVR_FindFile_V40查询文件列表
            HCNetSDK.NET_DVR_FILECOND_V40 struFileCond = new HCNetSDK.NET_DVR_FILECOND_V40();
            struFileCond.read();
            struFileCond.lChannel = channel; // 通道号
            struFileCond.dwFileType = 0xFF; // 所有文件类型
            struFileCond.byFindType = 0; // 0=定时录像

            // 设置开始时间
            HCNetSDK.NET_DVR_TIME startTimeStruct = convertToDvrTime(startTime);
            struFileCond.struStartTime.dwYear = startTimeStruct.dwYear;
            struFileCond.struStartTime.dwMonth = startTimeStruct.dwMonth;
            struFileCond.struStartTime.dwDay = startTimeStruct.dwDay;
            struFileCond.struStartTime.dwHour = startTimeStruct.dwHour;
            struFileCond.struStartTime.dwMinute = startTimeStruct.dwMinute;
            struFileCond.struStartTime.dwSecond = startTimeStruct.dwSecond;

            // 设置结束时间
            HCNetSDK.NET_DVR_TIME endTimeStruct = convertToDvrTime(endTime);
            struFileCond.struStopTime.dwYear = endTimeStruct.dwYear;
            struFileCond.struStopTime.dwMonth = endTimeStruct.dwMonth;
            struFileCond.struStopTime.dwDay = endTimeStruct.dwDay;
            struFileCond.struStopTime.dwHour = endTimeStruct.dwHour;
            struFileCond.struStopTime.dwMinute = endTimeStruct.dwMinute;
            struFileCond.struStopTime.dwSecond = endTimeStruct.dwSecond;

            struFileCond.write();

            // 建立查询
            int findHandle = hCNetSDK.NET_DVR_FindFile_V40(userId, struFileCond);
            if (findHandle <= -1) {
                logger.error("海康回放查询建立失败: userId={}, channel={}, 错误码={}", userId, channel, getLastError());
                return files;
            }

            // 循环查询文件
            while (true) {
                HCNetSDK.NET_DVR_FINDDATA_V40 struFindData = new HCNetSDK.NET_DVR_FINDDATA_V40();
                long state = hCNetSDK.NET_DVR_FindNextFile_V40(findHandle, struFindData);

                if (state <= -1) {
                    // 查询失败
                    logger.error("海康回放查询失败: userId={}, channel={}, 错误码={}", userId, channel, getLastError());
                    break;
                } else if (state == 1000) {
                    // 获取文件信息成功
                    struFindData.read();
                    try {
                        String fileName = new String(struFindData.sFileName, "UTF-8").trim();
                        if (!fileName.isEmpty()) {
                            // 转换文件时间
                            Calendar fileStartCal = Calendar.getInstance();
                            fileStartCal.set(struFindData.struStartTime.dwYear,
                                    struFindData.struStartTime.dwMonth - 1,
                                    struFindData.struStartTime.dwDay,
                                    struFindData.struStartTime.dwHour,
                                    struFindData.struStartTime.dwMinute,
                                    struFindData.struStartTime.dwSecond);
                            Date fileStartTime = fileStartCal.getTime();

                            Calendar fileEndCal = Calendar.getInstance();
                            fileEndCal.set(struFindData.struStopTime.dwYear,
                                    struFindData.struStopTime.dwMonth - 1,
                                    struFindData.struStopTime.dwDay,
                                    struFindData.struStopTime.dwHour,
                                    struFindData.struStopTime.dwMinute,
                                    struFindData.struStopTime.dwSecond);
                            Date fileEndTime = fileEndCal.getTime();

                            DeviceSDK.PlaybackFile playbackFile = new DeviceSDK.PlaybackFile(
                                    fileName, fileStartTime, fileEndTime,
                                    struFindData.dwFileSize, channel);
                            files.add(playbackFile);

                            logger.debug("找到回放文件: fileName={}, size={}, startTime={}, endTime={}",
                                    fileName, struFindData.dwFileSize, fileStartTime, fileEndTime);
                        }
                    } catch (Exception e) {
                        logger.warn("解析文件信息失败", e);
                    }
                } else if (state == 1001) {
                    // 未查找到文件
                    logger.debug("未查找到回放文件: userId={}, channel={}", userId, channel);
                    break;
                } else if (state == 1002) {
                    // 正在查找，请等待
                    try {
                        Thread.sleep(100); // 等待100ms后继续查询
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                } else if (state == 1003) {
                    // 没有更多的文件，查找结束
                    logger.debug("回放查询结束: userId={}, channel={}, 文件数={}", userId, channel, files.size());
                    break;
                } else if (state == 1004) {
                    // 查找文件时异常
                    logger.warn("回放查询异常: userId={}, channel={}", userId, channel);
                    break;
                } else if (state == 1005) {
                    // 查找文件超时
                    logger.warn("回放查询超时: userId={}, channel={}", userId, channel);
                    break;
                } else {
                    // 未知状态
                    logger.warn("回放查询未知状态: state={}, userId={}, channel={}", state, userId, channel);
                    break;
                }
            }

            // 关闭查询
            boolean closeResult = hCNetSDK.NET_DVR_FindClose_V30(findHandle);
            if (!closeResult) {
                logger.warn("关闭回放查询失败: userId={}, channel={}, 错误码={}", userId, channel, getLastError());
            }

            logger.info("海康回放查询成功: userId={}, channel={}, 文件数={}", userId, channel, files.size());
            return files;
        } catch (Exception e) {
            logger.error("海康回放查询异常: userId={}, channel={}", userId, channel, e);
            return files;
        }
    }

    /**
     * 转换Date为NET_DVR_TIME结构体
     */
    private HCNetSDK.NET_DVR_TIME convertToDvrTime(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        HCNetSDK.NET_DVR_TIME dvrTime = new HCNetSDK.NET_DVR_TIME();
        dvrTime.dwYear = cal.get(Calendar.YEAR);
        dvrTime.dwMonth = cal.get(Calendar.MONTH) + 1;
        dvrTime.dwDay = cal.get(Calendar.DAY_OF_MONTH);
        dvrTime.dwHour = cal.get(Calendar.HOUR_OF_DAY);
        dvrTime.dwMinute = cal.get(Calendar.MINUTE);
        dvrTime.dwSecond = cal.get(Calendar.SECOND);

        return dvrTime;
    }

    /**
     * 异常回调实现
     */
    class FExceptionCallBack_Imp implements HCNetSDK.FExceptionCallBack {
        private final Logger logger = LoggerFactory.getLogger(FExceptionCallBack_Imp.class);

        @Override
        public void invoke(int dwType, int lUserID, int lHandle, Pointer pUser) {
            logger.warn("SDK异常事件 - 类型: 0x{}, 用户ID: {}, 句柄: {}", Integer.toHexString(dwType), lUserID, lHandle);

            // 处理设备离线事件
            // 海康SDK的异常类型：
            // EXCEPTION_EXCHANGE = 0x8000 (用户交互时异常，可能包括设备离线)
            // EXCEPTION_PREVIEW = 0x8003 (网络预览异常，可能包括设备离线)
            // EXCEPTION_RECONNECT = 0x8005 (预览时重连，可能表示设备离线后重连)
            if (dwType == HCNetSDK.EXCEPTION_EXCHANGE ||
                    dwType == HCNetSDK.EXCEPTION_PREVIEW ||
                    dwType == HCNetSDK.EXCEPTION_RECONNECT) {
                handleDeviceOffline(lUserID, dwType);
            }
        }

        /**
         * 处理设备离线事件
         */
        private void handleDeviceOffline(int userId, int exceptionType) {
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

                // 获取设备信息
                DeviceInfo device = deviceManager.getDevice(deviceId);
                if (device == null) {
                    logger.warn("设备不存在: {}", deviceId);
                    return;
                }

                // 检查设备当前状态
                int currentStatus = device.getStatus();
                if (currentStatus == 0) {
                    logger.debug("设备已经是离线状态，跳过更新: {}", deviceId);
                    return;
                }

                // 更新设备状态为离线并发送MQTT通知
                deviceManager.updateDeviceStatusWithNotification(deviceId, 0);
                logger.info("设备离线事件已处理: {} (userId: {}, 异常类型: 0x{})",
                        deviceId, userId, Integer.toHexString(exceptionType));
            } catch (Exception e) {
                logger.error("处理设备离线事件失败: userId={}", userId, e);
            }
        }
    }

    @Override
    public boolean rebootDevice(int userId) {
        if (!initialized || hCNetSDK == null) {
            logger.error("海康SDK未初始化");
            return false;
        }

        if (userId < 0) {
            logger.error("无效的登录句柄: {}", userId);
            return false;
        }

        try {
            // 参考DeviceController.java:302-310的实现
            // 优先使用NET_DVR_RebootDVR，如果不可用则使用NET_DVR_RemoteControl
            boolean result = false;
            try {
                result = hCNetSDK.NET_DVR_RebootDVR(userId);
            } catch (Exception e) {
                // 如果NET_DVR_RebootDVR不可用，尝试使用NET_DVR_RemoteControl
                logger.debug("NET_DVR_RebootDVR不可用，尝试使用NET_DVR_RemoteControl: {}", e.getMessage());
                result = hCNetSDK.NET_DVR_RemoteControl(userId, HCNetSDK.MINOR_REMOTE_REBOOT, null, 0);
            }

            if (result) {
                logger.info("海康设备重启命令已发送: userId={}", userId);
                return true;
            } else {
                int errorCode = getLastError();
                logger.error("海康设备重启失败: userId={}, 错误码={}", userId, errorCode);
                return false;
            }
        } catch (Exception e) {
            logger.error("海康设备重启异常: userId={}", userId, e);
            return false;
        }
    }
}
