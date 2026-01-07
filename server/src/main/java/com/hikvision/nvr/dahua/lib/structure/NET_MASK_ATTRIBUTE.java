package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.hikvision.nvr.dahua.lib.enumeration.EM_COMPLIANCE_STATE;
import com.hikvision.nvr.dahua.lib.enumeration.EM_WEARING_STATE;

/**
 * @author ： 260611
 * @description ： 口罩相关属性状态信息
 * @since ： Created in 2022/03/10 11:17
 */

public class NET_MASK_ATTRIBUTE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 是否有戴口罩,{@link EM_WEARING_STATE}
     */
    public int              emHasMask;
    /**
     * 口罩检测结果,{@link EM_COMPLIANCE_STATE}
     */
    public int              emHasLegalMask;
}

