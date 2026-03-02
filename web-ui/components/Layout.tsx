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
  Server,
  Brain,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import { ViewState } from '../types';
import { useAppContext } from '../contexts/AppContext';
import { notificationService, Notification } from '../src/api/services';

interface LayoutProps {
  children: React.ReactNode;
  currentView: string;
}

const SIDEBAR_COLLAPSED_KEY = 'senhub_sidebar_collapsed';
const DEVELOPER_MODE_KEY = 'senhub_developer_mode';

const SidebarItem = ({ 
  icon: Icon, 
  label, 
  isActive, 
  onClick,
  collapsed 
}: { 
  icon: any; 
  label: string; 
  isActive: boolean; 
  onClick: () => void;
  collapsed?: boolean;
}) => (
  <button
    type="button"
    onClick={onClick}
    title={collapsed ? label : undefined}
    className={`w-full flex items-center rounded-lg transition-all duration-200 group ${
      collapsed ? 'justify-center px-2 py-2.5' : 'space-x-3 px-3 py-2.5'
    } ${
      isActive 
        ? 'bg-blue-600 text-white shadow-lg shadow-blue-900/20' 
        : 'text-gray-400 hover:bg-gray-800 hover:text-white'
    }`}
  >
    <Icon size={20} className={`flex-shrink-0 ${isActive ? 'text-white' : 'text-gray-400 group-hover:text-white'}`} />
    {!collapsed && (
      <span className="font-medium text-sm tracking-wide whitespace-nowrap truncate min-w-0">{label}</span>
    )}
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
      case 'AI_ANALYSIS':
        navigate('/ai-analysis');
        break;
      case 'AI_ALGORITHM_LIB':
        navigate('/ai-algorithm-library');
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

  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    try {
      return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true';
    } catch {
      return false;
    }
  });
  const [developerMode, setDeveloperMode] = useState(() => {
    try {
      return localStorage.getItem(DEVELOPER_MODE_KEY) === 'true';
    } catch {
      return false;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(sidebarCollapsed));
    } catch {}
  }, [sidebarCollapsed]);

  useEffect(() => {
    try {
      localStorage.setItem(DEVELOPER_MODE_KEY, String(developerMode));
    } catch {}
  }, [developerMode]);

  // Command+K (Mac) / Alt+K (Windows) 切换开发者模式
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey && e.key === 'k') || (e.altKey && e.key === 'k')) {
        e.preventDefault();
        setDeveloperMode(prev => !prev);
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, []);

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
      <aside className={`${sidebarCollapsed ? 'w-[72px]' : 'w-64'} bg-[#1e293b] flex-shrink-0 flex flex-col transition-all duration-300 z-20 shadow-xl overflow-hidden`}>
        <div className="p-4 flex flex-col flex-1 min-h-0">
          <div className={`flex items-center ${sidebarCollapsed ? 'justify-center mb-4' : 'space-x-3 mb-6'}`}>
            <img src="/logo.svg" alt="SenHub" className={`flex-shrink-0 object-contain ${sidebarCollapsed ? 'w-8 h-8' : 'w-8 h-8'}`} />
            {!sidebarCollapsed && (
              <h1 className="text-xl font-bold text-white tracking-tight">SenHub</h1>
            )}
          </div>

          <nav className="space-y-1 flex-1 overflow-y-auto">
            <SidebarItem 
              icon={LayoutDashboard} 
              label={t('dashboard')} 
              isActive={currentView === 'DASHBOARD'} 
              onClick={() => handleNavigate('DASHBOARD')}
              collapsed={sidebarCollapsed}
            />
            {!sidebarCollapsed && (
              <div className="pt-2 pb-1">
                <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('device_mgmt')}</p>
              </div>
            )}
            <SidebarItem 
              icon={Camera} 
              label={t('cameras')} 
              isActive={currentView === 'DEVICE_LIST' || currentView === 'DEVICE_DETAIL'} 
              onClick={() => handleNavigate('DEVICE_LIST')}
              collapsed={sidebarCollapsed}
            />
            <SidebarItem 
              icon={Package} 
              label={t('assembly_management')} 
              isActive={currentView === 'ASSEMBLY_MANAGEMENT' || currentView === 'ASSEMBLY_DETAIL'} 
              onClick={() => handleNavigate('ASSEMBLY_MANAGEMENT')}
              collapsed={sidebarCollapsed}
            />
            <SidebarItem 
              icon={Radar} 
              label={t('radar_mgmt')} 
              isActive={currentView === 'RADAR'} 
              onClick={() => handleNavigate('RADAR')}
              collapsed={sidebarCollapsed}
            />
            {developerMode && (
              <>
                {!sidebarCollapsed && (
                  <div className="pt-2 pb-1">
                    <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('integration')}</p>
                  </div>
                )}
                <SidebarItem 
                  icon={Cpu} 
                  label={t('drivers')} 
                  isActive={currentView === 'DRIVER_CONFIG'} 
                  onClick={() => handleNavigate('DRIVER_CONFIG')}
                  collapsed={sidebarCollapsed}
                />
                <SidebarItem 
                  icon={Network} 
                  label={t('mqtt_broker')} 
                  isActive={currentView === 'MQTT_CONFIG'} 
                  onClick={() => handleNavigate('MQTT_CONFIG')}
                  collapsed={sidebarCollapsed}
                />
              </>
            )}
            {!sidebarCollapsed && (
              <div className="pt-2 pb-1">
                <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('settings')}</p>
              </div>
            )}
            <SidebarItem 
              icon={Shield} 
              label={t('alarm_rules')} 
              isActive={currentView === 'ALARM_RULES'} 
              onClick={() => handleNavigate('ALARM_RULES')}
              collapsed={sidebarCollapsed}
            />
            <SidebarItem 
              icon={Package} 
              label={t('workflow_mgmt')} 
              isActive={currentView === 'WORKFLOW'} 
              onClick={() => handleNavigate('WORKFLOW')}
              collapsed={sidebarCollapsed}
            />
            <SidebarItem 
              icon={Brain} 
              label={t('ai_analysis_records')} 
              isActive={currentView === 'AI_ANALYSIS'} 
              onClick={() => handleNavigate('AI_ANALYSIS')}
              collapsed={sidebarCollapsed}
            />
            <SidebarItem 
              icon={Server} 
              label={t('ai_algorithm_lib')} 
              isActive={currentView === 'AI_ALGORITHM_LIB'} 
              onClick={() => handleNavigate('AI_ALGORITHM_LIB')}
              collapsed={sidebarCollapsed}
            />
            <SidebarItem 
              icon={Settings} 
              label={t('system_config')} 
              isActive={currentView === 'SYSTEM_CONFIG'} 
              onClick={() => handleNavigate('SYSTEM_CONFIG')}
              collapsed={sidebarCollapsed}
            />
          </nav>

          <button
            type="button"
            onClick={() => setSidebarCollapsed(prev => !prev)}
            className="mt-2 flex items-center justify-center w-full py-2 text-gray-400 hover:text-white hover:bg-gray-800 rounded-lg transition-colors"
            title={sidebarCollapsed ? t('expand') || '展开' : t('collapse') || '收起'}
          >
            {sidebarCollapsed ? <ChevronRight size={20} /> : <ChevronLeft size={20} />}
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
                currentView === 'AI_ANALYSIS' ? t('ai_analysis_records') :
                currentView === 'AI_ALGORITHM_LIB' ? t('ai_algorithm_lib') :
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

            {developerMode && (
              <span className="text-xs font-medium px-2 py-1 rounded bg-amber-100 text-amber-800 border border-amber-200" title="⌘K / Alt+K 切换">
                开发者模式
              </span>
            )}
            <div className="h-6 w-px bg-gray-200 mx-2"></div>
            
            <button 
                onClick={toggleLanguage}
                className="p-2 text-gray-500 hover:text-blue-600 transition-colors rounded-lg hover:bg-blue-50 flex items-center space-x-1"
                title="Switch Language"
            >
                <Languages size={20} />
                <span className="text-xs font-bold uppercase">{language}</span>
            </button>

            <button
              onClick={handleLogout}
              className="p-2 text-gray-500 hover:text-red-600 transition-colors rounded-lg hover:bg-red-50 flex items-center space-x-1"
              title={t('sign_out')}
            >
              <LogOut size={20} />
              <span className="text-xs font-medium hidden sm:inline">{t('sign_out')}</span>
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
            
          </div>
        </header>

        {/* Scrollable Content Area - 流程/装置管理页充满内容区 */}
        <div className="flex-1 overflow-hidden flex flex-col">
          <div className={`flex-1 overflow-auto ${currentView === 'WORKFLOW' ? 'p-0' : 'p-6'}`}>
            <div className={`h-full min-h-full animate-fade-in ${(currentView === 'WORKFLOW' || currentView === 'ASSEMBLY_MANAGEMENT') ? 'w-full' : 'max-w-7xl mx-auto'}`}>
              {children}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
};