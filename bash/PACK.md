# SenHub 一键安装所需压缩包打包说明

在**已编译好**的 SenHub 服务端 `server` 目录下执行以下命令，生成两个压缩包。上传到远程后，将下载 URL 填入 `install.sh` 顶部变量 `SENHUB_APP_URL`、`SENHUB_LIBS_URL`。

## 环境要求

- 已执行 `mvn clean package -DskipTests`
- 构建产物为 `target/senhub-app-1.0.0.jar`（pom.xml 已使用 SenHub 品牌 artifactId：senhub-app）
- 已具备完整 `lib/` 目录（x86/tiandy、x86/hikvision、x86/dahua、linux）

---

## 压缩包 1：SenHub 应用包

**文件名**：`SenHub-app-1.0.0.tar.gz`

**包含内容**：

| 路径 | 说明 |
|------|------|
| `target/senhub-app-1.0.0.jar` | 主程序 JAR（Maven 构建产物） |
| `src/main/resources/config.yaml` | 默认配置模板 |
| `test/fanguangyi.png` | 流程测试用图片 |
| `lib/x86/tiandy/sdk_log_config.ini` | 天地伟业 SDK 日志配置 |

**打包命令**（在 server 目录下执行）：

```bash
cd /path/to/demo/server

tar -czf /tmp/SenHub-app-1.0.0.tar.gz \
  target/senhub-app-1.0.0.jar \
  src/main/resources/config.yaml \
  test/fanguangyi.png \
  lib/x86/tiandy/sdk_log_config.ini
```

---

## 压缩包 2：SenHub SDK 库包

**文件名**：`Senhub-libs.tar.gz`

**包含内容**：

| 路径 | 说明 |
|------|------|
| `lib/x86/tiandy/` | 天地伟业 SDK（约 112M，含子目录 `lib/`） |
| `lib/x86/hikvision/` | 海康威视 SDK（约 26M） |
| `lib/x86/dahua/` | 大华 SDK（约 121M） |
| `lib/linux/` | Livox 雷达 SDK（约 4.8M） |

**打包命令**（在 server 目录下执行）：

```bash
cd /mnt/data/zyc/xwq/demo/server

tar -czf /tmp/Senhub-libs.tar.gz lib/
```

---

## 打包产物

- 应用包：`/tmp/SenHub-app-1.0.0.tar.gz`
- 库包：`/tmp/Senhub-libs.tar.gz`

将两个文件上传到可公网或内网下载的地址，把**应用包下载 URL** 和 **库包下载 URL** 填入 `install.sh` 中的 `SENHUB_APP_URL` 与 `SENHUB_LIBS_URL`，在新机器上执行 `sudo bash install.sh` 即可完成 SenHub 一键安装。
