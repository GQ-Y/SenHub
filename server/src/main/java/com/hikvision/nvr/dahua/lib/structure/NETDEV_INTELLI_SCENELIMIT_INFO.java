package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 场景边界限位信息
*/
public class NETDEV_INTELLI_SCENELIMIT_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 场景编号
    */
    public int              nScene;
    /**
     * 0:上边界限位,1:下边界限位,2:左边界,3:右边界
    */
    public int              nType;

    public NETDEV_INTELLI_SCENELIMIT_INFO() {
        this.dwSize = this.size();
    }
}

