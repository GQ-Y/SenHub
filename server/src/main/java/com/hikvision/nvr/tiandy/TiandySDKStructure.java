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
     */
    public static class tagLogonPara extends Structure {
        public int iSize;
        public byte[] cNvsIP = new byte[64];
        public int iNvsPort;
        public byte[] cUserName = new byte[64];
        public byte[] cUserPwd = new byte[64];
        public byte[] cCharSet = new byte[16];
        
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("iSize", "cNvsIP", "iNvsPort", "cUserName", "cUserPwd", "cCharSet");
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
