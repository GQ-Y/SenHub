package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_BackupManagerGetTaskInfo 接口输入参数
*/
public class NET_IN_BACKUP_MANAGER_GET_TASK_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小，必须赋值
    */
    public int              dwSize;
    /**
     * 备份任务组id, 0表示所有
    */
    public int              nGroup;

    public NET_IN_BACKUP_MANAGER_GET_TASK_INFO() {
        this.dwSize = this.size();
    }
}

