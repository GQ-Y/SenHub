// API服务模块
import { get, post, put, del, setToken } from './client';
import { Device, Driver, SystemConfig } from '../../types';

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
    const response = await get<Device[]>('/devices', params);
    return response;
  },

  /**
   * 获取设备详情
   */
  async getDevice(deviceId: string) {
    const response = await get<Device>(`/devices/${encodeURIComponent(deviceId)}`);
    return response;
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
    const response = await post<{ url: string }>(`/devices/${encodeURIComponent(deviceId)}/snapshot`, { channel });
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
