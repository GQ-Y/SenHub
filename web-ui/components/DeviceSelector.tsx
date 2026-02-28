import React, { useState } from 'react';
import { Search, Check, X } from 'lucide-react';
import { Device, DeviceStatus } from '../types';
import { useAppContext } from '../contexts/AppContext';

interface DeviceSelectorProps {
  devices: Device[];
  selected: string[];
  onChange: (deviceIds: string[]) => void;
  multiSelect?: boolean;
  excludeDeviceIds?: string[]; // 排除的设备ID列表
}

export const DeviceSelector: React.FC<DeviceSelectorProps> = ({
  devices,
  selected,
  onChange,
  multiSelect = true,
  excludeDeviceIds = [],
}) => {
  const { t } = useAppContext();
  const [searchTerm, setSearchTerm] = useState('');

  const filteredDevices = devices.filter(
    (device) =>
      !excludeDeviceIds.includes(device.id) &&
      (device.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        device.ip.toLowerCase().includes(searchTerm.toLowerCase()))
  );

  const handleToggle = (deviceId: string) => {
    if (multiSelect) {
      if (selected.includes(deviceId)) {
        onChange(selected.filter((id) => id !== deviceId));
      } else {
        onChange([...selected, deviceId]);
      }
    } else {
      onChange([deviceId]);
    }
  };

  const handleRemove = (deviceId: string) => {
    onChange(selected.filter((id) => id !== deviceId));
  };

  const getStatusColor = (status: DeviceStatus) => {
    switch (status) {
      case DeviceStatus.ONLINE:
        return 'bg-green-100 text-green-700 border-green-200';
      case DeviceStatus.OFFLINE:
        return 'bg-red-100 text-red-700 border-red-200';
      case DeviceStatus.WARNING:
        return 'bg-orange-100 text-orange-700 border-orange-200';
      default:
        return 'bg-gray-100 text-gray-700';
    }
  };

  return (
    <div className="space-y-4">
      {/* 搜索框 */}
      <div className="relative">
        <Search className="absolute left-3 top-2.5 text-gray-400" size={18} />
        <input
          type="text"
          placeholder="搜索设备名称或IP..."
          className="w-full pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
      </div>

      {/* 已选设备 */}
      {selected.length > 0 && (
        <div className="bg-blue-50 border border-blue-200 rounded-xl p-3">
          <div className="text-sm font-medium text-blue-800 mb-2">已选择 ({selected.length})</div>
          <div className="flex flex-wrap gap-2">
            {selected.map((deviceId) => {
              const device = devices.find((d) => d.id === deviceId);
              if (!device) return null;
              return (
                <span
                  key={deviceId}
                  className="inline-flex items-center space-x-2 px-3 py-1 bg-blue-100 text-blue-700 rounded-lg text-sm"
                >
                  <span>{device.name}</span>
                  <button
                    onClick={() => handleRemove(deviceId)}
                    className="hover:bg-blue-200 rounded-full p-0.5 transition-colors"
                  >
                    <X size={14} />
                  </button>
                </span>
              );
            })}
          </div>
        </div>
      )}

      {/* 设备列表 */}
      <div className="border border-gray-200 rounded-xl overflow-hidden max-h-96 overflow-y-auto">
        {filteredDevices.length === 0 ? (
          <div className="p-8 text-center text-gray-500">
            <p>没有可用设备</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-100">
            {filteredDevices.map((device) => {
              const isSelected = selected.includes(device.id);
              return (
                <div
                  key={device.id}
                  className={`p-4 hover:bg-gray-50 transition-colors cursor-pointer ${
                    isSelected ? 'bg-blue-50' : ''
                  }`}
                  onClick={() => handleToggle(device.id)}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex-1">
                      <div className="flex items-center space-x-2 mb-1">
                        <span className="font-medium text-gray-900">{device.name}</span>
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border ${getStatusColor(
                            device.status
                          )}`}
                        >
                          {device.status === DeviceStatus.ONLINE
                            ? t('online')
                            : device.status === DeviceStatus.WARNING
                            ? t('warning')
                            : t('offline')}
                        </span>
                      </div>
                      <div className="text-sm text-gray-500 font-mono">
                        {device.ip}:{device.port}
                      </div>
                      <div className="text-xs text-gray-400 mt-1">{device.brand} {device.model}</div>
                    </div>
                    <div className="ml-4">
                      {isSelected ? (
                        <div className="w-6 h-6 bg-blue-600 rounded-full flex items-center justify-center">
                          <Check size={16} className="text-white" />
                        </div>
                      ) : (
                        <div className="w-6 h-6 border-2 border-gray-300 rounded-full"></div>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
