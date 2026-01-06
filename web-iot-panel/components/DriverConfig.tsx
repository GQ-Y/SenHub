import React, { useState, useEffect } from 'react';
import { HardDrive, CheckCircle2, XCircle, Settings2, FileCode, X, Plus } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { Driver } from '../types';
import { driverService } from '../src/api/services';
import { Modal as AlertModal } from './Modal';
import { useModal } from '../hooks/useModal';

// Simple reused modal (duplicated to avoid external dependency for this file update)
const Modal = ({ isOpen, onClose, title, children, footer }: any) => {
  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity" onClick={onClose}></div>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg z-10 overflow-hidden animate-fade-in">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
          <h3 className="text-lg font-bold text-gray-800">{title}</h3>
          <button onClick={onClose} className="p-1 rounded-full hover:bg-gray-200 transition-colors text-gray-500">
            <X size={20} />
          </button>
        </div>
        <div className="p-6">{children}</div>
        {footer && <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end space-x-3">{footer}</div>}
      </div>
    </div>
  );
};

export const DriverConfig: React.FC = () => {
  const { t } = useAppContext();
  const alertModal = useModal();
  const [activeModal, setActiveModal] = useState<'NONE' | 'CONFIGURE' | 'LOGS' | 'NEW'>('NONE');
  const [selectedDriver, setSelectedDriver] = useState<Driver | null>(null);
  const [formData, setFormData] = useState<Partial<Driver>>({});
  const [drivers, setDrivers] = useState<Driver[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  
  // Mock Logs (如果后端不支持日志API，使用mock数据)
  const MOCK_LOGS = `[2023-10-27 10:00:01] [INFO] SDK Initialized successfully.
[2023-10-27 10:00:02] [INFO] Loading library from /usr/lib/hikvision/libhcnetsdk.so...
[2023-10-27 10:00:02] [DEBUG] Handle created: 0x7f8a1234
[2023-10-27 10:05:12] [INFO] Device connected: 192.168.1.101
[2023-10-27 10:05:15] [WARN] Keep-alive packet latency high: 120ms
[2023-10-27 11:20:00] [INFO] Stream started for Channel 1`;

  // 加载驱动列表
  useEffect(() => {
    const loadDrivers = async () => {
      setIsLoading(true);
      try {
        const response = await driverService.getDrivers();
        // 转换后端数据格式
        const driverList = response.data.map((d: any) => ({
          id: d.id || d.name?.toLowerCase().replace(/\s+/g, '-'),
          name: d.name || '',
          version: d.version || '1.0.0',
          status: (d.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE') as 'ACTIVE' | 'INACTIVE',
          libPath: d.libPath || '',
          logPath: d.logPath || '',
          logLevel: d.logLevel || 0,
        }));
        setDrivers(driverList);
      } catch (err) {
        console.error('加载驱动列表失败:', err);
      } finally {
        setIsLoading(false);
      }
    };

    loadDrivers();
  }, []);

  const handleConfigure = (driver: Driver) => {
    setSelectedDriver(driver);
    setFormData({ ...driver });
    setActiveModal('CONFIGURE');
  };

  const handleLogs = (driver: Driver) => {
    setSelectedDriver(driver);
    setActiveModal('LOGS');
  };

  const handleNewSDK = () => {
    setFormData({ name: '', version: '1.0.0', libPath: '', logPath: '/var/log/new_sdk.log', logLevel: 1 });
    setActiveModal('NEW');
  };

  const handleSave = async () => {
    setIsSaving(true);
    try {
      if (activeModal === 'NEW') {
        await driverService.addDriver({
          name: formData.name,
          version: formData.version,
          libPath: formData.libPath,
          logPath: formData.logPath,
          logLevel: formData.logLevel,
        });
      } else if (activeModal === 'CONFIGURE' && selectedDriver) {
        await driverService.updateDriver(selectedDriver.id, {
          libPath: formData.libPath,
          logPath: formData.logPath,
          logLevel: formData.logLevel,
        });
      }
      setActiveModal('NONE');
      setSelectedDriver(null);
      // 重新加载列表
      const response = await driverService.getDrivers();
      const driverList = response.data.map((d: any) => ({
        id: d.id || d.name?.toLowerCase().replace(/\s+/g, '-'),
        name: d.name || '',
        version: d.version || '1.0.0',
        status: (d.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE') as 'ACTIVE' | 'INACTIVE',
        libPath: d.libPath || '',
        logPath: d.logPath || '',
        logLevel: d.logLevel || 0,
      }));
      setDrivers(driverList);
    } catch (err: any) {
      alert(err.message || '保存失败');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <>
      {/* 弹窗组件 */}
      <AlertModal
        isOpen={alertModal.isOpen}
        onClose={alertModal.closeModal}
        title={alertModal.modalOptions?.title}
        message={alertModal.modalOptions?.message || ''}
        type={alertModal.modalOptions?.type || 'info'}
        confirmText={alertModal.modalOptions?.confirmText}
        onConfirm={alertModal.modalOptions?.onConfirm}
      />
      
      <div className="space-y-6">
       <div className="bg-gradient-to-r from-blue-600 to-blue-800 rounded-2xl p-8 text-white shadow-xl shadow-blue-900/20">
        <h2 className="text-2xl font-bold mb-2">{t('driver_mgmt')}</h2>
        <p className="text-blue-100 max-w-2xl">
            {t('driver_desc')}
        </p>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center h-64">
          <div className="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
        </div>
      ) : (
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {drivers.map((driver) => (
          <div key={driver.id} className="bg-white rounded-2xl p-6 shadow-sm border border-gray-100 flex flex-col justify-between h-full hover:shadow-lg transition-shadow duration-300">
            <div>
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center space-x-3">
                  <div className={`p-3 rounded-xl ${driver.status === 'ACTIVE' ? 'bg-blue-50 text-blue-600' : 'bg-gray-100 text-gray-500'}`}>
                    <HardDrive size={24} />
                  </div>
                  <div>
                    <h3 className="font-bold text-gray-800 text-lg">{driver.name}</h3>
                    <p className="text-sm text-gray-500">v{driver.version}</p>
                  </div>
                </div>
                <div className={`flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-semibold border ${driver.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border-green-200' : 'bg-gray-50 text-gray-600 border-gray-200'}`}>
                   {driver.status === 'ACTIVE' ? <CheckCircle2 size={12}/> : <XCircle size={12}/>}
                   <span>{driver.status}</span>
                </div>
              </div>

              <div className="space-y-3 mb-6">
                <div className="bg-gray-50 rounded-lg p-3 text-sm font-mono text-gray-600 break-all border border-gray-100">
                  <span className="text-gray-400 select-none">LIB: </span>{driver.libPath}
                </div>
                <div className="bg-gray-50 rounded-lg p-3 text-sm font-mono text-gray-600 break-all border border-gray-100">
                  <span className="text-gray-400 select-none">LOG: </span>{driver.logPath}
                </div>
              </div>
            </div>

            <div className="flex items-center gap-3 pt-4 border-t border-gray-100">
              <button 
                onClick={() => handleConfigure(driver)}
                className="flex-1 flex items-center justify-center space-x-2 py-2.5 rounded-xl bg-gray-50 hover:bg-gray-100 text-gray-700 font-medium transition-colors text-sm"
              >
                <Settings2 size={16} />
                <span>{t('configure')}</span>
              </button>
               <button 
                onClick={() => handleLogs(driver)}
                className="flex-1 flex items-center justify-center space-x-2 py-2.5 rounded-xl bg-gray-50 hover:bg-gray-100 text-gray-700 font-medium transition-colors text-sm"
               >
                <FileCode size={16} />
                <span>{t('driver_logs')}</span>
              </button>
            </div>
          </div>
        ))}

        {/* Add New Driver Card */}
         <div 
            onClick={handleNewSDK}
            className="bg-gray-50 rounded-2xl p-6 border-2 border-dashed border-gray-200 flex flex-col items-center justify-center min-h-[280px] text-gray-400 hover:border-blue-400 hover:text-blue-500 hover:bg-blue-50/10 transition-all cursor-pointer group"
         >
            <div className="w-16 h-16 rounded-full bg-white shadow-sm flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                <Plus size={32} className="text-gray-300 group-hover:text-blue-500" />
            </div>
            <span className="font-semibold">{t('integrate_new')}</span>
         </div>
      </div>
      )}

      {/* Configuration Modal */}
      <Modal
        isOpen={activeModal === 'CONFIGURE'}
        onClose={() => setActiveModal('NONE')}
        title={t('driver_config')}
        footer={
          <>
            <button onClick={() => setActiveModal('NONE')} disabled={isSaving} className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium disabled:opacity-50">{t('cancel')}</button>
            <button onClick={handleSave} disabled={isSaving} className="px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200 disabled:opacity-50 flex items-center">
              {isSaving && <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2"></div>}
              {t('save')}
            </button>
          </>
        }
      >
        <div className="space-y-4">
            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('lib_path')}</label>
                <input 
                    type="text" 
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500 font-mono text-sm"
                    value={formData.libPath || ''}
                    onChange={(e) => setFormData({...formData, libPath: e.target.value})}
                />
            </div>
            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('log_path')}</label>
                <input 
                    type="text" 
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500 font-mono text-sm"
                    value={formData.logPath || ''}
                    onChange={(e) => setFormData({...formData, logPath: e.target.value})}
                />
            </div>
            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('log_level')}</label>
                <select 
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.logLevel || 0}
                    onChange={(e) => setFormData({...formData, logLevel: parseInt(e.target.value)})}
                >
                    <option value="0">0 - None</option>
                    <option value="1">1 - Error</option>
                    <option value="2">2 - Info</option>
                    <option value="3">3 - Debug</option>
                </select>
            </div>
        </div>
      </Modal>

      {/* Logs Modal */}
      <Modal
        isOpen={activeModal === 'LOGS'}
        onClose={() => setActiveModal('NONE')}
        title={`${t('driver_logs')}: ${selectedDriver?.name}`}
        footer={
            <button onClick={() => setActiveModal('NONE')} className="px-4 py-2 bg-gray-100 text-gray-700 rounded-xl hover:bg-gray-200 transition-colors font-medium">{t('cancel')}</button>
        }
      >
        <div className="bg-gray-900 rounded-xl p-4 overflow-auto max-h-[300px]">
            <pre className="text-green-400 font-mono text-xs whitespace-pre-wrap">{MOCK_LOGS}</pre>
        </div>
      </Modal>

      {/* New SDK Modal */}
      <Modal
        isOpen={activeModal === 'NEW'}
        onClose={() => setActiveModal('NONE')}
        title={t('add_driver_title')}
        footer={
          <>
            <button onClick={() => setActiveModal('NONE')} disabled={isSaving} className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium disabled:opacity-50">{t('cancel')}</button>
            <button onClick={handleSave} disabled={isSaving} className="px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200 disabled:opacity-50 flex items-center">
              {isSaving && <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2"></div>}
              {t('save')}
            </button>
          </>
        }
      >
         <div className="space-y-4">
            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('driver_name')}</label>
                <input 
                    type="text" 
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="e.g. Huawei HoloSens SDK"
                    value={formData.name || ''}
                    onChange={(e) => setFormData({...formData, name: e.target.value})}
                />
            </div>
            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('version')}</label>
                <input 
                    type="text" 
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="e.g. 1.0.0"
                    value={formData.version || ''}
                    onChange={(e) => setFormData({...formData, version: e.target.value})}
                />
            </div>
            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('lib_path')}</label>
                <input 
                    type="text" 
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500 font-mono text-sm"
                    placeholder="/usr/lib/..."
                    value={formData.libPath || ''}
                    onChange={(e) => setFormData({...formData, libPath: e.target.value})}
                />
            </div>
        </div>
      </Modal>

    </div>
    </>
  );
};