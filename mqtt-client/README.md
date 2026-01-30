# MQTT测试客户端

用于测试海康威视NVR控制服务的MQTT命令功能。

## 功能特性

- 连接MQTT服务器并订阅响应主题
- 发送各种命令到server服务
- 接收并验证响应结果
- 支持多种测试场景（基本测试、错误处理等）
- 生成详细的测试报告

## 安装

1. 确保已安装Python 3.6+

2. 创建并激活虚拟环境（推荐）：
```bash
# 创建虚拟环境
python3 -m venv venv

# 激活虚拟环境
# macOS/Linux:
source venv/bin/activate
# 或使用提供的脚本:
source activate.sh

# Windows:
# venv\Scripts\activate
```

3. 安装依赖：
```bash
pip install -r requirements.txt
```

**注意**: 每次使用前需要先激活虚拟环境。如果使用 `activate.sh` 脚本，直接运行 `source activate.sh` 即可。

## 配置

编辑 `config.json` 文件，配置MQTT连接信息：

```json
{
  "mqtt": {
    "broker": "tcp://mqtt.yingzhu.net:1883",
    "username": "demos1",
    "password": "demos1",
    "command_topic": "hikvision/command",
    "response_topic": "hikvision/response",
    "status_topic": "hikvision/status",
    "gateway_status_topic": "senhub/gateway/status",
    "report_topic_prefix": "senhub/report",
    "qos": 1
  },
  "test": {
    "default_device_id": "192.168.1.100:8000",
    "response_timeout": 30
  }
}
```

其中 `gateway_status_topic`、`report_topic_prefix` 为可选，供 **mqtt_subscriber.py** 与网关 config.yaml 的 senhub 主题对齐使用。

## 使用方法

### 基本用法

```bash
# 基本测试（抓图、云台控制、回放）
python mqtt_test_client.py --device-id 192.168.1.100:8000 --test basic

# 执行所有测试（不包括reboot）
python mqtt_test_client.py --device-id 192.168.1.100:8000 --test all

# 只测试抓图
python mqtt_test_client.py --device-id 192.168.1.100:8000 --test capture

# 只测试云台控制
python mqtt_test_client.py --device-id 192.168.1.100:8000 --test ptz

# 只测试回放
python mqtt_test_client.py --device-id 192.168.1.100:8000 --test playback

# 测试错误处理
python mqtt_test_client.py --device-id 192.168.1.100:8000 --test error

# 测试重启（需要确认）
python mqtt_test_client.py --device-id 192.168.1.100:8000 --test reboot
```

### 命令行参数

- `--config`: 配置文件路径（默认: config.json）
- `--device-id`: 测试设备ID（必需，例如: 192.168.1.100:8000）
- `--test`: 测试类型
  - `basic`: 基本测试（默认）
  - `all`: 所有测试（不包括reboot）
  - `capture`: 只测试抓图
  - `ptz`: 只测试云台控制
  - `playback`: 只测试回放
  - `reboot`: 测试重启（会重启设备）
  - `error`: 错误处理测试
- `--broker`: MQTT服务器地址（覆盖配置文件）
- `--username`: MQTT用户名（覆盖配置文件）
- `--password`: MQTT密码（覆盖配置文件）

### 使用自定义配置

```bash
# 使用命令行参数覆盖配置
python mqtt_test_client.py \
  --device-id 192.168.1.100:8000 \
  --broker tcp://mqtt.example.com:1883 \
  --username myuser \
  --password mypass \
  --test basic
```

## 支持的测试命令

### 1. 抓图命令 (capture)

测试设备抓图功能，验证：
- 命令发送成功
- 响应包含图片数据（base64编码）
- 响应时间在合理范围内

### 2. 云台控制 (ptz_control)

测试云台控制功能，支持的动作：
- `up`: 向上
- `down`: 向下
- `left`: 向左
- `right`: 向右
- `zoom_in`: 放大
- `zoom_out`: 缩小

### 3. 回放命令 (playback)

测试录像回放功能：
- 自动计算当前时间前后30秒的时间范围
- 支持从本地录制文件或设备下载
- 验证响应包含视频数据或下载句柄

### 4. 重启命令 (reboot)

测试设备重启功能（谨慎使用）：
- 会实际重启设备
- 需要用户确认

### 5. 错误处理测试

测试各种错误场景：
- 无效设备ID
- 无效命令类型
- 缺少必需参数

## 测试报告

测试完成后会自动生成报告，包含：
- 总测试数
- 通过/失败统计
- 平均响应时间
- 每个测试的详细结果

示例输出：
```
============================================================
测试报告
============================================================
总测试数: 3
通过: 3
失败: 0
平均响应时间: 1.23秒

详细结果:
------------------------------------------------------------
1. capture - ✓ 通过
   设备ID: 192.168.1.100:8000
   响应时间: 1.15秒

2. ptz_control - ✓ 通过
   设备ID: 192.168.1.100:8000
   响应时间: 0.98秒

3. playback - ✓ 通过
   设备ID: 192.168.1.100:8000
   响应时间: 1.56秒
```

