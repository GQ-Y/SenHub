#!/bin/bash
# 检查服务器上的 Livox SDK 官方包和库文件

SERVER_IP="192.168.1.210"
SERVER_USER="zyc"
SERVER_PASS="admin"

echo "=========================================="
echo "检查服务器上的 Livox SDK"
echo "=========================================="

sshpass -p "$SERVER_PASS" ssh -T -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" << 'EOF'
cd /home/zyc/data/xwq

echo ""
echo "1. 检查 Livox SDK 官方包位置..."
SDK_PATHS=(
    "/home/zyc/data/xwq/demo/livox-sdk2-official"
    "/home/zyc/data/xwq/livox-demo/livox-sdk2-official"
    "/home/zyc/data/xwq/demo/server/../livox-sdk2-official"
)

FOUND_SDK=""
for SDK_PATH in "${SDK_PATHS[@]}"; do
    if [ -d "$SDK_PATH" ]; then
        echo "✓ 找到 SDK 包: $SDK_PATH"
        FOUND_SDK="$SDK_PATH"
        ls -ld "$SDK_PATH"
        break
    fi
done

if [ -z "$FOUND_SDK" ]; then
    echo "⚠️  未找到 Livox SDK 官方包，搜索其他位置..."
    FOUND=$(find /home/zyc/data/xwq -type d -name "livox-sdk2-official" 2>/dev/null | head -1)
    if [ -n "$FOUND" ]; then
        echo "✓ 找到 SDK 包: $FOUND"
        FOUND_SDK="$FOUND"
        ls -ld "$FOUND"
    else
        echo "✗ 未找到 Livox SDK 官方包"
    fi
fi

if [ -n "$FOUND_SDK" ]; then
    echo ""
    echo "2. 在 SDK 包中查找库文件..."
    echo "搜索 liblivox_lidar_sdk_shared.so..."
    SHARED_LIBS=$(find "$FOUND_SDK" -name "liblivox_lidar_sdk_shared.so" 2>/dev/null)
    if [ -n "$SHARED_LIBS" ]; then
        echo "✓ 找到依赖库文件:"
        echo "$SHARED_LIBS" | while read lib; do
            echo "  - $lib"
            ls -lh "$lib"
            file "$lib" 2>/dev/null || true
        done
    else
        echo "✗ 在 SDK 包中未找到 liblivox_lidar_sdk_shared.so"
        echo "   搜索位置: $FOUND_SDK"
        echo "   尝试查找其他可能的库文件..."
        find "$FOUND_SDK" -name "*.so" -type f 2>/dev/null | head -10
    fi
    
    echo ""
    echo "搜索 liblivoxjni.so..."
    JNI_LIBS=$(find "$FOUND_SDK" -name "liblivoxjni.so" 2>/dev/null)
    if [ -n "$JNI_LIBS" ]; then
        echo "✓ 找到 JNI 库文件:"
        echo "$JNI_LIBS" | while read lib; do
            echo "  - $lib"
            ls -lh "$lib"
        done
    else
        echo "⚠️  在 SDK 包中未找到 liblivoxjni.so（这是需要编译的）"
    fi
fi

echo ""
echo "3. 检查 server/lib/linux/ 目录..."
cd /home/zyc/data/xwq/demo/server
if [ ! -d "lib/linux" ]; then
    echo "⚠️  lib/linux 目录不存在"
    echo "   创建目录..."
    mkdir -p lib/linux
fi

echo "当前 lib/linux/ 目录内容:"
ls -lah lib/linux/ 2>/dev/null || echo "目录为空"

if [ -f "lib/linux/liblivoxjni.so" ]; then
    echo ""
    echo "✓ liblivoxjni.so 存在:"
    ls -lh lib/linux/liblivoxjni.so
    file lib/linux/liblivoxjni.so
    echo ""
    echo "检查依赖关系:"
    ldd lib/linux/liblivoxjni.so 2>/dev/null | head -15 || echo "无法检查依赖"
else
    echo ""
    echo "✗ liblivoxjni.so 不存在"
fi

if [ -f "lib/linux/liblivox_lidar_sdk_shared.so" ]; then
    echo ""
    echo "✓ liblivox_lidar_sdk_shared.so 存在:"
    ls -lh lib/linux/liblivox_lidar_sdk_shared.so
    file lib/linux/liblivox_lidar_sdk_shared.so
else
    echo ""
    echo "✗ liblivox_lidar_sdk_shared.so 不存在（这是导致加载失败的主要原因）"
    if [ -n "$SHARED_LIBS" ]; then
        FIRST_LIB=$(echo "$SHARED_LIBS" | head -1)
        echo ""
        echo "建议复制依赖库:"
        echo "  cp $FIRST_LIB lib/linux/"
    fi
fi

echo ""
echo "4. 检查系统架构..."
echo "系统架构: $(uname -m)"
echo "操作系统: $(uname -a)"

EOF

echo ""
echo "=========================================="
echo "检查完成"
echo "=========================================="
