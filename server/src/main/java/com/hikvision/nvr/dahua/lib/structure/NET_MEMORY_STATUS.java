package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * className：NET_MEMORY_STATUS
 * description：
 * author：251589
 * createTime：2021/2/25 13:36
 *
 * @version v1.0
 */

public class NET_MEMORY_STATUS extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * dwSize;
     */
    public int              dwSize;
    /**
     *  查询是否成功
     */
    public int              bEnable;
    /**
     *  内存信息
     */
    public NET_MEMORY_INFO  stuMemory;

    public NET_MEMORY_STATUS (){
        this.dwSize = this.size();
    }
}

