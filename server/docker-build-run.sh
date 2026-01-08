#!/bin/bash

# Docker编译和运行脚本
# 用于在Docker容器中编译、运行服务并进行API测试

set -e

echo "=== 综合性数字视频监控网关系统 - Docker编译运行脚本 ==="
echo ""

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker未运行，请先启动Docker"
    exit 1
fi

# 切换到server目录
cd "$(dirname "$0")"

# 停止并删除旧容器（如果存在）
echo "🛑 停止旧容器..."
docker-compose down 2>/dev/null || true

# 构建镜像
echo "📦 构建Docker镜像..."
docker-compose build

if [ $? -ne 0 ]; then
    echo "❌ 镜像构建失败"
    exit 1
fi

echo "✅ 镜像构建成功"
echo ""

# 编译项目
echo "🔨 编译项目..."
docker-compose run --rm video-gateway-service mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功"
echo ""

# 启动服务（后台运行）
echo "🚀 启动服务（后台运行）..."
docker-compose up -d

# 等待服务启动
echo "⏳ 等待服务启动..."
sleep 5

# 检查服务是否运行
if docker-compose ps | grep -q "Up"; then
    echo "✅ 服务已启动"
    echo ""
    echo "📋 服务信息："
    echo "   - HTTP API: http://localhost:8080"
    echo "   - 容器名称: video-gateway-service"
    echo ""
    echo "📝 查看日志: docker-compose logs -f"
    echo "🛑 停止服务: docker-compose down"
    echo ""
else
    echo "❌ 服务启动失败"
    echo "查看日志: docker-compose logs"
    exit 1
fi
