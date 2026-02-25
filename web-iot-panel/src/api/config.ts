// API配置文件
export const API_CONFIG = {
  // 从环境变量读取API基础URL，如果没有则使用默认值
  BASE_URL: import.meta.env.VITE_API_URL || '/api',
  // Token存储键名
  TOKEN_KEY: 'nvr_auth_token',
  // 请求超时时间（毫秒）
  TIMEOUT: 30000,
};
