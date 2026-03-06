import React, { useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Dashboard } from './components/Dashboard';
import { DeviceList } from './components/DeviceList';
import { DeviceDetail } from './components/DeviceDetail';
import { DriverConfig } from './components/DriverConfig';
import { MqttConfig } from './components/MqttConfig';
import { SystemSettings } from './components/SystemSettings';
import { LoginPage } from './components/LoginPage';
import { SetupWizard } from './components/SetupWizard';
import { AssemblyManagement } from './components/AssemblyManagement';
import { AssemblyDetail } from './components/AssemblyDetail';
import { AlarmRules } from './components/AlarmRules';
import { RadarManagement } from './components/RadarManagement';
import { RadarBackgroundCollection } from './components/RadarBackgroundCollection';
import { RadarDefenseZone } from './components/RadarDefenseZone';
import { RadarMonitoring } from './components/RadarMonitoring';
import { FlowManagement } from './components/FlowManagement';
import { AiAnalysisRecords } from './components/AiAnalysisRecords';
import { AiAlgorithmLibrary } from './components/AiAlgorithmLibrary';
import BigScreen from './components/BigScreen';
import UpdatePage from './components/UpdatePage';
import { AppProvider, useAppContext } from './contexts/AppContext';
import { setupService } from './src/api/services';

// 路由守卫组件
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useAppContext();
  const location = useLocation();

  // 如果未认证，跳转到登录页，并保存当前路径以便登录后重定向
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
  if (pathname.startsWith('/radar')) return 'RADAR';
  if (pathname.startsWith('/flows')) return 'WORKFLOW';
  if (pathname.startsWith('/ai-analysis')) return 'AI_ANALYSIS';
  if (pathname.startsWith('/ai-algorithm-library')) return 'AI_ALGORITHM_LIB';
  return 'DASHBOARD';
};

const AppContent: React.FC = () => {
  const location = useLocation();
  const { isAuthenticated } = useAppContext();
  const currentView = getViewFromPath(location.pathname);
  const isLoginPage = location.pathname === '/login';
  const isBigScreen = location.pathname === '/big-screen';
  const isUpdatePage = location.pathname === '/update';

  // 登录页面独立显示，不包含 Layout
  if (isLoginPage) {
    return isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />;
  }

  // 大屏页面独立显示，不包含 Layout
  if (isBigScreen) {
    return <BigScreen />;
  }

  // 更新页面独立全屏显示
  if (isUpdatePage) {
    return isAuthenticated ? <UpdatePage /> : <Navigate to="/login" replace />;
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
        <Route
          path="/radar"
          element={
            <ProtectedRoute>
              <RadarManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/radar/:deviceId/background"
          element={
            <ProtectedRoute>
              <RadarBackgroundCollection />
            </ProtectedRoute>
          }
        />
        <Route
          path="/radar/:deviceId/zones"
          element={
            <ProtectedRoute>
              <RadarDefenseZone />
            </ProtectedRoute>
          }
        />
        <Route
          path="/radar/:deviceId/monitoring"
          element={
            <ProtectedRoute>
              <RadarMonitoring />
            </ProtectedRoute>
          }
        />
        <Route
          path="/flows"
          element={
            <ProtectedRoute>
              <FlowManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/ai-analysis"
          element={
            <ProtectedRoute>
              <AiAnalysisRecords />
            </ProtectedRoute>
          }
        />
        <Route
          path="/ai-algorithm-library"
          element={
            <ProtectedRoute>
              <AiAlgorithmLibrary />
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
};

const App: React.FC = () => {
  const [installChecked, setInstallChecked] = useState(false);
  const [installed, setInstalled] = useState(true);

  useEffect(() => {
    setupService.getStatus()
      .then((res) => {
        setInstalled(res.installed);
      })
      .catch(() => {
        setInstalled(true);
      })
      .finally(() => {
        setInstallChecked(true);
      });
  }, []);

  if (!installChecked) {
    return (
      <div className="min-h-screen bg-slate-900 flex items-center justify-center">
        <div className="w-10 h-10 border-2 border-blue-600/30 border-t-blue-600 rounded-full animate-spin" />
      </div>
    );
  }

  // 未安装：只渲染安装向导，所有路径均指向 SetupWizard
  if (!installed) {
    return (
      <BrowserRouter>
        <Routes>
          <Route path="/setup" element={<SetupWizard onComplete={() => window.location.href = '/login'} />} />
          <Route path="*" element={<Navigate to="/setup" replace />} />
        </Routes>
      </BrowserRouter>
    );
  }

  return (
    <BrowserRouter>
      <AppProvider>
        <AppContent />
      </AppProvider>
    </BrowserRouter>
  );
};

export default App;