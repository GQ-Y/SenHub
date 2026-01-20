package com.digital.video.gateway.tiandy;

import com.digital.video.gateway.Common.ArchitectureChecker;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.device.DeviceSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 天地伟业SDK封装类
 */
public class TiandySDK implements DeviceSDK {
    private static final Logger logger = LoggerFactory.getLogger(TiandySDK.class);
    private static TiandySDK instance;
    private NvssdkLibrary nvssdkLibrary;
    private boolean initialized = false;
    private Config.SdkConfig sdkConfig;

    // 回调对象（延迟初始化，在库加载后创建，确保JNA能正确映射）
    private NvssdkLibrary.MAIN_NOTIFY_V4 emptyMainNotify;
    private NvssdkLibrary.ALARM_NOTIFY_V4 emptyAlarmNotify;
    private NvssdkLibrary.ALARM_NOTIFY_V4 alarmNotifyCallback;
    private com.digital.video.gateway.service.AlarmService alarmService;
    private com.digital.video.gateway.device.DeviceManager deviceManager;
    private NvssdkLibrary.PARACHANGE_NOTIFY_V4 emptyParaNotify;
    private NvssdkLibrary.COMRECV_NOTIFY_V4 emptyComNotify;
    private NvssdkLibrary.PROXY_NOTIFY emptyProxyNotify;

    /**
     * 初始化回调对象（在库加载后调用）
     */
    private void initCallbacks() {
        // 空的回调对象（SDK要求不能传null，必须使用匿名内部类，不能使用lambda）
        // 注意：MAIN_NOTIFY_V4的第二个参数必须是NativeLong，不能是long（与官方示例一致）
        emptyMainNotify = new NvssdkLibrary.MAIN_NOTIFY_V4() {
            @Override
            public void apply(int iLogonID, com.sun.jna.NativeLong wParam, com.sun.jna.Pointer lParam,
                    com.sun.jna.Pointer notifyUserData) {
                // 空实现
            }
        };

        emptyAlarmNotify = new NvssdkLibrary.ALARM_NOTIFY_V4() {
            @Override
            public void apply(int ulLogonID, int iChan, int iAlarmState, int iAlarmType, com.sun.jna.Pointer iUser) {
                // 空实现（默认使用空回调，如果设置了alarmService则使用实际回调）
                if (alarmNotifyCallback != null) {
                    alarmNotifyCallback.apply(ulLogonID, iChan, iAlarmState, iAlarmType, iUser);
                }
            }
        };

        // 初始化报警回调（如果alarmService已设置）
        if (alarmService != null) {
            // 创建报警回调
            alarmNotifyCallback = new NvssdkLibrary.ALARM_NOTIFY_V4() {
                @Override
                public void apply(int ulLogonID, int iChan, int iAlarmState, int iAlarmType,
                        com.sun.jna.Pointer iUser) {
                    try {
                        // 处理报警事件
                        if (alarmService != null) {
                            // 通过logonID查找设备信息
                            // 这里需要从设备管理器中查找对应的设备
                            String alarmType = "Tiandy_Alarm_" + iAlarmType;
                            String alarmMessage = String.format("天地伟业报警: logonID=%d, channel=%d, state=%d, type=%d",
                                    ulLogonID, iChan, iAlarmState, iAlarmType);
                            alarmService.handleAlarm(String.valueOf(ulLogonID), iChan, alarmType, alarmMessage);
                        }
                    } catch (Exception e) {
                        logger.error("处理天地伟业报警回调异常", e);
                    }
                }
            };
        }

        emptyParaNotify = new NvssdkLibrary.PARACHANGE_NOTIFY_V4() {
            @Override
            public void apply(int ulLogonID, int iChan, int iParaType, TiandySDKStructure.STR_Para strPara,
                    com.sun.jna.Pointer iUser) {
                // 空实现
            }
        };

        emptyComNotify = new NvssdkLibrary.COMRECV_NOTIFY_V4() {
            @Override
            public void apply(int ulLogonID, com.sun.jna.Pointer cData, int iLen, int iComNo,
                    com.sun.jna.Pointer iUser) {
                // 空实现
            }
        };

        emptyProxyNotify = new NvssdkLibrary.PROXY_NOTIFY() {
            @Override
            public void apply(int ulLogonID, int iCmdKey, com.sun.jna.Pointer cData, int iLen,
                    com.sun.jna.Pointer iUser) {
                // 空实现
            }
        };
    }

    private TiandySDK() {
    }
    
    /**
     * 设置报警服务和设备管理器（用于接收和处理报警事件）
     * 必须在SDK初始化后调用此方法，才能正确接收报警回调
     */
    public void setAlarmService(com.digital.video.gateway.service.AlarmService alarmService) {
        this.alarmService = alarmService;
        logger.info("天地伟业SDK报警服务已设置");
        
        // 重新初始化报警回调（因为initCallbacks在SDK初始化时alarmService还是null）
        initAlarmCallback();
    }
    
    /**
     * 设置设备管理器（用于通过loginHandle查找设备ID）
     */
    public void setDeviceManager(com.digital.video.gateway.device.DeviceManager deviceManager) {
        this.deviceManager = deviceManager;
        logger.info("天地伟业SDK设备管理器已设置");
    }
    
    /**
     * 初始化报警回调
     */
    private void initAlarmCallback() {
        if (alarmService != null && initialized) {
            alarmNotifyCallback = new NvssdkLibrary.ALARM_NOTIFY_V4() {
                @Override
                public void apply(int ulLogonID, int iChan, int iAlarmState, int iAlarmType,
                        com.sun.jna.Pointer iUser) {
                    try {
                        logger.info("收到天地伟业报警回调: logonID={}, channel={}, state={}, type={}", 
                                ulLogonID, iChan, iAlarmState, iAlarmType);
                        if (TiandySDK.this.alarmService != null) {
                            // 通过logonID查找真正的设备ID
                            String deviceId = null;
                            if (TiandySDK.this.deviceManager != null) {
                                deviceId = TiandySDK.this.deviceManager.getDeviceIdByLoginHandle(ulLogonID, "tiandy");
                            }
                            if (deviceId == null) {
                                // 如果找不到，使用logonID作为临时标识
                                deviceId = "tiandy_" + ulLogonID;
                                logger.warn("无法通过logonID={}找到设备，使用临时ID: {}", ulLogonID, deviceId);
                            }
                            
                            String alarmTypeStr = "Tiandy_Alarm_" + iAlarmType;
                            String alarmMessage = String.format("天地伟业报警: device=%s, channel=%d, state=%d, type=%d",
                                    deviceId, iChan, iAlarmState, iAlarmType);
                            TiandySDK.this.alarmService.handleAlarm(deviceId, iChan, alarmTypeStr, alarmMessage);
                        }
                    } catch (Exception e) {
                        logger.error("处理天地伟业报警回调异常", e);
                    }
                }
            };
            logger.info("天地伟业SDK报警回调已重新初始化");
        }
    }

    public static synchronized TiandySDK getInstance() {
        if (instance == null) {
            instance = new TiandySDK();
        }
        return instance;
    }

