package com.digital.video.gateway;

import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.config.ConfigLoader;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import com.digital.video.gateway.device.SDKFactory;
import com.digital.video.gateway.hikvision.HikvisionSDK;
import com.digital.video.gateway.mqtt.MqttClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 集成测试类
 * 测试所有功能模块
 */
public class IntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(IntegrationTest.class);

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("开始集成测试");
        logger.info("========================================");

        try {
            // 1. 加载配置
            logger.info("\n[1/7] 加载配置...");
            Config config = ConfigLoader.load("config.yaml");
            if (config == null) {
                logger.error("配置加载失败");
                System.exit(1);
            }
            logger.info("✓ 配置加载成功");
            logger.info("  - MQTT服务器: {}", config.getMqtt().getBroker());
            logger.info("  - 设备默认IP: 192.168.1.100");
            logger.info("  - SDK端口: {}", config.getDevice().getDefaultPort());
            logger.info("  - HTTP端口: {}", config.getDevice().getHttpPort());
            logger.info("  - RTSP端口: {}", config.getDevice().getRtspPort());

            // 2. 初始化SDK工厂
            logger.info("\n[2/7] 初始化SDK工厂...");
            SDKFactory.init(config);
            logger.info("✓ SDK工厂初始化成功");

            // 3. 初始化数据库
            logger.info("\n[3/7] 初始化数据库...");
            Database database = new Database(config.getDatabase().getPath());
            if (!database.init()) {
                logger.error("✗ 数据库初始化失败");
                System.exit(1);
            }
            logger.info("✓ 数据库初始化成功");

            // 4. 初始化设备管理器
            logger.info("\n[4/7] 初始化设备管理器...");
            DeviceManager deviceManager = new DeviceManager(database, config.getDevice());
            logger.info("✓ 设备管理器初始化成功");

            // 5. 测试摄像头连接
            logger.info("\n[5/7] 测试摄像头连接...");
            String testIp = "192.168.1.100";
            int testPort = config.getDevice().getDefaultPort();
            String testUsername = config.getDevice().getDefaultUsername();
            String testPassword = config.getDevice().getDefaultPassword();

            logger.info("尝试连接摄像头: {}:{} (用户: {})", testIp, testPort, testUsername);
            
            // 创建测试设备信息
            DeviceInfo testDevice = new DeviceInfo();
            testDevice.setDeviceId(testIp);
            testDevice.setIp(testIp);
            testDevice.setPort(testPort);
            testDevice.setUsername(testUsername);
            testDevice.setPassword(testPassword);
            testDevice.setName("测试摄像头");

            // 尝试登录
            logger.info("登录参数详情：");
            logger.info("  - IP: {}", testIp);
            logger.info("  - 端口: {} (SDK端口)", testPort);
            logger.info("  - 用户名: {}", testUsername);
            logger.info("  - 密码: {} (已隐藏)", testPassword != null && !testPassword.isEmpty() ? "***" : "空");
            
            boolean loginSuccess = deviceManager.loginDevice(testDevice);
            if (loginSuccess) {
                logger.info("✓ 摄像头连接成功！");
                logger.info("  - 用户ID: {}", testDevice.getUserId());
                
                // 保存设备信息
                database.saveOrUpdateDevice(testDevice);
                logger.info("✓ 设备信息已保存到数据库");
                
                // 等待几秒后登出
                Thread.sleep(2000);
                deviceManager.logoutDevice(testDevice.getDeviceId());
                logger.info("✓ 摄像头已登出");
            } else {
                logger.error("✗ 摄像头连接失败");
                DeviceSDK deviceSDK = SDKFactory.getSDK(testDevice != null ? testDevice.getBrand() : "hikvision");
                if (deviceSDK != null) {
                    logger.error("  - SDK错误码: {}", deviceSDK.getLastError());
                }
                logger.error("请检查：");
                logger.error("  1. 摄像头IP地址是否正确: {}", testIp);
                logger.error("  2. 摄像头是否在线（可尝试ping {}）", testIp);
                logger.error("  3. SDK端口是否正确: {} (海康威视默认8000)", testPort);
                logger.error("  4. 用户名和密码是否正确: {} / {}", testUsername, testPassword != null && !testPassword.isEmpty() ? "***" : "空");
                logger.error("  5. 网络连接是否正常");
                logger.error("  6. 防火墙是否阻止了端口 {}", testPort);
            }

            // 6. 测试MQTT连接
            logger.info("\n[6/7] 测试MQTT连接...");
            MqttClient mqttClient = new MqttClient(config.getMqtt());
            if (mqttClient.connect()) {
                logger.info("✓ MQTT连接成功");
                
                // 测试发布消息
                String testMessage = "{\"test\": \"hello from digital video gateway service\"}";
                if (mqttClient.publishStatus(testMessage)) {
                    logger.info("✓ MQTT消息发布成功");
                } else {
                    logger.warn("✗ MQTT消息发布失败");
                }
                
                // 等待消息发送
                Thread.sleep(1000);
                mqttClient.close();
            } else {
                logger.error("✗ MQTT连接失败");
                logger.error("请检查：");
                logger.error("  1. MQTT服务器地址: {}", config.getMqtt().getBroker());
                logger.error("  2. 用户名和密码是否正确");
                logger.error("  3. 网络连接是否正常");
            }

            // 7. 测试数据库操作
            logger.info("\n[7/7] 测试数据库操作...");
            if (loginSuccess) {
                DeviceInfo savedDevice = database.getDevice(testIp);
                if (savedDevice != null) {
                    logger.info("✓ 数据库查询成功");
                    logger.info("  - 设备ID: {}", savedDevice.getDeviceId());
                    logger.info("  - IP: {}", savedDevice.getIp());
                    logger.info("  - 端口: {}", savedDevice.getPort());
                    logger.info("  - 名称: {}", savedDevice.getName());
                } else {
                    logger.warn("✗ 数据库查询失败");
                }
            }

            // 清理
            logger.info("\n清理资源...");
            database.close();
            SDKFactory.cleanup();

            logger.info("\n========================================");
            logger.info("集成测试完成");
            logger.info("========================================");

        } catch (Exception e) {
            logger.error("集成测试失败", e);
            System.exit(1);
        }
    }
}
