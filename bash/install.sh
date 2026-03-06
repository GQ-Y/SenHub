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
    echo "[1/12] 检测系统环境..."
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
    if [ -n "$avail" ] && [ "$avail" -lt 2048 ]; then
        echo "错误: 磁盘剩余空间不足 2GB（PostgreSQL 需要额外空间）"
        exit 1
    fi
    echo "  系统: $os ($arch), 安装目录: $INSTALL_DIR"
}

# ---------- 步骤 2：安装系统依赖 ----------
step2_install_deps() {
    echo "[2/12] 安装系统依赖..."
    apt-get update -qq || true
    apt-get install -y --no-install-recommends \
        wget curl ca-certificates tar gzip openssl \
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
    echo "[3/12] 检测 Java 环境..."
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

# ---------- 步骤 4：安装 PostgreSQL ----------
step4_postgresql() {
    echo "[4/12] 安装 PostgreSQL..."

    if command -v psql &>/dev/null; then
        local pg_ver
        pg_ver="$(psql --version 2>/dev/null | awk '{print $3}' | cut -d. -f1)"
        echo "  PostgreSQL 已存在 (版本: $pg_ver)，跳过安装"
        # 确保服务已启动
        systemctl start postgresql 2>/dev/null || service postgresql start 2>/dev/null || true
        return
    fi

    echo "  安装 PostgreSQL 16..."
    # 添加官方 PostgreSQL APT 仓库以获取最新版本
    apt-get install -y --no-install-recommends gnupg lsb-release 2>/dev/null || true

    # 尝试通过官方仓库安装 pg16，失败则回退到系统仓库
    local installed=false
    if curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /usr/share/keyrings/postgresql.gpg 2>/dev/null; then
        local codename
        codename="$(lsb_release -cs 2>/dev/null || echo "bookworm")"
        echo "deb [signed-by=/usr/share/keyrings/postgresql.gpg] https://apt.postgresql.org/pub/repos/apt ${codename}-pgdg main" \
            > /etc/apt/sources.list.d/pgdg.list
        apt-get update -qq 2>/dev/null || true
        if apt-get install -y --no-install-recommends postgresql-16 2>/dev/null; then
            installed=true
            echo "  已通过官方仓库安装 PostgreSQL 16"
        fi
    fi

    if [ "$installed" = "false" ]; then
        echo "  官方仓库安装失败，使用系统仓库..."
        apt-get install -y --no-install-recommends postgresql 2>/dev/null || {
            echo "错误: PostgreSQL 安装失败，请手动安装后重试"
            exit 1
        }
    fi

    systemctl enable postgresql
    systemctl start postgresql
    sleep 2

    if ! systemctl is-active --quiet postgresql; then
        echo "错误: PostgreSQL 服务启动失败"
        exit 1
    fi
    echo "  PostgreSQL 安装并启动成功"
}

# ---------- 步骤 5：创建数据库用户和数据库 ----------
step5_db_setup() {
    echo "[5/12] 配置 PostgreSQL 数据库..."

    # 生成随机密码（32位字母数字）
    local db_password
    db_password="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)"
    if [ -z "$db_password" ]; then
        # openssl 不可用时的备用方案
        db_password="$(cat /dev/urandom | tr -dc 'A-Za-z0-9' | head -c 32 2>/dev/null || date +%s%N | sha256sum | head -c 32)"
    fi

    local db_user="senhub"
    local db_name="senhub"
    local db_host="127.0.0.1"
    local db_port="5432"

    # 创建数据库用户（已存在则更新密码）
    sudo -u postgres psql -c "DO \$\$ BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${db_user}') THEN
            CREATE ROLE ${db_user} WITH LOGIN PASSWORD '${db_password}';
        ELSE
            ALTER ROLE ${db_user} WITH PASSWORD '${db_password}';
        END IF;
    END \$\$;" 2>/dev/null || {
        echo "错误: 无法创建数据库用户，请检查 PostgreSQL 是否正常运行"
        exit 1
    }

    # 创建数据库（已存在则跳过）
    sudo -u postgres psql -c "SELECT 1 FROM pg_database WHERE datname = '${db_name}'" | grep -q 1 || \
        sudo -u postgres psql -c "CREATE DATABASE ${db_name} OWNER ${db_user} ENCODING 'UTF8' LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8';" 2>/dev/null || \
        sudo -u postgres psql -c "CREATE DATABASE ${db_name} OWNER ${db_user};" 2>/dev/null || {
            echo "错误: 无法创建数据库 ${db_name}"
            exit 1
        }

    # 授权
    sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE ${db_name} TO ${db_user};" 2>/dev/null || true
    sudo -u postgres psql -d "${db_name}" -c "GRANT ALL ON SCHEMA public TO ${db_user};" 2>/dev/null || true

    # 确保安装目录的 config 目录已存在
    mkdir -p "$INSTALL_DIR/config"

    # 写入凭据文件（systemd EnvironmentFile 格式）
    local db_env_file="$INSTALL_DIR/config/db.env"
    cat > "$db_env_file" << DBENV
# SenHub 数据库凭据（由 install.sh 自动生成，请勿手动修改）
# 生成时间: $(date '+%Y-%m-%d %H:%M:%S')
DB_HOST=${db_host}
DB_PORT=${db_port}
DB_USER=${db_user}
DB_PASSWORD=${db_password}
DB_NAME=${db_name}
DBENV

    # 严格限制权限：仅 root 可读写，防止密码泄露
    chmod 640 "$db_env_file"
    chown root:root "$db_env_file"

    echo "  数据库配置完成"
    echo "    用户: ${db_user}"
    echo "    数据库: ${db_name}"
    echo "    凭据文件: ${db_env_file}"
    echo "  注意: 数据库密码已随机生成并保存到凭据文件，应用启动时自动读取"
}

