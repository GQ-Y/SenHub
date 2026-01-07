#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MQTT测试客户端
用于测试海康威视NVR控制服务的MQTT命令功能
"""

import json
import time
import uuid
import argparse
from datetime import datetime, timedelta
from typing import Dict, Optional, Callable
import paho.mqtt.client as mqtt
from threading import Event, Lock


class MqttTestClient:
    """MQTT测试客户端类"""
    
    def __init__(self, broker: str, username: str, password: str, 
                 command_topic: str, response_topic: str, qos: int = 1):
        """
        初始化MQTT测试客户端
        
        Args:
            broker: MQTT服务器地址
            username: MQTT用户名
            password: MQTT密码
            command_topic: 命令发布主题
            response_topic: 响应订阅主题
            qos: 消息质量等级
        """
        self.broker = broker
        self.username = username
        self.password = password
        self.command_topic = command_topic
        self.response_topic = response_topic
        self.qos = qos
        
        # 客户端ID使用唯一标识
        client_id = f"mqtt_test_client_{uuid.uuid4().hex[:8]}"
        self.client = mqtt.Client(client_id=client_id)
        self.client.username_pw_set(username, password)
        
        # 设置回调
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.client.on_disconnect = self._on_disconnect
        
        # 响应等待机制
        self.pending_responses: Dict[str, Dict] = {}
        self.response_lock = Lock()
        self.response_timeout = 30  # 响应超时时间（秒）
        
        # 测试结果统计
        self.test_results = []
        self.connected = False
        
    def _on_connect(self, client, userdata, flags, rc):
        """连接回调"""
        if rc == 0:
            self.connected = True
            print(f"✓ MQTT连接成功: {self.broker}")
            # 订阅响应主题
            client.subscribe(self.response_topic, self.qos)
            print(f"✓ 已订阅响应主题: {self.response_topic}")
        else:
            self.connected = False
            print(f"✗ MQTT连接失败，错误码: {rc}")
    
    def _on_message(self, client, userdata, msg):
        """消息接收回调"""
        try:
            payload = msg.payload.decode('utf-8')
            response = json.loads(payload)
            request_id = response.get('requestId')
            
            if request_id and request_id in self.pending_responses:
                with self.response_lock:
                    self.pending_responses[request_id]['response'] = response
                    self.pending_responses[request_id]['received'] = True
                    self.pending_responses[request_id]['event'].set()
        except Exception as e:
            print(f"✗ 解析响应消息失败: {e}")
    
    def _on_disconnect(self, client, userdata, rc):
        """断开连接回调"""
        self.connected = False
        if rc != 0:
            print(f"✗ MQTT意外断开连接，错误码: {rc}")
        else:
            print("✓ MQTT连接已断开")
    
    def connect(self) -> bool:
        """连接到MQTT服务器"""
        try:
            # 解析broker地址
            # 支持格式: tcp://host:port 或 host:port
            broker_url = self.broker
            if broker_url.startswith('tcp://'):
                broker_url = broker_url[6:]
            elif broker_url.startswith('mqtt://'):
                broker_url = broker_url[7:]
            
            # 分离主机和端口
            if ':' in broker_url:
                host, port = broker_url.rsplit(':', 1)
                port = int(port)
            else:
                host = broker_url
                port = 1883  # 默认端口
            
            self.client.connect(host, port, 60)
            self.client.loop_start()
            # 等待连接建立
            time.sleep(2)
            return self.connected
        except Exception as e:
            print(f"✗ 连接失败: {e}")
            return False
    
    def disconnect(self):
        """断开连接"""
        if self.connected:
            self.client.loop_stop()
            self.client.disconnect()
            self.connected = False
    
    def send_command(self, command: str, device_id: str, 
                     params: Optional[Dict] = None) -> Optional[Dict]:
        """
        发送命令并等待响应
        
        Args:
            command: 命令类型
            device_id: 设备ID
            params: 额外参数
            
        Returns:
            响应字典，如果超时则返回None
        """
        if not self.connected:
            print("✗ MQTT未连接，无法发送命令")
            return None
        
        # 生成请求ID
        request_id = f"test_{command}_{uuid.uuid4().hex[:8]}"
        
        # 构建命令
        cmd = {
            "command": command,
            "device_id": device_id,
            "request_id": request_id
        }
        if params:
            cmd.update(params)
        
        # 注册响应等待
        event = Event()
        with self.response_lock:
            self.pending_responses[request_id] = {
                'event': event,
                'received': False,
                'response': None,
                'sent_time': time.time()
            }
        
        # 发送命令
        try:
            cmd_json = json.dumps(cmd, ensure_ascii=False)
            print(f"\n→ 发送命令: {command}")
            print(f"  设备ID: {device_id}")
            print(f"  请求ID: {request_id}")
            if params:
                print(f"  参数: {json.dumps(params, ensure_ascii=False, indent=2)}")
            
            self.client.publish(self.command_topic, cmd_json, self.qos)
            
            # 等待响应
            if event.wait(timeout=self.response_timeout):
                with self.response_lock:
                    response = self.pending_responses[request_id]['response']
                    elapsed = time.time() - self.pending_responses[request_id]['sent_time']
                    del self.pending_responses[request_id]
                    return {
                        'response': response,
                        'elapsed_time': elapsed
                    }
            else:
                # 超时
                with self.response_lock:
                    del self.pending_responses[request_id]
                print(f"✗ 等待响应超时 ({self.response_timeout}秒)")
                return None
                
        except Exception as e:
            print(f"✗ 发送命令失败: {e}")
            with self.response_lock:
                if request_id in self.pending_responses:
                    del self.pending_responses[request_id]
            return None
    
    def test_capture(self, device_id: str) -> Dict:
        """测试抓图命令"""
        print("\n" + "="*60)
        print("测试: 抓图命令 (capture)")
        print("="*60)
        
        start_time = time.time()
        result = self.send_command("capture", device_id)
        
        test_result = {
            'test_name': 'capture',
            'device_id': device_id,
            'success': False,
            'response': None,
            'elapsed_time': 0,
            'error': None
        }
        
        if result:
            response = result['response']
            elapsed = result['elapsed_time']
            test_result['response'] = response
            test_result['elapsed_time'] = elapsed
            
            if response.get('success'):
                test_result['success'] = True
                print(f"✓ 抓图成功")
                print(f"  响应时间: {elapsed:.2f}秒")
                if 'data' in response:
                    data = response['data']
                    if 'image_base64' in data:
                        img_size = data.get('image_size', 0)
                        print(f"  图片大小: {img_size} 字节")
                        print(f"  通道: {data.get('channel', 'N/A')}")
            else:
                test_result['error'] = response.get('error', '未知错误')
                print(f"✗ 抓图失败: {test_result['error']}")
        else:
            test_result['error'] = '响应超时或连接失败'
            print(f"✗ 测试失败: {test_result['error']}")
        
        self.test_results.append(test_result)
        return test_result
    
    def test_ptz_control(self, device_id: str, action: str = "up", 
                        speed: int = 5, channel: int = 1) -> Dict:
        """测试云台控制命令"""
        print("\n" + "="*60)
        print(f"测试: 云台控制 (ptz_control) - {action}")
        print("="*60)
        
        params = {
            "action": action,
            "speed": speed,
            "channel": channel
        }
        
        result = self.send_command("ptz_control", device_id, params)
        
        test_result = {
            'test_name': 'ptz_control',
            'device_id': device_id,
            'action': action,
            'success': False,
            'response': None,
            'elapsed_time': 0,
            'error': None
        }
        
        if result:
            response = result['response']
            elapsed = result['elapsed_time']
            test_result['response'] = response
            test_result['elapsed_time'] = elapsed
            
            if response.get('success'):
                test_result['success'] = True
                print(f"✓ 云台控制成功: {action}")
                print(f"  响应时间: {elapsed:.2f}秒")
            else:
                test_result['error'] = response.get('error', '未知错误')
                print(f"✗ 云台控制失败: {test_result['error']}")
        else:
            test_result['error'] = '响应超时或连接失败'
            print(f"✗ 测试失败: {test_result['error']}")
        
        self.test_results.append(test_result)
        return test_result
    
    def test_playback(self, device_id: str, channel: int = 1) -> Dict:
        """测试回放命令"""
        print("\n" + "="*60)
        print("测试: 回放命令 (playback)")
        print("="*60)
        
        # 计算时间范围（当前时间前后30秒，共1分钟）
        now = datetime.now()
        start_time = now - timedelta(seconds=30)
        end_time = now + timedelta(seconds=30)
        
        params = {
            "start_time": start_time.strftime("%Y-%m-%d %H:%M:%S"),
            "end_time": end_time.strftime("%Y-%m-%d %H:%M:%S"),
            "channel": channel
        }
        
        result = self.send_command("playback", device_id, params)
        
        test_result = {
            'test_name': 'playback',
            'device_id': device_id,
            'success': False,
            'response': None,
            'elapsed_time': 0,
            'error': None
        }
        
        if result:
            response = result['response']
            elapsed = result['elapsed_time']
            test_result['response'] = response
            test_result['elapsed_time'] = elapsed
            
            if response.get('success'):
                test_result['success'] = True
                print(f"✓ 回放命令成功")
                print(f"  响应时间: {elapsed:.2f}秒")
                if 'data' in response:
                    data = response['data']
                    source = data.get('source', 'unknown')
                    print(f"  数据源: {source}")
                    if source == 'local':
                        print(f"  文件路径: {data.get('file_path', 'N/A')}")
                        print(f"  文件大小: {data.get('file_size', 0)} 字节")
            else:
                test_result['error'] = response.get('error', '未知错误')
                print(f"✗ 回放失败: {test_result['error']}")
        else:
            test_result['error'] = '响应超时或连接失败'
            print(f"✗ 测试失败: {test_result['error']}")
        
        self.test_results.append(test_result)
        return test_result
    
    def test_reboot(self, device_id: str) -> Dict:
        """测试重启命令（谨慎使用）"""
        print("\n" + "="*60)
        print("测试: 重启命令 (reboot) - 警告：此操作会重启设备！")
        print("="*60)
        
        result = self.send_command("reboot", device_id)
        
        test_result = {
            'test_name': 'reboot',
            'device_id': device_id,
            'success': False,
            'response': None,
            'elapsed_time': 0,
            'error': None
        }
        
        if result:
            response = result['response']
            elapsed = result['elapsed_time']
            test_result['response'] = response
            test_result['elapsed_time'] = elapsed
            
            if response.get('success'):
                test_result['success'] = True
                print(f"✓ 重启命令已发送")
                print(f"  响应时间: {elapsed:.2f}秒")
            else:
                test_result['error'] = response.get('error', '未知错误')
                print(f"✗ 重启失败: {test_result['error']}")
        else:
            test_result['error'] = '响应超时或连接失败'
            print(f"✗ 测试失败: {test_result['error']}")
        
        self.test_results.append(test_result)
        return test_result
    
    def test_invalid_device(self) -> Dict:
        """测试无效设备ID"""
        print("\n" + "="*60)
        print("测试: 错误处理 - 无效设备ID")
        print("="*60)
        
        invalid_device_id = "999.999.999.999:9999"
        result = self.send_command("capture", invalid_device_id)
        
        test_result = {
            'test_name': 'invalid_device',
            'device_id': invalid_device_id,
            'success': False,
            'response': None,
            'elapsed_time': 0,
            'error': None
        }
        
        if result:
            response = result['response']
            elapsed = result['elapsed_time']
            test_result['response'] = response
            test_result['elapsed_time'] = elapsed
            
            # 期望返回错误
            if not response.get('success'):
                test_result['success'] = True  # 测试通过：正确返回了错误
                print(f"✓ 正确返回错误: {response.get('error', 'N/A')}")
            else:
                test_result['error'] = '应该返回错误但返回了成功'
                print(f"✗ 测试失败: 应该返回错误但返回了成功")
        else:
            test_result['error'] = '响应超时或连接失败'
            print(f"✗ 测试失败: {test_result['error']}")
        
        self.test_results.append(test_result)
        return test_result
    
    def test_invalid_command(self, device_id: str) -> Dict:
        """测试无效命令类型"""
        print("\n" + "="*60)
        print("测试: 错误处理 - 无效命令类型")
        print("="*60)
        
        result = self.send_command("invalid_command_xyz", device_id)
        
        test_result = {
            'test_name': 'invalid_command',
            'device_id': device_id,
            'success': False,
            'response': None,
            'elapsed_time': 0,
            'error': None
        }
        
        if result:
            response = result['response']
            elapsed = result['elapsed_time']
            test_result['response'] = response
            test_result['elapsed_time'] = elapsed
            
            # 期望返回错误
            if not response.get('success'):
                test_result['success'] = True  # 测试通过：正确返回了错误
                print(f"✓ 正确返回错误: {response.get('error', 'N/A')}")
            else:
                test_result['error'] = '应该返回错误但返回了成功'
                print(f"✗ 测试失败: 应该返回错误但返回了成功")
        else:
            test_result['error'] = '响应超时或连接失败'
            print(f"✗ 测试失败: {test_result['error']}")
        
        self.test_results.append(test_result)
        return test_result
    
    def test_missing_params(self, device_id: str) -> Dict:
        """测试缺少必需参数"""
        print("\n" + "="*60)
        print("测试: 错误处理 - 缺少必需参数")
        print("="*60)
        
        # 发送缺少device_id的命令（通过直接发布JSON）
        request_id = f"test_missing_params_{uuid.uuid4().hex[:8]}"
        cmd = {
            "command": "capture",
            "request_id": request_id
            # 故意缺少device_id
        }
        
        event = Event()
        with self.response_lock:
            self.pending_responses[request_id] = {
                'event': event,
                'received': False,
                'response': None,
                'sent_time': time.time()
            }
        
        try:
            cmd_json = json.dumps(cmd, ensure_ascii=False)
            print(f"→ 发送缺少device_id的命令")
            self.client.publish(self.command_topic, cmd_json, self.qos)
            
            if event.wait(timeout=self.response_timeout):
                with self.response_lock:
                    response = self.pending_responses[request_id]['response']
                    elapsed = time.time() - self.pending_responses[request_id]['sent_time']
                    del self.pending_responses[request_id]
                    
                    test_result = {
                        'test_name': 'missing_params',
                        'device_id': device_id,
                        'success': False,
                        'response': response,
                        'elapsed_time': elapsed,
                        'error': None
                    }
                    
                    # 期望返回错误
                    if not response.get('success'):
                        test_result['success'] = True
                        print(f"✓ 正确返回错误: {response.get('error', 'N/A')}")
                    else:
                        test_result['error'] = '应该返回错误但返回了成功'
                        print(f"✗ 测试失败: 应该返回错误但返回了成功")
                    
                    self.test_results.append(test_result)
                    return test_result
            else:
                with self.response_lock:
                    del self.pending_responses[request_id]
                test_result = {
                    'test_name': 'missing_params',
                    'device_id': device_id,
                    'success': False,
                    'response': None,
                    'elapsed_time': 0,
                    'error': '响应超时'
                }
                self.test_results.append(test_result)
                return test_result
        except Exception as e:
            with self.response_lock:
                if request_id in self.pending_responses:
                    del self.pending_responses[request_id]
            test_result = {
                'test_name': 'missing_params',
                'device_id': device_id,
                'success': False,
                'response': None,
                'elapsed_time': 0,
                'error': str(e)
            }
            self.test_results.append(test_result)
            return test_result
    
    def generate_report(self) -> str:
        """生成测试报告"""
        total = len(self.test_results)
        passed = sum(1 for r in self.test_results if r['success'])
        failed = total - passed
        
        avg_time = sum(r['elapsed_time'] for r in self.test_results if r['elapsed_time'] > 0)
        if total > 0:
            avg_time = avg_time / total
        
        report = f"""
{'='*60}
测试报告
{'='*60}
总测试数: {total}
通过: {passed}
失败: {failed}
平均响应时间: {avg_time:.2f}秒

