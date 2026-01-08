package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * 设置录像模式出参，对应接口{@link NetSDKLib#CLIENT_SetCourseRecordMode }
 *
 * @author ： 47040
 * @since ： Created in 2020/9/28 15:44
 */
public class NET_OUT_SET_COURSE_RECORD_MODE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;

    public NET_OUT_SET_COURSE_RECORD_MODE() {
        dwSize = this.size();
    }
}

