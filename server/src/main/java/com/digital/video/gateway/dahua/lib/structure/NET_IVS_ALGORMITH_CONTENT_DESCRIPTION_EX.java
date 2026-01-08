package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * IVS算法样本内容描述字段扩展
*/
public class NET_IVS_ALGORMITH_CONTENT_DESCRIPTION_EX extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 负样本内容描述列表
    */
    public BYTE_ARRAY_2048[] szNegativeContentEx = new BYTE_ARRAY_2048[8];
    /**
     * 正样本内容描述
    */
    public byte[]           szAlarmContentEx = new byte[2048];
    /**
     * 保留字节
    */
    public byte[]           szReservedEx = new byte[2048];

    public NET_IVS_ALGORMITH_CONTENT_DESCRIPTION_EX() {
        for(int i = 0; i < szNegativeContentEx.length; i++){
            szNegativeContentEx[i] = new BYTE_ARRAY_2048();
        }
    }
}

