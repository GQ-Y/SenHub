#!/bin/bash

# Docker开发环境启动脚本
# 用于在容器中进行Java开发、编译和测试

set -e

echo "=== Docker开发环境 ==="
echo ""

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker未运行，请先启动Docker"
    exit 1
fi

# 构建镜像（如果需要）
if ! docker images | grep -q "video-gateway-service"; then
    echo "📦 构建Docker镜像..."
    docker build --platform linux/arm64 -t video-gateway-service:latest .
    echo ""
fi

# 创建必要的目录
mkdir -p data logs sdkLog

echo "🚀 启动开发容器..."
echo ""
echo "容器已配置："
echo "  - 整个server目录已挂载到 /app"
echo "  - SDK目录已挂载到 /app/../sdk"
echo "  - 数据目录已挂载：data, logs, sdkLog"
echo "  - Maven仓库已持久化"
echo ""
echo "常用命令："
echo "  mvn clean compile          # 编译项目"
echo "  mvn exec:java -Dexec.mainClass='com.digital.video.gateway.IntegrationTest'  # 运行集成测试"
echo "  mvn exec:java -Dexec.mainClass='com.digital.video.gateway.Main'  # 运行主程序"
echo "  mvn clean package          # 打包项目"
echo ""

# 启动容器（如果已存在则直接进入）
if docker ps -a | grep -q "video-gateway-service"; then
    if docker ps | grep -q "video-gateway-service"; then
        echo "容器已在运行，直接进入..."
        docker exec -it video-gateway-service /bin/bash
    else
        echo "启动已存在的容器..."
        docker start video-gateway-service
        docker exec -it video-gateway-service /bin/bash
    fi
else
    echo "创建新容器..."
    docker run -it --rm \
        --platform linux/arm64 \
        --network host \
        --name video-gateway-service \
        -v "$(pwd):/app" \
        -v "$(pwd)/../sdk:/app/../sdk:ro" \
        -v "$(pwd)/data:/app/data" \
        -v "$(pwd)/logs:/app/logs" \
        -v "$(pwd)/sdkLog:/app/sdkLog" \
        -v "$(pwd)/storage:/app/storage" \
        -v maven-repo:/root/.m2 \
        -e JAVA_HOME=/usr/lib/jvm/java-11-openjdk-arm64 \
        -e MAVEN_HOME=/opt/maven \
        -e LD_LIBRARY_PATH="/app/lib:/app/lib/hikvision:/app/lib/hikvision/HCNetSDKCom:/app/lib/tiandy:/app/lib/dahua" \
        -w /app \
        video-gateway-service:latest \
        /bin/bash
fi
