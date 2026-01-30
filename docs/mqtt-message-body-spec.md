# MQTT 消息体设计规范

本文档定义 SenHub 网关 MQTT 各类消息的 payload 结构与字段约定，供实现 MQTT 独立模块、主题改造及平台对接时统一遵守。不修改业务代码，仅作设计规范。

**文档版本**：1.1  
**主题前缀约定**：`senhub/*`。设备/雷达上下线统一使用 **senhub/device/status**（不按设备分主题）；网关上下线独立使用 **senhub/gateway/status**；其余 senhub/command、senhub/response、senhub/report/{deviceId}、senhub/assembly/{assemblyId}/status 等见正文。

---

## 1. 概述

### 1.1 用途与适用范围

- 在实现 MQTT 独立进程/模块与主题改造前，先固化「消息体内容与格式」。
- 适用于：上下线、报警推送、工作流触发/输出、装置/雷达/摄像头状态、控制命令五类消息。
- 报警事件采用「网关内统一事件库 + 品牌映射」，上报平台时仅暴露统一事件标识（event_id/event_key）。

### 1.2 与现有实现的对应关系


| 规范章节        | 对应实现                                                      |
| --------------- | ------------------------------------------------------------- |
| 上下线/设备状态 | `DeviceManager.publishDeviceStatus`、Main 中设备发现/自动连接 |
| 报警推送        | `AlarmService`、`MqttPublishHandler`、`EventResolver`         |
| 报警事件库      | `CanonicalEventTable`、`brand_event_mapping`                  |
| 控制命令        | `CommandHandler`、`CommandResponse`                           |
| 工作流输出      | `MqttPublishHandler` 组装 payload、`FlowContext`              |

---

## 2. 公共约定

- **时间戳**：Unix 秒（整数），如 `1738147200`；若需毫秒则字段名可带后缀如 `timestamp_ms`，并在具体消息中注明。
- **设备 ID**：国标 GB28181 20 位编码（计划改造后为主键）；当前实现中部分仍为 IP，迁移后统一为国标 ID。
- **编码**：UTF-8。
- **可选扩展**：消息体可含 `version`（如 `"1.0"`）、`schema`（如 `"senhub/device_status/v1"`）便于后续兼容。

---

## 3. 报警事件库（网关内统一）

### 3.1 目的

对海康、天地伟业、大华的事件类型做融合与重新编码，对外（MQTT/平台）只暴露统一事件标识（event_id/event_key），便于过滤、统计与联动。

### 3.2 事件唯一标识与编号（1000～2000）

- **event_key**：英文常量，全局唯一，与表 `canonical_events.event_key` 一致（如 `PERIMETER_INTRUSION`、`MOTION_DETECTION`）。
- **event_id**：**纯数字编号**，范围 **1000～2000**，网关内唯一；同一事件在「纯报警信号」「附带抓图的报警」「附带回放视频的报警」三阶段中**必须使用同一 event_id**，以便上级平台按 event_id 将报警信号、报警图片、报警视频对齐。
  - **1000～1099**：基础报警（basic）
  - **1100～1999**：智能分析/人脸/交通等（vca、face、its）
- **事件编号表**：完整 event_id 与 event_key、name_zh、category、severity 对照见 **docs/mqtt-alarm-event-ids.csv**（可用 Excel 打开或另存为 xlsx）。

### 3.3 事件属性（与 canonical_events 表一致）


| 字段        | 类型   | 说明                              |
| ----------- | ------ | --------------------------------- |
| event_key   | string | 标准事件键                        |
| name_zh     | string | 中文名称                          |
| name_en     | string | 英文名称                          |
| category    | string | basic / vca / face / its          |
| description | string | 描述                              |
| severity    | string | info / warning / error / critical |

### 3.4 品牌映射

每品牌通过 `source_kind` + `source_code` 映射到同一 `event_key`：

