# Livox 雷达多设备支持架构分析

## 分析日期
2026-01-26

## 分析目标
1. 确认当前架构是否支持多雷达控制管理和点云接收
2. 确认当前架构是否支持多雷达的连接

---

## 一、当前架构概述

### 1.1 架构组件
- **RadarService**: 雷达服务主类，负责点云数据处理、设备管理、防区检测
- **LivoxDriver**: Livox SDK 驱动封装，负责 SDK 初始化和回调设置
- **LivoxJNI**: JNI 层，提供 native 方法调用
- **RadarWebSocketHandler**: WebSocket 推送处理器

### 1.2 设备管理数据结构

```java
// 设备状态管理（deviceId -> 状态）
private final Map<String, String> deviceStates = new ConcurrentHashMap<>(); // "collecting" 或 "detecting"
private final Map<String, BackgroundModel> loadedBackgrounds = new ConcurrentHashMap<>(); // deviceId -> BackgroundModel
private final Map<String, List<DefenseZone>> deviceZones = new ConcurrentHashMap<>(); // deviceId -> List<DefenseZone>
private final Map<String, String> deviceSerialMap = new ConcurrentHashMap<>(); // deviceId -> radarSerial
private final Map<String, Boolean> deviceConnectionStatus = new ConcurrentHashMap<>(); // deviceId -> connected

// SDK 设备信息缓存：handle -> (serial, ip)
private final Map<Integer, String[]> sdkDeviceInfoCache = new ConcurrentHashMap<>(); // handle -> [serial, ip]
private final Map<String, Integer> ipToHandleMap = new ConcurrentHashMap<>(); // ip -> handle
```

**结论**: ✅ 数据结构层面**支持多设备管理**，使用 `deviceId` 作为键，可以存储多个设备的状态、背景模型、防区等。

---

## 二、多雷达连接支持分析

### 2.1 SDK 层面支持

**Livox SDK 本身支持多设备连接**:
- SDK 通过 `handle` 区分不同设备
- `DeviceInfoChangeCallback` 回调会为每个连接的设备提供唯一的 `handle`
- 设备信息缓存已实现：`sdkDeviceInfoCache` (handle -> [serial, ip])

**代码证据**:
```java
// RadarService.java:130-141
LivoxJNI.setDeviceInfoCallback((handle, devType, serial, ip) -> {
    logger.info("SDK 设备信息回调: handle={}, devType={}, serial={}, ip={}", 
            handle, devType, serial, ip);
    
    // 缓存设备信息
    if (serial != null && ip != null) {
        sdkDeviceInfoCache.put(handle, new String[]{serial, ip});
        ipToHandleMap.put(ip, handle);
        
        // 更新数据库中的设备状态为在线
        updateDeviceStatusByIpOrSerial(ip, serial, true);
    }
});
```

**结论**: ✅ **SDK 层面支持多雷达连接**，每个设备有唯一的 `handle`，系统已缓存设备信息。

---

## 三、多雷达点云接收支持分析

### 3.1 点云回调接口

**PointCloudCallback 接口包含 handle**:
```java
// PointCloudCallback.java
public interface PointCloudCallback {
    void onPointCloud(int handle, int devType, int pointCount, int dataType, byte[] data);
}
```

**结论**: ✅ JNI 回调接口**支持 handle 参数**，可以区分不同雷达的点云数据。

### 3.2 问题：handle 信息丢失

**关键问题**:
```java
// LivoxDriver.java:368-381
private PointCloudCallback createLegacyAdapter(
        LegacyPointCloudCallback legacyCallback) {
    return new PointCloudCallback() {
        @Override
        public void onPointCloud(int handle, int devType, int pointCount, int dataType, byte[] data) {
            // 创建 SdkPacket 对象
            SdkPacket packet = new SdkPacket();
            packet.cmdType = 0x01; // MSG type
            packet.cmdId = dataType;
            packet.payload = data;
            // ⚠️ 问题：handle 信息丢失了！
            legacyCallback.onPointCloud(packet);
        }
    };
}
```

**SdkPacket 结构缺少 handle 字段**:
```java
// SdkPacket.java
public class SdkPacket {
    // ... 其他字段
    public byte[] payload;
    // ⚠️ 没有 handle 字段！
}
```

**结论**: ❌ **handle 信息在转换过程中丢失**，`SdkPacket` 不包含 `handle`，导致无法区分点云数据来源。

### 3.3 设备ID获取逻辑

**当前实现**:
```java
// RadarService.java:660-691
private String getCurrentDeviceId() {
    RadarDeviceDAO radarDeviceDAO = new RadarDeviceDAO(database.getConnection());
    List<RadarDevice> devices = radarDeviceDAO.getAll();

    if (devices.isEmpty()) {
        return null;
    } else if (devices.size() == 1) {
        // 只有一个雷达设备，直接使用
        return device.getDeviceId();
    } else {
        // 多个雷达设备，优先使用配置指定的，否则使用第一个
        String configuredDeviceId = database.getConfig("radar.current_device_id");
        if (configuredDeviceId != null && !configuredDeviceId.trim().isEmpty()) {
            // 检查配置的设备ID是否存在
            for (RadarDevice device : devices) {
                if (configuredDeviceId.equals(device.getDeviceId())) {
                    return device.getDeviceId();
                }
            }
        }
        // ⚠️ 问题：多个设备时，总是使用第一个设备！
        logger.debug("多个雷达设备，使用第一个: {}", first.getDeviceId());
        return first.getDeviceId();
    }
}
```

