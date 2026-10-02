# VNventory — 项目内 Agent 说明

Android 原生应用：Galgame/VN 实体收藏管理（Kotlin + Compose + Material 3 + Room + Ktor）。
完整架构、数据模型与 VNDB 说明见 `README.md`。

## 构建环境（本机，重要）

- **工具链全部位于项目内 `toolchain/`**（Android SDK / Gradle / 依赖缓存 / Robolectric 缓存），
  不要往 `~/Android`、`~/.gradle`、`~/Software` 安装任何东西。
  重建脚本：`scripts/setup-android-env.sh`。
- 使用 `./gradlew`（wrapper 已内置 `GRADLE_USER_HOME=<项目>/toolchain/gradle-home`）；
  JDK 21 由 `toolchain/gradle-home/gradle.properties` 的 `org.gradle.java.home` 指定
  （`scripts/setup-android-env.sh` 自动写入；**不要在项目内 `gradle.properties` 写机器路径**）。
- **AGP 9.4.1 采用内置 Kotlin**：不要再应用 `org.jetbrains.kotlin.android` 插件；
  KGP(2.3.21) / KSP(2.3.12) 版本在根 `build.gradle.kts` 的 `buildscript` classpath 提升。
- compileSdk = 37 **带小版本**：SDK 包名是 `platforms;android-37.2`（不是 `android-37`）。
- 内存紧张：`org.gradle.daemon.idletimeout=60000` + `kotlin.compiler.execution.strategy=in-process` 已配置；
  **构建完执行 `./gradlew --stop`**，并确认没有残留 Java daemon。

## 常用命令

```bash
./gradlew :app:compileDebugKotlin     # 快速编译反馈
./gradlew :app:testDebugUnitTest      # 全部 JVM 单测（含 Robolectric 数据层测试）
./gradlew :app:assembleDebug          # 产出 APK
./gradlew --stop                      # 用完即停（必须）
```

## 项目约定（改代码前先读）

- **金额一律 `Long` 最小货币单位**（JPY=1 円、CNY=1 分），格式化/解析只走 `domain/model/Money.kt`；
  不要引入浮点金额。
- **自动分摊结果不落库**：EQUAL / BY_PRICE 由 `domain/cost/CostEngine.kt` 实时计算（池化分摊）；
  只有 MANUAL 的分摊写入 `expense_allocation`。
- **VNDB 是缓存、本地是事实来源**：`owned_copy` 保存标题/封面快照，VNDB 缓存被清理不影响收藏。
- 改 Room schema：升 `version` + 写 `Migration`（schema 导出目录 `app/schemas/`，勿手改）。
- UI 文案直接写在 Compose 代码里（中文）；没有 XML 布局。
- 网络层只用 `api.vndb.org/kana`（字段以 schema 为准）；不要引入批量抓取或未授权的 API 用法。
