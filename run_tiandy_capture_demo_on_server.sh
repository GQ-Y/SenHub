#!/bin/bash
# 天地伟业抓图 Demo：本地编译 -> 上传 jar -> 在服务器上运行
# 完全参照官方示例流程，每 60 秒抓一张图，日志打印完整流程。
# 用法: ./run_tiandy_capture_demo_on_server.sh [ip] [port] [user] [password]

set -e
SERVER_IP="192.168.1.210"
SERVER_USER="zyc"
SERVER_PASS="admin"
# 服务器上项目目录（与 deploy_to_server 一致）
SERVER_DIR="/home/zyc/data/xwq/demo"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_PROJECT_DIR="$SERVER_DIR/server"

# 设备写死：192.168.1.10 admin / zyckj2021
IP="${1:-192.168.1.10}"
PORT="${2:-3000}"
USER="${3:-admin}"
PASS="${4:-zyckj2021}"

echo "=========================================="
echo "天地伟业抓图 Demo - 编译并运行到服务器"
echo "=========================================="
echo "设备: $IP:$PORT 用户: $USER"
echo ""

# 步骤1: 本地编译（生成含 TiandyCaptureDemo 的 jar）
echo "[1/4] 本地编译 server (mvn package)..."
cd "$SCRIPT_DIR/server"
mvn -q package -DskipTests -Dmaven.test.skip=true 2>/dev/null || mvn package -DskipTests -Dmaven.test.skip=true
JAR=$(ls -t target/video-gateway-service-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "错误: 未找到 target/video-gateway-service-*.jar"
  exit 1
fi
echo "     jar: $JAR"
echo ""

# 步骤2: 上传 jar 到服务器
echo "[2/4] 上传 jar 到服务器..."
sshpass -p "$SERVER_PASS" scp -o StrictHostKeyChecking=no "$JAR" "$SERVER_USER@$SERVER_IP:$SERVER_PROJECT_DIR/target/"
echo "     已上传到 $SERVER_PROJECT_DIR/target/"
echo ""

# 步骤3: 确定服务器上的库路径（与现有部署一致）
echo "[3/4] 在服务器上运行 Demo..."
# 服务器上可能为 lib/x86/tiandy 或 lib/linux，优先 x86/tiandy
RUN_CMD="cd $SERVER_PROJECT_DIR && LIB_DIR=\$(pwd)/lib/x86/tiandy; [ ! -d \"\$LIB_DIR\" ] && LIB_DIR=\$(pwd)/lib/linux; [ ! -d \"\$LIB_DIR\" ] && LIB_DIR=\$(pwd)/lib; export LD_LIBRARY_PATH=\"\$LIB_DIR:\$LD_LIBRARY_PATH\"; java -Dtiandy.lib.path=\"\$LIB_DIR\" -Djava.library.path=\"\$LIB_DIR\" -cp target/$(basename "$JAR") com.digital.video.gateway.tiandy.TiandyCaptureDemo $IP $PORT $USER $PASS"

# 步骤4: SSH 执行（前台运行，便于看日志；Ctrl+C 结束）
echo "[4/4] 启动 Demo（每 60 秒抓一张图，Ctrl+C 结束）..."
echo "------------------------------------------"
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" "$RUN_CMD"
