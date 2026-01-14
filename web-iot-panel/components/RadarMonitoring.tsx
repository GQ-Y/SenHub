import React, { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { radarService } from '../src/api/services';
import { useModal } from '../hooks/useModal';

/**
 * 实时监控页面
 */
export const RadarMonitoring: React.FC = () => {
  const { deviceId } = useParams<{ deviceId: string }>();
  const modal = useModal();
  const [isConnected, setIsConnected] = useState(false);
  const [intrusions, setIntrusions] = useState<any[]>([]);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    loadIntrusions();
    
    // 连接WebSocket
    if (deviceId) {
      const ws = radarService.connectWebSocket(deviceId, (data) => {
        if (data.type === 'intrusion') {
          setIntrusions(prev => [data, ...prev].slice(0, 50)); // 保留最近50条
        }
      });
      wsRef.current = ws;
      setIsConnected(true);
    }

    return () => {
      if (wsRef.current) {
        wsRef.current.close();
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

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">实时监控</h1>
        <div className="flex items-center space-x-2">
          <div className={`w-3 h-3 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`} />
          <span className="text-sm">{isConnected ? '已连接' : '未连接'}</span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold mb-4">实时点云渲染</h2>
          <div className="w-full h-96 bg-gray-100 rounded flex items-center justify-center">
            <p className="text-gray-500">Three.js点云渲染区域（待实现）</p>
          </div>
        </div>

        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold mb-4">侵入检测列表</h2>
          <div className="space-y-2 max-h-96 overflow-y-auto">
            {intrusions.length === 0 ? (
              <p className="text-gray-500 text-center py-8">暂无侵入记录</p>
            ) : (
              intrusions.map((intrusion, index) => (
                <div key={index} className="border rounded p-3">
                  <div className="text-sm">
                    <div>时间: {new Date(intrusion.detectedAt).toLocaleString()}</div>
                    <div>位置: ({intrusion.centroid?.x?.toFixed(2)}, {intrusion.centroid?.y?.toFixed(2)}, {intrusion.centroid?.z?.toFixed(2)})</div>
                    <div>体积: {intrusion.volume?.toFixed(3)} m³</div>
                    <div>点数: {intrusion.pointCount}</div>
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
