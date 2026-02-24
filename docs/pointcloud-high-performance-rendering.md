# 高性能点云实时绘制方案（调研摘要）

当前现象：点云已能显示，但每秒仅约 1～3 万点，目标为 20 万点/秒。以下为网络检索与业界常见做法整理，便于选型与落地。

---

## 一、当前瓶颈简要分析

1. **每帧替换整个 BufferAttribute**：Worker 每次 postMessage 都带新的 `positions`/`colors`，主线程用 `geom.setAttribute('position', new Float32BufferAttribute(positions, 3))` 替换属性，会触发 **整块 buffer 重新上传 GPU**，数据量大时非常耗性能。
2. **Worker 节流**：当前约 40ms 推送一次（~25fps），若再叠加主线程卡顿，实际可见更新率会更低。
3. **WebGL 限制**：CPU→GPU 每帧全量上传 20 万点 × (12+12) 字节，带宽与驱动开销都很大。

---

## 二、业界常用方案概览

### 1. 预分配 Buffer + 原地更新 + setDrawRange（最贴合当前架构）

- **思路**：只创建一次 `BufferGeometry`，分配足够大的 `position`/`color`（如 25 万点 × 3），之后每帧只 **往已有 attribute 的 array 里写数据**，再标记需要更新（或只更新变动区间），用 `setDrawRange(0, count)` 控制绘制点数。
- **优点**：不每帧 new 新 Buffer、不每帧替换 attribute，大幅减少 GPU 上传与 GC。
- **参考**：Three 文档 [How to update things](https://threejs.org/docs/manual/en/introduction/How-to-update-things.html)、社区建议对大 BufferGeometry 预分配并用 setDrawRange 控制绘制范围。
- **注意**：若用 `needsUpdate = true` 做全量更新，大 buffer 仍可能卡顿；可尝试 `updateRange` 做部分更新（但 Three 内对 updateRange 有 GC 与实现上的争议，需实测）。

### 2. Potree（八叉树 + LOD + 流式）

- **思路**：点云按八叉树组织，多级 LOD，视锥剔除，按需加载/卸载，点预算（如 100 万点）控制每帧实际绘制量。
- **适用**：超大规模、相对静态或可预处理的点云（如 5 亿+ 点）。
- **参考**：[Potree](https://github.com/potree/potree)、[Potree 论文](https://www.cg.tuwien.ac.at/research/publications/2016/SCHUETZ-2016-POT/)。
- **与当前场景**：更适合离线/预生成数据；实时 20 万点/秒流式若要做 LOD，需要服务端或前端实时抽稀，复杂度较高，可作为后续增强方向。

### 3. WebGPU + Compute / 实例化

- **思路**：用 Compute Shader 做点云处理或光栅化，或用 GPU 实例化绘制，减少 CPU→GPU 每帧传输与 draw call 开销。
- **参考**：
  - Three.js 示例：[webgpu_compute_points](https://threejs.org/examples/webgpu_compute_points.html)（约 30 万点）、[webgpu_instance_points](https://threejs.org/examples/webgpu_instance_points.html)
  - [m-schuetz/webgpu_pointcloud](https://github.com/m-schuetz/webgpu_pointcloud) 约 400 万点
- **与当前场景**：需浏览器支持 WebGPU，可作为中期方案；短期仍以 WebGL + 预分配 + 原地更新为主。

### 4. 压缩与传输

- **思路**：服务端对点云做压缩（如 GPU 友好 Huffman、Draco 等），前端解压后渲染，降低带宽与解析成本。
- **参考**：如 “Real-Time Decompression and Rasterization of Massive Point Clouds”（GPU 解压 + 光栅化）。
- **与当前场景**：若网络或解析不是主瓶颈，可先做渲染侧优化，再考虑压缩。

---

## 三、推荐落地顺序（针对「20 万点/秒实时显示」）

| 优先级 | 方案 | 说明 |
|--------|------|------|
| **P0** | **预分配 Buffer + 原地写入 + setDrawRange** | 在 PointCloudRenderer 中只创建一次足够大的 geometry，Worker 仍输出 positions/colors；主线程将数据 **拷贝进** 已有 `position.array` / `color.array`，再设 `attribute.needsUpdate = true` 或 `updateRange`，并用 `geometry.setDrawRange(0, count)`。避免每帧 new 新 Buffer 和替换 attribute。 |
| P1 | 降低 Worker 推送频率的“无效更新” | 若当前帧与上一帧点数/数据未变，可跳过 postMessage 或主线程跳过 setState，减少 React 与 Three 的无效重绘。 |
| P2 | 视情况尝试 updateRange 部分更新 | 若 Three 版本支持且实测有效，可只更新变更区间，进一步减少 GPU 上传量。 |
| P3 | ~~WebGPU / Potree 等~~ | **已启用**：点云渲染器使用 `three/webgpu` 的 WebGPURenderer，Chrome 等支持时走 WebGPU，否则自动回退 WebGL2。 |

---

## 四、实现要点（P0：预分配 + 原地更新）

1. **初始化（一次）**  
   - 分配 `Float32Array(MAX_POINTS * 3)` 给 position，`Float32Array(MAX_POINTS * 3)` 给 color。  
   - 创建 `BufferGeometry`，`setAttribute('position', new Float32BufferAttribute(positions, 3))` 等，并 `setDrawRange(0, 0)`。

2. **每帧更新**  
   - 从 Worker 拿到 `positions`、`colors`、`count`。  
   - 若 `count > MAX_POINTS` 则截断为 `MAX_POINTS`。  
   - 将 Worker 的 `positions`/`colors` **拷贝**到已存在的 `geom.attributes.position.array` / `geom.attributes.color.array`（例如 `array.set(workerPositions.subarray(0, count * 3))`）。  
   - 设置 `geom.attributes.position.needsUpdate = true`（及 color 同理），若使用 `updateRange` 则只设变更区间。  
   - `geom.setDrawRange(0, count)`。  
   - 不再每帧 `setAttribute(..., new Float32BufferAttribute(...))`。

3. **Worker 侧**  
   - 可保持现有二进制解析与滑动窗口逻辑；若希望减少主线程拷贝，可让 Worker 直接写入 SharedArrayBuffer（需考虑兼容性与安全策略），否则当前「Worker 传 Float32Array + 主线程拷贝进预分配 buffer」即可。

按上述方式改造后，渲染侧更易逼近 20 万点/秒的显示能力，再结合网络与后端优化做整体调优。

---

## 五、参考项目：webgpu_pointcloud 与「实时传输 + 实时渲染」的对应关系

**[m-schuetz/webgpu_pointcloud](https://github.com/m-schuetz/webgpu_pointcloud)** 是很好的 WebGPU 点云参考实现，但场景是「从 LAS 文件分批加载后渲染」，不是实时流。下面是对比与可借鉴点，便于在**实时传输 + 实时渲染**下沿用其思路。

### webgpu_pointcloud 在做什么

- **原始 WebGPU API**（无 Three.js）：`device.createBuffer`、`createRenderPipeline`、`primitiveTopology: 'point-list'`，顶点缓冲 position（float3）+ color（float4），每帧 `draw(n, 1, 0, 0)`。
- **数据来源**：从 LAS 文件通过 `LASLoader` 分批加载（每批约 10 万点），用 `buffer.setSubData(offset, batch)` 增量写入 GPU。
- **渲染**：单次 draw 绘制当前已加载点数 `n`，随加载进度 `n` 增加；MVP 用 uniform 每帧更新。

### 我们当前的「实时传输 + 实时渲染」管道

| 环节       | webgpu_pointcloud        | 本项（实时流） |
|------------|--------------------------|----------------|
| 数据来源   | LAS 文件，分批 load      | WebSocket 二进制帧，服务端按帧推送 |
| 解析与窗口 | 无（按文件顺序加载）     | Worker 内解析二进制、维护滑动时间窗口、节流输出 |
| 缓冲更新   | `setSubData(offset, batch)` 增量写 GPU | 主线程：Worker 输出 → 拷贝进预分配 `position`/`color` array → `needsUpdate` + `setDrawRange(0, count)` |
| 渲染 API   | 裸 WebGPU（pipeline + draw） | Three.js `three/webgpu`：`WebGPURenderer` + `Points`（底层同样是 point-list + 顶点缓冲） |
| 绘制方式   | 单次 `draw(n, 1, 0, 0)`  | 单次 Points 绘制，`setDrawRange(0, count)` 控制点数 |

### 可借鉴点（在保持实时流的前提下）

1. **point-list + 双缓冲**  
   与 webgpu_pointcloud 一致：position + color 两个顶点缓冲，单 draw 绘制当前窗口内所有点。我们已通过 Three.js `Points` + `BufferGeometry` 实现等价能力。

2. **增量/窗口更新思路**  
   他们用 `setSubData` 按「已加载范围」增量上传；我们是「滑动窗口」：只保留最近 N 秒内的点，每收到新帧在 Worker 里更新窗口，主线程只接收当前窗口的 positions/colors/count，拷贝进预分配 buffer 并更新 draw range。本质都是「只上传当前要画的那一段」。

3. **若未来要极致性能、可接受脱离 Three.js**  
   可参考 webgpu_pointcloud 的 pipeline 写一套**纯 WebGPU** 实时路径：  
   - 预创建两个 `GPUBuffer`（position、color），大小按最大点数（如 25 万）预分配；  
   - 每收到 Worker 的一帧：`queue.writeBuffer(positionBuffer, 0, positions)`（或 `setSubData` 等价），color 同理，再 `draw(count, 1, 0, 0)`；  
   - 相机/控制仍可用 DOM 或自己算 MVP 写入 uniform。  
   这样可避免 Three 的抽象层开销，但需要自行维护 resize、depth、swapchain 等，工程量较大。当前在「实时传输 + 实时渲染」已满足的前提下，继续用 Three.js WebGPU + 预分配 + 滑动窗口是更稳妥的折中。

### 小结

- **webgpu_pointcloud**：适合学习 WebGPU 下 point-list、顶点缓冲、增量上传的写法；场景是「文件分批加载」。  
- **本项**：同一类渲染思路（point-list、双缓冲、单 draw），但数据源改为 **实时 WebSocket → Worker 滑动窗口 → 主线程预分配 buffer 更新 → Three.js WebGPU 渲染**，实现「实时传输 + 实时渲染」。

---

## 六、为何之前无法稳定达到 20 万点 + 已做修正

### 瓶颈原因

- **收帧与刷新耦合**：Worker 里每收到一帧二进制就调用 `processPending()` → `flushWindow()`。服务端 20 万点/秒 ≈ 2000+ 帧/秒（每帧约 100 点），即 **每秒 2000+ 次** 对当前窗口（最多 20 万点）做「移除过期 + 合并 positions/colors + postMessage」。
- 单次 `flushWindow()` 是 O(窗口点数) 的，2000+ 次/秒 × 20 万点 ⇒ Worker 负担过大，无法跟上，表现为显示点数上不去或卡顿。

### 修正思路（秒级过渡 + 30fps + 20 万点丰富度）

1. **收帧与刷新解耦**
   - **收帧**：只做「解析二进制 + 入队 `frames`」，不再在收帧时调用 `flushWindow()`。
   - **刷新**：用 **定时器每 33ms（30fps）** 执行一次 `flushWindow()`，即「一边销毁过期帧、一边合并当前窗口、再 post 一次」。
   - 这样「接收」可以按 2000+ 帧/秒轻量处理，「合并 + 上传」固定 30 次/秒，Worker 能稳定维持约 20 万点窗口。

2. **过渡是秒级的**
   - 窗口按时间（如 1 秒）滑动：每 33ms 移除 `timestamp < now - windowMs` 的帧，新帧已在收帧时入队，实现「一边销毁一边接收」的秒级过渡。
   - 前后若干帧自然就是「旧点逐渐被移除、新点逐渐补上」，无需单独为「前后 3 帧」写特殊逻辑。

3. **30fps 与 20 万点丰富度**
   - 刷新率固定 30fps，主线程和 GPU 每帧只处理一次约 20 万点的 buffer 更新与绘制，延迟略增（约 33ms）可接受。
   - 窗口容量 `MAX_POINTS = 250000`、`windowMs = 1000`，1 秒 20 万点/秒时窗口内约 20 万点，显示稳定后即可保持 20 万点的丰富度。

---

## 七、上位机/Viewer 常见做法：接收与渲染不同步

参考 PCL、RViz、NVIDIA Holoscan 等实现，点云查看器普遍采用「接收与渲染解耦」，不把「收到一帧」和「画一帧」绑死在同一节奏。

### 1. PCL / PCLVisualizer（单视觉器 + 互斥更新）

- **单实例**：只维护一个 PCLVisualizer，不在每次收到点云时新建。
- **接收线程**：收到点云后写入共享 `cloud`，并设 `update = true`，用 **mutex** 保护。
- **渲染线程**：`spinOnce` 循环里先加锁，若 `update` 为 true 则调用 `updatePointCloud(cloud, ...)` 或 `addPointCloud`，然后置 `update = false`，再解锁。
- **要点**：不在回调里做重活、不阻塞；渲染线程按固定节奏取「当前最新一帧」更新显示。

参考：[Stream of Cloud Point Visualization using PCL](https://stackoverflow.com/questions/9003239/stream-of-cloud-point-visualization-using-pcl)、PCL CloudViewer 文档（CloudViewer 非线程安全，多线程用 PCLVisualizer + mutex）。

### 2. RViz（回调不阻塞 + 单 Display 更新）

- **单 Display 实例**：点云话题对应一个 PointCloud Display，不按消息新建。
- **回调**：只做「把消息转成内部点云 + 标记需要更新」，不在回调里 `spin()` 或长时间计算。
- **队列**：话题队列不宜过大（如 1000），否则高密度点云会堆积、占内存。
- **要点**：回调轻量；渲染/绘制在 RViz 主循环里按「是否有新数据」更新。

参考：ROS wiki rviz PointCloud、[Point Cloud not updating](https://stackoverflow.com/questions/63678442/point-cloud-not-updating)。

### 3. 双缓冲 / 三缓冲（Producer–Consumer）

- **双缓冲**：Buffer A 给 GPU 画，Buffer B 给 CPU 写；写完后 **原子交换** A/B，GPU 始终读「当前前台」缓冲。
- **三缓冲**：GPU 用 1、驱动准备 2、CPU 写 3，轮转，避免 CPU/GPU 争用同一块。
- **要点**：接收与渲染不同步；接收侧只负责往「空闲缓冲」写，渲染侧只读「当前前台」；交换时 O(1) 指针/引用交换，不做大块拷贝。

参考：NVIDIA Holoscan DoubleBufferReceiver、[Double Buffering vs Triple Buffering for Vertex Buffers](https://stackoverflow.com/questions/47563995/double-buffering-vs-triple-buffering-for-vertex-buffers)。

### 4. 在本项目中的对应设计（Web + Worker）

- **接收**：WebSocket → 主线程 → postMessage 给 Worker；Worker 解析、滑动窗口、按 30fps 向主线程 post 一帧（positions/colors/count，transfer）。
- **主线程**：收到 Worker 消息时 **只写 ref**（如 `pendingFrameRef.current = buf`），不在这条路径里做 setState/重计算，保持「接收路径」轻量。
- **渲染**：在 **同一 rAF 循环** 里、在 `renderAsync` 之前：若 `pendingFrameRef.current` 有值，则用其更新 Three 几何（setAttribute/setDrawRange），再清空 ref。这样「取当前待显示帧」与「画当前帧」在同一帧内完成，不依赖 React 的 useEffect 时机。
- **UI 点数**：由渲染侧在消费完一帧后通过回调（如 `onFrameConsumed(count)`）把当前显示点数回传给父组件，用于展示「约 20 万点」等，与几何更新解耦。
