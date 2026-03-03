import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { Plus, Search, Edit2, Trash2, X, Radar, MapPin, Settings, Activity, Bell, BellOff } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { radarService, assemblyService } from '../src/api/services';
import { useModal } from '../hooks/useModal';
import { Modal, ConfirmModal } from './Modal';
import { Assembly } from '../types';

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
    radarIp: '',
    radarName: '',
    assemblyId: '',
    radarSerial: ''
  });
  const [assemblies, setAssemblies] = useState<Assembly[]>([]);
  const [isSaving, setIsSaving] = useState(false);
  const [isDetecting, setIsDetecting] = useState(false);
  const [detectResult, setDetectResult] = useState<{ reachable?: boolean; radarSerial?: string; message?: string } | null>(null);
  const [detectionStates, setDetectionStates] = useState<Record<string, boolean>>({});
  const [detectionTogglingId, setDetectionTogglingId] = useState<string | null>(null);

  // 加载雷达设备列表
  const loadDevices = async () => {
    setIsLoading(true);
    try {
      const response = await radarService.getRadarDevices();
      const deviceList = response.data || [];
      setDevices(deviceList);
      const states: Record<string, boolean> = {};
      await Promise.allSettled(
        deviceList.map(async (d: any) => {
          try {
            const res = await radarService.getDetectionEnabled(d.deviceId);
            states[d.deviceId] = !!res.data?.detectionEnabled;
          } catch { /* ignore */ }
        })
      );
      setDetectionStates(states);
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

  const handleToggleDetection = async (deviceId: string) => {
    if (detectionTogglingId) return;
    const next = !detectionStates[deviceId];
    setDetectionTogglingId(deviceId);
    try {
      await radarService.setDetectionEnabled(deviceId, next);
      setDetectionStates((prev) => ({ ...prev, [deviceId]: next }));
    } catch (e: any) {
      modal.showModal({ message: e?.message || '操作失败', type: 'error' });
    } finally {
      setDetectionTogglingId(null);
    }
  };

  useEffect(() => {
    loadDevices();
    loadAssemblies();
  }, []);
  
  const loadAssemblies = async () => {
    try {
      const response = await assemblyService.getAssemblies({});
      setAssemblies(response.data || []);
    } catch (err) {
      console.error('加载装置列表失败', err);
    }
  };

  const openCreateModal = () => {
    setFormData({ radarIp: '', radarName: '', assemblyId: '', radarSerial: '' });
    setDetectResult(null);
    setActiveModal('CREATE');
  };

  const openEditModal = (device: any) => {
    setSelectedDevice(device);
    setFormData({
      radarIp: device.radarIp || '',
      radarName: device.radarName || '',
      assemblyId: device.assemblyId || '',
      radarSerial: device.radarSerial || ''
    });
    setDetectResult(null);
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
    setIsDetecting(false);
    setDetectResult(null);
  };

  const handleDetect = async () => {
    if (!formData.radarIp?.trim()) {
      modal.showModal({
        message: '请输入雷达IP地址',
        type: 'warning',
      });
      return;
    }
    const ipPattern = /^(\d{1,3}\.){3}\d{1,3}$/;
    if (!ipPattern.test(formData.radarIp.trim())) {
      modal.showModal({
        message: '请输入有效的IP地址格式',
        type: 'warning',
      });
      return;
    }
    setIsDetecting(true);
    setDetectResult(null);
    try {
      const res = await radarService.testRadarConnection(formData.radarIp.trim());
      const data = res.data || {};
      setDetectResult({
        reachable: data.reachable,
        radarSerial: data.radarSerial,
        message: data.message,
      });
      if (!data.reachable) {
        modal.showModal({
          message: data.message || '无法连接到雷达',
          type: 'warning',
        });
      } else {
        setFormData((prev) => ({ ...prev, radarSerial: data.radarSerial || prev.radarSerial }));
        modal.showModal({
          message: '检测成功' + (data.radarSerial ? `，序列号：${data.radarSerial}` : ''),
          type: 'success',
        });
      }
    } catch (err: any) {
      modal.showModal({
        message: err.message || '检测失败',
        type: 'error',
      });
    } finally {
      setIsDetecting(false);
    }
  };

  const handleSave = async () => {
    if (!formData.radarIp?.trim()) {
      modal.showModal({
        message: '请输入雷达IP地址',
        type: 'warning',
      });
      return;
    }
    
    // 验证IP格式
    const ipPattern = /^(\d{1,3}\.){3}\d{1,3}$/;
    if (!ipPattern.test(formData.radarIp.trim())) {
      modal.showModal({
        message: '请输入有效的IP地址格式',
        type: 'warning',
      });
      return;
    }
    
    if (!formData.radarName?.trim()) {
      modal.showModal({
        message: '请输入雷达名称',
        type: 'warning',
      });
      return;
    }

    // 新增时建议先检测连通性，但不强制要求 SN
    // 雷达上线后系统会自动填充 SN
    if (activeModal === 'CREATE') {
      // 如果没有检测结果，提示用户（但不强制）
      if (!detectResult?.reachable && !formData.radarSerial?.trim()) {
        // 只是提示，不阻止添加
        const confirmAdd = window.confirm(
          '未检测到雷达或未获取到序列号（SN）。\n\n' +
          '您仍可以添加设备，系统将在雷达上线后自动填充 SN。\n\n' +
          '是否继续添加？'
        );
        if (!confirmAdd) {
          return;
        }
      }
    }

    setIsSaving(true);
    try {
      if (activeModal === 'CREATE') {
        const result = await radarService.addRadarDevice(formData);
        const data = result.data || {};
        const msg = data.snPending 
          ? '设备已添加，SN将在雷达上线后自动填充' 
          : '创建成功';
        modal.showModal({
          message: msg,
          type: 'success',
        });
      } else if (activeModal === 'EDIT' && selectedDevice) {
        await radarService.updateRadarDevice(selectedDevice.deviceId, {
          radarIp: formData.radarIp,
          radarName: formData.radarName,
          assemblyId: formData.assemblyId,
        });
        modal.showModal({
          message: '更新成功',
          type: 'success',
        });
      }
      handleCloseModal();
      setFormData({ radarIp: '', radarName: '', assemblyId: '', radarSerial: '' });
      setDetectResult(null);
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
      const result = await radarService.deleteRadarDevice(selectedDevice.deviceId);
      const data = result.data || {};
      modal.showModal({
        message: `雷达设备已删除\n- 删除背景模型: ${data.deletedBackgrounds || 0} 个\n- 删除防区: ${data.deletedZones || 0} 个\n- 删除侵入记录: ${data.deletedIntrusions || 0} 条`,
        type: 'success',
      });
      handleCloseModal();
      await loadDevices();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '删除失败',
        type: 'error',
      });
    } finally {
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

                {/* 报警开关 */}
                <div className="flex items-center justify-between p-2.5 rounded-lg bg-gray-50 border border-gray-100 mb-3">
                  <div className="flex items-center gap-2">
                    {detectionStates[device.deviceId] ? (
                      <Bell size={14} className="text-amber-500" />
                    ) : (
                      <BellOff size={14} className="text-gray-400" />
                    )}
                    <span className="text-xs font-medium text-gray-600">侵入报警</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleToggleDetection(device.deviceId)}
                    disabled={detectionTogglingId === device.deviceId}
                    className={`relative w-9 h-5 rounded-full transition-colors duration-200 focus:outline-none disabled:opacity-50 ${
                      detectionStates[device.deviceId] ? 'bg-amber-500' : 'bg-gray-300'
                    }`}
                  >
                    <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform duration-200 ${
                      detectionStates[device.deviceId] ? 'translate-x-4' : 'translate-x-0'
                    }`} />
                  </button>
                </div>

                {/* 设备信息预览 */}
                <div className="space-y-2 mb-4">
                  {device.radarSerial && (
                    <div className="text-sm text-gray-600">
                      <span className="font-medium">序列号：</span>
                      <span className="text-xs font-mono">{device.radarSerial}</span>
                    </div>
                  )}
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

        {/* 创建/编辑弹窗 - 使用 Portal 挂载到 body，遮罩 fixed 覆盖完整视口 */}
        {(activeModal === 'CREATE' || activeModal === 'EDIT') &&
          createPortal(
            <div className="fixed inset-0 z-[100] flex items-center justify-center p-4" aria-modal="true" role="dialog">
              <div
                className="fixed inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
                onClick={handleCloseModal}
                aria-hidden="true"
              />
              <div className="relative z-10 bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden">
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
                    雷达IP <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.radarIp}
                    onChange={(e) => setFormData({ ...formData, radarIp: e.target.value })}
                    placeholder="192.168.1.115"
                  />
                  <p className="mt-1 text-xs text-gray-500">设备ID将自动生成</p>
                  <div className="mt-3 flex items-center space-x-2">
                    <button
                      onClick={handleDetect}
                      disabled={isDetecting}
                      className="px-3 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700 transition-colors disabled:opacity-60"
                    >
                      {isDetecting ? '检测中...' : '检测雷达'}
                    </button>
                    {detectResult && (
                      <span className={`text-sm ${detectResult.reachable ? 'text-green-600' : 'text-red-600'}`}>
                        {detectResult.reachable ? '已连接' : '未连接'}
                        {detectResult.radarSerial ? ` · 序列号: ${detectResult.radarSerial}` : ''}
                      </span>
                    )}
                  </div>
                  {detectResult?.message && (
                    <p className="mt-1 text-xs text-gray-500 whitespace-pre-line">{detectResult.message}</p>
                  )}
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    雷达序列号(SN) <span className="text-gray-400 text-xs font-normal">可选</span>
                  </label>
                  <input
                    type="text"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.radarSerial}
                    readOnly={!!detectResult?.radarSerial}
                    onChange={(e) => setFormData({ ...formData, radarSerial: e.target.value })}
                    placeholder="可选，雷达上线后自动填充"
                  />
                  <p className="mt-1 text-xs text-gray-500">
                    {detectResult?.radarSerial 
                      ? '已通过检测自动获取' 
                      : formData.radarSerial 
                        ? '将作为唯一设备ID存储' 
                        : '无需填写，雷达上线后系统将自动获取并填充'}
                  </p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    雷达名称 <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.radarName}
                    onChange={(e) => setFormData({ ...formData, radarName: e.target.value })}
                    placeholder="请输入雷达名称"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">所属装置（可选）</label>
                  <select
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.assemblyId || ''}
                    onChange={(e) => setFormData({ ...formData, assemblyId: e.target.value || '' })}
                  >
                    <option value="">不关联装置</option>
                    {assemblies.map((assembly) => (
                      <option key={assembly.id || assembly.assemblyId} value={assembly.assemblyId || assembly.id}>
                        {assembly.name}
                      </option>
                    ))}
                  </select>
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
          </div>,
            document.body
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
