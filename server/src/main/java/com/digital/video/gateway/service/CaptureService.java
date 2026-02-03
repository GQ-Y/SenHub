package com.digital.video.gateway.service;

import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import com.digital.video.gateway.database.DeviceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * 抓图服务
 * 根据设备品牌选择对应的SDK实现抓图功能
 * 支持同步和异步两种抓图模式
 */
public class CaptureService {
    private static final Logger logger = LoggerFactory.getLogger(CaptureService.class);
    
    // 抓图超时时间（毫秒）- SDK调用超时
    private static final int CAPTURE_TIMEOUT_MS = 8000;
    // 抓图冷却时间（毫秒）- 同一设备两次抓图之间的最小间隔
    private static final int CAPTURE_COOLDOWN_MS = 3000;
    
    private final DeviceManager deviceManager;
    private final String captureDir;
    
    // 设备级别锁，防止同一设备的并发抓图导致SDK崩溃
    private final ConcurrentHashMap<String, ReentrantLock> deviceLocks = new ConcurrentHashMap<>();
    // 设备最后抓图时间，用于冷却控制
    private final ConcurrentHashMap<String, Long> lastCaptureTime = new ConcurrentHashMap<>();
    // 设备最后抓图结果缓存（路径），用于冷却期间返回上次结果
    private final ConcurrentHashMap<String, String> lastCapturePath = new ConcurrentHashMap<>();
    
    // 用于异步执行抓图的线程池
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
     * 获取设备锁
     */
    private ReentrantLock getDeviceLock(String deviceId) {
        return deviceLocks.computeIfAbsent(deviceId, k -> new ReentrantLock());
    }
    
    /**
     * 检查设备是否在冷却期内
     * @return 如果在冷却期内返回true
     */
    private boolean isInCooldown(String deviceId) {
        Long lastTime = lastCaptureTime.get(deviceId);
        if (lastTime == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastTime) < CAPTURE_COOLDOWN_MS;
    }
    
    /**
     * 获取冷却期内的缓存结果
     */
    private String getCachedResult(String deviceId) {
        String cached = lastCapturePath.get(deviceId);
        if (cached != null && new File(cached).exists()) {
            return cached;
        }
        return null;
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
        
        // 使用设备级别锁防止并发抓图导致SDK崩溃
        // 等待最多5秒获取锁，如果超时则返回失败
        ReentrantLock lock = getDeviceLock(deviceId);
        try {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                logger.warn("设备正在抓图中，等待超时: {}", deviceId);
                return null;
            }
        } catch (InterruptedException e) {
            logger.warn("等待抓图锁被中断: {}", deviceId);
            Thread.currentThread().interrupt();
            return null;
        }
        
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
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 异步抓图
     * @param deviceId 设备ID
     * @param channel 通道号
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
        
