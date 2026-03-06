import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Lock, User, ArrowRight, Eye, EyeOff } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { authService } from '../src/api/services';

// CDN 背景图（Unsplash），AI 相关主题，自动轮播
const LOGIN_BG_IMAGES = [
  'https://images.unsplash.com/photo-1677442136019-0f110b4b2531?w=1920&q=80', // AI / 神经网络
  'https://images.unsplash.com/photo-1531746795393-6cde5e4b2eed?w=1920&q=80', // 机器人 / 人机协作
  'https://images.unsplash.com/photo-1620712943543-bcc4688e7485?w=1920&q=80', // 芯片 / 电路 / 算力
  'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=1920&q=80',   // 数据可视化 / 智能分析
];

export const LoginPage: React.FC = () => {
  const { login, t, setLanguage, language } = useAppContext();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string>('');
  const [showPassword, setShowPassword] = useState(false);
  const [bgIndex, setBgIndex] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setBgIndex((i) => (i + 1) % LOGIN_BG_IMAGES.length);
    }, 5000);
    return () => clearInterval(timer);
  }, []);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      await authService.login(username, password);
      login();
      // 重定向到之前尝试访问的页面，或默认到首页
      const from = (location.state as any)?.from?.pathname || '/';
      navigate(from, { replace: true });
    } catch (err: any) {
      setError(err.message || '登录失败，请检查用户名和密码');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 relative overflow-hidden">
      {/* CDN 图片背景轮播 + 遮罩 */}
      <div className="absolute inset-0 z-0">
        {LOGIN_BG_IMAGES.map((src, i) => (
          <div
            key={src}
            className="absolute inset-0 bg-cover bg-center transition-opacity duration-1000"
            style={{
              backgroundImage: `url(${src})`,
              opacity: i === bgIndex ? 1 : 0,
            }}
          />
        ))}
        <div className="absolute inset-0 bg-slate-900/60 backdrop-blur-[2px]" />
        <div className="absolute inset-0 bg-[linear-gradient(180deg,transparent_0%,rgba(15,23,42,0.7)_100%)]" />
      </div>

      <div className="bg-white/95 backdrop-blur-xl w-full max-w-md rounded-3xl shadow-2xl p-8 z-10 border border-white/80">
        <div className="text-center mb-10">
            <img src="/logo.svg" alt="SenHub" className="w-16 h-16 mx-auto mb-5 object-contain drop-shadow-lg" />
            <h1
              className="text-3xl font-bold text-gray-800 mb-2 tracking-tight transition-all duration-300 cursor-default select-none hover:scale-105 hover:text-[#0066FF] hover:tracking-wider"
              style={{ fontFamily: "'Orbitron', sans-serif" }}
            >
              {t('app_name')}
            </h1>
            <p className="text-gray-500 text-sm">{t('login_subtitle')}</p>
        </div>

        <form onSubmit={handleLogin} className="space-y-6">
            <div className="space-y-2">
                <label className="text-sm font-semibold text-gray-700 ml-1">{t('username')}</label>
                <div className="relative">
                    <User className="absolute left-4 top-3.5 text-gray-400" size={20} />
                    <input 
                        type="text" 
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        className="w-full pl-12 pr-4 py-3 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all"
                        placeholder="admin"
                    />
                </div>
            </div>

            <div className="space-y-2">
                <label className="text-sm font-semibold text-gray-700 ml-1">{t('password')}</label>
                <div className="relative">
                    <Lock className="absolute left-4 top-3.5 text-gray-400" size={20} />
                    <input 
                        type={showPassword ? 'text' : 'password'}
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        className="w-full pl-12 pr-12 py-3 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all"
                        placeholder="••••••"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword((v) => !v)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-200/80 transition-colors"
                      title={showPassword ? t('password') : t('password')}
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                    >
                      {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                    </button>
                </div>
            </div>

            {error && (
                <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl text-sm">
                    {error}
                </div>
            )}

            <button 
                type="submit" 
                disabled={isLoading}
                className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-3.5 rounded-xl shadow-lg shadow-blue-600/30 transition-all transform hover:scale-[1.02] active:scale-[0.98] flex items-center justify-center space-x-2 disabled:opacity-50 disabled:cursor-not-allowed"
            >
                {isLoading ? (
                    <div className="w-6 h-6 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                ) : (
                    <>
                        <span>{t('sign_in')}</span>
                        <ArrowRight size={20} />
                    </>
                )}
            </button>
        </form>

        <div className="mt-8 flex justify-center space-x-4">
            <button 
                onClick={() => setLanguage('en')} 
                className={`text-xs font-medium px-3 py-1 rounded-full transition-colors ${language === 'en' ? 'bg-gray-200 text-gray-800' : 'text-gray-400 hover:text-gray-600'}`}
            >
                English
            </button>
            <div className="w-px h-4 bg-gray-300 self-center"></div>
            <button 
                onClick={() => setLanguage('zh')} 
                className={`text-xs font-medium px-3 py-1 rounded-full transition-colors ${language === 'zh' ? 'bg-gray-200 text-gray-800' : 'text-gray-400 hover:text-gray-600'}`}
            >
                中文
            </button>
        </div>
      </div>
    </div>
  );
};