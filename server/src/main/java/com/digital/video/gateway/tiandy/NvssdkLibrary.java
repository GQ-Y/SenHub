package com.digital.video.gateway.tiandy;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.ByteBuffer;

/**
 * 天地伟业SDK JNA接口定义
 */
public interface NvssdkLibrary extends Library {
    // INSTANCE 将在 TiandySDK.loadLibrary() 中动态创建，避免静态初始化时找不到库文件
    // NvssdkLibrary INSTANCE = (NvssdkLibrary) Native.loadLibrary("libnvssdk", NvssdkLibrary.class);
    
    // 返回值常量
    int RET_SUCCESS = 0;
    int RET_FAILED = -1;
    
    // 登录类型
    int SERVER_NORMAL = 0;  // 普通模式
    int SERVER_ACTIVE = 1;  // 主动模式
    
    // 同步登录错误码
    int RET_SYNCLOGON_TIMEOUT = -2;
    int RET_SYNCLOGON_USENAME_ERROR = -3;
    int RET_SYNCLOGON_USRPWD_ERROR = -4;
    int RET_SYNCLOGON_PWDERRTIMES_OVERRUN = -5;
    int RET_SYNCLOGON_NET_ERROR = -6;
    int RET_SYNCLOGON_PORT_ERROR = -7;
    int RET_SYNCLOGON_UNKNOW_ERROR = -8;
    
    // 同步预览错误码
    int RET_SYNCREALPLAY_TIMEOUT = -2;
    
    // 登录状态常量（参考NVSSDK.java）
    int LOGON_SUCCESS = 0;  // 登录成功
    int LOGON_FAILED = 4;    // 登录失败
    int LOGON_TIMEOUT = 5;   // 登录超时
    int LOGON_RETRY = 6;     // 重试
    int LOGON_ING = 7;       // 登录中
    
    // 抓图类型
    int CAPTURE_PICTURE_TYPE_YUV = 0;
    int CAPTURE_PICTURE_TYPE_BMP = 1;
    int CAPTURE_PICTURE_TYPE_JPG = 2;
    int CAPTURE_PICTURE_TYPE_FEC_BMP = 3;
    int CAPTURE_PICTURE_TYPE_FEC_JPG = 4;
    
    // 命令ID
    int COMMAND_ID_TRANSPARENTCHANNELCONTROL_V5 = 0x1005;  // 云台控制命令
    
    // 云台控制码（参考NVSSDK.java和VideoCtrl.java示例）
    // 协议模式控制码（用于DeviceCtrlEx）
    int PROTOCOL_MOVE_UP = 1;        // 上
    int PROTOCOL_MOVE_DOWN = 2;      // 下
    int PROTOCOL_MOVE_LEFT = 3;      // 左
    int PROTOCOL_MOVE_RIGHT = 4;     // 右
    int PROTOCOL_MOVE_STOP = 9;      // 停止
    int SET_HOR_AUTO_BEGIN = 23;     // 自动开始
    int SET_HOR_AUTO_END = 24;       // 自动结束
    
    // 变倍控制码
    int ZOOM_BIG = 31;               // 放大
    int ZOOM_BIG_STOP = 32;          // 放大停止
    int ZOOM_SMALL = 33;             // 缩小
    int ZOOM_SMALL_STOP = 34;        // 缩小停止
    
    // 查询命令
    int CMD_NETFILE_QUERY_FILE = 0;  // 查询回放文件
    
    // 录制文件类型
    int REC_FILE_TYPE_SDV = 0;
    int REC_FILE_TYPE_AVI = 1;
    int REC_FILE_TYPE_RAWAAC = 2;
    int REC_FILE_TYPE_PS = 3;
    int REC_FILE_TYPE_TS = 4;
    
    // 原始流通知类型
    int RAW_NOTIFY_ALLOW_DECODE = 0;  // 允许解码
    int RAW_NOTIFY_FORBID_DECODE = 1;  // 禁止解码
    
    // 帧类型
    int AUDIO_FRAME = 5;
    
    // SDK初始化
    int NetClient_Startup_V4(int iServerPort, int iClientPort, int iWnd);
    int NetClient_Cleanup();
    int NetClient_GetVersion(TiandySDKStructure.SDK_VERSION ver);
    
    // 登录相关
    int NetClient_SyncLogon(int iLogonType, Pointer pInBuf, int iInBufSize);
    int NetClient_Logoff(int iLogonID);
    int NetClient_GetLogonStatus(int iLogonID);
    int NetClient_GetLastError();
    
