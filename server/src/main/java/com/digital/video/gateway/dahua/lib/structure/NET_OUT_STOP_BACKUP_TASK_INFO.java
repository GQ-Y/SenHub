package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_StopBackupTask接口输出参数
*/
public class NET_OUT_STOP_BACKUP_TASK_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_OUT_STOP_BACKUP_TASK_INFO() {
        this.dwSize = this.size();
    }
}

