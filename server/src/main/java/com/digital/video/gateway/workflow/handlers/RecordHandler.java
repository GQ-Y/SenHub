package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.database.RecordingTask;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.service.OssService;
import com.digital.video.gateway.service.RecorderService;
import com.digital.video.gateway.service.RecordingTaskService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * 录像下载节点 — 按设备品牌分别处理：
 *
 * - 海康(hikvision)：从 ZLM 本地循环录像的 MP4 分段中提取，避免 SDK SIGSEGV 崩溃
 * - 天地伟业(tiandy)/大华(dahua)/其他：通过 SDK downloadPlaybackByTimeRange 正常下载
 */
public class RecordHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(RecordHandler.class);

    private final RecordingTaskService recordingTaskService;
    private final OssService ossService;
    private RecorderService recorderService;
    private DeviceManager deviceManager;

    public RecordHandler(RecordingTaskService recordingTaskService) {
        this(recordingTaskService, null);
    }

    public RecordHandler(RecordingTaskService recordingTaskService, OssService ossService) {
        this.recordingTaskService = recordingTaskService;
        this.ossService = ossService;
    }

    public void setRecorderService(RecorderService recorderService) {
        this.recorderService = recorderService;
    }

    public void setDeviceManager(DeviceManager deviceManager) {
        this.deviceManager = deviceManager;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) {
        Map<String, Object> payload = context.getPayload();
        if (payload != null && Boolean.TRUE.equals(payload.get("test"))) {
            logger.info("测试模式，跳过录像节点: deviceId={}", context.getDeviceId());
            return true;
        }
        if (context.getDeviceId() == null) {
            logger.warn("缺少 deviceId，无法创建录像任务");
            return false;
        }

        // ---------- 解析配置 ----------
        Map<String, Object> cfg = node.getConfig();
        int channel = 1;
        long beforeSeconds = 15;
        long afterSeconds = 15;
        if (cfg != null) {
            if (cfg.get("channel") instanceof Number)
                channel = ((Number) cfg.get("channel")).intValue();
            if (cfg.get("duration") instanceof Number) {
                long d = ((Number) cfg.get("duration")).longValue();
                beforeSeconds = d / 2; afterSeconds = d - beforeSeconds;
            }
            if (cfg.get("beforeSeconds") instanceof Number)
                beforeSeconds = ((Number) cfg.get("beforeSeconds")).longValue();
            if (cfg.get("afterSeconds") instanceof Number)
                afterSeconds = ((Number) cfg.get("afterSeconds")).longValue();
            if (cfg.get("recordBeforeSeconds") instanceof Number)
                beforeSeconds = ((Number) cfg.get("recordBeforeSeconds")).longValue();
            if (cfg.get("recordAfterSeconds") instanceof Number)
                afterSeconds = ((Number) cfg.get("recordAfterSeconds")).longValue();
        }

        // ---------- 计算时间范围 ----------
        long alarmTime = System.currentTimeMillis();
        Object alarmTs = context.getVariables().get("alarmTimestamp");
        if (alarmTs instanceof Number) {
            alarmTime = ((Number) alarmTs).longValue();
            logger.info("使用原始报警时间戳: {}ms (当前偏移: {}ms)", alarmTime, System.currentTimeMillis() - alarmTime);
        }
        final long startMs = alarmTime - beforeSeconds * 1000;
        final long endMs   = alarmTime + afterSeconds * 1000;
        final String deviceId = context.getDeviceId();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        logger.info("录像节点开始: deviceId={}, channel={}, 报警时间={}, 范围=[{} ~ {}]",
                deviceId, channel, sdf.format(new Date(alarmTime)), sdf.format(new Date(startMs)), sdf.format(new Date(endMs)));

        // ---------- 判断设备品牌 ----------
        String brand = DeviceInfo.BRAND_HIKVISION;
        if (deviceManager != null) {
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device != null) {
                brand = device.getBrand() != null ? device.getBrand().toLowerCase() : "";
            }
        }

        String localPath = null;

        if (DeviceInfo.BRAND_HIKVISION.equals(brand) && recorderService != null) {
            // ========== 海康：从 ZLM 本地循环录像提取 ==========
            // ZLM 按 30s 切分段，正在写入的文件（.前缀）moov 未写入，FFmpeg 无法读取。
            // 策略：先等到 endMs 过去，然后每 3s 轮询一次，直到找到覆盖时间范围的已完成分段。
            long minWaitUntil = endMs + 2000L;
            long nowMs = System.currentTimeMillis();
            if (nowMs < minWaitUntil) {
                long w = minWaitUntil - nowMs;
                logger.info("等待 {}s 确保后段录像时间到达...", w / 1000);
                try { Thread.sleep(w); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            int maxAttempts = 20;
            int pollIntervalMs = 3000;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                localPath = recorderService.extractRecording(deviceId, startMs, endMs);
                if (localPath != null) {
                    logger.info("ZLM 本地录像提取成功（第{}次尝试）: {}", attempt, localPath);
                    break;
                }
                if (attempt < maxAttempts) {
                    logger.debug("ZLM 分段尚未就绪，{}s 后重试（{}/{}）", pollIntervalMs / 1000, attempt, maxAttempts);
                    try { Thread.sleep(pollIntervalMs); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
            if (localPath == null) {
                logger.error("ZLM 本地录像提取失败（已重试{}次）: deviceId={}", maxAttempts, deviceId);
                return false;
            }
        } else {
            // ========== 天地伟业/大华/其他：SDK 下载 ==========
            logger.info("设备品牌={}, 使用 SDK 录像下载...", brand);
            String startTimeStr = sdf.format(new Date(startMs));
            String endTimeStr   = sdf.format(new Date(endMs));

            RecordingTask task = recordingTaskService.downloadRecordingSync(
                    deviceId, channel, startTimeStr, endTimeStr, 120, false);
            if (task != null && task.getStatus() == 2) {
                localPath = task.getLocalFilePath();
                logger.info("SDK 录像下载成功: {}", localPath);
            } else {
                int status = task != null ? task.getStatus() : -1;
                logger.error("SDK 录像下载失败: deviceId={}, 状态={}", deviceId, status);
                return false;
            }
        }

        // ---------- 上传 OSS ----------
        String ossUrl = null;
        if (ossService != null && ossService.isEnabled()) {
            String ossPath = "recordings/" + deviceId + "/" + new File(localPath).getName();
            ossUrl = ossService.uploadFile(localPath, ossPath);
            if (ossUrl != null) {
                logger.info("录像已上传 OSS: {}", ossUrl);
            }
        }

        // ---------- 写入 context ----------
        context.putVariable("recordFilePath", localPath);
        context.putVariable("recordStartTime", sdf.format(new Date(startMs)));
        context.putVariable("recordEndTime", sdf.format(new Date(endMs)));
        if (ossUrl != null && !ossUrl.isEmpty()) {
            context.putVariable("recordOssUrl", ossUrl);
            context.putVariable("ossUrl", ossUrl);
        }
        return true;
    }
}
