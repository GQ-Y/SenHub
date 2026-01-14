#!/bin/bash

# Livox Demo 编译脚本
# 用于在远程 Ubuntu 服务器上编译 JNI 动态库

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Livox Demo JNI 编译脚本"
echo "=========================================="

# 检查依赖
echo "检查依赖..."
if ! command -v cmake &> /dev/null; then
    echo "错误: 未找到 cmake，请先安装: sudo apt install cmake"
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo "错误: 未找到 java，请先安装 JDK"
    exit 1
fi

# 检查 Livox SDK2 是否已编译
SDK_DIR="../livox-sdk2-official"
if [ ! -d "$SDK_DIR" ]; then
    echo "错误: 未找到 Livox SDK2 目录: $SDK_DIR"
    echo "请先克隆: git clone https://github.com/Livox-SDK/Livox-SDK2.git livox-sdk2-official"
    exit 1
fi

# 编译 Livox SDK2（如果尚未编译）
SDK_BUILD_DIR="$SDK_DIR/build"
if [ ! -f "$SDK_BUILD_DIR/liblivox_lidar_sdk_shared.so" ]; then
    echo "编译 Livox SDK2..."
    cd "$SDK_DIR"
    mkdir -p build
    cd build
    cmake .. && make -j$(nproc)
    sudo make install
    cd "$SCRIPT_DIR"
fi

# 创建 lib 目录
mkdir -p lib

# 编译 JNI 库
echo "编译 JNI 动态库..."
JNI_BUILD_DIR="jni/build"
mkdir -p "$JNI_BUILD_DIR"
cd "$JNI_BUILD_DIR"
cmake ../.. && make -j$(nproc)
cd "$SCRIPT_DIR"

echo "=========================================="
echo "编译完成！"
echo "动态库位置: lib/liblivoxjni.so"
echo "=========================================="
