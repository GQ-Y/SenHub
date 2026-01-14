#!/bin/bash

# macOS 测试运行脚本
# 用于测试 Livox 雷达连接和点云数据接收

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Livox Demo macOS 测试运行脚本"
echo "=========================================="

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "错误: 未找到 java，请先安装 JDK"
    exit 1
fi

# 检查 JAR 文件
JAR_FILE="target/livox-demo-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: 未找到 JAR 文件: $JAR_FILE"
    echo "请先运行: mvn clean package"
    exit 1
fi

# 检查动态库
LIB_DIR="lib/macos"
if [ ! -d "$LIB_DIR" ]; then
    echo "错误: 未找到动态库目录: $LIB_DIR"
    exit 1
fi

if [ ! -f "$LIB_DIR/liblivoxjni.dylib" ]; then
    echo "警告: 未找到 JNI 库: $LIB_DIR/liblivoxjni.dylib"
    echo "请确保已编译 JNI 库或从 artifacts 中复制"
fi

if [ ! -f "$LIB_DIR/liblivox_lidar_sdk_shared.dylib" ]; then
    echo "错误: 未找到 SDK 库: $LIB_DIR/liblivox_lidar_sdk_shared.dylib"
    exit 1
fi

# 检查配置文件
if [ ! -f "config.json" ]; then
    echo "错误: 未找到配置文件: config.json"
    exit 1
fi

# 设置动态库路径
export DYLD_LIBRARY_PATH="$SCRIPT_DIR/$LIB_DIR:$DYLD_LIBRARY_PATH"

echo ""
echo "配置信息:"
echo "  JAR 文件: $JAR_FILE"
echo "  库目录: $LIB_DIR"
echo "  DYLD_LIBRARY_PATH: $DYLD_LIBRARY_PATH"
echo ""

# 运行 Java 应用
echo "启动 Livox Demo..."
echo "提示: 按 Ctrl+C 退出程序"
echo ""

java --enable-native-access=ALL-UNNAMED \
     -Djava.library.path="$SCRIPT_DIR/$LIB_DIR" \
     -jar "$JAR_FILE"
