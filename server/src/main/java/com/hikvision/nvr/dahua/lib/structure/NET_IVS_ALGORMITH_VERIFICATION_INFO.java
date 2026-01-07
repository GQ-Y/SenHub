package com.hikvision.nvr.dahua.lib.structure;
import com.sun.jna.Pointer;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * IVS算法验证信息
*/
public class NET_IVS_ALGORMITH_VERIFICATION_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 负样本内容描述列表
    */
    public BYTE_ARRAY_512[] szNegativeContent = new BYTE_ARRAY_512[8];
    /**
     * 正样本内容描述
    */
    public byte[]           szAlarmContent = new byte[512];
    /**
     * 归一化阈值
    */
    public int              nAuxSimilarity;
    /**
     * 相似度阈值
    */
    public int              nSimilarity;
    /**
     * 负样本内容描述列表个数
    */
    public int              nNegativeContentNum;
    /**
     * 是否使用样本内容扩展字段
    */
    public int              bContentDescriptionExFlage;
    /**
     * 样本内容描述扩展字段,参见结构体定义 {@link com.netsdk.lib.structure.NET_IVS_ALGORMITH_CONTENT_DESCRIPTION_EX}
    */
    public Pointer          pContentDescriptionEx;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[248-com.hikvision.nvr.dahua.lib.NetSDKLib.POINTERSIZE];

    public NET_IVS_ALGORMITH_VERIFICATION_INFO() {
        for(int i = 0; i < szNegativeContent.length; i++){
            szNegativeContent[i] = new BYTE_ARRAY_512();
        }
    }
}

