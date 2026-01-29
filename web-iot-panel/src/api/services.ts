/// <reference types="vite/client" />
import { get, post, put, del, setToken } from './client';
import { API_CONFIG } from './config';
import { Device, Driver, SystemConfig, Assembly, AssemblyDevice, DeviceRole, AlarmRule, AlarmType, RuleScope, AlarmRecord, DeviceStatus, AlarmFlow, CameraEventType } from '../../types';

/**
 * 助手函数：转换后端设备数据格式到前端格式
 */
const mapDevice = (d: any): Device => {
  if (!d) return d;
  return {
    ...d,
    id: d.id || d.device_id || `${d.ip}:${d.port}`,
    status: (d.status === 1 || d.status === '1' || d.status === 'ONLINE')
      ? DeviceStatus.ONLINE
      : (d.status === 'WARNING' ? DeviceStatus.WARNING : DeviceStatus.OFFLINE)
  };
};

/**
 * 助手函数：转换后端装置数据格式到前端格式
 */
const mapAssembly = (a: any): Assembly => {
  if (!a) return a;
  return {
    ...a,
    id: a.id || a.assemblyId,
    status: (a.status === 1 || a.status === '1' || a.status === 'active') ? 'active' : 'inactive'
  };
};

// ==================== 认证服务 ====================
export const authService = {
  /**
   * 用户登录
   */
  async login(username: string, password: string) {
    const response = await post<{ token: string; username: string; expiresIn: number }>(
      '/auth/login',
      { username, password },
      { skipAuth: true }
    );

    if (response.data.token) {
      setToken(response.data.token);
    }

    return response;
  },
};

