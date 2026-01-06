import React, { useState } from 'react';
import { Save, RefreshCw, Server, Wifi, CheckCircle2 } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';

export const MqttConfig: React.FC = () => {
  const { t } = useAppContext();
  const [showToast, setShowToast] = useState(false);
  const [toastMessage, setToastMessage] = useState('');
  
  const [formData, setFormData] = useState({
    host: 'broker.hivemq.com',
    port: '1883',
    clientId: 'harmony-guard-001',
    username: '',
    password: '',
    topicStatus: 'harmony/devices/+/status',
    topicCommand: 'harmony/devices/+/command',
    qos: '1'
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSave = () => {
    // Simulate save
    setToastMessage(t('config_saved'));
    setShowToast(true);
    setTimeout(() => setShowToast(false), 3000);
  };

  const handleTest = () => {
    // Simulate test
    setToastMessage(t('connection_success'));
    setShowToast(true);
    setTimeout(() => setShowToast(false), 3000);
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6 relative">
      
      {/* Toast Notification */}
      {showToast && (
        <div className="fixed top-20 right-8 bg-gray-800 text-white px-6 py-3 rounded-xl shadow-xl flex items-center space-x-3 z-50 animate-fade-in">
            <CheckCircle2 size={20} className="text-green-400" />
            <span className="font-medium">{toastMessage}</span>
        </div>
      )}

      {/* Connection Status Banner */}
      <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 flex items-center justify-between">
         <div className="flex items-center space-x-4">
            <div className="w-12 h-12 rounded-full bg-green-100 flex items-center justify-center text-green-600">
                <Wifi size={24} />
            </div>
            <div>
                <h3 className="font-bold text-gray-800">{t('connected')}</h3>
                <p className="text-sm text-gray-500">{formData.host}:{formData.port}</p>
            </div>
         </div>
         <button 
            onClick={handleTest}
            className="px-4 py-2 bg-gray-50 text-gray-600 rounded-xl hover:bg-gray-100 font-medium text-sm transition-colors border border-gray-200"
         >
            {t('test_connection')}
         </button>
      </div>

      <div className="bg-white p-8 rounded-2xl shadow-sm border border-gray-100">
        <h3 className="text-xl font-bold text-gray-800 mb-6 flex items-center">
            <Server size={22} className="mr-2 text-blue-600"/>
            {t('broker_config')}
        </h3>
        
        <form className="space-y-6" onSubmit={(e) => e.preventDefault()}>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="space-y-1">
              <label className="text-sm font-medium text-gray-700">{t('broker_host')}</label>
              <input
                type="text"
                name="host"
                value={formData.host}
                onChange={handleChange}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 outline-none transition-all"
                placeholder="e.g. 192.168.1.10"
              />
            </div>
            <div className="space-y-1">
              <label className="text-sm font-medium text-gray-700">{t('port')}</label>
              <input
                type="text"
                name="port"
                value={formData.port}
                onChange={handleChange}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 outline-none transition-all"
                placeholder="e.g. 1883"
              />
            </div>

            <div className="space-y-1">
              <label className="text-sm font-medium text-gray-700">{t('client_id')}</label>
              <input
                type="text"
                name="clientId"
                value={formData.clientId}
                onChange={handleChange}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 outline-none transition-all"
              />
            </div>
             <div className="space-y-1">
              <label className="text-sm font-medium text-gray-700">{t('qos_level')}</label>
              <select
                name="qos"
                value={formData.qos}
                onChange={handleChange}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 outline-none transition-all"
              >
                  <option value="0">0 - At most once</option>
                  <option value="1">1 - At least once</option>
                  <option value="2">2 - Exactly once</option>
              </select>
            </div>

            <div className="space-y-1">
              <label className="text-sm font-medium text-gray-700">{t('username')} (Optional)</label>
              <input
                type="text"
                name="username"
                value={formData.username}
                onChange={handleChange}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 outline-none transition-all"
              />
            </div>
            <div className="space-y-1">
              <label className="text-sm font-medium text-gray-700">{t('password')} (Optional)</label>
              <input
                type="password"
                name="password"
                value={formData.password}
                onChange={handleChange}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 outline-none transition-all"
              />
            </div>
          </div>

          <div className="pt-6 border-t border-gray-100">
            <h4 className="text-sm font-bold text-gray-900 mb-4 uppercase tracking-wide">{t('topic_mapping')}</h4>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="space-y-1">
                    <label className="text-sm font-medium text-gray-700">{t('status_topic')}</label>
                    <input
                        type="text"
                        name="topicStatus"
                        value={formData.topicStatus}
                        onChange={handleChange}
                        className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl font-mono text-sm"
                    />
                </div>
                <div className="space-y-1">
                    <label className="text-sm font-medium text-gray-700">{t('command_topic')}</label>
                    <input
                        type="text"
                        name="topicCommand"
                        value={formData.topicCommand}
                        onChange={handleChange}
                        className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl font-mono text-sm"
                    />
                </div>
            </div>
          </div>

          <div className="flex justify-end pt-4 space-x-3">
             <button className="flex items-center space-x-2 px-6 py-3 bg-white border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 transition-colors font-medium">
                <RefreshCw size={18} />
                <span>{t('reset')}</span>
            </button>
            <button 
                onClick={handleSave}
                className="flex items-center space-x-2 px-6 py-3 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-200"
            >
                <Save size={18} />
                <span>{t('save_config')}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};