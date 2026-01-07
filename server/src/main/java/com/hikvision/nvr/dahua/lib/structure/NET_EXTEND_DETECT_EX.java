package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
public class NET_EXTEND_DETECT_EX extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 渗漏检测结果扩展数据,有效个数同nLeakageDetectNum,参见结构体定义 {@link com.netsdk.lib.structure.NET_LEAKAGE_DETECT_EX}
    */
    public NET_LEAKAGE_DETECT_EX[] stuLeakageDetectInfoEx = new NET_LEAKAGE_DETECT_EX[8];
    /**
     * 呼吸器检测结果扩展数据,有效个数同nRespiratorDetectNum,参见结构体定义 {@link com.netsdk.lib.structure.NET_RESPIRATOR_DETECT_EX}
    */
    public NET_RESPIRATOR_DETECT_EX[] stuRespiratorDetectInfoEx = new NET_RESPIRATOR_DETECT_EX[8];
    /**
     * 绝缘子检测结果,有效个数同nInsulatorDetectNum,参见结构体定义 {@link com.netsdk.lib.structure.NET_INSULATOR_DETECT_EX}
    */
    public NET_INSULATOR_DETECT_EX[] stuInsulatorDetectInfoEx = new NET_INSULATOR_DETECT_EX[8];
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[1024];

    public NET_EXTEND_DETECT_EX() {
        for(int i = 0; i < stuLeakageDetectInfoEx.length; i++){
            stuLeakageDetectInfoEx[i] = new NET_LEAKAGE_DETECT_EX();
        }
        for(int i = 0; i < stuRespiratorDetectInfoEx.length; i++){
            stuRespiratorDetectInfoEx[i] = new NET_RESPIRATOR_DETECT_EX();
        }
        for(int i = 0; i < stuInsulatorDetectInfoEx.length; i++){
            stuInsulatorDetectInfoEx[i] = new NET_INSULATOR_DETECT_EX();
        }
    }
}

