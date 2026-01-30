# 新标准全面整改方案 — 代码层排查报告

依据 [新标准全面整改方案](.cursor/plans/新标准全面整改方案_f5089943.plan.md) 与 [mqtt-message-body-spec.md](mqtt-message-body-spec.md)，对当前代码实现逐项核对后的结论与建议。

---

## 一、数据库

| 检查项 | 状态 | 位置/说明 |
|--------|------|------------|
| canonical_events 增加 event_id 列 | ✅ | CanonicalEventTable：CREATE 含 event_id INTEGER UNIQUE；ensureEventIdColumnAndBackfill 做 ALTER + 回填 |
| event_id 按 event_key 查询 | ✅ | CanonicalEventTable.getEventIdByEventKey(connection, eventKey) |
| event_id 回填数据源 | ✅ | 已改为从 mqtt-alarm-event-ids.csv 加载：优先 docs/、../docs/，其次 classpath；UPDATE 已有 event_key，INSERT 新 event_key，与最新 CSV 对齐 |
| devices 增加 camera_type、serial_number | ✅ | Database.createTables ALTER；DeviceInfo 字段；saveOrUpdateDevice INSERT 含两列；mapResultSetToDevice 读取 |
| 新设备默认 device_id 为虚拟 ID | ✅ | DeviceScanner 与 DeviceController.addDevice 均使用 `v_` + UUID |
| 国标 ID 由用户设置、建议接口 | ✅ | Database.setDeviceGbId、suggestDeviceGbId；DeviceController GET suggest-gb-id、PUT :id/set-gb-id；关联表同步更新 |
| setDeviceGbId 同步关联表 | ✅ | device_history, alarm_history, workflow_execution_history, assembly_devices, alarm_rules, device_ptz_extension, device_event_subscriptions, radar_*, recording_tasks, speakers, alarm_records, radar_defense_zones 等 |
| assemblies 增加 longitude、latitude | ✅ | AssemblyTable ALTER；Assembly 实体；AssemblyService create/update 含两列；Assembly.fromResultSet / toMap |

---

## 二、配置与 MQTT 主题

| 检查项 | 状态 | 位置/说明 |
|--------|------|------------|
| config.yaml 默认主题 | ✅ | status_topic: senhub/device/status；command_topic: senhub/command；response_topic: senhub/response；gateway_status_topic: senhub/gateway/status；report_topic_prefix: senhub/report |
| Config.MqttConfig 字段与默认值 | ✅ | getStatusTopic/getCommandTopic/getResponseTopic/getGatewayStatusTopic/getReportTopicPrefix 默认与上一致 |
| ConfigService 读写新项 | ✅ | saveConfig / getConfig 含 gateway_status_topic、report_topic_prefix |

---

## 三、MQTT 客户端行为

| 检查项 | 状态 | 位置/说明 |
|--------|------|------------|
| 网关 ID 使用 MAC | ✅ | MqttClient 连接时 getLocalMacAddress() 赋给 gatewayId，失败时回退 clientId |
| LWT 设置 | ✅ | setWill(gateway_status_topic, buildGatewayStatusPayload("offline", "connection_lost")) |
| 连接成功后发布 online | ✅ | publish(gatewayTopic, buildGatewayStatusPayload("online", null)) |
| 订阅 command_topic | ✅ | connect() 内 subscribe(config.getCommandTopic()) |
| 订阅 mqtt_subscribe 主题 | ✅ | Main.setupMqttTopicHandler() 中 flowService.getMqttSubscribeTopics() 后 mqttClient.subscribe(topic) |
| 重连后 mqtt_subscribe 主题 | ✅ | MqttClient 增加 onConnectedCallback，connect/重连成功后执行；Main 在 setupMqttTopicHandler 中注册回调并抽取 subscribeMqttWorkflowTopics()，重连后自动重订工作流主题 |
| 按主题派发（command vs 工作流） | ✅ | topicMessageHandler：commandTopic → CommandHandler；否则 getFlowDefinitionsByMqttTopic(topic) → FlowExecutor.execute |

---

## 四、设备/雷达/装置状态发布