- **海康**：`source_kind` 为 `command`（lCommand，如 0x4000/0x4007）或 `alarm_type`（dwAlarmType）、`vca_event`（wEventTypeEx 等）。
- **天地伟业**：`source_kind` 为 `alarm_type`（iAlarmType）、`vca_event`（iEventType）。
- **大华**：`source_kind` 为事件类型常量（如 `vca_event`）+ 对应 source_code（预留，当前代码中可后续从 Dahua 回调提取）。

### 3.5 事件库与 event_key 列表（摘录自 CanonicalEventTable）


| event_key            | name_zh      | category | severity |
| -------------------- | ------------ | -------- | -------- |
| MOTION_DETECTION     | 移动侦测     | basic    | warning  |
| VIDEO_LOST           | 视频丢失     | basic    | error    |
| VIDEO_TAMPER         | 视频遮挡     | basic    | warning  |
| ALARM_INPUT          | 开关量输入   | basic    | warning  |
| ALARM_OUTPUT         | 开关量输出   | basic    | info     |
| AUDIO_LOST           | 音频丢失     | basic    | warning  |
| DEVICE_EXCEPTION     | 设备异常     | basic    | error    |
| RECORDING_ALARM      | 录像报警     | basic    | warning  |
| UNIQUE_ALERT         | 特色警戒报警 | basic    | warning  |
| LINE_CROSSING        | 单绊线越界   | vca      | warning  |
| DOUBLE_LINE_CROSSING | 双绊线越界   | vca      | warning  |
| PERIMETER_INTRUSION  | 周界入侵     | vca      | warning  |
| LOITERING            | 徘徊检测     | vca      | warning  |
| PARKING_DETECTION    | 停车检测     | vca      | warning  |
| RUNNING_DETECTION    | 快速奔跑     | vca      | warning  |
| REGION_ENTRANCE      | 进入区域     | vca      | warning  |
| REGION_EXITING       | 离开区域     | vca      | info     |
| FALL_DETECTION       | 倒地检测     | vca      | critical |
| PLAYING_PHONE        | 玩手机       | vca      | warning  |
| BEHAVIOR_ANALYSIS    | 行为分析     | vca      | warning  |
| FACE_RECOGNITION     | 人脸识别     | face     | info     |
| …                   | …           | …       | …       |

### 3.6 按事件类型的可检测信息（extra）

部分 event_key 可携带扩展信息，供报警消息体 `extra` 使用：


| event_key               | 可能 extra 字段            |
| ----------------------- | -------------------------- |
| LINE_CROSSING 等越界    | rule_id, target_id, region |
| PERIMETER_INTRUSION     | rule_id, target_id, zone   |
| FACE_RECOGNITION        | face_id, score             |
| REGION_ENTRANCE/EXITING | region_id, target_id       |

---

## 4. 上下线消息体

### 4.1 网关上下线（独立主题）

- **主题**：`senhub/gateway/status`（仅网关使用，与设备/装置/雷达分离）
- **说明**：网关进程上线、离线或故障时发布；可配合 LWT 发布离线。**网关标识使用 MAC 地址**，不可使用随机 client_id。


| 字段       | 类型   | 必填 | 说明 |
| ---------- | ------ | ---- | ---- |
| type       | string | 是   | online / offline / fault |
| gateway_id | string | 是   | 网关唯一标识，**MAC 地址**（如 52:54:00:11:22:33） |
| timestamp  | long   | 是   | Unix 秒 |
| version    | string | 否   | 协议版本 |
| reason     | string | 否   | offline/fault 时原因 |

示例（上线）：

```json
{
  "type": "online",
  "gateway_id": "52:54:00:11:22:33",
  "timestamp": 1738147200,
  "version": "1.0"
}
```

示例（离线）：

```json
{
  "type": "offline",
  "gateway_id": "52:54:00:11:22:33",
  "timestamp": 1738147260,
  "reason": "connection_lost"
}
```

### 4.2 设备（摄像头、雷达）上下线（统一主题）

- **主题**：**senhub/device/status**（所有设备与雷达的上下线均发布到该主题，不做按设备 ID 的单独订阅）
- **说明**：通过消息体内的 `entity_type` 与 `device_id` 区分不同实体；device_id 为国标 20 位；摄像头需附带**摄像头类型**。


