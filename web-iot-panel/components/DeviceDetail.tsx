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
import { Modal, ConfirmModal } from './Modal';
import { useModal } from '../hooks/useModal';

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
  
  // 录像回放相关状态
  const [playbackVideoUrl, setPlaybackVideoUrl] = useState<string | null>(null);
  const [isLoadingPlayback, setIsLoadingPlayback] = useState(false);
  const [downloadProgress, setDownloadProgress] = useState<number>(0);
  const [downloadHandle, setDownloadHandle] = useState<number | null>(null);
  const [playbackStartTime, setPlaybackStartTime] = useState<string>('');
  const [playbackEndTime, setPlaybackEndTime] = useState<string>('');
  const [selectedDate, setSelectedDate] = useState<string>(new Date().toISOString().split('T')[0]);
  const videoRef = useRef<HTMLVideoElement>(null);
  const progressIntervalRef = useRef<NodeJS.Timeout | null>(null);
  
  // 弹窗管理
  const modal = useModal();

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

  // 清理定时器 - 必须在所有条件返回之前调用
  useEffect(() => {
    return () => {
      if (progressIntervalRef.current) {
        clearInterval(progressIntervalRef.current);
      }
    };
  }, []);

  // 初始化时间范围 - 必须在所有条件返回之前调用
  useEffect(() => {
    const selected = new Date(selectedDate);
    const start = new Date(selected);
    start.setHours(0, 0, 0, 0);
    const end = new Date(selected);
    end.setHours(23, 59, 59, 999);
    
    setPlaybackStartTime(start.toISOString().slice(0, 19).replace('T', ' '));
    setPlaybackEndTime(end.toISOString().slice(0, 19).replace('T', ' '));
  }, []); // 只在组件挂载时执行一次

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
        modal.showModal({
          message: err.message || '获取快照失败',
          type: 'error',
        });
      } finally {
      setIsLoadingSnapshot(false);
    }
  };

  const handleReboot = async () => {
    modal.showConfirm({
      title: '确认重启',
      message: '确定要重启设备吗？',
      onConfirm: async () => {
        try {
          await deviceService.rebootDevice(deviceId);
          modal.showModal({
            message: '重启命令已发送',
            type: 'success',
          });
        } catch (err: any) {
          modal.showModal({
            message: err.message || '重启设备失败',
            type: 'error',
          });
        }
      },
    });
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
    modal.showModal({
      message: '请先进行录像回放，然后才能导出',
      type: 'info',
    });
  };

  // 启动录像回放下载
  const handleStartPlayback = async () => {
    if (!playbackStartTime || !playbackEndTime) {
      modal.showModal({
        message: '请选择开始时间和结束时间',
        type: 'warning',
      });
      return;
    }

    if (!deviceId) return;

    setIsLoadingPlayback(true);
    setDownloadProgress(0);
    setPlaybackVideoUrl(null);

    try {
      // 启动下载
      const response = await deviceService.playback(deviceId, playbackStartTime, playbackEndTime, 1);
      
      // 检查响应数据
      if (!response || !response.data) {
        throw new Error('启动下载失败：服务器未返回数据');
      }
      
      // 检查响应数据结构
      console.log('Playback API响应:', JSON.stringify(response, null, 2));
      
      const handle = response.data?.downloadHandle;
      const filePath = response.data?.filePath;

      if (handle === undefined || handle === null || !filePath) {
        console.error('API响应数据:', response);
        throw new Error(`启动下载失败：未返回下载句柄或文件路径。响应: ${JSON.stringify(response.data)}`);
      }

      setDownloadHandle(handle);

      // 开始轮询下载进度
      const checkProgress = async () => {
        try {
          const progressResponse = await deviceService.getPlaybackProgress(deviceId, handle);
          const progress = progressResponse.data?.progress || 0;
          const isCompleted = progressResponse.data?.isCompleted || false;

          setDownloadProgress(progress);

          if (isCompleted || progress >= 100) {
            // 下载完成，获取视频URL
            const videoUrl = deviceService.getPlaybackFileUrl(deviceId, filePath);
            setPlaybackVideoUrl(videoUrl);
            setIsLoadingPlayback(false);
            if (progressIntervalRef.current) {
              clearInterval(progressIntervalRef.current);
              progressIntervalRef.current = null;
            }
          } else if (progressResponse.data?.isError) {
            // 下载出错
            setIsLoadingPlayback(false);
            modal.showModal({
              message: '下载失败，请重试',
              type: 'error',
            });
            if (progressIntervalRef.current) {
              clearInterval(progressIntervalRef.current);
              progressIntervalRef.current = null;
            }
          }
        } catch (err) {
          console.error('查询下载进度失败:', err);
        }
      };

      // 每1秒查询一次进度
      progressIntervalRef.current = setInterval(checkProgress, 1000);
      // 立即查询一次
      checkProgress();

    } catch (err: any) {
      console.error('启动录像回放失败:', err);
      modal.showModal({
        message: err.message || '启动录像回放失败',
        type: 'error',
      });
      setIsLoadingPlayback(false);
    }
  };

  // 停止录像下载
  const handleStopPlayback = async () => {
    if (downloadHandle !== null && deviceId) {
      try {
        await deviceService.stopPlayback(deviceId, downloadHandle);
        if (progressIntervalRef.current) {
          clearInterval(progressIntervalRef.current);
          progressIntervalRef.current = null;
        }
        setDownloadHandle(null);
        setIsLoadingPlayback(false);
        setDownloadProgress(0);
      } catch (err) {
        console.error('停止下载失败:', err);
      }
    }
  };

  // 根据选择的日期设置默认时间范围（当天）
  const handleDateChange = (date: string) => {
    setSelectedDate(date);
    const selected = new Date(date);
    const start = new Date(selected);
    start.setHours(0, 0, 0, 0);
    const end = new Date(selected);
    end.setHours(23, 59, 59, 999);
    
    setPlaybackStartTime(start.toISOString().slice(0, 19).replace('T', ' '));
    setPlaybackEndTime(end.toISOString().slice(0, 19).replace('T', ' '));
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
    <>
      {/* 弹窗组件 */}
      {modal.isConfirm ? (
        <ConfirmModal
          isOpen={modal.isOpen}
          onClose={modal.closeModal}
          title={modal.modalOptions?.title}
          message={modal.modalOptions?.message || ''}
          onConfirm={() => {
            if (modal.modalOptions?.onConfirm) {
              modal.modalOptions.onConfirm();
            }
            modal.closeModal();
          }}
          onCancel={() => {
            if (modal.modalOptions?.onCancel) {
              modal.modalOptions.onCancel();
            }
            modal.closeModal();
          }}
          confirmText={modal.modalOptions?.confirmText}
          cancelText={modal.modalOptions?.cancelText}
        />
      ) : (
        <Modal
          isOpen={modal.isOpen}
          onClose={modal.closeModal}
          title={modal.modalOptions?.title}
          message={modal.modalOptions?.message || ''}
          type={modal.modalOptions?.type || 'info'}
          confirmText={modal.modalOptions?.confirmText}
          onConfirm={modal.modalOptions?.onConfirm}
        />
      )}
      
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
            {playbackVideoUrl ? (
              <video
                ref={videoRef}
                src={playbackVideoUrl}
                controls
                className="w-full h-full"
                onError={(e) => {
                  console.error('视频加载失败:', playbackVideoUrl);
                  setPlaybackVideoUrl(null);
                }}
              />
            ) : snapshot ? (
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
                  ) : isLoadingPlayback ? (
                    <>
                      <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                      <p className="text-sm">正在下载录像: {downloadProgress}%</p>
                      {downloadHandle !== null && (
                        <button
                          onClick={handleStopPlayback}
                          className="mt-2 px-3 py-1 bg-red-600 text-white text-xs rounded hover:bg-red-700"
                        >
                          取消下载
                        </button>
                      )}
                    </>
                  ) : (
                    <>
                      <p className="mb-2">暂无图像</p>
                      <p className="text-sm">点击抓图按钮获取设备快照，或选择时间进行录像回放</p>
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
                <input 
                  type="date" 
                  value={selectedDate}
                  onChange={(e) => handleDateChange(e.target.value)}
                  className="bg-gray-50 border border-gray-200 rounded-lg px-3 py-1 text-sm outline-none focus:ring-1 focus:ring-blue-500"
                />
             </div>
             
             <div className="space-y-3">
               <div className="grid grid-cols-2 gap-3">
                 <div>
                   <label className="block text-xs text-gray-600 mb-1">开始时间</label>
                   <input
                     type="datetime-local"
                     value={playbackStartTime ? playbackStartTime.replace(' ', 'T') : ''}
                     onChange={(e) => setPlaybackStartTime(e.target.value.replace('T', ' '))}
                     className="w-full bg-gray-50 border border-gray-200 rounded-lg px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-blue-500"
                   />
                 </div>
                 <div>
                   <label className="block text-xs text-gray-600 mb-1">结束时间</label>
                   <input
                     type="datetime-local"
                     value={playbackEndTime ? playbackEndTime.replace(' ', 'T') : ''}
                     onChange={(e) => setPlaybackEndTime(e.target.value.replace('T', ' '))}
                     className="w-full bg-gray-50 border border-gray-200 rounded-lg px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-blue-500"
                   />
                 </div>
               </div>
               
               <div className="flex items-center space-x-2">
                 <button
                   onClick={handleStartPlayback}
                   disabled={isLoadingPlayback || !playbackStartTime || !playbackEndTime}
                   className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors text-sm font-medium"
                 >
                   {isLoadingPlayback ? `下载中 ${downloadProgress}%` : '开始回放'}
                 </button>
                 {playbackVideoUrl && (
                   <button
                     onClick={() => {
                       setPlaybackVideoUrl(null);
                       setSnapshot(null);
                       handleSnapshot();
                     }}
                     className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors text-sm"
                   >
                     查看快照
                   </button>
                 )}
               </div>
               
               {isLoadingPlayback && downloadProgress > 0 && (
                 <div className="w-full bg-gray-200 rounded-full h-2">
                   <div
                     className="bg-blue-600 h-2 rounded-full transition-all duration-300"
                     style={{ width: `${downloadProgress}%` }}
                   ></div>
                 </div>
               )}
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
    </>
  );
};