package com.digital.video.gateway.service;

import com.digital.video.gateway.database.Assembly;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.RadarDevice;
import com.digital.video.gateway.database.RadarDeviceDAO;
import com.digital.video.gateway.driver.livox.algorithm.CoordinateTransform;
import com.digital.video.gateway.driver.livox.algorithm.TargetClassifier;
import com.digital.video.gateway.driver.livox.model.DefenseZone;
import com.digital.video.gateway.driver.livox.model.IntrusionEvent;
import com.digital.video.gateway.driver.livox.model.Point;
import com.digital.video.gateway.driver.livox.model.PointCluster;
import com.digital.video.gateway.driver.livox.model.TargetType;
import com.digital.video.gateway.service.TargetTrackingService.TrackedTarget;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowDefinition;
import com.digital.video.gateway.workflow.FlowExecutor;
import com.digital.video.gateway.workflow.FlowService;
import com.digital.video.gateway.database.AlarmFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digital.video.gateway.driver.livox.model.ExclusionZone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 雷达点云处理中心。
 * 串联 5 个模块（预处理 → 检测 → 分类 → 跟踪 → PTZ 解算），
 * 维护每防区的跟随状态机，在关键时刻触发工作流。
 */
public class PointCloudProcessCenter {

    private static final Logger logger = LoggerFactory.getLogger(PointCloudProcessCenter.class);

    // ---- 配置常量 ----
    private static final long PTZ_BEAT_INTERVAL_MS = 200;
    private static final long CAPTURE_INTERVAL_MS = 30_000;
    private static final long DEFAULT_PTZ_DELAY_MS = 1500;

    // ---- 依赖 ----
    private final TargetClassifier targetClassifier = new TargetClassifier();
    private final TargetTrackingService targetTrackingService;
    private final MotionPredictionService motionPredictionService;
    private final PTZService ptzService;
    private final FlowService flowService;
    private final FlowExecutor flowExecutor;
    private final AssemblyService assemblyService;
    private final Database dbDatabase;

    // ---- 跟随状态机（防区级） ----
    private final Map<String, FollowState> followStates = new ConcurrentHashMap<>();

    // 防区级互斥锁：确保同一防区不会被两个 PointCloudWorker 线程并发处理
    private final ConcurrentHashMap<String, Object> zoneLocks = new ConcurrentHashMap<>();

    // ---- 静态噪点记忆 ----
    // 记录反复出现的目标位置，用于自动识别和过滤持续性噪点
    private final List<NoiseMemoryEntry> noiseMemory = new java.util.concurrent.CopyOnWriteArrayList<>();

    static class NoiseMemoryEntry {
        final float x, y, z;
        long firstSeen;
        long lastSeen;
        int hitCount;

        NoiseMemoryEntry(float x, float y, float z, long now) {
            this.x = x; this.y = y; this.z = z;
            this.firstSeen = now;
            this.lastSeen = now;
            this.hitCount = 1;
        }

        boolean isNear(float px, float py, float pz, float tolerance) {
            float dx = x - px, dy = y - py, dz = z - pz;
            return (dx * dx + dy * dy + dz * dz) <= tolerance * tolerance;
        }
    }

    // PTZ 联动开关缓存：radarDeviceId → (是否启用, 缓存过期时间戳)
    private final Map<String, long[]> ptzLinkageCache = new ConcurrentHashMap<>();
    private static final long PTZ_LINKAGE_CACHE_TTL_MS = 10_000;

    // CoordinateTransform 缓存：zoneId → transform（防区标定参数变更频率极低）
    private final Map<String, CoordinateTransform> coordTransformCache = new ConcurrentHashMap<>();

    // ---- PTZ 抑制（标定模式） ----
    // 被抑制的摄像头设备 ID 集合：标定期间禁止 PTZ 跟随
    private final java.util.Set<String> ptzSuppressedDevices = ConcurrentHashMap.newKeySet();

    /** 抑制指定摄像头的 PTZ 跟随（标定期间调用） */
    public void suppressPtz(String cameraDeviceId) {
        ptzSuppressedDevices.add(cameraDeviceId);
        logger.info("PTZ 跟随已抑制（标定模式）: cameraDeviceId={}", cameraDeviceId);
    }

    /** 恢复指定摄像头的 PTZ 跟随（标定结束调用） */
    public void unsuppressPtz(String cameraDeviceId) {
        ptzSuppressedDevices.remove(cameraDeviceId);
        logger.info("PTZ 跟随已恢复: cameraDeviceId={}", cameraDeviceId);
    }

    /**
     * 清除指定防区的坐标系变换缓存（标定参数更新后调用）。
     * 如果 zoneId 为 null 则清除全部缓存。
     */
    public void invalidateCoordTransformCache(String zoneId) {
        if (zoneId == null) {
            coordTransformCache.clear();
            logger.info("已清除所有防区坐标系变换缓存");
        } else {
            coordTransformCache.remove(zoneId);
            logger.info("已清除防区坐标系变换缓存: zoneId={}", zoneId);
        }
    }

    /** 检查摄像头是否被抑制 */
    public boolean isPtzSuppressed(String cameraDeviceId) {
        return ptzSuppressedDevices.contains(cameraDeviceId);
    }

    // ---- 空间白名单（排除区） ----
    // zoneId → 排除区列表（CopyOnWriteArrayList 保证读多写少场景的线程安全）
    private final Map<String, CopyOnWriteArrayList<ExclusionZone>> exclusionZones = new ConcurrentHashMap<>();
    private static final float EXCLUSION_MARGIN = 0.3f;

