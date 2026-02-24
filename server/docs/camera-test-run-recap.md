# 摄像头核心功能测试运行复盘

**执行时间**: 2026-02-03  
**执行环境**: 服务器 192.168.1.210 (Ubuntu, x86_64)  
**测试脚本**: `run_tests_on_server.sh` → `CameraTestRunner`

---

## 一、测试结果汇总

| 指标     | 数值 |
|----------|------|
| **通过** | 5    |
| **失败** | 13   |
| **退出码** | 1（存在失败） |

所有通过的用例均来自 **海康威视 192.168.1.100**；天地伟业 192.168.1.10、大华 192.168.1.200 相关用例全部失败。

---

## 二、失败原因分析

### 1. 天地伟业 192.168.1.10 — 全部失败（网络不可达）

- **现象**：登录失败，SDK 报错。
- **日志要点**：
  - `NetClient_LoadOsCore::LoadOSSDK fail!`
  - `[CLS_TCPClient::InnerReceive] socket error 1, ip=192.168.1.10, errno=111`  
    → **errno 111 = Connection refused**，表示服务器连不上该 IP 的端口。
  - 天地伟业 SDK 错误码: `268435511`（与网络/连接相关）。
- **结论**：从 192.168.1.210 无法访问 192.168.1.10（设备离线、未开服务、或网络/防火墙隔离）。**属于环境/网络问题，非业务代码错误。**

### 2. 大华 192.168.1.200 — 全部失败（登录超时）

- **现象**：登录超时。
- **日志要点**：
  - 大华 SDK：`error code: (0x80000000|107)` — **登录设备超时，请检查网络并重试**。
- **结论**：从 192.168.1.210 无法在超时时间内与 192.168.1.200 建立连接（设备离线、网络不通或端口/防火墙限制）。**属于环境/网络问题。**

### 3. 海康威视 192.168.1.100 — 仅 1 条失败

- **失败用例**：`Reconnect-hikvision-192.168.1.100`
- **失败信息**：`登出后仍显示已登录`
- **含义**：执行 `logoutDevice` 后，`isDeviceLoggedIn(deviceId)` 仍为 true。
- **可能原因**：
  - 海康 SDK 的 `logout` 返回了 false（例如连接已断开），我方未从 `deviceLoginMap` 移除，导致状态仍为“已登录”。
  - 或登出为异步，校验时尚未完成。
- **结论**：属**逻辑/兼容性**问题：需在“登出失败”或“登出未完成”时也正确更新或延迟校验登录状态。

### 4. 天地伟业 SDK 抓图内部超时 (GET_SNAP_PARA_TIME_OUT) — 已优化

- **现象**：连续对天地伟业设备抓图时，SDK 报错  
  `[CLS_PictureLayer::GetSnapshotPic] ulTimeSpan(-1731999604) >= GET_SNAP_PARA_TIME_OUT!`、`CapturePicByDevice GetSnapshotPic Failed`。
- **原因**：天地伟业 SDK 内部对同设备两次抓图有最小时间间隔限制，请求过密会触发超时判定（`ulTimeSpan` 与 `GET_SNAP_PARA_TIME_OUT` 比较）。
- **优化**：  
  - 在 **CaptureService** 中为天地伟业设备增加专用冷却时间 **TIANDY_CAPTURE_COOLDOWN_MS = 4000**：同步/异步抓图前若距上次抓图不足 4 秒，则先等待至满 4 秒再调用 SDK。  
  - 异步路径的冷却判断对天地伟业使用同一 4 秒冷却。  
  - 测试用例中，对天地伟业设备连续抓图间隔改为 **4500ms**，与冷却策略一致，避免触发上述 ERR。

### 5. 天地伟业抓图 CompletableFuture 超时 (8s 不足) — 已优化

- **现象**：异步抓图时出现 `抓图超时(8000ms): deviceId=..., channel=...`，即 `CaptureService` 内对 `CompletableFuture.get(8000, ms)` 在 8 秒内未完成。
- **原因**：天地伟业 `NetClient_CapturePicByDevice` 为同步 native 调用，在设备或网络较慢时可能超过 8 秒，与海康等设备共用 8 秒超时偏短。
- **优化**：  
  - 在 **CaptureService** 中新增 **TIANDY_CAPTURE_TIMEOUT_MS**（现为 **5000**，与海康等统一为 5 秒内未完成即视为失败）。  
  - 异步/同步抓图对天地伟业均使用该超时；等待“当前抓图完成”的轮询最大等待时间也按设备类型使用该超时。

### 5.1 接口同步抓图无超时导致 HTTP 线程阻塞 — 已修复

