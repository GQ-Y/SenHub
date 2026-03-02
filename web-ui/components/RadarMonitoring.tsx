import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { radarService } from '../src/api/services';
import { useModal } from '../hooks/useModal';
import { Modal, ConfirmModal } from './Modal';
import { PointCloudRenderer, type PointCloudBuffer } from './PointCloudRenderer';
import { IntrusionHistoryPanel } from './IntrusionHistoryPanel';
import { ArrowLeft } from 'lucide-react';

interface Point {
  x: number;
  y: number;
  z: number;
  r?: number;
  timestamp?: number; // 点云时间戳
  isZoneBoundary?: boolean;
  zoneId?: string;
}

interface IntrusionPoint extends Point {
  isIntrusion?: boolean;
}

/**
 * 实时监控页面
 */
interface TimestampedPoint extends Point {
  timestamp: number; // 点云数据的时间戳
}

export const RadarMonitoring: React.FC = () => {
  const { deviceId } = useParams<{ deviceId: string }>();
  const navigate = useNavigate();
  const modal = useModal();
  const [isConnected, setIsConnected] = useState(false);
  const [intrusions, setIntrusions] = useState<any[]>([]);
  const [pointCloudData, setPointCloudData] = useState<TimestampedPoint[]>([]);
  const [intrusionPoints, setIntrusionPoints] = useState<Point[]>([]);
  const wsRef = useRef<WebSocket | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const pointCloudContainerRef = useRef<HTMLDivElement>(null);

  // 侵入检测开关：未开启时仅推送点云，开启后每帧跑侵入检测
  const [detectionEnabled, setDetectionEnabledState] = useState<boolean | null>(null);
  const [detectionLoading, setDetectionLoading] = useState(false);

  // 累积时间配置（秒）- 滑动窗口模式
  // 只保留指定时间内的数据，老数据自动滑出窗口
  const [accumulationTime, setAccumulationTime] = useState(1); // 默认 1 秒
  const accumulationTimeRef = useRef(accumulationTime * 1000); // 毫秒

  // 同步更新 ref
  useEffect(() => {
    accumulationTimeRef.current = accumulationTime * 1000;
  }, [accumulationTime]);

  // Worker 路径：接收与渲染解耦，主线程只写 ref，渲染循环内消费（参考 PCL/RViz 单视觉器+互斥更新）
  const workerRef = useRef<Worker | null>(null);
  const workerReadyRef = useRef(false);
  const workerLatestRef = useRef<PointCloudBuffer | null>(null);
  const [workerReady, setWorkerReady] = useState(false);       // 用于决定是否传 pendingFrameRef
  const [workerDisplayCount, setWorkerDisplayCount] = useState(0); // 渲染消费后回传的点数，供 UI 显示
  const [pointCloudBuffer, setPointCloudBufferState] = useState<PointCloudBuffer | null>(null);

  // 传统路径备用：点云缓冲区（Worker 未启用时使用）
  const pointCloudBufferRef = useRef<TimestampedPoint[]>([]);
  const renderFrameRef = useRef<number | null>(null);
  const lastLogTimeRef = useRef<number>(0);
  const pendingUpdateRef = useRef(false);

  // 回放状态
  const [playbackMode, setPlaybackMode] = useState(false);
  const [playbackPoints, setPlaybackPoints] = useState<Point[]>([]);
  const playbackIntervalRef = useRef<NodeJS.Timeout | null>(null);

  const handlePlay = async (record: any) => {
    if (playbackIntervalRef.current) clearInterval(playbackIntervalRef.current);
    setPlaybackMode(true);
    setPlaybackPoints([]);

    try {
      const res = await radarService.getIntrusionData(record.recordId);
      // API 返回完整录制对象: { zoneId, deviceId, startTime, duration, frameCount, frames }
      const recordData = res.data;
      const frames = Array.isArray(recordData) ? recordData : recordData?.frames;
      if (!frames || !Array.isArray(frames)) {
        modal.showModal({ message: '无效的录制数据（缺少帧数据）', type: 'error' });
        setPlaybackMode(false);
        return;
      }

      let i = 0;
      playbackIntervalRef.current = setInterval(() => {
        if (i >= frames.length) {
          if (playbackIntervalRef.current) clearInterval(playbackIntervalRef.current);
          return;
        }
        const frame = frames[i];
        // 确保点云有 zoneId，如果没有则使用记录的 zoneId
        const pts = (frame.points || []).map((p: any) => ({
          x: p.x || 0,
          y: p.y || 0,
          z: p.z || 0,
          r: p.r,
          zoneId: p.zoneId || record.zoneId
        }));
        if (pts.length > 0) {
          console.log(`回放帧 ${i}/${frames.length}: ${pts.length} 个点`);
        }
        setPlaybackPoints(pts);
        i++;
      }, 100);
    } catch (e: any) {
      console.error(e);
      modal.showModal({ message: '加载轨迹失败: ' + e.message, type: 'error' });
    }
  };

  useEffect(() => {
    return () => {
      if (playbackIntervalRef.current) clearInterval(playbackIntervalRef.current);
    };
  }, []);

  // 拉取当前检测开关状态
  const fetchDetectionState = async () => {
    if (!deviceId) return;
    try {
      const res = await radarService.getDetectionEnabled(deviceId);
      setDetectionEnabledState(!!res.data?.detectionEnabled);
    } catch (e) {
      console.error('获取检测状态失败', e);
      setDetectionEnabledState(false);
    }
  };

  useEffect(() => {
    if (deviceId) fetchDetectionState();
  }, [deviceId]);

  // 累积时间变化时同步到 Worker
  useEffect(() => {
    if (workerRef.current) {
      workerRef.current.postMessage({ type: 'setWindow', windowMs: accumulationTime * 1000 });
    }
  }, [accumulationTime]);

  const handleToggleDetection = async () => {
    if (!deviceId || detectionLoading) return;
    const next = !(detectionEnabled ?? false);
    setDetectionLoading(true);
    try {
      await radarService.setDetectionEnabled(deviceId, next);
      setDetectionEnabledState(next);
      modal.showModal({ message: next ? '已开启侵入检测' : '已关闭侵入检测（仅推送点云）', type: 'success' });
    } catch (e: any) {
      modal.showModal({ message: (e?.message || '操作失败') + '', type: 'error' });
    } finally {
      setDetectionLoading(false);
    }
  };

  useEffect(() => {
    let isCleaningUp = false;
    let ws: WebSocket | null = null;

    loadIntrusions();

    // Worker 路径：二进制在子线程解析 + 滑动窗口，主线程只收缓冲并渲染，支撑 20 万点/秒
    const useWorker = true;
    if (useWorker) {
      try {
        const worker = new Worker(new URL('../src/workers/pointcloud.worker.ts', import.meta.url), { type: 'module' });
        workerRef.current = worker;
        worker.postMessage({ type: 'setWindow', windowMs: accumulationTimeRef.current });
        worker.onmessage = (e: MessageEvent) => {
          if (isCleaningUp) return;
          if (e.data?.type === 'update') {
            workerLatestRef.current = {
              positions: e.data.positions,
              colors: e.data.colors,
              count: e.data.count
            };
            pendingUpdateRef.current = true;
          }
        };
        worker.onerror = (err) => console.warn('[点云] Worker 错误', err);
        workerReadyRef.current = true;
        setWorkerReady(true);
        console.log('[点云] 使用 Worker 路径（接收与渲染解耦）');
      } catch (e) {
        console.warn('[点云] Worker 不可用，回退主线程', e);
        workerRef.current = null;
        workerReadyRef.current = false;
        setWorkerReady(false);
      }
    }

    if (deviceId) {
      try {
        console.log('开始连接WebSocket，deviceId:', deviceId);
        // 仅当 Worker 创建成功时才走二进制路径，否则主线程解析并更新点云（回退）
        const wsOptions = useWorker && workerReadyRef.current
          ? { onBinaryPointCloud: (buf: ArrayBuffer) => workerRef.current?.postMessage({ type: 'binary', buffer: buf }, [buf]) }
          : undefined;
        ws = radarService.connectWebSocket(
          deviceId,
          (data) => {
            if (isCleaningUp) return;
            if (data.type !== 'pointcloud') {
              console.log('收到WebSocket消息:', data.type, data);
            }
            if (data.type === 'pointcloud' && data.points && (!useWorker || !workerReadyRef.current)) {
              const now = Date.now();
              const pointCount = data.points.length;
              const timeSpread = 50;
              const newPoints: TimestampedPoint[] = data.points.map((p: any, index: number) => ({
                x: p.x || 0,
                y: p.y || 0,
                z: p.z || 0,
                r: p.r,
                timestamp: now - (timeSpread * index / Math.max(1, pointCount))
              }));
              const cutoffTime = now - accumulationTimeRef.current;
              pointCloudBufferRef.current = [
                ...pointCloudBufferRef.current.filter(p => p.timestamp! > cutoffTime),
                ...newPoints
              ];
              pendingUpdateRef.current = true;
            }
            if (data.type === 'intrusion') {
              setIntrusions(prev => [data, ...prev].slice(0, 50));
              if (data.cluster && data.cluster.points) {
                const intrusionPts: Point[] = data.cluster.points.map((p: any) => ({
                  x: p.x || 0,
                  y: p.y || 0,
                  z: p.z || 0
                }));
                setIntrusionPoints(prev => [...prev, ...intrusionPts].slice(-1000));
              }
            } else if (data.type === 'status') {
              setIsConnected(data.connected || false);
              console.log('WebSocket状态更新:', data.connected);
            }
          },
          wsOptions
        );
        wsRef.current = ws;

        // 监听WebSocket状态
        ws.onopen = () => {
          if (isCleaningUp) return;
          console.log('WebSocket连接已建立');
          setIsConnected(true);
        };

        ws.onerror = (error) => {
          // 如果正在清理（React StrictMode 导致），不报错
          if (isCleaningUp) {
            console.log('WebSocket清理中，忽略错误事件');
            return;
          }
          console.error('WebSocket错误:', error);
          setIsConnected(false);
        };

        ws.onclose = (event) => {
          // 如果正在清理，这是正常行为，不需要报错
          if (isCleaningUp) {
            console.log('WebSocket已正常关闭（组件卸载）');
            return;
          }
          // 1000 = 正常关闭, 1006 = 异常关闭
          if (event.code !== 1000) {
            console.log('WebSocket连接已关闭:', event.code, event.reason);
          }
          setIsConnected(false);
        };
      } catch (err) {
        console.error('WebSocket连接失败', err);
        setIsConnected(false);
      }
    }

    // rAF 节流：Worker 路径只从 Worker 更新；传统路径只做滑动窗口 + setPointCloudData
    const tick = () => {
      if (isCleaningUp) return;
      renderFrameRef.current = requestAnimationFrame(tick);
      if (!pendingUpdateRef.current) return;
      pendingUpdateRef.current = false;

      const now = Date.now();
      const buf = workerLatestRef.current;
      if (buf) {
        // 仅回退路径写 state 并清 ref；Worker 路径由 PointCloudRenderer 在 rAF 内消费 ref，不在此清
        if (!workerReadyRef.current) {
          if (buf.count > 0) setPointCloudBufferState(buf);
          workerLatestRef.current = null;
        }
        if (buf.count > 0 && now - lastLogTimeRef.current >= 2000) {
          console.log(`[点云 Worker] 窗口点数: ${buf.count.toLocaleString()}`);
          lastLogTimeRef.current = now;
        }
      } else if (!useWorker || !workerReadyRef.current) {
        // 传统路径或 Worker 失败回退：滑动窗口 + setPointCloudData
        const cutoffTime = now - accumulationTimeRef.current;
        const windowPoints = pointCloudBufferRef.current.filter(p => p.timestamp! > cutoffTime);
        pointCloudBufferRef.current = windowPoints;
        if (windowPoints.length > 0) {
          setPointCloudData(windowPoints);
        }
        if (now - lastLogTimeRef.current >= 2000) {
          const accTimeSeconds = accumulationTimeRef.current / 1000;
          console.log(`[点云滑动窗口] 点数: ${windowPoints.length.toLocaleString()}, 窗口: ${accTimeSeconds}s`);
          lastLogTimeRef.current = now;
        }
      }
    };
    renderFrameRef.current = requestAnimationFrame(tick);

    return () => {
      isCleaningUp = true;

      if (workerRef.current) {
        workerRef.current.terminate();
        workerRef.current = null;
        workerReadyRef.current = false;
        setWorkerReady(false);
        setWorkerDisplayCount(0);
      }
      if (ws) {
        console.log('关闭WebSocket连接');
        ws.close(1000, '组件卸载');
        wsRef.current = null;
      }
      if (renderFrameRef.current != null) {
        cancelAnimationFrame(renderFrameRef.current);
        renderFrameRef.current = null;
      }
      // 清理所有缓存数据，确保二次进入时从干净状态开始
      workerLatestRef.current = null;
      pendingUpdateRef.current = false;
      pointCloudBufferRef.current = [];
      setPointCloudData([]);
      setIntrusionPoints([]);
      setPointCloudBufferState(null);
    };
  }, [deviceId]);

  // Worker 路径：累积时间变化时同步窗口到 Worker
  useEffect(() => {
    const w = workerRef.current;
    if (w) {
      w.postMessage({ type: 'setWindow', windowMs: accumulationTime * 1000 });
    }
  }, [accumulationTime]);

  const loadIntrusions = async () => {
    try {
      const response = await radarService.getIntrusions(deviceId!);
      setIntrusions(response.data?.records || []);
    } catch (err: any) {
      console.error('加载侵入记录失败', err);
    }
  };

  // 全屏功能
  const toggleFullscreen = async () => {
    if (!pointCloudContainerRef.current) return;

    try {
      if (!isFullscreen) {
        // 进入全屏
        const element = pointCloudContainerRef.current;
        if (element.requestFullscreen) {
          await element.requestFullscreen();
        } else if ((element as any).webkitRequestFullscreen) {
          await (element as any).webkitRequestFullscreen();
        } else if ((element as any).mozRequestFullScreen) {
          await (element as any).mozRequestFullScreen();
        } else if ((element as any).msRequestFullscreen) {
          await (element as any).msRequestFullscreen();
        }
      } else {
        // 退出全屏
        if (document.exitFullscreen) {
          await document.exitFullscreen();
        } else if ((document as any).webkitExitFullscreen) {
          await (document as any).webkitExitFullscreen();
        } else if ((document as any).mozCancelFullScreen) {
          await (document as any).mozCancelFullScreen();
        } else if ((document as any).msExitFullscreen) {
          await (document as any).msExitFullscreen();
        }
      }
    } catch (err) {
      console.error('全屏操作失败', err);
    }
  };

  // 监听全屏状态变化
  useEffect(() => {
    const handleFullscreenChange = () => {
      const isCurrentlyFullscreen = !!(
        document.fullscreenElement ||
        (document as any).webkitFullscreenElement ||
        (document as any).mozFullScreenElement ||
        (document as any).msFullscreenElement
      );
      setIsFullscreen(isCurrentlyFullscreen);
    };

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    document.addEventListener('webkitfullscreenchange', handleFullscreenChange);
    document.addEventListener('mozfullscreenchange', handleFullscreenChange);
    document.addEventListener('MSFullscreenChange', handleFullscreenChange);

    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange);
      document.removeEventListener('webkitfullscreenchange', handleFullscreenChange);
      document.removeEventListener('mozfullscreenchange', handleFullscreenChange);
      document.removeEventListener('MSFullscreenChange', handleFullscreenChange);
    };
  }, []);

  // Worker 路径：点数由渲染消费后 onFrameConsumed 回传；回退/传统路径用 pointCloudBuffer 或 allPoints
  const allPoints: Point[] = [
    ...pointCloudData.map(({ timestamp, ...p }) => ({ ...p, isIntrusion: false })),
    ...intrusionPoints.map(p => ({ ...p, isIntrusion: true }))
  ];
  const displayPointCount = workerReady
    ? workerDisplayCount + intrusionPoints.length
    : (pointCloudBuffer ? pointCloudBuffer.count + intrusionPoints.length : allPoints.length);

  return (
    <div className="space-y-6">
      {/* 操作栏 */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-white p-4 rounded-2xl shadow-sm border border-gray-100">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate(-1)}
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors text-gray-600"
            title="返回"
          >
            <ArrowLeft size={20} />
          </button>
          <h1 className="text-2xl font-bold">实时监控</h1>
        </div>
        <div className="flex items-center gap-4">
          {/* 累积时间控制 */}
          <div className="flex items-center gap-2 bg-gray-50 px-3 py-1.5 rounded-lg">
            <span className="text-sm text-gray-600">累积时间:</span>
            <select
              value={accumulationTime}
              onChange={(e) => {
                const newTime = parseInt(e.target.value);
                setAccumulationTime(newTime);
                // 清空缓冲区重新累积
                pointCloudBufferRef.current = [];
                setPointCloudData([]);
              }}
              className="text-sm border-none bg-transparent focus:outline-none cursor-pointer font-medium"
            >
              <option value={1}>1秒</option>
              <option value={3}>3秒</option>
              <option value={5}>5秒</option>
            </select>
          </div>

          {/* 侵入检测开关 */}
          <div className="flex items-center gap-2 bg-gray-50 px-3 py-1.5 rounded-lg">
            <span className="text-sm text-gray-600">侵入检测:</span>
            <button
              type="button"
              onClick={handleToggleDetection}
              disabled={detectionLoading || detectionEnabled === null}
              className={`text-sm font-medium px-3 py-1 rounded-md transition-colors ${
                detectionEnabled
                  ? 'bg-amber-100 text-amber-800 hover:bg-amber-200'
                  : 'bg-gray-200 text-gray-600 hover:bg-gray-300'
              } ${detectionLoading ? 'opacity-60 cursor-not-allowed' : ''}`}
              title={detectionEnabled ? '点击关闭检测（仅推送点云，减轻队列压力）' : '点击开启侵入检测'}
            >
              {detectionLoading ? '…' : detectionEnabled ? '已开启' : '已关闭'}
            </button>
          </div>

          {/* 连接状态 */}
          <div className="flex items-center space-x-2">
            <div className={`w-3 h-3 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`} />
            <span className="text-sm">{isConnected ? '已连接' : '未连接'}</span>
          </div>
        </div>
      </div>

      <div className={`grid ${isFullscreen ? 'fixed inset-0 z-50 bg-black' : 'grid-cols-1 lg:grid-cols-4 gap-6'}`}>
        {/* 左侧：实时点云渲染 */}
        <div
          ref={pointCloudContainerRef}
          className={`bg-white rounded-xl shadow-sm border border-gray-100 p-6 ${isFullscreen ? 'w-full h-full rounded-none border-0' : 'lg:col-span-3'}`}
        >
          <div className={`flex justify-between items-center mb-4 ${isFullscreen ? 'absolute top-4 left-4 right-4 z-10' : ''}`}>
            <h2 className="text-lg font-semibold flex items-center">
              {playbackMode ? (
                <span className="text-red-500 flex items-center animate-pulse mr-2">● 回放中</span>
              ) : '实时点云渲染'}
            </h2>
            <div className="flex items-center gap-4">
              <button
                onClick={toggleFullscreen}
                className="p-2 rounded-lg hover:bg-gray-100 transition-colors"
                title={isFullscreen ? '退出全屏 (ESC)' : '全屏显示'}
              >
                {isFullscreen ? (
                  <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                ) : (
                  <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 8V4m0 0h4M4 4l5 5m11-1V4m0 0h-4m4 0l-5 5M4 16v4m0 0h4m-4 0l5-5m11 5l-5-5m5 5v-4m0 4h-4" />
                  </svg>
                )}
              </button>
            </div>
          </div>
          <div className={`w-full ${isFullscreen ? 'h-full' : 'h-[500px]'} rounded-lg overflow-hidden bg-gray-900 relative`}>
            {/* 停止回放按钮 */}
            {playbackMode && !isFullscreen && (
              <button
                onClick={() => {
                  if (playbackIntervalRef.current) clearInterval(playbackIntervalRef.current);
                  setPlaybackMode(false);
                  setPlaybackPoints([]);
                }}
                className="absolute top-4 left-4 z-10 bg-red-600/80 hover:bg-red-700 text-white px-3 py-1.5 rounded-lg text-sm font-medium backdrop-blur-sm transition-colors"
              >
                停止回放
              </button>
            )}

            {(displayPointCount > 0 || playbackPoints.length > 0 || isConnected) ? (
              <PointCloudRenderer
                points={playbackMode ? playbackPoints : (workerReady ? intrusionPoints : (pointCloudBuffer ? intrusionPoints : allPoints))}
                pointCloudBuffer={playbackMode || workerReady ? null : pointCloudBuffer}
                pendingFrameRef={workerReady ? workerLatestRef : undefined}
                onFrameConsumed={workerReady ? setWorkerDisplayCount : undefined}
                color={(point: Point) => {
                  // 侵入点显示为红色，正常点使用colorMode处理
                  const p = point as any;
                  // 优先使用 zoneId 判断
                  if (p.zoneId) return '#ff0000';
                  if (p.isIntrusion) return '#ff0000'; // 兼容旧逻辑
                  return ''; // 返回空字符串，由colorMode处理
                }}
                colorMode={playbackMode ? "defense" : "reflectivity"} // 回放模式下强制 defense 模式
                pointSize={0.015}
                backgroundColor="#0a0a0a"
                showGrid={true}
                showAxes={true}
                showRangeRings={true}
              />
            ) : (
              <div className="w-full h-full flex items-center justify-center text-gray-400">
                {isConnected ? (
                  <div className="text-center">
                    <div className="inline-block w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mb-2"></div>
                    <p>等待点云数据...</p>
                  </div>
                ) : (
                  <p>WebSocket未连接，无法接收实时数据</p>
                )}
              </div>
            )}

            {(isFullscreen || !playbackMode) && displayPointCount > 0 && (
              isFullscreen && (
                <>
                  <div className="absolute bottom-4 left-4 bg-black bg-opacity-70 text-white px-4 py-2 rounded text-sm">
                    当前显示 {displayPointCount.toLocaleString()} 个点
                    {accumulationTime >= 1 && (
                      <span className="ml-2 text-gray-300">
                        约 {(displayPointCount / accumulationTime).toFixed(1)} 点/秒
                      </span>
                    )}
                    {intrusionPoints.length > 0 && (
                      <span className="ml-2 text-red-400">
                        （{intrusionPoints.length} 个侵入点）
                      </span>
                    )}
                  </div>
                  <div className="absolute bottom-4 right-4 bg-black bg-opacity-70 text-white px-4 py-2 rounded text-xs">
                    <div>操作提示：</div>
                    <div>左键拖拽旋转 | 滚轮缩放 | 右键拖拽平移</div>
                    <div className="mt-1">按 ESC 或点击退出全屏</div>
                  </div>
                </>
              )
            )}
            {playbackMode && (
              <div className="absolute bottom-4 left-4 bg-black bg-opacity-70 text-white px-4 py-2 rounded text-sm">
                回放帧点数: {playbackPoints.length}
              </div>
            )}
          </div>
          {!isFullscreen && (displayPointCount > 0 || playbackPoints.length > 0) && (
            <div className="mt-2 text-xs text-gray-500 text-center">
              {playbackMode
                ? `正在回放... (${playbackPoints.length} points)`
                : accumulationTime >= 1
                  ? `当前显示 ${displayPointCount.toLocaleString()} 个点，约 ${(displayPointCount / accumulationTime).toFixed(1)} 点/秒`
                  : `当前显示 ${displayPointCount.toLocaleString()} 个点`}
            </div>
          )}
        </div>

        {/* 右侧：侵入检测列表 */}
        {!isFullscreen && (
          <div className="lg:col-span-1 h-[600px]">
            <IntrusionHistoryPanel deviceId={deviceId!} onPlay={handlePlay} className="h-full" />
          </div>
        )}
      </div>
    </div >
  );
};
