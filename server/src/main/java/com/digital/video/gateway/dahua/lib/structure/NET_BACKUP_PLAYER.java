package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 播放器目标文件备份目录
*/
public class NET_BACKUP_PLAYER extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 播放器备份的路径
    */
    public byte[]           szPath = new byte[256];
    /**
     * 保留字节
    */
    public byte[]           byReserved = new byte[1024];

    public NET_BACKUP_PLAYER() {
    }
}