// ==================== 设备服务 ====================
export const deviceService = {
  /**
   * 获取设备列表
   */
  async getDevices(params?: { search?: string; status?: string }) {
    const response = await get<any[]>('/devices', params);
    if (response.data && Array.isArray(response.data)) {
      response.data = response.data.map(mapDevice);
    }
    return response as any;
  },

  /**
   * 获取设备详情
   */
  async getDevice(deviceId: string) {
    const response = await get<any>(`/devices/${encodeURIComponent(deviceId)}`);
    if (response.data) {
      response.data = mapDevice(response.data);
    }
    return response as any;
  },

  /**
   * 添加设备
   */
  async addDevice(device: Partial<Device>) {
    const response = await post<Device>('/devices', device);
    return response;
  },

  /**
   * 更新设备
   */
  async updateDevice(deviceId: string, device: Partial<Device>) {
    const response = await put<Device>(`/devices/${encodeURIComponent(deviceId)}`, device);
    return response;
  },

  /**
   * 删除设备
   */
  async deleteDevice(deviceId: string) {
    const response = await del<{ message: string }>(`/devices/${encodeURIComponent(deviceId)}`);
    return response;
  },

  /**
   * 建议一个可用的设备国标 20 位 ID（供前端「自动生成」按钮使用）
   */
  async suggestGbId() {
    const response = await get<{ suggested_gb_id: string }>('/devices/suggest-gb-id');
    return response;
  },

  /**
   * 用户主动设置设备国标 ID（将当前 device_id 更新为 20 位国标 ID）
   */
  async setDeviceGbId(deviceId: string, gbId: string) {
    const response = await put<Device>(`/devices/${encodeURIComponent(deviceId)}/set-gb-id`, { gb_id: gbId });
    return response;
  },

  /**
   * 重启设备
   */
  async rebootDevice(deviceId: string) {
    const response = await post<{ message: string }>(`/devices/${encodeURIComponent(deviceId)}/reboot`);
    return response;
  },

  /**
   * 获取设备快照
   */
  async captureSnapshot(deviceId: string, channel: number = 1) {
    const response = await post<{ url: string; filePath: string; timestamp: string }>(`/devices/${encodeURIComponent(deviceId)}/snapshot`, { channel });
    return response;
  },

  /**
   * PTZ控制
   * @param deviceId 设备ID
   * @param command PTZ命令 (up/down/left/right/zoom_in/zoom_out)
   * @param action 动作 (start/stop)
   * @param speed 速度 (1-7)
   */
  async ptzControl(deviceId: string, command: string, action: string, speed: number = 1) {
    const response = await post<{ message: string }>(`/devices/${encodeURIComponent(deviceId)}/ptz`, { command, action, speed });
    return response;
  },

  /**
   * PTZ绝对定位控制
   * @param deviceId 设备ID
   * @param pan 水平角度 (0-360°)
   * @param tilt 垂直角度
   * @param zoom 变倍 (默认1.0)
   */
  async ptzGoto(deviceId: string, pan: number, tilt: number, zoom: number = 1.0) {
    const response = await post<{ message: string; pan: number; tilt: number; zoom: number }>(
      `/devices/${encodeURIComponent(deviceId)}/ptz/goto`,
      { pan, tilt, zoom }
    );
    return response;
  },

  /**
   * 获取设备PTZ位置
   * @param deviceId 设备ID
   */
  async getPtzPosition(deviceId: string) {
    const response = await get<{
      deviceId: string;
      ptzEnabled: boolean;
      pan: number;
      tilt: number;
      zoom: number;
      azimuth: number;
      horizontalFov: number;
      verticalFov: number;
      visibleRadius: number;
      lastUpdated: string | null;
      message?: string;
    }>(`/devices/${encodeURIComponent(deviceId)}/ptz/position`);
    return response;
  },

  /**
   * 刷新设备PTZ位置
   * @param deviceId 设备ID
   */
  async refreshPtzPosition(deviceId: string) {
    const response = await post<{
      message: string;
      pan: number;
      tilt: number;
      zoom: number;
    }>(`/devices/${encodeURIComponent(deviceId)}/ptz/refresh`);
    return response;
  },

  /**
   * 设置PTZ监控开关
   * @param deviceId 设备ID
   * @param enabled 是否启用
   */
  async setPtzMonitor(deviceId: string, enabled: boolean) {
    const response = await put<{
      deviceId: string;
      ptzEnabled: boolean;
      message: string;
    }>(`/devices/${encodeURIComponent(deviceId)}/ptz/monitor`, { enabled });
    return response;
  },

  /**
   * 获取视频流地址（返回最新录制的视频）
   */
  async getStreamUrl(deviceId: string) {
    const response = await get<{ videoUrl: string; streamType: string; rtspUrl?: string }>(`/devices/${encodeURIComponent(deviceId)}/stream`);
    return response;
  },

  /**
   * 录像回放（启动下载）
   */
  async playback(deviceId: string, startTime: string, endTime: string, channel: number = 1) {
    const response = await post<{ downloadHandle: number; filePath: string; channel: number; startTime: string; endTime: string; message: string }>(`/devices/${encodeURIComponent(deviceId)}/playback`, { startTime, endTime, channel });
    return response;
  },

  /**
   * 查询录像下载进度
   */
  async getPlaybackProgress(deviceId: string, downloadHandle: number) {
    const response = await get<{ downloadHandle: number; progress: number; isCompleted: boolean; isError: boolean }>(`/devices/${encodeURIComponent(deviceId)}/playback/progress`, { downloadHandle: downloadHandle.toString() });
    return response;
  },

  /**
   * 获取已下载的录像文件URL
   */
  getPlaybackFileUrl(deviceId: string, filePath: string): string {
    // 统一使用 API_CONFIG.BASE_URL
    const apiBaseUrl = API_CONFIG.BASE_URL;

    // 移除末尾的斜杠和 /api，因为我们要手动添加正确的路径
    const baseUrl = apiBaseUrl.replace(/\/api$/, '').replace(/\/$/, '');

    return `${baseUrl}/api/devices/${encodeURIComponent(deviceId)}/playback/file?filePath=${encodeURIComponent(filePath)}`;
  },

  /**
   * 停止录像下载
   */
  async stopPlayback(deviceId: string, downloadHandle: number) {
    const response = await post<{ downloadHandle: number; stopped: boolean; message: string }>(`/devices/${encodeURIComponent(deviceId)}/playback/stop?downloadHandle=${downloadHandle}`);
    return response;
  },

  /**
   * 导出录像
   */
  async exportVideo(deviceId: string, filePath: string) {
    const response = await post<{ downloadUrl: string; fileName: string; fileSize: number }>(`/devices/${encodeURIComponent(deviceId)}/export`, { filePath });
    return response;
  },

  /**
   * 获取支持的品牌列表
   */
  async getBrands() {
    const response = await get<{ supported: string[]; default: string }>('/devices/brands');
    return response;
  },

  /**
   * 获取设备配置（装置关联、规则等）
   */
  async getDeviceConfig(deviceId: string) {
    const response = await get<{
      assemblies: Assembly[];
      rules: AlarmRule[];
      role?: DeviceRole;
      extensionData?: string;
    }>(`/devices/${encodeURIComponent(deviceId)}/config`);
    return response;
  },

  /**
   * 更新设备配置
   */
  async updateDeviceConfig(deviceId: string, config: {
    assemblyId?: string;
    role?: DeviceRole;
    positionInfo?: string;
    extensionData?: string;
  }) {
    const response = await put<{ message: string }>(`/devices/${encodeURIComponent(deviceId)}/config`, config);
    return response;
  },

  /**
   * 获取设备所属的装置列表
   */
  async getDeviceAssemblies(deviceId: string) {
    const response = await get<Assembly[]>(`/devices/${encodeURIComponent(deviceId)}/assemblies`);
    return response;
  },
};

