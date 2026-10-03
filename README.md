# VNventory

面向 **Galgame / Visual Novel 实体收藏** 的管理工具（Android 原生）。

核心不是“玩过什么”，而是记录清楚：**我买了哪个具体版本、买了几盒、每盒最终实际花了多少钱。**

---

## 一、项目定位与 MVP 范围

以「实体盒（OwnedCopy）」为中心：

- 同一个 VNDB Release 可以拥有 **多盒**，每盒的价格 / 品相 / 购买时间 / 店铺 / 备注都可以不同；
- 一次购买 / 一次转运 = 一个 **PurchaseOrder（购买批次）**，批次里的运费、手续费、税费等按规则 **实时分摊** 到每一盒；
- VNDB 数据只是 **元数据缓存**：VNDB 数据变化、缓存被清空，都不会影响用户的购买记录；
- 找不到对应版本时可以先建 **手动版本**，之后随时绑定到 VNDB Release（数据结构和 UI 均已支持）。

已实现（第一阶段 MVP）：

- 首页：收藏 VN 数 / 实体盒数 / 总支出（含运费、手续费、税费、其他），最近购入
- 收藏列表：封面网格 / 列表两种视图、搜索、8 种排序；区分 Release、同版本多盒独立存在（角标显示 ×N）
- 添加收藏：搜索 VN → 查看 VN 信息 → 选择具体 Release（展示发行日期 / 平台 / 语言 / 发行商 / JAN(EAN/UPC) / 实体包装图，隐藏非官方版本）→ 分区填写购入信息（单盒价格与数量、品相、购买记录、备注）
- 手动版本：找不到对应版本时可创建；详情/编辑页可一键绑定到 VNDB Release
- 收藏详情：VN / Release 信息、买入价格、分摊费用明细、**最终实际成本**、品相、店铺、日期、备注
- 收藏编辑 / 删除
- 购买批次：新建批次、批次内多盒、多个费用、保存后的完整成本预览；区分订单支出、已分摊收藏成本和未分摊费用
- 费用分摊：**EQUAL 平均分摊 / BY_PRICE 按价格比例 / MANUAL 手动指定**；费用编辑器带分摊预览与手动分配差额校验
- 设置：分组入口与子页面（默认货币、备份与恢复、关于与数据来源）
- JSON 备份：默认货币、全部收藏/购买批次/费用/手动分摊；追加恢复或二次确认后覆盖恢复
- 关于：新品牌图标、应用与版本信息、本机隐私说明、VNDB 来源及 MPL-2.0 许可

界面采用「实体收藏书架」视觉方向：首页以最近入藏封面为主，收藏页以逐盒封面卡为主；
统一深浅主题、盒号/品相标记和按压/导航/步骤动效。真实金额不做滚动插值，保持成本数字准确。
作品默认以日语原文为主标题、罗马字为辅助标题；新加载版本也优先使用原文标题，旧收藏快照不强制改写。
页面导航及添加步骤支持跟随手势进度的预测性返回，取消时回弹，不提前清空当前选择或表单。
返回使用独立的 180ms 短转场，不叠加缩放；步骤手势松手只补完剩余进度，取消在 140ms 内回到原页面。
启动图标及应用内品牌标记统一使用用户提供的 SVG，原稿保存在 [`assets/branding/vnventory.svg`](assets/branding/vnventory.svg)；
Android 原生矢量保留彩色渐变与路径，另提供同源单色主题图标，不再使用旧盒子/心形图标。
动画兼容系统减少动态效果设置。界面截图及设计约定见 [`docs/ui-polish.md`](docs/ui-polish.md)，
本地预览截图由测试生成在 `toolchain/review/ui/screenshots/`（不提交）。

暂不包含（按需求搁置）：账号 / 云同步 / VNDB 账号同步 / 社区 / AI / 复杂统计图 / 多平台 / Web。

---

## 二、技术栈

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Kotlin | 2.3.21 | AGP 9 内置 Kotlin（不再单独应用 `kotlin-android` 插件） |
| Android Gradle Plugin | 9.4.1 | 内置 Kotlin；KGP / KSP 版本在根 `build.gradle.kts` buildscript classpath 提升 |
| Gradle | 9.8.0（wrapper） | |
| Jetpack Compose | BOM 2026.09.00 | Material 3，无 XML View |
| Room | 2.8.5（KSP + room 插件） | 本机数据库 |
| Ktor Client | 3.6.0（OkHttp 引擎） | VNDB API |
| kotlinx.serialization | 1.11.0 | JSON / typed navigation routes |
| Coil | 3.6.3 | 封面与包装图加载 |
| Coroutines / Flow | 1.11.0 | |
| DataStore Preferences | 1.2.1 | 设置 |
| minSdk / targetSdk / compileSdk | 26 / 37 / 37 (minor 2) | 单 Activity、纯 Compose |

