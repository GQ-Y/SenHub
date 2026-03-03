import React, { useState, useEffect, useRef, useCallback } from 'react';
import { X, ChevronRight, Crosshair, Check, Save, Camera, Image, ChevronUp, ChevronDown, ChevronLeft as ArrowLeft, ChevronRight as ArrowRight, Play, Square } from 'lucide-react';
import mpegts from 'mpegts.js';
import { assemblyService, deviceService, radarService } from '../src/api/services';
import { API_CONFIG } from '../src/api/config';
import { PointCloudRenderer } from './PointCloudRenderer';
import { useModal } from '../hooks/useModal';
import { Modal } from './Modal';

export interface CalibrationPoint {
  radarX: number;
  radarY: number;
  radarZ: number;
  cameraPan: number;
  cameraTilt: number;
}

interface RadarCalibrationWizardProps {
  assemblyId: string;
  onClose: () => void;
}

type Step = 1 | 2 | 3;
type CameraViewMode = 'stream' | 'snapshot';

/** PTZ 方向按钮组件：长按控制、松开停止，支持鼠标与触摸 */
const PtzButton: React.FC<{
  label: string;
  icon: React.ReactNode;
  onStart: () => void;
  onStop: () => void;
}> = ({ label, icon, onStart, onStop }) => {
  const activeRef = useRef(false);

  const begin = useCallback(() => {
    if (activeRef.current) return;
    activeRef.current = true;
    onStart();
  }, [onStart]);

  const end = useCallback(() => {
    if (!activeRef.current) return;
    activeRef.current = false;
    onStop();
  }, [onStop]);

  return (
    <button
      onMouseDown={begin}
      onMouseUp={end}
      onMouseLeave={end}
      onTouchStart={(e) => { e.preventDefault(); begin(); }}
      onTouchEnd={(e) => { e.preventDefault(); end(); }}
      onTouchCancel={end}
      className="flex items-center justify-center gap-1 px-3 py-2 bg-gray-100 rounded-lg text-sm font-medium
                 hover:bg-gray-200 active:bg-blue-100 active:text-blue-700 select-none touch-none
                 transition-colors cursor-pointer min-w-[52px]"
      title={label}
    >
      {icon}
      <span className="hidden sm:inline">{label}</span>
    </button>
  );
};

