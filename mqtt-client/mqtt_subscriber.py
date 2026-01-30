#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MQTT 订阅测试工具
订阅网关发布的 senhub 主题，监听并可选校验消息格式，用于联调与回归测试。
仅订阅、不发命令；与 mqtt_test_client.py 互补（后者负责发命令与收响应）。
"""

import json
import sys
import time
import uuid
import argparse
from datetime import datetime
from typing import List, Optional, Any, Dict

import paho.mqtt.client as mqtt


# 默认订阅主题（与网关 config.yaml 一致）
DEFAULT_TOPIC = "senhub/#"
EXPLICIT_TOPICS = [
    "senhub/gateway/status",
    "senhub/device/status",
    "senhub/assembly/+/status",
    "senhub/report/+",
    "senhub/response",
]


def load_config(config_path: str) -> Dict[str, Any]:
    """从 config.json 加载 MQTT 配置。"""
    with open(config_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    mqtt_cfg = data.get("mqtt", {})
    return {
        "broker": mqtt_cfg.get("broker", "tcp://localhost:1883"),
        "username": mqtt_cfg.get("username", ""),
        "password": mqtt_cfg.get("password", ""),
        "qos": int(mqtt_cfg.get("qos", 1)),
        "status_topic": mqtt_cfg.get("status_topic", "senhub/device/status"),
        "gateway_status_topic": mqtt_cfg.get("gateway_status_topic", "senhub/gateway/status"),
        "report_topic_prefix": mqtt_cfg.get("report_topic_prefix", "senhub/report"),
    }


def parse_broker(broker: str) -> tuple:
    """解析 broker 地址为 (host, port)。"""
    url = broker
    if url.startswith("tcp://"):
        url = url[6:]
    elif url.startswith("mqtt://"):
        url = url[7:]
    if ":" in url:
        host, port = url.rsplit(":", 1)
        return host.strip(), int(port)
    return url.strip(), 1883


def validate_gateway_status(payload: Dict[str, Any]) -> List[str]:
    """校验 senhub/gateway/status：type、gateway_id、timestamp；type 为 online/offline。"""
    errors = []
    for key in ("type", "gateway_id", "timestamp"):
        if key not in payload:
            errors.append(f"missing field: {key}")
    if "type" in payload and payload["type"] not in ("online", "offline"):
        errors.append(f"invalid type: {payload['type']}")
    return errors


def validate_device_status(payload: Dict[str, Any]) -> List[str]:
    """校验 senhub/device/status：entity_type、device_id、type、timestamp；camera 含 device_info，radar 含 radar_info。"""
    errors = []
    for key in ("entity_type", "device_id", "type", "timestamp"):
        if key not in payload:
            errors.append(f"missing field: {key}")
    entity = payload.get("entity_type")
    if entity == "camera":
        if "device_info" not in payload:
            errors.append("camera missing device_info")
        else:
            info = payload["device_info"]
            if "camera_type" not in info:
                errors.append("device_info missing camera_type")
    elif entity == "radar":
        if "radar_info" not in payload:
            errors.append("radar missing radar_info")
    if "type" in payload and payload["type"] not in ("online", "offline"):
        errors.append(f"invalid type: {payload['type']}")
    return errors


def validate_assembly_status(payload: Dict[str, Any]) -> List[str]:
    """校验 senhub/assembly/+/status：assembly_id、type、timestamp、assembly_info（可含 longitude、latitude、device_ids）。"""
    errors = []
    for key in ("assembly_id", "type", "timestamp", "assembly_info"):
        if key not in payload:
            errors.append(f"missing field: {key}")
    return errors


def validate_report(payload: Dict[str, Any]) -> List[str]:
    """校验 senhub/report/+：device_id、event_id、event_key、flowId。"""
    errors = []
    for key in ("device_id", "event_id", "event_key", "flowId"):
        if key not in payload:
            errors.append(f"missing field: {key}")
    return errors


def validate_response(payload: Dict[str, Any]) -> List[str]:
    """校验 senhub/response：requestId、deviceId、command、success。"""
    errors = []
    for key in ("requestId", "deviceId", "command", "success"):
        if key not in payload:
            errors.append(f"missing field: {key}")
    return errors


def get_validator(topic: str):
    """根据主题模式返回校验函数，不匹配则返回 None。"""
    if topic == "senhub/gateway/status":
        return validate_gateway_status
    if topic == "senhub/device/status":
        return validate_device_status
    if topic.startswith("senhub/assembly/") and topic.endswith("/status"):
        return validate_assembly_status
    if topic.startswith("senhub/report/"):
        return validate_report
    if topic == "senhub/response":
        return validate_response
    return None


class _Tee:
    """将 stdout/stderr 同时写入控制台和日志文件。"""
    def __init__(self, *streams):
        self.streams = streams

    def write(self, s):
        for st in self.streams:
            st.write(s)
            if hasattr(st, "flush"):
                st.flush()

    def flush(self):
        for st in self.streams:
            if hasattr(st, "flush"):
                st.flush()


def run_subscriber(
    config_path: str,
    topics: List[str],
    validate: bool,
    duration: Optional[int],
    buffer_size: int,
    log_file_path: Optional[str] = None,
) -> None:
    """连接 Broker，订阅主题，打印消息并可选校验；可选将输出写入日志文件。"""
    log_file = None
    if log_file_path:
        import os
        os.makedirs(os.path.dirname(log_file_path) or ".", exist_ok=True)
        log_file = open(log_file_path, "a", encoding="utf-8")
        log_file.write(f"\n===== mqtt_subscriber 启动 {datetime.now().isoformat()} =====\n")
        log_file.flush()
        old_stdout, old_stderr = sys.stdout, sys.stderr
        sys.stdout = _Tee(old_stdout, log_file)
        sys.stderr = _Tee(old_stderr, log_file)

    try:
        _run_subscriber_impl(
            config_path=config_path,
            topics=topics,
            validate=validate,
            duration=duration,
            buffer_size=buffer_size,
        )
    finally:
        if log_file is not None:
            sys.stdout, sys.stderr = old_stdout, old_stderr
            log_file.write(f"===== mqtt_subscriber 结束 {datetime.now().isoformat()} =====\n")
            log_file.close()


def _run_subscriber_impl(
    config_path: str,
    topics: List[str],
    validate: bool,
    duration: Optional[int],
    buffer_size: int,
) -> None:
    """实际订阅逻辑（在 Tee 生效时调用）。"""
    config = load_config(config_path)
    host, port = parse_broker(config["broker"])
    client_id = f"mqtt_subscriber_{uuid.uuid4().hex[:8]}"
    client = mqtt.Client(client_id=client_id)
    if config["username"] or config["password"]:
        client.username_pw_set(config["username"], config["password"])

    messages_buffer: List[Dict[str, Any]] = []
    qos = config["qos"]

    def on_connect(_client, _userdata, _flags, rc):
        if rc != 0:
            print(f"[ERROR] MQTT 连接失败, rc={rc}", file=sys.stderr)
            return
        print(f"[OK] 已连接: {host}:{port}")
        for t in topics:
            _client.subscribe(t, qos)
            print(f"[OK] 已订阅: {t}")

    def on_message(_client, _userdata, msg):
        try:
            raw = msg.payload.decode("utf-8")
            try:
                obj = json.loads(raw)
                body = json.dumps(obj, ensure_ascii=False, indent=2)
            except json.JSONDecodeError:
                body = raw
        except Exception:
            body = msg.payload.decode("utf-8", errors="replace")

        ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"\n[{ts}] topic: {msg.topic}")
        print(body)

        if buffer_size > 0:
            try:
                payload_obj = json.loads(raw)
            except Exception:
                payload_obj = raw
            messages_buffer.append({"topic": msg.topic, "payload": payload_obj, "time": ts})
            if len(messages_buffer) > buffer_size:
                messages_buffer.pop(0)

        if validate:
            try:
                obj = json.loads(raw)
            except Exception:
                print("[WARNING] 无法解析为 JSON，跳过校验")
                return
            validator = get_validator(msg.topic)
            if validator:
                errs = validator(obj)
                if errs:
                    print(f"[WARNING] 校验失败: {errs}")
                else:
                    print("[OK] 校验通过")

    client.on_connect = on_connect
    client.on_message = on_message

    try:
        client.connect(host, port, 60)
    except Exception as e:
        print(f"[ERROR] 连接失败: {e}", file=sys.stderr)
        sys.exit(1)

    client.loop_start()
    time.sleep(2)

    if duration is not None:
        time.sleep(duration)
        client.loop_stop()
        client.disconnect()
        print(f"\n[INFO] 已运行 {duration} 秒并退出")
        return

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        client.loop_stop()
        client.disconnect()
        print("\n[INFO] 已断开连接")


def main():
    parser = argparse.ArgumentParser(
        description="订阅 senhub 主题，监听网关发布的状态/报警消息并可选校验。"
    )
    parser.add_argument(
        "--config",
        default="config.json",
        help="配置文件路径 (默认: config.json)",
    )
    parser.add_argument(
        "--topics",
        nargs="+",
        default=None,
        metavar="TOPIC",
        help=f"仅监听指定主题；不指定时默认订阅 {DEFAULT_TOPIC}",
    )
    parser.add_argument(
        "--validate",
        action="store_true",
        help="对消息做结构化校验，失败时打印 WARNING",
    )
    parser.add_argument(
        "--duration",
        type=int,
        default=None,
        metavar="SECONDS",
        help="运行指定秒数后退出，便于 CI/脚本化",
    )
    parser.add_argument(
        "--buffer",
        type=int,
        default=0,
        metavar="N",
        help="在内存中保留最近 N 条消息（0 表示不缓存）",
    )
    parser.add_argument(
        "--log-file",
        default=None,
        metavar="PATH",
        help="将控制台输出同时写入该日志文件，便于后续分析（如 logs/mqtt_subscriber.log）",
    )
    args = parser.parse_args()

    topics = args.topics if args.topics is not None else [DEFAULT_TOPIC]
    run_subscriber(
        config_path=args.config,
        topics=topics,
        validate=args.validate,
        duration=args.duration,
        buffer_size=args.buffer,
        log_file_path=args.log_file,
    )


if __name__ == "__main__":
    main()
