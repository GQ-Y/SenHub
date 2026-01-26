import React, { createContext, useContext, useState, useEffect, ReactNode, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Language } from '../types';
import { translations } from '../locales';
import { getToken, clearToken, setAuthStateChangeCallback } from '../src/api/client';
import { dashboardService } from '../src/api/services';

interface AppContextType {
  isAuthenticated: boolean;
  login: () => void;
  logout: () => void;
  language: Language;
  setLanguage: (lang: Language) => void;
  t: (key: keyof typeof translations['en']) => string;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

// 内部组件，用于访问navigate
const AppContextProviderInner: React.FC<{ children: ReactNode }> = ({ children }) => {
  const navigate = useNavigate();
  // 初始化时检查是否有token
  const [isAuthenticated, setIsAuthenticated] = useState(() => {
    return !!getToken();
  });
  const [language, setLanguage] = useState<Language>('zh'); // Default to Chinese
  const isCheckingTokenRef = useRef(false);

  // 验证token有效性
  const verifyToken = async (): Promise<boolean> => {
    const token = getToken();
    if (!token) {
      return false;
    }

    // 如果正在检查，避免重复请求
    if (isCheckingTokenRef.current) {
      return isAuthenticated;
    }

    try {
      isCheckingTokenRef.current = true;
      // 使用一个轻量级的API来验证token（例如获取仪表板统计）
      // 如果token无效，会返回401，由API客户端处理
      await dashboardService.getStats();
      return true;
    } catch (error: any) {
      // 如果是401错误，token无效
      if (error.message && error.message.includes('未授权')) {
        clearToken();
        return false;
      }
      // 其他错误（如网络错误）不影响认证状态
      return isAuthenticated;
    } finally {
      isCheckingTokenRef.current = false;
    }
  };

  // 监听认证状态变化事件
  useEffect(() => {
    const handleUnauthorized = (event: CustomEvent) => {
      setIsAuthenticated(false);
      const path = (event as any).detail?.path;
      if (path && !path.includes('/login')) {
        navigate('/login', { state: { from: { pathname: path } }, replace: true });
      }
    };

    window.addEventListener('auth:unauthorized', handleUnauthorized as EventListener);

    // 设置API客户端的认证状态变化回调
    setAuthStateChangeCallback((authenticated: boolean) => {
      setIsAuthenticated(authenticated);
      if (!authenticated && !window.location.pathname.includes('/login')) {
        navigate('/login', { 
          state: { from: { pathname: window.location.pathname } }, 
          replace: true 
        });
      }
    });

    return () => {
      window.removeEventListener('auth:unauthorized', handleUnauthorized as EventListener);
      setAuthStateChangeCallback(null);
    };
  }, [navigate]);

  // 监听token变化
  useEffect(() => {
    const checkAuth = async () => {
      const token = getToken();
      if (!token) {
        setIsAuthenticated(false);
        return;
      }

      // 验证token有效性
      const isValid = await verifyToken();
      setIsAuthenticated(isValid);
    };
    
    // 初始化时验证一次
    checkAuth();
    
    // 定期检查token（每30秒，减少频率）
    const interval = setInterval(checkAuth, 30000);
    
    // 监听storage事件（跨标签页同步）
    window.addEventListener('storage', checkAuth);
    
    return () => {
      clearInterval(interval);
      window.removeEventListener('storage', checkAuth);
    };
  }, []);

  const login = () => {
    setIsAuthenticated(true);
  };

  const logout = () => {
    clearToken();
    setIsAuthenticated(false);
    navigate('/login', { replace: true });
  };

  const t = (key: keyof typeof translations['en']) => {
    return translations[language][key] || key;
  };

  return (
    <AppContext.Provider value={{ isAuthenticated, login, logout, language, setLanguage, t }}>
      {children}
    </AppContext.Provider>
  );
};

export const useAppContext = () => {
  const context = useContext(AppContext);
  if (context === undefined) {
    throw new Error('useAppContext must be used within an AppProvider');
  }
  return context;
};

// 导出AppProvider，需要在BrowserRouter内部使用
export const AppProvider = AppContextProviderInner;