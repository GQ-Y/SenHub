package com.digital.video.gateway.scanner;

import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.hikvision.HCNetSDK;
import com.digital.video.gateway.hikvision.HikvisionSDK;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 设备扫描器
 * 使用SDK的监听功能来发现局域网中的海康设备
 */
public class DeviceScanner {
    private static final Logger logger = LoggerFactory.getLogger(DeviceScanner.class);
    private HikvisionSDK sdk;
    private Database database;
    private DeviceManager deviceManager;
    private Config.ScannerConfig config;
    private Config.DeviceConfig deviceConfig;
    private int listenHandle = -1;
    private boolean running = false;
    private Consumer<DeviceInfo> deviceFoundCallback;
    private ExecutorService scanExecutor;
    private boolean scanning = false;

    // SDK消息类型常量
    private static final int NET_DVR_DEVICE_ADD = 0x1000; // 设备上线
    private static final int NET_DVR_DEVICE_OFFLINE = 0x1001; // 设备离线

    public DeviceScanner(HikvisionSDK sdk, Database database, DeviceManager deviceManager,
            Config.ScannerConfig scannerConfig, Config.DeviceConfig deviceConfig) {
        this.sdk = sdk;
        this.database = database;
        this.deviceManager = deviceManager;
        this.config = scannerConfig;
        this.deviceConfig = deviceConfig;
    }

    /**
     * 启动设备扫描
     */
    public boolean start() {
        if (running) {
            logger.warn("设备扫描器已在运行");
            return true;
        }

        if (!config.isEnabled()) {
            logger.info("设备扫描功能已禁用");
            return false;
        }

        try {
            HCNetSDK hcNetSDK = sdk.getSDK();
            if (hcNetSDK == null) {
                logger.error("SDK未初始化");
                return false;
            }

            // 创建消息回调
            DeviceMessageCallback callback = new DeviceMessageCallback();

            // 启动监听
            listenHandle = hcNetSDK.NET_DVR_StartListen_V30(
                    config.getListenIp(),
                    (short) config.getListenPort(),
                    callback,
                    null);

            if (listenHandle < 0) {
                logger.error("启动设备监听失败，错误码: {}", sdk.getLastError());
                return false;
            }

            running = true;
            logger.info("设备扫描器已启动 - 监听地址: {}:{}", config.getListenIp(), config.getListenPort());

            // 启动主动扫描
            startActiveScanning();

            return true;

        } catch (Exception e) {
            logger.error("启动设备扫描器失败", e);
            return false;
        }
    }

