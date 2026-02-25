export enum DeviceStatus {
  ONLINE = 'ONLINE',
  OFFLINE = 'OFFLINE',
  WARNING = 'WARNING'
}

export interface Device {
  id: string;
  name: string;
  ip: string;
  port: number;
  brand: string;
  model: string;
  status: DeviceStatus;
  lastSeen: string;
  firmware?: string;
  rtspUrl?: string;
  username?: string;
  password?: string;
  /** 摄像头类型：ptz=球机, bullet=枪机, dome=半球, other=其他 */
  cameraType?: string;
  /** 设备序列号 */
  serialNumber?: string;
}

export interface Driver {
  id: string;
  name: string;
  version: string;
  status: 'ACTIVE' | 'INACTIVE';
  libPath: string;
  logPath: string;
  logLevel: number;
}

// 品牌预设配置
export interface BrandPreset {
  port: number;
  username: string;
  password: string;
}

// 通知渠道配置
export interface NotificationChannel {
  enabled: boolean;
  webhookUrl: string;
  secret?: string;  // 仅钉钉需要
}

export interface SystemConfig {
  scanner: {
    enabled: boolean;
    interval: number;
    ports: string;
    scanSegment?: string;
    scanRangeStart?: number;
    scanRangeEnd?: number;
  };
  auth: {
    timeout: number;
    presets: {
      hikvision: BrandPreset;
      tiandy: BrandPreset;
      dahua: BrandPreset;
    };
  };
  keeper: {
    enabled: boolean;
    checkInterval: number;
  };
  oss: {
    enabled: boolean;
    type: 'aliyun' | 'minio' | 'custom';
    endpoint: string;
    bucket?: string;
    accessKeyId?: string;
    accessKeySecret?: string;
  };
  log: {
    level: string;
    retentionDays: number;
  };
  notification: {
    wechat: NotificationChannel;
    dingtalk: NotificationChannel;
    feishu: NotificationChannel;
  };
  ai?: {
    enabled: boolean;
    provider: 'openrouter' | 'oneapi' | 'newapi';
    baseUrl: string;
    apiKey: string;
    defaultModel: string;
    ttsProvider: string;
    ttsApiKey: string;
    ttsGroupId: string;
    ttsModel: string;
    ttsVoice: string;
  };
}

export type ViewState = 
  | 'DASHBOARD' 
  | 'DEVICE_LIST' 
  | 'DEVICE_DETAIL' 
  | 'DRIVER_CONFIG' 
  | 'MQTT_CONFIG' 
  | 'SYSTEM_CONFIG'
  | 'ASSEMBLY_MANAGEMENT'
  | 'ASSEMBLY_DETAIL'
  | 'ALARM_RULES'
  | 'RADAR'
  | 'WORKFLOW'
  | 'AI_ANALYSIS';

export interface AppState {
  currentView: ViewState;
  selectedDeviceId?: string;
}

export type Language = 'en' | 'zh';

// 设备角色枚举
export enum DeviceRole {
  LEFT_CAMERA = 'left_camera',
  RIGHT_CAMERA = 'right_camera',
  TOP_CAMERA = 'top_camera',
  SPEAKER = 'speaker',
  RADAR = 'radar'
}

// 报警类型枚举（保留用于兼容旧代码）
export enum AlarmType {
  HELMET_DETECTION = 'helmet_detection',
  VEST_DETECTION = 'vest_detection',
  VEHICLE_ALARM = 'vehicle_alarm',
  INPUT_PORT = 'input_port',
  RADAR_POINTCLOUD = 'radar_pointcloud'
}

// 摄像头事件类型（从后端获取）
export interface CameraEventType {
  id: number;
  brand: 'hikvision' | 'tiandy' | 'dahua';
  eventCode: number;
  eventName: string;
  eventNameEn?: string;
  category: 'basic' | 'vca' | 'face' | 'its';
  description?: string;
  enabled?: boolean;
  // 新字段：支持标准事件映射
  sourceKind?: string; // 事件来源类型：'alarm_type' | 'vca_event' | 'command' 等
  eventKey?: string; // 标准事件键（如 'ALARM_INPUT', 'PERIMETER_INTRUSION' 等）
}

// 规则范围枚举
export enum RuleScope {
  GLOBAL = 'global',
  ASSEMBLY = 'assembly',
  DEVICE = 'device'
}

// 装置设备接口
export interface AssemblyDevice {
  deviceId: string;
  deviceName: string;
  role: DeviceRole;
  positionInfo?: string; // JSON格式的位置信息
}

// 装置接口
export interface Assembly {
  id: string;
  assemblyId: string;
  name: string;
  description?: string;
  location?: string;
  status: 'active' | 'inactive';
  /** 是否开启 PTZ 联动（雷达侵入时联动球机） */
  ptzLinkageEnabled?: boolean;
  /** 经度（WGS84） */
  longitude?: number;
  /** 纬度（WGS84） */
  latitude?: number;
  deviceCount?: number;
  devices?: AssemblyDevice[];
  createdAt?: string;
  updatedAt?: string;
}

