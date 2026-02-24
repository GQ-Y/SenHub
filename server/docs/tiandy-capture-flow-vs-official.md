# 天地伟业抓图流程与官方示例逐项对照

## 对照来源

- **官方**：`JavaClientDemo`（1-综合示例）`Channel.java`、`Device.java`；`JavaVideoCtrlDemo`（5-预览控制示例）`VideoCtrl.java`、`NetClient.java`、`NVSSDK.java`。
- **本工程**：`TiandySDK.java`、`NvssdkLibrary.java`、`TiandySDKStructure.java`，`CaptureService.java` 调用链。

---

## 1. 登录（Login）

| 项目 | 官方 | 本工程 | 一致性 |
|------|------|--------|--------|
| 接口 | `NetClient_SyncLogon(iLogonType, tNormal.getPointer(), tNormal.iSize)`（Device.java:266） | `NetClient_SyncLogon`，参数通过 `TiandySDKStructure.tagLogonPara` | ✅ 一致 |
| 登录类型 | SERVER_NORMAL(0) / SERVER_ACTIVE(1) | 同左 | ✅ |
| 获取 logonID | 返回值 ≥0 为 logonID | 同左，由 DeviceManager 维护 userId↔设备 | ✅ |
| 登录状态 | 可选 `NetClient_GetLogonStatus(iLogonID)` | 在 `startRealPlay` 前做一次校验 | ✅ 一致（官方在 SyncRealPlay 前不强制调用） |

---

## 2. 预览启动（SyncRealPlay）

| 项目 | 官方 Channel.java | 本工程 TiandySDK.startRealPlay | 一致性 |
|------|-------------------|----------------------------------|--------|
| 通道数 | 先 `NetClient_GetDigitalChannelNum`，再 `NetClient_GetChannelNum` | 同左 | ✅ |
| 通道号 | 0-based，校验 `0 ~ iDigitalChanCount-1` | 同左，1-based 入参转 0-based | ✅ |
| 结构体 | `tagNetClientPara`，只调用一次 `tVideoPara.write()` | `TiandySDKStructure.tagNetClientPara`，先 `tCltInfo.write()` 再 `tVideoPara.write()` | ✅ 等价 |
| m_iServerID | `m_iLogonID` | `userId`（即 logonID） | ✅ |
| m_iChannelNo | 0-based | 0-based | ✅ |
| m_iStreamNO | 0=主 1=子 | 抓图用 1（子码流） | ✅ |
| m_iNetMode | 1（TCP） | 1 | ✅ |
| m_iTimeout | 20（秒） | 10（秒，便于 25s 抓图超时内得到结果） | ⚠️ 可调回 20 试设备 |
| **pCbkFullFrm** | **cbkPrivateFullFrame（非 null）** | **原为 null → 已改为 realPlayCbkFullFrame** | ✅ 已对齐 |
| **pCbkRawFrm** | **cbkRawFrame（非 null）** | **原为 null → 已改为 realPlayCbkRawFrame** | ✅ 已对齐 |
| pvCbk*UsrData | null | null | ✅ |
| iIsForbidDecode | RAW_NOTIFY_ALLOW_DECODE | 同左 | ✅ |
| pvWnd | null | null | ✅ |
| 输出 connectID | `IntBuffer.allocate(1)`，传参后 `piConnectID.get()` | `IntByReference`，传参后 `getValue()` | ✅ JNA 等价 |
| 调用方式 | `NetClient_SyncRealPlay(piConnectID, tVideoPara, tVideoPara.iSize)` 传 Structure | `NetClient_SyncRealPlay(piConnectID, tVideoPara.getPointer(), tVideoPara.iSize)` 传 Pointer | ✅ 等价 |

**重要**：官方注释写明 “If the parameter is not empty, it is the original stream callback”。传 null 时 SDK 可能不建立流或表现异常，因此已改为与官方一致传入非空空实现回调。

---

## 3. 抓图（Capture）

| 项目 | 官方 Channel.SnapShot / VideoCtrl | 本工程 TiandySDK.capturePicture | 一致性 |
|------|-----------------------------------|-----------------------------------|--------|
| 前置条件 | 先 SyncRealPlay 得到 m_iConnectID | 先 startRealPlay 得到 playConnectId | ✅ |
| NetClient_CapturePicture | (m_iConnectID, type, ByteBuffer 文件名) | 未使用（当前仅用 CapturePic） | ✅ 策略选择 |
| NetClient_CapturePic | (m_iConnectID, PointerByReference) 返回 YUV 长度 | 同左，再 YUV→JPG 写文件 | ✅ |
| 抓图后 | 可 StopRealPlay | 在 finally 中 stopRealPlay(playConnectId) | ✅ |
| 文件路径 | 相对路径如 "mySnapShot.jpg" | 绝对路径（CaptureService 生成） | ✅ 符合“绝对路径”要求 |

---

## 4. CaptureService 调用链

| 项目 | 说明 |
|------|------|
| 入口 | 异步抓图：CaptureWorker → CaptureService 带超时 CompletableFuture |
| 登录 | 由 DeviceManager 维护，抓图前已 loginDevice，得到 userId（即 logonID） |
| 通道 | 天地伟业通道号在 CaptureService 中调整为 0-based（channel 0） |
| 入参 | connectId=-1（不使用预览连接），userId/channel 由设备解析，fileName 绝对路径 |
| 超时 | TIANDY_CAPTURE_TIMEOUT_MS=25000，超时后有一次重试 |

---

## 5. 已修复项小结

- **SyncRealPlay 回调**：原 pCbkFullFrm / pCbkRawFrm 传 null，与官方不一致；已改为与 Channel.java 一致，传入非空空实现回调（realPlayCbkFullFrame、realPlayCbkRawFrame），避免 SDK 因“无回调”不建立流或权限异常。

---

## 6. 建议后续排查（若仍超时）

1. **设备/网络**：同一网段用官方客户端或示例做一次同步预览，确认 10s 内能出流。
2. **m_iTimeout**：可临时改回 20 秒，观察是否仍 -307 或阻塞。
3. **RET_SYNCREALPLAY_TIMEOUT**：头文件若为 -307，需与 NvssdkLibrary 常量一致，便于日志解读。
