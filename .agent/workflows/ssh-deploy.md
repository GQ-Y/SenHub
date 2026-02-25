---
description: 远程服务器部署与测试流程 (SSH Deployment & Testing)
---

本工作流用于将本地代码同步至远程 Ubuntu 测试服务器，并在远程端完成编译、进程替换与运行验证。

### 1. 准备阶段
- 确认远程服务器：`192.168.1.210`
- 账户：`zyc` / `admin`
- 远程目录：`/home/zyc/data/xwq/demo`

### 2. 同步与清理
// turbo
1. 在远程服务器创建目录并清理旧进程及日志：
   ```bash
   sshpass -p admin ssh zyc@192.168.1.210 "mkdir -p /home/zyc/data/xwq/demo && pkill -f senhub-app || true && rm -f /home/zyc/data/xwq/demo/server/server.log"
   ```

2. 同步代码（排除不需要的目录）：
   ```bash
   rsync -avz --exclude 'target' --exclude '.git' --exclude 'sdk/Livox-SDK2/build' ./ zyc@192.168.1.210:/home/zyc/data/xwq/demo/
   ```

### 3. 远程编译与启动
// turbo
1. 执行编译：
   ```bash
   sshpass -p admin ssh zyc@192.168.1.210 "cd /home/zyc/data/xwq/demo/server && mvn clean package -DskipTests"
   ```

2. 启动服务（后台运行并记录日志）：
   ```bash
   sshpass -p admin ssh zyc@192.168.1.210 "cd /home/zyc/data/xwq/demo/server && nohup java -jar target/senhub-app-1.0.0.jar > server.log 2>&1 &"
   ```

### 4. 验证阶段
1. 检查日志确认启动成功：
   ```bash
   sshpass -p admin ssh zyc@192.168.1.210 "tail -f /home/zyc/data/xwq/demo/server/server.log"
   ```