    @Override
    public boolean init(Config.SdkConfig config) {
        if (initialized) {
            logger.debug("天地伟业SDK已经初始化，跳过重复初始化");
            return true;
        }

        this.sdkConfig = config;

        try {
            // 加载SDK库
            if (!loadLibrary()) {
                logger.warn("天地伟业SDK库加载失败（可能原因：库文件不存在或架构不匹配），跳过初始化");
                return false;
            }

            // 在库加载后初始化回调对象（确保JNA能正确映射回调接口）
            initCallbacks();

            logger.debug("天地伟业SDK初始化流程开始");

            // 根据官方示例，先获取SDK版本信息（必须调用read()方法）
            try {
                TiandySDKStructure.SDK_VERSION ver = new TiandySDKStructure.SDK_VERSION();
                int versionRet = nvssdkLibrary.NetClient_GetVersion(ver);
                ver.read(); // 根据官方示例，必须调用read()方法
                logger.debug("天地伟业SDK获取版本信息返回值: {}", versionRet);
                if (versionRet == NvssdkLibrary.RET_SUCCESS && ver.m_cVerInfo != null) {
                    String versionInfo = new String(ver.m_cVerInfo).trim();
                    if (!versionInfo.isEmpty()) {
                        logger.info("天地伟业SDK版本: {}", versionInfo);
                    }
                }
            } catch (Exception e) {
                logger.warn("获取SDK版本信息失败: {}", e.getMessage());
            }

            // 尝试设置本地库路径（在Startup之前）
            // 某些SDK版本可能需要先设置本地库路径才能正确初始化
            try {
                String libDir = com.digital.video.gateway.Common.LibraryPathHelper.getSDKLibPath("tiandy");
                if (libDir != null) {
                    TiandySDKStructure.LocalSDKPath localSDKPath = new TiandySDKStructure.LocalSDKPath();
                    localSDKPath.iSize = localSDKPath.size();
                    localSDKPath.iType = NvssdkLibrary.INIT_CONFIG_LOCAL_LIBRARY_PATH;
                    byte[] pathBytes = libDir.getBytes("UTF-8");
                    System.arraycopy(pathBytes, 0, localSDKPath.cPath, 0,
                            Math.min(pathBytes.length, localSDKPath.cPath.length - 1));
                    localSDKPath.cPath[Math.min(pathBytes.length, localSDKPath.cPath.length - 1)] = 0; // Null-terminate
                    localSDKPath.write();
                    int setPathRet = nvssdkLibrary.NetClient_SetSDKInitConfig(
                            NvssdkLibrary.INIT_CONFIG_LOCAL_LIBRARY_PATH, localSDKPath.getPointer(),
                            localSDKPath.size());
                    logger.debug("天地伟业SDK设置本地库路径返回值: {}", setPathRet);
                    if (setPathRet != NvssdkLibrary.RET_SUCCESS) {
                        logger.warn("天地伟业SDK设置本地库路径失败，返回值: {}（继续初始化）", setPathRet);
                    }
                }
            } catch (Exception e) {
                logger.warn("设置本地库路径异常: {}（继续初始化）", e.getMessage());
            }

            // 参考官方示例JavaClientDemo.java:188-200的初始化顺序
            logger.debug("步骤1: 设置回调函数");
            int setNotifyRet = nvssdkLibrary.NetClient_SetNotifyFunction_V4(
                    emptyMainNotify, emptyAlarmNotify, emptyParaNotify, emptyComNotify, emptyProxyNotify);
            logger.debug("天地伟业SDK设置回调函数返回值: {}", setNotifyRet);

            if (setNotifyRet != NvssdkLibrary.RET_SUCCESS) {
                int lastError = nvssdkLibrary.NetClient_GetLastError();
                logger.error("天地伟业SDK设置回调函数失败，返回值: {}，GetLastError返回值: {}。这是致命错误，SDK无法使用",
                        setNotifyRet, lastError);
                initialized = false;
                return false;
            }

            logger.debug("步骤2: 启动SDK");
            int startupRet = nvssdkLibrary.NetClient_Startup_V4(0, 0, 0);
            logger.debug("天地伟业SDK Startup返回值: {}", startupRet);

            if (startupRet != NvssdkLibrary.RET_SUCCESS) {
                int lastError = nvssdkLibrary.NetClient_GetLastError();
                logger.error("天地伟业SDK Startup失败，返回值: {}，GetLastError返回值: {}。SDK未正确初始化，无法使用",
                        startupRet, lastError);
                // ⚠️ Startup失败时，需要清理已设置的回调
                try {
                    nvssdkLibrary.NetClient_Cleanup();
                } catch (Exception cleanupEx) {
                    logger.warn("清理失败的SDK时发生异常", cleanupEx);
                }
                initialized = false;
                return false;
            }

            initialized = true;
            logger.info("天地伟业SDK初始化成功");
            return true;

        } catch (Exception e) {
            logger.error("天地伟业SDK初始化异常", e);
            return false;
        }
    }

    /**
     * 加载SDK库
     */
    private boolean loadLibrary() {
        if (nvssdkLibrary != null) {
            return true;
        }

        try {
            // 天地伟业SDK库文件在 ./lib/x86/tiandy/ 目录下
            // 注意：天地伟业仅支持x86架构，不支持ARM
            String libDir = com.digital.video.gateway.Common.LibraryPathHelper.getSDKLibPath("tiandy");

            // 检查是否支持当前架构
            if (libDir == null) {
                String archDir = com.digital.video.gateway.Common.LibraryPathHelper.getArchitectureDir();
                logger.warn("天地伟业SDK仅支持x86架构，当前系统架构为{}，跳过初始化", archDir);
                return false;
            }

            logger.debug("天地伟业SDK库路径: {} (架构: {})", libDir,
                    com.digital.video.gateway.Common.LibraryPathHelper.getArchitectureDir());

            File libDirFile = new File(libDir);
            if (!libDirFile.exists()) {
                logger.error("天地伟业SDK库目录不存在: {}", libDir);
                return false;
            }

            // 查找实际的库文件
            String libFileName = "libnvssdk.so";
            File libFile = new File(libDir, libFileName);

            if (!libFile.exists()) {
                logger.error("天地伟业SDK库文件不存在: {}", libFile.getAbsolutePath());
                return false;
            }

            // 检查库文件架构是否与系统架构匹配
            if (!ArchitectureChecker.checkArchitecture(libFile)) {
                logger.warn("天地伟业SDK库文件架构不匹配，跳过加载");
                return false;
            }

            String actualLibPath = libFile.getAbsolutePath();

            // 设置库路径到java.library.path（用于加载依赖库）
            // 使用LibraryPathHelper构建完整的库路径
            String newLibPath = com.digital.video.gateway.Common.LibraryPathHelper.buildLibraryPath();
            System.setProperty("java.library.path", newLibPath);
            logger.debug("设置java.library.path: {}", newLibPath);

            // 官方示例使用：Native.loadLibrary("nvssdk", NvssdkLibrary.class)
            // 这样JNA会从java.library.path中查找libnvssdk.so
            try {
                // 先尝试使用库名加载（官方示例的方式）
                nvssdkLibrary = (NvssdkLibrary) Native.loadLibrary("nvssdk", NvssdkLibrary.class);
                logger.info("天地伟业SDK库加载成功（使用库名: nvssdk），库路径: {}", actualLibPath);
                return true;
            } catch (UnsatisfiedLinkError e) {
                // 如果库名加载失败，尝试使用绝对路径加载
                logger.warn("使用库名加载失败，尝试使用绝对路径: {}", e.getMessage());
                try {
                    nvssdkLibrary = (NvssdkLibrary) Native.load(actualLibPath, NvssdkLibrary.class);
                    logger.info("天地伟业SDK库加载成功（使用绝对路径），库路径: {}", actualLibPath);
                    return true;
                } catch (UnsatisfiedLinkError e2) {
                    logger.error("加载天地伟业SDK库失败，库路径: {}，错误: {}", actualLibPath, e2.getMessage());
                    return false;
                }
            }

        } catch (Exception e) {
            logger.error("加载天地伟业SDK库异常", e);
            return false;
        }
    }

