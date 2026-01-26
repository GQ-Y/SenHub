#!/bin/bash

# 检查服务器上 Livox SDK 库文件情况

echo "=== 检查 Livox SDK 库文件 ==="
echo ""

# 检查 lib/linux 目录
echo "1. 检查 lib/linux 目录："
sshpass -p 'admin' ssh -o StrictHostKeyChecking=no zyc@192.168.1.210 "cd /home/zyc/data/xwq/demo/server && ls -lah lib/linux/ 2>/dev/null || echo 'lib/linux 目录不存在'"
echo ""

# 检查 liblivoxjni.so
echo "2. 检查 liblivoxjni.so："
sshpass -p 'admin' ssh -o StrictHostKeyChecking=no zyc@192.168.1.210 "cd /home/zyc/data/xwq/demo/server && if [ -f lib/linux/liblivoxjni.so ]; then ls -lah lib/linux/liblivoxjni.so && file lib/linux/liblivoxjni.so; else echo 'liblivoxjni.so 不存在'; fi"
echo ""

# 检查依赖库
echo "3. 检查依赖库 liblivox_lidar_sdk_shared.so："
sshpass -p 'admin' ssh -o StrictHostKeyChecking=no zyc@192.168.1.210 "cd /home/zyc/data/xwq/demo/server && if [ -f lib/linux/liblivox_lidar_sdk_shared.so ]; then ls -lah lib/linux/liblivox_lidar_sdk_shared.so && file lib/linux/liblivox_lidar_sdk_shared.so && ldd lib/linux/liblivoxjni.so 2>/dev/null | head -20; else echo 'liblivox_lidar_sdk_shared.so 不存在'; fi"
echo ""

# 检查系统架构
echo "4. 检查系统架构："
sshpass -p 'admin' ssh -o StrictHostKeyChecking=no zyc@192.168.1.210 "uname -m && arch"
echo ""

# 检查日志中的错误
echo "5. 检查日志中的 Livox 相关错误："
sshpass -p 'admin' ssh -o StrictHostKeyChecking=no zyc@192.168.1.210 "cd /home/zyc/data/xwq/demo/server && tail -300 logs/app.log | grep -i -E 'livox|UnsatisfiedLinkError|NoClassDefFoundError|无法加载|雷达服务启动失败' | tail -20"
echo ""

# 检查 Java 库路径
echo "6. 检查 Java 进程的库路径："
sshpass -p 'admin' ssh -o StrictHostKeyChecking=no zyc@192.168.1.210 "ps aux | grep java | grep -v grep | head -1 | awk '{print \$2}' | xargs -I {} cat /proc/{}/environ 2>/dev/null | tr '\\0' '\\n' | grep -E 'LD_LIBRARY_PATH|java.library.path' || echo '无法获取 Java 进程信息'"
echo ""

echo "=== 检查完成 ==="
