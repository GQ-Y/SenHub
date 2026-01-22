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
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

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

            // 设置异常回调（此时deviceManager和mqttClient可能还未设置，稍后通过setStatusCallbacks设置）
            FExceptionCallBack_Imp exceptionCallback = new FExceptionCallBack_Imp();
            if (!hCNetSDK.NET_DVR_SetExceptionCallBack_V30(0, 0, exceptionCallback, null)) {
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
            logger.info("海康SDK初始化成功");
            return true;

        } catch (Exception e) {
            logger.error("海康SDK初始化异常", e);
            return false;
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

            // 设置库路径到java.library.path（用于JNA查找库文件和依赖库）
            // 使用LibraryPathHelper构建完整的库路径
            String libDir = libFile.getParent();
            String hcNetSDKComDir = libDir + "/HCNetSDKCom";

            // 使用LibraryPathHelper构建完整的库路径
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
                hCNetSDK = (HCNetSDK) Native.loadLibrary(libraryName, HCNetSDK.class);
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
     * 登录设备
     */
    @Override
    public int login(String ip, int port, String username, String password) {
        if (!initialized || hCNetSDK == null) {
            logger.error("SDK未初始化");
            return -1;
        }

        HCNetSDK.NET_DVR_USER_LOGIN_INFO loginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();
        HCNetSDK.NET_DVR_DEVICEINFO_V40 deviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V40();

        // 设置设备IP地址（按照示例代码的方式）
        byte[] deviceAddress = new byte[HCNetSDK.NET_DVR_DEV_ADDRESS_MAX_LEN];
        byte[] ipBytes = ip.getBytes();
        System.arraycopy(ipBytes, 0, deviceAddress, 0, Math.min(ipBytes.length, deviceAddress.length));
        loginInfo.sDeviceAddress = deviceAddress;

        // 设置用户名（按照示例代码的方式）
        byte[] userName = new byte[HCNetSDK.NET_DVR_LOGIN_USERNAME_MAX_LEN];
        byte[] userBytes = username.getBytes();
        System.arraycopy(userBytes, 0, userName, 0, Math.min(userBytes.length, userName.length));
        loginInfo.sUserName = userName;

        // 设置密码（按照示例代码的方式）
        byte[] passwordBytes = password.getBytes();
        System.arraycopy(passwordBytes, 0, loginInfo.sPassword, 0,
                Math.min(passwordBytes.length, loginInfo.sPassword.length));

        // 设置端口和登录模式（注意：wPort是short类型，需要转换）
        loginInfo.wPort = (short) port;
        loginInfo.bUseAsynLogin = false; // 同步登录
        loginInfo.byLoginMode = 0; // 使用SDK私有协议
        loginInfo.byUseTransport = 0; // 不使用传输层协议
        loginInfo.byProxyType = 0; // 不使用代理
        loginInfo.byHttps = 0; // 不使用HTTPS

        // 设置连接超时（可选，默认可能较短）
        // NET_DVR_SetConnectTime(等待时间ms, 重试次数)
        hCNetSDK.NET_DVR_SetConnectTime(10000, 3); // 等待10秒，重试3次

        // 确保结构体数据同步到本地内存（JNA需要）
        loginInfo.write();
        deviceInfo.write();

        // 执行登录
        logger.info("开始登录设备: {}:{}, 用户: {}", ip, port, username);
        logger.debug("登录参数详情: IP={}, Port={}, Username={}, Password长度={}, LoginMode={}",
                ip, port, username, password != null ? password.length() : 0, loginInfo.byLoginMode);

        int userID = hCNetSDK.NET_DVR_Login_V40(loginInfo, deviceInfo);

        // 读取设备信息（登录成功后设备信息会被填充）
        deviceInfo.read();

        logger.debug("登录调用返回: userID={}", userID);

        // 检查设备信息（登录成功后设备信息会被填充）
        if (deviceInfo.struDeviceV30 != null) {
            int chanNum = deviceInfo.struDeviceV30.byChanNum & 0xFF; // 转换为无符号
            int startDChan = deviceInfo.struDeviceV30.byStartDChan & 0xFF;
            logger.debug("设备信息: 通道数={}, 起始通道={}", chanNum, startDChan);
        }

        // 按照示例代码的逻辑：只要 userID != -1 就认为登录成功（包括 userID=0）
        if (userID == -1) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            logger.error("登录失败，错误码: {} (IP: {}:{}, 用户: {})", errorCode, ip, port, username);

            // 输出常见错误码的含义
            if (errorCode == HCNetSDK.NET_DVR_PASSWORD_ERROR) {
                logger.error("错误原因: 用户名或密码错误");
            } else if (errorCode == HCNetSDK.NET_DVR_NETWORK_FAIL_CONNECT) {
                logger.error("错误原因: 连接服务器失败 (错误码: 7)");
                logger.error("可能原因: 1)设备不在线或网络不通 2)端口错误 3)防火墙阻止连接");
                logger.error("         4)设备不是海康品牌，无法使用海康SDK连接 (如天地伟业、大华等品牌需要使用对应的SDK)");
                logger.error("         5)设备不支持海康SDK的私有协议");
            } else if (errorCode == HCNetSDK.NET_DVR_NETWORK_SEND_ERROR
                    || errorCode == HCNetSDK.NET_DVR_NETWORK_RECV_ERROR) {
                logger.error("错误原因: 网络通信错误，请检查网络连接和端口");
            } else if (errorCode == HCNetSDK.NET_DVR_NETWORK_RECV_TIMEOUT) {
                logger.error("错误原因: 网络接收超时，设备可能未响应");
            } else if (errorCode == 0) {
                logger.warn("注意: 错误码为0，可能是设备未响应或连接超时");
                logger.warn("可能原因: 1)设备不在线 2)端口错误 3)防火墙阻止 4)设备不支持该登录方式");
            } else {
                logger.warn("其他错误，错误码: {}", errorCode);
            }
        } else {
            logger.info("设备登录成功: {}:{} (userId: {})", ip, port, userID);
            
            // 登录成功后自动布防，以接收报警事件
            if (alarmService != null) {
                int alarmHandle = setupAlarmChan(userID);
                if (alarmHandle >= 0) {
                    logger.info("设备已自动布防: {}:{} (userId: {}, alarmHandle: {})", ip, port, userID, alarmHandle);
                } else {
                    logger.warn("设备自动布防失败: {}:{} (userId: {})", ip, port, userID);
                }
            }
        }

        return userID;
    }

    /**
     * 登出设备
     */
    @Override
    public boolean logout(int userID) {
        if (!initialized || hCNetSDK == null) {
            return false;
        }
        
        // 先撤防
        closeAlarmChan(userID);
        
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
            // 重新设置异常回调，确保回调可以访问最新的deviceManager和mqttClient
            FExceptionCallBack_Imp exceptionCallback = new FExceptionCallBack_Imp();
            hCNetSDK.NET_DVR_SetExceptionCallBack_V30(0, 0, exceptionCallback, null);
        }
        logger.debug("已设置状态回调：DeviceManager和MqttClient");
    }

    /**
     * 设置报警服务（用于报警自动抓图）
     */
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
     * 对设备进行布防，建立报警通道
     * 只有布防后，设备的报警事件才会推送到SDK的回调函数
     * @param userId 登录句柄
     * @return 布防句柄，失败返回-1
     */
    public int setupAlarmChan(int userId) {
        if (!initialized || hCNetSDK == null) {
            logger.error("海康SDK未初始化，无法布防");
            return -1;
        }
        
        // 检查是否已经布防
        Integer existingHandle = alarmHandles.get(userId);
        if (existingHandle != null && existingHandle >= 0) {
            logger.debug("设备已布防: userId={}, alarmHandle={}", userId, existingHandle);
            return existingHandle;
        }
        
        try {
            // 使用V50版本的布防接口
            HCNetSDK.NET_DVR_SETUPALARM_PARAM_V50 setupParam = new HCNetSDK.NET_DVR_SETUPALARM_PARAM_V50();
            setupParam.dwSize = setupParam.size();
            setupParam.byLevel = 1; // 二级优先级
            setupParam.byAlarmInfoType = 1; // 新报警信息
            setupParam.byRetAlarmTypeV40 = 1; // 返回V40报警信息
            setupParam.byDeployType = 1; // 实时布防
            setupParam.write();
            
            int alarmHandle = hCNetSDK.NET_DVR_SetupAlarmChan_V50(userId, setupParam, null, 0);
            if (alarmHandle < 0) {
                int errorCode = getLastError();
                // 某些设备可能不支持V50，尝试V41
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
                    // 再尝试V30
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
     * 撤防
     * @param userId 登录句柄
     * @return 是否成功
     */
    public boolean closeAlarmChan(int userId) {
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
     * 海康SDK报警消息回调实现
     */
    class AlarmMessageCallback implements HCNetSDK.FMSGCallBack {
        @Override
        public void invoke(int lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen,
                Pointer pUser) {
            try {
                // 记录所有收到的报警消息，便于调试
                logger.info("收到海康报警回调: lCommand=0x{}, dwBufLen={}", Integer.toHexString(lCommand), dwBufLen);
                
                // 从pAlarmer中提取设备信息
                String deviceIP = null;
                int userId = -1;
                if (pAlarmer != null) {
                    pAlarmer.read();
                    if (pAlarmer.byDeviceIPValid == 1) {
                        deviceIP = new String(pAlarmer.sDeviceIP, java.nio.charset.StandardCharsets.UTF_8).trim();
                        // 移除末尾的空字符
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
                }
                
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
                            String alarmType = getAlarmTypeName(lCommand);
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

        try {
            // 确保目录存在
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 使用绝对路径（海康SDK要求绝对路径）
            // 规范化路径，移除 ./ 和 ../
            String absolutePath = file.getAbsolutePath();
            try {
                absolutePath = new File(absolutePath).getCanonicalPath();
            } catch (java.io.IOException e) {
                logger.warn("无法规范化路径，使用原始路径: {}", e.getMessage());
            }
            logger.debug("海康抓图参数: userId={}, channel={}, filePath={}, absolutePath={}",
                    userId, channel, filePath, absolutePath);

            // 参考DeviceController.java:448-518的实现
            // 海康SDK的抓图使用NET_DVR_CaptureJPEGPicture，需要userId和channel，不需要connectId
            HCNetSDK.NET_DVR_JPEGPARA jpegPara = new HCNetSDK.NET_DVR_JPEGPARA();
            jpegPara.wPicSize = 0; // 0=CIF, 使用当前分辨率
            jpegPara.wPicQuality = 2; // 图片质量：0-最好 1-较好 2-一般
            jpegPara.write();

            // 海康SDK要求使用GBK编码（中文SDK）
            // 如果使用UTF-8可能导致路径解析错误
            byte[] fileNameBytes;
            try {
                fileNameBytes = absolutePath.getBytes("GBK");
            } catch (java.io.UnsupportedEncodingException e) {
                // 如果GBK不支持，回退到UTF-8
                logger.warn("GBK编码不支持，使用UTF-8: {}", e.getMessage());
                fileNameBytes = absolutePath.getBytes("UTF-8");
            }

            byte[] fileNameArray = new byte[256];
            int copyLength = Math.min(fileNameBytes.length, fileNameArray.length - 1);
            System.arraycopy(fileNameBytes, 0, fileNameArray, 0, copyLength);
            // 确保字符串以null结尾
            fileNameArray[copyLength] = 0;

            // 海康SDK的通道号从1开始，确保channel >= 1
            int actualChannel = channel > 0 ? channel : 1;

            boolean result = hCNetSDK.NET_DVR_CaptureJPEGPicture(userId, actualChannel, jpegPara, fileNameArray);
            if (result) {
                logger.info("海康抓图成功: userId={}, channel={}, filePath={}", userId, actualChannel, absolutePath);
                return true;
            } else {
                int errorCode = getLastError();
                String errorMsg = getLastErrorString();
                logger.error("海康抓图失败: userId={}, channel={}, filePath={}, 错误码={}, 错误信息={}",
                        userId, actualChannel, absolutePath, errorCode, errorMsg);
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

        try {
            // 参考DeviceController.java:620-636的实现
            int commandCode = getPtzCommandCode(command);
            if (commandCode == -1) {
                logger.error("未知的云台控制命令: {}", command);
                return false;
            }

            // action: start=0, stop=1
            int stopFlag = "stop".equalsIgnoreCase(action) ? 1 : 0;

            // 使用带速度参数的接口，使speed参数生效
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
            // 海康绝对定位参数：角度*10。例如水平 36.5 度传 365
            struPos.wPanPos = (short) (pan * 10);
            struPos.wTiltPos = (short) (tilt * 10);
            struPos.wZoomPos = (short) (zoom * 10);
            struPos.write();

            boolean result = hCNetSDK.NET_DVR_SetDVRConfig(userId, HCNetSDK.NET_DVR_SET_PTZPOS, channel,
                    struPos.getPointer(), struPos.size());
            if (!result) {
                logger.error("海康云台绝对定位失败: userId={}, channel={}, 错误码={}", userId, channel, getLastError());
                return false;
            }

            logger.info("海康云台绝对定位成功: userId={}, channel={}, pan={}, tilt={}, zoom={}",
                    userId, channel, pan, tilt, zoom);
            return true;
        } catch (Exception e) {
            logger.error("海康云台绝对定位异常: userId={}, channel={}", userId, channel, e);
            return false;
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
