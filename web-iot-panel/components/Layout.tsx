import React, { useState, useRef, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { 
  LayoutDashboard, 
  Camera, 
  Settings, 
  Cpu, 
  Network, 
  LogOut, 
  Bell, 
  Menu,
  Languages,
  X,
  Check,
  Package,
  Shield,
  Radar,
  Wifi,
  Server
} from 'lucide-react';
import { ViewState } from '../types';
import { useAppContext } from '../contexts/AppContext';
import { notificationService, Notification } from '../src/api/services';

interface LayoutProps {
  children: React.ReactNode;
  currentView: string;
}

const SidebarItem = ({ 
  icon: Icon, 
  label, 
  isActive, 
  onClick 
}: { 
  icon: any; 
  label: string; 
  isActive: boolean; 
  onClick: () => void; 
}) => (
  <button
    onClick={onClick}
    className={`w-full flex items-center space-x-3 px-4 py-3 rounded-xl transition-all duration-200 group ${
      isActive 
        ? 'bg-blue-600 text-white shadow-lg shadow-blue-900/20' 
        : 'text-gray-400 hover:bg-gray-800 hover:text-white'
    }`}
  >
    <Icon size={20} className={`flex-shrink-0 ${isActive ? 'text-white' : 'text-gray-400 group-hover:text-white'}`} />
    <span className="font-medium text-sm tracking-wide whitespace-nowrap truncate min-w-0">{label}</span>
  </button>
);

interface Notification {
  id: string;
  title: string;
  message: string;
  time: string;
  type: 'info' | 'warning' | 'error' | 'success';
  read: boolean;
}

export const Layout: React.FC<LayoutProps> = ({ children, currentView }) => {
  const { t, logout, language, setLanguage } = useAppContext();
  const navigate = useNavigate();
  const location = useLocation();

  const handleNavigate = (view: ViewState) => {
    switch (view) {
      case 'DASHBOARD':
        navigate('/');
        break;
      case 'DEVICE_LIST':
        navigate('/devices');
        break;
      case 'ASSEMBLY_MANAGEMENT':
        navigate('/assemblies');
        break;
      case 'ASSEMBLY_DETAIL':
        // 这个由路由自动处理
        break;
      case 'DRIVER_CONFIG':
        navigate('/drivers');
        break;
      case 'MQTT_CONFIG':
        navigate('/mqtt');
        break;
      case 'ALARM_RULES':
        navigate('/alarm-rules');
        break;
      case 'SYSTEM_CONFIG':
        navigate('/settings');
        break;
      case 'RADAR':
        navigate('/radar');
        break;
      case 'WORKFLOW':
        navigate('/flows');
        break;
      default:
        navigate('/');
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };
  const [showNotifications, setShowNotifications] = useState(false);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [isLoadingNotifications, setIsLoadingNotifications] = useState(false);
  const notificationRef = useRef<HTMLDivElement>(null);

  // 加载通知列表
  const loadNotifications = async () => {
    setIsLoadingNotifications(true);
    try {
      const response = await notificationService.getNotifications(50);
      if (response.data) {
        setNotifications(response.data);
      }
    } catch (err) {
      console.error('加载通知失败:', err);
    } finally {
      setIsLoadingNotifications(false);
    }
  };

  // 初始加载和定期刷新通知
  useEffect(() => {
    loadNotifications();
    // 每30秒刷新一次通知
    const interval = setInterval(loadNotifications, 30000);
    return () => clearInterval(interval);
  }, []);

  const toggleLanguage = () => {
    setLanguage(language === 'en' ? 'zh' : 'en');
  };

  const unreadCount = notifications.filter(n => !n.read).length;

  const markAllAsRead = async () => {
    try {
      await notificationService.markAllAsRead();
      await loadNotifications(); // 重新加载通知列表
    } catch (err) {
      console.error('标记所有通知为已读失败:', err);
    }
  };

  const markAsRead = async (id: string) => {
    try {
      await notificationService.markAsRead(id);
      // 更新本地状态
      setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
    } catch (err) {
      console.error('标记通知为已读失败:', err);
    }
  };

  // 点击外部关闭弹窗
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (notificationRef.current && !notificationRef.current.contains(event.target as Node)) {
        setShowNotifications(false);
      }
    };

    if (showNotifications) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [showNotifications]);

  return (
    <div className="flex h-screen bg-[#f3f4f6] overflow-hidden">
      {/* Sidebar */}
      <aside className="w-64 bg-[#1e293b] flex-shrink-0 flex flex-col justify-between transition-all duration-300 z-20 shadow-xl">
        <div className="p-6">
          <div className="flex items-center space-x-3 mb-10">
            <div className="w-8 h-8 bg-blue-500 rounded-lg flex items-center justify-center shadow-lg shadow-blue-500/30">
              <Camera className="text-white" size={18} />
            </div>
            <h1 className="text-xl font-bold text-white tracking-tight">AI智能<span className="text-blue-400">管理系统</span></h1>
          </div>

          <nav className="space-y-2">
            <SidebarItem 
              icon={LayoutDashboard} 
              label={t('dashboard')} 
              isActive={currentView === 'DASHBOARD'} 
              onClick={() => handleNavigate('DASHBOARD')}
            />
            <div className="pt-4 pb-2">
              <p className="px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('device_mgmt')}</p>
            </div>
            <SidebarItem 
              icon={Camera} 
              label={t('cameras')} 
              isActive={currentView === 'DEVICE_LIST' || currentView === 'DEVICE_DETAIL'} 
              onClick={() => handleNavigate('DEVICE_LIST')}
            />
            <SidebarItem 
              icon={Package} 
              label={t('assembly_management')} 
              isActive={currentView === 'ASSEMBLY_MANAGEMENT' || currentView === 'ASSEMBLY_DETAIL'} 
              onClick={() => handleNavigate('ASSEMBLY_MANAGEMENT')}
            />
            <SidebarItem 
              icon={Radar} 
              label={t('radar_mgmt')} 
              isActive={currentView === 'RADAR'} 
              onClick={() => handleNavigate('RADAR')}
            />
             <div className="pt-4 pb-2">
              <p className="px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('integration')}</p>
            </div>
            <SidebarItem 
              icon={Cpu} 
              label={t('drivers')} 
              isActive={currentView === 'DRIVER_CONFIG'} 
              onClick={() => handleNavigate('DRIVER_CONFIG')}
            />
            <SidebarItem 
              icon={Network} 
              label={t('mqtt_broker')} 
              isActive={currentView === 'MQTT_CONFIG'} 
              onClick={() => handleNavigate('MQTT_CONFIG')}
            />
             <div className="pt-4 pb-2">
              <p className="px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('settings')}</p>
            </div>
            <SidebarItem 
              icon={Shield} 
              label={t('alarm_rules')} 
              isActive={currentView === 'ALARM_RULES'} 
              onClick={() => handleNavigate('ALARM_RULES')}
            />
            <SidebarItem 
              icon={Package} 
              label={t('workflow_mgmt')} 
              isActive={currentView === 'WORKFLOW'} 
              onClick={() => handleNavigate('WORKFLOW')}
            />
            <SidebarItem 
              icon={Settings} 
              label={t('system_config')} 
              isActive={currentView === 'SYSTEM_CONFIG'} 
              onClick={() => handleNavigate('SYSTEM_CONFIG')}
            />
          </nav>
        </div>

        <div className="p-4 border-t border-gray-700">
          <button 
            onClick={handleLogout}
            className="w-full flex items-center space-x-3 px-4 py-3 text-gray-400 hover:text-white hover:bg-gray-800 rounded-xl transition-colors"
          >
            <LogOut size={20} />
            <span className="font-medium text-sm">{t('sign_out')}</span>
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col min-w-0 overflow-hidden relative">
        {/* Header */}
        <header className="bg-white/80 backdrop-blur-md h-16 flex items-center justify-between px-8 border-b border-gray-200 sticky top-0 z-10">
          <div className="flex items-center space-x-4">
            <button className="lg:hidden p-2 text-gray-600 hover:bg-gray-100 rounded-lg">
              <Menu size={24} />
            </button>
            <h2 className="text-lg font-semibold text-gray-800 capitalize">
               {/* Display translated view name or map from enum if strict */}
               {currentView === 'DASHBOARD' ? t('dashboard') : 
                currentView === 'DEVICE_LIST' ? t('cameras') :
                currentView === 'DEVICE_DETAIL' ? t('live_view') :
                currentView === 'ASSEMBLY_MANAGEMENT' ? t('assembly_management') :
                currentView === 'ASSEMBLY_DETAIL' ? t('assembly_management') :
                currentView === 'RADAR' ? t('radar_mgmt') :
                currentView === 'DRIVER_CONFIG' ? t('drivers') :
                currentView === 'MQTT_CONFIG' ? t('mqtt_broker') :
                currentView === 'ALARM_RULES' ? t('alarm_rules') :
                t('system_config')}
            </h2>
          </div>

          <div className="flex items-center space-x-6">
            {/* Status Indicators (icon only) */}
            <div className="hidden md:flex items-center space-x-2">
              <span
                className="flex items-center justify-center w-9 h-9 rounded-full bg-green-50 text-green-600 border border-green-100"
                title={t('mqtt_connected')}
              >
                <Wifi size={18} className="text-green-600" />
              </span>
              <span
                className="flex items-center justify-center w-9 h-9 rounded-full bg-blue-50 text-blue-600 border border-blue-100"
                title={t('nvr_active')}
              >
                <Server size={18} className="text-blue-600" />
              </span>
            </div>

            <div className="h-6 w-px bg-gray-200 mx-2"></div>
            
            <button 
                onClick={toggleLanguage}
                className="p-2 text-gray-500 hover:text-blue-600 transition-colors rounded-lg hover:bg-blue-50 flex items-center space-x-1"
                title="Switch Language"
            >
                <Languages size={20} />
                <span className="text-xs font-bold uppercase">{language}</span>
            </button>

            <div className="relative" ref={notificationRef}>
              <button 
                onClick={() => setShowNotifications(!showNotifications)}
                className="relative p-2 text-gray-500 hover:text-blue-600 transition-colors rounded-full hover:bg-blue-50"
              >
                <Bell size={20} />
                {unreadCount > 0 && (
                  <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500 rounded-full border border-white"></span>
                )}
              </button>

              {/* 消息通知弹窗 */}
              {showNotifications && (
                <div className="absolute right-0 top-12 w-96 bg-white rounded-2xl shadow-2xl border border-gray-200 z-50 max-h-[600px] flex flex-col">
                  {/* 弹窗头部 */}
                  <div className="flex items-center justify-between p-4 border-b border-gray-200">
                    <h3 className="text-lg font-bold text-gray-800">{t('notifications')}</h3>
                    <div className="flex items-center space-x-2">
                      {unreadCount > 0 && (
                        <button
                          onClick={markAllAsRead}
                          className="text-xs text-blue-600 hover:text-blue-700 font-medium px-2 py-1 hover:bg-blue-50 rounded-lg transition-colors"
                        >
                          {t('mark_all_read')}
                        </button>
                      )}
                      <button
                        onClick={() => setShowNotifications(false)}
                        className="p-1 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                      >
                        <X size={18} />
                      </button>
                    </div>
                  </div>

                  {/* 消息列表 */}
                  <div className="overflow-y-auto flex-1">
                    {notifications.length === 0 ? (
                      <div className="p-8 text-center text-gray-400">
                        <Bell size={32} className="mx-auto mb-2 opacity-50" />
                        <p className="text-sm">{t('no_notifications')}</p>
                      </div>
                    ) : (
                      <div className="divide-y divide-gray-100">
                        {notifications.map((notification) => (
                          <div
                            key={notification.id}
                            onClick={() => markAsRead(notification.id)}
                            className={`p-4 hover:bg-gray-50 cursor-pointer transition-colors ${
                              !notification.read ? 'bg-blue-50/50' : ''
                            }`}
                          >
                            <div className="flex items-start space-x-3">
                              <div className={`flex-shrink-0 w-2 h-2 rounded-full mt-2 ${
                                notification.type === 'success' ? 'bg-green-500' :
                                notification.type === 'warning' ? 'bg-yellow-500' :
                                notification.type === 'error' ? 'bg-red-500' :
                                'bg-blue-500'
                              }`}></div>
                              <div className="flex-1 min-w-0">
                                <div className="flex items-center justify-between mb-1">
                                  <h4 className={`text-sm font-semibold ${
                                    !notification.read ? 'text-gray-900' : 'text-gray-700'
                                  }`}>
                                    {notification.title}
                                  </h4>
                                  {!notification.read && (
                                    <div className="w-2 h-2 bg-blue-500 rounded-full flex-shrink-0"></div>
                                  )}
                                </div>
                                <p className="text-sm text-gray-600 mb-2 line-clamp-2">
                                  {notification.message}
                                </p>
                                <p className="text-xs text-gray-400">{notification.time}</p>
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
            
            <div className="flex items-center space-x-3 pl-2">
              <div className="text-right hidden sm:block">
                <p className="text-sm font-semibold text-gray-800">{t('admin_user')}</p>
                <p className="text-xs text-gray-500">{t('security_manager')}</p>
              </div>
              <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-blue-500 to-cyan-400 flex items-center justify-center text-white font-bold shadow-md">
                AD
              </div>
            </div>
          </div>
        </header>

        {/* Scrollable Content Area */}
        <div className="flex-1 overflow-hidden flex flex-col">
          <div className="flex-1 overflow-auto p-6">
            <div className={`h-full animate-fade-in ${currentView === 'WORKFLOW' ? '' : 'max-w-7xl mx-auto'}`}>
              {children}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
};