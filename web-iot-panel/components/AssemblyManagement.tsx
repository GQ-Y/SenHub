import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Search, Edit2, Trash2, X, Package, MapPin, Camera } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { assemblyService } from '../src/api/services';
import { Assembly } from '../types';
import { Modal, ConfirmModal } from './Modal';
import { useModal } from '../hooks/useModal';

export const AssemblyManagement: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useAppContext();
  const [assemblies, setAssemblies] = useState<Assembly[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeModal, setActiveModal] = useState<'NONE' | 'CREATE' | 'EDIT' | 'DELETE'>('NONE');
  const [selectedAssembly, setSelectedAssembly] = useState<Assembly | null>(null);
  const [formData, setFormData] = useState<Partial<Assembly>>({
    name: '',
    location: '',
    description: '',
    status: 'active',
  });
  const [isSaving, setIsSaving] = useState(false);
  const modal = useModal();

  // 加载装置列表
  const loadAssemblies = async () => {
    setIsLoading(true);
    try {
      const params: any = {};
      if (searchTerm) params.search = searchTerm;
      const response = await assemblyService.getAssemblies(params);
      setAssemblies(response.data || []);
    } catch (err: any) {
      console.error('加载装置列表失败:', err);
      modal.showModal({
        message: err.message || '加载装置列表失败',
        type: 'error',
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadAssemblies();
  }, [searchTerm]);

  const openCreateModal = () => {
    setFormData({ name: '', location: '', description: '', status: 'active' });
    setActiveModal('CREATE');
  };

  const openEditModal = (assembly: Assembly) => {
    setSelectedAssembly(assembly);
    setFormData({
      name: assembly.name,
      location: assembly.location,
      description: assembly.description,
      status: assembly.status,
    });
    setActiveModal('EDIT');
  };

  const openDeleteModal = (assembly: Assembly) => {
    setSelectedAssembly(assembly);
    setActiveModal('DELETE');
  };

  const handleCloseModal = () => {
    setActiveModal('NONE');
    setSelectedAssembly(null);
    setIsSaving(false);
  };

  const handleSave = async () => {
    if (!formData.name?.trim()) {
      modal.showModal({
        message: '请输入装置名称',
        type: 'warning',
      });
      return;
    }

    setIsSaving(true);
    try {
      if (activeModal === 'CREATE') {
        await assemblyService.createAssembly(formData);
        modal.showModal({
          message: '创建成功',
          type: 'success',
        });
      } else if (activeModal === 'EDIT' && selectedAssembly) {
        await assemblyService.updateAssembly(selectedAssembly.assemblyId || selectedAssembly.id, formData);
        modal.showModal({
          message: '更新成功',
          type: 'success',
        });
      }
      handleCloseModal();
      await loadAssemblies();
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
    if (!selectedAssembly) return;
    setIsSaving(true);
    try {
      await assemblyService.deleteAssembly(selectedAssembly.assemblyId || selectedAssembly.id);
      handleCloseModal();
      await loadAssemblies();
      modal.showModal({
        message: '删除成功',
        type: 'success',
      });
    } catch (err: any) {
      modal.showModal({
        message: err.message || '删除失败',
        type: 'error',
      });
      setIsSaving(false);
    }
  };

  const filteredAssemblies = assemblies.filter(assembly =>
    !searchTerm || 
    assembly.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    assembly.location?.toLowerCase().includes(searchTerm.toLowerCase())
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
                placeholder="搜索装置名称或位置..."
                className="w-full pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
          </div>

          <div className="flex items-center space-x-3 w-full md:w-auto">
            <button
              onClick={loadAssemblies}
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
              <span>{t('create_assembly')}</span>
            </button>
          </div>
        </div>

        {/* 装置列表 */}
        {isLoading ? (
          <div className="p-12 text-center">
            <div className="inline-block w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
            <p className="mt-4 text-gray-500">加载中...</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredAssemblies.map((assembly) => (
              <div
                key={assembly.id}
                className="bg-white rounded-xl p-6 shadow-sm border border-gray-100 hover:shadow-md transition-shadow"
              >
                <div className="flex justify-between items-start mb-4">
                  <div className="flex-1">
                    <h3 className="font-bold text-lg text-gray-800 mb-1">{assembly.name}</h3>
                    {assembly.location && (
                      <div className="flex items-center text-sm text-gray-500">
                        <MapPin size={14} className="mr-1" />
                        <span>{assembly.location}</span>
                      </div>
                    )}
                  </div>
                  <span
                    className={`px-2 py-1 rounded-full text-xs font-medium ${
                      assembly.status === 'active'
                        ? 'bg-green-100 text-green-700'
                        : 'bg-gray-100 text-gray-700'
                    }`}
                  >
                    {assembly.status === 'active' ? t('active') : t('inactive')}
                  </span>
                </div>

                {assembly.description && (
                  <p className="text-sm text-gray-600 mb-4 line-clamp-2">{assembly.description}</p>
                )}

                {/* 设备信息预览 */}
                <div className="space-y-2 mb-4">
                  <div className="text-sm text-gray-600">
                    <span className="font-medium">{t('assembly_devices')}：</span>
                    <span>{assembly.deviceCount || assembly.devices?.length || 0}</span>
                  </div>
                  {assembly.devices && assembly.devices.length > 0 && (
                    <div className="flex flex-wrap gap-2">
                      {assembly.devices.slice(0, 3).map((device, idx) => (
                        <span
                          key={idx}
                          className="px-2 py-1 bg-blue-50 text-blue-700 rounded-lg text-xs font-medium"
                        >
                          {device.role === 'left_camera' ? t('left_camera') :
                           device.role === 'right_camera' ? t('right_camera') :
                           device.role === 'top_camera' ? t('top_camera') :
                           device.role === 'speaker' ? t('speaker') :
                           device.role === 'radar' ? t('radar') : device.role}
                        </span>
                      ))}
                      {assembly.devices.length > 3 && (
                        <span className="px-2 py-1 bg-gray-50 text-gray-600 rounded-lg text-xs">
                          +{assembly.devices.length - 3}
                        </span>
                      )}
                    </div>
                  )}
                </div>

                {/* 操作按钮 */}
                <div className="flex space-x-2 pt-4 border-t border-gray-100">
                  <button
                    onClick={() => navigate(`/assemblies/${assembly.assemblyId || assembly.id}`)}
                    className="flex-1 px-3 py-2 bg-blue-50 text-blue-600 rounded-lg hover:bg-blue-100 transition-colors text-sm font-medium"
                  >
                    {t('view_details')}
                  </button>
                  <button
                    onClick={() => openEditModal(assembly)}
                    className="px-3 py-2 text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                    title={t('edit_assembly')}
                  >
                    <Edit2 size={16} />
                  </button>
                  <button
                    onClick={() => openDeleteModal(assembly)}
                    className="px-3 py-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                    title={t('delete_assembly')}
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {!isLoading && filteredAssemblies.length === 0 && (
          <div className="p-12 text-center text-gray-500 bg-white rounded-xl">
            <Package className="mx-auto mb-4 text-gray-300" size={48} />
            <p>暂无装置</p>
            <button
              onClick={openCreateModal}
              className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors"
            >
              {t('create_assembly')}
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
                  {activeModal === 'CREATE' ? t('create_assembly') : t('edit_assembly')}
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
                    {t('assembly_name')} <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.name || ''}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    placeholder="请输入装置名称"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('assembly_location')}</label>
                  <input
                    type="text"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.location || ''}
                    onChange={(e) => setFormData({ ...formData, location: e.target.value })}
                    placeholder="请输入位置信息"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('assembly_description')}</label>
                  <textarea
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    rows={3}
                    value={formData.description || ''}
                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                    placeholder="请输入描述信息（可选）"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('status')}</label>
                  <select
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.status || 'active'}
                    onChange={(e) => setFormData({ ...formData, status: e.target.value as 'active' | 'inactive' })}
                  >
                    <option value="active">{t('active')}</option>
                    <option value="inactive">{t('inactive')}</option>
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
          </div>
        )}

        {/* 删除确认弹窗 */}
        {activeModal === 'DELETE' && selectedAssembly && (
          <ConfirmModal
            isOpen={true}
            onClose={handleCloseModal}
            title={t('delete_assembly')}
            message={`确定要删除装置 "${selectedAssembly.name}" 吗？此操作无法撤销。`}
            onConfirm={handleDelete}
            onCancel={handleCloseModal}
            confirmText={t('delete')}
            cancelText={t('cancel')}
          />
        )}
      </div>
    </>
  );
};
