# 览沃 MID-360 雷达集成实施文档

## 1. 项目概述

### 1.1 目标
集成览沃（Livox）MID-360 多线激光雷达，实现基于3D点云的侵入检测和PTZ摄像头联动功能。

### 1.2 核心功能
- **背景建模**：采集并存储静态环境点云作为背景模型
- **防区设置**：通过缩小距离（向雷达中心）绘制虚拟防区
- **侵入检测**：实时检测防区内出现的物体
- **物体分析**：提取侵入物体的轮廓、位置、体积等特征
- **PTZ联动**：根据侵入物体位置自动控制摄像头转向和变焦
- **可视化**：前端实时渲染点云、背景模型和防区边界

---

## 2. SDK 文档资料

### 2.1 览沃官方文档
- **Livox SDK 官方文档**：https://livox-wiki-cn.readthedocs.io/zh_CN/latest/
- **MID-360 产品手册**：https://www.livoxtech.com/mid-360
- **Livox SDK GitHub**：https://github.com/Livox-SDK/Livox-SDK2
- **点云数据协议文档**：https://livox-wiki-cn.readthedocs.io/zh_CN/latest/introduction/protocol.html

### 2.2 关键协议说明
- **UDP 端口**：56186（默认）
- **数据格式**：Livox 自定义协议
- **点云数据类型**：
  - Type 0x01: 笛卡尔坐标点云（Cartesian）
  - 每个点 13 字节：x(4), y(4), z(4), reflectivity(1)

### 2.3 开发资源
- **Livox SDK Java 示例**：https://github.com/Livox-SDK/livox_ros_driver（ROS驱动，可参考协议实现）
- **点云处理库推荐**：
  - PCL (Point Cloud Library) - C++，可通过JNI调用
  - Java 3D 点云处理库（自研或使用第三方）

---

## 3. 系统架构设计

### 3.1 整体架构

```
┌─────────────────┐
│   前端界面      │  ← 点云可视化、防区配置、背景预览
│  (React)        │
└────────┬────────┘
         │ HTTP/WebSocket
┌────────▼─────────────────────────┐
│   后端服务 (Java SpringBoot/Netty) │
│  ┌──────────────────────────┐   │
│  │  Livox Driver (Netty)    │   │ ← 高性能 UDP 接收 (Netty UDP Server)
│  │  - 协议解析 (CRC16/32)    │   │
│  │  - 点云解包               │   │
│  └────────┬─────────────────┘   │
│           │ Pushed Data         │
│  ┌────────▼─────────────────┐   │
│  │  RadarService            │   │ ← 业务逻辑
│  │  - 背景建模               │   │
│  │  - 防区管理               │   │
│  │  - 侵入检测               │   │
│  └──────────────────────────┘   │
│  ┌──────────────────────────┐   │
│  │  RadarController         │   │ ← REST API
│  └──────────────────────────┘   │
│  ┌──────────────────────────┐   │
│  │  AlarmService            │   │ ← 报警处理、PTZ联动
│  └──────────────────────────┘   │
└────────┬─────────────────────────┘
         │ UDP (55000:Broadcast, 56000:Cmd, DataPort)
┌────────▼────────┐
│  MID-360 雷达   │
└─────────────────┘
```

### 3.2 数据流

1. **协议层 (Low-Level)**：
   - 使用 **Netty** 构建 UDP Server，监听数据端口（如 56001/56002）。
   - 实现 `CRC16` (Header) 和 `CRC32` (Body) 校验算法。
   - 解析 Livox SDK2 协议包，提取笛卡尔坐标点云 (Type 0x01)。

2. **业务层 (High-Level)**：
   - `Driver` 将原始点云转换为统一的 `PointCloudFrame` 对象推送给 `RadarService`。
   - `RadarService` 执行背景建模或侵入检测逻辑。

---

## 4. 驱动开发实施 (Java Native)

鉴于官方未提供 Java SDK，我们将采用 **纯 Java UDP 直连** 方案。

### 4.1 协议定义 (基于 SDK2)

### 4.1 协议定义 (基于 SDK2 源码)

