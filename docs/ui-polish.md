# 界面与动效打磨

## 视觉方向

保留原生 Material 3 与紫罗兰/玫瑰配色，以实体收藏书架而非传统记账仪表盘为中心：

- 首页先呈现作品数量与“最近入藏”封面陈列。首页封面卡片不显示版本副标题和日期；详情里的版本副标题原样保留。支出信息默认关闭，开启后移到次级区并可展开构成。
- 收藏页统一封面卡片，保留版本和品相，价格按设置展示；只有同版本多盒才显示自然语言「第 N 盒」，不显示数据库 ID。同一个 Release 的多盒不会合并成一条。
- 封面/列表可切换，各自保持滚动状态；排序选项标记当前选择。
- 添加流程有三步进度指示，作品/版本用可按压卡片选择，最后一屏明确按单盒填写价格。
- 作品日语原文优先作为主标题，罗马字保留作辅助标题与搜索；新加载的 Release 使用原文版本名。
- 选择/绑定列表屏蔽 `official=false` 的版本，联网结果和旧缓存使用同一规则；元数据仍保留，不删除已有收藏。
- 收藏档案突出包装图与最终实际成本，购买记录和 VNDB 元数据分别分区，长简介可展开。
- 批次、编辑与设置页共用标题、圆角、间距和保存反馈；错误保留可关闭的提示和未保存表单。
- 设置首页按收藏偏好、数据管理、应用信息分组，点击进入各自子页面；备份先验证并预览，覆盖需二次确认，操作中禁用重复点击与返回。
- 关于页以新图标、应用名、版本徽标和收藏定位为主，隐私与 VNDB 来源分区，末尾标注 MPL-2.0；使用可滚动布局适配大字体。
- 图片缺失、加载中或加载失败均有占位图案，不显示空白封面。封面加载状态提供无障碍语义。

颜色来自 MaterialTheme，深色模式没有硬编码白色卡片。正文使用系统字体与中文/日文回退，不下载额外字体。

## 首页、图标与外观设置（2026-10-04）

- 移除首页标题下和“最近入藏”下的说明文字，添加入口放标题右侧，查看全部与最近入藏合为一行，不再重复占两行操作。
- 首页卡片使用 `compact` 展示，只隐藏首页的版本副标题和日期；完整书架卡片、列表、详情保留版本信息。多盒区分改为封面角标，品相放在封面外，避免遮挡画面；金额开关及 null/0 语义不变。
- 减轻卡片描边并去掉叠加阴影，统一页面横向留白；书架排序与图标视图切换同排，搜索仍保留输入、键盘搜索和清空操作。二行标题采用更均衡的中日文换行。
- 新增 `ic_ui_*.xml` 原生线性图标，统一 24 坐标、1.8 描边与圆角。导航使用首页、书架、批次、偏好语义，设置入口分别使用偏好、店铺、备份、信息图标；不再用爱心代表实体书架或让多个设置入口共用齿轮。品牌 SVG、启动图标及生成资源没有改设计。
- 偏好设置新增外观区：跟随系统 / 浅色 / 深色三段选择，独立动态取色开关。三段按钮在大字体下等高并保留完整标签；动态取色在 Android 12 以下明确禁用，渲染安全回退固定配色。
- 默认跟随系统、关闭动态取色，保持原有品牌配色。设置使用既有 DataStore；页面和系统栏图标立即响应，导航位置不因主题切换重建。首次设置读取结束前不猜主题，避免先闪一帧错误配色。
- Room schema 不变；JSON v2 只添加可选外观字段，旧 v1/v2 备份缺字段时不重置本机外观。
- 新回归：`UiPolishUiTest`（首页副标题隐藏 / 详情副标题保留 / 多盒点击 / 导航 / 搜索 / 价格开关）；`AppearanceUiTest`（实际深浅与动态配色、系统模式、大字体等高、旧系统回退）；`AppearancePreferencesTest`（默认值、独立持久化、实时订阅、旧设置兼容）；`UiIconTest`（矢量一致性和图标联系表）；`BackupTest` 补充外观往返和旧备份保留本机主题。
- 截图：`home-polished-light.png`、`home-polished-large-dark.png`、`shelf-polished-light.png`、`appearance-fixed-dark.png`、`appearance-dynamic-light.png`、`appearance-large-dark.png`、`ui-icons.png`，位于项目内 `toolchain/review/ui/screenshots/`，不提交。
- 修改前 UI、资源、测试与文档备份：`toolchain/backups/20261004-211826-before-home-ui-polish.tar.gz`；回滚前解到独立目录、逐文件比对，不覆盖后续用户工作。未修改购买事实或费用分摊算法。
- 验证：`scripts/check.sh --offline :app:assembleRelease`（显式使用不存在的本地签名配置）通过；177 项 JVM/Room/Compose 测试、18 项 Python 回归通过，Debug 与 unsigned Release 构建成功，Lint 0 错误、5 个既有告警。SVG 一致性与 `git diff --check` 通过，Gradle 已停止且确认没有当前用户的残留 Java 进程。
- 使用 Robolectric Native Graphics 逐张检查浅/深主题、大字体、导航和图标截图；修正了大字体三段按钮高度不齐与中日文孤字换行。不把本机渲染测试当作真机壁纸取色、系统栏或手势体验验收，未覆盖已发布的 v1.0.1 标签/安装包。

