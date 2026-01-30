#!/bin/bash
# 激活虚拟环境、安装依赖并启动 MQTT 订阅测试，将监听日志写入 logs/ 目录便于后续分析。

set -e
cd "$(dirname "$0")"

LOG_DIR="logs"
LOG_NAME="mqtt_subscriber_$(date +%Y%m%d_%H%M%S).log"
LOG_PATH="${LOG_DIR}/${LOG_NAME}"

# 创建虚拟环境（若不存在）
if [ ! -d "venv" ]; then
    echo "正在创建虚拟环境 venv ..."
    python3 -m venv venv
fi

# 激活虚拟环境并安装依赖
echo "正在激活虚拟环境并安装依赖 ..."
source venv/bin/activate
pip install -q -r requirements.txt

# 启动订阅测试，控制台与日志文件同时输出
echo "正在启动 MQTT 订阅测试，日志将保存到: ${LOG_PATH}"
echo "按 Ctrl+C 停止。"
python mqtt_subscriber.py --log-file "${LOG_PATH}" --validate