| 字段         | 类型   | 必填 | 说明 |
| ------------ | ------ | ---- | ---- |
| entity_type  | string | 是   | camera / radar（设备类型） |
| device_id    | string | 是   | 国标 ID |
| type         | string | 是   | online / offline |
| timestamp    | long   | 是   | Unix 秒 |
| device_info  | object | 是   | 见下表（摄像头）或 radar_info（雷达） |
| reason       | string | 否   | 离线原因 |

**摄像头 device_info**：


| 字段           | 类型   | 说明 |
| -------------- | ------ | ---- |
| name           | string | 设备名称 |
| ip             | string | IP |
| port           | int    | 端口 |
| rtsp_url       | string | RTSP 地址 |
| brand          | string | hikvision / tiandy / dahua 等 |
| serial_number  | string | 可选，序列号 |
| **camera_type** | string | **必填**。球机=ptz，枪机=bullet，半球=dome，其他=other |

**雷达** 使用 `radar_info` 对象（见 4.4），字段含 radar_ip、radar_name、assembly_id、radar_serial、status 等。

示例（摄像头上线）：

```json
{
  "entity_type": "camera",
  "device_id": "34020000001320000001",
  "type": "online",
  "timestamp": 1738147200,
  "device_info": {
    "name": "前门球机",
    "ip": "192.168.1.100",
    "port": 8000,
    "rtsp_url": "rtsp://192.168.1.100:554/Streaming/Channels/101",
    "brand": "hikvision",
    "camera_type": "ptz"
  }
}
```

示例（雷达上线）：

```json
{
  "entity_type": "radar",
  "device_id": "34020000001320000002",
  "type": "online",
  "timestamp": 1738147200,
  "radar_info": {
    "radar_ip": "192.168.1.101",
    "radar_name": "入口雷达",
    "assembly_id": "34020000009990000001",
    "radar_serial": "LIVOX-xxx",
    "status": 1
  }
}
```

### 4.3 装置上下线

- **主题**：`senhub/assembly/{assemblyId}/status`
- **说明**：装置「在线」可定义为至少一台下属设备在线。**assembly_id 采用国标 GB28181 20 位编码**；附属信息中**必须附带装置下所有关联设备的 device_id 列表**，并增加**经纬度**字段。


| 字段           | 类型   | 必填 | 说明 |
| -------------- | ------ | ---- | ---- |
| assembly_id    | string | 是   | 装置国标 ID（20 位） |
| type           | string | 是   | online / offline |
| timestamp      | long   | 是   | Unix 秒 |
| assembly_info  | object | 是   | 见下表 |

**assembly_info**：


| 字段          | 类型     | 说明 |
| ------------- | -------- | ---- |
| name          | string   | 装置名称 |
| location      | string   | 位置描述（如「东区」） |
| longitude     | number   | 经度（WGS84） |
| latitude      | number   | 纬度（WGS84） |
| device_count  | int      | 关联设备数量 |
| **device_ids** | string[] | **必填**。装置下所有关联设备的国标 device_id 列表（含摄像头、雷达等） |

示例：

```json
{
  "assembly_id": "34020000009990000001",
  "type": "online",
  "timestamp": 1738147200,
  "assembly_info": {
    "name": "一号装置",
    "location": "东区",
    "longitude": 116.397128,
    "latitude": 39.916527,
    "device_count": 3,
    "device_ids": ["34020000001320000001", "34020000001320000002", "34020000001320000003"]
  }
}
```

### 4.4 雷达上下线（与 4.2 统一主题）

- **主题**：与摄像头一致，使用 **senhub/device/status**；消息体内 `entity_type` 为 `radar`，并携带 `radar_info` 对象。
- **radar_info** 字段：radar_ip、radar_name、assembly_id（国标）、radar_serial、status（0 离线 / 1 在线 / 2 采集中）。示例见 4.2。

---

## 5. 报警推送消息体

