#!/bin/bash

# API接口测试脚本

BASE_URL="http://localhost:8080/api"
TOKEN=""

echo "=== API接口测试 ==="
echo ""

# 1. 登录获取token
echo "1. 测试登录接口..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}')

echo "登录响应: $LOGIN_RESPONSE"
echo ""

# 提取token
TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo "❌ 登录失败，无法获取token"
    exit 1
fi

echo "✅ 登录成功，Token: ${TOKEN:0:20}..."
echo ""

# 2. 获取设备列表
echo "2. 测试获取设备列表..."
curl -s -X GET "$BASE_URL/devices" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
echo ""

# 3. 添加设备
echo "3. 测试添加设备..."
ADD_RESPONSE=$(curl -s -X POST "$BASE_URL/devices" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试设备",
    "ip": "192.168.1.100",
    "port": 8000,
    "username": "admin",
    "password": "123456",
    "channel": 1
  }')

echo "添加设备响应: $ADD_RESPONSE" | jq '.'
DEVICE_ID=$(echo $ADD_RESPONSE | jq -r '.data.id // empty')

if [ -z "$DEVICE_ID" ] || [ "$DEVICE_ID" = "null" ]; then
    echo "⚠️  无法获取设备ID，使用默认ID"
    DEVICE_ID="192.168.1.100:8000"
fi

echo "设备ID: $DEVICE_ID"
echo ""

# 4. 获取设备详情
echo "4. 测试获取设备详情..."
curl -s -X GET "$BASE_URL/devices/$DEVICE_ID" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
echo ""

# 5. 更新设备
echo "5. 测试更新设备..."
curl -s -X PUT "$BASE_URL/devices/$DEVICE_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "更新后的设备名称",
    "ip": "192.168.1.100",
    "port": 8000
  }' | jq '.'
echo ""

# 6. 获取驱动列表
echo "6. 测试获取驱动列表..."
curl -s -X GET "$BASE_URL/drivers" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
echo ""

# 7. 获取MQTT配置
echo "7. 测试获取MQTT配置..."
curl -s -X GET "$BASE_URL/mqtt/config" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
echo ""

# 8. 获取系统配置
echo "8. 测试获取系统配置..."
curl -s -X GET "$BASE_URL/system/config" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
echo ""

# 9. 获取仪表板统计
echo "9. 测试获取仪表板统计..."
curl -s -X GET "$BASE_URL/dashboard/stats" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
echo ""

# 10. 删除设备（可选）
read -p "是否删除测试设备? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "10. 测试删除设备..."
    curl -s -X DELETE "$BASE_URL/devices/$DEVICE_ID" \
      -H "Authorization: Bearer $TOKEN" | jq '.'
    echo ""
fi

echo "✅ API测试完成"
