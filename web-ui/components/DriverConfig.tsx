import React, { useState, useEffect } from 'react';
import { HardDrive, CheckCircle2, XCircle, FileCode, X, AlertCircle, Loader2 } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { Driver } from '../types';
import { driverService } from '../src/api/services';
import { Modal as AlertModal } from './Modal';
import { useModal } from '../hooks/useModal';

// Simple reused modal (duplicated to avoid external dependency for this file update)
const Modal = ({ isOpen, onClose, title, children, footer }: any) => {
  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-black/40 backdrop-blur-sm transition-opacity" onClick={onClose}></div>
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-lg z-[101] overflow-hidden animate-fade-in">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
          <h3 className="text-lg font-bold text-gray-800">{title}</h3>
          <button onClick={onClose} className="p-1 rounded-full hover:bg-gray-200 transition-colors text-gray-500">
            <X size={20} />
          </button>
        </div>
        <div className="p-6">{children}</div>
        {footer && <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end space-x-3">{footer}</div>}
      </div>
    </div>
  );
};

export const DriverConfig: React.FC = () => {
  const { t } = useAppContext();
  const alertModal = useModal();
  const [activeModal, setActiveModal] = useState<'NONE' | 'LOGS'>('NONE');
  const [drivers, setDrivers] = useState<Driver[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [logContent, setLogContent] = useState<string[]>([]);
  const [isLoadingLogs, setIsLoadingLogs] = useState(false);
  const [healthStatus, setHealthStatus] = useState<Record<string, {
    libPath: {
      exists: boolean;
      isDirectory: boolean;
      isFile: boolean;
      readable: boolean;
      writable: boolean;
      executable: boolean;
      error?: string;
    };
    logPath: {
      exists: boolean;
      isDirectory: boolean;
      isFile: boolean;
      readable: boolean;
      writable: boolean;
      error?: string;
    };
  }>>({});
  const [checkingDrivers, setCheckingDrivers] = useState<Set<string>>(new Set());
  

  // 检查SDK健康状态
  const checkDriverHealth = async (driverId: string) => {
    if (checkingDrivers.has(driverId)) return;
    
    setCheckingDrivers(prev => new Set(prev).add(driverId));
    try {
      const response = await driverService.checkDriver(driverId);
      if (response.data) {
        setHealthStatus(prev => ({
          ...prev,
          [driverId]: response.data
        }));
      }
    } catch (err) {
      console.error(`检查SDK健康状态失败: ${driverId}`, err);
    } finally {
      setCheckingDrivers(prev => {
        const newSet = new Set(prev);
        newSet.delete(driverId);
        return newSet;
      });
    }
  };

  // 加载驱动列表并检查健康状态
  useEffect(() => {
    const loadDrivers = async () => {
      setIsLoading(true);
      try {
        const response = await driverService.getDrivers();
        // 转换后端数据格式
        const driverList = response.data.map((d: any) => ({
          id: d.id || d.name?.toLowerCase().replace(/\s+/g, '-'),
          name: d.name || '',
          version: d.version || '1.0.0',
          status: (d.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE') as 'ACTIVE' | 'INACTIVE',
          libPath: d.libPath || '',
          logPath: d.logPath || '',
          logLevel: d.logLevel || 0,
        }));
        setDrivers(driverList);
      } catch (err) {
        console.error('加载驱动列表失败:', err);
      } finally {
        setIsLoading(false);
      }
    };

    loadDrivers();
  }, []);

  // 获取SDK健康状态
  const getDriverHealth = (driverId: string) => {
    const health = healthStatus[driverId];
    if (!health) return null;
    
    const libOk = health.libPath.exists && health.libPath.readable;
    const logOk = health.logPath.exists && health.logPath.writable;
    
    return {
      isHealthy: libOk && logOk,
      libOk,
      logOk,
      details: health
    };
  };

  const handleLogs = async () => {
    setActiveModal('LOGS');
    setIsLoadingLogs(true);
    try {
      const response = await driverService.getDriverLogs(200);
      if (response.data && response.data.content) {
        setLogContent(response.data.content);
      } else {
        setLogContent([]);
      }
    } catch (err: any) {
      console.error('获取驱动日志失败:', err);
      alertModal.showModal({
        message: err.message || '获取驱动日志失败',
        type: 'error',
      });
      setLogContent([]);
    } finally {
      setIsLoadingLogs(false);
    }
  };

  // 一键检查所有SDK健康状态
  const handleCheckAll = async () => {
    // 标记所有驱动为检查中
    const allDriverIds = drivers.map(d => d.id);
    setCheckingDrivers(new Set(allDriverIds));
    
    try {
      const response = await driverService.checkAllDrivers();
      if (response.data && Array.isArray(response.data)) {
        const newHealthStatus: Record<string, any> = {};
        response.data.forEach((item: any) => {
          newHealthStatus[item.driverId] = item.health;
        });
        setHealthStatus(newHealthStatus);
        alertModal.showModal({
          message: `已检查 ${response.data.length} 个SDK的健康状态`,
          type: 'success',
        });
      }
    } catch (err: any) {
      alertModal.showModal({
        message: err.message || '检查失败',
        type: 'error',
      });
    } finally {
      setCheckingDrivers(new Set());
    }
  };



  return (
    <>
      {/* 弹窗组件 */}
      <AlertModal
        isOpen={alertModal.isOpen}
        onClose={alertModal.closeModal}
        title={alertModal.modalOptions?.title}
        message={alertModal.modalOptions?.message || ''}
        type={alertModal.modalOptions?.type || 'info'}
        confirmText={alertModal.modalOptions?.confirmText}
        onConfirm={alertModal.modalOptions?.onConfirm}
      />
      
      <div className="space-y-4">
       <div className="bg-gradient-to-r from-blue-600 to-blue-800 rounded-xl p-4 text-white shadow-lg shadow-blue-900/20">
        <div className="flex items-center justify-between gap-4">
          <div className="min-w-0">
            <h2 className="text-lg font-bold mb-1">{t('driver_mgmt')}</h2>
            <p className="text-blue-100 text-sm max-w-xl">
                管理系统集成的各类SDK驱动，包括摄像头、雷达等设备的SDK配置。启用前请确保库文件路径正确且具有访问权限。
            </p>
          </div>
          <div className="flex items-center shrink-0 space-x-2">
            <button
              onClick={handleLogs}
              disabled={isLoading}
              className="flex items-center space-x-1.5 px-3 py-1.5 text-sm bg-white/20 hover:bg-white/30 text-white rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <FileCode size={16} />
              <span>{t('driver_logs')}</span>
            </button>
            <button
              onClick={handleCheckAll}
              disabled={isLoading}
              className="flex items-center space-x-1.5 px-3 py-1.5 text-sm bg-white/20 hover:bg-white/30 text-white rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isLoading ? (
                <Loader2 size={16} className="animate-spin" />
              ) : (
                <AlertCircle size={16} />
              )}
              <span>一键检查全部</span>
            </button>
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center h-40">
          <div className="w-6 h-6 border-2 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
        </div>
      ) : (
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {drivers.map((driver) => (
          <div key={driver.id} className="bg-white rounded-xl p-3 shadow-sm border border-gray-100 flex flex-col justify-between min-h-0 hover:shadow-md transition-shadow duration-200">
            <div>
              <div className="flex items-start justify-between gap-2 mb-2">
                <div className="flex items-center space-x-2 min-w-0">
                  <div className={`p-1.5 rounded-lg shrink-0 ${driver.status === 'ACTIVE' ? 'bg-blue-50 text-blue-600' : 'bg-gray-100 text-gray-500'}`}>
                    <HardDrive size={18} />
                  </div>
                  <div className="min-w-0">
                    <h3 className="font-semibold text-gray-800 text-sm truncate">{driver.name}</h3>
                    <p className="text-xs text-gray-500">v{driver.version}</p>
                  </div>
                </div>
                <div className="flex flex-col items-end gap-1 shrink-0">
                  {/* SDK健康状态指示器 */}
                  {(() => {
                    const health = getDriverHealth(driver.id);
                    const isChecking = checkingDrivers.has(driver.id);
                    if (isChecking) {
                      return (
                        <div className="flex items-center space-x-1 px-2 py-0.5 rounded-full text-[10px] font-semibold border bg-gray-50 text-gray-600 border-gray-200">
                          <Loader2 size={10} className="animate-spin" />
                          <span>检查中</span>
                        </div>
                      );
                    }
                    if (health) {
                      return (
                        <div className={`flex items-center space-x-1 px-2 py-0.5 rounded-full text-[10px] font-semibold border ${
                          health.isHealthy 
                            ? 'bg-green-50 text-green-700 border-green-200' 
                            : 'bg-red-50 text-red-700 border-red-200'
                        }`}>
                          {health.isHealthy ? <CheckCircle2 size={10}/> : <AlertCircle size={10}/>}
                          <span>{health.isHealthy ? '健康' : '异常'}</span>
                        </div>
                      );
                    }
                    return null;
                  })()}
                  <div className={`flex items-center space-x-1 px-2 py-0.5 rounded-full text-[10px] font-semibold border ${driver.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border-green-200' : 'bg-gray-50 text-gray-600 border-gray-200'}`}>
                   {driver.status === 'ACTIVE' ? <CheckCircle2 size={10}/> : <XCircle size={10}/>}
                   <span>{driver.status}</span>
                </div>
                </div>
              </div>

              <div className="space-y-2">
                <div className={`bg-gray-50 rounded-md p-2 text-xs font-mono break-all border ${
                  (() => {
                    const health = getDriverHealth(driver.id);
                    if (health && !health.libOk) {
                      return 'border-red-200 bg-red-50/30';
                    }
                    return 'border-gray-100';
                  })()
                }`}>
                  <div className="flex items-center justify-between mb-0.5">
                    <span className="text-gray-400 select-none text-[10px]">LIB: </span>
                    {(() => {
                      const health = getDriverHealth(driver.id);
                      if (health) {
                        if (!health.libOk) {
                          return <span className="text-[10px] text-red-600 flex items-center space-x-0.5">
                            <AlertCircle size={10} />
                            <span>异常</span>
                          </span>;
                        }
                        return <span className="text-[10px] text-green-600 flex items-center space-x-0.5">
                          <CheckCircle2 size={10} />
                          <span>正常</span>
                        </span>;
                      }
                      return null;
                    })()}
                  </div>
                  <div className="text-gray-600 text-[11px] leading-tight">{driver.libPath}</div>
                  {(() => {
                    const health = getDriverHealth(driver.id);
                    if (health && health.details.libPath) {
                      const lib = health.details.libPath;
                      if (!lib.exists) {
                        return <div className="text-[10px] text-red-600 mt-0.5">文件不存在</div>;
                      }
                      if (!lib.readable) {
                        return <div className="text-[10px] text-red-600 mt-0.5">无读取权限</div>;
                      }
                    }
                    return null;
                  })()}
                </div>
                <div className={`bg-gray-50 rounded-md p-2 text-xs font-mono break-all border ${
                  (() => {
                    const health = getDriverHealth(driver.id);
                    if (health && !health.logOk) {
                      return 'border-red-200 bg-red-50/30';
                    }
                    return 'border-gray-100';
                  })()
                }`}>
                  <div className="flex items-center justify-between mb-0.5">
                    <span className="text-gray-400 select-none text-[10px]">LOG: </span>
                    {(() => {
                      const health = getDriverHealth(driver.id);
                      if (health) {
                        if (!health.logOk) {
                          return <span className="text-[10px] text-red-600 flex items-center space-x-0.5">
                            <AlertCircle size={10} />
                            <span>异常</span>
                          </span>;
                        }
                        return <span className="text-[10px] text-green-600 flex items-center space-x-0.5">
                          <CheckCircle2 size={10} />
                          <span>正常</span>
                        </span>;
                      }
                      return null;
                    })()}
                  </div>
                  <div className="text-gray-600 text-[11px] leading-tight">{driver.logPath}</div>
                  {(() => {
                    const health = getDriverHealth(driver.id);
                    if (health && health.details.logPath) {
                      const log = health.details.logPath;
                      if (!log.exists) {
                        return <div className="text-[10px] text-red-600 mt-0.5">路径不存在</div>;
                      }
                      if (!log.writable) {
                        return <div className="text-[10px] text-red-600 mt-0.5">无写入权限</div>;
                      }
                    }
                    return null;
                  })()}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
      )}

      {/* Logs Drawer */}
      {activeModal === 'LOGS' && (
        <div className="fixed inset-0 z-[100]">
          {/* 背景遮罩 */}
          <div
            className="fixed inset-0 bg-black/50 backdrop-blur-sm transition-opacity"
            onClick={() => setActiveModal('NONE')}
          />
          {/* 抽屉内容 */}
          <div className="fixed right-0 top-0 h-full w-2/3 bg-white shadow-2xl z-[101] flex flex-col transform transition-transform duration-300 ease-out">
            {/* 抽屉头部 */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 bg-gray-50/50">
              <div className="flex items-center space-x-3">
                <FileCode size={24} className="text-gray-700" />
                <h3 className="text-lg font-bold text-gray-800">{t('driver_logs')}</h3>
              </div>
              <button
                onClick={() => setActiveModal('NONE')}
                className="p-2 rounded-full hover:bg-gray-200 transition-colors text-gray-500"
              >
                <X size={20} />
              </button>
            </div>
            {/* 抽屉内容 */}
            <div className="flex-1 overflow-auto p-6">
              <div className="bg-gray-900 rounded-xl p-4 h-full overflow-auto">
                {isLoadingLogs ? (
                  <div className="flex items-center justify-center py-8">
                    <Loader2 size={24} className="animate-spin text-green-400" />
                    <span className="ml-2 text-green-400">加载中...</span>
                  </div>
                ) : logContent.length === 0 ? (
                  <div className="text-gray-500 text-center py-8">暂无日志内容</div>
                ) : (
                  <pre className="text-green-400 font-mono text-xs whitespace-pre-wrap">
                    {logContent.join('\n')}
                  </pre>
                )}
              </div>
            </div>
            {/* 抽屉底部 */}
            <div className="flex items-center justify-end px-6 py-4 border-t border-gray-200 bg-gray-50/50">
              <button
                onClick={() => setActiveModal('NONE')}
                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-xl hover:bg-gray-200 transition-colors font-medium"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
    </>
  );
};