import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { flowService, eventTypeService } from '../src/api/services';
import { AlarmFlow, CanvasConnection, CanvasNode, FlowComponentDefinition, FlowNodeType, CanonicalEvent } from '../types';
import { useAppContext } from '../contexts/AppContext';
import {
  Plus, Trash2, RefreshCw, Save, X, Download, Upload, ZoomIn, ZoomOut,
  Play, Square, GitBranch, Camera, Video, Radio, Cloud, Volume2, Send, Zap, Settings, Move,
  Maximize2, Minimize2, ChevronDown, ChevronRight, Check, Clock, Power, MapPin, Globe, Brain,
  LayoutGrid, ShieldCheck, MessageSquare, Mic, Megaphone, ImagePlus
} from 'lucide-react';

// 内置组件定义
const FLOW_COMPONENTS: FlowComponentDefinition[] = [
  { type: 'event_trigger', label: 'node_event_trigger', icon: 'Zap', category: 'trigger', defaultConfig: { debounceSeconds: 5 } },
  { type: 'mqtt_subscribe', label: 'node_mqtt_subscribe', icon: 'Radio', category: 'trigger', defaultConfig: { topic: 'senhub/custom/command', qos: 1 } },
  { type: 'condition', label: 'node_condition', icon: 'GitBranch', category: 'logic', hasConditionPorts: true, defaultConfig: { field: 'event_key', operator: 'eq', value: '' } },
  { type: 'delay', label: 'node_delay', icon: 'Clock', category: 'logic', defaultConfig: { seconds: 5 } },
  { type: 'capture', label: 'node_capture', icon: 'Camera', category: 'action', defaultConfig: { channel: 1 } },
  { type: 'record', label: 'node_record', icon: 'Video', category: 'action', defaultConfig: { channel: 1, beforeSeconds: 15, afterSeconds: 15 } },
  { type: 'ptz_control', label: 'node_ptz_control', icon: 'Move', category: 'action', defaultConfig: { preset: 1 } },
  { type: 'ptz_goto', label: 'node_ptz_goto', icon: 'MapPin', category: 'action', defaultConfig: { deviceId: '{deviceId}', channel: 1 } },
  { type: 'device_reboot', label: 'node_device_reboot', icon: 'Power', category: 'action', defaultConfig: { deviceId: '{deviceId}' } },
  { type: 'radar_zone_toggle', label: 'node_radar_zone_toggle', icon: 'MapPin', category: 'action', defaultConfig: { zoneId: '', enabled: true } },
  { type: 'mqtt_publish', label: 'node_mqtt_publish', icon: 'Radio', category: 'output', defaultConfig: { topic: 'alarm/report/{deviceId}' } },
  { type: 'oss_upload', label: 'node_oss_upload', icon: 'Cloud', category: 'output', defaultConfig: { path: 'alarm/{deviceId}/{fileName}' } },
  { type: 'speaker_play', label: 'node_speaker_play', icon: 'Volume2', category: 'output', defaultConfig: { audioFile: '' } },
  { type: 'system_speaker', label: 'node_system_speaker', icon: 'Megaphone', category: 'output', defaultConfig: { audioPath: '' } },
  { type: 'webhook', label: 'node_webhook', icon: 'Send', category: 'output', defaultConfig: { url: '', method: 'POST' } },
  { type: 'http_request', label: 'node_http_request', icon: 'Globe', category: 'output', defaultConfig: { url: '', method: 'POST' } },
  { type: 'ai_inference', label: 'node_ai_inference', icon: 'Brain', category: 'output', defaultConfig: { apiUrl: '', requestBody: '{"image_url":"{captureUrl}"}', outputVariablePrefix: 'ai_', timeoutSeconds: 30 } },
  { type: 'ai_verify', label: 'node_ai_verify', icon: 'ShieldCheck', category: 'logic', hasConditionPorts: true, defaultConfig: { model: '', prompt: '' } },
  { type: 'ai_alert_text', label: 'node_ai_alert_text', icon: 'MessageSquare', category: 'action', defaultConfig: { model: '', prompt: '' } },
  { type: 'ai_tts', label: 'node_ai_tts', icon: 'Mic', category: 'action', defaultConfig: { text: '{ai_alert_text}', voice: '' } },
  { type: 'end', label: 'node_end', icon: 'Square', category: 'logic', defaultConfig: {} },
];

const ICON_MAP: Record<string, React.FC<{ size?: number; className?: string }>> = {
  Zap, GitBranch, Camera, Video, Move, Radio, Cloud, Volume2, Send, Square, Settings, Play, Clock, Power, MapPin, Globe, Brain, ShieldCheck, MessageSquare, Mic, Megaphone
};

const CATEGORY_ORDER = ['trigger', 'logic', 'action', 'output'] as const;

const NODE_WIDTH = 160;
const NODE_HEIGHT = 72;
const PORT_RADIUS = 8;
const LAYOUT_GAP_X = 100;
const LAYOUT_GAP_Y = 36;
const LAYOUT_START_X = 48;
const LAYOUT_START_Y = 48;

// 生成唯一ID
const genId = () => `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
const genConnId = () => `conn_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

// 节点颜色
const getNodeColor = (type: FlowNodeType) => {
  switch (type) {
    case 'event_trigger': case 'mqtt_subscribe': return { bg: 'bg-amber-50', border: 'border-amber-400', text: 'text-amber-700' };
    case 'condition': case 'delay': case 'ai_verify': return { bg: 'bg-purple-50', border: 'border-purple-400', text: 'text-purple-700' };
    case 'capture': case 'record': case 'ptz_control': case 'ptz_goto': case 'device_reboot': case 'radar_zone_toggle': case 'ai_alert_text': case 'ai_tts': return { bg: 'bg-blue-50', border: 'border-blue-400', text: 'text-blue-700' };
    case 'mqtt_publish': case 'oss_upload': case 'speaker_play': case 'webhook': case 'http_request': case 'ai_inference': return { bg: 'bg-green-50', border: 'border-green-400', text: 'text-green-700' };
    case 'end': return { bg: 'bg-gray-50', border: 'border-gray-400', text: 'text-gray-700' };
    default: return { bg: 'bg-gray-50', border: 'border-gray-300', text: 'text-gray-700' };
  }
};

interface FlowFormState {
  flowId?: string;
  name: string;
  description?: string;
  flowType: string;
  isDefault: boolean;
  enabled: boolean;
}

const emptyForm = (): FlowFormState => ({
  name: '',
  description: '',
  flowType: 'alarm',
  isDefault: false,
  enabled: true,
});