export const RadarCalibrationWizard: React.FC<RadarCalibrationWizardProps> = ({ assemblyId, onClose }) => {
  const modal = useModal();
  const [step, setStep] = useState<Step>(1);
  const [context, setContext] = useState<{
    radarDeviceId: string;
    radarDeviceName: string;
    cameraDeviceId: string;
    cameraDeviceName: string;
    zones: { zoneId: string; zoneName: string; cameraDeviceId: string; cameraChannel: number }[];
  } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedZone, setSelectedZone] = useState<{ zoneId: string; zoneName: string; cameraDeviceId: string; cameraChannel: number } | null>(null);
  const [zoneFullData, setZoneFullData] = useState<any>(null);
  const [backgroundPoints, setBackgroundPoints] = useState<{ x: number; y: number; z: number; r?: number }[]>([]);
  const [points, setPoints] = useState<CalibrationPoint[]>([]);
  const [pointCloudPoints, setPointCloudPoints] = useState<{ x: number; y: number; z: number; r?: number; zoneId?: string }[]>([]);
  const [wsConnected, setWsConnected] = useState(false);
  const [computeResult, setComputeResult] = useState<{ transform: any; error: any } | null>(null);
  const [saving, setSaving] = useState(false);
  const [verifySnapshotUrl, setVerifySnapshotUrl] = useState<string | null>(null);
  const [cameraViewMode, setCameraViewMode] = useState<CameraViewMode>('stream');
  const [cameraFlvUrl, setCameraFlvUrl] = useState<string | null>(null);
  const [cameraSnapshotUrl, setCameraSnapshotUrl] = useState<string | null>(null);
  const [snapshotActive, setSnapshotActive] = useState(false);
  const [isLoadingCamera, setIsLoadingCamera] = useState(false);
  const [streamFailed, setStreamFailed] = useState(false);
  const wsRef = useRef<ReturnType<typeof radarService.connectWebSocket> | null>(null);
  const cameraVideoRef = useRef<HTMLVideoElement>(null);
  const cameraFlvPlayerRef = useRef<ReturnType<typeof mpegts.createPlayer> | null>(null);
  const snapshotIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const pointCloudBufferRef = useRef<{ x: number; y: number; z: number; r?: number; zoneId?: string; timestamp: number }[]>([]);
  const accumulationMs = 2000;
  const renderTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const bufferDirtyRef = useRef(false);

  // 加载标定上下文
  useEffect(() => {
    let isCleaningUp = false;
    assemblyService.getCalibrationContext(assemblyId).then((res) => {
      if (isCleaningUp) return;
      if (res.data) {
        setContext(res.data as any);
        if (res.data.zones?.length === 1) setSelectedZone(res.data.zones[0]);
      } else {
        setError('获取标定上下文失败');
      }
    }).catch((e: any) => {
      if (!isCleaningUp) setError(e?.message || '获取标定上下文失败');
    }).finally(() => {
      if (!isCleaningUp) setLoading(false);
    });
    return () => { isCleaningUp = true; };
  }, [assemblyId]);

  // 选择防区后加载完整防区数据 + 背景点云（用于静态展示）
  useEffect(() => {
    if (!selectedZone || !context?.radarDeviceId) {
      setZoneFullData(null);
      setBackgroundPoints([]);
      return;
    }
    const deviceId = context.radarDeviceId;
    radarService.getZones(deviceId).then(async (res) => {
      const zones = res.data;
      if (!Array.isArray(zones)) return;
      const zone = zones.find((z: any) => z.zoneId === selectedZone.zoneId);
      if (!zone) return;
      setZoneFullData(zone);

      if (zone.backgroundId) {
        try {
          const bgRes = await radarService.getBackgroundPoints(deviceId, zone.backgroundId);
          const pts = bgRes.data?.points || bgRes.data;
          if (Array.isArray(pts) && pts.length > 0) {
            setBackgroundPoints(pts.map((p: any) => ({ x: p.x ?? 0, y: p.y ?? 0, z: p.z ?? 0, r: p.r })));
          }
        } catch (_) {}
      }
    }).catch(() => {});
  }, [selectedZone, context?.radarDeviceId]);

  // 标定模式 PTZ 抑制：进入 step 2 时禁止球机跟随，退出时恢复
  const cameraIdForSuppress = selectedZone?.cameraDeviceId || context?.cameraDeviceId;
  useEffect(() => {
    if (step !== 2 || !cameraIdForSuppress) return;
    radarService.setPtzSuppress(cameraIdForSuppress, true).catch(() => {});
    return () => {
      radarService.setPtzSuppress(cameraIdForSuppress, false).catch(() => {});
    };
  }, [step, cameraIdForSuppress]);

  // WebSocket 点云连接 — 侵入检测由服务端完成，前端只渲染服务端标记的侵入点
  // 数据累积在 ref 中（每帧），React 状态更新节流到 ~15fps 避免淹没渲染管线
  useEffect(() => {
    if (step !== 2 || !context?.radarDeviceId) return;
    const deviceId = context.radarDeviceId;
    const curZoneId = selectedZone?.zoneId;
    let isCleaningUp = false;

    radarService.setDetectionEnabled(deviceId, true).catch(() => {});

    // 定时器：每 66ms (~15fps) 将 ref 缓冲刷新到 React 状态
    renderTimerRef.current = setInterval(() => {
      if (!bufferDirtyRef.current) return;
      bufferDirtyRef.current = false;
      const cutoff = Date.now() - accumulationMs;
      const buf = pointCloudBufferRef.current;
      let start = 0;
      while (start < buf.length && buf[start].timestamp <= cutoff) start++;
      if (start > 0) pointCloudBufferRef.current = buf.slice(start);
      setPointCloudPoints(pointCloudBufferRef.current.map(({ timestamp, ...pt }) => pt));
    }, 66);

    const ws = radarService.connectWebSocket(
      deviceId,
      (data: any) => {
        if (isCleaningUp) return;
        if (data.type === 'pointcloud' && data.points?.length) {
          const now = Date.now();
          for (const p of data.points) {
            if (!p.isIntrusion) continue;
            pointCloudBufferRef.current.push({ x: p.x ?? 0, y: p.y ?? 0, z: p.z ?? 0, r: 255, zoneId: curZoneId, timestamp: now });
          }
          bufferDirtyRef.current = true;
        }
        if (data.type === 'status') setWsConnected(!!data.connected);
      }
    );
    wsRef.current = ws;
    if ((ws as any).readyState === WebSocket.OPEN) setWsConnected(true);
    return () => {
      isCleaningUp = true;
      if (renderTimerRef.current) {
        clearInterval(renderTimerRef.current);
        renderTimerRef.current = null;
      }
      try {
        const w = wsRef.current as any;
        if (w && typeof w.close === 'function') w.close(1000, 'unmount');
      } finally {
        wsRef.current = null;
      }
    };
  }, [step, context?.radarDeviceId, selectedZone?.zoneId]);

  // 球机画面逻辑
  const cameraDeviceIdForView = selectedZone?.cameraDeviceId || context?.cameraDeviceId;
  const cameraChannelForView = selectedZone?.cameraChannel ?? 1;
  const baseUrl = API_CONFIG.BASE_URL.replace(/\/api$/, '') || '';

  // 拉流模式
  useEffect(() => {
    if (step !== 2 || !cameraDeviceIdForView || cameraViewMode !== 'stream') {
      setCameraFlvUrl(null);
      return;
    }
    let cancelled = false;
    setIsLoadingCamera(true);
    setStreamFailed(false);
    setCameraFlvUrl(null);
    deviceService.getLiveUrl(cameraDeviceIdForView).then((res) => {
      if (cancelled) return;
      const url = res.data?.flv_url;
      if (url) {
        setCameraFlvUrl(url);
      } else {
        setStreamFailed(true);
      }
      setIsLoadingCamera(false);
    }).catch(() => {
      if (!cancelled) {
        setStreamFailed(true);
        setIsLoadingCamera(false);
      }
    });
    return () => { cancelled = true; };
  }, [step, cameraDeviceIdForView, cameraViewMode]);

  // 抓图模式：需手动开启后才开始轮询抓图（fetch blob 避免文件被清理后加载失败）
  useEffect(() => {
    if (step !== 2 || !cameraDeviceIdForView || cameraViewMode !== 'snapshot' || !snapshotActive) {
      if (snapshotIntervalRef.current) {
        clearInterval(snapshotIntervalRef.current);
        snapshotIntervalRef.current = null;
      }
      return;
    }
    let cancelled = false;
    const fetchSnapshot = () => {
      deviceService.captureSnapshot(cameraDeviceIdForView, cameraChannelForView).then(async (response) => {
        if (cancelled) return;
        if (response.data?.url) {
          const fullUrl = response.data.url.startsWith('http')
            ? response.data.url
            : `${baseUrl}${response.data.url}`;
          try {
            const res = await fetch(fullUrl);
            if (cancelled) return;
            if (res.ok) {
              const blob = await res.blob();
              if (cancelled) return;
              const blobUrl = URL.createObjectURL(blob);
              setCameraSnapshotUrl((prev) => {
                if (prev?.startsWith('blob:')) URL.revokeObjectURL(prev);
                return blobUrl;
              });
            }
          } catch (_) {
            if (!cancelled) setCameraSnapshotUrl(fullUrl);
          }
        }
      }).catch(() => {});
    };
    fetchSnapshot();
    const t = setInterval(fetchSnapshot, 2000);
    snapshotIntervalRef.current = t;
    return () => {
      cancelled = true;
      if (snapshotIntervalRef.current) {
        clearInterval(snapshotIntervalRef.current);
        snapshotIntervalRef.current = null;
      }
      setCameraSnapshotUrl((prev) => {
        if (prev?.startsWith('blob:')) URL.revokeObjectURL(prev);
        return null;
      });
    };
  }, [step, cameraDeviceIdForView, cameraChannelForView, cameraViewMode, snapshotActive, baseUrl]);

  // FLV 播放器
  useEffect(() => {
    if (!cameraFlvUrl || !cameraVideoRef.current) return;
    if (!mpegts.isSupported()) {
      setCameraFlvUrl(null);
      setStreamFailed(true);
      return;
    }
    const player = mpegts.createPlayer(
      { type: 'flv', url: cameraFlvUrl, isLive: true, hasAudio: false },
      { enableWorker: true, liveBufferLatencyChasing: true, liveBufferLatencyMaxLatency: 1.5, liveBufferLatencyMinRemain: 0.3 }
    );
    player.on(mpegts.Events.ERROR, () => {
      setStreamFailed(true);
    });
    player.attachMediaElement(cameraVideoRef.current);
    player.load();
    player.play();
    cameraFlvPlayerRef.current = player;
    return () => {
      try { player.destroy(); } catch (_) {}
      cameraFlvPlayerRef.current = null;
    };
  }, [cameraFlvUrl]);

  // 拉流失败自动切换到抓图模式
  useEffect(() => {
    if (streamFailed && cameraViewMode === 'stream') {
      setCameraViewMode('snapshot');
    }
  }, [streamFailed, cameraViewMode]);

  const handleConfirmAim = async () => {
    if (!context || !selectedZone) return;
    try {
      const zoneCameraId = selectedZone.cameraDeviceId || context.cameraDeviceId;
      const [targetRes, posRes] = await Promise.all([
        radarService.getCalibrationTarget(context.radarDeviceId, selectedZone.zoneId),
        deviceService.refreshPtzPosition(zoneCameraId).then(() =>
          deviceService.getPtzPosition(zoneCameraId)
        )
      ]);
      const pos = posRes?.data;
      const target = targetRes?.data;
      if (!target || target.radarX == null) {
        modal.showModal({ message: '未检测到目标，请站在防区内再试', type: 'warning' });
        return;
      }
      if (pos?.pan == null || pos?.tilt == null) {
        modal.showModal({ message: '无法获取球机当前角度，请确认球机支持 PTZ 位置查询', type: 'warning' });
        return;
      }
      const newPoint: CalibrationPoint = {
        radarX: target.radarX as number,
        radarY: target.radarY as number,
        radarZ: target.radarZ as number,
        cameraPan: typeof pos.pan === 'number' ? pos.pan : parseFloat(String(pos.pan)),
        cameraTilt: typeof pos.tilt === 'number' ? pos.tilt : parseFloat(String(pos.tilt))
      };
      setPoints((prev) => [...prev, newPoint]);
      modal.showModal({
        message: `采集成功！雷达坐标(${newPoint.radarX.toFixed(2)}, ${newPoint.radarY.toFixed(2)}, ${newPoint.radarZ.toFixed(2)}) → 球机 pan=${newPoint.cameraPan.toFixed(1)}° tilt=${newPoint.cameraTilt.toFixed(1)}°`,
        type: 'success'
      });
    } catch (e: any) {
      modal.showModal({ message: e?.message || '采集失败', type: 'error' });
    }
  };

  const handleCompute = async () => {
    if (!context || !selectedZone || points.length < 1) {
      modal.showModal({ message: '请至少采集 1 个标定点', type: 'warning' });
      return;
    }
    try {
      const res = await radarService.calibrationCompute(context.radarDeviceId, {
        zoneId: selectedZone.zoneId,
        points: points.map((p) => ({
          radarX: p.radarX, radarY: p.radarY, radarZ: p.radarZ,
          cameraPan: p.cameraPan, cameraTilt: p.cameraTilt
        }))
      });
      if (res.data?.transform) {
        setComputeResult({ transform: res.data.transform, error: res.data.error });
        setStep(3);
      } else {
        modal.showModal({ message: '计算失败', type: 'error' });
      }
    } catch (e: any) {
      modal.showModal({ message: e?.message || '计算失败', type: 'error' });
    }
  };

  const handleVerify = async () => {
    if (!context || !selectedZone || !computeResult?.transform) return;
    try {
      const targetRes = await radarService.getCalibrationTarget(context.radarDeviceId, selectedZone.zoneId);
      const target = targetRes?.data;
      if (!target || target.radarX == null) {
        modal.showModal({ message: '当前无目标，请站到防区内再点验证', type: 'warning' });
        return;
      }
      await radarService.calibrationVerify(context.radarDeviceId, {
        zoneId: selectedZone.zoneId,
        transform: computeResult.transform,
        radarX: target.radarX as number,
        radarY: target.radarY as number,
        radarZ: target.radarZ as number
      });
      const verifyCameraId = selectedZone.cameraDeviceId || context.cameraDeviceId;
      const channel = selectedZone.cameraChannel ?? 1;
      setTimeout(async () => {
        try {
          const snapRes = await deviceService.captureSnapshot(verifyCameraId, channel);
          if (snapRes.data?.url) {
            const fullUrl = snapRes.data.url.startsWith('http') ? snapRes.data.url : `${baseUrl}${snapRes.data.url}`;
            try {
              const res = await fetch(fullUrl);
              if (res.ok) {
                const blob = await res.blob();
                const blobUrl = URL.createObjectURL(blob);
                setVerifySnapshotUrl((prev) => {
                  if (prev?.startsWith('blob:')) URL.revokeObjectURL(prev);
                  return blobUrl;
                });
              }
            } catch (_inner) {
              setVerifySnapshotUrl(fullUrl);
            }
          }
        } catch (_) {}
      }, 1500);
      modal.showModal({ message: '已驱动球机转向，请查看左侧画面是否对准目标', type: 'info' });
    } catch (e: any) {
      modal.showModal({ message: e?.message || '验证失败', type: 'error' });
    }
  };

  const handleSave = async () => {
    if (!context || !selectedZone || !computeResult?.transform) return;
    setSaving(true);
    try {
      await radarService.calibrationApply(context.radarDeviceId, {
        zoneId: selectedZone.zoneId,
        transform: computeResult.transform
      });
      modal.showModal({ message: '标定已保存', type: 'success' });
      onClose();
    } catch (e: any) {
      modal.showModal({ message: e?.message || '保存失败', type: 'error' });
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
        <div className="bg-white rounded-xl p-8 shadow-xl">
          <div className="w-10 h-10 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
          <p className="text-gray-600">加载标定上下文...</p>
        </div>
      </div>
    );
  }

  if (error || !context) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
        <div className="bg-white rounded-xl p-8 shadow-xl max-w-md">
          <p className="text-red-600 mb-4">{error || '无标定上下文'}</p>
          <button onClick={onClose} className="px-4 py-2 bg-gray-200 rounded-lg hover:bg-gray-300">关闭</button>
        </div>
      </div>
    );
  }

  const currentZone = selectedZone || context.zones?.[0];
  const cameraDeviceId = currentZone?.cameraDeviceId || context.cameraDeviceId;
  const showingStream = cameraViewMode === 'stream' && cameraFlvUrl && !streamFailed;

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-gray-100">
      {modal.isOpen && (
        <Modal
          isOpen={modal.isOpen}
          onClose={modal.closeModal}
          title=""
          message={modal.modalOptions?.message || ''}
          type={modal.modalOptions?.type || 'info'}
          onConfirm={modal.closeModal}
        />
      )}
      <div className="flex items-center justify-between px-4 py-3 bg-white border-b border-gray-200">
        <h2 className="text-lg font-semibold">雷达-球机坐标系标定</h2>
        <button onClick={onClose} className="p-2 rounded-lg hover:bg-gray-100">
          <X size={20} />
        </button>
      </div>

      <div className="flex-1 flex flex-col min-h-0 p-4">
        {step === 1 && (
          <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100 max-w-lg">
            <h3 className="font-medium text-gray-800 mb-4">选择防区</h3>
            <p className="text-sm text-gray-500 mb-4">选择要标定坐标系的防区（雷达 + 球机已关联）</p>
            <select
              value={selectedZone?.zoneId ?? ''}
              onChange={(e) => {
                const z = context.zones.find((z) => z.zoneId === e.target.value);
                setSelectedZone(z || null);
              }}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 mb-6"
            >
              <option value="">请选择防区</option>
              {context.zones?.map((z) => (
                <option key={z.zoneId} value={z.zoneId}>{z.zoneName || z.zoneId}</option>
              ))}
            </select>
            <button
              onClick={() => setStep(2)}
              disabled={!selectedZone}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
            >
              下一步 <ChevronRight size={18} />
            </button>
          </div>
        )}

        {step === 2 && (
          <>
            <div className="flex gap-4 flex-1 min-h-0 mb-4">
              {/* 左侧：球机画面 */}
              <div className="flex-1 flex flex-col bg-white rounded-xl border border-gray-100 overflow-hidden shadow-sm">
                <div className="px-3 py-2 border-b border-gray-100 font-medium text-gray-700 flex items-center justify-between">
                  <span>球机画面</span>
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => { setCameraViewMode('stream'); setStreamFailed(false); setSnapshotActive(false); }}
                      className={`flex items-center gap-1 px-2 py-1 rounded text-xs transition-colors ${
                        cameraViewMode === 'stream' ? 'bg-blue-100 text-blue-700 font-medium' : 'text-gray-500 hover:bg-gray-100'
                      }`}
                      title="拉流模式"
                    >
                      <Camera size={13} /> 拉流
                    </button>
                    <button
                      onClick={() => { setCameraViewMode('snapshot'); if (cameraViewMode !== 'snapshot') setSnapshotActive(false); }}
                      className={`flex items-center gap-1 px-2 py-1 rounded text-xs transition-colors ${
                        cameraViewMode === 'snapshot' ? 'bg-blue-100 text-blue-700 font-medium' : 'text-gray-500 hover:bg-gray-100'
                      }`}
                      title="抓图模式"
                    >
                      <Image size={13} /> 抓图
                    </button>
                    {cameraViewMode === 'snapshot' && (
                      <button
                        onClick={() => setSnapshotActive((v) => !v)}
                        className={`flex items-center gap-1 px-2 py-1 rounded text-xs transition-colors ml-1 ${
                          snapshotActive ? 'bg-red-100 text-red-700 font-medium' : 'bg-green-100 text-green-700 font-medium'
                        }`}
                        title={snapshotActive ? '停止抓图' : '开始抓图'}
                      >
                        {snapshotActive ? <><Square size={11} /> 停止</> : <><Play size={11} /> 开始</>}
                      </button>
                    )}
                  </div>
                </div>
                <div className="flex-1 min-h-[280px] bg-black flex items-center justify-center text-gray-400 overflow-hidden relative">
                  {showingStream ? (
                    <video
                      ref={cameraVideoRef}
                      className="w-full h-full object-contain"
                      muted
                      playsInline
                    />
                  ) : cameraViewMode === 'snapshot' && cameraSnapshotUrl ? (
                    <img
                      src={cameraSnapshotUrl}
                      alt="球机画面"
                      className="w-full h-full object-contain"
                    />
                  ) : isLoadingCamera ? (
                    <div className="flex flex-col items-center gap-2">
                      <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin" />
                      <span className="text-sm">正在拉流...</span>
                    </div>
                  ) : cameraViewMode === 'stream' && streamFailed ? (
                    <div className="flex flex-col items-center gap-3">
                      <span className="text-sm text-gray-400">拉流失败，已自动切换到抓图模式</span>
                    </div>
                  ) : cameraViewMode === 'snapshot' && !cameraSnapshotUrl ? (
                    <div className="flex flex-col items-center gap-2">
                      {snapshotActive ? (
                        <>
                          <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin" />
                          <span className="text-sm">抓图中...</span>
                        </>
                      ) : (
                        <span className="text-sm text-gray-500">点击右上角「开始」按钮开始抓图</span>
                      )}
                    </div>
                  ) : (
                    <span className="text-sm text-gray-500">球机画面加载中...</span>
                  )}
                </div>
                {/* PTZ 方向控制 */}
                <div className="p-2 border-t border-gray-100 flex items-center justify-center gap-2">
                  <PtzButton
                    label="左"
                    icon={<ArrowLeft size={16} />}
                    onStart={() => deviceService.ptzControl(cameraDeviceId, 'left', 'start', 3)}
                    onStop={() => deviceService.ptzControl(cameraDeviceId, 'left', 'stop', 0)}
                  />
                  <div className="flex flex-col gap-1">
                    <PtzButton
                      label="上"
                      icon={<ChevronUp size={16} />}
                      onStart={() => deviceService.ptzControl(cameraDeviceId, 'up', 'start', 3)}
                      onStop={() => deviceService.ptzControl(cameraDeviceId, 'up', 'stop', 0)}
                    />
                    <PtzButton
                      label="下"
                      icon={<ChevronDown size={16} />}
                      onStart={() => deviceService.ptzControl(cameraDeviceId, 'down', 'start', 3)}
                      onStop={() => deviceService.ptzControl(cameraDeviceId, 'down', 'stop', 0)}
                    />
                  </div>
                  <PtzButton
                    label="右"
                    icon={<ArrowRight size={16} />}
                    onStart={() => deviceService.ptzControl(cameraDeviceId, 'right', 'start', 3)}
                    onStop={() => deviceService.ptzControl(cameraDeviceId, 'right', 'stop', 0)}
                  />
                </div>
              </div>

              {/* 右侧：防区点云 */}
              <div className="flex-1 flex flex-col bg-white rounded-xl border border-gray-100 overflow-hidden shadow-sm">
                <div className="px-3 py-2 border-b border-gray-100 font-medium text-gray-700 flex items-center gap-2">
                  <span>防区点云</span>
                  <span className={`w-2 h-2 rounded-full ${wsConnected ? 'bg-green-500' : 'bg-red-500'}`} />
                  <span className="text-xs text-gray-400 ml-auto">
                    {backgroundPoints.length > 0 ? `背景 ${backgroundPoints.length} 点` : ''}
                    {backgroundPoints.length > 0 && pointCloudPoints.length > 0 ? ' · ' : ''}
                    {pointCloudPoints.length > 0 ? <span className="text-red-400">侵入 {pointCloudPoints.length} 点</span> : (backgroundPoints.length > 0 ? '' : '等待数据...')}
                  </span>
                </div>
                <div className="flex-1 min-h-[280px] bg-gray-900 rounded-b overflow-hidden">
                  {(pointCloudPoints.length > 0 || backgroundPoints.length > 0) ? (
                    <PointCloudRenderer
                      points={pointCloudPoints}
                      pointSize={0.03}
                      backgroundColor="#0a0a0a"
                      showGrid
                      showAxes
                      showRangeRings
                      colorMode="defense"
                      defenseBackgroundPoints={backgroundPoints}
                      shrinkDistance={(zoneFullData?.shrinkDistanceCm || 20) / 100}
                    />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center text-gray-500">
                      {wsConnected
                        ? (backgroundPoints.length > 0 ? '背景已加载，等待侵入目标...' : '正在加载防区数据...')
                        : '点云未连接'}
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* 底部操作栏 */}
            <div className="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
              <div className="flex items-center gap-4 flex-wrap">
                <button
                  onClick={handleConfirmAim}
                  className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700"
                >
                  <Crosshair size={18} /> 确认瞄准
                </button>
                <span className="text-sm text-gray-500">
                  已采集 {points.length} 个点（{points.length === 0 ? '至少 1 个' : points.length === 1 ? '快速模式；推荐 2-3 个以提升精度' : '精确模式'}）
                </span>
                {points.length >= 1 && (
                  <button onClick={handleCompute} className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
                    <Check size={18} /> {points.length === 1 ? '快速标定' : '计算标定'}
                  </button>
                )}
              </div>
              {points.length > 0 && (
                <ul className="mt-3 text-xs text-gray-600 space-y-1 max-h-24 overflow-y-auto">
                  {points.map((p, i) => (
                    <li key={i} className="flex items-center gap-2">
                      <span className="flex-1">
                        点{i + 1}: 雷达({p.radarX.toFixed(2)}, {p.radarY.toFixed(2)}, {p.radarZ.toFixed(2)}) → 球机 pan={p.cameraPan.toFixed(1)}° tilt={p.cameraTilt.toFixed(1)}°
                      </span>
                      <button
                        onClick={() => setPoints((prev) => prev.filter((_, idx) => idx !== i))}
                        className="text-red-400 hover:text-red-600 shrink-0"
                        title="删除此标定点"
                      >
                        <X size={14} />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </>
        )}

        {step === 3 && computeResult && (
          <div className="flex gap-4 flex-1 min-h-0">
            <div className="flex-1 flex flex-col bg-white rounded-xl border border-gray-100 overflow-hidden shadow-sm">
              <div className="px-3 py-2 border-b border-gray-100 font-medium text-gray-700">球机画面（验证瞄准）</div>
              <div className="flex-1 min-h-[240px] bg-black flex items-center justify-center text-gray-400 overflow-hidden">
                {verifySnapshotUrl ? (
                  <img src={verifySnapshotUrl} alt="验证画面" className="w-full h-full object-contain" />
                ) : (
                  <span className="text-sm">点击「验证」后查看球机画面</span>
                )}
              </div>
            </div>
            <div className="flex-1 bg-white rounded-xl p-6 shadow-sm border border-gray-100 overflow-auto">
              <h3 className="font-medium text-gray-800 mb-4">标定结果</h3>
              <pre className="text-xs bg-gray-50 p-3 rounded-lg overflow-auto mb-4">
                {JSON.stringify(computeResult.transform, null, 2)}
              </pre>
              {computeResult.error && (
                <p className="text-sm text-gray-600 mb-4">
                  平均误差: {computeResult.error.avgDegrees?.toFixed(2)}° ，最大误差: {computeResult.error.maxDegrees?.toFixed(2)}°
                </p>
              )}
              <div className="flex flex-wrap gap-3">
                <button onClick={handleVerify} className="flex items-center gap-2 px-4 py-2 bg-amber-100 text-amber-800 rounded-lg hover:bg-amber-200">
                  验证
                </button>
                <button onClick={handleSave} disabled={saving} className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50">
                  <Save size={18} /> {saving ? '保存中...' : '保存到防区'}
                </button>
                <button onClick={() => { setStep(2); setVerifySnapshotUrl(null); }} className="px-4 py-2 bg-gray-200 rounded-lg hover:bg-gray-300">返回重采</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