- **主题**：`senhub/report/{deviceId}` 或工作流节点可配置的 topic。
- **说明**：报警上报分为三阶段，**同一事件的 event_id 必须一致**，便于上级平台将「报警信号」「报警图片」「报警视频」对齐：
  1. **纯报警信号**：仅事件与设备信息，无抓图/录像 URL。
  2. **附带抓图的报警**：在 1 基础上增加 capture_url（或 image_base64）。
  3. **附带回放视频的报警**：在 1 基础上增加 oss_url（录像地址）或 playback 信息。
- **event_id**：纯数字，**1000～2000** 范围，与 **docs/mqtt-alarm-event-ids.csv** 中编号一致；同一 event_key 在三阶段中必须使用同一 event_id。


| 字段          | 类型   | 必填 | 说明 |
| ------------- | ------ | ---- | ---- |
| **event_id**  | int    | **是** | 网关事件编号（1000～2000），与 CSV 表一致；三阶段同事件同 id |
| event_key     | string | 是   | 标准事件键 |
| event_name_zh | string | 否   | 中文名称 |
| event_name_en | string | 否   | 英文名称 |
| category      | string | 否   | basic/vca/face/its |
| severity      | string | 否   | info/warning/error/critical |
| device_id     | string | 是   | 国标 ID |
| assembly_id   | string | 否   | 装置国标 ID |
| channel       | int    | 否   | 通道号 |
| timestamp     | long   | 是   | Unix 秒 |
| source_brand  | string | 否   | hikvision / tiandy / dahua |
| source_code   | int/string | 否 | 厂商原始事件码 |
| flow_id       | string | 否   | 触发的工作流 ID |
| capture_url   | string | 否   | 阶段 2/3：抓图 URL |
| oss_url       | string | 否   | 阶段 3：OSS 图片/录像 URL |
| extra         | object | 否   | rule_id, target_id, region 等 |

示例（阶段 1，纯报警）：

```json
{
  "event_id": 1102,
  "event_key": "PERIMETER_INTRUSION",
  "event_name_zh": "周界入侵",
  "category": "vca",
  "severity": "warning",
  "device_id": "34020000001320000001",
  "assembly_id": "34020000009990000001",
  "channel": 1,
  "timestamp": 1738147200,
  "source_brand": "hikvision",
  "source_code": 4
}
```

示例（阶段 2/3，同 event_id 1102，附带抓图与录像）：

```json
{
  "event_id": 1102,
  "event_key": "PERIMETER_INTRUSION",
  "event_name_zh": "周界入侵",
  "device_id": "34020000001320000001",
  "assembly_id": "34020000009990000001",
  "channel": 1,
  "timestamp": 1738147200,
  "flow_id": "flow_alarm_001",
  "capture_url": "https://oss.example.com/capture/xxx.jpg",
  "oss_url": "https://oss.example.com/record/xxx.mp4",
  "extra": { "rule_id": 1, "target_id": 100 }
}
```

**与现有字段对应**：context 中 deviceId → device_id，assemblyId → assembly_id，alarmType/eventKey → event_key，captureUrl/ossUrl → capture_url/oss_url。event_id 由网关按 **mqtt-alarm-event-ids.csv** 从 event_key 解析得到。

---

## 6. 工作流相关消息体

### 6.1 MQTT 订阅节点触发入参（第三方发布，触发工作流）

- **说明**：订阅指定主题，收到消息后触发工作流；工作流内可通过 context 使用 payload 中的字段。以下为**完整建议结构**，便于联动球机、声光报警器、传感器等。


