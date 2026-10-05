# 字符串资源与店铺/渠道

## 国际化入口

- 默认中文 UI 文案位于 `app/src/main/res/values/strings.xml`；校验、错误、枚举和成本消息位于同目录的 `messages.xml`。
- Compose 使用 `stringResource`，数量相关文案使用 `pluralStringResource`；带参数的资源使用 `%1$s` / `%2$d` 等位置参数，不拼接翻译片段。
- 后续增加语言时在 `values-<locale>/` 下提供同名资源。应用名、品牌缩写及纯图形标记等不可翻译项已标记 `translatable="false"`。
- `domain/text/Message.kt` 只保存消息键与参数，不引用 Android 或 R；`ui/text/Messages.kt` 在显示时解析资源。ViewModel、校验异常、枚举标签和费用引擎都不提前固化语言。
- 用户录入的店铺、版本、备注、费用名称以及 VNDB 元数据属于内容，不参与翻译；数据库和备份也不保存翻译后的系统提示。
- 金额仍是 Long 最小货币单位，币种仍是 ISO 代码，不引入汇率或浮点金额。本轮不添加实际外语翻译或应用语言选择器。

`scripts/check.sh` 包含字符串回归检查，防止在生产 Kotlin 中重新硬编码文案；测试和 Debug 预览的展示样本不属于应用固定文案。

`scripts/check-ui-strings.py` 保守检查 `Text`（含命名参数）、标签、无障碍说明、Snackbar、Toast 及中文/日文字符串。注释、testTag、SQL、稳定 ID 不作为翻译文案；必要例外在 `ALLOWLIST` 中按文件路径和完整字面量列出原因。检查器自身有误报/漏报边界测试，资源检查还验证生产、测试和预览中的所有 strings/plurals 引用。

本轮 audit 未发现仍硬编码的中文固定 UI 文案；补齐了版本选择/绑定页面的紧凑斜线分隔符资源，保持原有视觉间距。测试中的断言、手势模拟页面以及用户/VNDB 示例内容不提取为翻译资源。

## VNDB 标题显示

- 设置 → 偏好设置 → 书架显示 → 标题显示，默认 **原标题**；与「显示版本名」独立。
- VN 原题沿用日语 main → 日语 official → main → alttitle 的规则；罗马音使用 main 的 `titles.latin`，缺失则使用顶层 `title`。ASCII 原题、与罗马音同名的原题均保留。
- Release 原题使用 `alttitle`，罗马音使用 `title`；空白转为 null，不自行转写或翻译。
- Domain 明确保存 `originalTitle` / `romanizedTitle`；旧缓存列映射为 `legacyTitle` / `legacyAltTitle`，不推断类型。
- Room v4 在两个 cache 中增加双标题，在 OwnedCopy 中增加 `vnOriginalTitle` / `vnRomanizedTitle` / `releaseOriginalTitle` / `releaseRomanizedTitle`。3→4 只加 nullable 列，保留旧标题、购买事实和自增序列；1→2→3→4 的正式迁移链继续可用。
- 新增收藏写入两套快照，不依赖当前显示偏好。手动版本保留用户输入；绑定 VNDB Release 时只更新所绑定收藏的版本快照。
- 全局 DataStore Flow 由应用级 ViewModel 订阅，`LocalTitleDisplayMode` 提供给全部 Compose 页面和弹窗；`ui/text/Titles.kt` 调用领域层统一 fallback，切换只重组 UI、不联网、不 UPDATE 收藏。
- 两种模式都回退另一种标题 → legacy → VNDB ID（最终安全非空占位）。收藏显示以 snapshot 为准，不依赖 cache；旧收藏只有一个标题时，两种模式显示相同 legacy 值，不猜测补齐。
- 书架搜索同时匹配四个双标题快照字段、旧作品/版本名、手动版本名、店铺和 VNDB ID；离线 VN 搜索同时匹配 cache 双标题与旧字段。标题排序跟随当前显示模式；其它排序维持原规则。
- 备份仍为 v2：按已有可选字段扩展策略增加 `settings.titleDisplayMode` 和四个快照字段，无需破坏性格式升级。旧 v1/v2 缺少模式时为 ORIGINAL，缺少双标题时为 null；恢复无网络请求，旧标题完整保留。

## 常用店铺/渠道

设置 → **店铺 / 渠道**：支持新增、改名和删除，名称去除首尾空白，空名和重复名会被拒绝。
列表保存在 DataStore，按录入顺序显示，写入是原子的，并发录入不会丢失其他候选项。

新增收藏、编辑收藏、新建购买批次共用可编辑的下拉选择器：

- 可直接选择常用候选项，也可临时输入不在列表内的名称或选择不填写。
- 候选项更新会进入当前表单，但不覆盖已经输入或回填的名称。
- 收藏与订单仍保存名称快照。候选项改名、删除不会改写历史记录，也无需升级 Room Schema。

## 备份兼容

- 新备份包含可选的 `settings.shopChannels` 列表，可单独决定是否恢复；选择恢复会替换本机候选列表。
- 旧备份没有该字段（或为 null）时保持本机列表；显式空数组表示可恢复为空列表。
- 非法候选项使备份校验失败，不做部分导入。购买事实仍在 Room 事务内恢复；随后默认货币和候选列表在单次 DataStore 写入中恢复。
- 如果仅偏好设置写入失败，界面明确提示购买事实已经恢复，不诱导用户重复追加。

修改前源码及权限备份：`toolchain/backups/20261004-012506-before-strings-shop-channels.tar.gz`。
回滚前先保留当前修改，将归档解到单独目录并逐文件比对；不要整仓重置，也不要覆盖真实收藏数据或签名文件。
