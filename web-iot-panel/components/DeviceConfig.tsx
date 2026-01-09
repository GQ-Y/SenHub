import React, { useState, useEffect } from 'react';
import { Settings, Package, Bell, User, X, Plus } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { deviceService, assemblyService, alarmRuleService } from '../src/api/services';
import { Device, Assembly, AlarmRule, DeviceRole } from '../types';
import { Modal } from './Modal';
import { useModal } from '../hooks/useModal';
import { DeviceSelector } from './DeviceSelector';

interface DeviceConfigProps {
  deviceId: string;
}

export const DeviceConfig: React.FC<DeviceConfigProps> = ({ deviceId }) => {
  const { t } = useAppContext();
  const [device, setDevice] = useState<Device | null>(null);
  const [assemblies, setAssemblies] = useState<Assembly[]>([]);
  const [rules, setRules] = useState<AlarmRule[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [selectedAssemblyId, setSelectedAssemblyId] = useState<string>('');
  const [deviceRole, setDeviceRole] = useState<DeviceRole | ''>('');
  const [allAssemblies, setAllAssemblies] = useState<Assembly[]>([]);
  const [showAddAssemblyModal, setShowAddAssemblyModal] = useState(false);
  const modal = useModal();

  useEffect(() => {
    loadConfig();
  }, [deviceId]);

  const loadConfig = async () => {
    setIsLoading(true);
    try {
      const [deviceRes, configRes, assembliesRes] = await Promise.all([
        deviceService.getDevice(deviceId),
        deviceService.getDeviceConfig(deviceId),
        assemblyService.getAssemblies(),
      ]);
      setDevice(deviceRes.data);
      setAssemblies(configRes.data.assemblies || []);
      setRules(configRes.data.rules || []);
      setAllAssemblies(assembliesRes.data || []);
      if (configRes.data.assemblies && configRes.data.assemblies.length > 0) {
        setSelectedAssemblyId(configRes.data.assemblies[0].id);
        // 从assembly中查找该设备的角色
        const assembly = configRes.data.assemblies[0];
        const deviceInAssembly = assembly.devices?.find((d) => d.deviceId === deviceId);
        if (deviceInAssembly) {
          setDeviceRole(deviceInAssembly.role);
        }
      }
      if (configRes.data.role) {
        setDeviceRole(configRes.data.role);
      }
    } catch (err: any) {
      console.error('加载设备配置失败:', err);
      modal.showModal({
        message: err.message || '加载设备配置失败',
        type: 'error',
      });
    } finally {
      setIsLoading(false);
    }
  };

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await deviceService.updateDeviceConfig(deviceId, {
        assemblyId: selectedAssemblyId || undefined,
        role: deviceRole || undefined,
      });
      modal.showModal({
        message: '保存成功',
        type: 'success',
      });
      await loadConfig();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '保存失败',
        type: 'error',
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleRemoveAssembly = async (assemblyId: string) => {
    try {
      await assemblyService.removeDeviceFromAssembly(assemblyId, deviceId);
      modal.showModal({
        message: '移除成功',
        type: 'success',
      });
      await loadConfig();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '移除失败',
        type: 'error',
      });
    }
  };

  const handleAddToAssembly = async (assemblyId: string, role: DeviceRole) => {
    try {
      await assemblyService.addDeviceToAssembly(assemblyId, deviceId, role);
      modal.showModal({
        message: '添加成功',
        type: 'success',
      });
      setShowAddAssemblyModal(false);
      await loadConfig();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '添加失败',
        type: 'error',
      });
    }
  };

  const handleToggleRule = async (rule: AlarmRule) => {
    try {
      await alarmRuleService.toggleRule(rule.ruleId || rule.id, !rule.enabled);
      await loadConfig();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '切换规则状态失败',
        type: 'error',
      });
    }
  };

  if (isLoading) {
    return (
      <div className="p-12 text-center">
        <div className="inline-block w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
        <p className="mt-4 text-gray-500">加载中...</p>
      </div>
    );
  }

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

      <div className="space-y-6">
        {/* 装置关联 */}
        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-bold text-gray-800 flex items-center">
              <Package size={20} className="mr-2 text-blue-500" />
              {t('assemblies')}
            </h3>
            <button
              onClick={() => setShowAddAssemblyModal(true)}
              className="flex items-center space-x-1 px-3 py-1.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm"
            >
              <Plus size={16} />
              <span>{t('add_device_to_assembly')}</span>
            </button>
          </div>

          {assemblies.length === 0 ? (
            <div className="text-center py-8 text-gray-500">
              <Package className="mx-auto mb-2 text-gray-300" size={32} />
              <p>设备未加入任何装置</p>
            </div>
          ) : (
            <div className="space-y-3">
              {assemblies.map((assembly) => {
                const deviceInAssembly = assembly.devices?.find((d) => d.deviceId === deviceId);
                return (
                  <div
                    key={assembly.id}
                    className="flex items-center justify-between p-4 bg-gray-50 rounded-lg border border-gray-200"
                  >
                    <div className="flex-1">
                      <div className="font-medium text-gray-900">{assembly.name}</div>
                      {assembly.location && (
                        <div className="text-sm text-gray-500 mt-1">{assembly.location}</div>
                      )}
                      {deviceInAssembly && (
                        <div className="text-xs text-gray-400 mt-1">
                          {t('device_role')}:{' '}
                          {deviceInAssembly.role === DeviceRole.LEFT_CAMERA
                            ? t('left_camera')
                            : deviceInAssembly.role === DeviceRole.RIGHT_CAMERA
                            ? t('right_camera')
                            : deviceInAssembly.role === DeviceRole.TOP_CAMERA
                            ? t('top_camera')
                            : deviceInAssembly.role === DeviceRole.SPEAKER
                            ? t('speaker')
                            : deviceInAssembly.role === DeviceRole.RADAR
                            ? t('radar')
                            : deviceInAssembly.role}
                        </div>
                      )}
                    </div>
                    <button
                      onClick={() => handleRemoveAssembly(assembly.assemblyId || assembly.id)}
                      className="p-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                      title={t('remove_device')}
                    >
                      <X size={18} />
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* 设备角色 */}
        {assemblies.length > 0 && (
          <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
            <h3 className="font-bold text-gray-800 flex items-center mb-4">
              <User size={20} className="mr-2 text-blue-500" />
              {t('device_role')}
            </h3>
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">{t('device_role')}</label>
                <select
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                  value={deviceRole}
                  onChange={(e) => setDeviceRole(e.target.value as DeviceRole)}
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
          </div>
        )}

        {/* 报警规则 */}
        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <h3 className="font-bold text-gray-800 flex items-center mb-4">
            <Bell size={20} className="mr-2 text-blue-500" />
            {t('alarm_rules')}
          </h3>
          {rules.length === 0 ? (
            <div className="text-center py-8 text-gray-500">
              <Bell className="mx-auto mb-2 text-gray-300" size={32} />
              <p>该设备暂无报警规则</p>
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
                      {rule.scope === 'global'
                        ? t('global_rule')
                        : rule.scope === 'assembly'
                        ? `${t('assembly_rule')}: ${rule.assemblyName}`
                        : `${t('device_rule')}`}
                    </div>
                  </div>
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
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 保存按钮 */}
        <div className="flex justify-end">
          <button
            onClick={handleSave}
            disabled={isSaving}
            className="px-6 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200 flex items-center"
          >
            {isSaving && (
              <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2"></div>
            )}
            {t('save')}
          </button>
        </div>
      </div>

      {/* 添加装置弹窗 */}
      {showAddAssemblyModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div
            className="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
            onClick={() => setShowAddAssemblyModal(false)}
          ></div>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg z-10 overflow-hidden">
            <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
              <h3 className="text-lg font-bold text-gray-800">{t('add_device_to_assembly')}</h3>
              <button
                onClick={() => setShowAddAssemblyModal(false)}
                className="p-1 rounded-full hover:bg-gray-200 transition-colors text-gray-500"
              >
                <X size={20} />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">{t('assemblies')}</label>
                <select
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                  value={selectedAssemblyId}
                  onChange={(e) => setSelectedAssemblyId(e.target.value)}
                >
                  <option value="">请选择装置</option>
                  {allAssemblies
                    .filter((a) => !assemblies.find((aa) => aa.id === a.id))
                    .map((assembly) => (
                      <option key={assembly.id} value={assembly.assemblyId || assembly.id}>
                        {assembly.name}
                      </option>
                    ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">{t('device_role')}</label>
                <select
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                  value={deviceRole}
                  onChange={(e) => setDeviceRole(e.target.value as DeviceRole)}
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
                onClick={() => setShowAddAssemblyModal(false)}
                className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium"
              >
                {t('cancel')}
              </button>
              <button
                onClick={() => {
                  if (selectedAssemblyId && deviceRole) {
                    handleAddToAssembly(selectedAssemblyId, deviceRole);
                  }
                }}
                disabled={!selectedAssemblyId || !deviceRole}
                className="px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {t('save')}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
