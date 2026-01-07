package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description CLIENT_FaceRecognitionDetectMultiFace 接口输出参数
 * @date 2022/11/21 17:53:13
 */
public class NET_OUT_FACE_RECOGNITION_DETECT_MULTI_FACE_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 此结构体的大小
     */
    public int              dwSize;

    public NET_OUT_FACE_RECOGNITION_DETECT_MULTI_FACE_INFO() {
        this.dwSize = this.size();
    }
}