    @Override
    public int login(String ip, int port, String username, String password) {
        // 天地伟业SDK默认端口为3000，但应以数据库配置的端口为准
        return loginWithIntPort(ip, port, username, password);
    }

    /**
     * 使用int类型端口登录
     * 天地伟业SDK默认端口为3000
     */
    public int loginWithIntPort(String ip, int port, String username, String password) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return -1;
        }

        try {
            // 按照天地伟业SDK示例代码的方式构造登录参数（参考Device.java第257-264行）
            // 官方示例直接使用getBytes()赋值，JNA的Structure会自动处理字节数组
            String strCharSet = "UTF-8";

            TiandySDKStructure.tagLogonPara logonPara = new TiandySDKStructure.tagLogonPara();
            logonPara.iSize = logonPara.size();

            // 按照官方示例，直接使用getBytes()赋值（JNA会自动处理字节数组到固定大小数组的转换）
            logonPara.cNvsIP = ip.getBytes("UTF-8");
            logonPara.iNvsPort = port;
            logonPara.cUserName = username.getBytes("UTF-8");
            logonPara.cUserPwd = password.getBytes("UTF-8");
            logonPara.cCharSet = strCharSet.getBytes("UTF-8");

            // 其他字段保持默认值（null或0）
            // cProxy, cNvsName, cProductID, cAccontName, cAccontPasswd, cNvsIPV6 使用默认值

            logonPara.write();

            logger.info("开始登录天地伟业设备: {}:{}, 用户: {}", ip, port, username);
            logger.debug("登录参数: iSize={}, iNvsPort={}, cNvsIP长度={}, cUserName长度={}, cUserPwd长度={}",
                    logonPara.iSize, logonPara.iNvsPort,
                    new String(logonPara.cNvsIP).trim().length(),
                    new String(logonPara.cUserName).trim().length(),
                    new String(logonPara.cUserPwd).trim().length());

            int logonID = nvssdkLibrary.NetClient_SyncLogon(NvssdkLibrary.SERVER_NORMAL, logonPara.getPointer(),
                    logonPara.iSize);

            // 获取详细错误信息
            int lastError = nvssdkLibrary.NetClient_GetLastError();
            String errorString = getLastErrorString();

            if (logonID >= 0) {
                logger.info("天地伟业设备登录成功: {}:{} (logonID: {})", ip, port, logonID);
                return logonID;
            } else {
                // 根据错误码提供更详细的错误信息
                String errorDetail = "未知错误";
                switch (logonID) {
                    case NvssdkLibrary.RET_SYNCLOGON_TIMEOUT:
                        errorDetail = "登录超时";
                        break;
                    case NvssdkLibrary.RET_SYNCLOGON_USENAME_ERROR:
                        errorDetail = "用户名错误";
                        break;
                    case NvssdkLibrary.RET_SYNCLOGON_USRPWD_ERROR:
                        errorDetail = "密码错误";
                        break;
                    case NvssdkLibrary.RET_SYNCLOGON_PWDERRTIMES_OVERRUN:
                        errorDetail = "密码错误次数超限";
                        break;
                    case NvssdkLibrary.RET_SYNCLOGON_NET_ERROR:
                        errorDetail = "网络错误（请检查设备IP和端口是否可达）";
                        break;
                    case NvssdkLibrary.RET_SYNCLOGON_PORT_ERROR:
                        errorDetail = "端口错误";
                        break;
                    case NvssdkLibrary.RET_SYNCLOGON_UNKNOW_ERROR:
                        errorDetail = "未知错误";
                        break;
                    default:
                        errorDetail = "错误码: " + logonID;
                        break;
                }
                logger.error("天地伟业设备登录失败: {}:{} (错误码: {}, 错误详情: {}, GetLastError: {}, GetLastErrorString: {})",
                        ip, port, logonID, errorDetail, lastError, errorString);
                return -1;
            }

        } catch (Exception e) {
            logger.error("天地伟业设备登录异常: {}:{}", ip, port, e);
            return -1;
        }
    }

    @Override
    public boolean logout(int userId) {
        if (!initialized || nvssdkLibrary == null) {
            return false;
        }

        if (userId < 0) {
            return false;
        }

        int ret = nvssdkLibrary.NetClient_Logoff(userId);
        if (ret == NvssdkLibrary.RET_SUCCESS) {
            logger.info("天地伟业设备登出成功: {}", userId);
            return true;
        } else {
            logger.error("天地伟业设备登出失败: {}, 错误码: {}", userId, ret);
            return false;
        }
    }

    @Override
    public int getLastError() {
        if (nvssdkLibrary == null) {
            return -1;
        }
        return nvssdkLibrary.NetClient_GetLastError();
    }

    @Override
    public String getLastErrorString() {
        int errorCode = getLastError();
        if (errorCode == 0) {
            return "没有错误";
        }

        // 根据同步登录的错误码返回描述
        switch (errorCode) {
            case NvssdkLibrary.RET_SYNCLOGON_TIMEOUT:
                return "登录超时";
            case NvssdkLibrary.RET_SYNCLOGON_USENAME_ERROR:
                return "用户名错误";
            case NvssdkLibrary.RET_SYNCLOGON_USRPWD_ERROR:
                return "密码错误";
            case NvssdkLibrary.RET_SYNCLOGON_PWDERRTIMES_OVERRUN:
                return "密码错误次数超限";
            case NvssdkLibrary.RET_SYNCLOGON_NET_ERROR:
                return "网络错误";
            case NvssdkLibrary.RET_SYNCLOGON_PORT_ERROR:
                return "端口错误";
            case NvssdkLibrary.RET_SYNCLOGON_UNKNOW_ERROR:
                return "未知错误";
            default:
                return "错误码: " + errorCode;
        }
    }

    @Override
    public void cleanup() {
        if (initialized && nvssdkLibrary != null) {
            nvssdkLibrary.NetClient_Cleanup();
            initialized = false;
            logger.info("天地伟业SDK清理完成");
        }
    }

    @Override
    public String getBrand() {
        return "tiandy";
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    // ========== 功能方法实现 ==========

    @Override
    public int startRealPlay(int userId, int channel, int streamType) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return -1;
        }

        try {
            // 参考Channel.java:245-318的实现
            // 首先验证登录状态（确保连接保持有效）
            int logonStatus = nvssdkLibrary.NetClient_GetLogonStatus(userId);
            if (logonStatus != NvssdkLibrary.LOGON_SUCCESS) { // 0表示登录成功
                logger.error("设备登录状态无效: userId={}, logonStatus={}（0=成功, 4=失败, 5=超时），无法启动预览",
                        userId, logonStatus);
                return -1;
            }

            // 天地伟业SDK的通道号从0开始，如果传入的是1-based的通道号，需要转换为0-based
            int channelNo = channel;
            if (channel > 0) {
                channelNo = channel - 1; // 转换为0-based索引
            }

            // 参考Channel.java:253-277，必须验证通道号是否有效
            IntByReference piDigitalChanCount = new IntByReference();
            int ret = nvssdkLibrary.NetClient_GetDigitalChannelNum(userId, piDigitalChanCount);
            if (ret != NvssdkLibrary.RET_SUCCESS) {
                logger.error("获取数字通道数失败: userId={}, 错误码={}", userId, ret);
                return -1;
            }
            int digitalChanCount = piDigitalChanCount.getValue();

            // 获取总通道数
            IntByReference piChanTotalCount = new IntByReference();
            ret = nvssdkLibrary.NetClient_GetChannelNum(userId, piChanTotalCount);
            if (ret != NvssdkLibrary.RET_SUCCESS) {
                logger.error("获取总通道数失败: userId={}, 错误码={}", userId, ret);
                return -1;
            }
            int chanTotalCount = piChanTotalCount.getValue();

            // 如果数字通道数为0，则是IPC设备，使用总通道数
            if (digitalChanCount == 0) {
                digitalChanCount = chanTotalCount;
            }

            logger.debug("通道信息: 总通道数={}, 数字通道数={}, 请求通道号={}(0-based: {})",
                    chanTotalCount, digitalChanCount, channel, channelNo);

            // 验证通道号是否在有效范围内
            if (channelNo < 0 || channelNo >= digitalChanCount) {
                logger.error("通道号无效: channelNo={}, 有效范围: 0-{}", channelNo, digitalChanCount - 1);
                return -1;
            }

            TiandySDKStructure.tagNetClientPara tVideoPara = new TiandySDKStructure.tagNetClientPara();

            // 设置预览参数
            tVideoPara.iSize = tVideoPara.size();

            // 初始化CLIENTINFO结构体的所有必要字段
            tVideoPara.tCltInfo.m_iServerID = userId; // logon handle
            tVideoPara.tCltInfo.m_iChannelNo = channelNo; // 使用0-based通道号
            tVideoPara.tCltInfo.m_iStreamNO = streamType; // 0=主码流, 1=子码流
            tVideoPara.tCltInfo.m_iNetMode = 1; // TCP方式
            tVideoPara.tCltInfo.m_iTimeout = 20;

            // 初始化字节数组字段（避免未初始化导致的问题）
            if (tVideoPara.tCltInfo.m_cNetFile == null) {
                tVideoPara.tCltInfo.m_cNetFile = new byte[255];
            }
            if (tVideoPara.tCltInfo.m_cRemoteIP == null) {
                tVideoPara.tCltInfo.m_cRemoteIP = new byte[16];
            }

            // 设置缓冲区参数（参考官方示例Channel.java:287-299和VideoCtrl.java:336-344）
            tVideoPara.tCltInfo.m_iBufferCount = 20; // 缓冲区数量，官方示例使用20
            tVideoPara.tCltInfo.m_iDelayNum = 1; // 延迟数量，官方示例使用1
            tVideoPara.tCltInfo.m_iDelayTime = 0; // 延迟时间
            tVideoPara.tCltInfo.m_iTTL = 8; // TTL值，官方示例使用8
            tVideoPara.tCltInfo.m_iFlag = 0; // 标志位
            tVideoPara.tCltInfo.m_iPosition = 0; // 位置
            tVideoPara.tCltInfo.m_iSpeed = 0; // 速度

            // 回调函数设置（可以为null，但需要确保结构体正确）
            tVideoPara.pCbkFullFrm = null; // 完整帧回调（可以为null）
            tVideoPara.pvCbkFullFrmUsrData = null;
            tVideoPara.pCbkRawFrm = null; // 原始流回调（可以为null）
            tVideoPara.pvCbkRawFrmUsrData = null;
            // 允许解码（参考Channel.java:297，预览时使用ALLOW_DECODE）
            // 虽然我们不需要显示，但SDK可能需要解码来建立连接
            tVideoPara.iIsForbidDecode = NvssdkLibrary.RAW_NOTIFY_ALLOW_DECODE;
            tVideoPara.pvWnd = null; // 不显示视频窗口

            // 先写入CLIENTINFO，再写入整个结构体
            tVideoPara.tCltInfo.write();
            tVideoPara.write();

            IntByReference piConnectID = new IntByReference();
            // 使用Pointer传递结构体（更安全，避免JNA自动转换问题）
            int iRet = nvssdkLibrary.NetClient_SyncRealPlay(piConnectID, tVideoPara.getPointer(), tVideoPara.iSize);

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                int connectID = piConnectID.getValue();
                logger.info("天地伟业预览启动成功: userId={}, channel={}(0-based: {}), streamType={}, connectID={}",
                        userId, channel, channelNo, streamType, connectID);
                return connectID;
            } else if (iRet == NvssdkLibrary.RET_SYNCREALPLAY_TIMEOUT) {
                logger.error("天地伟业预览启动超时: userId={}, channel={}(0-based: {})", userId, channel, channelNo);
                return -1;
            } else {
                logger.error("天地伟业预览启动失败: userId={}, channel={}(0-based: {}), 错误码={}", userId, channel, channelNo,
                        iRet);
                return -1;
            }
        } catch (Exception e) {
            logger.error("天地伟业预览启动异常: userId={}, channel={}", userId, channel, e);
            return -1;
        }
    }

    @Override
    public boolean stopRealPlay(int connectId) {
        if (!initialized || nvssdkLibrary == null) {
            return false;
        }

        if (connectId < 0) {
            return false;
        }

        try {
            int iRet = nvssdkLibrary.NetClient_StopRealPlay(connectId, 1);
            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                logger.info("天地伟业预览停止成功: connectID={}", connectId);
                return true;
            } else {
                logger.error("天地伟业预览停止失败: connectID={}, 错误码={}", connectId, iRet);
                return false;
            }
        } catch (Exception e) {
            logger.error("天地伟业预览停止异常: connectID={}", connectId, e);
            return false;
        }
    }

    @Override
    public boolean startRecording(int connectId, String filePath) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return false;
        }

        if (connectId < 0) {
            logger.error("无效的预览连接ID: {}", connectId);
            return false;
        }

        try {
            // 参考Channel.java:401-425的实现
            // 确保文件路径以.sdv结尾（天地伟业默认格式），并添加\0结尾
            String actualFilePath = filePath;
            if (!actualFilePath.endsWith(".sdv")) {
                actualFilePath = filePath + ".sdv";
            }
            // 参考Channel.java:414，文件名需要以\0结尾
            actualFilePath += "\0";

            ByteBuffer strBuffer = ByteBuffer.wrap(actualFilePath.getBytes());
            int iRet = nvssdkLibrary.NetClient_StartCaptureFile(connectId, strBuffer, NvssdkLibrary.REC_FILE_TYPE_SDV);

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                logger.info("天地伟业录制启动成功: connectID={}, filePath={}", connectId, actualFilePath.trim());
                return true;
            } else {
                logger.error("天地伟业录制启动失败: connectID={}, filePath={}, 错误码={}", connectId, actualFilePath.trim(), iRet);
                return false;
            }
        } catch (Exception e) {
            logger.error("天地伟业录制启动异常: connectID={}, filePath={}", connectId, filePath, e);
            return false;
        }
    }

    @Override
    public boolean stopRecording(int connectId) {
        if (!initialized || nvssdkLibrary == null) {
            return false;
        }

        if (connectId < 0) {
            return false;
        }

        try {
            int iRet = nvssdkLibrary.NetClient_StopCaptureFile(connectId);
            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                logger.info("天地伟业录制停止成功: connectID={}", connectId);
                return true;
            } else {
                logger.error("天地伟业录制停止失败: connectID={}, 错误码={}", connectId, iRet);
                return false;
            }
        } catch (Exception e) {
            logger.error("天地伟业录制停止异常: connectID={}", connectId, e);
            return false;
        }
    }

    @Override
    public boolean capturePicture(int connectId, int userId, int channel, String filePath, int pictureType) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return false;
        }

        try {
            // 使用NetClient_CapturePicByDevice直接抓图，不需要预览连接
            // 参考NetSdkClient.h:3830-3848
            // 天地伟业SDK的通道号从0开始，需要转换为0-based
            int channelNo = channel > 0 ? channel - 1 : 0;

            // 确保文件路径有正确的扩展名
            String actualFilePath = filePath;
            if (pictureType == NvssdkLibrary.CAPTURE_PICTURE_TYPE_BMP && !actualFilePath.endsWith(".bmp")) {
                actualFilePath = filePath + ".bmp";
            } else if (pictureType == NvssdkLibrary.CAPTURE_PICTURE_TYPE_JPG && !actualFilePath.endsWith(".jpg")) {
                actualFilePath = filePath + ".jpg";
            }

            // iQvalue: 图片质量值（通常1-100，0表示使用设备默认值）
            int iQvalue = 0; // 使用设备默认质量

            // 如果只需要保存到文件，ptSnapPicData可以传null
            // 参考官方文档：when _ptSnapPicData==NULL:don't save picture data (只保存到文件)
            ByteBuffer strBuffer = ByteBuffer.wrap(actualFilePath.getBytes());
            int iRet = nvssdkLibrary.NetClient_CapturePicByDevice(userId, channelNo, iQvalue, strBuffer, null, 0);

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                logger.info("天地伟业直接抓图成功: userId={}, channel={}(0-based: {}), filePath={}",
                        userId, channel, channelNo, actualFilePath);
                return true;
            } else {
                logger.error("天地伟业直接抓图失败: userId={}, channel={}(0-based: {}), filePath={}, 错误码={}",
                        userId, channel, channelNo, actualFilePath, iRet);
                return false;
            }
        } catch (Exception e) {
            logger.error("天地伟业直接抓图异常: userId={}, channel={}, filePath={}", userId, channel, filePath, e);
            return false;
        }
    }

    @Override
    public boolean ptzControl(int userId, int channel, String command, String action, int speed) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return false;
        }

        try {
            // 天地伟业SDK的通道号从0开始，需要转换为0-based
            int channelNo = channel > 0 ? channel - 1 : 0;

            // 获取控制码
            int actionCode;
            if ("stop".equalsIgnoreCase(action)) {
                // ⚠️ 修复：处理停止动作
                if ("zoom_in".equalsIgnoreCase(command)) {
                    actionCode = NvssdkLibrary.ZOOM_BIG_STOP; // 32 - 放大停止
                } else if ("zoom_out".equalsIgnoreCase(command)) {
                    actionCode = NvssdkLibrary.ZOOM_SMALL_STOP; // 34 - 缩小停止
                } else {
                    actionCode = NvssdkLibrary.PROTOCOL_MOVE_STOP; // 9 - 通用移动停止
                }
                logger.debug("云台停止动作: command={}, channel={}, actionCode={}", command, channel, actionCode);
            } else {
                // 处理开始动作
                actionCode = getPtzControlCode(command);
            }

            if (actionCode == 0) {
                logger.error("未知的云台控制命令或动作: command={}, action={}", command, action);
                return false;
            }

            // ⚠️ 关键修复：使用DeviceCtrlEx API（协议模式），参考官方示例
            // 官方示例：JavaVideoCtrlDemo/src/src/VideoCtrl.java:466-487行
            int iRet;

            // 停止动作通常速度设为0（或者SDK内部忽略）
            int currentSpeed = "stop".equalsIgnoreCase(action) ? 0 : speed;

            // 根据官方示例，不同方向的速度参数位置不同
            if (actionCode == NvssdkLibrary.PROTOCOL_MOVE_UP ||
                    actionCode == NvssdkLibrary.PROTOCOL_MOVE_DOWN) {
                // 上下移动：速度在param2（第5个参数）
                // DeviceCtrlEx(logonID, channel, action, 0, speed, 0)
                iRet = nvssdkLibrary.NetClient_DeviceCtrlEx(userId, channelNo, actionCode, 0, currentSpeed, 0);
                logger.debug("云台上下控制: actionCode={}, speed在param2={}, action={}", actionCode, currentSpeed, action);

            } else if (actionCode == NvssdkLibrary.PROTOCOL_MOVE_LEFT ||
                    actionCode == NvssdkLibrary.PROTOCOL_MOVE_RIGHT) {
                // 左右移动：速度在param1（第4个参数）
                // DeviceCtrlEx(logonID, channel, action, speed, 0, 0)
                iRet = nvssdkLibrary.NetClient_DeviceCtrlEx(userId, channelNo, actionCode, currentSpeed, 0, 0);
                logger.debug("云台左右控制: actionCode={}, speed在param1={}, action={}", actionCode, currentSpeed, action);

            } else {
                // 其他命令（停止、自动、缩放等）：所有参数为0
                // DeviceCtrlEx(logonID, channel, action, 0, 0, 0)
                // 变倍控制即使有速度通常也在 param2 或 param1，但官方示例提示中变倍并未提及速度，传0是最安全的
                iRet = nvssdkLibrary.NetClient_DeviceCtrlEx(userId, channelNo, actionCode, 0, 0, 0);
                logger.debug("云台其他控制: actionCode={}, action={}", actionCode, action);
            }

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                logger.info("天地伟业云台控制成功: userId={}, channel={} (0-based: {}), command={}, actionCode={}, speed={}",
                        userId, channel, channelNo, command, actionCode, speed);
                return true;
            } else {
                logger.error("天地伟业云台控制失败: userId={}, channel={} (0-based: {}), command={}, actionCode={}, 错误码={}",
                        userId, channel, channelNo, command, actionCode, iRet);
                return false;
            }
        } catch (Exception e) {
            logger.error("天地伟业云台控制异常: userId={}, channel={}, command={}", userId, channel, command, e);
            return false;
        }
    }

    @Override
    public boolean gotoAngle(int userId, int channel, float pan, float tilt, float zoom) {
        // 目前天地伟业 SDK 暂未实现该接口，仅作存根
        logger.warn("天地伟业 SDK 暂不支持绝对定位接口: userId={}, channel={}, pan={}, tilt={}", userId, channel, pan, tilt);
        return false;
    }

    /**
     * 获取云台控制码
     * 根据官方示例代码（NVSSDK.java和VideoCtrl.java）确认的控制码值
     * 参考：VideoCtrl.java:468行提示信息 "up-1, down-2, left-3, right-4"
     * 参考：NVSSDK.java中定义的常量 PROTOCOL_MOVE_UP=1, PROTOCOL_MOVE_DOWN=2等
     * 注意：透明通道控制（tagTransparentChannelControl）使用的控制码与协议模式（DeviceCtrlEx）相同
     */
    private int getPtzControlCode(String command) {
        // 根据官方示例代码确认的控制码值
        switch (command.toLowerCase()) {
            case "up":
                return NvssdkLibrary.PROTOCOL_MOVE_UP; // 1 - 上
            case "down":
                return NvssdkLibrary.PROTOCOL_MOVE_DOWN; // 2 - 下
            case "left":
                return NvssdkLibrary.PROTOCOL_MOVE_LEFT; // 3 - 左
            case "right":
                return NvssdkLibrary.PROTOCOL_MOVE_RIGHT; // 4 - 右
            case "zoom_in":
                return NvssdkLibrary.ZOOM_BIG; // 31 - 放大
            case "zoom_out":
                return NvssdkLibrary.ZOOM_SMALL; // 33 - 缩小
            default:
                return 0;
        }
    }

    @Override
    public List<DeviceSDK.PlaybackFile> queryPlaybackFiles(int userId, int channel, Date startTime, Date endTime) {
        List<DeviceSDK.PlaybackFile> files = new ArrayList<>();

        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return files;
        }

        try {
            // 参考SyncBusiness.java:494-588的实现
            TiandySDKStructure.NETFILE_QUERY_V5 tQueryFileV5 = new TiandySDKStructure.NETFILE_QUERY_V5();
            tQueryFileV5.iBufSize = tQueryFileV5.size();
            tQueryFileV5.iQueryChannelNo = channel; // 查询指定通道
            tQueryFileV5.iStreamNo = 0; // 主码流
            tQueryFileV5.iType = 0xFF; // 所有类型
            tQueryFileV5.iFiletype = 1; // 视频文件
            tQueryFileV5.iTriggerType = 0x7FFFFFFF; // 所有触发类型
            tQueryFileV5.iTrigger = 0;
            tQueryFileV5.iPageSize = 20; // 每页20条
            tQueryFileV5.iPageNo = 0; // 第一页
            tQueryFileV5.iQueryChannelCount = 0; // 单通道查询
            tQueryFileV5.iBufferSize = 0;
            tQueryFileV5.ptChannelList = null;

            // 设置时间范围
            Calendar cal = Calendar.getInstance();
            cal.setTime(startTime);
            tQueryFileV5.tStartTime.iYear = (short) cal.get(Calendar.YEAR);
            tQueryFileV5.tStartTime.iMonth = (short) (cal.get(Calendar.MONTH) + 1);
            tQueryFileV5.tStartTime.iDay = (short) cal.get(Calendar.DAY_OF_MONTH);
            tQueryFileV5.tStartTime.iHour = (short) cal.get(Calendar.HOUR_OF_DAY);
            tQueryFileV5.tStartTime.iMinute = (short) cal.get(Calendar.MINUTE);
            tQueryFileV5.tStartTime.iSecond = (short) cal.get(Calendar.SECOND);

            cal.setTime(endTime);
            tQueryFileV5.tStopTime.iYear = (short) cal.get(Calendar.YEAR);
            tQueryFileV5.tStopTime.iMonth = (short) (cal.get(Calendar.MONTH) + 1);
            tQueryFileV5.tStopTime.iDay = (short) cal.get(Calendar.DAY_OF_MONTH);
            tQueryFileV5.tStopTime.iHour = (short) cal.get(Calendar.HOUR_OF_DAY);
            tQueryFileV5.tStopTime.iMinute = (short) cal.get(Calendar.MINUTE);
            tQueryFileV5.tStopTime.iSecond = (short) cal.get(Calendar.SECOND);

            tQueryFileV5.write();

            // 准备结果缓冲区
            TiandySDKStructure.NVS_FILE_DATA tSingleData = new TiandySDKStructure.NVS_FILE_DATA();
            TiandySDKStructure.QueryFileResult tResult = new TiandySDKStructure.QueryFileResult();

            int iOutTotalLen = 20 * tSingleData.size();
            int iSingleLen = tSingleData.size();

            int iRet = nvssdkLibrary.NetClient_SyncQuery(userId, 0,
                    NvssdkLibrary.CMD_NETFILE_QUERY_FILE,
                    tQueryFileV5.getPointer(), tQueryFileV5.size(),
                    tResult.getPointer(), iOutTotalLen, iSingleLen);

            if (iRet < 0) {
                logger.error("天地伟业回放查询失败: userId={}, channel={}, 错误码={}", userId, channel, iRet);
                return files;
            }

            tQueryFileV5.read();
            tResult.read();

            int curCount = tQueryFileV5.iCurQueryCount;
            logger.info("天地伟业回放查询成功: userId={}, channel={}, 总数={}, 当前页={}",
                    userId, channel, tQueryFileV5.iTotalQueryCount, curCount);

            // 解析查询结果
            for (int i = 0; i < curCount && i < tResult.tArry.length; i++) {
                TiandySDKStructure.NVS_FILE_DATA fileData = tResult.tArry[i];
                if (fileData == null) {
                    continue;
                }

                // 读取结构体数据
                fileData.read();

                String fileName = new String(fileData.cFileName).trim();
                if (fileName.isEmpty()) {
                    continue;
                }

                // 转换时间
                Calendar startCal = Calendar.getInstance();
                startCal.set(fileData.tStartTime.iYear, fileData.tStartTime.iMonth - 1,
                        fileData.tStartTime.iDay, fileData.tStartTime.iHour,
                        fileData.tStartTime.iMinute, fileData.tStartTime.iSecond);
                Date fileStartTime = startCal.getTime();

                Calendar endCal = Calendar.getInstance();
                endCal.set(fileData.tStopTime.iYear, fileData.tStopTime.iMonth - 1,
                        fileData.tStopTime.iDay, fileData.tStopTime.iHour,
                        fileData.tStopTime.iMinute, fileData.tStopTime.iSecond);
                Date fileEndTime = endCal.getTime();

                DeviceSDK.PlaybackFile playbackFile = new DeviceSDK.PlaybackFile(
                        fileName, fileStartTime, fileEndTime, fileData.iFileSize, fileData.iChannel);
                files.add(playbackFile);
            }

            return files;
        } catch (Exception e) {
            logger.error("天地伟业回放查询异常: userId={}, channel={}", userId, channel, e);
            return files;
        }
    }

    @Override
    public int downloadPlaybackByTimeRange(int userId, int channel, Date startTime, Date endTime,
            String localFilePath, int streamType) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return -1;
        }

        if (userId < 0) {
            logger.error("无效的登录句柄: {}", userId);
            return -1;
        }

        try {
            // 参考SyncBusiness.java:754-803的实现
            // 天地伟业SDK的通道号从0开始，需要转换为0-based
            int channelNo = channel > 0 ? channel - 1 : 0;

            TiandySDKStructure.DOWNLOAD_TIMESPAN tDownloadTimeSpan = new TiandySDKStructure.DOWNLOAD_TIMESPAN();
            tDownloadTimeSpan.m_iSize = tDownloadTimeSpan.size();
            tDownloadTimeSpan.m_iSaveFileType = NvssdkLibrary.DOWNLOAD_FILE_TYPE_ZFMP4; // MP4格式（默认）
            tDownloadTimeSpan.m_iFileFlag = 0; // 0-下载多个文件，1-下载为单个文件
            tDownloadTimeSpan.m_iChannelNO = channelNo; // 通道号（0-based）
            tDownloadTimeSpan.m_iStreamNo = streamType; // 码流号：0-主码流，1-子码流
            tDownloadTimeSpan.m_iPosition = -1; // 不使用定位功能
            tDownloadTimeSpan.m_iSpeed = 16; // 下载速度：16倍速（官方示例建议）
            tDownloadTimeSpan.m_iIFrame = 0; // 0-全帧，1-只I帧
            tDownloadTimeSpan.m_iReqMode = 1; // 1-帧模式，0-流模式
            tDownloadTimeSpan.m_iVodTransEnable = 0; // 不启用VOD转换
            tDownloadTimeSpan.m_iFileAttr = 0; // 0-NVR本地存储

            // 设置本地保存文件名
            byte[] filenameBytes = localFilePath.getBytes();
            int copyLen = Math.min(filenameBytes.length, tDownloadTimeSpan.m_cLocalFilename.length - 1);
            System.arraycopy(filenameBytes, 0, tDownloadTimeSpan.m_cLocalFilename, 0, copyLen);
            tDownloadTimeSpan.m_cLocalFilename[copyLen] = 0; // 字符串结束符

            // 设置开始时间
            Calendar cal = Calendar.getInstance();
            cal.setTime(startTime);
            tDownloadTimeSpan.m_tTimeBegin.iYear = (short) cal.get(Calendar.YEAR);
            tDownloadTimeSpan.m_tTimeBegin.iMonth = (short) (cal.get(Calendar.MONTH) + 1);
            tDownloadTimeSpan.m_tTimeBegin.iDay = (short) cal.get(Calendar.DAY_OF_MONTH);
            tDownloadTimeSpan.m_tTimeBegin.iHour = (short) cal.get(Calendar.HOUR_OF_DAY);
            tDownloadTimeSpan.m_tTimeBegin.iMinute = (short) cal.get(Calendar.MINUTE);
            tDownloadTimeSpan.m_tTimeBegin.iSecond = (short) cal.get(Calendar.SECOND);

            // 设置结束时间
            cal.setTime(endTime);
            tDownloadTimeSpan.m_tTimeEnd.iYear = (short) cal.get(Calendar.YEAR);
            tDownloadTimeSpan.m_tTimeEnd.iMonth = (short) (cal.get(Calendar.MONTH) + 1);
            tDownloadTimeSpan.m_tTimeEnd.iDay = (short) cal.get(Calendar.DAY_OF_MONTH);
            tDownloadTimeSpan.m_tTimeEnd.iHour = (short) cal.get(Calendar.HOUR_OF_DAY);
            tDownloadTimeSpan.m_tTimeEnd.iMinute = (short) cal.get(Calendar.MINUTE);
            tDownloadTimeSpan.m_tTimeEnd.iSecond = (short) cal.get(Calendar.SECOND);

            tDownloadTimeSpan.write();

            IntByReference iConnID = new IntByReference();
            int iRet = nvssdkLibrary.NetClient_NetFileDownload(iConnID, userId,
                    NvssdkLibrary.DOWNLOAD_CMD_TIMESPAN, tDownloadTimeSpan.getPointer(), tDownloadTimeSpan.size());

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                int downloadId = iConnID.getValue();
                logger.info("天地伟业按时间范围下载启动成功: userId={}, channel={}(0-based: {}), " +
                        "startTime={}, endTime={}, localFile={}, downloadId={}",
                        userId, channel, channelNo, startTime, endTime, localFilePath, downloadId);
                return downloadId;
            } else {
                logger.error("天地伟业按时间范围下载启动失败: userId={}, channel={}(0-based: {}), 错误码={}",
                        userId, channel, channelNo, iRet);
                return -1;
            }
        } catch (Exception e) {
            logger.error("天地伟业按时间范围下载异常: userId={}, channel={}", userId, channel, e);
            return -1;
        }
    }

    /**
     * 调整下载速度
     * 参考官方示例Playback.java:495-502的DOWNLOAD_CMD_CONTROL调速
     * 
     * @param downloadId 下载连接ID
     * @param speed      下载速度：1,2,4,8,16,32，0表示暂停
     * @return 成功返回true
     */
    public boolean adjustDownloadSpeed(int downloadId, int speed) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return false;
        }

        if (downloadId < 0) {
            logger.error("无效的下载ID: {}", downloadId);
            return false;
        }

        try {
            TiandySDKStructure.DOWNLOAD_CONTROL tControl = new TiandySDKStructure.DOWNLOAD_CONTROL();
            tControl.m_iSize = tControl.size();
            tControl.m_iPosition = -1; // 不使用定位
            tControl.m_iSpeed = speed; // 下载速度
            tControl.m_iIFrame = 0; // 全帧
            tControl.m_iReqMode = 1; // 帧模式
            tControl.write();

            IntByReference iConnID = new IntByReference(downloadId);
            // 注意：DOWNLOAD_CMD_CONTROL 使用现有的 downloadId，不需要 userId 参数
            // 但官方API需要传入userId，这里传入0（SDK内部会使用已有连接）
            int iRet = nvssdkLibrary.NetClient_NetFileDownload(iConnID, 0,
                    NvssdkLibrary.DOWNLOAD_CMD_CONTROL, tControl.getPointer(), tControl.size());

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                logger.info("天地伟业下载调速成功: downloadId={}, speed={}x", downloadId, speed);
                return true;
            } else {
                logger.error("天地伟业下载调速失败: downloadId={}, speed={}, 错误码={}", downloadId, speed, iRet);
                return false;
            }
        } catch (Exception e) {
            logger.error("天地伟业下载调速异常: downloadId={}", downloadId, e);
            return false;
        }
    }

    /**
     * 下载报警事件录像（便捷方法）
     * 用于报警事件触发时下载事件前后指定秒数的录像
     * 
     * @param userId        登录句柄
     * @param channel       通道号（1-based）
     * @param eventTime     事件发生时间
     * @param beforeSeconds 事件前的秒数（如15秒）
     * @param afterSeconds  事件后的秒数（如15秒）
     * @param localFilePath 本地保存路径
     * @param fileType      文件类型：0-SDV, 3-PS, 4-MP4
     * @return 下载ID，失败返回-1
     */
    public int downloadAlarmClip(int userId, int channel, Date eventTime,
            int beforeSeconds, int afterSeconds, String localFilePath, int fileType) {
        if (eventTime == null) {
            logger.error("事件时间不能为空");
            return -1;
        }

        // 计算开始时间和结束时间
        Calendar cal = Calendar.getInstance();
        cal.setTime(eventTime);

        // 开始时间 = 事件时间 - beforeSeconds
        cal.add(Calendar.SECOND, -beforeSeconds);
        Date startTime = cal.getTime();

        // 结束时间 = 事件时间 + afterSeconds
        cal.setTime(eventTime);
        cal.add(Calendar.SECOND, afterSeconds);
        Date endTime = cal.getTime();

        logger.info("下载报警录像: eventTime={}, 范围=[{} ~ {}], channel={}, filePath={}",
                eventTime, startTime, endTime, channel, localFilePath);

        // 调用按时间范围下载（使用指定的文件类型）
        return downloadPlaybackByTimeRangeWithType(userId, channel, startTime, endTime, localFilePath, 0, fileType);
    }

    /**
     * 按时间范围下载录像（支持指定文件类型）
     * 
     * @param userId        登录句柄
     * @param channel       通道号（1-based）
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @param localFilePath 本地保存路径
     * @param streamType    码流类型：0-主码流，1-子码流
     * @param fileType      文件类型：0-SDV, 3-PS, 4-MP4
     * @return 下载ID，失败返回-1
     */
    public int downloadPlaybackByTimeRangeWithType(int userId, int channel, Date startTime, Date endTime,
            String localFilePath, int streamType, int fileType) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return -1;
        }

        if (userId < 0) {
            logger.error("无效的登录句柄: {}", userId);
            return -1;
        }

        try {
            int channelNo = channel > 0 ? channel - 1 : 0;

            TiandySDKStructure.DOWNLOAD_TIMESPAN tDownloadTimeSpan = new TiandySDKStructure.DOWNLOAD_TIMESPAN();
            tDownloadTimeSpan.m_iSize = tDownloadTimeSpan.size();
            tDownloadTimeSpan.m_iSaveFileType = fileType; // 使用指定的文件类型
            tDownloadTimeSpan.m_iFileFlag = 1; // 1-下载为单个文件（便于报警录像）
            tDownloadTimeSpan.m_iChannelNO = channelNo;
            tDownloadTimeSpan.m_iStreamNo = streamType;
            tDownloadTimeSpan.m_iPosition = -1;
            tDownloadTimeSpan.m_iSpeed = 32; // 先用32倍速启动
            tDownloadTimeSpan.m_iIFrame = 0;
            tDownloadTimeSpan.m_iReqMode = 1;
            tDownloadTimeSpan.m_iVodTransEnable = 0;
            tDownloadTimeSpan.m_iFileAttr = 0;

            // 设置本地保存文件名
            byte[] filenameBytes = localFilePath.getBytes();
            int copyLen = Math.min(filenameBytes.length, tDownloadTimeSpan.m_cLocalFilename.length - 1);
            System.arraycopy(filenameBytes, 0, tDownloadTimeSpan.m_cLocalFilename, 0, copyLen);
            tDownloadTimeSpan.m_cLocalFilename[copyLen] = 0;

            // 设置时间范围
            Calendar cal = Calendar.getInstance();
            cal.setTime(startTime);
            tDownloadTimeSpan.m_tTimeBegin.iYear = (short) cal.get(Calendar.YEAR);
            tDownloadTimeSpan.m_tTimeBegin.iMonth = (short) (cal.get(Calendar.MONTH) + 1);
            tDownloadTimeSpan.m_tTimeBegin.iDay = (short) cal.get(Calendar.DAY_OF_MONTH);
            tDownloadTimeSpan.m_tTimeBegin.iHour = (short) cal.get(Calendar.HOUR_OF_DAY);
            tDownloadTimeSpan.m_tTimeBegin.iMinute = (short) cal.get(Calendar.MINUTE);
            tDownloadTimeSpan.m_tTimeBegin.iSecond = (short) cal.get(Calendar.SECOND);

            cal.setTime(endTime);
            tDownloadTimeSpan.m_tTimeEnd.iYear = (short) cal.get(Calendar.YEAR);
            tDownloadTimeSpan.m_tTimeEnd.iMonth = (short) (cal.get(Calendar.MONTH) + 1);
            tDownloadTimeSpan.m_tTimeEnd.iDay = (short) cal.get(Calendar.DAY_OF_MONTH);
            tDownloadTimeSpan.m_tTimeEnd.iHour = (short) cal.get(Calendar.HOUR_OF_DAY);
            tDownloadTimeSpan.m_tTimeEnd.iMinute = (short) cal.get(Calendar.MINUTE);
            tDownloadTimeSpan.m_tTimeEnd.iSecond = (short) cal.get(Calendar.SECOND);

            tDownloadTimeSpan.write();

            IntByReference iConnID = new IntByReference();
            int iRet = nvssdkLibrary.NetClient_NetFileDownload(iConnID, userId,
                    NvssdkLibrary.DOWNLOAD_CMD_TIMESPAN, tDownloadTimeSpan.getPointer(), tDownloadTimeSpan.size());

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                int downloadId = iConnID.getValue();

                // 参考官方示例：下载启动成功后调整为16倍速
                adjustDownloadSpeed(downloadId, 16);

                logger.info("天地伟业录像下载启动成功: userId={}, channel={}, startTime={}, endTime={}, " +
                        "fileType={}, downloadId={}", userId, channel, startTime, endTime, fileType, downloadId);
                return downloadId;
            } else {
                logger.error("天地伟业录像下载启动失败: userId={}, channel={}, 错误码={}", userId, channel, iRet);
                return -1;
            }
        } catch (Exception e) {
            logger.error("天地伟业录像下载异常: userId={}, channel={}", userId, channel, e);
            return -1;
        }
    }

    @Override
    public boolean stopDownload(int downloadId) {
        if (!initialized || nvssdkLibrary == null) {
            return false;
        }

        if (downloadId < 0) {
            return false;
        }

        try {
            int iRet = nvssdkLibrary.NetClient_NetFileStopDownloadFile(downloadId);
            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                logger.info("天地伟业下载停止成功: downloadId={}", downloadId);
                return true;
            } else {
                logger.error("天地伟业下载停止失败: downloadId={}, 错误码={}", downloadId, iRet);
                return false;
            }
        } catch (Exception e) {
            logger.error("天地伟业下载停止异常: downloadId={}", downloadId, e);
            return false;
        }
    }

    @Override
    public int getDownloadProgress(int downloadId) {
        if (!initialized || nvssdkLibrary == null) {
            return -1;
        }

        if (downloadId < 0) {
            return -1;
        }

        try {
            IntByReference piPos = new IntByReference();
            IntByReference piDLSize = new IntByReference();
            int iRet = nvssdkLibrary.NetClient_NetFileGetDownloadPos(downloadId, piPos, piDLSize);
            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                int downloadedBytes = piPos.getValue(); // 已下载字节数
                int totalBytes = piDLSize.getValue(); // 总字节数

                // 计算百分比（0-100）
                int progress;
                if (totalBytes > 0) {
                    // 使用long避免溢出
                    long progressLong = (long) downloadedBytes * 100 / totalBytes;
                    progress = (int) Math.min(progressLong, 100); // 限制在0-100之间
                } else {
                    // 如果总大小为0，返回0或根据已下载字节数判断
                    progress = downloadedBytes > 0 ? 1 : 0;
                }

                logger.debug("天地伟业下载进度: downloadId={}, progress={}%, downloaded={} bytes, total={} bytes",
                        downloadId, progress, downloadedBytes, totalBytes);
                return progress;
            } else {
                logger.error("天地伟业获取下载进度失败: downloadId={}, 错误码={}", downloadId, iRet);
                return -1;
            }
        } catch (Exception e) {
            logger.error("天地伟业获取下载进度异常: downloadId={}", downloadId, e);
            return -1;
        }
    }

    @Override
    public boolean rebootDevice(int userId) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return false;
        }

        if (userId < 0) {
            logger.error("无效的登录句柄: {}", userId);
            return false;
        }

        try {
            // 参考NetSdk.h:223的实现
            // 使用NetClient_Reboot重启设备
            int iRet = nvssdkLibrary.NetClient_Reboot(userId);

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                logger.info("天地伟业设备重启命令已发送: userId={}", userId);
                return true;
            } else {
                logger.error("天地伟业设备重启失败: userId={}, 错误码={}", userId, iRet);
                return false;
            }
        } catch (Exception e) {
            logger.error("天地伟业设备重启异常: userId={}", userId, e);
            return false;
        }
    }
}