// ==================== 流程管理 ====================
export const flowService = {
  async listFlows() {
    const response = await get<AlarmFlow[]>('/flows');
    return response;
  },

  async getFlow(flowId: string) {
    const response = await get<AlarmFlow>(`/flows/${encodeURIComponent(flowId)}`);
    return response;
  },

  async createFlow(payload: Partial<AlarmFlow>) {
    const response = await post<AlarmFlow>('/flows', payload);
    return response;
  },

  async updateFlow(flowId: string, payload: Partial<AlarmFlow>) {
    const response = await put<AlarmFlow>(`/flows/${encodeURIComponent(flowId)}`, payload);
    return response;
  },

  async deleteFlow(flowId: string) {
    const response = await del<{ message: string }>(`/flows/${encodeURIComponent(flowId)}`);
    return response;
  },

  async testFlow(flowId: string) {
    const response = await post<{ message: string }>(`/flows/${encodeURIComponent(flowId)}/test`);
    return response;
  },
};

// ==================== 驱动服务 ====================
export const driverService = {
  /**
   * 获取驱动列表
   */
  async getDrivers() {
    const response = await get<Driver[]>('/drivers');
    return response;
  },

  /**
   * 获取驱动详情
   */
  async getDriver(driverId: string) {
    const response = await get<Driver>(`/drivers/${encodeURIComponent(driverId)}`);
    return response;
  },

  /**
   * 添加驱动
   */
  async addDriver(driver: Partial<Driver>) {
    const response = await post<Driver>('/drivers', driver);
    return response;
  },

  /**
   * 更新驱动
   */
  async updateDriver(driverId: string, driver: Partial<Driver>) {
    const response = await put<Driver>(`/drivers/${encodeURIComponent(driverId)}`, driver);
    return response;
  },

  /**
   * 删除驱动
   */
  async deleteDriver(driverId: string) {
    const response = await del<{ message: string }>(`/drivers/${encodeURIComponent(driverId)}`);
    return response;
  },

  /**
   * 检查SDK文件健康状态
   */
  async checkDriver(driverId: string) {
    const response = await get<{
      libPath: {
        exists: boolean;
        isDirectory: boolean;
        isFile: boolean;
        readable: boolean;
        writable: boolean;
        executable: boolean;
        error?: string;
      };
      logPath: {
        exists: boolean;
        isDirectory: boolean;
        isFile: boolean;
        readable: boolean;
        writable: boolean;
        error?: string;
      };
    }>(`/drivers/${encodeURIComponent(driverId)}/check`);
    return response;
  },

  /**
   * 一键检查所有SDK健康状态
   */
  async checkAllDrivers() {
    const response = await get<Array<{
      driverId: string;
      driverName: string;
      health: {
        libPath: {
          exists: boolean;
          isDirectory: boolean;
          isFile: boolean;
          readable: boolean;
          writable: boolean;
          executable: boolean;
          error?: string;
        };
        logPath: {
          exists: boolean;
          isDirectory: boolean;
          isFile: boolean;
          readable: boolean;
          writable: boolean;
          error?: string;
        };
      };
    }>>('/drivers/check-all');
    return response;
  },

  /**
   * 获取统一的驱动日志
   */
  async getDriverLogs(lines?: number) {
    const params = lines ? { lines: lines.toString() } : undefined;
    const response = await get<{
      file: string;
      lines: number;
      content: string[];
    }>('/drivers/logs', params);
    return response;
  },
};

