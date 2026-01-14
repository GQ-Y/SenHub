# Livox Demo 部署指南

## 部署到远程服务器

### 前置条件

1. **本地环境**
   - 已克隆 Livox SDK2 官方仓库到 `livox-sdk2-official/` 目录
   - 已安装 `sshpass`（用于自动 SSH 登录）

2. **远程服务器环境**
   - Ubuntu 18.04+
   - Java 11+
   - Maven 3.6+
   - CMake 3.0+
   - GCC/G++ 编译器

### 部署步骤

#### 1. 安装 sshpass（本地）

```bash
# macOS
brew install hudochenkov/sshpass/sshpass

# Linux
sudo apt install sshpass
```

#### 2. 执行部署脚本

```bash
cd livox-demo
./deploy.sh
```

部署脚本会：
1. 检查 SSH 连接
2. 创建远程目录
3. 同步文件到远程服务器
4. 在远程服务器上编译 SDK 和 JNI
5. 编译 Java 项目

#### 3. 在远程服务器上运行

```bash
ssh zyc@192.168.1.210
cd /home/zyc/data/xwq/livox-demo

# 设置动态库路径
export LD_LIBRARY_PATH=/usr/local/lib:$LD_LIBRARY_PATH:./lib

# 运行程序
java -Djava.library.path=./lib:/usr/local/lib -jar target/livox-demo-1.0.0.jar
```

### 手动部署（如果自动部署失败）

#### 1. 传输文件

```bash
# 压缩项目（排除不必要的文件）
tar -czf livox-demo.tar.gz \
    --exclude='target' \
    --exclude='.git' \
    --exclude='*.log' \
    livox-demo/

# 传输到远程服务器
scp livox-demo.tar.gz zyc@192.168.1.210:/home/zyc/data/xwq/

# SSH 连接
ssh zyc@192.168.1.210
cd /home/zyc/data/xwq
tar -xzf livox-demo.tar.gz
cd livox-demo
```

#### 2. 安装依赖

```bash
sudo apt update
sudo apt install -y cmake build-essential openjdk-11-jdk maven
```

#### 3. 编译

```bash
# 编译 SDK 和 JNI
./build.sh

# 编译 Java 项目
mvn clean package
```

#### 4. 运行

```bash
export LD_LIBRARY_PATH=/usr/local/lib:$LD_LIBRARY_PATH:./lib
java -Djava.library.path=./lib:/usr/local/lib -jar target/livox-demo-1.0.0.jar
```

### 常见问题

#### 1. 编译失败：找不到 Livox SDK

**错误**: `Could not find livox_lidar_sdk_shared`

**解决方案**:
```bash
# 确保已编译并安装 Livox SDK2
cd livox-sdk2-official
mkdir -p build && cd build
cmake .. && make -j$(nproc)
sudo make install
```

#### 2. JNI 编译失败：找不到 JNI 头文件

**错误**: `fatal error: jni.h: No such file or directory`

**解决方案**:
```bash
# 安装 JDK 开发包
sudo apt install openjdk-11-jdk
```

#### 3. 运行时无法加载动态库

**错误**: `UnsatisfiedLinkError`

**解决方案**:
```bash
# 检查动态库是否存在
ls -la lib/liblivoxjni.so
ls -la /usr/local/lib/liblivox_lidar_sdk_shared.so

# 设置库路径
export LD_LIBRARY_PATH=/usr/local/lib:$LD_LIBRARY_PATH:./lib
```

### 更新部署

如果代码有更新，重新执行部署脚本即可：

```bash
./deploy.sh
```

脚本会自动：
1. 停止旧进程
2. 删除旧文件
3. 重新编译
4. 准备运行

### 远程调试

如果需要远程调试，可以使用 SSH 端口转发：

```bash
# 本地执行
ssh -L 5005:localhost:5005 zyc@192.168.1.210

# 在远程服务器上运行（启用调试）
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -Djava.library.path=./lib:/usr/local/lib \
     -jar target/livox-demo-1.0.0.jar
```

然后在本地 IDE 中连接到 `localhost:5005` 进行调试。