详细结果:
{'-'*60}
"""
        for i, result in enumerate(self.test_results, 1):
            status = "✓ 通过" if result['success'] else "✗ 失败"
            report += f"{i}. {result['test_name']} - {status}\n"
            report += f"   设备ID: {result.get('device_id', 'N/A')}\n"
            if result['elapsed_time'] > 0:
                report += f"   响应时间: {result['elapsed_time']:.2f}秒\n"
            if result.get('error'):
                report += f"   错误: {result['error']}\n"
            report += "\n"
        
        return report
    
    def print_report(self):
        """打印测试报告"""
        print(self.generate_report())


def load_config(config_file: str) -> Dict:
    """加载配置文件"""
    try:
        with open(config_file, 'r', encoding='utf-8') as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"警告: 配置文件 {config_file} 不存在，使用默认配置")
        return {}
    except json.JSONDecodeError as e:
        print(f"错误: 配置文件格式错误: {e}")
        return {}


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='MQTT测试客户端')
    parser.add_argument('--config', type=str, default='config.json',
                       help='配置文件路径 (默认: config.json)')
    parser.add_argument('--device-id', type=str, required=True,
                       help='测试设备ID (例如: 192.168.1.100:8000)')
    parser.add_argument('--test', type=str, choices=['all', 'capture', 'ptz', 
                       'playback', 'reboot', 'error', 'basic'],
                       default='basic',
                       help='测试类型 (默认: basic)')
    parser.add_argument('--broker', type=str,
                       help='MQTT服务器地址 (覆盖配置文件)')
    parser.add_argument('--username', type=str,
                       help='MQTT用户名 (覆盖配置文件)')
    parser.add_argument('--password', type=str,
                       help='MQTT密码 (覆盖配置文件)')
    
    args = parser.parse_args()
    
    # 加载配置
    config = load_config(args.config)
    mqtt_config = config.get('mqtt', {})
    
    # 使用命令行参数或配置文件
    broker = args.broker or mqtt_config.get('broker', 'tcp://mqtt.yingzhu.net:1883')
    username = args.username or mqtt_config.get('username', 'demos1')
    password = args.password or mqtt_config.get('password', 'demos1')
    command_topic = mqtt_config.get('command_topic', 'hikvision/command')
    response_topic = mqtt_config.get('response_topic', 'hikvision/response')
    qos = mqtt_config.get('qos', 1)
    
    device_id = args.device_id
    
    # 创建测试客户端
    print("初始化MQTT测试客户端...")
    client = MqttTestClient(broker, username, password, command_topic, response_topic, qos)
    
    # 连接
    if not client.connect():
        print("无法连接到MQTT服务器，退出")
        return 1
    
    try:
        # 根据测试类型执行测试
        if args.test == 'all':
            # 执行所有测试
            client.test_capture(device_id)
            client.test_ptz_control(device_id, "up")
            client.test_ptz_control(device_id, "down")
            client.test_playback(device_id)
            client.test_invalid_device()
            client.test_invalid_command(device_id)
            client.test_missing_params(device_id)
            # 注意：reboot测试默认不包含在all中，需要明确指定
        elif args.test == 'basic':
            # 基本测试（安全命令）
            client.test_capture(device_id)
            client.test_ptz_control(device_id, "up")
            client.test_playback(device_id)
        elif args.test == 'capture':
            client.test_capture(device_id)
        elif args.test == 'ptz':
            client.test_ptz_control(device_id, "up")
            client.test_ptz_control(device_id, "down")
            client.test_ptz_control(device_id, "left")
            client.test_ptz_control(device_id, "right")
        elif args.test == 'playback':
            client.test_playback(device_id)
        elif args.test == 'reboot':
            # 警告提示
            confirm = input("警告：此操作会重启设备！确认继续？(yes/no): ")
            if confirm.lower() == 'yes':
                client.test_reboot(device_id)
            else:
                print("已取消重启测试")
        elif args.test == 'error':
            # 错误处理测试
            client.test_invalid_device()
            client.test_invalid_command(device_id)
            client.test_missing_params(device_id)
        
        # 打印报告
        client.print_report()
        
    finally:
        client.disconnect()
    
    return 0


if __name__ == '__main__':
    exit(main())
