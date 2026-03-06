import React, { useEffect, useRef, useState, useMemo } from 'react';
import * as THREE from 'three';
import { 
  BarChart, Bar, XAxis, Tooltip as RechartsTooltip, Cell, ResponsiveContainer,
  AreaChart, Area, YAxis, CartesianGrid,
  LineChart, Line
} from 'recharts';
import { Camera, Wifi, WifiOff, AlertTriangle, Zap, Activity, Monitor } from 'lucide-react';

// --- Types ---

interface AlarmHourly {
  hour: string;
  count: number;
}

interface Assembly {
  id: string;
  name: string;
  status: 'online' | 'partial' | 'offline'; // mapped from API or mock
  onlineCount: number;
  totalCount: number;
}

interface RadarCompare {
  deviceId: string;
  radarName: string;
  fps: number;
  pointCount: number;
  frameCount: number;
  intrusionCount: number;
  status: number; // 1: normal, 0: offline
}

interface RadarFramerate {
  time: string;
  [key: string]: number | string; // deviceId: fps
}

interface DeviceAvailability {
  time: string;
  total: number;
  online: number;
  rate: number;
}

interface AlarmRecord {
  id: string;
  time: string;
  content: string;
}

// --- API Helper ---

async function fetchAPI<T>(url: string): Promise<T | null> {
  try {
    const token = localStorage.getItem('nvr_auth_token');
    const res = await fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    });
    if (!res.ok) return null;
    const json = await res.json();
    return json.code === 200 ? json.data : null;
  } catch {
    return null;
  }
}

// --- Styles & Keyframes ---

const GlobalStyles = () => (
  <style>{`
    @import url('https://fonts.googleapis.com/css2?family=Courier+Prime:wght@400;700&display=swap');

    :root {
      --color-bg: #030712;
      --color-blue: #00d4ff;
      --color-green: #00ff9d;
      --color-orange: #ff6b35;
      --color-text-dim: rgba(255, 255, 255, 0.4);
    }

    body {
      margin: 0;
      padding: 0;
      background-color: var(--color-bg);
      font-family: 'Courier New', monospace;
      overflow: hidden;
    }

    /* Global Outline Removal */
    *:focus {
      outline: none !important;
    }
    
    /* Recharts specific focus removal */
    .recharts-wrapper:focus,
    .recharts-surface:focus,
    .recharts-layer:focus {
      outline: none !important;
    }
      width: 3px;
    }
    ::-webkit-scrollbar-track {
      background: transparent;
    }
    ::-webkit-scrollbar-thumb {
      background: var(--color-blue);
      border-radius: 2px;
    }

    /* Animations */
    @keyframes glowPulse {
      0%, 100% { text-shadow: 0 0 10px currentColor, 0 0 20px currentColor; }
      50% { text-shadow: 0 0 20px currentColor, 0 0 40px currentColor, 0 0 60px currentColor; }
    }

    @keyframes pulse {
      0%, 100% { transform: scale(1); opacity: 1; }
      50% { transform: scale(1.4); opacity: 0.7; }
    }

    @keyframes ripple {
      0% { transform: scale(1); opacity: 0.8; }
      100% { transform: scale(2.5); opacity: 0; }
    }

    @keyframes blink {
      0%, 100% { opacity: 1; }
      50% { opacity: 0; }
    }

    @keyframes slideInLeft {
      from { opacity: 0; transform: translateX(-20px); }
      to { opacity: 1; transform: translateX(0); }
    }

    @keyframes scanLine {
      0% { left: -100%; }
      100% { left: 200%; }
    }

    @keyframes borderFlow {
      0% { background-position: 0% 50%; }
      50% { background-position: 100% 50%; }
      100% { background-position: 0% 50%; }
    }

    @keyframes shimmer {
      0% { background-position: -200% 0; }
      100% { background-position: 200% 0; }
    }
    
    @keyframes marqueeIn {
      from { opacity: 0; transform: translateY(10px); }
      to { opacity: 1; transform: translateY(0); }
    }
    
    @keyframes marqueeOut {
      from { opacity: 1; transform: translateY(0); }
      to { opacity: 0; transform: translateY(-10px); }
    }

    .animate-glow-pulse { animation: glowPulse 2s infinite; }
    .animate-pulse-custom { animation: pulse 1.5s ease-in-out infinite; }
    .animate-ripple { animation: ripple 2s ease-out infinite; }
    .animate-blink { animation: blink 1s step-end infinite; }
    .animate-slide-in-left { animation: slideInLeft 0.4s ease-out forwards; }
    .animate-shimmer { 
      background: linear-gradient(90deg, rgba(255,255,255,0.05) 25%, rgba(255,255,255,0.1) 50%, rgba(255,255,255,0.05) 75%);
      background-size: 200% 100%;
      animation: shimmer 1.5s linear infinite;
    }
    
    .card-bg {
      background: rgba(0, 212, 255, 0.03);
      border: 1px solid rgba(0, 212, 255, 0.2);
      border-radius: 4px;
      position: relative;
    }
    .card-bg::before {
      content: '';
      position: absolute;
      top: 0; left: 0; right: 0;
      height: 2px;
      background: linear-gradient(90deg, transparent, #00d4ff, transparent);
    }
    
    .text-glow {
      text-shadow: 0 0 20px rgba(0,212,255,0.8), 0 0 40px rgba(0,212,255,0.4);
    }
  `}</style>
);

