import React, { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { radarService } from '../src/api/services';
import { useModal } from '../hooks/useModal';
import { PointCloudRenderer } from './PointCloudRenderer';

interface Point {
  x: number;
  y: number;
  z: number;
  r?: number;
  timestamp?: number; // 点云时间戳
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
  const modal = useModal();
  const [isConnected, setIsConnected] = useState(false);
  const [intrusions, setIntrusions] = useState<any[]>([]);
  const [pointCloudData, setPointCloudData] = useState<TimestampedPoint[]>([]);
  const [intrusionPoints, setIntrusionPoints] = useState<Point[]>([]);
  const wsRef = useRef<WebSocket | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const pointCloudContainerRef = useRef<HTMLDivElement>(null);
  const [pointCloudStats, setPointCloudStats] = useState({ frameCount: 0, totalPoints: 0, lastUpdate: Date.now() });

  // 累积时间配置（秒）- 关键参数！
  // Mid-360 是非重复扫描模式，需要较长时间累积才能形成完整点云
  // 上位机通常累积 10+ 秒才能达到 60 万点的密度
  const [accumulationTime, setAccumulationTime] = useState(10); // 默认 10 秒
  const accumulationTimeRef = useRef(accumulationTime * 1000); // 毫秒

  // 同步更新 ref
  useEffect(() => {
    accumulationTimeRef.current = accumulationTime * 1000;
  }, [accumulationTime]);

  // 点云缓冲区：累积指定时间内的所有点云数据
  const pointCloudBufferRef = useRef<TimestampedPoint[]>([]);
  const renderIntervalRef = useRef<number | null>(null);
  const lastRenderTimeRef = useRef<number>(Date.now());

  useEffect(() => {
    // 使用标志位防止 React StrictMode 导致的重复连接问题
    let isCleaningUp = false;
    let ws: WebSocket | null = null;

    loadIntrusions();

    // 连接WebSocket
    if (deviceId) {
      try {
        console.log('开始连接WebSocket，deviceId:', deviceId);
        ws = radarService.connectWebSocket(deviceId, (data) => {
          // 如果正在清理，忽略消息
          if (isCleaningUp) return;

          // 减少日志输出频率，避免控制台刷屏
          if (data.type !== 'pointcloud') {
            console.log('收到WebSocket消息:', data.type, data);
          }

          if (data.type === 'pointcloud' && data.points) {
            // 接收点云数据，添加时间戳后放入缓冲区（不立即触发渲染）
            const frameTimestamp = data.timestamp || Date.now();
            const newPoints: TimestampedPoint[] = data.points.map((p: any) => ({
              x: p.x || 0,
              y: p.y || 0,
              z: p.z || 0,
              r: p.r,
              timestamp: frameTimestamp
            }));

            // 更新统计信息
            setPointCloudStats(prev => ({
              frameCount: prev.frameCount + 1,
              totalPoints: prev.totalPoints + newPoints.length,
              lastUpdate: Date.now()
            }));

            // 将新点添加到缓冲区（不直接更新状态，由定时器统一处理）
            const now = Date.now();
            const cutoffTime = now - accumulationTimeRef.current;

            // 清理超过累积时间的旧数据并添加新点
            pointCloudBufferRef.current = [
              ...pointCloudBufferRef.current.filter(p => p.timestamp > cutoffTime),
              ...newPoints
            ];
          } else if (data.type === 'intrusion') {
            // 处理侵入检测结果
            setIntrusions(prev => [data, ...prev].slice(0, 50)); // 保留最近50条

            // 提取侵入物体点云（如果有）
            if (data.cluster && data.cluster.points) {
              const intrusionPts: Point[] = data.cluster.points.map((p: any) => ({
                x: p.x || 0,
                y: p.y || 0,
                z: p.z || 0
              }));
              setIntrusionPoints(prev => [...prev, ...intrusionPts].slice(-1000)); // 保留最近1000个侵入点
            }
          } else if (data.type === 'status') {
            setIsConnected(data.connected || false);
            console.log('WebSocket状态更新:', data.connected);
          }
        });
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

    // 核心改进：定时更新渲染，累积指定时间窗口内的点云数据
    renderIntervalRef.current = window.setInterval(() => {
      if (isCleaningUp) return;

      const now = Date.now();
      const cutoffTime = now - accumulationTimeRef.current;

      // 获取缓冲区中累积时间窗口内的数据
      const recentPoints = pointCloudBufferRef.current.filter(p => p.timestamp > cutoffTime);

      // 只在有数据时更新
      if (recentPoints.length > 0) {
        const timeSinceLastRender = now - lastRenderTimeRef.current;

        // 批量更新点云数据
        setPointCloudData(recentPoints);
        lastRenderTimeRef.current = now;

        // 减少日志输出频率（每秒打印一次）
        if (timeSinceLastRender >= 1000) {
          const accTimeSeconds = accumulationTimeRef.current / 1000;
          console.log(`[点云累积] 点数: ${recentPoints.length.toLocaleString()}, 累积时间: ${accTimeSeconds}s`);
        }
      }

      // 清理缓冲区中的旧数据
      pointCloudBufferRef.current = recentPoints;
    }, 200); // 每200ms检查一次

    return () => {
      // 设置清理标志，防止后续事件触发错误日志
      isCleaningUp = true;

      if (ws) {
        console.log('关闭WebSocket连接');
        ws.close(1000, '组件卸载'); // 使用正常关闭码
        wsRef.current = null;
      }
      if (renderIntervalRef.current) {
        clearInterval(renderIntervalRef.current);
        renderIntervalRef.current = null;
      }
      // 清空缓冲区
      pointCloudBufferRef.current = [];
    };
  }, [deviceId]);

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

  // 合并点云数据：背景点云（灰色）+ 侵入点（红色）
  // 移除timestamp字段，因为PointCloudRenderer不需要
  const allPoints: Point[] = [
    ...pointCloudData.map(({ timestamp, ...p }) => ({ ...p, isIntrusion: false })),
    ...intrusionPoints.map(p => ({ ...p, isIntrusion: true }))
  ];

  return (
    <div className="space-y-6">
      {/* 操作栏 */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-white p-4 rounded-2xl shadow-sm border border-gray-100">
        <h1 className="text-2xl font-bold">实时监控</h1>
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
              <option value={10}>10秒</option>
              <option value={20}>20秒</option>
              <option value={30}>30秒</option>
              <option value={60}>60秒</option>
            </select>
          </div>

          {/* 连接状态 */}
          <div className="flex items-center space-x-2">
            <div className={`w-3 h-3 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`} />
            <span className="text-sm">{isConnected ? '已连接' : '未连接'}</span>
          </div>
        </div>
      </div>

      <div className={`grid ${isFullscreen ? 'fixed inset-0 z-50 bg-black' : 'grid-cols-1 lg:grid-cols-2 gap-6'}`}>
        {/* 左侧：实时点云渲染 */}
        <div
          ref={pointCloudContainerRef}
          className={`bg-white rounded-xl shadow-sm border border-gray-100 p-6 ${isFullscreen ? 'w-full h-full rounded-none border-0' : ''}`}
        >
          <div className={`flex justify-between items-center mb-4 ${isFullscreen ? 'absolute top-4 left-4 right-4 z-10' : ''}`}>
            <h2 className="text-lg font-semibold">实时点云渲染</h2>
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
          <div className={`w-full ${isFullscreen ? 'h-full' : 'h-96'} rounded-lg overflow-hidden bg-gray-900 relative`}>
            {allPoints.length > 0 ? (
              <PointCloudRenderer
                points={allPoints}
                color={(point: Point) => {
                  // 侵入点显示为红色，正常点使用colorMode处理
                  const p = point as any;
                  if (p.isIntrusion) {
                    return '#ff0000'; // 红色标记侵入点
                  }
                  return ''; // 返回空字符串，由colorMode处理
                }}
                colorMode="reflectivity"
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
            {isFullscreen && allPoints.length > 0 && (
              <>
                <div className="absolute bottom-4 left-4 bg-black bg-opacity-70 text-white px-4 py-2 rounded text-sm space-y-1">
                  <div>
                    当前显示 {allPoints.length.toLocaleString()} 个点
                    {intrusionPoints.length > 0 && (
                      <span className="ml-2 text-red-400">
                        （{intrusionPoints.length} 个侵入点）
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-gray-300">
                    已接收 {pointCloudStats.frameCount} 帧，累计 {pointCloudStats.totalPoints.toLocaleString()} 个点
                  </div>
                </div>
                <div className="absolute bottom-4 right-4 bg-black bg-opacity-70 text-white px-4 py-2 rounded text-xs">
                  <div>操作提示：</div>
                  <div>左键拖拽旋转 | 滚轮缩放 | 右键拖拽平移</div>
                  <div className="mt-1">按 ESC 或点击退出全屏</div>
                </div>
              </>
            )}
          </div>
          {!isFullscreen && allPoints.length > 0 && (
            <div className="mt-2 space-y-1">
              <div className="text-xs text-gray-500 text-center">
                当前显示 {allPoints.length} 个点
                {intrusionPoints.length > 0 && (
                  <span className="ml-2 text-red-500">
                    （{intrusionPoints.length} 个侵入点）
                  </span>
                )}
              </div>
              <div className="text-xs text-gray-400 text-center">
                统计：已接收 {pointCloudStats.frameCount} 帧，累计 {pointCloudStats.totalPoints.toLocaleString()} 个点
              </div>
              <div className="text-xs text-gray-400 text-center mt-1">
                操作提示：鼠标左键拖拽旋转视角 | 滚轮缩放 | 鼠标右键拖拽平移 | 点击全屏按钮进入全屏模式
              </div>
            </div>
          )}
        </div>

        {/* 右侧：侵入检测列表 */}
        {!isFullscreen && (
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-lg font-semibold">侵入检测列表</h2>
              <button
                onClick={loadIntrusions}
                className="text-sm text-blue-600 hover:text-blue-800"
              >
                刷新
              </button>
            </div>
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {intrusions.length === 0 ? (
                <p className="text-gray-500 text-center py-8">暂无侵入记录</p>
              ) : (
                intrusions.map((intrusion, index) => (
                  <div key={index} className="border rounded-lg p-3 hover:bg-gray-50 transition-colors">
                    <div className="text-sm space-y-1">
                      <div className="font-medium text-gray-800">
                        {new Date(intrusion.detectedAt).toLocaleString()}
                      </div>
                      <div className="text-gray-600">
                        <span className="font-medium">位置：</span>
                        ({intrusion.centroid?.x?.toFixed(2) || 0},
                        {intrusion.centroid?.y?.toFixed(2) || 0},
                        {intrusion.centroid?.z?.toFixed(2) || 0})
                      </div>
                      <div className="flex justify-between text-xs text-gray-500">
                        <span>体积: {intrusion.volume?.toFixed(3) || 0} m³</span>
                        <span>点数: {intrusion.pointCount || 0}</span>
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}
      </div>
    </div >
  );
};
