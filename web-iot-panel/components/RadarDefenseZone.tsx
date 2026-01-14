import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { radarService } from '../src/api/services';
import { useModal } from '../hooks/useModal';

/**
 * 防区配置页面
 */
export const RadarDefenseZone: React.FC = () => {
  const { deviceId } = useParams<{ deviceId: string }>();
  const modal = useModal();
  const [zones, setZones] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [showAddModal, setShowAddModal] = useState(false);
  const [zoneType, setZoneType] = useState<'shrink' | 'bounding_box'>('shrink');
  const [formData, setFormData] = useState<any>({
    backgroundId: '',
    name: '',
    description: '',
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

  useEffect(() => {
    loadZones();
  }, [deviceId]);

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

  const handleAdd = async () => {
    try {
      const zoneData = {
        ...formData,
        zoneType,
        deviceId
      };
      await radarService.createZone(deviceId!, zoneData);
      modal.showModal({ message: '创建成功', type: 'success' });
      setShowAddModal(false);
      loadZones();
    } catch (err: any) {
      modal.showModal({ message: err.message || '创建失败', type: 'error' });
    }
  };

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">防区配置</h1>
        <button
          onClick={() => setShowAddModal(true)}
          className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
        >
          新建防区
        </button>
      </div>

      {isLoading ? (
        <div>加载中...</div>
      ) : (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">名称</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">类型</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">状态</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">操作</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {zones.map((zone) => (
                <tr key={zone.zoneId}>
                  <td className="px-6 py-4 whitespace-nowrap">{zone.name || '-'}</td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    {zone.zoneType === 'shrink' ? '缩小距离' : '边界框'}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 py-1 rounded text-xs ${
                      zone.enabled ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                    }`}>
                      {zone.enabled ? '启用' : '禁用'}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <button className="text-blue-600 hover:text-blue-800 mr-4">编辑</button>
                    <button className="text-red-600 hover:text-red-800">删除</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showAddModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-96 max-h-[90vh] overflow-y-auto">
            <h2 className="text-xl font-bold mb-4">新建防区</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">防区名称</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="w-full px-3 py-2 border rounded"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">防区类型</label>
                <select
                  value={zoneType}
                  onChange={(e) => setZoneType(e.target.value as 'shrink' | 'bounding_box')}
                  className="w-full px-3 py-2 border rounded"
                >
                  <option value="shrink">缩小距离</option>
                  <option value="bounding_box">边界框（x/y/z轴）</option>
                </select>
              </div>
              {zoneType === 'shrink' ? (
                <div>
                  <label className="block text-sm font-medium mb-1">缩小距离（厘米）</label>
                  <input
                    type="number"
                    value={formData.shrinkDistanceCm}
                    onChange={(e) => setFormData({ ...formData, shrinkDistanceCm: parseInt(e.target.value) || 20 })}
                    className="w-full px-3 py-2 border rounded"
                    min="5"
                    step="2"
                  />
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-sm font-medium mb-1">X最小值</label>
                      <input
                        type="number"
                        step="0.1"
                        value={formData.minX || ''}
                        onChange={(e) => setFormData({ ...formData, minX: parseFloat(e.target.value) || null })}
                        className="w-full px-3 py-2 border rounded"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium mb-1">X最大值</label>
                      <input
                        type="number"
                        step="0.1"
                        value={formData.maxX || ''}
                        onChange={(e) => setFormData({ ...formData, maxX: parseFloat(e.target.value) || null })}
                        className="w-full px-3 py-2 border rounded"
                      />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-sm font-medium mb-1">Y最小值</label>
                      <input
                        type="number"
                        step="0.1"
                        value={formData.minY || ''}
                        onChange={(e) => setFormData({ ...formData, minY: parseFloat(e.target.value) || null })}
                        className="w-full px-3 py-2 border rounded"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium mb-1">Y最大值</label>
                      <input
                        type="number"
                        step="0.1"
                        value={formData.maxY || ''}
                        onChange={(e) => setFormData({ ...formData, maxY: parseFloat(e.target.value) || null })}
                        className="w-full px-3 py-2 border rounded"
                      />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-sm font-medium mb-1">Z最小值</label>
                      <input
                        type="number"
                        step="0.1"
                        value={formData.minZ || ''}
                        onChange={(e) => setFormData({ ...formData, minZ: parseFloat(e.target.value) || null })}
                        className="w-full px-3 py-2 border rounded"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium mb-1">Z最大值</label>
                      <input
                        type="number"
                        step="0.1"
                        value={formData.maxZ || ''}
                        onChange={(e) => setFormData({ ...formData, maxZ: parseFloat(e.target.value) || null })}
                        className="w-full px-3 py-2 border rounded"
                      />
                    </div>
                  </div>
                </div>
              )}
            </div>
            <div className="mt-6 flex justify-end space-x-3">
              <button
                onClick={() => setShowAddModal(false)}
                className="px-4 py-2 border rounded hover:bg-gray-50"
              >
                取消
              </button>
              <button
                onClick={handleAdd}
                className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
              >
                保存
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="bg-white rounded-lg shadow p-6 mt-6">
        <h2 className="text-lg font-semibold mb-4">3D防区可视化</h2>
        <div className="w-full h-96 bg-gray-100 rounded flex items-center justify-center">
          <p className="text-gray-500">Three.js防区渲染区域（待实现）</p>
        </div>
      </div>
    </div>
  );
};
