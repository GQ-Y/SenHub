package com.hikvision.nvr.tiandy;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * 天地伟业SDK结构体定义
 */
public class TiandySDKStructure {
    
    /**
     * SDK版本信息
     */
    public static class SDK_VERSION extends Structure {
        public int m_ulMajorVersion;
        public int m_ulMinorVersion;
        public int m_ulBuilder;
        public byte[] m_cVerInfo = new byte[64];
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("m_ulMajorVersion", "m_ulMinorVersion", "m_ulBuilder", "m_cVerInfo");
        }
    }
    
    /**
     * 登录参数结构体
     * 按照天地伟业SDK示例代码的LogonPara结构体定义
     */
    public static class tagLogonPara extends Structure {
        public int iSize;                          // Structure size
        public byte[] cProxy = new byte[32];       // The ip address of the upper-level proxy
        public byte[] cNvsIP = new byte[32];       // IPV4 address, not more than 32 characters
        public byte[] cNvsName = new byte[32];     // Nvs name. Used for domain name resolution
        public byte[] cUserName = new byte[16];     // Login Nvs username, not more than 16 characters
        public byte[] cUserPwd = new byte[16];      // Login Nvs password, not more than 16 characters
        public byte[] cProductID = new byte[32];   // Product ID, not more than 32 characters
        public int iNvsPort;                       // The communication port used by the Nvs server
        public byte[] cCharSet = new byte[32];     // Character set
        public byte[] cAccontName = new byte[16];  // The username that connects to the contents server
        public byte[] cAccontPasswd = new byte[16]; // The password that connects to the contents server
        public byte[] cNvsIPV6 = new byte[64];     // IPV6 address
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("iSize", "cProxy", "cNvsIP", "cNvsName", "cUserName", "cUserPwd", 
                                "cProductID", "iNvsPort", "cCharSet", "cAccontName", "cAccontPasswd", "cNvsIPV6");
        }
    }
    
    /**
     * 设备信息结构体
     */
    public static class ENCODERINFO extends Structure {
        public byte[] m_cEncoder = new byte[64];
        public byte[] m_cFactoryID = new byte[64];
        public byte[] m_cSerialNumber = new byte[64];
        public int m_iChannelNum;
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("m_cEncoder", "m_cFactoryID", "m_cSerialNumber", "m_iChannelNum");
        }
    }
    
    /**
     * 客户端信息结构体
     * 参考NVSSDK.java:230-248
     */
    public static class CLIENTINFO extends Structure {
        public int m_iServerID;
        public int m_iChannelNo;
        public byte[] m_cNetFile = new byte[255];
        public byte[] m_cRemoteIP = new byte[16];
        public int m_iNetMode;
        public int m_iTimeout;
        public int m_iTTL;
        public int m_iBufferCount;
        public int m_iDelayNum;
        public int m_iDelayTime;
        public int m_iStreamNO;
        public int m_iFlag;
        public int m_iPosition;
        public int m_iSpeed;
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("m_iServerID", "m_iChannelNo", "m_cNetFile", "m_cRemoteIP", 
                                "m_iNetMode", "m_iTimeout", "m_iTTL", "m_iBufferCount", 
                                "m_iDelayNum", "m_iDelayTime", "m_iStreamNO", "m_iFlag", 
                                "m_iPosition", "m_iSpeed");
        }
    }
    
    /**
     * 网络客户端参数结构体
     * 用于同步预览接口
     */
    public static class tagNetClientPara extends Structure {
        public int iSize;
        public CLIENTINFO tCltInfo;
        public Pointer pCbkFullFrm;
        public Pointer pvCbkFullFrmUsrData;
        public Pointer pCbkRawFrm;
        public Pointer pvCbkRawFrmUsrData;
        public int iIsForbidDecode;
        public Pointer pvWnd;
        
        public tagNetClientPara() {
            tCltInfo = new CLIENTINFO();
        }
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("iSize", "tCltInfo", "pCbkFullFrm", "pvCbkFullFrmUsrData", 
                                "pCbkRawFrm", "pvCbkRawFrmUsrData", "iIsForbidDecode", "pvWnd");
        }
    }
    
    /**
     * 透明通道控制结构体
     * 用于云台控制
     */
    public static class tagTransparentChannelControl extends Structure {
        public int iControlCode;  // 控制码
        public int iSpeed;        // 速度
        public int iPresetNo;     // 预置点号
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("iControlCode", "iSpeed", "iPresetNo");
        }
    }
    
    /**
     * 文件时间结构体
     * 参考NVSSDK.java:330-337
     */
    public static class NVS_FILE_TIME extends Structure {
        public short iYear;
        public short iMonth;
        public short iDay;
        public short iHour;
        public short iMinute;
        public short iSecond;
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("iYear", "iMonth", "iDay", "iHour", "iMinute", "iSecond");
        }
    }
    
    /**
     * 回放文件查询结构体V5
     * 参考NVSSDK.java:348-374
     */
    public static class NETFILE_QUERY_V5 extends Structure {
        public int iBufSize;
        public int iQueryChannelNo;
        public int iStreamNo;
        public int iType;
        public NVS_FILE_TIME tStartTime;
        public NVS_FILE_TIME tStopTime;
        public int iPageSize;
        public int iPageNo;
        public int iFiletype;
        public int iDevType;
        public byte[] cOtherQuery = new byte[65];
        public int iTriggerType;
        public int iTrigger;
        public int iQueryType;
        public int iQueryCondition;
        public byte[] cField = new byte[5 * 68];
        public int iQueryChannelCount;
        public int iBufferSize;
        public Pointer ptChannelList;
        public byte[] cLaneNo = new byte[65];
        public byte[] cVehicleType = new byte[65];
        public int iFileAttr;
        public int[] iQueryTypeValue = new int[6];
        public int iCurQueryCount;      // 输出参数：当前查询数量
        public int iTotalQueryCount;     // 输出参数：总查询数量
        
        public NETFILE_QUERY_V5() {
            tStartTime = new NVS_FILE_TIME();
            tStopTime = new NVS_FILE_TIME();
        }
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("iBufSize", "iQueryChannelNo", "iStreamNo", "iType", 
                                "tStartTime", "tStopTime", "iPageSize", "iPageNo", 
                                "iFiletype", "iDevType", "cOtherQuery", "iTriggerType", 
                                "iTrigger", "iQueryType", "iQueryCondition", "cField", 
                                "iQueryChannelCount", "iBufferSize", "ptChannelList", 
                                "cLaneNo", "cVehicleType", "iFileAttr", "iQueryTypeValue", 
                                "iCurQueryCount", "iTotalQueryCount");
        }
    }
    
    /**
     * 文件数据结构体
     * 参考NVSSDK.java:377-384
     */
    public static class NVS_FILE_DATA extends Structure {
        public int iType;
        public int iChannel;
        public byte[] cFileName = new byte[250];
        public NVS_FILE_TIME tStartTime;
        public NVS_FILE_TIME tStopTime;
        public int iFileSize;
        
        public NVS_FILE_DATA() {
            tStartTime = new NVS_FILE_TIME();
            tStopTime = new NVS_FILE_TIME();
        }
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("iType", "iChannel", "cFileName", "tStartTime", "tStopTime", "iFileSize");
        }
    }
    
    /**
     * 查询文件结果结构体
     * 参考NVSSDK.java:386-388
     */
    public static class QueryFileResult extends Structure {
        public NVS_FILE_DATA[] tArry = new NVS_FILE_DATA[20];
        
        public QueryFileResult() {
            for (int i = 0; i < tArry.length; i++) {
                tArry[i] = new NVS_FILE_DATA();
            }
        }
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("tArry");
        }
    }
    
    /**
     * 原始帧信息结构体
     * 用于原始流回调
     */
    public static class RAWFRAME_INFO extends Structure {
        public int iFrameType;      // 帧类型
        public int iWidth;          // 宽度
        public int iHeight;         // 高度
        public int iTimeStamp;      // 时间戳
        public int iDataLen;        // 数据长度
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("iFrameType", "iWidth", "iHeight", "iTimeStamp", "iDataLen");
        }
    }
}