架构：**单 Activity + Compose + MVVM + Repository**，手写依赖容器（未用 Hilt，MVP 规模足够）。
金额一律使用 **Long 最小货币单位**（日元=円，人民币=分），杜绝浮点误差；日期用 `java.time`（minSdk 26 原生支持）。

```
app/src/main/java/com/vnventory/app/
├── MainActivity.kt / VNventoryApp.kt      # 单 Activity；Application 持有 AppContainer
├── core/                                  # AppResult、错误映射
├── di/                                    # AppContainer、AppViewModelProvider（手写 DI）
├── domain/                                # 纯 Kotlin，可单测，不依赖 Android
│   ├── model/                             #   Money / 领域模型 / 枚举 / 查询参数
│   └── cost/CostEngine.kt                 #   成本分摊引擎（纯函数 + 最大余数法）
├── data/
│   ├── local/                             #   Room：entity / dao / database / Converters
│   ├── remote/vndb/                       #   Ktor + DTO（api.vndb.org kana API）
│   ├── mapper/                            #   DTO/Entity ↔ Domain
│   └── repository/                        #   VnRepository / CollectionRepository /
│                                          #   PurchaseRepository / SettingsRepository
└── ui/
    ├── theme/                             # Material 3 主题（完整深色支持）
    ├── components/                        # 通用组件（封面、状态视图、金额、日期、选择器）
    ├── navigation/                        # typed routes + NavHost
    ├── home|collection|add|detail|edit|orders|settings/
    └── VNventoryRoot.kt                   # Scaffold + 底部导航
```

---

## 三、数据库设计（Room v2）

```
vn_cache 1 ── n release_vn n ── 1 release_cache  （多对多关联，合辑可以属于多个 VN）
purchase_order 1 ── n owned_copy       （删订单：收藏保留，orderId 置空）
purchase_order 1 ── n expense          （删订单：费用级联删除）
expense        1 ── n expense_allocation   （仅 MANUAL 模式存在行）
owned_copy     1 ── n expense_allocation   （删盒：分摊行级联删除）
```

关键约定：

- `owned_copy.release_id` 为 NULL ⇒ **手动版本**；绑定 VNDB Release 只是更新这一行；
- `owned_copy` 冗余保存 `vn_title / release_title / cover_url` 快照，保证 VNDB 缓存丢失后记录依旧完整；
- **自动分摊结果不落库**：`EQUAL / BY_PRICE` 由 `CostEngine` 实时计算（页面展示的“最终成本”永远是最新值）；
- `MANUAL` 模式把用户指定的金额存入 `expense_allocation`；
- 盒子被移出订单、删除，或费用改为自动模式时，陈旧的分摊行会被清理（事务内完成）；
- 多币种 **不做隐式汇率换算**：每盒最终成本按币种分行展示（如 `8400 JPY + 34 CNY`）。

Schema 导出目录：`app/schemas/`（保留 v1 与 v2，Room 插件生成）。
`MIGRATION_1_2` 只重构元数据缓存关联，保留已知关联、订单、收藏、费用与手动分摊；不使用破坏性重建。

### 成本分摊规则（CostEngine）

| 模式 | 规则 |
| --- | --- |
| EQUAL | 同币种的均摊费用先汇成池，再使用最大余数法分给各盒；有商品时各盒分摊之和等于费用池总额 |
| BY_PRICE | 同币种商品按价格比例分摊；全部 0 价时退化为均摊。商品币种混合时禁用（未提供汇率）；费用自身可用另一币种 |
| MANUAL | 每个非空输入必须为有效非负金额；空白按 0；禁止超额分配。未分完的金额显示为“未分摊费用” |

比例乘除使用 `BigInteger`，金额汇总使用检查加法；超出 `Long` 范围时拒绝写入，事务回滚，不静默溢出。
结果确定（余数相同取排序靠前的盒）。费用编辑器使用与正式保存一致的**完整订单计算**，不再展示与池化不一致的单笔预览。
订单实际支出 = 商品本体价 + 全部费用 = 已分摊收藏成本 + 未分摊费用（按币种分别成立）。
旧版本中无效的超额/混币种分摊不会被擅自修成猜测值：界面提示并暂记为未分摊，用户可编辑修正。

### 示例（与需求文档一致）

A=50 / B=60 / C=70，国际支付手续费 2 + 国际运费 100（均摊）：

