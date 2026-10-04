<p align="center">
  <img src="assets/branding/vnventory.svg" alt="VNventory — The collected V" width="180">
</p>

<h1 align="center">VNventory</h1>

<p align="center">把每一盒收藏，都收进书架。</p>

VNventory 是一款面向 Galgame / Visual Novel 实体收藏的 Android 应用。记录的不只是玩过哪些作品，更是：**买了哪个具体版本、拥有几盒，以及每盒最终实际花了多少钱。**

应用无需账号或云服务，收藏和购买记录保存在本机。VNDB 只提供可重新加载的作品与版本元数据；用户的收藏记录才是事实来源。

## 功能

- **收藏书架**：封面网格与详细列表、搜索、排序；同一版本的多盒始终独立记录。
- **VNDB 作品与版本**：按需搜索和分页，查看发行日期、平台、语言、发行商、JAN/EAN 与包装图；无对应版本时可创建手动版本，之后再绑定。
- **逐盒购买记录**：记录价格、币种、品相、日期、店铺/渠道与备注；批量添加也会为每盒建立独立记录。
- **常用店铺/渠道**：在设置中维护名称，在收藏或批次表单里直接选择；也可临时填写。管理候选项不会更改历史购买记录。
- **购买批次与成本**：集中记录多盒商品和运费、手续费、税费等费用，预览整个批次的最终成本。
- **三种费用分摊**：平均分摊、按价格比例、手动指定；显示未分摊金额，并在保存时再次校验。
- **JSON 备份与恢复**：系统文件选择器导出或导入，可追加或二次确认后覆盖；包含购买记录和可选恢复的常用店铺/渠道。
- **书架风格界面**：深浅主题、减少动态效果支持、预测性返回，以及为新设计的“收集 V”品牌图标。

## 界面与品牌

新图标的原始 SVG 保存在 [`assets/branding/vnventory.svg`](assets/branding/vnventory.svg)。Android 启动图标、主题单色图标和应用内标记使用从原稿转换的原生矢量，不使用位图或运行时 SVG 库。上一版原稿保存在 [`assets/branding/vnventory-previous.svg`](assets/branding/vnventory-previous.svg)。

矢量资源可用 Python 标准库重新生成并校验：

```bash
python3 scripts/convert-branding.py --check
python3 scripts/convert-branding.py --write
```

界面与返回动效约定见 [`docs/ui-polish.md`](docs/ui-polish.md)；本机测试截图位于 `toolchain/review/ui/screenshots/`，不提交到仓库。

## 技术栈与架构

| 组件 | 版本 / 用途 |
| --- | --- |
| Kotlin | 2.3.21（AGP 9 内置 Kotlin） |
| Android Gradle Plugin / Gradle | 9.4.1 / 9.8.0 |
| Jetpack Compose | Compose BOM 2026.09.00、Material 3 |
| Room | 2.8.5，本地收藏与购买数据 |
| DataStore Preferences | 货币和店铺/渠道候选项 |
| Ktor Client | 3.6.0，VNDB Kana API |
| Coil | 3.6.3，封面与实体包装图 |
| Android SDK | minSdk 26 / targetSdk 37 / compileSdk 37.2 |

应用采用单 Activity、Compose、MVVM 和 Repository，使用轻量手写依赖容器，不依赖 Hilt。源码主要位于 `app/src/main/java/com/vnventory/app/`：

```text
├── core/              结果与错误类型
├── domain/            纯 Kotlin 领域模型、国际化消息键、金额与成本分摊
├── data/local/        Room Entity、DAO、数据库与 Migration
├── data/remote/       VNDB Kana API、DTO 和客户端
├── data/backup/       独立于 Room schema 的 JSON 备份格式
├── data/repository/   收藏、订单、设置和备份仓库
└── ui/                Compose 页面、ViewModel、组件、导航与主题
```

用户可见文案位于 `app/src/main/res/values/strings.xml` 和 `messages.xml`。领域层只产生语言无关的消息键，UI 显示时解析资源；新增翻译可放入 `values-<locale>/`，不需要让业务逻辑依赖 Android。细节见 [`docs/localization.md`](docs/localization.md)。

## 金额与成本规则

- 金额使用 `Long` 最小货币单位存储：JPY 以円计，CNY 以分计；输入、格式化统一经过 `domain/model/Money.kt`，不使用浮点金额。
- 自动分摊按“模式 + 费用币种”池化计算，并以最大余数法保证总额守恒；计算结果不落库。
- 按价格比例分摊要求本订单商品币种一致，因为应用不做隐式汇率换算；全部商品价格为零时退化为平均分摊。
- 手动分摊只保存用户指定值：空白代表 0，拒绝非法、负数、超额或不属于当前订单的分配；未分完金额明确显示。
- 比例运算使用 `BigInteger`，各币种汇总进行溢出检查。超出 `Long` 范围时拒绝保存并回滚事务。
- 订单支出按币种分别满足：**商品金额 + 全部费用 = 已分摊收藏成本 + 未分摊费用**。