# ---------- 步骤 6：创建安装目录 ----------
step6_dirs() {
    echo "[6/12] 创建安装目录..."
    mkdir -p "$INSTALL_DIR"/{bin,lib,config,data,storage/captures,storage/records,storage/recordings,storage/downloads,storage/radar/backgrounds,storage/tts,logs,sdkLog,tmp,test}
    echo "  目录: $INSTALL_DIR"
}

# ---------- 步骤 7：下载并解压（应用包结构见 bash/PACK.md）----------
step7_download_extract() {
    echo "[7/12] 下载并解压..."
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

# ---------- 步骤 8：设置 LD_LIBRARY_PATH ----------
step8_env() {
    echo "[8/12] 设置环境变量..."
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

# ---------- 步骤 9：创建 systemd 服务 ----------
step9_systemd() {
    echo "[9/12] 创建 systemd 服务..."
    cat > "/etc/systemd/system/${SERVICE_NAME}.service" << EOF
[Unit]
Description=SenHub Video Gateway Service
After=network.target postgresql.service
Requires=postgresql.service

[Service]
Type=simple
WorkingDirectory=$INSTALL_DIR
EnvironmentFile=$INSTALL_DIR/bin/vgw-env
EnvironmentFile=$INSTALL_DIR/config/db.env
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

# ---------- 步骤 10：注入快捷命令 vgw ----------
step10_vgw_cmd() {
    echo "[10/12] 注入快捷命令 vgw..."
    cat > "$INSTALL_DIR/bin/vgw" << 'VGWSCRIPT'
#!/bin/bash
self="$(readlink -f "$0" 2>/dev/null || echo "$0")"
INSTALL_DIR="$(cd "$(dirname "$self")/.." && pwd)"
SERVICE_NAME="senhub-app"
VGW_ENV="$INSTALL_DIR/bin/vgw-env"
DB_ENV="$INSTALL_DIR/config/db.env"
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
    db)
        echo "========== SenHub 数据库状态 =========="
        if [ -f "$DB_ENV" ]; then
            # 加载数据库凭据（仅在 root 下可读）
            if [ -r "$DB_ENV" ]; then
                . "$DB_ENV"
                echo "  主机:   ${DB_HOST}:${DB_PORT}"
                echo "  数据库: ${DB_NAME}"
                echo "  用户:   ${DB_USER}"
                echo "  凭据文件: ${DB_ENV}"
                echo ""
                echo "  PostgreSQL 服务状态:"
                systemctl is-active postgresql && echo "    运行中" || echo "    未运行"
                echo ""
                echo "  测试数据库连接..."
                if command -v psql &>/dev/null; then
                    PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
                        -c "SELECT version();" 2>/dev/null && echo "  连接成功" || echo "  连接失败，请检查 PostgreSQL 服务"
                else
                    echo "  psql 未安装，无法测试连接"
                fi
            else
                echo "  凭据文件权限不足（需要 root 权限）"
            fi
        else
            echo "  凭据文件不存在: ${DB_ENV}"
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
        echo "  数据库:   PostgreSQL (详情: vgw db)"
        echo "  访问:     http://$(hostname -I 2>/dev/null | awk '{print $1}'):8084"
        ;;
    *) echo "用法: vgw { start | stop | restart | status | logs [sdk] | db | version | check | update | info }"; exit 1 ;;
esac
VGWSCRIPT
    chmod 755 "$INSTALL_DIR/bin/vgw"
    ln -sf "$INSTALL_DIR/bin/vgw" /usr/local/bin/vgw 2>/dev/null || true
    echo "  已创建 /usr/local/bin/vgw"
}

# ---------- 步骤 11：验证数据库连接 ----------
step11_verify_db() {
    echo "[11/12] 验证数据库连接..."
    if [ ! -f "$INSTALL_DIR/config/db.env" ]; then
        echo "  警告: 凭据文件不存在，跳过验证"
        return
    fi
    . "$INSTALL_DIR/config/db.env"
    local retries=5
    local ok=false
    for i in $(seq 1 $retries); do
        if PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" \
                -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1;" &>/dev/null; then
            ok=true
            break
        fi
        echo "  等待 PostgreSQL 就绪... ($i/$retries)"
        sleep 2
    done
    if [ "$ok" = "true" ]; then
        echo "  数据库连接验证成功"
    else
        echo "  警告: 数据库连接验证失败，应用启动后可能无法连接数据库"
        echo "  请执行 'vgw db' 手动检查数据库状态"
    fi
}

# ---------- 步骤 12：启动服务 ----------
step12_start() {
    echo "[12/12] 启动服务..."
    systemctl start "$SERVICE_NAME"
    sleep 5
    if systemctl is-active --quiet "$SERVICE_NAME"; then
        echo "  服务已启动 (版本: $SENHUB_VERSION)"
        tail -20 "$INSTALL_DIR/logs/server.log" 2>/dev/null || true
        echo ""
        local ip
        ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
        echo "  访问地址: http://${ip}:8084"
        echo "  首次访问将进入安装向导，请设置管理员账号密码"
        echo "  快捷命令: vgw start | stop | restart | status | logs | db | version | check | update | info"
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
    step4_postgresql
    step5_db_setup
    step6_dirs
    step7_download_extract
    step8_env
    step9_systemd
    step10_vgw_cmd
    step11_verify_db
    step12_start
    echo ""
    echo "安装完成. (版本: $SENHUB_VERSION)"
    echo "提示: 首次访问系统时请完成安装向导设置管理员账号"
}

main "$@"
