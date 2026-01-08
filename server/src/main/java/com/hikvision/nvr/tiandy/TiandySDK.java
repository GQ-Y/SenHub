package com.hikvision.nvr.tiandy;

import com.hikvision.nvr.Common.ArchitectureChecker;
import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.device.DeviceSDK;
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
    
    private TiandySDK() {
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
            
            // SDK初始化
            int ret = nvssdkLibrary.NetClient_Startup_V4(0, 0, 0);
            if (ret != NvssdkLibrary.RET_SUCCESS) {
                logger.error("天地伟业SDK初始化失败，返回值: {}（SDK库加载成功但SDK初始化失败）", ret);
                return false;
            }
            
            // 设置回调函数（可以传null，简化处理）
            nvssdkLibrary.NetClient_SetNotifyFunction_V4(null, null, null, null, null);
            
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
            // 天地伟业SDK库文件在 ./lib/tiandy/ 目录下，不依赖config中的lib_path
            String libDir = System.getProperty("user.dir") + "/lib/tiandy";
            
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
            
            // 设置库路径到LD_LIBRARY_PATH（用于加载依赖库）
            String currentLibPath = System.getProperty("java.library.path");
            String newLibPath = libDir + (currentLibPath != null ? ":" + currentLibPath : "");
            System.setProperty("java.library.path", newLibPath);
            
            // 使用绝对路径加载库（类似海康SDK的方式）
            try {
                nvssdkLibrary = (NvssdkLibrary) Native.loadLibrary(actualLibPath, NvssdkLibrary.class);
                logger.info("天地伟业SDK库加载成功，库路径: {}", actualLibPath);
                return true;
            } catch (UnsatisfiedLinkError e) {
                logger.error("加载天地伟业SDK库失败，库路径: {}，错误: {}", actualLibPath, e.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("加载天地伟业SDK库异常", e);
            return false;
        }
    }
    
    @Override
    public int login(String ip, short port, String username, String password) {
        // 天地伟业SDK支持大于32767的端口（如37777），但short类型会溢出
        // 如果port是负数，说明发生了溢出（如37777会变成-27759）
        // 需要恢复原始值：port & 0xFFFF 会将负数转换为无符号short值，但这不是我们想要的
        // 实际上，如果port是负数，说明原始值大于32767，但无法从负数恢复原始值
        // 因此，对于天地伟业SDK，应该直接使用loginWithIntPort方法，而不是通过这个接口
        // 这里保留此方法以兼容接口，但实际应该使用loginWithIntPort
        int intPort = port < 0 ? (port & 0xFFFF) : port;
        // 如果port是负数，说明原始值在32768-65535之间，需要加上65536
        if (port < 0) {
            intPort = port & 0xFFFF; // 转换为无符号short值（0-65535）
        } else {
            intPort = port;
        }
        return loginWithIntPort(ip, intPort, username, password);
    }
    
    /**
     * 使用int类型端口登录（支持大于32767的端口，如37777）
     * 这是天地伟业SDK的特殊需求，因为其默认端口37777超过了short的最大值
     */
    public int loginWithIntPort(String ip, int port, String username, String password) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return -1;
        }
        
        try {
            // 按照天地伟业SDK示例代码的方式构造登录参数（参考Device.java第257-264行）
            String strCharSet = "UTF-8";
            
            TiandySDKStructure.tagLogonPara logonPara = new TiandySDKStructure.tagLogonPara();
            logonPara.iSize = logonPara.size();
            
            // 按照示例代码的方式，直接使用getBytes()赋值
            // 示例代码：tNormal.cNvsIP = _strDevOrPublicIP.getBytes();
            logonPara.cNvsIP = ip.getBytes();
            logonPara.iNvsPort = port;
            logonPara.cUserName = username.getBytes();
            logonPara.cUserPwd = password.getBytes();
            logonPara.cCharSet = strCharSet.getBytes();
            
            // 其他字段保持默认值（null或0）
            // cProxy, cNvsName, cProductID, cAccontName, cAccontPasswd, cNvsIPV6 使用默认值
            
            logonPara.write();
            
            logger.info("开始登录天地伟业设备: {}:{}, 用户: {}", ip, port, username);
            
            int logonID = nvssdkLibrary.NetClient_SyncLogon(NvssdkLibrary.SERVER_NORMAL, logonPara.getPointer(), logonPara.iSize);
            
            if (logonID >= 0) {
                logger.info("天地伟业设备登录成功: {}:{} (logonID: {})", ip, port, logonID);
                return logonID;
            } else {
                logger.error("天地伟业设备登录失败: {}:{} (错误码: {})", ip, port, logonID);
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
            if (logonStatus != NvssdkLibrary.LOGON_SUCCESS) {  // 0表示登录成功
                logger.error("设备登录状态无效: userId={}, logonStatus={}（0=成功, 4=失败, 5=超时），无法启动预览", 
                    userId, logonStatus);
                return -1;
            }
            
            // 天地伟业SDK的通道号从0开始，如果传入的是1-based的通道号，需要转换为0-based
            int channelNo = channel;
            if (channel > 0) {
                channelNo = channel - 1;  // 转换为0-based索引
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
            tVideoPara.tCltInfo.m_iServerID = userId;  // logon handle
            tVideoPara.tCltInfo.m_iChannelNo = channelNo;  // 使用0-based通道号
            tVideoPara.tCltInfo.m_iStreamNO = streamType;  // 0=主码流, 1=子码流
            tVideoPara.tCltInfo.m_iNetMode = 1;  // TCP方式
            tVideoPara.tCltInfo.m_iTimeout = 20;
            
            // 初始化字节数组字段（避免未初始化导致的问题）
            if (tVideoPara.tCltInfo.m_cNetFile == null) {
                tVideoPara.tCltInfo.m_cNetFile = new byte[255];
            }
            if (tVideoPara.tCltInfo.m_cRemoteIP == null) {
                tVideoPara.tCltInfo.m_cRemoteIP = new byte[16];
            }
            
            // 设置缓冲区参数（参考官方示例Channel.java:287-299和VideoCtrl.java:336-344）
            tVideoPara.tCltInfo.m_iBufferCount = 20;  // 缓冲区数量，官方示例使用20
            tVideoPara.tCltInfo.m_iDelayNum = 1;  // 延迟数量，官方示例使用1
            tVideoPara.tCltInfo.m_iDelayTime = 0;  // 延迟时间
            tVideoPara.tCltInfo.m_iTTL = 8;  // TTL值，官方示例使用8
            tVideoPara.tCltInfo.m_iFlag = 0;  // 标志位
            tVideoPara.tCltInfo.m_iPosition = 0;  // 位置
            tVideoPara.tCltInfo.m_iSpeed = 0;  // 速度
            
            // 回调函数设置（可以为null，但需要确保结构体正确）
            tVideoPara.pCbkFullFrm = null;  // 完整帧回调（可以为null）
            tVideoPara.pvCbkFullFrmUsrData = null;
            tVideoPara.pCbkRawFrm = null;  // 原始流回调（可以为null）
            tVideoPara.pvCbkRawFrmUsrData = null;
            // 允许解码（参考Channel.java:297，预览时使用ALLOW_DECODE）
            // 虽然我们不需要显示，但SDK可能需要解码来建立连接
            tVideoPara.iIsForbidDecode = NvssdkLibrary.RAW_NOTIFY_ALLOW_DECODE;
            tVideoPara.pvWnd = null;  // 不显示视频窗口
            
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
                logger.error("天地伟业预览启动失败: userId={}, channel={}(0-based: {}), 错误码={}", userId, channel, channelNo, iRet);
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
            int iQvalue = 0;  // 使用设备默认质量
            
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
            // 参考Device.java:551-570的实现
            // 将命令字符串转换为控制码
            int controlCode = getPtzControlCode(command);
            if (controlCode == 0) {
                logger.error("未知的云台控制命令: {}", command);
                return false;
            }
            
            // 天地伟业SDK的通道号从0开始，需要转换为0-based
            int channelNo = channel > 0 ? channel - 1 : 0;
            
            TiandySDKStructure.tagTransparentChannelControl aPara = new TiandySDKStructure.tagTransparentChannelControl();
            aPara.iControlCode = controlCode;
            aPara.iSpeed = speed > 0 ? speed : 1;  // 速度至少为1
            aPara.iPresetNo = 0;  // 预置点号，普通控制为0
            aPara.write();
            
            int iRet = nvssdkLibrary.NetClient_SendCommand(userId, 
                NvssdkLibrary.COMMAND_ID_TRANSPARENTCHANNELCONTROL_V5, channelNo, 
                aPara.getPointer(), aPara.size());
            
            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                logger.info("天地伟业云台控制成功: userId={}, channel={}(0-based: {}), command={}, speed={}", 
                    userId, channel, channelNo, command, aPara.iSpeed);
                return true;
            } else {
                logger.error("天地伟业云台控制失败: userId={}, channel={}(0-based: {}), command={}, 错误码={}", 
                    userId, channel, channelNo, command, iRet);
                return false;
            }
        } catch (Exception e) {
            logger.error("天地伟业云台控制异常: userId={}, channel={}, command={}", userId, channel, command, e);
            return false;
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
                return NvssdkLibrary.PROTOCOL_MOVE_UP;  // 1 - 上
            case "down":
                return NvssdkLibrary.PROTOCOL_MOVE_DOWN;  // 2 - 下
            case "left":
                return NvssdkLibrary.PROTOCOL_MOVE_LEFT;  // 3 - 左
            case "right":
                return NvssdkLibrary.PROTOCOL_MOVE_RIGHT;  // 4 - 右
            case "zoom_in":
                return NvssdkLibrary.ZOOM_BIG;  // 31 - 放大
            case "zoom_out":
                return NvssdkLibrary.ZOOM_SMALL;  // 33 - 缩小
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
            tQueryFileV5.iQueryChannelNo = channel;  // 查询指定通道
            tQueryFileV5.iStreamNo = 0;  // 主码流
            tQueryFileV5.iType = 0xFF;  // 所有类型
            tQueryFileV5.iFiletype = 1;  // 视频文件
            tQueryFileV5.iTriggerType = 0x7FFFFFFF;  // 所有触发类型
            tQueryFileV5.iTrigger = 0;
            tQueryFileV5.iPageSize = 20;  // 每页20条
            tQueryFileV5.iPageNo = 0;  // 第一页
            tQueryFileV5.iQueryChannelCount = 0;  // 单通道查询
            tQueryFileV5.iBufferSize = 0;
            tQueryFileV5.ptChannelList = null;
            
            // 设置时间范围
            Calendar cal = Calendar.getInstance();
            cal.setTime(startTime);
            tQueryFileV5.tStartTime.iYear = (short)cal.get(Calendar.YEAR);
            tQueryFileV5.tStartTime.iMonth = (short)(cal.get(Calendar.MONTH) + 1);
            tQueryFileV5.tStartTime.iDay = (short)cal.get(Calendar.DAY_OF_MONTH);
            tQueryFileV5.tStartTime.iHour = (short)cal.get(Calendar.HOUR_OF_DAY);
            tQueryFileV5.tStartTime.iMinute = (short)cal.get(Calendar.MINUTE);
            tQueryFileV5.tStartTime.iSecond = (short)cal.get(Calendar.SECOND);
            
            cal.setTime(endTime);
            tQueryFileV5.tStopTime.iYear = (short)cal.get(Calendar.YEAR);
            tQueryFileV5.tStopTime.iMonth = (short)(cal.get(Calendar.MONTH) + 1);
            tQueryFileV5.tStopTime.iDay = (short)cal.get(Calendar.DAY_OF_MONTH);
            tQueryFileV5.tStopTime.iHour = (short)cal.get(Calendar.HOUR_OF_DAY);
            tQueryFileV5.tStopTime.iMinute = (short)cal.get(Calendar.MINUTE);
            tQueryFileV5.tStopTime.iSecond = (short)cal.get(Calendar.SECOND);
            
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
            tDownloadTimeSpan.m_iSaveFileType = NvssdkLibrary.DOWNLOAD_FILE_TYPE_SDV;  // SDV格式
            tDownloadTimeSpan.m_iFileFlag = 0;  // 0-下载多个文件，1-下载为单个文件
            tDownloadTimeSpan.m_iChannelNO = channelNo;  // 通道号（0-based）
            tDownloadTimeSpan.m_iStreamNo = streamType;  // 码流号：0-主码流，1-子码流
            tDownloadTimeSpan.m_iPosition = -1;  // 不使用定位功能
            tDownloadTimeSpan.m_iSpeed = 16;  // 下载速度：16倍速（官方示例建议）
            tDownloadTimeSpan.m_iIFrame = 0;  // 0-全帧，1-只I帧
            tDownloadTimeSpan.m_iReqMode = 1;  // 1-帧模式，0-流模式
            tDownloadTimeSpan.m_iVodTransEnable = 0;  // 不启用VOD转换
            tDownloadTimeSpan.m_iFileAttr = 0;  // 0-NVR本地存储
            
            // 设置本地保存文件名
            byte[] filenameBytes = localFilePath.getBytes();
            int copyLen = Math.min(filenameBytes.length, tDownloadTimeSpan.m_cLocalFilename.length - 1);
            System.arraycopy(filenameBytes, 0, tDownloadTimeSpan.m_cLocalFilename, 0, copyLen);
            tDownloadTimeSpan.m_cLocalFilename[copyLen] = 0;  // 字符串结束符
            
            // 设置开始时间
            Calendar cal = Calendar.getInstance();
            cal.setTime(startTime);
            tDownloadTimeSpan.m_tTimeBegin.iYear = (short)cal.get(Calendar.YEAR);
            tDownloadTimeSpan.m_tTimeBegin.iMonth = (short)(cal.get(Calendar.MONTH) + 1);
            tDownloadTimeSpan.m_tTimeBegin.iDay = (short)cal.get(Calendar.DAY_OF_MONTH);
            tDownloadTimeSpan.m_tTimeBegin.iHour = (short)cal.get(Calendar.HOUR_OF_DAY);
            tDownloadTimeSpan.m_tTimeBegin.iMinute = (short)cal.get(Calendar.MINUTE);
            tDownloadTimeSpan.m_tTimeBegin.iSecond = (short)cal.get(Calendar.SECOND);
            
            // 设置结束时间
            cal.setTime(endTime);
            tDownloadTimeSpan.m_tTimeEnd.iYear = (short)cal.get(Calendar.YEAR);
            tDownloadTimeSpan.m_tTimeEnd.iMonth = (short)(cal.get(Calendar.MONTH) + 1);
            tDownloadTimeSpan.m_tTimeEnd.iDay = (short)cal.get(Calendar.DAY_OF_MONTH);
            tDownloadTimeSpan.m_tTimeEnd.iHour = (short)cal.get(Calendar.HOUR_OF_DAY);
            tDownloadTimeSpan.m_tTimeEnd.iMinute = (short)cal.get(Calendar.MINUTE);
            tDownloadTimeSpan.m_tTimeEnd.iSecond = (short)cal.get(Calendar.SECOND);
            
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
                int progress = piPos.getValue();  // 进度：0-100
                logger.debug("天地伟业下载进度: downloadId={}, progress={}%, size={}", 
                    downloadId, progress, piDLSize.getValue());
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
