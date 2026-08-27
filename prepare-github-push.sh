#!/usr/bin/env bash
# 一键初始化 git 并提示推送到 GitHub（云端打包用）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ ! -d .git ]]; then
  git init
  echo "已执行 git init"
fi

git add .
if git diff --cached --quiet; then
  echo "没有新的变更需要提交。"
else
  git commit -m "初始提交：微信 AI 助手 + GitHub Actions 云端打包"
fi

git branch -M main

echo
echo "======= 下一步 ======="
echo "1. 打开 https://github.com/new 新建空仓库（不要勾选 README）"
echo "2. 复制仓库地址后执行（替换成你的地址）："
echo
echo "   git remote remove origin 2>/dev/null || true"
echo "   git remote add origin https://github.com/你的用户名/wechat-ai-assistant.git"
echo "   git push -u origin main"
echo
echo "3. 到仓库 Actions 页面等待 Build Android APK 成功，下载 Artifacts 里的 APK"
echo "详细说明：docs/CLOUD_BUILD.md"
