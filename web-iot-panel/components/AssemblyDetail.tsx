import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Package, Bell, History, Plus, X, Trash2, Edit2 } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { assemblyService, alarmRuleService, deviceService } from '../src/api/services';
import { Assembly, AssemblyDevice, AlarmRule, DeviceRole, Device } from '../types';
import { Modal, ConfirmModal } from './Modal';
import { useModal } from '../hooks/useModal';
import { DeviceSelector } from './DeviceSelector';
import { RuleForm } from './RuleForm';
import { AlarmHistory } from './AlarmHistory';

export const AssemblyDetail: React.FC = () => {
  const { assemblyId } = useParams<{ assemblyId: string }>();
  const navigate = useNavigate();
  const { t } = useAppContext();
  const [assembly, setAssembly] = useState<Assembly | null>(null);
  const [devices, setDevices] = useState<AssemblyDevice[]>([]);
  const [rules, setRules] = useState<AlarmRule[]>([]);
  const [allDevices, setAllDevices] = useState<Device[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'devices' | 'rules' | 'history'>('devices');
  const [showAddDeviceModal, setShowAddDeviceModal] = useState(false);
  const [showCreateRuleModal, setShowCreateRuleModal] = useState(false);
  const [selectedDeviceIds, setSelectedDeviceIds] = useState<string[]>([]);
  const [selectedRole, setSelectedRole] = useState<DeviceRole | ''>('');
  const [selectedRule, setSelectedRule] = useState<AlarmRule | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const modal = useModal();

  useEffect(() => {
    if (assemblyId) {
      loadAssembly();
    }
  }, [assemblyId]);

  const loadAssembly = async () => {
    if (!assemblyId) return;
    setIsLoading(true);
    try {
      const [assemblyRes, devicesRes, rulesRes, allDevicesRes] = await Promise.all([
        assemblyService.getAssembly(assemblyId),
        assemblyService.getAssemblyDevices(assemblyId),
        alarmRuleService.getAssemblyRules(assemblyId),
        deviceService.getDevices(),
      ]);
      setAssembly(assemblyRes.data);
      setDevices(devicesRes.data || []);
      setRules(rulesRes.data || []);
      setAllDevices(allDevicesRes.data || []);
    } catch (err: any) {
      console.error('加载装置详情失败:', err);
      modal.showModal({
        message: err.message || '加载装置详情失败',
        type: 'error',
      });
    } finally {
      setIsLoading(false);
    }
  };

  const handleAddDevice = async () => {
    if (selectedDeviceIds.length === 0 || !selectedRole || !assemblyId) {
      modal.showModal({
        message: '请选择设备和角色',
        type: 'warning',
      });
      return;
    }

    setIsSaving(true);
    try {
      for (const deviceId of selectedDeviceIds) {
        await assemblyService.addDeviceToAssembly(assemblyId, deviceId, selectedRole);
      }
      modal.showModal({
        message: '添加成功',
        type: 'success',
      });
      setShowAddDeviceModal(false);
      setSelectedDeviceIds([]);
      setSelectedRole('');
      await loadAssembly();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '添加失败',
        type: 'error',
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleRemoveDevice = async (deviceId: string) => {
    if (!assemblyId) return;
    try {
      await assemblyService.removeDeviceFromAssembly(assemblyId, deviceId);
      modal.showModal({
        message: '移除成功',
        type: 'success',
      });
      await loadAssembly();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '移除失败',
        type: 'error',
      });
    }
  };

  const handleSaveRule = async (ruleData: Partial<AlarmRule>) => {
    if (!assemblyId) return;
    setIsSaving(true);
    try {
      const ruleToSave = {
        ...ruleData,
        scope: 'assembly' as const,
        assemblyId,
      };
      if (selectedRule) {
        await alarmRuleService.updateAlarmRule(selectedRule.ruleId || selectedRule.id, ruleToSave);
        modal.showModal({
          message: '更新成功',
          type: 'success',
        });
      } else {
        await alarmRuleService.createAlarmRule(ruleToSave);
        modal.showModal({
          message: '创建成功',
          type: 'success',
        });
      }
      setShowCreateRuleModal(false);
      setSelectedRule(null);
      await loadAssembly();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '保存失败',
        type: 'error',
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteRule = async (rule: AlarmRule) => {
    try {
      await alarmRuleService.deleteAlarmRule(rule.ruleId || rule.id);
      modal.showModal({
        message: '删除成功',
        type: 'success',
      });
      await loadAssembly();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '删除失败',
        type: 'error',
      });
    }
  };

  const handleToggleRule = async (rule: AlarmRule) => {
    try {
      await alarmRuleService.toggleRule(rule.ruleId || rule.id, !rule.enabled);
      await loadAssembly();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '切换状态失败',
        type: 'error',
      });
    }
  };

  const getRoleLabel = (role: DeviceRole) => {
    switch (role) {
      case DeviceRole.LEFT_CAMERA:
        return t('left_camera');
      case DeviceRole.RIGHT_CAMERA:
        return t('right_camera');
      case DeviceRole.TOP_CAMERA:
        return t('top_camera');
      case DeviceRole.SPEAKER:
        return t('speaker');
      case DeviceRole.RADAR:
        return t('radar');
      default:
        return role;
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  if (!assembly) {
    return (
      <div className="flex flex-col items-center justify-center h-64">
        <p className="text-gray-500 mb-4">装置不存在</p>
        <button
          onClick={() => navigate('/assemblies')}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          返回装置列表
        </button>
      </div>
    );
  }

  const excludeDeviceIds = devices.map((d) => d.deviceId);

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
        {/* 头部 */}
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-4">
            <button
              onClick={() => navigate('/assemblies')}
              className="p-2 bg-white border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors"
            >
              <ArrowLeft size={20} className="text-gray-600" />
            </button>
            <div>
              <h2 className="text-2xl font-bold text-gray-800">{assembly.name}</h2>
              {assembly.location && (
                <div className="text-sm text-gray-500 mt-1">{assembly.location}</div>
              )}
            </div>
          </div>
          <span
            className={`px-3 py-1 rounded-full text-sm font-medium ${
              assembly.status === 'active'
                ? 'bg-green-100 text-green-700'
                : 'bg-gray-100 text-gray-700'
            }`}
          >
            {assembly.status === 'active' ? t('active') : t('inactive')}
          </span>
        </div>

        {/* 标签页 */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <div className="border-b border-gray-100">
            <div className="flex space-x-1 px-4">
              <button
                onClick={() => setActiveTab('devices')}
                className={`px-4 py-3 font-medium text-sm transition-colors border-b-2 ${
                  activeTab === 'devices'
                    ? 'border-blue-600 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700'
                }`}
              >
                <Package size={18} className="inline mr-2" />
                {t('assembly_devices')}
              </button>
              <button
                onClick={() => setActiveTab('rules')}
                className={`px-4 py-3 font-medium text-sm transition-colors border-b-2 ${
                  activeTab === 'rules'
                    ? 'border-blue-600 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700'
                }`}
              >
                <Bell size={18} className="inline mr-2" />
                {t('alarm_rules')}
              </button>
              <button
                onClick={() => setActiveTab('history')}
                className={`px-4 py-3 font-medium text-sm transition-colors border-b-2 ${
                  activeTab === 'history'
                    ? 'border-blue-600 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700'
                }`}
              >
                <History size={18} className="inline mr-2" />
                {t('alarm_history')}
              </button>
            </div>
          </div>

          <div className="p-6">
            {/* 设备列表标签页 */}
            {activeTab === 'devices' && (
              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <h3 className="font-bold text-gray-800">{t('assembly_devices')}</h3>
                  <button
                    onClick={() => setShowAddDeviceModal(true)}
                    className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors text-sm"
                  >
                    <Plus size={16} />
                    <span>{t('add_device_to_assembly')}</span>
                  </button>
                </div>
                {devices.length === 0 ? (
                  <div className="text-center py-8 text-gray-500">
                    <Package className="mx-auto mb-2 text-gray-300" size={32} />
                    <p>暂无设备</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {devices.map((device) => {
                      const deviceInfo = allDevices.find((d) => d.id === device.deviceId);
                      return (
                        <div
                          key={device.deviceId}
                          className="flex items-center justify-between p-4 bg-gray-50 rounded-lg border border-gray-200"
                        >
                          <div className="flex-1">
                            <div className="font-medium text-gray-900">
                              {device.deviceName || deviceInfo?.name || device.deviceId}
                            </div>
                            <div className="text-sm text-gray-500 mt-1">
                              {deviceInfo && `${deviceInfo.ip}:${deviceInfo.port}`}
                            </div>
                            <div className="text-xs text-gray-400 mt-1">
                              {t('device_role')}: {getRoleLabel(device.role)}
                            </div>
                          </div>
                          <button
                            onClick={() => handleRemoveDevice(device.deviceId)}
                            className="p-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                            title={t('remove_device')}
                          >
                            <Trash2 size={18} />
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            )}

            {/* 规则标签页 */}
            {activeTab === 'rules' && (
              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <h3 className="font-bold text-gray-800">{t('alarm_rules')}</h3>
                  <button
                    onClick={() => {
                      setSelectedRule(null);
                      setShowCreateRuleModal(true);
                    }}
                    className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors text-sm"
                  >
                    <Plus size={16} />
                    <span>{t('create_rule')}</span>
                  </button>
                </div>
                {rules.length === 0 ? (
                  <div className="text-center py-8 text-gray-500">
                    <Bell className="mx-auto mb-2 text-gray-300" size={32} />
                    <p>暂无规则</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {rules.map((rule) => (
                      <div
                        key={rule.id}
                        className="flex items-center justify-between p-4 bg-gray-50 rounded-lg border border-gray-200"
                      >
                        <div className="flex-1">
                          <div className="font-medium text-gray-900">{rule.name}</div>
                          <div className="text-sm text-gray-500 mt-1">
                            {rule.scope === 'assembly' ? t('assembly_rule') : rule.scope}
                          </div>
                        </div>
                        <div className="flex items-center space-x-2">
                          <label className="flex items-center space-x-2 cursor-pointer">
                            <input
                              type="checkbox"
                              className="w-5 h-5 text-blue-600 rounded focus:ring-blue-500"
                              checked={rule.enabled}
                              onChange={() => handleToggleRule(rule)}
                            />
                            <span className="text-sm font-medium text-gray-700">
                              {rule.enabled ? t('enabled') : t('disabled')}
                            </span>
                          </label>
                          <button
                            onClick={() => {
                              setSelectedRule(rule);
                              setShowCreateRuleModal(true);
                            }}
                            className="p-2 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                            title={t('edit_device')}
                          >
                            <Edit2 size={18} />
                          </button>
                          <button
                            onClick={() => handleDeleteRule(rule)}
                            className="p-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                            title={t('delete')}
                          >
                            <Trash2 size={18} />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 报警历史标签页 */}
            {activeTab === 'history' && <AlarmHistory assemblyId={assemblyId} />}
          </div>
        </div>
      </div>

      {/* 添加设备弹窗 */}
      {showAddDeviceModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div
            className="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
            onClick={() => setShowAddDeviceModal(false)}
          ></div>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl z-10 overflow-hidden max-h-[90vh] flex flex-col">
            <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
              <h3 className="text-lg font-bold text-gray-800">{t('add_device_to_assembly')}</h3>
              <button
                onClick={() => setShowAddDeviceModal(false)}
                className="p-1 rounded-full hover:bg-gray-200 transition-colors text-gray-500"
              >
                <X size={20} />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-6 space-y-4">
              <DeviceSelector
                devices={allDevices}
                selected={selectedDeviceIds}
                onChange={setSelectedDeviceIds}
                multiSelect={true}
                excludeDeviceIds={excludeDeviceIds}
              />
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">{t('device_role')}</label>
                <select
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                  value={selectedRole}
                  onChange={(e) => setSelectedRole(e.target.value as DeviceRole)}
                >
                  <option value="">请选择角色</option>
                  <option value={DeviceRole.LEFT_CAMERA}>{t('left_camera')}</option>
                  <option value={DeviceRole.RIGHT_CAMERA}>{t('right_camera')}</option>
                  <option value={DeviceRole.TOP_CAMERA}>{t('top_camera')}</option>
                  <option value={DeviceRole.SPEAKER}>{t('speaker')}</option>
                  <option value={DeviceRole.RADAR}>{t('radar')}</option>
                </select>
              </div>
            </div>
            <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end space-x-3">
              <button
                onClick={() => setShowAddDeviceModal(false)}
                className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium"
              >
                {t('cancel')}
              </button>
              <button
                onClick={handleAddDevice}
                disabled={isSaving || selectedDeviceIds.length === 0 || !selectedRole}
                className="px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200 flex items-center disabled:opacity-50 disabled:cursor-not-allowed"
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

      {/* 创建/编辑规则弹窗 */}
      {showCreateRuleModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div
            className="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
            onClick={() => {
              setShowCreateRuleModal(false);
              setSelectedRule(null);
            }}
          ></div>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl z-10 overflow-hidden max-h-[90vh] flex flex-col">
            <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
              <h3 className="text-lg font-bold text-gray-800">
                {selectedRule ? t('edit_device') : t('create_rule')}
              </h3>
              <button
                onClick={() => {
                  setShowCreateRuleModal(false);
                  setSelectedRule(null);
                }}
                className="p-1 rounded-full hover:bg-gray-200 transition-colors text-gray-500"
              >
                <X size={20} />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-6">
              <RuleForm
                rule={selectedRule || undefined}
                onSave={handleSaveRule}
                onCancel={() => {
                  setShowCreateRuleModal(false);
                  setSelectedRule(null);
                }}
              />
            </div>
          </div>
        </div>
      )}
    </>
  );
};
