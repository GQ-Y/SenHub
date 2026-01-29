import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { flowService, eventTypeService } from '../src/api/services';
import { AlarmFlow, CanvasConnection, CanvasNode, FlowComponentDefinition, FlowNodeType, CameraEventType } from '../types';
import { useAppContext } from '../contexts/AppContext';
import {
  Plus, Trash2, RefreshCw, Save, X, Download, Upload, ZoomIn, ZoomOut,
  Play, Square, GitBranch, Camera, Video, Radio, Cloud, Volume2, Send, Zap, Settings, Move,
  Maximize2, Minimize2, ChevronDown, ChevronRight, Check
} from 'lucide-react';

// 内置组件定义
const FLOW_COMPONENTS: FlowComponentDefinition[] = [
  { type: 'event_trigger', label: 'node_event_trigger', icon: 'Zap', category: 'trigger', defaultConfig: { debounceSeconds: 5 } },
  { type: 'mqtt_subscribe', label: 'node_mqtt_subscribe', icon: 'Radio', category: 'trigger', defaultConfig: { topic: 'senhub/custom/command', qos: 1 } },
  { type: 'condition', label: 'node_condition', icon: 'GitBranch', category: 'logic', hasConditionPorts: true, defaultConfig: { expression: '' } },
  { type: 'capture', label: 'node_capture', icon: 'Camera', category: 'action', defaultConfig: { channel: 1 } },
  { type: 'record', label: 'node_record', icon: 'Video', category: 'action', defaultConfig: { channel: 1, beforeSeconds: 15, afterSeconds: 15 } },
  { type: 'ptz_control', label: 'node_ptz_control', icon: 'Move', category: 'action', defaultConfig: { preset: 1 } },
  { type: 'mqtt_publish', label: 'node_mqtt_publish', icon: 'Radio', category: 'output', defaultConfig: { topic: 'alarm/report/{deviceId}' } },
  { type: 'oss_upload', label: 'node_oss_upload', icon: 'Cloud', category: 'output', defaultConfig: { path: 'alarm/{deviceId}/{fileName}' } },
  { type: 'speaker_play', label: 'node_speaker_play', icon: 'Volume2', category: 'output', defaultConfig: { audioFile: '' } },
  { type: 'webhook', label: 'node_webhook', icon: 'Send', category: 'output', defaultConfig: { url: '', method: 'POST' } },
  { type: 'end', label: 'node_end', icon: 'Square', category: 'logic', defaultConfig: {} },
];

const ICON_MAP: Record<string, React.FC<{ size?: number; className?: string }>> = {
  Zap, GitBranch, Camera, Video, Move, Radio, Cloud, Volume2, Send, Square, Settings, Play
};

const CATEGORY_ORDER = ['trigger', 'logic', 'action', 'output'] as const;

const NODE_WIDTH = 160;
const NODE_HEIGHT = 72;
const PORT_RADIUS = 8;

// 生成唯一ID
const genId = () => `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
const genConnId = () => `conn_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

