import React, { useState, useEffect, useRef } from 'react';
import { useAppContext } from '../contexts/AppContext';
import { aiAnalysisService, AiAnalysisRecord } from '../src/api/services';
import { Brain, Image as ImageIcon, Volume2, VolumeX, RefreshCw, Clock, Shield, ShieldCheck, ShieldX, AlertTriangle, Megaphone } from 'lucide-react';

export const AiAnalysisRecords: React.FC = () => {
  const { t } = useAppContext();
  const [records, setRecords] = useState<AiAnalysisRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [playingId, setPlayingId] = useState<string | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const loadRecords = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await aiAnalysisService.getRecords({ limit: 100 });
      setRecords(res.data || []);
    } catch (e: any) {
      setRecords([]);
      setError(e?.message || '加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRecords();
  }, []);

  const playVoice = (id: string, url: string) => {
    if (!url) return;
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current = null;
      if (playingId === id) {
        setPlayingId(null);
        return;
      }
    }
    const audio = new Audio(url);
    audioRef.current = audio;
    setPlayingId(id);
    audio.play().catch(() => setPlayingId(null));
    audio.onended = () => { audioRef.current = null; setPlayingId(null); };
    audio.onerror = () => { audioRef.current = null; setPlayingId(null); };
  };

  const verifyLabel = (r: AiAnalysisRecord) => {
    if (r.verifyResult === 'pass') return t('ai_verify_pass') || '通过';
    if (r.verifyResult === 'fail') return t('ai_verify_fail') || '不通过';
    return t('ai_verify_skip') || '跳过';
  };

  const VerifyIcon = ({ result }: { result: string }) => {
    if (result === 'pass') return <ShieldCheck size={12} />;
    if (result === 'fail') return <ShieldX size={12} />;
    return <Shield size={12} />;
  };

  const verifyBadgeClass = (r: AiAnalysisRecord) => {
    if (r.verifyResult === 'pass') return 'bg-emerald-500/90 text-white shadow-emerald-200';
    if (r.verifyResult === 'fail') return 'bg-red-500/90 text-white shadow-red-200';
    return 'bg-gray-500/80 text-white shadow-gray-200';
  };

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
            <p className="text-xs text-gray-400">{records.length > 0 ? `${records.length} 条记录` : ''}</p>
          </div>
        </div>
        <button
          onClick={loadRecords}
          disabled={loading}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl border border-gray-200 bg-white text-gray-600 text-sm font-medium hover:bg-gray-50 hover:border-gray-300 disabled:opacity-50 transition-all active:scale-95"
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          {t('refresh')}
        </button>
      </div>

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
          <p className="text-sm text-gray-400 mt-1.5 max-w-md mx-auto">{t('ai_analysis_records_hint') || '工作流中 AI 核验节点的执行结果将在此展示'}</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-4">
          {records.map((r) => (
            <div
              key={r.id}
              className="group rounded-2xl border border-gray-100 bg-white shadow-sm overflow-hidden hover:shadow-lg hover:border-gray-200 hover:-translate-y-0.5 transition-all duration-200"
            >
              {/* Image area */}
              <div className="aspect-[4/3] bg-gradient-to-br from-gray-50 to-gray-100 relative overflow-hidden">
                {r.imageUrl ? (
                  <img
                    src={r.imageUrl}
                    alt={r.eventTitle}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                    loading="lazy"
                  />
                ) : (
                  <div className="w-full h-full flex flex-col items-center justify-center text-gray-300">
                    <ImageIcon size={36} strokeWidth={1.5} />
                    <span className="text-[10px] mt-1.5">暂无图片</span>
                  </div>
                )}
                {/* Verify badge */}
                <span className={`absolute top-2 right-2 inline-flex items-center gap-1 px-2 py-1 rounded-lg text-[10px] font-semibold shadow-sm backdrop-blur-sm ${verifyBadgeClass(r)}`}>
                  <VerifyIcon result={r.verifyResult} />
                  {verifyLabel(r)}
                </span>
                {/* Time overlay */}
                <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/50 to-transparent px-2.5 pb-2 pt-6">
                  <span className="inline-flex items-center gap-1 text-[10px] text-white/90">
                    <Clock size={10} />
                    {r.time}
                  </span>
                </div>
              </div>

              {/* Content area */}
              <div className="p-3 space-y-2">
                <div>
                  <p className="text-[13px] font-semibold text-gray-800 truncate" title={r.eventTitle}>
                    {r.eventTitle}
                  </p>
                  <p className="text-[11px] text-gray-400 truncate mt-0.5" title={r.eventName}>
                    {r.eventName}
                  </p>
                </div>

                {r.verifyReason && (
                  <p className="text-[11px] text-gray-500 leading-relaxed line-clamp-2 border-l-2 border-gray-200 pl-2" title={r.verifyReason}>
                    {r.verifyReason}
                  </p>
                )}

                {r.alertText && (
                  <div className="flex items-start gap-1.5 rounded-lg bg-amber-50/80 border border-amber-100 px-2 py-1.5">
                    <Megaphone size={12} className="text-amber-500 flex-shrink-0 mt-0.5" />
                    <p className="text-[11px] text-amber-700 leading-relaxed line-clamp-2" title={r.alertText}>
                      {r.alertText}
                    </p>
                  </div>
                )}

                {r.voiceUrl && (
                  <button
                    type="button"
                    onClick={() => playVoice(r.id, r.voiceUrl!)}
                    className={`w-full flex items-center justify-center gap-1.5 py-2 rounded-xl text-xs font-medium transition-all active:scale-[0.97] ${
                      playingId === r.id
                        ? 'bg-blue-500 text-white shadow-sm shadow-blue-200'
                        : 'bg-blue-50 text-blue-600 hover:bg-blue-100'
                    }`}
                  >
                    {playingId === r.id ? (
                      <>
                        <VolumeX size={14} />
                        停止播放
                      </>
                    ) : (
                      <>
                        <Volume2 size={14} />
                        {t('voice_play') || '播放语音'}
                      </>
                    )}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
