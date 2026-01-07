import React, { useState, useEffect } from 'react';
import { Shield, Eye, Database, Cloud, Activity, FileText, Settings, CheckCircle2, Download } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { systemService } from '../src/api/services';
import { SystemConfig } from '../types';
import { Modal } from './Modal';
import { useModal } from '../hooks/useModal';

export const SystemSettings: React.FC = () => {
  const { t } = useAppContext();
  const [activeTab, setActiveTab] = useState('scanner');
  const [showToast, setShowToast] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const modal = useModal();
  const [config, setConfig] = useState<SystemConfig>({
    scanner: { enabled: true, interval: 300, ports: '80, 8000, 554, 37777' },
    auth: { defaultUser: 'admin', timeout: 5000 },
    keeper: { enabled: true, checkInterval: 60 },
    oss: { enabled: false, endpoint: '', bucket: '' },
    log: { level: 'info', retentionDays: 30 },
  });

  // 加载系统配置
  useEffect(() => {
    const loadConfig = async () => {
      setIsLoading(true);
      try {
        const response = await systemService.getConfig();
        setConfig(response.data);
      } catch (err) {
        console.error('加载系统配置失败:', err);
      } finally {
        setIsLoading(false);
      }
    };

    loadConfig();
  }, []);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await systemService.updateConfig(config);
      setShowToast(true);
      setTimeout(() => setShowToast(false), 3000);
    } catch (err: any) {
      modal.showModal({
        message: err.message || '保存失败',
        type: 'error',
      });
    } finally {
      setIsSaving(false);
    }
  };

  const TABS = [
    { id: 'scanner', label: t('scanner'), icon: Eye },
    { id: 'auth', label: t('auth'), icon: Shield },
    { id: 'keeper', label: t('keeper'), icon: Activity },
    { id: 'oss', label: t('oss'), icon: Cloud },
    { id: 'log', label: t('logs'), icon: FileText },
  ];

  return (
    <>
      {/* 弹窗组件 */}
      <Modal
        isOpen={modal.isOpen}
        onClose={modal.closeModal}
        title={modal.modalOptions?.title}
        message={modal.modalOptions?.message || ''}
        type={modal.modalOptions?.type || 'info'}
        confirmText={modal.modalOptions?.confirmText}
        onConfirm={modal.modalOptions?.onConfirm}
      />
      
      <div className="space-y-6 relative">
      
      {/* Toast Notification */}
      {showToast && (
        <div className="fixed top-20 right-8 bg-gray-800 text-white px-6 py-3 rounded-xl shadow-xl flex items-center space-x-3 z-50 animate-fade-in">
            <CheckCircle2 size={20} className="text-green-400" />
            <span className="font-medium">{t('config_saved')}</span>
        </div>
      )}

      {/* Tabs */}
      <div className="bg-white rounded-2xl p-2 shadow-sm border border-gray-100 flex flex-wrap gap-2">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`flex items-center space-x-2 px-4 py-3 rounded-xl transition-all duration-200 font-medium text-sm flex-1 justify-center sm:flex-none ${
              activeTab === tab.id
                ? 'bg-blue-600 text-white shadow-md'
                : 'text-gray-500 hover:bg-gray-50 hover:text-gray-900'
            }`}
          >
            <tab.icon size={18} />
            <span>{tab.label}</span>
          </button>
        ))}
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex items-center justify-center h-64">
          <div className="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
        </div>
      ) : (
      <div className="bg-white rounded-2xl p-8 shadow-sm border border-gray-100 min-h-[400px]">
        {activeTab === 'scanner' && (
           <div className="space-y-6 animate-fade-in">
              <div className="flex items-center justify-between">
                <div>
                    <h3 className="text-lg font-bold text-gray-800">{t('scanner')}</h3>
                    <p className="text-gray-500 text-sm">Automatically discover ONVIF devices on the local network.</p>
                </div>
                <div className="relative inline-block w-12 mr-2 align-middle select-none transition duration-200 ease-in">
                    <input 
                      type="checkbox" 
                      checked={config.scanner.enabled}
                      onChange={(e) => setConfig({...config, scanner: {...config.scanner, enabled: e.target.checked}})}
                      className="toggle-checkbox absolute block w-6 h-6 rounded-full bg-white border-4 appearance-none cursor-pointer checked:right-0 checked:border-blue-600"
                    />
                    <label className="toggle-label block overflow-hidden h-6 rounded-full bg-gray-300 cursor-pointer checked:bg-blue-600"></label>
                </div>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4">
                  <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">Scan Interval (seconds)</label>
                      <input 
                        type="number" 
                        value={config.scanner.interval}
                        onChange={(e) => setConfig({...config, scanner: {...config.scanner, interval: parseInt(e.target.value) || 300}})}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500" 
                      />
                  </div>
                   <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">Target Subnet</label>
                      <input 
                        type="text" 
                        value={config.scanner.ports}
                        onChange={(e) => setConfig({...config, scanner: {...config.scanner, ports: e.target.value}})}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500" 
                        placeholder="80, 8000, 554, 37777"
                      />
                  </div>
                  <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">Target Ports (Comma separated)</label>
                      <input 
                        type="text" 
                        value={config.scanner.ports}
                        onChange={(e) => setConfig({...config, scanner: {...config.scanner, ports: e.target.value}})}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500" 
                      />
                  </div>
              </div>
           </div>
        )}

        {activeTab === 'oss' && (
             <div className="space-y-6 animate-fade-in">
                <div className="flex items-center justify-between mb-6">
                    <div>
                        <h3 className="text-lg font-bold text-gray-800">{t('oss')}</h3>
                        <p className="text-gray-500 text-sm">Configure where snapshots and recording clips are stored.</p>
                    </div>
                </div>
                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Service Provider</label>
                        <select className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500">
                            <option>AWS S3</option>
                            <option>Aliyun OSS</option>
                            <option>MinIO (Self-hosted)</option>
                        </select>
                    </div>
                     <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Endpoint</label>
                        <input type="text" className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500" placeholder="oss-cn-hangzhou.aliyuncs.com" />
                    </div>
                     <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Access Key ID</label>
                        <input type="password" value="****************" className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500" />
                    </div>
                </div>
             </div>
        )}

        {activeTab === 'keeper' && (
            <div className="space-y-6 animate-fade-in">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-lg font-bold text-gray-800">{t('keeper')}</h3>
                    <p className="text-gray-500 text-sm mb-4">Monitor device health and automatically attempt recovery.</p>
                  </div>
                  <div className="relative inline-block w-12 mr-2 align-middle select-none transition duration-200 ease-in">
                    <input 
                      type="checkbox" 
                      checked={config.keeper.enabled}
                      onChange={(e) => setConfig({...config, keeper: {...config.keeper, enabled: e.target.checked}})}
                      className="toggle-checkbox absolute block w-6 h-6 rounded-full bg-white border-4 appearance-none cursor-pointer checked:right-0 checked:border-blue-600"
                    />
                    <label className="toggle-label block overflow-hidden h-6 rounded-full bg-gray-300 cursor-pointer checked:bg-blue-600"></label>
                  </div>
                </div>
                <div className="p-4 bg-yellow-50 border border-yellow-100 rounded-xl text-yellow-800 text-sm">
                    ⚠️ Reducing the check interval below 30 seconds may increase network load significantly.
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                     <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">Check Interval (seconds)</label>
                      <input 
                        type="number" 
                        value={config.keeper.checkInterval}
                        onChange={(e) => setConfig({...config, keeper: {...config.keeper, checkInterval: parseInt(e.target.value) || 60}})}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500" 
                      />
                  </div>
                </div>
            </div>
        )}

        {activeTab === 'auth' && (
            <div className="space-y-6 animate-fade-in">
                 <div className="flex items-center justify-between mb-6">
                    <div>
                        <h3 className="text-lg font-bold text-gray-800">{t('auth_title')}</h3>
                        <p className="text-gray-500 text-sm">{t('auth_desc')}</p>
                    </div>
                </div>
                 <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">{t('def_user')}</label>
                        <input 
                          type="text" 
                          value={config.auth.defaultUser}
                          onChange={(e) => setConfig({...config, auth: {...config.auth, defaultUser: e.target.value}})}
                          className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500" 
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">{t('timeout')}</label>
                        <input 
                          type="number" 
                          value={config.auth.timeout}
                          onChange={(e) => setConfig({...config, auth: {...config.auth, timeout: parseInt(e.target.value) || 5000}})}
                          className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500" 
                        />
                    </div>
                </div>
            </div>
        )}

        {activeTab === 'log' && (
             <div className="space-y-6 animate-fade-in">
                 <div className="flex items-center justify-between mb-6">
                    <div>
                        <h3 className="text-lg font-bold text-gray-800">{t('log_title')}</h3>
                        <p className="text-gray-500 text-sm">{t('log_desc')}</p>
                    </div>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                     <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">{t('log_level')}</label>
                         <select 
                           value={config.log.level}
                           onChange={(e) => setConfig({...config, log: {...config.log, level: e.target.value}})}
                           className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                         >
                            <option value="info">INFO</option>
                            <option value="debug">DEBUG</option>
                            <option value="warn">WARN</option>
                            <option value="error">ERROR</option>
                        </select>
                    </div>
                     <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">{t('retention')}</label>
                        <input 
                          type="number" 
                          value={config.log.retentionDays}
                          onChange={(e) => setConfig({...config, log: {...config.log, retentionDays: parseInt(e.target.value) || 30}})}
                          className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500" 
                        />
                    </div>
                </div>
                <div className="pt-4 border-t border-gray-100">
                    <button className="flex items-center space-x-2 text-blue-600 hover:text-blue-700 font-medium">
                        <Download size={18} />
                        <span>{t('download_logs')}</span>
                    </button>
                </div>
             </div>
        )}

        <div className="mt-8 pt-6 border-t border-gray-100 flex justify-end">
            <button 
                onClick={handleSave}
                disabled={isSaving}
                className="bg-blue-600 text-white px-8 py-3 rounded-xl font-medium shadow-lg shadow-blue-200 hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center"
            >
                {isSaving ? (
                  <>
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2"></div>
                    <span>保存中...</span>
                  </>
                ) : (
                  <span>{t('save_settings')}</span>
                )}
            </button>
        </div>
      </div>
      )}
    </div>
    </>
  );
};