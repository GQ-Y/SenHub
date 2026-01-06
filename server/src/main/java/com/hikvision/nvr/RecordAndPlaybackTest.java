package com.hikvision.nvr;

import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.config.ConfigLoader;
import com.hikvision.nvr.database.Database;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.hikvision.HCNetSDK;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 自动录制和回放测试类
 * 1. 连接设备
 * 2. 自动录制2分钟
 * 3. 测试回放获取录制的录像
 */
public class RecordAndPlaybackTest {
    private static final Logger logger = LoggerFactory.getLogger(RecordAndPlaybackTest.class);
    private static int realPlayHandle = -1;

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("自动录制和回放测试");
        logger.info("========================================");

        try {
            // 1. 加载配置
            logger.info("\n[1/6] 加载配置...");
            Config config = ConfigLoader.load("config.yaml");
            if (config == null) {
                logger.error("配置加载失败");
                System.exit(1);
            }
            logger.info("✓ 配置加载成功");

            // 2. 初始化SDK
            logger.info("\n[2/6] 初始化SDK...");
            HikvisionSDK sdk = HikvisionSDK.getInstance();
            if (!sdk.init(config.getSdk())) {
                logger.error("✗ SDK初始化失败，错误码: {}", sdk.getLastError());
                System.exit(1);
            }
            logger.info("✓ SDK初始化成功");

            // 3. 初始化数据库和设备管理器
            logger.info("\n[3/6] 初始化数据库和设备管理器...");
            Database database = new Database(config.getDatabase().getPath());
            database.init();
            logger.info("✓ 数据库初始化成功");

            DeviceManager deviceManager = new DeviceManager(sdk, database, config.getDevice());
            logger.info("✓ 设备管理器初始化成功");

            // 4. 连接设备
            logger.info("\n[4/6] 连接设备...");
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
                logger.error("  - SDK错误码: {}", sdk.getLastError());
                System.exit(1);
            }

            logger.info("✓ 设备登录成功");
            logger.info("  - 用户ID: {}", testDevice.getUserId());
            logger.info("  - 通道号: {}", testDevice.getChannel());

            // 等待一下确保连接稳定
            Thread.sleep(1000);

            // 5. 启动预览并录制2分钟
            logger.info("\n[5/6] 启动预览并录制2分钟...");
            int userId = testDevice.getUserId();
            int channel = testDevice.getChannel() > 0 ? testDevice.getChannel() : 1;

            HCNetSDK hcNetSDK = sdk.getSDK();

            // 设置预览参数
            HCNetSDK.NET_DVR_PREVIEWINFO previewInfo = new HCNetSDK.NET_DVR_PREVIEWINFO();
            previewInfo.lChannel = channel; // 通道号
            previewInfo.dwStreamType = 0; // 0-主码流，1-子码流
            previewInfo.dwLinkMode = 0; // 0-TCP方式
            previewInfo.bBlocked = 1; // 1-阻塞取流
            previewInfo.byProtoType = 0; // 0-私有协议
            previewInfo.write();

            // 创建回调函数（可以为null，因为我们只是录制）
            // 但SDK要求必须提供回调，即使为空实现
            HCNetSDK.FRealDataCallBack_V30 realDataCallback = new HCNetSDK.FRealDataCallBack_V30() {
                @Override
                public void invoke(int lRealHandle, int dwDataType, Pointer pBuffer, int dwBufSize, Pointer pUser) {
                    // 可以在这里处理数据，但录制不需要
                }
            };

            // 启动预览
            logger.info("启动预览: 通道={}, 码流类型=主码流", channel);
            realPlayHandle = hcNetSDK.NET_DVR_RealPlay_V40(userId, previewInfo, realDataCallback, null);

            if (realPlayHandle == -1) {
                int errorCode = sdk.getLastError();
                logger.error("✗ 启动预览失败，错误码: {}", errorCode);
                deviceManager.logoutDevice(testDevice.getDeviceId());
                System.exit(1);
            }

            logger.info("✓ 预览启动成功，句柄: {}", realPlayHandle);

            // 等待预览稳定
            Thread.sleep(2000);

