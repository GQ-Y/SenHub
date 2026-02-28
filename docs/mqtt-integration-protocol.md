# SenHub 视频网关 MQTT 协议对接文档

**版本**：v1.0  
**更新日期**：2026-02-27  
**适用系统**：SenHub 视频网关 v1.1.16+  
**协议**：MQTT 3.1.1 / 5.0  
**消息格式**：JSON (UTF-8)

---

## 目录

- [1. 概述](#1-概述)
- [2. 连接配置](#2-连接配置)
- [3. 主题规范总览](#3-主题规范总览)
- [4. 功能一：报警事件主动上报](#4-功能一报警事件主动上报)
  - [4.1 主题与 QoS](#41-主题与-qos)
  - [4.2 报警上报消息体结构](#42-报警上报消息体结构)
  - [4.3 三阶段上报机制](#43-三阶段上报机制)
  - [4.4 报警事件类型全量列表](#44-报警事件类型全量列表)
  - [4.5 品牌事件映射关系](#45-品牌事件映射关系)
  - [4.6 完整示例](#46-完整示例)
- [5. 功能二：MQTT 命令控制](#5-功能二mqtt-命令控制)
  - [5.1 通信模型](#51-通信模型)
  - [5.2 请求公共结构](#52-请求公共结构)
  - [5.3 响应公共结构](#53-响应公共结构)
  - [5.4 错误码规范](#54-错误码规范)
  - [5.5 命令：capture（抓图）](#55-命令capture抓图)
  - [5.6 命令：ptz_control（云台控制）](#56-命令ptz_control云台控制)
  - [5.7 命令：playback（录像回放查询）](#57-命令playback录像回放查询)
  - [5.8 命令：reboot（设备重启）](#58-命令reboot设备重启)
  - [5.9 命令：play_audio（语音播报）](#59-命令play_audio语音播报)
- [6. 设备与网关状态上报](#6-设备与网关状态上报)
  - [6.1 网关上下线](#61-网关上下线)
  - [6.2 设备上下线](#62-设备上下线)
- [7. 对接流程指南](#7-对接流程指南)
- [附录 A：报警事件编号完整表](#附录-a报警事件编号完整表)
- [附录 B：品牌事件映射详表](#附录-b品牌事件映射详表)

---

## 1. 概述

SenHub 视频网关通过 MQTT 协议对外提供两大核心功能：

| 功能 | 方向 | 说明 |
|------|------|------|
| **报警事件上报** | 网关 → 平台 | 网关接收到摄像头 SDK 报警后，经事件标准化处理，主动推送至 MQTT Broker |
| **命令控制** | 平台 → 网关 | 平台通过 MQTT 下发命令，实现对摄像头的抓拍、录像回放查询、云台控制等操作 |

### 1.1 支持的设备品牌

| 品牌 | 标识 | 抓图 | 云台控制 | 回放 | 报警事件 |
|------|------|------|----------|------|----------|
| 海康威视 | `hikvision` | ✅ | ✅ | ✅ | ✅ |
| 天地伟业 | `tiandy` | ✅ | ✅ | ✅ | ✅ |
| 大华 | `dahua` | ✅ | ✅ | ✅ | ✅ |

### 1.2 术语说明

| 术语 | 说明 |
|------|------|
| `device_id` | 设备唯一标识，采用国标 GB28181 20 位编码 |
| `assembly_id` | 装置唯一标识，采用国标 GB28181 20 位编码 |
| `event_key` | 标准事件键，全局唯一英文标识（如 `PERIMETER_INTRUSION`） |
| `event_id` | 事件编号，1000～2000 范围的整数，与 event_key 一一对应 |
| `channel` | 摄像头通道号，默认为 1 |
| `request_id` | 请求唯一标识，由调用方生成，用于请求-响应匹配 |

---

## 2. 连接配置

### 2.1 Broker 连接参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| Broker 地址 | `tcp://mqtt.yingzhu.net:1883` | 可通过系统配置修改 |
| Client ID | `senhub-app` | 网关端客户端标识 |
| 用户名 | `demos1` | MQTT 认证用户名 |
| 密码 | `demos1` | MQTT 认证密码 |
| QoS | `1` | 默认消息质量等级 |
| Keep Alive | `60` 秒 | 心跳间隔 |
| Clean Session | `true` | 每次连接清除历史会话 |
| 连接超时 | `30` 秒 | TCP 连接超时 |

> **注意**：以上为网关出厂默认值，实际部署时可通过 Web 管理界面 `/api/mqtt/config` 或 `config.yaml` 配置文件修改。对接方需使用独立的 Client ID 连接同一 Broker。

### 2.2 LWT（遗嘱消息）

网关连接时配置 LWT，当网关异常断开时 Broker 自动发布离线通知：

- **主题**：`senhub/gateway/status`
- **QoS**：与全局 QoS 一致
- **Retained**：`false`
- **Payload**：`{"type":"offline","gateway_id":"<MAC地址>","timestamp":<Unix秒>,"reason":"connection_lost"}`

---

## 3. 主题规范总览

所有主题均使用 `senhub/` 作为命名空间前缀。

| 主题 | 方向 | QoS | 说明 |
|------|------|-----|------|
| `senhub/report/{deviceId}` | 网关 → 平台 | 1 | **报警事件上报**（核心功能一） |
| `senhub/command` | 平台 → 网关 | 1 | **命令下发**（核心功能二） |
| `senhub/response` | 网关 → 平台 | 1 | **命令响应** |
| `senhub/device/status` | 网关 → 平台 | 1 | 设备（摄像头/雷达）上下线 |
| `senhub/gateway/status` | 网关 → 平台 | 1 | 网关上下线（含 LWT） |
| `senhub/assembly/{assemblyId}/status` | 网关 → 平台 | 1 | 装置上下线 |

> 其中 `{deviceId}` 为设备的国标 ID，`{assemblyId}` 为装置的国标 ID。

---

## 4. 功能一：报警事件主动上报

### 4.1 主题与 QoS

```
主题：senhub/report/{deviceId}
方向：网关 → 平台（PUBLISH）
QoS ：1（至少一次投递）
Retained：false
```

对接方需订阅 `senhub/report/#` 或 `senhub/report/{指定设备ID}` 接收报警事件。

**主题示例**：
- `senhub/report/34020000001320000001` — 接收指定设备的报警
- `senhub/report/#` — 接收所有设备的报警

### 4.2 报警上报消息体结构

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `event_id` | int | **是** | 事件编号（1000～2000），同一事件在三阶段中必须一致 |
| `event_key` | string | **是** | 标准事件键，全局唯一（如 `PERIMETER_INTRUSION`） |
| `event_name_zh` | string | 是 | 事件中文名称（如「周界入侵」） |
| `event_name_en` | string | 否 | 事件英文名称（如 `Perimeter Intrusion`） |
| `device_id` | string | **是** | 触发报警的设备国标 ID |
| `deviceId` | string | 是 | 同 `device_id`（兼容字段） |
| `assembly_id` | string | 否 | 所属装置国标 ID |
| `assemblyId` | string | 否 | 同 `assembly_id`（兼容字段） |
| `alarmType` | string | 否 | 原始报警类型标识（如 `Hikvision_Alarm_1102`） |
| `alarmMessage` | string | 否 | 报警描述文本 |
| `channel` | int | 否 | 触发通道号 |
| `flowId` | string | 否 | 触发的工作流 ID |
| `captureUrl` | string | 否 | 抓图 URL（阶段二/三） |
| `ossUrl` | string | 否 | OSS 图片/录像 URL（阶段二/三） |
| `alarmData` | object | 否 | 原始报警附加数据 |

### 4.3 三阶段上报机制

报警事件按处理进度分三个阶段上报，**同一事件的 `event_id` 在三阶段中保持一致**，便于上级平台将报警信号、报警图片、报警视频对齐关联。

```
                                    ┌──────────────────┐
                                    │   SDK 报警回调     │
                                    └────────┬─────────┘
                                             │
                                    ┌────────▼─────────┐
                                    │  事件标准化解析    │
                                    │  (EventResolver)  │
                                    └────────┬─────────┘
                                             │
                              ┌──────────────▼──────────────┐
                              │     工作流引擎执行            │
                              │     (FlowExecutor)           │
                              └──┬───────────┬───────────┬──┘
                                 │           │           │
                          ┌──────▼──┐  ┌─────▼─────┐  ┌─▼────────┐
                          │ 阶段一   │  │  阶段二    │  │  阶段三   │
                          │ 纯报警   │  │ +抓图URL   │  │ +录像URL  │
                          │ 信号     │  │           │  │          │
                          └─────────┘  └───────────┘  └──────────┘
```

#### 阶段一：纯报警信号

仅包含事件信息与设备标识，**无抓图/录像**。

```json
{
  "event_id": 1102,
  "event_key": "PERIMETER_INTRUSION",
  "event_name_zh": "周界入侵",
  "event_name_en": "Perimeter Intrusion",
  "device_id": "34020000001320000001",
  "deviceId": "34020000001320000001",
  "assembly_id": "34020000009990000001",
  "assemblyId": "34020000009990000001",
  "alarmType": "Hikvision_Alarm_1102",
  "channel": 1,
  "flowId": "default_alarm_flow"
}
```

#### 阶段二：附带抓图 URL

在阶段一基础上，工作流完成抓图后增加 `captureUrl`。

```json
{
  "event_id": 1102,
  "event_key": "PERIMETER_INTRUSION",
  "event_name_zh": "周界入侵",
  "device_id": "34020000001320000001",
  "deviceId": "34020000001320000001",
  "assembly_id": "34020000009990000001",
  "assemblyId": "34020000009990000001",
  "alarmType": "Hikvision_Alarm_1102",
  "channel": 1,
  "flowId": "default_alarm_flow",
  "captureUrl": "https://oss.example.com/captures/alarm_34020000001320000001_20260227_205230.jpg"
}
```

#### 阶段三：附带抓图 + 录像 URL

工作流完成录像提取并上传 OSS 后，增加 `ossUrl`。

```json
{
  "event_id": 1102,
  "event_key": "PERIMETER_INTRUSION",
  "event_name_zh": "周界入侵",
  "device_id": "34020000001320000001",
  "deviceId": "34020000001320000001",
  "assembly_id": "34020000009990000001",
  "assemblyId": "34020000009990000001",
  "alarmType": "Hikvision_Alarm_1102",
  "channel": 1,
  "flowId": "default_alarm_flow",
  "captureUrl": "https://oss.example.com/captures/alarm_xxx.jpg",
  "ossUrl": "https://oss.example.com/recordings/alarm_xxx.mp4"
}
```

### 4.4 报警事件类型全量列表

事件按类别分为以下分组，编号范围 1000～2000：

#### 基础报警事件（1000～1099）

| event_id | event_key | 中文名称 | 严重级别 |
|----------|-----------|----------|----------|
| 1000 | `MOTION_DETECTION` | 移动侦测 | warning |
| 1001 | `VIDEO_LOST` | 视频丢失 | error |
| 1002 | `VIDEO_TAMPER` | 视频遮挡 | warning |
| 1003 | `ALARM_INPUT` | 开关量输入 | warning |
| 1004 | `ALARM_OUTPUT` | 开关量输出 | info |
| 1005 | `AUDIO_LOST` | 音频丢失 | warning |
| 1006 | `DEVICE_EXCEPTION` | 设备异常 | error |
| 1007 | `RECORDING_ALARM` | 录像报警 | warning |
| 1008 | `UNIQUE_ALERT` | 特色警戒报警 | warning |

#### 智能分析事件（1100～1199，常用摘录）

| event_id | event_key | 中文名称 | 严重级别 |
|----------|-----------|----------|----------|
| 1100 | `LINE_CROSSING` | 单绊线越界 | warning |
| 1101 | `DOUBLE_LINE_CROSSING` | 双绊线越界 | warning |
| 1102 | `PERIMETER_INTRUSION` | 周界入侵 | warning |
| 1103 | `LOITERING` | 徘徊检测 | warning |
| 1104 | `PARKING_DETECTION` | 停车检测 | warning |
| 1105 | `RUNNING_DETECTION` | 快速奔跑 | warning |
| 1106 | `CROWD_DENSITY` | 区域人员密度 | info |
| 1107 | `OBJECT_LEFT` | 物品遗弃 | warning |
| 1108 | `OBJECT_REMOVAL` | 物品遗失 | warning |
| 1109 | `FACE_RECOGNITION` | 人脸识别 | info |
| 1113 | `CROWD_GATHERING` | 人群聚集 | warning |
| 1114 | `ABSENCE_DETECTION` | 离岗检测 | warning |
| 1117 | `BEHAVIOR_ANALYSIS` | 行为分析 | warning |
| 1118 | `REGION_ENTRANCE` | 进入区域 | warning |
| 1119 | `REGION_EXITING` | 离开区域 | info |
| 1120 | `FALL_DETECTION` | 倒地检测 | critical |
| 1121 | `PLAYING_PHONE` | 玩手机 | warning |

> 完整事件列表含 180+ 种事件类型，详见 [附录 A](#附录-a报警事件编号完整表) 或 `docs/mqtt-alarm-event-ids.csv`。

#### 严重级别说明

| severity | 含义 | 说明 |
|----------|------|------|
| `info` | 信息 | 常规通知，不需要处理 |
| `warning` | 警告 | 需要关注，可能需要处理 |
| `error` | 错误 | 设备/系统异常，需要处理 |
| `critical` | 严重 | 紧急事件（如倒地、打架），需要立即处理 |

### 4.5 品牌事件映射关系

网关内部将各品牌 SDK 原始报警码统一映射为标准 `event_key`，对接方**无需关心品牌差异**，只需按 `event_key` / `event_id` 处理。

#### 海康威视映射

| event_key | 映射来源 | SDK 原始码 | 说明 |
|-----------|----------|-----------|------|
| `MOTION_DETECTION` | command | 0x1100 / 0x4000 / 0x4007 | COMM_ALARM / V30 / V40 |
| `ALARM_INPUT` | alarm_type | 0 | IO 信号量报警 |
| `VIDEO_LOST` | alarm_type | 2 | 视频信号丢失 |
| `MOTION_DETECTION` | alarm_type | 3 | 移动侦测 |
| `VIDEO_TAMPER` | alarm_type | 6 | 遮挡报警 |
| `DEVICE_EXCEPTION` | alarm_type | 1/4/5/7/8 | 硬盘满/未格式化/读写出错/制式不匹配/非法访问 |
| `LINE_CROSSING` | vca_event | 1 | COMM_ALARM_RULE - 穿越警戒面 |
| `REGION_ENTRANCE` | vca_event | 2 | 目标进入区域 |
| `REGION_EXITING` | vca_event | 3 | 目标离开区域 |
| `PERIMETER_INTRUSION` | vca_event | 4 | 周界入侵 |
| `LOITERING` | vca_event | 5 | 徘徊 |
| `RUNNING_DETECTION` | vca_event | 8 | 快速移动/奔跑 |
| `ABSENCE_DETECTION` | vca_event | 15 | 离岗检测 |
| `FALL_DETECTION` | vca_event | 20 | 倒地检测 |
| `PLAYING_PHONE` | vca_event | 44 | 玩手机 |
| `BEHAVIOR_ANALYSIS` | command | 0x4993 | COMM_VCA_ALARM 智能检测通用 |

#### 天地伟业映射

| event_key | 映射来源 | SDK 原始码 | 说明 |
|-----------|----------|-----------|------|
| `MOTION_DETECTION` | alarm_type | 0 | 移动侦测 |
| `RECORDING_ALARM` | alarm_type | 1 | 录像报警 |
| `VIDEO_LOST` | alarm_type | 2 | 视频丢失 |
| `ALARM_INPUT` | alarm_type | 3 | 开关量输入 |
| `ALARM_OUTPUT` | alarm_type | 4 | 开关量输出 |
| `VIDEO_TAMPER` | alarm_type | 5 | 视频遮挡 |
| `AUDIO_LOST` | alarm_type | 7 | 音频丢失 |
| `DEVICE_EXCEPTION` | alarm_type | 8 | 设备异常 |
| `LINE_CROSSING` | vca_event | 0 | 单绊线越界 |
| `DOUBLE_LINE_CROSSING` | vca_event | 1 | 双绊线越界 |
| `PERIMETER_INTRUSION` | vca_event | 2 | 周界入侵 |
| `LOITERING` | vca_event | 3 | 徘徊检测 |
| `PARKING_DETECTION` | vca_event | 4 | 停车检测 |
| `RUNNING_DETECTION` | vca_event | 5 | 快速奔跑 |
| `CROWD_DENSITY` | vca_event | 6 | 区域人员密度 |
| `OBJECT_LEFT` | vca_event | 7 | 物品遗弃 |
| `OBJECT_REMOVAL` | vca_event | 8 | 物品遗失 |
| `FACE_RECOGNITION` | vca_event | 9 | 人脸识别 |
| `CROWD_GATHERING` | vca_event | 13 | 人群聚集 |
| `ABSENCE_DETECTION` | vca_event | 14 | 离岗检测 |

### 4.6 完整示例

#### 场景：海康摄像头检测到周界入侵

**订阅主题**（对接方）：

```
senhub/report/34020000001320000001
```

**收到消息**（阶段三，含抓图和录像）：

```json
{
  "event_id": 1102,
  "event_key": "PERIMETER_INTRUSION",
  "event_name_zh": "周界入侵",
  "event_name_en": "Perimeter Intrusion",
  "device_id": "34020000001320000001",
  "deviceId": "34020000001320000001",
  "assembly_id": "34020000009990000001",
  "assemblyId": "34020000009990000001",
  "alarmType": "Hikvision_Alarm_1102",
  "channel": 1,
  "flowId": "default_alarm_flow",
  "captureUrl": "https://oss.example.com/captures/alarm_34020000001320000001_20260227_205230.jpg",
  "ossUrl": "https://oss.example.com/recordings/alarm_34020000001320000001_20260227_205215.mp4"
}
```

#### 对接方处理逻辑建议

```python
# Python 伪代码示例
import paho.mqtt.client as mqtt
import json

def on_message(client, userdata, msg):
    payload = json.loads(msg.payload.decode('utf-8'))
    
    event_id   = payload.get('event_id')        # 1102
    event_key  = payload.get('event_key')        # "PERIMETER_INTRUSION"
    device_id  = payload.get('device_id')        # 国标ID
    capture    = payload.get('captureUrl')        # 抓图URL（可能为空）
    video      = payload.get('ossUrl')           # 录像URL（可能为空）
    
    # 按 event_id 做业务分发
    if event_id >= 1100:
        handle_vca_alarm(payload)   # 智能分析报警
    elif event_id >= 1000:
        handle_basic_alarm(payload) # 基础报警

client = mqtt.Client("platform-subscriber")
client.connect("mqtt.yingzhu.net", 1883)
client.subscribe("senhub/report/#", qos=1)
client.on_message = on_message
client.loop_forever()
```

---

## 5. 功能二：MQTT 命令控制

### 5.1 通信模型

```
                  senhub/command                    senhub/response
    ┌──────────┐ ──────────────────► ┌──────────┐ ──────────────────► ┌──────────┐
    │          │   PUBLISH (请求)     │          │   PUBLISH (响应)     │          │
    │  平台端   │                     │ SenHub   │                     │  平台端   │
    │          │ ◄────────────────── │  网关    │                     │          │
    └──────────┘   SUBSCRIBE         └──────────┘                     └──────────┘
```

- **请求主题**：`senhub/command` — 平台端发布命令
- **响应主题**：`senhub/response` — 网关发布响应结果
- **关联方式**：通过 `request_id` 字段将请求与响应一一对应

### 5.2 请求公共结构

所有命令请求共享以下公共字段：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `command` | string | **是** | 命令类型：`capture` / `ptz_control` / `playback` / `reboot` / `play_audio` |
| `device_id` | string | **是** | 目标设备的国标 ID |
| `request_id` | string | 推荐 | 请求唯一标识（UUID），用于匹配响应 |
| `channel` | int | 否 | 通道号，默认使用设备配置的通道号（通常为 1） |

### 5.3 响应公共结构

所有命令响应共享以下公共字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `requestId` | string | 与请求中的 `request_id` 一致（为空则表示请求未携带） |
| `deviceId` | string | 设备国标 ID |
| `command` | string | 命令类型 |
| `success` | boolean | 执行结果：`true` 成功 / `false` 失败 |
| `data` | object | 成功时的业务数据（按命令类型不同） |
| `error` | string | 失败时的错误信息，成功时为空字符串 |

> **注意**：响应字段使用 camelCase（`requestId`、`deviceId`），与 Java `CommandResponse` 类序列化保持一致。

### 5.4 错误码规范

| 错误信息 | 触发条件 |
|----------|----------|
| `设备ID不能为空` | 请求缺少 `device_id` 字段 |
| `设备不存在` | `device_id` 在网关中未注册 |
| `设备登录失败` | 网关无法通过 SDK 登录设备（网络不通/密码错误等） |
| `未知命令: xxx` | `command` 字段值不在支持列表中 |
| `抓图失败` | 抓图过程中 SDK 调用失败 |
| `抓图文件未生成` | SDK 调用成功但文件未落盘 |
| `动作参数不能为空` | 云台控制缺少 `action` 参数 |
| `云台控制失败` | PTZ 控制 SDK 调用返回失败 |
| `命令处理异常: xxx` | 服务端未预期的异常 |

---

### 5.5 命令：capture（抓图）

向指定摄像头发送抓图指令，返回 Base64 编码的图片数据。

#### 请求

**主题**：`senhub/command`

```json
{
  "command": "capture",
  "device_id": "34020000001320000001",
  "request_id": "req-capture-001",
  "channel": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `command` | string | 是 | 固定值 `"capture"` |
| `device_id` | string | 是 | 设备国标 ID |
| `request_id` | string | 推荐 | 请求标识 |
| `channel` | int | 否 | 通道号，默认使用设备配置通道 |

#### 成功响应

**主题**：`senhub/response`

```json
{
  "requestId": "req-capture-001",
  "deviceId": "34020000001320000001",
  "command": "capture",
  "success": true,
  "data": {
    "image_base64": "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAA...",
    "image_size": 245760,
    "channel": 1,
    "timestamp": "20260227205230"
  },
  "error": ""
}
```

| data 字段 | 类型 | 说明 |
|-----------|------|------|
| `image_base64` | string | 图片 JPEG 数据的 Base64 编码 |
| `image_size` | int | 原始图片字节数 |
| `channel` | int | 抓图通道号 |
| `timestamp` | string | 抓图时间，格式 `yyyyMMddHHmmss` |

#### 失败响应

```json
{
  "requestId": "req-capture-001",
  "deviceId": "34020000001320000001",
  "command": "capture",
  "success": false,
  "data": {},
  "error": "抓图失败"
}
```

---

### 5.6 命令：ptz_control（云台控制）

控制球机/云台进行方向转动和变焦操作。采用**启停模式**：先发 `start` 开始动作，再发 `stop` 停止。

#### 请求

**主题**：`senhub/command`

```json
{
  "command": "ptz_control",
  "device_id": "34020000001320000001",
  "request_id": "req-ptz-001",
  "action": "start",
  "speed": 5,
  "channel": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `command` | string | 是 | 固定值 `"ptz_control"` |
| `device_id` | string | 是 | 设备国标 ID |
| `request_id` | string | 推荐 | 请求标识 |
| `action` | string | **是** | 动作方向，见下表 |
| `speed` | int | 否 | 转动速度，默认 5 |
| `channel` | int | 否 | 通道号，默认使用设备配置通道 |

#### action 可选值

| action | 说明 | 备注 |
|--------|------|------|
| `up` | 向上转动 | |
| `down` | 向下转动 | |
| `left` | 向左转动 | |
| `right` | 向右转动 | |
| `zoom_in` | 变焦放大 | |
| `zoom_out` | 变焦缩小 | |
| `stop` | 停止动作 | **必须在方向动作后发送 stop 命令** |

#### speed 速度范围（按品牌）

| 品牌 | 速度范围 | 默认值 |
|------|----------|--------|
| 海康威视 | 1 ～ 7 | 5 |
| 大华 | 1 ～ 8 | 5 |
| 天地伟业 | 0 ～ 100 | 5 |

#### 成功响应

```json
{
  "requestId": "req-ptz-001",
  "deviceId": "34020000001320000001",
  "command": "ptz_control",
  "success": true,
  "data": {
    "action": "up",
    "command": "up",
    "speed": 5,
    "channel": 1
  },
  "error": ""
}
```

#### 操作流程示例

完整的云台「向上转动」操作需要两条命令：

**第一步：开始向上**

```json
{
  "command": "ptz_control",
  "device_id": "34020000001320000001",
  "request_id": "req-ptz-start",
  "action": "up",
  "speed": 5
}
```

**第二步：停止**（延迟若干毫秒后发送）

```json
{
  "command": "ptz_control",
  "device_id": "34020000001320000001",
  "request_id": "req-ptz-stop",
  "action": "stop"
}
```

> **重复抑制**：网关内置 120ms 的重复命令抑制机制，短时间内重复发送相同方向命令会被自动过滤。

---

### 5.7 命令：playback（录像回放）

以**接收到命令的时间**为基准，自动提取**前 15 秒 + 后 15 秒**共 30 秒的录像视频，合并后上传至 OSS，返回 OSS 地址。无需传入时间参数。

> **重要**：该命令为异步处理，网关需等待后 15 秒录像完成、分段写入、提取合并、上传 OSS 后才返回响应，整个过程约需 30-60 秒。请务必设置足够的超时时间。

#### 处理流程

```
  接收命令     等待后段录像     提取/合并录像      上传OSS       返回响应
  ─────┬───────────┬──────────────┬──────────────┬────────────┬──►
       T        T+15s+2s        提取30s视频      上传完成     MQTT响应
       │◄── 前15s ──┤── 后15s ──►│                            含 oss_url
```

#### 请求

**主题**：`senhub/command`

```json
{
  "command": "playback",
  "device_id": "34020000001320000001",
  "request_id": "req-playback-001"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `command` | string | 是 | 固定值 `"playback"` |
| `device_id` | string | 是 | 设备国标 ID |
| `request_id` | string | 推荐 | 请求标识 |
| `channel` | int | 否 | 通道号，默认使用设备配置通道 |

> 时间范围由网关自动计算（接收时间 ±15 秒），**不需要**也**不支持**传入 `start_time` / `end_time`。

#### 成功响应

**主题**：`senhub/response`

```json
{
  "requestId": "req-playback-001",
  "deviceId": "34020000001320000001",
  "command": "playback",
  "success": true,
  "data": {
    "file_path": "/opt/senhub/storage/recordings/alarm_34020000001320000001_20260227_205215.mp4",
    "file_size": 2097152,
    "channel": 1,
    "start_time": "2026-02-27 20:52:15",
    "end_time": "2026-02-27 20:52:45",
    "duration": 30,
    "oss_url": "https://oss.example.com/recordings/34020000001320000001/alarm_xxx.mp4"
  },
  "error": ""
}
```

| data 字段 | 类型 | 说明 |
|-----------|------|------|
| `file_path` | string | 网关本地录像文件路径 |
| `file_size` | long | 视频文件字节数 |
| `channel` | int | 通道号 |
| `start_time` | string | 录像起始时间（接收时间 - 15秒） |
| `end_time` | string | 录像结束时间（接收时间 + 15秒） |
| `duration` | int | 录像时长（秒），固定为 30 |
| `oss_url` | string | **OSS 下载地址**（已配置 OSS 时返回，核心字段） |

> **关键字段**：对接方应使用 `oss_url` 获取录像视频，不再通过 Base64 传输视频数据。

---

### 5.8 命令：reboot（设备重启）

远程重启设备。**支持海康威视、天地伟业、大华三品牌**，通过 DeviceSDK 统一接口自动适配。

#### 请求

```json
{
  "command": "reboot",
  "device_id": "34020000001320000001",
  "request_id": "req-reboot-001"
}
```

#### 成功响应

```json
{
  "requestId": "req-reboot-001",
  "deviceId": "34020000001320000001",
  "command": "reboot",
  "success": true,
  "data": {
    "message": "设备重启命令已发送",
    "device_id": "34020000001320000001",
    "brand": "hikvision"
  },
  "error": ""
}
```

| data 字段 | 类型 | 说明 |
|-----------|------|------|
| `message` | string | 操作结果描述 |
| `device_id` | string | 设备 ID |
| `brand` | string | 设备品牌（`hikvision` / `tiandy` / `dahua`） |

> **注意**：重启命令发送成功不代表设备已重启完成，设备重启需要 30-120 秒，期间设备不可用。

---

### 5.9 命令：play_audio（本地音频播放）

在网关服务器上通过系统音频播放器（mpv / ffplay / aplay）播放指定的本地音频文件。适用于接入了音箱/喇叭的场景。

> **说明**：当前已实现本地系统音频播放（通过服务器音频输出设备直接播放）。设备端广播（通过摄像头/音柱扬声器远程播放）尚未对接。

#### 请求

```json
{
  "command": "play_audio",
  "device_id": "34020000001320000001",
  "request_id": "req-audio-001",
  "audio_path": "/opt/senhub/audio/alert.mp3"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `command` | string | 是 | 固定值 `"play_audio"` |
| `device_id` | string | 是 | 设备 ID（用于日志关联） |
| `request_id` | string | 推荐 | 请求标识 |
| `audio_path` | string | **是** | 网关服务器上的音频文件绝对路径 |

#### 成功响应

```json
{
  "requestId": "req-audio-001",
  "deviceId": "34020000001320000001",
  "command": "play_audio",
  "success": true,
  "data": {
    "message": "音频播放已启动",
    "audio_path": "/opt/senhub/audio/alert.mp3",
    "player": "mpv"
  },
  "error": ""
}
```

| data 字段 | 类型 | 说明 |
|-----------|------|------|
| `message` | string | 操作结果 |
| `audio_path` | string | 播放的音频文件路径 |
| `player` | string | 使用的播放器（`mpv` / `ffplay` / `aplay`） |

> 播放为异步操作，响应返回时播放可能仍在进行中。

---

## 6. 设备与网关状态上报

### 6.1 网关上下线

**主题**：`senhub/gateway/status`

```json
{
  "type": "online",
  "gateway_id": "52:54:00:11:22:33",
  "timestamp": 1740643920,
  "version": "1.0"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | `online` / `offline` |
| `gateway_id` | string | 网关 MAC 地址 |
| `timestamp` | long | Unix 秒时间戳 |
| `version` | string | 协议版本 |
| `reason` | string | 离线原因（`offline` 时，如 `connection_lost`） |

### 6.2 设备上下线

**主题**：`senhub/device/status`（所有设备共用一个主题）

通过 `entity_type` 区分设备类型：

#### 摄像头上线

```json
{
  "entity_type": "camera",
  "device_id": "34020000001320000001",
  "type": "online",
  "timestamp": 1740643920,
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

| device_info 字段 | 类型 | 说明 |
|------------------|------|------|
| `name` | string | 设备名称 |
| `ip` | string | 设备 IP |
| `port` | int | SDK 通信端口 |
| `rtsp_url` | string | RTSP 视频流地址 |
| `brand` | string | 品牌标识：`hikvision` / `tiandy` / `dahua` |
| `camera_type` | string | 摄像头类型：`ptz`(球机) / `bullet`(枪机) / `dome`(半球) / `other` |

---

## 7. 对接流程指南

### 7.1 快速对接步骤

```
 第一步                第二步                  第三步
 ┌──────────┐         ┌──────────────┐         ┌──────────────┐
 │ 连接 MQTT │ ─────► │ 订阅报警主题  │ ─────► │ 发布控制命令  │
 │ Broker    │         │ 订阅响应主题  │         │ 处理命令响应  │
 └──────────┘         └──────────────┘         └──────────────┘
```

**第一步：连接 MQTT Broker**

使用任意 MQTT 客户端库连接 Broker，使用**独立的 Client ID**（不要与网关的 `senhub-app` 冲突）。

**第二步：订阅主题**

```
# 接收所有设备的报警事件
SUBSCRIBE senhub/report/#

# 接收命令响应
SUBSCRIBE senhub/response

# （可选）接收设备状态变化
SUBSCRIBE senhub/device/status
SUBSCRIBE senhub/gateway/status
```

**第三步：发送命令并处理响应**

```
# 发送抓图命令
PUBLISH senhub/command
{"command":"capture","device_id":"34020000001320000001","request_id":"req-001"}

# 在 senhub/response 中接收到响应后，按 request_id 匹配
```

### 7.2 注意事项

1. **request_id**：强烈建议每次命令都携带唯一的 `request_id`（UUID），以便准确匹配响应
2. **设备登录**：网关会在收到命令时自动检查设备登录状态，未登录的设备会自动尝试登录
3. **超时处理**：`capture` / `ptz_control` 建议 10-30 秒超时；**`playback` 命令需 60-90 秒超时**（需等待后 15 秒录像 + 提取合并 + OSS 上传）
4. **防抖机制**：报警事件默认 5 秒防抖间隔，相同设备的相同事件在 5 秒内不会重复上报
5. **PTZ 启停配对**：云台控制必须成对使用 start/stop，否则摄像头会持续转动
6. **playback 响应**：录像回放返回 `oss_url` 下载地址，必须先启用OSS
7. **多品牌支持**：`reboot` 命令已适配海康/天地伟业/大华三品牌，通过 DeviceSDK 统一接口自动识别

---

## 附录 A：报警事件编号完整表

完整的事件编号表（180+ 种事件类型）保存在 `docs/mqtt-alarm-event-ids.csv` 文件中，CSV 格式，列定义如下：

```
event_id,event_key,name_zh,name_en,category,severity,description
```

主要分类范围：

| 编号范围 | 分类 | 说明 |
|----------|------|------|
| 1000 ～ 1099 | basic | 基础报警（移动侦测、视频丢失、设备异常等） |
| 1100 ～ 1199 | vca | 智能视频分析（越界、入侵、徘徊、倒地等） |
| 1200 ～ 1299 | vca/its/face | 扩展智能分析（车辆识别、人脸抓拍等） |

---

## 附录 B：品牌事件映射详表

网关通过 `brand_event_mapping` 数据库表维护品牌原始报警码到标准事件的映射关系，支持运行时动态配置（通过 Web 管理界面或 API）。

映射查询逻辑：`brand` + `source_kind` + `source_code` → `event_key`

| 字段 | 说明 |
|------|------|
| `brand` | 品牌标识（`hikvision` / `tiandy` / `dahua`） |
| `source_kind` | 来源类型（`command` / `alarm_type` / `vca_event`） |
| `source_code` | SDK 原始事件码（整数） |
| `event_key` | 映射目标标准事件键 |
| `priority` | 优先级，同一 source_code 有多个映射时取优先级最高的 |

> 对接方无需关心映射细节，直接使用消息体中的 `event_key` 和 `event_id` 即可。映射关系仅在需要调试或自定义事件时参考。

---

*本文档基于 SenHub 视频网关 v1.1.16 版本编写，如有疑问，如有问题，请联系网关开发团队。*
