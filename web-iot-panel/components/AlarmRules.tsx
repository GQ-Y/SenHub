import React, { useState, useEffect } from 'react';
import { Plus, Search, Edit2, Trash2, X, ToggleLeft, ToggleRight, Workflow } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { alarmRuleService, flowService, eventTypeService } from '../src/api/services';
import { AlarmRule, AlarmType, AlarmFlow, CameraEventType } from '../types';
import { Modal, ConfirmModal } from './Modal';
import { useModal } from '../hooks/useModal';
import { RuleForm } from './RuleForm';

// 右侧抽屉组件
interface DrawerProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}

const Drawer: React.FC<DrawerProps> = ({ isOpen, onClose, title, children }) => {
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      document.body.style.overflow = 'hidden';
    }
    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.body.style.overflow = '';
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex">
      {/* 背景遮罩 */}
      <div
        className="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />
      {/* 抽屉面板 */}
      <div
        className="absolute right-0 top-0 h-full w-full max-w-lg bg-white shadow-2xl flex flex-col transform transition-transform duration-300 ease-out"
        style={{ transform: isOpen ? 'translateX(0)' : 'translateX(100%)' }}
      >
        {/* 抽屉头部 */}
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gradient-to-r from-blue-50 to-white">
          <h3 className="text-lg font-bold text-gray-800">{title}</h3>
          <button
            onClick={onClose}
            className="p-2 rounded-full hover:bg-gray-100 transition-colors text-gray-500"
          >
            <X size={20} />
          </button>
        </div>
        {/* 抽屉内容 */}
        <div className="flex-1 overflow-y-auto p-6">
          {children}
        </div>
      </div>
    </div>
  );
};

export const AlarmRules: React.FC = () => {
  const { t } = useAppContext();
  const [rules, setRules] = useState<AlarmRule[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeModal, setActiveModal] = useState<'NONE' | 'CREATE' | 'EDIT' | 'DELETE'>('NONE');
  const [selectedRule, setSelectedRule] = useState<AlarmRule | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [flows, setFlows] = useState<AlarmFlow[]>([]);
  const [eventTypes, setEventTypes] = useState<Record<string, CameraEventType[]>>({});
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

  // 加载流程列表和事件类型
  const loadFlowsAndEventTypes = async () => {
    try {
      const [flowsRes, eventTypesRes] = await Promise.all([
        flowService.listFlows(),
        eventTypeService.getEventTypes(),
      ]);
      setFlows(flowsRes.data || []);
      setEventTypes(eventTypesRes.data || {});
    } catch (err: any) {
      console.error('加载流程或事件类型失败:', err);
    }
  };

  useEffect(() => {
    loadRules();
    loadFlowsAndEventTypes();
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
      (rule.alarmType && getAlarmTypeLabel(rule.alarmType).toLowerCase().includes(searchTerm.toLowerCase()))
  );

  // 获取流程名称
  const getFlowName = (flowId?: string) => {
    if (!flowId) return t('no_workflow_bound');
    const flow = flows.find(f => f.flowId === flowId);
    return flow ? flow.name : flowId;
  };

  // 解析事件类型ID列表
  const parseEventTypeIds = (rule: AlarmRule): number[] => {
    if (rule.eventTypeIds) {
      // 如果是数组，直接返回
      if (Array.isArray(rule.eventTypeIds)) {
        return rule.eventTypeIds;
      }
      // 如果是字符串，尝试解析JSON
      if (typeof rule.eventTypeIds === 'string') {
        try {
          const parsed = JSON.parse(rule.eventTypeIds);
          if (Array.isArray(parsed)) {
            return parsed;
          }
        } catch (e) {
          console.warn('解析eventTypeIds失败:', e);
        }
      }
    }
    return [];
  };

  // 获取已选事件类型数量
  const getEventTypeCount = (rule: AlarmRule) => {
    const ids = parseEventTypeIds(rule);
    if (ids.length > 0) {
      return ids.length;
    }
    // 兼容旧规则
    if (rule.alarmType) return 1;
    return 0;
  };

  // 获取事件类型名称列表（最多显示3个，超过显示"等N个"）
  const getEventTypeNames = (rule: AlarmRule): string => {
    const ids = parseEventTypeIds(rule);
    if (ids.length === 0) {
      // 兼容旧规则
      if (rule.alarmType) {
        return getAlarmTypeLabel(rule.alarmType);
      }
      return t('no_event_selected');
    }

    // 从eventTypes中查找对应的名称
    const names: string[] = [];
    for (const [brand, events] of Object.entries(eventTypes)) {
      for (const event of events) {
        if (ids.includes(event.eventTypeId)) {
          names.push(event.name || `事件${event.eventTypeId}`);
          if (names.length >= 3) break;
        }
      }
      if (names.length >= 3) break;
    }

    if (names.length === 0) {
      return `${ids.length} ${t('alarm_type')}`;
    }

    if (ids.length <= 3) {
      return names.join('、');
    } else {
      return `${names.join('、')} 等${ids.length}个`;
    }
  };

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
                      {t('event_types_selected')}
                    </th>
                    <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      {t('rule_scope')}
                    </th>
                    <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      {t('bind_workflow')}
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
                      <td className="px-6 py-4">
                        <div className="flex flex-wrap gap-1">
                          {getEventTypeCount(rule) > 0 ? (
                            <span className="px-2 py-1 bg-blue-50 text-blue-700 rounded-lg text-xs font-medium" title={getEventTypeNames(rule)}>
                              {getEventTypeNames(rule)}
                            </span>
                          ) : (
                            <span className="px-2 py-1 bg-gray-50 text-gray-500 rounded-lg text-xs font-medium">
                              {rule.alarmType ? getAlarmTypeLabel(rule.alarmType) : t('no_event_selected')}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                        {getScopeLabel(rule)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center space-x-2">
                          <Workflow size={16} className="text-purple-500" />
                          <span className="text-sm text-gray-700">
                            {getFlowName(rule.flowId)}
                          </span>
                        </div>
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

        {/* 创建/编辑抽屉 */}
        <Drawer
          isOpen={activeModal === 'CREATE' || activeModal === 'EDIT'}
          onClose={handleCloseModal}
          title={activeModal === 'CREATE' ? t('create_rule') : t('edit_device')}
        >
          <RuleForm
            rule={selectedRule || undefined}
            onSave={handleSave}
            onCancel={handleCloseModal}
            flows={flows}
            eventTypes={eventTypes}
          />
        </Drawer>

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
