import React, { useState, useEffect } from 'react';
import { radarService } from '../src/api/services';
import { PlayCircle, Clock, AlertTriangle, RefreshCw, Trash2 } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';

interface Props {
    deviceId: string;
    onPlay: (record: any) => void;
    className?: string; // Allow custom styling for layout
}

export const IntrusionHistoryPanel: React.FC<Props> = ({ deviceId, onPlay, className }) => {
    const [records, setRecords] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [clearing, setClearing] = useState(false);
    const { t } = useAppContext();

    const loadRecords = async () => {
        setLoading(true);
        try {
            const res = await radarService.getIntrusions(deviceId, { page: 1, pageSize: 50 });
            setRecords(res.data?.records || []);
        } catch (e) {
            console.error(e);
        } finally {
            setLoading(false);
        }
    };

    const handleClearAll = async () => {
        if (!confirm('确定要清空所有侵入记录吗？此操作不可撤销。')) return;
        setClearing(true);
        try {
            await radarService.clearIntrusions(deviceId);
            setRecords([]);
        } catch (e) {
            console.error(e);
        } finally {
            setClearing(false);
        }
    };

    useEffect(() => {
        loadRecords();
    }, [deviceId]);

    return (
        <div className={`bg-white rounded-xl shadow-sm border border-gray-100 p-4 flex flex-col ${className || ''}`}>
            <div className="flex justify-between items-center mb-4">
                <h3 className="font-bold text-gray-800 flex items-center">
                    <AlertTriangle className="mr-2 text-orange-500" size={18} />
                    侵入记录
                </h3>
                <div className="flex items-center gap-1">
                    <button
                        onClick={handleClearAll}
                        disabled={clearing || records.length === 0}
                        className="p-1.5 text-red-500 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-50"
                        title="清空记录"
                    >
                        <Trash2 size={16} className={clearing ? 'animate-pulse' : ''} />
                    </button>
                    <button
                        onClick={loadRecords}
                        disabled={loading}
                        className="p-1.5 text-gray-500 hover:bg-gray-100 rounded-lg transition-colors"
                        title="刷新"
                    >
                        <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
                    </button>
                </div>
            </div>

            <div className="flex-1 overflow-y-auto space-y-3 pr-1 custom-scrollbar">
                {records.length === 0 ? (
                    <div className="text-center py-8 text-gray-400 text-sm">
                        暂无侵入记录
                    </div>
                ) : (
                    records.map(rec => (
                        <div key={rec.recordId} className="p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors border border-transparent hover:border-gray-200">
                            <div className="flex justify-between items-start mb-2">
                                <div className="font-medium text-sm text-gray-800">
                                    {rec.zoneId}
                                </div>
                                <span className="text-xs text-gray-400 flex items-center">
                                    <Clock size={10} className="mr-1" />
                                    {new Date(rec.detectedAt).toLocaleString()}
                                </span>
                            </div>
                            <div className="flex justify-between items-center">
                                <div className="text-xs text-gray-500">
                                    <div>点数: {rec.pointCount}</div>
                                    <div>时长: ---</div>
                                </div>
                                <button
                                    onClick={() => onPlay(rec)}
                                    className="flex items-center space-x-1 px-2 py-1 bg-blue-50 text-blue-600 rounded-md hover:bg-blue-100 transition-colors text-xs font-medium"
                                >
                                    <PlayCircle size={14} />
                                    <span>回放</span>
                                </button>
                            </div>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
};
