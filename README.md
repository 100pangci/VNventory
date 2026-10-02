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
- 添加收藏：搜索 VN → 查看 VN 信息 → 选择具体 Release（展示发行日期 / 平台 / 语言 / 发行商 / JAN(EAN/UPC) / 实体包装图）→ 填写购入信息（价格、币种、品相、日期、店铺、数量、所属订单、备注）
- 手动版本：找不到对应版本时可创建；详情/编辑页可一键绑定到 VNDB Release
- 收藏详情：VN / Release 信息、买入价格、分摊费用明细、**最终实际成本**、品相、店铺、日期、备注
- 收藏编辑 / 删除
- 购买批次：新建批次、批次内多盒、多个费用、每盒最终成本实时预览
- 费用分摊：**EQUAL 平均分摊 / BY_PRICE 按价格比例 / MANUAL 手动指定**；费用编辑器带分摊预览与手动分配差额校验
- 设置：默认货币；数据与来源说明

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

## 三、数据库设计（Room v1）

```
vn_cache 1 ── n release_cache          （纯缓存，可整体清空，不影响收藏）
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

Schema 导出目录：`app/schemas/`（Room 插件生成，供后续迁移使用）。

### 成本分摊规则（CostEngine）

| 模式 | 规则 |
| --- | --- |
| EQUAL | 平均分；除不尽时用**最大余数法**分配零头，**保证各盒之和 == 费用金额** |
| BY_PRICE | 按各盒本体价格比例；全部 0 价时退化为平均；免费（0 价）盒子不承担比例费用 |
| MANUAL | 完全按用户输入；允许存在差额，编辑界面会提示差额 |

全流程使用 `BigInteger` 计算，避免大金额溢出；结果确定性（余数相同取索引靠前者）。

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
└── android-user-home/    # ANDROID_USER_HOME
```

- 引导脚本：`scripts/setup-android-env.sh`（可重复执行；重建工具链时使用）。
- `gradlew` 内置了“缓存留在项目内”的默认值（可用环境变量覆盖）：
  未设置 `GRADLE_USER_HOME` 时自动指向 `toolchain/gradle-home`。
- `local.properties` 指向项目内 SDK：`sdk.dir=<项目>/toolchain/android-sdk`。
- Gradle/AGP 使用的 JDK：由 `toolchain/gradle-home/gradle.properties` 的 `org.gradle.java.home` 指定
  （引导脚本自动写入，不进版本库；其他机器用 `JAVA_HOME` 或自行设置即可，信任项目内的 `gradle.properties` 保持可移植）。
- 内存友好配置：`org.gradle.daemon.idletimeout=60000`（空闲 1 分钟自动退出）、
  `kotlin.compiler.execution.strategy=in-process`（不产生常驻 Kotlin daemon）。

### 常用命令

```bash
cd <项目目录>

./gradlew :app:assembleDebug        # 构建 Debug APK
./gradlew :app:testDebugUnitTest    # 全部单元测试（成本引擎 / 金额 / DTO 解析）
./gradlew :app:compileDebugKotlin   # 只编译（快速反馈）
./gradlew --stop                    # 用完即停（释放内存；本机已配置 60s 自动退出）

# 构建完记得杀进程：
pkill -f 'KotlinCompileD[a]emon' || true
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

未接模拟器（本机未配置 AVD）；需要真机时：`adb install -r app/build/outputs/apk/debug/app-debug.apk`。

### 首次使用流程（应用内）

1. 首页 / 收藏页右下角 **＋** → 搜索作品（罗马字 / 日文原名 / 中文译名）→ 选择作品；
2. 在版本列表选择具体 Release（无对应版本时 → **创建手动版本**）；
3. 填写购入价格、币种、品相、日期、店铺、数量（>1 会生成多盒，可稍后单独修改价格）、可选加入订单；
4. 订单页 → 新建批次 → 添加游戏、添加费用（国际运费 / 手续费 / 税费…）→ 每盒最终成本即时可见。

---

## 六、测试

JVM 单元测试（`./gradlew :app:testDebugUnitTest`）：

- `CostEngineTest`：均摊 / 池化 / 按比例 / 手动 / 多币种隔离 / 零价 / 大数不溢出 / 用户示例 A=84,B=94,C=104
- `MoneyTest`：最小单位 ↔ 字符串互转、千分位、超位数拒绝、未知货币回退
- `VndbDtoParseTest`：VNDB 响应解析与 DTO → 领域映射（样本 JSON，离线）
- `DataLayerTest`（Robolectric + Room 内存库）：搜索/排序 SQL、订单删除语义（收藏保留、费用级联）、
  手动分摊清理（移出订单/删盒/改模式）、池化成本端到端

（UI 层未做仪器化测试；本机无 AVD。Robolectric 的 android-all 依赖缓存于 `toolchain/maven-local`。）

---

## 七、许可

本项目采用 **Mozilla Public License 2.0（MPL-2.0）**，完整文本见 [LICENSE](LICENSE)。

简要说明（非法律意见）：

- 你可以自由使用、修改、分发本项目代码，需保留版权声明与许可证文本；
- 对 MPL 覆盖文件的修改（该文件本身的源码）需要继续以 MPL-2.0 提供；
- 允许把本项目与闭源代码组合成一个更大的作品发布（文件级 copyleft，不是整作品传染）。

---

## 八、已知限制与后续方向

- 无云同步 / 账号（刻意不做）；
- 汇率不做换算（多币种分行展示）；
- 列表排序中的价格排序是“原始数值”排序，跨币种比较无意义（同币种订单内才有可比性）；
- 手动版本绑定 UI 目前提供 Release 列表选择（含包装图/日期/平台）；
- 后续可做：统计图表、导出/导入、批量编辑、VNDB 账号收藏导入等。
