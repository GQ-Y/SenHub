package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 47081
 * @version 1.0
 * @description 空结构体, 代替一些只有长度的出参结构体
 * @date 2020/9/21
 */
public class EmptyStructure extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 结构体大小
     */
    public int              dwSize;

    public EmptyStructure() {
        this.dwSize = size();
    }
}

