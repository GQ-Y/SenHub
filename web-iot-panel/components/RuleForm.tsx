import React, { useState, useEffect, useMemo } from 'react';
import { AlarmRule, RuleScope, AlarmFlow, CanonicalEvent } from '../types';
import { useAppContext } from '../contexts/AppContext';
import { deviceService, assemblyService } from '../src/api/services';
import { Device, Assembly } from '../types';
import { ToggleLeft, ToggleRight, ChevronDown, ChevronUp, Workflow, Filter } from 'lucide-react';

interface RuleFormProps {
  rule?: AlarmRule;
  onSave: (rule: Partial<AlarmRule>) => void;
  onCancel: () => void;
  flows?: AlarmFlow[];
  allEvents?: CanonicalEvent[];
}

const brandLabels: Record<string, string> = {
  hikvision: '海康',
  tiandy: '天地伟业',
  dahua: '大华',
};

const brandColors: Record<string, string> = {
  hikvision: 'bg-red-50 text-red-600 border-red-200',
  tiandy: 'bg-teal-50 text-teal-600 border-teal-200',
  dahua: 'bg-orange-50 text-orange-600 border-orange-200',
};

const BrandTags: React.FC<{ brands?: string[] }> = ({ brands }) => {
  if (!brands || brands.length === 0) {
    return <span className="text-[10px] px-1 py-0.5 bg-gray-100 text-gray-400 rounded border border-gray-200">通用</span>;
  }
  return (
    <>
      {brands.map(b => (
        <span key={b} className={`text-[10px] px-1 py-0.5 rounded border ${brandColors[b] || 'bg-gray-50 text-gray-500 border-gray-200'}`}>
          {brandLabels[b] || b}
        </span>
      ))}
    </>
  );
};

const EventToggle: React.FC<{
  event: CanonicalEvent;
  checked: boolean;
  onChange: (checked: boolean) => void;
}> = ({ event, checked, onChange }) => (
  <label className="flex items-center justify-between py-2 px-3 hover:bg-gray-50 rounded-lg cursor-pointer">
    <div className="flex-1 min-w-0">
      <div className="flex items-center gap-1.5 flex-wrap">
        <span className="text-sm text-gray-800">{event.nameZh}</span>
        <BrandTags brands={event.brands} />
        <span className="text-xs text-gray-400 px-1.5 py-0.5 bg-gray-100 rounded">{event.eventKey}</span>
      </div>
      {event.description && (
        <span className="text-xs text-gray-400 block mt-0.5">{event.description}</span>
      )}
    </div>
    <button
      type="button"
      onClick={(e) => { e.preventDefault(); onChange(!checked); }}
      className="flex-shrink-0 ml-2"
    >
      {checked ? <ToggleRight className="text-blue-600" size={24} /> : <ToggleLeft className="text-gray-300" size={24} />}
    </button>
  </label>
);

const categoryLabels: Record<string, string> = {
  basic: '基础事件',
  vca: '智能分析',
  face: '人脸识别',
  its: '交通事件',
  unknown: '未分类',
};

