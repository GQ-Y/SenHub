import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { Shield, Wifi, AlertTriangle, HardDrive, Loader2, CheckCircle2, XCircle, Database, Server, FileCode, X, RefreshCw } from 'lucide-react';
import { CHART_DATA } from '../constants';
import { useAppContext } from '../contexts/AppContext';
import { dashboardService, systemService } from '../src/api/services';
import { useModal } from '../hooks/useModal';
import { Modal, ConfirmModal } from './Modal';

const StatCard = ({ icon: Icon, label, value, trend, trendValue, colorClass, bgClass, subtitle }: any) => (
  <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow duration-300">
    <div className="flex items-center justify-between mb-4">
      <div className={`p-3 rounded-xl ${bgClass}`}>
        <Icon className={colorClass} size={24} />
      </div>
      {trend && trendValue && (
        <span className={`text-xs font-semibold px-2 py-1 rounded-full ${trend === 'up' ? 'bg-green-50 text-green-600' : 'bg-red-50 text-red-600'}`}>
          {trend === 'up' ? '+' : '-'}{trendValue}
        </span>
      )}
    </div>
    <h3 className="text-3xl font-bold text-gray-800 mb-1">{value}</h3>
    <p className="text-sm text-gray-500 font-medium">{label}</p>
    {subtitle && <p className="text-xs text-gray-400 mt-1">{subtitle}</p>}
  </div>
);

