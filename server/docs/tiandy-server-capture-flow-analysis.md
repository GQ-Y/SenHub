# 天地伟业 Server 端登录→预览→抓图流程分析

本文档分析当前 server 系统中天地伟业设备的**登录 → 预览 → 抓图**流程是否正确，以及与独立 Demo、官方示例的差异；并说明设备 1 在 Demo 中出现的 `__fopen(.../xxx.jpg/Object) failed` 与 server 实现的关系。

---

## 一、Server 端完整流程梳理

### 1. 调用链

- **入口**：`CaptureService.captureSync()` / `captureAsync()`
- **前置条件**：设备已通过 `DeviceManager.loginDevice()` 登录，`deviceManager.getDeviceUserId(deviceId)` 得到 `userId`（即天地伟业 logon handle）
- **调用**：`sdk.capturePicture(connectId, userId, channelForCapture, fileName, pictureType)`
  - 天地伟业时 `connectId` 必须为 **登录后已启动的预览 connectID**（`deviceManager.getTiandyPreviewConnectId(deviceId)`）；**无预览连接时不执行抓图**（与 TiandyCaptureDemo 一致）。
  - `fileName` 为**绝对路径**且已带 `.jpg` 后缀（由 `CaptureService` 生成）

### 2. TiandySDK.capturePicture 内部流程（仅在有预览时抓图）

| 步骤 | 动作 | 说明 |
|------|------|------|
| 1 | 校验 SDK 已初始化 | `initialized`、`nvssdkLibrary != null` |
| 2 | **无预览则拒绝** | 若 `connectId < 0`：直接返回 false，不启动预览、不执行抓图 |
| 3 | 规范保存路径 | 去掉 `\0`、确保以 `.jpg` 结尾，得到 `actualFilePath` |
| 4 | 抓一帧到内存 | `NetClient_CapturePic(connectId, pucData)` 复用已有预览 |
| 5 | 写文件 | `inferYuv422Resolution` + `yuv422ToJpg` 写入 `actualFilePath` |

即：**仅当存在预览连接时**才执行“抓一帧 → 转 JPG 写文件”；**无预览连接时不做任何抓图流程**（不按次启停预览）。  
**未使用** `NetClient_CapturePicture`（SDK 按路径直接写文件）。

### 3. startRealPlay 内部（与官方示例一致）

- 使用 `NetClient_GetLogonStatus(userId)` 校验已登录
- 使用 `NetClient_GetDigitalChannelNum` / `NetClient_GetChannelNum` 校验通道号
- 填充 `tagNetClientPara`（含 `pCbkFullFrm` / `pCbkRawFrm` 非空回调、子码流、超时等）
- 调用 `NetClient_SyncRealPlay` 得到 `connectID`
- 与官方 Channel.java 及已修复的“回调不可为 null”策略一致

### 4. 通道号约定

- **CaptureService**：天地伟业且 `actualChannel == 1` 时改为 `0`，再传给 SDK
- **TiandySDK**：`channelNo = channel > 0 ? channel - 1 : 0`，即 1-based 转 0-based  
两处配合后，传入的 channel 在 SDK 内都会按 0-based 使用，**一致、正确**。

---

## 二、与 Demo / 官方示例的差异

| 项目 | Server (TiandySDK) | Demo (TiandyCaptureDemo) |
|------|--------------------|---------------------------|
| 抓图 API | **NetClient_CapturePic**（YUV 到内存） | **NetClient_CapturePicture**（SDK 按路径写文件） |
| 文件写入 | Java 侧 `yuv422ToJpg` 写 `actualFilePath` | SDK 内部根据传入的 ByteBuffer 路径写文件 |
| 路径来源 | 完全由 Java 控制（绝对路径 + `.jpg`） | `ByteBuffer.wrap(filePath.getBytes())` 传予 SDK |
| 预览与抓图顺序 | 仅在有预览连接时抓图，无预览则拒绝 | 一次预览，等首帧（或 3s）后再抓图 |

因此：

- **Server 流程**：登录（由 DeviceManager 保证）→ 每次抓图时 startRealPlay → NetClient_CapturePic → 自写 JPG → stopRealPlay，**与官方“先预览再抓图”一致**，且**不经过** `NetClient_CapturePicture`。
- **设备 1 在 Demo 中**出现的 `__fopen(.../tiandy_192_168_1_10_0002.jpg/Object) failed` 来自 **Demo 使用的 NetClient_CapturePicture**（同一预览连接上连续写多张图）。SDK 在部分设备/场景下可能将路径误当作目录再拼 `/Object`，属于 **NetClient_CapturePicture 的用法或 SDK 行为问题**，**与当前 server 实现无关**（server 根本未调用该 API）。

---

## 三、流程正确性结论

