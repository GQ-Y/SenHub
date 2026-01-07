package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 291189
 * @description 云台三维定位, 对应NET_EXTPTZ_MOVE_DIRECTLY枚举
 * @origin autoTool
 * @date 2023/03/07 16:38:49
 */
public class NET_IN_MOVE_DIRECTLY_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 结构体大小
     */
    public int              dwSize;
    /**
     * 屏幕坐标
     */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.PTZ_SPEED_UNIT stuScreen = new com.hikvision.nvr.dahua.lib.NetSDKLib.PTZ_SPEED_UNIT();
    /**
     * 云台运行速度
     */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.PTZ_SPEED_UNIT stuSpeed = new com.hikvision.nvr.dahua.lib.NetSDKLib.PTZ_SPEED_UNIT();

    public NET_IN_MOVE_DIRECTLY_INFO() {
        this.dwSize = this.size();
    }
}

