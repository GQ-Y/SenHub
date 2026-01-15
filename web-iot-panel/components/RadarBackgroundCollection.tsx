import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { radarService } from '../src/api/services';
import { useModal } from '../hooks/useModal';
import { PointCloudRenderer } from './PointCloudRenderer';
import { RefreshCw, Trash2, Eye, Clock } from 'lucide-react';

interface Point {
  x: number;
  y: number;
  z: number;
  r?: number;
}

interface Background {
  backgroundId: string;
  deviceId: string;
  status: string;
  durationSeconds: number;
  gridResolution: number;
  frameCount: number;
  pointCount: number;
  createdAt?: string;
}

/**
 * 背景采集页面
 * 功能：1. 开始采集（自动定时结束）2. 查看已采集的背景列表 3. 选择背景进行3D预览
 */
export const RadarBackgroundCollection: React.FC = () => {
  const { deviceId } = useParams<{ deviceId: string }>();
  const modal = useModal();

  // 采集设置
  const [config, setConfig] = useState({ durationSeconds: 10, gridResolution: 0.05 });
  const [isCollecting, setIsCollecting] = useState(false);
  const [status, setStatus] = useState<any>(null);

  // 背景列表和预览
  const [backgrounds, setBackgrounds] = useState<Background[]>([]);
  const [selectedBackground, setSelectedBackground] = useState<Background | null>(null);
  const [previewPoints, setPreviewPoints] = useState<Point[]>([]);
  const [isLoadingPreview, setIsLoadingPreview] = useState(false);
  const [isLoadingList, setIsLoadingList] = useState(false);

  // 加载背景列表
  useEffect(() => {
    if (deviceId) {
      loadBackgrounds();
    }
  }, [deviceId]);

  // 采集状态轮询
  useEffect(() => {
    let interval: NodeJS.Timeout | null = null;

    if (isCollecting) {
      interval = setInterval(async () => {
        try {
          const response = await radarService.getBackgroundStatus(deviceId!);
          setStatus(response.data);

          // 检查采集是否完成
          if (response.data?.status === 'ready' || !response.data) {
            setIsCollecting(false);
            modal.showModal({ message: '背景采集完成', type: 'success' });
            loadBackgrounds(); // 刷新列表
          }
        } catch (err: any) {
          // 状态查询失败可能意味着采集已完成
          console.debug('获取采集状态失败（可能已完成）', err);
          setIsCollecting(false);
          loadBackgrounds();
        }
      }, 1000);
    }

    return () => {
      if (interval) clearInterval(interval);
    };
  }, [isCollecting, deviceId]);

  const loadBackgrounds = async () => {
    setIsLoadingList(true);
    try {
      const response = await radarService.getBackgrounds(deviceId!);
      setBackgrounds(response.data || []);
    } catch (err: any) {
      console.error('加载背景列表失败', err);
    } finally {
      setIsLoadingList(false);
    }
  };

  const handleStart = async () => {
    try {
      await radarService.startBackgroundCollection(deviceId!, config);
      setIsCollecting(true);
      setStatus({ status: 'collecting', progress: 0, frameCount: 0 });
      modal.showModal({ message: `开始采集背景（${config.durationSeconds}秒后自动完成）`, type: 'success' });
    } catch (err: any) {
      modal.showModal({ message: err.message || '启动采集失败', type: 'error' });
    }
  };

  const handleSelectBackground = async (bg: Background) => {
    setSelectedBackground(bg);
    setIsLoadingPreview(true);
    setPreviewPoints([]);

    try {
      const response = await radarService.getBackgroundPoints(deviceId!, bg.backgroundId, 50000);
      if (response.data?.points) {
        setPreviewPoints(response.data.points);
      } else {
        modal.showModal({ message: '该背景暂无点云数据', type: 'warning' });
      }
    } catch (err: any) {
      modal.showModal({ message: err.message || '加载背景点云失败', type: 'error' });
    } finally {
      setIsLoadingPreview(false);
    }
  };

  const handleClearPreview = () => {
    setSelectedBackground(null);
    setPreviewPoints([]);
  };

  return (
    <div className="space-y-6">
      {/* 操作栏 */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-white p-4 rounded-2xl shadow-sm border border-gray-100">
        <h1 className="text-2xl font-bold">背景采集</h1>
        <button
          onClick={loadBackgrounds}
          disabled={isLoadingList}
          className="flex items-center gap-2 px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors"
        >
          <RefreshCw size={16} className={isLoadingList ? 'animate-spin' : ''} />
          刷新列表
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 左侧：采集设置和背景列表 */}
        <div className="space-y-6">
          {/* 采集设置 */}
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
                  max="60"
                  disabled={isCollecting}
                />
                <p className="mt-1 text-xs text-gray-500">设置后自动采集，时间到达后自动完成</p>
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

            <div className="mt-6">
              <button
                onClick={handleStart}
                disabled={isCollecting}
                className="w-full px-4 py-3 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isCollecting ? (
                  <span className="flex items-center justify-center gap-2">
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    采集中... {status?.progress ? `${(status.progress * 100).toFixed(0)}%` : ''}
                  </span>
                ) : (
                  '开始采集'
                )}
              </button>
            </div>

            {/* 采集进度 */}
            {isCollecting && status && (
              <div className="mt-4 space-y-2">
                <div className="w-full bg-gray-200 rounded-full h-2.5">
                  <div
                    className="bg-blue-600 h-2.5 rounded-full transition-all duration-300"
                    style={{ width: `${(status.progress || 0) * 100}%` }}
                  />
                </div>
                <div className="flex justify-between text-xs text-gray-500">
                  <span>已采集 {status.frameCount || 0} 帧</span>
                  <span>剩余 {status.estimatedRemainingSeconds || 0} 秒</span>
                </div>
              </div>
            )}
          </div>

          {/* 已采集背景列表 */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <h2 className="text-lg font-semibold mb-4">已采集背景</h2>
            {backgrounds.length === 0 ? (
              <div className="text-center py-8 text-gray-400">
                <Clock size={48} className="mx-auto mb-2 opacity-50" />
                <p>暂无采集记录</p>
              </div>
            ) : (
              <div className="space-y-2 max-h-80 overflow-y-auto">
                {backgrounds.map((bg) => (
                  <div
                    key={bg.backgroundId}
                    className={`p-4 rounded-xl border cursor-pointer transition-all ${selectedBackground?.backgroundId === bg.backgroundId
                        ? 'border-blue-500 bg-blue-50'
                        : 'border-gray-100 hover:border-blue-300 hover:bg-gray-50'
                      }`}
                    onClick={() => handleSelectBackground(bg)}
                  >
                    <div className="flex justify-between items-start">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-sm">{bg.backgroundId.slice(0, 20)}...</span>
                          <span className={`px-2 py-0.5 rounded text-xs ${bg.status === 'ready' ? 'bg-green-100 text-green-700' :
                              bg.status === 'collecting' ? 'bg-blue-100 text-blue-700' :
                                'bg-gray-100 text-gray-700'
                            }`}>
                            {bg.status === 'ready' ? '就绪' : bg.status === 'collecting' ? '采集中' : bg.status}
                          </span>
                        </div>
                        <div className="text-xs text-gray-500 mt-1">
                          {bg.pointCount?.toLocaleString() || 0} 点 | {bg.durationSeconds}秒 | 精度{bg.gridResolution}m
                        </div>
                      </div>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleSelectBackground(bg);
                        }}
                        className="p-2 text-blue-600 hover:bg-blue-100 rounded-lg transition-colors"
                        title="预览"
                      >
                        <Eye size={16} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* 右侧：3D点云预览 */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-semibold">3D点云预览</h2>
            {selectedBackground && (
              <button
                onClick={handleClearPreview}
                className="text-sm text-gray-500 hover:text-gray-700"
              >
                清除预览
              </button>
            )}
          </div>
          <div className="w-full h-96 rounded-lg overflow-hidden bg-gray-900">
            {isLoadingPreview ? (
              <div className="w-full h-full flex items-center justify-center text-gray-400">
                <div className="text-center">
                  <div className="inline-block w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mb-2"></div>
                  <p>加载点云数据...</p>
                </div>
              </div>
            ) : previewPoints.length > 0 ? (
              <PointCloudRenderer
                points={previewPoints}
                colorMode="reflectivity"
                pointSize={0.015}
                backgroundColor="#0a0a0a"
                showGrid={true}
                showAxes={true}
                showRangeRings={true}
              />
            ) : (
              <div className="w-full h-full flex items-center justify-center text-gray-400">
                <div className="text-center">
                  <Eye size={48} className="mx-auto mb-2 opacity-50" />
                  <p>选择一个背景进行预览</p>
                </div>
              </div>
            )}
          </div>
          {previewPoints.length > 0 && (
            <div className="mt-2 text-xs text-gray-500 text-center">
              显示 {previewPoints.length.toLocaleString()} 个点
              {selectedBackground && (
                <span className="ml-2">| 背景: {selectedBackground.backgroundId.slice(0, 15)}...</span>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
