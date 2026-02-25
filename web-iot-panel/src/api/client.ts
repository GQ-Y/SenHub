// API客户端工具
import { API_CONFIG } from './config';

// API响应类型
export interface ApiResponse<T = any> {
  code: number;
  data: T;
  message: string;
}

// 请求选项
interface RequestOptions extends RequestInit {
  params?: Record<string, string | number | boolean>;
  skipAuth?: boolean;
}

// 认证状态变化回调类型
type AuthStateChangeCallback = (isAuthenticated: boolean) => void;

// 全局认证状态变化回调
let authStateChangeCallback: AuthStateChangeCallback | null = null;

/**
 * 设置认证状态变化回调
 */
export function setAuthStateChangeCallback(callback: AuthStateChangeCallback | null) {
  authStateChangeCallback = callback;
}

/**
 * 获取存储的token
 */
export function getToken(): string | null {
  return localStorage.getItem(API_CONFIG.TOKEN_KEY);
}

/**
 * 保存token
 */
export function setToken(token: string): void {
  localStorage.setItem(API_CONFIG.TOKEN_KEY, token);
}

/**
 * 清除token
 */
export function clearToken(): void {
  localStorage.removeItem(API_CONFIG.TOKEN_KEY);
}

/**
 * 构建查询字符串
 */
function buildQueryString(params: Record<string, string | number | boolean>): string {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    query.append(key, String(value));
  });
  const queryString = query.toString();
  return queryString ? `?${queryString}` : '';
}

/**
 * 统一的HTTP请求函数
 */
async function request<T = any>(
  endpoint: string,
  options: RequestOptions = {}
): Promise<ApiResponse<T>> {
  const { params, skipAuth = false, ...fetchOptions } = options;

  // 构建完整URL
  let url = `${API_CONFIG.BASE_URL}${endpoint}`;
  if (params) {
    url += buildQueryString(params);
  }

  // 设置请求头
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...fetchOptions.headers,
  };

  // 添加认证token（如果需要）
  if (!skipAuth) {
    const token = getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }

  // 创建AbortController用于超时控制
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), API_CONFIG.TIMEOUT);

  try {
    const response = await fetch(url, {
      ...fetchOptions,
      headers,
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    // 处理HTTP错误状态码
    if (!response.ok) {
      // 处理401未授权错误
      if (response.status === 401) {
        clearToken();
        // 通知认证状态变化
        if (authStateChangeCallback) {
          authStateChangeCallback(false);
        }
        // 如果不在登录页，触发导航事件（由AppContext处理）
        if (!window.location.pathname.includes('/login')) {
          // 使用自定义事件通知需要导航到登录页
          window.dispatchEvent(new CustomEvent('auth:unauthorized', { 
            detail: { path: window.location.pathname } 
          }));
        }
        throw new Error('未授权，请重新登录');
      }

      // 处理其他HTTP错误状态码
      let errorMessage = '请求失败';
      try {
        // 尝试解析错误响应
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
          const errorData: ApiResponse = await response.json();
          errorMessage = errorData.message || getHttpErrorMessage(response.status);
        } else {
          errorMessage = getHttpErrorMessage(response.status);
        }
      } catch (parseError) {
        // 如果解析失败，使用默认错误消息
        errorMessage = getHttpErrorMessage(response.status);
      }

      throw new Error(errorMessage);
    }

    // 解析响应
    let data: ApiResponse<T>;
    try {
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        data = await response.json();
      } else {
        // 如果不是JSON响应，可能是文件下载等，返回空数据
        throw new Error('响应格式不是JSON');
      }
    } catch (parseError: any) {
      // JSON解析失败
      if (parseError.message === '响应格式不是JSON') {
        throw parseError;
      }
      throw new Error('服务器响应格式错误，无法解析数据');
    }

    // 检查业务状态码（后端成功码：0或200）
    if (data.code !== 0 && data.code !== 200) {
      throw new Error(data.message || '请求失败');
    }

    return data;
  } catch (error: any) {
    clearTimeout(timeoutId);
    
    // 处理超时错误
    if (error.name === 'AbortError') {
      throw new Error('请求超时，请检查网络连接');
    }
    
    // 处理网络错误
    if (error instanceof TypeError && error.message.includes('fetch')) {
      throw new Error('网络连接失败，请检查网络设置');
    }
    
    // 其他错误直接抛出
    throw error;
  }
}

/**
 * GET请求
 */
export async function get<T = any>(
  endpoint: string,
  params?: Record<string, string | number | boolean>,
  options?: RequestOptions
): Promise<ApiResponse<T>> {
  return request<T>(endpoint, {
    ...options,
    method: 'GET',
    params,
  });
}

/**
 * POST请求
 */
export async function post<T = any>(
  endpoint: string,
  body?: any,
  options?: RequestOptions
): Promise<ApiResponse<T>> {
  return request<T>(endpoint, {
    ...options,
    method: 'POST',
    body: body ? JSON.stringify(body) : undefined,
  });
}

/**
 * PUT请求
 */
export async function put<T = any>(
  endpoint: string,
  body?: any,
  options?: RequestOptions
): Promise<ApiResponse<T>> {
  return request<T>(endpoint, {
    ...options,
    method: 'PUT',
    body: body ? JSON.stringify(body) : undefined,
  });
}

/**
 * DELETE请求
 */
export async function del<T = any>(
  endpoint: string,
  options?: RequestOptions
): Promise<ApiResponse<T>> {
  return request<T>(endpoint, {
    ...options,
    method: 'DELETE',
  });
}

/**
 * 带 Token 请求媒体 URL，返回 Blob URL（用于 img/audio/video 等无法带 Header 的场景）
 * 调用方在不用时需自行 revokeObjectURL 释放
 */
export async function fetchWithAuthAsBlobUrl(url: string): Promise<string> {
  const token = getToken();
  const fullUrl = url.startsWith('http') ? url : (url.startsWith('/') ? window.location.origin + url : window.location.origin + '/' + url);
  const res = await fetch(fullUrl, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) throw new Error(res.status === 401 ? '未授权，请重新登录' : `请求失败 ${res.status}`);
  const blob = await res.blob();
  return URL.createObjectURL(blob);
}

/**
 * 根据HTTP状态码获取错误消息
 */
function getHttpErrorMessage(status: number): string {
  const errorMessages: Record<number, string> = {
    400: '请求参数错误',
    401: '未授权，请重新登录',
    403: '没有权限访问此资源',
    404: '请求的资源不存在',
    500: '服务器内部错误，请稍后重试',
    502: '网关错误，服务器暂时不可用',
    503: '服务暂时不可用，请稍后重试',
    504: '网关超时，请稍后重试',
  };

  return errorMessages[status] || `请求失败 (${status})`;
}
