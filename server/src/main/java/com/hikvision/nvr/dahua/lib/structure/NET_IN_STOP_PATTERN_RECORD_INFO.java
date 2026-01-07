package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 停止模式记录 对应DH_EXTPTZ_STOP_PATTERN_RECORD枚举
*/
public class NET_IN_STOP_PATTERN_RECORD_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 模式编号
    */
    public int              nIndex;

    public NET_IN_STOP_PATTERN_RECORD_INFO() {
        this.dwSize = this.size();
    }
}

