#!/bin/bash
# 部署脚本：同步代码到服务器，重新编译并启动

SERVER_IP="192.168.1.210"
SERVER_USER="zyc"
SERVER_PASS="admin"
SERVER_DIR="/home/zyc/data/xwq/demo"
LOCAL_DIR="/Users/hook/Downloads/demo"

echo "=========================================="
echo "开始部署到服务器..."
echo "=========================================="

# 步骤1: 同步代码文件
echo ""
echo "[步骤1] 同步代码文件到服务器..."
sshpass -p "$SERVER_PASS" rsync -avz --progress \
    -e "ssh -o StrictHostKeyChecking=no" \
    "$LOCAL_DIR/server/src/main/java/com/digital/video/gateway/Main.java" \
    "$SERVER_USER@$SERVER_IP:$SERVER_DIR/server/src/main/java/com/digital/video/gateway/"

sshpass -p "$SERVER_PASS" rsync -avz --progress \
    -e "ssh -o StrictHostKeyChecking=no" \
    "$LOCAL_DIR/server/src/main/java/com/digital/video/gateway/api/DeviceController.java" \
    "$SERVER_USER@$SERVER_IP:$SERVER_DIR/server/src/main/java/com/digital/video/gateway/api/"

sshpass -p "$SERVER_PASS" rsync -avz --progress \
    -e "ssh -o StrictHostKeyChecking=no" \
    "$LOCAL_DIR/server/src/main/java/com/digital/video/gateway/config/Config.java" \
    "$SERVER_USER@$SERVER_IP:$SERVER_DIR/server/src/main/java/com/digital/video/gateway/config/"

sshpass -p "$SERVER_PASS" rsync -avz --progress \
    -e "ssh -o StrictHostKeyChecking=no" \
    "$LOCAL_DIR/server/src/main/java/com/digital/video/gateway/database/Database.java" \
    "$LOCAL_DIR/server/src/main/java/com/digital/video/gateway/database/DevicePtzExtensionTable.java" \
    "$SERVER_USER@$SERVER_IP:$SERVER_DIR/server/src/main/java/com/digital/video/gateway/database/"

sshpass -p "$SERVER_PASS" rsync -avz --progress \
    -e "ssh -o StrictHostKeyChecking=no" \
    "$LOCAL_DIR/server/src/main/java/com/digital/video/gateway/device/DeviceSDK.java" \
    "$SERVER_USER@$SERVER_IP:$SERVER_DIR/server/src/main/java/com/digital/video/gateway/device/"

sshpass -p "$SERVER_PASS" rsync -avz --progress \
    -e "ssh -o StrictHostKeyChecking=no" \
    "$LOCAL_DIR/server/src/main/java/com/digital/video/gateway/hikvision/HikvisionSDK.java" \
    "$SERVER_USER@$SERVER_IP:$SERVER_DIR/server/src/main/java/com/digital/video/gateway/hikvision/"

sshpass -p "$SERVER_PASS" rsync -avz --progress \
    -e "ssh -o StrictHostKeyChecking=no" \
    "$LOCAL_DIR/server/src/main/java/com/digital/video/gateway/service/PtzMonitorService.java" \
    "$SERVER_USER@$SERVER_IP:$SERVER_DIR/server/src/main/java/com/digital/video/gateway/service/"

sshpass -p "$SERVER_PASS" rsync -avz --progress \
    -e "ssh -o StrictHostKeyChecking=no" \
    "$LOCAL_DIR/server/src/main/java/com/digital/video/gateway/tiandy/NvssdkLibrary.java" \
    "$LOCAL_DIR/server/src/main/java/com/digital/video/gateway/tiandy/TiandySDK.java" \
    "$LOCAL_DIR/server/src/main/java/com/digital/video/gateway/tiandy/TiandySDKStructure.java" \
    "$SERVER_USER@$SERVER_IP:$SERVER_DIR/server/src/main/java/com/digital/video/gateway/tiandy/"

sshpass -p "$SERVER_PASS" rsync -avz --progress \
    -e "ssh -o StrictHostKeyChecking=no" \
    "$LOCAL_DIR/server/src/main/resources/config.yaml" \
    "$SERVER_USER@$SERVER_IP:$SERVER_DIR/server/src/main/resources/"

if [ $? -ne 0 ]; then
    echo "❌ 文件同步失败"
    exit 1
fi
echo "✅ 文件同步完成"

# 步骤2: 杀掉旧进程
echo ""
echo "[步骤2] 检查并杀掉旧进程..."
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" << 'EOF'
cd /home/zyc/data/xwq/demo
echo "查找运行中的进程..."
PIDS=$(ps aux | grep -E 'java.*gateway|gateway.*jar' | grep -v grep | awk '{print $2}')
if [ -z "$PIDS" ]; then
    echo "没有找到运行中的进程"
else
    echo "找到进程: $PIDS"
    echo "$PIDS" | xargs kill -9 2>/dev/null
    sleep 2
    echo "✅ 旧进程已停止"
fi
EOF

if [ $? -ne 0 ]; then
    echo "⚠️  进程检查/停止可能有问题，继续执行..."
fi

# 步骤3: 重新编译
echo ""
echo "[步骤3] 在服务器上重新编译..."
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" << 'EOF'
cd /home/zyc/data/xwq/demo/server
echo "开始编译..."
mvn clean compile package -DskipTests 2>&1 | tail -50
if [ ${PIPESTATUS[0]} -eq 0 ]; then
    echo "✅ 编译成功"
    exit 0
else
    echo "❌ 编译失败"
    exit 1
fi
EOF

if [ $? -ne 0 ]; then
    echo "❌ 编译失败，请检查错误信息"
    exit 1
fi

# 步骤4: 启动服务
echo ""
echo "[步骤4] 启动服务..."
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" << 'EOF'
cd /home/zyc/data/xwq/demo/server
# 优先使用主jar文件（shaded版本），排除original、sources、javadoc
JAR_FILE=$(find target -name "video-gateway-service-*.jar" -not -name "*original*" -not -name "*sources*" -not -name "*javadoc*" 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
    # 如果找不到，尝试找所有jar（排除original、sources、javadoc）
    JAR_FILE=$(find target -name "*.jar" -not -name "*original*" -not -name "*sources*" -not -name "*javadoc*" 2>/dev/null | head -1)
fi

if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    echo "❌ 找不到jar文件"
    echo "当前目录: $(pwd)"
    ls -la target/*.jar 2>/dev/null || echo "target目录下没有jar文件"
    exit 1
fi

echo "启动服务: $JAR_FILE"
nohup java -jar "$JAR_FILE" > server.log 2>&1 &
sleep 3

# 检查进程是否启动
JAR_NAME=$(basename "$JAR_FILE")
if ps aux | grep -E "java.*$JAR_NAME" | grep -v grep > /dev/null; then
    echo "✅ 服务启动成功"
    echo "进程信息:"
    ps aux | grep -E "java.*$JAR_NAME" | grep -v grep
    echo ""
    echo "查看日志: tail -f server.log"
else
    echo "❌ 服务启动失败，查看日志:"
    tail -20 server.log
    exit 1
fi
EOF

if [ $? -eq 0 ]; then
    echo ""
    echo "=========================================="
    echo "✅ 部署完成！"
    echo "=========================================="
else
    echo ""
    echo "=========================================="
    echo "❌ 部署失败，请检查错误信息"
    echo "=========================================="
    exit 1
fi
