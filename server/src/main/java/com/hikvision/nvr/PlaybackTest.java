package com.hikvision.nvr;

import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.config.ConfigLoader;
import com.hikvision.nvr.database.Database;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.device.DeviceSDK;
import com.hikvision.nvr.device.SDKFactory;
import com.hikvision.nvr.hikvision.HCNetSDK;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 录像下载测试类
 */
public class PlaybackTest {
    private static final Logger logger = LoggerFactory.getLogger(PlaybackTest.class);

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("录像下载功能测试");
        logger.info("========================================");

        try {
            // 1. 加载配置
            logger.info("\n[1/5] 加载配置...");
            Config config = ConfigLoader.load("config.yaml");
            if (config == null) {
                logger.error("配置加载失败");
                System.exit(1);
            }
            logger.info("✓ 配置加载成功");

            // 2. 初始化SDK工厂
            logger.info("\n[2/5] 初始化SDK工厂...");
            SDKFactory.init(config);
            logger.info("✓ SDK工厂初始化成功");

            // 3. 初始化数据库和设备管理器
            logger.info("\n[3/5] 初始化数据库和设备管理器...");
            Database database = new Database(config.getDatabase().getPath());
            database.init();
            logger.info("✓ 数据库初始化成功");

            DeviceManager deviceManager = new DeviceManager(database, config.getDevice());
            logger.info("✓ 设备管理器初始化成功");

            // 4. 连接设备
            logger.info("\n[4/5] 连接设备...");
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

            // 5. 执行录像下载
            logger.info("\n[5/5] 执行录像下载...");
            int userId = testDevice.getUserId();
            int channel = testDevice.getChannel() > 0 ? testDevice.getChannel() : 1;

            // 获取SDK实例（仅支持海康SDK回放）
            DeviceSDK deviceSDK = SDKFactory.getSDK(testDevice.getBrand());
            if (deviceSDK == null || !(deviceSDK instanceof HikvisionSDK)) {
                logger.error("✗ 当前仅支持海康SDK回放，设备品牌: {}", testDevice.getBrand());
                deviceManager.logoutDevice(testDevice.getDeviceId());
                System.exit(1);
            }
            HikvisionSDK hikvisionSDK = (HikvisionSDK) deviceSDK;
            HCNetSDK hcNetSDK = hikvisionSDK.getSDK();

            // 计算时间：当前时间往前推，下载30秒的录像
            // 当前是1月6日12:53，我们往前找录像，比如12:50:00到12:50:30
            // 如果设备时间不同，可以尝试更早的时间
            Calendar cal = Calendar.getInstance();
            // 尝试下载12:50:00到12:50:30的录像（往前推3分钟，确保有录像）
            cal.set(2026, Calendar.JANUARY, 6, 12, 50, 0); // 2026-01-06 12:50:00
            
            HCNetSDK.NET_DVR_TIME startTime = new HCNetSDK.NET_DVR_TIME();
            startTime.dwYear = cal.get(Calendar.YEAR);
            startTime.dwMonth = cal.get(Calendar.MONTH) + 1; // Calendar月份从0开始
            startTime.dwDay = cal.get(Calendar.DAY_OF_MONTH);
            startTime.dwHour = cal.get(Calendar.HOUR_OF_DAY);
            startTime.dwMinute = cal.get(Calendar.MINUTE);
            startTime.dwSecond = cal.get(Calendar.SECOND);

            // 结束时间：30秒后
            cal.add(Calendar.SECOND, 30);
            HCNetSDK.NET_DVR_TIME endTime = new HCNetSDK.NET_DVR_TIME();
            endTime.dwYear = cal.get(Calendar.YEAR);
            endTime.dwMonth = cal.get(Calendar.MONTH) + 1;
            endTime.dwDay = cal.get(Calendar.DAY_OF_MONTH);
            endTime.dwHour = cal.get(Calendar.HOUR_OF_DAY);
            endTime.dwMinute = cal.get(Calendar.MINUTE);
            endTime.dwSecond = cal.get(Calendar.SECOND);

            // 设置下载条件
            HCNetSDK.NET_DVR_PLAYCOND playCond = new HCNetSDK.NET_DVR_PLAYCOND();
            playCond.dwChannel = channel; // 通道号
            playCond.struStartTime = startTime;
            playCond.struStopTime = endTime;
            playCond.byDrawFrame = 0; // 不抽帧
            playCond.byStreamType = 0; // 主码流
            playCond.write();

            // 创建下载目录
            String downloadDir = "./downloads";
            File dir = new File(downloadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 生成文件名
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String fileName = downloadDir + "/playback_" + testIp.replace(".", "_") + "_" + timestamp + ".mp4";
            byte[] fileNameBytes = fileName.getBytes("UTF-8");
            byte[] fileNameArray = new byte[256];
            System.arraycopy(fileNameBytes, 0, fileNameArray, 0, Math.min(fileNameBytes.length, fileNameArray.length - 1));

            logger.info("录像下载参数:");
            logger.info("  - 设备ID: {}", userId);
            logger.info("  - 通道号: {}", channel);
            logger.info("  - 开始时间: {}-{}-{} {}:{}:{}", 
                startTime.dwYear, startTime.dwMonth, startTime.dwDay,
                startTime.dwHour, startTime.dwMinute, startTime.dwSecond);
            logger.info("  - 结束时间: {}-{}-{} {}:{}:{}", 
                endTime.dwYear, endTime.dwMonth, endTime.dwDay,
                endTime.dwHour, endTime.dwMinute, endTime.dwSecond);
            logger.info("  - 保存路径: {}", fileName);

            // 开始下载录像
            logger.info("\n开始下载录像...");
            int downloadHandle = hcNetSDK.NET_DVR_GetFileByTime_V40(userId, fileName, playCond);

            if (downloadHandle < 0) {
                int errorCode = hikvisionSDK.getLastError();
                logger.error("✗ 开始下载录像失败，错误码: {}", errorCode);
                deviceManager.logoutDevice(testDevice.getDeviceId());
                System.exit(1);
            }

            logger.info("✓ 下载句柄创建成功: {}", downloadHandle);

            // 启动下载
            boolean playResult = hcNetSDK.NET_DVR_PlayBackControl(downloadHandle, HCNetSDK.NET_DVR_PLAYSTART, 0, null);
            if (!playResult) {
                int errorCode = hikvisionSDK.getLastError();
                logger.error("✗ 启动下载失败，错误码: {}", errorCode);
                hcNetSDK.NET_DVR_StopGetFile(downloadHandle);
                deviceManager.logoutDevice(testDevice.getDeviceId());
                System.exit(1);
            }

            logger.info("✓ 录像下载已启动");

            // 监控下载进度
            logger.info("\n监控下载进度...");
            int maxWaitTime = 60; // 最多等待60秒
            int waitCount = 0;
            File videoFile = new File(fileName);

            while (waitCount < maxWaitTime) {
                Thread.sleep(1000); // 每秒检查一次
                waitCount++;

                // 检查下载进度（NET_DVR_GetDownloadPos返回进度百分比，0-100）
                int progress = hcNetSDK.NET_DVR_GetDownloadPos(downloadHandle);
                
                if (progress >= 0) {
                    logger.info("下载进度: {}% (等待 {} 秒)", progress, waitCount);
                    
                    if (progress >= 100) {
                        logger.info("✓ 下载完成！");
                        break;
                    }
                } else {
                    // 如果获取进度失败，检查文件是否已生成
                    if (videoFile.exists() && videoFile.length() > 0) {
                        logger.info("文件已生成，大小: {} 字节", videoFile.length());
                    }
                }

                // 检查文件是否存在且大小在增长
                if (videoFile.exists()) {
                    long fileSize = videoFile.length();
                    logger.debug("当前文件大小: {} 字节", fileSize);
                }
            }

            // 停止下载
            logger.info("\n停止下载...");
            hcNetSDK.NET_DVR_StopGetFile(downloadHandle);
            logger.info("✓ 下载已停止");

            // 检查文件
            if (videoFile.exists()) {
                long fileSize = videoFile.length();
                logger.info("\n✓ 录像文件生成成功！");
                logger.info("  - 文件路径: {}", fileName);
                logger.info("  - 文件大小: {} 字节 ({} KB, {} MB)", 
                    fileSize, fileSize / 1024, fileSize / (1024 * 1024));
            } else {
                logger.warn("⚠ 录像文件未生成，可能该时间段没有录像");
            }

            // 登出设备
            Thread.sleep(500);
            deviceManager.logoutDevice(testDevice.getDeviceId());
            logger.info("✓ 设备已登出");

            logger.info("\n========================================");
            logger.info("录像下载测试完成！");
            logger.info("========================================");

        } catch (Exception e) {
            logger.error("录像下载测试异常", e);
            System.exit(1);
        } finally {
            HikvisionSDK.getInstance().cleanup();
        }
    }
}
