// API服务模块
import { get, post, put, del, setToken } from './client';
import { Device, Driver, SystemConfig, Assembly, AssemblyDevice, DeviceRole, AlarmRule, AlarmType, RuleScope, AlarmRecord, DeviceStatus } from '../../types';

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
    const baseUrl = (import.meta as any).env?.VITE_API_URL || 'http://localhost:8080';
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
};

// ==================== 仪表板服务 ====================
export interface DashboardStats {
  activeDevices: number;
  onlineStatus: string;
  alerts24h: number;
  storageUsed: string;
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

