import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Search, 
  Plus, 
  RefreshCw, 
  Filter,
  Video,
  Power,
  Trash2,
  Edit2,
  X,
  AlertTriangle,
  Check
} from 'lucide-react';
import { Device, DeviceStatus } from '../types';
import { useAppContext } from '../contexts/AppContext';
import { deviceService } from '../src/api/services';
import { Modal } from './Modal';
import { useModal } from '../hooks/useModal';

// Reusable Modal Component
const CustomModal = ({ 
  isOpen, 
  onClose, 
  title, 
  children, 
  footer 
}: { 
  isOpen: boolean; 
  onClose: () => void; 
  title: string; 
  children: React.ReactNode; 
  footer?: React.ReactNode 
}) => {
  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity" onClick={onClose}></div>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg z-10 overflow-hidden animate-fade-in transform transition-all scale-100">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
          <h3 className="text-lg font-bold text-gray-800">{title}</h3>
          <button onClick={onClose} className="p-1 rounded-full hover:bg-gray-200 transition-colors text-gray-500">
            <X size={20} />
          </button>
        </div>
        <div className="p-6">
          {children}
        </div>
        {footer && (
          <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end space-x-3">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
};

export const DeviceList: React.FC = () => {
  const navigate = useNavigate();
  const { t, language } = useAppContext();
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  
  // Local state for actions
  const [activeModal, setActiveModal] = useState<'NONE' | 'ADD' | 'EDIT' | 'DELETE' | 'REBOOT'>('NONE');
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [devices, setDevices] = useState<Device[]>([]);
  const [isLoadingDevices, setIsLoadingDevices] = useState(true);
  const [error, setError] = useState<string>('');
  
  // 弹窗管理
  const modal = useModal();

  // 品牌列表状态
  const [brands, setBrands] = useState<string[]>(['Hikvision', 'Dahua', 'Tiandy', 'Auto']);
  const [defaultBrand, setDefaultBrand] = useState<string>('Auto');
  const [isLoadingBrands, setIsLoadingBrands] = useState(false);

  // Form State
  const [formData, setFormData] = useState<Partial<Device> & { username?: string; password?: string }>({
    name: '', ip: '', port: 8000, brand: 'Auto', model: '', username: 'admin', password: '123456'
  });

  // 加载设备列表
  const loadDevices = async () => {
    setIsLoadingDevices(true);
    setError('');
    try {
      const params: any = {};
      if (searchTerm) params.search = searchTerm;
      if (statusFilter !== 'ALL') params.status = statusFilter;
      
      const response = await deviceService.getDevices(params);
      // 转换后端数据格式到前端格式
      const deviceList = response.data.map((d: any) => ({
        id: d.id || `${d.ip}:${d.port}`,
        name: d.name || '',
        ip: d.ip || '',
        port: d.port || 8000,
        brand: d.brand || defaultBrand || 'Auto',
        model: d.model || '',
        status: (d.status?.toUpperCase() as DeviceStatus) || DeviceStatus.OFFLINE,
        lastSeen: d.lastSeen || 'Never',
        firmware: d.firmware || '',
        rtspUrl: d.rtspUrl || '',
      }));
      setDevices(deviceList);
    } catch (err: any) {
      setError(err.message || '加载设备列表失败');
      console.error('加载设备列表失败:', err);
    } finally {
      setIsLoadingDevices(false);
    }
  };

  // 加载品牌列表
  const loadBrands = async () => {
    setIsLoadingBrands(true);
    try {
      const response = await deviceService.getBrands();
      if (response.data?.supported) {
        setBrands(response.data.supported);
        if (response.data.default) {
          setDefaultBrand(response.data.default);
        }
      }
    } catch (err: any) {
      console.error('加载品牌列表失败:', err);
      // 失败时使用默认品牌列表
      setBrands(['Hikvision', 'Dahua', 'Tiandy', 'Auto']);
      setDefaultBrand('Auto');
    } finally {
      setIsLoadingBrands(false);
    }
  };

  // 初始加载品牌列表（只执行一次）
  useEffect(() => {
    loadBrands();
  }, []);

  // 初始加载和搜索/过滤变化时重新加载设备列表
  useEffect(() => {
    loadDevices();
  }, [searchTerm, statusFilter]);

  const openAddModal = () => {
    setFormData({ name: '', ip: '', port: 8000, brand: defaultBrand, model: '', username: 'admin', password: '123456' });
    setActiveModal('ADD');
  };

  const openEditModal = (device: Device) => {
    setSelectedDevice(device);
    setFormData({ ...device });
    setActiveModal('EDIT');
  };

  const openDeleteModal = (device: Device) => {
    setSelectedDevice(device);
    setActiveModal('DELETE');
  };

  const openRebootModal = (device: Device) => {
    setSelectedDevice(device);
    setActiveModal('REBOOT');
  };

  const handleCloseModal = () => {
    setActiveModal('NONE');
    setSelectedDevice(null);
    setIsLoading(false);
  };

  const handleSave = async () => {
    setIsLoading(true);
    try {
      if (activeModal === 'ADD') {
        await deviceService.addDevice({
          name: formData.name,
          ip: formData.ip,
          port: formData.port,
          brand: formData.brand,
          model: formData.model,
          username: formData.username || 'admin',
          password: formData.password || '123456',
          channel: 1,
        } as any);
      } else if (activeModal === 'EDIT' && selectedDevice) {
        await deviceService.updateDevice(selectedDevice.id, {
          name: formData.name,
          ip: formData.ip,
          port: formData.port,
          brand: formData.brand,
          model: formData.model,
        });
      }
      handleCloseModal();
      await loadDevices(); // 重新加载列表
      modal.showModal({
        message: '保存成功',
        type: 'success',
      });
    } catch (err: any) {
      modal.showModal({
        message: err.message || '保存失败',
        type: 'error',
      });
    } finally {
      setIsLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedDevice) return;
    setIsLoading(true);
    try {
      await deviceService.deleteDevice(selectedDevice.id);
      handleCloseModal();
      await loadDevices(); // 重新加载列表
      modal.showModal({
        message: '删除成功',
        type: 'success',
      });
    } catch (err: any) {
      modal.showModal({
        message: err.message || '删除失败',
        type: 'error',
      });
      setIsLoading(false);
    }
  };

  const handleReboot = async () => {
    if (!selectedDevice) return;
    setIsLoading(true);
    try {
      await deviceService.rebootDevice(selectedDevice.id);
      handleCloseModal();
      await loadDevices(); // 重新加载列表
      modal.showModal({
        message: '重启命令已发送',
        type: 'success',
      });
    } catch (err: any) {
      modal.showModal({
        message: err.message || '重启失败',
        type: 'error',
      });
      setIsLoading(false);
    }
  };

  // Translation Helpers
  const getTranslatedStatus = (status: DeviceStatus) => {
    switch (status) {
      case DeviceStatus.ONLINE: return t('online');
      case DeviceStatus.OFFLINE: return t('offline');
      case DeviceStatus.WARNING: return t('warning');
      default: return status;
    }
  };

  const getTranslatedLastSeen = (lastSeen: string) => {
    // Simple mock translation logic based on string presence
    if (lastSeen.includes('Just now')) return t('just_now');
    if (lastSeen.includes('mins ago')) return lastSeen.replace('mins ago', t('mins_ago'));
    if (lastSeen.includes('days ago')) return lastSeen.replace('days ago', t('days_ago'));
    return lastSeen;
  };

  // 设备列表已经在API层面过滤，这里直接使用
  const filteredDevices = devices;

  const getStatusColor = (status: DeviceStatus) => {
    switch(status) {
      case DeviceStatus.ONLINE: return 'bg-green-100 text-green-700 border-green-200';
      case DeviceStatus.OFFLINE: return 'bg-red-100 text-red-700 border-red-200';
      case DeviceStatus.WARNING: return 'bg-orange-100 text-orange-700 border-orange-200';
      default: return 'bg-gray-100 text-gray-700';
    }
  };

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
      
      <div className="space-y-6">
      {/* Action Bar */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-white p-4 rounded-2xl shadow-sm border border-gray-100">
        <div className="flex items-center space-x-3 w-full md:w-auto">
          <div className="relative w-full md:w-64">
            <Search className="absolute left-3 top-2.5 text-gray-400" size={18} />
            <input 
              type="text" 
              placeholder={t('search_placeholder')} 
              className="w-full pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
          
          <div className="relative">
            <Filter className="absolute left-3 top-2.5 text-gray-400" size={18} />
            <select 
              className="pl-10 pr-8 py-2 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 outline-none appearance-none cursor-pointer text-gray-700"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <option value="ALL">{t('filter_all')}</option>
              <option value="ONLINE">{t('online')}</option>
              <option value="OFFLINE">{t('offline')}</option>
              <option value="WARNING">{t('warning')}</option>
            </select>
          </div>
        </div>

        <div className="flex items-center space-x-3 w-full md:w-auto">
          <button 
            onClick={loadDevices}
            disabled={isLoadingDevices}
            className="flex items-center space-x-2 px-4 py-2 bg-white border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 transition-colors font-medium text-sm shadow-sm disabled:opacity-50"
          >
            <RefreshCw size={16} className={isLoadingDevices ? 'animate-spin' : ''} />
            <span>{t('refresh')}</span>
          </button>
          <button 
            onClick={openAddModal}
            className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium text-sm shadow-lg shadow-blue-200"
          >
            <Plus size={16} />
            <span>{t('add_device')}</span>
          </button>
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl">
          {error}
        </div>
      )}

      {/* Device Table */}
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
        {isLoadingDevices ? (
          <div className="p-12 text-center">
            <div className="inline-block w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
            <p className="mt-4 text-gray-500">加载中...</p>
          </div>
        ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead>
              <tr className="bg-gray-50/50 border-b border-gray-100">
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('status')}</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('device_name')}</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('ip_address')}</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('brand_model')}</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('last_seen')}</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider text-right">{t('actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {filteredDevices.map((device) => (
                <tr 
                  key={device.id} 
                  className="hover:bg-blue-50/30 transition-colors cursor-pointer group"
                  onClick={() => navigate(`/devices/${device.id}`)}
                >
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${getStatusColor(device.status)}`}>
                      <span className={`w-1.5 h-1.5 rounded-full mr-1.5 ${device.status === DeviceStatus.ONLINE ? 'bg-green-500' : device.status === DeviceStatus.WARNING ? 'bg-orange-500' : 'bg-red-500'}`}></span>
                      {getTranslatedStatus(device.status)}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="font-medium text-gray-900">{device.name}</div>
                    <div className="text-xs text-gray-400">ID: {device.id}</div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600 font-mono">
                    {device.ip}:{device.port}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                    <div className="flex flex-col">
                      <span className="font-medium">{device.brand}</span>
                      <span className="text-xs text-gray-400">{device.model}</span>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {getTranslatedLastSeen(device.lastSeen)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                    <div className="flex items-center justify-end space-x-1 opacity-100 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
                      <button 
                        className="p-2 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                        title={t('live_view')}
                        onClick={(e) => { e.stopPropagation(); navigate(`/devices/${device.id}`); }}
                      >
                        <Video size={18} />
                      </button>
                      <button 
                        className="p-2 text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                        title={t('edit_device')}
                        onClick={(e) => { e.stopPropagation(); openEditModal(device); }}
                      >
                        <Edit2 size={18} />
                      </button>
                       <button 
                        className="p-2 text-orange-500 hover:bg-orange-50 rounded-lg transition-colors"
                        title={t('reboot')}
                        onClick={(e) => { e.stopPropagation(); openRebootModal(device); }}
                      >
                        <Power size={18} />
                      </button>
                      <button 
                        className="p-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                        title={t('delete')}
                        onClick={(e) => { e.stopPropagation(); openDeleteModal(device); }}
                      >
                        <Trash2 size={18} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        )}
        {!isLoadingDevices && filteredDevices.length === 0 && (
          <div className="p-12 text-center text-gray-500">
            <Search className="mx-auto mb-4 text-gray-300" size={48} />
            <p>No devices found matching your criteria.</p>
          </div>
        )}
      </div>

      {/* Add / Edit Modal */}
      <CustomModal
        isOpen={activeModal === 'ADD' || activeModal === 'EDIT'}
        onClose={handleCloseModal}
        title={activeModal === 'ADD' ? t('add_device') : t('edit_device')}
        footer={
          <>
            <button onClick={handleCloseModal} className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium">
              {t('cancel')}
            </button>
            <button onClick={handleSave} disabled={isLoading} className="px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200 flex items-center">
               {isLoading && <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2"></div>}
               {t('save')}
            </button>
          </>
        }
      >
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
           <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('device_name')}</label>
              <input 
                type="text" 
                className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500" 
                value={formData.name}
                onChange={e => setFormData({...formData, name: e.target.value})}
              />
           </div>
           <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('ip_address')}</label>
              <input 
                type="text" 
                className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500" 
                value={formData.ip}
                onChange={e => setFormData({...formData, ip: e.target.value})}
              />
           </div>
           <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('form_port')}</label>
              <input 
                type="number" 
                className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500" 
                value={formData.port}
                onChange={e => setFormData({...formData, port: parseInt(e.target.value) || 0})}
              />
           </div>
           <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('brand')}</label>
              <select 
                className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                value={formData.brand || defaultBrand}
                onChange={e => setFormData({...formData, brand: e.target.value})}
                disabled={isLoadingBrands}
              >
                {isLoadingBrands ? (
                  <option>加载中...</option>
                ) : (
                  brands.map(brand => (
                    <option key={brand} value={brand}>
                      {brand === 'Auto' ? (language === 'zh' ? '自动检测' : 'Auto Detect') : brand}
                    </option>
                  ))
                )}
              </select>
           </div>
           <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('model')}</label>
              <input 
                type="text" 
                className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500" 
                value={formData.model}
                onChange={e => setFormData({...formData, model: e.target.value})}
              />
           </div>
           <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('form_username')}</label>
              <input 
                type="text" 
                className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500" 
                value={formData.username || 'admin'}
                onChange={e => setFormData({...formData, username: e.target.value})}
              />
           </div>
           <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('form_password')}</label>
              <input 
                type="password" 
                className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500" 
                value={formData.password || ''}
                onChange={e => setFormData({...formData, password: e.target.value})}
                placeholder="输入设备密码"
              />
           </div>
        </div>
      </CustomModal>

      {/* Delete Confirmation Modal */}
      <CustomModal
        isOpen={activeModal === 'DELETE'}
        onClose={handleCloseModal}
        title={t('confirm_delete_title')}
        footer={
          <>
            <button onClick={handleCloseModal} className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium">
              {t('cancel')}
            </button>
            <button onClick={handleDelete} disabled={isLoading} className="px-4 py-2 bg-red-600 text-white rounded-xl hover:bg-red-700 transition-colors font-medium shadow-lg shadow-red-200 flex items-center">
              {isLoading && <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2"></div>}
              {t('delete')}
            </button>
          </>
        }
      >
        <div className="flex flex-col items-center text-center p-4">
            <div className="w-12 h-12 bg-red-100 rounded-full flex items-center justify-center text-red-600 mb-4">
                <AlertTriangle size={24} />
            </div>
            <p className="text-gray-600">{t('confirm_delete_msg')}</p>
            <p className="font-bold text-gray-800 mt-2">{selectedDevice?.name} ({selectedDevice?.ip})</p>
        </div>
      </CustomModal>

      {/* Reboot Confirmation Modal */}
       <CustomModal
        isOpen={activeModal === 'REBOOT'}
        onClose={handleCloseModal}
        title={t('confirm_reboot_title')}
        footer={
          <>
            <button onClick={handleCloseModal} className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium">
              {t('cancel')}
            </button>
            <button onClick={handleReboot} disabled={isLoading} className="px-4 py-2 bg-orange-500 text-white rounded-xl hover:bg-orange-600 transition-colors font-medium shadow-lg shadow-orange-200 flex items-center">
              {isLoading && <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2"></div>}
              {t('confirm')}
            </button>
          </>
        }
      >
        <div className="flex flex-col items-center text-center p-4">
            <div className="w-12 h-12 bg-orange-100 rounded-full flex items-center justify-center text-orange-600 mb-4">
                <Power size={24} />
            </div>
            <p className="text-gray-600">{t('confirm_reboot_msg')}</p>
             <p className="font-bold text-gray-800 mt-2">{selectedDevice?.name}</p>
        </div>
      </CustomModal>

    </div>
    </>
  );
};