| 检查项 | 状态 | 位置/说明 |
|--------|------|------------|
| 设备状态主题与 payload | ✅ | DeviceManager / Main.publishDeviceStatus → mqttPublisher.publishStatus → status_topic；entity_type=camera；device_info 含 camera_type、serial_number |
| 雷达状态主题与 payload | ✅ | Main.publishRadarStatus → status_topic；entity_type=radar；radar_info |
| 装置状态主题与 payload | ✅ | Main.publishAssemblyStatus → senhub/assembly/{assemblyId}/status；assembly_id, type, timestamp, assembly_info（name, location, longitude, latitude, device_count, device_ids） |

---

## 五、报警与 event_id

| 检查项 | 状态 | 位置/说明 |
|--------|------|------------|
| buildFlowContext 写入 event_id、event_key | ✅ | AlarmService.buildFlowContext：payload.put("event_key", alarmType)；getEventIdByEventKey 后 put("event_id", eventId) |
| MqttPublishHandler 使用 context.payload | ✅ | payload.putAll(context.getPayload())，故 event_id/event_key 会出现在发布消息中 |
| 报警消息体字段命名 | ✅ | MqttPublishHandler 已同时写入 device_id、assembly_id（snake_case）与 deviceId、assemblyId（兼容） |

---

## 六、MqttPublisher 与降级

| 检查项 | 状态 | 位置/说明 |
|--------|------|------------|
| MqttPublisher 接口 | ✅ | publish, publishStatus, publishResponse, isConnected, getGatewayId |
| MqttClientAdapter | ✅ | 封装 MqttClient 实现接口 |
| FallbackMqttPublisher | ✅ | 仅写日志，不抛错 |
| DelegatingMqttPublisher | ✅ | 连接时用 primary，否则 fallback |
| 业务依赖 MqttPublisher | ✅ | Main、DeviceManager、AlarmService、MqttPublishHandler 使用 mqttPublisher；Main 命令响应处 publishResponse 走 mqttPublisher |

---

## 七、工作流 mqtt_subscribe

| 检查项 | 状态 | 位置/说明 |
|--------|------|------------|
| FlowDefinition.getStartNode 支持 mqtt_subscribe | ✅ | 优先返回 mqtt_subscribe 节点 |
| MqttSubscribeHandler | ✅ | 将 payload 中 device_id、assembly_id、command、params 写入 context |
| FlowService.getMqttSubscribeTopics | ✅ | 遍历启用流程，取 mqtt_subscribe 起始节点 config.topic 去重 |
| FlowService.getFlowDefinitionsByMqttTopic | ✅ | 按 topic 匹配起始节点 config.topic（已加固 cfg.get("topic") 为 null 时的 NPE 防护） |
| 前端 mqtt_subscribe 节点与配置 | ✅ | FlowManagement FLOW_COMPONENTS 含 mqtt_subscribe；配置面板含 topic、qos；types.ts FlowNodeType 含 mqtt_subscribe |

---

## 八、前端与 API

| 检查项 | 状态 | 位置/说明 |
|--------|------|------------|
| 设备 API 返回/更新 camera_type、serial_number | ✅ | DeviceController.convertDeviceToMap 含 cameraType、serialNumber；updateDevice 解析 body 并更新 |
| 国标 ID 设置与建议 | ✅ | DeviceConfig：suggestGbId、setDeviceGbId、国标 ID 输入与「设置国标 ID」 |
| 装置 API 与前端经纬度 | ✅ | AssemblyController update 解析 longitude、latitude；AssemblyManagement 表单与 formData 含两字段；types Assembly 含 longitude、latitude |

---

## 九、已修复问题

- **FlowService.getFlowDefinitionsByMqttTopic**：cfg.get("topic") 为 null 时会导致 NPE，已改为 `cfg.get("topic") instanceof String` 再 trim 与 equals。

---

## 十、建议后续处理

- 重连后 mqtt_subscribe 主题：已通过 MqttClient.onConnectedCallback 在连接/重连成功后重订工作流主题。
- event_id 回填与 CSV 一致：已改为从 mqtt-alarm-event-ids.csv 加载（优先 docs/、../docs/，其次 classpath），UPDATE 已有行、INSERT 新 event_key，与最新 CSV 对齐；resources 中已放置副本供打包使用。

---

*排查基于当前代码与计划/规范逐项对照，如有实现变更请同步更新本清单。*
