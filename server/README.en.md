<div align="center">

<img src="../assets/logo.svg" alt="SenHub Logo" width="200"/>

# SenHub — Unified Multi-Brand Video Surveillance Alarm Hub

**Comprehensive Digital Video Surveillance Gateway System for Intelligent Security Scenarios**

[![License](https://img.shields.io/badge/license-Proprietary-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-11+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-red.svg)](https://maven.apache.org/)
[![GitHub](https://img.shields.io/badge/GitHub-SenHub-blue.svg)](https://github.com/GQ-Y/SenHub)

**One Platform Manages All Brands, One Event Triggers All Intelligence**

[English](README.en.md) | [中文](README.md)

[Quick Start](#quick-start) • [Key Features](#key-features) • [Technical Documentation](#technical-documentation) • [Contributing](#contributing)

</div>

---

## Project Overview

SenHub is a comprehensive digital video surveillance gateway system designed for intelligent security scenarios. It deeply integrates SDKs from mainstream camera manufacturers such as Hikvision, Dahua, and Tiandy, breaking down brand barriers to achieve unified access, standardized processing, and intelligent distribution of alarm events. The system can capture images in real-time, control PTZ cameras, and centrally receive alarm signals triggered by various devices (such as motion detection, intrusion, device abnormalities, etc.), pushing structured alarm events to AI analysis platforms, operation systems, or command centers in milliseconds through open protocols such as MQTT and Webhook.

SenHub not only significantly simplifies the integration complexity of multi-brand devices but also serves as an ideal frontend entry point for AI multimodal vision large models, providing high-quality, low-latency event-driven foundations for intelligent diagnosis, risk warning, and automated response, truly achieving "one platform manages all brands, one event triggers all intelligence."

<img src="../assets/banner.svg" alt="SenHub Banner" width="100%"/>

## Quick Start

> Before installing SenHub, make sure your device meets the minimum requirements:
>
> - **Operating System**: Linux (ARM64 or x86_64)
> - **CPU**: ≥ 2 cores
> - **Memory**: ≥ 2 GB RAM
> - **Storage**: ≥ 5 GB free space
> - **Java**: 11 or higher
> - **Maven**: 3.6 or higher
> - **MQTT Server**: Accessible MQTT Broker

### 1. Prepare SDK Library Files

Copy SDK library files from each brand to the `lib/` directory:

```bash
cd server
mkdir -p lib/arm/hikvision lib/arm/dahua lib/x86/hikvision lib/x86/tiandy lib/x86/dahua

# Example: Copy Hikvision ARM SDK
cp -r /path/to/HCNetSDK/arm64/* lib/arm/hikvision/

# Example: Copy Tiandy x86 SDK
cp -r /path/to/TiandySDK/x86/* lib/x86/tiandy/
```

### 2. Build the Project

```bash
cd server
mvn clean package
```

### 3. Configure the System

Edit the `src/main/resources/config.yaml` file to configure MQTT server address, device authentication information, etc.:

```yaml
mqtt:
  broker: "tcp://your-mqtt-broker:1883"
  client_id: "senhub-gateway"
  username: "your-username"
  password: "your-password"
```

### 4. Run the Service

```bash
# Method 1: Run with Maven
mvn exec:java -Dexec.mainClass="com.digital.video.gateway.Main"

# Method 2: Run the packaged jar
java -jar target/video-gateway-service-1.0.0.jar
```

After the service starts, the default HTTP API port is `8080`, and you can access the management interface at `http://localhost:8080`.

## Key Features

### Multi-Brand Unified Access

- ✅ **Hikvision** - Complete SDK integration, supporting device discovery, control, and alarm reception
- ✅ **Dahua** - Full feature support, including PTZ control and video playback
- ✅ **Tiandy** - Deep integration, supporting multi-channel device management
- ✅ **Automatic Device Discovery** - Automatic LAN scanning, intelligent brand and model identification
- ✅ **Unified Device Management** - Cross-brand device unified management interface, centralized configuration and monitoring

### Unified Alarm Event Processing

- ✅ **Multi-Type Alarm Reception** - Motion detection, intrusion, device abnormalities, intrusion detection, etc.
- ✅ **Standardized Event Format** - Unified event data structure for easy downstream system processing
- ✅ **Millisecond-Level Event Push** - Real-time alarm event push through MQTT and Webhook
- ✅ **Alarm Rule Engine** - Flexible rule configuration, supporting complex alarm logic
- ✅ **Alarm History Records** - Complete alarm event history query and statistics

### Real-Time Image Capture and PTZ Control

- ✅ **Real-Time Image Capture** - Support Base64 encoding or file upload
- ✅ **PTZ Control** - Up, down, left, right rotation, zoom, preset positions
- ✅ **Video Playback** - Support time-range video query and download
- ✅ **Audio Playback** - Support device-side audio playback
- ✅ **PTZ Position Monitoring** - Real-time monitoring of PTZ camera position status

### AI Multimodal Vision Large Model Frontend Entry

- ✅ **High-Quality Image Output** - Provide standardized image data for AI analysis
- ✅ **Low-Latency Event-Driven** - Millisecond-level event push, meeting real-time AI analysis requirements
- ✅ **Structured Data Output** - Unified format for device information, alarm events, and image metadata
- ✅ **Workflow Engine** - Visual workflow configuration, supporting AI analysis result linkage

### Open Protocol Integration

- ✅ **MQTT Protocol** - Standard MQTT 3.1.1/5.0 support with QoS guarantee
- ✅ **Webhook Callback** - HTTP/HTTPS Webhook support, integration with WeChat Work, DingTalk, Feishu
- ✅ **RESTful API** - Complete REST API supporting device management, configuration, and query
- ✅ **WebSocket** - Real-time data push, supporting frontend real-time monitoring

### Device Management and Monitoring

- ✅ **Device Status Monitoring** - Real-time monitoring of device online/offline status
- ✅ **Automatic Keep-Alive Mechanism** - Automatic device connection detection and reconnection
- ✅ **Device Information Management** - IP, port, RTSP address, channel information, etc.
- ✅ **Batch Operations** - Support batch device configuration and management
- ✅ **Device Grouping** - Support device grouping management for large-scale deployment

### Workflow Engine

- ✅ **Visual Process Configuration** - Configure workflows through API or interface
- ✅ **Multi-Node Support** - Capture, upload, notification, PTZ control, and other nodes
- ✅ **Event Triggering** - Automatically trigger workflows based on alarm events
- ✅ **Conditional Logic** - Support complex conditional logic for flexible process control

### Data Storage

- ✅ **SQLite Database** - Lightweight local database storing device information and configuration
- ✅ **OSS Object Storage** - Support Alibaba Cloud OSS, MinIO, and other object storage
- ✅ **Video Recording Management** - Automatic recording and storage management
- ✅ **Logging System** - Complete log recording and query

## Technical Documentation

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      SenHub Gateway                          │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │Hikvision │  │  Dahua   │  │  Tiandy  │  ...              │
│  │   SDK    │  │   SDK    │  │   SDK    │                   │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘                  │
│       │             │             │                          │
│       └─────────────┼─────────────┘                          │
│                     │                                         │
│  ┌──────────────────▼──────────────────┐                    │
│  │    Unified Device Management        │                    │
│  │  - Device Discovery & Registration  │                    │
│  │  - Status Monitoring & Keep-Alive  │                    │
│  │  - Command Routing & Processing     │                    │
│  └──────────────────┬──────────────────┘                    │
│                     │                                         │
│  ┌──────────────────▼──────────────────┐                    │
│  │      Event Processing Engine       │                    │
│  │  - Alarm Event Reception            │                    │
│  │  - Event Standardization            │                    │
│  │  - Workflow Execution               │                    │
│  └──────────────────┬──────────────────┘                    │
│                     │                                         │
│  ┌──────────────────▼──────────────────┐                    │
│  │      Open Protocol Interface        │                    │
│  │  - MQTT Publish/Subscribe           │                    │
│  │  - Webhook Callback                 │                    │
│  │  - RESTful API                      │                    │
│  └──────────────────────────────────────┘                    │
└─────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
    AI Analysis          Operations          Command
      Platform            System              Center
```

### Project Structure

```
server/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/digital/video/gateway/
│   │   │       ├── Main.java              # Main entry point
│   │   │       ├── api/                   # REST API controllers
│   │   │       ├── config/                # Configuration management
│   │   │       ├── hikvision/             # Hikvision SDK wrapper
│   │   │       ├── dahua/                 # Dahua SDK wrapper
│   │   │       ├── tiandy/                # Tiandy SDK wrapper
│   │   │       ├── mqtt/                  # MQTT client
│   │   │       ├── device/                # Device management
│   │   │       ├── scanner/               # Device scanner
│   │   │       ├── keeper/                # Keep-alive system
│   │   │       ├── command/               # Command processing
│   │   │       ├── service/               # Business service layer
│   │   │       ├── workflow/              # Workflow engine
│   │   │       ├── database/              # Database access
│   │   │       └── oss/                   # OSS upload
│   │   └── resources/
│   │       ├── config.yaml                # Configuration file
│   │       └── logback.xml                 # Logging configuration
├── lib/                                   # SDK library directory
│   ├── arm/                               # ARM architecture libraries
│   │   ├── hikvision/
│   │   └── dahua/
│   └── x86/                               # x86 architecture libraries
│       ├── hikvision/
│       ├── tiandy/
│       └── dahua/
├── pom.xml
└── README.md
```

### MQTT Message Format

#### Status Report

**Topic**: `hikvision/status` (configurable)

```json
{
  "device_id": "192.168.1.100",
  "status": "online|offline",
  "timestamp": 1234567890,
  "device_info": {
    "name": "Camera Name",
    "ip": "192.168.1.100",
    "port": 8000,
    "rtsp_url": "rtsp://192.168.1.100:554/Streaming/Channels/101",
    "brand": "hikvision",
    "model": "DS-2CD2xxx",
    "channels": 1
  }
}
```

#### Alarm Event Report

**Topic**: `hikvision/alarm` (configurable)

```json
{
  "device_id": "192.168.1.100",
  "alarm_type": "motion_detect|intrusion|device_abnormal",
  "alarm_level": "info|warning|critical",
  "timestamp": 1234567890,
  "data": {
    "channel": 1,
    "description": "Motion detection alarm"
  }
}
```

#### Command Dispatch

**Topic**: `hikvision/command` (configurable)

**Capture Command**:

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

**PTZ Control Command**:

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

#### Command Response

**Topic**: `hikvision/response` (configurable)

```json
{
  "request_id": "uuid-string",
  "device_id": "192.168.1.100",
  "command": "capture",
  "success": true,
  "data": {
    "image_base64": "base64-encoded-image"
  },
  "error": "",
  "timestamp": 1234567890
}
```

### RESTful API

SenHub provides a complete RESTful API supporting device management, configuration, query, and other functions.

**Base URL**: `http://localhost:8080/api`

Main interfaces include:

- `GET /api/devices` - Get device list
- `GET /api/devices/{deviceId}` - Get device details
- `POST /api/devices/{deviceId}/capture` - Capture image
- `POST /api/devices/{deviceId}/ptz` - PTZ control
- `GET /api/alarms` - Get alarm history
- `POST /api/workflows` - Create workflow

For detailed API documentation, please refer to the API controller classes in the code.

### Configuration

For detailed configuration, please refer to the comments in the `src/main/resources/config.yaml` file. Main configuration items include:

- **MQTT Configuration**: Broker address, authentication information, topic configuration
- **Device Configuration**: Default username/password, port configuration, brand presets
- **Scanner Configuration**: Auto-scan switch, scan range, scan interval
- **Keep-Alive Configuration**: Check interval, offline threshold
- **OSS Configuration**: Object storage type, authentication information, storage path
- **Logging Configuration**: Log level, file path, retention policy

### Database

SQLite is used to store device information, configuration, and alarm records. The database file is located at `data/devices.db`.

Main data tables:

- `devices` - Device information table
- `device_ptz_extension` - Device PTZ extension information
- `alarm_rules` - Alarm rules table
- `alarm_records` - Alarm records table
- `workflows` - Workflow configuration table
- `system_config` - System configuration table

## Important Notes

1. **Architecture Requirements**: SDK library files must match the system architecture (ARM64 or x86_64). Please ensure you use the correct library files.
2. **Library File Path**: Ensure SDK library file paths are correct. The program loads these libraries through JNA.
3. **Permission Requirements**: Sufficient permissions are required to access the network and file system.
4. **MQTT Connection**: Ensure the MQTT server is accessible, otherwise status reporting and command reception will fail.
5. **SDK License**: This project is developed based on various manufacturer SDKs. Please comply with the license requirements of the respective SDKs.

## Development Guide

### SDK Wrapper

SDK wrappers are located in the following packages:

- `com.digital.video.gateway.hikvision` - Hikvision SDK wrapper
- `com.digital.video.gateway.dahua` - Dahua SDK wrapper
- `com.digital.video.gateway.tiandy` - Tiandy SDK wrapper

All SDK wrappers use JNA (Java Native Access) to call the C interfaces of each brand's SDK.

### Adding New Features

1. **Adding New Command Processing**:

   - Add a new command handler class in the `com.digital.video.gateway.command` package
   - Register the new command handler in `CommandHandler`
   - Add command routing in `MqttClient`

2. **Adding New SDK Interfaces**:

   - Add interface methods in the corresponding SDK wrapper class
   - Use JNA to define corresponding C structures and function interfaces

3. **Adding New Workflow Nodes**:

   - Implement a new handler in the `com.digital.video.gateway.workflow.handlers` package
   - Register the handler in `FlowExecutor`

## Contributing

We welcome all forms of contributions! If you would like to contribute to SenHub, please:

1. Fork [this repository](https://github.com/GQ-Y/SenHub)
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

**Repository**: [https://github.com/GQ-Y/SenHub](https://github.com/GQ-Y/SenHub)

## License

This project is developed based on SDKs from Hikvision, Dahua, Tiandy, and other manufacturers. Please comply with the license requirements of the respective SDKs.

---

<div align="center">

**SenHub** - Unified Multi-Brand Video Surveillance Alarm Hub

[Back to Top](#senhub--unified-multi-brand-video-surveillance-alarm-hub)

</div>
