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
  const [pointCloudData, setPointCloudData] = useState<Point[]>([]);
  const pointCloudIntervalRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (isCollecting) {
      // 状态查询间隔
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

      // 点云数据获取间隔（每500ms获取一次，用于实时渲染）
      const pointCloudInterval = setInterval(async () => {
        try {
          const response = await radarService.getCollectingPointCloud(deviceId!, 5000);
          if (response.data && response.data.points) {
            setPointCloudData(response.data.points);
          }
        } catch (err) {
          // 忽略错误，继续尝试
          console.debug('获取点云数据失败（可能还未开始采集）', err);
        }
      }, 500);
      pointCloudIntervalRef.current = pointCloudInterval;
    } else {
      if (statusInterval) {
        clearInterval(statusInterval);
        setStatusInterval(null);
      }
      if (pointCloudIntervalRef.current) {
        clearInterval(pointCloudIntervalRef.current);
        pointCloudIntervalRef.current = null;
      }
      setPointCloudData([]); // 清空点云数据
    }

    return () => {
      if (statusInterval) clearInterval(statusInterval);
      if (pointCloudIntervalRef.current) clearInterval(pointCloudIntervalRef.current);
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
    <div className="space-y-6">
      {/* 操作栏 */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-white p-4 rounded-2xl shadow-sm border border-gray-100">
        <h1 className="text-2xl font-bold">背景采集</h1>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 左侧：采集设置和状态 */}
        <div className="space-y-6">
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <h2 className="text-lg font-semibold mb-4">采集设置</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  采集时长（秒）<span className="text-red-500">*</span>
                </label>
                <input
                  type="number"
                  value={config.durationSeconds}
                  onChange={(e) => setConfig({ ...config, durationSeconds: parseInt(e.target.value) || 10 })}
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                  min="5"
                  max="30"
                  disabled={isCollecting}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  网格精度（米）<span className="text-red-500">*</span>
                </label>
                <input
                  type="number"
                  step="0.01"
                  value={config.gridResolution}
                  onChange={(e) => setConfig({ ...config, gridResolution: parseFloat(e.target.value) || 0.05 })}
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                  min="0.01"
                  max="0.2"
                  disabled={isCollecting}
                />
                <p className="mt-1 text-xs text-gray-500">推荐值：0.05（5cm）</p>
              </div>
            </div>

            <div className="mt-6 flex space-x-3">
              {!isCollecting ? (
                <button
                  onClick={handleStart}
                  className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200"
                >
                  开始采集
                </button>
              ) : (
                <button
                  onClick={handleStop}
                  className="flex-1 px-4 py-2 bg-red-600 text-white rounded-xl hover:bg-red-700 transition-colors font-medium"
                >
                  停止采集
                </button>
              )}
            </div>
          </div>

          {status && (
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <h2 className="text-lg font-semibold mb-4">采集状态</h2>
              <div className="space-y-3">
                <div className="flex justify-between items-center">
                  <span className="text-sm text-gray-600">状态</span>
                  <span className={`px-3 py-1 rounded-full text-xs font-medium ${
                    status.status === 'collecting' ? 'bg-blue-100 text-blue-700' : 
                    status.status === 'ready' ? 'bg-green-100 text-green-700' : 
                    'bg-gray-100 text-gray-700'
                  }`}>
                    {status.status === 'collecting' ? '采集中' : 
                     status.status === 'ready' ? '已完成' : status.status}
                  </span>
                </div>
                <div>
                  <div className="flex justify-between items-center mb-1">
                    <span className="text-sm text-gray-600">进度</span>
                    <span className="text-sm font-medium">{(status.progress * 100).toFixed(1)}%</span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2.5">
                    <div
                      className="bg-blue-600 h-2.5 rounded-full transition-all duration-300"
                      style={{ width: `${status.progress * 100}%` }}
                    />
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-4 pt-2 border-t border-gray-100">
                  <div>
                    <div className="text-xs text-gray-500">已采集帧数</div>
                    <div className="text-lg font-semibold">{status.frameCount || 0}</div>
                  </div>
                  <div>
                    <div className="text-xs text-gray-500">预计剩余</div>
                    <div className="text-lg font-semibold">{status.estimatedRemainingSeconds || 0}秒</div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* 右侧：3D点云预览 */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <h2 className="text-lg font-semibold mb-4">3D点云预览</h2>
          <div className="w-full h-96 rounded-lg overflow-hidden bg-gray-900">
            {pointCloudData.length > 0 ? (
              <PointCloudRenderer
                points={pointCloudData}
                color="#888888"
                pointSize={0.02}
                backgroundColor="#000000"
                showGrid={true}
                showAxes={true}
              />
            ) : (
              <div className="w-full h-full flex items-center justify-center text-gray-400">
                {isCollecting ? (
                  <div className="text-center">
                    <div className="inline-block w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mb-2"></div>
                    <p>等待点云数据...</p>
                  </div>
                ) : (
                  <p>开始采集后，点云数据将在此显示</p>
                )}
              </div>
            )}
          </div>
          {pointCloudData.length > 0 && (
            <div className="mt-2 text-xs text-gray-500 text-center">
              当前显示 {pointCloudData.length} 个点
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
