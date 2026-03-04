#!/bin/bash
#
# SenHub / 视频网关 一键安装脚本（Ubuntu/Debian x86_64）
# 用法: sudo bash install.sh [安装目录]
# 默认安装目录: /opt/senhub
#
# 安装前请将下方 URL 改为实际下载地址。
#

set -e

# ---------- 版本与下载地址 ----------
SENHUB_BASE_URL="${SENHUB_BASE_URL:-http://demo.zt.admins.smartrail.cloud}"

# 优先从服务器拉取最新版本号，拉取失败则退出
if [ -z "$SENHUB_VERSION" ]; then
    SENHUB_VERSION="$(curl -fsSL "${SENHUB_BASE_URL}/LATEST_VERSION" 2>/dev/null | tr -d '[:space:]')"
    if [ -z "$SENHUB_VERSION" ]; then
        echo "错误: 无法从 ${SENHUB_BASE_URL}/LATEST_VERSION 获取版本信息，请检查网络或手动指定 SENHUB_VERSION=x.x.x"
        exit 1
    fi
    echo "检测到最新版本: $SENHUB_VERSION"
fi

SENHUB_APP_URL="${SENHUB_APP_URL:-${SENHUB_BASE_URL}/SenHub-app-${SENHUB_VERSION}.tar.gz}"
SENHUB_LIBS_URL="${SENHUB_LIBS_URL:-${SENHUB_BASE_URL}/Senhub-libs.tar.gz}"

# ---------- 安装目录 ----------
INSTALL_DIR="${1:-/opt/senhub}"
SERVICE_NAME="senhub-app"

# ---------- 步骤 1：系统环境检测 ----------
step1_check_system() {
    echo "[1/9] 检测系统环境..."
    local os arch
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        os="$ID"
    else
        os="unknown"
    fi
    arch="$(uname -m)"
    if [ "$arch" != "x86_64" ]; then
        echo "错误: 仅支持 x86_64 架构，当前为 $arch"
        exit 1
    fi
    case "$os" in
        ubuntu|debian) ;;
        *) echo "警告: 未在 Ubuntu/Debian 上测试，继续尝试...";;
    esac
    local avail
    avail="$(df -m "$(dirname "$INSTALL_DIR")" 2>/dev/null | tail -1 | awk '{print $4}')"
    if [ -n "$avail" ] && [ "$avail" -lt 1024 ]; then
        echo "错误: 磁盘剩余空间不足 1GB"
        exit 1
    fi
    echo "  系统: $os ($arch), 安装目录: $INSTALL_DIR"
}

# ---------- 步骤 2：安装系统依赖 ----------
step2_install_deps() {
    echo "[2/9] 安装系统依赖..."
    apt-get update -qq || true
    apt-get install -y --no-install-recommends \
        wget curl ca-certificates tar gzip \
        libstdc++6 libglib2.0-0 \
        libx11-6 libxext6 libxrender1 \
        libpulse0
    apt-get install -y --no-install-recommends libasound2t64 2>/dev/null || \
        apt-get install -y --no-install-recommends libasound2 2>/dev/null || true
    # FFmpeg：ZLM 回放转码（H.265→H.264）依赖，未安装时转码播放不可用
    if command -v ffmpeg &>/dev/null; then
        echo "  ffmpeg 已存在，跳过"
    else
        echo "  安装 FFmpeg（ZLM 回放转码依赖）..."
        apt-get install -y --no-install-recommends ffmpeg 2>/dev/null || true
    fi
    if command -v mpv &>/dev/null; then
        echo "  mpv 已存在，跳过"
    else
        apt-get install -y --no-install-recommends mpv 2>/dev/null || true
    fi
}

# ---------- 步骤 3：检测 Java 环境 ----------
step3_java() {
    echo "[3/9] 检测 Java 环境..."
    local ver
    if command -v java &>/dev/null; then
        ver="$(java -version 2>&1 | head -1)"
        if java -version 2>&1 | grep -qE "version \"(1[1-9]|[2-9][0-9])"; then
            echo "  已安装: $ver"
            return
        fi
    fi
    echo "  安装 OpenJDK 21 JRE..."
    apt-get install -y --no-install-recommends openjdk-21-jre-headless 2>/dev/null || \
    apt-get install -y --no-install-recommends openjdk-17-jre-headless 2>/dev/null || \
    apt-get install -y --no-install-recommends default-jre-headless
    java -version
}

# ---------- 步骤 4：创建安装目录 ----------
step4_dirs() {
    echo "[4/9] 创建安装目录..."
    mkdir -p "$INSTALL_DIR"/{bin,lib,config,data,storage/captures,storage/records,storage/recordings,storage/downloads,storage/radar/backgrounds,storage/tts,logs,sdkLog,tmp,test}
    echo "  目录: $INSTALL_DIR"
}

