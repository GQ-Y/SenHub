#!/bin/bash

# Docker集成测试脚本
# 测试所有功能模块和摄像头连接

set -e

echo "=== 集成测试 - Docker环境 ==="
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

# 创建必要的目录
mkdir -p data logs sdkLog

echo "🧪 运行集成测试..."
echo "测试内容："
echo "  1. 配置加载"
echo "  2. SDK初始化"
echo "  3. 数据库初始化"
echo "  4. 设备管理器初始化"
echo "  5. 摄像头连接测试 (192.168.1.100:8000)"
echo "  6. MQTT连接测试 (mqtt.yingzhu.net:1883)"
echo "  7. 数据库操作测试"
echo ""

docker run --rm \
    --platform linux/arm64 \
    --network host \
    -v "$(pwd):/app" \
    -v "$(pwd)/../sdk:/app/../sdk:ro" \
    -v "$(pwd)/data:/app/data" \
    -v "$(pwd)/logs:/app/logs" \
    -v "$(pwd)/sdkLog:/app/sdkLog" \
    -e LD_LIBRARY_PATH="/app/lib:/app/../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll:/app/../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll/HCNetSDKCom" \
    hikvision-nvr-service:latest \
    bash -c "cd /app && mvn clean compile exec:java -Dexec.mainClass='com.hikvision.nvr.IntegrationTest'"

echo ""
echo "✅ 集成测试完成"
echo ""
echo "查看日志文件："
echo "  - 应用日志: logs/app.log"
echo "  - SDK日志: sdkLog/"
echo "  - 数据库: data/devices.db"
