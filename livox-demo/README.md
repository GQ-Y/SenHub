# Livox Mid-360 激光雷达 Demo

基于 Livox-SDK2 官方 SDK 的 Mid-360 激光雷达通信连接和数据采集 Demo 项目。

**GitHub 仓库**: https://github.com/GQ-Y/livox-so.git

## 功能特性

- ✅ 基于官方 Livox SDK2 (C/C++) 通过 JNI 调用
- ✅ UDP 通信连接（自动设备发现和连接）
- ✅ 点云数据采集（笛卡尔坐标点云）
- ✅ 实时统计信息输出（点数、坐标范围、平均距离、点云密度等）
- ✅ 控制台友好输出
- ✅ **GitHub Actions 多平台自动编译**（Linux x86_64 + macOS ARM64）

## 系统要求

- **开发环境**: Java 11+, Maven 3.6+, CMake 3.0+
- **运行环境**: Ubuntu 18.04+ (Linux)
- **Livox Mid-360 激光雷达设备**（IP: 192.168.1.115）

## 项目结构

```
livox-demo/
├── pom.xml                    # Maven 项目配置
├── config.json                # Livox SDK 配置文件
├── build.sh                   # 编译脚本（在远程服务器上执行）
├── deploy.sh                  # 部署脚本（从本地部署到远程服务器）
├── README.md                  # 使用说明
├── jni/                       # JNI 封装代码
│   ├── LivoxJNI.h            # JNI 头文件
│   ├── LivoxJNI.cpp          # JNI 实现
│   └── CMakeLists.txt        # CMake 配置
├── lib/                       # 编译后的动态库目录
├── src/
│   └── main/
│       ├── java/
│       │   └── com/livox/demo/
│       │       ├── Main.java              # 主程序入口
│       │       ├── LivoxJNI.java          # JNI 接口类
│       │       ├── LivoxSDKDriver.java    # SDK 驱动封装
│       │       ├── PointCloudCallback.java # 点云回调接口
│       │       └── PointCloudStats.java   # 点云统计类
│       └── resources/
│           └── logback.xml                # 日志配置
└── livox-sdk2-official/      # Livox SDK2 官方仓库（需要克隆）
```

## 快速开始

### 方式一：使用 GitHub Actions 编译（推荐）

1. **克隆仓库（包含子模块）**
   ```bash
   git clone --recursive https://github.com/GQ-Y/livox-so.git
   cd livox-so
   ```

2. **查看编译状态**
   - 访问 GitHub 仓库的 Actions 页面
   - 查看最新的编译运行状态

3. **下载编译产物**
   - 在 Actions 页面找到成功的编译运行
   - 下载对应平台的 Artifacts：
     - `livox-demo-linux-x86_64.zip` - Linux 版本
     - `livox-demo-macos-arm64.zip` - macOS M1 版本

4. **运行程序**
   ```bash
   # 解压下载的 Artifacts
   unzip livox-demo-linux-x86_64.zip
   cd livox-demo-linux-x86_64
   
   # 设置动态库路径
   export LD_LIBRARY_PATH=./lib/linux:$LD_LIBRARY_PATH
   
   # 运行
   java -Djava.library.path=./lib/linux -jar livox-demo-1.0.0.jar
   ```

### 方式二：本地编译

#### 1. 克隆仓库和子模块

```bash
git clone --recursive https://github.com/GQ-Y/livox-so.git
cd livox-so
```

或者如果已经克隆了仓库：

```bash
git submodule update --init --recursive
```

### 2. 配置设备信息

编辑 `config.json`，修改设备 IP 和端口配置：

```json
{
  "MID360": {
    "lidar_net_info" : {
      "cmd_data_port"  : 56100,
      "push_msg_port"  : 56200,
      "point_data_port": 56300,
      "imu_data_port"  : 56400,
      "log_data_port"  : 56500
    },
    "host_net_info" : [
      {
        "host_ip"        : "192.168.1.5",  // 修改为你的主机IP
        "multicast_ip"   : "224.1.1.5",
        "cmd_data_port"  : 56101,
        "push_msg_port"  : 56201,
        "point_data_port": 56301,
        "imu_data_port"  : 56401,
        "log_data_port"  : 56501
      }
    ]
  }
}
```

#### 2. 本地编译（Linux）

```bash
# 使用编译脚本
./scripts/build-sdk.sh
./scripts/build-jni.sh
./scripts/build-java.sh
```

#### 3. 本地编译（macOS M1）

```bash
# 使用编译脚本
./scripts/build-sdk.sh
./scripts/build-jni.sh
./scripts/build-java.sh
```

或者使用 macOS 专用脚本：