## 填写页与设置分组（2026-10-04，续）

- 新增收藏、编辑收藏、费用录入与偏好设置统一使用 `FormSection`，轻描边、20dp 圆角、16dp 内边距、同源功能图标标题；保留字段与校验，不改数据库/备份格式。
- 金额和币种使用 `MoneyInputField`：宽度不足 300dp 或字体倍率大于 1.2 时上下排列；空商品价格仍是未记录，0 仍是真实零价，费用金额仍必填。
- 新增/编辑品相及费用类型、分摊方式使用换行选项，窄屏不把后续选项藏在横向滚动中；新增数量控件也允许自动换行。
- 手动版本仍显示版本名编辑与重新绑定按钮；购买日期、店铺和批次选择保留。日期/批次控件统一 56dp 最小触控高度、圆角与提示图标，长批次名称可换行。
- 新增、编辑、费用共用 `FormSaveBar`；费用保存从滚动区移到固定底部，长按钮随字体自然增高。新增/编辑容器沿用 IME inset 处理，保存前收起焦点；仍需设备确认键盘实际弹出体验。
- 手动分摊输入改为作品信息下的整行金额，预览金额不与长标题争抢宽度；保留池化算法、未知价格比例保护、未分摊提醒及 legacy 费用显示。
- 设置首页改为收藏偏好、数据管理、应用信息三组圆角卡片，去掉额外营销说明；偏好页外观、价格展示、默认货币分区，备份说明及二次确认保留。店铺列表把编辑/删除移到名称下方，长名称不挤占按钮。
- 新增 `FormsSettingsUiTest`，覆盖 360dp 窄屏、1.5/1.8 倍字体、金额/币种排布、编辑 null/0、手动版本绑定入口、品相自定义、五类费用与手动分摊、完整偏好页、设置四入口和长保存按钮。
- 截图位于 `toolchain/review/ui/screenshots/`：`copy-edit-grouped.png`、`copy-edit-large-dark.png`、`expense-form-large.png`、`settings-grouped-home.png`、`settings-preferences-large-dark.png`、`money-field-large.png`、`form-save-large.png`；原购入和备份截图也重新生成。
- 修改前备份：`toolchain/backups/20261004-222401-before-forms-settings.tar.gz`，恢复仍须解到独立目录逐文件比对。未改已发布 v1.0.1 的标签或安装包。
- 全量验证：184 项 JVM/Room/Compose 测试、18 项 Python 回归、Debug 与 unsigned Release 构建通过；Lint 0 错误、5 个既有告警，branding 和 diff 检查通过。已停止 Gradle 并确认当前用户没有残留 Java 进程。

## UI 复查与渠道删除后的订单崩溃（2026-10-04）

