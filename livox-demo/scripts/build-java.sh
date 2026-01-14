#!/bin/bash

# 编译 Java 项目

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

echo "=========================================="
echo "编译 Java 项目"
echo "=========================================="

if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到 mvn，请先安装 Maven"
    exit 1
fi

echo "编译 Java 项目..."
mvn clean package -DskipTests

if [ -f "target/livox-demo-1.0.0.jar" ]; then
    echo ""
    echo "Java 编译成功！"
    ls -lh target/livox-demo-1.0.0.jar
else
    echo "错误: Java 编译失败"
    exit 1
fi
