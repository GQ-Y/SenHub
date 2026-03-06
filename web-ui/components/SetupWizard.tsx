import React, { useState, useEffect } from 'react';
import {
  Shield, User, Lock, Eye, EyeOff, ArrowRight,
  CheckCircle, Database, Server, Key, ChevronRight
} from 'lucide-react';
import { setupService } from '../src/api/services';

// 安装向导背景图
const SETUP_BG_IMAGES = [
  'https://images.unsplash.com/photo-1558494949-ef010cbdcc31?w=1920&q=80',
  'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=1920&q=80',
  'https://images.unsplash.com/photo-1620712943543-bcc4688e7485?w=1920&q=80',
];

type Step = 'welcome' | 'account' | 'installing' | 'done';

interface FormData {
  username: string;
  password: string;
  confirmPassword: string;
}

interface SetupWizardProps {
  onComplete: () => void;
}

export const SetupWizard: React.FC<SetupWizardProps> = ({ onComplete }) => {
  const [step, setStep] = useState<Step>('welcome');
  const [bgIndex] = useState(() => Math.floor(Math.random() * SETUP_BG_IMAGES.length));
  const [form, setForm] = useState<FormData>({ username: 'admin', password: '', confirmPassword: '' });
  const [showPwd, setShowPwd] = useState(false);
  const [showConfirmPwd, setShowConfirmPwd] = useState(false);
  const [errors, setErrors] = useState<Partial<FormData>>({});
  const [submitError, setSubmitError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [countdown, setCountdown] = useState(0);

  const validate = (): boolean => {
    const newErrors: Partial<FormData> = {};
    if (!form.username.trim()) {
      newErrors.username = '用户名不能为空';
    } else if (form.username.trim().length < 2) {
      newErrors.username = '用户名至少 2 个字符';
    }
    if (!form.password) {
      newErrors.password = '密码不能为空';
    } else if (form.password.length < 6) {
      newErrors.password = '密码至少 6 位';
    }
    if (!form.confirmPassword) {
      newErrors.confirmPassword = '请再次输入密码';
    } else if (form.password !== form.confirmPassword) {
      newErrors.confirmPassword = '两次输入的密码不一致';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    setIsLoading(true);
    setSubmitError('');
    setStep('installing');

    try {
      await setupService.install(form.username.trim(), form.password, form.confirmPassword);
      // 安装成功，服务端会在 1.5s 后重启，前端倒计时 8s 后自动跳转登录
      setStep('done');
      setCountdown(8);
    } catch (err: any) {
      setSubmitError(err.message || '安装失败，请检查系统日志');
      setStep('account');
    } finally {
      setIsLoading(false);
    }
  };

  // 倒计时自动跳转
  useEffect(() => {
    if (step !== 'done' || countdown <= 0) return;
    if (countdown === 0) { onComplete(); return; }
    const t = setTimeout(() => {
      setCountdown((c) => {
        if (c <= 1) { onComplete(); return 0; }
        return c - 1;
      });
    }, 1000);
    return () => clearTimeout(t);
  }, [step, countdown]);

  return (
    <div className="min-h-screen flex items-center justify-center p-4 relative overflow-hidden">
      {/* 背景 */}
      <div className="absolute inset-0 z-0">
        <div
          className="absolute inset-0 bg-cover bg-center"
          style={{ backgroundImage: `url(${SETUP_BG_IMAGES[bgIndex]})` }}
        />
        <div className="absolute inset-0 bg-slate-900/65 backdrop-blur-[2px]" />
        <div className="absolute inset-0 bg-[linear-gradient(180deg,transparent_0%,rgba(15,23,42,0.75)_100%)]" />
      </div>

      {/* 卡片 */}
      <div className="bg-white/95 backdrop-blur-xl w-full max-w-lg rounded-3xl shadow-2xl p-8 z-10 border border-white/80">
        {/* Logo & Title */}
        <div className="text-center mb-8">
          <div className="relative inline-block mb-4">
            <img
              src="/logo.svg"
              alt="SenHub"
              className="w-16 h-16 mx-auto object-contain drop-shadow-lg"
              onError={(e) => {
                (e.target as HTMLImageElement).style.display = 'none';
              }}
            />
            <div className="absolute -bottom-1 -right-1 w-6 h-6 bg-emerald-500 rounded-full flex items-center justify-center shadow-lg">
              <Shield size={12} className="text-white" />
            </div>
          </div>
          <h1
            className="text-3xl font-bold text-gray-800 mb-1 tracking-tight"
            style={{ fontFamily: "'Orbitron', sans-serif" }}
          >
            SenHub
          </h1>
          <p className="text-gray-500 text-sm">系统初始化向导</p>
        </div>

        {/* 步骤进度条 */}
        {step !== 'done' && (
          <div className="flex items-center justify-center mb-8 gap-1">
            {(['welcome', 'account', 'installing'] as Step[]).map((s, i) => {
              const steps = ['welcome', 'account', 'installing'];
              const currentIdx = steps.indexOf(step);
              const isDone = i < currentIdx;
              const isCurrent = i === currentIdx;
              return (
                <React.Fragment key={s}>
                  <div
                    className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition-all
                      ${isDone ? 'bg-emerald-500 text-white' : isCurrent ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/30' : 'bg-gray-100 text-gray-400'}`}
                  >
                    {isDone ? <CheckCircle size={16} /> : i + 1}
                  </div>
                  {i < 2 && (
                    <div className={`h-0.5 w-12 rounded transition-all ${isDone ? 'bg-emerald-400' : 'bg-gray-200'}`} />
                  )}
                </React.Fragment>
              );
            })}
          </div>
        )}

        {/* Step 1: 欢迎 */}
        {step === 'welcome' && (
          <div className="space-y-6">
            <div className="bg-blue-50 border border-blue-100 rounded-2xl p-5 space-y-4">
              <h2 className="font-semibold text-blue-800 text-base">欢迎使用 SenHub</h2>
              <p className="text-blue-700 text-sm leading-relaxed">
                这是您的第一次启动。安装向导将帮助您完成初始化配置，整个过程只需要设置管理员账号密码，其他配置均已自动完成。
              </p>
              <div className="space-y-2">
                {[
                  { icon: <Database size={16} />, label: 'PostgreSQL 数据库', desc: '已自动安装并配置' },
                  { icon: <Key size={16} />, label: '数据库凭据', desc: '已随机生成并安全存储' },
                  { icon: <Server size={16} />, label: '系统服务', desc: '已初始化并就绪' },
                ].map((item) => (
                  <div key={item.label} className="flex items-center gap-3 text-sm">
                    <div className="w-7 h-7 bg-emerald-100 text-emerald-600 rounded-lg flex items-center justify-center flex-shrink-0">
                      {item.icon}
                    </div>
                    <div>
                      <span className="font-medium text-gray-700">{item.label}</span>
                      <span className="text-gray-400 ml-2 text-xs">{item.desc}</span>
                    </div>
                    <CheckCircle size={14} className="ml-auto text-emerald-500 flex-shrink-0" />
                  </div>
                ))}
              </div>
            </div>

            <div className="bg-amber-50 border border-amber-100 rounded-xl p-4 text-sm text-amber-700 leading-relaxed">
              <span className="font-semibold">接下来您只需要：</span>
              <br />
              设置一个管理员用户名和密码，完成后即可正常使用系统。
            </div>

            <button
              onClick={() => setStep('account')}
              className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-3.5 rounded-xl shadow-lg shadow-blue-600/30 transition-all transform hover:scale-[1.02] active:scale-[0.98] flex items-center justify-center gap-2"
            >
              <span>开始设置</span>
              <ChevronRight size={20} />
            </button>
          </div>
        )}

        {/* Step 2: 设置账号 */}
        {step === 'account' && (
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <h2 className="font-semibold text-gray-800 text-base mb-4">设置管理员账号</h2>

              {/* 用户名 */}
              <div className="space-y-1.5 mb-4">
                <label className="text-sm font-semibold text-gray-700 ml-1">用户名</label>
                <div className="relative">
                  <User className="absolute left-4 top-3.5 text-gray-400" size={18} />
                  <input
                    type="text"
                    value={form.username}
                    onChange={(e) => setForm({ ...form, username: e.target.value })}
                    className={`w-full pl-11 pr-4 py-3 bg-gray-50 border rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all text-sm
                      ${errors.username ? 'border-red-300 bg-red-50' : 'border-gray-200'}`}
                    placeholder="请输入管理员用户名"
                    autoComplete="username"
                  />
                </div>
                {errors.username && (
                  <p className="text-xs text-red-500 ml-1">{errors.username}</p>
                )}
              </div>

              {/* 密码 */}
              <div className="space-y-1.5 mb-4">
                <label className="text-sm font-semibold text-gray-700 ml-1">密码</label>
                <div className="relative">
                  <Lock className="absolute left-4 top-3.5 text-gray-400" size={18} />
                  <input
                    type={showPwd ? 'text' : 'password'}
                    value={form.password}
                    onChange={(e) => setForm({ ...form, password: e.target.value })}
                    className={`w-full pl-11 pr-12 py-3 bg-gray-50 border rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all text-sm
                      ${errors.password ? 'border-red-300 bg-red-50' : 'border-gray-200'}`}
                    placeholder="至少 6 位"
                    autoComplete="new-password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPwd((v) => !v)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-200/80 transition-colors"
                  >
                    {showPwd ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
                {errors.password && (
                  <p className="text-xs text-red-500 ml-1">{errors.password}</p>
                )}
              </div>

              {/* 确认密码 */}
              <div className="space-y-1.5">
                <label className="text-sm font-semibold text-gray-700 ml-1">确认密码</label>
                <div className="relative">
                  <Lock className="absolute left-4 top-3.5 text-gray-400" size={18} />
                  <input
                    type={showConfirmPwd ? 'text' : 'password'}
                    value={form.confirmPassword}
                    onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
                    className={`w-full pl-11 pr-12 py-3 bg-gray-50 border rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all text-sm
                      ${errors.confirmPassword ? 'border-red-300 bg-red-50' : 'border-gray-200'}`}
                    placeholder="再次输入密码"
                    autoComplete="new-password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPwd((v) => !v)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-200/80 transition-colors"
                  >
                    {showConfirmPwd ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
                {errors.confirmPassword && (
                  <p className="text-xs text-red-500 ml-1">{errors.confirmPassword}</p>
                )}
              </div>
            </div>

            {submitError && (
              <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl text-sm">
                {submitError}
              </div>
            )}

            <div className="flex gap-3 pt-1">
              <button
                type="button"
                onClick={() => setStep('welcome')}
                className="flex-1 py-3 border border-gray-200 rounded-xl text-gray-600 font-medium text-sm hover:bg-gray-50 transition-colors"
              >
                上一步
              </button>
              <button
                type="submit"
                disabled={isLoading}
                className="flex-[2] bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 rounded-xl shadow-lg shadow-blue-600/30 transition-all transform hover:scale-[1.01] active:scale-[0.99] flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <span>完成安装</span>
                <ArrowRight size={18} />
              </button>
            </div>
          </form>
        )}

        {/* Step 3: 安装中 */}
        {step === 'installing' && (
          <div className="flex flex-col items-center justify-center py-8 space-y-6">
            <div className="relative">
              <div className="w-20 h-20 rounded-full border-4 border-blue-100 flex items-center justify-center">
                <div className="w-20 h-20 rounded-full border-4 border-blue-600 border-t-transparent animate-spin absolute inset-0" />
                <Database size={32} className="text-blue-600" />
              </div>
            </div>
            <div className="text-center space-y-2">
              <h3 className="font-semibold text-gray-800 text-lg">正在完成初始化</h3>
              <p className="text-gray-500 text-sm">正在创建管理员账号并写入配置...</p>
            </div>
            <div className="w-full bg-gray-100 rounded-full h-1.5 overflow-hidden">
              <div className="h-full bg-blue-600 rounded-full animate-pulse" style={{ width: '70%' }} />
            </div>
          </div>
        )}

        {/* Step 4: 完成 */}
        {step === 'done' && (
          <div className="flex flex-col items-center justify-center py-6 space-y-6">
            <div className="w-20 h-20 bg-emerald-100 rounded-full flex items-center justify-center">
              <CheckCircle size={44} className="text-emerald-500" />
            </div>
            <div className="text-center space-y-2">
              <h3 className="font-bold text-gray-800 text-xl">安装完成！</h3>
              <p className="text-gray-500 text-sm leading-relaxed">
                SenHub 已成功初始化，系统正在重启中...
              </p>
            </div>
            <div className="w-full bg-emerald-50 border border-emerald-100 rounded-xl p-4 text-sm text-emerald-700 space-y-1">
              <div className="flex items-center gap-2">
                <CheckCircle size={14} className="flex-shrink-0" />
                <span>管理员账号 <strong>{form.username}</strong> 已创建</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle size={14} className="flex-shrink-0" />
                <span>数据库表结构已初始化</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle size={14} className="flex-shrink-0" />
                <span>系统配置已写入</span>
              </div>
            </div>
            <div className="w-full text-center space-y-3">
              <p className="text-gray-400 text-sm">
                {countdown > 0
                  ? `系统重启中，${countdown} 秒后自动跳转登录页...`
                  : '正在跳转...'}
              </p>
              <div className="w-full bg-gray-100 rounded-full h-1.5 overflow-hidden">
                <div
                  className="h-full bg-emerald-500 rounded-full transition-all duration-1000"
                  style={{ width: `${((8 - countdown) / 8) * 100}%` }}
                />
              </div>
              <button
                onClick={onComplete}
                className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-3.5 rounded-xl shadow-lg shadow-blue-600/30 transition-all transform hover:scale-[1.02] active:scale-[0.98] flex items-center justify-center gap-2"
              >
                <span>立即进入登录页面</span>
                <ArrowRight size={20} />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