// 节点颜色
const getNodeColor = (type: FlowNodeType) => {
  switch (type) {
    case 'event_trigger': case 'mqtt_subscribe': return { bg: 'bg-amber-50', border: 'border-amber-400', text: 'text-amber-700' };
    case 'condition': return { bg: 'bg-purple-50', border: 'border-purple-400', text: 'text-purple-700' };
    case 'capture': case 'record': case 'ptz_control': return { bg: 'bg-blue-50', border: 'border-blue-400', text: 'text-blue-700' };
    case 'mqtt_publish': case 'oss_upload': case 'speaker_play': case 'webhook': return { bg: 'bg-green-50', border: 'border-green-400', text: 'text-green-700' };
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
  const [eventTypes, setEventTypes] = useState<Record<string, CameraEventType[]>>({});
  const [eventTypesLoading, setEventTypesLoading] = useState(false);
  const [expandedBrands, setExpandedBrands] = useState<Record<string, boolean>>({});

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
        setEventTypes(res.data);
        // 默认展开所有品牌
        const expanded: Record<string, boolean> = {};
        Object.keys(res.data).forEach(brand => { expanded[brand] = true; });
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

    const canvasConns: CanvasConnection[] = (flow.connections || []).map((conn, idx) => ({
      id: `conn_${idx}`,
      fromNodeId: conn.from,
      toNodeId: conn.to,
      fromPort: conn.fromPort || 'default',
      condition: conn.condition,
    }));

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
    const flowConns = connections.map(c => ({
      from: c.fromNodeId,
      to: c.toNodeId,
      fromPort: c.fromPort,
      condition: c.condition,
    }));
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

  // 测试流程
  const handleTest = async (flowId: string) => {
    try {
      await flowService.testFlow(flowId);
      alert(t('flow_test_success'));
    } catch (e: any) {
      alert(e?.message || 'Test failed');
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
        const importedConns: CanvasConnection[] = (data.connections || []).map((c: any, idx: number) => ({
          id: c.id || `conn_${idx}`,
          fromNodeId: c.from || c.fromNodeId,
          toNodeId: c.to || c.toNodeId,
          fromPort: c.fromPort || 'default',
          condition: c.condition,
        }));
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

  // 渲染节点
  const renderNode = (node: CanvasNode) => {
    const comp = FLOW_COMPONENTS.find(c => c.type === node.type);
    const colors = getNodeColor(node.type);
    const IconComp = comp ? ICON_MAP[comp.icon] : Settings;
    const isSelected = selectedNodeId === node.id;
    const hasConditionPorts = comp?.hasConditionPorts;

    return (
      <div
        key={node.id}
        className={`absolute rounded-lg border-2 shadow-md cursor-move select-none transition-shadow ${colors.bg} ${colors.border} ${isSelected ? 'ring-2 ring-blue-500 shadow-lg' : ''}`}
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

        {/* 节点内容 */}
        <div className="flex flex-col items-center justify-center h-full px-2">
          <IconComp size={20} className={colors.text} />
          <div className={`text-xs font-medium mt-1 text-center truncate w-full ${colors.text}`}>
            {node.label}
          </div>
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
      <div className="absolute top-4 right-4 w-64 bg-white rounded-lg shadow-lg border border-gray-200 p-3 z-20">
        <div className="flex items-center justify-between mb-3">
          <h4 className="font-semibold text-gray-800">{t('node_config')}</h4>
          <button
            onClick={() => deleteNode(selectedNode.id)}
            className="text-red-500 hover:text-red-700"
            title={t('delete_node')}
          >
            <Trash2 size={16} />
          </button>
        </div>

        <div className="space-y-2 text-sm">
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
            <div>
              <label className="block text-gray-600 mb-1">{t('config_condition_expr')}</label>
              <input
                value={selectedNode.config?.expression || ''}
                onChange={e => updateNodeConfig('expression', e.target.value)}
                className="w-full px-2 py-1 rounded border border-gray-200 focus:ring-1 focus:ring-blue-500"
                placeholder="e.g. confidence > 0.8"
              />
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
                    : `${(selectedNode.config?.alarmTypes || []).length} ${t('types_selected')}`}
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

  return (
    <div 
      ref={containerRef}
      className={`flex ${isFullscreen ? 'fixed inset-0 z-50 bg-gray-50' : 'h-full min-h-[calc(100vh-10rem)]'}`}
      style={{ height: isFullscreen ? '100vh' : '100%' }}
    >
      {/* 左侧流程列表 - 全屏时隐藏 */}
      <div className={`w-64 bg-white border-r border-gray-200 flex flex-col ${isFullscreen ? 'hidden' : ''}`}>
        <div className="p-3 border-b border-gray-100">
          <div className="flex items-center justify-between mb-2">
            <h3 className="font-semibold text-gray-800">{t('flow_list')}</h3>
            <div className="flex space-x-1">
              <button onClick={loadData} className="p-1 text-gray-500 hover:text-gray-700" title={t('refresh')}>
                <RefreshCw size={16} />
              </button>
              <button onClick={openCreate} className="p-1 text-blue-500 hover:text-blue-700" title={t('create_flow')}>
                <Plus size={16} />
              </button>
            </div>
          </div>
        </div>

        <div className="flex-1 overflow-auto p-2 space-y-2">
          {loading ? (
            <div className="text-center text-gray-500 py-4">{t('loading')}</div>
          ) : flows.length === 0 ? (
            <div className="text-center text-gray-500 py-4">{t('no_data')}</div>
          ) : (
            flows.map(flow => (
              <div
                key={flow.flowId}
                onClick={() => openEdit(flow)}
                className={`p-2 rounded-lg border cursor-pointer transition ${
                  selected === flow.flowId ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-blue-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="font-medium text-gray-800 text-sm truncate">{flow.name}</div>
                  <span className={`px-1.5 py-0.5 rounded text-xs ${flow.enabled ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                    {flow.enabled ? t('enabled') : t('disabled')}
                  </span>
                </div>
                <div className="text-xs text-gray-500 mt-1 truncate">{flow.description || flow.flowId}</div>
                <div className="flex space-x-2 mt-2">
                  <button
                    onClick={(e) => { e.stopPropagation(); handleTest(flow.flowId); }}
                    className="text-emerald-600 text-xs hover:underline"
                  >
                    {t('test')}
                  </button>
                  <button
                    onClick={(e) => { e.stopPropagation(); handleDelete(flow.flowId); }}
                    className="text-red-500 text-xs hover:underline"
                  >
                    {t('delete')}
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* 主内容区 */}
      <div className="flex-1 flex flex-col bg-gray-50">
        {/* 顶部工具栏 */}
        <div className="bg-white border-b border-gray-200 px-4 py-2 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <input
              value={form.name}
              onChange={e => setForm({ ...form, name: e.target.value })}
              className="px-3 py-1.5 border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500 focus:outline-none"
              placeholder={t('flow_name')}
            />
            <input
              value={form.description || ''}
              onChange={e => setForm({ ...form, description: e.target.value })}
              className="px-3 py-1.5 border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500 focus:outline-none w-48"
              placeholder={t('description')}
            />
            <label className="flex items-center space-x-1 text-sm text-gray-600">
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={e => setForm({ ...form, enabled: e.target.checked })}
                className="rounded text-blue-600"
              />
              <span>{t('enabled')}</span>
            </label>
          </div>

          <div className="flex items-center space-x-2">
            <button
              onClick={() => setShowImportModal(true)}
              className="p-2 border border-gray-200 rounded-lg hover:bg-gray-50"
              title={t('import_flow')}
            >
              <Upload size={16} className="text-gray-600" />
            </button>
            <button
              onClick={() => setShowExportModal(true)}
              className="p-2 border border-gray-200 rounded-lg hover:bg-gray-50"
              title={t('export_flow')}
            >
              <Download size={16} className="text-gray-600" />
            </button>
            <button
              onClick={clearCanvas}
              className="p-2 border border-gray-200 rounded-lg hover:bg-gray-50"
              title={t('clear_canvas')}
            >
              <Trash2 size={16} className="text-red-500" />
            </button>
            <div className="h-5 w-px bg-gray-300" />
            <button onClick={() => setScale(s => Math.max(s - 0.1, 0.3))} className="p-1 text-gray-500 hover:text-gray-700" title={t('zoom_out')}>
              <ZoomOut size={18} />
            </button>
            <span className="text-sm text-gray-600 w-12 text-center">{Math.round(scale * 100)}%</span>
            <button onClick={() => setScale(s => Math.min(s + 0.1, 2))} className="p-1 text-gray-500 hover:text-gray-700" title={t('zoom_in')}>
              <ZoomIn size={18} />
            </button>
            <button onClick={fitView} className="px-2 py-1 text-sm text-gray-600 hover:text-gray-800">
              {t('zoom_fit')}
            </button>
            <div className="h-5 w-px bg-gray-300" />
            <button
              onClick={toggleFullscreen}
              className="p-2 border border-gray-200 rounded-lg hover:bg-gray-50"
              title={isFullscreen ? t('exit_fullscreen') : t('fullscreen')}
            >
              {isFullscreen ? <Minimize2 size={16} className="text-gray-600" /> : <Maximize2 size={16} className="text-gray-600" />}
            </button>
            <button
              disabled={!formValid || saving}
              onClick={handleSubmit}
              className={`inline-flex items-center px-4 py-1.5 rounded-lg text-white ${formValid ? 'bg-blue-600 hover:bg-blue-700' : 'bg-gray-400 cursor-not-allowed'}`}
            >
              <Save size={14} className="mr-1" />
              {saving ? t('saving') : t('save')}
            </button>
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
            className="flex-1 relative overflow-hidden cursor-grab active:cursor-grabbing"
            style={{ background: 'radial-gradient(circle, #e5e7eb 1px, transparent 1px)', backgroundSize: '20px 20px' }}
            onMouseMove={handleCanvasMouseMove}
            onMouseUp={handleCanvasMouseUp}
            onMouseLeave={handleCanvasMouseUp}
            onMouseDown={handleCanvasMouseDown}
            onDrop={handleDrop}
            onDragOver={handleDragOver}
          >
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

          {/* 组件面板 - 右侧边栏 */}
          <div className="w-48 bg-white border-l border-gray-200 flex flex-col overflow-hidden">
            <div className="px-3 py-2 border-b border-gray-100 bg-white">
              <h4 className="font-semibold text-gray-800 text-sm">{t('flow_components')}</h4>
              <p className="text-xs text-gray-500">{t('drag_hint')}</p>
            </div>
            <div className="flex-1 overflow-auto p-2 space-y-2">
              {CATEGORY_ORDER.map(cat => (
                <div key={cat}>
                  <div className="text-xs font-medium text-gray-500 uppercase mb-1">{t(`category_${cat}`)}</div>
                  <div className="space-y-1">
                    {groupedComponents[cat].map(comp => {
                      const IconComp = ICON_MAP[comp.icon] || Settings;
                      return (
                        <div
                          key={comp.type}
                          draggable
                          onDragStart={(e) => handleDragStart(e, comp)}
                          className="flex items-center space-x-2 px-2 py-1.5 rounded border border-gray-200 bg-gray-50 hover:bg-blue-50 hover:border-blue-300 cursor-grab active:cursor-grabbing transition"
                        >
                          <IconComp size={14} className="text-gray-600" />
                          <span className="text-xs text-gray-700">{t(comp.label)}</span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))}
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

              {/* 报警类型过滤 */}
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

                {eventTypesLoading ? (
                  <div className="text-center py-4 text-gray-500">{t('loading')}</div>
                ) : Object.keys(eventTypes).length === 0 ? (
                  <div className="text-center py-4 text-gray-500">{t('no_data')}</div>
                ) : (
                  <div className="space-y-2 max-h-[400px] overflow-auto">
                    {Object.entries(eventTypes).map(([brand, types]: [string, CameraEventType[]]) => {
                      const brandLabels: Record<string, string> = { hikvision: '海康威视', tiandy: '天地伟业', dahua: '大华' };
                      const isExpanded = expandedBrands[brand] ?? true;
                      const selectedTypes: string[] = selectedNode.config?.alarmTypes || [];
                      
                      // 按分类分组
                      const typesByCategory: Record<string, CameraEventType[]> = {};
                      types.forEach(t => {
                        const cat = t.category || 'other';
                        if (!typesByCategory[cat]) typesByCategory[cat] = [];
                        typesByCategory[cat].push(t);
                      });
                      
                      const categoryLabels: Record<string, string> = {
                        basic: '基础报警',
                        vca: '智能分析',
                        face: '人脸识别',
                        its: '交通/车辆',
                        other: '其他'
                      };

                      return (
                        <div key={brand} className="border border-gray-100 rounded-lg overflow-hidden">
                          <button
                            onClick={() => setExpandedBrands(prev => ({ ...prev, [brand]: !isExpanded }))}
                            className="w-full flex items-center justify-between px-3 py-2 bg-gray-50 hover:bg-gray-100 transition"
                          >
                            <span className="font-medium text-gray-700">{brandLabels[brand] || brand}</span>
                            {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                          </button>
                          
                          {isExpanded && (
                            <div className="p-2 space-y-2">
                              {Object.entries(typesByCategory).map(([category, catTypes]) => (
                                <div key={category}>
                                  <div className="text-xs text-gray-500 font-medium mb-1 px-1">
                                    {categoryLabels[category] || category}
                                  </div>
                                  <div className="flex flex-wrap gap-1">
                                    {catTypes.map(eventType => {
                                      const typeKey = `${brand}:${eventType.eventCode}`;
                                      const isSelected = selectedTypes.length === 0 || selectedTypes.includes(typeKey);
                                      return (
                                        <button
                                          key={typeKey}
                                          onClick={() => {
                                            if (selectedTypes.length === 0) {
                                              updateNodeConfig('alarmTypes', [typeKey]);
                                            } else if (selectedTypes.includes(typeKey)) {
                                              const newTypes = selectedTypes.filter(t => t !== typeKey);
                                              updateNodeConfig('alarmTypes', newTypes);
                                            } else {
                                              updateNodeConfig('alarmTypes', [...selectedTypes, typeKey]);
                                            }
                                          }}
                                          className={`px-2 py-1 rounded text-xs border transition ${
                                            isSelected
                                              ? 'bg-blue-50 border-blue-200 text-blue-700'
                                              : 'bg-white border-gray-200 text-gray-500 hover:bg-gray-50'
                                          }`}
                                          title={eventType.description}
                                        >
                                          {isSelected && <Check size={10} className="inline mr-0.5" />}
                                          {eventType.eventName}
                                        </button>
                                      );
                                    })}
                                  </div>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      );
                    })}
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
    </div>
  );
};
