package com.digital.video.gateway.database;

/**
 * 国标 GB/T 28181 设备编码生成器
 * 20 位十进制：中心编码(8) + 行业编码(2) + 类型编码(3) + 网络标识(1) + 序号(6)
 */
public class Gb28181IdGenerator {
    /** 中心编码默认 8 位（行政区划，示例） */
    public static final String DEFAULT_CENTER = "34017101";
    /** 行业编码默认 2 位，04=社会治安社区接入 */
    public static final String DEFAULT_INDUSTRY = "04";
    /** 类型编码默认 3 位，132=摄像机 */
    public static final String DEFAULT_TYPE = "132";
    /** 网络标识默认 1 位，0=监控专网 */
    public static final String DEFAULT_NETWORK = "0";

    private final String prefix; // 14 位：center(8)+industry(2)+type(3)+network(1)

    public Gb28181IdGenerator(String center, String industry, String type, String network) {
        String c = padRight(center != null ? center : DEFAULT_CENTER, 8, '0');
        String i = padRight(industry != null ? industry : DEFAULT_INDUSTRY, 2, '0');
        String t = padRight(type != null ? type : DEFAULT_TYPE, 3, '0');
        String n = (network != null && !network.isEmpty()) ? String.valueOf(network.charAt(0)) : DEFAULT_NETWORK;
        this.prefix = c + i + t + n;
        if (this.prefix.length() != 14) {
            throw new IllegalArgumentException("GB28181 prefix must be 14 digits, got: " + this.prefix);
        }
    }

    public Gb28181IdGenerator() {
        this(DEFAULT_CENTER, DEFAULT_INDUSTRY, DEFAULT_TYPE, DEFAULT_NETWORK);
    }

    /**
     * 生成 20 位国标 ID：前缀(14) + 序号(6)
     */
    public String generate(long sequence) {
        String seq = String.format("%06d", sequence % 1_000_000);
        return prefix + seq;
    }

    private static String padRight(String s, int len, char pad) {
        if (s == null) return repeat(pad, len);
        if (s.length() >= len) return s.substring(0, len);
        return s + repeat(pad, len - s.length());
    }

    private static String repeat(char c, int n) {
        if (n <= 0) return "";
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    /** 校验是否为 20 位数字（国标 ID 格式） */
    public static boolean isGb28181Format(String deviceId) {
        if (deviceId == null || deviceId.length() != 20) return false;
        for (int i = 0; i < 20; i++) {
            if (!Character.isDigit(deviceId.charAt(i))) return false;
        }
        return true;
    }
}
