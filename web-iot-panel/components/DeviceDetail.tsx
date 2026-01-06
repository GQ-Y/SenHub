import React, { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  ArrowLeft, 
  Camera, 
  Settings, 
  Volume2, 
  VolumeX, 
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
  const [isMuted, setIsMuted] = useState(true);
  const [snapshot, setSnapshot] = useState<string | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [videoUrl, setVideoUrl] = useState<string>('');
  const [isLoadingVideo, setIsLoadingVideo] = useState(false);
  const videoRef = useRef<HTMLVideoElement>(null);
  const videoContainerRef = useRef<HTMLDivElement>(null);

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

    // 定期获取最新录制视频URL（只获取已完成的文件，确保可以播放）
  useEffect(() => {
    if (!deviceId) return;

    const fetchVideoUrl = async () => {
      try {
        const streamResponse = await deviceService.getStreamUrl(deviceId);
        if (streamResponse.data) {
          // 优先使用videoUrl，如果没有则使用rtspUrl（向后兼容）
          const videoPath = streamResponse.data.videoUrl || streamResponse.data.rtspUrl;
          if (videoPath) {
            // 构建完整的视频URL
            const baseUrl = (import.meta as any).env?.VITE_API_URL?.replace('/api', '') || 'http://localhost:8080';
            const fullVideoUrl = videoPath.startsWith('http') 
              ? videoPath 
              : `${baseUrl}${videoPath}`;
            
            // 如果URL发生变化，更新视频源
            setVideoUrl(prevUrl => {
              if (prevUrl !== fullVideoUrl) {
                setIsLoadingVideo(true);
                return fullVideoUrl;
              }
              return prevUrl;
            });
          }
        }
      } catch (err) {
        console.error('获取录制视频失败:', err);
        // 不重置videoUrl，保持当前播放
      }
    };

    // 初始加载
    fetchVideoUrl();

    // 定期刷新视频URL（每10秒），获取最新的已完成文件
    const refreshInterval = setInterval(fetchVideoUrl, 10000);

    return () => {
      clearInterval(refreshInterval);
    };
  }, [deviceId]);

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
      alert(err.message || '获取快照失败');
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
    } catch (err: any) {
      console.error('PTZ控制失败:', err);
    }
  };

  const handleExport = async () => {
    // 这里需要先有回放文件，暂时提示
    alert('请先进行录像回放，然后才能导出');
  };

  const handleFullscreen = async () => {
    if (!videoContainerRef.current) return;

    try {
      if (!isFullscreen) {
        await videoContainerRef.current.requestFullscreen();
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
        {/* Main Video Area */}
        <div className="lg:col-span-2 space-y-6">
          <div 
            ref={videoContainerRef}
            className="relative bg-black rounded-2xl overflow-hidden shadow-lg aspect-video group"
          >
            {videoUrl ? (
              <video
                ref={videoRef}
                key={videoUrl} // 使用key强制重新加载视频源
                src={videoUrl}
                controls
                autoPlay
                muted // 默认静音，避免自动播放被阻止
                preload="auto"
                playsInline
                crossOrigin="anonymous"
                className="w-full h-full object-contain"
                onError={(e) => {
                  const video = e.target as HTMLVideoElement;
                  const error = video.error;
                  console.error('视频播放错误:', {
                    error,
                    code: error?.code,
                    message: error?.message,
                    networkState: video.networkState,
                    readyState: video.readyState,
                    src: video.src,
                  });
                  
                  // 错误代码说明：
                  // 1 = MEDIA_ERR_ABORTED - 用户中止
                  // 2 = MEDIA_ERR_NETWORK - 网络错误
                  // 3 = MEDIA_ERR_DECODE - 解码错误
                  // 4 = MEDIA_ERR_SRC_NOT_SUPPORTED - 格式不支持或文件损坏
                  
                  if (error?.code === 4) {
                    // 格式不支持或文件损坏，可能是文件正在录制中
                    console.warn('视频文件可能正在录制中或格式不完整，等待后重试...');
                    // 等待5秒后重新获取视频URL
                    setTimeout(() => {
                      if (deviceId) {
                        deviceService.getStreamUrl(deviceId).then((response) => {
                          if (response.data?.videoUrl) {
                            const baseUrl = (import.meta as any).env?.VITE_API_URL?.replace('/api', '') || 'http://localhost:8080';
                            const fullVideoUrl = response.data.videoUrl.startsWith('http') 
                              ? response.data.videoUrl 
                              : `${baseUrl}${response.data.videoUrl}`;
                            setVideoUrl(fullVideoUrl);
                          }
                        }).catch((err) => {
                          console.error('重新获取视频URL失败:', err);
                        });
                      }
                    }, 5000);
                  } else {
                    // 其他错误，尝试重新加载
                    if (videoRef.current) {
                      setTimeout(() => {
                        videoRef.current?.load();
                      }, 2000);
                    }
                  }
                }}
                onLoadedData={() => {
                  console.log('视频加载完成');
                  setIsLoadingVideo(false);
                }}
                onCanPlay={() => {
                  console.log('视频可以播放');
                }}
              />
            ) : (
              <div className="w-full h-full flex items-center justify-center bg-gray-900">
                <div className="text-center text-gray-400">
                  {isLoadingVideo ? (
                    <>
                      <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                      <p className="text-sm">正在加载视频...</p>
                    </>
                  ) : (
                    <>
                      <p className="mb-2">暂无录制视频</p>
                      <p className="text-sm">设备可能尚未开始录制</p>
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

            {/* 控制按钮 - 仅在视频上方显示 */}
            {videoUrl && (
              <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/80 to-transparent flex items-center justify-between opacity-0 group-hover:opacity-100 transition-opacity duration-300 z-10">
                  <div className="flex items-center space-x-4">
                      <button 
                          onClick={() => setIsMuted(!isMuted)}
                          className="text-white hover:text-blue-400 transition-colors"
                          title={isMuted ? '开启声音' : '关闭声音'}
                      >
                          {isMuted ? <VolumeX size={20} /> : <Volume2 size={20} />}
                      </button>
                  </div>
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

                {snapshot && (
                    <div className="mt-4 p-2 bg-gray-50 rounded-lg border border-gray-100 animate-fade-in">
                        <p className="text-xs text-gray-500 mb-2">Latest Snapshot:</p>
                        <img src={snapshot} alt="Snapshot" className="w-full rounded-md shadow-sm" />
                    </div>
                )}
            </div>
        </div>
      </div>
    </div>
  );
};