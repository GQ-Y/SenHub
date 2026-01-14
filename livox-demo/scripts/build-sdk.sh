#!/bin/bash

# 编译 Livox SDK2
# 平台无关脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

echo "=========================================="
echo "编译 Livox SDK2"
echo "=========================================="

SDK_DIR="livox-sdk2-official"
if [ ! -d "$SDK_DIR" ]; then
    echo "错误: 未找到 Livox SDK2 目录: $SDK_DIR"
    exit 1
fi

cd "$SDK_DIR"
mkdir -p build
cd build

echo "运行 CMake..."
cmake .. 2>&1 | tail -10

echo ""
echo "编译 SDK..."
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    make -j$(sysctl -n hw.ncpu)
else
    # Linux
    make -j$(nproc)
fi

echo ""
echo "SDK 编译完成！"
cd ../..
