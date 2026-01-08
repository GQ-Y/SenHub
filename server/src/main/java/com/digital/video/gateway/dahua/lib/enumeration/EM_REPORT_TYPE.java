package com.digital.video.gateway.dahua.lib.enumeration;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 事件的上报类型
*/
public enum EM_REPORT_TYPE
{
    /**
     * 未知
    */
    EM_REPORT_TYPE_UNKNOWN(0, "未知"),
    /**
     * 定时上报
    */
    EM_REPORT_TYPE_TIMING(1, "定时上报"),
    /**
     * 报警上报
    */
    EM_REPORT_TYPE_ALARM(2, "报警上报");

    private int value;
    private String note;

    public String getNote() {
        return note;
    }

    public int getValue() {
        return value;
    }

    EM_REPORT_TYPE(int givenValue, String note) {
        this.value = givenValue;
        this.note = note;
    }

    public static String getNoteByValue(int givenValue) {
        for (EM_REPORT_TYPE enumType : EM_REPORT_TYPE.values()) {
            if (givenValue == enumType.getValue()) {
                return enumType.getNote();
            }
        }
        return null;
    }

    public static int getValueByNote(String givenNote) {
        for (EM_REPORT_TYPE enumType : EM_REPORT_TYPE.values()) {
            if (givenNote.equals(enumType.getNote())) {
                return enumType.getValue();
            }
        }
        return -1;
    }
}

