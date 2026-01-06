import React from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { Shield, Wifi, AlertTriangle, HardDrive } from 'lucide-react';
import { CHART_DATA } from '../constants';
import { useAppContext } from '../contexts/AppContext';

const StatCard = ({ icon: Icon, label, value, trend, colorClass, bgClass }: any) => (
  <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow duration-300">
    <div className="flex items-center justify-between mb-4">
      <div className={`p-3 rounded-xl ${bgClass}`}>
        <Icon className={colorClass} size={24} />
      </div>
      <span className={`text-xs font-semibold px-2 py-1 rounded-full ${trend === 'up' ? 'bg-green-50 text-green-600' : 'bg-red-50 text-red-600'}`}>
        {trend === 'up' ? '+2.5%' : '-1.2%'}
      </span>
    </div>
    <h3 className="text-3xl font-bold text-gray-800 mb-1">{value}</h3>
    <p className="text-sm text-gray-500 font-medium">{label}</p>
  </div>
);

export const Dashboard: React.FC = () => {
  const { t } = useAppContext();

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard 
          icon={Shield} 
          label={t('active_devices')}
          value="24" 
          trend="up" 
          colorClass="text-blue-600" 
          bgClass="bg-blue-50"
        />
        <StatCard 
          icon={Wifi} 
          label={t('online_status')}
          value="98.2%" 
          trend="up" 
          colorClass="text-green-600" 
          bgClass="bg-green-50"
        />
        <StatCard 
          icon={AlertTriangle} 
          label={t('alerts_24h')} 
          value="3" 
          trend="down" 
          colorClass="text-orange-600" 
          bgClass="bg-orange-50"
        />
        <StatCard 
          icon={HardDrive} 
          label={t('storage_used')} 
          value="4.2 TB" 
          trend="up" 
          colorClass="text-purple-600" 
          bgClass="bg-purple-50"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
          <div className="flex items-center justify-between mb-6">
            <h3 className="text-lg font-bold text-gray-800">{t('device_connectivity')}</h3>
            <select className="bg-gray-50 border border-gray-200 text-gray-600 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block p-2">
              <option>Last 24 Hours</option>
              <option>Last 7 Days</option>
            </select>
          </div>
          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={CHART_DATA}>
                <defs>
                  <linearGradient id="colorOnline" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#10b981" stopOpacity={0.2}/>
                    <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{fill: '#94a3b8', fontSize: 12}} dy={10} />
                <YAxis axisLine={false} tickLine={false} tick={{fill: '#94a3b8', fontSize: 12}} />
                <Tooltip 
                  contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)' }}
                />
                <Area 
                  type="monotone" 
                  dataKey="online" 
                  stroke="#10b981" 
                  strokeWidth={3}
                  fillOpacity={1} 
                  fill="url(#colorOnline)" 
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
          <h3 className="text-lg font-bold text-gray-800 mb-4">{t('quick_actions')}</h3>
          <div className="space-y-3">
            <button className="w-full py-3 px-4 bg-gray-50 hover:bg-blue-50 hover:text-blue-600 rounded-xl flex items-center justify-between group transition-colors">
              <span className="font-medium text-sm">{t('add_device')}</span>
              <span className="bg-white p-1 rounded-md shadow-sm group-hover:bg-blue-200 transition-colors">+</span>
            </button>
            <button className="w-full py-3 px-4 bg-gray-50 hover:bg-blue-50 hover:text-blue-600 rounded-xl flex items-center justify-between group transition-colors">
              <span className="font-medium text-sm">{t('health_check')}</span>
              <span className="bg-white p-1 rounded-md shadow-sm group-hover:bg-blue-200 transition-colors">Run</span>
            </button>
            <button className="w-full py-3 px-4 bg-gray-50 hover:bg-blue-50 hover:text-blue-600 rounded-xl flex items-center justify-between group transition-colors">
              <span className="font-medium text-sm">{t('restart_mqtt')}</span>
              <span className="bg-white p-1 rounded-md shadow-sm group-hover:bg-blue-200 transition-colors">Auto</span>
            </button>
            <button className="w-full py-3 px-4 bg-gray-50 hover:bg-blue-50 hover:text-blue-600 rounded-xl flex items-center justify-between group transition-colors">
              <span className="font-medium text-sm">{t('view_logs')}</span>
              <span className="bg-white p-1 rounded-md shadow-sm group-hover:bg-blue-200 transition-colors">→</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};