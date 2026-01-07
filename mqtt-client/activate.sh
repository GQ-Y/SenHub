#!/bin/bash
# 激活虚拟环境的脚本

source venv/bin/activate
echo "虚拟环境已激活！"
echo "现在可以运行测试客户端："
echo "  python mqtt_test_client.py --device-id <设备ID> --test basic"
echo ""
echo "退出虚拟环境请运行: deactivate"
