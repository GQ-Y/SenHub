#!/bin/bash

# 部署脚本：将项目部署到远程服务器并编译运行

set -e

REMOTE_HOST="192.168.1.210"
REMOTE_USER="zyc"
REMOTE_PASS="admin"
REMOTE_DIR="/home/zyc/data/xwq/livox-demo"

echo "=========================================="
echo "Livox Demo 部署脚本"
echo "=========================================="

# 检查 SSH 连接
echo "检查 SSH 连接..."
if ! sshpass -p "$REMOTE_PASS" ssh -o StrictHostKeyChecking=no "$REMOTE_USER@$REMOTE_HOST" "echo 'SSH连接成功'" 2>/dev/null; then
    echo "错误: 无法连接到远程服务器"
    echo "请确保已安装 sshpass: sudo apt install sshpass"
    exit 1
fi

# 创建远程目录
echo "创建远程目录..."
sshpass -p "$REMOTE_PASS" ssh "$REMOTE_USER@$REMOTE_HOST" "mkdir -p $REMOTE_DIR"

# 同步文件到远程服务器
echo "同步文件到远程服务器..."
sshpass -p "$REMOTE_PASS" rsync -avz --exclude 'target' --exclude '.git' \
    ./ "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR/"

# 在远程服务器上执行编译
echo "在远程服务器上编译..."
sshpass -p "$REMOTE_PASS" ssh "$REMOTE_USER@$REMOTE_HOST" << 'ENDSSH'
cd /home/zyc/data/xwq/livox-demo

# 停止旧进程
pkill -f "com.livox.demo.Main" || true
sleep 1

# 编译 SDK 和 JNI
echo "编译 Livox SDK 和 JNI..."
bash build.sh

# 编译 Java 项目
echo "编译 Java 项目..."
mvn clean package -DskipTests

echo "编译完成！"
ENDSSH

echo "=========================================="
echo "部署完成！"
echo "=========================================="
echo ""
echo "在远程服务器上运行:"
echo "  cd $REMOTE_DIR"
echo "  java -Djava.library.path=./lib -jar target/livox-demo-1.0.0.jar"
