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
    "qos": 1
  },
  "test": {
    "default_device_id": "192.168.1.100:8000",
    "response_timeout": 30
  }
}
```

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

## 开发说明

### 代码结构

- `mqtt_test_client.py`: 主测试程序
  - `MqttTestClient`: MQTT测试客户端类
  - 各种测试方法（`test_capture`, `test_ptz_control`等）
  - 报告生成功能

### 扩展测试

要添加新的测试用例，可以：

1. 在 `MqttTestClient` 类中添加新的测试方法
2. 在 `main()` 函数中添加测试调用
3. 更新命令行参数解析

## 许可证

本项目遵循与主项目相同的许可证。
