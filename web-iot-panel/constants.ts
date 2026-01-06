import { Device, DeviceStatus, Driver } from './types';

export const APP_NAME = "HarmonyGuard";

export const MOCK_DEVICES: Device[] = [
  {
    id: 'dev_001',
    name: 'Main Gate Cam 01',
    ip: '192.168.1.101',
    port: 8000,
    brand: 'Hikvision',
    model: 'DS-2CD2043G0-I',
    status: DeviceStatus.ONLINE,
    lastSeen: 'Just now',
    firmware: 'V5.6.5 build 200316',
    rtspUrl: 'rtsp://192.168.1.101:554/Streaming/Channels/101'
  },
  {
    id: 'dev_002',
    name: 'Parking Lot PTZ',
    ip: '192.168.1.105',
    port: 8000,
    brand: 'Dahua',
    model: 'SD49225T-HN',
    status: DeviceStatus.WARNING,
    lastSeen: '5 mins ago',
    firmware: 'V2.800.0000000.8.R',
    rtspUrl: 'rtsp://192.168.1.105:554/cam/realmonitor?channel=1&subtype=0'
  },
  {
    id: 'dev_003',
    name: 'Lobby Wide',
    ip: '192.168.1.110',
    port: 80,
    brand: 'Uniview',
    model: 'IPC3614LR3-PF28',
    status: DeviceStatus.OFFLINE,
    lastSeen: '2 days ago',
    firmware: 'IPC_G1204-B5019P11D1602',
    rtspUrl: 'rtsp://192.168.1.110:554/unicast/c1/s0/live'
  },
  {
    id: 'dev_004',
    name: 'Warehouse A',
    ip: '192.168.1.112',
    port: 8000,
    brand: 'Hikvision',
    model: 'DS-2CD2T43G0-I5',
    status: DeviceStatus.ONLINE,
    lastSeen: '1 min ago',
    firmware: 'V5.6.3 build 190923',
    rtspUrl: 'rtsp://192.168.1.112:554/Streaming/Channels/101'
  }
];

export const MOCK_DRIVERS: Driver[] = [
  {
    id: 'drv_hik',
    name: 'Hikvision SDK',
    version: '6.1.4.20',
    status: 'ACTIVE',
    libPath: '/usr/lib/hikvision/libhcnetsdk.so',
    logPath: '/var/log/hik_sdk.log',
    logLevel: 3
  },
  {
    id: 'drv_dahua',
    name: 'Dahua NetSDK',
    version: '3.051.0000003.1',
    status: 'INACTIVE',
    libPath: '/usr/lib/dahua/libdhnetsdk.so',
    logPath: '/var/log/dahua_sdk.log',
    logLevel: 1
  }
];

export const CHART_DATA = [
  { name: '00:00', online: 4, offline: 0 },
  { name: '04:00', online: 4, offline: 0 },
  { name: '08:00', online: 3, offline: 1 },
  { name: '12:00', online: 4, offline: 0 },
  { name: '16:00', online: 4, offline: 0 },
  { name: '20:00', online: 3, offline: 1 },
  { name: '24:00', online: 4, offline: 0 },
];