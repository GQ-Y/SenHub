#!/bin/bash

# Docker测试脚本
# 在Docker容器中运行SDK测试

set -e

echo "=== SDK集成测试 - Docker环境 ==="
echo ""

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker未运行，请先启动Docker"
    exit 1
fi

# 构建镜像（如果需要）
if ! docker images | grep -q "hikvision-nvr-service"; then
    echo "📦 构建Docker镜像..."
    docker build --platform linux/arm64 -t hikvision-nvr-service:latest .
    echo ""
fi

# 运行测试
echo "🧪 运行SDK测试..."
docker run --rm \
    --platform linux/arm64 \
    -v "$(pwd):/app" \
    -v "$(pwd)/../sdk:/app/../sdk:ro" \
    -e LD_LIBRARY_PATH="/app/lib:/app/../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll:/app/../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll/HCNetSDKCom" \
    hikvision-nvr-service:latest \
    bash -c "cd /app && mvn clean compile exec:java -Dexec.mainClass='com.hikvision.nvr.hikvision.SDKTest'"
