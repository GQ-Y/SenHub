import React, { useState } from 'react';
import { Camera, Lock, User, ArrowRight } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';

export const LoginPage: React.FC = () => {
  const { login, t, setLanguage, language } = useAppContext();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin');
  const [isLoading, setIsLoading] = useState(false);

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    // Simulate network delay
    setTimeout(() => {
        setIsLoading(false);
        if (username === 'admin' && password === 'admin') {
            login();
        } else {
            alert('Invalid credentials (try admin/admin)');
        }
    }, 800);
  };

  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center p-4 relative overflow-hidden">
      {/* Background Decor */}
      <div className="absolute top-0 left-0 w-full h-full overflow-hidden z-0">
          <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-blue-200/30 rounded-full blur-[100px]"></div>
          <div className="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-cyan-200/30 rounded-full blur-[100px]"></div>
      </div>

      <div className="bg-white/80 backdrop-blur-xl w-full max-w-md rounded-3xl shadow-2xl p-8 z-10 border border-white">
        <div className="text-center mb-10">
            <div className="w-16 h-16 bg-blue-600 rounded-2xl flex items-center justify-center shadow-lg shadow-blue-600/30 mx-auto mb-6 transform rotate-3">
                <Camera className="text-white" size={32} />
            </div>
            <h1 className="text-3xl font-bold text-gray-800 mb-2">{t('app_name')}</h1>
            <p className="text-gray-500">{t('login_subtitle')}</p>
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
                        type="password" 
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        className="w-full pl-12 pr-4 py-3 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all"
                        placeholder="••••••"
                    />
                </div>
            </div>

            <button 
                type="submit" 
                disabled={isLoading}
                className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-3.5 rounded-xl shadow-lg shadow-blue-600/30 transition-all transform hover:scale-[1.02] active:scale-[0.98] flex items-center justify-center space-x-2"
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