**Header (Wrapper) 结构 - 24 Bytes (Little Endian)**:
- SOF (1 byte): `0xAA` (Fixed)
- Version (1 byte): `0x00` (Mid-360)
- Length (2 bytes): 整个包长度 (Header + Body + CRC32)
- SeqNum (4 bytes): 序列号 (`uint32_t`)
- CmdId (2 bytes): 指令 ID (`uint16_t`)
- CmdType (1 byte): `0x00`(Req), `0x01`(Ack), `0x02`(Msg)
- SenderType (1 byte): 发送方类型 (`0x00`: Host, `0x01`: Lidar)
- Rsvd (6 bytes): 保留字段 (全0)
- CRC16 (2 bytes): 校验 Header 前18个字节 (SOF -> Rsvd)。算法：Standard CRC16-CCITT (Init `0xFFFF`)

**Body 结构**:
- Payload (N bytes): 具体指令数据
- CRC32 (4 bytes): 校验 Payload。算法：Standard CRC32 (Init `0xFFFFFFFF`)


### 4.2 关键类设计 (已完成)

**`PacketDecoder.java`**:
- Netty `SimpleChannelInboundHandler` 实现。
- 负责校验 `SOF (0xAA)`, `Length`, `CRC16`。
- 输出 `SdkPacket` 对象。

**`LivoxDriver.java`**:
- 管理 Netty UDP Server (Binding 55000)。
- 负责发送指令 (To 56000)。
- 提供 `setPointCloudCallback` 供上层业务订阅点云。

**`RadarService.java`**:
- 业务入口，初始化 `LivoxDriver`。
- 订阅点云数据，解析笛卡尔坐标 (Type 0x01)。
- 执行简单的侵入检测和 PTZ 联动。

### 4.3当前状态
- [x] Java Netty 驱动依赖添加
- [x] SdkPacket/Protocol 定义
- [x] PacketDecoder 实现
- [x] LivoxDriver 实现
- [x] RadarService 集成
- [ ] 真实环境联调



---

## 4. 数据库设计

### 4.1 新增表结构

#### 4.1.1 `radar_backgrounds` - 背景模型表
```sql
CREATE TABLE radar_backgrounds (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    background_id TEXT UNIQUE NOT NULL,
    device_id TEXT NOT NULL,
    assembly_id TEXT,
    frame_count INTEGER NOT NULL,
    point_count INTEGER NOT NULL,
    grid_resolution REAL DEFAULT 0.1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status TEXT DEFAULT 'collecting', -- collecting, ready, expired
    FOREIGN KEY (device_id) REFERENCES devices(device_id),
    FOREIGN KEY (assembly_id) REFERENCES assemblies(assembly_id)
);
```

#### 4.1.2 `radar_background_points` - 背景点云数据表
```sql
CREATE TABLE radar_background_points (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    background_id TEXT NOT NULL,
    grid_key TEXT NOT NULL, -- 网格索引键，格式: "x_y_z"
    center_x REAL NOT NULL,
    center_y REAL NOT NULL,
    center_z REAL NOT NULL,
    point_count INTEGER NOT NULL,
    mean_distance REAL,
    std_deviation REAL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (background_id) REFERENCES radar_backgrounds(background_id),
    UNIQUE(background_id, grid_key)
);
CREATE INDEX idx_background_grid ON radar_background_points(background_id, grid_key);
```

#### 4.1.3 `radar_defense_zones` - 防区配置表
```sql
CREATE TABLE radar_defense_zones (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    zone_id TEXT UNIQUE NOT NULL,
    device_id TEXT NOT NULL,
    assembly_id TEXT,
    background_id TEXT NOT NULL,
    shrink_distance_cm INTEGER NOT NULL, -- 缩小距离（厘米），0表示无防区
    enabled BOOLEAN DEFAULT 1,
    name TEXT,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(device_id),
    FOREIGN KEY (assembly_id) REFERENCES assemblies(assembly_id),
    FOREIGN KEY (background_id) REFERENCES radar_backgrounds(background_id),
    CHECK (shrink_distance_cm >= 0 AND shrink_distance_cm % 2 == 0 OR shrink_distance_cm == 0)
);
```

