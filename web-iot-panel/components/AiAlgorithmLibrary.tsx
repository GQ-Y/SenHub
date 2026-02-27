import React, { useState, useEffect, useCallback } from 'react';
import {
  Search, Plus, Edit2, Trash2, X, Check, ChevronDown, ChevronUp,
  AlertTriangle, Info, Shield, Eye, Code, Database as DbIcon
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
    try {
      const detail = await eventLibraryService.getEvent(id);
      setEvents(prev => prev.map(e => e.id === id ? { ...e, ...detail } : e));
      setExpandedId(id);
    } catch (err) {
      console.error('加载事件详情失败:', err);
    }
  };

  return (
    <div className="p-6 space-y-6 overflow-auto h-full">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-gray-800">{t('ai_algorithm_lib')}</h2>
          <p className="text-sm text-gray-500 mt-1">{t('ai_algorithm_lib_desc')}</p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus size={18} />
          新增事件
        </button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[200px] max-w-md">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="搜索事件键 / 名称..."
            value={searchKey}
            onChange={e => setSearchKey(e.target.value)}
            className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          />
        </div>
        <select
          value={filterCategory}
          onChange={e => setFilterCategory(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
        >
          {CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
        </select>
        <select
          value={filterGeneric}
          onChange={e => setFilterGeneric(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
        >
          <option value="">全部类型</option>
          <option value="true">仅通用报警</option>
          <option value="false">仅具体事件</option>
        </select>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-20 text-gray-400">加载中...</div>
        ) : events.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-gray-400">
            <DbIcon size={48} className="mb-3 opacity-50" />
            <p>暂无事件</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200 text-gray-500 text-xs uppercase">
                <th className="px-4 py-3 text-left font-medium">事件键</th>
                <th className="px-4 py-3 text-left font-medium">中文名</th>
                <th className="px-4 py-3 text-left font-medium">分类</th>
                <th className="px-4 py-3 text-left font-medium">严重度</th>
                <th className="px-4 py-3 text-center font-medium">通用</th>
                <th className="px-4 py-3 text-center font-medium">启用</th>
                <th className="px-4 py-3 text-center font-medium">映射</th>
                <th className="px-4 py-3 text-center font-medium">提示词</th>
                <th className="px-4 py-3 text-right font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {events.map(event => (
                <React.Fragment key={event.id}>
                  <tr
                    className={`border-b border-gray-100 hover:bg-gray-50 transition-colors cursor-pointer ${expandedId === event.id ? 'bg-blue-50/50' : ''}`}
                    onClick={() => toggleExpand(event.id)}
                  >
                    <td className="px-4 py-3 font-mono text-xs text-gray-700">{event.eventKey}</td>
                    <td className="px-4 py-3 text-gray-800 font-medium">{event.nameZh}</td>
                    <td className="px-4 py-3">
                      <span className="px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-600">
                        {CATEGORY_LABELS[event.category] || event.category}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded-full text-xs ${SEVERITY_COLORS[event.severity] || 'bg-gray-100 text-gray-600'}`}>
                        {event.severity}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-center">
                      {event.isGeneric ? (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs bg-orange-100 text-orange-700">
                          <AlertTriangle size={12} /> 通用
                        </span>
                      ) : null}
                    </td>
                    <td className="px-4 py-3 text-center">
                      {event.enabled ? (
                        <Check size={16} className="inline text-green-500" />
                      ) : (
                        <X size={16} className="inline text-red-400" />
                      )}
                    </td>
                    <td className="px-4 py-3 text-center text-gray-500">
                      {event.mappings?.length ?? '-'}
                    </td>
                    <td className="px-4 py-3 text-center">
                      {event.aiVerifyPrompt ? (
                        <Info size={14} className="inline text-blue-500" title={event.aiVerifyPrompt} />
                      ) : (
                        <span className="text-gray-300">-</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right" onClick={e => e.stopPropagation()}>
                      <div className="flex items-center justify-end gap-1">
                        <button
                          onClick={() => setEditingEvent(event)}
                          className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                          title="编辑"
                        >
                          <Edit2 size={15} />
                        </button>
                        <button
                          onClick={() => setDeleteConfirmId(event.id)}
                          className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                          title="删除"
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </td>
                  </tr>
                  {expandedId === event.id && (
                    <tr>
                      <td colSpan={9} className="px-6 py-4 bg-gray-50/70 border-b border-gray-200">
                        <ExpandedDetail event={event} onRefresh={loadEvents} />
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
            </tbody>
          </table>
        )}
      </div>

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
    <div className="space-y-4">
      {/* Description & Prompt */}
      <div className="grid grid-cols-2 gap-4">
        <div>
          <h4 className="text-xs font-medium text-gray-500 mb-1">描述</h4>
          <p className="text-sm text-gray-700">{event.description || '-'}</p>
        </div>
        <div>
          <h4 className="text-xs font-medium text-gray-500 mb-1">AI复核提示词</h4>
          <p className="text-sm text-gray-700 whitespace-pre-wrap">{event.aiVerifyPrompt || '未配置（使用默认）'}</p>
        </div>
      </div>

      {/* Mappings */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <h4 className="text-xs font-medium text-gray-500">品牌映射</h4>
          <button
            onClick={() => setShowAddMapping(!showAddMapping)}
            className="text-xs text-blue-600 hover:text-blue-700 flex items-center gap-1"
          >
            <Plus size={14} /> 添加映射
          </button>
        </div>
        {showAddMapping && (
          <div className="flex items-center gap-2 mb-2 p-2 bg-white rounded-lg border border-gray-200">
            <select
              value={newMapping.brand}
              onChange={e => setNewMapping(p => ({ ...p, brand: e.target.value }))}
              className="px-2 py-1 border rounded text-xs"
            >
              <option value="">品牌</option>
              <option value="hikvision">海康</option>
              <option value="tiandy">天地伟业</option>
              <option value="dahua">大华</option>
            </select>
            <select
              value={newMapping.sourceKind}
              onChange={e => setNewMapping(p => ({ ...p, sourceKind: e.target.value }))}
              className="px-2 py-1 border rounded text-xs"
            >
              <option value="event_key">event_key</option>
              <option value="command">command</option>
              <option value="alarm_type">alarm_type</option>
              <option value="vca_event">vca_event</option>
            </select>
            <input
              type="number"
              value={newMapping.sourceCode}
              onChange={e => setNewMapping(p => ({ ...p, sourceCode: parseInt(e.target.value) || 0 }))}
              className="w-20 px-2 py-1 border rounded text-xs"
              placeholder="source_code"
            />
            <input
              type="text"
              value={newMapping.note}
              onChange={e => setNewMapping(p => ({ ...p, note: e.target.value }))}
              className="flex-1 px-2 py-1 border rounded text-xs"
              placeholder="备注"
            />
            <button onClick={handleAddMapping} className="px-2 py-1 bg-blue-600 text-white rounded text-xs hover:bg-blue-700">
              <Check size={14} />
            </button>
            <button onClick={() => setShowAddMapping(false)} className="px-2 py-1 bg-gray-200 text-gray-600 rounded text-xs hover:bg-gray-300">
              <X size={14} />
            </button>
          </div>
        )}
        {event.mappings && event.mappings.length > 0 ? (
          <div className="space-y-1">
            {event.mappings.map(m => (
              <div key={m.id} className="flex items-center justify-between px-3 py-1.5 bg-white rounded-lg border border-gray-100 text-xs">
                <div className="flex items-center gap-3">
                  <span className="font-medium text-gray-700">{m.brand}</span>
                  <span className="text-gray-400">{m.sourceKind}</span>
                  <span className="font-mono text-gray-500">{m.sourceCode}</span>
                  {m.note && <span className="text-gray-400">{m.note}</span>}
                </div>
                <button
                  onClick={() => handleDeleteMapping(m.id)}
                  className="p-1 text-gray-300 hover:text-red-500 transition-colors"
                >
                  <Trash2 size={13} />
                </button>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-xs text-gray-400">暂无映射</p>
        )}
      </div>

      {/* Raw Payloads */}
      {event.rawPayloads && event.rawPayloads.length > 0 && (
        <div>
          <h4 className="text-xs font-medium text-gray-500 mb-1 flex items-center gap-1">
            <Code size={13} /> 原始报警数据样本
          </h4>
          {event.rawPayloads.map(rp => (
            <div key={rp.id} className="mb-2">
              <div className="text-xs text-gray-400 mb-0.5">{rp.brand} · {rp.createdAt}</div>
              <pre className="text-xs bg-gray-900 text-green-400 p-3 rounded-lg overflow-x-auto max-h-40">
                {formatJson(rp.rawPayload)}
              </pre>
            </div>
          ))}
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
