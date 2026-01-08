package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.digital.video.gateway.dahua.lib.enumeration.NET_EM_RECORD_MODE;

/**
 * 获取录像模式出参，对应接口{@link NetSDKLib#CLIENT_GetCourseRecordMode}
 *
 * @author ： 47040
 * @since ： Created in 2020/9/28 15:50
 */
public class NET_OUT_GET_COURSE_RECORD_MODE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 结构体大小
     */
    public int              dwSize;
    /**
     * 录像模式{@link NET_EM_RECORD_MODE}
     */
    public int              emRecordMode;

    public NET_OUT_GET_COURSE_RECORD_MODE() {
        dwSize = this.size();
    }
}