#### 4.1.4 `radar_intrusion_records` - 侵入记录表
```sql
CREATE TABLE radar_intrusion_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    record_id TEXT UNIQUE NOT NULL,
    device_id TEXT NOT NULL,
    assembly_id TEXT,
    zone_id TEXT,
    cluster_id TEXT,
    centroid_x REAL NOT NULL,
    centroid_y REAL NOT NULL,
    centroid_z REAL NOT NULL,
    volume REAL,
    bbox_min_x REAL,
    bbox_min_y REAL,
    bbox_min_z REAL,
    bbox_max_x REAL,
    bbox_max_y REAL,
    bbox_max_z REAL,
    point_count INTEGER,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(device_id),
    FOREIGN KEY (assembly_id) REFERENCES assemblies(assembly_id),
    FOREIGN KEY (zone_id) REFERENCES radar_defense_zones(zone_id)
);
CREATE INDEX idx_intrusion_device_time ON radar_intrusion_records(device_id, detected_at);
```

---

## 5. 后端 API 接口设计

### 5.1 背景建模接口

#### 5.1.1 开始采集背景
```http
POST /api/radar/:deviceId/background/start
Content-Type: application/json

{
  "durationSeconds": 30,  // 采集时长（秒），默认30秒
  "gridResolution": 0.1  // 网格分辨率（米），默认0.1米
}

Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "backgroundId": "bg_xxx",
    "status": "collecting",
    "estimatedTime": 30
  }
}
```

#### 5.1.2 停止采集背景
```http
POST /api/radar/:deviceId/background/stop
Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "backgroundId": "bg_xxx",
    "status": "ready",
    "frameCount": 300,
    "pointCount": 50000
  }
}
```

#### 5.1.3 获取背景采集状态
```http
GET /api/radar/:deviceId/background/status
Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "backgroundId": "bg_xxx",
    "status": "collecting", // collecting, ready, expired
    "progress": 0.6,  // 0-1
    "frameCount": 180,
    "estimatedRemainingSeconds": 12
  }
}
```

#### 5.1.4 获取背景点云数据（用于前端渲染）
```http
GET /api/radar/:deviceId/background/:backgroundId/points
Query Parameters:
  - limit: 最大点数（默认10000，用于性能优化）
  - gridOnly: 是否只返回网格中心点（默认true）

Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "backgroundId": "bg_xxx",
    "gridResolution": 0.1,
    "points": [
      {
        "x": 1.2,
        "y": 0.5,
        "z": 0.3,
        "count": 15  // 该网格的点数
      },
      ...
    ],
    "totalPoints": 50000,
    "totalGrids": 3500
  }
}
```

#### 5.1.5 删除背景模型
```http
DELETE /api/radar/:deviceId/background/:backgroundId
Response:
{
  "code": 200,
  "message": "success"
}
```

### 5.2 防区管理接口

#### 5.2.1 创建防区
```http
POST /api/radar/:deviceId/zones
Content-Type: application/json

{
  "backgroundId": "bg_xxx",
  "shrinkDistanceCm": 20,  // 缩小距离（厘米），最小5cm，以2cm递增，0表示无防区
  "name": "主入口防区",
  "description": "检测主入口区域的侵入",
  "enabled": true
}

Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "zoneId": "zone_xxx",
    "shrinkDistanceCm": 20,
    "enabled": true,
    "createdAt": "2024-01-01T10:00:00Z"
  }
}
```

#### 5.2.2 更新防区
```http
PUT /api/radar/:deviceId/zones/:zoneId
Content-Type: application/json

{
  "shrinkDistanceCm": 30,  // 更新缩小距离
  "name": "主入口防区（更新）",
  "enabled": true
}
```

#### 5.2.3 获取防区列表
```http
GET /api/radar/:deviceId/zones
Response:
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "zoneId": "zone_xxx",
      "backgroundId": "bg_xxx",
      "shrinkDistanceCm": 20,
      "name": "主入口防区",
      "enabled": true,
      "createdAt": "2024-01-01T10:00:00Z"
    }
  ]
}
```

