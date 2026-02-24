# 天地伟业抓图方式说明

## 1. 当前使用的方式：仅 NetClient_CapturePic（不走图片流通道）

- **唯一路径**：**预览流 + NetClient_CapturePic**，不使用图片流通道（不使用 `NetClient_CapturePicByDevice`、也不使用 `NetClient_CapturePicture`）。
- **流程**：
  1. `NetClient_SyncRealPlay` 建立视频预览流（子码流），得到 `connectId`。
  2. `NetClient_CapturePic(connectId, pucData)` 从预览流抓一帧，**YUV(4:2:2) 写入内存**。
  3. 本地将 YUV 转为 JPG 并写入请求的文件路径。
  4. `NetClient_StopRealPlay(connectId)` 断开预览。
- **设备侧表现**：仅建立**视频流连接**，不建图片流通道，无 GET_SNAP_PARA_TIME_OUT 等限制。

## 2. SDK 抓图接口对照

| 接口 | 说明 | 本项目 |
|------|------|--------|
| **NetClient_CapturePic** | 按预览连接抓图，YUV 写入内存 | ✅ **当前唯一使用** |
| **NetClient_CapturePicture** | 按预览连接抓图并直接保存到文件 | ❌ 未使用 |
| **NetClient_CapturePicByDevice** | 按设备抓图，走图片流通道 | ❌ 未使用（不走图片流通道） |
| **NetClient_Snapshot** | 仅下发抓图命令，图片通过回调等返回 | ❌ 未使用 |

## 3. 预览启动流程（与官方示例一致）

- **参考**：`JavaClientDemo/Channel.java` 的 `SyncRealPlay()`、`SyncJavaDemo/SyncBusiness.java` 的同步预览。
- **步骤**：登录状态检查 → `NetClient_GetDigitalChannelNum` / `NetClient_GetChannelNum` → 组 `tagNetClientPara`（含 CLIENTINFO）→ `tVideoPara.write()` → **NetClient_SyncRealPlay(piConnectID, tVideoPara, iSize)**。
- **与官方一致点**：参数与官方一致（m_iServerID、m_iChannelNo、m_iStreamNO、m_iNetMode、m_iTimeout、回调为 null、iIsForbidDecode=ALLOW_DECODE）；我们使用 `getPointer()` 传参，与官方传 Structure 在 JNA 下等价。
- **若未出现「预览启动成功/失败」**：说明阻塞在 `startRealPlay` 的某一步。日志会按顺序出现：
  1. `天地伟业预览启动: 开始`
  2. `天地伟业预览启动: 登录状态检查完成`
  3. `天地伟业预览启动: 通道数获取完成`
  4. `天地伟业预览启动: 即将调用 NetClient_SyncRealPlay（可能阻塞至超时）`
  5. 随后才会出现「预览启动成功」或「预览启动超时/失败」
- **定位**：最后一条出现的上述日志即阻塞发生处（例如若只出现 1～3，则阻塞在获取通道数之后、SyncRealPlay 之前；若出现 4 后长时间无输出，则阻塞在 **NetClient_SyncRealPlay** 内部，多为设备/网络或 10s 超时未返回）。

## 4. 故障排查（服务器日志）

- **抓图超时**：若 `SyncRealPlay` 超时（-307），抓图会失败；预览参数 `m_iTimeout` 已设为 10 秒。
- **-307**：同步连接视频超时，对端未发视频头，需检查设备与服务器网络、设备负载。
- **YUV 尺寸无法推断分辨率**：若返回的 YUV 字节数不在常用分辨率（如 1920×1080、1280×720、704×576 等）对应 size 内，会报该错并失败。
