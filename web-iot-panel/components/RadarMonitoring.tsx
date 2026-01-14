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
}

interface IntrusionPoint extends Point {
  isIntrusion?: boolean;
}

/**
 * 实时监控页面
 */
export const RadarMonitoring: React.FC = () => {
  const { deviceId } = useParams<{ deviceId: string }>();
  const modal = useModal();
  const [isConnected, setIsConnected] = useState(false);
  const [intrusions, setIntrusions] = useState<any[]>([]);
  const [pointCloudData, setPointCloudData] = useState<Point[]>([]);
  const [intrusionPoints, setIntrusionPoints] = useState<Point[]>([]);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    loadIntrusions();
    
    // 连接WebSocket
    if (deviceId) {
      try {
        console.log('开始连接WebSocket，deviceId:', deviceId);
        const ws = radarService.connectWebSocket(deviceId, (data) => {
          console.log('收到WebSocket消息:', data.type, data);
          
          if (data.type === 'pointcloud' && data.points) {
            // 更新点云数据
            const points: Point[] = data.points.map((p: any) => ({
              x: p.x || 0,
              y: p.y || 0,
              z: p.z || 0,
              r: p.r
            }));
            setPointCloudData(points);
            console.log('更新点云数据，点数:', points.length);
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
          console.log('WebSocket连接已建立');
          setIsConnected(true);
        };
        
        ws.onerror = (error) => {
          console.error('WebSocket错误:', error);
          setIsConnected(false);
        };
        
        ws.onclose = (event) => {
          console.log('WebSocket连接已关闭:', event.code, event.reason);
          setIsConnected(false);
        };
      } catch (err) {
        console.error('WebSocket连接失败', err);
        setIsConnected(false);
      }
    }

    return () => {
      if (wsRef.current) {
        console.log('关闭WebSocket连接');
        wsRef.current.close();
        wsRef.current = null;
      }
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

  // 合并点云数据：背景点云（灰色）+ 侵入点（红色）
  const allPoints: Point[] = [
    ...pointCloudData.map(p => ({ ...p, isIntrusion: false })),
    ...intrusionPoints.map(p => ({ ...p, isIntrusion: true }))
  ];

  return (
    <div className="space-y-6">
      {/* 操作栏 */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-white p-4 rounded-2xl shadow-sm border border-gray-100">
        <h1 className="text-2xl font-bold">实时监控</h1>
        <div className="flex items-center space-x-2">
          <div className={`w-3 h-3 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`} />
          <span className="text-sm">{isConnected ? '已连接' : '未连接'}</span>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 左侧：实时点云渲染 */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <h2 className="text-lg font-semibold mb-4">实时点云渲染</h2>
          <div className="w-full h-96 rounded-lg overflow-hidden bg-gray-900">
            {allPoints.length > 0 ? (
              <PointCloudRenderer
                points={allPoints}
                color={(point: Point) => {
                  // 侵入点显示为红色，其他点显示为白色/蓝色
                  const p = point as any;
                  return p.isIntrusion ? '#ff0000' : '#ffffff';
                }}
                pointSize={0.015}
                backgroundColor="#000000"
                showGrid={true}
                showAxes={true}
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
          </div>
          {allPoints.length > 0 && (
            <div className="mt-2 text-xs text-gray-500 text-center">
              当前显示 {allPoints.length} 个点
              {intrusionPoints.length > 0 && (
                <span className="ml-2 text-red-500">
                  （{intrusionPoints.length} 个侵入点）
                </span>
              )}
            </div>
          )}
        </div>

        {/* 右侧：侵入检测列表 */}
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
      </div>
    </div>
  );
};
