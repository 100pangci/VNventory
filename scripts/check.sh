#!/usr/bin/env bash
# 同时验证 APK、JVM/Room 回归测试及 Lint；无论成功/失败均停止项目 daemon。
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cleanup() { "$ROOT/gradlew" -p "$ROOT" --stop; }
trap cleanup EXIT
python3 "$ROOT/scripts/tests/setup-toolchain-test.py"
"$ROOT/gradlew" -p "$ROOT" --no-daemon --max-workers=2 \
  :app:assembleDebug :app:testDebugUnitTest :app:lintDebug "$@"
