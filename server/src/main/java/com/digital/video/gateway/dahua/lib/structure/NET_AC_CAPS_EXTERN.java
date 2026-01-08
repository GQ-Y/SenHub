package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * ACCaps能力集扩展
*/
public class NET_AC_CAPS_EXTERN extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 支持互锁回能力掩码，支持位或 bit0-本地反潜;bit1-网络反潜主控;bit2-网络反潜分控
    */
    public int              bSupportCollectUserByDev;
    /**
     * 设备中可用于提取信息的可用读头能力信息数量
    */
    public int              nDevCollectAttributeNum;
    /**
     * 设备中可用于提取信息的可用读头能力信息,参见结构体定义 {@link com.netsdk.lib.structure.NET_DEV_COLLECT_ATTRIBUTE}
    */
    public NET_DEV_COLLECT_ATTRIBUTE[] stuDevCollectAttribute = new NET_DEV_COLLECT_ATTRIBUTE[16];
    /**
     * 门禁用户是否支持仅考勤模式功能
    */
    public int              bSupportAttendanceOnly;
    /**
     * 门禁设备是否支持个人验证方式配置
    */
    public int              bSupportPersonalVerification;
    /**
     * 支持多人开门设置组合验证方式
    */
    public int              bSupportMultiPeopleCombiMethod;
    /**
     * 是否支持蓝牙卡鉴权方式
    */
    public int              bHasBTCardAuth;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[1008];

    public NET_AC_CAPS_EXTERN() {
        for(int i = 0; i < stuDevCollectAttribute.length; i++){
            stuDevCollectAttribute[i] = new NET_DEV_COLLECT_ATTRIBUTE();
        }
    }
}

