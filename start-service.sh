#!/bin/bash

# 启动服务器上的服务

REMOTE_HOST="192.168.1.210"
REMOTE_USER="zyc"
REMOTE_PASS="admin"
REMOTE_SERVER_DIR="/home/zyc/data/xwq/demo/server"

echo "=========================================="
echo "启动服务"
echo "=========================================="

sshpass -p "$REMOTE_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "$REMOTE_USER@$REMOTE_HOST" << 'ENDSSH'
    cd /home/zyc/data/xwq/demo/server
    
    echo ""
    echo "步骤1: 检查并杀掉旧进程..."
    OLD_PIDS=$(ps aux | grep -E "video-gateway|com.digital.video.gateway.Main" | grep -v grep | awk '{print $2}')
    if [ -n "$OLD_PIDS" ]; then
        echo "找到旧进程: $OLD_PIDS"
        echo "$OLD_PIDS" | xargs kill -9 2>/dev/null || true
        sleep 2
        echo "旧进程已停止"
    else
        echo "未找到运行中的进程"
    fi
    
    echo ""
    echo "步骤2: 检查jar文件..."
    if [ ! -f target/video-gateway-service-1.0.0.jar ]; then
        echo "错误: jar文件不存在，请先编译项目"
        exit 1
    fi
    echo "找到jar文件: target/video-gateway-service-1.0.0.jar"
    ls -lh target/video-gateway-service-1.0.0.jar
    
    echo ""
    echo "步骤3: 启动服务..."
    # 设置LD_LIBRARY_PATH，确保能找到Livox依赖库
    export LD_LIBRARY_PATH=/home/zyc/data/xwq/demo/server/lib/linux:$LD_LIBRARY_PATH
    export JAVA_LIBRARY_PATH=/home/zyc/data/xwq/demo/server/lib/linux
    nohup java -Djava.library.path=/home/zyc/data/xwq/demo/server/lib/linux -jar target/video-gateway-service-1.0.0.jar > /dev/null 2>&1 &
    echo "服务启动命令已执行（已设置库路径）"
    
    echo ""
    echo "步骤4: 等待服务启动..."
    sleep 5
    
    echo ""
    echo "步骤5: 检查进程状态..."
    NEW_PIDS=$(ps aux | grep -E "video-gateway|com.digital.video.gateway.Main" | grep -v grep | awk '{print $2}')
    if [ -n "$NEW_PIDS" ]; then
        echo "✓ 服务已启动，进程ID: $NEW_PIDS"
        ps aux | grep -E "video-gateway|com.digital.video.gateway.Main" | grep -v grep
    else
        echo "✗ 服务启动失败，未找到进程"
    fi
    
    echo ""
    echo "步骤6: 查看启动日志..."
    if [ -f logs/app.log ]; then
        echo "=== 最新日志（最后50行） ==="
        tail -n 50 logs/app.log
    else
        echo "日志文件尚未创建，等待3秒..."
        sleep 3
        if [ -f logs/app.log ]; then
            echo "=== 最新日志（最后50行） ==="
            tail -n 50 logs/app.log
        else
            echo "警告: 日志文件仍未创建，请检查服务是否正常启动"
        fi
    fi
ENDSSH

echo ""
echo "=========================================="
echo "启动完成"
echo "=========================================="