- 每盒附加费 = 34 → A=84，B=94，C=104

单元测试覆盖该示例及各种边界（`CostEngineTest` / `MoneyTest`）。

---

## 四、VNDB 数据来源

- 官方数据库 API：`https://api.vndb.org/kana`（Kana API，POST + JSON）。
  字段结构以 `https://api.vndb.org/kana/schema` 为准（本项目按 2026-10 的 schema 实测实现）。
- 使用到的端点与字段：
  - `POST /vn`：`id,title,alttitle,released,description,image{url,thumbnail},titles{title,lang,main,official}`
  - `POST /release`：`id,title,alttitle,released,platforms,languages{lang,main},producers{id,name,developer,publisher},gtin,minage,official,patch,images{id,type,url,thumbnail,dims,sexual,violence}`
    - `gtin` 即 JAN / EAN / UPC；
    - `images` 中优先取 `type=pkgfront`（实体包装图），无则退回 VN 封面；
    - 按 VN 过滤 Release 使用嵌套过滤器 `["vn","=",["id","=","v17"]]`。
- 客户端设置独立 `User-Agent`（`VNventory/0.1.0`），仅按用户操作发起请求，不做批量抓取。
- 缓存策略：**网络优先、写穿缓存**；网络失败时列表页回退本地缓存并标注「网络不可用：以下为本地缓存结果」。
- VN 搜索支持“加载更多”，下一页失败保留已有结果并可重试；选择 VN 后 Release 会受控分页取全。
  仅在所有页成功后，事务替换该 VN 的缓存关联；途中失败或取消不破坏旧缓存。
- 切换搜索/作品或返回上一层时取消旧请求，并校验请求代次和 VN ID；仓库再次校验 Release 与 VN 关联，避免错绑。
- 收藏记录不自动上传，应用禁用 Android 系统云备份/数据迁移；卸载或清除数据前请在设置中导出 JSON 备份，并保存在应用之外。
- 免责声明：数据版权归 VNDB 及权利方所有；本应用与 VNDB 官方无隶属关系。

---

## 五、开发与运行

### 本机的自包含工具链（重要）

本仓库的工具链**全部放在项目内 `toolchain/`**（已加入 `.gitignore`，不含在版本库）：

```
toolchain/
├── android-sdk/          # Android SDK（platforms/android-37.2、build-tools 37.0.0、platform-tools）
├── gradle-9.8.0/         # Gradle 发行版（用于生成 wrapper；日常用 ./gradlew）
├── gradle-home/          # GRADLE_USER_HOME：wrapper 分发包 + 全部依赖缓存
├── android-user-home/    # ANDROID_USER_HOME
├── maven-local/          # Robolectric android-all 测试依赖
└── downloads/            # 引导脚本下载、临时解压与日志
```

- 引导脚本：`scripts/setup-android-env.sh`（可重复执行；重建工具链时使用）。
- `gradlew` 内置了“缓存留在项目内”的默认值（可用环境变量覆盖）：
  未设置 `GRADLE_USER_HOME` 时自动指向 `toolchain/gradle-home`。
- 引导脚本生成 `local.properties`（`sdk.dir=<项目>/toolchain/android-sdk`），不覆盖已有 SDK 配置。
  `gradlew`/`gradlew.bat` 在没有外部 `ANDROID_HOME` 时也会自动发现项目内 SDK；不依赖本机曾经手动配置的路径。
- Gradle/AGP 使用的 JDK：由 `toolchain/gradle-home/gradle.properties` 的 `org.gradle.java.home` 指定
  （引导脚本自动写入，不进版本库；其他机器用 `JAVA_HOME` 或自行设置即可，信任项目内的 `gradle.properties` 保持可移植）。
- 内存友好配置：`org.gradle.daemon.idletimeout=60000`（空闲 1 分钟自动退出）、
  `kotlin.compiler.execution.strategy=in-process`（不产生常驻 Kotlin daemon）。

### 常用命令

