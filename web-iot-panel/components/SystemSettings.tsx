import React, { useState, useEffect } from 'react';
import { Shield, Eye, Cloud, Activity, FileText, CheckCircle2, Download, Bell, Send } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { systemService } from '../src/api/services';
import { SystemConfig } from '../types';
import { Modal } from './Modal';
import { useModal } from '../hooks/useModal';

// 默认品牌预设配置
const DEFAULT_PRESETS = {
  hikvision: { port: 8000, username: 'admin', password: '' },
  tiandy: { port: 8000, username: 'Admin', password: '' },
  dahua: { port: 37777, username: 'admin', password: '' },
};

// 默认通知配置
const DEFAULT_NOTIFICATION = {
  wechat: { enabled: false, webhookUrl: '' },
  dingtalk: { enabled: false, webhookUrl: '', secret: '' },
  feishu: { enabled: false, webhookUrl: '' },
};

export const SystemSettings: React.FC = () => {
  const { t } = useAppContext();
  const [activeTab, setActiveTab] = useState('scanner');
  const [showToast, setShowToast] = useState(false);
  const [toastMessage, setToastMessage] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [testingChannel, setTestingChannel] = useState<string | null>(null);
  const modal = useModal();

  const [config, setConfig] = useState<SystemConfig>({
    scanner: {
      enabled: true,
      interval: 300,
      ports: '80, 8000, 554, 37777',
      scanSegment: '192.168.1',
      scanRangeStart: 10,
      scanRangeEnd: 100,
    },
    auth: {
      timeout: 5000,
      presets: DEFAULT_PRESETS,
    },
    keeper: { enabled: true, checkInterval: 60 },
    oss: {
      enabled: false,
      type: 'custom',
      endpoint: '',
      bucket: '',
      accessKeyId: '',
      accessKeySecret: '',
    },
    log: { level: 'info', retentionDays: 30 },
    notification: DEFAULT_NOTIFICATION,
  });

  // 加载系统配置
  useEffect(() => {
    const loadConfig = async () => {
      setIsLoading(true);
      try {
        const response = await systemService.getConfig();
        // 合并默认值，防止缺失字段
        const data = response.data;
        setConfig({
          ...config,
          ...data,
          auth: {
            ...config.auth,
            ...data.auth,
            presets: { ...DEFAULT_PRESETS, ...data.auth?.presets },
          },
          notification: { ...DEFAULT_NOTIFICATION, ...data.notification },
        });
      } catch (err) {
        console.error('加载系统配置失败:', err);
      } finally {
        setIsLoading(false);
      }
    };

    loadConfig();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const showToastMsg = (msg: string) => {
    setToastMessage(msg);
    setShowToast(true);
    setTimeout(() => setShowToast(false), 3000);
  };

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await systemService.updateConfig(config);
      showToastMsg(t('config_saved'));
    } catch (err: any) {
      modal.showModal({
        message: err.message || '保存失败',
        type: 'error',
      });
    } finally {
      setIsSaving(false);
    }
  };

  // 测试通知发送
  const handleTestNotification = async (channel: 'wechat' | 'dingtalk' | 'feishu') => {
    const channelConfig = config.notification[channel];
    if (!channelConfig.webhookUrl) {
      modal.showModal({ message: 'Webhook URL is required', type: 'error' });
      return;
    }

    setTestingChannel(channel);
    try {
      await systemService.testNotification(channel, channelConfig);
      showToastMsg(t('test_send_success'));
    } catch (err: any) {
      modal.showModal({ message: err.message || t('test_send_failed'), type: 'error' });
    } finally {
      setTestingChannel(null);
    }
  };

  const TABS = [
    { id: 'scanner', label: t('scanner'), icon: Eye },
    { id: 'auth', label: t('auth'), icon: Shield },
    { id: 'keeper', label: t('keeper'), icon: Activity },
    { id: 'oss', label: t('oss'), icon: Cloud },
    { id: 'notification', label: t('notifications'), icon: Bell },
    { id: 'log', label: t('logs'), icon: FileText },
  ];

  // 更新品牌预设
  const updatePreset = (brand: 'hikvision' | 'tiandy' | 'dahua', field: string, value: any) => {
    setConfig({
      ...config,
      auth: {
        ...config.auth,
        presets: {
          ...config.auth.presets,
          [brand]: { ...config.auth.presets[brand], [field]: value },
        },
      },
    });
  };

  // 更新通知配置
  const updateNotification = (channel: 'wechat' | 'dingtalk' | 'feishu', field: string, value: any) => {
    setConfig({
      ...config,
      notification: {
        ...config.notification,
        [channel]: { ...config.notification[channel], [field]: value },
      },
    });
  };

  return (
    <>
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
            <span className="font-medium">{toastMessage}</span>
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
            {/* Scanner Tab */}
            {activeTab === 'scanner' && (
              <div className="space-y-6 animate-fade-in">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-lg font-bold text-gray-800">{t('scanner')}</h3>
                    <p className="text-gray-500 text-sm">{t('scanner_desc')}</p>
                  </div>
                  <div className="relative inline-block w-12 mr-2 align-middle select-none">
                    <input
                      type="checkbox"
                      checked={config.scanner.enabled}
                      onChange={(e) => setConfig({ ...config, scanner: { ...config.scanner, enabled: e.target.checked } })}
                      className="toggle-checkbox absolute block w-6 h-6 rounded-full bg-white border-4 appearance-none cursor-pointer checked:right-0 checked:border-blue-600"
                    />
                    <label className="toggle-label block overflow-hidden h-6 rounded-full bg-gray-300 cursor-pointer"></label>
                  </div>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">{t('scan_interval')}</label>
                    <input
                      type="number"
                      value={config.scanner.interval}
                      onChange={(e) => setConfig({ ...config, scanner: { ...config.scanner, interval: parseInt(e.target.value) || 300 } })}
                      className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">{t('scan_segment')}</label>
                    <input
                      type="text"
                      value={config.scanner.scanSegment || ''}
                      onChange={(e) => setConfig({ ...config, scanner: { ...config.scanner, scanSegment: e.target.value } })}
                      className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      placeholder="192.168.1"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">{t('scan_range')}</label>
                    <div className="flex items-center space-x-2">
                      <input
                        type="number"
                        value={config.scanner.scanRangeStart || 10}
                        onChange={(e) => setConfig({ ...config, scanner: { ...config.scanner, scanRangeStart: parseInt(e.target.value) || 10 } })}
                        className="w-24 bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      />
                      <span className="text-gray-500">-</span>
                      <input
                        type="number"
                        value={config.scanner.scanRangeEnd || 100}
                        onChange={(e) => setConfig({ ...config, scanner: { ...config.scanner, scanRangeEnd: parseInt(e.target.value) || 100 } })}
                        className="w-24 bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">{t('scan_ports')}</label>
                    <input
                      type="text"
                      value={config.scanner.ports}
                      onChange={(e) => setConfig({ ...config, scanner: { ...config.scanner, ports: e.target.value } })}
                      className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      placeholder="80, 8000, 554, 37777"
                    />
                  </div>
                </div>
              </div>
            )}

            {/* Auth Tab */}
            {activeTab === 'auth' && (
              <div className="space-y-6 animate-fade-in">
                <div>
                  <h3 className="text-lg font-bold text-gray-800">{t('auth_brand_presets')}</h3>
                  <p className="text-gray-500 text-sm">{t('auth_brand_desc')}</p>
                </div>

                {/* Hikvision */}
                <div className="border border-gray-200 rounded-xl p-4">
                  <h4 className="font-semibold text-gray-800 mb-4 flex items-center">
                    <span className="w-3 h-3 bg-red-500 rounded-full mr-2"></span>
                    {t('brand_hikvision')}
                  </h4>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('default_port')}</label>
                      <input
                        type="number"
                        value={config.auth.presets.hikvision.port}
                        onChange={(e) => updatePreset('hikvision', 'port', parseInt(e.target.value) || 8000)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('def_user')}</label>
                      <input
                        type="text"
                        value={config.auth.presets.hikvision.username}
                        onChange={(e) => updatePreset('hikvision', 'username', e.target.value)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('def_pass')}</label>
                      <input
                        type="text"
                        value={config.auth.presets.hikvision.password}
                        onChange={(e) => updatePreset('hikvision', 'password', e.target.value)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="默认密码"
                      />
                    </div>
                  </div>
                </div>

                {/* Tiandy */}
                <div className="border border-gray-200 rounded-xl p-4">
                  <h4 className="font-semibold text-gray-800 mb-4 flex items-center">
                    <span className="w-3 h-3 bg-blue-500 rounded-full mr-2"></span>
                    {t('brand_tiandy')}
                  </h4>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('default_port')}</label>
                      <input
                        type="number"
                        value={config.auth.presets.tiandy.port}
                        onChange={(e) => updatePreset('tiandy', 'port', parseInt(e.target.value) || 8000)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('def_user')}</label>
                      <input
                        type="text"
                        value={config.auth.presets.tiandy.username}
                        onChange={(e) => updatePreset('tiandy', 'username', e.target.value)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('def_pass')}</label>
                      <input
                        type="text"
                        value={config.auth.presets.tiandy.password}
                        onChange={(e) => updatePreset('tiandy', 'password', e.target.value)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="默认密码"
                      />
                    </div>
                  </div>
                </div>

                {/* Dahua */}
                <div className="border border-gray-200 rounded-xl p-4">
                  <h4 className="font-semibold text-gray-800 mb-4 flex items-center">
                    <span className="w-3 h-3 bg-orange-500 rounded-full mr-2"></span>
                    {t('brand_dahua')}
                  </h4>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('default_port')}</label>
                      <input
                        type="number"
                        value={config.auth.presets.dahua.port}
                        onChange={(e) => updatePreset('dahua', 'port', parseInt(e.target.value) || 37777)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('def_user')}</label>
                      <input
                        type="text"
                        value={config.auth.presets.dahua.username}
                        onChange={(e) => updatePreset('dahua', 'username', e.target.value)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('def_pass')}</label>
                      <input
                        type="text"
                        value={config.auth.presets.dahua.password}
                        onChange={(e) => updatePreset('dahua', 'password', e.target.value)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="默认密码"
                      />
                    </div>
                  </div>
                </div>

                {/* Connection Timeout */}
                <div className="pt-4 border-t border-gray-100">
                  <div className="w-64">
                    <label className="block text-sm font-medium text-gray-700 mb-2">{t('connection_timeout')}</label>
                    <input
                      type="number"
                      value={config.auth.timeout}
                      onChange={(e) => setConfig({ ...config, auth: { ...config.auth, timeout: parseInt(e.target.value) || 5000 } })}
                      className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>
              </div>
            )}

            {/* Keeper Tab */}
            {activeTab === 'keeper' && (
              <div className="space-y-6 animate-fade-in">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-lg font-bold text-gray-800">{t('keeper')}</h3>
                    <p className="text-gray-500 text-sm">{t('keeper_desc')}</p>
                  </div>
                  <div className="relative inline-block w-12 mr-2 align-middle select-none">
                    <input
                      type="checkbox"
                      checked={config.keeper.enabled}
                      onChange={(e) => setConfig({ ...config, keeper: { ...config.keeper, enabled: e.target.checked } })}
                      className="toggle-checkbox absolute block w-6 h-6 rounded-full bg-white border-4 appearance-none cursor-pointer checked:right-0 checked:border-blue-600"
                    />
                    <label className="toggle-label block overflow-hidden h-6 rounded-full bg-gray-300 cursor-pointer"></label>
                  </div>
                </div>
                <div className="p-4 bg-yellow-50 border border-yellow-100 rounded-xl text-yellow-800 text-sm">
                  ⚠️ {t('keeper_warning')}
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">{t('check_interval')}</label>
                    <input
                      type="number"
                      value={config.keeper.checkInterval}
                      onChange={(e) => setConfig({ ...config, keeper: { ...config.keeper, checkInterval: parseInt(e.target.value) || 60 } })}
                      className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>
              </div>
            )}

            {/* OSS Tab */}
            {activeTab === 'oss' && (
              <div className="space-y-6 animate-fade-in">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-lg font-bold text-gray-800">{t('oss')}</h3>
                    <p className="text-gray-500 text-sm">{t('oss_desc')}</p>
                  </div>
                  <div className="relative inline-block w-12 mr-2 align-middle select-none">
                    <input
                      type="checkbox"
                      checked={config.oss.enabled}
                      onChange={(e) => setConfig({ ...config, oss: { ...config.oss, enabled: e.target.checked } })}
                      className="toggle-checkbox absolute block w-6 h-6 rounded-full bg-white border-4 appearance-none cursor-pointer checked:right-0 checked:border-blue-600"
                    />
                    <label className="toggle-label block overflow-hidden h-6 rounded-full bg-gray-300 cursor-pointer"></label>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">{t('oss_type')}</label>
                  <select
                    value={config.oss.type}
                    onChange={(e) => setConfig({ ...config, oss: { ...config.oss, type: e.target.value as any } })}
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="aliyun">{t('oss_type_aliyun')}</option>
                    <option value="minio">{t('oss_type_minio')}</option>
                    <option value="custom">{t('oss_type_custom')}</option>
                  </select>
                </div>

                {/* 自定义文件服务器 - 只需 endpoint */}
                {config.oss.type === 'custom' && (
                  <div className="space-y-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('oss_endpoint')}</label>
                      <input
                        type="text"
                        value={config.oss.endpoint}
                        onChange={(e) => setConfig({ ...config, oss: { ...config.oss, endpoint: e.target.value } })}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="https://your-server.com"
                      />
                    </div>
                    <div className="p-4 bg-blue-50 border border-blue-100 rounded-xl text-blue-800 text-sm">
                      {t('oss_custom_desc')}
                    </div>
                  </div>
                )}

                {/* 阿里云/MinIO - 需要完整配置 */}
                {config.oss.type !== 'custom' && (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('oss_endpoint')}</label>
                      <input
                        type="text"
                        value={config.oss.endpoint}
                        onChange={(e) => setConfig({ ...config, oss: { ...config.oss, endpoint: e.target.value } })}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="oss-cn-hangzhou.aliyuncs.com"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('oss_bucket')}</label>
                      <input
                        type="text"
                        value={config.oss.bucket || ''}
                        onChange={(e) => setConfig({ ...config, oss: { ...config.oss, bucket: e.target.value } })}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('oss_access_key')}</label>
                      <input
                        type="text"
                        value={config.oss.accessKeyId || ''}
                        onChange={(e) => setConfig({ ...config, oss: { ...config.oss, accessKeyId: e.target.value } })}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('oss_secret_key')}</label>
                      <input
                        type="text"
                        value={config.oss.accessKeySecret || ''}
                        onChange={(e) => setConfig({ ...config, oss: { ...config.oss, accessKeySecret: e.target.value } })}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Notification Tab */}
            {activeTab === 'notification' && (
              <div className="space-y-6 animate-fade-in">
                <div>
                  <h3 className="text-lg font-bold text-gray-800">{t('notification_title')}</h3>
                  <p className="text-gray-500 text-sm">{t('notification_desc')}</p>
                </div>

                {/* WeCom */}
                <div className="border border-gray-200 rounded-xl p-4">
                  <div className="flex items-center justify-between mb-4">
                    <h4 className="font-semibold text-gray-800 flex items-center">
                      <span className="w-8 h-8 bg-green-500 rounded-lg flex items-center justify-center mr-3">
                        <Send size={16} className="text-white" />
                      </span>
                      {t('wechat_work')}
                    </h4>
                    <div className="relative inline-block w-12 align-middle select-none">
                      <input
                        type="checkbox"
                        checked={config.notification.wechat.enabled}
                        onChange={(e) => updateNotification('wechat', 'enabled', e.target.checked)}
                        className="toggle-checkbox absolute block w-6 h-6 rounded-full bg-white border-4 appearance-none cursor-pointer checked:right-0 checked:border-blue-600"
                      />
                      <label className="toggle-label block overflow-hidden h-6 rounded-full bg-gray-300 cursor-pointer"></label>
                    </div>
                  </div>
                  <div className="space-y-3">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('webhook_url')}</label>
                      <input
                        type="text"
                        value={config.notification.wechat.webhookUrl}
                        onChange={(e) => updateNotification('wechat', 'webhookUrl', e.target.value)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=..."
                      />
                    </div>
                    <button
                      onClick={() => handleTestNotification('wechat')}
                      disabled={testingChannel === 'wechat' || !config.notification.wechat.webhookUrl}
                      className="px-4 py-2 bg-green-500 text-white rounded-lg text-sm hover:bg-green-600 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {testingChannel === 'wechat' ? '...' : t('test_send')}
                    </button>
                  </div>
                </div>

                {/* DingTalk */}
                <div className="border border-gray-200 rounded-xl p-4">
                  <div className="flex items-center justify-between mb-4">
                    <h4 className="font-semibold text-gray-800 flex items-center">
                      <span className="w-8 h-8 bg-blue-500 rounded-lg flex items-center justify-center mr-3">
                        <Send size={16} className="text-white" />
                      </span>
                      {t('dingtalk')}
                    </h4>
                    <div className="relative inline-block w-12 align-middle select-none">
                      <input
                        type="checkbox"
                        checked={config.notification.dingtalk.enabled}
                        onChange={(e) => updateNotification('dingtalk', 'enabled', e.target.checked)}
                        className="toggle-checkbox absolute block w-6 h-6 rounded-full bg-white border-4 appearance-none cursor-pointer checked:right-0 checked:border-blue-600"
                      />
                      <label className="toggle-label block overflow-hidden h-6 rounded-full bg-gray-300 cursor-pointer"></label>
                    </div>
                  </div>
                  <div className="space-y-3">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('webhook_url')}</label>
                      <input
                        type="text"
                        value={config.notification.dingtalk.webhookUrl}
                        onChange={(e) => updateNotification('dingtalk', 'webhookUrl', e.target.value)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="https://oapi.dingtalk.com/robot/send?access_token=..."
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('dingtalk_secret')}</label>
                      <input
                        type="text"
                        value={config.notification.dingtalk.secret || ''}
                        onChange={(e) => updateNotification('dingtalk', 'secret', e.target.value)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="SEC..."
                      />
                    </div>
                    <button
                      onClick={() => handleTestNotification('dingtalk')}
                      disabled={testingChannel === 'dingtalk' || !config.notification.dingtalk.webhookUrl}
                      className="px-4 py-2 bg-blue-500 text-white rounded-lg text-sm hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {testingChannel === 'dingtalk' ? '...' : t('test_send')}
                    </button>
                  </div>
                </div>

                {/* Feishu */}
                <div className="border border-gray-200 rounded-xl p-4">
                  <div className="flex items-center justify-between mb-4">
                    <h4 className="font-semibold text-gray-800 flex items-center">
                      <span className="w-8 h-8 bg-indigo-500 rounded-lg flex items-center justify-center mr-3">
                        <Send size={16} className="text-white" />
                      </span>
                      {t('feishu')}
                    </h4>
                    <div className="relative inline-block w-12 align-middle select-none">
                      <input
                        type="checkbox"
                        checked={config.notification.feishu.enabled}
                        onChange={(e) => updateNotification('feishu', 'enabled', e.target.checked)}
                        className="toggle-checkbox absolute block w-6 h-6 rounded-full bg-white border-4 appearance-none cursor-pointer checked:right-0 checked:border-blue-600"
                      />
                      <label className="toggle-label block overflow-hidden h-6 rounded-full bg-gray-300 cursor-pointer"></label>
                    </div>
                  </div>
                  <div className="space-y-3">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">{t('webhook_url')}</label>
                      <input
                        type="text"
                        value={config.notification.feishu.webhookUrl}
                        onChange={(e) => updateNotification('feishu', 'webhookUrl', e.target.value)}
                        className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="https://open.feishu.cn/open-apis/bot/v2/hook/..."
                      />
                    </div>
                    <button
                      onClick={() => handleTestNotification('feishu')}
                      disabled={testingChannel === 'feishu' || !config.notification.feishu.webhookUrl}
                      className="px-4 py-2 bg-indigo-500 text-white rounded-lg text-sm hover:bg-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {testingChannel === 'feishu' ? '...' : t('test_send')}
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* Log Tab */}
            {activeTab === 'log' && (
              <div className="space-y-6 animate-fade-in">
                <div>
                  <h3 className="text-lg font-bold text-gray-800">{t('log_title')}</h3>
                  <p className="text-gray-500 text-sm">{t('log_desc')}</p>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">{t('log_level')}</label>
                    <select
                      value={config.log.level}
                      onChange={(e) => setConfig({ ...config, log: { ...config.log, level: e.target.value } })}
                      className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="debug">DEBUG</option>
                      <option value="info">INFO</option>
                      <option value="warn">WARN</option>
                      <option value="error">ERROR</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">{t('retention')}</label>
                    <input
                      type="number"
                      value={config.log.retentionDays}
                      onChange={(e) => setConfig({ ...config, log: { ...config.log, retentionDays: parseInt(e.target.value) || 30 } })}
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

            {/* Save Button */}
            <div className="mt-8 pt-6 border-t border-gray-100 flex justify-end">
              <button
                onClick={handleSave}
                disabled={isSaving}
                className="bg-blue-600 text-white px-8 py-3 rounded-xl font-medium shadow-lg shadow-blue-200 hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center"
              >
                {isSaving ? (
                  <>
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2"></div>
                    <span>{t('saving')}</span>
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
