import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Dashboard } from './components/Dashboard';
import { DeviceList } from './components/DeviceList';
import { DeviceDetail } from './components/DeviceDetail';
import { DriverConfig } from './components/DriverConfig';
import { MqttConfig } from './components/MqttConfig';
import { SystemSettings } from './components/SystemSettings';
import { LoginPage } from './components/LoginPage';
import { AssemblyManagement } from './components/AssemblyManagement';
import { AssemblyDetail } from './components/AssemblyDetail';
import { AlarmRules } from './components/AlarmRules';
import { AppProvider, useAppContext } from './contexts/AppContext';

// 路由守卫组件
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useAppContext();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
};

// 根据路径获取当前视图
const getViewFromPath = (pathname: string): string => {
  if (pathname === '/') return 'DASHBOARD';
  if (pathname.startsWith('/devices/')) return 'DEVICE_DETAIL';
  if (pathname === '/devices') return 'DEVICE_LIST';
  if (pathname.startsWith('/assemblies/')) return 'ASSEMBLY_DETAIL';
  if (pathname === '/assemblies') return 'ASSEMBLY_MANAGEMENT';
  if (pathname === '/alarm-rules') return 'ALARM_RULES';
  if (pathname === '/drivers') return 'DRIVER_CONFIG';
  if (pathname === '/mqtt') return 'MQTT_CONFIG';
  if (pathname === '/settings') return 'SYSTEM_CONFIG';
  return 'DASHBOARD';
};

const AppContent: React.FC = () => {
  const location = useLocation();
  const { isAuthenticated } = useAppContext();
  const currentView = getViewFromPath(location.pathname);
  const isLoginPage = location.pathname === '/login';

  // 登录页面独立显示，不包含 Layout
  if (isLoginPage) {
    return isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />;
  }

  // 其他页面使用 Layout 包裹
  return (
    <Layout currentView={currentView as any}>
      <Routes>
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="/devices"
          element={
            <ProtectedRoute>
              <DeviceList />
            </ProtectedRoute>
          }
        />
        <Route
          path="/devices/:deviceId"
          element={
            <ProtectedRoute>
              <DeviceDetail />
            </ProtectedRoute>
          }
        />
        <Route
          path="/assemblies"
          element={
            <ProtectedRoute>
              <AssemblyManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/assemblies/:assemblyId"
          element={
            <ProtectedRoute>
              <AssemblyDetail />
            </ProtectedRoute>
          }
        />
        <Route
          path="/alarm-rules"
          element={
            <ProtectedRoute>
              <AlarmRules />
            </ProtectedRoute>
          }
        />
        <Route
          path="/drivers"
          element={
            <ProtectedRoute>
              <DriverConfig />
            </ProtectedRoute>
          }
        />
        <Route
          path="/mqtt"
          element={
            <ProtectedRoute>
              <MqttConfig />
            </ProtectedRoute>
          }
        />
        <Route
          path="/settings"
          element={
            <ProtectedRoute>
              <SystemSettings />
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
};

const App: React.FC = () => {
  return (
    <AppProvider>
      <BrowserRouter>
        <AppContent />
      </BrowserRouter>
    </AppProvider>
  );
};

export default App;