    // 通道相关
    int NetClient_GetChannelNum(int iLogonID, IntByReference piChanNum);
    int NetClient_GetDigitalChannelNum(int iLogonID, IntByReference piDigitChannelNum);
    
    // 预览相关
    // 使用Pointer传递结构体，更安全
    int NetClient_SyncRealPlay(IntByReference puiRecvID, Pointer ptPara, int iParaSize);
    int NetClient_StopRealPlay(int uiRecvID, int iParam);
    
    // 录制相关
    int NetClient_StartCaptureFile(int ulConID, ByteBuffer cFileName, int iRecFileType);
    int NetClient_StopCaptureFile(int ulConID);
    
    // 抓图相关
    int NetClient_CapturePicture(int uiConID, int iPicType, ByteBuffer strFileName);
    int NetClient_CapturePic(int uiConID, PointerByReference pucData);
    // 直接抓图（不需要预览连接）
    int NetClient_CapturePicByDevice(int iLogonID, int iChanNo, int iQvalue, ByteBuffer pcPicFilePath, 
                                     TiandySDKStructure.SnapPicData ptSnapPicData, int iInSize);
    
    // 云台控制
    int NetClient_SendCommand(int iLogonID, int iCommand, int iChannel, Pointer pvBuffer, int iBufSize);
    
    // 下载相关
    int NetClient_NetFileDownload(IntByReference uiConID, int iLogonID, int iCmd, Pointer pvBuf, int iBufSize);
    int NetClient_NetFileStopDownloadFile(int uiConID);
    int NetClient_NetFileGetDownloadPos(int uiConID, IntByReference piPos, IntByReference piDLSize);
    
    // 下载命令常量
    int DOWNLOAD_CMD_FILE = 0;           // 按文件名下载
    int DOWNLOAD_CMD_TIMESPAN = 1;       // 按时间范围下载
    int DOWNLOAD_CMD_CONTROL = 2;        // 下载控制
    int DOWNLOAD_CMD_FILE_CONTINUE = 3;  // 继续下载文件
    
    // 下载文件类型常量
    int DOWNLOAD_FILE_TYPE_SDV = 0;      // SDV格式
    int DOWNLOAD_FILE_TYPE_PS = 3;       // PS格式
    int DOWNLOAD_FILE_TYPE_ZFMP4 = 4;    // MP4格式
    
    // 查询相关
    int NetClient_SyncQuery(int iLogonID, int iChanNo, int iCmd, Pointer pvInPara, int iInLen, 
                            Pointer pvOutPara, int iOutTotalLen, int iSingleLen);
    
    // 设备控制相关
    int NetClient_Reboot(int iLogonID);  // 重启设备
    
    // 回调函数接口（简化版本，可以传null）
    int NetClient_SetNotifyFunction_V4(MAIN_NOTIFY_V4 mainNotify, 
                                        ALARM_NOTIFY_V4 alarmNotify,
                                        PARACHANGE_NOTIFY_V4 paraNotify,
                                        COMRECV_NOTIFY_V4 comNotify,
                                        PROXY_NOTIFY proxyNotify);
    
    // 回调接口定义
    interface MAIN_NOTIFY_V4 extends com.sun.jna.Callback {
        void apply(int iLogonID, com.sun.jna.NativeLong wParam, Pointer lParam, Pointer notifyUserData);
    }
    
    interface ALARM_NOTIFY_V4 extends com.sun.jna.Callback {
        void apply(int ulLogonID, int iChan, int iAlarmState, int iAlarmType, Pointer iUser);
    }
    
    interface PARACHANGE_NOTIFY_V4 extends com.sun.jna.Callback {
        void apply(int ulLogonID, int iChan, int iParaType, TiandySDKStructure.STR_Para strPara, Pointer iUser);
    }
    
    interface COMRECV_NOTIFY_V4 extends com.sun.jna.Callback {
        void apply(int ulLogonID, Pointer cData, int iLen, int iComNo, Pointer iUser);
    }
    
    interface PROXY_NOTIFY extends com.sun.jna.Callback {
        void apply(int ulLogonID, int iCmdKey, Pointer cData, int iLen, Pointer iUser);
    }
    
    // 原始流回调
    interface RAWFRAME_NOTIFY extends com.sun.jna.Callback {
        void apply(int uiID, Pointer pcData, int iLen, TiandySDKStructure.RAWFRAME_INFO ptRawFrameInfo, Pointer lpUserData);
    }
    
    // 完整帧回调
    interface FULLFRAME_NOTIFY_V4 extends com.sun.jna.Callback {
        void apply(int iConnectID, int iStreamType, Pointer pcData, int iLen, Pointer pvHeader, Pointer pvUserData);
    }
}