| 字段          | 类型   | 必填 | 说明 |
| ------------- | ------ | ---- | ---- |
| topic         | string | 否   | 实际收到的 MQTT 主题（网关可自动注入） |
| request_id    | string | 否   | 请求唯一标识，便于日志与响应关联 |
| timestamp     | long   | 否   | 发送方 Unix 秒 |
| source        | string | 否   | 来源标识（如平台 ID、系统名） |
| command       | string | 是*  | 动作类型，见下表（*至少 command 或 trigger_type 之一） |
| trigger_type  | string | 否   | 与 command 二选一或互补：alarm_linkage / manual / schedule / mqtt_cmd |
| target        | object | 否   | 目标描述，见下表 |
| params        | object | 否   | 命令参数，依 command 不同而不同 |
| context       | object | 否   | 业务上下文（如关联的 event_id、alarm_id、device_id） |
| priority      | int    | 否   | 优先级 1～10，数字越大越优先 |
| expire_at     | long   | 否   | 过期时间戳，超时可不执行 |
| extra         | object | 否   | 扩展字段 |

**target（目标）**：


| 字段         | 类型   | 说明 |
| ------------ | ------ | ---- |
| device_id    | string | 目标设备国标 ID（摄像头/雷达等） |
| assembly_id   | string | 目标装置国标 ID |
| device_ids   | string[] | 多设备时使用 |
| channel      | int    | 通道号（可选） |

**command 与 params 约定**：


| command        | 说明           | params 示例 |
| -------------- | -------------- | ----------- |
| ptz_preset     | 球机预置点     | preset: 1～255 |
| ptz_control    | 云台方向/变倍  | action: up/down/left/right/zoom_in/zoom_out, speed: 1～9 |
| speaker_play   | 声光报警/语音  | text: 播报内容, volume: 0～100, duration_ms: 毫秒 |
| relay_on_off   | 继电器开关     | relay_index: 1～n, on: true/false |
| capture        | 触发抓图       | channel: 可选 |
| record_start_stop | 录像启停    | action: start/stop, channel: 可选 |
| notify         | 仅通知/记录    | message: 文本, level: info/warning/error |
| custom         | 自定义动作     | 由工作流节点解析 |

示例（联动球机预置点）：

```json
{
  "request_id": "req-link-001",
  "timestamp": 1738147200,
  "source": "platform-01",
  "command": "ptz_preset",
  "trigger_type": "alarm_linkage",
  "target": {
    "device_id": "34020000001320000001",
    "channel": 1
  },
  "params": { "preset": 1 },
  "context": { "event_id": 1102, "alarm_id": "alm-uuid-xxx" },
  "priority": 5
}
```

示例（联动声光报警器）：

```json
{
  "request_id": "req-link-002",
  "timestamp": 1738147200,
  "command": "speaker_play",
  "target": { "device_id": "34020000001320000003" },
  "params": {
    "text": "请注意周界入侵",
    "volume": 80,
    "duration_ms": 5000
  },
  "context": { "event_id": 1102, "device_id": "34020000001320000001" },
  "priority": 6
}
```

示例（多设备联动 + 继电器）：

```json
{
  "request_id": "req-link-003",
  "command": "relay_on_off",
  "target": { "device_ids": ["34020000001320000003", "34020000001320000004"] },
  "params": { "relay_index": 1, "on": true },
  "expire_at": 1738147300
}
```

### 6.2 MQTT 发布节点输出（工作流执行后上报）

- **说明**：与报警推送消息体兼容；若无事件触发则为纯工作流结果（无 event_key 等）。
- **字段**：至少包含 deviceId、assemblyId、flowId；可选 event_key、captureUrl、ossUrl、自定义 payload；与 5 节结构一致，差异仅为「是否由报警驱动」（有则带 event_*，无则仅 flow 结果）。

---

## 7. 装置 / 雷达 / 摄像头状态消息体

- **约定**：设备（摄像头、雷达）的**状态**与**上下线**共用同一主题 **senhub/device/status**，通过 `entity_type`（camera/radar）与 `device_id` 区分；装置状态使用 **senhub/assembly/{assemblyId}/status**，与装置上下线共用，payload 中增加经纬度与 device_ids。

### 7.1 摄像头 / 雷达状态（senhub/device/status）

- **主题**：**senhub/device/status**
- **字段**：与 4.2 一致；`entity_type` 为 camera 或 radar，`type` 为 online/offline；摄像头必带 `device_info.camera_type`（ptz/bullet/dome/other）；雷达带 `radar_info`，status 取值 0 离线、1 在线、2 采集中；可选 current_background_id（雷达）、channel_status（摄像头）等扩展字段。

