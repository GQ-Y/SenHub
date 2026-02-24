# 雷达点云 WebSocket 吞吐分析与优化

## 现象

- Livox Mid-360 标称约 **20 万点/秒**
- 当前 WebSocket 端接收/展示仅能稳定在 **约 10 万点/秒**，约一半点云被漏掉

## 根因分析

### 1. 点云处理线程池过小 + 队列满即丢帧（主要瓶颈）

**位置**: `RadarService.java`

- 线程池: **4 个 worker**，有界队列容量 **8**
- 策略: 队列满时 **直接丢弃本帧**（`RejectedExecutionHandler`），不阻塞 Livox 回调

**计算**:

- Mid-360 约 20 万点/秒，若按每包约 2000 点，约 **100 包/秒** 进入 Java
- 每包需经 `routePointCloud`：降噪(SOR)、可选体素下采样、侵入检测/推送等
- **SOR 为 O(n²)**（见 `PointCloudProcessor.statisticalOutlierRemoval`）：每点对全部点算距离并排序取 k 近邻，单帧 2000 点约 400 万次距离计算，单帧耗时可达 **50–200ms**
- 4 线程 × (1000ms / 100ms) ≈ **40 帧/秒** 处理能力
- 到达 100 帧/秒 → 约 **60 帧/秒被拒绝** → 60 × 2000 ≈ **12 万点/秒被丢弃**，与“约漏一半”一致

结论: **队列容量 8 + 4 线程** 在开启 SOR 时无法跟上 20 万点/秒，大量帧在提交时就被拒绝，导致漏点。

---

### 2. 统计去噪 (SOR) 计算量过大

**位置**: `PointCloudProcessor.statisticalOutlierRemoval`

- 对每个点与**所有其他点**算距离并排序，取 k 近邻
- 复杂度 **O(n²)**，n 为单帧点数；单帧 2k 点即约 4M 次 `distanceTo` + 排序
- 在 20 万点/秒输入下，若每帧都做 SOR，worker 大部分时间耗在 SOR 上，吞吐上限约 4×（1000/100ms）= 40 帧/秒，远低于 100 帧/秒

结论: **SOR 是实时流水线上的主要 CPU 瓶颈**，直接导致处理帧率不足、队列满、丢帧。

---

### 3. WebSocket 缓冲与刷新策略

**位置**: `RadarWebSocketHandler.java`

- `BATCH_FLUSH_MS = 80`：每 80ms 刷新一次
- `MAX_BUFFER_POINTS = 80000`：缓冲超过 8 万点即 **丢弃最旧的一批（每次 1 万点）**
- `MAX_POINTS_PER_MESSAGE = 50000`：每条消息最多 5 万点
- 每 80ms 每设备 **只取一批**（最多 5 万点）发送

若上游能稳定推 20 万点/秒，而下游因 **单线程 JSON 序列化 + 单线程 send** 较慢（例如 5 万点 JSON 需 50–100ms），则：

- 实际刷新频率低于 10 次/秒
- 有效发送约 10 × 5 万 = **50 万点/秒** 理论值，但若序列化/网络成为瓶颈，实际可能只有约 10 万点/秒
- 缓冲容易触及 8 万点触发丢弃

结论: **单次刷新只取一批、大 JSON 序列化、单线程刷新** 在 20 万点/秒输入下容易成为瓶颈，并触发缓冲丢弃。

---

### 4. 每点 Map + JSON 序列化开销

**位置**: `RadarWebSocketHandler.pushPointCloud` → `convertPointsToMap` → `sendToAll`（ObjectMapper）

- 每个点转成 `Map<String, Object>`（x, y, z, r, zoneId）
- 再对整个 message（含 type、timestamp、points 数组）做 `writeValueAsString`
- 5 万点 × 约 50 字节/点 ≈ 2.5MB+ JSON，序列化与网络发送都有明显成本

结论: **纯 JSON + 每点 Map** 在高点率下占用大量 CPU 和带宽，限制有效吞吐。

---

### 5. JNI 回调与 Java 侧处理

**位置**: `LivoxJNI.cpp` → `RadarService.handlePacket` → `submitPointCloud`

- JNI 侧仅做 `CallVoidMethod` 与 `NewByteArray`/`SetByteArrayRegion`，单次回调开销相对可控
- 丢点主要发生在 **Java 侧**：`submitPointCloud` 被拒绝、或后续缓冲/刷新丢弃，而非 JNI 或 SDK 回调阻塞导致漏收

结论: **漏点主要在后端 Java 流水线（线程池、SOR、缓冲/刷新、JSON）**，而不是 Livox SDK/JNI 接收端。

---

## 通过服务器日志定位点云瓶颈

部署后可通过以下日志判断瓶颈所在，便于针对性调参或优化代码。

### 1. 点云处理队列（RadarService）

- **关键字**：`点云处理队列已满，丢弃本帧`
- **含义**：Livox 回调提交到 `pointCloudExecutor` 时队列已满，本帧被丢弃，未进入 `routePointCloud`。
- **应对**：当前为 32 线程、256 队列。若仍频繁出现，可再增大 `POINT_CLOUD_QUEUE_CAPACITY` / `POINT_CLOUD_WORKER_THREADS`，或降低单帧耗时（关闭/弱化 SOR、减少侵入检测计算）。

