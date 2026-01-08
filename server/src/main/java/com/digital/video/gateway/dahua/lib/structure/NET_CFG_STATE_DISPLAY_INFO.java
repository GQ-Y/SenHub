package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 状态展示配置, 对应枚举 NET_EM_CFG_STATE_DISPLAY
*/
public class NET_CFG_STATE_DISPLAY_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小, 必须赋值
    */
    public int              dwSize;
    /**
     * 0：在岗；1：离岗；2: 勿扰, -1:未知
    */
    public int              nState;
    /**
     * 图片路径
    */
    public byte[]           szBgName = new byte[128];
    /**
     * 门牌名称
    */
    public byte[]           szDoorStateName = new byte[128];
    /**
     * 状态文本数组第一维有效个数
    */
    public int              nStateTextNum;
    /**
     * 子状态文本数组第一维有效个数
    */
    public int              nSubStateTextNum;
    /**
     * 状态文本，与state配合使用，如当state为0时，["在岗"]字符串就是状态文本
    */
    public BYTE_ARRAY_128[] szStateText = new BYTE_ARRAY_128[10];
    /**
     * 子状态文本，与state配合使用，对状态的进一步说明，如state是2，["休息中，勿扰"]就是子状态文本
    */
    public BYTE_ARRAY_128[] szSubStateText = new BYTE_ARRAY_128[10];
    /**
     * 门牌状态使能
    */
    public int              bDoorplateEnable;

    public NET_CFG_STATE_DISPLAY_INFO() {
        this.dwSize = this.size();
        for(int i = 0; i < szStateText.length; i++){
            szStateText[i] = new BYTE_ARRAY_128();
        }
        for(int i = 0; i < szSubStateText.length; i++){
            szSubStateText[i] = new BYTE_ARRAY_128();
        }
    }
}

