#!/bin/bash
# 天地伟业视频网关服务启动脚本
# 设置LD_LIBRARY_PATH以加载天地伟业SDK的FFmpeg依赖库

# 服务器路径(新路径,在/mnt/data分区,有454G可用空间)
SERVER_DIR="/mnt/data/zyc/data/xwq/demo/server"
JAR_FILE="video-gateway-service-1.0.0.jar"

# 天地伟业SDK库路径
TIANDY_LIB_DIR="${SERVER_DIR}/lib/x86/tiandy"
TIANDY_LIB_SUB="${TIANDY_LIB_DIR}/lib"

# 设置LD_LIBRARY_PATH(包含tiandy和tiandy/lib目录)
export LD_LIBRARY_PATH="${TIANDY_LIB_DIR}:${TIANDY_LIB_SUB}:${LD_LIBRARY_PATH}"

echo "========================================="
echo "启动天地伟业视频网关服务"
echo "========================================="
echo "服务目录: ${SERVER_DIR}"
echo "JAR文件: ${JAR_FILE}"
echo "LD_LIBRARY_PATH: ${LD_LIBRARY_PATH}"
echo "========================================="

# 切换到服务目录
cd "${SERVER_DIR}" || exit 1

# 停止旧进程
echo "停止旧进程..."
pkill -f "${JAR_FILE}"
sleep 2

# 启动服务
echo "启动服务..."
# 创建临时目录(在/mnt/data分区,避免根分区空间不足)
mkdir -p "${SERVER_DIR}/tmp"
nohup env LD_LIBRARY_PATH="${TIANDY_LIB_DIR}:${TIANDY_LIB_SUB}:${LD_LIBRARY_PATH}" \
    java -Djava.io.tmpdir="${SERVER_DIR}/tmp" \
         -Djava.library.path="${TIANDY_LIB_DIR}:${TIANDY_LIB_SUB}" \
         -Dtiandy.lib.path="${TIANDY_LIB_DIR}" \
         -jar "${JAR_FILE}" > server.log 2>&1 &

# 等待启动
sleep 3

# 检查进程
if pgrep -f "${JAR_FILE}" > /dev/null; then
    echo "✅ 服务启动成功"
    echo "进程ID: $(pgrep -f "${JAR_FILE}")"
    echo "查看日志: tail -f ${SERVER_DIR}/server.log"
else
    echo "❌ 服务启动失败"
    echo "查看日志: cat ${SERVER_DIR}/server.log"
    exit 1
fi
