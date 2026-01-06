import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { Language } from '../types';
import { translations } from '../locales';
import { getToken, clearToken } from '../src/api/client';

interface AppContextType {
  isAuthenticated: boolean;
  login: () => void;
  logout: () => void;
  language: Language;
  setLanguage: (lang: Language) => void;
  t: (key: keyof typeof translations['en']) => string;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

export const AppProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  // 初始化时检查是否有token
  const [isAuthenticated, setIsAuthenticated] = useState(() => {
    return !!getToken();
  });
  const [language, setLanguage] = useState<Language>('zh'); // Default to Chinese

  // 监听token变化
  useEffect(() => {
    const checkAuth = () => {
      setIsAuthenticated(!!getToken());
    };
    
    // 定期检查token（每5秒）
    const interval = setInterval(checkAuth, 5000);
    
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