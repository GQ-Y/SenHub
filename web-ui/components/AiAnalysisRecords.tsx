import React, { useState, useEffect, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { useAppContext } from '../contexts/AppContext';
import { aiAnalysisService, AiAnalysisRecord } from '../src/api/services';
import { fetchWithAuthAsBlobUrl, appendTokenToStaticUrl } from '../src/api/client';
import {
  Brain, Image as ImageIcon, Volume2, VolumeX, RefreshCw,
  Clock, ShieldCheck, ShieldX, Shield, AlertTriangle,
  Megaphone, X, ZoomIn, ChevronLeft, ChevronRight, Trash2, Search,
  ChevronDown, Check, Calendar, CheckSquare, Square
} from 'lucide-react';

const PAGE_SIZE_OPTIONS = [20, 50, 100];
const EVENT_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: '全部类型' },
  { value: 'LOITERING', label: '徘徊检测' },
  { value: 'PERIMETER_INTRUSION', label: '周界入侵' },
  { value: 'MOTION_DETECTION', label: '移动侦测' },
  { value: 'CROSS_LINE_DETECTION', label: '越线检测' },
  { value: 'OTHER', label: '其他' },
];

/** 自定义事件类型下拉（非原生 select） */
const CustomEventTypeSelect: React.FC<{
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
  label: string;
}> = ({ value, options, onChange, label }) => {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const currentLabel = options.find((o) => o.value === value)?.label ?? options[0]?.label ?? '';
  useEffect(() => {
    const onOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    if (open) document.addEventListener('mousedown', onOutside);
    return () => document.removeEventListener('mousedown', onOutside);
  }, [open]);
  return (
    <div ref={ref} className="relative">
      <label className="block text-xs text-gray-500 mb-1">{label}</label>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full min-w-[140px] flex items-center justify-between gap-2 rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-sm text-left text-gray-800 hover:border-gray-300 hover:bg-gray-50/80 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-400"
      >
        <span className="truncate">{currentLabel}</span>
        <ChevronDown size={16} className={`text-gray-400 flex-shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="absolute top-full left-0 right-0 mt-1 py-1 rounded-xl border border-gray-200 bg-white shadow-lg z-50 max-h-56 overflow-y-auto">
          {options.map((o) => (
            <button
              key={o.value || 'all'}
              type="button"
              onClick={() => { onChange(o.value); setOpen(false); }}
              className={`w-full flex items-center justify-between px-3 py-2.5 text-sm text-left transition-colors ${
                o.value === value ? 'bg-blue-50 text-blue-700 font-medium' : 'text-gray-700 hover:bg-gray-50'
              }`}
            >
              {o.label}
              {o.value === value && <Check size={14} className="text-blue-600" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

/** 格式化 datetime 为显示用 */
function formatDateTimeLocal(s: string): string {
  if (!s || !s.trim()) return '';
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const h = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${day} ${h}:${min}`;
}

/** 自定义日期时间选择器（非原生 input[datetime-local]） */
const CustomDateTimeField: React.FC<{
  value: string;
  onChange: (value: string) => void;
  label: string;
  placeholder?: string;
}> = ({ value, onChange, label, placeholder = '选择日期时间' }) => {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const display = value ? formatDateTimeLocal(value) : '';
  const now = new Date();
  const [y, m, d, h, min] = (() => {
    if (!value) {
      return [
        String(now.getFullYear()),
        String(now.getMonth() + 1).padStart(2, '0'),
        String(now.getDate()).padStart(2, '0'),
        String(now.getHours()).padStart(2, '0'),
        String(now.getMinutes()).padStart(2, '0'),
      ];
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return ['', '', '', '', ''];
    return [
      String(date.getFullYear()),
      String(date.getMonth() + 1).padStart(2, '0'),
      String(date.getDate()).padStart(2, '0'),
      String(date.getHours()).padStart(2, '0'),
      String(date.getMinutes()).padStart(2, '0'),
    ];
  })();
  const iy = y === '' ? now.getFullYear() : parseInt(y, 10);
  const im = m === '' ? now.getMonth() + 1 : parseInt(m, 10);
  const id = d === '' ? now.getDate() : parseInt(d, 10);
  const ih = h === '' ? 0 : parseInt(h, 10);
  const imin = min === '' ? 0 : parseInt(min, 10);
  const setNow = () => {
    const n = new Date();
    const v = `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}T${String(n.getHours()).padStart(2, '0')}:${String(n.getMinutes()).padStart(2, '0')}`;
    onChange(v);
    setOpen(false);
  };
  const setParts = (ny?: number, nm?: number, nd?: number, nh?: number, nmin?: number) => {
    const yy = ny ?? iy; const mm = nm ?? im; const dd = nd ?? id;
    const hh = nh ?? ih; const mmin = nmin ?? imin;
    const date = new Date(yy, mm - 1, dd, hh, mmin);
    if (Number.isNaN(date.getTime())) return;
    const v = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
    onChange(v);
  };
  useEffect(() => {
    const onOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    if (open) document.addEventListener('mousedown', onOutside);
    return () => document.removeEventListener('mousedown', onOutside);
  }, [open]);
  const years = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() - 2 + i);
  const months = Array.from({ length: 12 }, (_, i) => i + 1);
  const daysInMonth = (year: number, month: number) => new Date(year, month, 0).getDate();
  const days = im && iy ? Array.from({ length: daysInMonth(iy, im) }, (_, i) => i + 1) : Array.from({ length: 31 }, (_, i) => i + 1);
  const hours = Array.from({ length: 24 }, (_, i) => i);
  const minutes = Array.from({ length: 60 }, (_, i) => i);
  return (
    <div ref={ref} className="relative">
      <label className="block text-xs text-gray-500 mb-1">{label}</label>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full min-w-[180px] flex items-center justify-between gap-2 rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-sm text-left text-gray-800 hover:border-gray-300 hover:bg-gray-50/80 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-400"
      >
        <span className={`truncate flex items-center gap-1.5 ${!display ? 'text-gray-400' : ''}`}>
          <Calendar size={14} className="text-gray-400 flex-shrink-0" />
          {display || placeholder}
        </span>
        <ChevronDown size={16} className={`text-gray-400 flex-shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="absolute top-full left-0 mt-1 w-[320px] p-3 rounded-xl border border-gray-200 bg-white shadow-xl z-50 space-y-3">
          <div className="grid grid-cols-3 gap-2">
            <div>
              <span className="text-[10px] text-gray-400 uppercase">年</span>
              <select
                value={y}
                onChange={(e) => {
                  const v = e.target.value;
                  setParts(v ? parseInt(v, 10) : undefined, im || undefined, id || undefined, ih || undefined, imin || undefined);
                }}
                className="w-full mt-0.5 rounded-lg border border-gray-200 px-2 py-1.5 text-xs"
              >
                {years.map((yr) => (
                  <option key={yr} value={yr}>{yr}</option>
                ))}
              </select>
            </div>
            <div>
              <span className="text-[10px] text-gray-400 uppercase">月</span>
              <select
                value={m}
                onChange={(e) => {
                  const v = e.target.value;
                  setParts(iy || undefined, v ? parseInt(v, 10) : undefined, id || undefined, ih || undefined, imin || undefined);
                }}
                className="w-full mt-0.5 rounded-lg border border-gray-200 px-2 py-1.5 text-xs"
              >
                {months.map((mo) => (
                  <option key={mo} value={mo}>{mo}</option>
                ))}
              </select>
            </div>
            <div>
              <span className="text-[10px] text-gray-400 uppercase">日</span>
              <select
                value={d}
                onChange={(e) => {
                  const v = e.target.value;
                  setParts(iy || undefined, im || undefined, v ? parseInt(v, 10) : undefined, ih || undefined, imin || undefined);
                }}
                className="w-full mt-0.5 rounded-lg border border-gray-200 px-2 py-1.5 text-xs"
              >
                {days.map((da) => (
                  <option key={da} value={da}>{da}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <span className="text-[10px] text-gray-400 uppercase">时</span>
              <select
                value={h}
                onChange={(e) => {
                  const v = e.target.value;
                  setParts(iy || undefined, im || undefined, id || undefined, v ? parseInt(v, 10) : undefined, imin || undefined);
                }}
                className="w-full mt-0.5 rounded-lg border border-gray-200 px-2 py-1.5 text-xs"
              >
                {hours.map((hr) => (
                  <option key={hr} value={hr}>{String(hr).padStart(2, '0')}</option>
                ))}
              </select>
            </div>
            <div>
              <span className="text-[10px] text-gray-400 uppercase">分</span>
              <select
                value={min}
                onChange={(e) => {
                  const v = e.target.value;
                  setParts(iy || undefined, im || undefined, id || undefined, ih || undefined, v ? parseInt(v, 10) : undefined);
                }}
                className="w-full mt-0.5 rounded-lg border border-gray-200 px-2 py-1.5 text-xs"
              >
                {minutes.map((mn) => (
                  <option key={mn} value={mn}>{String(mn).padStart(2, '0')}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="flex items-center gap-2 pt-1 border-t border-gray-100">
            <button type="button" onClick={setNow} className="flex-1 py-1.5 rounded-lg bg-blue-50 text-blue-600 text-xs font-medium hover:bg-blue-100">
              此刻
            </button>
            <button type="button" onClick={() => { onChange(''); setOpen(false); }} className="flex-1 py-1.5 rounded-lg bg-gray-100 text-gray-600 text-xs font-medium hover:bg-gray-200">
              清空
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

/** 带鉴权的图片：/api/static/ 用 query token 直链，其它 /api 用 fetch+blob */
const AuthImage: React.FC<{ url: string; alt: string; className?: string; loading?: 'lazy' | 'eager' }> = ({ url, alt, className, loading }) => {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [err, setErr] = useState(false);
  const blobRef = useRef<string | null>(null);
  const isStaticUrl = url && url.includes('/api/static/');
  const needAuth = !isStaticUrl && (url.startsWith('/') || url.includes('/api/'));
  useEffect(() => {
    if (!url || !needAuth) return;
    let cancelled = false;
    fetchWithAuthAsBlobUrl(url)
      .then((u) => {
        if (cancelled) { URL.revokeObjectURL(u); return; }
        if (blobRef.current) URL.revokeObjectURL(blobRef.current);
        blobRef.current = u;
        setBlobUrl(u);
        setErr(false);
      })
      .catch(() => { if (!cancelled) setErr(true); });
    return () => {
      cancelled = true;
      if (blobRef.current) {
        URL.revokeObjectURL(blobRef.current);
        blobRef.current = null;
      }
      setBlobUrl(null);
    };
  }, [url, needAuth]);
  if (err) return <div className={className} style={{ background: '#f3f4f6' }} />;
  const src = isStaticUrl ? appendTokenToStaticUrl(url) : (needAuth ? blobUrl : url);
  if (!src) return <div className={className} style={{ background: '#f3f4f6' }} />;
  return <img src={src} alt={alt} className={className} loading={loading} />;
};

export const AiAnalysisRecords: React.FC = () => {
  const { t } = useAppContext();
  const [records, setRecords] = useState<AiAnalysisRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [playingId, setPlayingId] = useState<string | null>(null);
  const [lightboxIdx, setLightboxIdx] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [eventType, setEventType] = useState('');
  const [startTime, setStartTime] = useState('');
  const [endTime, setEndTime] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [deleting, setDeleting] = useState(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const loadRecords = useCallback(async (opts?: { page?: number }) => {
    setLoading(true);
    setError(null);
    const p = opts?.page ?? page;
    try {
      const params: { limit: number; offset: number; eventType?: string; startTime?: string; endTime?: string } = {
        limit: pageSize,
        offset: (p - 1) * pageSize,
      };
      if (eventType) params.eventType = eventType;
      if (startTime) params.startTime = startTime.trim().replace('T', ' ') || undefined;
      if (endTime) params.endTime = endTime.trim().replace('T', ' ') || undefined;
      const res = await aiAnalysisService.getRecords(params);
      setRecords(res.data || []);
      setTotal(res.total ?? 0);
      if (opts?.page != null) setPage(opts.page);
    } catch (e: any) {
      setRecords([]);
      setTotal(0);
      setError(e?.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, eventType, startTime, endTime]);

  useEffect(() => { loadRecords(); }, [loadRecords]);

  const handleQuery = () => {
    setPage(1);
    loadRecords({ page: 1 });
  };

  const handleDeleteOne = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (deleting) return;
    setDeleting(true);
    try {
      await aiAnalysisService.deleteRecord(id);
      setSelectedIds((s) => { const n = new Set(s); n.delete(id); return n; });
      await loadRecords();
    } catch (err: any) {
      setError(err?.message || '删除失败');
    } finally {
      setDeleting(false);
    }
  };

  const handleBatchDelete = async () => {
    if (selectedIds.size === 0 || deleting) return;
    setDeleting(true);
    try {
      const ids = Array.from(selectedIds);
      await aiAnalysisService.deleteRecords(ids);
      setSelectedIds(new Set());
      await loadRecords();
    } catch (err: any) {
      setError(err?.message || '批量删除失败');
    } finally {
      setDeleting(false);
    }
  };

  const toggleSelect = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedIds((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id); else n.add(id);
      return n;
    });
  };

  const toggleSelectAll = () => {
    if (selectedIds.size === records.length) setSelectedIds(new Set());
    else setSelectedIds(new Set(records.map((r) => r.id)));
  };

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  const playVoice = async (id: string, url: string) => {
    if (!url) return;
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current = null;
      if (playingId === id) { setPlayingId(null); return; }
    }
    try {
      const isStaticUrl = url.includes('/api/static/');
      const needAuth = !isStaticUrl && (url.startsWith('/') || url.includes('/api/'));
      const src = isStaticUrl ? appendTokenToStaticUrl(url) : (needAuth ? await fetchWithAuthAsBlobUrl(url) : url);
      const audio = new Audio(src);
      audioRef.current = audio;
      setPlayingId(id);
      const revoke = needAuth && src && !isStaticUrl;
      audio.play().catch(() => { audioRef.current = null; setPlayingId(null); if (revoke) URL.revokeObjectURL(src); });
      audio.onended = () => { audioRef.current = null; setPlayingId(null); if (revoke) URL.revokeObjectURL(src); };
      audio.onerror = () => { audioRef.current = null; setPlayingId(null); if (revoke) URL.revokeObjectURL(src); };
    } catch {
      setPlayingId(null);
    }
  };

  const verifyLabel = (r: AiAnalysisRecord) => {
    if (r.verifyResult === 'pass') return t('ai_verify_pass') || '通过';
    if (r.verifyResult === 'fail') return t('ai_verify_fail') || '不通过';
    return t('ai_verify_skip') || '跳过';
  };

  const VerifyIcon = ({ result }: { result: string }) => {
    if (result === 'pass') return <ShieldCheck size={14} />;
    if (result === 'fail') return <ShieldX size={14} />;
    return <Shield size={14} />;
  };

  const verifyBadgeClass = (r: AiAnalysisRecord) => {
    if (r.verifyResult === 'pass') return 'bg-emerald-500 text-white';
    if (r.verifyResult === 'fail') return 'bg-red-500 text-white';
    return 'bg-gray-500 text-white';
  };

  const openLightbox = useCallback((idx: number) => setLightboxIdx(idx), []);
  const closeLightbox = useCallback(() => setLightboxIdx(null), []);
  const goPrev = useCallback(() => setLightboxIdx(i => i !== null ? (i - 1 + records.length) % records.length : null), [records.length]);
  const goNext = useCallback(() => setLightboxIdx(i => i !== null ? (i + 1) % records.length : null), [records.length]);

  useEffect(() => {
    if (lightboxIdx === null) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeLightbox();
      if (e.key === 'ArrowLeft') goPrev();
      if (e.key === 'ArrowRight') goNext();
    };
    window.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = ''; };
  }, [lightboxIdx, closeLightbox, goPrev, goNext]);

  const current = lightboxIdx !== null ? records[lightboxIdx] : null;

  return (
    <div className="p-6 max-w-[1600px] mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-5">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center shadow-lg shadow-blue-200/50">
            <Brain size={20} className="text-white" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-gray-800">{t('ai_analysis_records')}</h2>
            <p className="text-xs text-gray-400">共 {total} 条记录</p>
          </div>
        </div>
        <button
          onClick={() => loadRecords()}
          disabled={loading}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl border border-gray-200 bg-white text-gray-600 text-sm font-medium hover:bg-gray-50 hover:border-gray-300 disabled:opacity-50 transition-all active:scale-95"
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          {t('refresh')}
        </button>
      </div>

      {/* 筛选：自定义事件类型下拉 + 自定义日期时间选择器 */}
      <div className="mb-4 p-4 rounded-xl border border-gray-100 bg-white flex flex-wrap items-end gap-4">
        <CustomEventTypeSelect
          label="事件类型"
          value={eventType}
          options={EVENT_TYPE_OPTIONS}
          onChange={setEventType}
        />
        <CustomDateTimeField
          label="开始时间"
          value={startTime}
          onChange={setStartTime}
          placeholder="选择开始时间"
        />
        <CustomDateTimeField
          label="结束时间"
          value={endTime}
          onChange={setEndTime}
          placeholder="选择结束时间"
        />
        <div className="flex items-end">
          <button
            type="button"
            onClick={handleQuery}
            disabled={loading}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 disabled:opacity-50 shadow-sm hover:shadow transition-all active:scale-[0.98]"
          >
            <Search size={16} />
            查询
          </button>
        </div>
      </div>

      {/* 批量操作栏：已选数量 + 全选/取消/批量删除 */}
      {selectedIds.size > 0 && (
        <div className="mb-4 flex items-center justify-between rounded-xl border border-amber-200/80 bg-gradient-to-r from-amber-50 to-orange-50/80 px-4 py-3 shadow-sm">
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-200/60 text-amber-900 text-sm font-medium">
              <CheckSquare size={14} />
              已选 {selectedIds.size} 条
            </span>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={toggleSelectAll}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium text-amber-800 bg-white/80 border border-amber-200/80 hover:bg-amber-100/80 hover:border-amber-300 transition-colors"
            >
              {selectedIds.size === records.length ? <Square size={14} /> : <CheckSquare size={14} />}
              {selectedIds.size === records.length ? '取消全选' : '全选当页'}
            </button>
            <button
              type="button"
              onClick={() => setSelectedIds(new Set())}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium text-amber-800 bg-white/80 border border-amber-200/80 hover:bg-amber-100/80 hover:border-amber-300 transition-colors"
            >
              <X size={14} />
              取消选择
            </button>
            <button
              type="button"
              onClick={handleBatchDelete}
              disabled={deleting}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-red-500 text-white text-sm font-medium hover:bg-red-600 disabled:opacity-50 shadow-sm hover:shadow transition-all active:scale-[0.98]"
            >
              <Trash2 size={14} />
              批量删除
            </button>
          </div>
        </div>
      )}

      {error && (
        <div className="mb-4 rounded-xl bg-red-50 border border-red-100 text-red-600 px-4 py-3 text-sm flex items-center gap-2">
          <AlertTriangle size={16} />
          {error}
        </div>
      )}

      {loading ? (
        <div className="flex flex-col items-center justify-center py-24 text-gray-400">
          <div className="w-12 h-12 border-3 border-blue-200 border-t-blue-500 rounded-full animate-spin mb-4" />
          <span className="text-sm">{t('loading')}</span>
        </div>
      ) : records.length === 0 ? (
        <div className="rounded-2xl border-2 border-dashed border-gray-200 bg-gradient-to-b from-gray-50/80 to-white py-20 text-center">
          <div className="w-16 h-16 rounded-2xl bg-gray-100 flex items-center justify-center mx-auto mb-4">
            <Brain size={32} className="text-gray-300" />
          </div>
          <p className="text-gray-500 font-medium">{t('no_ai_analysis_records') || '暂无 AI 分析记录'}</p>
          <p className="text-sm text-gray-400 mt-1.5 max-w-md mx-auto">{t('ai_analysis_records_hint')}</p>
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3">
          {records.map((r, idx) => (
            <div
              key={r.id}
              className={`group relative rounded-2xl border bg-white overflow-hidden hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 cursor-pointer ${
                selectedIds.has(r.id) ? 'border-blue-400 ring-2 ring-blue-200' : 'border-gray-100 hover:border-gray-200'
              }`}
              onClick={() => openLightbox(idx)}
            >
              {/* 图片为主 */}
              <div className="aspect-[4/3] bg-gradient-to-br from-gray-50 to-gray-100 relative overflow-hidden">
                {/* 左上角自定义复选框 */}
                <button
                  type="button"
                  onClick={(e) => toggleSelect(r.id, e)}
                  className="absolute top-2 left-2 z-10 w-8 h-8 rounded-lg flex items-center justify-center shadow-md border border-white/80 transition-all hover:scale-105 active:scale-95 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:ring-offset-1"
                  style={{ backgroundColor: selectedIds.has(r.id) ? 'rgb(59 130 246)' : 'rgba(255,255,255,0.92)' }}
                  title={selectedIds.has(r.id) ? '取消选择' : '选择'}
                >
                  {selectedIds.has(r.id) ? (
                    <Check size={16} className="text-white" strokeWidth={3} />
                  ) : (
                    <Square size={16} className="text-gray-400" strokeWidth={2} />
                  )}
                </button>
                {/* 右上角删除按钮 */}
                <div className="absolute top-2 right-2 z-10 flex items-center gap-1">
                  <button
                    type="button"
                    onClick={(e) => handleDeleteOne(r.id, e)}
                    disabled={deleting}
                    className="p-2 rounded-lg bg-black/35 hover:bg-red-500 text-white backdrop-blur-sm transition-all disabled:opacity-50 shadow hover:shadow-md focus:outline-none focus:ring-2 focus:ring-red-400/80"
                    title="删除"
                  >
                    <Trash2 size={14} strokeWidth={2.5} />
                  </button>
                </div>
                {r.imageUrl ? (
                  <AuthImage
                    url={r.imageUrl}
                    alt={r.eventTitle}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                    loading="lazy"
                  />
                ) : (
                  <div className="w-full h-full flex flex-col items-center justify-center text-gray-300">
                    <ImageIcon size={36} strokeWidth={1.5} />
                  </div>
                )}
                {/* 核验结果角标（在删除按钮左侧） */}
                <span className={`absolute top-2 right-12 inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold shadow-sm backdrop-blur-sm ${verifyBadgeClass(r)}`}>
                  <VerifyIcon result={r.verifyResult} />
                  {verifyLabel(r)}
                </span>
                {/* 底部渐变：时间 + 放大提示 */}
                <div className="absolute bottom-0 inset-x-0 bg-gradient-to-t from-black/60 to-transparent px-2.5 pb-2 pt-8 flex items-end justify-between">
                  <span className="inline-flex items-center gap-1 text-[10px] text-white/90">
                    <Clock size={10} />
                    {r.time}
                  </span>
                  <ZoomIn size={14} className="text-white/70 opacity-0 group-hover:opacity-100 transition-opacity" />
                </div>
              </div>
              {/* 简要信息 + 语音 */}
              <div className="px-2.5 py-2 space-y-1.5">
                <p className="text-xs font-semibold text-gray-800 truncate" title={r.eventTitle}>{r.eventTitle}</p>
                <p className="text-[10px] text-gray-400 truncate">{r.eventName}</p>
                {r.voiceUrl && (
                  <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); playVoice(r.id, r.voiceUrl!); }}
                    className={`w-full flex items-center justify-center gap-1 py-1.5 rounded-lg text-[11px] font-medium transition-all active:scale-[0.97] ${
                      playingId === r.id
                        ? 'bg-blue-500 text-white'
                        : 'bg-blue-50 text-blue-600 hover:bg-blue-100'
                    }`}
                  >
                    {playingId === r.id ? <VolumeX size={12} /> : <Volume2 size={12} />}
                    {playingId === r.id ? '停止' : (t('voice_play') || '播放')}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 分页 */}
      {!loading && total > 0 && (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-gray-100 bg-white px-4 py-3">
          <div className="flex items-center gap-3">
            <span className="text-sm text-gray-500">每页</span>
            <select
              value={pageSize}
              onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1); }}
              className="rounded-lg border border-gray-200 px-2 py-1 text-sm"
            >
              {PAGE_SIZE_OPTIONS.map((n) => (
                <option key={n} value={n}>{n} 条</option>
              ))}
            </select>
            <span className="text-sm text-gray-500">共 {total} 条</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page <= 1}
              className="px-3 py-1.5 rounded-lg border border-gray-200 text-sm disabled:opacity-50 hover:bg-gray-50"
            >
              上一页
            </button>
            <span className="text-sm text-gray-600">
              {page} / {totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={page >= totalPages}
              className="px-3 py-1.5 rounded-lg border border-gray-200 text-sm disabled:opacity-50 hover:bg-gray-50"
            >
              下一页
            </button>
          </div>
        </div>
      )}

      {/* 全屏 Lightbox */}
      {current && createPortal(
        <div className="fixed inset-0 z-[300] flex" onClick={closeLightbox}>
          {/* 遮罩 */}
          <div className="absolute inset-0 bg-black/80 backdrop-blur-sm" />

          {/* 左右切换 */}
          <button
            onClick={(e) => { e.stopPropagation(); goPrev(); }}
            className="absolute left-4 top-1/2 -translate-y-1/2 z-20 w-10 h-10 rounded-full bg-white/10 hover:bg-white/20 backdrop-blur flex items-center justify-center text-white transition-colors"
          >
            <ChevronLeft size={24} />
          </button>
          <button
            onClick={(e) => { e.stopPropagation(); goNext(); }}
            className="absolute right-[340px] top-1/2 -translate-y-1/2 z-20 w-10 h-10 rounded-full bg-white/10 hover:bg-white/20 backdrop-blur flex items-center justify-center text-white transition-colors"
          >
            <ChevronRight size={24} />
          </button>

          {/* 关闭按钮 */}
          <button
            onClick={closeLightbox}
            className="absolute top-4 left-4 z-20 w-10 h-10 rounded-full bg-white/10 hover:bg-white/20 backdrop-blur flex items-center justify-center text-white transition-colors"
          >
            <X size={20} />
          </button>
          {/* 删除当前记录（详情栏内） */}
          {current && (
            <button
              onClick={(e) => { e.stopPropagation(); handleDeleteOne(current.id, e); closeLightbox(); }}
              disabled={deleting}
              className="absolute top-4 right-[340px] z-20 flex items-center gap-1.5 px-3 py-2 rounded-lg bg-red-500/90 hover:bg-red-600 text-white text-sm font-medium disabled:opacity-50"
            >
              <Trash2 size={16} />
              删除
            </button>
          )}

          {/* 图片区（左侧） */}
          <div
            className="flex-1 flex items-center justify-center relative z-10 p-8"
            onClick={(e) => e.stopPropagation()}
          >
            {current.imageUrl ? (
              <AuthImage
                url={current.imageUrl}
                alt={current.eventTitle}
                className="max-w-full max-h-full object-contain rounded-xl shadow-2xl"
              />
            ) : (
              <div className="w-96 h-72 rounded-xl bg-gray-800 flex flex-col items-center justify-center text-gray-500">
                <ImageIcon size={64} strokeWidth={1} />
                <span className="text-sm mt-2">暂无图片</span>
              </div>
            )}
            {/* 计数器 */}
            <div className="absolute bottom-6 left-1/2 -translate-x-1/2 bg-black/50 backdrop-blur-sm text-white/80 text-xs px-4 py-1.5 rounded-full">
              {(lightboxIdx ?? 0) + 1} / {records.length}
            </div>
          </div>

          {/* 详情面板（右侧） */}
          <div
            className="w-[320px] flex-shrink-0 bg-white/95 backdrop-blur-xl h-full overflow-y-auto z-10 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="p-5 space-y-5">
              {/* 核验结果 */}
              <div className="flex items-center gap-3">
                <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${
                  current.verifyResult === 'pass' ? 'bg-emerald-100 text-emerald-600'
                  : current.verifyResult === 'fail' ? 'bg-red-100 text-red-600'
                  : 'bg-gray-100 text-gray-600'
                }`}>
                  <VerifyIcon result={current.verifyResult} />
                </div>
                <div>
                  <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold ${verifyBadgeClass(current)}`}>
                    {verifyLabel(current)}
                  </span>
                  <p className="text-[11px] text-gray-400 mt-1 flex items-center gap-1">
                    <Clock size={10} />
                    {current.time}
                  </p>
                </div>
              </div>

              <div className="h-px bg-gray-200" />

              {/* 事件信息 */}
              <div className="space-y-3">
                <div>
                  <p className="text-[10px] text-gray-400 uppercase tracking-wider mb-1">事件标题</p>
                  <p className="text-sm font-semibold text-gray-800">{current.eventTitle}</p>
                </div>
                <div>
                  <p className="text-[10px] text-gray-400 uppercase tracking-wider mb-1">事件名称</p>
                  <p className="text-sm text-gray-600">{current.eventName}</p>
                </div>
              </div>

              {current.verifyReason && (
                <>
                  <div className="h-px bg-gray-200" />
                  <div>
                    <p className="text-[10px] text-gray-400 uppercase tracking-wider mb-1.5">核验说明</p>
                    <p className="text-sm text-gray-600 leading-relaxed">{current.verifyReason}</p>
                  </div>
                </>
              )}

              {current.alertText && (
                <>
                  <div className="h-px bg-gray-200" />
                  <div>
                    <p className="text-[10px] text-gray-400 uppercase tracking-wider mb-1.5 flex items-center gap-1">
                      <Megaphone size={10} />
                      AI 警示语
                    </p>
                    <div className="rounded-xl bg-amber-50 border border-amber-200 px-3 py-2.5">
                      <p className="text-sm text-amber-800 leading-relaxed">{current.alertText}</p>
                    </div>
                  </div>
                </>
              )}

              {current.voiceUrl && (
                <>
                  <div className="h-px bg-gray-200" />
                  <div>
                    <p className="text-[10px] text-gray-400 uppercase tracking-wider mb-1.5 flex items-center gap-1">
                      <Volume2 size={10} />
                      语音播报
                    </p>
                    <button
                      type="button"
                      onClick={() => playVoice(current.id, current.voiceUrl!)}
                      className={`w-full flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-medium transition-all active:scale-[0.97] ${
                        playingId === current.id
                          ? 'bg-blue-600 text-white shadow-lg shadow-blue-200'
                          : 'bg-blue-50 text-blue-600 hover:bg-blue-100 border border-blue-100'
                      }`}
                    >
                      {playingId === current.id ? (
                        <>
                          <VolumeX size={18} />
                          停止播放
                        </>
                      ) : (
                        <>
                          <Volume2 size={18} />
                          {t('voice_play') || '播放语音'}
                        </>
                      )}
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
};