- **现象**：通过 **POST /api/devices/:id/snapshot** 对天地伟业 192.168.1.10 抓图时，接口一直不返回，客户端约 65 秒后超时（HTTP 000）。
- **原因**：接口走 **同步抓图** `captureSnapshot()`，在 HTTP 工作线程内直接调用 `sdk.capturePicture()` → 天地伟业 `NetClient_CapturePicByDevice`。SDK 在设备不可达或响应慢时会**长时间阻塞**（无/很长超时），导致 HTTP 线程一直被占，接口无响应。
- **日志佐证**：服务器日志在请求进入 CaptureService（“天地伟业设备通道号调整为0: deviceId=192.168.1.10”）后，无“抓图成功/失败”输出，直到客户端超时；且曾出现 SDK 内部 `GetSnapshotPic ulTimeSpan >= GET_SNAP_PARA_TIME_OUT`、`CapturePicByDevice GetSnapshotPic Failed`。
- **修复**：在 **CaptureService.captureSnapshot()** 中，将 `sdk.capturePicture(...)` 改为提交到 **captureExecutor** 后 **get(timeoutMs)**：天地伟业 20 秒、其他 8 秒。超时则 `future.cancel(true)` 并返回 null，接口可正常返回 500“抓图失败”，不再无限阻塞。

### 5.2 接口抓图与报警工作流争锁导致“设备正在抓图中，等待超时” — 已优化

- **现象**：更新后接口仍返回抓图失败；日志出现“设备正在抓图中，等待超时: 192.168.1.10”，无“抓图成功/失败”即返回。
- **原因**：报警工作流（default_alarm_flow）触发对同一设备 192.168.1.10 的**异步抓图**，持锁约 20 秒（TIANDY_CAPTURE_TIMEOUT_MS）；用户调用 **POST /snapshot**（同步抓图）时仅 **tryLock(5 秒)**，5 秒内拿不到锁即返回 null → 500 抓图失败。
- **优化**：在 **CaptureService.captureSnapshot()** 中，对**天地伟业**设备将等锁时间改为 **TIANDY_CAPTURE_TIMEOUT_MS/1000 + 5**（当前为 10 秒），其他设备仍为 5 秒。异步抓图超时（5 秒）释放锁后，接口请求可排队拿到锁并执行一次抓图。

### 6. 进程结束时的 JVM 崩溃 (SIGSEGV)

- **现象**：测试逻辑跑完后，在清理阶段出现：
  - `SIGSEGV (0xb) at pc=0x000076feaa4c6450`
  - `Problematic frame: C 0x00000000000002b4`
- **含义**：在 **native 代码**（多为 SDK 的 JNI/so）中发生段错误，通常发生在 `SDKFactory.cleanup()` 或 `HikvisionSDK.getInstance().cleanup()` 等卸载/析构逻辑中。
- **结论**：**SDK 在进程退出时的清理存在已知风险**，不影响本次测试结果判定（用例已执行完毕），但需要在文档中标注，并在后续考虑：减少不必要的 cleanup 调用，或接受“测试进程退出时可能 coredump”。

---

## 三、按设备与测试项的结果矩阵

| 测试项           | 天地伟业 192.168.1.10 | 海康 192.168.1.100 | 大华 192.168.1.200 |
|------------------|------------------------|--------------------|---------------------|
| 1. 登录          | ❌ 连接被拒绝          | ✅ 通过            | ❌ 登录超时         |
| 2. 断网重连      | ❌ 首次登录失败        | ❌ 登出后仍显示已登录 | ❌ 首次登录失败   |
| 3. 高速抓拍      | ❌ 登录失败            | ✅ 通过            | ❌ 登录失败         |
| 4. 录像下载      | ❌ 登录失败            | ✅ 通过（含转码）  | ❌ 登录失败         |
| 5. 云台控制      | ❌ 登录失败            | ✅ 通过            | ❌ 登录失败         |
| 6. 设备信息      | ❌ 登录失败            | ✅ 通过            | ❌ 登录失败         |

---

## 四、结论与建议

### 1. 环境与网络

- 当前仅 **海康 192.168.1.100** 从服务器 192.168.1.210 可稳定连通。
- **192.168.1.10（天地伟业）**、**192.168.1.200（大华）** 在测试时不可达，需在实机/网络侧确认：
  - 设备已上电、已接入同一网段或路由可达；
  - 端口开放（天地伟业 37777、大华 37777）；
  - 防火墙、ACL 未拦截 192.168.1.210 的访问。

### 2. 代码与用例