- 按“添加渠道 → 订单和收藏使用渠道 → 添加费用 → 删除渠道 → 打开订单”复现；Room 与 DataStore 中的渠道快照仍保留，但界面报 `IllegalArgumentException: Key "1" was already used`。
- 原因：同一订单详情 LazyColumn 中，`owned_copy` 和 `expense` 各自自增的 ID 被直接用作 key。现在使用 `copy:<id>` 和 `expense:<id>`，避免两类记录的 ID 相同导致崩溃。未修改数据、迁移规则或店铺快照。
- 新建批次弹窗在 360dp 小屏、1.5 倍字体下出现测量无法稳定的问题；改为有明确宽高边界的 Dialog，表单滚动，创建/取消固定在底部。候选渠道消失后已填写名称继续保留，保存中禁止重复创建/关闭。
- 备份恢复预览含价格、外观和渠道设置时内容会超屏；预览与覆盖确认的正文均允许滚动，底部操作可见，二次确认逻辑不变。
- 编辑固定费用切换类型时原稳定 ID 曾被当作旧自定义名称直接展示；现在只展示原费用真正的自定义名称，固定类型保持资源文案。
- `UiReviewRegressionTest` 覆盖上述复现路径、同 ID 商品/费用分别可点、旧费用名称、新建批次渠道候选变化及大字体弹窗。为直接测试完整展示组件，仅提取 `OrderDetailContent` 和 `OrderCreateDialog`，仓库和 ViewModel 架构保持不变。
- 修改前备份：`toolchain/backups/20261004-233609-before-ui-review.tar.gz`；回滚仍逐文件比对。
- 复查结果：189 项 JVM/Room/Compose 测试、18 项 Python 回归、Debug/unsigned Release 构建全部通过；Lint 0 错误，仍为 5 个既有告警。停止 Gradle 后确认当前用户 Java 进程为 0，未推送或重新发布安装包。
- 实际渲染截图：`review-order-after-channel-deleted.png`、`review-order-dialog-large.png`、`review-backup-dialog-large.png`，位于 `toolchain/review/ui/screenshots/`。这是本机复现和自动回归，不替代用户设备上的崩溃日志、键盘和返回手势验收。

## 控件排版与触控反馈（2026-10-05，v1.0.2）

- 默认货币、品相、费用类型和分摊方式统一使用 `OptionGrid`：等宽列、同一行等高、48dp 最小触控高度、8dp 行列间距；根据可用宽度和字体倍率减少列数，末行不拉伸。保留选中语义与比例分摊禁用规则。
- 金额横排时币种改用同结构的带标签只读输入框，消除外置标题导致的边框错位；窄屏和大字体继续上下排列。金额提示和 null/0 规则不变。
- 动态取色和价格开关共用整行开关组件，圆角范围内提供持续按压反馈及内部留白，整行只执行一次切换；关闭或不支持时不会触发操作。
- 数量标签与加减控件正常字号垂直居中，窄屏、大字体上下排列；编辑页日期和批次标签与控件按新增页的方式成组。
- 标签和值正常宽度按首行基线对齐，大字体、窄屏或实际测量超出标签列的长标签改为上下排列。订单汇总复用该组件；多币种金额分行显示，订单收藏和费用金额移至独立整行，不再挤占标题。
- 版本平台及费用标签允许换行，分区标题与操作在窄屏、大字体时分行。手动版本入口使用可点击 Surface，反馈沿圆角裁切。
- 加载／错误／空状态不再固定为 200dp；添加页允许内容自然增高，绑定面板的长错误／空状态可滚动。店铺编辑弹窗固定标题和操作区，只滚动中间表单。
- 新增 `LayoutAlignmentUiTest`，覆盖金额对齐、货币等宽末行、长选项与禁用项、持续按压圆角与单次切换、大字体标签排布、不同字号基线与长标签、多币种长金额与删除入口、店铺弹窗固定操作。
- 本机渲染截图在 `toolchain/review/ui/screenshots/layout-*.png`，不提交；测试不能替代真机 IME、系统返回与厂商字体验收。
- 本地默认版本升级至 1.0.2 / 1000002；不改数据库 schema、费用分摊、备份格式或已有购买事实。不创建 tag、不推送或发布 GitHub Release。
- 修改前备份：`toolchain/backups/20261005-151448-before-layout-fixes.tar.gz`（UI 源码、资源、测试与本文）；恢复前解到独立目录逐文件比对，不覆盖后续修改。
- 最终验证：197 项 JVM／Room／Compose 测试、18 项 Python 回归通过；Lint 0 错误、5 个既有告警，branding 和 diff 检查通过。最终 Release 经 R8／资源压缩并沿用本机签名，v2/v3 校验通过；交付文件 `app/build/outputs/apk/release/VNventory-v1.0.2.apk`。
- 本机旧 JDK 21 路径已失效，本轮仅通过命令行 `-Dorg.gradle.java.home=/usr/lib/jvm/java-25-openjdk` 使用现有 JDK；不改机器路径配置。检查脚本的退出清理因旧路径报错后已显式使用有效路径执行 `--stop`，最终完整 Gradle 验证也通过。

