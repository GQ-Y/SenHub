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
  | 'SYSTEM_CONFIG';

export interface AppState {
  currentView: ViewState;
  selectedDeviceId?: string;
}

export type Language = 'en' | 'zh';