- **登录**：由 DeviceManager 在抓图前保证已登录，`userId` 正确传入 TiandySDK。
- **预览**：每次抓图前 `startRealPlay(userId, channel, 1)`，参数与官方一致，且 `tagNetClientPara` 中回调已设为非空，**正确**。
- **抓图**：使用 `NetClient_CapturePic` + 自写 JPG，**不依赖** `NetClient_CapturePicture`，因此：
  - 不会出现 Demo 里“路径被改为 xxx.jpg/Object”的 `__fopen` 问题；
  - 流程上“先起预览再抓一帧再停预览”**正确**。

整体上，**当前 server 的天地伟业登录→预览→抓图流程是正确的**；设备 1 在独立 Demo 中的异常不影响 server 行为。

---

## 四、预览复用策略（已实现）

为保障天地伟业抓图实时性，当前实现为：

1. **登录即启预览**：`DeviceManager.loginDevice()` 在天地伟业设备登录成功后，立即调用 `TiandySDK.startRealPlay(userId, channel, 1)` 启动子码流预览，并将返回的 `connectID` 存入 `tiandyPreviewConnectMap`。
2. **抓图复用**：`CaptureService` 调用抓图时，若为天地伟业则先取 `deviceManager.getTiandyPreviewConnectId(deviceId)`；若 ≥0 则将该 `connectId` 传入 `TiandySDK.capturePicture()`，内部直接使用 `NetClient_CapturePic(connectId, ...)`，**不再**每次启停预览。
3. **登出/离线清理**：`logoutDevice()` 或设备离线回调中将该设备的预览 `connectID` 从 map 移除并调用 `stopRealPlay`。
4. **失败时清理**：抓图超时或返回失败且本次使用的是复用连接时，调用 `deviceManager.clearTiandyPreview(deviceId)`，之后无预览连接，抓图请求将直接跳过直至设备重新登录并再次建立预览。
5. **预览未成功前不抓图**：与 TiandyCaptureDemo 一致，`CaptureService` 在天地伟业无预览连接时直接跳过抓图并打日志；`TiandySDK.capturePicture` 在 `connectId < 0` 时直接返回 false，不执行任何启停预览或抓图逻辑。
6. **登录后延迟再记预览**：`startRealPlay` 返回成功后等待 2 秒再写入 `tiandyPreviewConnectMap`，给预览流一定建立时间，再允许触发抓图。

因此天地伟业设备只有在“登录且预览已建立”后才会接受抓图，抓图从该连接直接取帧，实时性更好。

## 五、异步抓图与线程模型（与 Demo 对齐）

**TiandyCaptureDemo** 中抓图是**主线程同步**执行的：同一线程先 `NetClient_SyncRealPlay`，再在循环里直接 `NetClient_CapturePicture`，无线程池嵌套。

此前异步抓图实现为：`captureExecutor.submit(lambda)` 内再 `CompletableFuture.supplyAsync(() -> capturePicture(...), captureExecutor).get(timeout)`。导致：

1. **同一 4 线程池嵌套**：外层占一个 worker 并 `get()` 等待，内层再向同一池提交抓图；当多个异步抓图同时进行时，所有 worker 都在等内层任务，内层任务无人执行 → **死锁**，表现为超时或无回调。
2. **与 Demo 的“单线程同步调用”不一致**：部分 native SDK 对调用线程或顺序敏感。

**已做修改**：异步抓图路径中**不再嵌套线程池**，在**同一 worker 线程内直接同步调用** `sdk.capturePicture(...)`，再根据结果执行回调。这样：

- 每个异步抓图只占用 1 个 worker，不会死锁；
- 与 Demo 的“单线程同步抓图”模式一致，便于 SDK 稳定工作。

同步抓图仍使用 `captureExecutor.submit(() -> capturePicture(...)).get(timeout)`（由 HTTP 线程等待），仅占用 1 个 worker，无嵌套。

## 六、可优化点（非错误）

1. **首帧再抓图**：Demo 中“等首帧回调再抓”在 server 中未实现，目前依赖 `NetClient_SyncRealPlay` 成功即抓。若在实际环境中发现首帧未到就抓导致失败，可在 `startRealPlay` 成功后再等首帧或短延时再调 `NetClient_CapturePic`，作为后续增强选项。

---

## 七、相关文件与文档

- 流程与官方对照：`server/docs/tiandy-capture-flow-vs-official.md`
- 预览启动与日志：`server/docs/tiandy-capture-modes.md`
- 实现位置：
  - `server/src/main/java/com/digital/video/gateway/tiandy/TiandySDK.java`（`startRealPlay`、`capturePicture`）
  - `server/src/main/java/com/digital/video/gateway/service/CaptureService.java`（冷却、超时、重试、文件名生成）
