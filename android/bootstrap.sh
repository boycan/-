#!/usr/bin/env bash
# 生成 Gradle Wrapper jar 的备用说明：
# 推荐直接用 Android Studio 打开本工程，Studio 会自动处理 wrapper。
# 若已安装 gradle：
#   cd "$(dirname "$0")"
#   gradle wrapper --gradle-version 8.7
set -euo pipefail
cd "$(dirname "$0")"
echo "请使用 Android Studio 打开: $(pwd)"
echo "或在已安装 Gradle 时执行: gradle wrapper --gradle-version 8.7"
