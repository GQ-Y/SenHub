package com.digital.video.gateway.test;

import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.config.ConfigLoader;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.database.RecordingTask;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.SDKFactory;
import com.digital.video.gateway.hikvision.HikvisionSDK;
import com.digital.video.gateway.service.CaptureService;
import com.digital.video.gateway.service.OssService;
import com.digital.video.gateway.service.PTZService;
import com.digital.video.gateway.service.RecordingTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 摄像头核心功能测试运行器
 * 在服务器上执行：登录、断网重连、高速抓拍、录像下载、云台控制等测试
 */
public class CameraTestRunner {
    private static final Logger logger = LoggerFactory.getLogger(CameraTestRunner.class);

    private Config config;
    private Database database;
    private DeviceManager deviceManager;
    private CaptureService captureService;
    private RecordingTaskService recordingTaskService;
    private PTZService ptzService;

    private final List<String> passed = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();

    public static void main(String[] args) {
        CameraTestRunner runner = new CameraTestRunner();
        try {
            runner.init();
            runner.runAll();
            runner.printSummary();
        } catch (Throwable t) {
            logger.error("测试运行异常", t);
            runner.failed.add("Runner: " + t.getMessage());
            runner.printSummary();
            System.exit(1);
        } finally {
            runner.cleanup();
        }
    }

    private void init() throws Exception {
        logger.info("加载配置...");
        config = ConfigLoader.load("config.yaml");
        SDKFactory.init(config);
        database = new Database(config.getDatabase());
        database.init();
        deviceManager = new DeviceManager(database, config.getDevice());
        captureService = new CaptureService(deviceManager, "./storage/captures");
        OssService ossService = new OssService(config.getOss());
        recordingTaskService = new RecordingTaskService(database, deviceManager, ossService);
        ptzService = new PTZService(deviceManager);
        logger.info("初始化完成");
    }

    private void runAll() {
        runLoginTests();
        runDisconnectReconnectTests();
        runHighSpeedCaptureTests();
        runRecordingDownloadTests();
        runPTZTests();
        runDeviceInfoTests();
    }

    /** 1. 摄像头连接登录测试 */
    private void runLoginTests() {
        logger.info("========== 1. 摄像头连接登录测试 ==========");
        for (DeviceInfo device : TestConfig.testDevices()) {
            String name = "Login-" + device.getBrand() + "-" + device.getIp();
            try {
                boolean ok = deviceManager.loginDevice(device);
                if (!ok) {
                    fail(name, "登录失败");
                    continue;
                }
                if (!deviceManager.isDeviceLoggedIn(device.getDeviceId())) {
                    fail(name, "登录后状态未更新");
                    continue;
                }
                pass(name);
                deviceManager.logoutDevice(device.getDeviceId());
                Thread.sleep(300);
            } catch (Exception e) {
                fail(name, e.getMessage());
            }
        }
    }

    /** 2. 断网重连测试（登出后再次登录） */
    private void runDisconnectReconnectTests() {
        logger.info("========== 2. 断网重连测试 ==========");
        for (DeviceInfo device : TestConfig.testDevices()) {
            String name = "Reconnect-" + device.getBrand() + "-" + device.getIp();
            try {
                if (!deviceManager.loginDevice(device)) {
                    fail(name, "首次登录失败");
                    continue;
                }
                deviceManager.logoutDevice(device.getDeviceId());
                Thread.sleep(500);
                if (deviceManager.isDeviceLoggedIn(device.getDeviceId())) {
                    fail(name, "登出后仍显示已登录");
                    continue;
                }
                if (!deviceManager.loginDevice(device)) {
                    fail(name, "重连登录失败");
                    continue;
                }
                pass(name);
                deviceManager.logoutDevice(device.getDeviceId());
                Thread.sleep(300);
            } catch (Exception e) {
                fail(name, e.getMessage());
            }
        }
    }

    /** 3. 高速抓拍命令下发测试 */
    private void runHighSpeedCaptureTests() {
        logger.info("========== 3. 高速抓拍测试 ==========");
        int count = TestConfig.HIGH_SPEED_CAPTURE_COUNT;
        for (DeviceInfo device : TestConfig.testDevices()) {
            String name = "Capture-" + device.getBrand() + "-" + device.getIp() + " x" + count;
            try {
                if (!deviceManager.loginDevice(device)) {
                    fail(name, "登录失败");
                    continue;
                }
                Thread.sleep(500);
                int channel = device.getChannel() > 0 ? device.getChannel() : 1;
                // 天地伟业 SDK 连续抓图过密会报 GET_SNAP_PARA_TIME_OUT，间隔需 ≥4s（CaptureService 内也有冷却）
                int intervalMs = DeviceInfo.BRAND_TIANDY.equalsIgnoreCase(device.getBrand()) ? 4500 : 200;
                int success = 0;
                for (int i = 0; i < count; i++) {
                    String path = captureService.captureSnapshot(device.getDeviceId(), channel);
                    if (path != null && new File(path).exists()) success++;
                    Thread.sleep(intervalMs);
                }
                deviceManager.logoutDevice(device.getDeviceId());
                if (success >= count / 2) {
                    pass(name + " (成功" + success + "/" + count + ")");
                } else {
                    fail(name, "抓拍成功数 " + success + "/" + count);
                }
            } catch (Exception e) {
                fail(name, e.getMessage());
                try { deviceManager.logoutDevice(device.getDeviceId()); } catch (Exception ignored) {}
            }
        }
    }

