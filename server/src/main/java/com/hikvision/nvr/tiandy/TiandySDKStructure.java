package com.hikvision.nvr.tiandy;

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
}