        // 检查冷却期：如果短时间内已经抓过图，直接返回缓存结果
        if (isInCooldown(deviceId)) {
            String cached = getCachedResult(deviceId);
            if (cached != null) {
                logger.info("设备在冷却期内，返回缓存抓图: deviceId={}, path={}", deviceId, cached);
                callback.accept(cached, true);
                return;
            }
            logger.debug("设备在冷却期内但无缓存，继续抓图: {}", deviceId);
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
        
        // 提交异步抓图任务
        captureExecutor.submit(() -> {
            ReentrantLock lock = getDeviceLock(finalDeviceId);
            boolean lockAcquired = false;
            try {
                // 尝试获取锁，超时时间很短（500ms），避免等待太久
                if (!lock.tryLock(500, TimeUnit.MILLISECONDS)) {
                    // 锁获取失败，说明设备正在抓图中
                    // 等待一段时间（最多等待CAPTURE_TIMEOUT_MS），让当前抓图完成
                    logger.info("设备正在抓图中，等待当前抓图完成: deviceId={}", finalDeviceId);
                    long waitStart = System.currentTimeMillis();
                    long maxWaitTime = CAPTURE_TIMEOUT_MS;
                    
                    // 轮询检查缓存，直到有结果或超时
                    while (System.currentTimeMillis() - waitStart < maxWaitTime) {
                        try {
                            Thread.sleep(200); // 每200ms检查一次
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.warn("等待抓图完成被中断: {}", finalDeviceId);
                            finalCallback.accept(null, false);
                            return;
                        }
                        
                        // 检查是否有缓存结果
                        String cached = getCachedResult(finalDeviceId);
                        if (cached != null) {
                            logger.info("等待后获取到缓存结果: deviceId={}, path={}", finalDeviceId, cached);
                            finalCallback.accept(cached, true);
                            return;
                        }
                    }
                    
                    // 超时后仍无缓存，返回失败
                    logger.warn("等待抓图完成超时，无缓存结果: deviceId={}", finalDeviceId);
                    finalCallback.accept(null, false);
                    return;
                }
                lockAcquired = true;
                
                // 生成文件名
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                String timestamp = sdf.format(new Date());
                String deviceIdForFile = finalDeviceId.replace(".", "_").replace(":", "_");
                String fileName = captureDir + "/capture_" + deviceIdForFile + "_" + timestamp + ".jpg";
                
                // 清理旧图片（只保留最新一张）
                cleanupOldCaptures(deviceIdForFile);
                
                // 执行抓图（带超时控制）
                int connectId = -1;  // 不使用预览连接
                int pictureType = 2; // JPG格式
                
                // 使用 CompletableFuture 实现超时控制
                final String finalFileName = fileName;
                CompletableFuture<Boolean> captureFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return finalSdk.capturePicture(connectId, finalUserId, finalActualChannel, finalFileName, pictureType);
                    } catch (Exception e) {
                        logger.error("抓图执行异常: deviceId={}, channel={}", finalDeviceId, finalActualChannel, e);
                        return false;
                    }
                }, captureExecutor);
                
                boolean result;
                try {
                    result = captureFuture.get(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    captureFuture.cancel(true);
                    logger.error("抓图超时({}ms): deviceId={}, channel={}", CAPTURE_TIMEOUT_MS, finalDeviceId, finalActualChannel);
                    finalCallback.accept(null, false);
                    return;
                } catch (ExecutionException | InterruptedException e) {
                    logger.error("抓图执行异常: deviceId={}, channel={}", finalDeviceId, finalActualChannel, e);
                    finalCallback.accept(null, false);
                    return;
                }
                
                if (result) {
                    // 等待文件写入完成（减少等待时间）
                    Thread.sleep(200);
                    
                    File picFile = new File(finalFileName);
                    if (picFile.exists()) {
                        String absolutePath = picFile.getAbsolutePath();
                        logger.info("抓图成功: deviceId={}, channel={}, filePath={}", finalDeviceId, finalActualChannel, absolutePath);
                        
                        // 更新缓存
                        lastCaptureTime.put(finalDeviceId, System.currentTimeMillis());
                        lastCapturePath.put(finalDeviceId, absolutePath);
                        
                        finalCallback.accept(absolutePath, true);
                    } else {
                        logger.warn("抓图文件未生成: {}", finalFileName);
                        finalCallback.accept(null, false);
                    }
                } else {
                    logger.error("抓图失败: deviceId={}, channel={}", finalDeviceId, finalActualChannel);
                    finalCallback.accept(null, false);
                }
            } catch (InterruptedException e) {
                logger.warn("等待抓图锁被中断: {}", finalDeviceId);
                Thread.currentThread().interrupt();
                finalCallback.accept(null, false);
            } catch (Exception e) {
                logger.error("抓图异常: deviceId={}, channel={}", finalDeviceId, channel, e);
                finalCallback.accept(null, false);
            } finally {
                if (lockAcquired) {
                    lock.unlock();
                }
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
