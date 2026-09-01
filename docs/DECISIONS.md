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

## ADR-0004：首个 Play 版本采用用户主动启动的最小权限提醒

- Status: Accepted
- Recorded: 2026-08-27

### Context

原型同时声明后台位置、使用情况访问、通知读取、精确闹钟和全屏通知，并提供长期出行检测与固定日程自动启动。产品评审确认：自动识别出行当前可靠性不足，固定日程属于后续优化；首发不可替代的用户价值是用户主动选择目的地、启动持续守候并在到站时收到响铃和通知。

这些增强功能不足以证明后台位置和其他特殊访问对首发核心功能不可替代。继续保留会扩大隐私、平台兼容、耗电和 Google Play 审核风险。

### Decision

首个 Play 版本只支持用户在可见 Activity 中主动启动到站提醒。启动后由 location 类型前台服务持续定位，任务停止或触发后停止位置更新；到站使用声音、振动和高优先级可点击通知，不使用全屏通知。

首发 Manifest 不声明后台位置、使用情况访问、精确闹钟、开机完成或全屏通知，不注册通知读取服务、固定日程 Receiver 或开机 Receiver。长期车站识别、其他 App 信号识别和固定日程自动启动保留为后续独立产品与架构评审事项，不在首发 UI 或运行调用链中启用。

升级安装时保留已有固定日程数据，但取消其系统调度；旧版出行检测设置被禁用并清除其运行状态，避免升级后恢复已撤回的后台行为。

### Alternatives

- 为全部原型能力准备后台位置和特殊权限材料：功能并非首发核心且可靠性证据不足，因此拒绝。
- 继续全天运行 Location FGS 识别车站：耗电和平台政策风险高，并且 Google Play 已公布后续不再接受以 FGS 承担 geofencing 的方向，因此拒绝作为首发架构。
- 立即引入 Geofence API 和全国车站数据：需要新依赖、数据源、无 GMS 兼容和独立可靠性验证，超出本次 P0 范围。

### Consequences

- 首发权限面和审核材料显著缩小，运行行为与用户主动操作一致。
- 用户必须主动设置并启动每次到站提醒；首发不会自动发现车站或按日程启动。
- 既有固定日程记录暂留本机但不可编辑或触发，后续重新引入功能时可制定迁移策略。
- 后续恢复任一敏感能力必须新增或修订 ADR，并提供可靠性、耗电、隐私和 Play 审核证据。

### Verification

- 构建门禁拒绝首发 Manifest 中重新出现上述权限和组件。
- 单元测试、Release Lint、Release AAB 与 Bundletool 验证通过。
- 真机验证用户主动启动、切后台持续守候、到站响铃通知、停止任务和权限拒绝路径。

## ADR-0005：监控中断后必须由用户手动恢复

- Status: Accepted
- Recorded: 2026-08-30
- Supersedes: None

### Context

Android 设备重启、系统终止进程或厂商省电策略都可能使 `TrackingService` 不再运行。`AppPrefs` 中保留的 `active` 任务只能证明用户曾经启动过提醒，不能证明当前仍有定位服务。`ADR-0004` 又明确禁止首发版本注册开机 Receiver 或通过延期权限实现后台自动恢复。

### Decision

将任务数据状态与服务运行态分开判断。应用打开时，若存在 `active` 任务但当前进程没有运行中的 `TrackingService`，立即将任务转为 `interrupted`，首页显示“需要恢复”和一个按钮；用户点击后才重新启动 location 前台服务。恢复路径保留现有定位/通知权限门禁。`TrackingService` 返回 `START_NOT_STICKY`，系统不会因为任务仍保存在本机而自动重建监控。

实现上，运行态由不持久化的 `ReminderRuntime.Snapshot` 管理，并由 `ReminderRuntimeCoordinator` 统一协调 Activity、恢复通知和服务。状态必须经过前台启动、定位监听注册和首个定位三个事实阶段，心跳使用 `SystemClock.elapsedRealtime()`；Provider 使用集合而不是可被乱序回调破坏的计数器。恢复启动请求带运行代次，服务不会让旧实例的延迟销毁或定位回调覆盖新请求；恢复路径复用同一个服务实例的幂等启动动作，避免异步停止再启动的竞态。

### Alternatives

- 注册开机 Receiver 自动启动：违反 ADR-0004 的首发权限和后台策略，拒绝。
- 继续仅用持久化 `active` 或延迟心跳判断：无法在应用刚打开时区分“任务存在”和“服务正在运行”，会继续产生误导，拒绝。

### Consequences

- 重启或进程终止后的恢复动作明确、可见且由用户控制。
- 用户需要在恢复态点击一次按钮；权限被撤销时需要先重新授权。
- 应用未打开时不会收到开机即时恢复通知，这是不注册开机 Receiver 的直接结果；打开应用后会显示恢复状态并补发恢复提示通知。

### Verification

- 纯 Java 状态测试覆盖无服务立即恢复、心跳过期、恢复后监控和到站已触发状态。
- 真机验证：启动提醒、强制停止/重启设备、打开应用首页出现“需要恢复”、点击一次恢复后前台服务和定位通知重新出现。
- 纯 Java 回归测试还覆盖 Provider 注册、重复注册、未知 Provider 禁用、逐个禁用和清空；全量 Java 源码结合 Android API 编译检查通过。
- 2026-09-01 重装固定的 Build Tools `36.0.0` 后，Debug 全链与 Release 全链均通过：55 个核心测试、Debug/Release Lint、R8、权限门禁、APK/AAB 和 Bundletool 均有完成证据。
- 内部状态广播使用按 Application ID 派生的 `signature` 权限；API 33+ 额外使用 `RECEIVER_NOT_EXPORTED`，避免外部伪造状态刷新并允许不同签名的 Debug/Release 并存。
- 当前仍无真机或有效 AVD；真实进程终止、重启、厂商省电、FGS 限制、权限撤销和恢复通知路径仍需设备验证。

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
