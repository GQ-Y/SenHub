# 天地伟业 SDK Linux 部署指南

## 1. LD_LIBRARY_PATH 环境变量配置

天地伟业 SDK 在 Linux 环境下运行时，**必须设置 `LD_LIBRARY_PATH` 环境变量**，否则 SDK 内部的依赖库（解码器、FFmpeg 等）无法被系统动态链接器找到。

> 仅设置 Java 的 `-Djava.library.path` 只能让 JNA 加载主库 `libnvssdk.so`，但 SDK 内部二次依赖的 `libavdecsdk.so` → `libavcodec.so` 等链式加载仍依赖系统的 `LD_LIBRARY_PATH`。

### 配置方式

**方式一：启动时设置（推荐）**

```bash
TIANDY_LIB="/path/to/server/lib/x86/tiandy"
TIANDY_LIB_SUB="$TIANDY_LIB/lib"

export LD_LIBRARY_PATH="$TIANDY_LIB:$TIANDY_LIB_SUB:${LD_LIBRARY_PATH}"

java -Djava.library.path="$TIANDY_LIB:$TIANDY_LIB_SUB" \
     -jar senhub-app-1.0.0.jar
```

**方式二：拷贝到系统 lib 目录**

```bash
sudo cp /path/to/server/lib/x86/tiandy/*.so /usr/lib/
sudo cp /path/to/server/lib/x86/tiandy/lib/*.so /usr/lib/
sudo ldconfig
```

### 库文件目录结构

```
server/lib/x86/tiandy/
├── libnvssdk.so          # SDK 主库
├── libavdecsdk.so        # 解码器
├── libavshowsdk.so       # 显示模块
├── libavfiltersdk.so     # 滤镜模块
├── libaviconvert.so      # 格式转换
├── libactivelinkserver.so
├── ...
└── lib/                  # FFmpeg 依赖库
    ├── libavcodec.so
    ├── libavformat.so
    ├── libavutil.so
    ├── libswresample.so
    └── ...
```

## 2. 无头 Linux 服务器的预览参数配置

在无图形界面的 Linux 服务器上，`NetClient_SyncRealPlay` 的 `NetClientPara` 必须正确配置以下参数：

| 参数 | 值 | 说明 |
|------|-----|------|
| `iIsForbidDecode` | `0` | 允许解码（设为 1 会导致 SDK 内部 `PushData` 崩溃） |
| `pvWnd` | `null` | 不需要窗口句柄 |
| `iVideoRenderFlag` | `1` | 禁止视频渲染显示 |
| `m_iBitRateFlag` | `0` | 请求原始流 |

> **重要**：`iIsForbidDecode = 1` 在当前 SDK 版本（V5.5.0.0）的 Linux 环境下会导致 `CLS_BaseLayer::PushData` 崩溃（SIGSEGV），因为 SDK 在数据到达时仍会尝试调用未创建的播放器。正确做法是保持解码但禁止渲染（`iVideoRenderFlag = 1`）。

## 3. NetClientPara 完整结构体

JNA 映射必须包含所有字段，缺少任何字段会导致内存偏移错位：

```java
public static class tagNetClientPara extends Structure {
    public int iSize;
    public CLIENTINFO tCltInfo;
    public int iCryptType;           // 加密类型，默认 0
    public Pointer pCbkFullFrm;      // 解码帧回调
    public Pointer pvCbkFullFrmUsrData;
    public Pointer pCbkRawFrm;       // 原始流回调
    public Pointer pvCbkRawFrmUsrData;
    public int iIsForbidDecode;      // 0=解码, 1=不解码
    public Pointer pvWnd;            // 窗口句柄
    public int iVideoRenderFlag;     // 0=允许显示, 1=禁止显示
    public int m_iBitRateFlag;       // 0=原始流, 1=压缩流
}
```

## 4. 端口说明

| 端口 | 用途 |
|------|------|
| 3000 | SDK 登录端口（`SyncLogon`） |
| 3001 | 数据连接端口（预览、抓图、控制），SDK 内部自动使用 |

## 5. 问题排查清单

- **`CreatePlayerFromStream failed`**：检查 `LD_LIBRARY_PATH` 是否包含 SDK 库路径
- **`Inner_StartRecvEx fail! iRet=-36`**：码流类型不支持，尝试切换 `m_iStreamNO`（0=主码流, 1=子码流）
- **`Inner_StartRecvEx fail! DevIp=`（空）**：`CLIENTINFO.m_cRemoteIP` 未填充设备 IP
- **SIGSEGV at `PushData`**：`iIsForbidDecode` 不要设为 1，改用 `iVideoRenderFlag = 1` 禁止显示
- **SDK 日志级别**：修改 `sdk_log_config.ini` 中 `Level=300` 开启 debug 日志

---

## 6. 一键安装脚本（新系统部署）

项目提供一键安装脚本，在**全新 Ubuntu/Debian x86_64** 上完成环境检测、系统依赖、Java、SDK 库、环境变量、systemd 服务与快捷命令的安装。

### 脚本位置与用法

- **路径**：`bash/install.sh`
- **用法**：`sudo bash install.sh [安装目录]`
- **默认安装目录**：`/opt/senhub`

### 安装前准备

1. **打包应用与库**：在已编译的 server 目录下按 `bash/PACK.md` 执行打包，得到两个压缩包。
2. **上传并获取 URL**：将 `SenHub-app-1.0.0.tar.gz` 与 `Senhub-libs.tar.gz` 上传到可下载地址。
3. **修改脚本中的 URL**：编辑 `install.sh` 顶部，将 `SENHUB_APP_URL` 和 `SENHUB_LIBS_URL` 改为实际上传后的下载地址。

### 脚本执行步骤概览

| 步骤 | 内容 |
|------|------|
| 1 | 检测系统（Ubuntu/Debian x86_64、磁盘空间） |
| 2 | 安装系统依赖（wget/curl/tar、libx11/libasound、mpv 等） |
| 3 | 检测/安装 Java（>=11，缺省则安装 OpenJDK 21） |
| 4 | 创建安装目录（bin、lib、config、data、storage/*、logs、sdkLog、tmp、test） |
| 5 | 下载并解压应用包、SDK 库包到安装目录 |
| 6 | 设置 LD_LIBRARY_PATH（vgw-env + /etc/profile.d/senhub.sh） |
| 7 | 创建并启用 systemd 服务 `senhub-app` |
| 8 | 注入快捷命令 `vgw`（/usr/local/bin/vgw） |
| 9 | 启动服务并输出访问地址 |

### 安装后的目录与命令

- **安装根目录**（默认 `/opt/senhub`）：包含 `senhub-app.jar`、`lib/`、`config/`、`data/`、`storage/`（含 `storage/tts`）、`logs/`、`sdkLog/` 等。
- **快捷命令**：`vgw start | stop | restart | status | logs [sdk] | info`
- **服务管理**：`systemctl start|stop|restart|status senhub-app`
- **访问地址**：`http://<本机IP>:8084`

安装脚本会确保天地伟业 SDK 所需的 `LD_LIBRARY_PATH` 通过 `EnvironmentFile` 注入到 systemd 服务中，无需再手动配置。
