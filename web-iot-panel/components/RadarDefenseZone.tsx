import React, { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { Plus, Search, Edit2, Trash2, X, Shield, Camera, MapPin, Power, PowerOff } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { radarService, deviceService } from '../src/api/services';
import { useModal } from '../hooks/useModal';
import { Modal, ConfirmModal } from './Modal';
import { PointCloudRenderer } from './PointCloudRenderer';

interface Point {
  x: number;
  y: number;
  z: number;
  r?: number;
  isZoneBoundary?: boolean; // 标记是否为防区边界点
}

interface TimestampedPoint extends Point {
  timestamp: number;
}

/**
 * 防区配置页面
 */
export const RadarDefenseZone: React.FC = () => {
  const { deviceId } = useParams<{ deviceId: string }>();
  const { t } = useAppContext();
  const modal = useModal();
  const [zones, setZones] = useState<any[]>([]);
  const [backgrounds, setBackgrounds] = useState<any[]>([]);
  const [selectedBackgroundId, setSelectedBackgroundId] = useState<string>('');
  const [selectedBackgroundPoints, setSelectedBackgroundPoints] = useState<Point[]>([]);
  const [devices, setDevices] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeModal, setActiveModal] = useState<'NONE' | 'CREATE' | 'EDIT' | 'DELETE'>('NONE');
  const [selectedZone, setSelectedZone] = useState<any | null>(null);
  const [zoneType, setZoneType] = useState<'shrink' | 'bounding_box'>('shrink');
  const [formData, setFormData] = useState<any>({
    backgroundId: '',
    name: '',
    description: '',
    enabled: true,
    shrinkDistanceCm: 20,
    minX: null,
    maxX: null,
    minY: null,
    maxY: null,
    minZ: null,
    maxZ: null,
    cameraDeviceId: '',
    cameraChannel: 1
  });
  const [isSaving, setIsSaving] = useState(false);

  // WebSocket 实时点云
  const wsRef = useRef<WebSocket | null>(null);
  const pointCloudBufferRef = useRef<TimestampedPoint[]>([]);
  const renderIntervalRef = useRef<number | null>(null);
  const [realtimePoints, setRealtimePoints] = useState<TimestampedPoint[]>([]);
  const [isWsConnected, setIsWsConnected] = useState(false);

  // 滑动窗口配置
  const ACCUMULATION_TIME = 3000; // 3秒窗口
  const lastLogTimeRef = useRef<number>(0);

  useEffect(() => {
    if (deviceId) {
      loadZones();
      loadBackgrounds();
      loadDevices();
      // connectWebSocket(); // 移除实时点云连接，按需只显示背景和防区
    }

    return () => {
      cleanupWebSocket();
    };
  }, [deviceId]);

  // 当选择的背景发生变化时，加载背景点云数据以供防区渲染参考
  useEffect(() => {
    if (selectedBackgroundId && deviceId) {
      loadBackgroundPoints(selectedBackgroundId);
    } else {
      setSelectedBackgroundPoints([]);
    }
  }, [selectedBackgroundId, deviceId]);

  const loadBackgroundPoints = async (bgId: string) => {
    try {
      const response = await radarService.getBackgroundPoints(deviceId!, bgId, 50000);
      if (response.data && response.data.points) {
        setSelectedBackgroundPoints(response.data.points);
      }
    } catch (err: any) {
      console.error('加载背景点云数据失败', err);
    }
  };

  // WebSocket 逻辑保持定义但不再自动开启
  const connectWebSocket = () => {
    if (wsRef.current) {
      wsRef.current.close();
    }

    console.log('[防区可视化] 正在连接WebSocket...');

    const ws = radarService.connectWebSocket(deviceId!, (data) => {
      if (data.type === 'pointcloud' && data.points) {
        const now = Date.now();
        const pointCount = data.points.length;
        const timeSpread = 50;
        const cutoffTime = now - ACCUMULATION_TIME;

        const newPoints: TimestampedPoint[] = data.points.map((p: any, index: number) => ({
          x: p.x || 0,
          y: p.y || 0,
          z: p.z || 0,
          r: p.r,
          timestamp: now - (timeSpread * index / pointCount)
        }));

        // 滑动窗口
        pointCloudBufferRef.current = [
          ...pointCloudBufferRef.current.filter(p => p.timestamp > cutoffTime),
          ...newPoints
        ];
      }
    });

    ws.onopen = () => {
      console.log('[防区可视化] WebSocket已连接');
      setIsWsConnected(true);
    };

    ws.onclose = () => {
      console.log('[防区可视化] WebSocket已关闭');
      setIsWsConnected(false);
    };

    wsRef.current = ws;

    // 渲染定时器
    renderIntervalRef.current = window.setInterval(() => {
      const now = Date.now();
      const cutoffTime = now - ACCUMULATION_TIME;
      const windowPoints = pointCloudBufferRef.current.filter(p => p.timestamp > cutoffTime);
      pointCloudBufferRef.current = windowPoints;

      if (windowPoints.length > 0) {
        setRealtimePoints(windowPoints);
      }

      // 日志
      if (now - lastLogTimeRef.current >= 3000) {
        console.log(`[防区可视化] 点数: ${windowPoints.length.toLocaleString()}`);
        lastLogTimeRef.current = now;
      }
    }, 100);
  };

  const cleanupWebSocket = () => {
    if (renderIntervalRef.current) {
      clearInterval(renderIntervalRef.current);
      renderIntervalRef.current = null;
    }
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    pointCloudBufferRef.current = [];
  };

  // 生成防区边界点云（红色虚拟点）
  const generateZoneBoundaryPoints = (): Point[] => {
    const boundaryPoints: Point[] = [];
    const enabledZones = zones.filter(z => z.enabled);

    enabledZones.forEach(zone => {
      if (zone.zoneType === 'bounding_box' &&
        zone.minX != null && zone.maxX != null &&
        zone.minY != null && zone.maxY != null) {
        // 为边界框类型的防区生成边界点
        const minX = zone.minX, maxX = zone.maxX;
        const minY = zone.minY, maxY = zone.maxY;
        const minZ = zone.minZ ?? -0.5, maxZ = zone.maxZ ?? 2;

        // 在边界上生成点（网格密度）
        const step = 0.2; // 每20cm一个点

        // 底面边界
        for (let x = minX; x <= maxX; x += step) {
          boundaryPoints.push({ x, y: minY, z: minZ, isZoneBoundary: true });
          boundaryPoints.push({ x, y: maxY, z: minZ, isZoneBoundary: true });
        }
        for (let y = minY; y <= maxY; y += step) {
          boundaryPoints.push({ x: minX, y, z: minZ, isZoneBoundary: true });
          boundaryPoints.push({ x: maxX, y, z: minZ, isZoneBoundary: true });
        }

        // 顶面边界
        for (let x = minX; x <= maxX; x += step) {
          boundaryPoints.push({ x, y: minY, z: maxZ, isZoneBoundary: true });
          boundaryPoints.push({ x, y: maxY, z: maxZ, isZoneBoundary: true });
        }
        for (let y = minY; y <= maxY; y += step) {
          boundaryPoints.push({ x: minX, y, z: maxZ, isZoneBoundary: true });
          boundaryPoints.push({ x: maxX, y, z: maxZ, isZoneBoundary: true });
        }

        // 垂直边界
        for (let z = minZ; z <= maxZ; z += step) {
          boundaryPoints.push({ x: minX, y: minY, z, isZoneBoundary: true });
          boundaryPoints.push({ x: maxX, y: minY, z, isZoneBoundary: true });
          boundaryPoints.push({ x: minX, y: maxY, z, isZoneBoundary: true });
          boundaryPoints.push({ x: maxX, y: maxY, z, isZoneBoundary: true });
        }
      }
    });

    return boundaryPoints;
  };

  // 合并实时点云和防区边界点云
  const getAllDisplayPoints = (): Point[] => {
    const zoneBoundaryPoints = generateZoneBoundaryPoints();
    return [
      ...realtimePoints,
      ...zoneBoundaryPoints
    ];
  };

  const loadZones = async () => {
    setIsLoading(true);
    try {
      const response = await radarService.getZones(deviceId!);
      setZones(response.data || []);
    } catch (err: any) {
      modal.showModal({ message: err.message || '加载防区列表失败', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  const loadBackgrounds = async () => {
    try {
      const response = await radarService.getBackgrounds(deviceId!);
      const bgs = response.data || [];
      setBackgrounds(bgs);
      // 如果没有选中的背景，默认选第一个
      if (!selectedBackgroundId && bgs.length > 0) {
        setSelectedBackgroundId(bgs[0].backgroundId);
      }
    } catch (err: any) {
      console.error('加载背景列表失败', err);
    }
  };

  const loadDevices = async () => {
    try {
      const response = await deviceService.getDevices({});
      setDevices(response.data || []);
    } catch (err: any) {
      console.error('加载设备列表失败', err);
    }
  };

  const openCreateModal = () => {
    setFormData({
      backgroundId: selectedBackgroundId || '',
      name: '',
      description: '',
      enabled: true,
      shrinkDistanceCm: 20,
      minX: null,
      maxX: null,
      minY: null,
      maxY: null,
      minZ: null,
      maxZ: null,
      cameraDeviceId: '',
      cameraChannel: 1
    });
    setZoneType('shrink');
    setActiveModal('CREATE');
  };

  const openEditModal = (zone: any) => {
    setSelectedZone(zone);
    setFormData({
      backgroundId: zone.backgroundId || '',
      name: zone.name || '',
      description: zone.description || '',
      enabled: zone.enabled !== false,
      shrinkDistanceCm: zone.shrinkDistanceCm || 20,
      minX: zone.minX,
      maxX: zone.maxX,
      minY: zone.minY,
      maxY: zone.maxY,
      minZ: zone.minZ,
      maxZ: zone.maxZ,
      cameraDeviceId: zone.cameraDeviceId || '',
      cameraChannel: zone.cameraChannel || 1
    });
    setZoneType(zone.zoneType || 'shrink');
    setActiveModal('EDIT');
  };

  const openDeleteModal = (zone: any) => {
    setSelectedZone(zone);
    setActiveModal('DELETE');
  };

  const handleCloseModal = () => {
    setActiveModal('NONE');
    setSelectedZone(null);
    setIsSaving(false);
  };

  const handleSave = async () => {
    if (!formData.name?.trim()) {
      modal.showModal({ message: '请输入防区名称', type: 'warning' });
      return;
    }
    if (!formData.backgroundId) {
      modal.showModal({ message: '请选择背景模型', type: 'warning' });
      return;
    }

    setIsSaving(true);
    try {
      const zoneData = {
        ...formData,
        zoneType,
        deviceId
      };

      if (activeModal === 'CREATE') {
        await radarService.createZone(deviceId!, zoneData);
        modal.showModal({ message: '创建成功', type: 'success' });
      } else if (activeModal === 'EDIT' && selectedZone) {
        await radarService.updateZone(deviceId!, selectedZone.zoneId, zoneData);
        modal.showModal({ message: '更新成功', type: 'success' });
      }
      handleCloseModal();
      await loadZones();
    } catch (err: any) {
      modal.showModal({ message: err.message || '保存失败', type: 'error' });
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedZone) return;
    setIsSaving(true);
    try {
      await radarService.deleteZone(deviceId!, selectedZone.zoneId);
      handleCloseModal();
      await loadZones();
      modal.showModal({ message: '删除成功', type: 'success' });
    } catch (err: any) {
      modal.showModal({ message: err.message || '删除失败', type: 'error' });
      setIsSaving(false);
    }
  };

  const handleToggle = async (zone: any) => {
    try {
      await radarService.toggleZone(deviceId!, zone.zoneId);
      await loadZones();
      modal.showModal({
        message: zone.enabled ? '防区已禁用' : '防区已启用',
        type: 'success'
      });
    } catch (err: any) {
      modal.showModal({ message: err.message || '操作失败', type: 'error' });
    }
  };

  const filteredZones = zones.filter(zone =>
    !searchTerm ||
    zone.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
    zone.description?.toLowerCase().includes(searchTerm.toLowerCase())
  );

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

      <div className="space-y-6">
        {/* 操作栏 */}
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-white p-4 rounded-2xl shadow-sm border border-gray-100">
          <div className="flex items-center space-x-3 w-full md:w-auto">
            <div className="relative w-full md:w-64">
              <Search className="absolute left-3 top-2.5 text-gray-400" size={18} />
              <input
                type="text"
                placeholder="搜索防区名称或描述..."
                className="w-full pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
          </div>

          <div className="flex items-center space-x-3 w-full md:w-auto">
            <button
              onClick={loadZones}
              disabled={isLoading}
              className="flex items-center space-x-2 px-4 py-2 bg-white border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 transition-colors font-medium text-sm shadow-sm disabled:opacity-50"
            >
              <Search size={16} className={isLoading ? 'animate-spin' : ''} />
              <span>{t('refresh')}</span>
            </button>
            <button
              onClick={openCreateModal}
              className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium text-sm shadow-lg shadow-blue-200"
            >
              <Plus size={16} />
              <span>新建防区</span>
            </button>
          </div>
        </div>

        {/* 防区列表 */}
        {isLoading ? (
          <div className="p-12 text-center">
            <div className="inline-block w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
            <p className="mt-4 text-gray-500">加载中...</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredZones.map((zone) => (
              <div
                key={zone.zoneId}
                className="bg-white rounded-xl p-6 shadow-sm border border-gray-100 hover:shadow-md transition-shadow"
              >
                <div className="flex justify-between items-start mb-4">
                  <div className="flex-1">
                    <div className="flex items-center space-x-2 mb-1">
                      <Shield size={20} className="text-blue-600" />
                      <h3 className="font-bold text-lg text-gray-800">{zone.name || zone.zoneId}</h3>
                    </div>
                    {zone.description && (
                      <p className="text-sm text-gray-600 mb-2 line-clamp-2">{zone.description}</p>
                    )}
                  </div>
                  <span
                    className={`px-2 py-1 rounded-full text-xs font-medium ${zone.enabled
                      ? 'bg-green-100 text-green-700'
                      : 'bg-gray-100 text-gray-700'
                      }`}
                  >
                    {zone.enabled ? '启用' : '禁用'}
                  </span>
                </div>

                {/* 防区信息 */}
                <div className="space-y-2 mb-4">
                  <div className="text-sm text-gray-600">
                    <span className="font-medium">类型：</span>
                    <span>{zone.zoneType === 'shrink' ? '缩小距离' : '边界框'}</span>
                  </div>
                  {zone.zoneType === 'shrink' && zone.shrinkDistanceCm && (
                    <div className="text-sm text-gray-600">
                      <span className="font-medium">缩小距离：</span>
                      <span>{zone.shrinkDistanceCm} cm</span>
                    </div>
                  )}
                  {zone.zoneType === 'bounding_box' && (
                    <div className="text-xs text-gray-500">
                      X: [{zone.minX?.toFixed(2) || '-'}, {zone.maxX?.toFixed(2) || '-'}]<br />
                      Y: [{zone.minY?.toFixed(2) || '-'}, {zone.maxY?.toFixed(2) || '-'}]<br />
                      Z: [{zone.minZ?.toFixed(2) || '-'}, {zone.maxZ?.toFixed(2) || '-'}]
                    </div>
                  )}
                  {zone.cameraDeviceId && (
                    <div className="flex items-center text-sm text-gray-600">
                      <Camera size={14} className="mr-1" />
                      <span>摄像头: {zone.cameraDeviceId}</span>
                    </div>
                  )}
                </div>

                {/* 操作按钮 */}
                <div className="flex space-x-2 pt-4 border-t border-gray-100">
                  <button
                    onClick={() => handleToggle(zone)}
                    className={`flex-1 px-3 py-2 rounded-lg transition-colors text-sm font-medium ${zone.enabled
                      ? 'bg-gray-50 text-gray-600 hover:bg-gray-100'
                      : 'bg-green-50 text-green-600 hover:bg-green-100'
                      }`}
                    title={zone.enabled ? '禁用' : '启用'}
                  >
                    {zone.enabled ? (
                      <>
                        <PowerOff size={14} className="inline mr-1" />
                        禁用
                      </>
                    ) : (
                      <>
                        <Power size={14} className="inline mr-1" />
                        启用
                      </>
                    )}
                  </button>
                  <button
                    onClick={() => openEditModal(zone)}
                    className="px-3 py-2 text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                    title="编辑"
                  >
                    <Edit2 size={16} />
                  </button>
                  <button
                    onClick={() => openDeleteModal(zone)}
                    className="px-3 py-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                    title="删除"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {!isLoading && filteredZones.length === 0 && (
          <div className="p-12 text-center text-gray-500 bg-white rounded-xl">
            <Shield className="mx-auto mb-4 text-gray-300" size={48} />
            <p>暂无防区配置</p>
            <button
              onClick={openCreateModal}
              className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors"
            >
              新建防区
            </button>
          </div>
        )}

        {/* 创建/编辑弹窗 */}
        {(activeModal === 'CREATE' || activeModal === 'EDIT') && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div
              className="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
              onClick={handleCloseModal}
            ></div>
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl z-10 overflow-hidden max-h-[90vh] overflow-y-auto">
              <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
                <h3 className="text-lg font-bold text-gray-800">
                  {activeModal === 'CREATE' ? '新建防区' : '编辑防区'}
                </h3>
                <button
                  onClick={handleCloseModal}
                  className="p-1 rounded-full hover:bg-gray-200 transition-colors text-gray-500"
                >
                  <X size={20} />
                </button>
              </div>
              <div className="p-6 space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    防区名称 <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    placeholder="请输入防区名称"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">描述</label>
                  <textarea
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    rows={2}
                    value={formData.description}
                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                    placeholder="请输入描述信息（可选）"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    背景模型 <span className="text-red-500">*</span>
                  </label>
                  <select
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.backgroundId}
                    onChange={(e) => {
                      setFormData({ ...formData, backgroundId: e.target.value });
                      setSelectedBackgroundId(e.target.value);
                    }}
                  >
                    <option value="">请选择背景模型</option>
                    {backgrounds
                      .filter((bg: any) => bg.status === 'ready')
                      .map((bg: any) => (
                        <option key={bg.backgroundId} value={bg.backgroundId}>
                          {bg.backgroundId} ({bg.pointCount || 0} 点)
                        </option>
                      ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">防区类型</label>
                  <select
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={zoneType}
                    onChange={(e) => setZoneType(e.target.value as 'shrink' | 'bounding_box')}
                  >
                    <option value="shrink">缩小距离</option>
                    <option value="bounding_box">边界框（x/y/z轴）</option>
                  </select>
                </div>
                {zoneType === 'shrink' ? (
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      缩小距离（厘米） <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="number"
                      className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                      value={formData.shrinkDistanceCm}
                      onChange={(e) => setFormData({ ...formData, shrinkDistanceCm: parseInt(e.target.value) || 20 })}
                      min="5"
                      step="2"
                    />
                    <p className="mt-1 text-xs text-gray-500">推荐值：20cm</p>
                  </div>
                ) : (
                  <div className="space-y-2">
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">X最小值（米）</label>
                        <input
                          type="number"
                          step="0.1"
                          className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                          value={formData.minX || ''}
                          onChange={(e) => setFormData({ ...formData, minX: parseFloat(e.target.value) || null })}
                        />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">X最大值（米）</label>
                        <input
                          type="number"
                          step="0.1"
                          className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                          value={formData.maxX || ''}
                          onChange={(e) => setFormData({ ...formData, maxX: parseFloat(e.target.value) || null })}
                        />
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">Y最小值（米）</label>
                        <input
                          type="number"
                          step="0.1"
                          className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                          value={formData.minY || ''}
                          onChange={(e) => setFormData({ ...formData, minY: parseFloat(e.target.value) || null })}
                        />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">Y最大值（米）</label>
                        <input
                          type="number"
                          step="0.1"
                          className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                          value={formData.maxY || ''}
                          onChange={(e) => setFormData({ ...formData, maxY: parseFloat(e.target.value) || null })}
                        />
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">Z最小值（米）</label>
                        <input
                          type="number"
                          step="0.1"
                          className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                          value={formData.minZ || ''}
                          onChange={(e) => setFormData({ ...formData, minZ: parseFloat(e.target.value) || null })}
                        />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">Z最大值（米）</label>
                        <input
                          type="number"
                          step="0.1"
                          className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                          value={formData.maxZ || ''}
                          onChange={(e) => setFormData({ ...formData, maxZ: parseFloat(e.target.value) || null })}
                        />
                      </div>
                    </div>
                  </div>
                )}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">关联摄像头（可选）</label>
                  <select
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500"
                    value={formData.cameraDeviceId}
                    onChange={(e) => setFormData({ ...formData, cameraDeviceId: e.target.value || '' })}
                  >
                    <option value="">不关联摄像头</option>
                    {devices.map((device) => (
                      <option key={device.deviceId} value={device.deviceId}>
                        {device.deviceName || device.deviceId}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="flex items-center space-x-2">
                    <input
                      type="checkbox"
                      className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                      checked={formData.enabled}
                      onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
                    />
                    <span className="text-sm font-medium text-gray-700">启用防区</span>
                  </label>
                </div>
              </div>
              <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end space-x-3">
                <button
                  onClick={handleCloseModal}
                  className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors font-medium"
                >
                  取消
                </button>
                <button
                  onClick={handleSave}
                  disabled={isSaving}
                  className="px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200 flex items-center"
                >
                  {isSaving && (
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2"></div>
                  )}
                  保存
                </button>
              </div>
            </div>
          </div>
        )}

        {/* 删除确认弹窗 */}
        {activeModal === 'DELETE' && selectedZone && (
          <ConfirmModal
            isOpen={true}
            onClose={handleCloseModal}
            title="删除防区"
            message={`确定要删除防区 "${selectedZone.name || selectedZone.zoneId}" 吗？此操作无法撤销。`}
            onConfirm={handleDelete}
            onCancel={handleCloseModal}
            confirmText="删除"
            cancelText="取消"
          />
        )}

        {/* 3D防区可视化 */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-semibold">3D防区可视化</h2>
            <div className="flex items-center gap-4 text-sm">
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 rounded-full bg-blue-500" />
                <span className="text-gray-600">背景点云</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 rounded-full bg-red-500" />
                <span className="text-gray-600">防区边界</span>
              </div>
            </div>
          </div>
          <div className="w-full h-[500px] rounded-2xl overflow-hidden bg-gray-900 shadow-inner">
            {selectedBackgroundPoints.length > 0 ? (
              <PointCloudRenderer
                points={[...selectedBackgroundPoints, ...generateZoneBoundaryPoints()]}
                color={(p: any) => p.isZoneBoundary ? '#ff0000' : ''}
                colorMode="reflectivity"
                pointSize={0.015}
                backgroundColor="#0a0a0a"
                showGrid={true}
                showAxes={true}
                showRangeRings={true}
                showModeling={true}
                modelingDistance={0.5}
              />
            ) : (
              <div className="w-full h-full flex flex-col items-center justify-center text-gray-400">
                <div className="w-16 h-16 bg-gray-800 rounded-full flex items-center justify-center mb-4">
                  <Shield size={32} className="opacity-20" />
                </div>
                <p className="text-sm font-medium">请选择背景模型以载入防区视图</p>
                <p className="text-xs mt-2 opacity-50">载入后将根据“启用”状态实时呈现防区轮廓</p>
              </div>
            )}
          </div>
          {selectedBackgroundPoints.length > 0 && (
            <div className="mt-4 flex justify-between items-center text-[11px] text-gray-400">
              <div>显示背景点: {selectedBackgroundPoints.length.toLocaleString()} 个</div>
              <div className="text-blue-500 font-medium">
                当前启用防区: {zones.filter(z => z.enabled).length} 个
              </div>
            </div>
          )}
        </div>
      </div>
    </>
  );
};
