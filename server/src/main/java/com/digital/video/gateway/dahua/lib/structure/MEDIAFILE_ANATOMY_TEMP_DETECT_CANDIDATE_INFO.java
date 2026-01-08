package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * @author 251823
 * @description 测温查询候选人信息
 * @date 2021/02/22
 */
public class MEDIAFILE_ANATOMY_TEMP_DETECT_CANDIDATE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	 /**
     *  相似度，值越大，越相似
     */
    public int              nSimilarity;
    /**
     *  人信息 
     */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.FACERECOGNITION_PERSON_INFOEX stuPersonInfo;
    /**
     *  预留字段
     */
    public byte[]           byReserved = new byte[2048];
}

