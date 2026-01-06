package com.hikvision.nvr.scanner;

import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.database.Database;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.hikvision.HCNetSDK;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 设备扫描器
 * 使用SDK的监听功能来发现局域网中的海康设备
 */
public class DeviceScanner {
    private static final Logger logger = LoggerFactory.getLogger(DeviceScanner.class);
    private HikvisionSDK sdk;
    private Database database;
    private Config.ScannerConfig config;
    private Config.DeviceConfig deviceConfig;
    private int listenHandle = -1;
    private boolean running = false;
    private Consumer<DeviceInfo> deviceFoundCallback;

    // SDK消息类型常量
    private static final int NET_DVR_DEVICE_ADD = 0x1000; // 设备上线
    private static final int NET_DVR_DEVICE_OFFLINE = 0x1001; // 设备离线

    public DeviceScanner(HikvisionSDK sdk, Database database, Config.ScannerConfig scannerConfig, Config.DeviceConfig deviceConfig) {
        this.sdk = sdk;
        this.database = database;
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
                null
            );

            if (listenHandle < 0) {
                logger.error("启动设备监听失败，错误码: {}", sdk.getLastError());
                return false;
            }

            running = true;
            logger.info("设备扫描器已启动 - 监听地址: {}:{}", config.getListenIp(), config.getListenPort());
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
     */
    private void handleDeviceFound(String ip, int port, String deviceName) {
        try {
            // 生成设备ID（使用IP地址）
            String deviceId = ip;

            // 检查设备是否已存在
            DeviceInfo existingDevice = database.getDeviceByIpPort(ip, port);
            if (existingDevice != null) {
                // 更新最后发现时间
                database.updateLastSeen(deviceId);
                logger.debug("设备已存在，更新最后发现时间: {}:{}", ip, port);
                return;
            }

            // 创建新设备信息
            DeviceInfo device = new DeviceInfo();
            device.setDeviceId(deviceId);
            device.setIp(ip);
            device.setPort(port);
            device.setName(deviceName != null ? deviceName : "未知设备");
            device.setUsername(deviceConfig.getDefaultUsername());
            device.setPassword(deviceConfig.getDefaultPassword());
            device.setStatus("offline");
            device.setUserId(-1);

            // 生成RTSP URL
            device.setRtspUrl(String.format("rtsp://%s:%d/Streaming/Channels/101", ip, port));

            // 保存到数据库
            database.saveOrUpdateDevice(device);
            logger.info("发现新设备: {}:{} ({})", ip, port, device.getName());

            // 触发回调
            if (deviceFoundCallback != null) {
                deviceFoundCallback.accept(device);
            }

        } catch (Exception e) {
            logger.error("处理发现的设备失败: {}:{}", ip, port, e);
        }
    }

    /**
     * SDK消息回调实现
     */
    private class DeviceMessageCallback implements HCNetSDK.FMSGCallBack {
        @Override
        public void invoke(int lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser) {
            try {
                if (lCommand == NET_DVR_DEVICE_ADD) {
                    // 设备上线
                    if (pAlarmInfo != null && dwBufLen > 0) {
                        // 从消息中提取设备信息
                        // 注意：实际的消息格式需要根据SDK文档来确定
                        // 这里是一个简化的实现
                        byte[] buffer = pAlarmInfo.getByteArray(0, Math.min(dwBufLen, 256));
                        String message = new String(buffer, StandardCharsets.UTF_8).trim();
                        
                        // 解析设备信息（简化处理，实际需要根据SDK消息格式解析）
                        // 通常消息包含IP、端口等信息
                        logger.debug("收到设备上线消息: {}", message);
                        
                        // 从pAlarmer中提取设备信息
                        if (pAlarmer != null && pAlarmer.byDeviceIPValid == 1) {
                            String deviceIP = new String(pAlarmer.sDeviceIP, StandardCharsets.UTF_8).trim();
                            int port = deviceConfig.getDefaultPort();
                            if (pAlarmer.byLinkPortValid == 1) {
                                port = pAlarmer.wLinkPort;
                            }
                            String deviceName = null;
                            if (pAlarmer.byDeviceNameValid == 1) {
                                deviceName = new String(pAlarmer.sDeviceName, StandardCharsets.UTF_8).trim();
                            }
                            if (!deviceIP.isEmpty()) {
                                handleDeviceFound(deviceIP, port, deviceName);
                            }
                        }
                    }
                } else if (lCommand == NET_DVR_DEVICE_OFFLINE) {
                    // 设备离线
                    logger.debug("收到设备离线消息");
                    // 可以在这里更新设备状态为离线
                }
            } catch (Exception e) {
                logger.error("处理设备消息失败", e);
            }
        }
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return running;
    }
}
