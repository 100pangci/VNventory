#!/usr/bin/env bash
# 项目内 Android/Gradle 工具链引导。不安装或修改全局软件、shell 配置。
# 用法：VNVENTORY_JDK=/path/to/jdk scripts/setup-android-env.sh
set -euo pipefail

for command in python3 curl unzip sha256sum; do
  command -v "$command" >/dev/null || { echo "缺少构建引导工具：$command" >&2; exit 1; }
done

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN="$PROJ/toolchain"
SDK="$TOOLCHAIN/android-sdk"
DL="$TOOLCHAIN/downloads"
GRADLE_VER=9.8.0
GRADLE_SHA256=bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c
CMDLINE_BUILD=15859902

JDK="${VNVENTORY_JDK:-${JAVA_HOME:-}}"
if [ -z "$JDK" ]; then
  for candidate in "$HOME/Software/jdk-21.0.12.1+1" \
    /usr/lib/jvm/java-21-openjdk /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-17-openjdk /usr/lib/jvm/java-17-openjdk-amd64; do
    if [ -x "$candidate/bin/java" ]; then JDK="$candidate"; break; fi
  done
fi
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/java" ]; then
  echo "未找到 JDK，请设置 VNVENTORY_JDK=<JDK 17–21 目录>" >&2
  exit 1
fi
major=$("$JDK/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1)
if [ -z "$major" ] || (( major < 17 || major > 21 )); then
  echo "请使用 JDK 17–21，当前版本：${major:-未知}" >&2
  exit 1
fi
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
export ANDROID_HOME="$SDK"
export ANDROID_USER_HOME="$TOOLCHAIN/android-user-home"
export GRADLE_USER_HOME="$TOOLCHAIN/gradle-home"
mkdir -p "$DL" "$SDK/cmdline-tools" "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"

cleanup() {
  if [ -x "$PROJ/gradlew" ]; then "$PROJ/gradlew" --stop >"$DL/stop.log" 2>&1 || true; fi
}
trap cleanup EXIT

# 不覆盖用户已有 SDK/JDK 配置。Java Properties 使用正斜杠并转义特殊字符。
python3 - "$PROJ" "$SDK" "$JDK" "$GRADLE_USER_HOME" <<'PY'
import pathlib, sys
project, sdk, jdk, gradle = map(pathlib.Path, sys.argv[1:])
def add_if_missing(path, key, value):
    text = path.read_text() if path.exists() else ''
    if any(line.strip().startswith(key + '=') or line.strip().startswith(key + ' =') for line in text.splitlines()):
        return
    if path.exists():
        import datetime, shutil
        shutil.copy2(path, str(path) + '.bak-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
    escaped = str(value).replace('\\', '\\\\').replace(':', '\\:').replace(' ', '\\ ')
    path.write_text(text + ('\n' if text and not text.endswith('\n') else '') + key + '=' + escaped + '\n')
add_if_missing(project / 'local.properties', 'sdk.dir', sdk)
add_if_missing(gradle / 'gradle.properties', 'org.gradle.java.home', jdk)
PY

dl() {
  local url="$1" out="$2"
  if [ -s "$out" ] && python3 -m zipfile -t "$out" >/dev/null 2>&1; then return; fi
  curl -fsSL --noproxy '*' --connect-timeout 15 --retry 3 -o "$out.part" "$url" \
    || curl -fsSL --connect-timeout 15 --retry 3 -o "$out.part" "$url"
  python3 -m zipfile -t "$out.part" >/dev/null
  mv -- "$out.part" "$out"
}

echo "==> JDK: $JDK；全部下载/依赖缓存位于 $TOOLCHAIN"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  dl "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_BUILD}_latest.zip" "$DL/cmdline-tools.zip"
  staging=$(mktemp -d "$DL/cmdline.XXXXXX")
  # Python zipfile extraction does not preserve executable permissions.
  unzip -q "$DL/cmdline-tools.zip" -d "$staging"
  if [ -e "$SDK/cmdline-tools/latest" ]; then
    mv -- "$SDK/cmdline-tools/latest" "$SDK/cmdline-tools/latest.bak-$(date +%Y%m%d-%H%M%S)"
  fi
  mv -- "$staging/cmdline-tools" "$SDK/cmdline-tools/latest"
  rmdir -- "$staging"
fi
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
missing=()
[ -x "$SDK/platform-tools/adb" ] || missing+=("platform-tools")
[ -f "$SDK/platforms/android-37.2/android.jar" ] || missing+=("platforms;android-37.2")
[ -x "$SDK/build-tools/37.0.0/aapt2" ] || missing+=("build-tools;37.0.0")
if ((${#missing[@]})); then
  set +e
  yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >"$DL/licenses.log" 2>&1
  license_status=${PIPESTATUS[1]}
  set -e
  if ((license_status != 0)); then cat "$DL/licenses.log"; exit "$license_status"; fi
  "$SDKMANAGER" --sdk_root="$SDK" "${missing[@]}" >"$DL/sdkmanager.log" 2>&1 || { cat "$DL/sdkmanager.log"; exit 1; }
fi

if [ ! -x "$TOOLCHAIN/gradle-$GRADLE_VER/bin/gradle" ]; then
  dl "https://services.gradle.org/distributions/gradle-${GRADLE_VER}-bin.zip" "$DL/gradle-${GRADLE_VER}-bin.zip"
  echo "$GRADLE_SHA256  $DL/gradle-${GRADLE_VER}-bin.zip" | sha256sum --check --status
  unzip -q "$DL/gradle-${GRADLE_VER}-bin.zip" -d "$TOOLCHAIN"
fi
# 已提交的自定义 wrapper 保留项目内路径规则，不重新生成或覆盖。
"$PROJ/gradlew" --no-daemon --version
echo "SETUP_OK：SDK 路径已配置；接着执行 ./gradlew :app:assembleDebug"