            // 开始录制
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String recordDir = "./records";
            File dir = new File(recordDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String recordFileName = recordDir + "/record_" + testIp.replace(".", "_") + "_" + timestamp + ".mp4";
            
            logger.info("开始录制: {}", recordFileName);
            boolean saveResult = hcNetSDK.NET_DVR_SaveRealData(realPlayHandle, recordFileName);
            
            if (!saveResult) {
                int errorCode = sdk.getLastError();
                logger.error("✗ 开始录制失败，错误码: {}", errorCode);
                hcNetSDK.NET_DVR_StopRealPlay(realPlayHandle);
                deviceManager.logoutDevice(testDevice.getDeviceId());
                System.exit(1);
            }

            logger.info("✓ 录制已开始，将录制2分钟...");
            
            // 录制2分钟（120秒）
            int recordSeconds = 120;
            for (int i = 0; i < recordSeconds; i++) {
                Thread.sleep(1000);
                if ((i + 1) % 10 == 0) {
                    logger.info("录制进度: {}/{} 秒", i + 1, recordSeconds);
                }
            }

            // 停止录制
            logger.info("\n停止录制...");
            hcNetSDK.NET_DVR_StopSaveRealData(realPlayHandle);
            logger.info("✓ 录制已停止");

            // 停止预览
            Thread.sleep(1000);
            hcNetSDK.NET_DVR_StopRealPlay(realPlayHandle);
            logger.info("✓ 预览已停止");

            // 检查录制文件
            File recordFile = new File(recordFileName);
            if (recordFile.exists() && recordFile.length() > 0) {
                long fileSize = recordFile.length();
                logger.info("✓ 录制文件生成成功！");
                logger.info("  - 文件路径: {}", recordFileName);
                logger.info("  - 文件大小: {} 字节 ({} KB, {} MB)", 
                    fileSize, fileSize / 1024, fileSize / (1024 * 1024));
            } else {
                logger.warn("⚠ 录制文件未生成或大小为0");
            }

            // 记录录制开始和结束时间（用于回放下载）
            Calendar recordStartCal = Calendar.getInstance();
            recordStartCal.add(Calendar.MINUTE, -2); // 录制开始时间（2分钟前）
            Calendar recordEndCal = Calendar.getInstance(); // 录制结束时间（当前）

            // 等待一段时间，让设备有时间将录像写入存储
            logger.info("\n等待10秒，让设备将录像写入存储...");
            Thread.sleep(10000);

            // 6. 测试回放获取录制的录像
            logger.info("\n[6/6] 测试回放获取录制的录像...");
            
            // 使用录制的时间范围
            Calendar cal = (Calendar) recordStartCal.clone();
            
            HCNetSDK.NET_DVR_TIME startTime = new HCNetSDK.NET_DVR_TIME();
            startTime.dwYear = cal.get(Calendar.YEAR);
            startTime.dwMonth = cal.get(Calendar.MONTH) + 1;
            startTime.dwDay = cal.get(Calendar.DAY_OF_MONTH);
            startTime.dwHour = cal.get(Calendar.HOUR_OF_DAY);
            startTime.dwMinute = cal.get(Calendar.MINUTE);
            startTime.dwSecond = cal.get(Calendar.SECOND);

            // 结束时间：录制结束时间
            Calendar endCal = (Calendar) recordEndCal.clone();
            HCNetSDK.NET_DVR_TIME endTime = new HCNetSDK.NET_DVR_TIME();
            endTime.dwYear = endCal.get(Calendar.YEAR);
            endTime.dwMonth = endCal.get(Calendar.MONTH) + 1;
            endTime.dwDay = endCal.get(Calendar.DAY_OF_MONTH);
            endTime.dwHour = endCal.get(Calendar.HOUR_OF_DAY);
            endTime.dwMinute = endCal.get(Calendar.MINUTE);
            endTime.dwSecond = endCal.get(Calendar.SECOND);

            // 设置下载条件
            HCNetSDK.NET_DVR_PLAYCOND playCond = new HCNetSDK.NET_DVR_PLAYCOND();
            playCond.dwChannel = channel;
            playCond.struStartTime = startTime;
            playCond.struStopTime = endTime;
            playCond.byDrawFrame = 0; // 不抽帧
            playCond.byStreamType = 0; // 主码流
            playCond.write();

            // 创建下载目录
            String downloadDir = "./downloads";
            File downloadDirFile = new File(downloadDir);
            if (!downloadDirFile.exists()) {
                downloadDirFile.mkdirs();
            }

            // 生成下载文件名
            String downloadTimestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String downloadFileName = downloadDir + "/playback_" + testIp.replace(".", "_") + "_" + downloadTimestamp + ".mp4";
            byte[] fileNameBytes = downloadFileName.getBytes(StandardCharsets.UTF_8);
            byte[] fileNameArray = new byte[256];
            System.arraycopy(fileNameBytes, 0, fileNameArray, 0, Math.min(fileNameBytes.length, fileNameArray.length - 1));

            logger.info("回放下载参数:");
            logger.info("  - 通道号: {}", channel);
            logger.info("  - 开始时间: {}-{}-{} {}:{}:{}", 
                startTime.dwYear, startTime.dwMonth, startTime.dwDay,
                startTime.dwHour, startTime.dwMinute, startTime.dwSecond);
            logger.info("  - 结束时间: {}-{}-{} {}:{}:{}", 
                endTime.dwYear, endTime.dwMonth, endTime.dwDay,
                endTime.dwHour, endTime.dwMinute, endTime.dwSecond);
            logger.info("  - 保存路径: {}", downloadFileName);

            // 开始下载录像
            logger.info("\n开始下载录像...");
            int downloadHandle = hcNetSDK.NET_DVR_GetFileByTime_V40(userId, downloadFileName, playCond);

            if (downloadHandle < 0) {
                int errorCode = sdk.getLastError();
                logger.error("✗ 开始下载录像失败，错误码: {}", errorCode);
                deviceManager.logoutDevice(testDevice.getDeviceId());
                System.exit(1);
            }

            logger.info("✓ 下载句柄创建成功: {}", downloadHandle);

            // 启动下载
            boolean playResult = hcNetSDK.NET_DVR_PlayBackControl(downloadHandle, HCNetSDK.NET_DVR_PLAYSTART, 0, null);
            if (!playResult) {
                int errorCode = sdk.getLastError();
                logger.error("✗ 启动下载失败，错误码: {}", errorCode);
                hcNetSDK.NET_DVR_StopGetFile(downloadHandle);
                deviceManager.logoutDevice(testDevice.getDeviceId());
                System.exit(1);
            }

            logger.info("✓ 录像下载已启动");

            // 监控下载进度
            logger.info("\n监控下载进度...");
            int maxWaitTime = 120; // 最多等待120秒
            int waitCount = 0;
            File videoFile = new File(downloadFileName);

            while (waitCount < maxWaitTime) {
                Thread.sleep(1000);
                waitCount++;

                // 检查下载进度
                int progress = hcNetSDK.NET_DVR_GetDownloadPos(downloadHandle);
                
                if (progress >= 0) {
                    if (waitCount % 5 == 0) { // 每5秒输出一次
                        logger.info("下载进度: {}% (等待 {} 秒)", progress, waitCount);
                    }
                    
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
                if (videoFile.exists() && videoFile.length() > 0) {
                    long fileSize = videoFile.length();
                    if (waitCount % 10 == 0) {
                        logger.debug("当前文件大小: {} 字节", fileSize);
                    }
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
                logger.info("  - 文件路径: {}", downloadFileName);
                logger.info("  - 文件大小: {} 字节 ({} KB, {} MB)", 
                    fileSize, fileSize / 1024, fileSize / (1024 * 1024));
            } else {
                logger.warn("⚠ 录像文件未生成");
            }

            // 登出设备
            Thread.sleep(500);
            deviceManager.logoutDevice(testDevice.getDeviceId());
            logger.info("✓ 设备已登出");

            logger.info("\n========================================");
            logger.info("自动录制和回放测试完成！");
            logger.info("========================================");

        } catch (Exception e) {
            logger.error("测试异常", e);
            // 确保清理资源
            if (realPlayHandle != -1) {
                try {
                    HikvisionSDK.getInstance().getSDK().NET_DVR_StopSaveRealData(realPlayHandle);
                    HikvisionSDK.getInstance().getSDK().NET_DVR_StopRealPlay(realPlayHandle);
                } catch (Exception ex) {
                    // 忽略清理错误
                }
            }
            System.exit(1);
        } finally {
            HikvisionSDK.getInstance().cleanup();
        }
    }
}
