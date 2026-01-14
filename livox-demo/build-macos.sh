#!/bin/bash

# macOS M1 ARM64 编译脚本
# 在本地 macOS 上执行此脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Livox Demo macOS M1 ARM64 编译脚本"
echo "=========================================="

# 检查系统
if [[ "$(uname)" != "Darwin" ]]; then
    echo "错误: 此脚本只能在 macOS 上运行"
    exit 1
fi

# 检查架构
ARCH=$(uname -m)
if [[ "$ARCH" != "arm64" ]]; then
    echo "警告: 当前架构是 $ARCH，不是 arm64 (M1)"
    read -p "是否继续？(y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# 检查依赖
echo "检查依赖..."
if ! command -v cmake &> /dev/null; then
    echo "错误: 未找到 cmake，请先安装: brew install cmake"
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
if [ ! -f "$SDK_BUILD_DIR/sdk_core/liblivox_lidar_sdk_shared.dylib" ]; then
    echo "编译 Livox SDK2..."
    cd "$SDK_DIR"
    mkdir -p build
    cd build
    cmake .. && make -j$(sysctl -n hw.ncpu)
    echo "SDK 编译完成"
    cd "$SCRIPT_DIR"
else
    echo "SDK 已编译，跳过"
fi

# 创建 lib/macos 目录
mkdir -p lib/macos

# 复制 SDK 库到 lib/macos
if [ -f "$SDK_BUILD_DIR/sdk_core/liblivox_lidar_sdk_shared.dylib" ]; then
    cp "$SDK_BUILD_DIR/sdk_core/liblivox_lidar_sdk_shared.dylib" lib/macos/
    echo "已复制 SDK 库到 lib/macos/"
fi

# 编译 JNI 库
echo "编译 JNI 动态库..."
cd jni
if [ ! -d "build" ]; then
    mkdir build
fi
cd build
rm -rf *
cmake .. 2>&1 | tail -10

echo ""
echo "编译 JNI 库..."
make -j$(sysctl -n hw.ncpu) 2>&1 | tail -10

# 检查是否编译成功
if [ -f "../../lib/liblivoxjni.dylib" ]; then
    echo "JNI 编译成功！"
    # 移动到 macos 目录
    mv ../../lib/liblivoxjni.dylib ../../lib/macos/ 2>/dev/null || true
    ls -lh ../../lib/macos/*.dylib
else
    echo "JNI 编译失败，检查错误信息"
    exit 1
fi

cd "$SCRIPT_DIR"

echo ""
echo "=========================================="
echo "编译完成！"
echo "动态库位置: lib/macos/"
echo "=========================================="
