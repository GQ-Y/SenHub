import React, { useState, useEffect, useCallback } from 'react';
import {
  Search, Plus, Edit2, Trash2, X, Check,
  AlertTriangle, Info, Code, Database as DbIcon
} from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { eventLibraryService, EventLibraryItem, EventBrandMapping, EventRawPayload } from '../src/api/services';

const CATEGORIES = [
  { value: '', label: '全部分类' },
  { value: 'basic', label: '基础报警' },
  { value: 'vca', label: '智能分析' },
  { value: 'face', label: '人脸识别' },
  { value: 'its', label: '交通' },
  { value: 'generic', label: '通用报警' },
  { value: 'unknown', label: '未知(自动发现)' },
];

const SEVERITY_COLORS: Record<string, string> = {
  critical: 'bg-red-100 text-red-700',
  error: 'bg-red-50 text-red-600',
  warning: 'bg-yellow-100 text-yellow-700',
  info: 'bg-blue-100 text-blue-700',
};

const CATEGORY_LABELS: Record<string, string> = {
  basic: '基础报警',
  vca: '智能分析',
  face: '人脸识别',
  its: '交通',
  generic: '通用报警',
  unknown: '未知',
};

export const AiAlgorithmLibrary: React.FC = () => {
  const { t } = useAppContext();
  const [events, setEvents] = useState<EventLibraryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchKey, setSearchKey] = useState('');
  const [filterCategory, setFilterCategory] = useState('');
  const [filterGeneric, setFilterGeneric] = useState<string>('');
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [editingEvent, setEditingEvent] = useState<EventLibraryItem | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);

  const loadEvents = useCallback(async () => {
    setLoading(true);
    try {
      const params: any = {};
      if (searchKey) params.eventKey = searchKey;
      if (filterCategory) params.category = filterCategory;
      if (filterGeneric === 'true') params.isGeneric = true;
      if (filterGeneric === 'false') params.isGeneric = false;
      const data = await eventLibraryService.getEvents(params);
      setEvents(data);
    } catch (err) {
      console.error('加载事件库失败:', err);
    } finally {
      setLoading(false);
    }
  }, [searchKey, filterCategory, filterGeneric]);

  useEffect(() => { loadEvents(); }, [loadEvents]);

  const handleDelete = async (id: number) => {
    try {
      await eventLibraryService.deleteEvent(id);
      setDeleteConfirmId(null);
      loadEvents();
    } catch (err) {
      console.error('删除事件失败:', err);
    }
  };

  const toggleExpand = async (id: number) => {
    if (expandedId === id) {
      setExpandedId(null);
      return;
    }
    // 先展开（列表里已有 mappings），再后台拉取详情补全 rawPayloads 等
    setExpandedId(id);
    try {
      const detail = await eventLibraryService.getEvent(id);
      setEvents(prev => prev.map(e => Number(e.id) === Number(id) ? { ...e, ...detail } : e));
    } catch (err) {
      console.error('加载事件详情失败:', err);
    }
  };

  return (
    <div className="h-full flex flex-col min-h-0 bg-[#f8f9fa]">
      {/* Header - 极简扁平 */}
      <div className="flex-shrink-0 flex items-center justify-between px-6 py-4 bg-white border-b border-gray-200/80">
        <div>
          <h2 className="text-lg font-semibold text-gray-800">{t('ai_algorithm_lib')}</h2>
          <p className="text-xs text-gray-500 mt-0.5">{t('ai_algorithm_lib_desc')}</p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="flex items-center gap-2 px-3 py-2 bg-gray-800 text-white text-sm rounded-md hover:bg-gray-700 transition-colors"
        >
          <Plus size={16} />
          新增事件
        </button>
      </div>

      {/* Filters - 扁平单行 */}
      <div className="flex-shrink-0 flex items-center gap-3 px-6 py-3 bg-white border-b border-gray-100">
        <div className="relative flex-1 min-w-[180px] max-w-sm">
          <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="搜索事件键 / 名称..."
            value={searchKey}
            onChange={e => setSearchKey(e.target.value)}
            className="w-full pl-8 pr-3 py-2 border border-gray-200 rounded-md text-sm bg-gray-50/50 focus:bg-white focus:border-gray-300 focus:outline-none"
          />
        </div>
        <select
          value={filterCategory}
          onChange={e => setFilterCategory(e.target.value)}
          className="px-3 py-2 border border-gray-200 rounded-md text-sm bg-gray-50/50 focus:bg-white focus:border-gray-300 focus:outline-none"
        >
          {CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
        </select>
        <select
          value={filterGeneric}
          onChange={e => setFilterGeneric(e.target.value)}
          className="px-3 py-2 border border-gray-200 rounded-md text-sm bg-gray-50/50 focus:bg-white focus:border-gray-300 focus:outline-none"
        >
          <option value="">全部类型</option>
          <option value="true">仅通用报警</option>
          <option value="false">仅具体事件</option>
        </select>
      </div>

      {/* 内容区 - 卡片占满 */}
      <div className="flex-1 min-h-0 overflow-auto p-6">
        <div className="h-full min-h-[400px] rounded-lg border border-gray-200 bg-white flex flex-col">
          {loading ? (
            <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">加载中...</div>
          ) : events.length === 0 ? (
            <div className="flex-1 flex flex-col items-center justify-center text-gray-400 text-sm">
              <DbIcon size={40} className="mb-2 opacity-40" />
              <p>暂无事件</p>
            </div>
          ) : (
            <div className="flex-1 overflow-auto p-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
                {events.map(event => (
                  <div
                    key={event.id}
                    className={`rounded-lg border bg-white transition-colors cursor-pointer flex flex-col min-h-0 ${
                      expandedId === event.id
                        ? 'border-gray-400 ring-2 ring-gray-200'
                        : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50/50'
                    }`}
                    onClick={() => toggleExpand(event.id)}
                  >
                    <div className="p-3 flex-1 flex flex-col min-w-0">
                      <div className="flex items-start justify-between gap-2">
                        <span className="font-mono text-xs text-gray-500 truncate" title={event.eventKey}>
                          {event.eventKey}
                        </span>
                        <div className="flex items-center gap-0.5 flex-shrink-0" onClick={e => e.stopPropagation()}>
                          <button
                            onClick={e => { e.stopPropagation(); setEditingEvent(event); }}
                            className="p-1.5 text-gray-400 hover:text-gray-700 hover:bg-gray-100 rounded transition-colors"
                            title="编辑"
                          >
                            <Edit2 size={14} />
                          </button>
                          <button
                            onClick={e => { e.stopPropagation(); setDeleteConfirmId(event.id); }}
                            className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
                            title="删除"
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </div>
                      <p className="text-sm font-medium text-gray-800 mt-1 truncate" title={event.nameZh}>
                        {event.nameZh}
                      </p>
                      <div className="flex flex-wrap items-center gap-1.5 mt-2">
                        <span className="px-1.5 py-0.5 rounded text-xs bg-gray-100 text-gray-600">
                          {CATEGORY_LABELS[event.category] || event.category}
                        </span>
                        <span className={`px-1.5 py-0.5 rounded text-xs ${SEVERITY_COLORS[event.severity] || 'bg-gray-100 text-gray-600'}`}>
                          {event.severity}
                        </span>
                        {event.isGeneric && (
                          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-xs bg-amber-50 text-amber-700">
                            <AlertTriangle size={10} /> 通用
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-3 mt-2 text-xs text-gray-400">
                        <span className={event.enabled ? 'text-green-600' : 'text-gray-400'}>
                          {event.enabled ? <><Check size={12} className="inline" /> 启用</> : <><X size={12} className="inline" /> 关闭</>}
                        </span>
                        <span>映射 {event.mappings?.length ?? 0}</span>
                        {event.aiVerifyPrompt ? <Info size={12} className="text-blue-500" title={event.aiVerifyPrompt} /> : null}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 右侧抽屉：事件详情 */}
      {expandedId !== null && (() => {
        const event = events.find(e => Number(e.id) === Number(expandedId));
        if (!event) return null;
        return (
          <>
            <div
              className="fixed inset-0 z-40 bg-black/25 transition-opacity"
              aria-hidden
              onClick={() => setExpandedId(null)}
            />
            <div
              className="fixed top-0 right-0 bottom-0 z-50 w-full max-w-lg bg-white shadow-xl flex flex-col transition-transform duration-200 ease-out"
              role="dialog"
              aria-label="事件详情"
            >
              <div className="flex-shrink-0 flex items-center justify-between px-4 py-3 border-b border-gray-200 bg-gray-50/80">
                <div className="flex items-center gap-2 min-w-0">
                  <span className="font-mono text-sm text-gray-600 truncate">{event.eventKey}</span>
                  <span className="text-gray-400 flex-shrink-0">/</span>
                  <span className="font-medium text-gray-800 truncate">{event.nameZh}</span>
                  <span className="px-2 py-0.5 rounded text-xs bg-gray-200 text-gray-600 flex-shrink-0">
                    {CATEGORY_LABELS[event.category] || event.category}
                  </span>
                </div>
                <button
                  onClick={() => setExpandedId(null)}
                  className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-200 rounded-md transition-colors flex-shrink-0"
                  title="关闭"
                >
                  <X size={20} />
                </button>
              </div>
              <div className="flex-1 min-h-0 overflow-auto p-4">
                <ExpandedDetail event={event} onRefresh={loadEvents} />
              </div>
            </div>
          </>
        );
      })()}

      {/* Delete Confirm */}
      {deleteConfirmId !== null && (
        <ConfirmDialog
          message="确定删除该事件及其所有品牌映射？"
          onConfirm={() => handleDelete(deleteConfirmId)}
          onCancel={() => setDeleteConfirmId(null)}
        />
      )}

      {/* Create / Edit Modal */}
      {(showCreateModal || editingEvent) && (
        <EventFormModal
          event={editingEvent}
          onClose={() => { setShowCreateModal(false); setEditingEvent(null); }}
          onSaved={() => { setShowCreateModal(false); setEditingEvent(null); loadEvents(); }}
        />
      )}
    </div>
  );
};

const ExpandedDetail: React.FC<{ event: EventLibraryItem; onRefresh: () => void }> = ({ event, onRefresh }) => {
  const [showAddMapping, setShowAddMapping] = useState(false);
  const [newMapping, setNewMapping] = useState({ brand: '', sourceKind: 'event_key', sourceCode: 0, note: '' });

  const handleAddMapping = async () => {
    if (!newMapping.brand) return;
    try {
      await eventLibraryService.addMapping(event.id, {
        brand: newMapping.brand,
        sourceKind: newMapping.sourceKind,
        sourceCode: newMapping.sourceCode,
        priority: 0,
        note: newMapping.note,
      });
      setShowAddMapping(false);
      setNewMapping({ brand: '', sourceKind: 'event_key', sourceCode: 0, note: '' });
      onRefresh();
    } catch (err) {
      console.error('添加映射失败:', err);
    }
  };

  const handleDeleteMapping = async (mappingId: number) => {
    try {
      await eventLibraryService.deleteMapping(event.id, mappingId);
      onRefresh();
    } catch (err) {
      console.error('删除映射失败:', err);
    }
  };

  return (
    <div className="space-y-6">
      {/* 描述与提示词：左右分栏，留白充足 */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div>
          <h4 className="text-sm font-medium text-gray-700 mb-2">描述</h4>
          <p className="text-sm text-gray-600 leading-relaxed">{event.description || '—'}</p>
        </div>
        <div>
          <h4 className="text-sm font-medium text-gray-700 mb-2">AI 复核提示词</h4>
          <p className="text-sm text-gray-600 leading-relaxed whitespace-pre-wrap">
            {event.aiVerifyPrompt || '未配置（使用默认）'}
          </p>
        </div>
      </div>

      {/* 品牌映射：表格式 + 添加表单 */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h4 className="text-sm font-medium text-gray-700">品牌映射</h4>
          <button
            onClick={() => setShowAddMapping(!showAddMapping)}
            className="flex items-center gap-1.5 px-2.5 py-1.5 text-sm text-gray-600 hover:text-gray-800 hover:bg-gray-100 rounded-md transition-colors"
          >
            <Plus size={14} /> 添加映射
          </button>
        </div>
        {showAddMapping && (
          <div className="flex flex-wrap items-end gap-3 mb-4 p-4 bg-gray-50 rounded-lg border border-gray-200">
            <div>
              <label className="block text-xs text-gray-500 mb-1">品牌</label>
              <select
                value={newMapping.brand}
                onChange={e => setNewMapping(p => ({ ...p, brand: e.target.value }))}
                className="px-3 py-2 border border-gray-200 rounded-md text-sm min-w-[120px]"
              >
                <option value="">请选择</option>
                <option value="hikvision">海康</option>
                <option value="tiandy">天地伟业</option>
                <option value="dahua">大华</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">来源类型</label>
              <select
                value={newMapping.sourceKind}
                onChange={e => setNewMapping(p => ({ ...p, sourceKind: e.target.value }))}
                className="px-3 py-2 border border-gray-200 rounded-md text-sm min-w-[120px]"
              >
                <option value="event_key">event_key</option>
                <option value="command">command</option>
                <option value="alarm_type">alarm_type</option>
                <option value="vca_event">vca_event</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">来源编码</label>
              <input
                type="number"
                value={newMapping.sourceCode}
                onChange={e => setNewMapping(p => ({ ...p, sourceCode: parseInt(e.target.value) || 0 }))}
                className="w-24 px-3 py-2 border border-gray-200 rounded-md text-sm"
              />
            </div>
            <div className="flex-1 min-w-[160px]">
              <label className="block text-xs text-gray-500 mb-1">备注</label>
              <input
                type="text"
                value={newMapping.note}
                onChange={e => setNewMapping(p => ({ ...p, note: e.target.value }))}
                className="w-full px-3 py-2 border border-gray-200 rounded-md text-sm"
                placeholder="如：开关量输入"
              />
            </div>
            <div className="flex gap-2">
              <button
                onClick={handleAddMapping}
                className="px-3 py-2 bg-gray-800 text-white text-sm rounded-md hover:bg-gray-700"
              >
                <Check size={16} className="inline" /> 添加
              </button>
              <button
                onClick={() => setShowAddMapping(false)}
                className="px-3 py-2 bg-white border border-gray-200 text-gray-600 text-sm rounded-md hover:bg-gray-50"
              >
                <X size={16} className="inline" /> 取消
              </button>
            </div>
          </div>
        )}
        {event.mappings && event.mappings.length > 0 ? (
          <div className="rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-gray-50 text-left text-gray-500 text-xs font-medium">
                  <th className="px-4 py-2.5">品牌</th>
                  <th className="px-4 py-2.5">来源类型</th>
                  <th className="px-4 py-2.5">来源编码</th>
                  <th className="px-4 py-2.5">备注</th>
                  <th className="px-4 py-2.5 w-16"></th>
                </tr>
              </thead>
              <tbody>
                {event.mappings.map(m => (
                  <tr key={m.id} className="border-t border-gray-100 hover:bg-gray-50/50">
                    <td className="px-4 py-2.5 font-medium text-gray-800">{m.brand}</td>
                    <td className="px-4 py-2.5 text-gray-600 font-mono text-xs">{m.sourceKind}</td>
                    <td className="px-4 py-2.5 text-gray-600 font-mono">{m.sourceCode}</td>
                    <td className="px-4 py-2.5 text-gray-500">{m.note || '—'}</td>
                    <td className="px-4 py-2.5">
                      <button
                        onClick={() => handleDeleteMapping(m.id)}
                        className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
                        title="删除"
                      >
                        <Trash2 size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-gray-400 py-4">暂无映射，点击「添加映射」新增</p>
        )}
      </div>

      {/* 原始报警数据样本 */}
      {event.rawPayloads && event.rawPayloads.length > 0 && (
        <div>
          <h4 className="text-sm font-medium text-gray-700 mb-2 flex items-center gap-2">
            <Code size={14} /> 原始报警数据样本
          </h4>
          <div className="space-y-3">
            {event.rawPayloads.map(rp => (
              <div key={rp.id} className="rounded-lg border border-gray-200 overflow-hidden">
                <div className="px-3 py-1.5 bg-gray-50 text-xs text-gray-500 border-b border-gray-100">
                  {rp.brand} · {rp.createdAt}
                </div>
                <pre className="p-4 text-xs bg-gray-900 text-green-400 overflow-x-auto max-h-48 font-mono">
                  {formatJson(rp.rawPayload)}
                </pre>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

function formatJson(str?: string): string {
  if (!str) return '';
  try {
    return JSON.stringify(JSON.parse(str), null, 2);
  } catch {
    return str;
  }
}

const EventFormModal: React.FC<{
  event: EventLibraryItem | null;
  onClose: () => void;
  onSaved: () => void;
}> = ({ event, onClose, onSaved }) => {
  const isEdit = !!event;
  const [form, setForm] = useState({
    eventKey: event?.eventKey || '',
    nameZh: event?.nameZh || '',
    nameEn: event?.nameEn || '',
    category: event?.category || 'vca',
    severity: event?.severity || 'warning',
    description: event?.description || '',
    isGeneric: event?.isGeneric || false,
    aiVerifyPrompt: event?.aiVerifyPrompt || '',
    enabled: event?.enabled ?? true,
  });
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.eventKey || !form.nameZh) return;
    setSaving(true);
    try {
      if (isEdit && event) {
        await eventLibraryService.updateEvent(event.id, form);
      } else {
        await eventLibraryService.createEvent(form);
      }
      onSaved();
    } catch (err) {
      console.error('保存失败:', err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-semibold text-gray-800">{isEdit ? '编辑事件' : '新增事件'}</h3>
          <button onClick={onClose} className="p-1 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100">
            <X size={20} />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="px-6 py-4 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">事件键 (event_key)</label>
              <input
                type="text"
                value={form.eventKey}
                onChange={e => setForm(p => ({ ...p, eventKey: e.target.value }))}
                disabled={isEdit}
                className="w-full px-3 py-2 border rounded-lg text-sm disabled:bg-gray-100"
                placeholder="如 PERIMETER_INTRUSION"
                required
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">中文名称</label>
              <input
                type="text"
                value={form.nameZh}
                onChange={e => setForm(p => ({ ...p, nameZh: e.target.value }))}
                className="w-full px-3 py-2 border rounded-lg text-sm"
                required
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">英文名称</label>
              <input
                type="text"
                value={form.nameEn}
                onChange={e => setForm(p => ({ ...p, nameEn: e.target.value }))}
                className="w-full px-3 py-2 border rounded-lg text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">分类</label>
              <select
                value={form.category}
                onChange={e => setForm(p => ({ ...p, category: e.target.value }))}
                className="w-full px-3 py-2 border rounded-lg text-sm"
              >
                <option value="basic">基础报警</option>
                <option value="vca">智能分析</option>
                <option value="face">人脸识别</option>
                <option value="its">交通</option>
                <option value="generic">通用报警</option>
                <option value="unknown">未知</option>
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">严重度</label>
              <select
                value={form.severity}
                onChange={e => setForm(p => ({ ...p, severity: e.target.value }))}
                className="w-full px-3 py-2 border rounded-lg text-sm"
              >
                <option value="info">信息</option>
                <option value="warning">警告</option>
                <option value="error">错误</option>
                <option value="critical">严重</option>
              </select>
            </div>
            <div className="flex items-end gap-4 pb-1">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={form.isGeneric}
                  onChange={e => setForm(p => ({ ...p, isGeneric: e.target.checked }))}
                  className="rounded border-gray-300"
                />
                通用报警
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={e => setForm(p => ({ ...p, enabled: e.target.checked }))}
                  className="rounded border-gray-300"
                />
                启用
              </label>
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">描述</label>
            <input
              type="text"
              value={form.description}
              onChange={e => setForm(p => ({ ...p, description: e.target.value }))}
              className="w-full px-3 py-2 border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">AI复核提示词（留空则使用默认）</label>
            <textarea
              value={form.aiVerifyPrompt}
              onChange={e => setForm(p => ({ ...p, aiVerifyPrompt: e.target.value }))}
              className="w-full px-3 py-2 border rounded-lg text-sm resize-y"
              rows={3}
              placeholder="自定义AI核验时的附加说明文本..."
            />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200 text-sm">
              取消
            </button>
            <button
              type="submit"
              disabled={saving}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 text-sm"
            >
              {saving ? '保存中...' : (isEdit ? '保存' : '创建')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

const ConfirmDialog: React.FC<{ message: string; onConfirm: () => void; onCancel: () => void }> = ({ message, onConfirm, onCancel }) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onCancel}>
    <div className="bg-white rounded-xl shadow-xl p-6 max-w-sm mx-4" onClick={e => e.stopPropagation()}>
      <div className="flex items-center gap-3 mb-4">
        <AlertTriangle size={24} className="text-red-500" />
        <p className="text-gray-800">{message}</p>
      </div>
      <div className="flex justify-end gap-3">
        <button onClick={onCancel} className="px-4 py-2 text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200 text-sm">
          取消
        </button>
        <button onClick={onConfirm} className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 text-sm">
          确认删除
        </button>
      </div>
    </div>
  </div>
);