#### 5.2.4 获取防区详情（包含防区边界点云）
```http
GET /api/radar/:deviceId/zones/:zoneId
Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "zoneId": "zone_xxx",
    "backgroundId": "bg_xxx",
    "shrinkDistanceCm": 20,
    "name": "主入口防区",
    "enabled": true,
    "boundaryPoints": [  // 防区边界点云（用于前端渲染）
      {
        "x": 1.0,
        "y": 0.4,
        "z": 0.2
      },
      ...
    ],
    "createdAt": "2024-01-01T10:00:00Z"
  }
}
```

#### 5.2.5 删除防区
```http
DELETE /api/radar/:deviceId/zones/:zoneId
```

#### 5.2.6 启用/禁用防区
```http
PUT /api/radar/:deviceId/zones/:zoneId/toggle
```

### 5.3 实时点云数据接口（WebSocket）

#### 5.3.1 WebSocket 连接
```javascript
// 前端连接
const ws = new WebSocket('ws://localhost:8084/api/radar/:deviceId/stream');

// 订阅消息
ws.send(JSON.stringify({
  type: 'subscribe',
  topics: ['pointcloud', 'intrusion', 'status']
}));
```

#### 5.3.2 消息格式

**点云数据消息**：
```json
{
  "type": "pointcloud",
  "timestamp": 1234567890,
  "points": [
    {"x": 1.2, "y": 0.5, "z": 0.3, "r": 128},
    ...
  ],
  "pointCount": 5000
}
```

**侵入检测消息**：
```json
{
  "type": "intrusion",
  "timestamp": 1234567890,
  "zoneId": "zone_xxx",
  "clusters": [
    {
      "clusterId": "cluster_xxx",
      "centroid": {"x": 1.5, "y": 0.6, "z": 0.4},
      "pointCount": 150,
      "volume": 0.05,
      "bbox": {
        "min": {"x": 1.3, "y": 0.4, "z": 0.2},
        "max": {"x": 1.7, "y": 0.8, "z": 0.6}
      }
    }
  ]
}
```

### 5.4 侵入记录查询接口

#### 5.4.1 获取侵入记录列表
```http
GET /api/radar/:deviceId/intrusions
Query Parameters:
  - zoneId: 防区ID（可选）
  - startTime: 开始时间（ISO 8601）
  - endTime: 结束时间（ISO 8601）
  - page: 页码（默认1）
  - pageSize: 每页数量（默认20）

Response:
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "recordId": "rec_xxx",
        "zoneId": "zone_xxx",
        "centroid": {"x": 1.5, "y": 0.6, "z": 0.4},
        "pointCount": 150,
        "volume": 0.05,
        "detectedAt": "2024-01-01T10:00:00Z"
      }
    ],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```

---

## 6. 前端界面设计

### 6.1 背景采集界面

#### 6.1.1 界面布局
```
┌─────────────────────────────────────┐
│  雷达背景采集                        │
├─────────────────────────────────────┤
│  [设备选择: MID-360-001 ▼]          │
│                                      │
│  采集设置:                           │
│  ○ 采集时长: [30] 秒                │
│  ○ 网格精度: [0.1] 米               │
│                                      │
│  [开始采集] [停止采集] [预览背景]    │
│                                      │
│  采集状态:                           │
│  ████████░░ 60% (18/30秒)           │
│  已采集帧数: 180                     │
│  预计剩余: 12秒                      │
│                                      │
│  ┌──────────────────────────────┐   │
│  │    3D点云预览区域            │   │
│  │    (Three.js渲染)            │   │
│  │                              │   │
│  │    [点云可视化]              │   │
│  │    [网格显示]                │   │
│  │    [统计信息]                │   │
│  └──────────────────────────────┘   │
└─────────────────────────────────────┘
```

#### 6.1.2 功能点
- 实时显示采集进度
- 3D点云实时渲染（使用Three.js）
- 网格可视化选项
- 采集完成后可预览和保存背景模型

### 6.2 防区配置界面

