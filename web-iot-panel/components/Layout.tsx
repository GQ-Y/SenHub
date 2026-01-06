import React from 'react';
import { 
  LayoutDashboard, 
  Camera, 
  Settings, 
  Cpu, 
  Network, 
  LogOut, 
  Bell, 
  Menu,
  Languages
} from 'lucide-react';
import { ViewState } from '../types';
import { useAppContext } from '../contexts/AppContext';

interface LayoutProps {
  children: React.ReactNode;
  currentView: ViewState;
  onNavigate: (view: ViewState) => void;
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
    <Icon size={20} className={isActive ? 'text-white' : 'text-gray-400 group-hover:text-white'} />
    <span className="font-medium text-sm tracking-wide">{label}</span>
  </button>
);

export const Layout: React.FC<LayoutProps> = ({ children, currentView, onNavigate }) => {
  const { t, logout, language, setLanguage } = useAppContext();

  const toggleLanguage = () => {
    setLanguage(language === 'en' ? 'zh' : 'en');
  };

  return (
    <div className="flex h-screen bg-[#f3f4f6] overflow-hidden">
      {/* Sidebar */}
      <aside className="w-64 bg-[#1e293b] flex-shrink-0 flex flex-col justify-between transition-all duration-300 z-20 shadow-xl">
        <div className="p-6">
          <div className="flex items-center space-x-3 mb-10">
            <div className="w-8 h-8 bg-blue-500 rounded-lg flex items-center justify-center shadow-lg shadow-blue-500/30">
              <Camera className="text-white" size={18} />
            </div>
            <h1 className="text-xl font-bold text-white tracking-tight">Harmony<span className="text-blue-400">Guard</span></h1>
          </div>

          <nav className="space-y-2">
            <SidebarItem 
              icon={LayoutDashboard} 
              label={t('dashboard')} 
              isActive={currentView === 'DASHBOARD'} 
              onClick={() => onNavigate('DASHBOARD')}
            />
            <div className="pt-4 pb-2">
              <p className="px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('device_mgmt')}</p>
            </div>
            <SidebarItem 
              icon={Camera} 
              label={t('cameras')} 
              isActive={currentView === 'DEVICE_LIST' || currentView === 'DEVICE_DETAIL'} 
              onClick={() => onNavigate('DEVICE_LIST')}
            />
             <div className="pt-4 pb-2">
              <p className="px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('integration')}</p>
            </div>
            <SidebarItem 
              icon={Cpu} 
              label={t('drivers')} 
              isActive={currentView === 'DRIVER_CONFIG'} 
              onClick={() => onNavigate('DRIVER_CONFIG')}
            />
            <SidebarItem 
              icon={Network} 
              label={t('mqtt_broker')} 
              isActive={currentView === 'MQTT_CONFIG'} 
              onClick={() => onNavigate('MQTT_CONFIG')}
            />
             <div className="pt-4 pb-2">
              <p className="px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">{t('settings')}</p>
            </div>
            <SidebarItem 
              icon={Settings} 
              label={t('system_config')} 
              isActive={currentView === 'SYSTEM_CONFIG'} 
              onClick={() => onNavigate('SYSTEM_CONFIG')}
            />
          </nav>
        </div>

        <div className="p-4 border-t border-gray-700">
          <button 
            onClick={logout}
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
                currentView === 'DRIVER_CONFIG' ? t('drivers') :
                currentView === 'MQTT_CONFIG' ? t('mqtt_broker') : t('system_config')}
            </h2>
          </div>

          <div className="flex items-center space-x-6">
            {/* Status Indicators */}
            <div className="hidden md:flex items-center space-x-4 text-xs font-medium">
              <div className="flex items-center space-x-1.5 px-3 py-1 bg-green-50 text-green-700 rounded-full border border-green-100">
                <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></div>
                <span>{t('mqtt_connected')}</span>
              </div>
              <div className="flex items-center space-x-1.5 px-3 py-1 bg-blue-50 text-blue-700 rounded-full border border-blue-100">
                <div className="w-2 h-2 rounded-full bg-blue-500"></div>
                <span>{t('nvr_active')}</span>
              </div>
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

            <button className="relative p-2 text-gray-500 hover:text-blue-600 transition-colors rounded-full hover:bg-blue-50">
              <Bell size={20} />
              <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500 rounded-full border border-white"></span>
            </button>
            
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
        <div className="flex-1 overflow-auto p-8">
          <div className="max-w-7xl mx-auto animate-fade-in">
            {children}
          </div>
        </div>
      </main>
    </div>
  );
};