### 2. 点云统计（每 60 秒）

- **关键字**：`[点云统计]`
- **内容**：本周期接收帧数/点数、**本周期丢弃帧数**、累计接收/丢弃、**处理队列排队数、工作中线程数**。
- **判断**：若「本周期丢弃」持续 > 0，说明处理队列仍是瓶颈；若「排队」长期接近 256，说明处理速度跟不上输入。

### 3. 点云发送队列（RadarWebSocketHandler）

- **关键字**：`点云发送队列已满，丢弃本帧`
- **含义**：`pushPointCloud` 提交到 `pointCloudSendExecutor` 时队列已满。
- **应对**：当前为 8 线程、512 队列。若仍出现，可增大发送线程/队列或检查网络/客户端消费速度。

### 4. 点云按帧推送（每 5 秒）

- **关键字**：`[点云按帧推送]`
- **内容**：总帧数、总点数、平均每帧点数、连接数。可对比前端实际收到的帧率与点数，判断是否在发送侧或网络/前端受限。

---

## 优化建议（按优先级）

### P0：扩大处理能力，减少“队列满丢帧”（已多次实施）

1. **增大点云处理队列容量**  
   - 当前：`POINT_CLOUD_QUEUE_CAPACITY = 256`（已从 8 → 64 → 128 → 256）。若日志中「本周期丢弃」仍 > 0，可再提高到 512。
2. **增加 worker 线程数**  
   - 当前：`POINT_CLOUD_WORKER_THREADS = 32`（已从 4 → 8 → 16 → 32）。可按 CPU 核数再调，或优先降低单帧耗时（SOR/侵入检测）。

### P0：降低单帧处理耗时（SOR）

3. **实时预览路径可关闭 SOR 或延后做**  
   - 在“仅实时点云推送、不做过重检测”的模式下，可配置 **关闭 SOR**，或先做轻量体素下采样再决定是否做 SOR，把吞吐提上去。
4. **先下采样再做 SOR（若必须保留 SOR）**  
   - 先对单帧做体素下采样（如 5cm），将 n 从 2000 降到 500 再做 SOR，复杂度从 O(2000²) 降到 O(500²)，单帧耗时显著下降。
5. **SOR 参数与调用条件**  
   - 仅当点数或业务需要时再做 SOR（例如仅检测模式、或点数 > 某阈值），并考虑减小 `kNeighbors`（如 20→10）以减轻计算。

### P1：提高 WebSocket 发送侧吞吐

6. **每 80ms 多批发送**  
   - 在 `flushPointCloudBatches` 中，每设备每 80ms 可 **连续取多批**（如最多 3 批或按时间片）直到缓冲不足或达到上限，避免只发一批导致缓冲堆积。
7. **提高缓冲上限与单批大小**  
   - `MAX_BUFFER_POINTS` 可提高到 **150000–200000**，`MAX_POINTS_PER_MESSAGE` 可保持或略调（如 40000），在保证单条消息不过大的前提下提高排水率。
8. **异步发送或独立发送线程**  
   - 将“序列化 + send”从 flusher 线程拆出：flusher 只组 batch、放入发送队列，由专门线程或线程池做 JSON 与 `sendString`，避免 flusher 被大 JSON 阻塞导致刷新频率下降。

### P2：减少序列化与带宽（已实施）

9. **二进制点云格式（已实施）**  
   - 服务端：点云通过 WebSocket **Binary 帧**发送。格式（小端序）：`1B type(0) + 8B timestamp + 4B pointCount + 每点 13B (x,y,z float + r byte)`。  
   - 前端：`services.parsePointCloudBinary(ArrayBuffer)` 解析二进制，`onmessage` 中自动识别 `ArrayBuffer/Blob` 走二进制路径，否则走 JSON（侵入/状态等）。  
   - 相比 JSON 大幅减少体积与序列化/解析 CPU，支撑 20 万点/秒。
10. **前端渲染节流（已实施）**  
    - 使用 **requestAnimationFrame** 节流：收到点云仅置 `pendingUpdateRef = true`，rAF 内最多一次 `setPointCloudData`，避免 20+ 次/秒 setState 导致主线程卡顿。
11. **按帧发送（已实施）**  
    - 不再按 80ms 或固定点数攒批：每收到**一帧**（一次 Livox 回调/一次 routePointCloud 输出）即提交发送任务，**按帧实时传输**。  
    - 消除“整万/整十万”的假象，与 Mid-360 非重复扫描的连续点率一致；前端按帧接收、滑动窗口展示，速率显示为一位小数（约 xxx.x 点/秒）。

---

## 预期效果（实施 P0 + 部分 P1 后）

- **队列 + 线程**：少丢帧甚至不丢帧（在 20 万点/秒、关闭或弱化 SOR 时）。
- **关闭/延后 SOR 或先下采样再 SOR**：单帧耗时从 50–200ms 降到数 ms 级，处理帧率可接近或超过 100 帧/秒。
- **多批刷新 + 缓冲与发送解耦**：WebSocket 端可稳定输出 **15–20 万点/秒**，漏点现象明显缓解。

建议先做 **P0（队列/线程 + SOR 策略）**，观察端到端点率与日志中的“点云处理队列已满”是否消失，再按需做 P1/P2。
