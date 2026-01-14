#!/bin/bash
# 从服务器同步 Linux 版动态库到本地

echo "从服务器同步 Linux 版动态库..."
sshpass -p 'admin' scp 'zyc@192.168.1.210:/home/zyc/data/xwq/livox-demo/lib/*.so' lib/linux/ 2>&1

if [ $? -eq 0 ]; then
    echo "同步成功！"
    ls -lh lib/linux/
else
    echo "同步失败"
    exit 1
fi
