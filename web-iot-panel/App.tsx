import React, { useState } from 'react';
import { Layout } from './components/Layout';
import { Dashboard } from './components/Dashboard';
import { DeviceList } from './components/DeviceList';
import { DeviceDetail } from './components/DeviceDetail';
import { DriverConfig } from './components/DriverConfig';
import { MqttConfig } from './components/MqttConfig';
import { SystemSettings } from './components/SystemSettings';
import { LoginPage } from './components/LoginPage';
import { ViewState } from './types';
import { AppProvider, useAppContext } from './contexts/AppContext';

const AppContent: React.FC = () => {
  const { isAuthenticated } = useAppContext();
  const [currentView, setCurrentView] = useState<ViewState>('DASHBOARD');
  const [selectedDeviceId, setSelectedDeviceId] = useState<string | null>(null);

  // Route Guard
  if (!isAuthenticated) {
    return <LoginPage />;
  }

  const handleNavigate = (view: ViewState) => {
    setCurrentView(view);
    if (view !== 'DEVICE_DETAIL') {
      setSelectedDeviceId(null);
    }
  };

  const handleDeviceSelect = (id: string) => {
    setSelectedDeviceId(id);
    setCurrentView('DEVICE_DETAIL');
  };

  const renderContent = () => {
    switch (currentView) {
      case 'DASHBOARD':
        return <Dashboard />;
      case 'DEVICE_LIST':
        return <DeviceList onSelectDevice={handleDeviceSelect} />;
      case 'DEVICE_DETAIL':
        if (!selectedDeviceId) return <DeviceList onSelectDevice={handleDeviceSelect} />;
        return <DeviceDetail deviceId={selectedDeviceId} onBack={() => handleNavigate('DEVICE_LIST')} />;
      case 'DRIVER_CONFIG':
        return <DriverConfig />;
      case 'MQTT_CONFIG':
        return <MqttConfig />;
      case 'SYSTEM_CONFIG':
        return <SystemSettings />;
      default:
        return <Dashboard />;
    }
  };

  return (
    <Layout currentView={currentView} onNavigate={handleNavigate}>
      {renderContent()}
    </Layout>
  );
};

const App: React.FC = () => {
  return (
    <AppProvider>
      <AppContent />
    </AppProvider>
  );
};

export default App;