    /**
     * 停止设备扫描
     */
    public void stop() {
        if (!running) {
            return;
        }

        try {
            if (listenHandle >= 0) {
                HCNetSDK hcNetSDK = sdk.getSDK();
                if (hcNetSDK != null) {
                    hcNetSDK.NET_DVR_StopListen_V30(listenHandle);
                }
                listenHandle = -1;
            }
            if (scanExecutor != null && !scanExecutor.isShutdown()) {
                scanExecutor.shutdown();
                try {
                    if (!scanExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                        scanExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scanExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                logger.info("设备扫描器线程池已关闭");
            }
            running = false;
            logger.info("设备扫描器已停止");
        } catch (Exception e) {
            logger.error("停止设备扫描器失败", e);
        }
    }

    /**
     * 设置设备发现回调
     */
    public void setDeviceFoundCallback(Consumer<DeviceInfo> callback) {
        this.deviceFoundCallback = callback;
    }

    /**
     * 处理发现的设备
     * 必须登录成功后才保存到数据库
     */
    private void handleDeviceFound(String ip, int port, String deviceName) {
        // 如果配置了网段过滤，检查发现的设备 IP 是否在网段内
        String scanSegment = config.getScanSegment();
        if (scanSegment != null && !scanSegment.isEmpty() && !ip.startsWith(scanSegment)) {
            logger.debug("发现设备但被过滤（不在指定网段 {}）: {}", scanSegment, ip);
            return;
        }

        try {
            // 检查设备是否已存在（按 ip:port）
            DeviceInfo existingDevice = database.getDeviceByIpPort(ip, port);
            if (existingDevice != null) {
                // 更新最后发现时间（使用国标 device_id）
                database.updateLastSeen(existingDevice.getDeviceId());
                logger.debug("设备已存在，更新最后发现时间: {}:{}", ip, port);
                return;
            }

            // 尝试使用不同品牌的认证配置进行登录
            boolean loginSuccess = false;
            DeviceInfo device = null;
            String detectedBrand = DeviceInfo.BRAND_AUTO;

            // 尝试不同品牌的配置
            Config.BrandPreset[] brandPresets = {
                deviceConfig.getHikvision(),
                deviceConfig.getTiandy(),
                deviceConfig.getDahua()
            };
            String[] brandNames = {DeviceInfo.BRAND_HIKVISION, DeviceInfo.BRAND_TIANDY, DeviceInfo.BRAND_DAHUA};

            for (int i = 0; i < brandPresets.length; i++) {
                Config.BrandPreset preset = brandPresets[i];
                String brand = brandNames[i];
                int brandPort = preset.getPort();
                String brandUsername = preset.getUsername();
                String brandPassword = preset.getPassword();

                // 创建临时设备信息用于登录尝试（device_id 仅用于登录尝试，新设备保存前会赋国标 ID）
                DeviceInfo tempDevice = new DeviceInfo();
                tempDevice.setDeviceId(ip);
                tempDevice.setIp(ip);
                tempDevice.setPort(brandPort);
                tempDevice.setUsername(brandUsername);
                tempDevice.setPassword(brandPassword);
                tempDevice.setBrand(brand);
                tempDevice.setStatus(0);
                tempDevice.setUserId(-1);

                logger.debug("尝试使用品牌 {} 的配置登录设备: {}:{} (用户名: {})", brand, ip, brandPort, brandUsername);

                // 尝试登录
                if (deviceManager != null && deviceManager.loginDevice(tempDevice)) {
                    // 登录成功
                    device = tempDevice;
                    device.setName(deviceName != null ? deviceName : "未知设备");
                    detectedBrand = brand;
                    loginSuccess = true;

                    // 生成RTSP URL（使用RTSP端口，不是SDK端口）
                    int rtspPort = deviceConfig.getRtspPort();
                    device.setRtspUrl(String.format("rtsp://%s:%d/Streaming/Channels/101", ip, rtspPort));

                    logger.info("扫描发现设备并登录成功: {}:{} (品牌: {}, 端口: {})", ip, brandPort, brand, brandPort);
                    break;
                } else {
                    logger.debug("使用品牌 {} 的配置登录失败: {}:{}", brand, ip, brandPort);
                }
            }

            // 只有登录成功才保存到数据库
            if (loginSuccess && device != null) {
                // 新设备使用虚拟 ID，国标 ID 由用户在前端主动设置
                device.setDeviceId("v_" + java.util.UUID.randomUUID().toString().replace("-", ""));
                device.setIp(ip);
                // 保存到数据库
                database.saveOrUpdateDevice(device);
                logger.info("扫描发现的新设备已保存到数据库: {}:{} (品牌: {})", ip, device.getPort(), detectedBrand);

                // 触发回调
                if (deviceFoundCallback != null) {
                    deviceFoundCallback.accept(device);
                }
            } else {
                logger.debug("扫描发现的设备登录失败，未保存到数据库: {}:{}", ip, port);
            }

        } catch (Exception e) {
            logger.error("处理发现的设备失败: {}:{}", ip, port, e);
        }
    }

    /**
     * SDK消息回调实现
     */
    /**
     * SDK 消息回调在 native 线程执行；pAlarmer/pAlarmInfo 可能为 null，且仅在回调内有效。
     * 必须先判空并在访问 pAlarmer 字段前调用 pAlarmer.read()，回调内禁止调用任何海康 SDK 接口。
     */
    private class DeviceMessageCallback implements HCNetSDK.FMSGCallBack {
        @Override
        public void invoke(int lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen,
                Pointer pUser) {
            try {
                if (pAlarmer == null) {
                    logger.debug("设备消息回调 pAlarmer 为 null, lCommand=0x{}", Integer.toHexString(lCommand));
                    return;
                }
                pAlarmer.read();
                if (lCommand == NET_DVR_DEVICE_ADD) {
                    // 设备上线
                    if (pAlarmInfo != null && dwBufLen > 0) {
                        byte[] buffer = pAlarmInfo.getByteArray(0, Math.min(dwBufLen, 256));
                        String message = new String(buffer, StandardCharsets.UTF_8).trim();
                        logger.debug("收到设备上线消息: {}", message);
                    }
                    if (pAlarmer.byDeviceIPValid == 1) {
                        String deviceIP = new String(pAlarmer.sDeviceIP, StandardCharsets.UTF_8).trim();
                        int nullIndex = deviceIP.indexOf('\0');
                        if (nullIndex >= 0) {
                            deviceIP = deviceIP.substring(0, nullIndex);
                        }
                        int port = deviceConfig.getDefaultPort();
                        if (pAlarmer.byLinkPortValid == 1) {
                            port = pAlarmer.wLinkPort & 0xFFFF;
                        }
                        String deviceName = null;
                        if (pAlarmer.byDeviceNameValid == 1) {
                            deviceName = new String(pAlarmer.sDeviceName, StandardCharsets.UTF_8).trim();
                            int nn = deviceName.indexOf('\0');
                            if (nn >= 0) {
                                deviceName = deviceName.substring(0, nn);
                            }
                        }
                        if (!deviceIP.isEmpty()) {
                            handleDeviceFound(deviceIP, port, deviceName);
                        }
                    }
                } else if (lCommand == NET_DVR_DEVICE_OFFLINE) {
                    logger.debug("收到设备离线消息");
                }
            } catch (Exception e) {
                logger.error("处理设备消息失败", e);
            }
        }
    }

    /**
     * 启动主动扫描
     * 定期扫描局域网中的设备（IP段10-100）
     */
    private void startActiveScanning() {
        if (scanExecutor == null) {
            scanExecutor = Executors.newFixedThreadPool(10); // 使用10个线程并发扫描
        }

        // 立即执行一次扫描
        scanNetwork();

        // 如果配置了扫描间隔，定期执行扫描
        if (config.getInterval() > 0) {
            Thread scanThread = new Thread(() -> {
                while (running) {
                    try {
                        Thread.sleep(config.getInterval() * 1000L);
                        if (running) {
                            scanNetwork();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "DeviceScanner-ActiveScan");
            scanThread.setDaemon(true);
            scanThread.start();
        }
    }

    /**
     * 主动扫描局域网设备
     * 扫描范围：当前网段的10-100
     */
    private void scanNetwork() {
        if (scanning) {
            logger.debug("扫描正在进行中，跳过本次扫描");
            return;
        }

        try {
            scanning = true;
            String networkSegment = getCurrentNetworkSegment();
            if (networkSegment == null) {
                logger.warn("无法获取当前网段，跳过主动扫描");
                return;
            }

            logger.info("开始主动扫描局域网设备，网段: {}，IP范围: {}.{}-{}",
                    networkSegment, networkSegment, config.getScanRangeStart(), config.getScanRangeEnd());

            // 扫描IP范围：10-100
            for (int i = config.getScanRangeStart(); i <= config.getScanRangeEnd(); i++) {
                final String ip = networkSegment + "." + i;
                final int defaultPort = deviceConfig.getDefaultPort();

                // 使用线程池并发扫描
                scanExecutor.submit(() -> scanDevice(ip, defaultPort));
            }

            logger.info("主动扫描任务已提交，IP范围: {}.{}-{}",
                    networkSegment, config.getScanRangeStart(), config.getScanRangeEnd());
        } catch (Exception e) {
            logger.error("启动主动扫描失败", e);
        } finally {
            // 延迟重置扫描标志，避免频繁扫描
            scanExecutor.submit(() -> {
                try {
                    Thread.sleep(5000); // 等待5秒后重置标志
                    scanning = false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    /**
     * 获取当前主网段（例如：192.168.1）
     * 
     * @return 网段字符串，如果无法获取则返回null
     */
    private String getCurrentNetworkSegment() {
        // 优先使用配置中指定的网段
        String configSegment = config.getScanSegment();
        if (configSegment != null && !configSegment.isEmpty()) {
            logger.info("使用配置指定的扫描网段: {}", configSegment);
            return configSegment;
        }

        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                // 跳过回环接口和未启用的接口
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();

                    // 只处理IPv4地址
                    if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
                        String hostAddress = inetAddress.getHostAddress();
                        // 提取网段（例如：192.168.1.100 -> 192.168.1）
                        int lastDotIndex = hostAddress.lastIndexOf('.');
                        if (lastDotIndex > 0) {
                            String segment = hostAddress.substring(0, lastDotIndex);
                            logger.debug("获取到当前网段: {}", segment);
                            return segment;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            logger.error("获取网络接口失败", e);
        }

        logger.warn("无法获取当前网段，使用默认网段: 192.168.1");
        return "192.168.1"; // 默认网段
    }

    /**
     * 扫描单个设备
     * 使用不同品牌的端口进行TCP连接测试，如果连接成功则尝试登录
     */
    private void scanDevice(String ip, int defaultPort) {
        try {
            // 获取不同品牌的端口配置
            Config.BrandPreset[] brandPresets = {
                deviceConfig.getHikvision(),
                deviceConfig.getTiandy(),
                deviceConfig.getDahua()
            };
            String[] brandNames = {DeviceInfo.BRAND_HIKVISION, DeviceInfo.BRAND_TIANDY, DeviceInfo.BRAND_DAHUA};

            boolean deviceFound = false;

            // 尝试不同品牌的端口
            for (int i = 0; i < brandPresets.length; i++) {
                Config.BrandPreset preset = brandPresets[i];
                int brandPort = preset.getPort();

                // 使用TCP连接测试设备是否在线
                java.net.Socket socket = new java.net.Socket();
                socket.setSoTimeout(2000); // 2秒超时
                boolean connected = false;

                try {
                    socket.connect(new java.net.InetSocketAddress(ip, brandPort), 2000);
                    connected = true;
                } catch (Exception e) {
                    // 连接失败，设备可能不在线或端口未开放
                    logger.debug("设备连接失败: {}:{} (品牌: {}) - {}", ip, brandPort, brandNames[i], e.getMessage());
                } finally {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }

                if (connected) {
                    logger.debug("发现设备在线: {}:{} (品牌: {})", ip, brandPort, brandNames[i]);
                    // 设备在线，尝试登录并保存到数据库
                    handleDeviceFound(ip, brandPort, null);
                    deviceFound = true;
                    break; // 找到一个可用的端口就停止
                }
            }

            // 如果所有品牌端口都失败，尝试使用默认端口
            if (!deviceFound) {
                java.net.Socket socket = new java.net.Socket();
                socket.setSoTimeout(2000);
                boolean connected = false;

                try {
                    socket.connect(new java.net.InetSocketAddress(ip, defaultPort), 2000);
                    connected = true;
                } catch (Exception e) {
                    logger.debug("设备连接失败: {}:{} (默认端口) - {}", ip, defaultPort, e.getMessage());
                } finally {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }

                if (connected) {
                    logger.debug("发现设备在线: {}:{} (默认端口)", ip, defaultPort);
                    // 设备在线，尝试登录并保存到数据库
                    handleDeviceFound(ip, defaultPort, null);
                }
            }
        } catch (Exception e) {
            logger.debug("扫描设备失败: {}:{} - {}", ip, defaultPort, e.getMessage());
        }
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 手动扫描（不写入数据库）
     * @param session 扫描会话，用于更新进度
     * @return 扫描到的设备列表
     */
    public java.util.List<com.digital.video.gateway.api.ScannerController.ScannedDevice> manualScan(
            com.digital.video.gateway.api.ScannerController.ScanSession session) {
        java.util.List<com.digital.video.gateway.api.ScannerController.ScannedDevice> results = 
                new java.util.ArrayList<>();
        
        try {
            String networkSegment = getCurrentNetworkSegment();
            if (networkSegment == null) {
                logger.warn("无法获取当前网段，跳过手动扫描");
                return results;
            }

            logger.info("开始手动扫描，网段: {}，IP范围: {}.{}-{}",
                    networkSegment, networkSegment, config.getScanRangeStart(), config.getScanRangeEnd());

            // 使用线程池并发扫描
            if (scanExecutor == null) {
                scanExecutor = Executors.newFixedThreadPool(10);
            }

            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(
                    config.getScanRangeEnd() - config.getScanRangeStart() + 1);

            // 扫描IP范围
            for (int i = config.getScanRangeStart(); i <= config.getScanRangeEnd(); i++) {
                final String ip = networkSegment + "." + i;
                final int defaultPort = deviceConfig.getDefaultPort();

                scanExecutor.submit(() -> {
                    try {
                        java.util.List<com.digital.video.gateway.api.ScannerController.ScannedDevice> deviceResults = 
                                scanDeviceForManual(ip, defaultPort);
                        synchronized (results) {
                            results.addAll(deviceResults);
                            session.setTotalScanned(session.getTotalScanned() + 1);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // 等待所有扫描完成（最多等待5分钟）
            latch.await(5, java.util.concurrent.TimeUnit.MINUTES);

            logger.info("手动扫描完成，发现设备: {}", results.size());
            return results;
        } catch (Exception e) {
            logger.error("手动扫描失败", e);
            return results;
        }
    }

    /**
     * 扫描单个设备（手动扫描模式，不写入数据库）
     */
    private java.util.List<com.digital.video.gateway.api.ScannerController.ScannedDevice> scanDeviceForManual(
            String ip, int defaultPort) {
        java.util.List<com.digital.video.gateway.api.ScannerController.ScannedDevice> results = 
                new java.util.ArrayList<>();
        
        try {
            // 获取不同品牌的端口配置
            Config.BrandPreset[] brandPresets = {
                deviceConfig.getHikvision(),
                deviceConfig.getTiandy(),
                deviceConfig.getDahua()
            };
            String[] brandNames = {DeviceInfo.BRAND_HIKVISION, DeviceInfo.BRAND_TIANDY, DeviceInfo.BRAND_DAHUA};

            // 尝试不同品牌的端口
            for (int i = 0; i < brandPresets.length; i++) {
                Config.BrandPreset preset = brandPresets[i];
                int brandPort = preset.getPort();
                String brand = brandNames[i];

                // 使用TCP连接测试设备是否在线
                java.net.Socket socket = new java.net.Socket();
                socket.setSoTimeout(2000);
                boolean connected = false;

                try {
                    socket.connect(new java.net.InetSocketAddress(ip, brandPort), 2000);
                    connected = true;
                } catch (Exception e) {
                    logger.debug("设备连接失败: {}:{} (品牌: {}) - {}", ip, brandPort, brand, e.getMessage());
                } finally {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }

                if (connected) {
                    logger.debug("发现设备在线: {}:{} (品牌: {})", ip, brandPort, brand);
                    
                    // 尝试登录
                    com.digital.video.gateway.api.ScannerController.ScannedDevice scannedDevice = 
                            tryLoginForManual(ip, brandPort, brand, preset);
                    if (scannedDevice != null) {
                        results.add(scannedDevice);
                        break; // 找到一个可用的端口就停止
                    }
                }
            }

            // 如果所有品牌端口都失败，尝试使用默认端口
            if (results.isEmpty()) {
                java.net.Socket socket = new java.net.Socket();
                socket.setSoTimeout(2000);
                boolean connected = false;

                try {
                    socket.connect(new java.net.InetSocketAddress(ip, defaultPort), 2000);
                    connected = true;
                } catch (Exception e) {
                    logger.debug("设备连接失败: {}:{} (默认端口) - {}", ip, defaultPort, e.getMessage());
                } finally {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }

                if (connected) {
                    logger.debug("发现设备在线: {}:{} (默认端口)", ip, defaultPort);
                    // 尝试使用默认配置登录
                    Config.BrandPreset defaultPreset = deviceConfig.getHikvision();
                    com.digital.video.gateway.api.ScannerController.ScannedDevice scannedDevice = 
                            tryLoginForManual(ip, defaultPort, DeviceInfo.BRAND_AUTO, defaultPreset);
                    if (scannedDevice != null) {
                        results.add(scannedDevice);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("扫描设备失败: {}:{} - {}", ip, defaultPort, e.getMessage());
        }

        return results;
    }

    /**
     * 尝试登录设备（手动扫描模式，不写入数据库）
     */
    private com.digital.video.gateway.api.ScannerController.ScannedDevice tryLoginForManual(
            String ip, int port, String brand, Config.BrandPreset preset) {
        try {
            String deviceId = ip;
            String brandUsername = preset.getUsername();
            String brandPassword = preset.getPassword();

            // 创建临时设备信息用于登录尝试
            DeviceInfo tempDevice = new DeviceInfo();
            tempDevice.setDeviceId(deviceId);
            tempDevice.setIp(ip);
            tempDevice.setPort(port);
            tempDevice.setUsername(brandUsername);
            tempDevice.setPassword(brandPassword);
            tempDevice.setBrand(brand);
            tempDevice.setStatus(0);
            tempDevice.setUserId(-1);

            logger.debug("尝试使用品牌 {} 的配置登录设备: {}:{} (用户名: {})", brand, ip, port, brandUsername);

            // 尝试登录（但不保存到数据库）
            boolean loginSuccess = false;
            String detectedBrand = brand;
            int userId = -1;
            String deviceName = "未知设备";
            String errorMessage = null;

            if (deviceManager != null) {
                // 使用不保存到数据库的登录方法
                boolean result = deviceManager.loginDeviceWithoutSave(tempDevice);
                if (result) {
                    loginSuccess = true;
                    detectedBrand = tempDevice.getBrand();
                    userId = tempDevice.getUserId();
                    deviceName = tempDevice.getName() != null ? tempDevice.getName() : "未知设备";
                    
                    // 登出设备（因为这是手动扫描，不保留登录状态）
                    deviceManager.logoutDevice(deviceId);
                } else {
                    errorMessage = "登录失败";
                }
            }

            // 创建扫描结果
            com.digital.video.gateway.api.ScannerController.ScannedDevice scannedDevice = 
                    new com.digital.video.gateway.api.ScannerController.ScannedDevice();
            scannedDevice.setIp(ip);
            scannedDevice.setPort(port);
            scannedDevice.setName(deviceName);
            scannedDevice.setBrand(detectedBrand);
            scannedDevice.setLoginSuccess(loginSuccess);
            scannedDevice.setUserId(userId);
            scannedDevice.setErrorMessage(errorMessage);
            scannedDevice.setUsername(brandUsername);
            scannedDevice.setPassword(brandPassword);

            if (loginSuccess) {
                logger.info("手动扫描发现设备并登录成功: {}:{} (品牌: {})", ip, port, detectedBrand);
            }

            return scannedDevice;
        } catch (Exception e) {
            logger.error("尝试登录设备失败: {}:{}", ip, port, e);
            com.digital.video.gateway.api.ScannerController.ScannedDevice scannedDevice = 
                    new com.digital.video.gateway.api.ScannerController.ScannedDevice();
            scannedDevice.setIp(ip);
            scannedDevice.setPort(port);
            scannedDevice.setName("未知设备");
            scannedDevice.setBrand(brand);
            scannedDevice.setLoginSuccess(false);
            scannedDevice.setErrorMessage(e.getMessage());
            return scannedDevice;
        }
    }

}
