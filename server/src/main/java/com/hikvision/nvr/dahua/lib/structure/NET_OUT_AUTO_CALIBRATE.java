package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_AutoCalibrate 接口输出参数
*/
public class NET_OUT_AUTO_CALIBRATE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_OUT_AUTO_CALIBRATE() {
        this.dwSize = this.size();
    }
}

