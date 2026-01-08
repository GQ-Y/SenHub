#!/bin/bash

# 综合性数字视频监控网关系统 - 服务器更新部署脚本
# 用于SSH连接到服务器，同步代码，重新编译和启动服务

set -e

# 服务器配置
SERVER_IP="192.168.1.210"
SERVER_USER="zyc"
SERVER_PASS="admin"
REMOTE_DIR="/home/zyc/data/xwq/demo"
PROJECT_NAME="demo"

echo "=========================================="
echo "综合性数字视频监控网关系统 - 服务器更新部署"
echo "=========================================="
echo ""

# 1. 检查SSH连接
echo "[1/7] 检查SSH连接..."
if ! sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 "$SERVER_USER@$SERVER_IP" "echo 'SSH连接成功'" 2>/dev/null; then
    echo "❌ SSH连接失败，请检查网络和服务器配置"
    exit 1
fi
echo "✅ SSH连接成功"
echo ""

# 2. 创建远程目录
echo "[2/7] 创建远程目录..."
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" "mkdir -p $REMOTE_DIR/server && echo '远程目录已创建'"
echo "✅ 远程目录准备完成"
echo ""

# 3. 同步代码到服务器
echo "[3/7] 同步代码到服务器..."
cd "$(dirname "$0")"
rsync -avz --delete \
    --exclude='.git' \
    --exclude='node_modules' \
    --exclude='target' \
    --exclude='venv' \
    --exclude='*.log' \
    --exclude='.DS_Store' \
    --exclude='hs_err_*.log' \
    --exclude='dependency-reduced-pom.xml' \
    -e "sshpass -p '$SERVER_PASS' ssh -o StrictHostKeyChecking=no" \
    ./ "$SERVER_USER@$SERVER_IP:$REMOTE_DIR/server/"

if [ $? -eq 0 ]; then
    echo "✅ 代码同步成功"
else
    echo "❌ 代码同步失败"
    exit 1
fi
echo ""

# 4. 停止旧服务
echo "[4/7] 停止旧服务..."
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" << 'EOF'
    cd /home/zyc/data/xwq/demo/server
    
    # 查找并停止运行中的服务
    PID=$(ps aux | grep -E 'video-gateway-service|com.digital.video.gateway.Main' | grep -v grep | awk '{print $2}' | head -1)
    
    if [ -n "$PID" ]; then
        echo "发现运行中的服务，PID: $PID"
        kill -9 $PID 2>/dev/null || true
        sleep 2
        echo "✅ 旧服务已停止"
    else
        echo "ℹ️  没有运行中的服务"
    fi
EOF
echo ""

