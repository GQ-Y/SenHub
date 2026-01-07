package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_ModifyFloorHeight 接口输出参数
*/
public class NET_OUT_MODIFY_FLOOR_HEIGHT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_OUT_MODIFY_FLOOR_HEIGHT() {
        this.dwSize = this.size();
    }
}