// --- Components ---

const Skeleton = ({ className }: { className?: string }) => (
  <div className={`animate-shimmer rounded ${className}`} />
);

const CardTitle = ({ title }: { title: string }) => (
  <div className="flex items-center mb-4">
    <div className="w-1 h-1 bg-[#00d4ff] mr-2 shadow-[0_0_5px_#00d4ff]" />
    <h3 className="text-[11px] font-bold tracking-[3px] text-[rgba(0,212,255,0.7)] uppercase">
      {title}
    </h3>
  </div>
);

// --- Left Panel Components ---

const VideoOverviewCard = () => {
  const [data, setData] = useState<{ total: number; online: number; alarm: number } | null>(null);
  const [displayOnline, setDisplayOnline] = useState(0);

  useEffect(() => {
    const loadData = async () => {
      const res = await fetchAPI<any>('/api/metrics/big-screen-summary');
      if (res) {
        setData({
          total: res.deviceTotal ?? 0,
          online: res.deviceOnline ?? 0,
          alarm: res.alarmTotal24h ?? 0
        });
      }
    };
    loadData();
    const timer = setInterval(loadData, 30000);
    return () => clearInterval(timer);
  }, []);

  // Number scrolling animation
  useEffect(() => {
    if (!data) return;
    let start = 0;
    const end = data.online;
    const duration = 1500;
    const startTime = performance.now();

    const animate = (currentTime: number) => {
      const elapsed = currentTime - startTime;
      const progress = Math.min(elapsed / duration, 1);
      // easeOutCubic
      const ease = 1 - Math.pow(1 - progress, 3);
      
      setDisplayOnline(Math.floor(start + (end - start) * ease));

      if (progress < 1) {
        requestAnimationFrame(animate);
      }
    };
    requestAnimationFrame(animate);
  }, [data?.online]);

  if (!data) return <div className="card-bg p-4 h-full"><Skeleton className="w-full h-full" /></div>;

  const onlineRate = (data.online / data.total) * 100;

  return (
    <div className="card-bg p-4 flex flex-col h-full overflow-hidden">
      <CardTitle title="视频监控总览" />
      
      <div className="flex-1 flex flex-col justify-between min-h-0">
        <div className="flex-shrink-0">
          <div className="text-[rgba(0,212,255,0.7)] text-xs mb-1">在线摄像头</div>
          <div className="text-[#00ff9d] text-[48px] font-bold leading-none animate-glow-pulse font-mono">
            {displayOnline}
          </div>
        </div>

        <div className="flex-shrink-0 my-2">
          <div className="flex justify-between text-[10px] text-[rgba(255,255,255,0.4)] mb-1">
            <span>在线率</span>
            <span>{onlineRate.toFixed(1)}%</span>
          </div>
          <div className="h-2 bg-[rgba(255,255,255,0.1)] rounded overflow-hidden">
            <div 
              className="h-full bg-gradient-to-r from-[#00ff9d] to-[#00d4ff] transition-all duration-1000 ease-out"
              style={{ width: `${onlineRate}%` }}
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2 flex-shrink-0">
          {[
            { label: '总数', value: data.total, icon: Camera, color: '#00d4ff' },
            { label: '在线', value: data.online, icon: Wifi, color: '#00d4ff' },
            { label: '告警', value: data.alarm, icon: AlertTriangle, color: '#ff6b35' },
            { label: '离线', value: data.total - data.online, icon: WifiOff, color: '#00d4ff' },
          ].map((item, i) => (
            <div 
              key={i}
              className="bg-[rgba(0,212,255,0.05)] border border-transparent hover:border-[#00d4ff] transition-colors duration-200 p-2 rounded flex items-center justify-between group"
            >
              <div className="min-w-0">
                <div className="text-[10px] text-[rgba(255,255,255,0.4)] truncate">{item.label}</div>
                <div className="text-lg font-bold leading-tight" style={{ color: item.color }}>{item.value}</div>
              </div>
              <item.icon size={16} className="text-[rgba(255,255,255,0.2)] group-hover:text-[#00d4ff] transition-colors flex-shrink-0 ml-1" />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

const AlarmHourlyCard = () => {
  const [data, setData] = useState<AlarmHourly[]>([]);

  useEffect(() => {
    const loadData = async () => {
      const res = await fetchAPI<AlarmHourly[]>('/api/metrics/alarm-hourly?hours=24');
      if (res) setData(res);
    };
    loadData();
    const timer = setInterval(loadData, 60000);
    return () => clearInterval(timer);
  }, []);

  const avg = data.reduce((acc, cur) => acc + cur.count, 0) / (data.length || 1);

  return (
    <div className="card-bg p-4 h-full flex flex-col">
      <CardTitle title="24小时告警热力图" />
      <div className="flex-1 w-full min-h-0">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data}>
            <XAxis 
              dataKey="hour" 
              interval={3} 
              tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 10 }}
              axisLine={false}
              tickLine={false}
            />
            <RechartsTooltip 
              cursor={{ fill: 'rgba(255,255,255,0.05)' }}
              contentStyle={{ 
                backgroundColor: 'rgba(3,7,18,0.9)', 
                borderColor: 'rgba(0,212,255,0.4)',
                color: '#fff',
                fontSize: '12px'
              }}
            />
            <Bar dataKey="count" barSize={8} radius={[2, 2, 0, 0]} animationDuration={1200} animationEasing="ease-out">
              {data.map((entry, index) => (
                <Cell 
                  key={`cell-${index}`} 
                  fill={entry.count > avg * 1.5 ? '#ff6b35' : 'url(#barGradient)'} 
                />
              ))}
            </Bar>
            <defs>
              <linearGradient id="barGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="rgba(0,212,255,0.8)" />
                <stop offset="100%" stopColor="rgba(0,212,255,0.1)" />
              </linearGradient>
            </defs>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};

const AssemblyStatusCard = () => {
  const [assemblies, setAssemblies] = useState<Assembly[]>([]);

  useEffect(() => {
    const loadData = async () => {
      const res = await fetchAPI<any[]>('/api/assemblies');
      if (res) {
        setAssemblies(res.map((a: any) => ({
          id: a.assemblyId ?? a.assembly_id ?? String(a.id),
          name: a.name,
          status: (() => {
            const total = a.deviceCount ?? 0;
            const online = a.onlineDeviceCount ?? 0;
            if (total === 0 || online === 0) return 'offline' as const;
            if (online < total) return 'partial' as const;
            return 'online' as const;
          })(),
          onlineCount: a.onlineDeviceCount ?? 0,
          totalCount: a.deviceCount ?? 0,
        })));
      }
    };
    loadData();
    const timer = setInterval(loadData, 30000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="card-bg p-4 h-full flex flex-col overflow-hidden">
      <CardTitle title="装置运行状态" />
      <div className="flex-1 overflow-y-auto pr-2 space-y-2">
        {assemblies.map((item, index) => (
          <div 
            key={item.id}
            className="flex items-center justify-between p-2 rounded hover:bg-[rgba(0,212,255,0.08)] transition-all duration-150 border-l-2 border-transparent hover:border-[#00d4ff] animate-slide-in-left"
            style={{ animationDelay: `${index * 80}ms` }}
          >
            <div className="flex items-center space-x-3">
              <div className="relative w-3 h-3 flex items-center justify-center">
                <div className={`w-2 h-2 rounded-full ${
                  item.status === 'online' ? 'bg-[#00ff9d]' : 
                  item.status === 'partial' ? 'bg-yellow-400' : 'bg-[#ff6b35]'
                }`} />
                <div className={`absolute w-full h-full rounded-full animate-ripple ${
                  item.status === 'online' ? 'bg-[#00ff9d]' : 
                  item.status === 'partial' ? 'bg-yellow-400' : 'bg-[#ff6b35]'
                }`} />
              </div>
              <span className="text-sm text-white font-medium">{item.name}</span>
            </div>
            <span className="text-xs text-[rgba(255,255,255,0.5)] font-mono">
              {item.onlineCount}/{item.totalCount}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
};

// --- Center Panel Components ---

const SVGGauge = ({ value, label, unit }: { value: number; label: string; unit: string }) => {
  const [displayValue, setDisplayValue] = useState(0);
  
  // Animation for value
  useEffect(() => {
    let start = 0;
    const end = value;
    const duration = 1800;
    const startTime = performance.now();

    const animate = (currentTime: number) => {
      const elapsed = currentTime - startTime;
      const progress = Math.min(elapsed / duration, 1);
      // easeOutExpo
      const ease = progress === 1 ? 1 : 1 - Math.pow(2, -10 * progress);
      
      setDisplayValue(start + (end - start) * ease);

      if (progress < 1) {
        requestAnimationFrame(animate);
      }
    };
    requestAnimationFrame(animate);
  }, [value]);

  // SVG parameters
  const radius = 80;
  const strokeWidth = 10;
  const center = 100;
  const circumference = 2 * Math.PI * radius;
  const arcLength = circumference; // 360 degrees
  const dashOffset = arcLength - (displayValue / 100) * arcLength;
  
  // Rotation to start from -90deg (top)
  const rotation = -90; 

  const isHigh = displayValue > 60;
  const colorStart = isHigh ? '#00ff9d' : '#ff6b35';
  const colorEnd = isHigh ? '#00d4ff' : '#ff9f43';

  return (
    <div className="flex flex-col items-center justify-center relative w-[200px] h-[200px]">
      <svg width="200" height="200" viewBox="0 0 200 200">
        {/* Background Arc */}
        <circle
          cx={center}
          cy={center}
          r={radius}
          fill="none"
          stroke="rgba(255,255,255,0.06)"
          strokeWidth={strokeWidth}
          transform={`rotate(${rotation} ${center} ${center})`}
        />
        {/* Progress Arc */}
        <defs>
          <linearGradient id={`grad-${label}`} x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor={colorStart} />
            <stop offset="100%" stopColor={colorEnd} />
          </linearGradient>
        </defs>
        <circle
          cx={center}
          cy={center}
          r={radius}
          fill="none"
          stroke={`url(#grad-${label})`}
          strokeWidth={strokeWidth}
          strokeDasharray={`${arcLength} ${circumference}`}
          strokeDashoffset={dashOffset}
          strokeLinecap="round"
          transform={`rotate(${rotation} ${center} ${center})`}
          style={{ transition: 'stroke-dashoffset 0.1s linear' }}
        />
        {/* Decorative Outer Ring */}
        <circle
          cx={center}
          cy={center}
          r={radius + 15}
          fill="none"
          stroke="rgba(0,212,255,0.15)"
          strokeWidth="1"
          strokeDasharray="4 4"
        />
      </svg>
      {/* Center Text */}
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <div className="text-4xl font-bold text-white font-mono">
          {Math.round(displayValue)}<span className="text-sm ml-1">{unit}</span>
        </div>
        <div className="text-[10px] tracking-[2px] text-[rgba(0,212,255,0.7)] mt-1 uppercase">
          {label}
        </div>
      </div>
    </div>
  );
};

const GaugeDashboard = () => {
  const [metrics, setMetrics] = useState({ availability: 0, radarRate: 0, alarmRate: 85 });

  useEffect(() => {
    const loadData = async () => {
      const summary = await fetchAPI<any>('/api/metrics/big-screen-summary');
      if (summary) {
        setMetrics({
          availability: summary.deviceAvailability ?? 0,
          radarRate: summary.radarRate ?? 0,
          alarmRate: summary.alarmHandleRate ?? 100
        });
      }
    };
    loadData();
    const timer = setInterval(loadData, 30000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="flex justify-around items-center py-6 bg-gradient-to-b from-[rgba(0,212,255,0.05)] to-transparent border-b border-[rgba(0,212,255,0.15)]">
      <SVGGauge value={metrics.availability} label="设备在线率" unit="%" />
      <SVGGauge value={metrics.radarRate} label="雷达运行率" unit="%" />
      <SVGGauge value={metrics.alarmRate} label="告警处理率" unit="%" />
    </div>
  );
};

const DeviceAvailabilityChart = () => {
  const [data, setData] = useState<DeviceAvailability[]>([]);

  useEffect(() => {
    const loadData = async () => {
      const res = await fetchAPI<DeviceAvailability[]>('/api/metrics/device-availability?minutes=60');
      if (res && res.length > 0) setData(res);
    };
    loadData();
    const timer = setInterval(loadData, 30000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="flex-1 p-4 flex flex-col min-h-0">
      <div className="flex items-center justify-between mb-4">
        <CardTitle title="设备在线趋势" />
        <div className="flex items-center space-x-2">
          <div className="w-2 h-2 rounded-full bg-red-500 animate-blink" />
          <span className="text-xs font-bold text-red-500 tracking-wider">实时</span>
        </div>
      </div>
      
      <div className="flex-1 w-full min-h-0">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 10, right: 20, left: -10, bottom: 0 }}>
            <defs>
              <linearGradient id="totalFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="rgba(255,255,255,0.05)" />
                <stop offset="95%" stopColor="rgba(255,255,255,0)" />
              </linearGradient>
              <linearGradient id="onlineFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="rgba(0,255,157,0.2)" />
                <stop offset="95%" stopColor="rgba(0,255,157,0)" />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="rgba(255,255,255,0.04)" vertical={false} />
            <XAxis dataKey="time" tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 10 }} axisLine={{ stroke: 'rgba(255,255,255,0.2)' }} tickLine={false} />
            <YAxis yAxisId="left" tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 10 }} axisLine={false} tickLine={false} />
            <YAxis yAxisId="right" orientation="right" domain={[0, 100]} tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 10 }} axisLine={false} tickLine={false} unit="%" />
            <RechartsTooltip 
              contentStyle={{ backgroundColor: 'rgba(3,7,18,0.9)', borderColor: 'rgba(0,212,255,0.4)', color: '#fff' }}
              itemStyle={{ fontSize: '12px' }}
            />
            <Area yAxisId="left" type="monotone" dataKey="total" stroke="rgba(255,255,255,0.3)" fill="url(#totalFill)" strokeWidth={1} />
            <Area yAxisId="left" type="monotone" dataKey="online" stroke="#00ff9d" fill="url(#onlineFill)" strokeWidth={2} />
            <Line yAxisId="right" type="monotone" dataKey="rate" stroke="#00d4ff" strokeWidth={2} strokeDasharray="5 3" dot={false} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};

// --- Right Panel Components ---

const RadarCompareCard = () => {
  const [radars, setRadars] = useState<RadarCompare[]>([]);

  useEffect(() => {
    const loadData = async () => {
      const res = await fetchAPI<RadarCompare[]>('/api/metrics/radar-compare');
      if (res && res.length > 0) setRadars(res);
    };
    loadData();
    const timer = setInterval(loadData, 5000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="card-bg p-4 h-full flex flex-col overflow-hidden">
      <CardTitle title="雷达状态监测" />
      <div className="flex-1 overflow-y-auto space-y-4 pr-1">
        {radars.map((radar) => (
          <div key={radar.deviceId} className="pb-4 border-b border-[rgba(0,212,255,0.1)] last:border-0">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center space-x-2">
                <div className={`w-2 h-2 rounded-full ${radar.status === 1 ? 'bg-green-500 shadow-[0_0_5px_#22c55e]' : 'bg-gray-500 opacity-50'}`} />
                <span className={`text-sm font-bold ${radar.status === 1 ? 'text-white' : 'text-gray-500'}`}>{radar.radarName}</span>
              </div>
              <span className={`text-[10px] px-1.5 py-0.5 rounded border ${
                radar.status === 1 ? 'border-green-500/30 text-green-400 bg-green-500/10' : 'border-gray-500/30 text-gray-400 bg-gray-500/10'
              }`}>
                {radar.status === 1 ? '正常' : '离线'}
              </span>
            </div>

            <div className="space-y-2">
              {/* FPS */}
              <div className="flex items-center justify-between text-xs">
                <span className="text-[rgba(255,255,255,0.5)]">FPS</span>
                <span className="text-[#00d4ff] font-mono">{radar.fps.toFixed(1)} fps</span>
              </div>
              <div className="h-1 bg-[rgba(255,255,255,0.08)] rounded overflow-hidden">
                <div 
                  className="h-full bg-[#00d4ff] transition-all duration-700 ease-out"
                  style={{ width: `${Math.min((radar.fps / 20) * 100, 100)}%` }}
                />
              </div>

              {/* Points */}
              <div className="flex items-center justify-between text-xs">
                <span className="text-[rgba(255,255,255,0.5)]">点云数</span>
                <span className="text-[#00ff9d] font-mono">{radar.pointCount.toLocaleString()}</span>
              </div>
              <div className="h-1 bg-[rgba(255,255,255,0.08)] rounded overflow-hidden">
                <div 
                  className="h-full bg-[#00ff9d] transition-all duration-700 ease-out"
                  style={{ width: `${Math.min((radar.pointCount / 50000) * 100, 100)}%` }}
                />
              </div>

              {/* Intrusion */}
              <div className="flex items-center justify-between text-xs mt-1">
                <span className="text-[rgba(255,255,255,0.5)]">侵入数</span>
                <div className="flex items-center space-x-1">
                  {radar.intrusionCount > 0 && <AlertTriangle size={12} className="text-[#ff6b35] animate-bounce" />}
                  <span className={`font-mono ${radar.intrusionCount > 0 ? 'text-[#ff6b35] font-bold' : 'text-gray-500'}`}>
                    {radar.intrusionCount}
                  </span>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

const RadarFramerateCard = () => {
  const [data, setData] = useState<RadarFramerate[]>([]);
  const [radarKeys, setRadarKeys] = useState<string[]>([]);

  useEffect(() => {
    const loadData = async () => {
      const res = await fetchAPI<any[]>('/api/metrics/radar-framerate?minutes=10');
      if (res && res.length > 0) {
        // 按时间归并，每个 deviceId 作为一列
        const timeMap = new Map<string, RadarFramerate>();
        const keys = new Set<string>();
        res.forEach(item => {
          const t = item.time as string;
          const devId = item.deviceId as string;
          keys.add(devId);
          if (!timeMap.has(t)) timeMap.set(t, { time: t });
          timeMap.get(t)![devId] = item.fps as number;
        });
        setRadarKeys(Array.from(keys));
        setData(Array.from(timeMap.values()));
      }
    };
    loadData();
    const timer = setInterval(loadData, 10000);
    return () => clearInterval(timer);
  }, []);

  const colors = ['#00d4ff', '#00ff9d', '#ff6b35', '#a78bfa', '#f59e0b'];

  return (
    <div className="card-bg p-4 h-full flex flex-col">
      <CardTitle title="帧率历史曲线" />
      <div className="flex-1 w-full min-h-0">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data}>
            <CartesianGrid stroke="rgba(255,255,255,0.04)" vertical={false} />
            <XAxis dataKey="time" tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 10 }} axisLine={false} tickLine={false} interval={2} />
            <YAxis tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 10 }} axisLine={false} tickLine={false} domain={[0, 25]} />
            <RechartsTooltip 
              contentStyle={{ backgroundColor: 'rgba(3,7,18,0.9)', borderColor: 'rgba(0,212,255,0.4)', color: '#fff', fontSize: '12px' }}
              cursor={{ stroke: 'rgba(0,212,255,0.4)', strokeWidth: 1, strokeDasharray: '5 5' }}
            />
            {radarKeys.map((key, i) => (
              <Line key={key} type="monotone" dataKey={key} stroke={colors[i % colors.length]} strokeWidth={2} dot={false} />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};

// --- Main Layout Components ---

const TopBar = () => {
  const [time, setTime] = useState(new Date());

  useEffect(() => {
    const timer = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="relative h-[80px] flex items-center justify-between px-8 z-10 overflow-hidden"
      style={{
        background: 'linear-gradient(90deg, transparent, rgba(0,212,255,0.08), transparent)',
        borderBottom: '1px solid rgba(0,212,255,0.3)',
        boxShadow: '0 0 20px rgba(0,212,255,0.2)'
      }}
    >
      {/* Scanline effect */}
      <div className="absolute top-0 bottom-0 w-[100px] bg-gradient-to-r from-transparent via-[rgba(255,255,255,0.1)] to-transparent skew-x-[-20deg]"
        style={{ animation: 'scanLine 3s linear infinite' }}
      />
      
      {/* Corner decorations */}
      <div className="absolute top-0 left-0 w-5 h-5 border-t-2 border-l-2 border-[#00d4ff]" />
      <div className="absolute top-0 right-0 w-5 h-5 border-t-2 border-r-2 border-[#00d4ff]" />
      <div className="absolute bottom-0 left-0 w-5 h-5 border-b-2 border-l-2 border-[#00d4ff]" />
      <div className="absolute bottom-0 right-0 w-5 h-5 border-b-2 border-r-2 border-[#00d4ff]" />

      {/* Left: Time */}
      <div className="text-[#00d4ff] font-mono text-sm tracking-widest w-[200px]">
        {time.toISOString().replace('T', ' ').split('.')[0]}
      </div>

      {/* Center: Title */}
      <div className="flex flex-col items-center">
        <h1 className="text-white text-[28px] font-bold text-glow tracking-wider">
          智能安防监控指挥中心
        </h1>
        <div className="text-[11px] text-[rgba(0,212,255,0.6)] tracking-[4px] mt-1">
          INTELLIGENT SECURITY COMMAND CENTER
        </div>
      </div>

      {/* Right: Status */}
      <div className="flex items-center space-x-3 w-[200px] justify-end">
        <div className="w-3 h-3 rounded-full bg-[#00ff9d] animate-pulse-custom" />
        <span className="text-[#00ff9d] font-bold tracking-widest text-sm">系统在线</span>
      </div>
    </div>
  );
};

const BottomBar = () => {
  const [alarms, setAlarms] = useState<AlarmRecord[]>([]);
  const [currentAlarmIndex, setCurrentAlarmIndex] = useState(0);
  const [ping, setPing] = useState(12);

  useEffect(() => {
    const loadAlarms = async () => {
      const res = await fetchAPI<any[]>('/api/metrics/alarm-recent?limit=20');
      if (res && res.length > 0) {
        setAlarms(res.map((a: any) => ({
          id: a.id ?? String(Math.random()),
          time: a.time ?? '--:--:--',
          content: a.content ?? '告警'
        })));
        setCurrentAlarmIndex(0);
      }
    };
    loadAlarms();
    const refreshTimer = setInterval(loadAlarms, 30000);

    const marqueeTimer = setInterval(() => {
      setCurrentAlarmIndex(prev => alarms.length > 0 ? (prev + 1) % alarms.length : 0);
    }, 3000);

    const pingTimer = setInterval(() => {
      setPing(Math.floor(5 + Math.random() * 25));
    }, 5000);

    return () => {
      clearInterval(refreshTimer);
      clearInterval(marqueeTimer);
      clearInterval(pingTimer);
    };
  }, [alarms.length]);

  return (
    <div className="h-[50px] bg-[rgba(0,0,0,0.5)] border-t border-[rgba(0,212,255,0.2)] flex items-center justify-between px-6 z-10">
      <div className="text-[rgba(255,255,255,0.3)] text-[11px] font-mono">
        v1.0.0 BUILD 20260306
      </div>

      <div className="flex-1 mx-10 h-full flex items-center justify-center overflow-hidden relative">
        {alarms.length > 0 && (
          <div 
            key={currentAlarmIndex}
            className="flex items-center space-x-4 animate-[marqueeIn_0.5s_ease]"
          >
            <span className="text-[#ff6b35] font-mono text-xs">[{alarms[currentAlarmIndex].time}]</span>
            <span className="text-white text-sm tracking-wide">{alarms[currentAlarmIndex].content}</span>
          </div>
        )}
      </div>

      <div className="flex items-center space-x-2 text-[#00ff9d] font-mono text-xs">
        <Activity size={14} />
        <span>PING {ping}ms</span>
      </div>
    </div>
  );
};

const ThreeBackground = () => {
  const mountRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!mountRef.current) return;

    const width = window.innerWidth;
    const height = window.innerHeight;

    // Scene
    const scene = new THREE.Scene();
    
    // Camera
    const camera = new THREE.PerspectiveCamera(75, width / height, 0.1, 1000);
    camera.position.z = 100;

    // Renderer
    const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(window.devicePixelRatio);
    mountRef.current.appendChild(renderer.domElement);

    // Particles
    const particleCount = 3000;
    const geometry = new THREE.BufferGeometry();
    const positions = new Float32Array(particleCount * 3);
    const colors = new Float32Array(particleCount * 3);

    const color1 = new THREE.Color('#00d4ff');
    const color2 = new THREE.Color('#00ff9d');

    for (let i = 0; i < particleCount; i++) {
      // Random position in a cube
      positions[i * 3] = (Math.random() - 0.5) * 200;
      positions[i * 3 + 1] = (Math.random() - 0.5) * 200;
      positions[i * 3 + 2] = (Math.random() - 0.5) * 200;

      // Random color mix
      const mixedColor = color1.clone().lerp(color2, Math.random());
      colors[i * 3] = mixedColor.r;
      colors[i * 3 + 1] = mixedColor.g;
      colors[i * 3 + 2] = mixedColor.b;
    }

    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));

    const material = new THREE.PointsMaterial({
      size: 0.6,
      vertexColors: true,
      transparent: true,
      opacity: 0.3,
      sizeAttenuation: true
    });

    const particles = new THREE.Points(geometry, material);
    scene.add(particles);

    // Animation Loop
    let animationId: number;
    const animate = () => {
      animationId = requestAnimationFrame(animate);
      
      particles.rotation.y += 0.0002;
      particles.rotation.x += 0.0001;

      renderer.render(scene, camera);
    };
    animate();

    // Resize Handler
    const handleResize = () => {
      camera.aspect = window.innerWidth / window.innerHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(window.innerWidth, window.innerHeight);
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      cancelAnimationFrame(animationId);
      if (mountRef.current) {
        mountRef.current.removeChild(renderer.domElement);
      }
      geometry.dispose();
      material.dispose();
      renderer.dispose();
    };
  }, []);

  return (
    <div 
      ref={mountRef} 
      style={{ 
        position: 'fixed', 
        top: 0, 
        left: 0, 
        width: '100vw', 
        height: '100vh', 
        zIndex: -1, 
        pointerEvents: 'none' 
      }} 
    />
  );
};

// --- Main Component ---

const BigScreen: React.FC = () => {
  return (
    <>
      <GlobalStyles />
      
      {/* Background Layers */}
      <div className="fixed inset-0 bg-[#030712] -z-20" />
      <div 
        className="fixed inset-0 -z-10"
        style={{
          backgroundImage: 'radial-gradient(rgba(0,212,255,0.05) 1px, transparent 1px)',
          backgroundSize: '40px 40px'
        }}
      />
      <ThreeBackground />

      {/* Main Grid Layout */}
      <div className="relative z-10 h-screen w-screen grid grid-rows-[80px_1fr_50px] grid-cols-[380px_1fr_380px] overflow-hidden min-w-[1280px]">
        
        {/* Top Bar (Full Width) */}
        <div className="col-span-3">
          <TopBar />
        </div>

        {/* Left Panel */}
        <div className="p-5 flex flex-col space-y-4 overflow-hidden">
          <div className="flex-[0.4] min-h-0 relative">
            <VideoOverviewCard />
          </div>
          <div className="flex-[0.25] min-h-0 relative">
            <AlarmHourlyCard />
          </div>
          <div className="flex-[0.35] min-h-0 relative">
            <AssemblyStatusCard />
          </div>
        </div>

        {/* Center Panel */}
        <div className="py-5 px-2 flex flex-col space-y-5 overflow-hidden">
          <div className="flex-none">
            <GaugeDashboard />
          </div>
          <div className="flex-1 card-bg flex flex-col min-h-0">
            <DeviceAvailabilityChart />
          </div>
        </div>

        {/* Right Panel */}
        <div className="p-5 flex flex-col space-y-5 overflow-hidden">
          <div className="flex-[0.55] min-h-0">
            <RadarCompareCard />
          </div>
          <div className="flex-[0.45] min-h-0">
            <RadarFramerateCard />
          </div>
        </div>

        {/* Bottom Bar (Full Width) */}
        <div className="col-span-3">
          <BottomBar />
        </div>

      </div>
    </>
  );
};

export default BigScreen;