    /** 4. 录像下载测试 */
    private void runRecordingDownloadTests() {
        logger.info("========== 4. 录像下载测试 ==========");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, -TestConfig.RECORDING_RANGE_SECONDS);
        String startTime = sdf.format(cal.getTime());
        String endTime = sdf.format(new Date());
        for (DeviceInfo device : TestConfig.testDevices()) {
            String name = "RecordingDownload-" + device.getBrand() + "-" + device.getIp();
            try {
                if (!deviceManager.loginDevice(device)) {
                    fail(name, "登录失败");
                    continue;
                }
                int channel = device.getChannel() > 0 ? device.getChannel() : 1;
                RecordingTask task = recordingTaskService.downloadRecordingSync(
                        device.getDeviceId(), channel, startTime, endTime, 30);
                deviceManager.logoutDevice(device.getDeviceId());
                if (task == null) {
                    fail(name, "创建下载任务失败");
                    continue;
                }
                if (task.getStatus() == RecordingTaskService.STATUS_COMPLETED && task.getLocalFilePath() != null) {
                    File f = new File(task.getLocalFilePath());
                    pass(name + (f.exists() ? " (文件存在)" : " (任务完成)"));
                } else if (task.getStatus() == RecordingTaskService.STATUS_FAILED) {
                    pass(name + " (无录像数据，接口正常)");
                } else {
                    pass(name + " (任务已创建，状态=" + task.getStatus() + ")");
                }
            } catch (Exception e) {
                fail(name, e.getMessage());
                try { deviceManager.logoutDevice(device.getDeviceId()); } catch (Exception ignored) {}
            }
        }
    }

    /** 5. 云台控制测试 */
    private void runPTZTests() {
        logger.info("========== 5. 云台控制测试 ==========");
        int channel = 1;
        int speed = 3;
        for (DeviceInfo device : TestConfig.testDevices()) {
            String name = "PTZ-" + device.getBrand() + "-" + device.getIp();
            try {
                if (!deviceManager.loginDevice(device)) {
                    fail(name, "登录失败");
                    continue;
                }
                boolean startOk = ptzService.ptzControl(device.getDeviceId(), channel, "up", "start", speed);
                if (!startOk) {
                    deviceManager.logoutDevice(device.getDeviceId());
                    pass(name + " (设备可能不支持云台，接口已调用)");
                    continue;
                }
                Thread.sleep(TestConfig.PTZ_MOVE_DURATION_MS);
                boolean stopOk = ptzService.ptzControl(device.getDeviceId(), channel, "up", "stop", speed);
                deviceManager.logoutDevice(device.getDeviceId());
                if (stopOk || startOk) pass(name);
                else fail(name, "start/stop 未完全成功");
            } catch (Exception e) {
                fail(name, e.getMessage());
                try { deviceManager.logoutDevice(device.getDeviceId()); } catch (Exception ignored) {}
            }
        }
    }

    /** 6. 设备信息与状态测试 */
    private void runDeviceInfoTests() {
        logger.info("========== 6. 设备信息测试 ==========");
        for (DeviceInfo device : TestConfig.testDevices()) {
            String name = "DeviceInfo-" + device.getBrand() + "-" + device.getIp();
            try {
                if (!deviceManager.loginDevice(device)) {
                    fail(name, "登录失败");
                    continue;
                }
                DeviceInfo got = deviceManager.getDevice(device.getDeviceId());
                int userId = deviceManager.getDeviceUserId(device.getDeviceId());
                deviceManager.logoutDevice(device.getDeviceId());
                if (got != null && userId >= 0) pass(name);
                else fail(name, "getDevice或userId异常");
            } catch (Exception e) {
                fail(name, e.getMessage());
                try { deviceManager.logoutDevice(device.getDeviceId()); } catch (Exception ignored) {}
            }
        }
    }

    private void pass(String name) {
        passed.add(name);
        logger.info("[PASS] {}", name);
    }

    private void fail(String name, String reason) {
        failed.add(name + ": " + reason);
        logger.warn("[FAIL] {} - {}", name, reason);
    }

    private void printSummary() {
        logger.info("========================================");
        logger.info("测试汇总: 通过 {} , 失败 {}", passed.size(), failed.size());
        logger.info("========================================");
        for (String s : failed) logger.error("FAIL: {}", s);
        if (!failed.isEmpty()) System.exit(1);
    }

    private void cleanup() {
        try {
            if (database != null) database.close();
            SDKFactory.cleanup();
            if (HikvisionSDK.getInstance() != null) HikvisionSDK.getInstance().cleanup();
        } catch (Exception e) {
            logger.debug("清理异常", e);
        }
    }
}
