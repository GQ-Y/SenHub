package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 47081
 * @version 1.0
 * @description
 * @date 2020/6/9
 */
public class NET_CFG_AUDIO_PLAY_PATH extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 文件路径
     */
    public byte[]           szPath = new byte[256];
    /**
     * 是否支持上传
     */
    public int              bSupportUpload;
    /**
     * 支持最大上传个数, 支持上传才有效
     */
    public int              nMaxFileUploadNum;
    /**
     * 最大上传文件大小，单位字节, 支持上传才有效
     */
    public int              nMaxUploadFileSize;
}

