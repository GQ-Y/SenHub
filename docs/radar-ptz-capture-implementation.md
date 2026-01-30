# 雷达点云报警PTZ联动抓拍功能实现

## 功能概述

实现了基于雷达点云报警的智能PTZ联动抓拍功能。当防区中出现点云报警时，系统能够：
1. 根据报警点云位置计算坐标系，联动球机对目标进行抓拍
2. 检测目标是否滞空，以及预测落地落点位置
3. 考虑PTZ转向和变焦的延迟，预测目标实际位置后再进行抓拍

## 实现架构

### 1. 目标跟踪服务 (TargetTrackingService)

**文件**: `server/src/main/java/com/digital/video/gateway/service/TargetTrackingService.java`

**功能**:
- 跟踪目标的运动轨迹，记录历史位置和速度
- 支持多目标同时跟踪
- 自动清理过期轨迹（5秒未更新）

**核心类**:
- `TrajectoryPoint`: 轨迹点，包含位置、时间戳和速度
- `TargetTrajectory`: 目标轨迹，管理轨迹点列表

### 2. 运动预测服务 (MotionPredictionService)

**文件**: `server/src/main/java/com/digital/video/gateway/service/MotionPredictionService.java`

**功能**:
- 检测目标运动状态：滞空(HOVERING)、下落(FALLING)、已落地(LANDED)、正常移动(MOVING)
- 预测目标未来位置（考虑重力加速度）
- 预测落地位置和落地时间
- 为PTZ联动提供位置预测（考虑PTZ延迟）

**核心方法**:
- `detectMotionState()`: 检测目标运动状态
- `predictPosition()`: 预测目标在未来某个时刻的位置
- `predictLanding()`: 预测目标落地位置和时间
- `predictForPTZ()`: 预测PTZ延迟后的目标位置

### 3. RadarService增强

**文件**: `server/src/main/java/com/digital/video/gateway/service/RadarService.java`

**新增功能**:
- 集成目标跟踪服务，在检测到侵入事件时更新目标轨迹
- 增强`handleIntrusionEvent()`方法，实现智能PTZ联动和抓拍

**核心流程**:

1. **目标跟踪更新** (`processDetection`方法):
   ```java
   // 更新目标跟踪
   for (IntrusionEvent event : events) {
       PointCluster cluster = event.getCluster();
       if (cluster != null && cluster.getCentroid() != null) {
           String targetId = event.getClusterId();
           targetTrackingService.updateTarget(targetId, cluster.getCentroid());
       }
   }
   ```

2. **智能PTZ联动** (`handleIntrusionEvent`方法):
   - 获取目标当前速度和轨迹
   - 使用运动预测服务预测PTZ延迟后的目标位置
   - 将预测位置转换到摄像头坐标系
   - 计算PTZ角度（pan, tilt）和变倍（zoom）
   - 驱动PTZ转向到预测位置

3. **智能抓拍策略** (`scheduleCapture`方法):
   - **下落中(FALLING)**: 
     - 如果落地时间小于PTZ延迟，等待落地后抓拍
     - 如果落地时间大于PTZ延迟，PTZ到位后立即抓拍
   - **滞空(HOVERING)**: PTZ到位后稍等片刻再抓拍，确保目标稳定
   - **已落地(LANDED)**: PTZ到位后立即抓拍
   - **正常移动(MOVING)**: PTZ到位后立即抓拍

## 关键技术点

### 1. 坐标系转换

使用`CoordinateTransform`类将雷达坐标系转换为摄像头坐标系：
- 支持平移、旋转、缩放变换
- 计算PTZ角度（水平角和垂直角）

### 2. 运动预测算法

**下落预测**:
- 考虑重力加速度（9.8 m/s²）
- 使用运动学公式：`z(t) = z0 + v0*t - 0.5*g*t²`
- 计算落地时间：`t = (v0 + sqrt(v0² + 2*g*Δz)) / g`

**位置预测**:
- X、Y方向：线性预测 `p(t) = p0 + v*t`
- Z方向：考虑重力加速度的影响

### 3. PTZ延迟补偿

默认PTZ延迟时间：0.5秒

系统会预测PTZ延迟后的目标位置，确保PTZ到位时能够准确对准目标。

### 4. 变倍计算

根据目标距离和运动状态自动调整变倍：
- 距离 > 50m: 3.0x
- 距离 > 20m: 2.0x
- 距离 > 10m: 1.5f
- 距离 ≤ 10m: 1.0x
- 下落中目标：额外放大1.2倍

## 配置说明

### PTZ延迟时间

在`RadarService.handleIntrusionEvent()`方法中可配置：
```java
float ptzDelaySeconds = 0.5f; // 默认0.5秒，可根据实际情况调整
```

### 运动状态检测阈值

在`MotionPredictionService`中可配置：
```java
private static final float HOVERING_VELOCITY_THRESHOLD = 0.5f; // 滞空速度阈值（米/秒）
private static final float HOVERING_HEIGHT_THRESHOLD = 0.5f;   // 滞空高度阈值（米）
private static final float GROUND_HEIGHT_THRESHOLD = 0.3f;     // 落地高度阈值（米）
```

## 使用说明

### 1. 初始化

系统启动时，`Main.java`会自动：
- 创建`TargetTrackingService`和`MotionPredictionService`
- 将`CaptureService`注入到`RadarService`

### 2. 工作流程

1. 雷达接收点云数据
2. 防区检测到侵入事件
3. 更新目标跟踪轨迹
4. 预测目标未来位置
5. 驱动PTZ转向到预测位置
6. 根据目标状态安排抓拍

### 3. 日志输出

系统会输出详细的日志信息：
- PTZ联动信息：目标ID、运动状态、PTZ角度、变倍、置信度
- 抓拍策略信息：抓拍延迟、目标状态
- 抓拍结果：成功/失败、文件路径

## 注意事项

1. **CaptureService配置**: 确保在`Main.java`中正确设置了`CaptureService`，否则抓拍功能不会执行
2. **PTZ延迟时间**: 根据实际球机性能调整`ptzDelaySeconds`参数
3. **轨迹清理**: 目标轨迹会在5秒未更新后自动清理，避免内存泄漏
4. **并发控制**: 抓拍任务使用独立的线程池执行，不会阻塞主流程

## 未来优化方向

1. **自适应延迟**: 根据PTZ实际到位时间动态调整延迟参数
2. **多目标跟踪**: 优化多目标同时出现时的跟踪和抓拍策略
3. **预测精度**: 使用更复杂的运动模型（如考虑空气阻力）提高预测精度
4. **抓拍质量**: 根据目标大小和距离自动调整抓拍参数（分辨率、曝光等）
