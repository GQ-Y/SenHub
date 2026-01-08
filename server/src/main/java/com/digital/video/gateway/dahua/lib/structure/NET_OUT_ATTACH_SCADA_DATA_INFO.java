package com.digital.video.gateway.dahua.lib.structure;


import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description CLIENT_AttachSCADAData 接口出参
 * @date 2022/12/13 10:20:50
 */
public class NET_OUT_ATTACH_SCADA_DATA_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 此结构体大小,必须赋值
     */
    public int              dwSize;

    public NET_OUT_ATTACH_SCADA_DATA_INFO() {
        this.dwSize = this.size();
    }
}