示例（摄像头状态）：

```json
{
  "entity_type": "camera",
  "device_id": "34020000001320000001",
  "type": "online",
  "timestamp": 1738147200,
  "device_info": {
    "name": "前门球机",
    "ip": "192.168.1.100",
    "port": 8000,
    "rtsp_url": "rtsp://192.168.1.100:554/Streaming/Channels/101",
    "brand": "hikvision",
    "camera_type": "ptz"
  }
}
```

示例（雷达状态）：

```json
{
  "entity_type": "radar",
  "device_id": "34020000001320000002",
  "type": "online",
  "timestamp": 1738147200,
  "radar_info": {
    "radar_ip": "192.168.1.101",
    "radar_name": "入口雷达",
    "assembly_id": "34020000009990000001",
    "radar_serial": "LIVOX-xxx",
    "status": 1,
    "current_background_id": "bg_001"
  }
}
```

### 7.2 装置状态（senhub/assembly/{assemblyId}/status）

- **主题**：**senhub/assembly/{assemblyId}/status**
- **说明**：与装置上下线共用主题；状态消息体需包含装置国标 ID、经纬度、附属信息中**装置下所有关联设备的 device_id 列表**。

| 字段           | 类型     | 说明 |
| -------------- | -------- | ---- |
| assembly_id    | string   | 装置国标 ID（20 位） |
| type           | string   | online / offline（聚合结果） |
| status         | int      | 0 离线 1 在线（与 type 对应） |
| timestamp      | long     | Unix 秒 |
| longitude      | number   | 经度（WGS84） |
| latitude       | number   | 纬度（WGS84） |
| device_count   | int      | 关联设备总数 |
| online_count   | int      | 当前在线设备数 |
| device_ids     | string[] | 装置下所有关联设备的国标 device_id 列表 |

示例（装置状态）：

```json
{
  "assembly_id": "34020000009990000001",
  "type": "online",
  "status": 1,
  "timestamp": 1738147200,
  "longitude": 116.397128,
  "latitude": 39.916527,
  "device_count": 3,
  "online_count": 2,
  "device_ids": ["34020000001320000001", "34020000001320000002", "34020000001320000003"]
}
```

---

## 8. 控制命令消息体

### 8.1 请求（senhub/command）

**公共字段**：


| 字段       | 类型   | 必填 | 说明     |
| ---------- | ------ | ---- | -------- |
| command    | string | 是   | 见下表   |
| device_id  | string | 是   | 国标 ID  |
| request_id | string | 否   | 请求追踪 |
| timestamp  | long   | 否   | Unix 秒  |

**按命令类型**（与 CommandHandler 一致）：


| command     | 额外参数                                                                                    |
| ----------- | ------------------------------------------------------------------------------------------- |
| capture     | channel（可选，默认 1）                                                                     |
| reboot      | 无                                                                                          |
| playback    | start_time, end_time（可选）, channel（可选）                                               |
| play_audio  | text, volume（可选，TBD）                                                                   |
| ptz_control | action 或 command（up/down/left/right/zoom_in/zoom_out 等）, speed（可选）, channel（可选） |

**capture 请求示例**：

```json
{
  "command": "capture",
  "device_id": "34020000001320000001",
  "request_id": "req-uuid-1",
  "channel": 1
}
```

**ptz_control 请求示例**：

```json
{
  "command": "ptz_control",
  "device_id": "34020000001320000001",
  "request_id": "req-uuid-2",
  "action": "up",
  "speed": 5,
  "channel": 1
}
```

**playback 请求示例**：

```json
{
  "command": "playback",
  "device_id": "34020000001320000001",
  "request_id": "req-uuid-3",
  "start_time": "2025-01-29 12:00:00",
  "end_time": "2025-01-29 12:01:00",
  "channel": 1
}
```

### 8.2 响应（senhub/response）

**公共字段**（与 CommandResponse 一致）：


