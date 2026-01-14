# 仓库初始化指南

## 目标仓库

**GitHub 仓库**: https://github.com/GQ-Y/livox-so.git

## 初始化步骤

### 1. 初始化 Git 仓库

```bash
cd livox-demo
git init
git branch -M main
```

### 2. 添加远程仓库

```bash
git remote add origin https://github.com/GQ-Y/livox-so.git
```

### 3. 添加 Livox SDK2 子模块

```bash
git submodule add https://github.com/Livox-SDK/Livox-SDK2.git livox-sdk2-official
```

### 4. 添加所有文件

```bash
git add .
git commit -m "Initial commit: Livox Demo with GitHub Actions"
```

### 5. 推送到远程仓库

```bash
git push -u origin main
```

## 验证 GitHub Actions

1. **访问 Actions 页面**
   - 打开 https://github.com/GQ-Y/livox-so/actions

2. **查看 Workflow 运行**
   - 推送代码后，GitHub Actions 会自动触发编译
   - 可以看到两个 job 并行运行（Linux 和 macOS）

3. **下载编译产物**
   - 编译完成后，在 Actions 页面下载 Artifacts
   - 每个平台生成一个 ZIP 文件

## 后续更新

### 更新代码

```bash
git add .
git commit -m "Update: 描述你的更改"
git push
```

### 更新子模块

```bash
# 更新子模块到最新版本
cd livox-sdk2-official
git pull origin master
cd ..
git add livox-sdk2-official
git commit -m "Update Livox SDK2 submodule"
git push
```

### 手动触发编译

1. 访问 GitHub 仓库的 Actions 页面
2. 选择 "Build Multi-Platform" workflow
3. 点击 "Run workflow" 按钮
4. 选择分支并运行

## 文件说明

### 关键文件

- `.github/workflows/build.yml` - GitHub Actions workflow 配置
- `.gitmodules` - Git 子模块配置
- `.gitignore` - Git 忽略规则
- `scripts/` - 编译脚本目录
  - `build-sdk.sh` - 编译 Livox SDK2
  - `build-jni.sh` - 编译 JNI 库
  - `build-java.sh` - 编译 Java 项目

### 不提交到 Git 的文件

以下文件/目录会被 `.gitignore` 忽略：
- `build/` - 编译临时目录
- `target/` - Maven 编译产物
- `lib/` - 动态库目录（编译产物）
- `livox-sdk2-official/build/` - SDK 编译目录
- `artifacts/` - GitHub Actions 打包目录

## 故障排查

### 子模块未初始化

如果克隆仓库后子模块为空：

```bash
git submodule update --init --recursive
```

### GitHub Actions 编译失败

1. **检查子模块**
   - 确保 `.gitmodules` 文件存在
   - 确保子模块 URL 正确

2. **查看编译日志**
   - 在 Actions 页面查看详细的编译日志
   - 检查错误信息

3. **手动测试编译脚本**
   - 在本地运行编译脚本，检查是否有问题

### 权限问题

如果推送失败，检查：
- GitHub 仓库权限
- Git 认证配置

## 注意事项

1. **首次编译时间较长**
   - 需要下载和编译 Livox SDK2
   - Linux 和 macOS 会并行编译，但每个平台仍需要 10-20 分钟

2. **macOS Runner 限制**
   - GitHub 免费版每月有 2000 分钟的 macOS runner 使用时间
   - 注意控制编译频率

3. **Artifacts 保留时间**
   - GitHub 免费版保留 90 天
   - 建议重要版本下载保存

4. **子模块更新**
   - 更新代码时记得同步更新子模块
   - 子模块更新后需要重新编译
