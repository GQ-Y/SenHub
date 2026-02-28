import React, { useState, useEffect } from 'react';
import { X, Package, Bell } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { deviceService, assemblyService, alarmRuleService } from '../src/api/services';
import { Device, Assembly, AlarmRule, DeviceRole } from '../types';

interface QuickConfigModalProps {
  device: Device;
  onClose: () => void;
  onSave?: () => void;
}

export const QuickConfigModal: React.FC<QuickConfigModalProps> = ({ device, onClose, onSave }) => {
  const { t } = useAppContext();
  const [assemblies, setAssemblies] = useState<Assembly[]>([]);
  const [rules, setRules] = useState<AlarmRule[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [selectedAssemblyId, setSelectedAssemblyId] = useState<string>('');
  const [deviceRole, setDeviceRole] = useState<DeviceRole | ''>('');
  const [allAssemblies, setAllAssemblies] = useState<Assembly[]>([]);
  const [enabledRules, setEnabledRules] = useState<Set<string>>(new Set());

  useEffect(() => {
    loadConfig();
  }, [device.id]);

  const loadConfig = async () => {
    setIsLoading(true);
    try {
      const [configRes, assembliesRes, rulesRes] = await Promise.all([
        deviceService.getDeviceConfig(device.id),
        assemblyService.getAssemblies(),
        alarmRuleService.getDeviceRules(device.id),
      ]);
      setAssemblies(configRes.data.assemblies || []);
      setRules(rulesRes.data || []);
      setAllAssemblies(assembliesRes.data || []);
      if (configRes.data.assemblies && configRes.data.assemblies.length > 0) {
        setSelectedAssemblyId(configRes.data.assemblies[0].id);
        const assembly = configRes.data.assemblies[0];
        const deviceInAssembly = assembly.devices?.find((d) => d.deviceId === device.id);
        if (deviceInAssembly) {
          setDeviceRole(deviceInAssembly.role);
        }
      }
      if (configRes.data.role) {
        setDeviceRole(configRes.data.role);
      }
      // 设置已启用的规则
      const enabled = new Set<string>();
      (rulesRes.data || []).forEach((rule) => {
        if (rule.enabled) {
          enabled.add(rule.id);
        }
      });
      setEnabledRules(enabled);
    } catch (err: any) {
      console.error('加载配置失败:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSave = async () => {
    setIsSaving(true);
    try {
      // 更新设备配置
      if (selectedAssemblyId && deviceRole) {
        await deviceService.updateDeviceConfig(device.id, {
          assemblyId: selectedAssemblyId,
          role: deviceRole,
        });
      }

      // 更新规则状态
      for (const rule of rules) {
        const shouldBeEnabled = enabledRules.has(rule.id);
        if (rule.enabled !== shouldBeEnabled) {
          await alarmRuleService.toggleRule(rule.ruleId || rule.id, shouldBeEnabled);
        }
      }

      if (onSave) {
        onSave();
      }
      onClose();
    } catch (err: any) {
      console.error('保存失败:', err);
      alert(err.message || '保存失败');
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleRule = (ruleId: string) => {
    const newEnabled = new Set(enabledRules);
    if (newEnabled.has(ruleId)) {
      newEnabled.delete(ruleId);
    } else {
      newEnabled.add(ruleId);
    }
    setEnabledRules(newEnabled);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity" onClick={onClose}></div>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl z-10 overflow-hidden max-h-[90vh] flex flex-col">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
          <h3 className="text-lg font-bold text-gray-800">{t('quick_config')}</h3>
          <button
            onClick={onClose}
            className="p-1 rounded-full hover:bg-gray-200 transition-colors text-gray-500"
          >
            <X size={20} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {isLoading ? (
            <div className="text-center py-8">
              <div className="inline-block w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
              <p className="mt-4 text-gray-500">加载中...</p>
            </div>
          ) : (
            <>
              {/* 装置关联 */}
              <div>
                <div className="flex items-center mb-3">
                  <Package size={18} className="mr-2 text-blue-500" />
                  <h4 className="font-semibold text-gray-800">{t('assemblies')}</h4>
                </div>
                <select
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                  value={selectedAssemblyId}
                  onChange={(e) => setSelectedAssemblyId(e.target.value)}
                >
                  <option value="">不加入装置</option>
                  {allAssemblies.map((assembly) => (
                    <option key={assembly.id} value={assembly.assemblyId || assembly.id}>
                      {assembly.name}
                    </option>
                  ))}
                </select>
                {selectedAssemblyId && (
                  <div className="mt-3">
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
                )}
              </div>

              {/* 快速规则开关 */}
              {rules.length > 0 && (
                <div>
                  <div className="flex items-center mb-3">
                    <Bell size={18} className="mr-2 text-blue-500" />
                    <h4 className="font-semibold text-gray-800">{t('alarm_rules')}</h4>
                  </div>
                  <div className="space-y-2">
                    {rules.map((rule) => (
                      <label
                        key={rule.id}
                        className="flex items-center justify-between p-3 bg-gray-50 rounded-lg border border-gray-200 cursor-pointer hover:bg-gray-100 transition-colors"
                      >
                        <div className="flex-1">
                          <div className="font-medium text-gray-900">{rule.name}</div>
                          <div className="text-xs text-gray-500 mt-1">
                            {rule.scope === 'global'
                              ? t('global_rule')
                              : rule.scope === 'assembly'
                              ? t('assembly_rule')
                              : t('device_rule')}
                          </div>
                        </div>
                        <input
                          type="checkbox"
                          className="w-5 h-5 text-blue-600 rounded focus:ring-blue-500"
                          checked={enabledRules.has(rule.id)}
                          onChange={() => handleToggleRule(rule.id)}
                        />
                      </label>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>
        <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end space-x-3">
          <button
            onClick={onClose}
            className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium"
          >
            {t('cancel')}
          </button>
          <button
            onClick={handleSave}
            disabled={isSaving || isLoading}
            className="px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200 flex items-center disabled:opacity-50"
          >
            {isSaving && (
              <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2"></div>
            )}
            {t('save')}
          </button>
        </div>
      </div>
    </div>
  );
};
