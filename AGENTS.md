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
- 引导脚本会生成 SDK 路径配置；wrapper 会自动发现项目内 SDK，且 Linux/Windows 均将缓存默认放在项目内。
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
- 禁止混币种商品 BY_PRICE（无汇率）；MANUAL 不能非法、负数或超额，部分分摊须显示未分摊金额。
  仓库在事务内二次校验，预览必须使用完整订单引擎；金额相加使用 `Money.add/sum/totals`，不得静默溢出。
- **VNDB 是缓存、本地是事实来源**：`owned_copy` 保存标题/封面快照，VNDB 缓存被清理不影响收藏。
- Room 当前 v4，VN/Release 通过 `release_vn` 多对多；v1→v2→v3→v4 非破坏性迁移已有真实 schema 回归测试。
- 原标题/罗马音使用明确字段并同时缓存、同时保存 OwnedCopy 快照；旧标题只作 legacy fallback，不猜类型。TitleDisplayMode 只控制显示，不请求 VNDB、不批量重写收藏；手动版本名保持用户输入。全局 Compose 显示走 `ui/text/Titles.kt`，版本名开关独立。
- 商品价格 `Long?`：null=未记录，0=真实零价；不把空值补成0。比例分摊要求全部商品价格已知，平均/手动不受影响。
- 改 Room schema：升 `version` + 写 `Migration`（schema 导出目录 `app/schemas/`，勿手改）。
- 用户可见文案统一放在 `app/src/main/res/values/strings.xml` / `messages.xml`；Compose 使用
  `stringResource` / `pluralStringResource`，不得重新硬编码。默认中文，后续通过 `values-<locale>/` 添加翻译；没有 XML 布局。
- domain 保持纯 Kotlin：校验、枚举标签和成本提示使用 `domain/text/Message.kt` 的消息键，
  UI 在显示时解析资源；不得把翻译结果存进数据库、ViewModel 或备份。用户输入及 VNDB 快照不翻译。
- 常用店铺/渠道是 DataStore 候选列表；收藏和订单继续保存原始名称快照，候选项改名/删除不能改历史记录。
  备份中的候选列表为可选字段，旧备份没有该字段时不得清空本机列表。
- 网络层只用 `api.vndb.org/kana`（字段以 schema 为准）；不要引入批量抓取或未授权的 API 用法。
