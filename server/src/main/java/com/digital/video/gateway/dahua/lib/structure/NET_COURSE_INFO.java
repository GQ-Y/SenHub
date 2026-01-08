package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

import static com.digital.video.gateway.dahua.lib.NetSDKLib.NET_COMMON_STRING_128;
import static com.digital.video.gateway.dahua.lib.NetSDKLib.NET_COMMON_STRING_64;

/**
 * 课程信息
 *
 * @author ： 47040
 * @since ： Created in 2020/9/28 18:39
 */
public class NET_COURSE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
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