// ==================== MQTT服务 ====================
export interface MqttConfig {
  host: string;
  port: string;
  clientId: string;
  username?: string;
  password?: string;
  topicStatus: string;
  topicCommand: string;
  qos: number;
  connected?: boolean;
}

export const mqttService = {
  /**
   * 获取MQTT配置
   */
  async getConfig() {
    const response = await get<MqttConfig>('/mqtt/config');
    return response;
  },

  /**
   * 更新MQTT配置
   */
  async updateConfig(config: Partial<MqttConfig>) {
    const response = await put<MqttConfig>('/mqtt/config', config);
    return response;
  },

  /**
   * 测试MQTT连接
   */
  async testConnection() {
    const response = await post<{ connected: boolean; message: string }>('/mqtt/test');
    return response;
  },
};

// ==================== 系统服务 ====================
export const systemService = {
  /**
   * 获取系统配置
   */
  async getConfig() {
    const response = await get<SystemConfig>('/system/config');
    return response;
  },

  /**
   * 更新系统配置
   */
  async updateConfig(config: Partial<SystemConfig>) {
    const response = await put<SystemConfig>('/system/config', config);
    return response;
  },

  /**
   * 系统健康检查
   */
  async healthCheck() {
    const response = await get<{
      status: string;
      mqtt: { connected: boolean };
      database: { status: string };
      sdk: { status: string };
      disk: { freeSpace: string; totalSpace: string; usagePercent: string };
    }>('/system/health');
    return response;
  },

  /**
   * 重启MQTT连接
   */
  async restartMqtt() {
    const response = await post<{ success: boolean; message: string }>('/system/mqtt/restart');
    return response;
  },

  /**
   * 获取系统日志
   */
  async getLogs(lines?: number) {
    const response = await get<{ file: string; lines: number; content: string[] }>('/system/logs', lines ? { lines: lines.toString() } : undefined);
    return response;
  },

  /**
   * 测试通知发送
   */
  async testNotification(channel: 'wechat' | 'dingtalk' | 'feishu', config: { webhookUrl: string; secret?: string }) {
    const response = await post<{ success: boolean; message: string }>(`/system/notification/test`, {
      channel,
      ...config,
    });
    return response;
  },
};

// ==================== 仪表板服务 ====================
export interface DashboardStats {
  activeDevices: number;
  onlineStatus: string;
  alerts24h: number;
  storageUsed: string;
  storageTotal?: string;
  storagePercent?: string;
}

export interface ChartData {
  name: string;
  online: number;
  offline: number;
}

export const dashboardService = {
  /**
   * 获取统计数据
   */
  async getStats() {
    const response = await get<DashboardStats>('/dashboard/stats');
    return response;
  },

  /**
   * 获取图表数据（如果后端支持）
   */
  async getChartData() {
    try {
      const response = await get<ChartData[]>('/dashboard/chart');
      return response;
    } catch (error) {
      // 如果后端不支持，返回空数组
      return { code: 200, data: [], message: 'success' };
    }
  },
};

// ==================== 装置服务 ====================
export const assemblyService = {
  /**
   * 获取装置列表
   */
  async getAssemblies(params?: { search?: string; status?: string }) {
    const response = await get<any[]>('/assemblies', params);
    if (response.data && Array.isArray(response.data)) {
      response.data = response.data.map(mapAssembly);
    }
    return response as any;
  },

  /**
   * 获取装置详情
   */
  async getAssembly(assemblyId: string) {
    const response = await get<any>(`/assemblies/${encodeURIComponent(assemblyId)}`);
    if (response.data) {
      response.data = mapAssembly(response.data);
    }
    return response as any;
  },

  /**
   * 创建装置
   */
  async createAssembly(assembly: Partial<Assembly>) {
    const response = await post<Assembly>('/assemblies', assembly);
    return response;
  },

  /**
   * 更新装置
   */
  async updateAssembly(assemblyId: string, assembly: Partial<Assembly>) {
    const response = await put<Assembly>(`/assemblies/${encodeURIComponent(assemblyId)}`, assembly);
    return response;
  },

  /**
   * 删除装置
   */
  async deleteAssembly(assemblyId: string) {
    const response = await del<{ message: string }>(`/assemblies/${encodeURIComponent(assemblyId)}`);
    return response;
  },

  /**
   * 添加设备到装置
   */
  async addDeviceToAssembly(assemblyId: string, deviceId: string, role: DeviceRole, positionInfo?: string) {
    const response = await post<AssemblyDevice>(`/assemblies/${encodeURIComponent(assemblyId)}/devices`, {
      deviceId,
      role,
      positionInfo,
    });
    return response;
  },

  /**
   * 从装置移除设备
   */
  async removeDeviceFromAssembly(assemblyId: string, deviceId: string) {
    const response = await del<{ message: string }>(
      `/assemblies/${encodeURIComponent(assemblyId)}/devices/${encodeURIComponent(deviceId)}`
    );
    return response;
  },

  /**
   * 获取装置的所有设备
   */
  async getAssemblyDevices(assemblyId: string) {
    const response = await get<AssemblyDevice[]>(`/assemblies/${encodeURIComponent(assemblyId)}/devices`);
    return response;
  },
};

