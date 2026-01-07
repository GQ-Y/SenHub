package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * 开启/关闭指定通道录像出参 {@link NetSDKLib#CLIENT_SetCourseRecordState}
 *
 * @author ： 47040
 * @since ： Created in 2020/9/28 16:16
 */
public class NET_OUT_SET_COURSE_RECORD_STATE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 该结构体大小
     */
    public int              dwSize;

    public NET_OUT_SET_COURSE_RECORD_STATE() {
        dwSize = this.size();
    }
}