#### 6.2.1 界面布局
```
┌─────────────────────────────────────┐
│  防区配置                            │
├─────────────────────────────────────┤
│  背景模型: [bg_xxx ▼]                │
│                                      │
│  防区列表:                           │
│  ┌──────────────────────────────┐   │
│  │ ☑ 主入口防区                  │   │
│  │    缩小距离: 20cm             │   │
│  │    [编辑] [删除]              │   │
│  ├──────────────────────────────┤   │
│  │ ☐ 侧门防区                    │   │
│  │    缩小距离: 30cm             │   │
│  │    [编辑] [删除]              │   │
│  └──────────────────────────────┘   │
│                                      │
│  [新建防区]                          │
│                                      │
│  ┌──────────────────────────────┐   │
│  │    3D防区可视化              │   │
│  │                              │   │
│  │    [背景点云]                │   │
│  │    [防区边界]                │   │
│  │    [缩小效果预览]            │   │
│  │                              │   │
│  │    [重置视角] [切换显示]     │   │
│  └──────────────────────────────┘   │
└─────────────────────────────────────┘
```

#### 6.2.2 防区创建/编辑表单
```
┌─────────────────────────────────────┐
│  新建防区                            │
├─────────────────────────────────────┤
│  防区名称: [________________]        │
│  描述: [____________________]        │
│                                      │
│  缩小距离设置:                       │
│  ┌──────────────────────────────┐   │
│  │ 滑块: |----●----|             │   │
│  │ 当前值: 20 cm                 │   │
│  │                               │   │
│  │ 选项:                          │   │
│  │ ○ 无防区 (0cm)                │   │
│  │ ○ 5cm                         │   │
│  │ ○ 7cm                         │   │
│  │ ○ 9cm                         │   │
│  │ ...                           │   │
│  │ ● 20cm (当前)                 │   │
│  │ ○ 22cm                        │   │
│  │ ...                           │   │
│  │ ○ 100cm                       │   │
│  └──────────────────────────────┘   │
│                                      │
│  说明:                                │
│  - 最小缩小距离: 5cm                 │
│  - 递增步长: 2cm                     │
│  - 0cm表示无防区（使用原始背景）     │
│                                      │
│  [取消] [保存]                       │
└─────────────────────────────────────┘
```

#### 6.2.3 3D可视化要求
- **背景点云渲染**：使用不同颜色显示背景点云
- **防区边界渲染**：显示缩小后的防区边界（半透明）
- **对比视图**：可切换显示原始背景和缩小后的防区
- **交互控制**：旋转、缩放、平移视角

### 6.3 实时监控界面

#### 6.3.1 界面布局
```
┌─────────────────────────────────────┐
│  雷达实时监控                        │
├─────────────────────────────────────┤
│  [设备: MID-360-001] [防区: 主入口] │
│                                      │
│  ┌──────────────────────────────┐   │
│  │    实时点云渲染              │   │
│  │                              │   │
│  │    [背景点云]                │   │
│  │    [当前点云]                │   │
│  │    [侵入物体] (红色高亮)     │   │
│  │    [防区边界]                │   │
│  │                              │   │
│  │    [播放/暂停] [重置]         │   │
│  └──────────────────────────────┘   │
│                                      │
│  侵入检测:                           │
│  ┌──────────────────────────────┐   │
│  │ ⚠ 检测到侵入 (10:30:15)      │   │
│  │   位置: (1.5m, 0.6m, 0.4m)   │   │
│  │   体积: 0.05 m³              │   │
│  │   [查看详情] [PTZ联动]       │   │
│  └──────────────────────────────┘   │
└─────────────────────────────────────┘
```

---

## 7. 核心算法实现

### 7.1 背景建模算法

```java
/**
 * 背景点云累积和统计
 */
public class BackgroundModelBuilder {
    private float gridResolution = 0.1f; // 网格分辨率
    private Map<String, GridStatistics> gridStats = new ConcurrentHashMap<>();
    
    /**
     * 累积点云到背景模型
     */
    public void accumulatePoints(List<Point> points) {
        for (Point p : points) {
            String gridKey = getGridKey(p);
            gridStats.computeIfAbsent(gridKey, k -> new GridStatistics())
                    .addPoint(p);
        }
    }
    
    /**
     * 构建最终背景模型
     */
    public BackgroundModel build() {
        BackgroundModel model = new BackgroundModel();
        for (Map.Entry<String, GridStatistics> entry : gridStats.entrySet()) {
            BackgroundPoint bgPoint = entry.getValue().toBackgroundPoint();
            model.addPoint(bgPoint);
        }
        return model;
    }
    
    private String getGridKey(Point p) {
        int gx = (int) (p.x / gridResolution);
        int gy = (int) (p.y / gridResolution);
        int gz = (int) (p.z / gridResolution);
        return gx + "_" + gy + "_" + gz;
    }
}
```

