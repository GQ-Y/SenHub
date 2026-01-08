package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 主驾驶人脸图片信息
*/
public class NET_DRIVER_FACE_IMAGE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 图片文件大小，单位:字节
    */
    public int              nLength;
    /**
     * 图片偏移字节数
    */
    public int              nOffset;
    /**
     * 预留字段
    */
    public byte[]           szReserved = new byte[256];

    public NET_DRIVER_FACE_IMAGE_INFO() {
    }
}

