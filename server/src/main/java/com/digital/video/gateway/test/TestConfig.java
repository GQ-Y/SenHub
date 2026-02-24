package com.digital.video.gateway.test;

import com.digital.video.gateway.database.DeviceInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用设备配置
 * 用于在服务器上执行核心功能测试（摄像头连接、抓拍、录像、云台等）
 */
public final class TestConfig {

    /** 测试账号 */
    public static final String USERNAME = "admin";
    /** 测试密码 */
    public static final String PASSWORD = "zyckj2021";

    /** 天地伟业 1 - IP、端口、品牌（天地伟业 SDK 端口为 3000） */
    public static final String TIANDY_IP = "192.168.1.10";
    public static final int TIANDY_PORT = 3000;
    public static final String TIANDY_BRAND = DeviceInfo.BRAND_TIANDY;

    /** 天地伟业 2（另一台） */
    public static final String TIANDY_IP_2 = "192.168.1.200";
    public static final int TIANDY_PORT_2 = 3000;

    /** 海康威视 */
    public static final String HIKVISION_IP = "192.168.1.100";
    public static final int HIKVISION_PORT = 8000;
    public static final String HIKVISION_BRAND = DeviceInfo.BRAND_HIKVISION;

    /** 大华暂无摄像头，暂不参与测试 */

    /**
     * 构建天地伟业测试设备（192.168.1.10）
     */
    public static DeviceInfo tiandyDevice() {
        DeviceInfo d = new DeviceInfo();
        d.setDeviceId(TIANDY_IP);
        d.setIp(TIANDY_IP);
        d.setPort(TIANDY_PORT);
        d.setUsername(USERNAME);
        d.setPassword(PASSWORD);
        d.setBrand(TIANDY_BRAND);
        d.setChannel(1);
        d.setName("测试-天地伟业-10");
        return d;
    }

    /**
     * 构建天地伟业测试设备（192.168.1.200）
     */
    public static DeviceInfo tiandyDevice2() {
        DeviceInfo d = new DeviceInfo();
        d.setDeviceId(TIANDY_IP_2);
        d.setIp(TIANDY_IP_2);
        d.setPort(TIANDY_PORT_2);
        d.setUsername(USERNAME);
        d.setPassword(PASSWORD);
        d.setBrand(TIANDY_BRAND);
        d.setChannel(1);
        d.setName("测试-天地伟业-200");
        return d;
    }

    /**
     * 构建海康威视测试设备
     */
    public static DeviceInfo hikvisionDevice() {
        DeviceInfo d = new DeviceInfo();
        d.setDeviceId(HIKVISION_IP);
        d.setIp(HIKVISION_IP);
        d.setPort(HIKVISION_PORT);
        d.setUsername(USERNAME);
        d.setPassword(PASSWORD);
        d.setBrand(HIKVISION_BRAND);
        d.setChannel(1);
        d.setName("测试-海康威视");
        return d;
    }

    /**
     * 返回参与测试的设备列表：两台天地伟业 + 一台海康（大华暂无摄像头暂不测试）
     */
    public static List<DeviceInfo> testDevices() {
        List<DeviceInfo> list = new ArrayList<>(3);
        list.add(tiandyDevice());
        list.add(tiandyDevice2());
        list.add(hikvisionDevice());
        return list;
    }

    /** 高速抓拍测试：连续抓拍次数 */
    public static final int HIGH_SPEED_CAPTURE_COUNT = 10;

    /** 录像下载测试：时间范围长度（秒） */
    public static final int RECORDING_RANGE_SECONDS = 60;

    /** 云台动作持续时间（毫秒） */
    public static final int PTZ_MOVE_DURATION_MS = 500;

    private TestConfig() {}
}
