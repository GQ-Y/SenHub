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

export interface SystemConfig {
  scanner: { enabled: boolean; interval: number; ports: string };
  auth: { defaultUser: string; timeout: number };
  keeper: { enabled: boolean; checkInterval: number };
  oss: { enabled: boolean; endpoint: string; bucket: string };
  log: { level: string; retentionDays: number };
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
  | 'ALARM_RULES';

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

// 报警类型枚举
export enum AlarmType {
  HELMET_DETECTION = 'helmet_detection',
  VEST_DETECTION = 'vest_detection',
  VEHICLE_ALARM = 'vehicle_alarm',
  INPUT_PORT = 'input_port',
  RADAR_POINTCLOUD = 'radar_pointcloud'
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
  alarmType: AlarmType;
  scope: RuleScope;
  deviceId?: string; // 设备级规则
  assemblyId?: string; // 装置级规则
  enabled: boolean;
  priority?: number;
  actions: AlarmRuleActions;
  conditions?: AlarmRuleConditions;
  createdAt?: string;
  updatedAt?: string;
  // 显示用的关联信息
  deviceName?: string;
  assemblyName?: string;
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