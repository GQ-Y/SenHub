import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { Shield, Wifi, AlertTriangle, HardDrive, Loader2 } from 'lucide-react';
import { CHART_DATA } from '../constants';
import { useAppContext } from '../contexts/AppContext';
import { dashboardService, systemService } from '../src/api/services';

const StatCard = ({ icon: Icon, label, value, trend, colorClass, bgClass }: any) => (
  <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow duration-300">
    <div className="flex items-center justify-between mb-4">
      <div className={`p-3 rounded-xl ${bgClass}`}>
        <Icon className={colorClass} size={24} />
      </div>
      <span className={`text-xs font-semibold px-2 py-1 rounded-full ${trend === 'up' ? 'bg-green-50 text-green-600' : 'bg-red-50 text-red-600'}`}>
        {trend === 'up' ? '+2.5%' : '-1.2%'}
      </span>
    </div>
    <h3 className="text-3xl font-bold text-gray-800 mb-1">{value}</h3>
    <p className="text-sm text-gray-500 font-medium">{label}</p>
  </div>
);

export const Dashboard: React.FC = () => {
  const { t } = useAppContext();
  const navigate = useNavigate();
  const [stats, setStats] = useState({
    activeDevices: 0,
    onlineStatus: '0%',
    alerts24h: 0,
    storageUsed: '0 B',
  });
  const [chartData, setChartData] = useState(CHART_DATA);
  const [isLoading, setIsLoading] = useState(true);
  const [isActionLoading, setIsActionLoading] = useState<string | null>(null);
  const [logModalOpen, setLogModalOpen] = useState(false);
  const [logContent, setLogContent] = useState<string[]>([]);

  useEffect(() => {
    const loadData = async () => {
      try {
        const [statsResponse, chartResponse] = await Promise.all([
          dashboardService.getStats(),
          dashboardService.getChartData(),
        ]);
        
        setStats(statsResponse.data);
        if (chartResponse.data && chartResponse.data.length > 0) {
          setChartData(chartResponse.data);
        }
      } catch (err) {
        console.error('加载仪表板数据失败:', err);
      } finally {
        setIsLoading(false);
      }
    };

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
      
      const statusText = health.status === 'healthy' ? '健康' : '警告';
      const mqttStatus = health.mqtt.connected ? '已连接' : '未连接';
      const diskUsage = health.disk.usagePercent;
      
      const message = `系统状态: ${statusText}\n\n` +
        `MQTT: ${mqttStatus}\n` +
        `数据库: ${health.database.status}\n` +
        `SDK: ${health.sdk.status}\n` +
        `磁盘使用: ${diskUsage}\n` +
        `可用空间: ${health.disk.freeSpace}\n` +
        `总空间: ${health.disk.totalSpace}`;
      
      alert(message);
    } catch (err: any) {
      alert('健康检查失败: ' + (err.message || '未知错误'));
    } finally {
      setIsActionLoading(null);
    }
  };

  const handleRestartMqtt = async () => {
    if (!confirm('确定要重启MQTT连接吗？')) {
      return;
    }
    
    setIsActionLoading('mqtt');
    try {
      const response = await systemService.restartMqtt();
      if (response.data.success) {
        alert('MQTT重启成功');
      } else {
        alert('MQTT重启失败: ' + response.data.message);
      }
    } catch (err: any) {
      alert('重启MQTT失败: ' + (err.message || '未知错误'));
    } finally {
      setIsActionLoading(null);
    }
  };

  const handleViewLogs = async () => {
    setIsActionLoading('logs');
    try {
      const response = await systemService.getLogs(100);
      setLogContent(response.data.content);
      setLogModalOpen(true);
    } catch (err: any) {
      alert('获取日志失败: ' + (err.message || '未知错误'));
    } finally {
      setIsActionLoading(null);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard 
          icon={Shield} 
          label={t('active_devices')}
          value={stats.activeDevices.toString()} 
          trend="up" 
          colorClass="text-blue-600" 
          bgClass="bg-blue-50"
        />
        <StatCard 
          icon={Wifi} 
          label={t('online_status')}
          value={stats.onlineStatus} 
          trend="up" 
          colorClass="text-green-600" 
          bgClass="bg-green-50"
        />
        <StatCard 
          icon={AlertTriangle} 
          label={t('alerts_24h')} 
          value={stats.alerts24h.toString()} 
          trend="down" 
          colorClass="text-orange-600" 
          bgClass="bg-orange-50"
        />
        <StatCard 
          icon={HardDrive} 
          label={t('storage_used')} 
          value={stats.storageUsed} 
          trend="up" 
          colorClass="text-purple-600" 
          bgClass="bg-purple-50"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
          <div className="flex items-center justify-between mb-6">
            <h3 className="text-lg font-bold text-gray-800">{t('device_connectivity')}</h3>
            <span className="text-sm text-gray-500 font-medium">{t('last_24_hours')}</span>
          </div>
          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="colorOnline" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#10b981" stopOpacity={0.2}/>
                    <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{fill: '#94a3b8', fontSize: 12}} dy={10} />
                <YAxis axisLine={false} tickLine={false} tick={{fill: '#94a3b8', fontSize: 12}} />
                <Tooltip 
                  contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)' }}
                />
                <Area 
                  type="monotone" 
                  dataKey="online" 
                  stroke="#10b981" 
                  strokeWidth={3}
                  fillOpacity={1} 
                  fill="url(#colorOnline)" 
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

      {/* 日志查看Modal */}
      {logModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div
            className="absolute inset-0 bg-black/50 backdrop-blur-sm"
            onClick={() => setLogModalOpen(false)}
          />
          <div className="relative bg-white rounded-2xl shadow-2xl max-w-4xl w-full mx-4 max-h-[80vh] flex flex-col">
            <div className="flex items-center justify-between p-6 border-b border-gray-200">
              <h3 className="text-lg font-semibold text-gray-800">系统日志</h3>
              <button
                onClick={() => setLogModalOpen(false)}
                className="p-1 hover:bg-gray-100 rounded-lg transition-colors"
              >
                <span className="text-gray-500 text-xl">×</span>
              </button>
            </div>
            <div className="flex-1 overflow-auto p-6">
              <div className="max-h-96 overflow-auto bg-gray-900 text-green-400 p-4 rounded-lg font-mono text-xs">
                {logContent.length === 0 ? (
                  <div className="text-gray-500">暂无日志内容</div>
                ) : (
                  logContent.map((line, index) => (
                    <div key={index} className="mb-1">{line}</div>
                  ))
                )}
              </div>
            </div>
            <div className="flex items-center justify-end p-6 border-t border-gray-200">
              <button
                onClick={() => setLogModalOpen(false)}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};