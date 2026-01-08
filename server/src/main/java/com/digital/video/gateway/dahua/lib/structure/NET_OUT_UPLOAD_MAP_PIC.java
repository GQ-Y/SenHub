package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_UploadMapPic 接口输出参数
*/
public class NET_OUT_UPLOAD_MAP_PIC extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 错误码, -1:未知，0: 上传成功, 1: 上传失败, 2: 图片格式不支持
    */
    public int              nErrorCode;
    /**
     * 错误描述
    */
    public byte[]           szErrorInfo = new byte[128];

    public NET_OUT_UPLOAD_MAP_PIC() {
        this.dwSize = this.size();
    }
}