// ==================== 事件类型服务 ====================
export const eventTypeService = {
  /**
   * 获取所有事件类型（按品牌分组）
   */
  async getEventTypes() {
    const response = await get<Record<string, CameraEventType[]>>('/event-types');
    return response;
  },

  /**
   * 获取指定品牌的事件类型
   */
  async getEventTypesByBrand(brand: string) {
    const response = await get<CameraEventType[]>(`/event-types/${encodeURIComponent(brand)}`);
    return response;
  },

  /**
   * 获取所有事件类型（平铺列表）
   */
  async getAllEventTypesList() {
    const response = await get<CameraEventType[]>('/event-types/all');
    return response;
  },
};

// ==================== 报警规则服务 ====================
export const alarmRuleService = {
  /**
   * 获取规则列表（支持过滤）
   */
  async getAlarmRules(params?: { deviceId?: string; assemblyId?: string; alarmType?: AlarmType; enabled?: boolean }) {
    const response = await get<AlarmRule[]>('/alarm-rules', params);
    return response;
  },

  /**
   * 获取规则详情
   */
  async getAlarmRule(ruleId: string) {
    const response = await get<AlarmRule>(`/alarm-rules/${encodeURIComponent(ruleId)}`);
    return response;
  },

  /**
   * 创建规则
   */
  async createAlarmRule(rule: Partial<AlarmRule>) {
    const response = await post<AlarmRule>('/alarm-rules', rule);
    return response;
  },

  /**
   * 更新规则
   */
  async updateAlarmRule(ruleId: string, rule: Partial<AlarmRule>) {
    const response = await put<AlarmRule>(`/alarm-rules/${encodeURIComponent(ruleId)}`, rule);
    return response;
  },

  /**
   * 删除规则
   */
  async deleteAlarmRule(ruleId: string) {
    const response = await del<{ message: string }>(`/alarm-rules/${encodeURIComponent(ruleId)}`);
    return response;
  },

  /**
   * 启用/禁用规则
   */
  async toggleRule(ruleId: string, enabled: boolean) {
    const response = await put<AlarmRule>(`/alarm-rules/${encodeURIComponent(ruleId)}/toggle`, { enabled });
    return response;
  },

  /**
   * 获取设备的所有规则
   */
  async getDeviceRules(deviceId: string) {
    const response = await get<AlarmRule[]>(`/devices/${encodeURIComponent(deviceId)}/alarm-rules`);
    return response;
  },

  /**
   * 获取装置的所有规则
   */
  async getAssemblyRules(assemblyId: string) {
    const response = await get<AlarmRule[]>(`/assemblies/${encodeURIComponent(assemblyId)}/alarm-rules`);
    return response;
  },
};

// ==================== 报警记录服务 ====================
export const alarmRecordService = {
  /**
   * 获取报警记录列表
   */
  async getAlarmRecords(params?: {
    deviceId?: string;
    assemblyId?: string;
    alarmType?: AlarmType;
    startTime?: string;
    endTime?: string;
    limit?: number;
  }) {
    const response = await get<AlarmRecord[]>('/alarm-records', params);
    return response;
  },

  /**
   * 获取报警记录详情
   */
  async getAlarmRecord(recordId: string) {
    const response = await get<AlarmRecord>(`/alarm-records/${encodeURIComponent(recordId)}`);
    return response;
  },
};

