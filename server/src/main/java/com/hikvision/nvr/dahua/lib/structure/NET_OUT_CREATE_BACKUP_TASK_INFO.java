package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_CreateBackupTask接口输出参数
*/
public class NET_OUT_CREATE_BACKUP_TASK_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 创建的实例ID
    */
    public int              nInstanceID;

    public NET_OUT_CREATE_BACKUP_TASK_INFO() {
        this.dwSize = this.size();
    }
}

