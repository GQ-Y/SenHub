package com.digital.video.gateway.driver.livox.model;

/**
 * 目标类型枚举（基于点云聚类几何特征分类）
 */
public enum TargetType {
    PERSON("person", "人"),
    ANIMAL("animal", "动物"),
    VEHICLE("vehicle", "车"),
    OTHER("other", "其他");

    private final String code;
    private final String label;

    TargetType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static TargetType fromCode(String code) {
        if (code == null) return OTHER;
        for (TargetType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        return OTHER;
    }
}
