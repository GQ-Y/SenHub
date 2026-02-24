# 点云 20 万点/秒前端优化策略

## 目标

- **全部点显示**：服务端约 20 万点/秒的实时点云全部在界面显示，不因主线程阻塞而只显示 5～6 万点/秒。
- **低资源占用**：主线程不解析二进制、不维护 20 万级 JS 对象，渲染用 BufferGeometry 增量更新，计算资源占用可控。

## 架构概览

```
WebSocket (ArrayBuffer) → Worker (解析 + 滑动窗口 + 着色) → 主线程 (rAF 收缓冲 → PointCloudRenderer 更新几何)
```

1. **WebSocket**：服务端按帧推送二进制点云，前端收到 `ArrayBuffer` 后**直接 transfer 给 Worker**，主线程不解析。
2. **Worker**：解析二进制帧、按时间戳维护滑动窗口（可配置 1/3/5 秒）、反射率着色、节流（约 25 fps）向主线程 post 出 `positions` + `colors` + `count`（Transferable）。
3. **主线程**：仅在 rAF 中取 Worker 最新缓冲、`setState(pointCloudBuffer)`，驱动 PointCloudRenderer 更新。
4. **PointCloudRenderer**：收到 `pointCloudBuffer` 时只做 **BufferGeometry 增量更新**（setAttribute + setDrawRange），不遍历 20 万对象、不重建整棵 Points 树。

## 关键文件

| 文件 | 作用 |
|------|------|
| `src/workers/pointcloud.worker.ts` | 解析二进制、滑动窗口、反射率着色、节流 post |
| `src/api/services.ts` | `connectWebSocket(..., { onBinaryPointCloud })` 将二进制直接转给 Worker |
| `components/RadarMonitoring.tsx` | 创建 Worker、WS 回调里 post 二进制、rAF 中 setState(pointCloudBuffer) |
| `components/PointCloudRenderer.tsx` | 支持 `pointCloudBuffer`，仅更新 BufferGeometry，侵入点用第二层 Points |

## 资源控制

- **Worker**：单线程、无 DOM，解析与着色在子线程，主线程只收结果。
- **节流**：Worker 向主线程 post 间隔 40ms（约 25 fps），避免主线程被 2000+ 次/秒的 setState 拖垮。
- **滑动窗口**：最多保留 25 万点（略大于 20 万/秒 × 1 秒），超出的最旧帧丢弃。
- **渲染**：单次 Points 绘制、BufferAttribute 更新，无 20 万次对象创建。

## 使用说明

- 雷达监控页默认启用上述路径：二进制点云走 Worker，主点云由 `pointCloudBuffer` 驱动，侵入点仍为 `points`（红色第二层）。
- 累积时间（1/3/5 秒）变化时会通过 `setWindow` 同步到 Worker。
