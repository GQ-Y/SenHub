#!/bin/bash
# 在远程服务器上执行摄像头核心功能测试（需先部署或同步代码）
# 测试项：登录、断网重连、高速抓拍、录像下载、云台控制、设备信息

SERVER_IP="192.168.1.210"
SERVER_USER="zyc"
SERVER_PASS="admin"
SERVER_DIR="/home/zyc/data/xwq/demo"
LOCAL_DIR="/Users/hook/Downloads/demo"

echo "=========================================="
echo "同步测试代码并在服务器上执行测试"
echo "=========================================="

# 1. 同步测试相关代码
echo ""
echo "[1] 同步测试代码到服务器..."
sshpass -p "$SERVER_PASS" rsync -avz --progress \
    -e "ssh -o StrictHostKeyChecking=no" \
    "$LOCAL_DIR/server/src/main/java/com/digital/video/gateway/test/" \
    "$SERVER_USER@$SERVER_IP:$SERVER_DIR/server/src/main/java/com/digital/video/gateway/test/"

echo "✅ 测试代码同步完成（请确保已通过 deploy_to_server.sh 同步过完整工程或服务器上已有最新代码）"

# 2. 在服务器上先 ping 摄像头 IP，确保网络可达
echo ""
echo "[2] 在服务器上检查与摄像头的连通性..."
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" << 'PINGCHECK'
echo "Ping 天地伟业 192.168.1.10..."
ping -c 2 -W 3 192.168.1.10 && echo "  ✓ 192.168.1.10 可达" || echo "  ✗ 192.168.1.10 不可达"
echo "Ping 天地伟业 192.168.1.200..."
ping -c 2 -W 3 192.168.1.200 && echo "  ✓ 192.168.1.200 可达" || echo "  ✗ 192.168.1.200 不可达"
echo "Ping 海康 192.168.1.100..."
ping -c 2 -W 3 192.168.1.100 && echo "  ✓ 192.168.1.100 可达" || echo "  ✗ 192.168.1.100 不可达"
PINGCHECK

# 3. 在服务器上编译并运行测试
echo ""
echo "[3] 在服务器上编译并运行测试..."
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" << 'REMOTE'
cd /home/zyc/data/xwq/demo/server
echo "编译中 (mvn package -DskipTests)..."
mvn clean compile package -DskipTests -q 2>&1 | tail -5
if [ ${PIPESTATUS[0]} -ne 0 ]; then
  echo "❌ 编译失败"
  exit 1
fi
JAR=$(find target -name "video-gateway-service-*.jar" -not -name "*original*" -not -name "*sources*" -not -name "*javadoc*" 2>/dev/null | head -1)
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
  echo "❌ 未找到 jar 文件"
  exit 1
fi
export LD_LIBRARY_PATH="$(pwd)/lib/linux:${LD_LIBRARY_PATH}"
echo "运行测试: CameraTestRunner"
java -Djava.library.path="$(pwd)/lib/linux" -cp "$JAR" com.digital.video.gateway.test.CameraTestRunner 2>&1 | tee test-run.log
EXIT=${PIPESTATUS[0]}
if [ "$EXIT" -eq 0 ]; then
  echo ""
  echo "✅ 测试全部通过"
else
  echo ""
  echo "❌ 测试存在失败，详见上方输出或 test-run.log"
fi
exit $EXIT
REMOTE

EXIT=$?
echo ""
echo "=========================================="
if [ $EXIT -eq 0 ]; then
  echo "✅ 服务器端测试执行完成且通过"
else
  echo "❌ 服务器端测试存在失败 (exit=$EXIT)"
fi
echo "=========================================="
exit $EXIT
