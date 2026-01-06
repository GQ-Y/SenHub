import React, { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  ArrowLeft, 
  Camera, 
  Settings, 
  ZoomIn, 
  ZoomOut, 
  RotateCw,
  MoreHorizontal,
  Calendar,
  Download,
  Maximize,
  Minimize
} from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { deviceService } from '../src/api/services';
import { Device, DeviceStatus } from '../types';

export const DeviceDetail: React.FC = () => {
  const { deviceId } = useParams<{ deviceId: string }>();
  const navigate = useNavigate();
  const { t, language } = useAppContext();
  const [device, setDevice] = useState<Device | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [snapshot, setSnapshot] = useState<string | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [isLoadingSnapshot, setIsLoadingSnapshot] = useState(false);
  const imageContainerRef = useRef<HTMLDivElement>(null);

  // 加载设备详情
  useEffect(() => {
    const loadDevice = async () => {
      if (!deviceId) return;
      
      setIsLoading(true);
      try {
        const response = await deviceService.getDevice(deviceId);
        const deviceData = response.data;
        const deviceInfo = {
          id: deviceData.id || deviceId,
          name: deviceData.name || '',
          ip: deviceData.ip || '',
          port: deviceData.port || 8000,
          brand: deviceData.brand || 'Hikvision',
          model: deviceData.model || '',
          status: (deviceData.status?.toUpperCase() as DeviceStatus) || DeviceStatus.OFFLINE,
          lastSeen: deviceData.lastSeen || 'Never',
          firmware: deviceData.firmware || '',
          rtspUrl: deviceData.rtspUrl || '',
          username: deviceData.username || '',
          password: deviceData.password || '',
        };
        setDevice(deviceInfo);
      } catch (err) {
        console.error('加载设备详情失败:', err);
      } finally {
        setIsLoading(false);
      }
    };

    loadDevice();
  }, [deviceId]);

  // 进入页面时自动抓图
  useEffect(() => {
    if (!deviceId || !device) return;
    
    const captureSnapshot = async () => {
      setIsLoadingSnapshot(true);
      try {
        const response = await deviceService.captureSnapshot(deviceId, 1);
        if (response.data?.url) {
          const baseUrl = (import.meta as any).env?.VITE_API_URL || 'http://localhost:8080';
          const fullUrl = response.data.url.startsWith('http') 
            ? response.data.url 
            : `${baseUrl}${response.data.url}`;
          setSnapshot(fullUrl);
        }
      } catch (err) {
        console.error('自动抓图失败:', err);
      } finally {
        setIsLoadingSnapshot(false);
      }
    };

    captureSnapshot();
  }, [deviceId, device]);

  // 全屏事件监听 - 必须在所有条件返回之前调用
  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(!!document.fullscreenElement);
    };

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange);
    };
  }, []);

  // 条件返回必须在所有 Hooks 调用之后
  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  if (!deviceId || !device) {
    return (
      <div className="flex flex-col items-center justify-center h-64">
        <p className="text-gray-500 mb-4">{t('device_name')} {t('offline')}</p>
        <button
          onClick={() => navigate('/devices')}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          {language === 'zh' ? '返回设备列表' : 'Back to Devices'}
        </button>
      </div>
    );
  }

  const handleSnapshot = async () => {
    setIsLoadingSnapshot(true);
    try {
      const response = await deviceService.captureSnapshot(deviceId, 1);
      if (response.data?.url) {
        // 构建完整的URL
        const baseUrl = (import.meta as any).env?.VITE_API_URL || 'http://localhost:8080';
        const fullUrl = response.data.url.startsWith('http') 
          ? response.data.url 
          : `${baseUrl}${response.data.url}`;
        setSnapshot(fullUrl);
      }
    } catch (err: any) {
      console.error('抓图失败:', err);
      alert(err.message || '获取快照失败');
    } finally {
      setIsLoadingSnapshot(false);
    }
  };

  const handleReboot = async () => {
    if (!confirm('确定要重启设备吗？')) return;
    try {
      await deviceService.rebootDevice(deviceId);
      alert('重启命令已发送');
    } catch (err: any) {
      alert(err.message || '重启设备失败');
    }
  };

  const handlePtzControl = async (command: string, action: 'start' | 'stop' = 'start') => {
    try {
      await deviceService.ptzControl(deviceId, command, action, 1);
      
      // PTZ控制结束时（stop），自动抓图并更新显示
      if (action === 'stop') {
        // 延迟500ms后抓图，确保PTZ操作完全停止
        setTimeout(async () => {
          try {
            const response = await deviceService.captureSnapshot(deviceId, 1);
            if (response.data?.url) {
              const baseUrl = (import.meta as any).env?.VITE_API_URL || 'http://localhost:8080';
              const fullUrl = response.data.url.startsWith('http') 
                ? response.data.url 
                : `${baseUrl}${response.data.url}`;
              setSnapshot(fullUrl);
            }
          } catch (err) {
            console.error('PTZ控制后自动抓图失败:', err);
          }
        }, 500);
      }
    } catch (err: any) {
      console.error('PTZ控制失败:', err);
    }
  };

  const handleExport = async () => {
    // 这里需要先有回放文件，暂时提示
    alert('请先进行录像回放，然后才能导出');
  };

  const handleFullscreen = async () => {
    if (!imageContainerRef.current) return;

    try {
      if (!isFullscreen) {
        await imageContainerRef.current.requestFullscreen();
        setIsFullscreen(true);
      } else {
        await document.exitFullscreen();
        setIsFullscreen(false);
      }
    } catch (error) {
      console.error('Fullscreen error:', error);
    }
  };

  const getStatusText = (status: string) => {
    if (status === 'ONLINE') return t('online');
    if (status === 'OFFLINE') return t('offline');
    if (status === 'WARNING') return t('warning');
    return status;
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-4">
          <button 
            onClick={() => navigate('/devices')}
            className="p-2 bg-white border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors"
          >
            <ArrowLeft size={20} className="text-gray-600" />
          </button>
          <div>
            <h2 className="text-2xl font-bold text-gray-800">{device.name}</h2>
            <div className="flex items-center space-x-3 text-sm text-gray-500 mt-1">
              <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                device.status === 'ONLINE' ? 'bg-green-100 text-green-700' : 
                device.status === 'OFFLINE' ? 'bg-red-100 text-red-700' : 
                'bg-yellow-100 text-yellow-700'
              }`}>
                {getStatusText(device.status)}
              </span>
              <span>•</span>
              <span className="font-mono">{device.ip}</span>
              <span>•</span>
              <span>{device.brand} {device.model}</span>
            </div>
          </div>
        </div>
        <div className="flex items-center space-x-3">
            <button className="flex items-center space-x-2 px-4 py-2 bg-white border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 transition-colors shadow-sm">
                <Settings size={18} />
                <span>{t('settings')}</span>
            </button>
            <button 
              onClick={handleReboot}
              className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors shadow-lg shadow-blue-200"
            >
                <RotateCw size={18} />
                <span>{t('reboot')}</span>
            </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Image Area - 录像回放播放窗口 */}
        <div className="lg:col-span-2 space-y-6">
          <div 
            ref={imageContainerRef}
            className="relative bg-black rounded-2xl overflow-hidden shadow-lg aspect-video group"
          >
            {snapshot ? (
              <img
                src={snapshot}
                alt="设备快照"
                className="w-full h-full object-contain"
                onError={(e) => {
                  console.error('图片加载失败:', snapshot);
                  setSnapshot(null);
                }}
              />
            ) : (
              <div className="w-full h-full flex items-center justify-center bg-gray-900">
                <div className="text-center text-gray-400">
                  {isLoadingSnapshot ? (
                    <>
                      <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                      <p className="text-sm">正在抓图...</p>
                    </>
                  ) : (
                    <>
                      <p className="mb-2">暂无图像</p>
                      <p className="text-sm">点击抓图按钮获取设备快照</p>
                    </>
                  )}
                </div>
              </div>
            )}
            
            {/* Overlay UI - 时间显示 */}
            <div className="absolute top-4 right-4 z-10">
                <span className="px-2 py-1 bg-black/50 backdrop-blur-md text-white text-xs rounded font-mono">
                    {new Date().toLocaleTimeString()}
                </span>
            </div>

            {/* 控制按钮 - 全屏 */}
            {snapshot && (
              <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/80 to-transparent flex items-center justify-end opacity-0 group-hover:opacity-100 transition-opacity duration-300 z-10">
                  <div className="flex items-center space-x-3">
                      <button 
                          onClick={handleFullscreen}
                          className="text-white hover:text-blue-400 transition-colors" 
                          title={isFullscreen ? t('exit_fullscreen') : t('fullscreen')}
                      >
                          {isFullscreen ? <Minimize size={18} /> : <Maximize size={18} />}
                      </button>
                  </div>
              </div>
            )}
          </div>
          
          {/* Timeline / Playback Controls */}
          <div className="bg-white p-4 rounded-2xl shadow-sm border border-gray-100">
             <div className="flex items-center justify-between mb-4">
                <h3 className="font-semibold text-gray-800 flex items-center">
                    <Calendar size={18} className="mr-2 text-blue-500" />
                    {t('playback')}
                </h3>
                <input type="date" className="bg-gray-50 border border-gray-200 rounded-lg px-3 py-1 text-sm outline-none focus:ring-1 focus:ring-blue-500"/>
             </div>
             <div className="h-12 bg-gray-50 rounded-lg border border-gray-100 relative overflow-hidden flex items-center px-2 cursor-pointer">
                 {/* Mock Timeline */}
                 <div className="w-1/4 h-2 bg-blue-200 rounded-full mr-1"></div>
                 <div className="w-1/6 h-2 bg-gray-200 rounded-full mr-1"></div>
                 <div className="w-1/3 h-2 bg-blue-500 rounded-full mr-1"></div>
                 <div className="absolute top-0 bottom-0 left-1/2 w-0.5 bg-red-500 z-10"></div>
             </div>
          </div>
        </div>

        {/* Controls Sidebar */}
        <div className="space-y-6">
            {/* PTZ Control */}
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
                <h3 className="font-bold text-gray-800 mb-6 flex items-center justify-between">
                    {t('ptz_control')}
                    <span className="text-xs bg-blue-50 text-blue-600 px-2 py-1 rounded">{t('online')}</span>
                </h3>
                
                <div className="flex justify-center mb-6">
                    <div className="relative w-48 h-48 bg-gray-50 rounded-full border border-gray-200 flex items-center justify-center shadow-inner">
                         {/* D-Pad Buttons */}
                         <button 
                           onMouseDown={() => handlePtzControl('up', 'start')}
                           onMouseUp={() => handlePtzControl('up', 'stop')}
                           className="absolute top-2 left-1/2 -translate-x-1/2 w-10 h-10 bg-white shadow-md rounded-lg flex items-center justify-center text-gray-600 hover:text-blue-600 active:bg-blue-50 transition-colors"
                         >
                            <span className="rotate-90">‹</span>
                         </button>
                         <button 
                           onMouseDown={() => handlePtzControl('down', 'start')}
                           onMouseUp={() => handlePtzControl('down', 'stop')}
                           className="absolute bottom-2 left-1/2 -translate-x-1/2 w-10 h-10 bg-white shadow-md rounded-lg flex items-center justify-center text-gray-600 hover:text-blue-600 active:bg-blue-50 transition-colors"
                         >
                            <span className="-rotate-90">‹</span>
                         </button>
                         <button 
                           onMouseDown={() => handlePtzControl('left', 'start')}
                           onMouseUp={() => handlePtzControl('left', 'stop')}
                           className="absolute left-2 top-1/2 -translate-y-1/2 w-10 h-10 bg-white shadow-md rounded-lg flex items-center justify-center text-gray-600 hover:text-blue-600 active:bg-blue-50 transition-colors"
                         >
                            <span>‹</span>
                         </button>
                         <button 
                           onMouseDown={() => handlePtzControl('right', 'start')}
                           onMouseUp={() => handlePtzControl('right', 'stop')}
                           className="absolute right-2 top-1/2 -translate-y-1/2 w-10 h-10 bg-white shadow-md rounded-lg flex items-center justify-center text-gray-600 hover:text-blue-600 active:bg-blue-50 transition-colors"
                         >
                            <span className="rotate-180">‹</span>
                         </button>
                         
                         {/* Center Reset */}
                         <button className="w-14 h-14 bg-gradient-to-b from-white to-gray-50 rounded-full shadow flex items-center justify-center text-xs font-bold text-gray-500 border border-gray-100 active:scale-95 transition-transform">
                             AUTO
                         </button>
                    </div>
                </div>

                <div className="flex justify-between items-center px-4">
                    <div className="flex flex-col items-center space-y-2">
                        <button 
                          onMouseDown={() => handlePtzControl('zoom_out', 'start')}
                          onMouseUp={() => handlePtzControl('zoom_out', 'stop')}
                          className="w-10 h-10 rounded-full bg-gray-100 hover:bg-gray-200 flex items-center justify-center transition-colors"
                        >
                            <ZoomOut size={18} />
                        </button>
                        <span className="text-xs text-gray-500">{t('zoom_out')}</span>
                    </div>
                    <div className="flex flex-col items-center space-y-2">
                        <button 
                          onMouseDown={() => handlePtzControl('zoom_in', 'start')}
                          onMouseUp={() => handlePtzControl('zoom_in', 'stop')}
                          className="w-10 h-10 rounded-full bg-blue-50 hover:bg-blue-100 text-blue-600 flex items-center justify-center transition-colors"
                        >
                            <ZoomIn size={18} />
                        </button>
                        <span className="text-xs text-gray-500">{t('zoom_in')}</span>
                    </div>
                </div>
            </div>

            {/* Actions */}
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
                <h3 className="font-bold text-gray-800 mb-4">{t('quick_actions')}</h3>
                <div className="grid grid-cols-2 gap-3">
                    <button 
                        onClick={handleSnapshot}
                        className="flex flex-col items-center justify-center p-3 bg-gray-50 rounded-xl hover:bg-blue-50 hover:text-blue-600 transition-colors border border-transparent hover:border-blue-100"
                    >
                        <Camera size={20} className="mb-1" />
                        <span className="text-xs font-medium">{t('snapshot')}</span>
                    </button>
                     <button 
                       onClick={handleExport}
                       className="flex flex-col items-center justify-center p-3 bg-gray-50 rounded-xl hover:bg-blue-50 hover:text-blue-600 transition-colors border border-transparent hover:border-blue-100"
                     >
                        <Download size={20} className="mb-1" />
                        <span className="text-xs font-medium">{t('export')}</span>
                    </button>
                </div>
            </div>
        </div>
      </div>
    </div>
  );
};