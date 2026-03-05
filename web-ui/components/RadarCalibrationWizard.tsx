import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { X, ChevronRight, Crosshair, Check, Save, Camera, Image, ChevronUp, ChevronDown, ChevronLeft as ArrowLeft, ChevronRight as ArrowRight, Play, Square, Lock, RotateCcw, Plus, Gauge, Target, Radio, ZoomIn, ZoomOut } from 'lucide-react';
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
  cameraZoom?: number;
  radarDistance?: number;
}

interface RadarCalibrationWizardProps {
  assemblyId: string;
  onClose: () => void;
}

type Step = 1 | 2 | 3;
type CameraViewMode = 'stream' | 'snapshot';
type CalibrationPhase = 'live' | 'freezing' | 'frozen';

interface FrozenPoint {
  x: number; y: number; z: number;
  distance: number;
  horizontalAngle: number;
  height: number;
  clusterSize: { width: number; height: number; depth: number };
  pointCount: number;
}

/** PTZ 方向按钮组件：长按控制、松开停止，支持鼠标与触摸 */
const PtzButton: React.FC<{
  icon: React.ReactNode;
  onStart: () => void;
  onStop: () => void;
  size?: 'sm' | 'md';
}> = ({ icon, onStart, onStop, size = 'md' }) => {
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

  const sizeClass = size === 'sm' ? 'w-9 h-9' : 'w-10 h-10';

  return (
    <button
      onMouseDown={begin}
      onMouseUp={end}
      onMouseLeave={end}
      onTouchStart={(e) => { e.preventDefault(); begin(); }}
      onTouchEnd={(e) => { e.preventDefault(); end(); }}
      onTouchCancel={end}
      className={`${sizeClass} flex items-center justify-center rounded-full bg-gray-700/80 text-gray-200
                 hover:bg-gray-600 active:bg-blue-600 active:text-white select-none touch-none
                 transition-all cursor-pointer shadow-sm backdrop-blur-sm`}
    >
      {icon}
    </button>
  );
};

const PTZ_SPEEDS = [
  { label: '慢', value: 20 },
  { label: '中', value: 50 },
  { label: '快', value: 80 },
] as const;

