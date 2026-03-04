// API配置文件

/**
 * 动态解析 API 基础地址：
 * 1. 优先使用构建时注入的环境变量 VITE_API_URL（适合有 nginx 反代的场景）
 * 2. 否则使用运行时当前页面的 origin + /api，保证 SPA 静态文件部署到任何机器时
 *    都能正确请求到同一台机器的后端，无需 nginx 反代
 */
function resolveBaseUrl(): string {
  if (import.meta.env.VITE_API_URL) {
    return import.meta.env.VITE_API_URL;
  }
  // 运行时动态取当前域名/IP，适配直接部署（无 nginx）的工控机场景
  if (typeof window !== 'undefined') {
    return `${window.location.origin}/api`;
  }
  return '/api';
}

export const API_CONFIG = {
  BASE_URL: resolveBaseUrl(),
  // Token存储键名
  TOKEN_KEY: 'nvr_auth_token',
  // 请求超时时间（毫秒）
  TIMEOUT: 30000,
};
