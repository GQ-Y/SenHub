package com.hikvision.nvr.tiandy;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

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
    
    // SDK初始化
    int NetClient_Startup_V4(int iServerPort, int iClientPort, int iWnd);
    int NetClient_Cleanup();
    int NetClient_GetVersion(TiandySDKStructure.SDK_VERSION ver);
    
    // 登录相关
    int NetClient_SyncLogon(int iLogonType, Pointer pInBuf, int iInBufSize);
    int NetClient_Logoff(int iLogonID);
    int NetClient_GetLogonStatus(int iLogonID);
    int NetClient_GetLastError();
    
    // 回调函数接口（简化版本，可以传null）
    int NetClient_SetNotifyFunction_V4(MAIN_NOTIFY_V4 mainNotify, 
                                        ALARM_NOTIFY_V4 alarmNotify,
                                        PARACHANGE_NOTIFY_V4 paraNotify,
                                        COMRECV_NOTIFY_V4 comNotify,
                                        PROXY_NOTIFY proxyNotify);
    
    // 回调接口定义
    interface MAIN_NOTIFY_V4 extends com.sun.jna.Callback {
        void apply(int iLogonID, long wParam, Pointer lParam, Pointer notifyUserData);
    }
    
    interface ALARM_NOTIFY_V4 extends com.sun.jna.Callback {
        void apply(int ulLogonID, int iChan, int iAlarmState, int iAlarmType, Pointer iUser);
    }
    
    interface PARACHANGE_NOTIFY_V4 extends com.sun.jna.Callback {
        void apply(int ulLogonID, int iChan, int iParaType, Pointer strPara, Pointer iUser);
    }
    
    interface COMRECV_NOTIFY_V4 extends com.sun.jna.Callback {
        void apply(int ulLogonID, Pointer cData, int iLen, int iComNo, Pointer iUser);
    }
    
    interface PROXY_NOTIFY extends com.sun.jna.Callback {
        void apply(int ulLogonID, int iCmdKey, Pointer cData, int iLen, Pointer iUser);
    }
}