export const Dashboard: React.FC = () => {
  const { t } = useAppContext();
  const navigate = useNavigate();
  const modal = useModal();
  const [stats, setStats] = useState({
    activeDevices: 0,
    onlineStatus: '0%',
    alerts24h: 0,
    storageUsed: '0 B',
    storageTotal: '0 B',
    storagePercent: '0.0%',
  });
  const [chartData, setChartData] = useState(CHART_DATA);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isActionLoading, setIsActionLoading] = useState<string | null>(null);
  const [logModalOpen, setLogModalOpen] = useState(false);
  const [logContent, setLogContent] = useState<string[]>([]);
  const [healthModalOpen, setHealthModalOpen] = useState(false);
  const [healthData, setHealthData] = useState<any>(null);

  const loadData = async () => {
    try {
      setError(null);
      setIsLoading(true);
      const [statsResponse, chartResponse] = await Promise.all([
        dashboardService.getStats(),
        dashboardService.getChartData(),
      ]);
      
      setStats(statsResponse.data);
      if (chartResponse.data && chartResponse.data.length > 0) {
        setChartData(chartResponse.data);
      }
    } catch (err: any) {
      const errorMessage = err.message || '加载仪表板数据失败，请稍后重试';
      setError(errorMessage);
      console.error('加载仪表板数据失败:', err);
      
      // 显示错误提示
      modal.showModal({
        message: errorMessage,
        type: 'error',
        title: '加载失败',
        confirmText: '确定',
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    // 每30秒刷新一次
    const interval = setInterval(loadData, 30000);
    return () => clearInterval(interval);
  }, []);

  const handleHealthCheck = async () => {
    setIsActionLoading('health');
    try {
      const response = await systemService.healthCheck();
      const health = response.data;
      setHealthData(health);
      setHealthModalOpen(true);
    } catch (err: any) {
      modal.showModal({
        message: err.message || '健康检查失败，请稍后重试',
        type: 'error',
        title: '健康检查失败',
        confirmText: '确定',
      });
    } finally {
      setIsActionLoading(null);
    }
  };

  const handleRestartMqtt = async () => {
    modal.showConfirm({
      title: '确认重启',
      message: '确定要重启MQTT连接吗？',
      onConfirm: async () => {
        setIsActionLoading('mqtt');
        try {
          const response = await systemService.restartMqtt();
          if (response.data.success) {
            modal.showModal({
              message: 'MQTT重启成功',
              type: 'success',
              title: '操作成功',
              confirmText: '确定',
            });
          } else {
            modal.showModal({
              message: response.data.message || 'MQTT重启失败',
              type: 'error',
              title: '操作失败',
              confirmText: '确定',
            });
          }
        } catch (err: any) {
          modal.showModal({
            message: err.message || '重启MQTT失败，请稍后重试',
            type: 'error',
            title: '操作失败',
            confirmText: '确定',
          });
        } finally {
          setIsActionLoading(null);
        }
      },
      confirmText: '确定',
      cancelText: '取消',
    });
  };

  const handleViewLogs = async () => {
    setIsActionLoading('logs');
    try {
      const response = await systemService.getLogs(100);
      setLogContent(response.data.content);
      setLogModalOpen(true);
    } catch (err: any) {
      modal.showModal({
        message: err.message || '获取日志失败，请稍后重试',
        type: 'error',
        title: '获取失败',
        confirmText: '确定',
      });
    } finally {
      setIsActionLoading(null);
    }
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

      {isLoading && !error ? (
        <div className="flex items-center justify-center h-64">
          <div className="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
        </div>
      ) : error ? (
        <div className="flex flex-col items-center justify-center h-64 space-y-4">
          <AlertTriangle className="w-16 h-16 text-red-500" />
          <div className="text-center">
            <h3 className="text-lg font-semibold text-gray-800 mb-2">加载失败</h3>
            <p className="text-gray-600 mb-4">{error}</p>
            <button
              onClick={loadData}
              className="inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              <RefreshCw className="w-4 h-4 mr-2" />
              重试
            </button>
          </div>
        </div>
      ) : (
        <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard 
          icon={Shield} 
          label={t('active_devices')}
          value={stats.activeDevices.toString()} 
          colorClass="text-blue-600" 
          bgClass="bg-blue-50"
        />
        <StatCard 
          icon={Wifi} 
          label={t('online_status')}
          value={stats.onlineStatus} 
          colorClass="text-green-600" 
          bgClass="bg-green-50"
        />
        <StatCard 
          icon={AlertTriangle} 
          label={t('alerts_24h')} 
          value={stats.alerts24h.toString()} 
          colorClass="text-orange-600" 
          bgClass="bg-orange-50"
        />
        <StatCard 
          icon={HardDrive} 
          label={t('storage_used')} 
          value={stats.storageUsed} 
          subtitle={stats.storageTotal ? `${stats.storagePercent} / ${stats.storageTotal}` : undefined}
          colorClass="text-purple-600" 
          bgClass="bg-purple-50"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h3 className="text-lg font-bold text-gray-800">{t('event_statistics')}</h3>
              <div className="flex items-center gap-4 mt-2">
                <div className="flex items-center gap-1.5">
                  <span className="w-3 h-3 rounded-full bg-red-500"></span>
                  <span className="text-xs text-gray-500">{t('alarm_events')}</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-3 h-3 rounded-full bg-blue-500"></span>
                  <span className="text-xs text-gray-500">{t('workflow_executions')}</span>
                </div>
              </div>
            </div>
            <span className="text-sm text-gray-500 font-medium">{t('last_24_hours')}</span>
          </div>
          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="colorAlarms" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#ef4444" stopOpacity={0.2}/>
                    <stop offset="95%" stopColor="#ef4444" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="colorWorkflows" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.2}/>
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{fill: '#94a3b8', fontSize: 12}} dy={10} interval={2} />
                <YAxis axisLine={false} tickLine={false} tick={{fill: '#94a3b8', fontSize: 12}} />
                <Tooltip 
                  contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)' }}
                  formatter={(value: number, name: string) => {
                    const label = name === 'alarms' ? t('alarm_events') : t('workflow_executions');
                    return [value, label];
                  }}
                />
                <Area 
                  type="monotone" 
                  dataKey="alarms" 
                  stroke="#ef4444" 
                  strokeWidth={2}
                  fillOpacity={1} 
                  fill="url(#colorAlarms)" 
                  name="alarms"
                />
                <Area 
                  type="monotone" 
                  dataKey="workflows" 
                  stroke="#3b82f6" 
                  strokeWidth={2}
                  fillOpacity={1} 
                  fill="url(#colorWorkflows)" 
                  name="workflows"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
          <h3 className="text-lg font-bold text-gray-800 mb-4">{t('quick_actions')}</h3>
          <div className="space-y-3">
            <button 
              onClick={() => navigate('/devices')}
              className="w-full py-3 px-4 bg-gray-50 hover:bg-blue-50 hover:text-blue-600 rounded-xl flex items-center justify-between group transition-colors"
            >
              <span className="font-medium text-sm">{t('add_device')}</span>
              <span className="bg-white p-1 rounded-md shadow-sm group-hover:bg-blue-200 transition-colors">+</span>
            </button>
            <button 
              onClick={handleHealthCheck}
              disabled={isActionLoading === 'health'}
              className="w-full py-3 px-4 bg-gray-50 hover:bg-blue-50 hover:text-blue-600 rounded-xl flex items-center justify-between group transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span className="font-medium text-sm">{t('health_check')}</span>
              {isActionLoading === 'health' ? (
                <Loader2 className="w-4 h-4 animate-spin text-blue-600" />
              ) : (
                <span className="bg-white p-1 rounded-md shadow-sm group-hover:bg-blue-200 transition-colors">Run</span>
              )}
            </button>
            <button 
              onClick={handleRestartMqtt}
              disabled={isActionLoading === 'mqtt'}
              className="w-full py-3 px-4 bg-gray-50 hover:bg-blue-50 hover:text-blue-600 rounded-xl flex items-center justify-between group transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span className="font-medium text-sm">{t('restart_mqtt')}</span>
              {isActionLoading === 'mqtt' ? (
                <Loader2 className="w-4 h-4 animate-spin text-blue-600" />
              ) : (
                <span className="bg-white p-1 rounded-md shadow-sm group-hover:bg-blue-200 transition-colors">Auto</span>
              )}
            </button>
            <button 
              onClick={handleViewLogs}
              disabled={isActionLoading === 'logs'}
              className="w-full py-3 px-4 bg-gray-50 hover:bg-blue-50 hover:text-blue-600 rounded-xl flex items-center justify-between group transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span className="font-medium text-sm">{t('view_logs')}</span>
              {isActionLoading === 'logs' ? (
                <Loader2 className="w-4 h-4 animate-spin text-blue-600" />
              ) : (
                <span className="bg-white p-1 rounded-md shadow-sm group-hover:bg-blue-200 transition-colors">→</span>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* 系统体检结果Modal */}
      {healthModalOpen && healthData && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center">
          <div
            className="fixed inset-0 bg-black/50 backdrop-blur-sm"
            onClick={() => setHealthModalOpen(false)}
          />
          <div className="relative bg-white rounded-2xl shadow-2xl max-w-2xl w-full mx-4 z-[101]">
            <div className="flex items-center justify-between p-6 border-b border-gray-200">
              <div className="flex items-center space-x-3">
                {healthData.status === 'healthy' ? (
                  <CheckCircle2 className="w-6 h-6 text-green-500" />
                ) : (
                  <AlertTriangle className="w-6 h-6 text-yellow-500" />
                )}
                <h3 className="text-lg font-semibold text-gray-800">系统体检结果</h3>
              </div>
              <button
                onClick={() => setHealthModalOpen(false)}
                className="p-1 hover:bg-gray-100 rounded-lg transition-colors"
              >
                <span className="text-gray-500 text-xl">×</span>
              </button>
            </div>
            <div className="p-6 space-y-4">
              {/* 总体状态 */}
              <div className={`p-4 rounded-xl ${
                healthData.status === 'healthy' 
                  ? 'bg-green-50 border border-green-200' 
                  : 'bg-yellow-50 border border-yellow-200'
              }`}>
                <div className="flex items-center justify-between">
                  <span className="font-medium text-gray-700">系统状态</span>
                  <span className={`px-3 py-1 rounded-full text-sm font-semibold ${
                    healthData.status === 'healthy'
                      ? 'bg-green-100 text-green-700'
                      : 'bg-yellow-100 text-yellow-700'
                  }`}>
                    {healthData.status === 'healthy' ? '健康' : '警告'}
                  </span>
                </div>
              </div>

              {/* MQTT状态 */}
              <div className="bg-gray-50 p-4 rounded-xl">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center space-x-2">
                    <Server className="w-5 h-5 text-gray-600" />
                    <span className="font-medium text-gray-700">MQTT连接</span>
                  </div>
                  <div className="flex items-center space-x-2">
                    {healthData.mqtt.connected ? (
                      <>
                        <CheckCircle2 className="w-5 h-5 text-green-500" />
                        <span className="text-sm text-green-600 font-medium">已连接</span>
                      </>
                    ) : (
                      <>
                        <XCircle className="w-5 h-5 text-red-500" />
                        <span className="text-sm text-red-600 font-medium">未连接</span>
                      </>
                    )}
                  </div>
                </div>
              </div>

              {/* 数据库状态 */}
              <div className="bg-gray-50 p-4 rounded-xl">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center space-x-2">
                    <Database className="w-5 h-5 text-gray-600" />
                    <span className="font-medium text-gray-700">数据库</span>
                  </div>
                  <div className="flex items-center space-x-2">
                    <CheckCircle2 className="w-5 h-5 text-green-500" />
                    <span className="text-sm text-green-600 font-medium">{healthData.database.status}</span>
                  </div>
                </div>
              </div>

              {/* SDK状态 */}
              <div className="bg-gray-50 p-4 rounded-xl">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center space-x-2">
                    <Shield className="w-5 h-5 text-gray-600" />
                    <span className="font-medium text-gray-700">SDK</span>
                  </div>
                  <div className="flex items-center space-x-2">
                    <CheckCircle2 className="w-5 h-5 text-green-500" />
                    <span className="text-sm text-green-600 font-medium">{healthData.sdk.status}</span>
                  </div>
                </div>
              </div>

              {/* 磁盘使用情况 */}
              <div className="bg-gray-50 p-4 rounded-xl">
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center space-x-2">
                    <HardDrive className="w-5 h-5 text-gray-600" />
                    <span className="font-medium text-gray-700">磁盘使用</span>
                  </div>
                  <span className={`text-sm font-semibold ${
                    parseFloat(healthData.disk.usagePercent) > 90 
                      ? 'text-red-600' 
                      : parseFloat(healthData.disk.usagePercent) > 70
                      ? 'text-yellow-600'
                      : 'text-green-600'
                  }`}>
                    {healthData.disk.usagePercent}
                  </span>
                </div>
                <div className="space-y-2">
                  <div className="flex justify-between text-sm text-gray-600">
                    <span>可用空间</span>
                    <span className="font-medium">{healthData.disk.freeSpace}</span>
                  </div>
                  <div className="flex justify-between text-sm text-gray-600">
                    <span>总空间</span>
                    <span className="font-medium">{healthData.disk.totalSpace}</span>
                  </div>
                  {/* 进度条 */}
                  <div className="w-full bg-gray-200 rounded-full h-2 mt-2">
                    <div
                      className={`h-2 rounded-full transition-all ${
                        parseFloat(healthData.disk.usagePercent) > 90
                          ? 'bg-red-500'
                          : parseFloat(healthData.disk.usagePercent) > 70
                          ? 'bg-yellow-500'
                          : 'bg-green-500'
                      }`}
                      style={{ width: healthData.disk.usagePercent }}
                    ></div>
                  </div>
                </div>
              </div>
            </div>
            <div className="flex items-center justify-end p-6 border-t border-gray-200">
              <button
                onClick={() => setHealthModalOpen(false)}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 日志查看Drawer */}
      {logModalOpen && (
        <div className="fixed inset-0 z-[100]">
          {/* 背景遮罩 */}
          <div
            className="fixed inset-0 bg-black/50 backdrop-blur-sm transition-opacity"
            onClick={() => setLogModalOpen(false)}
          />
          {/* 抽屉内容 */}
          <div className="fixed right-0 top-0 h-full w-2/3 bg-white shadow-2xl z-[101] flex flex-col transform transition-transform duration-300 ease-out">
            {/* 抽屉头部 */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 bg-gray-50/50">
              <div className="flex items-center space-x-3">
                <FileCode size={24} className="text-gray-700" />
                <h3 className="text-lg font-bold text-gray-800">系统日志</h3>
              </div>
              <button
                onClick={() => setLogModalOpen(false)}
                className="p-2 rounded-full hover:bg-gray-200 transition-colors text-gray-500"
              >
                <X size={20} />
              </button>
            </div>
            {/* 抽屉内容 */}
            <div className="flex-1 overflow-auto p-6">
              <div className="bg-gray-900 rounded-xl p-4 h-full overflow-auto">
                {logContent.length === 0 ? (
                  <div className="text-gray-500 text-center py-8">暂无日志内容</div>
                ) : (
                  <pre className="text-green-400 font-mono text-xs whitespace-pre-wrap">
                    {logContent.map((line, index) => (
                      <div key={index} className="mb-1">{line}</div>
                    ))}
                  </pre>
                )}
              </div>
            </div>
            {/* 抽屉底部 */}
            <div className="flex items-center justify-end px-6 py-4 border-t border-gray-200 bg-gray-50/50">
              <button
                onClick={() => setLogModalOpen(false)}
                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-xl hover:bg-gray-200 transition-colors font-medium"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
        </div>
      )}
    </>
  );
};