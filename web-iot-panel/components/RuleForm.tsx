import React, { useState, useEffect, useMemo } from 'react';
import { AlarmRule, RuleScope, AlarmFlow, CameraEventType } from '../types';
import { useAppContext } from '../contexts/AppContext';
import { deviceService, assemblyService } from '../src/api/services';
import { Device, Assembly } from '../types';
import { ToggleLeft, ToggleRight, ChevronDown, ChevronUp, Workflow } from 'lucide-react';

interface RuleFormProps {
  rule?: AlarmRule;
  onSave: (rule: Partial<AlarmRule>) => void;
  onCancel: () => void;
  flows?: AlarmFlow[];
  eventTypes?: Record<string, CameraEventType[]>;
}

// 事件类型开关组件
const EventTypeToggle: React.FC<{
  event: CameraEventType;
  checked: boolean;
  onChange: (checked: boolean) => void;
}> = ({ event, checked, onChange }) => {
  return (
    <label className="flex items-center justify-between py-2 px-3 hover:bg-gray-50 rounded-lg cursor-pointer">
      <div className="flex-1 min-w-0">
        <span className="text-sm text-gray-800">{event.eventName}</span>
        {event.description && (
          <span className="text-xs text-gray-400 ml-2">({event.description})</span>
        )}
      </div>
      <button
        type="button"
        onClick={(e) => {
          e.preventDefault();
          onChange(!checked);
        }}
        className="flex-shrink-0 ml-2"
      >
        {checked ? (
          <ToggleRight className="text-blue-600" size={24} />
        ) : (
          <ToggleLeft className="text-gray-300" size={24} />
        )}
      </button>
    </label>
  );
};

