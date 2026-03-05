package com.digital.video.gateway.service;

import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import com.digital.video.gateway.database.DeviceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * 抓图服务
 * 根据设备品牌选择对应的SDK实现抓图功能
 * 支持同步和异步两种抓图模式
 */
public class CaptureService {
    private static final Logger logger = LoggerFactory.getLogger(CaptureService.class);

    private final DeviceManager deviceManager;
    private final String captureDir;

    private final ExecutorService captureExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "CaptureWorker");
        t.setDaemon(true);
        return t;
    });

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
     * 关闭抓图线程池（用于进程优雅退出）
     */
    public void shutdown() {
        if (captureExecutor != null && !captureExecutor.isShutdown()) {
            captureExecutor.shutdown();
            try {
                if (!captureExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    captureExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                captureExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("抓图服务线程池已关闭");
        }
    }

    /**
     * 抓图
     * 
     * @param deviceId 设备ID
     * @param channel  通道号
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

        // 天地伟业：健康检查未通过前不执行抓图
        if (DeviceInfo.BRAND_TIANDY.equalsIgnoreCase(device.getBrand())) {
            DeviceInfo.HealthStatus health = deviceManager.checkTiandyDeviceHealth(deviceId);
            if (health != DeviceInfo.HealthStatus.HEALTHY) {
                logger.warn("天地伟业设备健康检查未通过，跳过抓图: deviceId={}, 状态={}", deviceId, health);
                return null;
            }
        }

        int userId = deviceManager.getDeviceUserId(deviceId);
        // 天地伟业抓图时打一条映射日志，便于确认 deviceId -> userId -> 设备 IP 对应关系
        if ("tiandy".equalsIgnoreCase(device.getBrand())) {
            logger.info("天地伟业抓图请求: deviceId={}, ip={}, userId={}, channel={}",
                    deviceId, device.getIp(), userId, channel);
        }
        // 根据设备品牌确定实际通道号
        // 天地伟业设备通道号从0开始，如果传入的是默认值1且没有明确配置，则使用0
        int actualChannel;
        if (channel > 0) {
            actualChannel = channel;
        } else {
            actualChannel = device.getChannel();
        }

        // 天地伟业设备特殊处理：通道号从0开始，如果传入的是1，改为0
        if ("tiandy".equalsIgnoreCase(device.getBrand()) && actualChannel == 1) {
            actualChannel = 0;
            logger.info("天地伟业设备通道号调整为0: deviceId={}, 原通道号={}", deviceId, channel);
        }

        try {
            // 生成文件名（天地伟业 NetClient_CapturePicByDevice 要求传入绝对路径并附带扩展名）
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            String timestamp = sdf.format(new Date());
            String deviceIdForFile = deviceId.replace(".", "_").replace(":", "_");
            String fileName = new File(captureDir, "capture_" + deviceIdForFile + "_" + timestamp + ".jpg")
                    .getAbsolutePath();

            // 清理旧图片（只保留最新一张）
            cleanupOldCaptures(deviceIdForFile);

            // 执行抓图（带超时，避免天地伟业 SDK 阻塞导致接口无响应）
            // 天地伟业：与 Demo 一致，仅在有预览连接时抓图；无预览前不执行任何抓图流程
            boolean isTiandy = "tiandy".equalsIgnoreCase(device.getBrand());
            int connectId = -1;
            if (isTiandy) {
                connectId = deviceManager.getTiandyPreviewConnectId(deviceId);
                if (connectId < 0) {
                    connectId = deviceManager.ensureTiandyPreview(deviceId);
                }
                if (connectId < 0) {
                    logger.warn("天地伟业设备无预览连接且重建失败，跳过抓图: deviceId={}", deviceId);
                    return null;
                }
                logger.info("天地伟业同步抓图使用预览连接: deviceId={}, connectId={}", deviceId, connectId);
            }
            int pictureType = 2; // JPG格式
            final int channelForCapture = actualChannel;
            final int connectIdForCapture = connectId;

            boolean result = sdk.capturePicture(connectIdForCapture, userId, channelForCapture, fileName, pictureType);

            if (result) {
                File picFile = new File(fileName);
                if (picFile.exists()) {
                    logger.info("抓图成功: deviceId={}, channel={}, filePath={}", deviceId, actualChannel, fileName);
                    return compressIfNeeded(picFile.getAbsolutePath());
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
     * 异步抓图
     * 
     * @param deviceId 设备ID
     * @param channel  通道号
     * @param callback 回调函数，参数为(图片路径, 是否成功)
     */
    public void captureAsync(String deviceId, int channel, BiConsumer<String, Boolean> callback) {
        if (callback == null) {
            logger.warn("异步抓图回调为空，忽略请求: deviceId={}", deviceId);
            return;
        }

        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) {
            logger.warn("设备不存在: {}", deviceId);
            callback.accept(null, false);
            return;
        }

        // 确保设备已登录
        if (!deviceManager.isDeviceLoggedIn(deviceId)) {
            if (!deviceManager.loginDevice(device)) {
                logger.warn("设备未登录，无法抓图: {}", deviceId);
                callback.accept(null, false);
                return;
            }
        }

        // 获取设备SDK
        DeviceSDK sdk = deviceManager.getDeviceSDK(deviceId);
        if (sdk == null) {
            logger.error("无法获取设备SDK: {}", deviceId);
            callback.accept(null, false);
            return;
        }

        // 天地伟业：健康检查未通过前不执行抓图
        if (DeviceInfo.BRAND_TIANDY.equalsIgnoreCase(device.getBrand())) {
            DeviceInfo.HealthStatus health = deviceManager.checkTiandyDeviceHealth(deviceId);
            if (health != DeviceInfo.HealthStatus.HEALTHY) {
                logger.warn("天地伟业设备健康检查未通过，跳过抓图: deviceId={}, 状态={}", deviceId, health);
                callback.accept(null, false);
                return;
            }
        }

        int userId = deviceManager.getDeviceUserId(deviceId);
        // 根据设备品牌确定实际通道号
        // 天地伟业设备通道号从0开始，如果传入的是默认值1且没有明确配置，则使用0
        int actualChannel;
        if (channel > 0) {
            actualChannel = channel;
        } else {
            actualChannel = device.getChannel();
        }

        // 天地伟业设备特殊处理：通道号从0开始，如果传入的是1，改为0
        if ("tiandy".equalsIgnoreCase(device.getBrand()) && actualChannel == 1) {
            actualChannel = 0;
            logger.info("天地伟业设备通道号调整为0: deviceId={}, 原通道号={}", deviceId, channel);
        }

        // 创建 final 变量供 lambda 使用
        final int finalUserId = userId;
        final int finalActualChannel = actualChannel;
        final DeviceSDK finalSdk = sdk;
        final String finalDeviceId = deviceId;
        final BiConsumer<String, Boolean> finalCallback = callback;

        final boolean isTiandy = device != null && "tiandy".equalsIgnoreCase(device.getBrand());
        captureExecutor.submit(() -> {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                String timestamp = sdf.format(new Date());
                String deviceIdForFile = finalDeviceId.replace(".", "_").replace(":", "_");
                String fileName = new File(captureDir, "capture_" + deviceIdForFile + "_" + timestamp + ".jpg")
                        .getAbsolutePath();

                cleanupOldCaptures(deviceIdForFile);

                int connectId = -1;
                if (isTiandy) {
                    connectId = deviceManager.getTiandyPreviewConnectId(finalDeviceId);
                    if (connectId < 0) {
                        connectId = deviceManager.ensureTiandyPreview(finalDeviceId);
                    }
                    if (connectId < 0) {
                        logger.warn("天地伟业设备无预览连接且重建失败，跳过抓图: deviceId={}", finalDeviceId);
                        finalCallback.accept(null, false);
                        return;
                    }
                }
                int pictureType = 2; // JPG格式

                boolean result = finalSdk.capturePicture(connectId, finalUserId, finalActualChannel, fileName, pictureType);

                if (result) {
                    File picFile = new File(fileName);
                    if (picFile.exists()) {
                        logger.info("异步抓图成功: deviceId={}, channel={}, filePath={}", finalDeviceId, finalActualChannel, picFile.getAbsolutePath());
                        finalCallback.accept(compressIfNeeded(picFile.getAbsolutePath()), true);
                    } else {
                        logger.warn("抓图文件未生成: {}", fileName);
                        finalCallback.accept(null, false);
                    }
                } else {
                    logger.error("异步抓图失败: deviceId={}, channel={}", finalDeviceId, finalActualChannel);
                    finalCallback.accept(null, false);
                }
            } catch (Exception e) {
                logger.error("异步抓图异常: deviceId={}, channel={}", finalDeviceId, channel, e);
                finalCallback.accept(null, false);
            }
        });
    }

    /**
     * 清理旧抓图文件（只保留最新一张）
     */
    private void cleanupOldCaptures(String deviceIdForFile) {
        try {
            File dir = new File(captureDir);
            String prefix = "capture_" + deviceIdForFile + "_";
            File[] oldFiles = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(".jpg"));

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

    /** 目标文件大小上限：600 KB */
    private static final long MAX_CAPTURE_SIZE_BYTES = 600 * 1024L;

    /**
     * 对抓图文件进行无损质量递减压缩，确保文件不超过 600 KB。
     * 采用二分查找快速定位合适的 JPEG quality，避免多次全量重编码。
     *
     * @param filePath 原始抓图的绝对路径（.jpg）
     * @return 压缩后文件路径（与入参相同，原地替换）；若压缩失败则返回原路径
     */
    private String compressIfNeeded(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return filePath;

            long fileSize = file.length();
            if (fileSize <= MAX_CAPTURE_SIZE_BYTES) {
                // 文件已经在目标大小内，无需压缩
                return filePath;
            }

            logger.info("抓图大小 {}KB 超过 600KB，开始压缩: {}", fileSize / 1024, filePath);

            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                logger.warn("无法读取图片用于压缩: {}", filePath);
                return filePath;
            }

            // 获取 JPEG ImageWriter
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                logger.warn("未找到 JPEG ImageWriter，跳过压缩");
                return filePath;
            }
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);

            // 二分查找合适的 quality（精度 0.02，最多约 6 次迭代）
            float lo = 0.30f, hi = 0.95f, quality = 0.85f;
            File tempFile = new File(filePath + ".tmp");
            for (int iter = 0; iter < 8; iter++) {
                quality = (lo + hi) / 2f;
                param.setCompressionQuality(quality);
                try (FileImageOutputStream fios = new FileImageOutputStream(tempFile)) {
                    writer.setOutput(fios);
                    writer.write(null, new IIOImage(image, null, null), param);
                }
                long sz = tempFile.length();
                if (sz <= MAX_CAPTURE_SIZE_BYTES) {
                    lo = quality; // 还可以提高质量
                } else {
                    hi = quality; // 需要继续降低质量
                }
                if ((hi - lo) < 0.02f) break;
            }

            writer.dispose();

            // 用压缩结果替换原文件
            if (tempFile.exists() && tempFile.length() > 0) {
                if (file.delete() && tempFile.renameTo(file)) {
                    logger.info("抓图压缩完成: quality={}, 压缩后大小={}KB, 路径={}",
                            String.format("%.2f", quality), file.length() / 1024, filePath);
                } else {
                    logger.warn("替换压缩文件失败，保留原文件: {}", filePath);
                    tempFile.delete();
                }
            } else {
                logger.warn("压缩结果文件异常，保留原文件: {}", filePath);
                if (tempFile.exists()) tempFile.delete();
            }
        } catch (Exception e) {
            logger.warn("抓图压缩失败，使用原始文件: {}", filePath, e);
        }
        return filePath;
    }
}
