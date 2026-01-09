import React, { useState, useEffect } from 'react';
import { AlarmRule, AlarmType, RuleScope, DeviceRole, AlarmRuleActions } from '../types';
import { useAppContext } from '../contexts/AppContext';
import { deviceService, assemblyService } from '../src/api/services';
import { Device, Assembly } from '../types';

interface RuleFormProps {
  rule?: AlarmRule;
  onSave: (rule: Partial<AlarmRule>) => void;
  onCancel: () => void;
}

export const RuleForm: React.FC<RuleFormProps> = ({ rule, onSave, onCancel }) => {
  const { t } = useAppContext();
  // 根据报警类型获取默认动作配置
  const getDefaultActions = (alarmType: AlarmType): AlarmRuleActions => {
    // 算法类报警（安全帽、反光衣、车辆）：自动抓拍、MQTT上报，录像可选
    if ([AlarmType.HELMET_DETECTION, AlarmType.VEST_DETECTION, AlarmType.VEHICLE_ALARM].includes(alarmType)) {
      return {
        capture: true,
        record: false, // 录像可选
        recordDuration: 60,
        upload: false, // 算法类报警录像可选，不上传OSS
        speaker: false,
        mqtt: true, // MQTT上报开启
      };
    }
    // 输入端口报警（雷达触发）：自动抓拍、录像上传OSS、MQTT上报
    if (alarmType === AlarmType.INPUT_PORT) {
      return {
        capture: true,
        record: true, // 输入端口报警需要录像
        recordDuration: 60,
        upload: true, // 需要上传到OSS
        speaker: false,
        mqtt: true, // MQTT上报开启
      };
    }
    // 雷达点云报警
    if (alarmType === AlarmType.RADAR_POINTCLOUD) {
      return {
        capture: true,
        record: true,
        recordDuration: 60,
        upload: true,
        speaker: false,
        mqtt: true,
      };
    }
    // 默认配置
    return {
      capture: true,
      record: false,
      recordDuration: 60,
      upload: false,
      speaker: false,
      mqtt: true,
    };
  };

  const [formData, setFormData] = useState<Partial<AlarmRule>>({
    name: '',
    alarmType: AlarmType.HELMET_DETECTION,
    scope: RuleScope.GLOBAL,
    enabled: true,
    actions: getDefaultActions(AlarmType.HELMET_DETECTION),
    conditions: {
      area: 'all',
    },
  });

  const [devices, setDevices] = useState<Device[]>([]);
  const [assemblies, setAssemblies] = useState<Assembly[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (rule) {
      setFormData({
        ...rule,
        actions: rule.actions || getDefaultActions(rule.alarmType),
        conditions: rule.conditions || {
          area: 'all',
        },
      });
    }
  }, [rule]);

  useEffect(() => {
    const loadOptions = async () => {
      setIsLoading(true);
      try {
        const [devicesRes, assembliesRes] = await Promise.all([
          deviceService.getDevices(),
          assemblyService.getAssemblies(),
        ]);
        setDevices(devicesRes.data || []);
        setAssemblies(assembliesRes.data || []);
      } catch (err) {
        console.error('加载选项失败:', err);
      } finally {
        setIsLoading(false);
      }
    };
    loadOptions();
  }, []);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name?.trim()) {
      alert('请输入规则名称');
      return;
    }
    onSave(formData);
  };

  const updateAction = (key: keyof typeof formData.actions, value: boolean | number) => {
    setFormData({
      ...formData,
      actions: {
        ...formData.actions!,
        [key]: value,
      },
    });
  };

  const updateCondition = (key: keyof typeof formData.conditions, value: any) => {
    setFormData({
      ...formData,
      conditions: {
        ...formData.conditions!,
        [key]: value,
      },
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* 基本信息 */}
      <div className="space-y-4">
        <h3 className="font-bold text-gray-800">{t('device_name')}</h3>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {t('rule_name')} <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
            value={formData.name || ''}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {t('alarm_type')} <span className="text-red-500">*</span>
          </label>
          <select
            className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
            value={formData.alarmType || AlarmType.HELMET_DETECTION}
            onChange={(e) => {
              const newAlarmType = e.target.value as AlarmType;
              // 切换报警类型时，自动调整默认动作配置
              setFormData({
                ...formData,
                alarmType: newAlarmType,
                actions: getDefaultActions(newAlarmType),
                // 如果是雷达点云，保留距离范围；否则清除
                conditions: {
                  ...formData.conditions,
                  distanceRange: newAlarmType === AlarmType.RADAR_POINTCLOUD ? formData.conditions?.distanceRange : undefined,
                },
              });
            }}
            required
          >
            <option value={AlarmType.HELMET_DETECTION}>{t('helmet_detection')}</option>
            <option value={AlarmType.VEST_DETECTION}>{t('vest_detection')}</option>
            <option value={AlarmType.VEHICLE_ALARM}>{t('vehicle_alarm')}</option>
            <option value={AlarmType.INPUT_PORT}>{t('input_port')}</option>
            <option value={AlarmType.RADAR_POINTCLOUD}>{t('radar_pointcloud')}</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {t('rule_scope')} <span className="text-red-500">*</span>
          </label>
          <select
            className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
            value={formData.scope || RuleScope.GLOBAL}
            onChange={(e) => {
              const scope = e.target.value as RuleScope;
              setFormData({
                ...formData,
                scope,
                deviceId: scope !== RuleScope.DEVICE ? undefined : formData.deviceId,
                assemblyId: scope !== RuleScope.ASSEMBLY ? undefined : formData.assemblyId,
              });
            }}
            required
          >
            <option value={RuleScope.GLOBAL}>{t('global_rule')}</option>
            <option value={RuleScope.ASSEMBLY}>{t('assembly_rule')}</option>
            <option value={RuleScope.DEVICE}>{t('device_rule')}</option>
          </select>
        </div>
        {formData.scope === RuleScope.ASSEMBLY && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('assemblies')}</label>
            <select
              className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
              value={formData.assemblyId || ''}
              onChange={(e) => setFormData({ ...formData, assemblyId: e.target.value || undefined })}
              required
            >
              <option value="">请选择装置</option>
              {assemblies.map((assembly) => (
                <option key={assembly.id} value={assembly.assemblyId || assembly.id}>
                  {assembly.name}
                </option>
              ))}
            </select>
          </div>
        )}
        {formData.scope === RuleScope.DEVICE && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('device_name')}</label>
            <select
              className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
              value={formData.deviceId || ''}
              onChange={(e) => setFormData({ ...formData, deviceId: e.target.value || undefined })}
              required
            >
              <option value="">请选择设备</option>
              {devices.map((device) => (
                <option key={device.id} value={device.id}>
                  {device.name} ({device.ip})
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* 触发条件 */}
      <div className="space-y-4">
        <h3 className="font-bold text-gray-800">{t('trigger_conditions')}</h3>
        {/* 区域限制 - 仅对装置级规则有效 */}
        {(formData.scope === RuleScope.ASSEMBLY || formData.scope === RuleScope.DEVICE) && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('area_limit')}</label>
            <select
              className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
              value={formData.conditions?.area || 'all'}
              onChange={(e) => updateCondition('area', e.target.value)}
            >
              <option value="all">全部</option>
              <option value="left">{t('left_camera')}</option>
              <option value="right">{t('right_camera')}</option>
            </select>
          </div>
        )}
        {/* 距离范围 - 仅对雷达点云报警类型显示 */}
        {formData.alarmType === AlarmType.RADAR_POINTCLOUD && (
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">最小距离（米）</label>
              <input
                type="number"
                min="0"
                className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                value={formData.conditions?.distanceRange?.[0] || ''}
                onChange={(e) =>
                  updateCondition('distanceRange', [
                    parseFloat(e.target.value) || 0,
                    formData.conditions?.distanceRange?.[1] || 100,
                  ])
                }
                placeholder="0"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">最大距离（米）</label>
              <input
                type="number"
                min="0"
                className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                value={formData.conditions?.distanceRange?.[1] || ''}
                onChange={(e) =>
                  updateCondition('distanceRange', [
                    formData.conditions?.distanceRange?.[0] || 0,
                    parseFloat(e.target.value) || 100,
                  ])
                }
                placeholder="100"
              />
            </div>
          </div>
        )}
      </div>

      {/* 执行动作 */}
      <div className="space-y-4">
        <h3 className="font-bold text-gray-800">{t('actions')}</h3>
        <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mb-4">
          <p className="text-sm text-blue-800">
            {formData.alarmType === AlarmType.INPUT_PORT
              ? '输入端口报警（雷达触发）：自动抓拍、录像上传OSS、MQTT上报'
              : [AlarmType.HELMET_DETECTION, AlarmType.VEST_DETECTION, AlarmType.VEHICLE_ALARM].includes(formData.alarmType!)
              ? '算法类报警：自动抓拍、MQTT上报（录像可选）'
              : '请配置执行动作'}
          </p>
        </div>
        <div className="space-y-3">
          <label className="flex items-center space-x-3 cursor-pointer">
            <input
              type="checkbox"
              className="w-5 h-5 text-blue-600 rounded focus:ring-blue-500"
              checked={formData.actions?.capture || false}
              onChange={(e) => updateAction('capture', e.target.checked)}
            />
            <span className="text-sm font-medium text-gray-700">{t('auto_capture')}</span>
          </label>
          <div className="ml-8 space-y-2">
            <label className="flex items-center space-x-3 cursor-pointer">
              <input
                type="checkbox"
                className="w-5 h-5 text-blue-600 rounded focus:ring-blue-500"
                checked={formData.actions?.record || false}
                onChange={(e) => updateAction('record', e.target.checked)}
              />
              <span className="text-sm font-medium text-gray-700">{t('record_video')}</span>
            </label>
            {formData.actions?.record && (
              <div>
                <input
                  type="number"
                  min="1"
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                  value={formData.actions.recordDuration || 60}
                  onChange={(e) => updateAction('recordDuration', parseInt(e.target.value) || 60)}
                  placeholder="录像时长（秒）"
                />
                <p className="text-xs text-gray-500 mt-1">录像时长（秒），建议60-300秒</p>
              </div>
            )}
          </div>
          <label className="flex items-center space-x-3 cursor-pointer">
            <input
              type="checkbox"
              className="w-5 h-5 text-blue-600 rounded focus:ring-blue-500"
              checked={formData.actions?.upload || false}
              onChange={(e) => updateAction('upload', e.target.checked)}
            />
            <span className="text-sm font-medium text-gray-700">{t('upload_oss')}</span>
            <span className="text-xs text-gray-500">（录像上传到OSS）</span>
          </label>
          <label className="flex items-center space-x-3 cursor-pointer">
            <input
              type="checkbox"
              className="w-5 h-5 text-blue-600 rounded focus:ring-blue-500"
              checked={formData.actions?.speaker || false}
              onChange={(e) => updateAction('speaker', e.target.checked)}
            />
            <span className="text-sm font-medium text-gray-700">{t('speaker_playback')}</span>
            <span className="text-xs text-gray-500">（音柱播报警示语音）</span>
          </label>
          <label className="flex items-center space-x-3 cursor-pointer">
            <input
              type="checkbox"
              className="w-5 h-5 text-blue-600 rounded focus:ring-blue-500"
              checked={formData.actions?.mqtt || false}
              onChange={(e) => updateAction('mqtt', e.target.checked)}
            />
            <span className="text-sm font-medium text-gray-700">{t('mqtt_report')}</span>
            <span className="text-xs text-gray-500">（开启后必须上报）</span>
          </label>
        </div>
      </div>

      {/* 按钮 */}
      <div className="flex justify-end space-x-3 pt-4 border-t border-gray-100">
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium"
        >
          {t('cancel')}
        </button>
        <button
          type="submit"
          className="px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200"
        >
          {t('save')}
        </button>
      </div>
    </form>
  );
};
