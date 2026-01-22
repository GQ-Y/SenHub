#!/bin/bash

# 简化的部署脚本 - 使用rsync同步并执行远程命令

REMOTE_HOST="192.168.1.210"
REMOTE_USER="zyc"
REMOTE_PASS="admin"
REMOTE_BASE_DIR="/home/zyc/data/xwq/demo"
REMOTE_SERVER_DIR="$REMOTE_BASE_DIR/server"

echo "=========================================="
echo "部署 server 服务到远程服务器"
echo "=========================================="

# 步骤1: 杀掉进程
echo ""
echo "步骤1: 杀掉旧进程..."
sshpass -p "$REMOTE_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$REMOTE_USER@$REMOTE_HOST" "pkill -f 'video-gateway' || pkill -f 'com.digital.video.gateway.Main' || true; sleep 1"

# 步骤2: 删除server目录
echo ""
echo "步骤2: 删除server目录..."
sshpass -p "$REMOTE_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$REMOTE_USER@$REMOTE_HOST" "rm -rf $REMOTE_SERVER_DIR; mkdir -p $REMOTE_BASE_DIR"

# 步骤3: 同步代码
echo ""
echo "步骤3: 同步server目录..."
sshpass -p "$REMOTE_PASS" rsync -avz --delete --exclude 'target' --exclude '.git' --exclude '*.class' --exclude 'logs' --exclude 'sdkLog' --exclude 'data' --exclude 'storage' --exclude 'captures' --exclude 'downloads' --exclude 'records' ./server/ "$REMOTE_USER@$REMOTE_HOST:$REMOTE_SERVER_DIR/"

# 步骤4: 编译
echo ""
echo "步骤4: 编译..."
sshpass -p "$REMOTE_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$REMOTE_USER@$REMOTE_HOST" "cd $REMOTE_SERVER_DIR && mvn clean package -DskipTests"

# 步骤5: 启动
echo ""
echo "步骤5: 启动服务..."
sshpass -p "$REMOTE_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$REMOTE_USER@$REMOTE_HOST" "cd $REMOTE_SERVER_DIR && nohup java -jar target/video-gateway-service-1.0.0.jar > /dev/null 2>&1 & sleep 5 && echo '检查进程状态:' && ps aux | grep -E 'video-gateway|com.digital.video.gateway.Main' | grep -v grep || echo '未找到进程' && echo '' && echo '查看启动日志（使用系统日志配置）:' && if [ -f logs/app.log ]; then tail -n 30 logs/app.log; else echo '日志文件尚未创建，服务可能正在启动中...'; fi"

echo ""
echo "部署完成！"