## 批次编辑与启动图标留白（2026-10-05，v1.0.2 续）

- 购买批次详情页右上角增加“编辑批次”铅笔入口；复用新建批次表单，预填名称、店铺/渠道、日期、默认币种及备注。取消不写入，重新打开读取当前记录；候选渠道不存在时仍保留历史名称。
- 保存中禁用表单、日期、币种、取消和返回，拒绝重复保存。失败保留草稿及可关闭错误，不创建新订单；更新在事务中检查订单仍存在，并保留创建时间。默认币种变更不转换或修改已有收藏、费用或手动分摊。
- 通过字段级原子更新修改编辑草稿，避免多个字段快速修改时使用旧界面快照覆盖其他输入；Room 订阅刷新详情页标题、元数据和批次列表。
- 启动图标彩色前景和单色前景的中心等比缩放由 0.84 调整为 0.68，图案尺寸减少约 19%，背景满铺不变。只改自适应启动图标留白，原 SVG、应用内标记和关于页图案保持不变。
- `OrderEditingUiTest` 覆盖实际详情页入口、预填、取消/重新打开、全部字段保存、创建时间与收藏/费用/成本保持、空名称/已删除批次保护、失败保留草稿、小屏大字体与保存期间禁用；品牌测试校验两个前景等比缩放、最终占比与单色孔洞。
- 截图：`order-edit-large-dark.png`、更新后的 `brand-launcher.png` 和 `brand-monochrome.png`，位于本机 `toolchain/review/ui/screenshots/`。
- 修改前备份：`toolchain/backups/20261005-154050-before-order-edit-icon-inset.tar.gz`；恢复需解到独立目录逐文件比对，不覆盖此前 UI 修复或后续修改。
- 最终验证：202 项 JVM／Room／Compose 测试、19 项 Python 回归通过；Lint 0 错误、5 个既有告警，v1.0.2 已签名 Release 重新构建并通过 v2/v3 校验。仍交付 `app/build/outputs/apk/release/VNventory-v1.0.2.apk`，不改版本号、不发布；已停止 Gradle daemon。
- 随后修正本机 `toolchain/gradle-home/gradle.properties` 的 JDK 路径至 `~/Software/lib/jdk-21.0.12.1+1`，配置已按时间戳备份且继续不入库；仓库内引导脚本增加 `Software/lib` 下 JDK 21／17 的自动发现，保留显式环境变量及已有配置优先规则，并补充含空格路径的模拟回归。后续构建恢复使用 JDK 21，无需命令行路径覆盖。
- 根据真机截图修复购买批次下拉菜单按内容收窄的问题：`OrderSelector` 改用与渠道同源的 `ExposedDropdownMenuBox`／只读输入框／等宽菜单，统一箭头及焦点样式；新增和编辑收藏均受益。保留“不加入订单”、选中 ID 与长名称换行；补充两种菜单等宽、选择/清空、长名称无溢出回归和 `layout-order-dropdown*.png` 截图。
- JDK 21 下最终执行 `scripts/check.sh --offline :app:assembleRelease` 成功（退出清理也成功）：204 项 JVM／Room／Compose 测试、20 项 Python 回归通过，Lint 0 错误、5 个既有告警；Debug／已签名 Release 构建及 v2/v3 验签通过，Gradle 已停止。

## 品牌图标

