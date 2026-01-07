package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;

import static com.hikvision.nvr.dahua.lib.NetSDKLib.MAX_INSIDEOBJECT_NUM;


/**
 * X光机关联图片类型
 *
 * @author ： 260611
 * @since ： Created in 2021/10/19 14:46
 */
public class DEV_EVENT_XRAY_DETECTION_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 通道号
     */
    public int              nChannelID;
    /**
     * 事件名称
     */
    public byte             szName[] = new byte[128];
    /**
     * 保留字节对齐
     */
    public byte             Reserved[] = new byte[4];
    /**
     * 时间戳(单位是毫秒)
     */
    public double           PTS;
    /**
     * 事件发生的时间
     */
    public NET_TIME_EX      UTC = new NET_TIME_EX();
    /**
     * 事件ID
     */
    public int              nEventID;
    /**
     * 所属大类 {@link com.netsdk.lib.enumeration.EM_CLASS_TYPE}
     */
    public int              emClassType;
    /**
     * 包裹信息
     */
    public NET_PACKAGE_INFO stuPacketInfo = new NET_PACKAGE_INFO();
    /**
     * 保留字节对齐
     */
    public byte             Reserved1[] = new byte[4];
    /**
     * 主视角包裹内物品个数
     */
    public int              nObjectNum;
    /**
     * 主视角包裹内物品信息
     */
    public NET_INSIDE_OBJECT stuInsideObj[] = (NET_INSIDE_OBJECT[]) new NET_INSIDE_OBJECT().toArray(MAX_INSIDEOBJECT_NUM);
    /**
     * 从视角包裹内物品个数
     */
    public int              nSlaveViewObjectNum;
    /**
     * 从视角包裹内物品信息
     */
    public NET_INSIDE_OBJECT stuSlaveViewInsideObj[] = (NET_INSIDE_OBJECT[]) new NET_INSIDE_OBJECT().toArray(MAX_INSIDEOBJECT_NUM);
    /**
     * 图片数量
     */
    public int              nImageCount;
    /**
     * 图片信息
     */
    public NET_XRAY_IMAGE_INFO stuImageInfo[] = (NET_XRAY_IMAGE_INFO[]) new NET_XRAY_IMAGE_INFO().toArray(8);
    /**
     * 客户自定义信息个数
     */
    public int              nViewCustomInfoNum;
    /**
     * 客户自定义信息, X光机专用
     */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_XRAY_CUSTOM_INFO stuViewCustomInfo[] = (com.hikvision.nvr.dahua.lib.NetSDKLib.NET_XRAY_CUSTOM_INFO[]) new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_XRAY_CUSTOM_INFO().toArray(4);
    /**
     * 包裹标识, 用来唯一标识一个包裹
     */
    public byte             szPackageTag[] = new byte[32];
    /**
     * 包裹产生方式 {@link com.netsdk.lib.enumeration.EM_XRAY_PACKAGE_MODE}
     */
    public int              emPackageMode;
    /**
     * 关联图片数量
     */
    public int              nRelatedImageNum;
    /**
     * 关联图片
     */
    public NET_XRAY_RELATED_IMAGE_INFO stuRelatedImageInfo[] = new NET_XRAY_RELATED_IMAGE_INFO[8];
    /**
     * 与包裹关联的单号的个数
     */
    public int              nBarCodeCount;
    /**
     * 与包裹关联的单号的内容
     */
    public NET_BAR_CODE_INFO stuBarCodeInfo[] = new NET_BAR_CODE_INFO[32];
    /**
     * 事件公共扩展字段结构体
     */
    public NET_EVENT_INFO_EXTEND stuEventInfoEx = new NET_EVENT_INFO_EXTEND();
    /**
     * 是否使用远程判图 {@link com.netsdk.lib.enumeration.EM_XRAY_DETECTION_JUDGE_REMOTELY}
     */
    public int              emJudgeRemotely;
    /**
     * / 图片信息个数
     */
    public int              nImageInfoNum;
    /**
     * / 图片信息数组, refer to {@link NET_IMAGE_INFO_EX3}
     */
    public Pointer          pstuImageInfo;
    /**
     * / 切包信息
     */
    public NET_XRAY_CUT_INFO stuCutInfo = new NET_XRAY_CUT_INFO();
    /**
     * 关联通道,参见结构体定义 {@link com.netsdk.lib.structure.NET_RELATED_CHANNEL_INFO}
    */
    public NET_RELATED_CHANNEL_INFO stuRelatedChannel = new NET_RELATED_CHANNEL_INFO();
    /**
     * 主视角包裹内物品个数
    */
    public int              nInsideCount;
    /**
     * 从视角包裹内物品个数
    */
    public int              nSlaveViewInsideCount;
    /**
     * 主视角包裹内物品,参见结构体定义 {@link com.netsdk.lib.structure.NET_INSIDE_OBJECT}
    */
    public Pointer          pstuInside;
    /**
     * 从视角包裹内物品,参见结构体定义 {@link com.netsdk.lib.structure.NET_INSIDE_OBJECT}
    */
    public Pointer          pstuSlaveViewInside;
    /**
     * 运单信息,参见结构体定义 {@link com.netsdk.lib.structure.NET_WAYBILL_INFO}
    */
    public Pointer          pstuWaybillInfo;
    /**
     * 运单信息个数
    */
    public int              nWaybillInfoCount;
    /**
     * 包裹在带白边背景中的位置，使用 8192坐标系
    */
    public SHORT_ARRAY_4[]  nPosition = new SHORT_ARRAY_4[2];
    /**
     * 带白边背景中的位置数组实际数量
    */
    public int              nPositionNum;
    /**
     * 包裹id
    */
    public int              nPackageID;
    /**
     * 下发开包请求的客户端
    */
    public byte[]           szUnpackingClientType = new byte[32];
    /**
     * 保留字节,留待扩展
     */
    public byte             byReserved[] = new byte[192-com.hikvision.nvr.dahua.lib.NetSDKLib.POINTERSIZE*4];

    public DEV_EVENT_XRAY_DETECTION_INFO() {
        for (int i = 0; i < stuRelatedImageInfo.length; i++) {
            stuRelatedImageInfo[i] = new NET_XRAY_RELATED_IMAGE_INFO();
        }

        for (int i = 0; i < stuBarCodeInfo.length; i++) {
            stuBarCodeInfo[i] = new NET_BAR_CODE_INFO();
        }
    }
}

