import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Download, CheckCircle, AlertCircle, RefreshCw, Terminal } from 'lucide-react';

type Stage = 'idle' | 'checking' | 'ready' | 'updating' | 'done' | 'error';

interface CheckResult {
  currentVersion: string;
  latestVersion: string;
  hasUpdate: boolean;
  updateUrl: string;
}

async function apiFetch<T>(url: string, method = 'GET', body?: object): Promise<T | null> {
  const token = localStorage.getItem('nvr_auth_token');
  const res = await fetch(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) return null;
  const json = await res.json();
  return json.data ?? null;
}

const UpdatePage: React.FC = () => {
  const navigate = useNavigate();
  const [stage, setStage] = useState<Stage>('idle');
  const [checkResult, setCheckResult] = useState<CheckResult | null>(null);
  const [logs, setLogs] = useState<string[]>([]);
  const [countdown, setCountdown] = useState(0);

  const addLog = (msg: string) => setLogs(prev => [...prev, `[${new Date().toLocaleTimeString()}] ${msg}`]);

  const check = async () => {
    setStage('checking');
    addLog('正在连接更新服务器...');
    const result = await apiFetch<CheckResult>('/api/system/auto-update/check');
    if (!result) {
      addLog('无法连接到更新服务器，请检查网络');
      setStage('error');
      return;
    }
    setCheckResult(result);
    addLog(`当前版本: v${result.currentVersion}`);
    addLog(`服务器最新版本: v${result.latestVersion}`);
    if (result.hasUpdate) {
      addLog(`发现新版本 v${result.latestVersion}，可以更新`);
      setStage('ready');
    } else {
      addLog('当前已是最新版本，无需更新');
      setStage('idle');
    }
  };

  const applyUpdate = async () => {
    setStage('updating');
    addLog('正在下载并安装新版本...');
    addLog('请勿关闭此页面，更新完成后系统将自动重启');
    const ok = await apiFetch('/api/system/auto-update/apply', 'POST');
    if (!ok) {
      addLog('更新请求失败，请稍后重试');
      setStage('error');
      return;
    }
    addLog('更新任务已下发，等待系统重启...');
    setStage('done');
    // 倒计时 60 秒后尝试刷新
    let sec = 60;
    setCountdown(sec);
    const timer = setInterval(() => {
      sec--;
      setCountdown(sec);
      if (sec <= 0) {
        clearInterval(timer);
        addLog('正在重新连接...');
        window.location.href = '/';
      }
    }, 1000);
  };

  useEffect(() => {
    check();
  }, []);

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-950 via-gray-900 to-gray-950 flex flex-col">
      {/* Header */}
      <div className="flex items-center space-x-4 px-8 py-5 border-b border-white/10">
        <button
          onClick={() => navigate(-1)}
          disabled={stage === 'updating'}
          className="p-2 text-gray-400 hover:text-white hover:bg-white/10 rounded-lg transition-colors disabled:opacity-30"
        >
          <ArrowLeft size={20} />
        </button>
        <div>
          <h1 className="text-white font-bold text-lg">系统更新</h1>
          <p className="text-gray-500 text-xs mt-0.5">安全更新系统到最新版本</p>
        </div>
      </div>

      <div className="flex-1 flex flex-col items-center justify-center px-8 py-12">
        <div className="w-full max-w-xl space-y-6">

          {/* 版本卡片 */}
          <div className="bg-white/5 border border-white/10 rounded-2xl p-6">
            <div className="grid grid-cols-2 gap-6">
              <div>
                <div className="text-gray-500 text-xs mb-1">当前版本</div>
                <div className="font-mono text-white text-xl font-bold">
                  {checkResult ? `v${checkResult.currentVersion}` : '...'}
                </div>
              </div>
              <div>
                <div className="text-gray-500 text-xs mb-1">最新版本</div>
                <div className={`font-mono text-xl font-bold ${
                  checkResult?.hasUpdate ? 'text-amber-400' : 'text-emerald-400'
                }`}>
                  {checkResult ? `v${checkResult.latestVersion}` : '...'}
                </div>
              </div>
            </div>

            {/* 状态展示 */}
            <div className="mt-5 pt-5 border-t border-white/10 flex items-center space-x-3">
              {stage === 'checking' && (
                <>
                  <RefreshCw size={18} className="text-blue-400 animate-spin" />
                  <span className="text-blue-400 text-sm">正在检查更新...</span>
                </>
              )}
              {stage === 'idle' && (
                <>
                  <CheckCircle size={18} className="text-emerald-400" />
                  <span className="text-emerald-400 text-sm">当前已是最新版本</span>
                </>
              )}
              {stage === 'ready' && (
                <>
                  <Download size={18} className="text-amber-400" />
                  <span className="text-amber-400 text-sm">发现新版本，点击下方按钮开始更新</span>
                </>
              )}
              {stage === 'updating' && (
                <>
                  <RefreshCw size={18} className="text-blue-400 animate-spin" />
                  <span className="text-blue-400 text-sm">正在更新，请勿关闭此页面...</span>
                </>
              )}
              {stage === 'done' && (
                <>
                  <CheckCircle size={18} className="text-emerald-400" />
                  <span className="text-emerald-400 text-sm">
                    更新完成，系统将在 <span className="font-bold">{countdown}</span> 秒后自动重启
                  </span>
                </>
              )}
              {stage === 'error' && (
                <>
                  <AlertCircle size={18} className="text-red-400" />
                  <span className="text-red-400 text-sm">操作失败，请检查网络或日志</span>
                </>
              )}
            </div>
          </div>

          {/* 操作按钮 */}
          <div className="flex items-center space-x-3">
            {(stage === 'idle' || stage === 'error') && (
              <button
                onClick={check}
                className="flex items-center space-x-2 px-5 py-2.5 bg-white/10 hover:bg-white/15 text-white rounded-xl text-sm font-medium transition-colors border border-white/20"
              >
                <RefreshCw size={15} />
                <span>重新检查</span>
              </button>
            )}
            {stage === 'ready' && (
              <button
                onClick={applyUpdate}
                className="flex-1 flex items-center justify-center space-x-2 px-5 py-3 bg-amber-500 hover:bg-amber-600 text-white rounded-xl text-sm font-semibold transition-colors shadow-lg shadow-amber-500/30"
              >
                <Download size={16} />
                <span>立即更新到 v{checkResult?.latestVersion}</span>
              </button>
            )}
          </div>

          {/* 日志区 */}
          {logs.length > 0 && (
            <div className="bg-black/50 border border-white/10 rounded-xl p-4">
              <div className="flex items-center space-x-2 mb-3 text-gray-400 text-xs">
                <Terminal size={13} />
                <span>更新日志</span>
              </div>
              <div className="space-y-1 max-h-48 overflow-y-auto">
                {logs.map((log, i) => (
                  <div key={i} className="font-mono text-xs text-gray-400 leading-relaxed">
                    {log}
                  </div>
                ))}
              </div>
            </div>
          )}

        </div>
      </div>
    </div>
  );
};

export default UpdatePage;
