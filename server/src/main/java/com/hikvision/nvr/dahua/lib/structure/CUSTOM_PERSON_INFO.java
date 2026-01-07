package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

import static com.hikvision.nvr.dahua.lib.NetSDKLib.DH_MAX_PERSON_INFO_LEN;

/**
 * className：CUSTOM_PERSON_INFO
 * description：
 * author：251589
 * createTime：2020/12/28 11:08
 *
 * @version v1.0
 */
public class CUSTOM_PERSON_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public byte[]           szPersonInfo = new byte[DH_MAX_PERSON_INFO_LEN]; //人员扩展信息
    public byte[]           byReserved = new byte[124];           // 保留字节
}

