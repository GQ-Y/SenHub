import React, { useEffect, useState, useCallback } from 'react';
import { Download, RefreshCw, Settings, X, CheckCircle, AlertCircle, Clock, Globe, ToggleLeft, ToggleRight, ExternalLink } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

interface UpdateConfig {
  enabled: boolean;
  schedule: string; // e.g. "FRIDAY,02:00"
  updateUrl: string;
}

interface UpdateCheckResult {
  currentVersion: string;
  latestVersion: string;
  hasUpdate: boolean;
  updateUrl: string;
}

const DAYS = [
  { value: 'MONDAY',    label: '周一' },
  { value: 'TUESDAY',   label: '周二' },
  { value: 'WEDNESDAY', label: '周三' },
  { value: 'THURSDAY',  label: '周四' },
  { value: 'FRIDAY',    label: '周五' },
  { value: 'SATURDAY',  label: '周六' },
  { value: 'SUNDAY',    label: '周日' },
];

async function api<T>(url: string, method = 'GET', body?: object): Promise<T | null> {
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

export const AutoUpdateWidget: React.FC = () => {
  const navigate = useNavigate();
  const [showModal, setShowModal] = useState(false);
  const [checkResult, setCheckResult] = useState<UpdateCheckResult | null>(null);
  const [config, setConfig] = useState<UpdateConfig>({ enabled: false, schedule: 'FRIDAY,02:00', updateUrl: 'http://demo.zt.admins.smartrail.cloud' });
  const [saving, setSaving] = useState(false);
  const [checking, setChecking] = useState(false);

  // 解析 schedule 字段
  const scheduleDay  = config.schedule?.split(',')[0] ?? 'FRIDAY';
  const scheduleTime = config.schedule?.split(',')[1] ?? '02:00';

  const loadData = useCallback(async () => {
    const [cfg, check] = await Promise.all([
      api<UpdateConfig>('/api/system/auto-update'),
      api<UpdateCheckResult>('/api/system/auto-update/check'),
    ]);
    if (cfg)   setConfig(cfg);
    if (check) setCheckResult(check);
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleCheck = async () => {
    setChecking(true);
    const check = await api<UpdateCheckResult>('/api/system/auto-update/check');
    if (check) setCheckResult(check);
    setChecking(false);
  };

  const handleSave = async () => {
    setSaving(true);
    await api('/api/system/auto-update', 'PUT', config);
    setSaving(false);
    setShowModal(false);
  };

  const hasUpdate = checkResult?.hasUpdate ?? false;

  return (
    <>
      {/* 右上角图标按钮 */}
      <button
        onClick={() => setShowModal(true)}
        className={`relative p-2 rounded-lg transition-colors flex items-center space-x-1 ${
          hasUpdate
            ? 'text-amber-600 hover:text-amber-700 hover:bg-amber-50 animate-pulse'
            : 'text-gray-500 hover:text-blue-600 hover:bg-blue-50'
        }`}
        title={hasUpdate ? `发现新版本 ${checkResult?.latestVersion}` : '系统更新'}
      >
        <Download size={20} />
        {hasUpdate && (
          <span className="absolute top-1 right-1 w-2 h-2 bg-amber-500 rounded-full" />
        )}
      </button>

      {/* 设置模态框 */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div className="bg-white rounded-2xl shadow-2xl w-[480px] max-w-[95vw] overflow-hidden">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
              <div className="flex items-center space-x-2">
                <Settings size={18} className="text-blue-600" />
                <span className="font-semibold text-gray-800">系统更新设置</span>
              </div>
              <button onClick={() => setShowModal(false)} className="p-1 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100">
                <X size={18} />
              </button>
            </div>

            <div className="px-6 py-5 space-y-5">
              {/* 版本信息 */}
              <div className="bg-gray-50 rounded-xl p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-xs text-gray-500 mb-1">当前版本</div>
                    <div className="font-mono font-semibold text-gray-800">v{checkResult?.currentVersion ?? '...'}</div>
                  </div>
                  <div className="text-center">
                    {hasUpdate ? (
                      <div className="flex flex-col items-center">
                        <div className="flex items-center space-x-1 text-amber-600 text-sm font-medium">
                          <AlertCircle size={15} />
                          <span>有新版本</span>
                        </div>
                        <div className="font-mono text-amber-700 font-bold mt-1">v{checkResult?.latestVersion}</div>
                      </div>
                    ) : (
                      <div className="flex flex-col items-center text-emerald-600">
                        <CheckCircle size={20} />
                        <span className="text-xs mt-1">已是最新</span>
                      </div>
                    )}
                  </div>
                  <button
                    onClick={handleCheck}
                    disabled={checking}
                    className="flex items-center space-x-1 text-xs text-blue-600 hover:text-blue-700 border border-blue-200 hover:border-blue-300 px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50"
                  >
                    <RefreshCw size={12} className={checking ? 'animate-spin' : ''} />
                    <span>{checking ? '检查中...' : '检查更新'}</span>
                  </button>
                </div>
              </div>

              {/* 手动更新入口 */}
              {hasUpdate && (
                <button
                  onClick={() => { setShowModal(false); navigate('/update'); }}
                  className="w-full flex items-center justify-center space-x-2 bg-amber-500 hover:bg-amber-600 text-white py-2.5 rounded-xl font-medium transition-colors"
                >
                  <Download size={16} />
                  <span>立即更新到 v{checkResult?.latestVersion}</span>
                  <ExternalLink size={14} />
                </button>
              )}

              {/* 自动更新开关 */}
              <div className="flex items-center justify-between">
                <div>
                  <div className="font-medium text-gray-800 text-sm">自动更新</div>
                  <div className="text-xs text-gray-500 mt-0.5">按计划自动检查并安装新版本</div>
                </div>
                <button
                  onClick={() => setConfig(c => ({ ...c, enabled: !c.enabled }))}
                  className={`transition-colors ${config.enabled ? 'text-blue-600' : 'text-gray-400'}`}
                >
                  {config.enabled ? <ToggleRight size={32} /> : <ToggleLeft size={32} />}
                </button>
              </div>

              {/* 更新计划 */}
              {config.enabled && (
                <div className="space-y-3">
                  <div>
                    <label className="text-xs font-medium text-gray-600 mb-1.5 flex items-center space-x-1">
                      <Clock size={12} />
                      <span>更新时间</span>
                    </label>
                    <div className="flex items-center space-x-2">
                      <select
                        value={scheduleDay}
                        onChange={e => setConfig(c => ({ ...c, schedule: `${e.target.value},${scheduleTime}` }))}
                        className="flex-1 text-sm border border-gray-200 rounded-lg px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-300"
                      >
                        {DAYS.map(d => <option key={d.value} value={d.value}>{d.label}</option>)}
                      </select>
                      <input
                        type="time"
                        value={scheduleTime}
                        onChange={e => setConfig(c => ({ ...c, schedule: `${scheduleDay},${e.target.value}` }))}
                        className="flex-1 text-sm border border-gray-200 rounded-lg px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-300"
                      />
                    </div>
                  </div>
                </div>
              )}

              {/* 更新地址 */}
              <div>
                <label className="text-xs font-medium text-gray-600 mb-1.5 flex items-center space-x-1">
                  <Globe size={12} />
                  <span>更新服务器地址</span>
                </label>
                <input
                  type="url"
                  value={config.updateUrl}
                  onChange={e => setConfig(c => ({ ...c, updateUrl: e.target.value }))}
                  placeholder="http://example.com"
                  className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-300 font-mono"
                />
              </div>
            </div>

            {/* Footer */}
            <div className="flex items-center justify-end space-x-3 px-6 py-4 border-t border-gray-100 bg-gray-50">
              <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 rounded-lg hover:bg-gray-100 transition-colors">
                取消
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="px-5 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-60"
              >
                {saving ? '保存中...' : '保存设置'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default AutoUpdateWidget;
