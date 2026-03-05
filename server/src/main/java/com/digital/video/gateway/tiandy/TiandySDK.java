package com.digital.video.gateway.tiandy;

import com.digital.video.gateway.Common.ArchitectureChecker;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.device.DeviceSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import com.sun.jna.CallbackReference;

import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 天地伟业SDK封装类
 */
public class TiandySDK implements DeviceSDK {
    private static final Logger logger = LoggerFactory.getLogger(TiandySDK.class);
    private static TiandySDK instance;
    private NvssdkLibrary nvssdkLibrary;
    private boolean initialized = false;
    private Config.SdkConfig sdkConfig;

    // 存储登录 ID 到 IP 的映射，用于 startRealPlay
    private final Map<Integer, String> loginIdToIpMap = new ConcurrentHashMap<>();

    /** 预览启动锁（按登录句柄隔离），避免全局串行导致 80 台设备启动相互阻塞 */
    private final Map<Integer, java.util.concurrent.locks.ReentrantLock> startRealPlayLocks = new ConcurrentHashMap<>();

    /** 统一受控线程池，避免每次调用 startRealPlay 都创建新线程 */
    private final ExecutorService syncRealPlayExecutor = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "Tiandy-SyncRealPlay");
        t.setDaemon(true);
        return t;
    });

    /** 下载连接ID到登录句柄映射，用于 DOWNLOAD_CMD_CONTROL 严格按官方示例传 m_iLogonID */
    private final Map<Integer, Integer> downloadIdToUserIdMap = new ConcurrentHashMap<>();

    /**
     * PTZ 位置内存缓存：key = "userId_channel"，通过 PARA_GET_PTZ 回调或 gotoAngle 成功后更新。
     * 由于 SYNC_GETPTZ 在天地伟业设备上通常失败，依赖此缓存提供最新的已知位置。
     */
    private final Map<String, PtzPosition> ptzPositionCache = new ConcurrentHashMap<>();

    private String ptzCacheKey(int userId, int channel) {
        return userId + "_" + channel;
    }

    // 回调对象（延迟初始化，在库加载后创建，确保JNA能正确映射）
    private NvssdkLibrary.MAIN_NOTIFY_V4 emptyMainNotify;
    private NvssdkLibrary.ALARM_NOTIFY_V4 emptyAlarmNotify;
    private NvssdkLibrary.ALARM_NOTIFY_V4 alarmNotifyCallback;
    private com.digital.video.gateway.service.AlarmService alarmService;
    private com.digital.video.gateway.device.DeviceManager deviceManager;
    private NvssdkLibrary.PARACHANGE_NOTIFY_V4 emptyParaNotify;
    private NvssdkLibrary.COMRECV_NOTIFY_V4 emptyComNotify;
    private NvssdkLibrary.PROXY_NOTIFY emptyProxyNotify;

    /** 预览用回调（与官方 Channel.java 一致，不可为 null，否则 SDK 可能不建立流） */
    private NvssdkLibrary.FULLFRAME_NOTIFY_V4 realPlayCbkFullFrame;
    private NvssdkLibrary.RAWFRAME_NOTIFY realPlayCbkRawFrame;

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

        // PARA_GET_PTZ = 473：当球机 PTZ 位置发生变化时，SDK 通过此回调上报最新角度
        // m_iPara[0]=pan*100, m_iPara[1]=tilt*100, m_iPara[2]=zoom*100
        emptyParaNotify = new NvssdkLibrary.PARACHANGE_NOTIFY_V4() {
            private static final int PARA_GET_PTZ = 473;
            @Override
            public void apply(int ulLogonID, int iChan, int iParaType, TiandySDKStructure.STR_Para strPara,
                    com.sun.jna.Pointer iUser) {
                try {
                    if (iParaType == PARA_GET_PTZ && strPara != null) {
                        float pan = strPara.m_iPara[0] / 100.0f;
                        float tilt = strPara.m_iPara[1] / 100.0f;
                        float zoom = strPara.m_iPara[2] / 100.0f;
                        int channel = iChan + 1; // SDK 回调 iChan 从 0 开始
                        ptzPositionCache.put(ptzCacheKey(ulLogonID, channel), new PtzPosition(pan, tilt, zoom));
                        logger.debug("PARA_GET_PTZ 回调更新缓存: userId={}, channel={}, pan={}°, tilt={}°, zoom={}x",
                                ulLogonID, channel, pan, tilt, zoom);
                    }
                } catch (Exception e) {
                    logger.warn("PARA_GET_PTZ 回调处理异常", e);
                }
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

        // 预览回调（与官方 Channel.java:106-218 一致，必须非 null 否则 SDK 可能不建立流/权限异常）
        realPlayCbkFullFrame = new NvssdkLibrary.FULLFRAME_NOTIFY_V4() {
            @Override
            public void apply(int iConnectID, int iStreamType, Pointer pcData, int iLen, Pointer pvHeader,
                    Pointer pvUserData) {
                // 空实现,仅用于满足SDK要求
            }
        };
        realPlayCbkRawFrame = new NvssdkLibrary.RAWFRAME_NOTIFY() {
            @Override
            public void apply(int uiID, Pointer pcData, int iLen, TiandySDKStructure.RAWFRAME_INFO ptRawFrameInfo,
                    Pointer lpUserData) {
                // 空实现，仅满足 SDK 要求有有效回调指针
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

                            // 如果是智能分析报警（iAlarmType=6或9），尝试获取具体的事件类型
                            if (iAlarmType == 6 || iAlarmType == 9) {
                                try {
                                    if (TiandySDK.this.nvssdkLibrary != null) {
                                        // 使用通道号作为报警索引（根据SDK文档，iAlarmIndex应该是通道号）
                                        // 注意：示例代码中使用iAlarmState作为索引，但根据SDK文档，应该使用通道号
                                        TiandySDKStructure.VcaTAlarmInfo vcaAlarmInfo = new TiandySDKStructure.VcaTAlarmInfo();
                                        int result = TiandySDK.this.nvssdkLibrary.NetClient_VCAGetAlarmInfo(ulLogonID,
                                                iChan, vcaAlarmInfo.getPointer(), vcaAlarmInfo.size());
                                        if (result == 0) {
                                            vcaAlarmInfo.read();
                                            int eventType = vcaAlarmInfo.iEventType;
                                            logger.info(
                                                    "获取智能分析报警详细信息: logonID={}, channel={}, alarmType={}, eventType={}, ruleID={}, targetID={}",
                                                    ulLogonID, iChan, iAlarmType, eventType, vcaAlarmInfo.iRuleID,
                                                    vcaAlarmInfo.uiTargetID);

                                            // 根据eventType构建更精确的alarmType（如Tiandy_Alarm_102表示周界入侵）
                                            // 注意：eventType是智能分析事件代码（如0-单绊线越界，2-周界入侵），不是iAlarmType
                                            // 但为了保持一致性，我们使用eventType来构建alarmType
                                            // 注意：eventType的值范围是0-15（基础事件）或100+（扩展事件），需要根据实际情况映射
                                            if (eventType >= 0) {
                                                // 如果eventType是基础事件（0-15），直接使用；如果是扩展事件（100+），也直接使用
                                                alarmTypeStr = "Tiandy_Alarm_" + eventType;
                                                alarmMessage = String.format(
                                                        "天地伟业智能分析报警: device=%s, channel=%d, state=%d, alarmType=%d, eventType=%d, ruleID=%d, targetID=%d",
                                                        deviceId, iChan, iAlarmState, iAlarmType, eventType,
                                                        vcaAlarmInfo.iRuleID, vcaAlarmInfo.uiTargetID);
                                            }
                                        } else {
                                            logger.warn(
                                                    "获取智能分析报警信息失败: logonID={}, channel={}, alarmType={}, result={}, 尝试使用iAlarmState作为索引",
                                                    ulLogonID, iChan, iAlarmType, result);
                                            // 如果使用通道号失败，尝试使用iAlarmState作为索引（如示例代码所示）
                                            vcaAlarmInfo = new TiandySDKStructure.VcaTAlarmInfo();
                                            result = TiandySDK.this.nvssdkLibrary.NetClient_VCAGetAlarmInfo(ulLogonID,
                                                    iAlarmState, vcaAlarmInfo.getPointer(), vcaAlarmInfo.size());
                                            if (result == 0) {
                                                vcaAlarmInfo.read();
                                                int eventType = vcaAlarmInfo.iEventType;
                                                logger.info(
                                                        "使用iAlarmState作为索引成功获取智能分析报警信息: logonID={}, channel={}, alarmType={}, eventType={}, ruleID={}",
                                                        ulLogonID, iChan, iAlarmType, eventType, vcaAlarmInfo.iRuleID);
                                                if (eventType >= 0) {
                                                    alarmTypeStr = "Tiandy_Alarm_" + eventType;
                                                    alarmMessage = String.format(
                                                            "天地伟业智能分析报警: device=%s, channel=%d, state=%d, alarmType=%d, eventType=%d, ruleID=%d",
                                                            deviceId, iChan, iAlarmState, iAlarmType, eventType,
                                                            vcaAlarmInfo.iRuleID);
                                                }
                                            } else {
                                                logger.warn(
                                                        "使用iAlarmState作为索引也失败: logonID={}, channel={}, alarmType={}, result={}",
                                                        ulLogonID, iChan, iAlarmType, result);
                                            }
                                        }
                                    } else {
                                        logger.warn(
                                                "nvssdkLibrary未初始化，无法获取智能分析报警详细信息: logonID={}, channel={}, alarmType={}",
                                                ulLogonID, iChan, iAlarmType);
                                    }
                                } catch (Exception e) {
                                    logger.error("调用NetClient_VCAGetAlarmInfo异常: logonID={}, channel={}, alarmType={}",
                                            ulLogonID, iChan, iAlarmType, e);
                                }
                            }

                            // 使用EventResolver解析标准事件（如果可用）
                            if (TiandySDK.this.alarmService != null) {
                                // 直接调用handleAlarm，AlarmService内部会使用EventResolver解析
                                // 但为了更好的性能，我们也可以在这里直接解析并传递eventKey
                                TiandySDK.this.alarmService.handleAlarm(deviceId, iChan, alarmTypeStr, alarmMessage);
                            }
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

            // 设置 SDK 日志级别，抑制终端噪音（libvautil、CLS_CmdAnalyzeData 等 ERR 刷屏）
            try {
                TiandySDKStructure.SdkLogLevel logLevel = new TiandySDKStructure.SdkLogLevel();
                logLevel.iTerminalOutputLevel = 0; // SDK_LOG_LEVEL_FORBID，不输出到终端
                logLevel.iIsWriteFile = 0; // 不写日志文件
                logLevel.iLogFileWriteLevel = 100; // 若写文件则仅 ERROR
                logLevel.write();
                int setLogRet = nvssdkLibrary.NetClient_SetSDKInitConfig(
                        NvssdkLibrary.INIT_CONFIG_SET_LOG_LEVEL, logLevel.getPointer(), logLevel.size());
                if (setLogRet != NvssdkLibrary.RET_SUCCESS) {
                    logger.debug("天地伟业SDK设置日志级别失败，返回值: {}（继续初始化）", setLogRet);
                }
            } catch (Exception e) {
                logger.debug("设置天地伟业SDK日志级别异常: {}（继续初始化）", e.getMessage());
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
            String soParent = libDirFile.getAbsolutePath();

            // 与 TiandyCaptureDemo 完全一致：用绝对路径加载 so，并把 tiandy 目录及 tiandy/lib 放入库路径
            // 这样 libnvssdk.so 的依赖（如 ffmpeg）可由系统在 tiandy/lib 中找到，避免 Inner_StartRecvEx 失败
            File tiandyLibSub = new File(libDirFile, "lib");
            String pathForLoad = soParent;
            if (tiandyLibSub.exists() && tiandyLibSub.isDirectory()) {
                pathForLoad = soParent + File.pathSeparator + tiandyLibSub.getAbsolutePath();
            }
            String existingPath = System.getProperty("java.library.path", "");
            System.setProperty("java.library.path",
                    pathForLoad + (existingPath.isEmpty() ? "" : File.pathSeparator + existingPath));
            logger.debug("天地伟业库路径(与Demo一致): {}", pathForLoad);

            try {
                nvssdkLibrary = (NvssdkLibrary) Native.load(actualLibPath, NvssdkLibrary.class);
                logger.info("天地伟业SDK库加载成功(绝对路径，与Demo一致): {}", actualLibPath);
                return true;
            } catch (UnsatisfiedLinkError e) {
                logger.error(
                        "加载天地伟业SDK库失败，库路径: {}，错误: {}（若为找不到 libavcodec 等，请确保启动时 LD_LIBRARY_PATH 含 tiandy 与 tiandy/lib）",
                        actualLibPath, e.getMessage());
                return false;
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

            // 使用 arraycopy 填充固定大小 byte[]，避免替换 JNA Structure 字段引用导致内存布局异常
            fillBytes(logonPara.cNvsIP, ip);
            logonPara.iNvsPort = port;
            fillBytes(logonPara.cUserName, username);
            fillBytes(logonPara.cUserPwd, password);
            fillBytes(logonPara.cCharSet, strCharSet);

            // 其他字段保持默认值（null或0）
            // cProxy, cNvsName, cProductID, cAccontName, cAccontPasswd, cNvsIPV6 使用默认值

            logonPara.write();

            logger.info("开始登录天地伟业设备: {}:{}, 用户: {}, tagLogonPara.size={}",
                    ip, port, username, logonPara.iSize);
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
                loginIdToIpMap.put(logonID, ip); // Store IP for later use
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
            loginIdToIpMap.remove(userId); // Remove IP on logout
            startRealPlayLocks.remove(userId);
            downloadIdToUserIdMap.entrySet().removeIf(e -> e.getValue() != null && e.getValue() == userId);
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

    /** 抓图接口返回值描述（与 RetValue.h 一致，便于日志排查） */
    private static void fillBytes(byte[] dest, String value) {
        java.util.Arrays.fill(dest, (byte) 0);
        if (value != null) {
            byte[] src = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            System.arraycopy(src, 0, dest, 0, Math.min(src.length, dest.length));
        }
    }

    private static String getCaptureErrorDesc(int iRet) {
        switch (iRet) {
            case NvssdkLibrary.RET_FAILED:
                return " (失败-通用)";
            case NvssdkLibrary.RET_DEVICE_CAPTURE_FAIL:
                return " (设备远程抓拍失败，设备返回数据长度为0)";
            case NvssdkLibrary.RET_DEVICE_CAPTURE_TIMEOUT:
                return " (设备远程抓拍超时，设备未回复数据)";
            default:
                return "";
        }
    }

    /** 按 SDK C 字符串规则写入本地路径（UTF-8 + '\0'） */
    private static byte[] toCStringBytes(String value) {
        String safeValue = value == null ? "" : value.replace("\0", "");
        return (safeValue + "\0").getBytes(StandardCharsets.UTF_8);
    }

    /** 是否为可重试错误码（网络抖动、设备短暂忙、同步预览超时等） */
    private static boolean isRetriableError(int iRet) {
        return iRet == NvssdkLibrary.RET_FAILED
                || iRet == NvssdkLibrary.RET_SYNCLOGON_TIMEOUT
                || iRet == NvssdkLibrary.RET_SYNCLOGON_NET_ERROR
                || iRet == NvssdkLibrary.RET_SYNCREALPLAY_TIMEOUT
                || iRet == NvssdkLibrary.RET_SYNCREALPLAY_FAIL;
    }

    private static boolean isRetriableCaptureError(int iRet) {
        return iRet == NvssdkLibrary.RET_FAILED
                || iRet == NvssdkLibrary.RET_DEVICE_CAPTURE_FAIL
                || iRet == NvssdkLibrary.RET_DEVICE_CAPTURE_TIMEOUT;
    }

    @Override
    public void cleanup() {
        if (initialized && nvssdkLibrary != null) {
            nvssdkLibrary.NetClient_Cleanup();
            initialized = false;
            logger.info("天地伟业SDK清理完成");
        }
        downloadIdToUserIdMap.clear();
        startRealPlayLocks.clear();
        syncRealPlayExecutor.shutdownNow();
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
            logger.debug("TiandySDK.startRealPlay check initialized={}", initialized);
            logger.error("天地伟业SDK未初始化");
            return -1;
        }

        java.util.concurrent.locks.ReentrantLock realPlayLock = startRealPlayLocks.computeIfAbsent(userId,
                k -> new java.util.concurrent.locks.ReentrantLock());
        boolean locked = false;
        try {
            logger.debug("TiandySDK start locking");
            logger.info("天地伟业预览启动: 准备获取锁 userId={}, channel={}", userId, channel);
            // 使用 tryLock 避免死锁，等待时间需大于 SyncRealPlay 的超时时间(60s)
            locked = realPlayLock.tryLock(65, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.debug("TiandySDK lock interrupted");
            e.printStackTrace();
            logger.error("天地伟业预览启动: 获取锁被中断 userId={}, channel={}", userId, channel);
            Thread.currentThread().interrupt();
            return -1;
        }

        if (!locked) {
            logger.debug("TiandySDK lock result=false");
            logger.error("天地伟业预览启动: 获取锁超时(65s), 可能存在死锁 userId={}, channel={}", userId, channel);
            return -1;
        }
        logger.debug("TiandySDK lock result=true");
        logger.info("天地伟业预览启动: 获取锁成功, 开始执行 userId={}, channel={}", userId, channel);

        try {
            // 参考Channel.java:245-318的实现（官方示例在 SyncRealPlay 前不调用 GetLogonStatus，此处仅做快速校验）
            logger.debug("Calling GetLogonStatus userId={}", userId);
            int logonStatus = nvssdkLibrary.NetClient_GetLogonStatus(userId);
            logger.debug("LogonStatus={}", logonStatus);
            if (logonStatus != NvssdkLibrary.LOGON_SUCCESS) { // 0表示登录成功
                logger.debug("LogonStatus invalid");
                logger.error("设备登录状态无效: userId={}, logonStatus={}（0=成功, 4=失败, 5=超时），无法启动预览",
                        userId, logonStatus);
                return -1;
            }
            logger.info("天地伟业预览启动: 登录状态检查完成 logonStatus={}", logonStatus);

            // 天地伟业SDK的通道号从0开始，如果传入的是1-based的通道号，需要转换为0-based
            int channelNo = channel;
            if (channel > 0) {
                channelNo = channel - 1; // 转换为0-based索引
            }

            // 参考Channel.java:253-277，必须验证通道号是否有效
            IntByReference piDigitalChanCount = new IntByReference();
            logger.debug("Calling GetDigitalChannelNum");
            int ret = nvssdkLibrary.NetClient_GetDigitalChannelNum(userId, piDigitalChanCount);
            logger.debug("GetDigitalChannelNum ret={}", ret);
            if (ret != NvssdkLibrary.RET_SUCCESS) {
                logger.debug("GetDigitalChannelNum failed");
                logger.error("获取数字通道数失败: userId={}, 错误码={}", userId, ret);
                return -1;
            }
            int digitalChanCount = piDigitalChanCount.getValue();

            // 获取总通道数
            IntByReference piChanTotalCount = new IntByReference();
            logger.debug("Calling GetChannelNum");
            ret = nvssdkLibrary.NetClient_GetChannelNum(userId, piChanTotalCount);
            logger.debug("GetChannelNum ret={}", ret);
            if (ret != NvssdkLibrary.RET_SUCCESS) {
                logger.debug("GetChannelNum failed");
                logger.error("获取总通道数失败: userId={}, 错误码={}", userId, ret);
                return -1;
            }
            int chanTotalCount = piChanTotalCount.getValue();

            // 如果数字通道数为0，则是IPC设备，使用总通道数
            if (digitalChanCount == 0) {
                digitalChanCount = chanTotalCount;
            }
            logger.debug("Channel counts digital={} total={}", digitalChanCount, chanTotalCount);

            logger.info("天地伟业预览启动: 通道数获取完成 总通道={}, 数字通道={}, 请求通道0-based={}",
                    chanTotalCount, digitalChanCount, channelNo);

            // 验证通道号是否在有效范围内
            if (channelNo < 0 || channelNo >= digitalChanCount) {
                logger.error("通道号无效: channelNo={}, 有效范围: 0-{}", channelNo, digitalChanCount - 1);
                return -1;
            }

            TiandySDKStructure.tagNetClientPara tVideoPara = new TiandySDKStructure.tagNetClientPara();

            // 设置预览参数
            tVideoPara.iSize = tVideoPara.size();
            tVideoPara.tCltInfo.m_iServerID = userId;
            tVideoPara.tCltInfo.m_iChannelNo = channelNo;
            tVideoPara.tCltInfo.m_iStreamNO = 0;
            tVideoPara.tCltInfo.m_iNetMode = 1; // UDP
            tVideoPara.tCltInfo.m_iTimeout = 20;

            // 填充 m_cRemoteIP：SDK 需要设备 IP 来建立数据连接（登录3000, 数据3001, SDK自动处理端口）
            String deviceIp = loginIdToIpMap.get(userId);
            if (deviceIp != null && !deviceIp.isEmpty()) {
                fillBytes(tVideoPara.tCltInfo.m_cRemoteIP, deviceIp);
                logger.info("天地伟业预览启动: userId={}, channelNo={}, streamNO=1, m_cRemoteIP={}, structSize={}",
                        userId, channelNo, deviceIp, tVideoPara.size());
            } else {
                logger.warn("天地伟业预览启动: userId={} 无 IP 映射, structSize={}", userId, tVideoPara.size());
            }

            logger.debug("VideoPara streamType={} netMode={} timeout={} channelNo={} cltInfoSize={}",
                    tVideoPara.tCltInfo.m_iStreamNO, tVideoPara.tCltInfo.m_iNetMode, tVideoPara.tCltInfo.m_iTimeout,
                    channelNo, tVideoPara.tCltInfo.size());

            // 采用 SDK 默认缓冲区参数
            /*
             * tVideoPara.tCltInfo.m_iBufferCount = 20;
             * tVideoPara.tCltInfo.m_iDelayNum = 1;
             * tVideoPara.tCltInfo.m_iDelayTime = 0;
             * tVideoPara.tCltInfo.m_iTTL = 8;
             * tVideoPara.tCltInfo.m_iFlag = 0;
             * tVideoPara.tCltInfo.m_iPosition = 0;
             * tVideoPara.tCltInfo.m_iSpeed = 0;
             */

            tVideoPara.iCryptType = 0;
            tVideoPara.pCbkFullFrm = CallbackReference.getFunctionPointer(realPlayCbkFullFrame);
            tVideoPara.pvCbkFullFrmUsrData = null;
            tVideoPara.pCbkRawFrm = CallbackReference.getFunctionPointer(realPlayCbkRawFrame);
            tVideoPara.pvCbkRawFrmUsrData = null;
            tVideoPara.iIsForbidDecode = NvssdkLibrary.RAW_NOTIFY_ALLOW_DECODE;
            tVideoPara.pvWnd = null;
            tVideoPara.iVideoRenderFlag = 1;
            tVideoPara.m_iBitRateFlag = 0;

            // 写入
            tVideoPara.tCltInfo.write();
            tVideoPara.write();

            IntByReference piConnectID = new IntByReference();
            logger.debug("Calling SyncRealPlay");
            logger.info("天地伟业预览启动: 即将调用 NetClient_SyncRealPlay（可能阻塞至超时）");

            final int SYNC_REALPLAY_HARD_TIMEOUT_SEC = 90;
            final com.sun.jna.Pointer paraPtr = tVideoPara.getPointer();
            final int paraSize = tVideoPara.iSize;

            long startTime = System.currentTimeMillis();
            int iRet = NvssdkLibrary.RET_FAILED;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    Future<Integer> future = syncRealPlayExecutor.submit(
                            () -> nvssdkLibrary.NetClient_SyncRealPlay(piConnectID, paraPtr, paraSize));
                    iRet = future.get(SYNC_REALPLAY_HARD_TIMEOUT_SEC, TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException te) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.error("天地伟业预览启动: SyncRealPlay Java层硬超时({}s), userId={}, channel={}, attempt={}, elapsedMs={}",
                            SYNC_REALPLAY_HARD_TIMEOUT_SEC, userId, channel, attempt, elapsed);
                    return -1;
                } catch (java.util.concurrent.ExecutionException ee) {
                    logger.error("天地伟业预览启动: SyncRealPlay执行异常 userId={}, attempt={}", userId, attempt, ee.getCause());
                    return -1;
                }
                if (iRet == NvssdkLibrary.RET_SUCCESS || !isRetriableError(iRet) || attempt == 2) {
                    break;
                }
                logger.warn("天地伟业预览启动首次失败，准备重试: userId={}, channel={}, iRet={}, attempt={}",
                        userId, channel, iRet, attempt);
                Thread.sleep(300L * attempt);
            }
            long endTime = System.currentTimeMillis();
            logger.debug("SyncRealPlay returns {} (took {}ms)", iRet, endTime - startTime);

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
        } finally {
            if (locked) {
                realPlayLock.unlock();
                logger.debug("TiandySDK unlocked");
                logger.info("天地伟业预览启动: 释放锁 userId={}, channel={}", userId, channel);
            }
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
            String actualFilePath = filePath == null ? "" : filePath.replace("\0", "");
            if (!actualFilePath.endsWith(".sdv")) {
                actualFilePath = actualFilePath + ".sdv";
            }
            // 参考Channel.java:414，文件名需要以\0结尾
            ByteBuffer strBuffer = ByteBuffer.wrap(toCStringBytes(actualFilePath));
            int iRet = nvssdkLibrary.NetClient_StartCaptureFile(connectId, strBuffer, NvssdkLibrary.REC_FILE_TYPE_SDV);

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                logger.info("天地伟业录制启动成功: connectID={}, filePath={}", connectId, actualFilePath);
                return true;
            } else {
                logger.error("天地伟业录制启动失败: connectID={}, filePath={}, 错误码={}", connectId, actualFilePath, iRet);
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

        if (connectId < 0) {
            logger.warn("天地伟业抓图拒绝: 无可用预览连接(connectId<0)");
            return false;
        }

        try {
            if (filePath == null || filePath.isEmpty()) {
                logger.error("天地伟业抓图: 文件路径为空");
                return false;
            }
            String actualFilePath = filePath.replace("\0", "");
            if (!actualFilePath.endsWith(".jpg")) {
                actualFilePath = actualFilePath.endsWith(".bmp") ? actualFilePath.replace(".bmp", ".jpg")
                        : actualFilePath + ".jpg";
            }

            logger.info("天地伟业抓图: connectId={}, filePath={}", connectId, actualFilePath);

            int capRet = NvssdkLibrary.RET_FAILED;
            for (int attempt = 1; attempt <= 2; attempt++) {
                ByteBuffer buf = ByteBuffer.wrap(toCStringBytes(actualFilePath));
                capRet = nvssdkLibrary.NetClient_CapturePicture(connectId, NvssdkLibrary.CAPTURE_PICTURE_TYPE_JPG, buf);
                if (capRet > 0) {
                    logger.info("天地伟业抓图成功: connectId={}, 字节数={}, filePath={}, attempt={}",
                            connectId, capRet, actualFilePath, attempt);
                    return true;
                }
                if (!isRetriableCaptureError(capRet) || attempt == 2) {
                    break;
                }
                logger.warn("天地伟业抓图失败准备重试: connectId={}, filePath={}, capRet={}, attempt={}",
                        connectId, actualFilePath, capRet, attempt);
                Thread.sleep(120L * attempt);
            }
            logger.error("天地伟业抓图失败: connectId={}, 返回值={}{}", connectId, capRet, getCaptureErrorDesc(capRet));
            return false;
        } catch (Exception e) {
            logger.error("天地伟业抓图异常: connectId={}, filePath={}", connectId, filePath, e);
            return false;
        }
    }

    /** 根据 YUV422 字节数推断宽高（width*height*2 = size），常用分辨率优先 */
    private static int[] inferYuv422Resolution(int size) {
        int half = size / 2;
        int[][] candidates = { { 1920, 1080 }, { 1280, 720 }, { 704, 576 }, { 640, 480 }, { 352, 288 }, { 320, 240 } };
        for (int[] wh : candidates) {
            if (wh[0] * wh[1] == half)
                return wh;
        }
        return null;
    }

    /** YUV422 (YUYV) 转 RGB 并写入 JPG 文件 */
    private boolean yuv422ToJpg(byte[] yuv, int width, int height, String jpgPath) {
        if (yuv == null || width <= 0 || height <= 0 || yuv.length < width * height * 2) {
            return false;
        }
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            byte[] rgb = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
            int pixelCount = width * height;
            int rgbIdx = 0;
            for (int i = 0; i < pixelCount * 2; i += 4) {
                int y0 = yuv[i] & 0xff;
                int u = (yuv[i + 1] & 0xff) - 128;
                int y1 = yuv[i + 2] & 0xff;
                int v = (yuv[i + 3] & 0xff) - 128;
                int c0 = y0 - 16, c1 = y1 - 16;
                int r0 = (298 * c0 + 409 * v + 128) >> 8;
                int g0 = (298 * c0 - 100 * u - 208 * v + 128) >> 8;
                int b0 = (298 * c0 + 516 * u + 128) >> 8;
                int r1 = (298 * c1 + 409 * v + 128) >> 8;
                int g1 = (298 * c1 - 100 * u - 208 * v + 128) >> 8;
                int b1 = (298 * c1 + 516 * u + 128) >> 8;
                rgb[rgbIdx++] = (byte) Math.max(0, Math.min(255, b0));
                rgb[rgbIdx++] = (byte) Math.max(0, Math.min(255, g0));
                rgb[rgbIdx++] = (byte) Math.max(0, Math.min(255, r0));
                rgb[rgbIdx++] = (byte) Math.max(0, Math.min(255, b1));
                rgb[rgbIdx++] = (byte) Math.max(0, Math.min(255, g1));
                rgb[rgbIdx++] = (byte) Math.max(0, Math.min(255, r1));
            }
            // 使用较高 JPEG 质量（0.9）写入，避免默认压缩导致不清晰
            return writeJpegWithQuality(img, jpgPath, 0.9f);
        } catch (Exception e) {
            logger.warn("YUV 转 JPG 写入失败: {}", jpgPath, e);
            return false;
        }
    }

    /** 以指定质量写入 JPEG，quality 建议 0.85f～1.0f，越高越清晰、文件越大 */
    private boolean writeJpegWithQuality(BufferedImage img, String jpgPath, float quality) {
        Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            try {
                return ImageIO.write(img, "jpg", new File(jpgPath));
            } catch (IOException e) {
                return false;
            }
        }
        ImageWriter writer = writers.next();
        try {
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(new File(jpgPath))) {
                if (ios == null) {
                    return ImageIO.write(img, "jpg", new File(jpgPath));
                }
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }
                writer.write(null, new IIOImage(img, null, null), param);
                return true;
            }
        } catch (Exception e) {
            logger.warn("JPEG 高质量写入失败，回退默认: {}", jpgPath, e);
            try {
                return ImageIO.write(img, "jpg", new File(jpgPath));
            } catch (IOException e2) {
                return false;
            }
        } finally {
            writer.dispose();
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
            // 停止动作通常速度设为0（或者SDK内部忽略）
            int currentSpeed = "stop".equalsIgnoreCase(action) ? 0 : speed;

            int iRet = NvssdkLibrary.RET_FAILED;
            for (int attempt = 1; attempt <= 2; attempt++) {
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
                    iRet = nvssdkLibrary.NetClient_DeviceCtrlEx(userId, channelNo, actionCode, 0, 0, 0);
                    logger.debug("云台其他控制: actionCode={}, action={}", actionCode, action);
                }
                if (iRet == NvssdkLibrary.RET_SUCCESS || !isRetriableError(iRet) || attempt == 2) {
                    break;
                }
                logger.warn("天地伟业云台控制失败准备重试: userId={}, channel={}, command={}, iRet={}, attempt={}",
                        userId, channel, command, iRet, attempt);
                Thread.sleep(120L * attempt);
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
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return false;
        }

        try {
            // 通道号转换为0-based
            int channelNo = channel > 0 ? channel - 1 : 0;

            // 将角度转换为SDK参数（角度*100，保留两位小数精度）
            int panValue = (int) (pan * 100);
            int tiltValue = (int) (tilt * 100);
            int zoomValue = (int) (zoom * 100);

            logger.info("天地伟业云台绝对定位: userId={}, channel={}, pan={}°, tilt={}°, zoom={}x",
                    userId, channel, pan, tilt, zoom);

            // 方法1：尝试使用COMMAND_ID_SYNC_SETPTZ同步接口设置绝对坐标
            TiandySDKStructure.PTZ_ABSOLUTE_POS ptzPos = new TiandySDKStructure.PTZ_ABSOLUTE_POS();
            ptzPos.iSize = ptzPos.size();
            ptzPos.iChannelNo = channelNo;
            ptzPos.iPan = panValue;
            ptzPos.iTilt = tiltValue;
            ptzPos.iZoom = zoomValue;
            ptzPos.write();

            int iRet = nvssdkLibrary.NetClient_SendCommand(userId,
                    NvssdkLibrary.COMMAND_ID_SYNC_SETPTZ,
                    channelNo, ptzPos.getPointer(), ptzPos.size());

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                logger.info("天地伟业云台绝对定位成功(SYNC_SETPTZ): userId={}, channel={}, pan={}°, tilt={}°, zoom={}x",
                        userId, channel, pan, tilt, zoom);
                ptzPositionCache.put(ptzCacheKey(userId, channel), new PtzPosition(pan, tilt, zoom));
                return true;
            }

            logger.warn("天地伟业SYNC_SETPTZ失败(错误码={}), 尝试备用方法...", iRet);

            // 方法2：先获取当前位置，再用方向控制模拟绝对定位
            return gotoAngleFallback(userId, channel, channelNo, pan, tilt, zoom);

        } catch (Exception e) {
            logger.error("天地伟业云台绝对定位异常: userId={}, channel={}, pan={}, tilt={}", userId, channel, pan, tilt, e);
            return false;
        }
    }

    /**
     * 天地伟业绝对定位 fallback：
     * 先通过 SYNC_GETPTZ 读取当前位置，计算角度差，用方向控制转动到目标位置。
     * 若无法读取当前位置，直接以高速发送方向控制指令并按角度估算转动时长。
     */
    private boolean gotoAngleFallback(int userId, int channel, int channelNo, float targetPan, float targetTilt, float targetZoom) {
        try {
            // 尝试获取当前 PTZ 位置
            float currentPan = Float.NaN, currentTilt = Float.NaN;
            TiandySDKStructure.PTZ_ABSOLUTE_POS getPtzPos = new TiandySDKStructure.PTZ_ABSOLUTE_POS();
            getPtzPos.iSize = getPtzPos.size();
            getPtzPos.iChannelNo = channelNo;
            getPtzPos.write();
            int getRet = nvssdkLibrary.NetClient_RecvCommand(userId,
                    NvssdkLibrary.COMMAND_ID_SYNC_GETPTZ,
                    channelNo, getPtzPos.getPointer(), getPtzPos.size());
            if (getRet == NvssdkLibrary.RET_SUCCESS) {
                getPtzPos.read();
                currentPan = getPtzPos.iPan / 100.0f;
                currentTilt = getPtzPos.iTilt / 100.0f;
                logger.info("天地伟业当前PTZ位置: pan={}°, tilt={}°", currentPan, currentTilt);
            } else {
                logger.warn("天地伟业获取当前PTZ位置失败，将使用纯时间估算模式");
            }

            // 天地伟业 PTZ 全速大约 120°/s（根据实测估算，速度=100时）
            final float DEG_PER_SEC_AT_MAX = 120.0f;
            final int MOVE_SPEED = 80; // 使用80/100速度（约96°/s）
            final float DEG_PER_SEC = DEG_PER_SEC_AT_MAX * MOVE_SPEED / 100.0f;

            boolean panOk = true, tiltOk = true;

            // --- Pan 控制 ---
            if (!Float.isNaN(currentPan)) {
                // 计算最短路径差（-180~+180）
                float panDiff = targetPan - currentPan;
                while (panDiff > 180) panDiff -= 360;
                while (panDiff < -180) panDiff += 360;
                if (Math.abs(panDiff) > 1.0f) {
                    int panCmd = panDiff > 0 ? NvssdkLibrary.PROTOCOL_MOVE_RIGHT : NvssdkLibrary.PROTOCOL_MOVE_LEFT;
                    long durationMs = (long) (Math.abs(panDiff) / DEG_PER_SEC * 1000);
                    durationMs = Math.min(durationMs, 5000); // 最多转5秒
                    nvssdkLibrary.NetClient_DeviceCtrlEx(userId, channelNo, panCmd, MOVE_SPEED, 0, 0);
                    Thread.sleep(durationMs);
                    nvssdkLibrary.NetClient_DeviceCtrlEx(userId, channelNo, NvssdkLibrary.PROTOCOL_MOVE_STOP, 0, 0, 0);
                    logger.info("天地伟业pan方向控制: diff={}°, dur={}ms", String.format("%.1f", panDiff), durationMs);
                }
            } else {
                // 没有当前位置，直接高速转向目标方向（无法精确到位，仅尽力而为）
                // 此时无法计算差值，跳过
                logger.warn("天地伟业无当前位置，跳过方向控制fallback");
                return false;
            }

            // --- Tilt 控制 ---
            if (!Float.isNaN(currentTilt)) {
                float tiltDiff = targetTilt - currentTilt;
                if (Math.abs(tiltDiff) > 1.0f) {
                    int tiltCmd = tiltDiff > 0 ? NvssdkLibrary.PROTOCOL_MOVE_UP : NvssdkLibrary.PROTOCOL_MOVE_DOWN;
                    long durationMs = (long) (Math.abs(tiltDiff) / DEG_PER_SEC * 1000);
                    durationMs = Math.min(durationMs, 3000);
                    nvssdkLibrary.NetClient_DeviceCtrlEx(userId, channelNo, tiltCmd, 0, MOVE_SPEED, 0);
                    Thread.sleep(durationMs);
                    nvssdkLibrary.NetClient_DeviceCtrlEx(userId, channelNo, NvssdkLibrary.PROTOCOL_MOVE_STOP, 0, 0, 0);
                    logger.info("天地伟业tilt方向控制: diff={}°, dur={}ms", String.format("%.1f", tiltDiff), durationMs);
                }
            }

            // --- Zoom 控制（简单时间估算）---
            float currentZoom = Float.NaN;
            if (getRet == NvssdkLibrary.RET_SUCCESS) {
                currentZoom = getPtzPos.iZoom / 100.0f;
            }
            if (!Float.isNaN(currentZoom) && Math.abs(targetZoom - currentZoom) > 0.5f) {
                int zoomCmd = targetZoom > currentZoom ? NvssdkLibrary.ZOOM_BIG : NvssdkLibrary.ZOOM_SMALL;
                int zoomStopCmd = targetZoom > currentZoom ? NvssdkLibrary.ZOOM_BIG_STOP : NvssdkLibrary.ZOOM_SMALL_STOP;
                long zoomMs = (long) (Math.abs(targetZoom - currentZoom) * 200); // 约200ms/倍
                zoomMs = Math.min(zoomMs, 3000);
                nvssdkLibrary.NetClient_DeviceCtrlEx(userId, channelNo, zoomCmd, 0, 0, 0);
                Thread.sleep(zoomMs);
                nvssdkLibrary.NetClient_DeviceCtrlEx(userId, channelNo, zoomStopCmd, 0, 0, 0);
                logger.info("天地伟业zoom控制: diff={}, dur={}ms", String.format("%.1f", targetZoom - currentZoom), zoomMs);
            }

            logger.info("天地伟业云台绝对定位(fallback)完成: userId={}, targetPan={}°, targetTilt={}°, targetZoom={}x",
                    userId, targetPan, targetTilt, targetZoom);
            boolean result = panOk && tiltOk;
            if (result) {
                ptzPositionCache.put(ptzCacheKey(userId, channel), new PtzPosition(targetPan, targetTilt, targetZoom));
            }
            return result;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.warn("天地伟业绝对定位fallback被中断");
            return false;
        } catch (Exception e) {
            logger.error("天地伟业绝对定位fallback异常", e);
            return false;
        }
    }

    @Override
    public PtzPosition getPtzPosition(int userId, int channel) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return null;
        }

        try {
            // 通道号转换为0-based
            int channelNo = channel > 0 ? channel - 1 : 0;

            // 尝试使用COMMAND_ID_SYNC_GETPTZ同步接口获取绝对坐标
            TiandySDKStructure.PTZ_ABSOLUTE_POS ptzPos = new TiandySDKStructure.PTZ_ABSOLUTE_POS();
            ptzPos.iSize = ptzPos.size();
            ptzPos.iChannelNo = channelNo;
            ptzPos.write();

            int iRet = nvssdkLibrary.NetClient_RecvCommand(userId,
                    NvssdkLibrary.COMMAND_ID_SYNC_GETPTZ,
                    channelNo, ptzPos.getPointer(), ptzPos.size());

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                ptzPos.read();

                // 将SDK返回值转换为实际角度（除以100）
                float pan = ptzPos.iPan / 100.0f;
                float tilt = ptzPos.iTilt / 100.0f;
                float zoom = ptzPos.iZoom / 100.0f;

                logger.debug("天地伟业获取PTZ位置成功: userId={}, channel={}, pan={}°, tilt={}°, zoom={}x",
                        userId, channel, pan, tilt, zoom);

                PtzPosition pos = new PtzPosition(pan, tilt, zoom);
                ptzPositionCache.put(ptzCacheKey(userId, channel), pos);
                return pos;
            } else {
                logger.debug("天地伟业获取PTZ位置失败(错误码={}): userId={}, channel={}", iRet, userId, channel);
                logger.debug("天地伟业SDK可能不支持SYNC_GETPTZ，需要通过PARA_GET_PTZ回调获取");
                // 返回内存缓存（由 PARA_GET_PTZ 回调或上次 gotoAngle 写入）
                PtzPosition cached = ptzPositionCache.get(ptzCacheKey(userId, channel));
                if (cached != null) {
                    logger.debug("天地伟业使用PTZ位置缓存: userId={}, channel={}, pan={}°, tilt={}°, zoom={}x",
                            userId, channel, cached.getPan(), cached.getTilt(), cached.getZoom());
                }
                return cached;
            }
        } catch (Exception e) {
            logger.error("天地伟业获取PTZ位置异常: userId={}, channel={}", userId, channel, e);
            return ptzPositionCache.get(ptzCacheKey(userId, channel));
        }
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
            byte[] filenameBytes = toCStringBytes(localFilePath);
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
            int iRet = NvssdkLibrary.RET_FAILED;
            for (int attempt = 1; attempt <= 2; attempt++) {
                iRet = nvssdkLibrary.NetClient_NetFileDownload(iConnID, userId,
                        NvssdkLibrary.DOWNLOAD_CMD_TIMESPAN, tDownloadTimeSpan.getPointer(), tDownloadTimeSpan.size());
                if (iRet == NvssdkLibrary.RET_SUCCESS || !isRetriableError(iRet) || attempt == 2) {
                    break;
                }
                logger.warn("天地伟业按时间范围下载首次失败，准备重试: userId={}, channel={}, iRet={}, attempt={}",
                        userId, channel, iRet, attempt);
                Thread.sleep(200L * attempt);
            }

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                int downloadId = iConnID.getValue();
                downloadIdToUserIdMap.put(downloadId, userId);
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

            Integer userId = downloadIdToUserIdMap.get(downloadId);
            if (userId == null || userId < 0) {
                logger.error("下载调速失败: 未找到downloadId对应的userId, downloadId={}", downloadId);
                return false;
            }
            IntByReference iConnID = new IntByReference(downloadId);
            // 官方 Java Demo(Playback/Channel/SyncBusiness) 均使用 m_iLogonID 作为 DOWNLOAD_CMD_CONTROL 第二个参数
            int iRet = nvssdkLibrary.NetClient_NetFileDownload(iConnID, userId,
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
            byte[] filenameBytes = toCStringBytes(localFilePath);
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
            int iRet = NvssdkLibrary.RET_FAILED;
            for (int attempt = 1; attempt <= 2; attempt++) {
                iRet = nvssdkLibrary.NetClient_NetFileDownload(iConnID, userId,
                        NvssdkLibrary.DOWNLOAD_CMD_TIMESPAN, tDownloadTimeSpan.getPointer(), tDownloadTimeSpan.size());
                if (iRet == NvssdkLibrary.RET_SUCCESS || !isRetriableError(iRet) || attempt == 2) {
                    break;
                }
                logger.warn("天地伟业按时间范围下载(指定类型)首次失败，准备重试: userId={}, channel={}, iRet={}, attempt={}",
                        userId, channel, iRet, attempt);
                Thread.sleep(200L * attempt);
            }

            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                int downloadId = iConnID.getValue();
                downloadIdToUserIdMap.put(downloadId, userId);

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
                downloadIdToUserIdMap.remove(downloadId);
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
                long downloadedBytes = Integer.toUnsignedLong(piPos.getValue()); // 已下载字节数（按无符号处理）
                long totalBytes = Integer.toUnsignedLong(piDLSize.getValue()); // 总字节数（按无符号处理）

                // 计算百分比（0-100）
                int progress;
                if (totalBytes > 0) {
                    // 使用long避免溢出
                    long progressLong = downloadedBytes * 100 / totalBytes;
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
