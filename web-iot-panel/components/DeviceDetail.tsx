import React, { useState } from 'react';
import { 
  ArrowLeft, 
  Camera, 
  Settings, 
  Volume2, 
  VolumeX, 
  ZoomIn, 
  ZoomOut, 
  RotateCw,
  MoreHorizontal,
  Calendar,
  Download
} from 'lucide-react';
import { MOCK_DEVICES } from '../constants';
import { useAppContext } from '../contexts/AppContext';

interface DeviceDetailProps {
  deviceId: string;
  onBack: () => void;
}

export const DeviceDetail: React.FC<DeviceDetailProps> = ({ deviceId, onBack }) => {
  const { t } = useAppContext();
  const device = MOCK_DEVICES.find(d => d.id === deviceId);
  const [isMuted, setIsMuted] = useState(true);
  const [snapshot, setSnapshot] = useState<string | null>(null);

  if (!device) return <div>Device not found</div>;

  const handleSnapshot = () => {
    // Simulate snapshot
    setSnapshot(`https://picsum.photos/400/300?random=${Date.now()}`);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-4">
          <button 
            onClick={onBack}
            className="p-2 bg-white border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors"
          >
            <ArrowLeft size={20} className="text-gray-600" />
          </button>
          <div>
            <h2 className="text-2xl font-bold text-gray-800">{device.name}</h2>
            <div className="flex items-center space-x-3 text-sm text-gray-500 mt-1">
              <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${device.status === 'ONLINE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                {device.status}
              </span>
              <span>•</span>
              <span className="font-mono">{device.ip}</span>
              <span>•</span>
              <span>{device.brand} {device.model}</span>
            </div>
          </div>
        </div>
        <div className="flex items-center space-x-3">
            <button className="flex items-center space-x-2 px-4 py-2 bg-white border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 transition-colors shadow-sm">
                <Settings size={18} />
                <span>{t('settings')}</span>
            </button>
            <button className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors shadow-lg shadow-blue-200">
                <RotateCw size={18} />
                <span>{t('reboot')}</span>
            </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Video Area */}
        <div className="lg:col-span-2 space-y-6">
          <div className="relative bg-black rounded-2xl overflow-hidden shadow-lg aspect-video group">
            <img 
              src={`https://picsum.photos/800/450?random=${device.id}`} 
              alt="Live Feed" 
              className="w-full h-full object-cover opacity-80"
            />
            
            {/* Overlay UI */}
            <div className="absolute top-4 left-4 flex items-center space-x-2">
                <span className="px-2 py-1 bg-red-600/90 text-white text-xs font-bold rounded flex items-center">
                    <span className="w-2 h-2 bg-white rounded-full mr-1.5 animate-pulse"></span>
                    LIVE
                </span>
                <span className="px-2 py-1 bg-black/50 backdrop-blur-md text-white text-xs rounded font-mono">
                    {new Date().toLocaleTimeString()}
                </span>
            </div>

            <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/80 to-transparent flex items-center justify-between opacity-0 group-hover:opacity-100 transition-opacity duration-300">
                <div className="flex items-center space-x-4">
                    <button 
                        onClick={() => setIsMuted(!isMuted)}
                        className="text-white hover:text-blue-400 transition-colors"
                    >
                        {isMuted ? <VolumeX size={20} /> : <Volume2 size={20} />}
                    </button>
                    <input type="range" className="w-24 h-1 bg-gray-600 rounded-lg appearance-none cursor-pointer" />
                </div>
                <div className="flex items-center space-x-3">
                    <button className="text-white hover:text-blue-400" title="Full Screen"><Settings size={18} /></button>
                </div>
            </div>
          </div>
          
          {/* Timeline / Playback Controls */}
          <div className="bg-white p-4 rounded-2xl shadow-sm border border-gray-100">
             <div className="flex items-center justify-between mb-4">
                <h3 className="font-semibold text-gray-800 flex items-center">
                    <Calendar size={18} className="mr-2 text-blue-500" />
                    {t('playback')}
                </h3>
                <input type="date" className="bg-gray-50 border border-gray-200 rounded-lg px-3 py-1 text-sm outline-none focus:ring-1 focus:ring-blue-500"/>
             </div>
             <div className="h-12 bg-gray-50 rounded-lg border border-gray-100 relative overflow-hidden flex items-center px-2 cursor-pointer">
                 {/* Mock Timeline */}
                 <div className="w-1/4 h-2 bg-blue-200 rounded-full mr-1"></div>
                 <div className="w-1/6 h-2 bg-gray-200 rounded-full mr-1"></div>
                 <div className="w-1/3 h-2 bg-blue-500 rounded-full mr-1"></div>
                 <div className="absolute top-0 bottom-0 left-1/2 w-0.5 bg-red-500 z-10"></div>
             </div>
          </div>
        </div>

        {/* Controls Sidebar */}
        <div className="space-y-6">
            {/* PTZ Control */}
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
                <h3 className="font-bold text-gray-800 mb-6 flex items-center justify-between">
                    {t('ptz_control')}
                    <span className="text-xs bg-blue-50 text-blue-600 px-2 py-1 rounded">Active</span>
                </h3>
                
                <div className="flex justify-center mb-6">
                    <div className="relative w-48 h-48 bg-gray-50 rounded-full border border-gray-200 flex items-center justify-center shadow-inner">
                         {/* D-Pad Buttons */}
                         <button className="absolute top-2 left-1/2 -translate-x-1/2 w-10 h-10 bg-white shadow-md rounded-lg flex items-center justify-center text-gray-600 hover:text-blue-600 active:bg-blue-50 transition-colors">
                            <span className="rotate-90">‹</span>
                         </button>
                         <button className="absolute bottom-2 left-1/2 -translate-x-1/2 w-10 h-10 bg-white shadow-md rounded-lg flex items-center justify-center text-gray-600 hover:text-blue-600 active:bg-blue-50 transition-colors">
                            <span className="-rotate-90">‹</span>
                         </button>
                         <button className="absolute left-2 top-1/2 -translate-y-1/2 w-10 h-10 bg-white shadow-md rounded-lg flex items-center justify-center text-gray-600 hover:text-blue-600 active:bg-blue-50 transition-colors">
                            <span>‹</span>
                         </button>
                         <button className="absolute right-2 top-1/2 -translate-y-1/2 w-10 h-10 bg-white shadow-md rounded-lg flex items-center justify-center text-gray-600 hover:text-blue-600 active:bg-blue-50 transition-colors">
                            <span className="rotate-180">‹</span>
                         </button>
                         
                         {/* Center Reset */}
                         <button className="w-14 h-14 bg-gradient-to-b from-white to-gray-50 rounded-full shadow flex items-center justify-center text-xs font-bold text-gray-500 border border-gray-100 active:scale-95 transition-transform">
                             AUTO
                         </button>
                    </div>
                </div>

                <div className="flex justify-between items-center px-4">
                    <div className="flex flex-col items-center space-y-2">
                        <button className="w-10 h-10 rounded-full bg-gray-100 hover:bg-gray-200 flex items-center justify-center transition-colors">
                            <ZoomOut size={18} />
                        </button>
                        <span className="text-xs text-gray-500">Zoom Out</span>
                    </div>
                    <div className="flex flex-col items-center space-y-2">
                        <button className="w-10 h-10 rounded-full bg-blue-50 hover:bg-blue-100 text-blue-600 flex items-center justify-center transition-colors">
                            <ZoomIn size={18} />
                        </button>
                        <span className="text-xs text-gray-500">Zoom In</span>
                    </div>
                </div>
            </div>

            {/* Actions */}
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
                <h3 className="font-bold text-gray-800 mb-4">{t('quick_actions')}</h3>
                <div className="grid grid-cols-2 gap-3">
                    <button 
                        onClick={handleSnapshot}
                        className="flex flex-col items-center justify-center p-3 bg-gray-50 rounded-xl hover:bg-blue-50 hover:text-blue-600 transition-colors border border-transparent hover:border-blue-100"
                    >
                        <Camera size={20} className="mb-1" />
                        <span className="text-xs font-medium">{t('snapshot')}</span>
                    </button>
                     <button className="flex flex-col items-center justify-center p-3 bg-gray-50 rounded-xl hover:bg-blue-50 hover:text-blue-600 transition-colors border border-transparent hover:border-blue-100">
                        <Download size={20} className="mb-1" />
                        <span className="text-xs font-medium">{t('export')}</span>
                    </button>
                </div>

                {snapshot && (
                    <div className="mt-4 p-2 bg-gray-50 rounded-lg border border-gray-100 animate-fade-in">
                        <p className="text-xs text-gray-500 mb-2">Latest Snapshot:</p>
                        <img src={snapshot} alt="Snapshot" className="w-full rounded-md shadow-sm" />
                    </div>
                )}
            </div>
        </div>
      </div>
    </div>
  );
};