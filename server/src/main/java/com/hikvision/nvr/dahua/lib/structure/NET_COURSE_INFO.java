package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

import static com.hikvision.nvr.dahua.lib.NetSDKLib.NET_COMMON_STRING_128;
import static com.hikvision.nvr.dahua.lib.NetSDKLib.NET_COMMON_STRING_64;

/**
 * 课程信息
 *
 * @author ： 47040
 * @since ： Created in 2020/9/28 18:39
 */
public class NET_COURSE_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 课程名称
     */
    public byte[]           szCourseName = new byte[NET_COMMON_STRING_64];
    /**
     * 教师姓名
     */
    public byte[]           szTeacherName = new byte[NET_COMMON_STRING_64];
    /**
     * 视频简介
     */
    public byte[]           szIntroduction = new byte[NET_COMMON_STRING_128];
    /**
     * 保留字节
     */
    public byte[]           byReserved = new byte[64];
}