// 品牌事件组
const BrandEventGroup: React.FC<{
  brand: string;
  brandLabel: string;
  events: CameraEventType[];
  selectedIds: number[];
  onToggle: (eventId: number, checked: boolean) => void;
}> = ({ brand, brandLabel, events, selectedIds, onToggle }) => {
  const [isExpanded, setIsExpanded] = useState(true);
  
  // 按类别分组
  const groupedEvents = useMemo(() => {
    const groups: Record<string, CameraEventType[]> = {};
    events.forEach(event => {
      const category = event.category || 'basic';
      if (!groups[category]) groups[category] = [];
      groups[category].push(event);
    });
    return groups;
  }, [events]);

  const selectedCount = events.filter(e => selectedIds.includes(e.id)).length;

  return (
    <div className="border border-gray-200 rounded-xl overflow-hidden">
      <button
        type="button"
        onClick={() => setIsExpanded(!isExpanded)}
        className="w-full flex items-center justify-between px-4 py-3 bg-gray-50 hover:bg-gray-100 transition-colors"
      >
        <div className="flex items-center space-x-2">
          <span className="font-medium text-gray-800">{brandLabel}</span>
          {selectedCount > 0 && (
            <span className="px-2 py-0.5 bg-blue-100 text-blue-700 rounded-full text-xs">
              {selectedCount}
            </span>
          )}
        </div>
        {isExpanded ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
      </button>
      {isExpanded && (
        <div className="p-3 space-y-2 max-h-64 overflow-y-auto">
          {Object.entries(groupedEvents).map(([category, categoryEvents]) => (
            <div key={category}>
              {Object.keys(groupedEvents).length > 1 && (
                <div className="text-xs font-medium text-gray-500 uppercase mb-1 px-2">
                  {getCategoryLabel(category)}
                </div>
              )}
              <div className="space-y-1">
                {categoryEvents.map(event => (
                  <EventTypeToggle
                    key={event.id}
                    event={event}
                    checked={selectedIds.includes(event.id)}
                    onChange={(checked) => onToggle(event.id, checked)}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

// 获取类别标签
const getCategoryLabel = (category: string): string => {
  const labels: Record<string, string> = {
    basic: '基础事件',
    vca: '智能分析',
    face: '人脸识别',
    its: '交通事件',
  };
  return labels[category] || category;
};

export const RuleForm: React.FC<RuleFormProps> = ({ rule, onSave, onCancel, flows = [], eventTypes = {} }) => {
  const { t } = useAppContext();
  
  const [formData, setFormData] = useState<Partial<AlarmRule>>({
    name: '',
    scope: RuleScope.GLOBAL,
    enabled: true,
    eventTypeIds: [],
    flowId: undefined,
    conditions: {
      area: 'all',
    },
  });

  const [devices, setDevices] = useState<Device[]>([]);
  const [assemblies, setAssemblies] = useState<Assembly[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [showRadarEvent, setShowRadarEvent] = useState(false);

  // 解析已选事件类型
  const selectedEventTypeIds = useMemo(() => {
    if (!formData.eventTypeIds) return [];
    if (typeof formData.eventTypeIds === 'string') {
      try {
        return JSON.parse(formData.eventTypeIds);
      } catch {
        return [];
      }
    }
    return formData.eventTypeIds;
  }, [formData.eventTypeIds]);

  useEffect(() => {
    if (rule) {
      let parsedEventTypeIds: number[] = [];
      if (rule.eventTypeIds) {
        if (typeof rule.eventTypeIds === 'string') {
          try {
            parsedEventTypeIds = JSON.parse(rule.eventTypeIds);
          } catch {
            parsedEventTypeIds = [];
          }
        } else {
          parsedEventTypeIds = rule.eventTypeIds;
        }
      }
      setFormData({
        ...rule,
        eventTypeIds: parsedEventTypeIds,
        conditions: rule.conditions || { area: 'all' },
      });
      // 检查是否有雷达事件
      if (rule.alarmType === 'radar_pointcloud') {
        setShowRadarEvent(true);
      }
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
    if (selectedEventTypeIds.length === 0 && !showRadarEvent) {
      alert('请至少选择一个报警事件类型');
      return;
    }
    
    // 构建提交数据
    const submitData: Partial<AlarmRule> = {
      ...formData,
      eventTypeIds: selectedEventTypeIds,
      // 如果选择了雷达事件，设置alarmType为兼容
      alarmType: showRadarEvent ? ('radar_pointcloud' as any) : undefined,
    };
    
    onSave(submitData);
  };

  const handleToggleEventType = (eventId: number, checked: boolean) => {
    const current = [...selectedEventTypeIds];
    if (checked) {
      if (!current.includes(eventId)) {
        current.push(eventId);
      }
    } else {
      const index = current.indexOf(eventId);
      if (index > -1) {
        current.splice(index, 1);
      }
    }
    setFormData({ ...formData, eventTypeIds: current });
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

  const brandLabels: Record<string, string> = {
    hikvision: t('brand_hikvision'),
    tiandy: t('brand_tiandy'),
    dahua: t('brand_dahua'),
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* 基本信息 */}
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {t('rule_name')} <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
            value={formData.name || ''}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            placeholder="输入规则名称"
            required
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {t('rule_scope')} <span className="text-red-500">*</span>
          </label>
          <div className="flex space-x-2">
            {[RuleScope.GLOBAL, RuleScope.ASSEMBLY, RuleScope.DEVICE].map((scope) => (
              <button
                key={scope}
                type="button"
                onClick={() => {
                  setFormData({
                    ...formData,
                    scope,
                    deviceId: scope !== RuleScope.DEVICE ? undefined : formData.deviceId,
                    assemblyId: scope !== RuleScope.ASSEMBLY ? undefined : formData.assemblyId,
                  });
                }}
                className={`flex-1 px-3 py-2 rounded-xl text-sm font-medium transition-colors ${
                  formData.scope === scope
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {scope === RuleScope.GLOBAL && t('global_rule')}
                {scope === RuleScope.ASSEMBLY && t('assembly_rule')}
                {scope === RuleScope.DEVICE && t('device_rule')}
              </button>
            ))}
          </div>
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

      {/* 报警事件类型选择 */}
      <div className="space-y-4">
        <h3 className="font-bold text-gray-800">{t('select_event_types')} <span className="text-red-500">*</span></h3>
        <div className="text-sm text-gray-500 mb-2">
          {selectedEventTypeIds.length > 0 ? (
            <span className="text-blue-600 font-medium">
              {t('event_types_selected')}: {selectedEventTypeIds.length}
            </span>
          ) : (
            <span>{t('no_event_selected')}</span>
          )}
        </div>
        
        {/* 品牌事件分组 */}
        <div className="space-y-3">
          {Object.entries(eventTypes).map(([brand, events]) => (
            <BrandEventGroup
              key={brand}
              brand={brand}
              brandLabel={brandLabels[brand] || brand}
              events={events}
              selectedIds={selectedEventTypeIds}
              onToggle={handleToggleEventType}
            />
          ))}
        </div>

        {/* 雷达入侵事件（独立选项） */}
        <div className="border border-gray-200 rounded-xl overflow-hidden">
          <label className="flex items-center justify-between px-4 py-3 bg-purple-50 hover:bg-purple-100 cursor-pointer">
            <div className="flex items-center space-x-2">
              <span className="font-medium text-gray-800">{t('radar_intrusion_event')}</span>
              {showRadarEvent && (
                <span className="px-2 py-0.5 bg-purple-100 text-purple-700 rounded-full text-xs">1</span>
              )}
            </div>
            <button
              type="button"
              onClick={() => setShowRadarEvent(!showRadarEvent)}
            >
              {showRadarEvent ? (
                <ToggleRight className="text-purple-600" size={24} />
              ) : (
                <ToggleLeft className="text-gray-300" size={24} />
              )}
            </button>
          </label>
          {showRadarEvent && (
            <div className="p-4 space-y-4 bg-white">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">最小距离（米）</label>
                  <input
                    type="number"
                    min="0"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
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
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
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
            </div>
          )}
        </div>
      </div>

      {/* 绑定工作流程 */}
      <div className="space-y-4">
        <h3 className="font-bold text-gray-800">{t('bind_workflow')}</h3>
        <div className="relative">
          <Workflow className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={18} />
          <select
            className="w-full bg-gray-50 border border-gray-200 rounded-xl pl-10 pr-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500 appearance-none"
            value={formData.flowId || ''}
            onChange={(e) => setFormData({ ...formData, flowId: e.target.value || undefined })}
          >
            <option value="">{t('select_workflow')}</option>
            {flows.map((flow) => (
              <option key={flow.flowId} value={flow.flowId}>
                {flow.name} {flow.isDefault && `(${t('default_flow')})`}
              </option>
            ))}
          </select>
          <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" size={18} />
        </div>
        <p className="text-xs text-gray-500">
          选择触发报警后执行的工作流程，工作流程在"工作流"模块中配置。
        </p>
      </div>

      {/* 按钮 */}
      <div className="flex justify-end space-x-3 pt-4 border-t border-gray-100">
        <button
          type="button"
          onClick={onCancel}
          className="px-5 py-2.5 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium"
        >
          {t('cancel')}
        </button>
        <button
          type="submit"
          className="px-5 py-2.5 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200"
        >
          {t('save')}
        </button>
      </div>
    </form>
  );
};
