package com.hikvision.nvr.hikvision;

import com.hikvision.nvr.Common.ArchitectureChecker;
import com.hikvision.nvr.Common.osSelect;
import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.device.DeviceSDK;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.mqtt.MqttClient;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 海康威视SDK封装类
 */
public class HikvisionSDK implements DeviceSDK {
    private static final Logger logger = LoggerFactory.getLogger(HikvisionSDK.class);
    private static HikvisionSDK instance;
    private HCNetSDK hCNetSDK;
    private boolean initialized = false;
    private Config.SdkConfig sdkConfig;
    private DeviceManager deviceManager;
    private MqttClient mqttClient;
    private com.hikvision.nvr.service.AlarmService alarmService;

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
                    false
                );
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
            String baseLibPath;
            if (sdkConfig != null && sdkConfig.getLibPath() != null) {
                baseLibPath = sdkConfig.getLibPath();
            } else {
                baseLibPath = System.getProperty("user.dir") + "/lib/hikvision";
            }

            // 根据系统架构选择对应的库文件
            String osArch = System.getProperty("os.arch");
            File libFile = null;
            String libFileName = "libhcnetsdk.so";
            
            // 尝试使用架构特定的库文件
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
            
            // 如果架构特定库不存在，尝试使用默认库
            if (libFile == null || !libFile.exists()) {
                String defaultLibPath = baseLibPath + "/libhcnetsdk.so";
                libFile = new File(defaultLibPath);
                if (!libFile.exists()) {
                    logger.error("SDK库文件不存在: {} (已尝试: {})", defaultLibPath, baseLibPath + "/libhcnetsdk.so." + normalizeArchitecture(osArch));
                    return false;
                }
                libFileName = "libhcnetsdk.so";
            }
            
            // 检查库文件架构是否与系统架构匹配
            if (!ArchitectureChecker.checkArchitecture(libFile)) {
                logger.warn("海康SDK库文件架构不匹配，跳过加载");
                return false;
            }

            // 设置库路径到java.library.path（用于JNA查找库文件）
            String libDir = libFile.getParent();
            String currentLibPath = System.getProperty("java.library.path");
            String newLibPath = libDir + (currentLibPath != null ? ":" + currentLibPath : "");
            System.setProperty("java.library.path", newLibPath);
            
            // JNA的Native.loadLibrary需要库文件名（不带路径和扩展名）
            // 库文件名格式：libhcnetsdk.so 或 libhcnetsdk.so.x86_64
            // 需要提取基础名称：hcnetsdk
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
            
            // 使用库文件名加载（JNA会从java.library.path中查找）
            hCNetSDK = (HCNetSDK) Native.loadLibrary(libraryName, HCNetSDK.class);
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
            String libPath = config.getLibPath();
            if (libPath == null) {
                libPath = System.getProperty("user.dir") + "/lib/hikvision";
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
            String strPathCom = libPath;  // lib/hikvision目录
            HCNetSDK.NET_DVR_LOCAL_SDK_PATH struComPath = new HCNetSDK.NET_DVR_LOCAL_SDK_PATH();
            byte[] pathBytes = strPathCom.getBytes();
            int copyLen = Math.min(pathBytes.length, struComPath.sPath.length - 1);
            System.arraycopy(pathBytes, 0, struComPath.sPath, 0, copyLen);
            struComPath.sPath[copyLen] = 0;  // 确保以null结尾
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

    /**
     * 登录设备
     */
    @Override
    public int login(String ip, short port, String username, String password) {
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
        System.arraycopy(passwordBytes, 0, loginInfo.sPassword, 0, Math.min(passwordBytes.length, loginInfo.sPassword.length));

        // 设置端口和登录模式
        loginInfo.wPort = port;
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
            } else if (errorCode == HCNetSDK.NET_DVR_NETWORK_SEND_ERROR || errorCode == HCNetSDK.NET_DVR_NETWORK_RECV_ERROR) {
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
    public void setAlarmService(com.hikvision.nvr.service.AlarmService alarmService) {
        this.alarmService = alarmService;
        // 设置报警回调
        if (hCNetSDK != null && alarmService != null) {
            AlarmMessageCallback alarmCallback = new AlarmMessageCallback();
            hCNetSDK.NET_DVR_SetDVRMessCallBack(alarmCallback);
            logger.info("海康SDK报警回调已设置");
        }
    }
    
    /**
     * 海康SDK报警消息回调实现
     */
    class AlarmMessageCallback implements HCNetSDK.FMSGCallBack {
        @Override
        public void invoke(int lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser) {
            try {
                // 处理报警消息
                // 常见的报警类型：COMM_ALARM (0x1100), COMM_ALARM_V30 (0x4000), COMM_ALARM_V40 (0x4007)
                if (lCommand == HCNetSDK.COMM_ALARM || 
                    lCommand == HCNetSDK.COMM_ALARM_V30 || 
                    lCommand == HCNetSDK.COMM_ALARM_V40 ||
                    lCommand == HCNetSDK.COMM_ALARM_RULE ||
                    lCommand == HCNetSDK.COMM_SWITCH_ALARM) {
                    
                    // 从pAlarmer中提取设备信息
                    if (pAlarmer != null && pAlarmer.byDeviceIPValid == 1) {
                        String deviceIP = new String(pAlarmer.sDeviceIP, java.nio.charset.StandardCharsets.UTF_8).trim();
                        int channel = 1; // 默认通道1
                        if (pAlarmer.byChannelValid == 1) {
                            channel = pAlarmer.byChannel;
                        }
                        
                        // 通过IP和端口查找设备
                        if (deviceManager != null && alarmService != null) {
                            // 尝试通过userId查找设备（如果pAlarmer中有userId信息）
                            // 否则通过IP查找
                            int userId = -1;
                            if (pAlarmer.byUserIDValid == 1) {
                                userId = pAlarmer.lUserID;
                            }
                            
                            String deviceId = null;
                            if (userId > 0) {
                                deviceId = deviceManager.getDeviceIdByUserId(userId);
                            }
                            
                            // 如果通过userId找不到，尝试通过IP查找
                            if (deviceId == null) {
                                // 获取所有设备，查找匹配的IP
                                java.util.List<DeviceInfo> devices = deviceManager.getAllDevices();
                                for (DeviceInfo device : devices) {
                                    if (deviceIP.equals(device.getIp())) {
                                        deviceId = device.getDeviceId();
                                        break;
                                    }
                                }
                            }
                            
                            if (deviceId != null) {
                                String alarmType = "COMM_ALARM_" + Integer.toHexString(lCommand);
                                String alarmMessage = "报警类型: " + alarmType;
                                alarmService.handleAlarm(deviceId, channel, alarmType, alarmMessage);
                            } else {
                                logger.debug("无法找到设备: IP={}, userId={}", deviceIP, userId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("处理报警回调异常", e);
            }
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
            previewInfo.dwStreamType = streamType;  // 0=主码流, 1=子码流
            previewInfo.dwLinkMode = 0;  // TCP方式
            previewInfo.bBlocked = 1;  // 阻塞取流
            previewInfo.byProtoType = 0;  // 私有协议
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
            Thread.sleep(500);  // 等待数据写入完成
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
            jpegPara.wPicSize = 0;  // 0=CIF, 使用当前分辨率
            jpegPara.wPicQuality = 2;  // 图片质量：0-最好 1-较好 2-一般
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
            boolean result = hCNetSDK.NET_DVR_PTZControl_Other(userId, channel, commandCode, stopFlag);
            
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
            struFileCond.lChannel = channel;  // 通道号
            struFileCond.dwFileType = 0xFF;  // 所有文件类型
            struFileCond.byFindType = 0;  // 0=定时录像
            
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
                        Thread.sleep(100);  // 等待100ms后继续查询
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
                String currentStatus = device.getStatus();
                if ("offline".equals(currentStatus)) {
                    logger.debug("设备已经是离线状态，跳过更新: {}", deviceId);
                    return;
                }
                
                // 更新设备状态为离线并发送MQTT通知
                deviceManager.updateDeviceStatusWithNotification(deviceId, "offline");
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