/** 在 1 秒采集的侵入点中找到距雷达最近的聚类 */
function findNearestCluster(pts: { x: number; y: number; z: number }[]): FrozenPoint | null {
  if (pts.length === 0) return null;

  const withDist = pts.map(p => ({
    ...p,
    dist: Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z)
  }));
  withDist.sort((a, b) => a.dist - b.dist);

  const seed = withDist[0];
  const CLUSTER_RADIUS = 0.8;
  const cluster = withDist.filter(p => {
    const dx = p.x - seed.x, dy = p.y - seed.y, dz = p.z - seed.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz) <= CLUSTER_RADIUS;
  });

  if (cluster.length === 0) return null;

  let cx = 0, cy = 0, cz = 0;
  for (const p of cluster) { cx += p.x; cy += p.y; cz += p.z; }
  cx /= cluster.length; cy /= cluster.length; cz /= cluster.length;

  let minX = Infinity, maxX = -Infinity;
  let minY = Infinity, maxY = -Infinity;
  let minZ = Infinity, maxZ = -Infinity;
  for (const p of cluster) {
    if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x;
    if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y;
    if (p.z < minZ) minZ = p.z; if (p.z > maxZ) maxZ = p.z;
  }

  const distance = Math.sqrt(cx * cx + cy * cy + cz * cz);
  const horizontalAngle = Math.atan2(cy, cx) * (180 / Math.PI);

  return {
    x: cx, y: cy, z: cz,
    distance,
    horizontalAngle,
    height: cz,
    clusterSize: {
      width: maxX - minX,
      height: maxZ - minZ,
      depth: maxY - minY,
    },
    pointCount: cluster.length,
  };
}

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

  // 标定流程新增状态
  const [calibPhase, setCalibPhase] = useState<CalibrationPhase>('live');
  const [frozenPoint, setFrozenPoint] = useState<FrozenPoint | null>(null);
  const [ptzLive, setPtzLive] = useState<{ pan: number; tilt: number; zoom: number } | null>(null);
  const [ptzSpeed, setPtzSpeed] = useState(50);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const wsRef = useRef<ReturnType<typeof radarService.connectWebSocket> | null>(null);
  const cameraVideoRef = useRef<HTMLVideoElement>(null);
  const cameraFlvPlayerRef = useRef<ReturnType<typeof mpegts.createPlayer> | null>(null);
  const snapshotIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const pointCloudBufferRef = useRef<{ x: number; y: number; z: number; r?: number; zoneId?: string; timestamp: number }[]>([]);
  const accumulationMs = 2000;
  const renderTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const bufferDirtyRef = useRef(false);
  const freezeBufferRef = useRef<{ x: number; y: number; z: number }[]>([]);
  const calibPhaseRef = useRef<CalibrationPhase>('live');
  const ptzPollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // 保持 ref 与 state 同步，供 WebSocket 回调读取
  useEffect(() => { calibPhaseRef.current = calibPhase; }, [calibPhase]);

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

  // 进入 step 3（验证）时关闭雷达侵入检测，防止 PTZ 联动干扰验证；离开时恢复
  const detectionPrevRef = useRef<boolean | null>(null);
  useEffect(() => {
    if (step !== 3 || !context?.radarDeviceId) return;
    let restored = false;
    (async () => {
      try {
        const res = await radarService.getDetectionEnabled(context.radarDeviceId);
        detectionPrevRef.current = !!res.data?.detectionEnabled;
        if (detectionPrevRef.current) {
          await radarService.setDetectionEnabled(context.radarDeviceId, false);
        }
      } catch { /* ignore */ }
    })();
    return () => {
      if (restored) return;
      restored = true;
      if (detectionPrevRef.current && context?.radarDeviceId) {
        radarService.setDetectionEnabled(context.radarDeviceId, true).catch(() => {});
      }
    };
  }, [step, context?.radarDeviceId]);

  // WebSocket 点云连接
  useEffect(() => {
    if (step !== 2 || !context?.radarDeviceId) return;
    const deviceId = context.radarDeviceId;
    const curZoneId = selectedZone?.zoneId;
    let isCleaningUp = false;

    radarService.setDetectionEnabled(deviceId, true).catch(() => {});

    renderTimerRef.current = setInterval(() => {
      if (calibPhaseRef.current !== 'live') return;
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
          const phase = calibPhaseRef.current;

          if (phase === 'live') {
            for (const p of data.points) {
              if (!p.isIntrusion) continue;
              pointCloudBufferRef.current.push({ x: p.x ?? 0, y: p.y ?? 0, z: p.z ?? 0, r: 255, zoneId: curZoneId, timestamp: now });
            }
            bufferDirtyRef.current = true;
          } else if (phase === 'freezing') {
            for (const p of data.points) {
              if (!p.isIntrusion) continue;
              freezeBufferRef.current.push({ x: p.x ?? 0, y: p.y ?? 0, z: p.z ?? 0 });
            }
          }
          // frozen 阶段：不处理新侵入点
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

  // PTZ 角度轮询：frozen 阶段启动
  const cameraDeviceIdForPtz = selectedZone?.cameraDeviceId || context?.cameraDeviceId;
  useEffect(() => {
    if (calibPhase !== 'frozen' || !cameraDeviceIdForPtz) {
      if (ptzPollRef.current) { clearInterval(ptzPollRef.current); ptzPollRef.current = null; }
      return;
    }
    const poll = async () => {
      try {
        await deviceService.refreshPtzPosition(cameraDeviceIdForPtz);
        const res = await deviceService.getPtzPosition(cameraDeviceIdForPtz);
        if (res?.data) {
          setPtzLive({
            pan: typeof res.data.pan === 'number' ? res.data.pan : parseFloat(String(res.data.pan)) || 0,
            tilt: typeof res.data.tilt === 'number' ? res.data.tilt : parseFloat(String(res.data.tilt)) || 0,
            zoom: typeof res.data.zoom === 'number' ? res.data.zoom : parseFloat(String(res.data.zoom)) || 1,
          });
        }
      } catch (_) {}
    };
    poll();
    ptzPollRef.current = setInterval(poll, 800);
    return () => {
      if (ptzPollRef.current) { clearInterval(ptzPollRef.current); ptzPollRef.current = null; }
    };
  }, [calibPhase, cameraDeviceIdForPtz]);

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

  // 抓图模式
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

  // --- 标定操作 Handlers ---

  const handleFreeze = () => {
    freezeBufferRef.current = [];
    setCalibPhase('freezing');

    setTimeout(() => {
      const collected = freezeBufferRef.current;
      if (collected.length === 0) {
        modal.showModal({ message: '未采集到侵入点，请确保目标物在防区内', type: 'warning' });
        setCalibPhase('live');
        return;
      }
      const result = findNearestCluster(collected);
      if (!result) {
        modal.showModal({ message: '未能识别有效目标，请重试', type: 'warning' });
        setCalibPhase('live');
        return;
      }
      setFrozenPoint(result);
      setPointCloudPoints([]);
      pointCloudBufferRef.current = [];
      setCalibPhase('frozen');
    }, 1000);
  };

  const handleResetToLive = () => {
    setCalibPhase('live');
    setFrozenPoint(null);
    setPtzLive(null);
    pointCloudBufferRef.current = [];
    setPointCloudPoints([]);
  };

  const handleConfirmAim = () => {
    if (!frozenPoint) {
      modal.showModal({ message: '请先固化目标', type: 'warning' });
      return;
    }
    if (!ptzLive || ptzLive.pan == null) {      modal.showModal({ message: '无法获取球机当前角度', type: 'warning' });
      return;
    }
    const newPoint: CalibrationPoint = {
      radarX: frozenPoint.x,
      radarY: frozenPoint.y,
      radarZ: frozenPoint.z,
      cameraPan: ptzLive.pan,
      cameraTilt: ptzLive.tilt,
      cameraZoom: ptzLive.zoom ?? 1,
      radarDistance: frozenPoint.distance,
    };
    setPoints((prev) => [...prev, newPoint]);
    modal.showModal({
      message: `采集成功！雷达(${newPoint.radarX.toFixed(2)}, ${newPoint.radarY.toFixed(2)}, ${newPoint.radarZ.toFixed(2)}) → 球机 Pan=${newPoint.cameraPan.toFixed(1)}° Tilt=${newPoint.cameraTilt.toFixed(1)}° Zoom=${(newPoint.cameraZoom ?? 1).toFixed(1)}x`,
      type: 'success'
    });
    // 采集成功后保持 frozen 状态，用户可选择追加或计算
  };

  const handleAddMore = () => {
    handleResetToLive();
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
        // 从标定点构建距离-变倍映射数据，存入 computeResult 供验证使用
        const zoomCalibData = points
          .filter((p) => p.cameraZoom && p.cameraZoom > 0 && p.radarDistance && p.radarDistance > 0)
          .map((p) => ({ distance: p.radarDistance!, zoom: p.cameraZoom! }));
        setComputeResult({ transform: res.data.transform, error: res.data.error, zoomCalibData });
        setStep(3);
      } else {
        modal.showModal({ message: '计算失败', type: 'error' });
      }
    } catch (e: any) {
      modal.showModal({ message: e?.message || '计算失败', type: 'error' });
    }
  };

  /** 根据标定点的 distance-zoom 映射估算指定距离的变倍 */
  const estimateZoomForDistance = (distance: number): number => {
    const data = computeResult?.zoomCalibData;
    if (!data || data.length === 0) return 1;
    if (data.length === 1) {
      // 单点标定：按线性比例推算 zoom = (distance / calibDistance) * calibZoom
      const ratio = data[0].zoom / data[0].distance;
      return Math.max(1, Math.min(40, ratio * distance));
    }
    // 多点标定：找最近的两个标定距离做线性插值
    const sorted = [...data].sort((a, b) => a.distance - b.distance);
    if (distance <= sorted[0].distance) return sorted[0].zoom;
    if (distance >= sorted[sorted.length - 1].distance) return sorted[sorted.length - 1].zoom;
    for (let i = 0; i < sorted.length - 1; i++) {
      if (distance >= sorted[i].distance && distance <= sorted[i + 1].distance) {
        const t = (distance - sorted[i].distance) / (sorted[i + 1].distance - sorted[i].distance);
        return sorted[i].zoom + t * (sorted[i + 1].zoom - sorted[i].zoom);
      }
    }
    return data[0].zoom;
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
      const rx = target.radarX as number, ry = target.radarY as number, rz = target.radarZ as number;
      const targetDist = Math.sqrt(rx * rx + ry * ry + rz * rz);
      const zoom = estimateZoomForDistance(targetDist);
      await radarService.calibrationVerify(context.radarDeviceId, {
        zoneId: selectedZone.zoneId,
        transform: computeResult.transform,
        radarX: rx,
        radarY: ry,
        radarZ: rz,
        zoom,
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
      const transformWithZoom = {
        ...computeResult.transform,
        zoomCalibration: computeResult.zoomCalibData || [],
      };
      await radarService.calibrationApply(context.radarDeviceId, {
        zoneId: selectedZone.zoneId,
        transform: transformWithZoom
      });
      modal.showModal({ message: '标定已保存', type: 'success' });
      onClose();
    } catch (e: any) {
      modal.showModal({ message: e?.message || '保存失败', type: 'error' });
    } finally {
      setSaving(false);
    }
  };

  // 构建 frozenMarker prop（供 PointCloudRenderer 渲染固化点标记）
  const frozenMarker = useMemo(() => {
    if (!frozenPoint || calibPhase !== 'frozen') return null;
    return {
      x: frozenPoint.x,
      y: frozenPoint.y,
      z: frozenPoint.z,
      label: `距雷达 ${frozenPoint.distance.toFixed(2)}m\nX=${frozenPoint.x.toFixed(2)}  Y=${frozenPoint.y.toFixed(2)}  Z=${frozenPoint.z.toFixed(2)}`,
    };
  }, [frozenPoint, calibPhase]);

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
      {/* 顶部导航栏 */}
      <div className="flex items-center justify-between px-5 py-3 bg-white border-b border-gray-200">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center">
            <Crosshair size={16} className="text-white" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-gray-800">雷达-球机坐标系标定</h2>
            <p className="text-xs text-gray-400">建立雷达探测坐标与球机 PTZ 角度的映射关系</p>
          </div>
        </div>
        <div className="flex items-center gap-4">
          {/* 步骤指示器 */}
          <div className="flex items-center gap-1.5">
            {[
              { n: 1, label: '选择防区' },
              { n: 2, label: '采集标定' },
              { n: 3, label: '验证保存' },
            ].map((s, i) => (
              <React.Fragment key={s.n}>
                {i > 0 && <div className={`w-6 h-px ${step >= s.n ? 'bg-blue-400' : 'bg-gray-200'}`} />}
                <div className="flex items-center gap-1">
                  <div className={`w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold
                    ${step > s.n ? 'bg-blue-600 text-white' : step === s.n ? 'bg-blue-600 text-white ring-2 ring-blue-200' : 'bg-gray-200 text-gray-400'}`}>
                    {step > s.n ? <Check size={11} /> : s.n}
                  </div>
                  <span className={`text-xs hidden sm:inline ${step >= s.n ? 'text-gray-700 font-medium' : 'text-gray-400'}`}>{s.label}</span>
                </div>
              </React.Fragment>
            ))}
          </div>
          <button onClick={onClose} className="p-2 rounded-lg hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors">
            <X size={18} />
          </button>
        </div>
      </div>

      <div className="flex-1 flex flex-col min-h-0 p-4">
        {step === 1 && (
          <div className="flex-1 flex items-center justify-center">
            <div className="w-full max-w-xl">
              {/* 前置条件提示 */}
              <div className="bg-white rounded-xl p-5 shadow-sm border border-gray-100 mb-4">
                <h3 className="font-semibold text-gray-800 mb-1">开始前请确认</h3>
                <p className="text-xs text-gray-400 mb-4">标定前需满足以下条件，否则可能导致标定失败</p>
                <div className="space-y-2.5">
                  {[
                    { icon: <Radio size={15} />, title: '雷达在线运行', desc: '雷达设备已连接并正常输出点云数据' },
                    { icon: <Image size={15} />, title: '已完成背景采集', desc: '在防区设置中已采集并保存背景点云模型' },
                    { icon: <Target size={15} />, title: '已设置防区并启用', desc: '至少创建一个防区、配置缩小距离并启用检测' },
                  ].map((item, i) => (
                    <div key={i} className="flex items-start gap-3 p-2.5 rounded-lg bg-gray-50">
                      <div className="w-8 h-8 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center shrink-0 mt-0.5">
                        {item.icon}
                      </div>
                      <div>
                        <div className="text-sm font-medium text-gray-700">{item.title}</div>
                        <div className="text-xs text-gray-400">{item.desc}</div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* 防区选择 */}
              <div className="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
                <h3 className="font-semibold text-gray-800 mb-1">选择标定防区</h3>
                <p className="text-xs text-gray-400 mb-4">选择需要建立坐标映射的防区，该防区需已关联雷达与球机</p>
                <select
                  value={selectedZone?.zoneId ?? ''}
                  onChange={(e) => {
                    const z = context.zones.find((z) => z.zoneId === e.target.value);
                    setSelectedZone(z || null);
                  }}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2.5 mb-4 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option value="">请选择防区...</option>
                  {context.zones?.map((z) => (
                    <option key={z.zoneId} value={z.zoneId}>{z.zoneName || z.zoneId}</option>
                  ))}
                </select>
                {selectedZone && (
                  <div className="mb-4 p-3 bg-blue-50 rounded-lg text-sm text-blue-700 flex items-center gap-2">
                    <Check size={15} className="text-blue-500 shrink-0" />
                    已选择「{selectedZone.zoneName || selectedZone.zoneId}」，点击下一步开始标定
                  </div>
                )}
                <button
                  onClick={() => setStep(2)}
                  disabled={!selectedZone}
                  className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 text-white rounded-lg
                             hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed font-medium transition-colors"
                >
                  开始标定 <ChevronRight size={16} />
                </button>
              </div>
            </div>
          </div>
        )}

        {step === 2 && (
          <>
            <div className="flex gap-3 flex-1 min-h-0 mb-3">
              {/* 左侧：球机画面 + PTZ 控制 */}
              <div className="flex-1 flex flex-col bg-gray-900 rounded-xl overflow-hidden shadow-md">
                {/* 球机画面头部 */}
                <div className="px-3 py-2 bg-gray-800 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Camera size={14} className="text-gray-400" />
                    <span className="text-sm font-medium text-gray-200">球机画面</span>
                  </div>
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => { setCameraViewMode('stream'); setStreamFailed(false); setSnapshotActive(false); }}
                      className={`flex items-center gap-1 px-2 py-1 rounded text-xs transition-colors ${
                        cameraViewMode === 'stream' ? 'bg-blue-600/30 text-blue-300 font-medium' : 'text-gray-400 hover:bg-gray-700'
                      }`}
                    >
                      <Radio size={11} /> 拉流
                    </button>
                    <button
                      onClick={() => { setCameraViewMode('snapshot'); if (cameraViewMode !== 'snapshot') setSnapshotActive(false); }}
                      className={`flex items-center gap-1 px-2 py-1 rounded text-xs transition-colors ${
                        cameraViewMode === 'snapshot' ? 'bg-blue-600/30 text-blue-300 font-medium' : 'text-gray-400 hover:bg-gray-700'
                      }`}
                    >
                      <Image size={11} /> 抓图
                    </button>
                    {cameraViewMode === 'snapshot' && (
                      <button
                        onClick={() => setSnapshotActive((v) => !v)}
                        className={`flex items-center gap-1 px-2 py-1 rounded text-xs transition-colors ml-1 ${
                          snapshotActive ? 'bg-red-500/30 text-red-300' : 'bg-green-500/30 text-green-300'
                        }`}
                      >
                        {snapshotActive ? <><Square size={10} /> 停止</> : <><Play size={10} /> 开始</>}
                      </button>
                    )}
                  </div>
                </div>

                {/* 球机画面 */}
                <div className="flex-1 min-h-[240px] bg-black flex items-center justify-center text-gray-500 overflow-hidden relative">
                  {showingStream ? (
                    <video ref={cameraVideoRef} className="w-full h-full object-contain" muted playsInline />
                  ) : cameraViewMode === 'snapshot' && cameraSnapshotUrl ? (
                    <img src={cameraSnapshotUrl} alt="球机画面" className="w-full h-full object-contain" />
                  ) : isLoadingCamera ? (
                    <div className="flex flex-col items-center gap-2">
                      <div className="w-7 h-7 border-2 border-blue-400 border-t-transparent rounded-full animate-spin" />
                      <span className="text-xs text-gray-400">正在拉流...</span>
                    </div>
                  ) : cameraViewMode === 'stream' && streamFailed ? (
                    <span className="text-xs text-gray-500">拉流失败，已切换抓图模式</span>
                  ) : cameraViewMode === 'snapshot' && !cameraSnapshotUrl ? (
                    <span className="text-xs text-gray-500">{snapshotActive ? '抓图中...' : '点击「开始」开始抓图'}</span>
                  ) : (
                    <span className="text-xs text-gray-500">等待画面...</span>
                  )}
                </div>

                {/* PTZ 控制区域 */}
                <div className="bg-gray-800/90 px-3 py-2.5">
                  <div className="flex items-center justify-between">
                    {/* 方向控制十字键 */}
                    <div className="flex items-center gap-1">
                      <PtzButton
                        icon={<ArrowLeft size={15} />}
                        onStart={() => deviceService.ptzControl(cameraDeviceId, 'left', 'start', ptzSpeed)}
                        onStop={() => deviceService.ptzControl(cameraDeviceId, 'left', 'stop', 0)}
                      />
                      <div className="flex flex-col gap-1">
                        <PtzButton
                          icon={<ChevronUp size={15} />}
                          onStart={() => deviceService.ptzControl(cameraDeviceId, 'up', 'start', ptzSpeed)}
                          onStop={() => deviceService.ptzControl(cameraDeviceId, 'up', 'stop', 0)}
                        />
                        <PtzButton
                          icon={<ChevronDown size={15} />}
                          onStart={() => deviceService.ptzControl(cameraDeviceId, 'down', 'start', ptzSpeed)}
                          onStop={() => deviceService.ptzControl(cameraDeviceId, 'down', 'stop', 0)}
                        />
                      </div>
                      <PtzButton
                        icon={<ArrowRight size={15} />}
                        onStart={() => deviceService.ptzControl(cameraDeviceId, 'right', 'start', ptzSpeed)}
                        onStop={() => deviceService.ptzControl(cameraDeviceId, 'right', 'stop', 0)}
                      />
                    </div>

                    {/* 变倍 + 速度 */}
                    <div className="flex flex-col items-center gap-1.5">
                      <div className="flex items-center gap-1">
                        <PtzButton
                          icon={<ZoomOut size={14} />}
                          size="sm"
                          onStart={() => deviceService.ptzControl(cameraDeviceId, 'zoom_out', 'start', ptzSpeed)}
                          onStop={() => deviceService.ptzControl(cameraDeviceId, 'zoom_out', 'stop', 0)}
                        />
                        <PtzButton
                          icon={<ZoomIn size={14} />}
                          size="sm"
                          onStart={() => deviceService.ptzControl(cameraDeviceId, 'zoom_in', 'start', ptzSpeed)}
                          onStop={() => deviceService.ptzControl(cameraDeviceId, 'zoom_in', 'stop', 0)}
                        />
                      </div>
                      <div className="flex items-center gap-1">
                        <Gauge size={11} className="text-gray-500" />
                        {PTZ_SPEEDS.map((s) => (
                          <button
                            key={s.value}
                            onClick={() => setPtzSpeed(s.value)}
                            className={`px-1.5 py-0.5 rounded text-[10px] font-medium transition-colors ${
                              ptzSpeed === s.value
                                ? 'bg-blue-600 text-white'
                                : 'bg-gray-700 text-gray-400 hover:bg-gray-600'
                            }`}
                          >
                            {s.label}
                          </button>
                        ))}
                      </div>
                    </div>

                    {/* PTZ 实时角度 */}
                    {calibPhase === 'frozen' && ptzLive ? (
                      <div className="text-xs font-mono text-gray-300 text-right leading-relaxed">
                        <div>P <span className="text-blue-400 font-semibold">{ptzLive.pan.toFixed(1)}°</span></div>
                        <div>T <span className="text-green-400 font-semibold">{ptzLive.tilt.toFixed(1)}°</span></div>
                        <div>Z <span className="text-amber-400 font-semibold">{ptzLive.zoom.toFixed(1)}x</span></div>
                      </div>
                    ) : (
                      <div className="w-16" />
                    )}
                  </div>
                </div>
              </div>

              {/* 右侧：防区点云 */}
              <div className="flex-1 flex flex-col bg-gray-900 rounded-xl overflow-hidden shadow-md">
                <div className="px-3 py-2 bg-gray-800 flex items-center gap-2">
                  <Target size={14} className="text-gray-400" />
                  <span className="text-sm font-medium text-gray-200">防区点云</span>
                  <span className={`w-1.5 h-1.5 rounded-full ${wsConnected ? 'bg-green-400' : 'bg-red-400'}`} />
                  {calibPhase === 'freezing' && (
                    <span className="text-xs text-blue-400 ml-1 flex items-center gap-1">
                      <div className="w-3 h-3 border-2 border-blue-400 border-t-transparent rounded-full animate-spin" />
                      固化中...
                    </span>
                  )}
                  <span className="text-xs text-gray-500 ml-auto">
                    {calibPhase === 'frozen' && frozenPoint ? (
                      <span className="text-blue-400 font-medium">固化目标 · {frozenPoint.pointCount} 点</span>
                    ) : (
                      <>
                        {backgroundPoints.length > 0 && <span>背景 {backgroundPoints.length}</span>}
                        {pointCloudPoints.length > 0 && (
                          <span className="text-red-400 ml-1">侵入 {pointCloudPoints.length}</span>
                        )}
                      </>
                    )}
                  </span>
                </div>
                <div className="flex-1 min-h-[280px] bg-gray-950 overflow-hidden">
                  {(pointCloudPoints.length > 0 || backgroundPoints.length > 0 || frozenMarker) ? (
                    <PointCloudRenderer
                      points={calibPhase === 'frozen' ? [] : pointCloudPoints}
                      pointSize={0.03}
                      backgroundColor="#050508"
                      showGrid
                      showAxes
                      showRangeRings
                      colorMode="defense"
                      defenseBackgroundPoints={backgroundPoints}
                      shrinkDistance={(zoneFullData?.shrinkDistanceCm || 20) / 100}
                      frozenMarker={frozenMarker}
                    />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center text-gray-600 text-sm">
                      {wsConnected
                        ? (backgroundPoints.length > 0 ? '等待侵入目标...' : '加载防区...')
                        : '点云未连接'}
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* 底部操作栏 */}
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
              {/* 固化目标信息条 */}
              {calibPhase === 'frozen' && frozenPoint && (
                <div className="px-4 py-2 bg-gradient-to-r from-blue-50 to-indigo-50 border-b border-blue-100">
                  <div className="flex items-center gap-4 text-sm">
                    <div className="flex items-center gap-1.5 text-blue-700 font-semibold">
                      <Target size={15} />
                      固化目标
                    </div>
                    <div className="h-4 w-px bg-blue-200" />
                    <span className="text-blue-800">距雷达 <b>{frozenPoint.distance.toFixed(2)}m</b></span>
                    <span className="text-blue-700 font-mono text-xs bg-blue-100 px-2 py-0.5 rounded">
                      X={frozenPoint.x.toFixed(2)} Y={frozenPoint.y.toFixed(2)} Z={frozenPoint.z.toFixed(2)}
                    </span>
                    <span className="text-blue-600 text-xs">
                      {frozenPoint.clusterSize.width.toFixed(2)}×{frozenPoint.clusterSize.height.toFixed(2)}×{frozenPoint.clusterSize.depth.toFixed(2)}m
                    </span>
                    <span className="text-blue-500 text-xs">{frozenPoint.pointCount} 点</span>
                  </div>
                </div>
              )}

              <div className="px-4 py-3">
                <div className="flex items-center gap-3">
                  {/* Live 阶段 */}
                  {calibPhase === 'live' && (
                    <>
                      <button
                        onClick={handleFreeze}
                        className="flex items-center gap-2 px-5 py-2.5 bg-gradient-to-r from-amber-500 to-orange-500 text-white rounded-lg
                                   hover:from-amber-600 hover:to-orange-600 font-medium shadow-sm transition-all"
                      >
                        <Lock size={16} /> 固化目标
                      </button>
                      <span className="text-sm text-gray-400">将目标物放入防区 → 点击固化 → 瞄准 → 确认</span>
                    </>
                  )}

                  {/* Freezing 阶段 */}
                  {calibPhase === 'freezing' && (
                    <div className="flex items-center gap-3 py-0.5">
                      <div className="w-5 h-5 border-2 border-amber-500 border-t-transparent rounded-full animate-spin" />
                      <span className="text-sm text-amber-600 font-medium">正在固化目标...</span>
                    </div>
                  )}

                  {/* Frozen 阶段 */}
                  {calibPhase === 'frozen' && (
                    <>
                      <button
                        onClick={handleConfirmAim}
                        className="flex items-center gap-2 px-5 py-2.5 bg-gradient-to-r from-green-500 to-emerald-600 text-white rounded-lg
                                   hover:from-green-600 hover:to-emerald-700 font-medium shadow-sm transition-all"
                      >
                        <Crosshair size={16} /> 确认瞄准
                      </button>
                      <button
                        onClick={handleResetToLive}
                        className="flex items-center gap-1.5 px-3 py-2.5 text-gray-500 hover:text-gray-700 hover:bg-gray-50 rounded-lg text-sm transition-colors"
                      >
                        <RotateCcw size={14} /> 重新固化
                      </button>
                      <div className="h-5 w-px bg-gray-200 mx-1" />
                      <span className="text-xs text-gray-400">操作 PTZ 瞄准目标后确认</span>
                    </>
                  )}

                  {/* 标定点操作 */}
                  {points.length > 0 && (
                    <div className="ml-auto flex items-center gap-2">
                      <span className="text-sm text-gray-500">
                        <b className="text-gray-700">{points.length}</b> 个标定点
                      </span>
                      {calibPhase === 'frozen' && (
                        <button
                          onClick={handleAddMore}
                          className="flex items-center gap-1 px-3 py-2 text-blue-600 hover:bg-blue-50 rounded-lg text-sm font-medium transition-colors"
                        >
                          <Plus size={14} /> 追加
                        </button>
                      )}
                      <button
                        onClick={handleCompute}
                        className="flex items-center gap-2 px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-medium shadow-sm transition-colors"
                      >
                        <Check size={16} /> {points.length === 1 ? '快速标定' : '计算标定'}
                      </button>
                    </div>
                  )}
                </div>

                {/* 已采集标定点列表 */}
                {points.length > 0 && (
                  <div className="mt-3 border-t border-gray-100 pt-2">
                    <div className="flex flex-wrap gap-2">
                      {points.map((p, i) => (
                        <div key={i} className="flex items-center gap-1.5 bg-gray-50 rounded-lg px-2.5 py-1.5 text-xs group">
                          <span className="w-5 h-5 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center font-semibold text-[10px]">
                            {i + 1}
                          </span>
                          <span className="text-gray-500">雷达坐标</span>
                          <span className="text-gray-700 font-mono">
                            ({p.radarX.toFixed(2)}, {p.radarY.toFixed(2)}, {p.radarZ.toFixed(2)})
                          </span>
                          <span className="text-gray-400">→</span>
                          <span className="text-gray-500">水平</span>
                          <span className="text-gray-700 font-mono">{p.cameraPan.toFixed(1)}°</span>
                          <span className="text-gray-500">俯仰</span>
                          <span className="text-gray-700 font-mono">{p.cameraTilt.toFixed(1)}°</span>
                          {p.cameraZoom && p.cameraZoom > 1 && (
                            <>
                              <span className="text-gray-500">变倍</span>
                              <span className="text-blue-600 font-mono">{p.cameraZoom.toFixed(1)}x</span>
                            </>
                          )}
                          <button
                            onClick={() => setPoints((prev) => prev.filter((_, idx) => idx !== i))}
                            className="text-gray-300 hover:text-red-500 transition-colors opacity-0 group-hover:opacity-100"
                          >
                            <X size={13} />
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          </>
        )}

        {step === 3 && computeResult && (() => {
          const t = computeResult.transform;
          const e = computeResult.error;
          const avgErr = e?.avgDegrees ?? 0;
          const maxErr = e?.maxDegrees ?? 0;
          const quality = avgErr < 1 ? 'excellent' : avgErr < 3 ? 'good' : avgErr < 10 ? 'fair' : 'poor';
          const qualityMap = {
            excellent: { label: '优秀', color: 'text-green-600', bg: 'bg-green-50', ring: 'ring-green-200', desc: '标定精度极高，可直接投入使用' },
            good: { label: '良好', color: 'text-blue-600', bg: 'bg-blue-50', ring: 'ring-blue-200', desc: '标定精度较好，建议验证确认' },
            fair: { label: '一般', color: 'text-amber-600', bg: 'bg-amber-50', ring: 'ring-amber-200', desc: '误差偏大，建议重新采集标定点' },
            poor: { label: '较差', color: 'text-red-600', bg: 'bg-red-50', ring: 'ring-red-200', desc: '误差过大，请返回重新标定' },
          };
          const q = qualityMap[quality];

          return (
            <div className="flex gap-4 flex-1 min-h-0">
              {/* 左侧：验证画面 */}
              <div className="flex-1 flex flex-col bg-gray-900 rounded-xl overflow-hidden shadow-md">
                <div className="bg-gray-800/90 px-3 py-2.5 flex items-center justify-between">
                  <span className="text-sm font-medium text-gray-200 flex items-center gap-1.5">
                    <Camera size={14} className="text-blue-400" /> 球机画面（验证瞄准）
                  </span>
                  <span className="text-[10px] text-gray-500">侵入检测已暂停</span>
                </div>
                <div className="flex-1 min-h-[240px] bg-black flex items-center justify-center text-gray-500 overflow-hidden">
                  {verifySnapshotUrl ? (
                    <img src={verifySnapshotUrl} alt="验证画面" className="w-full h-full object-contain" />
                  ) : (
                    <div className="text-center">
                      <Crosshair size={32} className="mx-auto mb-2 text-gray-600" />
                      <p className="text-sm text-gray-500">站到防区内，点击「验证瞄准」</p>
                      <p className="text-xs text-gray-600 mt-1">球机将自动转向目标位置并抓图显示</p>
                    </div>
                  )}
                </div>
              </div>

              {/* 右侧：标定结果 */}
              <div className="flex-1 flex flex-col bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                <div className="px-5 py-4 border-b border-gray-100">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-semibold text-gray-800 flex items-center gap-2">
                      <Target size={16} className="text-blue-500" /> 标定结果
                    </h3>
                    <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ring-1 ${q.bg} ${q.color} ${q.ring}`}>
                      {q.label}
                    </span>
                  </div>
                  <p className="text-xs text-gray-400">{q.desc}</p>
                </div>

                <div className="flex-1 overflow-auto px-5 py-4 space-y-4">
                  {/* 精度指标卡片 */}
                  <div className="grid grid-cols-2 gap-3">
                    <div className="p-3 rounded-xl bg-gray-50 border border-gray-100 text-center">
                      <div className="text-[10px] text-gray-400 uppercase tracking-wide mb-1">平均误差</div>
                      <div className={`text-lg font-bold ${q.color}`}>{avgErr.toFixed(2)}°</div>
                    </div>
                    <div className="p-3 rounded-xl bg-gray-50 border border-gray-100 text-center">
                      <div className="text-[10px] text-gray-400 uppercase tracking-wide mb-1">最大误差</div>
                      <div className="text-lg font-bold text-gray-700">{maxErr.toFixed(2)}°</div>
                    </div>
                  </div>

                  {/* 简易参数显示 */}
                  <div className="space-y-2">
                    <div className="flex items-center gap-2 text-sm text-gray-600">
                      <span className="text-gray-400 w-16 text-right shrink-0">水平偏转</span>
                      <div className="flex-1 h-1.5 bg-gray-100 rounded-full overflow-hidden">
                        <div className="h-full bg-blue-500 rounded-full" style={{ width: `${Math.min(Math.abs(t.rotation?.z || 0) / 180 * 100, 100)}%` }} />
                      </div>
                      <span className="font-mono text-xs font-semibold w-16 text-right">{(t.rotation?.z || 0).toFixed(1)}°</span>
                    </div>
                    <div className="flex items-center gap-2 text-sm text-gray-600">
                      <span className="text-gray-400 w-16 text-right shrink-0">俯仰偏转</span>
                      <div className="flex-1 h-1.5 bg-gray-100 rounded-full overflow-hidden">
                        <div className="h-full bg-indigo-500 rounded-full" style={{ width: `${Math.min(Math.abs(t.rotation?.x || 0) / 90 * 100, 100)}%` }} />
                      </div>
                      <span className="font-mono text-xs font-semibold w-16 text-right">{(t.rotation?.x || 0).toFixed(1)}°</span>
                    </div>
                  </div>

                  {/* 展开/收起专业参数 */}
                  <button
                    onClick={() => setShowAdvanced(!showAdvanced)}
                    className="flex items-center gap-1 text-xs text-gray-400 hover:text-gray-600 transition-colors"
                  >
                    {showAdvanced ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                    {showAdvanced ? '收起' : '展开'}专业参数
                  </button>

                  {showAdvanced && (
                    <div className="p-3 rounded-lg bg-gray-50 border border-gray-100 space-y-2">
                      <div className="grid grid-cols-3 gap-2 text-center">
                        {['X', 'Y', 'Z'].map((axis) => (
                          <div key={axis}>
                            <div className="text-[10px] text-gray-400">旋转 {axis}</div>
                            <div className="text-xs font-mono font-semibold text-gray-700">{(t.rotation?.[axis.toLowerCase()] ?? 0).toFixed(4)}°</div>
                          </div>
                        ))}
                      </div>
                      <div className="grid grid-cols-3 gap-2 text-center">
                        {['X', 'Y', 'Z'].map((axis) => (
                          <div key={axis}>
                            <div className="text-[10px] text-gray-400">平移 {axis}</div>
                            <div className="text-xs font-mono font-semibold text-gray-700">{(t.translation?.[axis.toLowerCase()] ?? 0).toFixed(4)}</div>
                          </div>
                        ))}
                      </div>
                      <div className="text-center">
                        <div className="text-[10px] text-gray-400">缩放系数</div>
                        <div className="text-xs font-mono font-semibold text-gray-700">{t.scale ?? 1}</div>
                      </div>
                      {e?.perPointPan && (
                        <div className="border-t border-gray-200 pt-2 mt-2">
                          <div className="text-[10px] text-gray-400 mb-1">逐点误差</div>
                          {e.perPointPan.map((_: any, i: number) => (
                            <div key={i} className="text-[11px] font-mono text-gray-500">
                              点{i + 1}: 水平 {(e.perPointPan[i] ?? 0).toFixed(3)}° / 俯仰 {(e.perPointTilt?.[i] ?? 0).toFixed(3)}°
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* 底部操作 */}
                <div className="px-5 py-3.5 border-t border-gray-100 flex items-center gap-2">
                  <button
                    onClick={handleVerify}
                    className="flex-1 flex items-center justify-center gap-1.5 px-4 py-2.5 bg-amber-500 text-white rounded-lg hover:bg-amber-600 font-medium text-sm shadow-sm transition-colors"
                  >
                    <Crosshair size={15} /> 验证瞄准
                  </button>
                  <button
                    onClick={handleSave}
                    disabled={saving}
                    className="flex-1 flex items-center justify-center gap-1.5 px-4 py-2.5 bg-green-600 text-white rounded-lg hover:bg-green-700 font-medium text-sm shadow-sm disabled:opacity-50 transition-colors"
                  >
                    <Save size={15} /> {saving ? '保存中...' : '保存到防区'}
                  </button>
                  <button
                    onClick={() => { setStep(2); setVerifySnapshotUrl(null); handleResetToLive(); }}
                    className="px-3 py-2.5 bg-gray-100 text-gray-500 rounded-lg hover:bg-gray-200 text-sm font-medium transition-colors"
                  >
                    返回重采
                  </button>
                </div>
              </div>
            </div>
          );
        })()}
      </div>
    </div>
  );
};
