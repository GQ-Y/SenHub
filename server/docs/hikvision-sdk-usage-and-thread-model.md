# 海康 SDK (HCNetSDK V6.1.9.48) 使用与线程约定

本文档记录与 **海康威视网络SDK Linux64 V6.1.9.48**（如 `海康SDK-CH-HCNetSDKV6.1.9.48_build20230410_linux64`）对照后的使用约定，用于避免 JNA 调用导致的 SIGSEGV 崩溃。

## 1. 结构体与头文件对照

- **NET_DVR_ALARMER**：回调参数，字段顺序与长度必须与 SDK 头文件一致。当前 Java 定义：`sDeviceIP[128]`、`sSocketIP[128]`、`byRes2[11]`，SERIALNO_LEN=48、NAME_LEN=32、MACADDR_LEN=6。**部署前请用官方头文件再次核对**，任何不一致可能导致 native 段错误。
- **NET_DVR_JPEGPARA**、**NET_DVR_SETUPALARM_PARAM_V50/V41**、**NET_DVR_LOCAL_SDK_PATH**：与头文件尺寸/对齐一致。
- **NET_SDK_MAX_FILE_PATH**：当前 256，需在头文件中确认。

## 2. 回调与线程模型（必须遵守）

- **NET_DVR_SetDVRMessageCallBack_V30** / **NET_DVR_StartListen_V30**：回调在 **SDK 内部 native 线程** 中执行；`pAlarmer`、`pAlarmInfo` 仅在回调内有效，**可能为 null**，回调返回后指针失效。
- **约定**：
  - 回调内必须先做 `pAlarmer == null` 检查；访问任何 `pAlarmer` 字段前必须调用 `pAlarmer.read()` 从 native 内存同步到 Java。
  - **禁止在回调内调用任何海康 SDK 接口**（如 login、capture、setupAlarmChan 等），否则易导致死锁或崩溃。
  - 回调内只做最小数据拷贝，然后派发到应用线程（如 AlarmService）处理业务。

## 3. SDK 接口调用线程

- 为降低多线程并发调用 native 导致的崩溃风险，**所有** 海康 SDK 接口调用（login、logout、setupAlarmChan、closeAlarmChan、capturePicture、ptzControl 等）在实现中**统一通过单线程执行器串行执行**，并带超时与异常捕获。

## 4. 初始化与路径

- **NET_DVR_SetSDKInitCfg**：在 `NET_DVR_Init()` 之前设置 SDK_PATH、LIBEAY_PATH、SSLEAY_PATH；路径以 null 结尾，长度不超过结构体规定。
- 库加载顺序：先设置 `java.library.path` 或使用绝对路径加载 `libhcnetsdk.so`，再 `NET_DVR_Init()`。

---
*对照 SDK 目录：如 sdk/海康SDK-CH-HCNetSDKV6.1.9.48_build20230410_linux64 中的头文件与文档。*
