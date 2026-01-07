package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description 大图信息(大图抠小图功能)
 * @date 2022/11/21 17:53:12
 */
public class NET_DETECT_BIG_PIC_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 大图ID
     */
    public int              nPicID;
    /**
     * 文件在二进制数据块中的偏移位置, 单位:字节
     */
    public int              dwOffSet;
    /**
     * 文件大小, 单位:字节
     */
    public int              dwFileLenth;
    /**
     * 图片宽度, 单位:像素
     */
    public int              dwWidth;
    /**
     * 图片高度, 单位:像素
     */
    public int              dwHeight;
    /**
     * 规则检测区域顶点数
     */
    public int              nDetectRegionNum;
    /**
     * 规则检测区域
     */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT[] stuDetectRegion = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT[20];
    /**
     * 保留字节
     */
    public byte[]           bReserved = new byte[44];

    public NET_DETECT_BIG_PIC_INFO() {
        for (int i = 0; i < stuDetectRegion.length; i++) {
            stuDetectRegion[i] = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT();
        }
    }
}

