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

    // 处理401未授权错误
    if (response.status === 401) {
      clearToken();
      // 如果不在登录页，跳转到登录页
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login';
      }
      throw new Error('未授权，请重新登录');
    }

    // 解析响应
    const data: ApiResponse<T> = await response.json();

    // 检查业务状态码
    if (data.code !== 200) {
      throw new Error(data.message || '请求失败');
    }

    return data;
  } catch (error: any) {
    clearTimeout(timeoutId);
    if (error.name === 'AbortError') {
      throw new Error('请求超时');
    }
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
