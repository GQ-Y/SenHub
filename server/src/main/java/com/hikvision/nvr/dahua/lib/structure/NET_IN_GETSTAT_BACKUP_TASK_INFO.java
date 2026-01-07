package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_GetStateBackupTask接口输入参数
*/
public class NET_IN_GETSTAT_BACKUP_TASK_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 创建的实例ID,CLIENT_CreateBackupTask的出参
    */
    public int              nInstanceID;

    public NET_IN_GETSTAT_BACKUP_TASK_INFO() {
        this.dwSize = this.size();
    }
}

