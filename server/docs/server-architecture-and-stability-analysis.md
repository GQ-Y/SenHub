# Server 系统架构与稳定性分析

## 一、系统架构概览

### 1.1 定位与技术栈

- **定位**：综合性数字视频监控网关（SenHub），多品牌摄像头/雷达统一接入、报警事件标准化、MQTT/WebSocket 开放协议输出。
- **技术栈**：Java 11、Spark Java（HTTP）、Jetty WebSocket、Eclipse Paho MQTT、JNA 多品牌 SDK、SQLite、Netty（UDP）、阿里云 OSS / MinIO。

### 1.2 整体分层

```
┌─────────────────────────────────────────────────────────────────────────┐
│  HTTP/WebSocket (Spark + Jetty)  │  MQTT (Paho)                          │
├──────────────────────────────────┴──────────────────────────────────────┤
│  API 层：DeviceController, RadarController, FlowController, Mqtt...   │
├─────────────────────────────────────────────────────────────────────────┤
│  服务层：DeviceManager, AlarmService, RadarService, CaptureService,     │
│          PTZService, RecorderService, FlowExecutor, ConfigService...    │
├─────────────────────────────────────────────────────────────────────────┤
│  设备/SDK 层：HikvisionSDK, DahuaSDK, TiandySDK, LivoxDriver (JNI)       │
├─────────────────────────────────────────────────────────────────────────┤
│  数据层：Database (SQLite, 单连接 + connectionLock), OSS                 │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.3 主流程（Main.start）

1. 日志清理 → 加载 config.yaml → ConfigService（数据库优先）→ SDKFactory.init  
2. Database.init → DeviceManager → 各业务 Service（Recorder, Capture, PTZ, Alarm, Assembly, Flow, Radar…）  
3. Recorder.start → MqttClient.connect（失败不退出）→ 设置 MQTT 主题/命令处理器 → 自动连接已有设备 → 设置各 SDK 状态/报警回调  
4. CommandHandler → DeviceScanner.start（可选）→ Keeper.start → 设备状态统计定时任务 → startHttpServer（端口 8084）  
5. 后台线程：2 秒后为已有设备启动录制  
6. 注册 JVM shutdown hook → 主线程 `Thread.currentThread().join()` 保持运行  

---

## 二、核心流程设计

### 2.1 设备生命周期

- **发现**：DeviceScanner（海康监听 + 主动扫描）→ `onDeviceFound` → loginDevice → 发布 MQTT 状态、可选启动录制。  
- **保活**：Keeper 定时（config 检查间隔）遍历设备，未登录则尝试 login，连续失败达到 `offlineThreshold` 则置离线。  
- **离线**：SDK 回调 / 保活判定 → DeviceManager.updateDeviceStatus(0) → MQTT 发布 offline。  

### 2.2 报警与工作流

- 海康/大华/天地 SDK 报警回调 → AlarmService → 规则匹配 → 写库、MQTT 上报、**同步**执行 FlowExecutor（抓图、OSS、Webhook、PTZ 等）。  
- MQTT 命令主题：`topicMessageHandler` → CommandHandler.handleCommand → 发布 response。  
- MQTT 工作流主题：按 topic 匹配 FlowDefinition → FlowExecutor.execute(definition, context)（同一线程内同步执行）。  

### 2.3 雷达与点云

- LivoxDriver (JNI) 收包 → handlePacket → 解析后 **提交到 pointCloudExecutor**（避免阻塞回调）→ routePointCloud（降噪/检测/推送）。  
- 推送：RadarWebSocketHandler.pushPointCloud → pointCloudSendExecutor 异步发送；状态由 RadarStatusMonitor / 点云超时检测维护。  

### 2.4 MQTT

- 连接：单例 MqttAsyncClient，连接失败不阻塞启动；LWT 到 senhub/gateway/status。  
- 重连：connectionLost → 后台 new Thread 重连，最多 5 次，间隔 5 秒，之后不再重试。  
- 消息：messageArrived 在 Paho 回调线程中直接调用 topicMessageHandler（命令处理 + 工作流执行）。  

---

## 三、存在的稳定性与设计缺陷

### 3.1 关闭顺序与资源未释放（高影响）

**现象**：`Main.shutdown()` 未停止部分服务，且部分服务内部线程池未关闭，导致 JVM 退出依赖超时或非 daemon 线程。

| 问题点 | 说明 |
|--------|------|
| **radarService 未停止** | `Main.shutdown()` 未调用 `radarService.stop()`。若雷达已启动，Livox 驱动、超时检测、统计任务等未收尾。 |
| **ptzMonitorService 未停止** | 未调用 `ptzMonitorService.stop()`，定时轮询线程不会退出。 |
| **RadarService.stop() 不完整** | 仅关闭了 `statsExecutor`、`timeoutCheckExecutor`、livoxDriver、statusMonitor；**未关闭** `pointCloudExecutor`，且静态 **`ptzCaptureScheduler`** 从未 shutdown，存在线程与资源泄漏。 |
| **RadarWebSocketHandler** | `pointCloudSendExecutor` 未暴露 shutdown 接口，Main 也未在关闭时调用，关闭后仍可能提交任务。 |
| **DeviceScanner.stop()** | 仅停止 SDK 监听（listenHandle），**未关闭 scanExecutor**。若曾调用过 startActiveScanning，会创建 FixedThreadPool(10)，stop 后该池仍存在。 |
| **Recorder.stop()** | 仅 `cleanupScheduler.shutdownNow()`，无 `awaitTermination`，对快速退出可接受，但非优雅收尾。 |

**建议**：

- 在 `Main.shutdown()` 中按依赖顺序增加：  
  `ptzMonitorService.stop()` → `radarService.stop()`（并在 RadarService.stop 内关闭 pointCloudExecutor、静态 ptzCaptureScheduler）。  
- 为 RadarWebSocketHandler 增加 `shutdown()`，关闭 pointCloudSendExecutor，并在 Main 或 RadarService 关闭时调用。  
- DeviceScanner.stop() 中若 `scanExecutor != null`，执行 shutdown/awaitTermination。  

---

### 3.2 MQTT 回调线程被长时间占用（高影响）

**现象**：`messageArrived` 在 Paho 回调线程中同步执行：

- 命令处理：`commandHandler.handleCommand(payload)`（可能含设备登录、抓图、PTZ、回放等 IO）。  
- 工作流执行：`flowExecutor.execute(def, ctx)`（抓图、OSS 上传、Webhook、录像等）。  

长时间任务会阻塞该线程，导致：

- 同一连接下其他主题消息处理延迟。  
- 心跳与协议处理被推迟，在负载高时易被 Broker 判定断线或堆积。  

**建议**：将“命令处理”和“工作流执行”提交到独立线程池（有界队列 + 明确拒绝策略），MQTT 回调仅做解析与投递，避免在回调线程做 IO 或重计算。

---

### 3.3 MQTT 重连策略（中影响）

**现象**：

- 重连仅在“当前一次 connectionLost”后重试 5 次，失败后不再重试，需依赖外部重启或 API 再次连接。  
- 每次重连 `new Thread("MQTT Reconnect Thread")`，无统一线程管理；多次断线会多次创建新线程。  

**建议**：重连改为“持续重试”或“指数退避 + 最大间隔”，并复用单一线程或小型调度器执行重连，避免无界 new Thread。

---

### 3.4 工作流执行与线程池（中影响）

**现象**：

- `FlowExecutor.execute()` 为**同步**执行，由调用线程（如 MQTT 回调）直接跑完整个 DAG。  
- 分支并行使用 `Executors.newCachedThreadPool()`，高并发或分支多时线程数可能持续增长。  

**建议**：  
- 从 MQTT 侧调用时，将 `flowExecutor.execute` 提交到专用有界线程池执行，避免阻塞 MQTT 回调。  
- 将 branchExecutor 改为有界线程池（或固定大小 + 有界队列），并考虑在 Main.shutdown 时关闭该池。

---

### 3.5 Keeper 与保活（低～中影响）

**现象**：

- `failureCountMap` 只增不减：设备从库中删除后，其 deviceId 仍保留在 map 中，长时间运行可能缓慢增长。  
- 单线程定时轮询所有设备，单个设备 login 慢（如网络超时）会拖慢整轮，影响其他设备保活及时性。  

**建议**：  
- 在检查前或删除设备时，从 failureCountMap 移除对应 deviceId。  
- 视需求考虑按设备并行检查（有界线程池），或为单次 login 设置超时。

---

### 3.6 其他线程/资源（低影响）

| 组件 | 说明 |
|------|------|
| **RecordingTaskService** | 内部 `executorService`、`scheduler` 未在 Main.shutdown 中关闭。 |
| **CaptureService** | `captureExecutor` 固定线程池，未在 shutdown 关闭。 |
| **FlowExecutor.branchExecutor** | 静态 CachedThreadPool，未在进程退出前 shutdown。 |

统一在 Main.shutdown 中按依赖关系关闭所有业务线程池，可避免“僵尸”任务和线程堆积。

---

### 3.7 数据库与并发

- Database 使用**单连接 + connectionLock** 串行化访问，符合 SQLite 典型用法，能避免 connection closed / database locked。  
- 关闭顺序当前为：keeper → scanner → statisticsScheduler → recorder → deviceManager.logoutAll → mqttClient.close → **database.close** → Spark.stop → SDKFactory.cleanup。  
- 若在 database.close 之后仍有异步任务（如未关闭的 RecordingTaskService、FlowExecutor）访问数据库，会抛异常。因此**先停所有使用 DB 的定时/线程池，再关库**更安全。

---

## 四、稳定性改进优先级建议（已实施）

1. **P0** ✅补全 Main.shutdown 与 RadarService/DeviceScanner/RadarWebSocketHandler 的停止与线程池关闭，避免泄漏与无法干净退出。  
2. **P0** ✅ MQTT 消息处理已为“入队 + 独立线程池执行”，避免长时间阻塞 Paho 回调线程。  
3. **P1** ✅ MQTT 重连已改为持续重试 + 单线程/调度器；FlowExecutor 从 MQTT 侧改为异步执行，branchExecutor 有界化并参与 shutdown。  
4. **P2** ✅ Keeper 的 failureCountMap 已清理已删除设备；业务线程池已在 Main.shutdown 统一关闭。

---

## 五、小结

- **架构**：分层清晰，设备/报警/工作流/MQTT/雷达点云路径明确；单库单连接 + 锁、MQTT 失败不阻塞启动等设计合理。  
- **流程**：设备发现→保活→离线、报警→规则→工作流、点云→线程池解耦，主流程设计正确。  
- **稳定性**：主要风险在于**关闭阶段未停止雷达/PTZ 监控及多处线程池未关闭**、**MQTT 回调线程被同步重任务阻塞**、**MQTT 重连有限次后放弃**以及**工作流与分支线程池的无界/未收尾**。按上述 P0/P1/P2 逐步修补，可显著提升长期运行与优雅退出时的稳定性。
