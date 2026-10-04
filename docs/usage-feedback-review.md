# 真实使用反馈与工程收尾（2026-10-04）

## 数据兼容方案

- Room v3：正式注册 `MIGRATION_1_2` 和 `MIGRATION_2_3`。仅重建 `owned_copy` 以放宽 `priceMinor` 的 NOT NULL，原值包括 0 原样保留；不猜历史价格。
- 重建前暂存手动分摊和自增序列，重建后恢复，保留收藏 ID、订单关联、快照和购买事实。迁移测试使用仓库真实 v1/v2 schema，并检查外键及清空收藏后的自增序列。
- 由 Room 编译器导出 `app/schemas/com.vnventory.app.data.local.VNventoryDatabase/3.json`，未手工编辑 schema。
- JSON 备份升级为 v2，明确写出 `priceMinor: null`，继续接受 v1 的非空整数价格。金额校验和恢复均不补 0；历史名称、分类、手动分摊继续保留。
- 两个价格开关随备份保存/恢复，缺少字段时默认 false；旧备份缺少店铺列表仍不清空本机列表。Room 与 DataStore 仍是两次提交，配置失败明确提示购买事实已恢复，避免重复追加。
- 旧应用不能读取 v2 备份，回退需要保留升级前的备份。

## UI 与产品行为

- 添加、批量添加和编辑允许空价格；空白保存为 null，明确的 0 保存为真实零价。详情始终可看价格状态。
- 「在书架显示价格」和「显示价格统计」独立且默认关闭；开关不删除价格能力，不影响详情。设置项整行可点击并提供 Switch 无障碍语义。
- 没有重做首页：统计保留已记录金额口径，排除未知价格并显示价格覆盖盒数/总盒数；缺失币种组不会凭空生成零总额。批次列表、详情和分摊预览也提示价格覆盖。
- 书架标题下副标题删除；同版本单盒没有编号，多盒用自然顺序「第 N 盒」/「×N」。手动版本也参与区分，搜索和排序不会改变盒子的相对序号，仍可逐盒编辑。
- 新费用不再输入名称，直接选五种固定类型；名称列用稳定枚举 ID，不存翻译。旧类别和自定义名称可以显示、编辑和恢复，不删除数据库列。
- 无未分摊金额时不占一行；有金额时突出提示，allocation issue 继续显示。取消批次详情中与已记录支出重复的已分摊汇总；无附加费用的单盒行不重复显示相同金额。
- 平均/手动分摊可用于未知商品价格；比例分摊遇到未知价格时不猜权重。已存在的比例费用保留并提示问题/未分摊，不阻止清空商品价格。
- VNDB 缓存边界、多盒事实、手动版本重新绑定、币种分别统计及现有架构保持不变。

## 工程收尾

- 默认版本为 1.1.0 / 1001000，高于已存在的 v1.0.0。未改旧 tag，也未创建或推送新 tag。
- `AppContainer` 将实际 `BuildConfig.VERSION_NAME` 传给客户端工厂；`VndbApi` 不依赖 Android BuildConfig。所有 VNDB 端点继承统一 User-Agent 和集中 BASE_URL。
- 保留 15 秒连接、30 秒请求/读超时，调试日志只记 headers，非 2xx 仅向 UI 暴露状态码。网络优先、写穿缓存、第一页离线回退及分页 stalled 保护未重写。
- tag 发布提前校验版本和完整签名 secrets；构建后校验 APK 签名、文件存在和构建元数据中的版本/文件名。发布失败不更新 CHANGELOG，CHANGELOG 更新失败不影响已发布 APK；保留原幂等去重及 bot 分支提交不触发 tag 工作流。
- 签名临时文件权限 600，结束时清理，不输出密码/私钥；本地仍能构建 unsigned Release。未实际发布 GitHub Release。
- SVG 视觉设计不变，仅补充两个 path ID。单色图标的孔洞几何改为从 SVG 反向生成，不再在转换脚本中维护另一份轮廓；现有六个生成矢量资源逐字不变。

## 测试改动