## 注意事项

1. **设备ID获取**: 测试前需要确认server数据库中存在的设备ID。可以通过以下方式获取：
   - HTTP API: `GET http://server:port/api/devices`
   - 直接查询数据库: `SELECT device_id FROM devices`

2. **时间限制**: playback命令只能查询当前时间前后1分钟的视频

3. **测试顺序**: 建议先测试非破坏性命令（capture, ptz_control），最后测试reboot

4. **网络连接**: 确保MQTT broker可访问，server服务正在运行

5. **响应超时**: 默认响应超时时间为30秒，可在代码中调整

## 故障排查

### 连接失败

- 检查MQTT broker地址和端口是否正确
- 检查用户名和密码是否正确
- 检查网络连接是否正常

### 响应超时

- 检查server服务是否正在运行
- 检查设备是否在线
- 检查MQTT主题配置是否正确

### 命令执行失败

- 检查设备ID是否正确
- 检查设备是否已添加到server数据库
- 检查设备是否在线且可访问
- 查看server日志获取详细错误信息

## MQTT 监听测试

除命令测试外，可使用 **mqtt_subscriber.py** 订阅网关发布的状态与报警主题，验证“网关是否按规范发出正确消息”。

### 用途与主题

- **mqtt_test_client.py**：发命令（`senhub/command`）+ 收响应（`senhub/response`），验证命令链路。
- **mqtt_subscriber.py**：仅订阅、不发命令，验证网关主动推送的以下主题：
  - `senhub/gateway/status` — 网关上下线 / LWT
  - `senhub/device/status` — 设备/雷达上下线
  - `senhub/assembly/+/status` — 装置状态
  - `senhub/report/+` — 工作流报警上报
  - `senhub/response` — 命令响应（可选，与 test client 重叠）

### 运行方式

```bash
# 默认订阅 senhub/#，持续打印，Ctrl+C 退出
python mqtt_subscriber.py

# 开启 payload 结构化校验，失败时打印 WARNING
python mqtt_subscriber.py --validate

# 仅监听指定主题
python mqtt_subscriber.py --topics senhub/device/status senhub/gateway/status

# 运行 60 秒后退出（便于 CI/脚本）
python mqtt_subscriber.py --duration 60

# 将监听日志写入文件，便于后续分析（控制台与文件同时输出）
python mqtt_subscriber.py --log-file logs/mqtt_subscriber.log --validate

# 使用指定配置文件
python mqtt_subscriber.py --config config.json --validate
```

**一键启动（虚拟环境 + 日志保存）**：在 `mqtt-client` 目录下执行：

```bash
chmod +x run_subscriber_with_log.sh
./run_subscriber_with_log.sh
```

脚本会创建/激活虚拟环境、安装依赖、以 `--validate` 启动订阅，并将输出同时写入 `logs/mqtt_subscriber_YYYYMMDD_HHMMSS.log`，按 Ctrl+C 停止。

### 校验项说明

开启 `--validate` 时，会根据主题对 payload 做简单校验：

- **senhub/gateway/status**：`type`、`gateway_id`、`timestamp`；`type` 为 online/offline。
- **senhub/device/status**：`entity_type`、`device_id`、`type`、`timestamp`；camera 含 `device_info`（含 `camera_type`），radar 含 `radar_info`。
- **senhub/assembly/+/status**：`assembly_id`、`type`、`timestamp`、`assembly_info`（可含 longitude、latitude、device_ids）。
- **senhub/report/+**：`device_id`、`event_id`、`event_key`、`flowId`。

校验失败时打印 WARNING 及缺失/异常字段，不中断运行。

### 联调建议

1. 先启动 `python mqtt_subscriber.py --validate`。
2. 再启动或重启网关，观察是否收到 `senhub/gateway/status` 的 online。
3. 触发设备上线/下线或装置变更，观察 `senhub/device/status`、`senhub/assembly/+/status` 消息格式。
4. 触发报警并执行带 mqtt_publish 的工作流，观察 `senhub/report/+` 是否含 `event_id`、`event_key` 等。

---

## 开发说明

### 代码结构

- `mqtt_test_client.py`: 主测试程序（命令下发与响应）
  - `MqttTestClient`: MQTT测试客户端类
  - 各种测试方法（`test_capture`, `test_ptz_control`等）
  - 报告生成功能
- `mqtt_subscriber.py`: MQTT 监听测试（仅订阅、可选校验）
  - 默认订阅 `senhub/#`，可指定 `--topics`、`--validate`、`--duration`

### 扩展测试

要添加新的测试用例，可以：

1. 在 `MqttTestClient` 类中添加新的测试方法
2. 在 `main()` 函数中添加测试调用
3. 更新命令行参数解析

## 许可证

本项目遵循与主项目相同的许可证。