- **海康**：登录、抓拍、录像下载、云台、设备信息均通过，说明在**可达设备**上核心流程正常。
- **登出状态（已修复）**：已修改 `DeviceManager.logoutDevice`：无论 SDK 的 `logout()` 是否返回 true，都会清除本地 `deviceLoginMap`/`deviceSDKMap` 并更新数据库状态，从而“登出后仍显示已登录”的断言可通过。
- **清理阶段崩溃**：在文档中注明“测试进程退出时可能因 SDK 清理触发 SIGSEGV”；若需稳定 CI，可考虑在测试结束前不调用易崩溃的 SDK cleanup，或单独进程跑测试。

### 3. 测试套件本身

- 测试流程执行完整，通过/失败统计正确，失败原因可追溯。
- 建议后续支持“按设备跳过”（如某 IP 不可达时自动跳过该设备所有用例并标记为 SKIP），便于在部分设备离线时仍能稳定跑通其他设备。

### 7. 抓图失败原因与日志位置（2026-02-03 复盘）

- **抓图日志在哪里**：抓图相关日志在 **server.log**、**logs/app.log** 中都有，出现在**启动日志之后**（例如报警触发后“提交异步抓图任务”“天地伟业设备通道号调整为0”，以及“抓图超时(25000ms)”或“抓图失败”）。**logs/sdk.log** 中有 TiandySDK 的“天地伟业直接抓图失败 … 错误码=…”（sdk.log 可能含二进制，用 `grep -a` 搜索）。
- **抓图失败的两类表现**：
  1. **抓图超时(25000ms)**：`NetClient_CapturePicByDevice` 在 25 秒内未返回，CaptureService 判超时并记录“抓图超时(25000ms): deviceId=…”。
  2. **抓图失败**（无“超时”字样）：SDK 已返回，但返回值为失败；在 **sdk.log** 中可见 TiandySDK：“天地伟业直接抓图失败: userId=…, filePath=…, **错误码=-1**”（或 **错误码=-107**）。
- **错误码含义**（来源：`sdk/天地伟业CH-NetSDK(Linux64)V5.5.0.0_build20250325/头文件/RetValue.h`）：
  - **-1 = RET_FAILED**：失败（通用），不区分具体原因。
  - **-106 = RET_DEVICE_CAPTURE_FAIL**：设备远程抓拍失败，设备返回的数据长度为 0。
  - **-107 = RET_DEVICE_CAPTURE_TIMEOUT**：设备远程抓拍超时，设备未回复数据。
  建议保证同一设备两次抓图间隔 ≥4 秒，并检查设备可达性与网络；出现 -107 时可适当增大超时或检查设备响应。

### 8. 天地伟业 NetClient_CapturePicByDevice 参数与调用方式确认

- **对照依据**：`sdk/天地伟业CH-NetSDK(Linux64)V5.5.0.0_build20250325/头文件/NetSdkClient.h`（约 3830–3848 行）、`NetSdk.h`、`NetClientTypes.h`（SnapPicData 结构）。
- **SDK 声明**：  
  `int NetClient_CapturePicByDevice(int _iLogonID, int _iChanNo, int _iQvalue, char* _pcPicFilePath, SnapPicData* _ptSnapPicData, int _iInSize);`
- **参数核对**：
  | 参数 | SDK 说明 | 当前实现 | 结论 |
  |------|----------|----------|------|
  | _iLogonID | 登录句柄 | `userId`（SyncLogon 返回） | ✅ |
  | _iChanNo | 通道号 | `channelNo`（0-based，channel>0 时 channel-1 否则 0） | ✅ |
  | _iQvalue | Q 值（质量） | `0`（使用设备默认） | ✅ |
  | _pcPicFilePath | 保存路径（char*，以 \\0 结尾） | `ByteBuffer.wrap(actualFilePath.getBytes(UTF_8))`，路径末尾已加 `\0` | ✅ |
  | _ptSnapPicData | 存图结构；NULL 表示只保存到文件 | `null` | ✅ |
  | _iInSize | 结构体大小；ptSnapPicData 为 NULL 时 | `0` | ✅ |
- **调用方式**：使用 `NetClient_CapturePicByDevice` 直接抓图（无需预览连接）；仅保存到文件时传 `ptSnapPicData=null`、`iInSize=0`，与头文件“when _ptSnapPicData==NULL: don't save picture data”一致。
- **结论**：**当前参数与调用方法无误**；抓图仍失败时，原因在设备/网络（如 -1/-107）或抓图间隔/超时，而非接口参数或 JNA 映射错误。

---

## 五、附录：本次通过用例列表

1. `Login-hikvision-192.168.1.100`
2. `Capture-hikvision-192.168.1.100 x10 (成功10/10)`
3. `RecordingDownload-hikvision-192.168.1.100 (文件存在)`
4. `PTZ-hikvision-192.168.1.100`
5. `DeviceInfo-hikvision-192.168.1.100`

以上 5 条均在 **海康威视 192.168.1.100** 上通过。
