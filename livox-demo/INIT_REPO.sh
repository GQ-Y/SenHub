#!/bin/bash

# 初始化 Git 仓库并推送到 GitHub
# 目标仓库: https://github.com/GQ-Y/livox-so.git

set -e

REPO_URL="https://github.com/GQ-Y/livox-so.git"

echo "=========================================="
echo "初始化 Git 仓库并推送到 GitHub"
echo "=========================================="

# 检查是否已经是 Git 仓库
if [ -d ".git" ]; then
    echo "检测到已有 Git 仓库"
    read -p "是否继续？(y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    echo "初始化 Git 仓库..."
    git init
    git branch -M main
fi

# 检查远程仓库
if git remote | grep -q "^origin$"; then
    CURRENT_URL=$(git remote get-url origin)
    echo "当前远程仓库: $CURRENT_URL"
    if [ "$CURRENT_URL" != "$REPO_URL" ]; then
        read -p "远程仓库不匹配，是否更新？(y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git remote set-url origin "$REPO_URL"
        fi
    fi
else
    echo "添加远程仓库..."
    git remote add origin "$REPO_URL"
fi

# 检查子模块
if [ ! -f ".gitmodules" ]; then
    echo "错误: 未找到 .gitmodules 文件"
    exit 1
fi

# 初始化子模块（如果尚未初始化）
if [ ! -d "livox-sdk2-official/.git" ]; then
    echo "初始化 Git 子模块..."
    git submodule update --init --recursive
else
    echo "子模块已初始化"
fi

# 添加所有文件
echo ""
echo "添加文件到 Git..."
git add .

# 检查是否有更改
if git diff --staged --quiet; then
    echo "没有需要提交的更改"
else
    echo ""
    echo "提交更改..."
    git commit -m "Initial commit: Livox Demo with GitHub Actions multi-platform build"
fi

# 推送到远程
echo ""
echo "推送到远程仓库..."
echo "注意: 如果是首次推送，可能需要输入 GitHub 凭据"
git push -u origin main

echo ""
echo "=========================================="
echo "完成！"
echo "=========================================="
echo ""
echo "访问以下链接查看 GitHub Actions:"
echo "https://github.com/GQ-Y/livox-so/actions"
echo ""