    // 工作流触发线程池（替代无限创建 new Thread）
    private static final ExecutorService workflowExecutor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(64),
            r -> { Thread t = new Thread(r, "RadarIntrusion-Flow"); t.setDaemon(true); return t; },
            (r, e) -> LoggerFactory.getLogger(PointCloudProcessCenter.class)
                    .warn("雷达工作流队列已满，丢弃本次触发")
    );

    private static final long FOLLOW_STATE_EXPIRE_MS = 60_000;

    /** 目标脱离宽限期（毫秒）：连续无目标超过此时间才认为目标真正离开 */
    private static final long TARGET_LOST_GRACE_MS = 3000;
    /** 目标切换冷却期（毫秒）：防止在不同 trackingId 之间频繁切换触发工作流 */
    private static final long TARGET_SWITCH_COOLDOWN_MS = 8000;
    /** 工作流触发最小间隔（毫秒）：同一防区连续触发工作流的最小间隔 */
    private static final long WORKFLOW_TRIGGER_MIN_INTERVAL_MS = 15000;
    /** 最小跟随距离（米）：距雷达太近的目标忽略（通常是设备近处噪点） */
    private static final float MIN_FOLLOW_DISTANCE = 0.3f;
    /** PTZ 角度跳变阈值（度）：连续两次 pan 差超过此值时进入跳变观望 */
    private static final float PAN_JUMP_THRESHOLD_DEG = 40f;
    /** 跳变确认所需的连续稳定帧数：候选角度连续出现此次数后才确认执行 */
    private static final int JUMP_CONFIRM_FRAMES = 3;
    /** 跳变确认容差（度）：候选角度在此范围内视为"稳定" */
    private static final float JUMP_CONFIRM_TOLERANCE = 15f;
    /** 单次跳变最大抑制帧数：超过此帧数后强制执行，防止永久抑制 */
    private static final int JUMP_MAX_SUPPRESS_FRAMES = 10;
    /** Zoom 平滑：每次 PTZ beat 允许的最大 zoom 变化量（上升步长） */
    private static final float ZOOM_MAX_STEP_UP = 1.0f;
    /** Zoom 平滑：下降时允许更快收敛（切换目标后迅速回到合适变倍） */
    private static final float ZOOM_MAX_STEP_DOWN = 3.0f;
    /** 静态噪点记忆位置容差（米）：在此半径内反复出现的目标视为同一静态噪点 */
    private static final float NOISE_POSITION_TOLERANCE = 0.5f;
    /** 静态噪点记忆窗口时间（毫秒） */
    private static final long NOISE_MEMORY_WINDOW_MS = 120_000;
    /** 在窗口内同一位置出现超过此次数则视为静态噪点并过滤 */
    private static final int NOISE_HIT_THRESHOLD = 4;

    /** 防区级分类投票窗口大小（跨跟踪 ID 累积，比单目标窗口更大以获得稳定性） */
    private static final int ZONE_TYPE_VOTE_WINDOW = 15;

    /**
     * 防区级跟随状态
     */
    static class FollowState {
        String followingTargetId;
        Point lastFollowedPosition;
        long lastPtzTime;
        long followStartTime;
        long lastCaptureTime;
        long lastAccessTime;
        /** 最后一次检测到目标（任何目标）的时间 */
        long lastTargetSeenTime;
        /** 上次切换目标的时间（用于冷却） */
        long lastSwitchTime;
        /** 上次触发工作流的时间 */
        long lastWorkflowTriggerTime;
        /** 上次实际执行的 PTZ pan 角度（用于跳变抑制） */
        float lastExecutedPan = Float.NaN;
        /** 上次实际执行的 PTZ tilt 角度 */
        float lastExecutedTilt = Float.NaN;
        /** 连续跳变抑制计数（防止永久抑制用） */
        int jumpSuppressCount = 0;
        /** 跳变观望中的候选 pan 角度（NaN 表示无候选） */
        float pendingJumpPan = Float.NaN;
        /** 候选角度连续出现的帧数（达到阈值后确认执行） */
        int pendingJumpFrames = 0;
        /** 上次实际执行的 zoom（用于平滑） */
        float lastExecutedZoom = Float.NaN;

        /** 防区级分类投票历史（跨跟踪 ID 保持连续性） */
        final Deque<TargetType> zoneTypeHistory = new ArrayDeque<>();
        final Map<TargetType, Integer> zoneTypeVotes = new EnumMap<>(TargetType.class);
        /** 当前防区平滑后的目标类型 */
        TargetType smoothedType = null;

        void addTypeVote(TargetType type) {
            if (type == null) type = TargetType.OTHER;
            zoneTypeHistory.addLast(type);
            zoneTypeVotes.merge(type, 1, Integer::sum);
            while (zoneTypeHistory.size() > ZONE_TYPE_VOTE_WINDOW) {
                TargetType old = zoneTypeHistory.pollFirst();
                int cnt = zoneTypeVotes.getOrDefault(old, 1) - 1;
                if (cnt <= 0) zoneTypeVotes.remove(old);
                else zoneTypeVotes.put(old, cnt);
            }
            smoothedType = getMajorityType();
        }

        private TargetType getMajorityType() {
            TargetType best = TargetType.OTHER;
            int maxCount = 0;
            for (Map.Entry<TargetType, Integer> e : zoneTypeVotes.entrySet()) {
                if (e.getValue() > maxCount) {
                    maxCount = e.getValue();
                    best = e.getKey();
                }
            }
            return best;
        }
    }

    public PointCloudProcessCenter(
            TargetTrackingService targetTrackingService,
            MotionPredictionService motionPredictionService,
            PTZService ptzService,
            FlowService flowService,
            FlowExecutor flowExecutor,
            AssemblyService assemblyService,
            Database dbDatabase) {
        this.targetTrackingService = targetTrackingService;
        this.motionPredictionService = motionPredictionService;
        this.ptzService = ptzService;
        this.flowService = flowService;
        this.flowExecutor = flowExecutor;
        this.assemblyService = assemblyService;
        this.dbDatabase = dbDatabase;
    }

    /**
     * 处理侵入检测事件（由 RadarService 在每帧检测到入侵后调用）。
     * 
     * 流程：分类 → 跟踪 → PTZ 解算 → 跟随状态机 → 触发工作流
     */
    public void processEvents(String radarDeviceId, List<IntrusionEvent> eventsIn, DefenseZone zone) {
        // null = 节流中（检测服务未输出新结果），跳过本帧，保持当前跟随状态不变
        if (eventsIn == null) {
            return;
        }
        if (eventsIn.isEmpty()) {
            handleNoTargets(zone.getZoneId());
            return;
        }

        // 防区级互斥：同一防区同一时刻只允许一个线程处理，防止重复触发工作流
        Object lock = zoneLocks.computeIfAbsent(zone.getZoneId(), k -> new Object());
        synchronized (lock) {
            processEventsLocked(radarDeviceId, eventsIn, zone);
        }
    }

    private void processEventsLocked(String radarDeviceId, List<IntrusionEvent> eventsIn, DefenseZone zone) {
        List<IntrusionEvent> events = new ArrayList<>(eventsIn);

        // 0. 前置条件检查
        String cameraDeviceId = zone.getCameraDeviceId();
        if (cameraDeviceId == null || cameraDeviceId.trim().isEmpty()) {
            return;
        }

        // 1. 分类：对每个聚类进行目标分类（无论 PTZ 是否抑制都需要执行，以便标定时获取目标）
        for (IntrusionEvent event : events) {
            PointCluster cluster = event.getCluster();
            if (cluster != null) {
                TargetType type = targetClassifier.classify(cluster);
                cluster.setTargetType(type);
                event.setTargetType(type);
            }
        }

        // 2. 白名单过滤：移除落入排除区的聚类
        String zoneId = zone.getZoneId();
        CopyOnWriteArrayList<ExclusionZone> exclusions = exclusionZones.get(zoneId);
        if (exclusions != null && !exclusions.isEmpty()) {
            Iterator<IntrusionEvent> it = events.iterator();
            while (it.hasNext()) {
                IntrusionEvent ev = it.next();
                PointCluster cl = ev.getCluster();
                if (cl != null) {
                    for (ExclusionZone ez : exclusions) {
                        if (ez.containsCluster(cl)) {
                            it.remove();
                            break;
                        }
                    }
                }
            }
            if (events.isEmpty()) {
                handleNoTargets(zoneId);
                return;
            }
        }

        // 3. 跟踪（无论 PTZ 是否抑制都需要执行，标定模式依赖跟踪目标获取坐标）
        List<PointCluster> clusters = events.stream()
                .map(IntrusionEvent::getCluster)
                .filter(c -> c != null && c.getCentroid() != null)
                .collect(java.util.stream.Collectors.toList());

        List<TrackedTarget> trackedTargets = targetTrackingService.associateAndUpdate(clusters);

        // 回填 trackingId 到 IntrusionEvent
        for (IntrusionEvent event : events) {
            PointCluster cluster = event.getCluster();
            if (cluster == null) continue;
            for (TrackedTarget tt : trackedTargets) {
                if (tt.getLatestCluster() == cluster) {
                    event.setTrackingId(tt.getTrackingId());
                    break;
                }
            }
        }

        // 4. PTZ 控制前置检查：抑制模式或未启用联动时，跳过 PTZ 控制但保留跟踪结果
        if (isPtzSuppressed(cameraDeviceId)) {
            return;
        }
        if (!isPtzLinkageEnabled(radarDeviceId)) {
            return;
        }

        // 5. PTZ 解算 + 跟随状态机
        if (trackedTargets.isEmpty()) {
            handleNoTargets(zone.getZoneId());
            return;
        }

        int channel = zone.getCameraChannel() != null ? zone.getCameraChannel() : 1;
        long now = System.currentTimeMillis();

        FollowState state = followStates.computeIfAbsent(zoneId, k -> new FollowState());
        state.lastAccessTime = now;
        state.lastTargetSeenTime = now;
        cleanStaleFollowStates(now);

        // 选择跟随目标：优先续跟当前目标，而非每帧都切到最近目标
        TrackedTarget primaryTarget = selectFollowTarget(trackedTargets, state);
        if (primaryTarget == null) {
            handleNoTargets(zone.getZoneId());
            return;
        }

        // PTZ 解算
        CoordinateTransform coordTransform = coordTransformCache.computeIfAbsent(zoneId, k -> {
            DefenseZone.CoordinateTransform t = zone.getCoordinateTransform();
            CoordinateTransform ct = new CoordinateTransform(
                    t.translationX, t.translationY, t.translationZ,
                    t.rotationX, t.rotationY, t.rotationZ,
                    t.scale);
            // 复制变倍标定数据
            if (t.zoomCalibPoints != null) {
                for (float[] p : t.zoomCalibPoints) {
                    ct.addZoomCalibPoint(p[0], p[1]);
                }
            }
            return ct;
        });

        Point centroid = primaryTarget.getPosition();
        float[] velocity = primaryTarget.getVelocity();

        float ptzDelaySeconds = DEFAULT_PTZ_DELAY_MS / 1000.0f;
        MotionPredictionService.PredictionResult prediction = motionPredictionService.predictForPTZ(
                centroid, velocity, ptzDelaySeconds);
        Point predictedCameraPoint = coordTransform.transformRadarToCamera(prediction.predictedPosition);
        float[] angles = coordTransform.calculatePTZAngles(predictedCameraPoint);
        float pan = angles[0];
        float tilt = angles[1];
        float rawZoom = coordTransform.hasZoomCalibration()
                ? coordTransform.estimateZoom(predictedCameraPoint.distance())
                : calculateZoom(prediction, predictedCameraPoint);
        float distance = predictedCameraPoint.distance();

        // Zoom 平滑：限制每次变化幅度；PTZ 跳变观望期间冻结 zoom
        // pendingJumpPan != NaN 表示当前处于跳变观望中（pan 未确认到位）
        boolean jumpPending = !Float.isNaN(state.pendingJumpPan);
        float zoom = smoothZoom(state, rawZoom, distance, jumpPending);

        if (Float.isNaN(pan) || Float.isNaN(tilt) || Float.isNaN(zoom)
                || Float.isInfinite(pan) || Float.isInfinite(tilt) || Float.isInfinite(zoom)) {
            logger.warn("PTZ参数异常（NaN/Infinity），跳过本帧: zoneId={}, pan={}, tilt={}, zoom={}",
                    zoneId, pan, tilt, zoom);
            return;
        }

        // 4. 防区级分类平滑：每帧将当前目标的分类喂入 FollowState 的投票器
        state.addTypeVote(primaryTarget.getTargetType());

        // 5. 跟随状态机
        boolean isNewTarget = state.followingTargetId == null;
        boolean isSameTarget = !isNewTarget && state.followingTargetId.equals(primaryTarget.getTrackingId());

        if (isNewTarget) {
            // 未跟随 → 首次锁定
            state.followingTargetId = primaryTarget.getTrackingId();
            state.lastFollowedPosition = centroid;
            state.lastPtzTime = now;
            state.followStartTime = now;
            state.lastCaptureTime = now;
            state.lastSwitchTime = now;
            state.lastWorkflowTriggerTime = now;
            state.lastExecutedPan = pan;
            state.lastExecutedTilt = tilt;
            state.lastExecutedZoom = zoom;
            state.jumpSuppressCount = 0;
            state.pendingJumpPan = Float.NaN;
            state.pendingJumpFrames = 0;

            logger.info("首次锁定目标: zoneId={}, trackingId={}, rawType={}, smoothedType={}, distance={}m, pan={}, tilt={}, zoom={}",
                    zoneId, primaryTarget.getTrackingId(), primaryTarget.getTargetType(),
                    state.smoothedType, String.format("%.2f", distance), String.format("%.1f", pan),
                    String.format("%.1f", tilt), String.format("%.1f", zoom));

            triggerWorkflow(radarDeviceId, zone, primaryTarget, prediction,
                    pan, tilt, zoom, distance, cameraDeviceId, channel, state);

        } else if (isSameTarget) {
            // 续跟当前目标 → 更新 PTZ（节拍）
            state.lastFollowedPosition = centroid;

            if (now - state.lastPtzTime >= PTZ_BEAT_INTERVAL_MS) {
                if (isPtzJumpAllowed(state, pan, tilt, zoneId)) {
                    ptzService.gotoAngle(cameraDeviceId, channel, pan, tilt, zoom);
                    state.lastPtzTime = now;
                }
            }

            // 定时抓拍
            if (now - state.lastCaptureTime >= CAPTURE_INTERVAL_MS) {
                state.lastCaptureTime = now;
                logger.info("定时抓拍: zoneId={}, trackingId={}, smoothedType={}",
                        zoneId, primaryTarget.getTrackingId(), state.smoothedType);
                triggerWorkflow(radarDeviceId, zone, primaryTarget, prediction,
                        pan, tilt, zoom, distance, cameraDeviceId, channel, state);
            }
        } else {
            // trackingId 变了 — 可能是 MOT 重分配了 ID，不一定是真正的新目标
            // 仅当冷却期过后才正式切换并触发工作流
            boolean cooldownPassed = (now - state.lastSwitchTime) >= TARGET_SWITCH_COOLDOWN_MS;
            boolean workflowReady = (now - state.lastWorkflowTriggerTime) >= WORKFLOW_TRIGGER_MIN_INTERVAL_MS;

            // 即使 ID 变了，PTZ 仍然追踪最近的位置（保持云台跟随不中断）
            state.lastFollowedPosition = centroid;
            if (now - state.lastPtzTime >= PTZ_BEAT_INTERVAL_MS) {
                if (isPtzJumpAllowed(state, pan, tilt, zoneId)) {
                    ptzService.gotoAngle(cameraDeviceId, channel, pan, tilt, zoom);
                    state.lastPtzTime = now;
                }
            }

            if (cooldownPassed) {
                state.followingTargetId = primaryTarget.getTrackingId();
                state.lastSwitchTime = now;
                // 切换新目标时，重置 zoom 平滑历史，避免旧目标的高 zoom 传染给新目标
                state.lastExecutedZoom = Float.NaN;

                if (workflowReady) {
                    state.lastWorkflowTriggerTime = now;
                    state.lastCaptureTime = now;
                    logger.info("切换跟随目标: zoneId={}, newTrackingId={}, rawType={}, smoothedType={}",
                            zoneId, primaryTarget.getTrackingId(), primaryTarget.getTargetType(),
                            state.smoothedType);
                    triggerWorkflow(radarDeviceId, zone, primaryTarget, prediction,
                            pan, tilt, zoom, distance, cameraDeviceId, channel, state);
                } else {
                    logger.debug("切换跟随目标（工作流冷却中）: zoneId={}, newTrackingId={}",
                            zoneId, primaryTarget.getTrackingId());
                }
            }
        }
    }

    /**
     * PTZ 角度跳变抑制：防止噪点或跟踪跳变导致云台剧烈旋转。
     * <p>
     * 核心策略：
     * 1. 若 pan 变化量 > 阈值，进入"观望"状态，不立即执行 PTZ。
     * 2. 连续 JUMP_CONFIRM_FRAMES 次都落在新角度附近（容差 JUMP_CONFIRM_TOLERANCE），
     *    则认为目标确实移动到了新位置，执行 PTZ 并更新基准。
     * 3. 若中途角度不稳定（散乱），抑制帧计数重置，继续观望。
     * 4. 防止永久抑制：单方向抑制超过 JUMP_MAX_SUPPRESS_FRAMES 帧时强制执行。
     * </p>
     */
    private boolean isPtzJumpAllowed(FollowState state, float pan, float tilt, String zoneId) {
        if (Float.isNaN(state.lastExecutedPan)) {
            state.lastExecutedPan = pan;
            state.lastExecutedTilt = tilt;
            state.jumpSuppressCount = 0;
            state.pendingJumpPan = Float.NaN;
            state.pendingJumpFrames = 0;
            return true;
        }

        float panDelta = Math.abs(normalizeAngle(pan - state.lastExecutedPan));
        boolean isJump = panDelta > PAN_JUMP_THRESHOLD_DEG;

        if (!isJump) {
            // 正常范围内的移动：清除跳变状态，直接允许
            state.jumpSuppressCount = 0;
            state.pendingJumpPan = Float.NaN;
            state.pendingJumpFrames = 0;
            state.lastExecutedPan = pan;
            state.lastExecutedTilt = tilt;
            return true;
        }

        // 检测到跳变——进入"观望"阶段
        state.jumpSuppressCount++;

        // 安全阀：抑制过久则强制执行，防止目标永远无法跟上
        if (state.jumpSuppressCount > JUMP_MAX_SUPPRESS_FRAMES) {
            logger.warn("PTZ跳变抑制超时强制执行: zoneId={}, pan={}, delta={}, suppressCount={}",
                    zoneId, String.format("%.1f", pan), String.format("%.1f", panDelta), state.jumpSuppressCount);
            state.lastExecutedPan = pan;
            state.lastExecutedTilt = tilt;
            state.jumpSuppressCount = 0;
            state.pendingJumpPan = Float.NaN;
            state.pendingJumpFrames = 0;
            state.lastExecutedZoom = Float.NaN;
            return true;
        }

        // 检查新角度是否"稳定"（连续落在同一位置附近）
        if (Float.isNaN(state.pendingJumpPan)) {
            // 首次跳变，记录候选角度
            state.pendingJumpPan = pan;
            state.pendingJumpFrames = 1;
        } else {
            float pendingDelta = Math.abs(normalizeAngle(pan - state.pendingJumpPan));
            if (pendingDelta <= JUMP_CONFIRM_TOLERANCE) {
                // 新角度在候选位置附近，累计确认帧
                state.pendingJumpFrames++;
            } else {
                // 角度不稳定（散乱噪点），重置候选
                state.pendingJumpPan = pan;
                state.pendingJumpFrames = 1;
            }
        }

        // 达到确认帧数：认为目标确实移动，执行 PTZ
        if (state.pendingJumpFrames >= JUMP_CONFIRM_FRAMES) {
            logger.info("PTZ跳变确认执行: zoneId={}, lastPan={}, newPan={}, delta={}, confirmFrames={}",
                    zoneId, String.format("%.1f", state.lastExecutedPan),
                    String.format("%.1f", pan), String.format("%.1f", panDelta), state.pendingJumpFrames);
            state.lastExecutedPan = pan;
            state.lastExecutedTilt = tilt;
            state.jumpSuppressCount = 0;
            state.pendingJumpPan = Float.NaN;
            state.pendingJumpFrames = 0;
            // 跳变确认后重置 zoom 历史，让 zoom 从当前距离重新计算，避免旧方向的 zoom 影响新方向
            state.lastExecutedZoom = Float.NaN;
            return true;
        }

        // 继续观望，本帧抑制
        logger.debug("PTZ跳变抑制观望中: zoneId={}, lastPan={}, newPan={}, delta={}, pendingFrames={}/{}",
                zoneId, String.format("%.1f", state.lastExecutedPan),
                String.format("%.1f", pan), String.format("%.1f", panDelta),
                state.pendingJumpFrames, JUMP_CONFIRM_FRAMES);
        return false;
    }

    private static float normalizeAngle(float angle) {
        while (angle > 180f) angle -= 360f;
        while (angle < -180f) angle += 360f;
        return angle;
    }

    /**
     * Zoom 平滑：限制帧间 zoom 变化幅度，防止画面剧烈缩放。
     *
     * <p>策略：
     * <ul>
     *   <li>上升慢（ZOOM_MAX_STEP_UP），防止云台未到位时画面已极度拉近</li>
     *   <li>下降快（ZOOM_MAX_STEP_DOWN），切换目标后迅速回到合理倍数</li>
     *   <li>PTZ 跳变观望期间冻结 zoom，避免方向未到位时 zoom 继续爬升</li>
     *   <li>按当前目标距离施加动态上限，防止标定偏差导致变倍飙升</li>
     * </ul>
     * </p>
     *
     * @param state       防区跟随状态（含 lastExecutedZoom 和跳变抑制状态）
     * @param targetZoom  本帧目标 zoom（来自 estimateZoom 或 calculateZoom）
     * @param distance    当前目标距离（米），用于施加距离上限
     * @param jumpPending 当前帧是否处于 PTZ 跳变观望中（true 时冻结 zoom）
     */
    private float smoothZoom(FollowState state, float targetZoom, float distance, boolean jumpPending) {
        // 按距离施加绝对硬上限：无论平滑过程中处于哪个阶段，输出不能超过此值
        float distanceCap;
        if (distance < 1.0f)       distanceCap = 3.0f;
        else if (distance < 3.0f)  distanceCap = 6.0f;
        else if (distance < 8.0f)  distanceCap = 12.0f;
        else if (distance < 20.0f) distanceCap = 20.0f;
        else                       distanceCap = 30.0f;
        targetZoom = Math.min(targetZoom, distanceCap);

        if (Float.isNaN(state.lastExecutedZoom)) {
            state.lastExecutedZoom = targetZoom;
            return targetZoom;
        }

        // PTZ 跳变观望期间冻结 zoom（pan/tilt 未确认到位，不做拉近）
        // 但仍需对冻结值做硬上限裁剪，防止旧位置的高 zoom 继续输出
        if (jumpPending) {
            float frozen = Math.min(state.lastExecutedZoom, distanceCap);
            state.lastExecutedZoom = frozen;
            return frozen;
        }

        float delta = targetZoom - state.lastExecutedZoom;
        float smoothed;
        if (delta >= 0) {
            // 上升：步长小，慢慢推进
            smoothed = (delta <= ZOOM_MAX_STEP_UP) ? targetZoom : state.lastExecutedZoom + ZOOM_MAX_STEP_UP;
        } else {
            // 下降：步长大，快速收敛
            smoothed = (Math.abs(delta) <= ZOOM_MAX_STEP_DOWN) ? targetZoom : state.lastExecutedZoom - ZOOM_MAX_STEP_DOWN;
        }
        // 输出端再次施加硬上限，确保平滑过渡期内也不超出当前距离允许的最大值
        smoothed = Math.max(1.0f, Math.min(smoothed, distanceCap));
        state.lastExecutedZoom = smoothed;
        return smoothed;
    }

    /**
     * 检查一个位置是否属于已知的静态噪点位置。
     * 同时记录本次位置，如果某位置在时间窗口内被反复命中，则被标记为静态噪点。
     */
    private boolean isStaticNoise(Point pos, long now) {
        // 清理过期条目
        noiseMemory.removeIf(e -> now - e.lastSeen > NOISE_MEMORY_WINDOW_MS);

        for (NoiseMemoryEntry entry : noiseMemory) {
            if (entry.isNear(pos.x, pos.y, pos.z, NOISE_POSITION_TOLERANCE)) {
                entry.hitCount++;
                entry.lastSeen = now;
                if (entry.hitCount >= NOISE_HIT_THRESHOLD) {
                    return true;
                }
                return false;
            }
        }
        // 新位置，添加记忆
        noiseMemory.add(new NoiseMemoryEntry(pos.x, pos.y, pos.z, now));
        return false;
    }

    private volatile long lastStaleCleanTime = 0;

    private void cleanStaleFollowStates(long now) {
        if (now - lastStaleCleanTime < 30_000) return;
        lastStaleCleanTime = now;
        followStates.entrySet().removeIf(e ->
                e.getValue().lastAccessTime > 0 && now - e.getValue().lastAccessTime > FOLLOW_STATE_EXPIRE_MS);
        ptzLinkageCache.entrySet().removeIf(e -> now >= e.getValue()[1]);
    }

    /**
     * 本帧无目标时处理（带宽限期）
     */
    private void handleNoTargets(String zoneId) {
        FollowState state = followStates.get(zoneId);
        if (state != null && state.followingTargetId != null) {
            long elapsed = System.currentTimeMillis() - state.lastTargetSeenTime;
            if (elapsed < TARGET_LOST_GRACE_MS) {
                return;
            }
            logger.info("目标脱离防区，结束跟随: zoneId={}, trackingId={}, smoothedType={}, 无目标持续={}ms",
                    zoneId, state.followingTargetId, state.smoothedType, elapsed);
            state.followingTargetId = null;
            state.lastFollowedPosition = null;
            state.zoneTypeHistory.clear();
            state.zoneTypeVotes.clear();
            state.smoothedType = null;
        }
    }

    /**
     * 选择跟随目标：
     * 1. 如果当前已有跟随目标且它仍在跟踪池中，继续跟随它
     * 2. 否则选距离最近的目标
     */
    private TrackedTarget selectFollowTarget(List<TrackedTarget> targets, FollowState state) {
        long now = System.currentTimeMillis();
        // 过滤掉距雷达过近的噪点目标 + 静态噪点位置
        List<TrackedTarget> valid = new java.util.ArrayList<>();
        for (TrackedTarget t : targets) {
            Point pos = t.getPosition();
            if (pos.distance() < MIN_FOLLOW_DISTANCE) continue;
            if (isStaticNoise(pos, now)) continue;
            valid.add(t);
        }
        if (valid.isEmpty()) return null;

        if (state.followingTargetId != null) {
            for (TrackedTarget t : valid) {
                if (state.followingTargetId.equals(t.getTrackingId())) {
                    return t;
                }
            }
        }
        // 当前目标已丢失，选最近的有效目标
        TrackedTarget nearest = null;
        float minDist = Float.MAX_VALUE;
        for (TrackedTarget t : valid) {
            Point pos = t.getPosition();
            float dist = pos.distance();
            if (dist < minDist) {
                minDist = dist;
                nearest = t;
            }
        }
        return nearest;
    }

    /**
     * 默认变倍计算（无标定数据时使用）。
     * 保守策略：近距离低变倍保证画面稳定，远距离适度提升。
     */
    private float calculateZoom(MotionPredictionService.PredictionResult prediction, Point cameraPoint) {
        float distance = cameraPoint.distance();
        float baseZoom;

        if (distance < 1.0f) {
            baseZoom = 1.0f;
        } else if (distance < 3.0f) {
            baseZoom = 2.0f;
        } else if (distance < 10.0f) {
            baseZoom = 4.0f;
        } else if (distance < 20.0f) {
            baseZoom = 6.0f;
        } else if (distance < 40.0f) {
            baseZoom = 8.0f;
        } else {
            baseZoom = 10.0f;
        }

        return Math.max(1.0f, Math.min(35.0f, baseZoom));
    }

    /**
     * 查找雷达入侵专用工作流定义。
     * 优先查找 flowId="default_radar_intrusion_flow"，
     * 再查找 flowType="radar" 的启用流程。
     * <p>
     * 注意：不回退到通用报警流程（flowType="alarm"），以防止将雷达事件错误地
     * 路由到为摄像头报警设计的流程（MQTT主题、Webhook格式均不匹配雷达事件）。
     * 若无可用的雷达专用流程，则跳过工作流触发。
     * </p>
     */
    private FlowDefinition findRadarFlowDefinition() {
        // 优先使用雷达专用流程
        AlarmFlow radarFlow = flowService.getFlow("default_radar_intrusion_flow");
        if (radarFlow != null && radarFlow.isEnabled()) {
            FlowDefinition def = flowService.toDefinition(radarFlow);
            if (def != null) return def;
        }

        // 查找 flowType=radar 的已启用流程
        List<AlarmFlow> all = flowService.listFlows();
        if (all != null) {
            for (AlarmFlow f : all) {
                if (f.isEnabled() && "radar".equalsIgnoreCase(f.getFlowType())) {
                    FlowDefinition def = flowService.toDefinition(f);
                    if (def != null) return def;
                }
            }
        }

        // 无雷达专用流程可用，返回 null（不回退到通用报警流程）
        return null;
    }

    /**
     * 检查雷达所属装置是否开启了 PTZ 联动（带缓存，避免每帧查库）
     */
    private boolean isPtzLinkageEnabled(String radarDeviceId) {
        if (assemblyService == null) return true;

        long now = System.currentTimeMillis();
        long[] cached = ptzLinkageCache.get(radarDeviceId);
        if (cached != null && now < cached[1]) {
            return cached[0] == 1;
        }

        try {
            RadarDeviceDAO dao = new RadarDeviceDAO(dbDatabase);
            RadarDevice radar = dao.getByDeviceId(radarDeviceId);
            if (radar == null || radar.getAssemblyId() == null || radar.getAssemblyId().trim().isEmpty()) {
                ptzLinkageCache.put(radarDeviceId, new long[]{0, now + PTZ_LINKAGE_CACHE_TTL_MS});
                return false;
            }
            Assembly assembly = assemblyService.getAssembly(radar.getAssemblyId());
            boolean enabled = assembly != null && assembly.isPtzLinkageEnabled();
            ptzLinkageCache.put(radarDeviceId, new long[]{enabled ? 1 : 0, now + PTZ_LINKAGE_CACHE_TTL_MS});
            return enabled;
        } catch (Exception e) {
            logger.error("检查PTZ联动状态失败: radarDeviceId={}", radarDeviceId, e);
            return false;
        }
    }

    /**
     * 构建 FlowContext 并触发工作流
     */
    private void triggerWorkflow(
            String radarDeviceId,
            DefenseZone zone,
            TrackedTarget target,
            MotionPredictionService.PredictionResult prediction,
            float pan, float tilt, float zoom, float distance,
            String cameraDeviceId, int channel,
            FollowState followState) {

        try {
            // 查找雷达入侵专用流程（优先使用 radar 类型流程，否则使用默认流程）
            FlowDefinition definition = findRadarFlowDefinition();
            if (definition == null) {
                logger.warn("无可用雷达工作流，跳过触发: radarDeviceId={}", radarDeviceId);
                return;
            }

            // 构建 FlowContext
            FlowContext context = new FlowContext();
            context.setDeviceId(cameraDeviceId);
            context.setAssemblyId(zone.getAssemblyId());
            context.setAlarmType("RADAR_INTRUSION");

            // 查询 AI 算法库中配置的中文名称，优先用于 webhook/MQTT 推送展示
            String radarAlarmTypeName = null;
            if (dbDatabase != null) {
                try (java.sql.Connection _conn = dbDatabase.getPoolConnection()) {
                    Map<String, Object> canonicalEvent = com.digital.video.gateway.database.CanonicalEventTable
                            .getCanonicalEvent(_conn, "RADAR_INTRUSION");
                    if (canonicalEvent != null) {
                        Object nameZh = canonicalEvent.get("nameZh");
                        if (nameZh instanceof String && !((String) nameZh).isEmpty()) {
                            radarAlarmTypeName = (String) nameZh;
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (radarAlarmTypeName == null) {
                radarAlarmTypeName = "雷达入侵";
            }
            context.putVariable("alarmTypeName", radarAlarmTypeName);

            // 写入完整空间信息
            context.putVariable("radarDeviceId", radarDeviceId);
            context.putVariable("cameraDeviceId", cameraDeviceId);
            context.putVariable("cameraChannel", channel);
            context.putVariable("zoneId", zone.getZoneId());
            context.putVariable("zoneName", zone.getName());

            TargetType effectiveType = followState != null && followState.smoothedType != null
                    ? followState.smoothedType
                    : (target.getTargetType() != null ? target.getTargetType() : TargetType.OTHER);

            context.putVariable("targetId", target.getTrackingId());
            context.putVariable("targetType", effectiveType.getCode());

            Point centroid = target.getPosition();
            context.putVariable("centroidX", centroid.x);
            context.putVariable("centroidY", centroid.y);
            context.putVariable("centroidZ", centroid.z);
            context.putVariable("distance", distance);

            double azimuth = Math.toDegrees(Math.atan2(centroid.y, centroid.x));
            azimuth = (azimuth + 360) % 360;
            context.putVariable("azimuth", (float) azimuth);

            context.putVariable("pan", pan);
            context.putVariable("tilt", tilt);
            context.putVariable("zoom", zoom);
            context.putVariable("ptzDelay", DEFAULT_PTZ_DELAY_MS);
            // 传入上次执行的PTZ角度，供RadarIntrusionHandler动态计算转向等待时长
            if (followState != null && !Float.isNaN(followState.lastExecutedPan)) {
                context.putVariable("lastExecutedPan", followState.lastExecutedPan);
                context.putVariable("lastExecutedTilt", followState.lastExecutedTilt);
            }

            context.putVariable("motionState", prediction.motionState != null ? prediction.motionState.name() : "UNKNOWN");

            PointCluster cluster = target.getLatestCluster();
            if (cluster != null) {
                context.putVariable("bboxWidth", cluster.getWidth());
                context.putVariable("bboxHeight", cluster.getHeight());
                context.putVariable("bboxDepth", cluster.getDepth());
                context.putVariable("pointCount", cluster.getPointCount());
            }

            // 同样写入 payload（供模板引擎使用）
            context.getPayload().put("alarmType", "RADAR_INTRUSION");
            context.getPayload().put("eventName", "雷达入侵");
            context.getPayload().put("deviceId", cameraDeviceId);
            context.getPayload().put("radarDeviceId", radarDeviceId);
            context.getPayload().put("targetType", effectiveType.getLabel());
            context.getPayload().put("distance", String.format("%.1f", distance));

            logger.info("触发雷达入侵工作流: radarId={}, zoneId={}, trackingId={}, " +
                            "rawType={}, smoothedType={}, pan={}, tilt={}, zoom={}, distance={}m, motionState={}",
                    radarDeviceId, zone.getZoneId(), target.getTrackingId(),
                    target.getTargetType(), effectiveType, pan, tilt, zoom, distance, prediction.motionState);

            // 异步触发工作流（使用线程池，避免无限创建线程）
            workflowExecutor.submit(() -> {
                try {
                    flowExecutor.execute(definition, context);
                } catch (Exception e) {
                    logger.error("雷达入侵工作流执行失败: radarId={}", radarDeviceId, e);
                }
            });

        } catch (Exception e) {
            logger.error("触发雷达入侵工作流异常: radarDeviceId={}", radarDeviceId, e);
        }
    }

    // ==================== 白名单（空间排除区）管理 ====================

    /**
     * 通过跟踪目标 ID 添加白名单。
     * 系统自动提取该目标当前的空间包围盒并加 margin，作为排除区。
     *
     * @return 创建的排除区，如未找到目标则返回 null
     */
    public ExclusionZone addWhitelistByTrackingId(String zoneId, String trackingId) {
        TrackedTarget target = targetTrackingService.getTrackedTarget(trackingId);
        if (target == null || target.getLatestCluster() == null) {
            logger.warn("白名单添加失败：找不到跟踪目标 trackingId={}", trackingId);
            return null;
        }
        ExclusionZone ez = ExclusionZone.fromCluster(zoneId, trackingId,
                target.getLatestCluster(), EXCLUSION_MARGIN);
        exclusionZones.computeIfAbsent(zoneId, k -> new CopyOnWriteArrayList<>()).add(ez);

        // 如果当前正在跟随这个目标，立即释放跟随状态
        FollowState state = followStates.get(zoneId);
        if (state != null && trackingId.equals(state.followingTargetId)) {
            state.followingTargetId = null;
            state.lastFollowedPosition = null;
            logger.info("白名单已释放跟随状态: zoneId={}, trackingId={}", zoneId, trackingId);
        }

        logger.info("白名单已添加: zoneId={}, exclusionId={}, trackingId={}, " +
                        "bbox=[{},{},{} ~ {},{},{}]",
                zoneId, ez.getExclusionId(), trackingId,
                ez.getMinX(), ez.getMinY(), ez.getMinZ(),
                ez.getMaxX(), ez.getMaxY(), ez.getMaxZ());
        return ez;
    }

    /**
     * 手动指定空间范围添加白名单。
     */
    public ExclusionZone addWhitelistManual(String zoneId, String label,
                                            float minX, float maxX,
                                            float minY, float maxY,
                                            float minZ, float maxZ) {
        ExclusionZone ez = ExclusionZone.manual(zoneId, label, minX, maxX, minY, maxY, minZ, maxZ);
        exclusionZones.computeIfAbsent(zoneId, k -> new CopyOnWriteArrayList<>()).add(ez);
        logger.info("白名单已手动添加: zoneId={}, exclusionId={}, label={}", zoneId, ez.getExclusionId(), label);
        return ez;
    }

    /**
     * 移除指定排除区。
     */
    public boolean removeWhitelistEntry(String zoneId, String exclusionId) {
        CopyOnWriteArrayList<ExclusionZone> list = exclusionZones.get(zoneId);
        if (list == null) return false;
        boolean removed = list.removeIf(ez -> ez.getExclusionId().equals(exclusionId));
        if (removed) {
            logger.info("白名单已移除: zoneId={}, exclusionId={}", zoneId, exclusionId);
        }
        return removed;
    }

    /**
     * 清空指定防区的所有白名单。
     */
    public int clearWhitelist(String zoneId) {
        CopyOnWriteArrayList<ExclusionZone> list = exclusionZones.remove(zoneId);
        int count = list != null ? list.size() : 0;
        if (count > 0) {
            logger.info("白名单已全部清除: zoneId={}, count={}", zoneId, count);
        }
        return count;
    }

    /**
     * 获取指定防区的白名单列表。
     */
    public List<ExclusionZone> getWhitelist(String zoneId) {
        CopyOnWriteArrayList<ExclusionZone> list = exclusionZones.get(zoneId);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    /**
     * 获取所有防区的白名单列表。
     */
    public Map<String, List<ExclusionZone>> getAllWhitelists() {
        Map<String, List<ExclusionZone>> result = new HashMap<>();
        for (Map.Entry<String, CopyOnWriteArrayList<ExclusionZone>> entry : exclusionZones.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    /**
     * 获取当前所有防区正在跟踪的目标信息（供前端加白名单选择用）。
     */
    public List<Map<String, Object>> getActiveTargets(String zoneId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (TrackedTarget target : targetTrackingService.getAllTrackedTargets()) {
            PointCluster cluster = target.getLatestCluster();
            if (cluster == null || cluster.getCentroid() == null) continue;
            Map<String, Object> info = new HashMap<>();
            info.put("trackingId", target.getTrackingId());
            info.put("targetType", target.getTargetType() != null ? target.getTargetType().getCode() : "other");
            info.put("targetTypeLabel", target.getTargetType() != null ? target.getTargetType().getLabel() : "其他");
            Point pos = target.getPosition();
            info.put("centroidX", pos.x);
            info.put("centroidY", pos.y);
            info.put("centroidZ", pos.z);
            info.put("distance", pos.distance());
            info.put("pointCount", cluster.getPointCount());
            if (cluster.getBbox() != null) {
                info.put("bboxWidth", cluster.getWidth());
                info.put("bboxHeight", cluster.getHeight());
                info.put("bboxDepth", cluster.getDepth());
            }
            result.add(info);
        }
        return result;
    }
}