- `NullablePriceTest`：空价格批量保存、单盒编辑为 0/清空、多盒独立、已记录总计/覆盖数量、批次全未知金额、null 升降序均排最后、DataStore 默认关闭与独立开关。
- `PriceDisplayUiTest`：默认书架隐藏金额/单盒编号/标题副标题，开启显示已知价格/真实零价，未知不冒充 0，详情保持可见、首页开关及覆盖率、无未分摊提示/真实未分摊提示、两个设置开关独立交互。
- `MigrationTest`：真实 v1 → v2 → v3、v2 → v3、空收藏自增序列保留，价格/零价/分摊/缓存关联/外键/快照保留。
- `BackupTest`：v2 null/0/设置往返，旧 v1 设置默认关闭，legacy Expense 恢复，未知价格导致既有比例费用失效时仍能恢复事实。
- `CostEngineTest`：未知价格不生成零成本，平均/手动仍可分摊，比例不猜权重、保留 issue 和未分摊金额。
- `ExpenseEditorTest`：五种新建类型、稳定 ID、旧分类/名称/备注保留；`PurchaseFormTest` 更新空价格语义。
- `ShelfUiTest` 更新自然语言多盒选择；`ViewModelRegressionTest` 更新固定费用录入，未删除测试。
- `VndbClientFactoryTest`：三个端点的注入版本和实际 BuildConfig 版本 User-Agent；`VndbDtoParseTest` 验证非 2xx 不泄露响应正文。
- Python release/branding 回归：tag/versionCode、逐项缺失 secret、APK 缺失/空文件、元数据版本/文件名、签名发布顺序和保护、SVG 一致性与孔洞源变化检测。已有工具链、资源和 CHANGELOG 测试继续运行。

## 实际修改文件

以下包括开始时工作区已有、与本次要求一致且保留核验的发布/VNDB 改动。

```text
.github/workflows/ci.yml
.github/workflows/release.yml
AGENTS.md
README.md
app/build.gradle.kts
app/schemas/com.vnventory.app.data.local.VNventoryDatabase/3.json
app/src/main/java/com/vnventory/app/data/backup/BackupModels.kt
app/src/main/java/com/vnventory/app/data/local/VNventoryDatabase.kt
app/src/main/java/com/vnventory/app/data/local/dao/OwnedCopyDao.kt
app/src/main/java/com/vnventory/app/data/local/dao/Projections.kt
app/src/main/java/com/vnventory/app/data/local/dao/PurchaseOrderDao.kt
app/src/main/java/com/vnventory/app/data/local/entity/OwnedCopyEntity.kt
app/src/main/java/com/vnventory/app/data/remote/vndb/VndbApi.kt
app/src/main/java/com/vnventory/app/data/remote/vndb/VndbClientFactory.kt
app/src/main/java/com/vnventory/app/data/repository/BackupRepository.kt
app/src/main/java/com/vnventory/app/data/repository/CollectionRepository.kt
app/src/main/java/com/vnventory/app/data/repository/LocalRules.kt
app/src/main/java/com/vnventory/app/data/repository/PurchaseRepository.kt
app/src/main/java/com/vnventory/app/data/repository/SettingsRepository.kt
app/src/main/java/com/vnventory/app/di/AppContainer.kt
app/src/main/java/com/vnventory/app/domain/cost/CostEngine.kt
app/src/main/java/com/vnventory/app/domain/model/CollectionModels.kt
app/src/main/java/com/vnventory/app/domain/model/Enums.kt
app/src/main/java/com/vnventory/app/domain/model/OrderModels.kt
app/src/main/java/com/vnventory/app/domain/model/PurchaseModels.kt
app/src/main/java/com/vnventory/app/domain/text/Message.kt
app/src/main/java/com/vnventory/app/ui/add/AddFlowViewModel.kt
app/src/main/java/com/vnventory/app/ui/add/PurchaseFormContent.kt
app/src/main/java/com/vnventory/app/ui/collection/CollectionScreen.kt
app/src/main/java/com/vnventory/app/ui/collection/CollectionViewModel.kt
app/src/main/java/com/vnventory/app/ui/components/ShelfComponents.kt
app/src/main/java/com/vnventory/app/ui/detail/CopyDetailScreen.kt
app/src/main/java/com/vnventory/app/ui/detail/CopyDetailViewModel.kt
app/src/main/java/com/vnventory/app/ui/edit/CopyEditScreen.kt
app/src/main/java/com/vnventory/app/ui/edit/CopyEditViewModel.kt
app/src/main/java/com/vnventory/app/ui/home/HomeScreen.kt
app/src/main/java/com/vnventory/app/ui/home/HomeViewModel.kt
app/src/main/java/com/vnventory/app/ui/orders/OrderDetailScreen.kt
app/src/main/java/com/vnventory/app/ui/orders/OrderDetailViewModel.kt
app/src/main/java/com/vnventory/app/ui/orders/OrdersScreen.kt
app/src/main/java/com/vnventory/app/ui/settings/SettingsDetailScreens.kt
app/src/main/java/com/vnventory/app/ui/settings/SettingsViewModel.kt
app/src/main/java/com/vnventory/app/ui/text/Messages.kt
app/src/main/res/values/messages.xml
app/src/main/res/values/strings.xml
app/src/test/java/com/vnventory/app/data/BackupTest.kt
app/src/test/java/com/vnventory/app/data/MigrationTest.kt
app/src/test/java/com/vnventory/app/data/NullablePriceTest.kt
app/src/test/java/com/vnventory/app/data/ViewModelRegressionTest.kt
app/src/test/java/com/vnventory/app/data/remote/vndb/VndbClientFactoryTest.kt
app/src/test/java/com/vnventory/app/data/remote/vndb/VndbDtoParseTest.kt
app/src/test/java/com/vnventory/app/domain/CostEngineTest.kt
app/src/test/java/com/vnventory/app/domain/ExpenseEditorTest.kt
app/src/test/java/com/vnventory/app/domain/PurchaseFormTest.kt
app/src/test/java/com/vnventory/app/ui/PriceDisplayUiTest.kt
app/src/test/java/com/vnventory/app/ui/ShelfUiTest.kt
assets/branding/vnventory.svg
docs/usage-feedback-review.md
scripts/check.sh
scripts/convert-branding.py
scripts/release.py
scripts/tests/branding-test.py
scripts/tests/release-test.py
```