### 7.2 防区缩小算法

```java
/**
 * 根据缩小距离计算防区边界
 */
public class DefenseZoneCalculator {
    /**
     * 计算缩小后的防区点云
     * @param backgroundPoints 背景点云
     * @param shrinkDistanceCm 缩小距离（厘米）
     * @return 缩小后的防区边界点云
     */
    public List<Point> calculateZoneBoundary(
            List<BackgroundPoint> backgroundPoints, 
            int shrinkDistanceCm) {
        
        if (shrinkDistanceCm == 0) {
            // 无防区，返回原始背景
            return backgroundPoints.stream()
                    .map(bp -> new Point(bp.getCenterX(), bp.getCenterY(), bp.getCenterZ()))
                    .collect(Collectors.toList());
        }
        
        float shrinkDistance = shrinkDistanceCm / 100.0f; // 转换为米
        List<Point> shrunkPoints = new ArrayList<>();
        
        for (BackgroundPoint bgPoint : backgroundPoints) {
            Point original = new Point(
                    bgPoint.getCenterX(), 
                    bgPoint.getCenterY(), 
                    bgPoint.getCenterZ()
            );
            
            // 计算到雷达原点的距离
            float distance = (float) Math.sqrt(
                    original.x * original.x + 
                    original.y * original.y + 
                    original.z * original.z
            );
            
            if (distance > shrinkDistance) {
                // 向雷达中心缩小
                float scale = (distance - shrinkDistance) / distance;
                Point shrunk = new Point(
                        original.x * scale,
                        original.y * scale,
                        original.z * scale
                );
                shrunkPoints.add(shrunk);
            }
            // 距离小于缩小距离的点被过滤掉（在防区内部）
        }
        
        return shrunkPoints;
    }
}
```

### 7.3 侵入检测算法

```java
/**
 * 侵入检测核心算法
 */
public class IntrusionDetector {
    /**
     * 检测防区内的侵入物体
     */
    public List<PointCluster> detectIntrusion(
            List<Point> currentPoints,
            DefenseZone zone) {
        
        List<Point> intrusionPoints = new ArrayList<>();
        
        // 1. 过滤当前点云，只保留防区内的点
        for (Point p : currentPoints) {
            if (isPointInZone(p, zone)) {
                intrusionPoints.add(p);
            }
        }
        
        // 2. 从防区点中移除背景点（差分）
        List<Point> newPoints = subtractBackground(intrusionPoints, zone.getBackground());
        
        // 3. 对侵入点进行聚类
        return clusterPoints(newPoints);
    }
    
    /**
     * 判断点是否在防区内
     */
    private boolean isPointInZone(Point p, DefenseZone zone) {
        // 计算点到雷达原点的距离
        float distance = (float) Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z);
        float zoneDistance = zone.getShrinkDistance();
        
        // 检查点是否在防区边界外（即侵入区域）
        return distance > zoneDistance;
    }
}
```

---

## 8. 实施步骤

### 8.1 第一阶段：基础功能（1-2周）

1. **数据库表创建**
   - 创建 `radar_backgrounds` 表
   - 创建 `radar_background_points` 表
   - 创建 `radar_defense_zones` 表
   - 创建 `radar_intrusion_records` 表

2. **后端基础服务**
   - 完善 `RadarService` 的点云接收功能
   - 实现背景采集接口
   - 实现背景存储和查询

3. **前端基础界面**
   - 创建背景采集页面
   - 集成 Three.js 进行点云渲染
   - 实现采集进度显示

### 8.2 第二阶段：防区功能（1-2周）

1. **后端防区服务**
   - 实现防区创建、更新、删除接口
   - 实现防区边界计算算法
   - 实现防区点云数据查询

2. **前端防区配置**
   - 创建防区配置页面
   - 实现缩小距离选择器（5cm起步，2cm递增）
   - 实现防区边界可视化

