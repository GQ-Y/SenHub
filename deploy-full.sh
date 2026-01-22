#!/bin/bash

# 完整部署脚本：杀掉进程、清空目录、同步、编译、启动

REMOTE_HOST="192.168.1.210"
REMOTE_USER="zyc"
REMOTE_PASS="admin"
REMOTE_BASE_DIR="/home/zyc/data/xwq"
REMOTE_DEMO_DIR="$REMOTE_BASE_DIR/demo"
REMOTE_SERVER_DIR="$REMOTE_DEMO_DIR/server"

echo "=========================================="
echo "完整部署流程"
echo "=========================================="

# 步骤1: 杀掉旧进程
echo ""
echo "步骤1: 杀掉旧进程..."
sshpass -p "$REMOTE_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$REMOTE_USER@$REMOTE_HOST" "pkill -f 'video-gateway' || pkill -f 'com.digital.video.gateway.Main' || true; sleep 2; echo '进程已清理'"

# 步骤2: 清空xwq目录下的所有文件
echo ""
echo "步骤2: 清空xwq目录下的所有文件..."
sshpass -p "$REMOTE_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$REMOTE_USER@$REMOTE_HOST" "rm -rf $REMOTE_BASE_DIR/*; mkdir -p $REMOTE_DEMO_DIR; echo 'xwq目录已清空'"

# 步骤3: 同步server目录文件
echo ""
echo "步骤3: 同步server目录文件..."
sshpass -p "$REMOTE_PASS" rsync -avz --delete \
    --exclude 'target' \
    --exclude '.git' \
    --exclude '*.class' \
    --exclude 'logs' \
    --exclude 'sdkLog' \
    --exclude 'data' \
    --exclude 'storage' \
    --exclude 'captures' \
    --exclude 'downloads' \
    --exclude 'records' \
    ./server/ "$REMOTE_USER@$REMOTE_HOST:$REMOTE_SERVER_DIR/"

echo "代码同步完成"

# 步骤4: 编译
echo ""
echo "步骤4: 编译项目..."
sshpass -p "$REMOTE_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$REMOTE_USER@$REMOTE_HOST" "cd $REMOTE_SERVER_DIR && echo '开始Maven编译...' && mvn clean package -DskipTests"

if [ $? -eq 0 ]; then
    echo "编译成功！"
else
    echo "编译失败！"
    exit 1
fi

# 步骤5: 启动服务
echo ""
echo "步骤5: 启动服务..."
sshpass -p "$REMOTE_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$REMOTE_USER@$REMOTE_HOST" << 'ENDSSH'
    cd /home/zyc/data/xwq/demo/server
    echo "启动服务..."
    # 设置LD_LIBRARY_PATH，确保能找到Livox依赖库
    export LD_LIBRARY_PATH=/home/zyc/data/xwq/demo/server/lib/linux:$LD_LIBRARY_PATH
    nohup java -Djava.library.path=/home/zyc/data/xwq/demo/server/lib/linux -jar target/video-gateway-service-1.0.0.jar > /dev/null 2>&1 &
    sleep 5
    echo ""
    echo "检查进程状态:"
    ps aux | grep -E "video-gateway|com.digital.video.gateway.Main" | grep -v grep || echo "未找到进程"
    echo ""
    echo "查看启动日志（使用系统日志配置）:"
    if [ -f logs/app.log ]; then
        tail -n 40 logs/app.log
    else
        echo "日志文件尚未创建，服务可能正在启动中..."
        echo "等待3秒后再次检查..."
        sleep 3
        if [ -f logs/app.log ]; then
            tail -n 40 logs/app.log
        else
            echo "日志文件仍未创建，请手动检查服务状态"
        fi
    fi
ENDSSH

echo ""
echo "=========================================="
echo "部署完成！"
echo "=========================================="
echo ""
echo "查看实时日志:"
echo "  sshpass -p admin ssh zyc@192.168.1.210 'tail -f /home/zyc/data/xwq/demo/server/logs/app.log'"
echo ""
echo "检查服务状态:"
echo "  sshpass -p admin ssh zyc@192.168.1.210 'ps aux | grep video-gateway'"