const CategoryGroup: React.FC<{
  category: string;
  events: CanonicalEvent[];
  selectedKeys: string[];
  onToggle: (eventKey: string, checked: boolean) => void;
}> = ({ category, events, selectedKeys, onToggle }) => {
  const [isExpanded, setIsExpanded] = useState(true);
  const selectedCount = events.filter(e => selectedKeys.includes(e.eventKey)).length;

  return (
    <div className="border border-gray-200 rounded-xl overflow-hidden">
      <button
        type="button"
        onClick={() => setIsExpanded(!isExpanded)}
        className="w-full flex items-center justify-between px-4 py-3 bg-gray-50 hover:bg-gray-100 transition-colors"
      >
        <div className="flex items-center space-x-2">
          <span className="font-medium text-gray-800">{categoryLabels[category] || category}</span>
          <span className="text-xs text-gray-400">{events.length}个</span>
          {selectedCount > 0 && (
            <span className="px-2 py-0.5 bg-blue-100 text-blue-700 rounded-full text-xs">{selectedCount}</span>
          )}
        </div>
        {isExpanded ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
      </button>
      {isExpanded && (
        <div className="p-3 space-y-1 max-h-64 overflow-y-auto">
          {events.map(event => (
            <EventToggle
              key={event.eventKey}
              event={event}
              checked={selectedKeys.includes(event.eventKey)}
              onChange={(checked) => onToggle(event.eventKey, checked)}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export const RuleForm: React.FC<RuleFormProps> = ({ rule, onSave, onCancel, flows = [], allEvents = [] }) => {
  const { t } = useAppContext();

  const [formData, setFormData] = useState<Partial<AlarmRule>>({
    name: '',
    scope: RuleScope.GLOBAL,
    enabled: true,
    eventKeys: [],
    flowId: undefined,
    conditions: { area: 'all' },
  });

  const [devices, setDevices] = useState<Device[]>([]);
  const [assemblies, setAssemblies] = useState<Assembly[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [showRadarEvent, setShowRadarEvent] = useState(false);
  const [brandFilter, setBrandFilter] = useState<string | null>(null);

  const selectedEventKeys = useMemo(() => {
    if (!formData.eventKeys) return [];
    if (typeof formData.eventKeys === 'string') {
      try { return JSON.parse(formData.eventKeys as any); } catch { return []; }
    }
    return formData.eventKeys;
  }, [formData.eventKeys]);

  const availableBrands = useMemo(() => {
    const set = new Set<string>();
    allEvents.forEach(e => e.brands?.forEach(b => set.add(b)));
    return Array.from(set).sort();
  }, [allEvents]);

  const filteredEvents = useMemo(() => {
    if (!brandFilter) return allEvents;
    return allEvents.filter(e => e.brands?.includes(brandFilter));
  }, [allEvents, brandFilter]);

  const groupedEvents = useMemo(() => {
    const groups: Record<string, CanonicalEvent[]> = {};
    filteredEvents.forEach(e => {
      const cat = e.category || 'unknown';
      if (!groups[cat]) groups[cat] = [];
      groups[cat].push(e);
    });
    return groups;
  }, [filteredEvents]);

  useEffect(() => {
    if (rule) {
      let parsedKeys: string[] = [];
      if (rule.eventKeys) {
        if (typeof rule.eventKeys === 'string') {
          try { parsedKeys = JSON.parse(rule.eventKeys as any); } catch { parsedKeys = []; }
        } else {
          parsedKeys = rule.eventKeys;
        }
      }
      setFormData({
        ...rule,
        eventKeys: parsedKeys,
        conditions: rule.conditions || { area: 'all' },
      });
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
    if (selectedEventKeys.length === 0 && !showRadarEvent) {
      alert('请至少选择一个报警事件类型');
      return;
    }
    const submitData: Partial<AlarmRule> = {
      ...formData,
      eventKeys: selectedEventKeys,
      alarmType: showRadarEvent ? ('radar_pointcloud' as any) : undefined,
    };
    onSave(submitData);
  };

  const handleToggleEvent = (eventKey: string, checked: boolean) => {
    const current = [...selectedEventKeys];
    if (checked) {
      if (!current.includes(eventKey)) current.push(eventKey);
    } else {
      const idx = current.indexOf(eventKey);
      if (idx > -1) current.splice(idx, 1);
    }
    setFormData({ ...formData, eventKeys: current });
  };

  const updateCondition = (key: string, value: any) => {
    setFormData({
      ...formData,
      conditions: { ...(formData.conditions as any), [key]: value },
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
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
                onClick={() => setFormData({
                  ...formData, scope,
                  deviceId: scope !== RuleScope.DEVICE ? undefined : formData.deviceId,
                  assemblyId: scope !== RuleScope.ASSEMBLY ? undefined : formData.assemblyId,
                })}
                className={`flex-1 px-3 py-2 rounded-xl text-sm font-medium transition-colors ${
                  formData.scope === scope ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
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
              {assemblies.map((a) => (
                <option key={a.id} value={a.assemblyId || a.id}>{a.name}</option>
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
              {devices.map((d) => (
                <option key={d.id} value={d.id}>{d.name} ({d.ip})</option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* 报警事件类型选择 */}
      <div className="space-y-4">
        <h3 className="font-bold text-gray-800">{t('select_event_types')} <span className="text-red-500">*</span></h3>

        {/* 品牌筛选栏 */}
        <div className="flex items-center gap-2 flex-wrap">
          <Filter size={14} className="text-gray-400" />
          <button
            type="button"
            onClick={() => setBrandFilter(null)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
              brandFilter === null ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50'
            }`}
          >
            全部品牌
          </button>
          {availableBrands.map(b => (
            <button
              key={b}
              type="button"
              onClick={() => setBrandFilter(brandFilter === b ? null : b)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                brandFilter === b
                  ? (brandColors[b]?.replace('bg-', 'bg-').replace('50', '100') || 'bg-blue-100 text-blue-700 border-blue-300')
                  : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50'
              }`}
            >
              {brandLabels[b] || b}
            </button>
          ))}
        </div>

        <div className="text-sm text-gray-500">
          {selectedEventKeys.length > 0 ? (
            <span className="text-blue-600 font-medium">{t('event_types_selected')}: {selectedEventKeys.length}</span>
          ) : (
            <span>{t('no_event_selected')}</span>
          )}
          {brandFilter && (
            <span className="ml-2 text-xs text-gray-400">
              (仅显示 {brandLabels[brandFilter] || brandFilter} 支持的事件)
            </span>
          )}
        </div>

        <div className="space-y-3">
          {Object.entries(groupedEvents).map(([category, events]) => (
            <CategoryGroup
              key={category}
              category={category}
              events={events}
              selectedKeys={selectedEventKeys}
              onToggle={handleToggleEvent}
            />
          ))}
          {Object.keys(groupedEvents).length === 0 && brandFilter && (
            <div className="text-center py-6 text-gray-400 text-sm">
              该品牌暂无关联的事件类型
            </div>
          )}
        </div>

        {/* 雷达入侵事件 */}
        <div className="border border-gray-200 rounded-xl overflow-hidden">
          <label className="flex items-center justify-between px-4 py-3 bg-purple-50 hover:bg-purple-100 cursor-pointer">
            <div className="flex items-center space-x-2">
              <span className="font-medium text-gray-800">{t('radar_intrusion_event')}</span>
              {showRadarEvent && (
                <span className="px-2 py-0.5 bg-purple-100 text-purple-700 rounded-full text-xs">1</span>
              )}
            </div>
            <button type="button" onClick={() => setShowRadarEvent(!showRadarEvent)}>
              {showRadarEvent ? <ToggleRight className="text-purple-600" size={24} /> : <ToggleLeft className="text-gray-300" size={24} />}
            </button>
          </label>
          {showRadarEvent && (
            <div className="p-4 space-y-4 bg-white">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">最小距离（米）</label>
                  <input
                    type="number" min="0"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                    value={(formData.conditions as any)?.distanceRange?.[0] || ''}
                    onChange={(e) => updateCondition('distanceRange', [parseFloat(e.target.value) || 0, (formData.conditions as any)?.distanceRange?.[1] || 100])}
                    placeholder="0"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">最大距离（米）</label>
                  <input
                    type="number" min="0"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-blue-500"
                    value={(formData.conditions as any)?.distanceRange?.[1] || ''}
                    onChange={(e) => updateCondition('distanceRange', [(formData.conditions as any)?.distanceRange?.[0] || 0, parseFloat(e.target.value) || 100])}
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
        <p className="text-xs text-gray-500">选择触发报警后执行的工作流程，工作流程在"工作流"模块中配置。</p>
      </div>

      <div className="flex justify-end space-x-3 pt-4 border-t border-gray-100">
        <button type="button" onClick={onCancel} className="px-5 py-2.5 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium">
          {t('cancel')}
        </button>
        <button type="submit" className="px-5 py-2.5 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200">
          {t('save')}
        </button>
      </div>
    </form>
  );
};
