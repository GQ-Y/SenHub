#!/bin/bash

# Docker运行脚本
# 用于在Docker容器中运行SDK测试

set -e

echo "=== 海康威视NVR控制服务 - Docker运行脚本 ==="
echo ""

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker未运行，请先启动Docker"
    exit 1
fi

# 检查平台
ARCH=$(docker version --format '{{.Server.Arch}}')
echo "Docker架构: $ARCH"
echo ""

# 构建镜像
echo "📦 构建Docker镜像..."
docker build --platform linux/arm64 -t hikvision-nvr-service:latest .

if [ $? -ne 0 ]; then
    echo "❌ 镜像构建失败"
    exit 1
fi

echo "✅ 镜像构建成功"
echo ""

# 运行容器
echo "🚀 启动容器..."
docker run --rm -it \
    --platform linux/arm64 \
    -v "$(pwd):/app" \
    -v "$(pwd)/../sdk:/app/../sdk:ro" \
    -e LD_LIBRARY_PATH="/app/lib:/app/../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll:/app/../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll/HCNetSDKCom" \
    hikvision-nvr-service:latest \
    "$@"