export const FlowManagement: React.FC = () => {
  const { t } = useAppContext();

  // 流程列表状态
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [flows, setFlows] = useState<AlarmFlow[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [form, setForm] = useState<FlowFormState>(emptyForm());
  const [error, setError] = useState<string | null>(null);

  // 画布状态
  const [nodes, setNodes] = useState<CanvasNode[]>([]);
  const [connections, setConnections] = useState<CanvasConnection[]>([]);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedConnId, setSelectedConnId] = useState<string | null>(null);
  const [draggingNodeId, setDraggingNodeId] = useState<string | null>(null);
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });
  const [connecting, setConnecting] = useState<{ nodeId: string; port: 'default' | 'yes' | 'no' } | null>(null);
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 });
  const [scale, setScale] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isPanning, setIsPanning] = useState(false);
  const [panStart, setPanStart] = useState({ x: 0, y: 0 });

  // 导入导出模态框
  const [showImportModal, setShowImportModal] = useState(false);
  const [showExportModal, setShowExportModal] = useState(false);
  const [importJson, setImportJson] = useState('');

  // 事件触发器抽屉
  const [showEventTriggerDrawer, setShowEventTriggerDrawer] = useState(false);
  const [canonicalEvents, setCanonicalEvents] = useState<CanonicalEvent[]>([]);
  const [eventTypesLoading, setEventTypesLoading] = useState(false);
  const [expandedBrands, setExpandedBrands] = useState<Record<string, boolean>>({});
  const [eventBrandFilter, setEventBrandFilter] = useState<string | null>(null);

  // 全屏状态
  const [isFullscreen, setIsFullscreen] = useState(false);

  const canvasRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // 解析流程数据
  const parseFlow = (flow: any): AlarmFlow => ({
    ...flow,
    nodes: typeof flow.nodes === 'string' ? JSON.parse(flow.nodes || '[]') : (flow.nodes || []),
    connections: typeof flow.connections === 'string' ? JSON.parse(flow.connections || '[]') : (flow.connections || []),
  });

  // 加载流程列表
  const loadData = async () => {
    setLoading(true);
    try {
      const res = await flowService.listFlows();
      const data = (res.data || []).map(parseFlow);
      setFlows(data);
    } catch (e: any) {
      console.error(e);
      setError(e?.message || 'Failed to load flows');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    loadEventTypes();
  }, []);

  // 加载事件类型列表
  const loadEventTypes = async () => {
    setEventTypesLoading(true);
    try {
      const res = await eventTypeService.getEventTypes();
      if (res.data) {
        setCanonicalEvents(res.data.events || []);
        const grouped = res.data.grouped || {};
        const expanded: Record<string, boolean> = {};
        Object.keys(grouped).forEach(cat => { expanded[cat] = true; });
        setExpandedBrands(expanded);
      }
    } catch (e) {
      console.error('加载事件类型失败', e);
    } finally {
      setEventTypesLoading(false);
    }
  };

  // 将AlarmFlow转换为画布节点和连接
  const flowToCanvas = (flow: AlarmFlow) => {
    const canvasNodes: CanvasNode[] = (flow.nodes || []).map((node, idx) => {
      const comp = FLOW_COMPONENTS.find(c => c.type === node.type);
      // 尝试从config中获取位置，否则使用默认布局
      const x = node.config?.x ?? 100 + (idx % 4) * 200;
      const y = node.config?.y ?? 100 + Math.floor(idx / 4) * 120;
      return {
        id: node.nodeId,
        type: node.type as FlowNodeType,
        label: comp ? t(comp.label) : node.type,
        x,
        y,
        config: node.config,
      };
    });

    const canvasConns: CanvasConnection[] = (flow.connections || []).map((conn, idx) => {
      const fromNode = (flow.nodes || []).find((n: any) => n.nodeId === conn.from);
      const isConditionFrom = fromNode?.type === 'condition';
      const fromPort = isConditionFrom && (conn.condition === 'true' || conn.condition === 'false')
        ? (conn.condition === 'true' ? 'yes' : 'no')
        : (conn.fromPort || 'default');
      return {
        id: `conn_${idx}`,
        fromNodeId: conn.from,
        toNodeId: conn.to,
        fromPort: fromPort as 'default' | 'yes' | 'no',
        condition: conn.condition,
      };
    });

    setNodes(canvasNodes);
    setConnections(canvasConns);
  };

  // 将画布数据转换为AlarmFlow格式
  const canvasToFlow = (): { nodes: any[]; connections: any[] } => {
    const flowNodes = nodes.map(n => ({
      nodeId: n.id,
      type: n.type,
      config: { ...n.config, x: n.x, y: n.y },
    }));
    const flowConns = connections.map(c => {
      const fromNode = nodes.find(n => n.id === c.fromNodeId);
      const isConditionFrom = fromNode?.type === 'condition';
      const condition = isConditionFrom
        ? (c.fromPort === 'yes' ? 'true' : 'false')
        : c.condition;
      return {
        from: c.fromNodeId,
        to: c.toNodeId,
        fromPort: c.fromPort,
        condition,
      };
    });
    return { nodes: flowNodes, connections: flowConns };
  };

  // 打开创建
  const openCreate = () => {
    setSelected(null);
    setForm(emptyForm());
    setNodes([]);
    setConnections([]);
    setSelectedNodeId(null);
    setError(null);
  };

  // 打开编辑
  const openEdit = (flow: AlarmFlow) => {
    setSelected(flow.flowId);
    setForm({
      flowId: flow.flowId,
      name: flow.name,
      description: flow.description,
      flowType: flow.flowType || 'alarm',
      isDefault: !!flow.isDefault,
      enabled: flow.enabled !== false,
    });
    flowToCanvas(flow);
    setSelectedNodeId(null);
    setError(null);
  };

  // 删除流程
  const handleDelete = async (flowId: string) => {
    if (!window.confirm(t('confirm_delete_flow'))) return;
    try {
      await flowService.deleteFlow(flowId);
      if (selected === flowId) {
        openCreate();
      }
      await loadData();
    } catch (e: any) {
      setError(e?.message || 'Delete failed');
    }
  };

  // 保存流程
  const handleSubmit = async () => {
    try {
      setSaving(true);
      setError(null);
      const { nodes: flowNodes, connections: flowConns } = canvasToFlow();

      const payload: Partial<AlarmFlow> = {
        flowId: form.flowId,
        name: form.name,
        description: form.description,
        flowType: form.flowType,
        isDefault: form.isDefault,
        enabled: form.enabled,
        nodes: flowNodes,
        connections: flowConns,
      };

      if (form.flowId) {
        await flowService.updateFlow(form.flowId, payload);
      } else {
        await flowService.createFlow(payload);
      }
      await loadData();
    } catch (e: any) {
      setError(e?.message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  // 测试流程弹窗
  const [testingFlowId, setTestingFlowId] = useState<string | null>(null);
  const [showTestModal, setShowTestModal] = useState(false);
  const [testModalFlowId, setTestModalFlowId] = useState<string>('');
  const [testForm, setTestForm] = useState({
    eventName: '反光衣检测',
    alarmType: 'VEST_DETECTION',
    deviceId: 'test-camera-001',
    deviceIp: '192.168.1.100',
  });
  const [testImage, setTestImage] = useState<File | null>(null);
  const [testImagePreview, setTestImagePreview] = useState<string | null>(null);
  const testFileRef = useRef<HTMLInputElement>(null);

  const openTestModal = (flowId: string) => {
    setTestModalFlowId(flowId);
    setTestImage(null);
    setTestImagePreview(null);
    setShowTestModal(true);
  };

  const handleTestImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setTestImage(file);
      const reader = new FileReader();
      reader.onload = (ev) => setTestImagePreview(ev.target?.result as string);
      reader.readAsDataURL(file);
    }
  };

  const handleTestSubmit = async () => {
    if (testingFlowId) return;
    setTestingFlowId(testModalFlowId);
    setShowTestModal(false);
    try {
      const formData = new FormData();
      formData.append('eventName', testForm.eventName);
      formData.append('alarmType', testForm.alarmType);
      formData.append('deviceId', testForm.deviceId);
      formData.append('deviceIp', testForm.deviceIp);
      if (testImage) {
        formData.append('image', testImage);
      }

      const token = localStorage.getItem('nvr_auth_token');
      const { API_CONFIG } = await import('../src/api/config');
      const baseUrl = API_CONFIG.BASE_URL;
      const resp = await fetch(`${baseUrl}/flows/${encodeURIComponent(testModalFlowId)}/test`, {
        method: 'POST',
        headers: token ? { 'Authorization': `Bearer ${token}` } : {},
        body: formData,
      });
      const json = await resp.json();
      if (json.code === 0 || json.code === 200) {
        alert(json.data?.message || t('flow_test_success'));
      } else {
        alert(json.message || 'Test failed');
      }
    } catch (e: any) {
      alert(e?.message || 'Test failed');
    } finally {
      setTestingFlowId(null);
    }
  };

  // ========== 画布操作 ==========

  // 获取画布坐标
  const getCanvasCoords = useCallback((clientX: number, clientY: number) => {
    if (!canvasRef.current) return { x: 0, y: 0 };
    const rect = canvasRef.current.getBoundingClientRect();
    return {
      x: (clientX - rect.left - pan.x) / scale,
      y: (clientY - rect.top - pan.y) / scale,
    };
  }, [pan, scale]);

  // 拖拽组件到画布
  const handleDragStart = (e: React.DragEvent, comp: FlowComponentDefinition) => {
    e.dataTransfer.setData('component', JSON.stringify(comp));
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const data = e.dataTransfer.getData('component');
    if (!data) return;
    try {
      const comp: FlowComponentDefinition = JSON.parse(data);
      const coords = getCanvasCoords(e.clientX, e.clientY);
      const newNode: CanvasNode = {
        id: genId(),
        type: comp.type,
        label: t(comp.label),
        x: coords.x - NODE_WIDTH / 2,
        y: coords.y - NODE_HEIGHT / 2,
        config: { ...comp.defaultConfig },
      };
      setNodes(prev => [...prev, newNode]);
    } catch (err) {
      console.error('Drop error:', err);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
  };

  // 节点拖拽
  const handleNodeMouseDown = (e: React.MouseEvent, nodeId: string) => {
    if (e.button !== 0) return;
    e.stopPropagation();
    const node = nodes.find(n => n.id === nodeId);
    if (!node) return;
    const coords = getCanvasCoords(e.clientX, e.clientY);
    setDraggingNodeId(nodeId);
    setDragOffset({ x: coords.x - node.x, y: coords.y - node.y });
    setSelectedNodeId(nodeId);
    setSelectedConnId(null);
  };

  const handleCanvasMouseMove = useCallback((e: React.MouseEvent) => {
    const coords = getCanvasCoords(e.clientX, e.clientY);
    setMousePos(coords);

    if (draggingNodeId) {
      setNodes(prev => prev.map(n =>
        n.id === draggingNodeId
          ? { ...n, x: coords.x - dragOffset.x, y: coords.y - dragOffset.y }
          : n
      ));
    }

    if (isPanning) {
      setPan({
        x: e.clientX - panStart.x,
        y: e.clientY - panStart.y,
      });
    }
  }, [draggingNodeId, dragOffset, getCanvasCoords, isPanning, panStart]);

  const handleCanvasMouseUp = () => {
    setDraggingNodeId(null);
    setIsPanning(false);
    if (connecting) {
      setConnecting(null);
    }
  };

  // 画布平移
  const handleCanvasMouseDown = (e: React.MouseEvent) => {
    if (e.button === 1 || (e.button === 0 && e.altKey)) {
      setIsPanning(true);
      setPanStart({ x: e.clientX - pan.x, y: e.clientY - pan.y });
    } else if (e.target === canvasRef.current || e.target === svgRef.current) {
      setSelectedNodeId(null);
      setSelectedConnId(null);
    }
  };

  // 缩放 - 使用 useEffect 添加 non-passive 事件监听器
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const handleWheel = (e: WheelEvent) => {
      e.preventDefault();
      const delta = e.deltaY > 0 ? 0.9 : 1.1;
      setScale(s => Math.min(Math.max(s * delta, 0.3), 2));
    };

    canvas.addEventListener('wheel', handleWheel, { passive: false });
    return () => {
      canvas.removeEventListener('wheel', handleWheel);
    };
  }, []);

  // 连接线操作
  const handlePortMouseDown = (e: React.MouseEvent, nodeId: string, port: 'default' | 'yes' | 'no') => {
    e.stopPropagation();
    e.preventDefault();
    setConnecting({ nodeId, port });
  };

  // 完成连接（在目标节点或输入端口上释放鼠标）
  const handleConnectTo = (e: React.MouseEvent, nodeId: string) => {
    e.stopPropagation();
    e.preventDefault();
    if (connecting && connecting.nodeId !== nodeId) {
      // 创建连接
      const newConn: CanvasConnection = {
        id: genConnId(),
        fromNodeId: connecting.nodeId,
        toNodeId: nodeId,
        fromPort: connecting.port,
      };
      // 避免重复连接
      const exists = connections.some(
        c => c.fromNodeId === newConn.fromNodeId && c.toNodeId === newConn.toNodeId && c.fromPort === newConn.fromPort
      );
      if (!exists) {
        setConnections(prev => [...prev, newConn]);
      }
      setConnecting(null);
    }
  };

  const handleNodeClick = (e: React.MouseEvent, nodeId: string) => {
    e.stopPropagation();
    // 如果正在连接，完成连接
    if (connecting && connecting.nodeId !== nodeId) {
      handleConnectTo(e, nodeId);
    } else if (!connecting) {
      setSelectedNodeId(nodeId);
      setSelectedConnId(null);
    }
  };

  // 节点上的鼠标释放事件 - 用于完成连接
  const handleNodeMouseUp = (e: React.MouseEvent, nodeId: string) => {
    if (connecting && connecting.nodeId !== nodeId) {
      handleConnectTo(e, nodeId);
    }
  };

  // 删除节点
  const deleteNode = (nodeId: string) => {
    setNodes(prev => prev.filter(n => n.id !== nodeId));
    setConnections(prev => prev.filter(c => c.fromNodeId !== nodeId && c.toNodeId !== nodeId));
    setSelectedNodeId(null);
  };

  // 删除连接
  const deleteConnection = (connId: string) => {
    setConnections(prev => prev.filter(c => c.id !== connId));
    setSelectedConnId(null);
  };

  // 清空画布
  const clearCanvas = () => {
    if (window.confirm(t('confirm_clear_canvas'))) {
      setNodes([]);
      setConnections([]);
      setSelectedNodeId(null);
      setSelectedConnId(null);
    }
  };

  // 自适应视图
  const fitView = () => {
    if (nodes.length === 0) {
      setPan({ x: 0, y: 0 });
      setScale(1);
      return;
    }
    const minX = Math.min(...nodes.map(n => n.x));
    const maxX = Math.max(...nodes.map(n => n.x + NODE_WIDTH));
    const minY = Math.min(...nodes.map(n => n.y));
    const maxY = Math.max(...nodes.map(n => n.y + NODE_HEIGHT));
    const width = maxX - minX + 100;
    const height = maxY - minY + 100;

    if (canvasRef.current) {
      const rect = canvasRef.current.getBoundingClientRect();
      const scaleX = rect.width / width;
      const scaleY = rect.height / height;
      const newScale = Math.min(Math.max(Math.min(scaleX, scaleY), 0.3), 1.5);
      setScale(newScale);
      setPan({
        x: (rect.width - width * newScale) / 2 - minX * newScale + 50,
        y: (rect.height - height * newScale) / 2 - minY * newScale + 50,
      });
    }
  };

  // 按执行顺序自动规范排版（从左到右按连接层级排列）
  const autoLayout = useCallback(() => {
    if (nodes.length === 0) return;
    const nodeIds = new Set(nodes.map(n => n.id));
    const levelById: Record<string, number> = {};
    nodes.forEach(n => { levelById[n.id] = 0; });
    let changed = true;
    while (changed) {
      changed = false;
      for (const c of connections) {
        if (!nodeIds.has(c.fromNodeId) || !nodeIds.has(c.toNodeId)) continue;
        const nextLevel = levelById[c.fromNodeId] + 1;
        if (nextLevel > levelById[c.toNodeId]) {
          levelById[c.toNodeId] = nextLevel;
          changed = true;
        }
      }
    }
    const maxLevel = Math.max(0, ...Object.values(levelById));
    const byLevel: Record<number, string[]> = {};
    for (let l = 0; l <= maxLevel; l++) byLevel[l] = [];
    nodes.forEach(n => byLevel[levelById[n.id]].push(n.id));
    const newNodes = nodes.map(n => {
      const level = levelById[n.id];
      const indexInLevel = byLevel[level].indexOf(n.id);
      const x = LAYOUT_START_X + level * (NODE_WIDTH + LAYOUT_GAP_X);
      const y = LAYOUT_START_Y + indexInLevel * (NODE_HEIGHT + LAYOUT_GAP_Y);
      return { ...n, x, y };
    });
    setNodes(newNodes);
  }, [nodes, connections]);

  // 导入JSON
  const handleImport = () => {
    try {
      const data = JSON.parse(importJson);
      if (data.nodes && Array.isArray(data.nodes)) {
        const importedNodes: CanvasNode[] = data.nodes.map((n: any, idx: number) => {
          const comp = FLOW_COMPONENTS.find(c => c.type === n.type);
          return {
            id: n.nodeId || n.id || genId(),
            type: n.type,
            label: comp ? t(comp.label) : n.type,
            x: n.config?.x ?? n.x ?? 100 + (idx % 4) * 200,
            y: n.config?.y ?? n.y ?? 100 + Math.floor(idx / 4) * 120,
            config: n.config,
          };
        });
        const importedNodesForConn = (data.nodes || []).map((n: any) => ({ id: n.nodeId || n.id, type: n.type }));
        const importedConns: CanvasConnection[] = (data.connections || []).map((c: any, idx: number) => {
          const fromId = c.from || c.fromNodeId;
          const fromNode = importedNodesForConn.find((n: any) => n.id === fromId);
          const isConditionFrom = fromNode?.type === 'condition';
          const fromPort = isConditionFrom && (c.condition === 'true' || c.condition === 'false')
            ? (c.condition === 'true' ? 'yes' : 'no')
            : (c.fromPort || 'default');
          return {
            id: c.id || `conn_${idx}`,
            fromNodeId: fromId,
            toNodeId: c.to || c.toNodeId,
            fromPort: fromPort as 'default' | 'yes' | 'no',
            condition: c.condition,
          };
        });
        setNodes(importedNodes);
        setConnections(importedConns);
        setShowImportModal(false);
        setImportJson('');
        alert(t('import_success'));
      } else {
        alert(t('invalid_json'));
      }
    } catch {
      alert(t('invalid_json'));
    }
  };

  // 导出JSON
  const exportJson = useMemo(() => {
    const { nodes: flowNodes, connections: flowConns } = canvasToFlow();
    return JSON.stringify({ nodes: flowNodes, connections: flowConns }, null, 2);
  }, [nodes, connections]);

  // 复制到剪贴板
  const copyToClipboard = () => {
    navigator.clipboard.writeText(exportJson);
  };

  // 切换全屏
  const toggleFullscreen = () => {
    setIsFullscreen(prev => !prev);
  };

  // ESC 退出全屏
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isFullscreen) {
        setIsFullscreen(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isFullscreen]);

  // 下载JSON文件
  const downloadJson = () => {
    const blob = new Blob([exportJson], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${form.name || 'flow'}_${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // 获取节点端口位置
  const getPortPosition = (node: CanvasNode, port: 'input' | 'default' | 'yes' | 'no') => {
    const comp = FLOW_COMPONENTS.find(c => c.type === node.type);
    const hasConditionPorts = comp?.hasConditionPorts;

    switch (port) {
      case 'input':
        return { x: node.x, y: node.y + NODE_HEIGHT / 2 };
      case 'default':
        return { x: node.x + NODE_WIDTH, y: node.y + NODE_HEIGHT / 2 };
      case 'yes':
        return { x: node.x + NODE_WIDTH, y: node.y + NODE_HEIGHT / 3 };
      case 'no':
        return { x: node.x + NODE_WIDTH, y: node.y + (NODE_HEIGHT * 2) / 3 };
      default:
        return { x: node.x + NODE_WIDTH, y: node.y + NODE_HEIGHT / 2 };
    }
  };

  // 渲染连接线
  const renderConnection = (conn: CanvasConnection) => {
    const fromNode = nodes.find(n => n.id === conn.fromNodeId);
    const toNode = nodes.find(n => n.id === conn.toNodeId);
    if (!fromNode || !toNode) return null;

    const from = getPortPosition(fromNode, conn.fromPort);
    const to = getPortPosition(toNode, 'input');

    // 贝塞尔曲线控制点
    const dx = Math.abs(to.x - from.x);
    const cpOffset = Math.max(dx * 0.5, 50);

    const path = `M ${from.x} ${from.y} C ${from.x + cpOffset} ${from.y}, ${to.x - cpOffset} ${to.y}, ${to.x} ${to.y}`;

    const isSelected = selectedConnId === conn.id;
    const color = conn.fromPort === 'yes' ? '#22c55e' : conn.fromPort === 'no' ? '#ef4444' : '#6b7280';

    return (
      <g key={conn.id} onClick={(e) => { e.stopPropagation(); setSelectedConnId(conn.id); setSelectedNodeId(null); }}>
        <path
          d={path}
          stroke={isSelected ? '#3b82f6' : color}
          strokeWidth={isSelected ? 3 : 2}
          fill="none"
          className="cursor-pointer"
          markerEnd="url(#arrowhead)"
        />
        {conn.fromPort !== 'default' && (
          <text
            x={(from.x + to.x) / 2}
            y={(from.y + to.y) / 2 - 8}
            fontSize="12"
            fill={color}
            textAnchor="middle"
          >
            {conn.fromPort === 'yes' ? t('condition_yes') : t('condition_no')}
          </text>
        )}
      </g>
    );
  };

  // 渲染正在连接的线
  const renderConnectingLine = () => {
    if (!connecting) return null;
    const fromNode = nodes.find(n => n.id === connecting.nodeId);
    if (!fromNode) return null;

    const from = getPortPosition(fromNode, connecting.port);
    const to = mousePos;

    const dx = Math.abs(to.x - from.x);
    const cpOffset = Math.max(dx * 0.5, 50);

    const path = `M ${from.x} ${from.y} C ${from.x + cpOffset} ${from.y}, ${to.x - cpOffset} ${to.y}, ${to.x} ${to.y}`;

    return (
      <path
        d={path}
        stroke="#3b82f6"
        strokeWidth={2}
        strokeDasharray="5,5"
        fill="none"
        pointerEvents="none"
      />
    );
  };

  // 渲染节点（图标为主，悬停显示名称）
  const renderNode = (node: CanvasNode) => {
    const comp = FLOW_COMPONENTS.find(c => c.type === node.type);
    const colors = getNodeColor(node.type);
    const IconComp = comp ? ICON_MAP[comp.icon] : Settings;
    const isSelected = selectedNodeId === node.id;
    const hasConditionPorts = comp?.hasConditionPorts;

    return (
      <div
        key={node.id}
        title={node.label}
        className={`absolute rounded-xl border-2 shadow-md cursor-move select-none transition-all duration-200 hover:shadow-lg ${colors.bg} ${colors.border} ${isSelected ? 'ring-2 ring-blue-500 ring-offset-2 shadow-lg' : ''}`}
        style={{
          left: node.x,
          top: node.y,
          width: NODE_WIDTH,
          height: NODE_HEIGHT,
        }}
        onMouseDown={(e) => handleNodeMouseDown(e, node.id)}
        onMouseUp={(e) => handleNodeMouseUp(e, node.id)}
        onClick={(e) => handleNodeClick(e, node.id)}
      >
        {/* 输入端口 - 可以接收连接 */}
        {node.type !== 'event_trigger' && (
          <div
            className={`absolute w-4 h-4 rounded-full border-2 border-white shadow cursor-crosshair transition-colors ${connecting ? 'bg-blue-500 hover:bg-blue-600 scale-125' : 'bg-gray-400 hover:bg-blue-500'}`}
            style={{ left: -8, top: NODE_HEIGHT / 2 - 8 }}
            onMouseUp={(e) => handleConnectTo(e, node.id)}
            onClick={(e) => { e.stopPropagation(); if (connecting) handleConnectTo(e, node.id); }}
          />
        )}

        {/* 节点内容：仅图标，名称通过 title 悬停显示 */}
        <div className="flex items-center justify-center h-full px-2">
          <IconComp size={32} className={colors.text} strokeWidth={2} />
        </div>

        {/* 输出端口 */}
        {node.type !== 'end' && !hasConditionPorts && (
          <div
            className="absolute w-4 h-4 rounded-full bg-gray-600 border-2 border-white shadow cursor-crosshair hover:bg-blue-500"
            style={{ right: -8, top: NODE_HEIGHT / 2 - 8 }}
            onMouseDown={(e) => handlePortMouseDown(e, node.id, 'default')}
          />
        )}

        {/* 条件分支端口 (Yes/No) */}
        {hasConditionPorts && (
          <>
            <div
              className="absolute w-4 h-4 rounded-full bg-green-500 border-2 border-white shadow cursor-crosshair hover:bg-green-600"
              style={{ right: -8, top: NODE_HEIGHT / 3 - 8 }}
              onMouseDown={(e) => handlePortMouseDown(e, node.id, 'yes')}
              title={t('condition_yes')}
            />
            <div
              className="absolute w-4 h-4 rounded-full bg-red-500 border-2 border-white shadow cursor-crosshair hover:bg-red-600"
              style={{ right: -8, top: (NODE_HEIGHT * 2) / 3 - 8 }}
              onMouseDown={(e) => handlePortMouseDown(e, node.id, 'no')}
              title={t('condition_no')}
            />
          </>
        )}
      </div>
    );
  };

  // 选中节点的配置面板
  const selectedNode = nodes.find(n => n.id === selectedNodeId);

  const updateNodeConfig = (key: string, value: any) => {
    if (!selectedNodeId) return;
    setNodes(prev => prev.map(n =>
      n.id === selectedNodeId
        ? { ...n, config: { ...n.config, [key]: value } }
        : n
    ));
  };

  const renderNodeConfigPanel = () => {
    if (!selectedNode) return null;
    const comp = FLOW_COMPONENTS.find(c => c.type === selectedNode.type);

    return (
      <div className="absolute top-4 right-4 w-64 max-h-[85vh] bg-white rounded-lg shadow-lg border border-gray-200 p-3 z-20 flex flex-col">
        <div className="flex items-center justify-between gap-2 mb-3 flex-shrink-0">
          <h4 className="font-semibold text-gray-800 truncate min-w-0" title={selectedNode.label}>{selectedNode.label}</h4>
          <button
            onClick={() => deleteNode(selectedNode.id)}
            className="text-red-500 hover:text-red-700"
            title={t('delete_node')}
          >
            <Trash2 size={16} />
          </button>
        </div>

        <div className="space-y-2 text-sm overflow-y-auto flex-1 min-h-0">
          <div>
            <label className="block text-gray-600 mb-1">ID</label>
            <input
              value={selectedNode.id}
              disabled
              className="w-full px-2 py-1 rounded border border-gray-200 bg-gray-50 text-gray-500"
            />
          </div>

          <div>
            <label className="block text-gray-600 mb-1">Type</label>
            <input
              value={selectedNode.type}
              disabled
              className="w-full px-2 py-1 rounded border border-gray-200 bg-gray-50 text-gray-500"
            />
          </div>

          {selectedNode.type === 'mqtt_publish' && (
            <div>
              <label className="block text-gray-600 mb-1">{t('config_topic')}</label>
              <input
                value={selectedNode.config?.topic || ''}
                onChange={e => updateNodeConfig('topic', e.target.value)}
                className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
              />
            </div>
          )}

          {selectedNode.type === 'mqtt_subscribe' && (
            <div className="space-y-2">
              <div>
                <label className="block text-gray-600 mb-1">{t('config_topic')}</label>
                <input
                  value={selectedNode.config?.topic || ''}
                  onChange={e => updateNodeConfig('topic', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                  placeholder="senhub/custom/command"
                />
                <p className="text-xs text-gray-400 mt-1">消息到达该主题时触发本流程</p>
              </div>
              <div>
                <label className="block text-gray-600 mb-1">QoS</label>
                <input
                  type="number"
                  min="0"
                  max="2"
                  value={selectedNode.config?.qos ?? 1}
                  onChange={e => updateNodeConfig('qos', parseInt(e.target.value) || 0)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                />
              </div>
            </div>
          )}

          {selectedNode.type === 'oss_upload' && (
            <div>
              <label className="block text-gray-600 mb-1">{t('config_path')}</label>
              <input
                value={selectedNode.config?.path || ''}
                onChange={e => updateNodeConfig('path', e.target.value)}
                className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
              />
            </div>
          )}

          {selectedNode.type === 'speaker_play' && (
            <div>
              <label className="block text-gray-600 mb-1">{t('config_audio_file')}</label>
              <input
                value={selectedNode.config?.audioFile || ''}
                onChange={e => updateNodeConfig('audioFile', e.target.value)}
                className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
              />
            </div>
          )}

          {selectedNode.type === 'system_speaker' && (
            <div>
              <label className="block text-gray-600 mb-1">{t('config_audio_path')}</label>
              <input
                value={selectedNode.config?.audioPath || ''}
                onChange={e => updateNodeConfig('audioPath', e.target.value)}
                className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                placeholder={t('config_audio_path_hint')}
              />
              <p className="text-[10px] text-gray-400 mt-1">{t('config_system_speaker_hint')}</p>
            </div>
          )}

          {selectedNode.type === 'webhook' && (
            <div>
              <label className="block text-gray-600 mb-1">{t('config_webhook_url')}</label>
              <input
                value={selectedNode.config?.url || ''}
                onChange={e => updateNodeConfig('url', e.target.value)}
                className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                placeholder="https://qyapi.weixin.qq.com/..."
              />
            </div>
          )}

          {selectedNode.type === 'condition' && (
            <div className="space-y-2">
              <div>
                <label className="block text-gray-600 mb-1">{t('config_condition_field')}</label>
                <input
                  value={selectedNode.config?.field || 'event_key'}
                  onChange={e => updateNodeConfig('field', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                  placeholder="event_key, assemblyId, deviceId..."
                />
                <p className="text-xs text-gray-400 mt-1">{t('config_condition_field_hint')}</p>
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_condition_operator')}</label>
                <select
                  value={selectedNode.config?.operator || 'eq'}
                  onChange={e => updateNodeConfig('operator', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                >
                  <option value="eq">eq (等于)</option>
                  <option value="ne">ne (不等于)</option>
                  <option value="in">in (在列表中)</option>
                  <option value="not_in">not_in (不在列表中)</option>
                </select>
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_condition_value')}</label>
                <input
                  value={Array.isArray(selectedNode.config?.value) ? (selectedNode.config.value as string[]).join(',') : (selectedNode.config?.value ?? '')}
                  onChange={e => {
                    const v = e.target.value.trim();
                    if (selectedNode.config?.operator === 'in' || selectedNode.config?.operator === 'not_in') {
                      updateNodeConfig('value', v ? v.split(',').map(s => s.trim()) : []);
                    } else {
                      updateNodeConfig('value', v);
                    }
                  }}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                  placeholder="PERIMETER_INTRUSION 或 逗号分隔列表"
                />
              </div>
            </div>
          )}

          {selectedNode.type === 'delay' && (
            <div>
              <label className="block text-gray-600 mb-1">{t('config_delay_seconds')}</label>
              <input
                type="number"
                min="0"
                max="300"
                step="0.5"
                value={selectedNode.config?.seconds ?? 5}
                onChange={e => updateNodeConfig('seconds', parseFloat(e.target.value) || 0)}
                className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
              />
            </div>
          )}

          {selectedNode.type === 'ptz_goto' && (
            <div className="space-y-2">
              <div>
                <label className="block text-gray-600 mb-1">{t('config_device_id')}</label>
                <input
                  value={selectedNode.config?.deviceId ?? '{deviceId}'}
                  onChange={e => updateNodeConfig('deviceId', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                  placeholder="{deviceId}"
                />
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_channel')}</label>
                <input
                  type="number"
                  min="1"
                  value={selectedNode.config?.channel ?? 1}
                  onChange={e => updateNodeConfig('channel', parseInt(e.target.value) || 1)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_ptz_goto_preset')}</label>
                <input
                  type="number"
                  min="0"
                  value={selectedNode.config?.presetIndex ?? ''}
                  onChange={e => updateNodeConfig('presetIndex', e.target.value === '' ? undefined : parseInt(e.target.value))}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                  placeholder="留空则用角度"
                />
              </div>
              <div className="text-xs text-gray-500">或填写 pan / tilt / zoom 角度</div>
              <div className="grid grid-cols-3 gap-1">
                <div>
                  <label className="block text-gray-500 text-xs">pan</label>
                  <input
                    type="number"
                    value={selectedNode.config?.pan ?? ''}
                    onChange={e => updateNodeConfig('pan', e.target.value === '' ? undefined : parseFloat(e.target.value))}
                    className="w-full px-2 py-1 rounded border border-gray-200 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-gray-500 text-xs">tilt</label>
                  <input
                    type="number"
                    value={selectedNode.config?.tilt ?? ''}
                    onChange={e => updateNodeConfig('tilt', e.target.value === '' ? undefined : parseFloat(e.target.value))}
                    className="w-full px-2 py-1 rounded border border-gray-200 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-gray-500 text-xs">zoom</label>
                  <input
                    type="number"
                    step="0.1"
                    value={selectedNode.config?.zoom ?? ''}
                    onChange={e => updateNodeConfig('zoom', e.target.value === '' ? undefined : parseFloat(e.target.value))}
                    className="w-full px-2 py-1 rounded border border-gray-200 text-sm"
                  />
                </div>
              </div>
            </div>
          )}

          {selectedNode.type === 'device_reboot' && (
            <div>
              <label className="block text-gray-600 mb-1">{t('config_device_id')}</label>
              <input
                value={selectedNode.config?.deviceId ?? '{deviceId}'}
                onChange={e => updateNodeConfig('deviceId', e.target.value)}
                className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                placeholder="{deviceId}"
              />
            </div>
          )}

          {selectedNode.type === 'radar_zone_toggle' && (
            <div className="space-y-2">
              <div>
                <label className="block text-gray-600 mb-1">{t('config_radar_zone_id')}</label>
                <input
                  value={selectedNode.config?.zoneId ?? ''}
                  onChange={e => updateNodeConfig('zoneId', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                  placeholder="zone_xxx"
                />
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_radar_device_id')}</label>
                <input
                  value={selectedNode.config?.radarDeviceId ?? ''}
                  onChange={e => updateNodeConfig('radarDeviceId', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                  placeholder="可选，留空从防区解析"
                />
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="radar_zone_enabled"
                  checked={selectedNode.config?.enabled !== false}
                  onChange={e => updateNodeConfig('enabled', e.target.checked)}
                  className="rounded border-gray-300"
                />
                <label htmlFor="radar_zone_enabled" className="text-sm text-gray-600">{t('config_radar_zone_enabled')}</label>
              </div>
            </div>
          )}

          {selectedNode.type === 'http_request' && (
            <div className="space-y-2">
              <div>
                <label className="block text-gray-600 mb-1">{t('config_http_url')}</label>
                <input
                  value={selectedNode.config?.url ?? ''}
                  onChange={e => updateNodeConfig('url', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                  placeholder="https://..."
                />
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_http_method')}</label>
                <select
                  value={selectedNode.config?.method ?? 'POST'}
                  onChange={e => updateNodeConfig('method', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                >
                  <option value="GET">GET</option>
                  <option value="POST">POST</option>
                  <option value="PUT">PUT</option>
                  <option value="DELETE">DELETE</option>
                </select>
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_http_body')}</label>
                <textarea
                  value={selectedNode.config?.body ?? ''}
                  onChange={e => updateNodeConfig('body', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500 text-sm min-h-[60px]"
                  placeholder='{"key":"{deviceId}"}'
                />
              </div>
            </div>
          )}

          {selectedNode.type === 'ai_inference' && (
            <div className="space-y-2">
              <div>
                <label className="block text-gray-600 mb-1">{t('config_ai_api_url')}</label>
                <input
                  value={selectedNode.config?.apiUrl ?? ''}
                  onChange={e => updateNodeConfig('apiUrl', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                  placeholder="https://api.xxx/v1/vision"
                />
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_ai_request_body')}</label>
                <textarea
                  value={selectedNode.config?.requestBody ?? '{"image_url":"{captureUrl}"}'}
                  onChange={e => updateNodeConfig('requestBody', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500 text-sm min-h-[60px] font-mono"
                />
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_ai_output_prefix')}</label>
                <input
                  value={selectedNode.config?.outputVariablePrefix ?? 'ai_'}
                  onChange={e => updateNodeConfig('outputVariablePrefix', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_ai_timeout')}</label>
                <input
                  type="number"
                  min="5"
                  max="120"
                  value={selectedNode.config?.timeoutSeconds ?? 30}
                  onChange={e => updateNodeConfig('timeoutSeconds', parseInt(e.target.value) || 30)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                />
              </div>
            </div>
          )}

          {selectedNode.type === 'ai_verify' && (
            <div className="space-y-2">
              <div>
                <label className="block text-gray-600 mb-1">{t('config_ai_verify_model')}</label>
                <input
                  value={selectedNode.config?.model ?? ''}
                  onChange={e => updateNodeConfig('model', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                  placeholder="默认使用系统配置"
                />
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_ai_verify_prompt')}</label>
                <textarea
                  value={selectedNode.config?.prompt ?? ''}
                  onChange={e => updateNodeConfig('prompt', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500 text-sm min-h-[50px]"
                  placeholder="附加判定说明（可选）"
                />
              </div>
            </div>
          )}

          {selectedNode.type === 'ai_alert_text' && (
            <div className="space-y-2">
              <div>
                <label className="block text-gray-600 mb-1">{t('config_ai_alert_text_model')}</label>
                <input
                  value={selectedNode.config?.model ?? ''}
                  onChange={e => updateNodeConfig('model', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                  placeholder="默认使用系统配置"
                />
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_ai_alert_text_prompt')}</label>
                <textarea
                  value={selectedNode.config?.prompt ?? ''}
                  onChange={e => updateNodeConfig('prompt', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500 text-sm min-h-[50px]"
                  placeholder="附加生成要求（可选）"
                />
              </div>
            </div>
          )}

          {selectedNode.type === 'ai_tts' && (
            <div className="space-y-2">
              <div>
                <label className="block text-gray-600 mb-1">{t('config_ai_tts_text')}</label>
                <input
                  value={selectedNode.config?.text ?? '{ai_alert_text}'}
                  onChange={e => updateNodeConfig('text', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                  placeholder="{ai_alert_text}"
                />
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_ai_tts_voice')}</label>
                <select
                  value={selectedNode.config?.voice ?? ''}
                  onChange={e => updateNodeConfig('voice', e.target.value)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                >
                  <option value="">默认（系统配置）</option>
                  <option value="male-qn-qingse">male-qn-qingse</option>
                  <option value="female-shaonv">female-shaonv</option>
                  <option value="male-qn-jingying">male-qn-jingying</option>
                  <option value="female-yujie">female-yujie</option>
                </select>
              </div>
            </div>
          )}

          {selectedNode.type === 'event_trigger' && (
            <>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_debounce_seconds')}</label>
                <input
                  type="number"
                  min="0"
                  value={selectedNode.config?.debounceSeconds ?? 5}
                  onChange={e => updateNodeConfig('debounceSeconds', parseInt(e.target.value) || 0)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                />
                <p className="text-xs text-gray-400 mt-1">{t('config_debounce_hint')}</p>
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_alarm_types')}</label>
                <div className="text-xs text-gray-500 mb-1">
                  {t('selected')}: {(selectedNode.config?.alarmTypes || []).length === 0
                    ? t('all_types')
                    : (() => {
                        const keys: string[] = selectedNode.config?.alarmTypes || [];
                        const names = keys.slice(0, 3).map(k => {
                          const ev = canonicalEvents.find(e => e.eventKey === k);
                          return ev ? ev.nameZh : k;
                        });
                        return keys.length <= 3 ? names.join('、') : `${names.join('、')} 等${keys.length}个`;
                      })()}
                </div>
                <button
                  onClick={() => setShowEventTriggerDrawer(true)}
                  className="w-full px-3 py-2 text-sm bg-blue-50 text-blue-600 rounded border border-blue-200 hover:bg-blue-100 transition"
                >
                  {t('configure_event_types')}
                </button>
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_device_brands')}</label>
                <div className="text-xs text-gray-500 mb-1">
                  {t('selected')}: {(selectedNode.config?.deviceBrands || []).length === 0 
                    ? t('all_brands') 
                    : (selectedNode.config?.deviceBrands || []).join(', ')}
                </div>
              </div>
            </>
          )}

          {selectedNode.type === 'record' && (
            <>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_channel')}</label>
                <input
                  type="number"
                  min="1"
                  value={selectedNode.config?.channel ?? 1}
                  onChange={e => updateNodeConfig('channel', parseInt(e.target.value) || 1)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_before_seconds')}</label>
                <input
                  type="number"
                  min="0"
                  value={selectedNode.config?.beforeSeconds ?? 15}
                  onChange={e => updateNodeConfig('beforeSeconds', parseInt(e.target.value) || 0)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                />
                <p className="text-xs text-gray-400 mt-1">{t('config_before_seconds_hint')}</p>
              </div>
              <div>
                <label className="block text-gray-600 mb-1">{t('config_after_seconds')}</label>
                <input
                  type="number"
                  min="0"
                  value={selectedNode.config?.afterSeconds ?? 15}
                  onChange={e => updateNodeConfig('afterSeconds', parseInt(e.target.value) || 0)}
                  className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                />
                <p className="text-xs text-gray-400 mt-1">{t('config_after_seconds_hint')}</p>
              </div>
              <p className="text-xs text-gray-400 mt-2">{t('config_record_webhook_auto_hint')}</p>
            </>
          )}

          {selectedNode.type === 'capture' && (
            <div>
              <label className="block text-gray-600 mb-1">{t('config_channel')}</label>
              <input
                type="number"
                min="1"
                value={selectedNode.config?.channel ?? 1}
                onChange={e => updateNodeConfig('channel', parseInt(e.target.value) || 1)}
                className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
              />
            </div>
          )}

          {selectedNode.type === 'ptz_control' && (
            <div>
              <label className="block text-gray-600 mb-1">{t('config_ptz_preset')}</label>
              <input
                type="number"
                value={selectedNode.config?.preset || 1}
                onChange={e => updateNodeConfig('preset', parseInt(e.target.value))}
                className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
              />
            </div>
          )}
        </div>
      </div>
    );
  };

  // 渲染组件面板
  const groupedComponents = useMemo(() => {
    const groups: Record<string, FlowComponentDefinition[]> = {};
    CATEGORY_ORDER.forEach(cat => groups[cat] = []);
    FLOW_COMPONENTS.forEach(comp => {
      if (groups[comp.category]) {
        groups[comp.category].push(comp);
      }
    });
    return groups;
  }, []);

  const formValid = useMemo(() => form.name.trim().length > 0, [form.name]);

  const categoryStyle: Record<string, { border: string; bg: string; icon: string }> = {
    trigger: { border: 'border-l-amber-400', bg: 'bg-amber-50/50', icon: 'text-amber-600' },
    logic: { border: 'border-l-purple-400', bg: 'bg-purple-50/50', icon: 'text-purple-600' },
    action: { border: 'border-l-blue-400', bg: 'bg-blue-50/50', icon: 'text-blue-600' },
    output: { border: 'border-l-green-400', bg: 'bg-green-50/50', icon: 'text-green-600' },
  };

  return (
    <div 
      ref={containerRef}
      className={`flex min-h-0 ${isFullscreen ? 'fixed inset-0 z-50 bg-gray-50' : 'h-full w-full'}`}
      style={{ height: isFullscreen ? '100vh' : '100%', minHeight: isFullscreen ? undefined : 'calc(100vh - 4rem)' }}
    >
      {/* 左侧流程列表 - 收窄，全屏时隐藏 */}
      <div className={`w-52 flex-shrink-0 bg-white border-r border-gray-200 flex flex-col ${isFullscreen ? 'hidden' : ''}`}>
        <div className="p-2.5 border-b border-gray-100">
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-gray-800 text-sm">{t('flow_list')}</h3>
            <div className="flex space-x-0.5">
              <button onClick={loadData} className="p-1.5 text-gray-500 hover:text-gray-700 rounded-lg hover:bg-gray-100" title={t('refresh')}>
                <RefreshCw size={14} />
              </button>
              <button onClick={openCreate} className="p-1.5 text-blue-500 hover:text-blue-700 rounded-lg hover:bg-blue-50" title={t('create_flow')}>
                <Plus size={14} />
              </button>
            </div>
          </div>
        </div>

        <div className="flex-1 overflow-auto p-2 space-y-2">
          {loading ? (
            <div className="text-center text-gray-500 py-6 text-sm">{t('loading')}</div>
          ) : flows.length === 0 ? (
            <div className="text-center text-gray-500 py-6 text-sm">{t('no_data')}</div>
          ) : (
            flows.map(flow => (
              <div
                key={flow.flowId}
                onClick={() => openEdit(flow)}
                className={`rounded-xl border cursor-pointer transition-all duration-200 overflow-hidden ${
                  selected === flow.flowId
                    ? 'border-blue-400 bg-gradient-to-br from-blue-50 to-white shadow-md shadow-blue-100/50 ring-1 ring-blue-200/60'
                    : 'border-gray-200 bg-white hover:border-blue-200 hover:bg-gray-50/80 hover:shadow-sm'
                }`}
              >
                <div className="p-2.5">
                  <div className="flex items-start justify-between gap-2">
                    <div className="font-semibold text-gray-800 text-sm truncate min-w-0 leading-tight">{flow.name}</div>
                    <span className={`flex-shrink-0 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide ${flow.enabled ? 'bg-emerald-100 text-emerald-700' : 'bg-gray-200 text-gray-500'}`}>
                      {flow.enabled ? t('enabled') : t('disabled')}
                    </span>
                  </div>
                  <div className="text-[11px] text-gray-500 mt-1 truncate leading-snug">{flow.description || flow.flowId}</div>
                  <div className="flex items-center gap-1 mt-2 pt-2 border-t border-gray-100">
                    <button
                      disabled={testingFlowId === flow.flowId}
                      onClick={(e) => { e.stopPropagation(); openTestModal(flow.flowId); }}
                      className="flex-1 py-1.5 rounded-lg text-[11px] font-medium text-emerald-600 bg-emerald-50 hover:bg-emerald-100 transition-colors disabled:opacity-50"
                    >
                      {testingFlowId === flow.flowId ? '...' : t('test')}
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDelete(flow.flowId); }}
                      className="flex-1 py-1.5 rounded-lg text-[11px] font-medium text-red-600 bg-red-50 hover:bg-red-100 transition-colors"
                    >
                      {t('delete')}
                    </button>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* 主内容区 */}
      <div className="flex-1 flex flex-col bg-gray-50">
        {/* 顶部工具栏 - 美化分组 */}
        <div className="bg-white border-b border-gray-200 shadow-sm">
          <div className="px-4 py-3 flex items-center justify-between gap-4 flex-wrap">
            {/* 左侧：流程信息 */}
            <div className="flex items-center gap-3 flex-wrap">
              <div className="flex items-center gap-2">
                <input
                  value={form.name}
                  onChange={e => setForm({ ...form, name: e.target.value })}
                  className="px-3 py-2 border border-gray-200 rounded-xl text-sm font-medium text-gray-800 placeholder:text-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-shadow w-40"
                  placeholder={t('flow_name')}
                />
                <input
                  value={form.description || ''}
                  onChange={e => setForm({ ...form, description: e.target.value })}
                  className="px-3 py-2 border border-gray-200 rounded-xl text-sm text-gray-600 placeholder:text-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none w-44 hidden sm:block"
                  placeholder={t('description')}
                />
              </div>
              <label className="flex items-center gap-2 px-3 py-2 rounded-xl bg-gray-50 border border-gray-200 cursor-pointer hover:bg-gray-100 transition-colors">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={e => setForm({ ...form, enabled: e.target.checked })}
                  className="rounded text-blue-600 focus:ring-blue-500"
                />
                <span className="text-sm font-medium text-gray-700">{t('enabled')}</span>
              </label>
            </div>

            {/* 右侧：操作与缩放 */}
            <div className="flex items-center gap-1 flex-wrap">
              <div className="flex items-center rounded-xl border border-gray-200 bg-gray-50/80 p-1">
                <button
                  onClick={() => setShowImportModal(true)}
                  className="p-2 rounded-lg text-gray-500 hover:text-blue-600 hover:bg-white hover:shadow-sm transition-all"
                  title={t('import_flow')}
                >
                  <Upload size={16} />
                </button>
                <button
                  onClick={() => setShowExportModal(true)}
                  className="p-2 rounded-lg text-gray-500 hover:text-blue-600 hover:bg-white hover:shadow-sm transition-all"
                  title={t('export_flow')}
                >
                  <Download size={16} />
                </button>
                <button
                  onClick={clearCanvas}
                  className="p-2 rounded-lg text-gray-500 hover:text-red-600 hover:bg-white hover:shadow-sm transition-all"
                  title={t('clear_canvas')}
                >
                  <Trash2 size={16} />
                </button>
              </div>
              <div className="h-6 w-px bg-gray-200 mx-1" />
              <div className="flex items-center rounded-xl border border-gray-200 bg-gray-50/80 px-2 py-1">
                <button onClick={() => setScale(s => Math.max(s - 0.1, 0.3))} className="p-2 rounded-lg text-gray-500 hover:text-gray-800 hover:bg-white transition-colors" title={t('zoom_out')}>
                  <ZoomOut size={18} />
                </button>
                <span className="text-xs font-semibold text-gray-600 tabular-nums min-w-[2.5rem] text-center">{Math.round(scale * 100)}%</span>
                <button onClick={() => setScale(s => Math.min(s + 0.1, 2))} className="p-2 rounded-lg text-gray-500 hover:text-gray-800 hover:bg-white transition-colors" title={t('zoom_in')}>
                  <ZoomIn size={18} />
                </button>
                <button onClick={fitView} className="px-2.5 py-1.5 text-xs font-medium text-gray-600 hover:text-blue-600 hover:bg-white rounded-lg transition-colors" title={t('zoom_fit')}>
                  {t('zoom_fit')}
                </button>
              </div>
              <div className="h-6 w-px bg-gray-200 mx-1" />
              <button
                onClick={autoLayout}
                className="p-2 rounded-xl border border-gray-200 bg-gray-50/80 text-gray-500 hover:text-blue-600 hover:bg-white hover:border-blue-200 hover:shadow-sm transition-all"
                title={t('auto_layout')}
              >
                <LayoutGrid size={18} />
              </button>
              <button
                onClick={toggleFullscreen}
                className="p-2 rounded-xl border border-gray-200 bg-gray-50/80 text-gray-500 hover:text-gray-800 hover:bg-white hover:shadow-sm transition-all"
                title={isFullscreen ? t('exit_fullscreen') : t('fullscreen')}
              >
                {isFullscreen ? <Minimize2 size={18} /> : <Maximize2 size={18} />}
              </button>
              <div className="h-6 w-px bg-gray-200 mx-1" />
              <button
                disabled={!formValid || saving}
                onClick={handleSubmit}
                className={`inline-flex items-center gap-2 px-4 py-2 rounded-xl font-semibold text-sm shadow-sm transition-all ${formValid ? 'bg-blue-600 text-white hover:bg-blue-700 hover:shadow' : 'bg-gray-300 text-gray-500 cursor-not-allowed'}`}
              >
                <Save size={16} />
                {saving ? t('saving') : t('save')}
              </button>
            </div>
          </div>
        </div>

        {error && (
          <div className="mx-4 mt-2 rounded-lg border border-red-200 bg-red-50 text-red-700 px-4 py-2 text-sm">
            {error}
          </div>
        )}

        {/* 画布区域 */}
        <div className="flex-1 flex relative overflow-hidden">
          {/* 画布 */}
          <div
            ref={canvasRef}
            className="flex-1 relative overflow-hidden cursor-grab active:cursor-grabbing bg-[#f8fafc]"
            style={{
              backgroundImage: `
                radial-gradient(circle at 1px 1px, rgba(148,163,184,0.35) 1px, transparent 0),
                linear-gradient(180deg, rgba(241,245,249,0.6) 0%, transparent 50%)
              `,
              backgroundSize: '24px 24px',
              backgroundPosition: '0 0',
            }}
            onMouseMove={handleCanvasMouseMove}
            onMouseUp={handleCanvasMouseUp}
            onMouseLeave={handleCanvasMouseUp}
            onMouseDown={handleCanvasMouseDown}
            onDrop={handleDrop}
            onDragOver={handleDragOver}
          >
            {/* 画布左下角水印 */}
            <div className="absolute bottom-3 left-3 z-10 pointer-events-none select-none flex items-center gap-1.5 text-gray-400/80 text-xs font-medium">
              <span className="tracking-wide">Hook.Design</span>
              <span className="text-gray-300">·</span>
              <span>原创设计工作流引擎</span>
            </div>
            <div
              style={{
                transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale})`,
                transformOrigin: '0 0',
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
              }}
            >
              {/* SVG连接线层 */}
              <svg
                ref={svgRef}
                className="absolute inset-0 pointer-events-none"
                style={{ width: '100%', height: '100%', overflow: 'visible' }}
              >
                <defs>
                  <marker
                    id="arrowhead"
                    markerWidth="10"
                    markerHeight="7"
                    refX="9"
                    refY="3.5"
                    orient="auto"
                  >
                    <polygon points="0 0, 10 3.5, 0 7" fill="#6b7280" />
                  </marker>
                </defs>
                <g style={{ pointerEvents: 'auto' }}>
                  {connections.map(renderConnection)}
                  {renderConnectingLine()}
                </g>
              </svg>

              {/* 节点层 */}
              {nodes.map(renderNode)}
            </div>

            {/* 节点配置面板 */}
            {renderNodeConfigPanel()}

            {/* 连接删除提示 */}
            {selectedConnId && (
              <div className="absolute top-4 right-4 bg-white rounded-lg shadow-lg border border-gray-200 p-3 z-20">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-gray-700">{t('delete_connection')}</span>
                  <button
                    onClick={() => deleteConnection(selectedConnId)}
                    className="ml-4 text-red-500 hover:text-red-700"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* 组件库 - 右侧边栏，加强并美化 */}
          <div className="w-56 flex-shrink-0 bg-white border-l border-gray-200 flex flex-col overflow-hidden shadow-sm">
            <div className="px-3 py-3 border-b border-gray-100 bg-gradient-to-b from-gray-50/80 to-white">
              <div className="flex items-center gap-2 mb-1">
                <div className="w-8 h-8 rounded-lg bg-blue-100 flex items-center justify-center">
                  <Settings size={16} className="text-blue-600" />
                </div>
                <div>
                  <h4 className="font-semibold text-gray-800 text-sm">{t('flow_components')}</h4>
                  <p className="text-[11px] text-gray-500">{t('drag_hint')}</p>
                </div>
              </div>
            </div>
            <div className="flex-1 overflow-auto p-2 space-y-4">
              {CATEGORY_ORDER.map(cat => {
                const style = categoryStyle[cat] || { border: 'border-l-gray-400', bg: 'bg-gray-50/50', icon: 'text-gray-600' };
                return (
                  <div key={cat} className="space-y-1.5">
                    <div className={`text-[11px] font-semibold text-gray-500 uppercase tracking-wider px-2 py-1 border-l-2 ${style.border} ${style.bg} rounded-r`}>
                      {t(`category_${cat}`)}
                    </div>
                    <div className="space-y-1">
                      {groupedComponents[cat].map(comp => {
                        const IconComp = ICON_MAP[comp.icon] || Settings;
                        return (
                          <div
                            key={comp.type}
                            draggable
                            onDragStart={(e) => handleDragStart(e, comp)}
                            className={`flex items-center gap-2 px-2.5 py-2 rounded-lg border border-gray-200 bg-white hover:bg-white hover:border-blue-300 hover:shadow-md cursor-grab active:cursor-grabbing transition-all duration-200 ${style.border} border-l-4`}
                          >
                            <div className={`w-7 h-7 rounded-md flex items-center justify-center flex-shrink-0 ${style.bg}`}>
                              <IconComp size={14} className={style.icon} />
                            </div>
                            <span className="text-xs text-gray-700 font-medium truncate">{t(comp.label)}</span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>

      {/* 导入模态框 */}
      {showImportModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-[600px] max-h-[80vh] overflow-hidden">
            <div className="px-4 py-3 border-b border-gray-200 flex items-center justify-between">
              <h3 className="font-semibold text-gray-800">{t('import_json_title')}</h3>
              <button onClick={() => setShowImportModal(false)} className="text-gray-500 hover:text-gray-700">
                <X size={20} />
              </button>
            </div>
            <div className="p-4">
              <textarea
                value={importJson}
                onChange={e => setImportJson(e.target.value)}
                className="w-full h-64 border border-gray-200 rounded-lg p-3 font-mono text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
                placeholder='{"nodes": [...], "connections": [...]}'
              />
            </div>
            <div className="px-4 py-3 border-t border-gray-200 flex justify-end space-x-2">
              <button
                onClick={() => setShowImportModal(false)}
                className="px-4 py-2 border border-gray-200 rounded-lg hover:bg-gray-50"
              >
                {t('cancel')}
              </button>
              <button
                onClick={handleImport}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
              >
                {t('import_flow')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 导出模态框 */}
      {showExportModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-[600px] max-h-[80vh] overflow-hidden">
            <div className="px-4 py-3 border-b border-gray-200 flex items-center justify-between">
              <h3 className="font-semibold text-gray-800">{t('export_json_title')}</h3>
              <button onClick={() => setShowExportModal(false)} className="text-gray-500 hover:text-gray-700">
                <X size={20} />
              </button>
            </div>
            <div className="p-4">
              <textarea
                value={exportJson}
                readOnly
                className="w-full h-64 border border-gray-200 rounded-lg p-3 font-mono text-sm bg-gray-50"
              />
            </div>
            <div className="px-4 py-3 border-t border-gray-200 flex justify-end space-x-2">
              <button
                onClick={() => setShowExportModal(false)}
                className="px-4 py-2 border border-gray-200 rounded-lg hover:bg-gray-50"
              >
                {t('cancel')}
              </button>
              <button
                onClick={copyToClipboard}
                className="px-4 py-2 border border-gray-200 rounded-lg hover:bg-gray-50"
              >
                复制
              </button>
              <button
                onClick={downloadJson}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
              >
                <Download size={14} className="inline mr-1" />
                下载
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 事件触发器配置抽屉 */}
      {showEventTriggerDrawer && selectedNode?.type === 'event_trigger' && (
        <div className="fixed inset-0 z-50 flex">
          {/* 遮罩 */}
          <div 
            className="flex-1 bg-black bg-opacity-50" 
            onClick={() => setShowEventTriggerDrawer(false)}
          />
          {/* 抽屉 */}
          <div className="w-[480px] bg-white shadow-xl flex flex-col h-full">
            {/* 抽屉头部 */}
            <div className="px-4 py-3 border-b border-gray-200 flex items-center justify-between bg-gray-50">
              <h3 className="font-semibold text-gray-800">{t('event_trigger_config')}</h3>
              <button 
                onClick={() => setShowEventTriggerDrawer(false)} 
                className="text-gray-500 hover:text-gray-700"
              >
                <X size={20} />
              </button>
            </div>

            {/* 抽屉内容 */}
            <div className="flex-1 overflow-auto p-4 space-y-4">
              {/* 防抖间隔配置 */}
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  {t('config_debounce_seconds')}
                </label>
                <input
                  type="number"
                  min="0"
                  value={selectedNode.config?.debounceSeconds ?? 5}
                  onChange={e => updateNodeConfig('debounceSeconds', parseInt(e.target.value) || 0)}
                  className="w-full px-3 py-2 rounded-lg border border-gray-200 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                />
                <p className="text-xs text-gray-500 mt-2">{t('config_debounce_hint')}</p>
              </div>

              {/* 设备品牌过滤 */}
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center justify-between mb-3">
                  <label className="text-sm font-medium text-gray-700">{t('config_device_brands')}</label>
                  <button
                    onClick={() => updateNodeConfig('deviceBrands', [])}
                    className="text-xs text-blue-600 hover:text-blue-800"
                  >
                    {t('select_all')}
                  </button>
                </div>
                <div className="flex flex-wrap gap-2">
                  {['hikvision', 'tiandy', 'dahua'].map(brand => {
                    const selectedBrands: string[] = selectedNode.config?.deviceBrands || [];
                    const isSelected = selectedBrands.length === 0 || selectedBrands.includes(brand);
                    const brandLabels: Record<string, string> = { hikvision: '海康威视', tiandy: '天地伟业', dahua: '大华' };
                    return (
                      <button
                        key={brand}
                        onClick={() => {
                          if (selectedBrands.length === 0) {
                            // 当前是全选，点击后只选中当前品牌
                            updateNodeConfig('deviceBrands', [brand]);
                          } else if (selectedBrands.includes(brand)) {
                            // 已选中，移除
                            const newBrands = selectedBrands.filter(b => b !== brand);
                            updateNodeConfig('deviceBrands', newBrands.length === 0 ? [] : newBrands);
                          } else {
                            // 未选中，添加
                            updateNodeConfig('deviceBrands', [...selectedBrands, brand]);
                          }
                        }}
                        className={`px-3 py-1.5 rounded-lg text-sm border transition ${
                          isSelected
                            ? 'bg-blue-50 border-blue-300 text-blue-700'
                            : 'bg-gray-50 border-gray-200 text-gray-500 hover:bg-gray-100'
                        }`}
                      >
                        {isSelected && <Check size={12} className="inline mr-1" />}
                        {brandLabels[brand] || brand}
                      </button>
                    );
                  })}
                </div>
                <p className="text-xs text-gray-500 mt-2">{t('config_device_brands_hint')}</p>
              </div>

              {/* 报警类型过滤（按 category 分组，含品牌筛选） */}
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center justify-between mb-3">
                  <label className="text-sm font-medium text-gray-700">{t('config_alarm_types')}</label>
                  <div className="flex items-center space-x-2">
                    <span className="text-xs text-gray-500">
                      {(selectedNode.config?.alarmTypes || []).length === 0
                        ? t('all_types')
                        : `${(selectedNode.config?.alarmTypes || []).length} ${t('types_selected')}`}
                    </span>
                    <button
                      onClick={() => updateNodeConfig('alarmTypes', [])}
                      className="text-xs text-blue-600 hover:text-blue-800"
                    >
                      {t('select_all')}
                    </button>
                  </div>
                </div>

                {/* 品牌筛选 */}
                <div className="flex items-center gap-1.5 mb-3 flex-wrap">
                  <span className="text-xs text-gray-400">品牌:</span>
                  <button
                    onClick={() => setEventBrandFilter(null)}
                    className={`px-2 py-1 rounded text-xs border transition ${eventBrandFilter === null ? 'bg-blue-600 text-white border-blue-600' : 'bg-white border-gray-200 text-gray-500 hover:bg-gray-50'}`}
                  >全部</button>
                  {(() => {
                    const brands = new Set<string>();
                    canonicalEvents.forEach(ev => ev.brands?.forEach(b => brands.add(b)));
                    const brandLabelMap: Record<string, string> = { hikvision: '海康', tiandy: '天地伟业', dahua: '大华' };
                    return Array.from(brands).sort().map(b => (
                      <button
                        key={b}
                        onClick={() => setEventBrandFilter(eventBrandFilter === b ? null : b)}
                        className={`px-2 py-1 rounded text-xs border transition ${eventBrandFilter === b ? 'bg-blue-50 border-blue-300 text-blue-700' : 'bg-white border-gray-200 text-gray-500 hover:bg-gray-50'}`}
                      >{brandLabelMap[b] || b}</button>
                    ));
                  })()}
                </div>

                {eventTypesLoading ? (
                  <div className="text-center py-4 text-gray-500">{t('loading')}</div>
                ) : canonicalEvents.length === 0 ? (
                  <div className="text-center py-4 text-gray-500">{t('no_data')}</div>
                ) : (
                  <div className="space-y-2 max-h-[400px] overflow-auto">
                    {(() => {
                      const categoryLabels: Record<string, string> = {
                        basic: '基础报警', vca: '智能分析', face: '人脸识别',
                        its: '交通/车辆', unknown: '未分类', other: '其他'
                      };
                      const brandLabelMap: Record<string, string> = { hikvision: '海康', tiandy: '天地伟业', dahua: '大华' };
                      const brandColorMap: Record<string, string> = {
                        hikvision: 'bg-red-50 text-red-500',
                        tiandy: 'bg-teal-50 text-teal-500',
                        dahua: 'bg-orange-50 text-orange-500',
                      };
                      const filtered = eventBrandFilter
                        ? canonicalEvents.filter(ev => ev.brands?.includes(eventBrandFilter))
                        : canonicalEvents;
                      const grouped: Record<string, CanonicalEvent[]> = {};
                      filtered.forEach(ev => {
                        const cat = ev.category || 'other';
                        if (!grouped[cat]) grouped[cat] = [];
                        grouped[cat].push(ev);
                      });
                      const selectedTypes: string[] = selectedNode.config?.alarmTypes || [];

                      if (Object.keys(grouped).length === 0) {
                        return <div className="text-center py-4 text-gray-400 text-sm">该品牌暂无关联的事件</div>;
                      }

                      return Object.entries(grouped).map(([category, events]) => {
                        const isExpanded = expandedBrands[category] ?? true;
                        return (
                          <div key={category} className="border border-gray-100 rounded-lg overflow-hidden">
                            <button
                              onClick={() => setExpandedBrands(prev => ({ ...prev, [category]: !isExpanded }))}
                              className="w-full flex items-center justify-between px-3 py-2 bg-gray-50 hover:bg-gray-100 transition"
                            >
                              <div className="flex items-center gap-1.5">
                                <span className="font-medium text-gray-700">{categoryLabels[category] || category}</span>
                                <span className="text-xs text-gray-400">{events.length}</span>
                              </div>
                              {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                            </button>
                            {isExpanded && (
                              <div className="p-2">
                                <div className="flex flex-wrap gap-1">
                                  {events.map(ev => {
                                    const isSelected = selectedTypes.length === 0 || selectedTypes.includes(ev.eventKey);
                                    return (
                                      <button
                                        key={ev.eventKey}
                                        onClick={() => {
                                          if (selectedTypes.length === 0) {
                                            updateNodeConfig('alarmTypes', [ev.eventKey]);
                                          } else if (selectedTypes.includes(ev.eventKey)) {
                                            const newTypes = selectedTypes.filter(k => k !== ev.eventKey);
                                            updateNodeConfig('alarmTypes', newTypes);
                                          } else {
                                            updateNodeConfig('alarmTypes', [...selectedTypes, ev.eventKey]);
                                          }
                                        }}
                                        className={`inline-flex items-center gap-1 px-2 py-1 rounded text-xs border transition ${
                                          isSelected
                                            ? 'bg-blue-50 border-blue-200 text-blue-700'
                                            : 'bg-white border-gray-200 text-gray-500 hover:bg-gray-50'
                                        }`}
                                        title={`${ev.description || ev.eventKey}${ev.brands?.length ? ' [' + ev.brands.map(b => brandLabelMap[b] || b).join(', ') + ']' : ''}`}
                                      >
                                        {isSelected && <Check size={10} className="inline" />}
                                        {ev.nameZh}
                                        {ev.brands && ev.brands.length > 0 && ev.brands.map(b => (
                                          <span key={b} className={`text-[9px] px-0.5 rounded ${brandColorMap[b] || 'bg-gray-100 text-gray-400'}`}>
                                            {brandLabelMap[b]?.[0] || b[0]}
                                          </span>
                                        ))}
                                      </button>
                                    );
                                  })}
                                </div>
                              </div>
                            )}
                          </div>
                        );
                      });
                    })()}
                  </div>
                )}
                <p className="text-xs text-gray-500 mt-2">{t('config_alarm_types_hint')}</p>
              </div>
            </div>

            {/* 抽屉底部 */}
            <div className="px-4 py-3 border-t border-gray-200 bg-gray-50 flex justify-end">
              <button
                onClick={() => setShowEventTriggerDrawer(false)}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
              >
                {t('confirm')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 流程测试弹窗 */}
      {showTestModal && (
        <div className="fixed inset-0 bg-black/40 z-[200] flex items-center justify-center" onClick={() => setShowTestModal(false)}>
          <div className="bg-white rounded-2xl shadow-2xl w-[460px] max-h-[90vh] overflow-auto" onClick={e => e.stopPropagation()}>
            <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-800 flex items-center gap-2">
                <Play size={18} className="text-emerald-500" />
                {t('flow_test_title')}
              </h3>
              <button onClick={() => setShowTestModal(false)} className="text-gray-400 hover:text-gray-600">
                <X size={18} />
              </button>
            </div>

            <div className="px-6 py-4 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('test_event_name')}</label>
                <input
                  value={testForm.eventName}
                  onChange={e => setTestForm(f => ({ ...f, eventName: e.target.value }))}
                  className="w-full px-3 py-2 rounded-lg border border-gray-200 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm"
                  placeholder="反光衣检测"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('test_alarm_type')}</label>
                <input
                  value={testForm.alarmType}
                  onChange={e => setTestForm(f => ({ ...f, alarmType: e.target.value }))}
                  className="w-full px-3 py-2 rounded-lg border border-gray-200 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm"
                  placeholder="VEST_DETECTION"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('test_device_id')}</label>
                  <input
                    value={testForm.deviceId}
                    onChange={e => setTestForm(f => ({ ...f, deviceId: e.target.value }))}
                    className="w-full px-3 py-2 rounded-lg border border-gray-200 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm"
                    placeholder="test-camera-001"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('test_device_ip')}</label>
                  <input
                    value={testForm.deviceIp}
                    onChange={e => setTestForm(f => ({ ...f, deviceIp: e.target.value }))}
                    className="w-full px-3 py-2 rounded-lg border border-gray-200 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm"
                    placeholder="192.168.1.100"
                  />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('test_capture_image')}</label>
                <div
                  onClick={() => testFileRef.current?.click()}
                  className="border-2 border-dashed border-gray-200 rounded-xl p-4 cursor-pointer hover:border-blue-400 hover:bg-blue-50/30 transition-all text-center"
                >
                  {testImagePreview ? (
                    <div className="relative">
                      <img src={testImagePreview} alt="preview" className="max-h-40 mx-auto rounded-lg object-contain" />
                      <p className="text-xs text-gray-500 mt-2">{testImage?.name} ({(testImage?.size ?? 0 / 1024).toFixed(0)} KB)</p>
                    </div>
                  ) : (
                    <div className="text-gray-400">
                      <ImagePlus size={32} className="mx-auto mb-2" />
                      <p className="text-sm">{t('test_upload_hint')}</p>
                      <p className="text-xs text-gray-400 mt-1">{t('test_upload_optional')}</p>
                    </div>
                  )}
                </div>
                <input
                  ref={testFileRef}
                  type="file"
                  accept="image/*"
                  className="hidden"
                  onChange={handleTestImageChange}
                />
              </div>
            </div>

            <div className="px-6 py-4 border-t border-gray-100 flex justify-end gap-3">
              <button
                onClick={() => setShowTestModal(false)}
                className="px-4 py-2 rounded-lg text-sm text-gray-600 hover:bg-gray-100 transition"
              >
                {t('cancel')}
              </button>
              <button
                onClick={handleTestSubmit}
                disabled={!!testingFlowId}
                className="px-5 py-2 rounded-lg text-sm font-medium text-white bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 transition flex items-center gap-2"
              >
                <Play size={14} />
                {t('test_run')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
