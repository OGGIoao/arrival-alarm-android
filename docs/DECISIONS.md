# Technical Decisions

本文件是重要技术决策的追加式记录。修改架构、依赖、权限、用户数据、网络服务、后台执行、平台版本、构建或签名策略前，先新增或修订一条决策记录。已被替代的决策保留历史并标为 `Superseded`。

## ADR-0001：保持无 Gradle 和无第三方 SDK 的原生 Android 构建

- Status: Superseded by ADR-0003
- Recorded: 2026-08-26
- Original decision date: Unknown

### Context

当前应用最低支持 Android 8.0，并由 PowerShell 脚本直接调用 Android Platform 36、Build Tools 36.0.0 和 JDK。仓库不依赖 Gradle、AndroidX、Google Play Services 或第三方 SDK。

### Decision

在有独立决策证明迁移收益前，继续以 `android-app/build-apk.ps1` 作为唯一受支持的构建入口，并优先使用 Android 平台 API 和纯 Java 核心逻辑。

### Consequences

- 构建链较小、依赖和供应链面较窄。
- UI、测试、依赖管理和现代 Android 工具支持需要更多手工维护。
- 引入 Gradle、AndroidX 或第三方 SDK 属于架构变更，必须先评估维护成本、体积、许可证、隐私、供应链、迁移和回滚方案。

## ADR-0003：采用 Gradle/AGP 作为 Android 唯一构建系统

- Status: Accepted
- Recorded: 2026-08-26
- Supersedes: ADR-0001

### Context

手工 PowerShell 构建只能产出调试签名 APK，不能提供 Google Play 新应用需要的 AAB、标准 Release 变体、R8、资源压缩、Android Lint 或受控上传签名。继续扩展手工链会重复维护 AGP 已提供的发布能力，并增加签名、版本和工具漂移风险。

### Decision

`android-app` 采用固定版本的 Gradle Wrapper 和 Android Gradle Plugin，作为 Debug、测试、Lint、APK 与 AAB 的唯一构建系统。首轮迁移通过 Gradle `sourceSets` 接入现有源码目录，避免同时移动业务代码；`build-apk.ps1` 只保留为调用 Wrapper 的兼容入口。

正式 Application ID 为 `com.navobell.arrivalalarm`；现有 Java 命名空间暂时保持 `com.example.arrivalalarm`，避免在发布工程任务中混入机械改名。Release 使用 R8 和资源压缩。上传密钥只通过本机或 CI 的安全环境变量提供，不写入仓库；首次上传后由 Play App Signing 管理面向用户的应用签名密钥。

### Alternatives

- 继续扩展手工 SDK 脚本：供应链较小，但需要自行实现和长期维护 AAB、Lint、优化、变体与签名集成，因此拒绝。
- 同时迁移到标准 `app/src/main` 目录并重命名全部 Java 包：最终结构更常见，但会扩大当前 P0 的 Diff 和回归面，留待独立任务。

### Consequences

- 构建会下载固定版本的 AGP、Gradle、JUnit 和 bundletool，供应链面增加；通过固定版本、Wrapper 发行包校验、依赖锁和依赖校验元数据控制。
- Windows 工作区位于含中文字符的用户目录，因此显式启用 AGP 的 `android.overridePathCheck`，并由本地构建入口把 Gradle 缓存和构建产物放到系统临时目录中的项目专用 ASCII 路径；源码不复制，CI 使用其原生 ASCII 工作目录和标准 `build/`。
- 未提供上传密钥时仍可生成并验证未签名 Release AAB；`verifyPlayBundle` 会明确失败，避免误把未签名包当作可上传产物。
- `com.navobell.arrivalalarm.debug` 与原调试包是不同应用，已有原型安装不会原地升级，测试设备需要并存或手动迁移验证。

### Verification

- `gradlew clean testDebugUnitTest lintRelease bundleRelease validateReleaseBundle` 成功。
- 使用临时生成、不会用于发布的非 Debug 测试密钥配置四个 `ARRIVAL_UPLOAD_*` 环境变量后，`gradlew verifyPlayBundle` 成功，并确认签名证书不是 Android Debug；正式上传密钥仍需在首次上传前单独创建、备份并放入 CI Secret。
- 待正式上传密钥和 Play 内部测试包就绪后，使用 bundletool/Play 生成测试 APK，并在最低和当前 Android 版本真机完成提醒、前台服务、Receiver 和通知冒烟测试；当前尚未完成。

## ADR-0002：用户数据本地优先且不建设业务后端

- Status: Accepted
- Recorded: 2026-08-26
- Original decision date: Unknown

### Context

当前产品没有账号、自建业务后端、广告或分析 SDK。任务、最近地点、固定日程和设置保存在应用私有 `SharedPreferences`。地点关键词和可选附近坐标会直接发送给地点服务。

### Decision

提醒和设置数据继续保存在本机。应用只为明确的地点查询调用外部服务，并在产品和隐私文档中披露数据边界。

### Consequences

- 减少账号、同步、后端安全和用户画像风险。
- 换机同步、多设备共享和服务端恢复不在当前能力内。
- 新增后端、遥测、云同步或新的数据接收方前，必须进行产品、隐私、安全和迁移评审。

## 新决策模板

```markdown
## ADR-NNNN：决策标题

- Status: Proposed | Accepted | Superseded | Rejected
- Recorded: YYYY-MM-DD
- Supersedes: ADR-NNNN（如适用）

### Context

需要解决的问题、约束和当前证据。

### Decision

选择什么，以及明确不选择什么。

### Alternatives

考虑过的更简单或不同方案及未选择原因。

### Consequences

收益、成本、风险、迁移、兼容和回滚边界。

### Verification

如何证明决策按预期工作。
```
