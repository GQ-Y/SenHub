<div align="center">

<img src="assets/logo.svg" alt="SenHub Logo" width="200"/>

# SenHub — 多品牌视频监控报警统一中枢

**Unified Multi-Brand Video Surveillance Alarm Hub**

[![License](https://img.shields.io/badge/license-Proprietary-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-11+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-red.svg)](https://maven.apache.org/)
[![GitHub](https://img.shields.io/badge/GitHub-SenHub-blue.svg)](https://github.com/GQ-Y/SenHub)

**一平台纳管全品牌，一事件触发全智能**  
**One Platform Manages All Brands, One Event Triggers All Intelligence**

[English](server/README.en.md) | [中文](server/README.md)

</div>

---

## 项目简介 | Project Overview

SenHub 是一款面向智能安防场景的综合性数字视频监控网关系统，深度集成海康、大华、天地伟业等主流厂商摄像头 SDK，打破品牌壁垒，实现报警事件的统一接入、标准化处理与智能分发。

SenHub is a comprehensive digital video surveillance gateway system designed for intelligent security scenarios. It deeply integrates SDKs from mainstream camera manufacturers such as Hikvision, Dahua, and Tiandy, breaking down brand barriers to achieve unified access, standardized processing, and intelligent distribution of alarm events.

### 核心特性 | Key Features

- 🔌 **多品牌统一接入** | Multi-Brand Unified Access
- 🚨 **报警事件统一处理** | Unified Alarm Event Processing
- 📸 **实时图像与云台控制** | Real-Time Image Capture and PTZ Control
- 🤖 **AI 多模态视觉大模型前端入口** | AI Multimodal Vision Large Model Frontend Entry
- 🔄 **开放协议集成** | Open Protocol Integration
- 📊 **设备管理与监控** | Device Management and Monitoring
- 🎯 **工作流引擎** | Workflow Engine

## 快速开始 | Quick Start

```bash
# Clone the repository
git clone https://github.com/GQ-Y/SenHub.git
cd SenHub/server

# Build the project
mvn clean package

# Run the service
java -jar target/video-gateway-service-1.0.0.jar
```

更多详细信息请查看：
For more details, please see:

- 📖 [中文文档](server/README.md)
- 📖 [English Documentation](server/README.en.md)

## 项目结构 | Project Structure

```
SenHub/
├── server/              # 核心服务端 | Core Server
│   ├── src/            # 源代码 | Source Code
│   ├── lib/            # SDK 库文件 | SDK Libraries
│   └── README.md       # 中文文档 | Chinese Documentation
│   └── README.en.md    # 英文文档 | English Documentation
├── assets/             # 品牌资源 | Brand Assets
│   ├── logo.svg        # Logo
│   └── banner.svg      # Banner
├── web-iot-panel/      # Web 管理界面 | Web Management Panel
└── README.md           # 本文件 | This File
```

## 仓库信息 | Repository

**GitHub**: [https://github.com/GQ-Y/SenHub](https://github.com/GQ-Y/SenHub)

## 贡献 | Contributing

我们欢迎所有形式的贡献！请查看 [贡献指南](server/README.md#贡献指南)。

We welcome all forms of contributions! Please see the [Contributing Guide](server/README.en.md#contributing).

## 许可证 | License

本项目基于海康威视、大华、天地伟业等厂商 SDK 开发，请遵守相应 SDK 的许可证要求。

This project is developed based on SDKs from Hikvision, Dahua, Tiandy, and other manufacturers. Please comply with the license requirements of the respective SDKs.

---

<div align="center">

**SenHub** - 多品牌视频监控报警统一中枢 | Unified Multi-Brand Video Surveillance Alarm Hub

[返回顶部](#senhub--多品牌视频监控报警统一中枢) | [Back to Top](#senhub--多品牌视频监控报警统一中枢)

</div>