// 报警规则动作配置
export interface AlarmRuleActions {
  capture: boolean; // 自动抓拍
  record: boolean; // 录像下载
  recordDuration?: number; // 录像时长（秒）
  upload: boolean; // 上传到OSS
  speaker: boolean; // 音柱播报
  mqtt: boolean; // MQTT上报
}

// 报警规则触发条件
export interface AlarmRuleConditions {
  minConfidence?: number; // 最小置信度 0-1
  area?: 'left' | 'right' | 'all'; // 区域限制
  distanceRange?: [number, number]; // 距离范围 [min, max] 米
}

// 报警规则接口
export interface AlarmRule {
  id: string;
  ruleId: string;
  name: string;
  alarmType?: AlarmType;  // 旧字段，保留兼容
  eventTypeIds?: number[];  // 新字段：选中的事件类型ID列表
  scope: RuleScope;
  deviceId?: string; // 设备级规则
  assemblyId?: string; // 装置级规则
  enabled: boolean;
  priority?: number;
  flowId?: string;  // 绑定的工作流程ID
  actions?: AlarmRuleActions;  // 旧字段，改为可选
  conditions?: AlarmRuleConditions;
  createdAt?: string;
  updatedAt?: string;
  // 显示用的关联信息
  deviceName?: string;
  assemblyName?: string;
  flowName?: string;
}

// 报警记录接口
export interface AlarmRecord {
  id: string;
  alarmId: string;
  deviceId: string;
  assemblyId?: string;
  alarmType: AlarmType;
  alarmLevel: 'warning' | 'critical' | 'info';
  channel?: number;
  alarmData?: string; // JSON格式的报警详细信息
  captureUrl?: string; // 抓拍图片URL
  videoUrl?: string; // 录像URL
  status?: 'pending' | 'processed' | 'ignored';
  mqttSent?: boolean;
  speakerTriggered?: boolean;
  recordedAt: string;
  processedAt?: string;
  // 显示用的关联信息
  deviceName?: string;
  assemblyName?: string;
}

// 流程定义
export interface FlowNodeDefinition {
  nodeId: string;
  type: string;
  config?: Record<string, any>;
}

export interface FlowConnection {
  from: string;
  to: string;
  fromPort?: 'default' | 'yes' | 'no';  // 用于条件分支
  condition?: string;
}

export interface AlarmFlow {
  id?: string;
  flowId: string;
  name: string;
  description?: string;
  flowType?: string;
  nodes: FlowNodeDefinition[];
  connections: FlowConnection[];
  isDefault?: boolean;
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

// 画布节点类型
export type FlowNodeType = 
  | 'event_trigger'   // 事件触发器（起始节点）
  | 'mqtt_subscribe'  // MQTT 订阅（起始节点，消息到达主题时触发）
  | 'condition'       // 条件判断 (if/else)
  | 'delay'           // 延迟 N 秒
  | 'capture'         // 抓拍
  | 'record'          // 录像
  | 'ptz_control'    // 云台控制（相对）
  | 'ptz_goto'        // 云台转预置点/角度
  | 'device_reboot'   // 设备重启
  | 'radar_zone_toggle' // 雷达防区启用/关闭
  | 'mqtt_publish'    // MQTT上报
  | 'oss_upload'      // OSS上传
  | 'speaker_play'    // 音柱播报
  | 'webhook'         // Webhook推送（企业微信等）
  | 'http_request'    // 通用 HTTP 请求
  | 'ai_inference'    // AI 推理
  | 'ai_verify'       // AI 图片核验（分支）
  | 'ai_alert_text'   // AI 警示语生成
  | 'ai_tts'          // AI 语音合成
  | 'system_speaker'  // 系统喇叭广播
  | 'end';            // 结束节点

// 画布节点（包含位置信息）
export interface CanvasNode {
  id: string;
  type: FlowNodeType;
  label: string;
  x: number;
  y: number;
  config?: Record<string, any>;
}

// 画布连接线
export interface CanvasConnection {
  id: string;
  fromNodeId: string;
  toNodeId: string;
  fromPort: 'default' | 'yes' | 'no';  // default为普通节点，yes/no用于条件分支
  condition?: string;
}

// 工作流画布数据（用于完整序列化）
export interface CanvasFlowData {
  nodes: CanvasNode[];
  connections: CanvasConnection[];
}

// 内置组件定义
export interface FlowComponentDefinition {
  type: FlowNodeType;
  label: string;
  icon: string;
  category: 'trigger' | 'action' | 'logic' | 'output';
  defaultConfig?: Record<string, any>;
  hasConditionPorts?: boolean;  // 是否有条件分支端口(yes/no)
}