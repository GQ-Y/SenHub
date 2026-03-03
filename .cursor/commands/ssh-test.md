#### 远程开发服务器-测试服务器

SSH：IP：192.168.1.210

管理员账户：zyc

管理员密码：admin

专用运行工作目录：/opt/senhub

远程开发服务器环境：Ubuntu环境，java、go开发编译运行均可进行部署。

#### 后端服务更新流程

当后端代码有变更时，按以下步骤完成部署：

1. **发布新版本到更新服务器**：在本地项目根目录执行发布脚本（会自动构建前端、同步代码到远程编译、打包上传至 1Panel 更新服务器）：
   ```bash
   ~/.cursor/skills/fullstack-deploy/scripts/release-app.sh
   ```

2. **在测试工作服务器上执行更新**：SSH 连接到 192.168.1.210 后，执行 `vgw update`，该命令会自动从更新服务器下载最新版本、替换 JAR 并重启服务：
   ```bash
   sshpass -p 'admin' ssh -o StrictHostKeyChecking=no zyc@192.168.1.210 "sudo /opt/senhub/bin/vgw update"
   ```

#### vgw 常用命令

| 命令 | 说明 |
|------|------|
| `vgw status` | 查看服务运行状态 |
| `vgw version` | 查看当前安装版本 |
| `vgw check` | 检查是否有新版本可用 |
| `vgw update` | 在线更新到最新版本（下载 → 停服 → 替换 JAR → 启动） |
| `vgw start` | 启动服务 |
| `vgw stop` | 停止服务 |
| `vgw restart` | 重启服务 |
| `vgw logs` | 查看实时日志（server.log） |
| `vgw logs sdk` | 查看 SDK 日志 |
| `vgw info` | 查看安装信息 |