// ==================== 雷达服务 ====================
export const radarService = {
  /**
   * 获取雷达设备列表
   */
  async getRadarDevices() {
    const response = await get<any[]>('/radar/devices');
    return response;
  },

  /**
   * 添加雷达设备
   */
  async addRadarDevice(device: { deviceId?: string; radarIp: string; radarName?: string; assemblyId?: string; radarSerial?: string }) {
    const response = await post<any>('/radar/devices', device);
    return response;
  },

  /**
   * 更新雷达设备
   */
  async updateRadarDevice(deviceId: string, device: { radarIp?: string; radarName?: string; assemblyId?: string }) {
    const response = await put<any>(`/radar/devices/${encodeURIComponent(deviceId)}`, device);
    return response;
  },

  /**
   * 删除雷达设备（级联删除所有关联数据）
   */
  async deleteRadarDevice(deviceId: string) {
    const response = await del<any>(`/radar/devices/${encodeURIComponent(deviceId)}`);
    return response;
  },

  /**
   * 测试雷达连通性（获取序列号）
   */
  async testRadarConnection(ip: string) {
    const response = await get<any>('/radar/test', { ip });
    return response;
  },

  /**
   * 开始采集背景
   */
  async startBackgroundCollection(deviceId: string, config: { durationSeconds?: number; gridResolution?: number }) {
    const response = await post<any>(`/radar/${encodeURIComponent(deviceId)}/background/start`, config);
    return response;
  },

  /**
   * 停止采集背景
   */
  async stopBackgroundCollection(deviceId: string) {
    const response = await post<any>(`/radar/${encodeURIComponent(deviceId)}/background/stop`);
    return response;
  },

  /**
   * 获取采集状态
   */
  async getBackgroundStatus(deviceId: string) {
    const response = await get<any>(`/radar/${encodeURIComponent(deviceId)}/background/status`);
    return response;
  },

  /**
   * 获取采集中的点云数据（用于实时预览）
   * 注意：实时点云数据应通过WebSocket获取
   */
  async getCollectingPointCloud(deviceId: string, maxPoints?: number) {
    const params = maxPoints ? { maxPoints } : undefined;
    const response = await get<any>(`/radar/${encodeURIComponent(deviceId)}/background/collecting/points`, params);
    return response;
  },

  /**
   * 获取背景点云数据（从文件读取）
   */
  async getBackgroundPoints(deviceId: string, backgroundId: string, maxPoints?: number) {
    const params = maxPoints ? { maxPoints } : undefined;
    const response = await get<any>(`/radar/${encodeURIComponent(deviceId)}/background/${encodeURIComponent(backgroundId)}/points`, params);
    return response;
  },

  /**
   * 获取防区列表
   */
  async getZones(deviceId: string) {
    const response = await get<any[]>(`/radar/${encodeURIComponent(deviceId)}/zones`);
    return response;
  },

  /**
   * 创建防区
   */
  async createZone(deviceId: string, zone: any) {
    const response = await post<any>(`/radar/${encodeURIComponent(deviceId)}/zones`, zone);
    return response;
  },

  /**
   * 更新防区
   */
  async updateZone(deviceId: string, zoneId: string, zone: any) {
    const response = await put<any>(`/radar/${encodeURIComponent(deviceId)}/zones/${encodeURIComponent(zoneId)}`, zone);
    return response;
  },

  /**
   * 删除防区
   */
  async deleteZone(deviceId: string, zoneId: string) {
    const response = await del<any>(`/radar/${encodeURIComponent(deviceId)}/zones/${encodeURIComponent(zoneId)}`);
    return response;
  },

  /**
   * 切换防区启用状态
   */
  async toggleZone(deviceId: string, zoneId: string) {
    const response = await put<any>(`/radar/${encodeURIComponent(deviceId)}/zones/${encodeURIComponent(zoneId)}/toggle`);
    return response;
  },

  /**
   * 获取背景模型列表
   */
  async getBackgrounds(deviceId: string) {
    const response = await get<any[]>(`/radar/${encodeURIComponent(deviceId)}/backgrounds`);
    return response;
  },

  /**
   * 删除背景模型
   */
  async deleteBackground(deviceId: string, backgroundId: string) {
    const response = await del<void>(`/radar/${encodeURIComponent(deviceId)}/backgrounds/${encodeURIComponent(backgroundId)}`);
    return response;
  },

  /**
   * 获取侵入记录列表
   */
  async getIntrusions(deviceId: string, params?: { zoneId?: string; startTime?: string; endTime?: string; page?: number; pageSize?: number }) {
    const response = await get<any>(`/radar/${encodeURIComponent(deviceId)}/intrusions`, params);
    return response;
  },

  /**
   * 清空侵入记录
   */
  async clearIntrusions(deviceId: string) {
    const response = await del<{ deletedCount: number }>(`/radar/${encodeURIComponent(deviceId)}/intrusions`);
    return response;
  },

  /**
   * 获取侵入记录的轨迹数据
   */
  async getIntrusionData(recordId: string) {
    const response = await get<any[]>(`/radar/intrusions/${encodeURIComponent(recordId)}/data`);
    return response;
  },

  /**
   * WebSocket连接（需要在组件中实现）
   */
  connectWebSocket(deviceId: string, onMessage: (data: any) => void) {
    // 从API配置获取基础URL（去掉/api前缀，因为WebSocket路径是完整的）
    // Spark WebSocket不支持路径参数，使用查询参数传递deviceId
    const apiBaseUrl = import.meta.env.VITE_API_URL || 'http://192.168.1.210:8084/api';
    const baseUrl = apiBaseUrl.replace('/api', '').replace('http://', '').replace('https://', '');
    const protocol = apiBaseUrl.startsWith('https') ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${baseUrl}/api/radar/stream?deviceId=${encodeURIComponent(deviceId)}`;

    console.log('正在连接WebSocket:', wsUrl);
    const ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      console.log('Radar WebSocket连接成功:', deviceId);
      // 发送订阅消息（可选）
      try {
        ws.send(JSON.stringify({ type: 'subscribe', topics: ['pointcloud', 'intrusion', 'status'] }));
      } catch (e) {
        console.warn('发送订阅消息失败（可能不需要）', e);
      }
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        onMessage(data);
      } catch (e) {
        console.error('解析WebSocket消息失败', e, event.data);
      }
    };

    ws.onerror = (error) => {
      console.error('Radar WebSocket错误:', error);
      // 尝试重新连接
      setTimeout(() => {
        if (ws.readyState === WebSocket.CLOSED) {
          console.log('尝试重新连接WebSocket...');
          // 注意：这里不自动重连，由组件管理重连逻辑
        }
      }, 3000);
    };

    ws.onclose = (event) => {
      console.log('Radar WebSocket关闭:', event.code, event.reason);
    };

    return ws;
  },
};

// ==================== 扫描服务 ====================
export interface ScanSession {
  sessionId: string;
  status: 'scanning' | 'completed' | 'error';
  totalScanned: number;
  successCount: number;
  failedCount: number;
  devices: ScannedDevice[];
  errorMessage?: string;
  startTime: number;
  endTime?: number;
}

export interface ScannedDevice {
  ip: string;
  port: number;
  name: string;
  brand: string;
  loginSuccess: boolean;
  errorMessage?: string;
  userId: number;
}

export const scannerService = {
  /**
   * 启动扫描
   */
  async startScan() {
    const response = await post<{ sessionId: string; status: string }>('/scanner/start', {});
    return response;
  },

  /**
   * 获取扫描状态
   */
  async getScanStatus(sessionId: string) {
    const response = await get<ScanSession>(`/scanner/status/${encodeURIComponent(sessionId)}`);
    return response;
  },

  /**
   * 添加扫描到的设备到数据库
   */
  async addDevices(sessionId: string, deviceIps: string[]) {
    const response = await post<{ addedCount: number; skippedCount: number; failedCount: number; addedDevices: string[]; errors: string[] }>(
      '/scanner/add-devices',
      { sessionId, deviceIps }
    );
    return response;
  },
};

// ==================== 通知服务 ====================
export interface Notification {
  id: string;
  title: string;
  message: string;
  type: 'info' | 'success' | 'warning' | 'error';
  read: boolean;
  time: string;
}

export const notificationService = {
  /**
   * 获取通知列表
   */
  async getNotifications(limit?: number) {
    const params = limit ? { limit: limit.toString() } : undefined;
    const response = await get<Notification[]>('/notifications', params);
    return response;
  },

  /**
   * 标记通知为已读
   */
  async markAsRead(notificationId: string) {
    const response = await post<{ message: string }>('/notifications/read', { id: notificationId });
    return response;
  },

  /**
   * 标记所有通知为已读
   */
  async markAllAsRead() {
    const response = await post<{ message: string; success: boolean }>('/notifications/read-all', {});
    return response;
  },
};

