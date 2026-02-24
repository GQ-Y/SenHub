#!/bin/bash
# 检查服务器后端服务状态、查看日志并重启
# 使用方式: ./check-and-restart-server.sh
# 若本机无法直连 192.168.1.210，可在能 SSH 到该机的环境执行，或把下方 SSH 块改成在服务器上直接执行

SERVER_IP="${SERVER_IP:-192.168.1.210}"
SERVER_USER="${SERVER_USER:-zyc}"
SERVER_PASS="${SERVER_PASS:-admin}"
SERVER_DIR="/home/zyc/data/xwq/demo/server"

run_remote() {
  sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$SERVER_USER@$SERVER_IP" "$@"
}

echo "=========================================="
echo "检查服务器后端: $SERVER_USER@$SERVER_IP"
echo "=========================================="

echo ""
echo "[1] 进程状态"
run_remote "ps aux | grep -E 'java.*gateway|gateway.*jar|video-gateway' | grep -v grep" || true

echo ""
echo "[2] server.log 最后 60 行"
run_remote "tail -60 $SERVER_DIR/server.log 2>/dev/null || echo '无 server.log'"

echo ""
echo "[3] logs/app.log 最后 60 行"
run_remote "tail -60 $SERVER_DIR/logs/app.log 2>/dev/null || echo '无 logs/app.log'"

echo ""
echo "[4] 重启服务（杀旧进程并启动）"
run_remote "cd $SERVER_DIR && \
  PIDS=\$(ps aux | grep -E 'java.*gateway|gateway.*jar' | grep -v grep | awk '{print \$2}'); \
  if [ -n \"\$PIDS\" ]; then echo \"停止旧进程: \$PIDS\"; echo \"\$PIDS\" | xargs kill -9 2>/dev/null; sleep 2; fi; \
  JAR_FILE=\$(find target -name 'video-gateway-service-*.jar' -not -name '*original*' -not -name '*sources*' -not -name '*javadoc*' 2>/dev/null | head -1); \
  if [ -z \"\$JAR_FILE\" ]; then JAR_FILE=\$(find target -name '*.jar' -not -name '*original*' -not -name '*sources*' -not -name '*javadoc*' 2>/dev/null | head -1); fi; \
  if [ -z \"\$JAR_FILE\" ] || [ ! -f \"\$JAR_FILE\" ]; then echo '❌ 未找到 jar，请先在服务器上编译: mvn clean package -DskipTests'; exit 1; fi; \
  TIANDY_LIB=\$(pwd)/lib/x86/tiandy; TIANDY_LIB_SUB=\$TIANDY_LIB/lib; \
  if [ -d \"\$TIANDY_LIB\" ]; then export LD_LIBRARY_PATH=\"\$TIANDY_LIB:\$TIANDY_LIB_SUB:\$(pwd)/lib/linux:\${LD_LIBRARY_PATH}\"; export JAVA_LIB_PATH=\"\$TIANDY_LIB:\$TIANDY_LIB_SUB:\$(pwd)/lib/linux\"; else export LD_LIBRARY_PATH=\"\$(pwd)/lib/linux:\${LD_LIBRARY_PATH}\"; export JAVA_LIB_PATH=\"\$(pwd)/lib/linux\"; fi; \
  nohup java -Djava.library.path=\"\$JAVA_LIB_PATH\" -jar \"\$JAR_FILE\" > server.log 2>&1 & \
  echo \"已启动: \$JAR_FILE\"; sleep 3; \
  ps aux | grep -E \"java.*\$(basename \$JAR_FILE)\" | grep -v grep || (echo '启动可能失败，查看: tail -30 server.log'; tail -30 server.log)"

echo ""
echo "=========================================="
echo "完成。查看实时日志: ssh $SERVER_USER@$SERVER_IP 'tail -f $SERVER_DIR/server.log'"
echo "或: ssh $SERVER_USER@$SERVER_IP 'tail -f $SERVER_DIR/logs/app.log'"
echo "=========================================="
