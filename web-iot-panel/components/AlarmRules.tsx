import React, { useState, useEffect } from 'react';
import { Plus, Search, Edit2, Trash2, X, ToggleLeft, ToggleRight } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { alarmRuleService } from '../src/api/services';
import { AlarmRule, AlarmType } from '../types';
import { Modal, ConfirmModal } from './Modal';
import { useModal } from '../hooks/useModal';
import { RuleForm } from './RuleForm';

export const AlarmRules: React.FC = () => {
  const { t } = useAppContext();
  const [rules, setRules] = useState<AlarmRule[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeModal, setActiveModal] = useState<'NONE' | 'CREATE' | 'EDIT' | 'DELETE'>('NONE');
  const [selectedRule, setSelectedRule] = useState<AlarmRule | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const modal = useModal();

  // 加载规则列表
  const loadRules = async () => {
    setIsLoading(true);
    try {
      const response = await alarmRuleService.getAlarmRules();
      setRules(response.data || []);
    } catch (err: any) {
      console.error('加载规则列表失败:', err);
      modal.showModal({
        message: err.message || '加载规则列表失败',
        type: 'error',
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadRules();
  }, []);

  const openCreateModal = () => {
    setSelectedRule(null);
    setActiveModal('CREATE');
  };

  const openEditModal = (rule: AlarmRule) => {
    setSelectedRule(rule);
    setActiveModal('EDIT');
  };

  const openDeleteModal = (rule: AlarmRule) => {
    setSelectedRule(rule);
    setActiveModal('DELETE');
  };

  const handleCloseModal = () => {
    setActiveModal('NONE');
    setSelectedRule(null);
    setIsSaving(false);
  };

  const handleSave = async (ruleData: Partial<AlarmRule>) => {
    setIsSaving(true);
    try {
      if (activeModal === 'CREATE') {
        await alarmRuleService.createAlarmRule(ruleData);
        modal.showModal({
          message: '创建成功',
          type: 'success',
        });
      } else if (activeModal === 'EDIT' && selectedRule) {
        await alarmRuleService.updateAlarmRule(selectedRule.ruleId || selectedRule.id, ruleData);
        modal.showModal({
          message: '更新成功',
          type: 'success',
        });
      }
      handleCloseModal();
      await loadRules();
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
    if (!selectedRule) return;
    setIsSaving(true);
    try {
      await alarmRuleService.deleteAlarmRule(selectedRule.ruleId || selectedRule.id);
      handleCloseModal();
      await loadRules();
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

  const handleToggle = async (rule: AlarmRule) => {
    try {
      await alarmRuleService.toggleRule(rule.ruleId || rule.id, !rule.enabled);
      await loadRules();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '切换状态失败',
        type: 'error',
      });
    }
  };

  const getAlarmTypeLabel = (type: AlarmType) => {
    switch (type) {
      case AlarmType.HELMET_DETECTION:
        return t('helmet_detection');
      case AlarmType.VEST_DETECTION:
        return t('vest_detection');
      case AlarmType.VEHICLE_ALARM:
        return t('vehicle_alarm');
      case AlarmType.INPUT_PORT:
        return t('input_port');
      case AlarmType.RADAR_POINTCLOUD:
        return t('radar_pointcloud');
      default:
        return type;
    }
  };

  const getScopeLabel = (rule: AlarmRule) => {
    if (rule.scope === 'global') return t('global_rule');
    if (rule.scope === 'assembly') return `${t('assembly_rule')}: ${rule.assemblyName || rule.assemblyId}`;
    if (rule.scope === 'device') return `${t('device_rule')}: ${rule.deviceName || rule.deviceId}`;
    return rule.scope;
  };

  const filteredRules = rules.filter(
    (rule) =>
      !searchTerm ||
      rule.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      getAlarmTypeLabel(rule.alarmType).toLowerCase().includes(searchTerm.toLowerCase())
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
                placeholder="搜索规则名称或报警类型..."
                className="w-full pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
          </div>

          <div className="flex items-center space-x-3 w-full md:w-auto">
            <button
              onClick={loadRules}
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
              <span>{t('create_rule')}</span>
            </button>
          </div>
        </div>

        {/* 规则列表表格 */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
          {isLoading ? (
            <div className="p-12 text-center">
              <div className="inline-block w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
              <p className="mt-4 text-gray-500">加载中...</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr className="bg-gray-50/50 border-b border-gray-100">
                    <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      {t('rule_name')}
                    </th>
                    <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      {t('alarm_type')}
                    </th>
                    <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      {t('rule_scope')}
                    </th>
                    <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      {t('status')}
                    </th>
                    <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider text-right">
                      {t('actions')}
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {filteredRules.map((rule) => (
                    <tr key={rule.id} className="hover:bg-blue-50/30 transition-colors">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="font-medium text-gray-900">{rule.name}</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className="px-2 py-1 bg-blue-50 text-blue-700 rounded-lg text-xs font-medium">
                          {getAlarmTypeLabel(rule.alarmType)}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                        {getScopeLabel(rule)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <button
                          onClick={() => handleToggle(rule)}
                          className="flex items-center space-x-2"
                        >
                          {rule.enabled ? (
                            <ToggleRight className="text-green-600" size={24} />
                          ) : (
                            <ToggleLeft className="text-gray-400" size={24} />
                          )}
                          <span className={`text-xs font-medium ${rule.enabled ? 'text-green-700' : 'text-gray-500'}`}>
                            {rule.enabled ? t('enabled') : t('disabled')}
                          </span>
                        </button>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                        <div className="flex items-center justify-end space-x-2">
                          <button
                            onClick={() => openEditModal(rule)}
                            className="p-2 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                            title={t('edit_device')}
                          >
                            <Edit2 size={18} />
                          </button>
                          <button
                            onClick={() => openDeleteModal(rule)}
                            className="p-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                            title={t('delete')}
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
          {!isLoading && filteredRules.length === 0 && (
            <div className="p-12 text-center text-gray-500">
              <p>暂无规则</p>
              <button
                onClick={openCreateModal}
                className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors"
              >
                {t('create_rule')}
              </button>
            </div>
          )}
        </div>

        {/* 创建/编辑弹窗 */}
        {(activeModal === 'CREATE' || activeModal === 'EDIT') && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div
              className="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
              onClick={handleCloseModal}
            ></div>
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl z-10 overflow-hidden max-h-[90vh] flex flex-col">
              <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
                <h3 className="text-lg font-bold text-gray-800">
                  {activeModal === 'CREATE' ? t('create_rule') : t('edit_device')}
                </h3>
                <button
                  onClick={handleCloseModal}
                  className="p-1 rounded-full hover:bg-gray-200 transition-colors text-gray-500"
                >
                  <X size={20} />
                </button>
              </div>
              <div className="flex-1 overflow-y-auto p-6">
                <RuleForm rule={selectedRule || undefined} onSave={handleSave} onCancel={handleCloseModal} />
              </div>
            </div>
          </div>
        )}

        {/* 删除确认弹窗 */}
        {activeModal === 'DELETE' && selectedRule && (
          <ConfirmModal
            isOpen={true}
            onClose={handleCloseModal}
            title={t('delete')}
            message={`确定要删除规则 "${selectedRule.name}" 吗？此操作无法撤销。`}
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
