package com.digital.video.gateway.dahua.lib.enumeration;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 渗漏状态
*/
public enum EM_LEAKAGE_STATE
{
    /**
     * 未知
    */
    EM_LEAKAGE_STATE_UNKNOWN(0, "未知"),
    /**
     * 地面油污
    */
    EM_LEAKAGE_STATE_GROUNDOIL(1, "地面油污"),
    /**
     * 部件表面油污
    */
    EM_LEAKAGE_STATE_INTERFACEOIL(2, "部件表面油污");

    private int value;
    private String note;

    public String getNote() {
        return note;
    }

    public int getValue() {
        return value;
    }

    EM_LEAKAGE_STATE(int givenValue, String note) {
        this.value = givenValue;
        this.note = note;
    }

    public static String getNoteByValue(int givenValue) {
        for (EM_LEAKAGE_STATE enumType : EM_LEAKAGE_STATE.values()) {
            if (givenValue == enumType.getValue()) {
                return enumType.getNote();
            }
        }
        return null;
    }

    public static int getValueByNote(String givenNote) {
        for (EM_LEAKAGE_STATE enumType : EM_LEAKAGE_STATE.values()) {
            if (givenNote.equals(enumType.getNote())) {
                return enumType.getValue();
            }
        }
        return -1;
    }
}

