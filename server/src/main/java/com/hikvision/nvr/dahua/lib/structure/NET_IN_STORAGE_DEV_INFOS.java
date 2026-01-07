package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.hikvision.nvr.dahua.lib.enumeration.NET_VOLUME_TYPE;

/**
 * CLIENT_QueryDevInfo , NET_QUERY_DEV_STORAGE_INFOS接口输入参数
 * @author 29779
 */
public class NET_IN_STORAGE_DEV_INFOS extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;
	/**
	 * 要获取的卷类型
	 * {@link NET_VOLUME_TYPE }
	 */
    public int              emVolumeType;

	public NET_IN_STORAGE_DEV_INFOS() {
		this.dwSize = this.size();
	}
}

