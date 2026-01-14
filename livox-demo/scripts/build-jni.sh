#!/bin/bash

# 编译 JNI 动态库
# 根据平台生成 .so 或 .dylib

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

echo "=========================================="
echo "编译 JNI 动态库"
echo "=========================================="

# 检测平台
if [[ "$OSTYPE" == "darwin"* ]]; then
    PLATFORM="macos"
    LIB_EXT=".dylib"
    CPU_COUNT=$(sysctl -n hw.ncpu)
else
    PLATFORM="linux"
    LIB_EXT=".so"
    CPU_COUNT=$(nproc)
fi

echo "平台: $PLATFORM"
echo "库扩展名: $LIB_EXT"

# 创建 lib 目录
mkdir -p lib/$PLATFORM

# 复制 SDK 库
SDK_LIB="livox-sdk2-official/build/sdk_core/liblivox_lidar_sdk_shared$LIB_EXT"
if [ -f "$SDK_LIB" ]; then
    cp "$SDK_LIB" lib/$PLATFORM/
    echo "已复制 SDK 库到 lib/$PLATFORM/"
else
    echo "错误: 未找到 SDK 库: $SDK_LIB"
    echo "请先编译 Livox SDK2"
    exit 1
fi

# 编译 JNI
cd jni
mkdir -p build
cd build

echo ""
echo "运行 CMake..."
cmake .. 2>&1 | tail -10

echo ""
echo "编译 JNI 库..."
make -j$CPU_COUNT 2>&1 | tail -10

# 移动 JNI 库到 lib 目录
JNI_LIB="../../lib/liblivoxjni$LIB_EXT"
if [ -f "$JNI_LIB" ]; then
    mv "$JNI_LIB" "../../lib/$PLATFORM/"
    echo ""
    echo "JNI 编译成功！"
    ls -lh "../../lib/$PLATFORM/"
else
    echo "错误: JNI 编译失败"
    exit 1
fi

cd ../..