## 刻意没有改动的范围

- 月份切换/年度统计未新增：采用需求允许的小范围方案，默认关闭现有统计、排除未知价格并显示覆盖数量。
- 已知 Gradle 10 兼容性弃用提示（包括现有 sourceSets.srcDir）未顺手更换构建 DSL 或升级工具链。
- 未改 Room 与 DataStore 无法跨存储原子提交的既有架构，仅保持明确的部分恢复结果。
- 未更改原签名、更改旧 tag、自动发布/推送；正式签名 secrets 仍需在 GitHub 仓库配置，不能靠本地模拟证明线上 secrets 有效。
- 本机无 AVD/真机操作，未把 Robolectric 交互测试宣称为真机验收；IME、返回手势和滚动体验仍需设备验证。

## 验证结果

- `python3 scripts/convert-branding.py --check`：通过，六个生成资源与 SVG 一致。
- `scripts/check.sh --offline`：通过，161 项 JVM/Room/Compose 测试无失败、无跳过；18 项 Python 回归通过；Debug APK 和 Lint 构建通过。
- 无私钥的 `:app:assembleRelease --offline`：通过，生成本地 unsigned APK；正式发布工作流不使用该包。
- 最终联合复核 `env VNVENTORY_SIGNING_PROPERTIES=/tmp/opencode/vnventory-unsigned-test-missing.properties scripts/check.sh --offline :app:assembleRelease`：通过（签名配置路径刻意不存在），Debug、Release、测试、Lint 均成功。两个 APK 元数据均为 versionName 1.1.0 / versionCode 1001000。
- Lint：0 错误，5 个既有告警（3 个调试预览矢量大小告警、2 个 Kotlin 插件新版本提示）；本次产生的未用资源和 Modifier 参数顺序告警已清理。不为消除告警改品牌设计或升级依赖。
- APK：`app/build/outputs/apk/debug/app-debug.apk`；本地无签名验证产物 `app/build/outputs/apk/release/app-release-unsigned.apk`。
- 工作流 YAML：使用本机已有 PyYAML 解析通过；全部 `run` 步骤经 `bash -n` 校验通过。未安装 actionlint，不声称完成 GitHub 托管环境或线上签名 secrets 的实测。
- 中间一轮新增开关交互测试发现独立渲染时两行布局重叠，已给组件补上 Column 和整行 Switch 语义，保留测试并修复后重新跑全量。
- `git diff --check`：通过。
- 最后执行 `./gradlew --stop`，返回无 Gradle daemon；检查当前用户进程，Java/Javaw 均为 0，没有遗留构建 Java 进程。
