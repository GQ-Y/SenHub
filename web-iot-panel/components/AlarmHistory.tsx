import React, { useState, useEffect } from 'react';
import { Search, Filter, Calendar, Image, Video, ExternalLink } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { alarmRecordService } from '../src/api/services';
import { AlarmRecord, AlarmType } from '../types';

interface AlarmHistoryProps {
  deviceId?: string;
  assemblyId?: string;
  limit?: number;
}

export const AlarmHistory: React.FC<AlarmHistoryProps> = ({ deviceId, assemblyId, limit }) => {
  const { t } = useAppContext();
  const [records, setRecords] = useState<AlarmRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState<AlarmType | 'all'>('all');

  useEffect(() => {
    loadRecords();
  }, [deviceId, assemblyId, filterType]);

  const loadRecords = async () => {
    setIsLoading(true);
    try {
      const params: any = {};
      if (deviceId) params.deviceId = deviceId;
      if (assemblyId) params.assemblyId = assemblyId;
      if (filterType !== 'all') params.alarmType = filterType;
      if (limit) params.limit = limit;
      const response = await alarmRecordService.getAlarmRecords(params);
      setRecords(response.data || []);
    } catch (err: any) {
      console.error('加载报警记录失败:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const getAlarmTypeLabel = (type: AlarmType) => {
    switch (type) {
      case AlarmType.HELMET_DETECTION:
        return t('helmet_detection');
      case AlarmType.VEST_DETECTION:
        return t('vest_detection');
      case AlarmType.VEHICLE_ALARM:
        return t('vehicle_alarm');
      case AlarmType.INPUT_PORT:
        return t('input_port');
      case AlarmType.RADAR_POINTCLOUD:
        return t('radar_pointcloud');
      default:
        return type;
    }
  };

  const getAlarmLevelColor = (level: string) => {
    switch (level) {
      case 'critical':
        return 'bg-red-100 text-red-700 border-red-200';
      case 'warning':
        return 'bg-orange-100 text-orange-700 border-orange-200';
      case 'info':
        return 'bg-blue-100 text-blue-700 border-blue-200';
      default:
        return 'bg-gray-100 text-gray-700';
    }
  };

  const formatTime = (timeStr: string) => {
    try {
      const date = new Date(timeStr);
      return date.toLocaleString('zh-CN');
    } catch {
      return timeStr;
    }
  };

  const filteredRecords = records.filter(
    (record) =>
      !searchTerm ||
      record.deviceName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      record.assemblyName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      getAlarmTypeLabel(record.alarmType).toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="space-y-4">
      {/* 过滤栏 */}
      <div className="flex flex-col md:flex-row gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-2.5 text-gray-400" size={18} />
          <input
            type="text"
            placeholder="搜索设备、装置或报警类型..."
            className="w-full pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <div className="relative">
          <Filter className="absolute left-3 top-2.5 text-gray-400" size={18} />
          <select
            className="pl-10 pr-8 py-2 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 outline-none appearance-none cursor-pointer text-gray-700"
            value={filterType}
            onChange={(e) => setFilterType(e.target.value as AlarmType | 'all')}
          >
            <option value="all">全部类型</option>
            <option value={AlarmType.HELMET_DETECTION}>{t('helmet_detection')}</option>
            <option value={AlarmType.VEST_DETECTION}>{t('vest_detection')}</option>
            <option value={AlarmType.VEHICLE_ALARM}>{t('vehicle_alarm')}</option>
            <option value={AlarmType.INPUT_PORT}>{t('input_port')}</option>
            <option value={AlarmType.RADAR_POINTCLOUD}>{t('radar_pointcloud')}</option>
          </select>
        </div>
      </div>

      {/* 记录列表 */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        {isLoading ? (
          <div className="p-12 text-center">
            <div className="inline-block w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
            <p className="mt-4 text-gray-500">加载中...</p>
          </div>
        ) : filteredRecords.length === 0 ? (
          <div className="p-12 text-center text-gray-500">
            <p>暂无报警记录</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-100">
            {filteredRecords.map((record) => (
              <div key={record.id} className="p-4 hover:bg-gray-50 transition-colors">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center space-x-2 mb-2">
                      <span
                        className={`px-2 py-1 rounded-full text-xs font-medium border ${getAlarmLevelColor(
                          record.alarmLevel
                        )}`}
                      >
                        {record.alarmLevel === 'critical' ? '严重' : record.alarmLevel === 'warning' ? '警告' : '信息'}
                      </span>
                      <span className="px-2 py-1 bg-blue-50 text-blue-700 rounded-lg text-xs font-medium">
                        {getAlarmTypeLabel(record.alarmType)}
                      </span>
                    </div>
                    <div className="text-sm text-gray-600 space-y-1">
                      {record.deviceName && (
                        <div>
                          <span className="font-medium">设备：</span>
                          {record.deviceName}
                          {record.channel && <span className="text-gray-400"> (通道 {record.channel})</span>}
                        </div>
                      )}
                      {record.assemblyName && (
                        <div>
                          <span className="font-medium">装置：</span>
                          {record.assemblyName}
                        </div>
                      )}
                      {(record.position?.location || (record.position?.latitude != null && record.position?.longitude != null)) && (
                        <div>
                          <span className="font-medium">位置：</span>
                          {record.position?.location || `${record.position?.latitude?.toFixed(5)}, ${record.position?.longitude?.toFixed(5)}`}
                        </div>
                      )}
                      <div className="flex items-center text-gray-500">
                        <Calendar size={14} className="mr-1" />
                        <span>{formatTime(record.recordedAt)}</span>
                      </div>
                    </div>
                    {/* 文件链接 */}
                    {(record.captureUrl || record.videoUrl) && (
                      <div className="flex items-center space-x-4 mt-3">
                        {record.captureUrl && (
                          <a
                            href={record.captureUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex items-center space-x-1 text-blue-600 hover:text-blue-700 text-sm"
                          >
                            <Image size={16} />
                            <span>查看抓拍</span>
                            <ExternalLink size={12} />
                          </a>
                        )}
                        {record.videoUrl && (
                          <a
                            href={record.videoUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex items-center space-x-1 text-blue-600 hover:text-blue-700 text-sm"
                          >
                            <Video size={16} />
                            <span>查看录像</span>
                            <ExternalLink size={12} />
                          </a>
                        )}
                      </div>
                    )}
                  </div>
                  <div className="ml-4 text-right">
                    <div className="text-xs text-gray-400">
                      {record.mqttSent && (
                        <span className="block mb-1 px-2 py-1 bg-green-50 text-green-700 rounded">
                          MQTT已上报
                        </span>
                      )}
                      {record.speakerTriggered && (
                        <span className="block px-2 py-1 bg-blue-50 text-blue-700 rounded">音柱已触发</span>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
