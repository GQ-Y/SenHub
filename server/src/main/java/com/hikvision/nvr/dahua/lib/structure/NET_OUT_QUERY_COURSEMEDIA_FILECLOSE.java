package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * 关闭课程视频查询出参 {@link NetSDKLib#CLIENT_CloseQueryCourseMediaFile}
 *
 * @author ： 47040
 * @since ： Created in 2020/9/28 19:06
 */
public class NET_OUT_QUERY_COURSEMEDIA_FILECLOSE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 该结构体大小
     */
    public int              dwSize;

    public NET_OUT_QUERY_COURSEMEDIA_FILECLOSE() {
        dwSize = this.size();
    }
}

