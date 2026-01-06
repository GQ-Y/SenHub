# 海康威视NVR录像机控制服务

基于海康威视NVR录像机Linux端SDK开发的Java语言局域网控制服务程序。

## 功能特性

- ✅ 自动扫描局域网海康摄像头设备
- ✅ 自动匹配设备并进行登录验证
- ✅ MQTT协议上报摄像头状态（在线/离线）
- ✅ 获取并上报摄像头信息（名称、IP、端口、RTSP等）
- ✅ 接收MQTT命令：
  - 抓图命令（返回base64图片）
  - 重启命令
  - 录像回放获取（上传至OSS）
  - 播放声音命令
- ✅ 自动保活系统（监控设备在线状态）
- ✅ 云台驱动控制（上下左右转动、变焦）

## 系统要求

- Linux操作系统（ARM64架构）
- Java 11+
- Maven 3.6+
- 海康威视设备网络 SDK（libhcnetsdk.so）
- MQTT服务器

## 项目结构

```
server/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/hikvision/nvr/
│   │   │       ├── Main.java              # 主程序入口
│   │   │       ├── config/                # 配置管理
│   │   │       ├── hikvision/             # 海康威视SDK封装
│   │   │       ├── mqtt/                 # MQTT客户端
│   │   │       ├── device/               # 设备管理
│   │   │       ├── scanner/               # 设备扫描
│   │   │       ├── keeper/                # 保活系统
│   │   │       ├── command/               # 命令处理
│   │   │       └── oss/                   # OSS上传
│   │   └── resources/
│   │       ├── config.yaml               # 配置文件
│   │       └── logback.xml                # 日志配置
├── lib/                                   # SDK库文件目录（需要从SDK复制）
├── pom.xml
└── README.md
```

## 安装和运行

### 1. 准备SDK库文件

将SDK库文件复制到 `lib/` 目录下：

```bash
cd server
mkdir -p lib
cp -r ../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll/* lib/
```

### 2. 编译项目

```bash
cd server
mvn clean package
```

### 3. 配置

编辑 `src/main/resources/config.yaml` 文件，配置MQTT服务器地址、设备认证信息等。

### 4. 运行

```bash
# 方式1：使用Maven运行
mvn exec:java -Dexec.mainClass="com.hikvision.nvr.Main"

# 方式2：运行打包后的jar
java -jar target/nvr-control-service-1.0.0.jar
```

## 配置说明

详细配置请参考`src/main/resources/config.yaml`文件中的注释。

## MQTT消息格式

### 状态上报

主题：`hikvision/status`

消息格式：
```json
{
  "device_id": "192.168.1.100",
  "status": "online|offline",
  "timestamp": 1234567890,
  "device_info": {
    "name": "摄像头名称",
    "ip": "192.168.1.100",
    "port": 8000,
    "rtsp_url": "rtsp://192.168.1.100:554/Streaming/Channels/101"
  }
}
```

### 命令下发

主题：`hikvision/command`

#### 抓图命令
```json
{
  "command": "capture",
  "device_id": "192.168.1.100",
  "request_id": "uuid-string",
  "data": {
    "channel": 1
  }
}
```

#### 重启命令
```json
{
  "command": "reboot",
  "device_id": "192.168.1.100",
  "request_id": "uuid-string"
}
```

#### 录像回放命令
```json
{
  "command": "playback",
  "device_id": "192.168.1.100",
  "request_id": "uuid-string",
  "data": {
    "start_time": "2024-01-01 10:00:00",
    "end_time": "2024-01-01 11:00:00"
  }
}
```

#### 播放声音命令
```json
{
  "command": "play_audio",
  "device_id": "192.168.1.100",
  "request_id": "uuid-string",
  "data": {
    "audio_data": "base64-encoded-audio"
  }
}
```

#### 云台控制命令
```json
{
  "command": "ptz_control",
  "device_id": "192.168.1.100",
  "request_id": "uuid-string",
  "data": {
    "action": "up|down|left|right|zoom_in|zoom_out",
    "speed": 5,
    "channel": 1
  }
}
```

### 命令响应

主题：`hikvision/response`

消息格式：
```json
{
  "request_id": "uuid-string",
  "device_id": "192.168.1.100",
  "command": "capture",
  "success": true,
  "data": {},
  "error": "",
  "timestamp": 1234567890
}
```

## 数据库

使用SQLite存储设备信息，数据库文件位于 `data/devices.db`。

设备表结构：
- `device_id`: 设备ID（IP地址）
- `serial_number`: 设备序列号
- `name`: 设备名称
- `ip`: IP地址
- `port`: 端口
- `username`: 用户名
- `password`: 密码（加密存储）
- `status`: 状态（online/offline）
- `channels`: 通道数
- `rtsp_url`: RTSP地址

## 注意事项

1. **架构要求**: SDK库文件为ARM64架构，只能在ARM64 Linux系统上运行
2. **库文件路径**: 确保SDK库文件路径正确，程序会通过JNA加载这些库
3. **权限要求**: 需要足够的权限访问网络和文件系统
4. **MQTT连接**: 确保MQTT服务器可访问，否则无法上报状态和接收命令

## 开发说明

### SDK封装

SDK封装位于 `com.hikvision.nvr.hikvision` 包，使用JNA调用海康威视SDK的C接口。

### 添加新功能

1. 在 `com.hikvision.nvr.command` 包中添加新的命令处理类
2. 在 `MqttClient` 中添加命令路由
3. 如需新的SDK接口，在 `HikvisionSDK` 中添加封装

## 许可证

本项目基于海康威视SDK开发，请遵守海康威视SDK的许可证要求。
