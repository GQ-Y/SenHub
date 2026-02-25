# ZLM4J 集成分析：SenHub Server 与 j_zlm_sdk

## 一、当前 Server 项目概览

SenHub 是**多品牌视频监控网关**（Java 11 + Maven），主要能力：

| 能力 | 实现方式 |
|------|----------|
| 设备接入 | 海康 / 大华 / 天地伟业 原生 SDK（JNA），多品牌设备管理 |
| 实时预览/录制 | 通过 SDK `startRealPlay` + `startRecording` 拉流并落盘 |
| 流地址 API | `GET /api/devices/:id/stream` 返回**最新录制视频**的 MP4 URL，非实时 RTSP/FLV |
| 抓图 | `CaptureService` 调用各品牌 SDK 抓图 |
| 回放/下载 | `RecordingTaskService` + 各 SDK 按时间范围下载录像 |
| 协议与扩展 | HTTP(Spark)、WebSocket、MQTT、工作流、OSS、AI 等 |

设备表中有 `rtsp_url` 字段，但当前**未用 ZLMediaKit 做 RTSP 代理或转码**，实时浏览器播放依赖的是「最新录制文件」或前端直连 RTSP（若前端支持）。

---

## 二、j_zlm_sdk（zlm4j）简介

- **仓库**: [lidaofu-hub/j_zlm_sdk](https://github.com/lidaofu-hub/j_zlm_sdk)
- **定位**: 开源流媒体框架 **ZLMediaKit** 的 C API 的 **Java 封装**（JNA），与 SenHub 现有 JNA 使用方式一致。
- **运行方式**: 内嵌 ZLMediaKit 动态库（mk_api），在**同一 JVM 进程**内提供流媒体服务，无需单独部署 ZLM 进程。

### 2.1 zlm4j 主要 API 能力（与 ZLMediaKit 对齐）

| 能力 | 说明 |
|------|------|
| **流媒体服务** | HTTP/RTSP/RTMP 端口可配置，兼容 ZLM 配置方式 |
| **推流** | RTSP / RTMP / RTC / SRT / **GB28181** / WebRTC，支持推流鉴权 |
| **拉流/输出** | RTSP / RTMP / **FLV** / **HLS** / **FMP4** 等，便于浏览器播放 |
| **代理** | 拉流接入、鉴权、**按需拉流**、**无人观看自动关流**、流量统计 |
| **录制** | MP4 / FLV / M3U8，支持 MP4 分片 |
| **事件回调** | 流上下线、推拉流、录制完成、无人观看、RTSP 鉴权等 |

---

## 三、集成后能实现的功能

在**不替换**现有 SDK 录制/抓图/回放的前提下，增加 zlm4j 可带来以下能力。

### 3.1 实时直播（RTSP → HTTP-FLV / HLS / FMP4）

- **现状**: `/api/devices/:id/stream` 返回的是「最新录制视频」URL，不是实时流；浏览器直接播 RTSP 需要插件或转码。
- **集成 zlm4j 后**:
  - 用设备已有 `rtsp_url`，通过 ZLM **拉流代理**把摄像机 RTSP 拉进 ZLM，再以 FLV/HLS/FMP4 输出。
  - 前端可获例如：`http://{host}:{zlm_http_port}/live/{app}/{streamId}.flv` 或 HLS 地址，用 flv.js / hls.js 播放。
- **收益**: 多品牌摄像机统一为 HTTP 流，无需为每种品牌单独处理 RTSP；可减少对 SDK 实时预览的依赖，降低 SDK 连接数。

### 3.2 按需拉流与无人观看关流

- 使用 ZLM 的**按需拉流**：仅当有播放请求时才从摄像机拉 RTSP。
- 结合**无人观看自动关流**回调，无观众时自动断掉拉流，节省带宽与摄像机连接数。

### 3.3 协议转换与多协议输出

- 同一路 RTSP 可同时输出：FLV、HLS、FMP4、WebRTC 等，方便不同前端（Web、App、小程序）选用合适协议。

### 3.4 基于 ZLM 的录制（可选）

- 对「仅 RTSP、无 SDK」或「不想用 SDK 录制」的通道，可由 ZLM 拉 RTSP 并录制为 MP4/FLV/M3U8。
- 可与现有 `RecorderService`（SDK 录制）并存：部分设备走 SDK 录制，部分走 ZLM 录制。

### 3.5 国标 GB28181 推流

- 若需将设备或某路流推到上级国标平台，可使用 zlm4j 的 **GB28181 推流**能力，由 ZLM 统一对接国标平台。

### 3.6 流式截图

- ZLM 支持对拉到的流做截图，可作为 `CaptureService` 的补充（例如对仅 RTSP 的设备用 ZLM 截图，而不走 SDK）。

### 3.7 流事件与运维

- 通过 zlm4j 的**流上下线、推拉流、录制完成、无人观看**等回调，可做：
  - 设备/流状态监控、统计、告警；
  - 与现有 MQTT/工作流联动（如某路流上线时发 MQTT、触发工作流）。

---

## 四、集成方式建议

### 4.1 依赖

在 `server/pom.xml` 中增加（版本请以 Maven 中央仓库或项目文档为准）：

```xml
<dependency>
    <groupId>com.aizuda</groupId>
    <artifactId>zlm4j</artifactId>
    <version>1.8.0</version>  <!-- 或最新版本，见仓库 Release -->
</dependency>
```

zlm4j 自带 JNA，若与现有 JNA 版本冲突，可统一到 zlm4j 使用的版本或做 exclusion。项目已使用 JNA 5.13.0，需确认 zlm4j 是否兼容。

### 4.2 原生库

- zlm4j 会依赖 ZLMediaKit 的 **mk_api** 动态库（如 `mk_api.dll` / `libmk_api.so` / `libmk_api.dylib`），支持 win64/linux64/linux_arm64/mac64/mac_arm64。
- 与现有 `lib/arm/hikvision`、`lib/x86/tiandy` 等类似，需在部署包中带上对应平台的 ZLM 库，或通过 zlm4j 文档指定库路径（若支持）。
- 若 zlm4j 从 classpath 或固定目录加载库，需保证该目录在运行时可访问。

### 4.3 配置

在 `config.yaml`（或现有配置体系）中增加可选段，例如：

```yaml
# 可选：ZLMediaKit 内嵌服务（用于 RTSP 代理、FLV/HLS 输出、按需拉流等）
zlm:
  enabled: true
  http_port: 7788   # 与现有 Spark 端口 8084 错开
  rtsp_port: 5540   # 与标准 554 错开，避免冲突
  rtmp_port: 19350  # 可选
  # 媒体服务器 ID，用于多实例区分
  media_server_id: "SenHubZLM"
```

### 4.4 服务封装与启动

1. **ZlmMediaService（新建）**
   - 在 `Main.start()` 中根据 `zlm.enabled` 决定是否启动。
   - 调用 zlm4j 的：
     - `mk_env_init2` 初始化；
     - `mk_ini_default` / `mk_ini_set_option` 设置参数（如 `general.mediaServerId`）；
     - 注册全局回调 `MK_EVENTS`（如 `on_mk_media_changed`）；
     - `mk_http_server_start`、`mk_rtsp_server_start` 等启动 HTTP/RTSP。
   - 关闭时调用 `mk_stop_all_server()`，并在 `Main.shutdown()` 中调用。

2. **与现有端口的协调**
   - Spark 已占 8084；ZLM HTTP 建议用 7788 或其它未占用端口，避免冲突。

### 4.5 拉流代理与 API

1. **设备 RTSP → ZLM**
   - 根据 `DeviceInfo.getRtspUrl()` 调用 ZLM 的**拉流代理** API（具体以 zlm4j 文档为准，一般为「指定 RTSP 地址 + 本地 app/streamId」）。
   - 约定规则：例如 `app=live`, `streamId=deviceId` 或 `deviceId_channel`，便于与现有设备模型一致。

2. **统一直播 URL API**
   - 新增例如：`GET /api/devices/:id/live/url`，返回：
     - `flv_url`: `http://{host}:{zlm_http_port}/live/{app}/{streamId}.flv`
     - `hls_url`: 对应 HLS 地址（若有）
   - 可在响应中带 token 或鉴权参数（若 ZLM 或网关做了鉴权）。

3. **按需拉流**
   - 首次请求某设备直播 URL 时再调用 ZLM 拉流代理；结合 ZLM 的「无人观看关流」回调，在无观众时释放拉流。

### 4.6 可选：录制与 GB28181

- **ZLM 录制**：对选定设备/通道，在 ZLM 拉流后开启录制任务（MP4/FLV），与 `RecordingTaskService` 的「按时间下载」并存，互不替代。
- **GB28181**：需要时再起单独模块，从 ZLM 推流到国标平台，与现有设备/流模型对接。

---

## 五、实现时注意点

1. **线程与 JNA**  
   zlm4j 通过 JNA 调 C，回调可能在 Native 线程触发，需注意线程安全与在回调中做重操作（建议转交线程池或队列）。

2. **端口与防火墙**  
   ZLM 的 HTTP/RTSP/RTMP 端口需在部署环境中开放，且与现有服务端口不冲突。

3. **资源与生命周期**  
   - 按需拉流时，要管理「谁在观看」、何时关流，避免重复拉流或泄漏。
   - 进程退出时务必 `mk_stop_all_server()` 并释放 zlm4j 相关资源。

4. **兼容与降级**  
   - 若 zlm4j 初始化失败（如库未找到、端口占用），应仅禁用「ZLM 直播/代理」能力，不影响现有 SDK 录制、抓图、回放、报警等。

5. **版本**  
   - 以 [j_zlm_sdk Releases](https://github.com/lidaofu-hub/j_zlm_sdk/releases) 和 Maven 仓库为准选择 zlm4j 版本；飞书文档中有更详细 API 说明。

---

## 六、功能对照小结

| 能力 | 当前 SenHub | 集成 zlm4j 后 |
|------|-------------|----------------|
| 实时浏览器播放 | 依赖「最新录制文件」或前端直连 RTSP | 提供 HTTP-FLV/HLS/FMP4，多品牌统一 |
| 按需拉流 | 无 | 支持，节省带宽与连接数 |
| 无人观看关流 | 无 | 支持 |
| 协议转换 | 无 | RTSP→FLV/HLS/FMP4/WebRTC 等 |
| 录制 | SDK 录制 | 可增加 ZLM 录制（与 SDK 并存） |
| 国标 GB28181 | 未实现 | 可推流到上级平台 |
| 流截图 | 仅 SDK | 可增加 ZLM 流截图 |
| 流事件/运维 | 无 | 上下线、录制完成、无人观看等回调 |

---

## 七、推荐实施顺序

1. 引入 zlm4j 依赖与原生库，在 `Main` 中增加可开关的 **ZlmMediaService** 初始化与关闭。
2. 实现「设备 RTSP → ZLM 拉流代理」及 **GET /api/devices/:id/live/url** 返回 FLV/HLS 地址。
3. 前端对接新直播 URL（flv.js/hls.js），验证多品牌设备统一播放。
4. 按需实现：按需拉流、无人观看关流、ZLM 录制、GB28181、流截图等。

以上为基于当前 server 架构与 j_zlm_sdk 能力的集成分析与可实现功能说明，可直接作为方案评审与开发任务拆解依据。
