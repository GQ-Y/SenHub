package com.digital.video.gateway;

import com.digital.video.gateway.service.RadarTestService;

/**
 * 独立的雷达连接测试运行器
 */
public class LivoxRadarTester {
    public static void main(String[] args) {
        String lidarIp = "192.168.1.115";
        if (args.length > 0) {
            lidarIp = args[0];
        }

        System.out.println("========================================");
        System.out.println("正在启动览沃 Mid-360 雷达连通性测试...");
        System.out.println("目标 IP: " + lidarIp);
        System.out.println("========================================");

        RadarTestService service = new RadarTestService();
        String result = service.testConnection(lidarIp);

        System.out.println("\n测试结果报告:");
        System.out.println("----------------------------------------");
        System.out.println(result);
        System.out.println("----------------------------------------");
        System.out.println("测试程序运行结束。");
        System.out.println("========================================");
    }
}
