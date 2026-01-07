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
            return true;
        }
        
        this.sdkConfig = config;
        
        try {
            // 加载SDK库
            if (!loadLibrary()) {
                logger.error("加载天地伟业SDK库失败");
                return false;
            }
            
            // SDK初始化
            int ret = nvssdkLibrary.NetClient_Startup_V4(0, 0, 0);
            if (ret != NvssdkLibrary.RET_SUCCESS) {
                logger.error("天地伟业SDK初始化失败，返回值: {}", ret);
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
            TiandySDKStructure.tagNetClientPara tVideoPara = new TiandySDKStructure.tagNetClientPara();
            
            // 设置预览参数
            tVideoPara.iSize = tVideoPara.size();
            tVideoPara.tCltInfo.m_iServerID = userId;  // logon handle
            tVideoPara.tCltInfo.m_iChannelNo = channel;
            tVideoPara.tCltInfo.m_iStreamNO = streamType;  // 0=主码流, 1=子码流
            tVideoPara.tCltInfo.m_iNetMode = 1;  // TCP方式
            tVideoPara.tCltInfo.m_iTimeout = 20;
            tVideoPara.pCbkFullFrm = null;  // 完整帧回调（可以为null）
            tVideoPara.pvCbkFullFrmUsrData = null;
            tVideoPara.pCbkRawFrm = null;  // 原始流回调（可以为null）
            tVideoPara.pvCbkRawFrmUsrData = null;
            tVideoPara.iIsForbidDecode = NvssdkLibrary.RAW_NOTIFY_ALLOW_DECODE;
            tVideoPara.pvWnd = null;  // 不显示视频窗口
            
            tVideoPara.write();
            
            IntByReference piConnectID = new IntByReference();
            int iRet = nvssdkLibrary.NetClient_SyncRealPlay(piConnectID, tVideoPara.getPointer(), tVideoPara.iSize);
            
            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                int connectID = piConnectID.getValue();
                logger.info("天地伟业预览启动成功: userId={}, channel={}, streamType={}, connectID={}", 
                    userId, channel, streamType, connectID);
                return connectID;
            } else if (iRet == NvssdkLibrary.RET_SYNCREALPLAY_TIMEOUT) {
                logger.error("天地伟业预览启动超时: userId={}, channel={}", userId, channel);
                return -1;
            } else {
                logger.error("天地伟业预览启动失败: userId={}, channel={}, 错误码={}", userId, channel, iRet);
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
            // 确保文件路径以.sdv结尾（天地伟业默认格式）
            String actualFilePath = filePath;
            if (!actualFilePath.endsWith(".sdv")) {
                actualFilePath = filePath + ".sdv";
            }
            
            ByteBuffer strBuffer = ByteBuffer.wrap((actualFilePath + "\0").getBytes());
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
        
        // 天地伟业SDK的抓图需要connectID，如果connectId无效，需要先启动预览
        if (connectId < 0) {
            logger.warn("天地伟业抓图需要有效的预览连接ID，尝试启动预览: userId={}, channel={}", userId, channel);
            connectId = startRealPlay(userId, channel, 0);  // 使用主码流启动预览
            if (connectId < 0) {
                logger.error("启动预览失败，无法抓图: userId={}, channel={}", userId, channel);
                return false;
            }
        }
        
        try {
            // 参考Channel.java:485-520的实现
            // 确保文件路径有正确的扩展名
            String actualFilePath = filePath;
            if (pictureType == NvssdkLibrary.CAPTURE_PICTURE_TYPE_BMP && !actualFilePath.endsWith(".bmp")) {
                actualFilePath = filePath + ".bmp";
            } else if (pictureType == NvssdkLibrary.CAPTURE_PICTURE_TYPE_JPG && !actualFilePath.endsWith(".jpg")) {
                actualFilePath = filePath + ".jpg";
            }
            
            ByteBuffer strBuffer = ByteBuffer.wrap(actualFilePath.getBytes());
            int iRet = nvssdkLibrary.NetClient_CapturePicture(connectId, pictureType, strBuffer);
            
            if (iRet > 0) {
                logger.info("天地伟业抓图成功: connectID={}, filePath={}, size={}", connectId, actualFilePath, iRet);
                return true;
            } else {
                logger.error("天地伟业抓图失败: connectID={}, filePath={}, 错误码={}", connectId, actualFilePath, iRet);
                return false;
            }
        } catch (Exception e) {
            logger.error("天地伟业抓图异常: connectID={}, filePath={}", connectId, filePath, e);
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
            
            TiandySDKStructure.tagTransparentChannelControl aPara = new TiandySDKStructure.tagTransparentChannelControl();
            aPara.iControlCode = controlCode;
            aPara.iSpeed = speed;
            aPara.iPresetNo = 0;  // 预置点号，普通控制为0
            aPara.write();
            
            int iRet = nvssdkLibrary.NetClient_SendCommand(userId, 
                NvssdkLibrary.COMMAND_ID_TRANSPARENTCHANNELCONTROL_V5, channel, 
                aPara.getPointer(), aPara.size());
            
            if (iRet == NvssdkLibrary.RET_SUCCESS) {
                logger.info("天地伟业云台控制成功: userId={}, channel={}, command={}, action={}, speed={}", 
                    userId, channel, command, action, speed);
                return true;
            } else {
                logger.error("天地伟业云台控制失败: userId={}, channel={}, command={}, 错误码={}", 
                    userId, channel, command, iRet);
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