- 原稿：`assets/branding/vnventory.svg`，保留完整收集 V 图稿、渐变、椭圆阴影、裁切和无障碍描述；旧稿归档为 `vnventory-previous.svg`。
- `scripts/convert-branding.py` 用 Python 标准库把图稿转换为 Android 原生矢量：椭圆径向渐变变成仿射单位圆，线性渐变保留原始坐标，裁切仍是 vector clip-path；不栅格化，也不引入 SVG 运行时依赖。
- `ic_launcher_foreground.xml` 保留软倒角、双层实体盒套、内嵌盒脊标签、象牙/薰衣草色 V、香槟金收藏牌与投影；`ic_launcher_foreground_safe.xml` 将完整图稿缩进 Android 自适应图标安全区。
- 启动背景与关于页图标分别使用同源圆角 tile 和满版底色；普通及 round 启动图标都使用安全区前景和镂空单色主题图标。
- 关于页 `AppLogo`、启动图标及占位/加载/空状态 `BrandMark` 均使用新稿的对应原生矢量；主彩色标志保持原色，单色标志只将游戏盒与收藏牌作为轮廓，标签和金牌孔洞透明。

## 动效约定

集中配置位于 `ui/theme/Motion.kt`：

| 场景 | 动效 |
| --- | --- |
| 卡片按压 | 160ms 缩小至 97.5%，绘制层变换，不在每帧重组整张卡片 |
| 顶级页面切换 | 先淡出再淡入，避免大幅横向滑动 |
| 详情与表单进入 | 保留 240–300ms 淡入 + 短距离滑动，支持 RTL |
| 普通返回 | 独立 180ms 转场，快速起步、轻微位移，无缩放或延迟；页面及添加步骤共用 |
| 预测性返回 | 淡入、淡出、位移同长且线性跟手，去掉缩放，方向取真实手势边缘；步骤松手只补完剩余进度 |
| 返回收尾 | 取消回弹 140ms；底栏显隐 120ms，避免页面已返回后底栏仍缓慢展开 |
| 收藏插入/删除/排序 | Lazy 列表稳定本地 ID + animateItem，网格/列表淡入切换 |
| 添加步骤 | SeekableTransitionState 跟随返回手势；只按步骤键转场，输入价格或备注不会触发全屏动画 |
| 展开与简介 | 容器高度渐变；数量、进度和保存中状态有轻量反馈 |
| 成功保存 | 导航后显示确认 Snackbar；保存中禁用按钮，避免重复操作 |

使用 Compose 内置动画，遵循系统动画时长设置；不添加持续闪动、背景漂浮或夸张弹跳。
**真实金额不做数字滚动/插值**，始终直接显示准确的最终值。
Coil 的请求使用 remember，图片以 240ms 交叉淡入；不对每个列表项使用额外的 SubcomposeAsyncImage。

添加步骤的整个页面（含标题、进度与保存栏）参与同一转场，并保留退出页的最后完整数据与各步滚动状态。
松手完成动画后才提交返回；取消则倒放同一个转场的进度，不启动反方向的新转场，避免先向外跳再回弹。
步骤手势的松手完成时长为 `180ms × (1 - progress)`；例如 80% 进度时只补完 36ms，接近完成不会再播放整段转场。
手势阶段继续使用线性插值，松手及普通返回使用快速起步的减速曲线；减少动态效果时仍即时完成。
普通步骤切换显式设置 SeekableTransitionState 的时间轴（进入 300ms、返回 180ms），避免它沿用旧的进入时长；关闭动画时直接 snap，不等待该时间轴。

日期选择器参考 DateNote-Weii 的 `1a5d6f4`：显式初始化显示月份，日历/文本输入模式用 `key(displayMode)`
仅重建 DatePicker 界面，选择状态留在 key 外，绕过 Material 3 模式切换时缓慢的 AnimatedContent 转场。
添加、编辑和新建批次统一使用此组件；日期继续按 UTC 转换，避免时区引起前后差一天。

## 购入表单层级

- **所选版本**：封面、日语作品名、版本名和 ID；手动版本名称也放在这里。
- **价格与数量**：突出单盒价格、币种和盒数；商品小计明确“不含批次费用”，检查加法防止溢出。
- **品相**：选项自动换行，大字体下不把选项藏在横向滚动区；自定义说明只在需要时显示。
- **购买记录**：明确标注选填，集中放日期、店铺/渠道、所属购买批次及费用记录说明。
- **备注**：独立的选填区域，不再与主要金额输入混排。

保存栏固定在底部；加入批次的提示读取实际选中的批次，而非仅按进入页面时的上下文判断。

