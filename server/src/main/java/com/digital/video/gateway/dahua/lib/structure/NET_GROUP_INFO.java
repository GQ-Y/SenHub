package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 盘组信息
*/
public class NET_GROUP_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 盘组名称
    */
    public byte[]           szGroup = new byte[32];
    /**
     * 剩余容量, 单位字节
    */
    public long             nFreeSpace;
    /**
     * 总容量，单位字节
    */
    public long             nTotalSpace;
    /**
     * 盘组中设备个数
    */
    public int              nDeviceNum;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[256];

    public NET_GROUP_INFO() {
    }
}