### 8.3 第三阶段：侵入检测（2-3周）

1. **后端检测算法**
   - 实现背景差分算法
   - 实现点云聚类算法（DBSCAN）
   - 实现侵入物体特征提取
   - 集成报警服务

2. **WebSocket 实时推送**
   - 实现点云数据实时推送
   - 实现侵入检测结果推送

3. **前端实时监控**
   - 创建实时监控页面
   - 实现点云实时渲染
   - 实现侵入物体高亮显示

### 8.4 第四阶段：优化和测试（1-2周）

1. **性能优化**
   - 点云数据压缩
   - 空间索引优化（KD-Tree/Octree）
   - 前端渲染性能优化

2. **功能完善**
   - 侵入记录查询
   - 统计报表
   - 参数调优界面

3. **测试和文档**
   - 单元测试
   - 集成测试
   - API文档完善

---

## 9. 技术栈

### 9.1 后端
- **语言**: Java 11+
- **框架**: Spark Java (HTTP服务器)
- **数据库**: SQLite (可扩展至PostgreSQL)
- **点云处理**: 自研算法（可考虑集成PCL via JNI）
- **WebSocket**: Jetty WebSocket

### 9.2 前端
- **框架**: React + TypeScript
- **3D渲染**: Three.js
- **UI组件**: Tailwind CSS
- **状态管理**: React Hooks
- **WebSocket**: native WebSocket API

### 9.3 点云处理库推荐
- **Java**: 
  - 自研轻量级点云处理库
  - 或使用 JNI 调用 PCL (C++)
- **前端**:
  - Three.js (点云渲染)
  - Potree (大规模点云可视化，可选)

---

## 10. 参数配置说明

### 10.1 缩小距离参数规则

| 参数值 | 说明 |
|--------|------|
| 0 cm | 无防区，使用原始背景边界 |
| 5 cm | 最小防区，向雷达中心缩小5cm |
| 7 cm | 以2cm递增 |
| 9 cm | 以2cm递增 |
| ... | ... |
| 100 cm | 最大建议值（可根据实际调整） |

**验证规则**：
- 最小值：5cm
- 最大值：无限制（建议不超过背景最大距离的50%）
- 步长：2cm
- 特殊值：0表示无防区

### 10.2 其他关键参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 网格分辨率 | 0.1m | 背景点云网格化精度 |
| 背景采集时长 | 30秒 | 建立背景模型所需时间 |
| 聚类距离阈值 | 0.3m | DBSCAN算法的eps参数 |
| 最小聚类点数 | 5 | 过滤噪声的最小点数 |
| 点云采样率 | 100% | 可降低以提高性能 |

---

## 11. 注意事项

### 11.1 性能考虑
- **点云数据量大**：单帧可能包含数万个点，需要合理采样和压缩
- **实时性要求**：检测延迟应控制在100ms以内
- **存储优化**：背景点云使用网格化存储，减少存储空间

### 11.2 精度考虑
- **雷达标定**：确保雷达坐标系与摄像头坐标系对齐
- **环境变化**：背景模型需要定期更新（如光照、温度变化）
- **噪声处理**：需要有效的噪声过滤算法

### 11.3 用户体验
- **可视化性能**：前端渲染大量点云时可能卡顿，需要LOD（细节层次）优化
- **配置简化**：防区配置界面要直观易用
- **实时反馈**：采集和检测过程要有清晰的进度提示

---

## 12. 参考资料

1. **Livox SDK 文档**: https://livox-wiki-cn.readthedocs.io/
2. **Three.js 文档**: https://threejs.org/docs/
3. **点云处理算法**:
   - DBSCAN聚类: https://en.wikipedia.org/wiki/DBSCAN
   - 空间索引: KD-Tree, Octree
4. **点云可视化**:
   - Potree: https://github.com/potree/potree

---

## 13. 版本历史

| 版本 | 日期 | 作者 | 说明 |
|------|------|------|------|
| 1.0 | 2024-01-01 | System | 初始版本 |

---

**文档维护**: 本文档应随实施进度持续更新，记录实际开发中的变更和优化。
