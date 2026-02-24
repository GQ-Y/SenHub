# 摄像头核心功能测试套件

在**服务器环境**（192.168.1.210）执行，因需加载各品牌 SDK  native 库（海康、天地伟业、大华）。

## 测试设备与账号

| 品牌     | IP            | 端口 | 账号  | 密码      |
|----------|---------------|------|-------|-----------|
| 天地伟业 | 192.168.1.10  | 3000 | admin | zyckj2021 |
| 天地伟业 | 192.168.1.200 | 3000 | admin | zyckj2021 |
| 海康威视 | 192.168.1.100 | 8000 | admin | zyckj2021 |

大华暂无摄像头，暂不参与测试。运行测试前脚本会在服务器上对上述三个 IP 执行 ping，确保连通后再跑用例。

## 测试项说明

1. **摄像头连接登录**  
   对三台设备分别执行：登录 → 校验已登录状态 → 登出。

2. **断网重连**  
   登录 → 登出（模拟断网）→ 再次登录，校验重连成功。

3. **高速抓拍**  
   每台设备连续下发 10 次抓拍命令，校验至少半数成功并生成文件。

4. **录像下载**  
   创建最近 1 分钟时间段的录像下载任务；无录像时仅校验接口与任务状态正常。

5. **云台控制**  
   对每台设备下发云台“上”的 start/stop，校验接口调用；设备不支持云台时仅记录不按失败处理。

6. **设备信息**  
   登录后校验 `getDevice`、`getDeviceUserId` 等接口返回正确。

## 运行方式

### 在服务器上执行（推荐）

```bash
# 1. 先部署/同步代码（含测试代码）
./deploy_to_server.sh

# 2. 在服务器上仅运行测试（不启动主服务）
./run_tests_on_server.sh
```

`run_tests_on_server.sh` 会：同步 `server/.../gateway/test/` → 在服务器上 `mvn package -DskipTests` → 使用生成的 jar 执行 `com.digital.video.gateway.test.CameraTestRunner`。

### 登录服务器后手动执行

```bash
ssh zyc@192.168.1.210
cd /home/zyc/data/xwq/demo/server
export LD_LIBRARY_PATH="$(pwd)/lib/linux:$LD_LIBRARY_PATH"
JAR=$(find target -name "video-gateway-service-*.jar" -not -name "*original*" | head -1)
java -Djava.library.path="$(pwd)/lib/linux" -cp "$JAR" com.digital.video.gateway.test.CameraTestRunner
```

## 配置与代码位置

- 测试设备 IP/端口/账号密码：`TestConfig.java`
- 测试运行逻辑与用例编排：`CameraTestRunner.java`
- 部署时已同步测试目录：`deploy_to_server.sh` 会同步 `server/.../gateway/test/`

## 输出说明

- 每个用例结束会打印 `[PASS]` 或 `[FAIL]`。
- 结束时输出汇总：通过数、失败数；若有失败会列出原因并 exit 1。