```bash
cd <项目目录>

# 首次克隆（Linux）：已有 JDK 17–21、python3、curl、unzip、sha256sum 即可。
# 不安装全局软件；JDK 可以通过 VNVENTORY_JDK 显式指定。
VNVENTORY_JDK=/path/to/jdk scripts/setup-android-env.sh

./gradlew :app:assembleDebug        # 构建 Debug APK
./gradlew :app:testDebugUnitTest    # 全部单元测试（成本引擎 / 金额 / DTO 解析）
./gradlew :app:compileDebugKotlin   # 只编译（快速反馈）
./gradlew :app:lintDebug            # Android 静态检查
./gradlew --stop                    # 用完即停（仅本项目缓存下的 Gradle daemon）
# 本项目 Kotlin 使用 in-process 编译，无需 pkill 全局 Java/Kotlin 进程。

# 一键检查：引导脚本离线回归 + APK + JVM 测试 + Lint，成功/失败均停 daemon。
scripts/check.sh
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

Debug 包名为 **`com.vnventory.app.debug`**，Release 保持 **`com.vnventory.app`**，可以同时安装，应用私有数据各自隔离。
源码 namespace 仍为 `com.vnventory.app`；Debug 预览封面 URI 使用 `BuildConfig.APPLICATION_ID`，不写死正式版包名。
旧的无 `.debug` 后缀 Debug 版不会自动迁移数据到新包名：如有真实收藏，请先在旧版导出备份，再在新版恢复，不要直接卸载旧版。

### Release 优化与签名

- Release 开启 **R8 混淆/优化/代码压缩**（`isMinifyEnabled=true`）及**资源压缩**（`isShrinkResources=true`），
  使用 `proguard-android-optimize.txt` 和 `app/proguard-rules.pro`。
- 签名配置默认读取 `~/.sign/vnventory-release.properties`；可通过 `VNVENTORY_SIGNING_PROPERTIES` 指定其他私有配置文件。
  配置字段为 `storeFile`、`storeType`、`keyAlias`、`storePassword`、`keyPassword`；相对的 `storeFile` 以配置文件所在目录为基准。
  不要把实际密码写进源码、README 或日志。缺少私有配置不影响 Debug 构建，但签名 Release 需要先配齐。
- 本机私钥为 `/home/ywpc/.sign/vnventory-release.p12`（PKCS12，RSA 4096，别名 `vnventory`），
  使用 APK v2/v3 签名（minSdk 26 无需 v1，v4 增量安装签名未开启）。
  私有配置与私钥权限 `600`，所在目录权限 `700`；两者均在仓库外，另有 Git 忽略规则防误提交。
- **务必在安全位置另外备份私钥及签名配置**。后续发布更新需沿用同一个签名，不要重新生成或覆盖。
  Release 与 Debug 证书不同；新 Debug 版通过 `.debug` 包名与正式版并存，不互相覆盖。
  旧的无后缀 Debug 版仍与 Release 同包名且签名不同，换签名或卸载前先导出备份，确认文件已保存在应用之外且可读取。

```bash
./gradlew :app:assembleRelease
./gradlew --stop
# 其他机器 / CI：仅向该进程传入私有配置路径，不通过命令行传密码
VNVENTORY_SIGNING_PROPERTIES=/private/path/vnventory-release.properties ./gradlew :app:assembleRelease
./gradlew --stop
```

签名 APK：`app/build/outputs/apk/release/app-release.apk`。
R8 映射：`app/build/outputs/mapping/release/mapping.txt`（应按发布版本安全归档，供崩溃堆栈还原，不提交构建产物）。

本轮配置修改前备份：`toolchain/backups/20261003-171218-before-release-signing.tar.gz`。
如需回退构建配置，先保存当前工作区，再逐文件比对该归档中的 `app/build.gradle.kts`、`.gitignore` 和 README；
不要整仓重置，也不要删除或替换已经用于发布的签名私钥。

未接模拟器（本机未配置 AVD）；真机安装使用项目内 adb：
`toolchain/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`。

### 首次使用流程（应用内）

1. 首页 / 收藏页右下角 **＋** → 搜索作品（罗马字 / 日文原名 / 中文译名）→ 选择作品；
2. 在版本列表选择具体 Release（无对应版本时 → **创建手动版本**）；
3. 填写购入价格、币种、品相、日期、店铺、数量（>1 会生成多盒，可稍后单独修改价格）、可选加入订单；
4. 订单页 → 新建批次 → 添加游戏、添加费用（国际运费 / 手续费 / 税费…）→ 每盒最终成本即时可见。

### 备份与恢复

设置 → **备份与恢复**：

- **导出备份**：通过系统文件选择器保存 `VNventory-backup-日期.json`，无需存储权限。
  包含默认货币、所有收藏快照、购买批次、费用及手动分摊；金额仍为 `Long` 最小货币单位，日期为 ISO 文本。
  不包含 VNDB 缓存、下载的封面图片或自动分摊结果；封面链接随收藏快照保留。
- **选择备份文件**：先完整验证文件格式、版本、金额、日期、ID 和订单/分摊关联，再显示备份数量及导出时间。
  **追加恢复**保留现有记录、重新映射所有本地 ID（重复导入不会去重）；**覆盖恢复**需再次确认，将替换全部购买事实，但不清理 VNDB 缓存。
  可取消勾选“同时恢复默认货币”以保留当前配置。
- 购买事实在一个 Room 事务内恢复，非法数据、写入失败或追加后全库金额溢出均回滚。
  默认货币在购买事实提交后写入 DataStore；如仅配置写入失败，界面明确提示数据已恢复，避免重复追加。
- 备份格式独立版本 `VNventoryBackup / schemaVersion=1`，拒绝未知版本或超过 **32 MiB** 的文件，不静默丢弃坏记录。
  JSON 为未加密文本，含购买价格、店铺和备注等私人信息，请妥善保管。

---

## 六、测试

JVM 单元测试（`./gradlew :app:testDebugUnitTest`）：

- `CostEngineTest`：均摊 / 池化 / 按比例 / 手动 / 多币种隔离 / 零价 / 大数不溢出 / 用户示例 A=84,B=94,C=104
- `MoneyTest`：最小单位 ↔ 字符串互转、千分位、超位数拒绝、未知货币回退
- `VndbDtoParseTest`：VNDB 响应解析与 DTO → 领域映射（样本 JSON，离线）
- `DataLayerTest`（Robolectric + Room 内存库）：搜索/排序 SQL、订单删除语义（收藏保留、费用级联）、
  手动分摊清理与校验、错绑阻止、混币种限制、事务回滚、支出守恒、完整订单预览与新增/编辑保存一致
- `MigrationTest`：从导出的 v1 Schema 建库升级到 v2，验证 Room Schema、购买事实和分摊保留、合辑多 VN 缓存
- `CatalogRegressionTest`：超过 100 条 Release 的分页去重、失败不覆盖旧缓存、取消传播、合辑关联
- `ViewModelRegressionTest`：慢网旧请求隔离、搜索分页重试、写入失败保留表单且错误可见、恢复后重试
- `ExpenseEditorTest`：非法/负数/超额输入阻止保存、空白明确为 0、部分分摊
- `PurchaseFormTest`：按单盒价格与数量精确计算商品小计，溢出或非法数量禁止保存
- `ShelfUiTest` / `ReducedMotionUiTest`（Compose + Robolectric Native Graphics）：深浅主题渲染截图、空状态、大字体、封面/网格/列表/排序交互、保存反馈、步骤进度动画和系统减少动态效果
- `PurchaseFlowUiTest`：购入表单分区与大字体、数量/店铺输入、预测返回的跟手方向、取消回弹、完成返回及页面导航出栈
- `BackupTest`：JSON 往返、金额精度、损坏/未知版本/超大文件拒绝、关联与分摊校验、追加 ID 映射、覆盖事务回滚、配置部分失败及系统文件读写流程
- `SettingsAndDateUiTest`：设置分组、大字体深色备份页、覆盖二次确认与错误可见、日期模式切换保留选择和取消语义
- `BrandingTest` / 关于页 UI 测试：新图标的渐变、V 与金色价签、启动图标背景及单色镂空、深浅主题和大字体下的版本/隐私/来源/许可展示
- `BackMotionTest` / 返回 UI 回归：180ms 普通返回、手势剩余时长、取消保持页面、减少动态效果时即时完成
- `DebugPackagingTest`：Debug 独立应用 ID、Application/Activity namespace 解析、预览封面的包名与资源可用性

（本机无 AVD，未做真机/仪器化测试。Robolectric 的 android-all 依赖缓存于 `toolchain/maven-local`；本地界面截图在 `toolchain/review/ui/screenshots/`。）

---

## 七、许可

本项目采用 **Mozilla Public License 2.0（MPL-2.0）**，完整文本见 [LICENSE](LICENSE)。

简要说明（非法律意见）：

- 你可以自由使用、修改、分发本项目代码，需保留版权声明与许可证文本；
- **分发**包含修改的 MPL 覆盖文件时，需要按 MPL-2.0 提供相关源码；仅私人使用的修改不要求公开；
- 允许把本项目与闭源代码组合成一个更大的作品发布（文件级 copyleft，不是整作品传染）。

---

## 八、已知限制与后续方向

- 无云同步 / 账号（刻意不做）；
- 汇率不做换算（多币种分行展示）；
- 列表排序中的价格排序是“原始数值”排序，跨币种比较无意义（同币种订单内才有可比性）；
- 手动版本绑定 UI 目前提供 Release 列表选择（含包装图/日期/平台）；
- 后续可做：统计图表、批量编辑、VNDB 账号收藏导入等。