## 本地数据与数据库

Room 当前为 **v2**。VNDB 的 VN 与 Release 缓存通过 `release_vn` 多对多关联，以支持同一 Release 属于多个 VN。v1 → v2 Migration 只调整元数据缓存关联，不重建购买事实表。

每盒收藏保存标题、版本名和封面链接快照。即使 VNDB 缓存变化或清空，用户的购买记录也不受影响。费用自动分摊实时计算；只有手动分摊明细保存到数据库。

常用店铺/渠道和默认货币保存在 DataStore。备份在独立的 `VNventoryBackup` JSON 格式中携带店铺候选项；恢复旧版备份时，如果文件没有店铺列表字段，就保留设备当前列表。候选项改名或删除不会重写收藏及批次中的历史名称。

## 备份、隐私与网络

- 备份通过 Android 系统文件选择器读写，不申请传统存储权限。
- JSON 备份为**未加密文本**，包含价格、店铺/渠道、备注及收藏快照。请存放在可信位置，并在应用外保留副本。
- 追加恢复会重新映射本地 ID，不自动去重；覆盖恢复会替换全部购买事实，并要求二次确认。
- 恢复前验证格式、金额、日期、ID、订单关系和手动分摊；购买事实在单个 Room 事务内提交。备份不包含 VNDB 缓存或图片文件。
- Android 系统自动备份与数据提取已禁用。卸载应用或清除数据前，请先导出备份。
- 网络层仅按用户操作请求 `https://api.vndb.org/kana`，不做批量抓取。VNDB 元数据属于缓存，收藏不会自动上传。VNDB 与权利方保留其数据版权；本应用与 VNDB 官方无隶属关系。

## 构建与测试

本仓库的 Android SDK、Gradle 缓存和 Robolectric 依赖放在已忽略的项目内 `toolchain/`，不需要安装到全局目录。构建环境细节见 [`AGENTS.md`](AGENTS.md)。首次配置需本机已有 JDK 17–21、Python 3、curl、unzip 和 sha256sum：

```bash
VNVENTORY_JDK=/path/to/jdk scripts/setup-android-env.sh

./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew --stop
```

一键检查（离线模式要求依赖已缓存；成功或失败都会停止 Gradle daemon）：

```bash
scripts/check.sh --offline
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。Debug 包名为 `com.vnventory.app.debug`，正式版为 `com.vnventory.app`，可同时安装且数据相互隔离。迁移旧 Debug 安装中的真实收藏前，请先通过应用内备份导出并在新版本恢复。

测试采用 JUnit、Robolectric、Room 内存数据库和 Compose UI 测试，覆盖金额/分摊、数据库迁移、缓存分页与并发请求、备份兼容、商店候选项、资源消息、页面渲染和返回动效。本机没有 AVD；Robolectric 测试不能代替真机上的系统返回手势、IME 和滚动体验验证。

## Release 构建与签名

Release 开启 R8 优化/混淆和资源压缩；配置签名后 APK 使用 v2/v3 签名。Release 签名从仓库外的 `~/.sign/vnventory-release.properties` 读取；也可以通过 `VNVENTORY_SIGNING_PROPERTIES` 指定私有配置文件。文件包含 `storeFile`、`storeType`、`keyAlias`、`storePassword` 和 `keyPassword`。**不要提交签名私钥或密码**；后续更新必须沿用同一签名。

```bash
./gradlew :app:assembleRelease
./gradlew --stop
```

没有本地签名配置时 Debug 构建和测试不受影响，Release 也可构建为未签名 APK；签名 Release 需要事先准备私有配置。Release APK 位于 `app/build/outputs/apk/release/app-release.apk`。

GitHub Actions 会在推送 `vMAJOR.MINOR.PATCH` 标签时创建 GitHub Release，并按标签设置 `versionName`。Android `versionCode` 根据 `MAJOR.MINOR.PATCH` 计算（`MAJOR × 1,000,000 + MINOR × 1,000 + PATCH`；`MINOR` 和 `PATCH` 均须小于 1000），本地默认版本也使用同一规则。需要签名发布时，在仓库 Actions secrets 中配置 `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS` 和 `ANDROID_KEY_PASSWORD`；`ANDROID_KEYSTORE_TYPE` 可选，默认为 `PKCS12`。未配置签名密钥时仍会发布未签名 APK，该 APK 不能作为已有签名安装的更新包。

## 当前不包含

账号、云同步、VNDB 账号收藏同步、汇率换算、社区、AI、统计图表、多平台或 Web 版本。项目使用 Mozilla Public License 2.0，见 [LICENSE](LICENSE)。
