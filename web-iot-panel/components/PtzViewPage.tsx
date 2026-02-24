import React, { useState, useEffect } from 'react';
import { RefreshCw, MapPin, Compass, Eye, Maximize2 } from 'lucide-react';
import { deviceService } from '../src/api/services';
import { PtzView3D } from './PtzView3D';
import { useAppContext } from '../contexts/AppContext';

interface PtzViewPageProps {
  deviceId: string;
}

interface PtzPositionData {
  deviceId: string;
  ptzEnabled: boolean;
  pan: number;
  tilt: number;
  zoom: number;
  azimuth: number;
  horizontalFov: number;
  verticalFov: number;
  visibleRadius: number;
  lastUpdated: string | null;
  message?: string;
  position?: {
    latitude: number;
    longitude: number;
  };
}

/**
 * PTZ视角页面组件
 * 显示球机的3D视角仿真和GIS信息
 */
export const PtzViewPage: React.FC<PtzViewPageProps> = ({ deviceId }) => {
  const { t } = useAppContext();
  const [ptzData, setPtzData] = useState<PtzPositionData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  // 加载PTZ位置信息
  const loadPtzPosition = async () => {
    try {
      setIsLoading(true);
      setError(null);
      const response = await deviceService.getPtzPosition(deviceId);
      setPtzData(response.data);
    } catch (err: any) {
      console.error('获取PTZ位置失败:', err);
      setError(err.message || '获取PTZ位置失败');
    } finally {
      setIsLoading(false);
    }
  };

  // 刷新PTZ位置
  const handleRefresh = async () => {
    try {
      setIsRefreshing(true);
      setError(null);
      await deviceService.refreshPtzPosition(deviceId);
      // 刷新后重新获取
      await loadPtzPosition();
    } catch (err: any) {
      console.error('刷新PTZ位置失败:', err);
      setError(err.message || '刷新PTZ位置失败');
    } finally {
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    loadPtzPosition();
    // 每5秒自动刷新一次
    const interval = setInterval(loadPtzPosition, 5000);
    return () => clearInterval(interval);
  }, [deviceId]);

  // 格式化角度显示（兼容 undefined/null）
  const formatAngle = (angle: number | undefined | null) => {
    if (angle == null || typeof angle !== 'number' || Number.isNaN(angle)) {
      return '—';
    }
    return `${angle.toFixed(1)}°`;
  };

  // 格式化坐标显示
  const formatCoordinate = (lat: number, lon: number) => {
    const latDir = lat >= 0 ? 'N' : 'S';
    const lonDir = lon >= 0 ? 'E' : 'W';
    const latAbs = Math.abs(lat);
    const lonAbs = Math.abs(lon);
    const latDeg = Math.floor(latAbs);
    const latMin = Math.floor((latAbs - latDeg) * 60);
    const latSec = ((latAbs - latDeg) * 60 - latMin) * 60;
    const lonDeg = Math.floor(lonAbs);
    const lonMin = Math.floor((lonAbs - lonDeg) * 60);
    const lonSec = ((lonAbs - lonDeg) * 60 - lonMin) * 60;
    return `${latDir} ${latDeg}°${latMin}'${latSec.toFixed(2)}", ${lonDir} ${lonDeg}°${lonMin}'${lonSec.toFixed(2)}"`;
  };

  // 获取朝向描述
  const getDirection = (azimuth: number) => {
    const directions = ['正北', '东北', '正东', '东南', '正南', '西南', '正西', '西北'];
    const index = Math.round(azimuth / 45) % 8;
    return directions[index];
  };

  if (isLoading && !ptzData) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center">
          <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
          <p className="text-gray-500">正在加载PTZ信息...</p>
        </div>
      </div>
    );
  }

  if (error && !ptzData) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center">
          <p className="text-red-500 mb-4">{error}</p>
          <button
            onClick={loadPtzPosition}
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            重试
          </button>
        </div>
      </div>
    );
  }

  if (!ptzData) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center text-gray-500">
          <p>暂无PTZ位置信息</p>
          <button
            onClick={handleRefresh}
            className="mt-4 px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            刷新位置
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 控制栏 */}
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-4">
          <button
            onClick={handleRefresh}
            disabled={isRefreshing}
            className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <RefreshCw size={18} className={isRefreshing ? 'animate-spin' : ''} />
            <span>刷新位置</span>
          </button>
          <button
            onClick={() => setPtzData({
              deviceId,
              ptzEnabled: true,
              pan: 79.5,
              tilt: 0.1,
              zoom: 1.0,
              azimuth: 79.54,
              horizontalFov: 57.53,
              verticalFov: 33.2,
              visibleRadius: 13.8,
              lastUpdated: new Date().toISOString(),
              position: { latitude: 0.0, longitude: 0.0 }
            })}
            className="flex items-center space-x-2 px-4 py-2 bg-white border border-gray-200 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
          >
            <Eye size={18} />
            <span>加载模拟数据</span>
          </button>
          {ptzData.ptzEnabled && (
            <span className="px-3 py-1 bg-green-100 text-green-800 text-sm rounded-full">
              PTZ监控已启用
            </span>
          )}
        </div>
        {ptzData.lastUpdated && (
          <span className="text-sm text-gray-500">
            最后更新: {new Date(ptzData.lastUpdated).toLocaleString()}
          </span>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 3D视角区域 */}
        <div className="lg:col-span-2">
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-4">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-gray-800">3D视角仿真</h3>
              <button
                onClick={() => setIsFullscreen(!isFullscreen)}
                className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded transition-colors"
                title="全屏"
              >
                <Maximize2 size={18} />
              </button>
            </div>
            <div className={`${isFullscreen ? 'fixed inset-0 z-50 bg-gray-900' : 'relative'} rounded-lg overflow-hidden`} style={{ height: isFullscreen ? '100vh' : '600px' }}>
              <PtzView3D
                pan={ptzData.pan}
                tilt={ptzData.tilt}
                zoom={ptzData.zoom}
                azimuth={ptzData.azimuth}
                horizontalFov={ptzData.horizontalFov || 60}
                verticalFov={ptzData.verticalFov || 40}
                visibleRadius={ptzData.visibleRadius || 20}
                position={ptzData.position}
              />
              {isFullscreen && (
                <button
                  onClick={() => setIsFullscreen(false)}
                  className="absolute top-4 right-4 p-2 bg-white bg-opacity-20 hover:bg-opacity-30 text-white rounded transition-colors"
                >
                  <Maximize2 size={18} />
                </button>
              )}
            </div>
          </div>
        </div>

        {/* GIS信息面板 */}
        <div className="space-y-6">
          {/* GIS位置信息 */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <h3 className="text-lg font-semibold text-gray-800 mb-4 flex items-center">
              <MapPin size={20} className="mr-2 text-blue-600" />
              GIS信息
            </h3>
            <div className="space-y-3">
              <div>
                <label className="text-sm text-gray-500">位置</label>
                <p className="text-gray-800 font-medium">
                  {ptzData.position 
                    ? formatCoordinate(ptzData.position.latitude, ptzData.position.longitude)
                    : 'N 0°0\'0.00", E 0°0\'0.00"'}
                </p>
              </div>
              <div>
                <label className="text-sm text-gray-500">坐标</label>
                <p className="text-gray-800 font-medium">
                  ({ptzData.position?.latitude?.toFixed(6) || '0.0'}, {ptzData.position?.longitude?.toFixed(6) || '0.0'})
                </p>
              </div>
              <div>
                <label className="text-sm text-gray-500">朝向</label>
                <p className="text-gray-800 font-medium flex items-center">
                  <Compass size={16} className="mr-1" />
                  {formatAngle(ptzData.azimuth)} {ptzData.azimuth != null && !Number.isNaN(ptzData.azimuth) ? `(${getDirection(ptzData.azimuth)})` : ''}
                </p>
              </div>
            </div>
          </div>

          {/* PTZ参数信息 */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <h3 className="text-lg font-semibold text-gray-800 mb-4 flex items-center">
              <Eye size={20} className="mr-2 text-blue-600" />
              PTZ参数
            </h3>
            <div className="space-y-3">
              <div>
                <label className="text-sm text-gray-500">水平角度</label>
                <p className="text-gray-800 font-medium">{formatAngle(ptzData.pan)}</p>
              </div>
              <div>
                <label className="text-sm text-gray-500">垂直角度</label>
                <p className="text-gray-800 font-medium">{formatAngle(ptzData.tilt)}</p>
              </div>
              <div>
                <label className="text-sm text-gray-500">变倍</label>
                <p className="text-gray-800 font-medium">{ptzData.zoom != null && !Number.isNaN(ptzData.zoom) ? `${ptzData.zoom.toFixed(1)}x` : '—'}</p>
              </div>
            </div>
          </div>

          {/* 视场角信息 */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <h3 className="text-lg font-semibold text-gray-800 mb-4">视场角</h3>
            <div className="space-y-3">
              <div>
                <label className="text-sm text-gray-500">水平视场角</label>
                <p className="text-gray-800 font-medium">{formatAngle(ptzData.horizontalFov || 60)}</p>
              </div>
              <div>
                <label className="text-sm text-gray-500">垂直视场角</label>
                <p className="text-gray-800 font-medium">{formatAngle(ptzData.verticalFov || 40)}</p>
              </div>
              <div>
                <label className="text-sm text-gray-500">可视半径</label>
                <p className="text-gray-800 font-medium">{(ptzData.visibleRadius || 0).toFixed(1)}m</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
