import React, { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import flvjs from 'flv.js';
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
  Minimize,
  Trash2,
  Play,
  Video as VideoIcon,
  Code,
  X,
  Eye,
  ChevronLeft,
  ChevronRight,
  Gauge,
  Radio
} from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { deviceService } from '../src/api/services';
import { Device, DeviceStatus } from '../types';
import { Modal, ConfirmModal } from './Modal';
import { useModal } from '../hooks/useModal';
import { API_CONFIG } from '../src/api/config';
import { DeviceConfig } from './DeviceConfig';
import { PtzViewPage } from './PtzViewPage';

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
  const hasAutoSnapshotRef = useRef(false); // 跟踪是否已自动抓图

  // 录像回放相关状态
  const [playbackVideoUrl, setPlaybackVideoUrl] = useState<string | null>(null);
  const [playbackFilePath, setPlaybackFilePath] = useState<string | null>(null);
  const [isLoadingPlayback, setIsLoadingPlayback] = useState(false);
  const [downloadProgress, setDownloadProgress] = useState<number>(0);
  const [downloadHandle, setDownloadHandle] = useState<number | null>(null);
  const [selectedDate, setSelectedDate] = useState<string>(new Date().toISOString().split('T')[0]);
  const videoRef = useRef<HTMLVideoElement>(null);
  const progressIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const playbackSegmentRef = useRef<{ hour: number; minute: number } | null>(null);

  // 时间轴：按分钟选择（小时 + 分钟）
  const [timelineHalf, setTimelineHalf] = useState<'AM' | 'PM'>(() => {
    return new Date().getHours() >= 12 ? 'PM' : 'AM';
  });
  const [selectedHour, setSelectedHour] = useState<number | null>(null);
  const [selectedMinute, setSelectedMinute] = useState<number | null>(null);
  const [playbackSpeed, setPlaybackSpeed] = useState<number>(1);
  const [activeTab, setActiveTab] = useState<'preview' | 'config' | 'ptz'>('preview');

  // 实时直播 / 回放转码（ZLM HTTP-FLV，共用一套 flv 播放）
  const [flvPlaybackUrl, setFlvPlaybackUrl] = useState<string | null>(null);
  const [flvPlaybackKey, setFlvPlaybackKey] = useState<string | null>(null); // 仅回放转码时有值，用于 transcode-stop
  const [isLoadingLive, setIsLoadingLive] = useState(false);
  const [isLoadingTranscode, setIsLoadingTranscode] = useState(false);
  const liveVideoRef = useRef<HTMLVideoElement>(null);
  const flvPlayerRef = useRef<ReturnType<typeof flvjs.createPlayer> | null>(null);

  // 云台控制速率 (1-100)
  const [ptzRate, setPtzRate] = useState<number>(50);

  // 开发调试弹窗状态
  const [isDebugModalOpen, setIsDebugModalOpen] = useState(false);
  const [debugPan, setDebugPan] = useState<number>(0);
  const [debugTilt, setDebugTilt] = useState<number>(0);
  const [debugZoom, setDebugZoom] = useState<number>(1);
  const [isDebugLoading, setIsDebugLoading] = useState(false);

  // 弹窗管理
  const modal = useModal();

  // 加载设备详情
  useEffect(() => {
    // 设备ID变化时重置自动抓图标志
    hasAutoSnapshotRef.current = false;
    
    const loadDevice = async () => {
      if (!deviceId) return;

      setIsLoading(true);
      try {
        const response = await deviceService.getDevice(deviceId);
        setDevice(response.data);
      } catch (err) {
        console.error('加载设备详情失败:', err);
      } finally {
        setIsLoading(false);
      }
    };

    loadDevice();
  }, [deviceId]);

  // 进入页面时自动抓图（只执行一次）
  useEffect(() => {
    if (!deviceId || !device) return;
    
    // 防止重复抓图（React Strict Mode 或依赖变化导致）
    if (hasAutoSnapshotRef.current) return;
    hasAutoSnapshotRef.current = true;

    const captureSnapshot = async () => {
      setIsLoadingSnapshot(true);
      try {
        const response = await deviceService.captureSnapshot(deviceId, 1);
        if (response.data?.url) {
          // 使用API_CONFIG.BASE_URL，但需要去掉/api后缀，因为response.data.url已经包含了/api前缀
          const baseUrl = API_CONFIG.BASE_URL.replace(/\/api$/, '') || 'http://192.168.1.210:8084';
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

  useEffect(() => {
    return () => {
      if (playbackVideoUrl && playbackVideoUrl.startsWith('blob:')) {
        URL.revokeObjectURL(playbackVideoUrl);
      }
    };
  }, [playbackVideoUrl]);

  // 倍速同步
  useEffect(() => {
    if (videoRef.current) {
      videoRef.current.playbackRate = playbackSpeed;
    }
  }, [playbackSpeed, playbackVideoUrl]);

  // 实时直播/回放转码：flv.js 绑定/销毁
  useEffect(() => {
    if (!flvPlaybackUrl || !liveVideoRef.current) return;
    if (!flvjs.isSupported()) {
      modal.showModal({ message: '当前浏览器不支持 HTTP-FLV 播放', type: 'error' });
      setFlvPlaybackUrl(null);
      setFlvPlaybackKey(null);
      return;
    }
    const isLive = flvPlaybackKey == null;
    const player = flvjs.createPlayer({ type: 'flv', url: flvPlaybackUrl }, { isLive });
    player.attachMediaElement(liveVideoRef.current);
    player.load();
    player.play();
    flvPlayerRef.current = player;
    return () => {
      try {
        player.destroy();
      } catch (_) {}
      flvPlayerRef.current = null;
    };
  }, [flvPlaybackUrl]);

  // 组件卸载或切设备时释放转码任务
  useEffect(() => {
    return () => {
      if (flvPlaybackKey && deviceId) {
        deviceService.postPlaybackTranscodeStop(deviceId, flvPlaybackKey).catch(() => {});
      }
    };
  }, [flvPlaybackKey, deviceId]);

  const handleStartLiveStream = async () => {
    if (!deviceId) return;
    setIsLoadingLive(true);
    try {
      const res = await deviceService.getLiveUrl(deviceId);
      const url = res.data?.flv_url;
      if (url) {
        setFlvPlaybackKey(null);
        setFlvPlaybackUrl(url);
      } else {
        modal.showModal({ message: res?.message || '直播服务未启用或设备无 RTSP 地址', type: 'error' });
      }
    } catch (e: any) {
      modal.showModal({ message: e?.message || '获取直播地址失败', type: 'error' });
    } finally {
      setIsLoadingLive(false);
    }
  };

  const handleStopFlvPlayback = async () => {
    if (flvPlaybackKey && deviceId) {
      try {
        await deviceService.postPlaybackTranscodeStop(deviceId, flvPlaybackKey);
      } catch (_) {}
    }
    setFlvPlaybackUrl(null);
    setFlvPlaybackKey(null);
  };

  const handleStartTranscodePlayback = async () => {
    if (!deviceId || !playbackFilePath) {
      modal.showModal({ message: '请先选择时间段并等待录像下载完成', type: 'error' });
      return;
    }
    setIsLoadingTranscode(true);
    try {
      const res = await deviceService.getPlaybackTranscodeUrl(deviceId, playbackFilePath);
      const url = res.data?.flv_url;
      const key = res.data?.key;
      if (url && key) {
        setFlvPlaybackUrl(url);
        setFlvPlaybackKey(key);
      } else {
        modal.showModal({ message: res?.message || '转码服务未启用或启动失败（需配置 zlm.enabled 并安装 FFmpeg）', type: 'error' });
      }
    } catch (e: any) {
      modal.showModal({ message: e?.message || '获取转码地址失败', type: 'error' });
    } finally {
      setIsLoadingTranscode(false);
    }
  };

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
        // 使用API_CONFIG.BASE_URL，但需要去掉/api后缀，因为response.data.url已经包含了/api前缀
        const baseUrl = API_CONFIG.BASE_URL.replace(/\/api$/, '') || 'http://192.168.1.210:8084';
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
      await deviceService.ptzControl(deviceId, command, action, ptzRate);

      // PTZ控制结束时（stop），自动抓图并更新显示
      if (action === 'stop') {
        // 延迟500ms后抓图，确保PTZ操作完全停止
        setTimeout(async () => {
          try {
            const response = await deviceService.captureSnapshot(deviceId, 1);
            if (response.data?.url) {
              // 使用API_CONFIG.BASE_URL，但需要去掉/api后缀，因为response.data.url已经包含了/api前缀
              const baseUrl = API_CONFIG.BASE_URL.replace(/\/api$/, '') || 'http://192.168.1.210:8084';
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

  // PTZ 绝对定位控制
  const handlePtzGoto = async () => {
    if (!deviceId) return;
    
    setIsDebugLoading(true);
    try {
      const response = await deviceService.ptzGoto(deviceId, debugPan, debugTilt, debugZoom);
      if (response.data) {
        modal.showModal({
          message: t('ptz_goto_success'),
          type: 'success',
        });
        // 延迟抓图更新画面
        setTimeout(async () => {
          try {
            const snapshotResponse = await deviceService.captureSnapshot(deviceId, 1);
            if (snapshotResponse.data?.url) {
              const baseUrl = API_CONFIG.BASE_URL.replace(/\/api$/, '') || 'http://192.168.1.210:8084';
              const fullUrl = snapshotResponse.data.url.startsWith('http')
                ? snapshotResponse.data.url
                : `${baseUrl}${snapshotResponse.data.url}`;
              setSnapshot(fullUrl);
            }
          } catch (err) {
            console.error('PTZ定位后自动抓图失败:', err);
          }
        }, 1000);
      }
    } catch (err: any) {
      console.error('PTZ绝对定位失败:', err);
      modal.showModal({
        message: t('ptz_goto_failed') + ': ' + (err.message || '未知错误'),
        type: 'error',
      });
    } finally {
      setIsDebugLoading(false);
    }
  };

  const handleExport = async () => {
    // 这里需要先有回放文件，暂时提示
    modal.showModal({
      message: '请先进行录像回放，然后才能导出',
      type: 'info',
    });
  };

  const baseHour = timelineHalf === 'AM' ? 0 : 12;

  // 判断某分钟是否为“未来”（不可点击）
  const isMinuteInFuture = (hour: number, minute: number): boolean => {
    const today = selectedDate === new Date().toISOString().split('T')[0];
    if (!today) return false;
    const now = new Date();
    const h = now.getHours();
    const m = now.getMinutes();
    if (hour < h) return false;
    if (hour > h) return true;
    return minute > m;
  };

  // 点击某一分钟，触发该分钟回放（1分钟分段）
  const handleMinuteClick = (hour: number, minute: number) => {
    if (isMinuteInFuture(hour, minute)) return;
    setSelectedHour(hour);
    setSelectedMinute(minute);
    const startStr = `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00`;
    const nextMin = minute + 1;
    const endHour = nextMin >= 60 ? hour + 1 : hour;
    const endMin = nextMin >= 60 ? 0 : nextMin;
    const endStr = `${String(endHour).padStart(2, '0')}:${String(endMin).padStart(2, '0')}:00`;
    handleStartPlaybackWithTime(startStr, endStr);
  };

  // 启动录像回放下载（1分钟分段，支持缓存命中）
  const handleStartPlaybackWithTime = async (start: string, end: string) => {
    if (!start || !end || !deviceId) return;

    setIsLoadingPlayback(true);
    setDownloadProgress(0);
    if (playbackVideoUrl && playbackVideoUrl.startsWith('blob:')) URL.revokeObjectURL(playbackVideoUrl);
    setPlaybackVideoUrl(null);

    const fullStartTime = `${selectedDate} ${start}:00`;
    const fullEndTime = `${selectedDate} ${end}:00`;

    const startTimeDate = new Date(`${selectedDate}T${start}`);
    const endTimeDate = new Date(`${selectedDate}T${end}`);
    const timeDiff = endTimeDate.getTime() - startTimeDate.getTime();

    if (timeDiff <= 0 || timeDiff > 60000) {
      modal.showModal({ message: '时间范围无效（仅支持1分钟分段）', type: 'error' });
      setIsLoadingPlayback(false);
      return;
    }

    try {
      const response = await deviceService.playback(deviceId, fullStartTime, fullEndTime, 1);
      if (!response?.data) throw new Error('启动下载失败：服务器未返回数据');

      const handle = response.data?.downloadHandle;
      const filePath = response.data?.filePath;
      const cached = !!response.data?.cached;
      if (handle === undefined || handle === null || !filePath) {
        throw new Error(`启动下载失败：未返回下载句柄或文件路径`);
      }

      setDownloadHandle(handle);

      if (cached || handle === -2) {
        setDownloadProgress(100);
        setPlaybackFilePath(filePath);
        setPlaybackVideoUrl(deviceService.getPlaybackFileUrlWithToken(deviceId, filePath));
        const [, timePart] = fullStartTime.split(' ');
        const [h, m] = (timePart || '00:00:00').split(':').map(Number);
        playbackSegmentRef.current = { hour: h, minute: m };
        setIsLoadingPlayback(false);
        return;
      }

      const POLL_TIMEOUT_MS = 60000; // 轮询超时 60 秒
      const pollStartRef = { current: Date.now() };

      const checkProgress = async () => {
        try {
          if (Date.now() - pollStartRef.current > POLL_TIMEOUT_MS) {
            if (progressIntervalRef.current) {
              clearInterval(progressIntervalRef.current);
              progressIntervalRef.current = null;
            }
            setIsLoadingPlayback(false);
            modal.showModal({ message: '录像下载超时，请重试或选择其他时间段。', type: 'error' });
            return;
          }

          const progressResponse = await deviceService.getPlaybackProgress(deviceId, handle);
          const progress = progressResponse.data?.progress || 0;
          const isCompleted = progressResponse.data?.isCompleted || false;
          const completedFilePath = progressResponse.data?.filePath ?? filePath;

          setDownloadProgress(progress);

          if (isCompleted || progress >= 100) {
            if (progressIntervalRef.current) {
              clearInterval(progressIntervalRef.current);
              progressIntervalRef.current = null;
            }
            setPlaybackFilePath(completedFilePath);
            setPlaybackVideoUrl(deviceService.getPlaybackFileUrlWithToken(deviceId, completedFilePath));
            const [, timePart] = fullStartTime.split(' ');
            const [h, m] = (timePart || '00:00:00').split(':').map(Number);
            playbackSegmentRef.current = { hour: h, minute: m };
            setIsLoadingPlayback(false);
          } else if (progressResponse.data?.isError) {
            setIsLoadingPlayback(false);
            modal.showModal({ message: '下载失败，请重试', type: 'error' });
            if (progressIntervalRef.current) {
              clearInterval(progressIntervalRef.current);
              progressIntervalRef.current = null;
            }
          }
        } catch (err) {
          console.error('查询下载进度失败:', err);
        }
      };

      progressIntervalRef.current = setInterval(checkProgress, 1000);
      checkProgress();
    } catch (err: any) {
      console.error('启动录像回放失败:', err);
      modal.showModal({ message: err.message || '启动录像回放失败', type: 'error' });
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

  const handleDateChange = (date: string) => {
    setSelectedDate(date);
    setSelectedHour(null);
    setSelectedMinute(null);
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

      {/* 开发调试弹窗 */}
      {isDebugModalOpen && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
              <h3 className="text-lg font-semibold text-gray-800">{t('dev_debug')}</h3>
              <button
                onClick={() => setIsDebugModalOpen(false)}
                className="p-1 hover:bg-gray-100 rounded-lg transition-colors"
              >
                <X size={20} className="text-gray-500" />
              </button>
            </div>
            <div className="p-6 space-y-5">
              <div className="text-sm font-medium text-gray-600 mb-2">{t('ptz_absolute_position')}</div>
              
              {/* 水平角度 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">
                  {t('pan_angle')} (0° - 360°)
                </label>
                <input
                  type="number"
                  value={debugPan}
                  onChange={(e) => setDebugPan(parseFloat(e.target.value) || 0)}
                  min="0"
                  max="360"
                  step="0.1"
                  className="w-full px-4 py-2.5 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  placeholder="0.0"
                />
              </div>
              
              {/* 垂直角度 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">
                  {t('tilt_angle')} (-90° - 90°)
                </label>
                <input
                  type="number"
                  value={debugTilt}
                  onChange={(e) => setDebugTilt(parseFloat(e.target.value) || 0)}
                  min="-90"
                  max="90"
                  step="0.1"
                  className="w-full px-4 py-2.5 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  placeholder="0.0"
                />
              </div>
              
              {/* 变倍 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">
                  {t('zoom_level')} (1.0x - 40.0x)
                </label>
                <input
                  type="number"
                  value={debugZoom}
                  onChange={(e) => setDebugZoom(parseFloat(e.target.value) || 1)}
                  min="1"
                  max="40"
                  step="0.1"
                  className="w-full px-4 py-2.5 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  placeholder="1.0"
                />
              </div>

              {/* 定位按钮 */}
              <button
                onClick={handlePtzGoto}
                disabled={isDebugLoading}
                className="w-full py-3 bg-blue-600 text-white font-medium rounded-xl hover:bg-blue-700 disabled:bg-blue-300 disabled:cursor-not-allowed transition-colors flex items-center justify-center space-x-2"
              >
                {isDebugLoading ? (
                  <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                ) : (
                  <>
                    <RotateCw size={18} />
                    <span>{t('goto_position')}</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
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
                <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${device.status === 'ONLINE' ? 'bg-green-100 text-green-700' :
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
            <button 
              onClick={() => setIsDebugModalOpen(true)}
              className="flex items-center space-x-2 px-4 py-2 bg-white border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 transition-colors shadow-sm"
            >
              <Code size={18} />
              <span>{t('dev_debug')}</span>
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

        {/* 标签页 */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <div className="border-b border-gray-100">
            <div className="flex space-x-1 px-4">
              <button
                onClick={() => setActiveTab('preview')}
                className={`px-4 py-3 font-medium text-sm transition-colors border-b-2 ${activeTab === 'preview'
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
                  }`}
              >
                <VideoIcon size={18} className="inline mr-2" />
                {t('live_view')}
              </button>
              <button
                onClick={() => setActiveTab('config')}
                className={`px-4 py-3 font-medium text-sm transition-colors border-b-2 ${activeTab === 'config'
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
                  }`}
              >
                <Settings size={18} className="inline mr-2" />
                {t('device_config')}
              </button>
              <button
                onClick={() => setActiveTab('ptz')}
                className={`px-4 py-3 font-medium text-sm transition-colors border-b-2 ${activeTab === 'ptz'
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
                  }`}
              >
                <Eye size={18} className="inline mr-2" />
                3D仿真
              </button>
            </div>
          </div>

          <div className="p-6">
            {activeTab === 'preview' && (
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Main Image Area - 录像回放播放窗口 */}
                <div className="lg:col-span-2 space-y-6">
                  <div
                    ref={imageContainerRef}
                    className="relative bg-black rounded-2xl overflow-hidden shadow-lg aspect-video group"
                  >
                    {flvPlaybackUrl ? (
                      <div className="w-full h-full flex flex-col">
                        <video
                          ref={liveVideoRef}
                          controls
                          className="w-full h-full"
                          muted={false}
                        />
                        <div className="absolute bottom-0 left-0 right-0 p-2 bg-black/50 flex justify-end">
                          <button
                            onClick={handleStopFlvPlayback}
                            className="px-3 py-1 bg-red-600 text-white text-xs rounded hover:bg-red-700"
                          >
                            {flvPlaybackKey ? '关闭转码播放' : '关闭直播'}
                          </button>
                        </div>
                      </div>
                    ) : playbackVideoUrl ? (
                      <div className="w-full h-full flex flex-col">
                        <video
                          ref={videoRef}
                          src={playbackVideoUrl}
                          controls
                          autoPlay
                          className="w-full h-full"
                          onError={(e) => {
                            const video = e.currentTarget;
                            const err = video?.error;
                            let message = '视频播放失败，文件格式可能不受浏览器支持。';
                            if (err) {
                              // MEDIA_ERR_ABORTED=1, MEDIA_ERR_NETWORK=2, MEDIA_ERR_DECODE=3, MEDIA_ERR_SRC_NOT_SUPPORTED=4
                              if (err.code === 3 || err.code === 4) {
                                const detail = err.message ? `（${err.message}）` : '';
                                message = `当前浏览器不支持该视频编码${detail}。请使用 Safari 尝试播放，或点击下方「导出录像」下载后用 VLC 等本地播放器观看。`;
                              } else if (err.code === 2) {
                                message = '视频加载失败，请检查网络后重试。';
                              }
                              console.error('视频播放失败:', err.code, err.message, err);
                            } else {
                              console.error('视频播放失败:', e);
                            }
                            modal.showModal({ message, type: 'error' });
                          }}
                          onLoadedMetadata={() => {
                            if (videoRef.current) videoRef.current.playbackRate = playbackSpeed;
                          }}
                          onEnded={() => {
                            const seg = playbackSegmentRef.current;
                            if (!seg) return;
                            let nextHour = seg.hour;
                            let nextMinute = seg.minute + 1;
                            if (nextMinute >= 60) {
                              nextMinute = 0;
                              nextHour += 1;
                            }
                            if (nextHour > 23) return;
                            if (isMinuteInFuture(nextHour, nextMinute)) return;
                            handleMinuteClick(nextHour, nextMinute);
                          }}
                        />
                        {downloadProgress > 0 && downloadProgress < 100 && (
                          <div className="absolute bottom-0 left-0 right-0 bg-black bg-opacity-50 text-white p-2 text-sm">
                            下载进度: {downloadProgress}%
                          </div>
                        )}
                      </div>
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
                              <p className="text-sm mb-3">点击抓图获取快照，选择时间进行录像回放，或开启实时直播</p>
                              <button
                                onClick={handleStartLiveStream}
                                disabled={isLoadingLive}
                                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2 mx-auto"
                              >
                                <Radio size={16} />
                                {isLoadingLive ? '连接中...' : '实时直播'}
                              </button>
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

                    {/* 控制按钮 - 全屏、实时直播 */}
                    {snapshot && !flvPlaybackUrl && (
                      <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/80 to-transparent flex items-center justify-end opacity-0 group-hover:opacity-100 transition-opacity duration-300 z-10">
                        <div className="flex items-center space-x-3">
                          <button
                            onClick={handleStartLiveStream}
                            disabled={isLoadingLive}
                            className="text-white hover:text-blue-400 transition-colors flex items-center gap-1"
                            title="实时直播"
                          >
                            <Radio size={18} />
                            <span className="text-xs">{isLoadingLive ? '连接中...' : '实时直播'}</span>
                          </button>
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
                    {/* Header row: title, date, AM/PM, speed */}
                    <div className="flex items-center justify-between mb-3 flex-wrap gap-2">
                      <h3 className="font-semibold text-gray-800 flex items-center text-sm">
                        <Calendar size={16} className="mr-1.5 text-blue-500" />
                        {t('playback')}
                      </h3>
                      <div className="flex items-center gap-2 flex-wrap">
                        {/* Date picker */}
                        <input
                          type="date"
                          value={selectedDate}
                          onChange={(e) => handleDateChange(e.target.value)}
                          className="bg-gray-50 border border-gray-200 rounded-lg px-2.5 py-1 text-xs outline-none focus:ring-1 focus:ring-blue-500"
                        />
                        {/* AM/PM toggle */}
                        <div className="flex bg-gray-100 rounded-lg p-0.5">
                          <button
                            onClick={() => { setTimelineHalf('AM'); setSelectedHour(null); setSelectedMinute(null); }}
                            className={`px-2.5 py-1 rounded-md text-xs font-medium transition-all ${timelineHalf === 'AM' ? 'bg-white shadow-sm text-blue-600' : 'text-gray-500 hover:text-gray-700'}`}
                          >
                            上午
                          </button>
                          <button
                            onClick={() => { setTimelineHalf('PM'); setSelectedHour(null); setSelectedMinute(null); }}
                            className={`px-2.5 py-1 rounded-md text-xs font-medium transition-all ${timelineHalf === 'PM' ? 'bg-white shadow-sm text-blue-600' : 'text-gray-500 hover:text-gray-700'}`}
                          >
                            下午
                          </button>
                        </div>
                        {/* 转码播放（浏览器不支持 H.265 时使用） */}
                        {playbackFilePath && (
                          <button
                            onClick={handleStartTranscodePlayback}
                            disabled={isLoadingTranscode}
                            className="px-2.5 py-1 rounded-md text-xs font-medium bg-amber-100 text-amber-800 hover:bg-amber-200 disabled:opacity-50"
                          >
                            {isLoadingTranscode ? '转码中...' : '转码播放'}
                          </button>
                        )}
                        {/* Speed control */}
                        <div className="flex items-center bg-gray-100 rounded-lg p-0.5">
                          <Gauge size={12} className="ml-1.5 text-gray-400" />
                          {[1, 2, 3].map(s => (
                            <button
                              key={s}
                              onClick={() => {
                                setPlaybackSpeed(s);
                                if (videoRef.current) videoRef.current.playbackRate = s;
                              }}
                              className={`px-2 py-1 rounded-md text-xs font-medium transition-all ${playbackSpeed === s ? 'bg-white shadow-sm text-blue-600' : 'text-gray-500 hover:text-gray-700'}`}
                            >
                              {s}x
                            </button>
                          ))}
                        </div>
                      </div>
                    </div>

                    {/* 第一级：小时选择 */}
                    <div className="flex flex-wrap gap-1 mb-2">
                      {Array.from({ length: 12 }, (_, i) => {
                        const h = baseHour + i;
                        const isSelected = selectedHour === h;
                        return (
                          <button
                            key={h}
                            type="button"
                            onClick={() => { setSelectedHour(h); setSelectedMinute(null); }}
                            className={`px-2 py-1 rounded-md text-xs font-medium transition-all ${isSelected ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
                          >
                            {String(h).padStart(2, '0')}
                          </button>
                        );
                      })}
                    </div>

                    {/* 第二级：选中小时后的 60 个分钟方块 */}
                    {selectedHour !== null && (
                      <>
                        <div className="flex mb-0.5 select-none text-[10px] font-mono text-gray-400">
                          {[0, 10, 20, 30, 40, 50].map(m => (
                            <div key={m} className="flex-1 text-center" style={{ width: `${100/6}%` }}>:{String(m).padStart(2, '0')}</div>
                          ))}
                        </div>
                        <div className="flex h-8 rounded-lg overflow-hidden border border-gray-200 select-none">
                          {Array.from({ length: 60 }, (_, minute) => {
                            const isFuture = isMinuteInFuture(selectedHour, minute);
                            const isSelected = selectedMinute === minute;
                            return (
                              <div
                                key={minute}
                                role="button"
                                tabIndex={0}
                                onClick={() => handleMinuteClick(selectedHour, minute)}
                                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); handleMinuteClick(selectedHour, minute); } }}
                                title={isFuture ? '未到时间' : `${String(selectedHour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`}
                                className={`flex-1 min-w-0 transition-colors duration-75 border-r border-gray-100 last:border-r-0 ${
                                  isFuture ? 'bg-gray-100 cursor-not-allowed pointer-events-none opacity-60' : 'cursor-pointer hover:bg-blue-100'
                                } ${isSelected ? 'bg-blue-500 hover:bg-blue-600' : ''}`}
                              />
                            );
                          })}
                        </div>
                      </>
                    )}

                    <div className="mt-2 flex items-center justify-end gap-1.5">
                      {playbackVideoUrl && (
                        <button
                          onClick={() => {
                            if (playbackVideoUrl && playbackVideoUrl.startsWith('blob:')) URL.revokeObjectURL(playbackVideoUrl);
                            setPlaybackVideoUrl(null);
                            setSnapshot(null);
                            handleSnapshot();
                          }}
                          className="px-3 py-1 bg-gray-100 text-gray-600 rounded-lg hover:bg-gray-200 transition-colors text-xs font-medium"
                        >
                          返回快照
                        </button>
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

                    {/* Speed/Rate Control */}
                    <div className="mb-6 px-2">
                      <div className="flex justify-between items-center mb-2">
                        <label className="text-xs font-medium text-gray-600">{language === 'zh' ? '云台速率' : 'PTZ Rate'}</label>
                        <span className="text-xs font-bold text-blue-600">{ptzRate}</span>
                      </div>
                      <input
                        type="range"
                        min="1"
                        max="100"
                        value={ptzRate}
                        onChange={(e) => setPtzRate(parseInt(e.target.value))}
                        className="w-full h-2 bg-gray-100 rounded-lg appearance-none cursor-pointer accent-blue-600"
                      />
                      <div className="flex justify-between text-[10px] text-gray-400 mt-1">
                        <span>1</span>
                        <span>50</span>
                        <span>100</span>
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
                          onClick={async () => {
                            if (playbackFilePath && deviceId) {
                              try {
                                const blobUrl = await deviceService.fetchPlaybackBlob(deviceId, playbackFilePath);
                                const link = document.createElement('a');
                                link.href = blobUrl;
                                link.download = `playback_${deviceId}_${new Date().getTime()}.mp4`;
                                document.body.appendChild(link);
                                link.click();
                                document.body.removeChild(link);
                                URL.revokeObjectURL(blobUrl);
                              } catch (err: any) {
                                modal.showModal({ message: err.message || '导出失败', type: 'error' });
                              }
                            }
                          }}
                          className={`flex flex-col items-center justify-center p-3 rounded-xl transition-colors border ${playbackFilePath
                            ? 'bg-blue-50 hover:bg-blue-100 text-blue-600 border-blue-100'
                            : 'bg-gray-50 text-gray-400 border-transparent cursor-not-allowed'
                            }`}
                          disabled={!playbackFilePath}
                        >
                          <Download size={20} className="mb-1" />
                          <span className="text-xs font-medium">{language === 'zh' ? '导出录像' : 'Export Video'}</span>
                        </button>
                      </div>
                  </div>
                </div>
              </div>
            )}

            {activeTab === 'config' && deviceId && <DeviceConfig deviceId={deviceId} />}

            {activeTab === 'ptz' && deviceId && <PtzViewPage deviceId={deviceId} />}
          </div>
        </div>
      </div >
    </>
  );
};