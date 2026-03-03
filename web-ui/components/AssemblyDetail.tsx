import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { ArrowLeft, Package, Bell, History, Plus, X, Trash2, Edit2, Camera, Crosshair, MapPin, Radio, Shield, Volume2, ChevronRight } from 'lucide-react';
import { useAppContext } from '../contexts/AppContext';
import { assemblyService, alarmRuleService, deviceService, radarService } from '../src/api/services';
import { Assembly, AssemblyDevice, AlarmRule, DeviceRole, Device, DeviceStatus } from '../types';
import { Modal, ConfirmModal } from './Modal';
import { useModal } from '../hooks/useModal';
import { DeviceSelector } from './DeviceSelector';
import { RuleForm } from './RuleForm';
import { AlarmHistory } from './AlarmHistory';
import { RadarCalibrationWizard } from './RadarCalibrationWizard';

export const AssemblyDetail: React.FC = () => {
  const { assemblyId } = useParams<{ assemblyId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useAppContext();
  const [assembly, setAssembly] = useState<Assembly | null>(null);
  const [devices, setDevices] = useState<AssemblyDevice[]>([]);
  const [rules, setRules] = useState<AlarmRule[]>([]);
  const [allDevices, setAllDevices] = useState<Device[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'devices' | 'rules' | 'history'>('devices');
  const [showAddDeviceModal, setShowAddDeviceModal] = useState(false);
  const [showCreateRuleModal, setShowCreateRuleModal] = useState(false);
  const [selectedDeviceIds, setSelectedDeviceIds] = useState<string[]>([]);
  const [selectedRole, setSelectedRole] = useState<DeviceRole | ''>('');
  const [selectedRule, setSelectedRule] = useState<AlarmRule | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [showCalibrationWizard, setShowCalibrationWizard] = useState(false);
  const [calibrationInfo, setCalibrationInfo] = useState<{ rotation: { x: number; y: number; z: number }; translation: { x: number; y: number; z: number }; scale: number; zoneName?: string } | null>(null);
  const modal = useModal();

  useEffect(() => {
    if (assemblyId) {
      loadAssembly();
    }
  }, [assemblyId]);

  // 从装置列表点击「坐标系标定」进入时自动打开标定向导
  useEffect(() => {
    const state = location.state as { openCalibration?: boolean } | null;
    if (state?.openCalibration && assemblyId) {
      setShowCalibrationWizard(true);
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [assemblyId, location.state, location.pathname, navigate]);

  const loadAssembly = async () => {
    if (!assemblyId) return;
    setIsLoading(true);
    try {
      const [assemblyRes, devicesRes, rulesRes, allDevicesRes, radarDevicesRes] = await Promise.all([
        assemblyService.getAssembly(assemblyId),
        assemblyService.getAssemblyDevices(assemblyId),
        alarmRuleService.getAssemblyRules(assemblyId),
        deviceService.getDevices(),
        radarService.getRadarDevices().catch(() => ({ data: [] })),
      ]);
      const mainDevices: Device[] = allDevicesRes.data || [];
      const radarList = Array.isArray(radarDevicesRes?.data) ? radarDevicesRes.data : [];
      const existingIds = new Set(mainDevices.map((d) => d.id));
      const radarAsDevices: Device[] = radarList
        .filter((r: any) => r.deviceId && !existingIds.has(r.deviceId))
        .map((r: any) => ({
          id: r.deviceId,
          name: r.radarName || r.deviceId,
          ip: r.radarIp || '',
          port: 0,
          brand: 'radar',
          model: '',
          status: r.status === 1 ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE,
          lastSeen: '',
        }));
      setAssembly(assemblyRes.data);
      const devs: AssemblyDevice[] = devicesRes.data || [];
      setDevices(devs);
      setRules(rulesRes.data || []);
      setAllDevices([...mainDevices, ...radarAsDevices]);

      // 加载标定参数：找到装置内的雷达设备，查询其防区的 coordinateTransform
      const radarDev = devs.find((d) => String((d as any).deviceRole || d.role || '').toLowerCase() === 'radar');
      if (radarDev && assemblyRes.data?.ptzLinkageEnabled) {
        try {
          const zonesRes = await radarService.getZones(radarDev.deviceId);
          const zones = zonesRes?.data || [];
          for (const z of zones) {
            const ct = z.coordinateTransform;
            if (ct && z.enabled) {
              const parsed = typeof ct === 'string' ? JSON.parse(ct) : ct;
              if (parsed?.rotation) {
                setCalibrationInfo({ ...parsed, zoneName: z.name });
                break;
              }
            }
          }
        } catch { /* ignore */ }
      }
    } catch (err: any) {
      console.error('加载装置详情失败:', err);
      modal.showModal({
        message: err.message || '加载装置详情失败',
        type: 'error',
      });
    } finally {
      setIsLoading(false);
    }
  };

  const handleAddDevice = async () => {
    if (selectedDeviceIds.length === 0 || !selectedRole || !assemblyId) {
      modal.showModal({
        message: '请选择设备和角色',
        type: 'warning',
      });
      return;
    }

    setIsSaving(true);
    try {
      for (const deviceId of selectedDeviceIds) {
        await assemblyService.addDeviceToAssembly(assemblyId, deviceId, selectedRole);
      }
      modal.showModal({
        message: '添加成功',
        type: 'success',
      });
      setShowAddDeviceModal(false);
      setSelectedDeviceIds([]);
      setSelectedRole('');
      await loadAssembly();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '添加失败',
        type: 'error',
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleRemoveDevice = async (deviceId: string) => {
    if (!assemblyId) return;
    try {
      await assemblyService.removeDeviceFromAssembly(assemblyId, deviceId);
      modal.showModal({
        message: '移除成功',
        type: 'success',
      });
      await loadAssembly();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '移除失败',
        type: 'error',
      });
    }
  };

  const handleSaveRule = async (ruleData: Partial<AlarmRule>) => {
    if (!assemblyId) return;
    setIsSaving(true);
    try {
      const ruleToSave = {
        ...ruleData,
        scope: 'assembly' as const,
        assemblyId,
      };
      if (selectedRule) {
        await alarmRuleService.updateAlarmRule(selectedRule.ruleId || selectedRule.id, ruleToSave);
        modal.showModal({
          message: '更新成功',
          type: 'success',
        });
      } else {
        await alarmRuleService.createAlarmRule(ruleToSave);
        modal.showModal({
          message: '创建成功',
          type: 'success',
        });
      }
      setShowCreateRuleModal(false);
      setSelectedRule(null);
      await loadAssembly();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '保存失败',
        type: 'error',
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteRule = async (rule: AlarmRule) => {
    try {
      await alarmRuleService.deleteAlarmRule(rule.ruleId || rule.id);
      modal.showModal({
        message: '删除成功',
        type: 'success',
      });
      await loadAssembly();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '删除失败',
        type: 'error',
      });
    }
  };

  const handleToggleRule = async (rule: AlarmRule) => {
    try {
      await alarmRuleService.toggleRule(rule.ruleId || rule.id, !rule.enabled);
      await loadAssembly();
    } catch (err: any) {
      modal.showModal({
        message: err.message || '切换状态失败',
        type: 'error',
      });
    }
  };

  const getRoleLabel = (role: DeviceRole) => {
    switch (role) {
      case DeviceRole.LEFT_CAMERA:
        return t('left_camera');
      case DeviceRole.RIGHT_CAMERA:
        return t('right_camera');
      case DeviceRole.TOP_CAMERA:
        return t('top_camera');
      case DeviceRole.SPEAKER:
        return t('speaker');
      case DeviceRole.RADAR:
        return t('radar');
      default:
        return role;
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  if (!assembly) {
    return (
      <div className="flex flex-col items-center justify-center h-64">
        <p className="text-gray-500 mb-4">装置不存在</p>
        <button
          onClick={() => navigate('/assemblies')}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          返回装置列表
        </button>
      </div>
    );
  }

  const excludeDeviceIds = devices.map((d) => d.deviceId);

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

      <div className="space-y-5">
        {/* 头部信息卡片 */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
          <div className="px-6 py-5">
            <div className="flex items-start justify-between">
              <div className="flex items-start gap-4">
                <button
                  onClick={() => navigate('/assemblies')}
                  className="mt-1 p-2 rounded-xl border border-gray-200 hover:bg-gray-50 transition-colors text-gray-500 hover:text-gray-700"
                >
                  <ArrowLeft size={18} />
                </button>
                <div>
                  <div className="flex items-center gap-3 mb-1">
                    <h2 className="text-xl font-bold text-gray-800">{assembly.name}</h2>
                    <span
                      className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                        assembly.status === 'active'
                          ? 'bg-green-50 text-green-600 ring-1 ring-green-200'
                          : 'bg-gray-50 text-gray-500 ring-1 ring-gray-200'
                      }`}
                    >
                      {assembly.status === 'active' ? t('active') : t('inactive')}
                    </span>
                  </div>
                  {assembly.location && (
                    <div className="text-sm text-gray-400 flex items-center gap-1 mb-2">
                      <MapPin size={13} /> {assembly.location}
                    </div>
                  )}
                  <div className="flex items-center gap-3 flex-wrap">
                    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium ${
                      assembly.ptzLinkageEnabled
                        ? 'bg-blue-50 text-blue-600 ring-1 ring-blue-100'
                        : 'bg-gray-50 text-gray-500 ring-1 ring-gray-200'
                    }`}>
                      <Camera size={12} />
                      PTZ 联动{assembly.ptzLinkageEnabled ? '已开启' : '未开启'}
                    </span>
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium bg-gray-50 text-gray-500 ring-1 ring-gray-200">
                      <Package size={12} />
                      {devices.length} 个设备
                    </span>
                    {assembly.ptzLinkageEnabled &&
                      devices.some((d) => String((d as any).deviceRole || d.role || '').toLowerCase() === 'radar') &&
                      devices.some((d) => {
                        const r = String((d as any).deviceRole || d.role || '').toLowerCase();
                        return r.includes('camera') || r === 'left_camera' || r === 'right_camera' || r === 'top_camera';
                      }) && (
                        <button
                          onClick={() => setShowCalibrationWizard(true)}
                          className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-semibold
                                     bg-gradient-to-r from-blue-500 to-indigo-500 text-white
                                     hover:from-blue-600 hover:to-indigo-600 shadow-sm transition-all"
                        >
                          <Crosshair size={12} />
                          坐标系标定
                          <ChevronRight size={12} />
                        </button>
                      )}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* 坐标系标定参数 */}
          {calibrationInfo && assembly.ptzLinkageEnabled && (
            <div className="px-6 pb-5 -mt-1">
              <div className="flex items-start gap-3 p-3 rounded-xl bg-indigo-50/60 border border-indigo-100">
                <Crosshair size={14} className="text-indigo-500 mt-0.5 shrink-0" />
                <div className="flex-1 min-w-0">
                  <div className="text-xs font-semibold text-indigo-600 mb-1.5">
                    坐标系标定参数{calibrationInfo.zoneName ? ` · ${calibrationInfo.zoneName}` : ''}
                  </div>
                  <div className="flex flex-wrap gap-x-5 gap-y-1 text-[11px] font-mono text-indigo-700/80">
                    <span>旋转 X: <strong>{calibrationInfo.rotation.x.toFixed(2)}°</strong></span>
                    <span>旋转 Y: <strong>{calibrationInfo.rotation.y.toFixed(2)}°</strong></span>
                    <span>旋转 Z: <strong>{calibrationInfo.rotation.z.toFixed(2)}°</strong></span>
                    <span>平移 ({calibrationInfo.translation.x}, {calibrationInfo.translation.y}, {calibrationInfo.translation.z})</span>
                    <span>缩放: {calibrationInfo.scale}</span>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* 标签页 */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
          <div className="border-b border-gray-100 px-5">
            <div className="flex gap-1">
              {[
                { key: 'devices' as const, icon: <Package size={15} />, label: t('assembly_devices'), count: devices.length },
                { key: 'rules' as const, icon: <Bell size={15} />, label: t('alarm_rules'), count: rules.length },
                { key: 'history' as const, icon: <History size={15} />, label: t('alarm_history') },
              ].map((tab) => (
                <button
                  key={tab.key}
                  onClick={() => setActiveTab(tab.key)}
                  className={`flex items-center gap-1.5 px-4 py-3 text-sm font-medium transition-colors border-b-2 ${
                    activeTab === tab.key
                      ? 'border-blue-600 text-blue-600'
                      : 'border-transparent text-gray-400 hover:text-gray-600'
                  }`}
                >
                  {tab.icon}
                  {tab.label}
                  {tab.count !== undefined && (
                    <span className={`ml-0.5 px-1.5 py-0.5 rounded-full text-[10px] font-semibold ${
                      activeTab === tab.key ? 'bg-blue-100 text-blue-600' : 'bg-gray-100 text-gray-400'
                    }`}>
                      {tab.count}
                    </span>
                  )}
                </button>
              ))}
            </div>
          </div>

          <div className="p-5">
            {/* 设备列表标签页 */}
            {activeTab === 'devices' && (
              <div>
                <div className="flex justify-between items-center mb-4">
                  <div>
                    <h3 className="font-semibold text-gray-800">{t('assembly_devices')}</h3>
                    <p className="text-xs text-gray-400 mt-0.5">管理装置关联的设备及其角色</p>
                  </div>
                  <button
                    onClick={() => setShowAddDeviceModal(true)}
                    className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors text-sm font-medium shadow-sm"
                  >
                    <Plus size={15} />
                    {t('add_device_to_assembly')}
                  </button>
                </div>
                {devices.length === 0 ? (
                  <div className="text-center py-12 text-gray-400">
                    <Package className="mx-auto mb-3 text-gray-200" size={40} />
                    <p className="text-sm">暂无关联设备</p>
                    <p className="text-xs text-gray-300 mt-1">点击右上角添加设备到此装置</p>
                  </div>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {devices.map((device) => {
                      const deviceInfo = allDevices.find((d) => d.id === device.deviceId);
                      const role = String((device as any).deviceRole || device.role || '').toLowerCase();
                      const isRadar = role === 'radar';
                      const isCamera = role.includes('camera');
                      const isSpeaker = role === 'speaker';
                      const roleIcon = isRadar ? <Radio size={16} /> : isCamera ? <Camera size={16} /> : isSpeaker ? <Volume2 size={16} /> : <Shield size={16} />;
                      const roleColor = isRadar ? 'text-purple-500 bg-purple-50 ring-purple-100' : isCamera ? 'text-blue-500 bg-blue-50 ring-blue-100' : isSpeaker ? 'text-amber-500 bg-amber-50 ring-amber-100' : 'text-gray-500 bg-gray-50 ring-gray-200';
                      const isOnline = deviceInfo?.status === DeviceStatus.ONLINE;

                      return (
                        <div
                          key={device.deviceId}
                          className="flex items-center gap-3 p-3.5 rounded-xl border border-gray-100 hover:border-gray-200 hover:shadow-sm transition-all group"
                        >
                          <div className={`w-10 h-10 rounded-xl flex items-center justify-center ring-1 shrink-0 ${roleColor}`}>
                            {roleIcon}
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2">
                              <span className="font-medium text-gray-800 text-sm truncate">
                                {device.deviceName || deviceInfo?.name || device.deviceId}
                              </span>
                              <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${isOnline ? 'bg-green-400' : 'bg-gray-300'}`} />
                            </div>
                            <div className="flex items-center gap-2 mt-0.5">
                              <span className="text-xs text-gray-400">
                                {deviceInfo ? `${deviceInfo.ip}:${deviceInfo.port}` : device.deviceId}
                              </span>
                              <span className="text-[10px] text-gray-400">·</span>
                              <span className={`text-xs font-medium ${isRadar ? 'text-purple-500' : isCamera ? 'text-blue-500' : isSpeaker ? 'text-amber-500' : 'text-gray-500'}`}>
                                {getRoleLabel((device as any).deviceRole || device.role)}
                              </span>
                            </div>
                          </div>
                          <button
                            onClick={() => handleRemoveDevice(device.deviceId)}
                            className="p-1.5 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
                            title={t('remove_device')}
                          >
                            <Trash2 size={15} />
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            )}

            {/* 规则标签页 */}
            {activeTab === 'rules' && (
              <div>
                <div className="flex justify-between items-center mb-4">
                  <div>
                    <h3 className="font-semibold text-gray-800">{t('alarm_rules')}</h3>
                    <p className="text-xs text-gray-400 mt-0.5">配置装置的报警触发规则</p>
                  </div>
                  <button
                    onClick={() => {
                      setSelectedRule(null);
                      setShowCreateRuleModal(true);
                    }}
                    className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors text-sm font-medium shadow-sm"
                  >
                    <Plus size={15} />
                    {t('create_rule')}
                  </button>
                </div>
                {rules.length === 0 ? (
                  <div className="text-center py-12 text-gray-400">
                    <Bell className="mx-auto mb-3 text-gray-200" size={40} />
                    <p className="text-sm">暂无报警规则</p>
                    <p className="text-xs text-gray-300 mt-1">创建规则来定义报警触发条件</p>
                  </div>
                ) : (
                  <div className="space-y-2.5">
                    {rules.map((rule) => (
                      <div
                        key={rule.id}
                        className="flex items-center justify-between p-3.5 rounded-xl border border-gray-100 hover:border-gray-200 hover:shadow-sm transition-all group"
                      >
                        <div className="flex items-center gap-3 flex-1 min-w-0">
                          <div className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${
                            rule.enabled ? 'bg-green-50 text-green-500 ring-1 ring-green-100' : 'bg-gray-50 text-gray-400 ring-1 ring-gray-200'
                          }`}>
                            <Bell size={15} />
                          </div>
                          <div className="min-w-0">
                            <div className="font-medium text-gray-800 text-sm truncate">{rule.name}</div>
                            <div className="text-xs text-gray-400 mt-0.5">
                              {rule.scope === 'assembly' ? t('assembly_rule') : rule.scope}
                            </div>
                          </div>
                        </div>
                        <div className="flex items-center gap-1.5">
                          <button
                            onClick={() => handleToggleRule(rule)}
                            className={`px-2.5 py-1 rounded-full text-xs font-medium transition-colors ${
                              rule.enabled
                                ? 'bg-green-50 text-green-600 hover:bg-green-100'
                                : 'bg-gray-100 text-gray-400 hover:bg-gray-200'
                            }`}
                          >
                            {rule.enabled ? t('enabled') : t('disabled')}
                          </button>
                          <button
                            onClick={() => { setSelectedRule(rule); setShowCreateRuleModal(true); }}
                            className="p-1.5 text-gray-300 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
                          >
                            <Edit2 size={15} />
                          </button>
                          <button
                            onClick={() => handleDeleteRule(rule)}
                            className="p-1.5 text-gray-300 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
                          >
                            <Trash2 size={15} />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 报警历史标签页 */}
            {activeTab === 'history' && <AlarmHistory assemblyId={assemblyId} />}
          </div>
        </div>
      </div>

      {/* 添加设备弹窗 */}
      {showAddDeviceModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div
            className="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
            onClick={() => setShowAddDeviceModal(false)}
          ></div>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl z-10 overflow-hidden max-h-[90vh] flex flex-col">
            <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
              <div>
                <h3 className="text-lg font-bold text-gray-800">{t('add_device_to_assembly')}</h3>
                <p className="text-xs text-gray-400 mt-0.5">选择设备并指定在装置中的角色</p>
              </div>
              <button
                onClick={() => setShowAddDeviceModal(false)}
                className="p-1.5 rounded-lg hover:bg-gray-100 transition-colors text-gray-400 hover:text-gray-600"
              >
                <X size={18} />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-6 space-y-5">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">选择设备</label>
                <DeviceSelector
                  devices={allDevices}
                  selected={selectedDeviceIds}
                  onChange={setSelectedDeviceIds}
                  multiSelect={true}
                  excludeDeviceIds={excludeDeviceIds}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2.5">{t('device_role')}</label>
                <div className="grid grid-cols-3 gap-2">
                  {([
                    { value: DeviceRole.LEFT_CAMERA, label: t('left_camera'), icon: <Camera size={18} />, color: 'blue' },
                    { value: DeviceRole.RIGHT_CAMERA, label: t('right_camera'), icon: <Camera size={18} />, color: 'cyan' },
                    { value: DeviceRole.TOP_CAMERA, label: t('top_camera'), icon: <Camera size={18} />, color: 'indigo' },
                    { value: DeviceRole.SPEAKER, label: t('speaker'), icon: <Volume2 size={18} />, color: 'amber' },
                    { value: DeviceRole.RADAR, label: t('radar'), icon: <Radio size={18} />, color: 'purple' },
                  ] as const).map((item) => {
                    const selected = selectedRole === item.value;
                    const colorMap: Record<string, { bg: string; ring: string; text: string; icon: string }> = {
                      blue:   { bg: 'bg-blue-50',   ring: 'ring-blue-400',   text: 'text-blue-700',   icon: 'text-blue-500' },
                      cyan:   { bg: 'bg-cyan-50',   ring: 'ring-cyan-400',   text: 'text-cyan-700',   icon: 'text-cyan-500' },
                      indigo: { bg: 'bg-indigo-50', ring: 'ring-indigo-400', text: 'text-indigo-700', icon: 'text-indigo-500' },
                      amber:  { bg: 'bg-amber-50',  ring: 'ring-amber-400',  text: 'text-amber-700',  icon: 'text-amber-500' },
                      purple: { bg: 'bg-purple-50', ring: 'ring-purple-400', text: 'text-purple-700', icon: 'text-purple-500' },
                    };
                    const c = colorMap[item.color];
                    return (
                      <button
                        key={item.value}
                        type="button"
                        onClick={() => setSelectedRole(item.value)}
                        className={`flex flex-col items-center gap-1.5 px-3 py-3 rounded-xl border-2 transition-all text-center ${
                          selected
                            ? `${c.bg} border-transparent ring-2 ${c.ring} ${c.text}`
                            : 'border-gray-100 hover:border-gray-200 hover:bg-gray-50 text-gray-500'
                        }`}
                      >
                        <span className={selected ? c.icon : 'text-gray-400'}>{item.icon}</span>
                        <span className="text-xs font-medium">{item.label}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
            <div className="px-6 py-4 bg-gray-50/80 border-t border-gray-100 flex items-center justify-between">
              <span className="text-xs text-gray-400">
                {selectedDeviceIds.length > 0 ? `已选 ${selectedDeviceIds.length} 个设备` : '未选择设备'}
                {selectedRole ? ` · ${getRoleLabel(selectedRole as DeviceRole)}` : ''}
              </span>
              <div className="flex gap-2">
                <button
                  onClick={() => setShowAddDeviceModal(false)}
                  className="px-4 py-2 text-gray-500 hover:bg-gray-100 rounded-xl transition-colors text-sm font-medium"
                >
                  {t('cancel')}
                </button>
                <button
                  onClick={handleAddDevice}
                  disabled={isSaving || selectedDeviceIds.length === 0 || !selectedRole}
                  className="px-5 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors text-sm font-medium shadow-sm disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1.5"
                >
                  {isSaving && (
                    <div className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                  )}
                  确认添加
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 创建/编辑规则弹窗 */}
      {showCreateRuleModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div
            className="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
            onClick={() => {
              setShowCreateRuleModal(false);
              setSelectedRule(null);
            }}
          ></div>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl z-10 overflow-hidden max-h-[90vh] flex flex-col">
            <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
              <h3 className="text-lg font-bold text-gray-800">
                {selectedRule ? t('edit_device') : t('create_rule')}
              </h3>
              <button
                onClick={() => {
                  setShowCreateRuleModal(false);
                  setSelectedRule(null);
                }}
                className="p-1 rounded-full hover:bg-gray-200 transition-colors text-gray-500"
              >
                <X size={20} />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-6">
              <RuleForm
                rule={selectedRule || undefined}
                onSave={handleSaveRule}
                onCancel={() => {
                  setShowCreateRuleModal(false);
                  setSelectedRule(null);
                }}
              />
            </div>
          </div>
        </div>
      )}

      {showCalibrationWizard && assemblyId && (
        <RadarCalibrationWizard
          assemblyId={assemblyId}
          onClose={() => setShowCalibrationWizard(false)}
        />
      )}
    </>
  );
};