## 界面验证

- `app/src/debug/java/.../ui/preview/ShelfPreviews.kt`：首页浅/深主题、收藏页大字体与详情的 Android Studio Preview。
- `ShelfUiTest`：使用 Compose 测试规则和 Robolectric Native Graphics 真实渲染，覆盖封面点击、空状态 CTA、
  列表切换、排序、大字体、独立收藏成本、保存中反馈与步骤动画中间帧。
- `ReducedMotionUiTest`：动画时长比例设为 0 时步骤进度立即完成，封面直接显示无淡入的最终海报帧。
- `PurchaseFlowUiTest`：购入分区、商品小计、数量/店铺输入、大字体品相布局与截图；系统返回事件验证
  半程不提交、左右边缘方向、取消后的回弹中间帧、再次返回及 NavHost 取消/完成时的出栈语义。
  另验证普通页面/步骤在 180ms 内完成返回、高进度松手按剩余时长完成且只提交一次。
- `BackMotionTest`：返回、回弹和底栏时长层级，快速起步曲线，手势剩余时长与边界。
- `DebugPackagingTest`：`.debug` 独立包名不改变源码 namespace；预览海报 URI 跟随当前应用 ID，不会因包名分离而丢失。
- `SettingsAndDateUiTest`：设置分组入口、大字体深色备份页、覆盖二次确认、错误提示与日期模式切换；截图 `settings-home.png`、`settings-backup-large-dark.png`。
- 关于页截图：`settings-about-light.png`、`settings-about-large-dark.png`、`settings-about-large-dark-footer.png`；验证图标、版本、来源及许可在大字体下均可达，并验证返回。
- `BrandingTest`：原图颜色、象牙 V / 薰衣草盒套 / 香槟金收藏牌、启动安全区、普通/round 启动图标一致性、满铺背景和标签镂空；截图 `brand-logo.png`、`brand-launcher.png`、`brand-monochrome.png`。
- 截图输出目录：`toolchain/review/ui/screenshots/`（不提交，运行测试生成）。
- 界面预览中的记录是纯展示样本；三张海报是原创矢量资源，仅位于 `app/src/debug/res/`，不进入 Release APK；
  不下载 VNDB 图片，也不写入 Room。

```bash
scripts/check.sh                       # 构建、全部测试、Lint，结束后停 daemon
./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.vnventory.app.ui.ShelfUiTest'
./gradlew --stop
```

本地渲染与动画时钟测试不能替代真机上的手势、IME、滚动帧率和返回手势验证。没有为本轮工作安装模拟器或全局工具。

## 本轮修改前备份

`toolchain/backups/20261003-164610-before-purchase-ui-fixes.tar.gz` 保留了修改前的 `app/src/`、本文与 `README.md` 及权限。
如需回滚，先备份当前工作区，再将归档解到单独目录，逐文件比对、恢复本轮涉及的文件；本轮新增文件单独处理，
不要整仓重置或直接覆盖其他未提交修改。此次未修改数据库 Schema 或购买事实。

设置与备份这一轮的修改前文件及权限单独保存在 `toolchain/backups/20261003-211756-before-settings-backup.tar.gz`。
回滚时同样先解到独立目录、逐文件比对；本轮新增备份实现和测试单独处理，不恢复或覆盖其他用户改动。
配置严格读取接口的修改前文件另存于 `toolchain/backups/20261003-213328-SettingsRepository.kt`。

关于页和旧图标替换前的文件及权限备份：`toolchain/backups/20261004-000032-before-branding-about.tar.gz`。
当前收集 V 的 SVG 与原生矢量转换器为 `assets/branding/vnventory.svg`、`scripts/convert-branding.py`；运行 `--check` 可验证输出，`--write` 可从原稿重建。需要回滚时先解档到单独目录并逐文件比对；保留旧图稿 `assets/branding/vnventory-previous.svg`，不要整仓重置。

返回动画和 Debug 包名分离前的相关文件及权限备份：`toolchain/backups/20261004-002918-before-back-debug-fixes.tar.gz`。
回滚时同样先解到独立目录并逐文件比对；新增测试单独处理，保留其他用户改动。源码回滚不会合并两个包名下的真实应用数据。
