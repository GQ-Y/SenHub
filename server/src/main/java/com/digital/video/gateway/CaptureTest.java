package com.digital.video.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.config.ConfigLoader;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import com.digital.video.gateway.device.SDKFactory;
import com.digital.video.gateway.hikvision.HCNetSDK;
import com.digital.video.gateway.hikvision.HikvisionSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

/**
 * 抓图测试类
 */
public class CaptureTest {
    private static final Logger logger = LoggerFactory.getLogger(CaptureTest.class);

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("抓图功能测试");
        logger.info("========================================");

        try {
            // 1. 加载配置
            logger.info("\n[1/4] 加载配置...");
            Config config = ConfigLoader.load("config.yaml");
            if (config == null) {
                logger.error("配置加载失败");
                System.exit(1);
            }
            logger.info("✓ 配置加载成功");

            // 2. 初始化SDK工厂
            logger.info("\n[2/4] 初始化SDK工厂...");
            SDKFactory.init(config);
            logger.info("✓ SDK工厂初始化成功");

            // 3. 初始化数据库和设备管理器
            logger.info("\n[3/4] 初始化数据库和设备管理器...");
            Database database = new Database(config.getDatabase().getPath());
            database.init();
            logger.info("✓ 数据库初始化成功");

            DeviceManager deviceManager = new DeviceManager(database, config.getDevice());
            logger.info("✓ 设备管理器初始化成功");

            // 4. 连接设备并测试抓图
            logger.info("\n[4/4] 连接设备并测试抓图...");
            String testIp = "192.168.1.100";
            int testSdkPort = config.getDevice().getDefaultPort();
            String testUsername = config.getDevice().getDefaultUsername();
            String testPassword = config.getDevice().getDefaultPassword();

            logger.info("尝试连接摄像头:");
            logger.info("  - IP: {}", testIp);
            logger.info("  - 端口: {} (SDK端口)", testSdkPort);
            logger.info("  - 用户名: {}", testUsername);

            // 创建或获取设备信息
            DeviceInfo testDevice = database.getDeviceByIpPort(testIp, testSdkPort);
            if (testDevice == null) {
                testDevice = new DeviceInfo();
                testDevice.setDeviceId(testIp);
                testDevice.setIp(testIp);
                testDevice.setPort(testSdkPort);
                testDevice.setUsername(testUsername);
                testDevice.setPassword(testPassword);
                testDevice.setName("测试摄像头");
                testDevice.setChannel(1); // 默认通道1
            }

            // 登录设备
            boolean loginSuccess = deviceManager.loginDevice(testDevice);
            if (!loginSuccess) {
                logger.error("✗ 设备登录失败");
                DeviceSDK deviceSDK = SDKFactory.getSDK(testDevice.getBrand());
                if (deviceSDK != null) {
                    logger.error("  - SDK错误码: {}", deviceSDK.getLastError());
                }
                System.exit(1);
            }

            logger.info("✓ 设备登录成功");
            logger.info("  - 用户ID: {}", testDevice.getUserId());
            logger.info("  - 通道号: {}", testDevice.getChannel());

            // 等待一下确保连接稳定
            Thread.sleep(1000);

            // 执行抓图
            logger.info("\n开始执行抓图...");
            int userId = testDevice.getUserId();
            int channel = testDevice.getChannel() > 0 ? testDevice.getChannel() : 1;

            // 获取SDK实例（仅支持海康SDK抓图）
            DeviceSDK deviceSDK = SDKFactory.getSDK(testDevice.getBrand());
            if (deviceSDK == null || !(deviceSDK instanceof HikvisionSDK)) {
                logger.error("✗ 当前仅支持海康SDK抓图，设备品牌: {}", testDevice.getBrand());
                deviceManager.logoutDevice(testDevice.getDeviceId());
                System.exit(1);
            }
            HikvisionSDK hikvisionSDK = (HikvisionSDK) deviceSDK;
            HCNetSDK hcNetSDK = hikvisionSDK.getSDK();
            
            // 设置抓图参数
            HCNetSDK.NET_DVR_JPEGPARA jpegPara = new HCNetSDK.NET_DVR_JPEGPARA();
            jpegPara.wPicSize = 0; // 0=CIF, 使用当前分辨率
            jpegPara.wPicQuality = 2; // 图片质量：0-最好 1-较好 2-一般
            jpegPara.write();

            // 创建临时文件保存图片
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String picDir = "./captures";
            File dir = new File(picDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String picFileName = picDir + "/capture_" + testIp.replace(".", "_") + "_" + timestamp + ".jpg";
            byte[] fileNameBytes = picFileName.getBytes("UTF-8");
            byte[] fileNameArray = new byte[256];
            System.arraycopy(fileNameBytes, 0, fileNameArray, 0, Math.min(fileNameBytes.length, fileNameArray.length - 1));

            logger.info("抓图参数:");
            logger.info("  - 设备ID: {}", userId);
            logger.info("  - 通道号: {}", channel);
            logger.info("  - 图片质量: {}", jpegPara.wPicQuality);
            logger.info("  - 保存路径: {}", picFileName);

            // 执行抓图
            boolean captureResult = hcNetSDK.NET_DVR_CaptureJPEGPicture(userId, channel, jpegPara, fileNameArray);

            if (!captureResult) {
                int errorCode = hikvisionSDK.getLastError();
                logger.error("✗ 抓图失败，错误码: {}", errorCode);
                deviceManager.logoutDevice(testDevice.getDeviceId());
                System.exit(1);
            }

            logger.info("✓ 抓图命令执行成功");

            // 等待文件写入完成
            Thread.sleep(1000);

            // 检查文件是否存在
            File picFile = new File(picFileName);
            if (!picFile.exists()) {
                logger.error("✗ 抓图文件未生成: {}", picFileName);
                deviceManager.logoutDevice(testDevice.getDeviceId());
                System.exit(1);
            }

            // 读取图片文件
            byte[] imageBytes = Files.readAllBytes(Paths.get(picFileName));
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            logger.info("✓ 抓图成功！");
            logger.info("  - 文件路径: {}", picFileName);
            logger.info("  - 文件大小: {} 字节 ({} KB)", imageBytes.length, imageBytes.length / 1024);
            logger.info("  - Base64长度: {} 字符", base64Image.length());
            logger.info("  - Base64预览: {}...", base64Image.substring(0, Math.min(50, base64Image.length())));

            // 登出设备
            Thread.sleep(500);
            deviceManager.logoutDevice(testDevice.getDeviceId());
            logger.info("✓ 设备已登出");

            logger.info("\n========================================");
            logger.info("抓图测试完成！");
            logger.info("========================================");

        } catch (Exception e) {
            logger.error("抓图测试异常", e);
            System.exit(1);
        } finally {
            HikvisionSDK.getInstance().cleanup();
        }
    }
}
