import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { radarService } from '../src/api/services';
import { useModal } from '../hooks/useModal';

/**
 * 背景采集页面
 */
export const RadarBackgroundCollection: React.FC = () => {
  const { deviceId } = useParams<{ deviceId: string }>();
  const modal = useModal();
  const [isCollecting, setIsCollecting] = useState(false);
  const [status, setStatus] = useState<any>(null);
  const [config, setConfig] = useState({ durationSeconds: 10, gridResolution: 0.05 });
  const [statusInterval, setStatusInterval] = useState<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (isCollecting) {
      const interval = setInterval(async () => {
        try {
          const response = await radarService.getBackgroundStatus(deviceId!);
          setStatus(response.data);
          if (response.data && response.data.status === 'ready') {
            setIsCollecting(false);
            if (statusInterval) clearInterval(statusInterval);
            modal.showModal({ message: '背景采集完成', type: 'success' });
          }
        } catch (err) {
          console.error('获取采集状态失败', err);
        }
      }, 1000);
      setStatusInterval(interval);
    } else {
      if (statusInterval) {
        clearInterval(statusInterval);
        setStatusInterval(null);
      }
    }

    return () => {
      if (statusInterval) clearInterval(statusInterval);
    };
  }, [isCollecting, deviceId]);

  const handleStart = async () => {
    try {
      await radarService.startBackgroundCollection(deviceId!, config);
      setIsCollecting(true);
      modal.showModal({ message: '开始采集背景', type: 'success' });
    } catch (err: any) {
      modal.showModal({ message: err.message || '启动采集失败', type: 'error' });
    }
  };

  const handleStop = async () => {
    try {
      await radarService.stopBackgroundCollection(deviceId!);
      setIsCollecting(false);
      modal.showModal({ message: '停止采集', type: 'success' });
    } catch (err: any) {
      modal.showModal({ message: err.message || '停止采集失败', type: 'error' });
    }
  };

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">背景采集</h1>

      <div className="bg-white rounded-lg shadow p-6 mb-6">
        <h2 className="text-lg font-semibold mb-4">采集设置</h2>
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1">采集时长（秒）</label>
            <input
              type="number"
              value={config.durationSeconds}
              onChange={(e) => setConfig({ ...config, durationSeconds: parseInt(e.target.value) || 10 })}
              className="w-full px-3 py-2 border rounded"
              min="5"
              max="30"
              disabled={isCollecting}
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">网格精度（米）</label>
            <input
              type="number"
              step="0.01"
              value={config.gridResolution}
              onChange={(e) => setConfig({ ...config, gridResolution: parseFloat(e.target.value) || 0.05 })}
              className="w-full px-3 py-2 border rounded"
              min="0.01"
              max="0.2"
              disabled={isCollecting}
            />
          </div>
        </div>

        <div className="mt-6 flex space-x-3">
          {!isCollecting ? (
            <button
              onClick={handleStart}
              className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
            >
              开始采集
            </button>
          ) : (
            <button
              onClick={handleStop}
              className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700"
            >
              停止采集
            </button>
          )}
        </div>
      </div>

      {status && (
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold mb-4">采集状态</h2>
          <div className="space-y-2">
            <div>状态: {status.status}</div>
            <div>进度: {(status.progress * 100).toFixed(1)}%</div>
            <div className="w-full bg-gray-200 rounded-full h-2.5">
              <div
                className="bg-blue-600 h-2.5 rounded-full"
                style={{ width: `${status.progress * 100}%` }}
              />
            </div>
            <div>已采集帧数: {status.frameCount}</div>
            <div>预计剩余: {status.estimatedRemainingSeconds}秒</div>
          </div>
        </div>
      )}

      <div className="bg-white rounded-lg shadow p-6 mt-6">
        <h2 className="text-lg font-semibold mb-4">3D点云预览</h2>
        <div className="w-full h-96 bg-gray-100 rounded flex items-center justify-center">
          <p className="text-gray-500">Three.js点云渲染区域（待实现）</p>
        </div>
      </div>
    </div>
  );
};
