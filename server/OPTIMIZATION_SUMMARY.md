# 系统架构优化总结

## 已完成的工作

### 1. ✅ SDK库文件目录规范化
- **操作**：合并`MakeAll/`目录到`lib/hikvision/`，删除重复的SDK文件
- **结果**：所有SDK库文件统一管理在`lib/`目录下
- **影响**：简化目录结构，避免库文件加载错误

### 2. ✅ 文件存储路径统一管理
- **操作**：创建`storage/`目录，整合`captures/`、`records/`、`downloads/`
- **新结构**：
  - `storage/captures/` - 抓图文件
  - `storage/records/` - 录制文件
  - `storage/downloads/` - 下载文件
- **代码更新**：更新所有相关代码中的路径引用
- **配置文件**：更新`config.yaml`中的`record_path`

### 3. ✅ Docker配置优化
- **更新文件**：
  - `docker-compose.yml` - 添加`storage`目录挂载
  - `docker-dev.sh` - 添加`storage`目录挂载
- **结果**：Docker容器可以正确访问新的存储目录

### 4. ✅ 多余文件清理
- **删除**：`server/CaptureTest.java`（重复的测试文件）
- **保留**：`src/main/java/com/digital/video/gateway/CaptureTest.java`（源码中的测试类）

### 5. ✅ 报警自动抓图功能实现
- **创建服务**：`AlarmService.java` - 报警处理服务
- **海康SDK集成**：在`com.digital.video.gateway.hikvision.HikvisionSDK.java`中添加报警回调支持
  - 实现`AlarmMessageCallback`类
  - 支持`COMM_ALARM`、`COMM_ALARM_V30`、`COMM_ALARM_V40`等报警类型
- **天地伟业SDK集成**：在`com.digital.video.gateway.tiandy.TiandySDK.java`中添加报警回调支持
  - 使用`ALARM_NOTIFY_V4`回调接口
- **主程序集成**：在`com.digital.video.gateway.Main.java`中初始化报警服务

### 6. ✅ OSS上传服务实现
- **创建服务**：`OssService.java` - OSS上传服务
- **功能**：
  - 支持阿里云OSS文件上传
  - 自动生成OSS文件URL
  - 支持配置开关
  - 集成到报警自动抓图流程中
- **主程序集成**：在`Main.java`中初始化OSS服务

## 待完成的工作

### 1. ⏳ 数据库设备管理兼容性优化
- **问题**：不同品牌设备字段规范需要统一
- **建议**：
  - 统一品牌字段值：`hikvision`、`tiandy`、`dahua`
  - 添加数据验证和迁移脚本
  - 统一设备ID生成规则

### 2. ⏳ 天地伟业SDK报警回调完善
- **当前状态**：已添加基础结构，需要完善设备ID查找逻辑
- **需要**：在`com.digital.video.gateway.tiandy.TiandySDK`中添加`setAlarmService`方法，完善报警回调实现

## 新功能说明

### 报警自动抓图
1. **触发条件**：设备发送报警信号（海康/天地伟业SDK）
2. **处理流程**：
   - 接收报警回调
   - 自动执行抓图
   - 如果OSS启用，自动上传到OSS
3. **配置**：通过`AlarmService.setEnabled()`控制开关

### OSS上传
1. **配置项**：在`config.yaml`中配置OSS参数
2. **自动上传**：报警抓图后自动上传
3. **手动上传**：可通过`OssService.uploadFile()`手动上传

## 配置文件更新

### config.yaml
```yaml
# 录制配置
recorder:
  record_path: "./storage/records"  # 已更新

# OSS配置（已存在，需要配置）
oss:
  enabled: false  # 设置为true启用
  endpoint: ""    # OSS端点
  access_key_id: ""
  access_key_secret: ""
  bucket_name: ""
  region: ""
```

## 目录结构变化

### 优化前
```
server/
├── lib/
│   ├── hikvision/
│   ├── tiandy/
│   └── dahua/
├── MakeAll/          # ❌ 已删除
├── captures/         # ❌ 已迁移
├── records/          # ❌ 已迁移
└── downloads/        # ❌ 已迁移
```

### 优化后
```
server/
├── lib/
│   ├── hikvision/    # ✅ 包含原MakeAll中的文件
│   ├── tiandy/
│   └── dahua/
└── storage/          # ✅ 新增统一存储目录
    ├── captures/
    ├── records/
    └── downloads/
```

## 注意事项

1. **数据迁移**：如果已有数据在旧目录，需要手动迁移到`storage/`目录
2. **Docker部署**：确保Docker配置中的`storage`目录挂载正确
3. **OSS配置**：需要配置正确的OSS参数才能使用上传功能
4. **报警回调**：确保设备已正确登录，报警回调才能正常工作

## 下一步建议

1. 完善天地伟业SDK的报警回调实现
2. 添加数据库迁移脚本，统一设备品牌字段
3. 添加报警历史记录功能
4. 优化OSS上传的重试机制
5. 添加报警抓图的配置选项（如通道号、图片质量等）