# ---------- 步骤 5：下载并解压（应用包结构见 bash/PACK.md）----------
step5_download_extract() {
    echo "[5/9] 下载并解压..."
    local tmp_app="/tmp/senhub-app-$$" tmp_libs="/tmp/senhub-libs-$$"
    mkdir -p "$tmp_app" "$tmp_libs"
    if [ -n "$SENHUB_LIBS_URL" ] && [ "$SENHUB_LIBS_URL" != "https://example.com/"* ]; then
        (cd "$tmp_libs" && curl -fsSL "$SENHUB_LIBS_URL" | tar -xz)
        if [ -d "$tmp_libs/lib" ]; then
            cp -a "$tmp_libs"/lib/* "$INSTALL_DIR/lib/"
        fi
    else
        echo "  跳过库包下载（未设置 SENHUB_LIBS_URL 或为示例 URL）"
    fi
    if [ -n "$SENHUB_APP_URL" ] && [ "$SENHUB_APP_URL" != "https://example.com/"* ]; then
        (cd "$tmp_app" && curl -fsSL "$SENHUB_APP_URL" | tar -xz)
        local jar
        jar="$(find "$tmp_app" -maxdepth 4 -name "*.jar" -type f 2>/dev/null | head -1)"
        if [ -n "$jar" ]; then
            cp "$jar" "$INSTALL_DIR/senhub-app.jar"
            echo "  已复制 JAR: $INSTALL_DIR/senhub-app.jar"
        fi
        if [ -f "$tmp_app/src/main/resources/config.yaml" ]; then
            [ ! -f "$INSTALL_DIR/config/config.yaml" ] && cp "$tmp_app/src/main/resources/config.yaml" "$INSTALL_DIR/config/config.yaml"
        fi
        [ -f "$tmp_app/test/fanguangyi.png" ] && cp "$tmp_app/test/fanguangyi.png" "$INSTALL_DIR/test/"
        if [ -f "$tmp_app/lib/x86/tiandy/sdk_log_config.ini" ] && [ -d "$INSTALL_DIR/lib/x86/tiandy" ]; then
            cp "$tmp_app/lib/x86/tiandy/sdk_log_config.ini" "$INSTALL_DIR/lib/x86/tiandy/"
        fi
    else
        echo "  跳过应用包下载（未设置 SENHUB_APP_URL 或为示例 URL）"
    fi
    rm -rf "$tmp_app" "$tmp_libs"
    if [ ! -f "$INSTALL_DIR/senhub-app.jar" ]; then
        echo "错误: 未找到 JAR 文件，请设置 SENHUB_APP_URL 并确保应用包内包含 .jar"
        exit 1
    fi
    echo "$SENHUB_VERSION" > "$INSTALL_DIR/VERSION"
    echo "  已安装版本: $SENHUB_VERSION"
}

# ---------- 步骤 6：设置 LD_LIBRARY_PATH ----------
step6_env() {
    echo "[6/9] 设置环境变量..."
    local vgw_env="$INSTALL_DIR/bin/vgw-env"
    cat > "$vgw_env" << EOF
# SenHub 运行环境（systemd EnvironmentFile 格式，不可使用 export）
VGW_HOME=$INSTALL_DIR
LD_LIBRARY_PATH=$INSTALL_DIR/lib/x86/hikvision:$INSTALL_DIR/lib/x86/hikvision/HCNetSDKCom:$INSTALL_DIR/lib/x86/tiandy:$INSTALL_DIR/lib/x86/tiandy/lib:$INSTALL_DIR/lib/linux
JAVA_LIB_PATH=$INSTALL_DIR/lib/x86/hikvision:$INSTALL_DIR/lib/x86/hikvision/HCNetSDKCom:$INSTALL_DIR/lib/x86/tiandy:$INSTALL_DIR/lib/x86/tiandy/lib:$INSTALL_DIR/lib/linux
EOF
    chmod 644 "$vgw_env"
    cat > /etc/profile.d/senhub.sh << EOF
# SenHub 全局库路径（新开 shell 生效）
export VGW_HOME="$INSTALL_DIR"
export LD_LIBRARY_PATH="\$VGW_HOME/lib/x86/hikvision:\$VGW_HOME/lib/x86/hikvision/HCNetSDKCom:\$VGW_HOME/lib/x86/tiandy:\$VGW_HOME/lib/x86/tiandy/lib:\$VGW_HOME/lib/linux:\${LD_LIBRARY_PATH}"
EOF
    chmod 644 /etc/profile.d/senhub.sh
    echo "  已写入 $vgw_env 与 /etc/profile.d/senhub.sh"
}

# ---------- 步骤 7：创建 systemd 服务 ----------
step7_systemd() {
    echo "[7/9] 创建 systemd 服务..."
    cat > "/etc/systemd/system/${SERVICE_NAME}.service" << EOF
[Unit]
Description=SenHub Video Gateway Service
After=network.target

[Service]
Type=simple
WorkingDirectory=$INSTALL_DIR
EnvironmentFile=$INSTALL_DIR/bin/vgw-env
ExecStart=/usr/bin/java \\
  -Djava.io.tmpdir=$INSTALL_DIR/tmp \\
  -Djava.library.path=\${JAVA_LIB_PATH} \\
  -jar $INSTALL_DIR/senhub-app.jar
Restart=on-failure
RestartSec=10
StandardOutput=append:$INSTALL_DIR/logs/server.log
StandardError=append:$INSTALL_DIR/logs/server.log

[Install]
WantedBy=multi-user.target
EOF
    systemctl daemon-reload
    systemctl enable "$SERVICE_NAME"
    echo "  已安装并启用 $SERVICE_NAME"
}

# ---------- 步骤 8：注入快捷命令 vgw ----------
step8_vgw_cmd() {
    echo "[8/9] 注入快捷命令 vgw..."
    cat > "$INSTALL_DIR/bin/vgw" << 'VGWSCRIPT'
#!/bin/bash
self="$(readlink -f "$0" 2>/dev/null || echo "$0")"
INSTALL_DIR="$(cd "$(dirname "$self")/.." && pwd)"
SERVICE_NAME="senhub-app"
VGW_ENV="$INSTALL_DIR/bin/vgw-env"
SENHUB_BASE_URL="http://demo.zt.admins.smartrail.cloud"

# 语义化版本比较：$1 > $2 返回 0
_ver_gt() {
    [ "$1" = "$2" ] && return 1
    local IFS=.
    local i a=($1) b=($2)
    for ((i=0; i<${#a[@]} || i<${#b[@]}; i++)); do
        local va=${a[i]:-0} vb=${b[i]:-0}
        if ((va > vb)); then return 0; fi
        if ((va < vb)); then return 1; fi
    done
    return 1
}

cmd="${1:-status}"
case "$cmd" in
    start)   systemctl start "$SERVICE_NAME" ;;
    stop)    systemctl stop "$SERVICE_NAME" ;;
    restart) systemctl restart "$SERVICE_NAME" ;;
    status)  systemctl status "$SERVICE_NAME" --no-pager ;;
    logs)
        if [ "$2" = "sdk" ]; then
            tail -f "$INSTALL_DIR/logs/sdk.log" 2>/dev/null || tail -f "$INSTALL_DIR/sdkLog"/*.log 2>/dev/null || echo "无 SDK 日志"
        else
            tail -f "$INSTALL_DIR/logs/server.log" 2>/dev/null || tail -f "$INSTALL_DIR/logs/app.log" 2>/dev/null || echo "无日志"
        fi
        ;;
    version)
        local_ver="$(cat "$INSTALL_DIR/VERSION" 2>/dev/null || echo "未知")"
        echo "SenHub 当前版本: $local_ver"
        ;;
    update)
        echo "========== SenHub 在线更新 =========="
        local_ver="$(cat "$INSTALL_DIR/VERSION" 2>/dev/null || echo "0.0.0")"
        echo "当前运行版本: $local_ver"
        echo "正在检查最新版本..."
        remote_ver="$(curl -fsSL "${SENHUB_BASE_URL}/LATEST_VERSION" 2>/dev/null | tr -d '[:space:]')"
        if [ -z "$remote_ver" ]; then
            echo "错误: 无法获取远程版本信息 (${SENHUB_BASE_URL}/LATEST_VERSION)"
            exit 1
        fi
        echo "服务器最新版本: $remote_ver"
        if ! _ver_gt "$remote_ver" "$local_ver"; then
            echo "当前已是最新版本，无需更新。"
            exit 0
        fi
        echo ""
        echo "发现新版本 $remote_ver (当前 $local_ver)，开始更新..."
        TMP_DIR="/tmp/senhub-update-$$"
        mkdir -p "$TMP_DIR"
        APP_URL="${SENHUB_BASE_URL}/SenHub-app-${remote_ver}.tar.gz"
        echo "下载: $APP_URL"
        if ! curl -fSL "$APP_URL" | tar -xz -C "$TMP_DIR"; then
            echo "错误: 下载或解压失败"
            rm -rf "$TMP_DIR"
            exit 1
        fi
        JAR="$(find "$TMP_DIR" -maxdepth 4 -name "*.jar" -type f 2>/dev/null | head -1)"
        if [ -z "$JAR" ]; then
            echo "错误: 更新包中未找到 JAR 文件"
            rm -rf "$TMP_DIR"
            exit 1
        fi
        echo "停止服务..."
        systemctl stop "$SERVICE_NAME" 2>/dev/null || true
        sleep 2
        echo "替换 JAR..."
        cp "$JAR" "$INSTALL_DIR/senhub-app.jar"
        # 更新附带文件（如有）
        [ -f "$TMP_DIR/test/fanguangyi.png" ] && cp "$TMP_DIR/test/fanguangyi.png" "$INSTALL_DIR/test/"
        if [ -f "$TMP_DIR/lib/x86/tiandy/sdk_log_config.ini" ] && [ -d "$INSTALL_DIR/lib/x86/tiandy" ]; then
            cp "$TMP_DIR/lib/x86/tiandy/sdk_log_config.ini" "$INSTALL_DIR/lib/x86/tiandy/"
        fi
        echo "$remote_ver" > "$INSTALL_DIR/VERSION"
        rm -rf "$TMP_DIR"
        echo "启动服务..."
        systemctl start "$SERVICE_NAME"
        sleep 3
        if systemctl is-active --quiet "$SERVICE_NAME"; then
            echo "========== 更新完成 =========="
            echo "已从 $local_ver 更新到 $remote_ver"
        else
            echo "警告: 服务启动异常，请检查: journalctl -u $SERVICE_NAME -n 50"
            exit 1
        fi
        ;;
    check)
        local_ver="$(cat "$INSTALL_DIR/VERSION" 2>/dev/null || echo "0.0.0")"
        remote_ver="$(curl -fsSL "${SENHUB_BASE_URL}/LATEST_VERSION" 2>/dev/null | tr -d '[:space:]')"
        if [ -z "$remote_ver" ]; then
            echo "无法获取远程版本"; exit 1
        fi
        echo "当前版本: $local_ver"
        echo "最新版本: $remote_ver"
        if _ver_gt "$remote_ver" "$local_ver"; then
            echo "有新版本可用！执行 vgw update 进行更新。"
        else
            echo "已是最新版本。"
        fi
        ;;
    info)
        local_ver="$(cat "$INSTALL_DIR/VERSION" 2>/dev/null || echo "未知")"
        echo "SenHub 安装信息"
        echo "  版本:     $local_ver"
        echo "  安装目录: $INSTALL_DIR"
        echo "  服务名:   $SERVICE_NAME"
        echo "  JAR:      $INSTALL_DIR/senhub-app.jar"
        echo "  日志:     $INSTALL_DIR/logs/"
        echo "  数据:     $INSTALL_DIR/data/ $INSTALL_DIR/storage/"
        echo "  访问:     http://$(hostname -I 2>/dev/null | awk '{print $1}'):8084"
        ;;
    *) echo "用法: vgw { start | stop | restart | status | logs [sdk] | version | check | update | info }"; exit 1 ;;
esac
VGWSCRIPT
    chmod 755 "$INSTALL_DIR/bin/vgw"
    ln -sf "$INSTALL_DIR/bin/vgw" /usr/local/bin/vgw 2>/dev/null || true
    echo "  已创建 /usr/local/bin/vgw"
}

# ---------- 步骤 9：启动服务 ----------
step9_start() {
    echo "[9/9] 启动服务..."
    systemctl start "$SERVICE_NAME"
    sleep 5
    if systemctl is-active --quiet "$SERVICE_NAME"; then
        echo "  服务已启动 (版本: $SENHUB_VERSION)"
        tail -20 "$INSTALL_DIR/logs/server.log" 2>/dev/null || true
        echo ""
        echo "  访问地址: http://$(hostname -I 2>/dev/null | awk '{print $1}'):8084"
        echo "  快捷命令: vgw start | stop | restart | status | logs | version | check | update | info"
    else
        echo "  启动可能异常，请检查: journalctl -u $SERVICE_NAME -n 50"
        exit 1
    fi
}

# ---------- 主流程 ----------
main() {
    [ "$(id -u)" -ne 0 ] && { echo "请使用 root 或 sudo 执行"; exit 1; }
    step1_check_system
    step2_install_deps
    step3_java
    step4_dirs
    step5_download_extract
    step6_env
    step7_systemd
    step8_vgw_cmd
    step9_start
    echo ""
    echo "安装完成. (版本: $SENHUB_VERSION)"
}

main "$@"
