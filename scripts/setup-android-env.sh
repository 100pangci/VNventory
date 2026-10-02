#!/usr/bin/env bash
# VNventory 本地工具链引导：Android cmdline-tools + SDK 包 + Gradle 发行版 + wrapper
# 全部安装在项目根目录 toolchain/ 内（不进版本库），幂等，可重复执行。
#
# 用法：
#   scripts/setup-android-env.sh
#
# 可选环境变量：
#   VNVENTORY_JDK  JDK 17–21 安装路径（默认自动探测常见位置）
#
# 说明：
#   - 本脚本在 <项目>/toolchain/gradle-home/gradle.properties 写入 org.gradle.java.home，
#     使 ./gradlew 始终用指定 JDK（不污染项目内受版本控制的 gradle.properties）。
set -euo pipefail

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN="$PROJ/toolchain"
SDK="$TOOLCHAIN/android-sdk"
DL="${TMPDIR:-/tmp}/vnventory-downloads"
GRADLE_VER=9.8.0
CMDLINE_BUILD=15859902

# ---- JDK 探测（17–21） ----
JDK="${VNVENTORY_JDK:-}"
if [ -z "$JDK" ]; then
  for candidate in \
    "$HOME/Software/jdk-21.0.12.1+1" \
    /usr/lib/jvm/java-21-openjdk \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-17-openjdk \
    /usr/lib/jvm/java-17-openjdk-amd64; do
    if [ -x "$candidate/bin/java" ]; then JDK="$candidate"; break; fi
  done
fi
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/java" ]; then
  echo "错误：未找到 JDK 17–21，请设置 VNVENTORY_JDK=<jdk 路径>" >&2
  exit 1
fi
echo "==> 使用 JDK: $JDK"

export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
export ANDROID_USER_HOME="$TOOLCHAIN/android-user-home"
export GRADLE_USER_HOME="$TOOLCHAIN/gradle-home"
mkdir -p "$DL" "$TOOLCHAIN" "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"

# Gradle 固定使用该 JDK（GRADLE_USER_HOME 内的属性文件，不进版本库）
if ! grep -qs '^org.gradle.java.home=' "$GRADLE_USER_HOME/gradle.properties"; then
  echo "org.gradle.java.home=$JDK" >> "$GRADLE_USER_HOME/gradle.properties"
fi

step() { echo "==> $*"; }
unzip_q() {
  if command -v unzip >/dev/null 2>&1; then unzip -q -o "$1" -d "$2"
  else python3 -m zipfile -e "$1" "$2"; fi
}
dl() { # $1 url $2 out （先直连，失败走代理环境）
  local url="$1" out="$2"
  [ -s "$out" ] && { echo "  cached: $out"; return 0; }
  curl -fsSL --noproxy '*' --connect-timeout 15 --retry 3 -o "$out" "$url" \
    || curl -fsSL --connect-timeout 15 --retry 3 -o "$out" "$url"
}

step "1/4 cmdline-tools ($CMDLINE_BUILD)"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  dl "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_BUILD}_latest.zip" "$DL/cmdline-tools.zip"
  unzip_q "$DL/cmdline-tools.zip" "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

step "2/4 接受 licenses"
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >"$DL/licenses.log" 2>&1 || true

step "3/4 SDK 包（platform-tools / android-37.2 / build-tools 37）"
"$SDKMANAGER" --sdk_root="$SDK" "platform-tools" "platforms;android-37.2" "build-tools;37.0.0" 2>&1 | tail -3

step "4/4 Gradle $GRADLE_VER"
if [ ! -x "$TOOLCHAIN/gradle-$GRADLE_VER/bin/gradle" ]; then
  dl "https://services.gradle.org/distributions/gradle-${GRADLE_VER}-bin.zip" "$DL/gradle-${GRADLE_VER}-bin.zip"
  unzip_q "$DL/gradle-${GRADLE_VER}-bin.zip" "$TOOLCHAIN"
fi
"$TOOLCHAIN/gradle-$GRADLE_VER/bin/gradle" --version | grep -E 'Gradle |JVM '

step "生成 Gradle wrapper（如缺失）"
if [ ! -f "$PROJ/gradle/wrapper/gradle-wrapper.jar" ]; then
  TMPW=$(mktemp -d "$DL/wrapper.XXXXXX")
  ( cd "$TMPW" && touch settings.gradle.kts \
      && "$TOOLCHAIN/gradle-$GRADLE_VER/bin/gradle" wrapper \
           --gradle-version "$GRADLE_VER" --distribution-type bin --no-daemon -q )
  mkdir -p "$PROJ/gradle/wrapper"
  cp -f "$TMPW/gradle/wrapper/gradle-wrapper.jar" "$TMPW/gradle/wrapper/gradle-wrapper.properties" "$PROJ/gradle/wrapper/"
  cp -f "$TMPW/gradlew" "$PROJ/gradlew"
  [ -f "$TMPW/gradlew.bat" ] && cp -f "$TMPW/gradlew.bat" "$PROJ/"
  chmod +x "$PROJ/gradlew"
  rm -rf "$TMPW"
fi

echo "SETUP_OK：工具链位于 $TOOLCHAIN"