| 字段       | 类型    | 说明           |
| ---------- | ------- | -------------- |
| request_id | string  | 与请求一致     |
| device_id  | string  | 设备国标 ID    |
| command    | string  | 命令类型       |
| success    | boolean | 是否成功       |
| data       | object  | 成功时结果     |
| error      | string  | 失败时错误信息 |

**data 按命令类型**：

- **capture**：image_base64, image_size, channel, timestamp（如 yyyyMMddHHmmss）
- **reboot**：message
- **playback**：source（local/device）, files（列表）或 file_path/video_base64, channel, start_time, end_time
- **ptz_control**：action, command, speed, channel
- **play_audio**：message（TBD）

**capture 成功响应示例**：

```json
{
  "requestId": "req-uuid-1",
  "deviceId": "34020000001320000001",
  "command": "capture",
  "success": true,
  "data": {
    "image_base64": "base64...",
    "image_size": 12345,
    "channel": 1,
    "timestamp": "20250129120000"
  },
  "error": ""
}
```

**失败响应示例**：

```json
{
  "requestId": "req-uuid-1",
  "deviceId": "34020000001320000001",
  "command": "capture",
  "success": false,
  "data": {},
  "error": "设备不存在"
}
```

---

## 9. 附录

### 9.1 报警事件编号表（event_id 1000～2000）

- **完整表**：见 **docs/mqtt-alarm-event-ids.csv**（可用 Excel 打开或另存为 xlsx）。列：event_id、event_key、name_zh、name_en、category、severity、description。
- **编号范围**：
  - **1000～1099**：基础报警（basic），如 MOTION_DETECTION(1000)、VIDEO_LOST(1001)、ALARM_INPUT(1003) 等。
  - **1100～1999**：智能分析/人脸/交通（vca、face、its），如 PERIMETER_INTRUSION(1102)、FALL_DETECTION(1120) 等。
- 同一事件在「纯报警」「带抓图报警」「带回放报警」三阶段中**必须使用同一 event_id**，便于上级平台对齐。

### 9.2 报警事件 event_key 与品牌 source_code 对照（摘录）

**海康（hikvision）**：


| event_key           | source_kind | source_code 示例       |
| ------------------- | ----------- | ---------------------- |
| MOTION_DETECTION    | command     | 0x1100, 0x4000, 0x4007 |
| ALARM_INPUT         | alarm_type  | 0                      |
| VIDEO_LOST          | alarm_type  | 2                      |
| VIDEO_TAMPER        | alarm_type  | 6                      |
| PERIMETER_INTRUSION | vca_event   | 4                      |
| REGION_ENTRANCE     | vca_event   | 2                      |
| FALL_DETECTION      | vca_event   | 20                     |

**天地伟业（tiandy）**：


| event_key           | source_kind | source_code |
| ------------------- | ----------- | ----------- |
| MOTION_DETECTION    | alarm_type  | 0           |
| VIDEO_LOST          | alarm_type  | 2           |
| ALARM_INPUT         | alarm_type  | 3           |
| LINE_CROSSING       | vca_event   | 0           |
| PERIMETER_INTRUSION | vca_event   | 2           |

**大华（dahua）**：按实际回调事件类型在 brand_event_mapping 中配置，此处预留。

### 9.3 命令错误码约定（error 字符串）


| 含义         | 建议 error 取值                      |
| ------------ | ------------------------------------ |
| 设备不存在   | 设备不存在 / device_not_found        |
| 设备登录失败 | 设备登录失败 / login_failed          |
| 抓图失败     | 抓图失败 / capture_failed            |
| 云台控制失败 | 云台控制失败 / ptz_control_failed    |
| 未知命令     | 未知命令: xxx / unknown_command      |
| 参数缺失     | 设备ID不能为空 / 动作参数不能为空 等 |

---

*本文档与现有实现（Config、DeviceManager、CommandHandler、CanonicalEventTable、MqttPublishHandler、AlarmService、EventResolver）对应；实现时以本规范为准，并可在代码注释中引用本文档路径。*
