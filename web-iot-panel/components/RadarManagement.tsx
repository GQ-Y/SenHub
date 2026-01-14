import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Search, Edit2, Trash2, X, Radar, MapPin, Settings, Activity } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { radarService } from '../src/api/services';
import { useModal } from '../hooks/useModal';
import { Modal, ConfirmModal } from './Modal';

/**
 * 雷达管理页面
 */
export const RadarManagement: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useAppContext();
  const modal = useModal();
  const [devices, setDevices] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeModal, setActiveModal] = useState<'NONE' | 'CREATE' | 'EDIT' | 'DELETE'>('NONE');
  const [selectedDevice, setSelectedDevice] = useState<any | null>(null);
  const [formData, setFormData] = useState({
    deviceId: '',
    radarIp: '',
    radarName: '',
    assemblyId: ''
  });
  const [isSaving, setIsSaving] = useState(false);

  // 加载雷达设备列表
  const loadDevices = async () => {
    setIsLoading(true);
    try {
      const response = await radarService.getRadarDevices();
      setDevices(response.data || []);
    } catch (err: any) {
      console.error('加载雷达设备列表失败:', err);
      modal.showModal({
        message: err.message || '加载雷达设备列表失败',
        type: 'error',
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadDevices();
  }, []);

  const openCreateModal = () => {
    setFormData({ deviceId: '', radarIp: '', radarName: '', assemblyId: '' });
    setActiveModal('CREATE');
  };

  const openEditModal = (device: any) => {
    setSelectedDevice(device);
    setFormData({
      deviceId: device.deviceId || '',
      radarIp: device.radarIp || '',
      radarName: device.radarName || '',
      assemblyId: device.assemblyId || ''
    });
    setActiveModal('EDIT');
  };

  const openDeleteModal = (device: any) => {
    setSelectedDevice(device);
    setActiveModal('DELETE');
  };

  const handleCloseModal = () => {
    setActiveModal('NONE');
    setSelectedDevice(null);
    setIsSaving(false);
  };

  const handleSave = async () => {
    if (!formData.deviceId?.trim()) {
      modal.showModal({
        message: '请输入设备ID',
        type: 'warning',
      });
      return;
    }
    if (!formData.radarIp?.trim()) {
      modal.showModal({
        message: '请输入雷达IP地址',
        type: 'warning',
      });
      return;
    }

    setIsSaving(true);
    try {
      if (activeModal === 'CREATE') {
        await radarService.addRadarDevice(formData);
        modal.showModal({
          message: '创建成功',
          type: 'success',
        });
      } else if (activeModal === 'EDIT' && selectedDevice) {
        // TODO: 实现更新接口
        modal.showModal({
          message: '更新功能待实现',
          type: 'info',
        });
      }
      handleCloseModal();
      await loadDevices();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '保存失败',
        type: 'error',
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedDevice) return;
    setIsSaving(true);
    try {
      // TODO: 实现删除接口
      modal.showModal({
        message: '删除功能待实现',
        type: 'info',
      });
      handleCloseModal();
      await loadDevices();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '删除失败',
        type: 'error',
      });
      setIsSaving(false);
    }
  };

  const getStatusText = (status: number) => {
    switch (status) {
      case 0:
        return '离线';
      case 1:
        return '在线';
      case 2:
        return '采集背景中';
      default:
        return '未知';
    }
  };

  const getStatusColor = (status: number) => {
    switch (status) {
      case 0:
        return 'bg-gray-100 text-gray-700';
      case 1:
        return 'bg-green-100 text-green-700';
      case 2:
        return 'bg-blue-100 text-blue-700';
      default:
        return 'bg-gray-100 text-gray-700';
    }
  };

  const filteredDevices = devices.filter(device =>
    !searchTerm ||
    device.deviceId?.toLowerCase().includes(searchTerm.toLowerCase()) ||
    device.radarIp?.toLowerCase().includes(searchTerm.toLowerCase()) ||
    device.radarName?.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <>
      {/* 弹窗组件 */}
      {modal.isConfirm ? (
        <ConfirmModal
          isOpen={modal.isOpen}
          onClose={modal.closeModal}
          title={modal.modalOptions?.title}
          message={modal.modalOptions?.message || ''}
          onConfirm={() => {
            if (modal.modalOptions?.onConfirm) {
              modal.modalOptions.onConfirm();
            }
            modal.closeModal();
          }}
          onCancel={() => {
            if (modal.modalOptions?.onCancel) {
              modal.modalOptions.onCancel();
            }
            modal.closeModal();
          }}
          confirmText={modal.modalOptions?.confirmText}
          cancelText={modal.modalOptions?.cancelText}
        />
      ) : (
        <Modal
          isOpen={modal.isOpen}
          onClose={modal.closeModal}
          title={modal.modalOptions?.title}
          message={modal.modalOptions?.message || ''}
          type={modal.modalOptions?.type || 'info'}
          confirmText={modal.modalOptions?.confirmText}
          onConfirm={modal.modalOptions?.onConfirm}
        />
      )}

      <div className="space-y-6">
        {/* 操作栏 */}
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-white p-4 rounded-2xl shadow-sm border border-gray-100">
          <div className="flex items-center space-x-3 w-full md:w-auto">
            <div className="relative w-full md:w-64">
              <Search className="absolute left-3 top-2.5 text-gray-400" size={18} />
              <input
                type="text"
                placeholder="搜索设备ID、IP或名称..."
                className="w-full pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
          </div>

          <div className="flex items-center space-x-3 w-full md:w-auto">
            <button
              onClick={loadDevices}
              disabled={isLoading}
              className="flex items-center space-x-2 px-4 py-2 bg-white border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 transition-colors font-medium text-sm shadow-sm disabled:opacity-50"
            >
              <Search size={16} className={isLoading ? 'animate-spin' : ''} />
              <span>{t('refresh')}</span>
            </button>
            <button
              onClick={openCreateModal}
              className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium text-sm shadow-lg shadow-blue-200"
            >
              <Plus size={16} />
              <span>添加雷达设备</span>
            </button>
          </div>
        </div>

        {/* 雷达设备列表 */}
        {isLoading ? (
          <div className="p-12 text-center">
            <div className="inline-block w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
            <p className="mt-4 text-gray-500">加载中...</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredDevices.map((device) => (
              <div
                key={device.deviceId}
                className="bg-white rounded-xl p-6 shadow-sm border border-gray-100 hover:shadow-md transition-shadow"
              >
                <div className="flex justify-between items-start mb-4">
                  <div className="flex-1">
                    <div className="flex items-center space-x-2 mb-1">
                      <Radar size={20} className="text-blue-600" />
                      <h3 className="font-bold text-lg text-gray-800">{device.radarName || device.deviceId}</h3>
                    </div>
                    <div className="flex items-center text-sm text-gray-500 mb-1">
                      <MapPin size={14} className="mr-1" />
                      <span>{device.radarIp || '-'}</span>
                    </div>
                    {device.deviceId && (
                      <div className="text-xs text-gray-400">
                        ID: {device.deviceId}
                      </div>
                    )}
                  </div>
                  <span
                    className={`px-2 py-1 rounded-full text-xs font-medium ${getStatusColor(device.status || 0)}`}
                  >
                    {getStatusText(device.status || 0)}
                  </span>
                </div>

                {/* 设备信息预览 */}
                <div className="space-y-2 mb-4">
                  {device.currentBackgroundId && (
                    <div className="text-sm text-gray-600">
                      <span className="font-medium">背景模型：</span>
                      <span className="text-xs">{device.currentBackgroundId.substring(0, 8)}...</span>
                    </div>
                  )}
                  {device.assemblyId && (
                    <div className="text-sm text-gray-600">
                      <span className="font-medium">所属装置：</span>
                      <span>{device.assemblyId}</span>
                    </div>
                  )}
                </div>

                {/* 操作按钮 */}
                <div className="flex space-x-2 pt-4 border-t border-gray-100">
                  <button
                    onClick={() => navigate(`/radar/${device.deviceId}/background`)}
                    className="flex-1 px-3 py-2 bg-blue-50 text-blue-600 rounded-lg hover:bg-blue-100 transition-colors text-sm font-medium"
                  >
                    背景采集
                  </button>
                  <button
                    onClick={() => navigate(`/radar/${device.deviceId}/zones`)}
                    className="flex-1 px-3 py-2 bg-green-50 text-green-600 rounded-lg hover:bg-green-100 transition-colors text-sm font-medium"
                  >
                    防区配置
                  </button>
                  <button
                    onClick={() => navigate(`/radar/${device.deviceId}/monitoring`)}
                    className="px-3 py-2 text-purple-600 hover:bg-purple-50 rounded-lg transition-colors"
                    title="实时监控"
                  >
                    <Activity size={16} />
                  </button>
                  <button
                    onClick={() => openEditModal(device)}
                    className="px-3 py-2 text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                    title="编辑"
                  >
                    <Edit2 size={16} />
                  </button>
                  <button
                    onClick={() => openDeleteModal(device)}
                    className="px-3 py-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                    title="删除"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {!isLoading && filteredDevices.length === 0 && (
          <div className="p-12 text-center text-gray-500 bg-white rounded-xl">
            <Radar className="mx-auto mb-4 text-gray-300" size={48} />
            <p>暂无雷达设备</p>
            <button
              onClick={openCreateModal}
              className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors"
            >
              添加雷达设备
            </button>
          </div>
        )}

        {/* 创建/编辑弹窗 */}
        {(activeModal === 'CREATE' || activeModal === 'EDIT') && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div
              className="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
              onClick={handleCloseModal}
            ></div>
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg z-10 overflow-hidden">
              <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
                <h3 className="text-lg font-bold text-gray-800">
                  {activeModal === 'CREATE' ? '添加雷达设备' : '编辑雷达设备'}
                </h3>
                <button
                  onClick={handleCloseModal}
                  className="p-1 rounded-full hover:bg-gray-200 transition-colors text-gray-500"
                >
                  <X size={20} />
                </button>
              </div>
              <div className="p-6 space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    设备ID <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.deviceId}
                    onChange={(e) => setFormData({ ...formData, deviceId: e.target.value })}
                    placeholder="请输入设备ID"
                    disabled={activeModal === 'EDIT'}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    雷达IP <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.radarIp}
                    onChange={(e) => setFormData({ ...formData, radarIp: e.target.value })}
                    placeholder="192.168.1.115"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">设备名称</label>
                  <input
                    type="text"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.radarName}
                    onChange={(e) => setFormData({ ...formData, radarName: e.target.value })}
                    placeholder="请输入设备名称（可选）"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">所属装置ID</label>
                  <input
                    type="text"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.assemblyId}
                    onChange={(e) => setFormData({ ...formData, assemblyId: e.target.value })}
                    placeholder="请输入所属装置ID（可选）"
                  />
                </div>
              </div>
              <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end space-x-3">
                <button
                  onClick={handleCloseModal}
                  className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium"
                >
                  {t('cancel')}
                </button>
                <button
                  onClick={handleSave}
                  disabled={isSaving}
                  className="px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200 flex items-center"
                >
                  {isSaving && (
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2"></div>
                  )}
                  {t('save')}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* 删除确认弹窗 */}
        {activeModal === 'DELETE' && selectedDevice && (
          <ConfirmModal
            isOpen={true}
            onClose={handleCloseModal}
            title="删除雷达设备"
            message={`确定要删除雷达设备 "${selectedDevice.radarName || selectedDevice.deviceId}" 吗？此操作无法撤销。`}
            onConfirm={handleDelete}
            onCancel={handleCloseModal}
            confirmText="删除"
            cancelText={t('cancel')}
          />
        )}
      </div>
    </>
  );
};
