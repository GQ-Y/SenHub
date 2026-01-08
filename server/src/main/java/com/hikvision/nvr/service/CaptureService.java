package com.hikvision.nvr.service;

import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.device.DeviceSDK;
import com.hikvision.nvr.database.DeviceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 抓图服务
 * 根据设备品牌选择对应的SDK实现抓图功能
 */
public class CaptureService {
    private static final Logger logger = LoggerFactory.getLogger(CaptureService.class);
    
    private final DeviceManager deviceManager;
    private final String captureDir;
    
    public CaptureService(DeviceManager deviceManager, String captureDir) {
        this.deviceManager = deviceManager;
        this.captureDir = captureDir != null ? captureDir : "./storage/captures";
        
        // 确保目录存在
        File dir = new File(this.captureDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    /**
     * 抓图
     * @param deviceId 设备ID
     * @param channel 通道号
     * @return 图片文件路径，失败返回null
     */
    public String captureSnapshot(String deviceId, int channel) {
        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) {
            logger.warn("设备不存在: {}", deviceId);
            return null;
        }
        
        // 确保设备已登录
        if (!deviceManager.isDeviceLoggedIn(deviceId)) {
            if (!deviceManager.loginDevice(device)) {
                logger.warn("设备未登录，无法抓图: {}", deviceId);
                return null;
            }
        }
        
        // 获取设备SDK
        DeviceSDK sdk = deviceManager.getDeviceSDK(deviceId);
        if (sdk == null) {
            logger.error("无法获取设备SDK: {}", deviceId);
            return null;
        }
        
        int userId = deviceManager.getDeviceUserId(deviceId);
        int actualChannel = channel > 0 ? channel : device.getChannel();
        
        try {
            // 生成文件名
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            String timestamp = sdf.format(new Date());
            String deviceIdForFile = deviceId.replace(".", "_").replace(":", "_");
            String fileName = captureDir + "/capture_" + deviceIdForFile + "_" + timestamp + ".jpg";
            
            // 清理旧图片（只保留最新一张）
            cleanupOldCaptures(deviceIdForFile);
            
            // 执行抓图
            // 注意：现在所有SDK都支持直接抓图，不需要预览连接
            // 天地伟业SDK已更新为使用NetClient_CapturePicByDevice，直接抓图不需要预览
            int connectId = -1;  // 不使用预览连接
            int pictureType = 2; // JPG格式
            
            boolean result = sdk.capturePicture(connectId, userId, actualChannel, fileName, pictureType);
            
            if (result) {
                // 等待文件写入完成
                Thread.sleep(1000);
                
                File picFile = new File(fileName);
                if (picFile.exists()) {
                    logger.info("抓图成功: deviceId={}, channel={}, filePath={}", deviceId, actualChannel, fileName);
                    return picFile.getAbsolutePath();
                } else {
                    logger.warn("抓图文件未生成: {}", fileName);
                    return null;
                }
            } else {
                logger.error("抓图失败: deviceId={}, channel={}", deviceId, actualChannel);
                return null;
            }
        } catch (Exception e) {
            logger.error("抓图异常: deviceId={}, channel={}", deviceId, channel, e);
            return null;
        }
    }
    
    /**
     * 清理旧抓图文件（只保留最新一张）
     */
    private void cleanupOldCaptures(String deviceIdForFile) {
        try {
            File dir = new File(captureDir);
            String prefix = "capture_" + deviceIdForFile + "_";
            File[] oldFiles = dir.listFiles((d, name) -> 
                name.startsWith(prefix) && name.endsWith(".jpg"));
            
            if (oldFiles != null && oldFiles.length > 0) {
                int deletedCount = 0;
                for (File oldFile : oldFiles) {
                    if (oldFile.delete()) {
                        deletedCount++;
                    }
                }
                if (deletedCount > 0) {
                    logger.debug("清理了 {} 个旧抓图文件: {}", deletedCount, deviceIdForFile);
                }
            }
        } catch (Exception e) {
            logger.warn("清理旧抓图文件失败", e);
        }
    }
}
