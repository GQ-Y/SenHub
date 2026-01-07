package com.hikvision.nvr.dahua.lib.enumeration;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 金属锈蚀状态
*/
public enum EM_METAL_CORROSION_STATE
{
    /**
     * 未知
    */
    EM_METAL_CORROSION_STATE_UNKNOWN(0, "未知"),
    /**
     * 导电接头锈蚀
    */
    EM_METAL_CORROSION_STATE_CONDUCTIVE_JOINT_RUSTY(1, "导电接头锈蚀"),
    /**
     * 导电引线锈蚀
    */
    EM_METAL_CORROSION_STATE_CONDUCTIVE_LEAD_RUSTY(2, "导电引线锈蚀"),
    /**
     * 接地引下线锈蚀
    */
    EM_METAL_CORROSION_STATE_GROUND_LEAD_RUSTY(3, "接地引下线锈蚀"),
    /**
     * 二次接线盒锈蚀
    */
    EM_METAL_CORROSION_STATE_SEC_JUNCTION_BOX(4, "二次接线盒锈蚀");

    private int value;
    private String note;

    public String getNote() {
        return note;
    }

    public int getValue() {
        return value;
    }

    EM_METAL_CORROSION_STATE(int givenValue, String note) {
        this.value = givenValue;
        this.note = note;
    }

    public static String getNoteByValue(int givenValue) {
        for (EM_METAL_CORROSION_STATE enumType : EM_METAL_CORROSION_STATE.values()) {
            if (givenValue == enumType.getValue()) {
                return enumType.getNote();
            }
        }
        return null;
    }

    public static int getValueByNote(String givenNote) {
        for (EM_METAL_CORROSION_STATE enumType : EM_METAL_CORROSION_STATE.values()) {
            if (givenNote.equals(enumType.getNote())) {
                return enumType.getValue();
            }
        }
        return -1;
    }
}