```bash
./build-macos.sh
```

### 方式三：在远程服务器上编译和运行

#### 使用部署脚本（推荐）

```bash
# 从本地部署到远程服务器
./deploy.sh
```

#### 手动部署

1. **将项目文件传输到远程服务器**

```bash
scp -r livox-demo/ zyc@192.168.1.210:/home/zyc/data/xwq/
```

2. **SSH 连接到远程服务器**

```bash
ssh zyc@192.168.1.210
cd /home/zyc/data/xwq/livox-demo
```

3. **编译 SDK 和 JNI 动态库**

```bash
# 安装依赖（如果未安装）
sudo apt update
sudo apt install -y cmake build-essential openjdk-11-jdk

# 编译
./build.sh
```

4. **编译 Java 项目**

```bash
mvn clean package
```

5. **运行程序**

```bash
# 设置动态库路径
export LD_LIBRARY_PATH=/usr/local/lib:$LD_LIBRARY_PATH:./lib

# 运行
java -Djava.library.path=./lib:/usr/local/lib -jar target/livox-demo-1.0.0.jar
```

## 编译说明

### 编译流程

1. **编译 Livox SDK2**
   - 进入 `livox-sdk2-official` 目录
   - 执行 `mkdir build && cd build && cmake .. && make -j`
   - 执行 `sudo make install` 安装到系统

2. **编译 JNI 动态库**
   - 进入 `jni` 目录
   - 执行 `mkdir build && cd build && cmake .. && make -j`
   - 生成的 `liblivoxjni.so` 在 `lib/` 目录

3. **编译 Java 项目**
   - 执行 `mvn clean package`
   - 生成的 JAR 在 `target/` 目录

### 依赖项

- **Livox SDK2**: 官方 C/C++ SDK
- **JNI**: Java Native Interface
- **Netty**: Java UDP 通信（已移除，使用官方 SDK）
- **SLF4J + Logback**: 日志

## 输出示例

程序运行后，控制台会输出类似以下格式的统计信息：

```
[2024-01-01 10:00:00] 收到点云数据帧 #1
  点数: 5000
  坐标范围: X[-5.2, 5.1] Y[-3.8, 3.9] Z[-0.5, 2.3] (米)
  质心位置: (0.5, 0.3, 1.2) (米)
  平均距离: 4.2 米
  点云密度: 1250 点/平方米
  累计帧数: 1, 累计点数: 5000
```

## 故障排查

### 无法加载动态库

**错误**: `UnsatisfiedLinkError: no livoxjni in java.library.path`

**解决方案**:
```bash
# 确保动态库在正确的位置
export LD_LIBRARY_PATH=/usr/local/lib:$LD_LIBRARY_PATH:./lib
java -Djava.library.path=./lib:/usr/local/lib -jar target/livox-demo-1.0.0.jar
```

### SDK 初始化失败

1. **检查配置文件路径**
   - 确保 `config.json` 文件存在且格式正确
   - 检查配置文件中的 IP 地址是否正确

2. **检查网络连接**
   - 确保计算机与雷达在同一网络
   - 检查防火墙设置

3. **检查设备状态**
   - 确认雷达设备已启动
   - 尝试 ping 设备 IP

### 无法接收点云数据

1. **检查设备连接**
   - 查看日志中是否有设备发现信息
   - 确认设备 IP 配置正确

2. **检查工作模式**
   - 确保设备工作模式为 NORMAL（正常模式）
   - SDK 会自动设置工作模式

## 开发说明

### JNI 接口

主要 JNI 方法：

- `LivoxJNI.init(String configPath)`: 初始化 SDK
- `LivoxJNI.start()`: 启动 SDK
- `LivoxJNI.stop()`: 停止 SDK
- `LivoxJNI.setPointCloudCallback(PointCloudCallback)`: 设置点云回调

### 扩展功能

如需扩展功能，可以：

1. **添加更多回调**
   - IMU 数据回调
   - 设备状态回调
   - 命令响应回调

2. **数据保存**
   - 在 `PointCloudStats` 中添加文件保存功能
   - 支持 CSV、PCD 等格式

3. **可视化**
   - 集成 Three.js 或其他 3D 库
   - 实时渲染点云数据

## 参考资料

- [Livox SDK 官方文档](https://livox-wiki-cn.readthedocs.io/zh_CN/latest/)
- [Livox-SDK2 GitHub](https://github.com/Livox-SDK/Livox-SDK2)
- [MID-360 产品手册](https://www.livoxtech.com/mid-360)
- [JNI 开发指南](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/)

## 许可证

本项目仅用于演示和学习目的。Livox SDK2 遵循其官方许可证。
