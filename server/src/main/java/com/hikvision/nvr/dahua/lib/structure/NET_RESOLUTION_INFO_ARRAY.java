package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

// CFG_MAX_CAPTURE_SIZE_NUM = 32

/**
 * 支持的视频分辨率细节
 * 是 {@link NET_STREAM_CFG_CAPS#stuIndivResolutionTypes} 的第二维拆分数组
 *
 * @author 47040
 * @since Created at 2021/5/26 9:12
 */
public class NET_RESOLUTION_INFO_ARRAY extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 支持的视频分辨率
     * 有效长度由 {@link NET_STREAM_CFG_CAPS#nIndivResolutionNums} 决定
     * 其下标与 {@link NET_STREAM_CFG_CAPS#stuIndivResolutionTypes} 第一维数组精确匹配
     */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_RESOLUTION_INFO[] stuIndivResolutions = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_RESOLUTION_INFO[32]; // CFG_MAX_CAPTURE_SIZE_NUM

    public NET_RESOLUTION_INFO_ARRAY() {
        for (int i = 0; i < stuIndivResolutions.length; i++) {
            stuIndivResolutions[i] = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_RESOLUTION_INFO();
        }
    }
}

