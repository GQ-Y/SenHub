package com.digital.video.gateway.dahua.lib.structure;


import com.digital.video.gateway.dahua.lib.NetSDKLib;

/** 
本地服务网络信息
*/
public class NET_LOCAL_SERVER_NET_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
/** 
/< 本地服务IP
*/
    public			byte[]         szLocalIp = new byte[64];
/** 
/< 本地服务端口
*/
    public			int            nLocalPort;
/** 
/< 保留字节
*/
    public			byte[]         szReserved = new byte[516];
}

