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
     */
    public static class CLIENTINFO extends Structure {
        public CLIENTINFO() {
            super();
        }
        
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
     * 预览参数结构体（NetClientPara）
     */
    public static class tagNetClientPara extends Structure {
        public int iSize;
        public CLIENTINFO tCltInfo;
        public int iCryptType;  // 0=no encryption, 1=AES encryption
        public byte[] cCryptKey = new byte[32];
        public Pointer cbkDataArrive;  // Network to receive the original data callback
        public Pointer pvUserData;
        public int iPicType;  // Client request picture stream type
        public NvssdkLibrary.FULLFRAME_NOTIFY_V4 pCbkFullFrm;  // full frame callback
        public Pointer pvCbkFullFrmUsrData;
        public NvssdkLibrary.RAWFRAME_NOTIFY pCbkRawFrm;  // raw frame callback
        public Pointer pvCbkRawFrmUsrData;
        public int iIsForbidDecode;  // 0=allow decode, 1=forbid decode
        public Pointer pvWnd;  // window handle for show video, NULL means not show
        
        public tagNetClientPara() {
            super();
            tCltInfo = new CLIENTINFO();
        }
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("iSize", "tCltInfo", "iCryptType", "cCryptKey", "cbkDataArrive", 
                                "pvUserData", "iPicType", "pCbkFullFrm", "pvCbkFullFrmUsrData", 
                                "pCbkRawFrm", "pvCbkRawFrmUsrData", "iIsForbidDecode", "pvWnd");
        }
    }
    
    /**
     * 云台控制参数结构体
     */
    public static class tagTransparentChannelControl extends Structure {
        public int iControlCode;  // 控制码
        public int iSpeed;       // 速度
        public int iPresetNo;    // 预置点号
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("iControlCode", "iSpeed", "iPresetNo");
        }
    }
    
    /**
     * 文件时间结构体
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
     * 查询文件通道结构体
     */
    public static class QueryFileChannel extends Structure {
        public int iChannelNo;
        public int iStreamNo;
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("iChannelNo", "iStreamNo");
        }
    }
    
    /**
     * 查询文件通道数组
     */
    public static class ArrayQueryFileChannel extends Structure {
        public QueryFileChannel[] tArry = new QueryFileChannel[2];
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("tArry");
        }
    }
    
    /**
     * 回放查询参数结构体（NETFILE_QUERY_V5）
     */
    public static class NETFILE_QUERY_V5 extends Structure {
        public int iBufSize;
        public int iQueryChannelNo;  // 查询通道号，0x7FFFFFFF表示查询所有通道
        public int iStreamNo;  // 码流号
        public int iType;  // 视频类型
        public NVS_FILE_TIME tStartTime;  // 开始时间
        public NVS_FILE_TIME tStopTime;  // 结束时间
        public int iPageSize;  // 每页大小
        public int iPageNo;  // 页码
        public int iFiletype;  // 文件类型 0=all, 1=Video, 2=picture
        public int iDevType;  // 设备类型
        public byte[] cOtherQuery = new byte[65];
        public int iTriggerType;  // 报警类型
        public int iTrigger;  // 端口(通道)号
        public int iQueryType;  // 查询类型
        public int iQueryCondition;  // 查询条件
        public byte[] cField = new byte[5 * 68];  // 查询消息
        public int iQueryChannelCount;  // 查询通道数量
        public int iBufferSize;  // sizeof(QueryFileChannel)
        public Pointer ptChannelList;  // 通道列表指针
        public byte[] cLaneNo = new byte[65];  // 车道号
        public byte[] cVehicleType = new byte[65];  // 车辆类型
        public int iFileAttr;  // 文件属性
        public int[] iQueryTypeValue = new int[6];
        public int iCurQueryCount;  // 输出参数：当前查询数量
        public int iTotalQueryCount;  // 输出参数：总查询数量
        
        public NETFILE_QUERY_V5() {
            super();
            tStartTime = new NVS_FILE_TIME();
            tStopTime = new NVS_FILE_TIME();
        }
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("iBufSize", "iQueryChannelNo", "iStreamNo", "iType", "tStartTime", 
                                "tStopTime", "iPageSize", "iPageNo", "iFiletype", "iDevType", 
                                "cOtherQuery", "iTriggerType", "iTrigger", "iQueryType", 
                                "iQueryCondition", "cField", "iQueryChannelCount", "iBufferSize", 
                                "ptChannelList", "cLaneNo", "cVehicleType", "iFileAttr", 
                                "iQueryTypeValue", "iCurQueryCount", "iTotalQueryCount");
        }
    }
    
    /**
     * 回放文件数据结构体（NVS_FILE_DATA）
     */
    public static class NVS_FILE_DATA extends Structure {
        public int iType;  // 录制类型 1=手动录制, 2=定时录制, 3=报警录制
        public int iChannel;  // 录制通道
        public byte[] cFileName = new byte[250];  // 文件名
        public NVS_FILE_TIME tStartTime;  // 文件开始时间
        public NVS_FILE_TIME tStopTime;  // 文件结束时间
        public int iFileSize;  // 文件大小
        
        public NVS_FILE_DATA() {
            super();
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
     */
    public static class QueryFileResult extends Structure {
        public NVS_FILE_DATA[] tArry = new NVS_FILE_DATA[20];
        
        public QueryFileResult() {
            super();
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
     */
    public static class RAWFRAME_INFO extends Structure {
        public int nType;  // 帧类型
        public int nWidth;  // 宽度
        public int nHeight;  // 高度
        public int nTimeStamp;  // 时间戳
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("nType", "nWidth", "nHeight", "nTimeStamp");
        }
    }
}
