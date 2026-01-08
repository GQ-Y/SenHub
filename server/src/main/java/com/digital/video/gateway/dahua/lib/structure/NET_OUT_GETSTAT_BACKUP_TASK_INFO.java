package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_GetStateBackupTask接口输出参数
*/
public class NET_OUT_GETSTAT_BACKUP_TASK_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * "Created": 任务创建、"Ready": 就绪、"Running": 备份中、"Finished": 备份结束、"Error": 备份错误、"Aborted": 意外中止、"Pause": 任务暂停、"ChangeDisk":备份换盘中
    */
    public byte[]           szStatus = new byte[16];

    public NET_OUT_GETSTAT_BACKUP_TASK_INFO() {
        this.dwSize = this.size();
    }
}