# 5. 删除旧的可执行文件
echo "[5/7] 删除旧的可执行文件..."
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" << 'EOF'
    cd /home/zyc/data/xwq/demo/server
    
    # 删除旧的jar文件
    rm -f target/*.jar target/original-*.jar 2>/dev/null || true
    echo "✅ 旧jar文件已删除"
EOF
echo ""

# 6. 重新编译
echo "[6/7] 重新编译项目..."
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" << 'EOF'
    cd /home/zyc/data/xwq/demo/server
    
    echo "开始编译..."
    mvn clean package -DskipTests
    
    if [ $? -eq 0 ]; then
        echo "✅ 编译成功"
        
        # 检查生成的jar文件
        if [ -f "target/video-gateway-service-1.0.0.jar" ]; then
            echo "✅ JAR文件已生成: target/video-gateway-service-1.0.0.jar"
            ls -lh target/video-gateway-service-1.0.0.jar
        else
            echo "⚠️  警告: 未找到预期的JAR文件"
            ls -lh target/*.jar 2>/dev/null || echo "target目录下没有jar文件"
        fi
    else
        echo "❌ 编译失败"
        exit 1
    fi
EOF

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ 编译失败，请检查错误信息"
    exit 1
fi
echo ""

# 7. 启动服务
echo "[7/7] 启动服务..."
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" << 'EOF'
    cd /home/zyc/data/xwq/demo/server
    
    # 检查jar文件是否存在
    if [ ! -f "target/video-gateway-service-1.0.0.jar" ]; then
        echo "❌ JAR文件不存在，无法启动服务"
        exit 1
    fi
    
    # 创建必要的目录（新的架构区分目录结构）
    mkdir -p data logs sdkLog storage/captures storage/records storage/downloads
    mkdir -p lib/arm/hikvision lib/arm/dahua
    mkdir -p lib/x86/hikvision lib/x86/tiandy lib/x86/dahua
    
    # 检测系统架构
    OS_ARCH=$(uname -m)
    if [[ "$OS_ARCH" == "aarch64" || "$OS_ARCH" == "arm64" ]]; then
        ARCH_DIR="arm"
    else
        ARCH_DIR="x86"
    fi
    
    echo "检测到系统架构: $OS_ARCH (使用目录: lib/$ARCH_DIR/)"
    
    # 检查SDK库文件是否存在（根据系统架构）
    if [ ! -f "lib/$ARCH_DIR/hikvision/libhcnetsdk.so" ]; then
        echo "⚠️  警告: 海康SDK库文件不存在，服务可能无法正常启动"
        echo "   请确保SDK库文件已部署到 lib/$ARCH_DIR/hikvision/ 目录"
    else
        echo "✅ 海康SDK库文件已找到: lib/$ARCH_DIR/hikvision/libhcnetsdk.so"
    fi
    
    if [ "$ARCH_DIR" == "x86" ] && [ ! -f "lib/x86/tiandy/libnvssdk.so" ]; then
        echo "⚠️  警告: 天地伟业SDK库文件不存在（仅x86架构需要）"
        echo "   请确保SDK库文件已部署到 lib/x86/tiandy/ 目录"
    elif [ "$ARCH_DIR" == "x86" ]; then
        echo "✅ 天地伟业SDK库文件已找到: lib/x86/tiandy/libnvssdk.so"
    fi
    
    if [ ! -f "lib/$ARCH_DIR/dahua/libdhnetsdk.so" ]; then
        echo "⚠️  警告: 大华SDK库文件不存在"
        echo "   请确保SDK库文件已部署到 lib/$ARCH_DIR/dahua/ 目录"
    else
        echo "✅ 大华SDK库文件已找到: lib/$ARCH_DIR/dahua/libdhnetsdk.so"
    fi
    
    # 杀掉占用8084端口的进程（如果有）
    # 尝试使用fuser（可能需要sudo）或lsof
    PID_8084=$(lsof -ti:8084 2>/dev/null || fuser 8084/tcp 2>/dev/null | awk '{print $1}' || echo "")
    if [ -n "$PID_8084" ]; then
        echo "发现占用8084端口的进程: $PID_8084"
        kill -9 $PID_8084 2>/dev/null || true
        echo "已尝试杀掉占用8084端口的进程"
    else
        echo "8084端口未被占用"
    fi
    
    # 构建java.library.path（根据架构）
    LIB_PATH="./lib/$ARCH_DIR/hikvision:./lib/$ARCH_DIR/hikvision/HCNetSDKCom"
    if [ "$ARCH_DIR" == "x86" ]; then
        LIB_PATH="$LIB_PATH:./lib/x86/tiandy"
    fi
    LIB_PATH="$LIB_PATH:./lib/$ARCH_DIR/dahua"
    
    echo "使用库路径: $LIB_PATH"
    
    # 启动服务（后台运行，设置java.library.path）
    nohup java -Djna.library.path="$LIB_PATH" -jar target/video-gateway-service-1.0.0.jar > logs/service.log 2>&1 &
    
    sleep 3
    
    # 检查服务是否启动
    PID=$(ps aux | grep 'video-gateway-service-1.0.0.jar' | grep -v grep | awk '{print $2}' | head -1)
    
    if [ -n "$PID" ]; then
        echo "✅ 服务已启动，PID: $PID"
        echo "📋 服务信息："
        echo "   - HTTP API: http://192.168.1.210:8080"
        echo "   - 日志文件: logs/service.log"
        echo ""
        echo "查看日志: tail -f logs/service.log"
        echo "停止服务: kill $PID"
    else
        echo "❌ 服务启动失败，请查看日志: logs/service.log"
        tail -20 logs/service.log 2>/dev/null || echo "无法读取日志文件"
        exit 1
    fi
EOF

if [ $? -eq 0 ]; then
    echo ""
    echo "=========================================="
    echo "✅ 部署完成！"
    echo "=========================================="
    echo ""
    echo "服务地址: http://192.168.1.210:8080"
    echo "查看日志: ssh $SERVER_USER@$SERVER_IP 'tail -f $REMOTE_DIR/server/logs/service.log'"
else
    echo ""
    echo "❌ 服务启动失败"
    exit 1
fi
