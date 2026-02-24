#!/bin/bash
# 服务器磁盘空间清理脚本
# 清理Qoder编辑器缓存、系统日志和临时文件

echo "========================================="
echo "服务器磁盘空间清理"
echo "========================================="

# 显示当前磁盘使用情况
echo "清理前磁盘使用情况:"
df -h /

echo ""
echo "========================================="
echo "开始清理..."
echo "========================================="

# 1. 清理Qoder编辑器旧版本缓存(保留最新2个版本)
echo "[1/5] 清理Qoder编辑器旧版本缓存..."
if [ -d "/home/zyc/.qoder-server/bin" ]; then
    cd /home/zyc/.qoder-server/bin
    # 列出所有版本目录,按时间排序,删除除最新2个外的所有版本
    ls -t | tail -n +3 | xargs -r rm -rf
    echo "     已清理Qoder旧版本"
fi

# 2. 清理系统日志(保留最近7天)
echo "[2/5] 清理系统日志..."
sudo journalctl --vacuum-time=7d 2>/dev/null || echo "     journalctl清理跳过"
sudo find /var/log -type f -name "*.log.*" -mtime +7 -delete 2>/dev/null || echo "     /var/log清理跳过"
sudo find /var/log -type f -name "*.gz" -mtime +7 -delete 2>/dev/null || echo "     压缩日志清理跳过"
echo "     已清理旧日志文件"

# 3. 清理临时文件
echo "[3/5] 清理临时文件..."
sudo rm -rf /tmp/* 2>/dev/null || echo "     /tmp清理跳过"
sudo rm -rf /var/tmp/* 2>/dev/null || echo "     /var/tmp清理跳过"
echo "     已清理临时文件"

# 4. 清理APT缓存
echo "[4/5] 清理APT缓存..."
sudo apt-get clean 2>/dev/null || echo "     APT清理跳过"
sudo apt-get autoclean 2>/dev/null || echo "     APT autoclean跳过"
echo "     已清理APT缓存"

# 5. 清理npm缓存
echo "[5/5] 清理npm缓存..."
if [ -d "/home/zyc/.npm" ]; then
    npm cache clean --force 2>/dev/null || echo "     npm缓存清理跳过"
    echo "     已清理npm缓存"
fi

echo ""
echo "========================================="
echo "清理完成!"
echo "========================================="

# 显示清理后磁盘使用情况
echo "清理后磁盘使用情况:"
df -h /

echo ""
echo "释放的空间:"
echo "请对比上面的清理前后数据"