**结论**: ❌ **多设备时无法正确区分**，所有点云数据都被路由到第一个设备或配置的设备，无法根据实际数据来源（handle）进行区分。

---

## 四、总结

### 4.1 多雷达连接支持

| 项目 | 状态 | 说明 |
|------|------|------|
| SDK 层面 | ✅ 支持 | Livox SDK 通过 handle 区分设备 |
| 设备信息缓存 | ✅ 支持 | `sdkDeviceInfoCache` 和 `ipToHandleMap` 已实现 |
| 设备状态管理 | ✅ 支持 | 通过 `deviceId` 管理多个设备状态 |
| 数据库支持 | ✅ 支持 | `radar_devices` 表支持多设备存储 |

**结论**: ✅ **架构支持多雷达连接**，SDK 和数据结构层面都已准备好。

### 4.2 多雷达点云接收支持

| 项目 | 状态 | 说明 |
|------|------|------|
| JNI 回调接口 | ✅ 支持 | `PointCloudCallback` 包含 `handle` 参数 |
| handle 传递 | ❌ **不支持** | `SdkPacket` 缺少 `handle` 字段，信息丢失 |
| 设备ID映射 | ❌ **不支持** | `getCurrentDeviceId()` 无法根据 handle 区分设备 |
| 点云路由 | ❌ **不支持** | 所有点云数据都被路由到第一个/配置的设备 |

**结论**: ❌ **当前架构不支持多雷达点云接收**，存在以下问题：
1. `handle` 信息在回调转换时丢失
2. 无法根据 `handle` 映射到正确的 `deviceId`
3. 多设备时所有点云数据都被路由到同一个设备

---

## 五、修复建议

### 5.1 方案一：扩展 SdkPacket 添加 handle 字段（推荐）

**步骤**:
1. 修改 `SdkPacket.java`，添加 `handle` 字段
2. 修改 `LivoxDriver.createLegacyAdapter()`，传递 `handle` 到 `SdkPacket`
3. 修改 `RadarService.handlePacket()`，使用 `packet.handle` 获取设备ID
4. 实现 `getDeviceIdByHandle(int handle)` 方法，通过 handle 映射到 deviceId

**代码示例**:
```java
// SdkPacket.java
public class SdkPacket {
    public int handle; // 添加 handle 字段
    // ... 其他字段
}

// LivoxDriver.java
private PointCloudCallback createLegacyAdapter(
        LegacyPointCloudCallback legacyCallback) {
    return new PointCloudCallback() {
        @Override
        public void onPointCloud(int handle, int devType, int pointCount, int dataType, byte[] data) {
            SdkPacket packet = new SdkPacket();
            packet.handle = handle; // 传递 handle
            packet.cmdType = 0x01;
            packet.cmdId = dataType;
            packet.payload = data;
            legacyCallback.onPointCloud(packet);
        }
    };
}

// RadarService.java
private void handlePacket(SdkPacket packet) {
    // 根据 handle 获取设备ID
    String deviceId = getDeviceIdByHandle(packet.handle);
    if (deviceId == null) {
        logger.warn("无法找到 handle={} 对应的设备，忽略点云数据", packet.handle);
        return;
    }
    // ... 处理点云数据
}

private String getDeviceIdByHandle(int handle) {
    // 从缓存获取设备信息
    String[] info = sdkDeviceInfoCache.get(handle);
    if (info == null || info.length < 2) {
        return null;
    }
    String serial = info[0];
    String ip = info[1];
    
    // 通过 serial 或 ip 查找 deviceId
    RadarDeviceDAO dao = new RadarDeviceDAO(database.getConnection());
    RadarDevice device = dao.getBySerial(serial);
    if (device != null) {
        return device.getDeviceId();
    }
    
    // 如果 serial 找不到，通过 IP 查找
    List<RadarDevice> devices = dao.getAll();
    for (RadarDevice d : devices) {
        if (ip.equals(d.getRadarIp())) {
            return d.getDeviceId();
        }
    }
    
    return null;
}
```

### 5.2 方案二：直接使用 PointCloudCallback（不推荐）

跳过 `SdkPacket`，直接使用 `PointCloudCallback` 接口，但需要大量重构现有代码。

---

## 六、结论

### 6.1 多雷达连接
✅ **已支持** - SDK 和数据结构层面都已准备好，可以同时连接多个雷达设备。

### 6.2 多雷达点云接收
❌ **不支持** - 当前实现存在以下问题：
- `handle` 信息丢失
- 无法根据 `handle` 区分设备
- 所有点云数据被路由到同一个设备

### 6.3 修复优先级
🔴 **高优先级** - 需要修复才能支持多雷达点云接收，建议采用方案一进行修复。

---

## 七、测试建议

修复后需要测试：
1. 同时连接 2 个雷达设备
2. 验证每个设备的点云数据是否正确路由到对应的 `deviceId`
3. 验证 WebSocket 推送是否按设备区分
4. 验证防区检测是否按设备独立工作
5. 验证背景采集是否按设备独立工作
