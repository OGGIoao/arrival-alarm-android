# Release Readiness Report

- 更新时间：2026-10-06（统一设备验收暂缓；代码修复与设备验收分开记录）
- 当前范围：`P0-1 Release 凭据与内部测试`、`P0-2 敏感权限与上架材料`、`P0-3 监控中断与手动恢复`、`P0-4 服务启动与假激活`、`P0-5 异步结果与生命周期绑定`
- 当前结论：`NOT READY`

`NOT READY` 表示应用尚未完成 Play 内部测试、真机验收和 P0-2 外部材料核验，不表示 P0-1 Release 工程仍然缺失。正式上传密钥和远端签名 CI 已有完成证据，但当前产物仍不得直接进入生产发布。

## 2026-09-07 产品目标与当前任务范围

## 2026-10-06 当前检查点与统一验收安排

项目负责人决定：先推进已确认问题的代码修复及必要的单元测试、build、lint，再集中执行模拟器和真机验收。当前暂停 instrumentation 环境排障，不再重复安装工具。必须依赖设备复现才能确定根因的问题保留为 `BLOCKED`，不得猜测修复或提前关闭。

| 问题 | 已有证据 | 未完成 / 后续验收 | 当前状态 |
|---|---|---|---|
| P0-1 Release 凭据与内部测试 | 正式密钥备份、恢复演练、受保护签名 CI 与构建检查有历史 PASS 证据 | Play 内部测试及设备冒烟；历史 CI artifact 不保证仍在保留期 | PARTIAL |
| P0-2 权限与上架材料 | 权限裁剪、Manifest 门禁、披露草稿及历史构建检查 | Console 主体/分类核对、公开材料确认、网络抓包、设备权限验收 | PARTIAL |
| P0-3 中断与手动恢复 | 状态机、Provider 代次隔离及历史核心测试/build/lint | 进程终止、强制停止、重启、权限撤销、恢复通知、厂商省电 | PARTIAL |
| P0-4 服务启动与假激活 | starting/active/interrupted 状态与失败回滚、定位恢复策略及历史静态验证 | Android 12–16 FGS 限制与启动失败、后台/锁屏、服务恢复交错 | PARTIAL |
| P0-5 异步结果与生命周期 | 请求 token/组件代次检查、纯 Java 测试、APK 构建安装及 API 35 普通 Activity 启动 | 旋转/销毁后迟到回调、旧请求覆盖、Service 竞态；当前自定义 instrumentation 未完成 | PARTIAL；自动设备测试 BLOCKED |

上述历史 PASS 不代表本次检查点已重新完成全部测试，也不代表 P0 已全部关闭。统一验收须覆盖各项修复的交互、最低支持版本与当前版本；厂商后台限制仍需真机。整体结论保持 `NOT READY`。

备份范围为当前工作区源码、测试、资源及文档；SDK、AVD、安装包、构建产物、密钥和本地配置不纳入 Git。恢复步骤及工具链现状见 [实验日志](experiment_log.md)。

### 历史产品范围说明

项目负责人确认自动车站提示属于核心目标，首批面向中国大陆常见国产 Android 手机。此前将自动提示视为非核心增强功能的前提已由 ADR-0006 调整；当前代码和以下历史验证记录仍对应手动启动版本。新核心能力尚未实现或验证，整体仍为 `NOT READY`。

按项目负责人要求，P0-1 Play 内部测试、P0-2 上架证据提交和 P0-3 真机验收暂缓。没有 Play Console 不妨碍源码数据流核对、披露整改、自动测试设计及设备方案评估。自动检测所需权限、SDK、数据源、生命周期与商店兼容性需要重新建立证据，详见 [自动车站提示设计](STATION_PROMPT_DESIGN.md)。以下“当前停止点”保留为历史发布流程，恢复任务时优先以本节和新设计为准。

## 已完成

### Release 工程

- 已建立标准 Gradle/AGP 工程与 Gradle Wrapper，固定 AGP `9.2.1`、Gradle `9.4.1`、compile/target SDK `36` 和 Build Tools `36.0.0`。
- 已建立独立 `debug` / `release` 构建类型；Debug Application ID 带 `.debug` 后缀。
- 正式 Application ID 已确定为 `com.navobell.arrivalalarm`，版本为 `versionCode 8` / `versionName 0.4.3`。
- Release 已启用 R8、资源压缩和优化 ProGuard 默认规则。
- 已生成有效的 Release AAB，并使用 Bundletool `1.18.3` 完成格式验证。
- 已建立上传密钥环境变量接入；四项签名变量必须全部配置，否则签名验证失败。
- 已建立签名门禁：拒绝 Android Debug 证书，并检查 AAB 的签名文件、签名块和 `jarsigner` 结果。
- 高德 Web Key 已改为构建时环境变量注入，不再使用源码占位字符串。

### 质量和可复现性

- 核心 Java 测试套件已通过，并已接入 Gradle/JUnit；2026-09-21 复验为 `84 tests passed`，另有 `LocationRecoveryPolicyTest: PASS`，包含提醒状态机、Provider 生命周期、异步结果代次和地点联想交互门禁。
- `lintDebug` 与 `lintRelease` 已通过，阻断错误为 0；Release 警告经 2026-09-17 重新生成后从 35 条降至 5 条，处置明细见 [Lint 处置记录](LINT_DISPOSITION.md)。
- 已生成 `gradle.lockfile` 和依赖 SHA-256 校验元数据。
- Gradle 发行包配置了官方 SHA-256 校验值。
- 已针对中文 Windows 用户目录设置独立 ASCII Gradle 缓存和构建输出路径。
- 本地已完成一次干净的测试、Lint、R8、资源压缩、AAB 和 Bundletool 验证。

### P0-2 首发权限裁剪

- 当前状态：`PARTIAL`。代码、Manifest、构建和申报草稿已完成；正式隐私主体、Data Safety 核验、抓包和真机证据尚未完成。
- 可重复执行的工作流、证据包标准、完成判据和恢复点见 [`play/p0-2-workflow.md`](play/p0-2-workflow.md)。
- 首发产品范围已收缩为用户主动设置目的地并启动到站守候；长期车站识别、其他 App 信号识别和固定日程自动启动延期。
- Release Manifest 已移除后台位置、使用情况访问、精确闹钟、开机恢复和全屏通知，并取消注册通知读取服务、固定日程 Receiver 和开机 Receiver。
- 到站行为已降级为声音、振动和高优先级可点击通知；应用不再申请全屏通知特殊访问。
- 已增加权限申请前说明和应用内在线隐私政策入口。
- 已增加 `verifyReleasePermissionScope` 构建门禁，最终合并 Manifest 核验通过。
- 已建立 ADR-0004、Data Safety 草稿、Location FGS 申报文案和演示视频脚本。
- 2026-08-27 最终本地验证通过：核心测试、Release Lint、R8、AAB、Bundletool 和权限门禁全部 PASS；Lint 仍有 34 条非阻断结果，但没有本次权限范围相关的 `MissingPermission` 或特殊权限警告。

### P0-3 监控中断与手动恢复

- 当前状态：`PARTIAL`。代码、状态机测试、Debug/Release 构建和静态验证已通过；真机生命周期验收仍为 `BLOCKED`。
- 持久化任务与前台服务运行态已分开建模；无服务、启动超时、心跳过期或 Provider 全部失效会进入“需要恢复”，不会伪装成正在监控。
- `TrackingService` 使用 `START_NOT_STICKY`，不在重启或进程终止后静默恢复；恢复只能由可见 Activity 或用户点击恢复通知触发。
- Provider 以名称集合维护，运行代次隔离旧服务的延迟销毁、心跳和定位回调；恢复复用幂等启动动作，避免异步停止再启动竞态。
- 内部状态广播由按变体 Application ID 派生的 `signature` 权限保护；API 33+ 同时使用 `RECEIVER_NOT_EXPORTED`。
- 2026-09-21 复验：Debug 与 Release 全链均 `PASS`；84 个核心测试、两种 Lint、R8、权限门禁、Debug APK、Release AAB 和 Bundletool 均通过。
- 当前没有连接设备、有效系统镜像或 AVD；强制停止、系统重启、厂商省电、权限撤销、FGS 限制和恢复通知仍需最低与当前 Android 真机验收。

### P0-4 服务启动与假激活

- 当前状态：`PARTIAL`。代码、状态机测试、Debug/Release 构建和静态验证已通过；Android 12–16 FGS 启动限制与真实启动失败路径仍为 `BLOCKED`。
- 任务先持久化为 `starting`，只有 `TrackingService` 成功进入前台并注册至少一个定位 Provider 后才提交 `active`；`startForegroundService()` 同步异常、前台化失败、启动超时和 Provider 初始化失败统一转为 `interrupted` 并显示恢复通知。
- 服务存活期间定位过期先进入 `RECOVERING`，以 10 秒、30 秒退避最多重注册两次；收到新定位后恢复监控，权限/Provider 关闭或重试耗尽才要求用户手动恢复。
- 通知动作和固定入口复用 `ReminderRuntimeCoordinator`，不再用普通 `startService()` 绕过 FGS 启动契约；启动失败不会留下“active 但实际未运行”的任务。

### P0-5 异步结果与生命周期绑定

- 当前状态：`PARTIAL`。代码、纯 Java 代次测试、Debug/Release 构建和静态验证已通过；Activity 旋转/销毁后的真实回调、进程回收和 Service 命令竞态仍需 instrumentation 或真机验收。
- `AsyncResultGate` 为页面任务和服务查询提供统一请求 token；`Future.cancel()` 后仍到达的回调必须再次通过组件代次、生命周期和 token 检查才能提交。
- `MainActivity` 的处理覆盖地点联想、坐标确认和固定地点解析；`TrackingService` 的处理覆盖车站反查。取消、替换请求和组件销毁都会使旧结果失效。
- 自动验证复验与缺口矩阵见 [`docs/experiment_log.md`](experiment_log.md)。此前用户提及的真机工作目前缺少设备、产物和日志绑定信息，暂记为 `UNKNOWN`，不作为 P0-5 PASS 证据。
- 2026-10-05 已建立可用的 API 35 AVD `arrival-api35-clean`；Debug APK 安装、主 Activity 启动、force-stop 后重启冒烟通过。Activity 旋转和迟到回调仍未运行 instrumentation，P0-5 继续为 `PARTIAL`。
- 2026-10-05 instrumentation 重试未形成 PASS：首次因 AVD 中旧 APK 签名不一致而被拒装，卸载后重试又卡在模拟器控制台/启动链；已更换为独立的 `arrival-api35-validation2` AVD，仍未进入 `adb device`。详见 [`docs/experiment_log.md`](experiment_log.md)，P0-5 继续为 `PARTIAL / BLOCKED`。
- 2026-10-05 清理后使用 `ARRIVAL_BUILD_DIR=C:\\temp\\arrival-build-p05-rebuild` 重建，Debug 单元测试、Java 编译和 `assembleDebug` 均 `PASS`；这只证明构建路径问题已绕开，不替代 instrumentation 运行时证据。
- 2026-10-06 已用管理员 ADB 35.0.2 启动 API 35 AVD，APK 安装和 `MainActivity` 的直接 `am start -W` 均 `PASS`；`am instrument` 自定义 Runner 不返回，instrumentation 仍为 `BLOCKED`，P0-5 不关闭。

### 签名流程验证

- 使用明确不会用于发布的临时非 Debug 测试密钥完成了 `verifyPlayBundle`，证明签名配置、证书检查和 AAB 校验链路可运行。
- 临时测试密钥和临时签名 AAB 不得用于正式上传，也不构成正式上传密钥已建立的证据。
- 已检查仓库，没有发现临时测试密码、测试密钥路径或私钥进入源码。

### CI 和凭据准备

- 仓库中已增加普通 Release 检查工作流和 `play-release` 环境签名工作流。
- 密钥治理方案已确定为：个人 KeePassXC 保险库 + GitHub `play-release` Environment Secrets；普通协作者不接触上传密钥。
- KeePassXC `2.7.12` 已从官方 GitHub Release 下载，并核对 SHA-256 与 Windows 数字签名。
- KeePassXC 已以“仅当前用户”方式安装成功；首次全机安装因缺少管理员权限返回 `Error 1925`，改为用户级安装后通过。
- 已建立 OneDrive KeePassXC 主保险库和 U 盘副本，主副本均已人工解锁验证；最新密码库副本 SHA-256 一致。
- 已创建正式 `arrival-upload` 上传密钥：PKCS12、RSA 4096、有效期至 2054-01-14；密钥库和公开证书均有 OneDrive 主副本、KeePassXC 加密附件和 U 盘独立副本。
- 正式上传证书 SHA-256 指纹为 `8D:9A:FF:A6:61:07:35:91:F5:20:B4:59:5C:E6:76:85:B6:28:D8:BC:F5:51:96:3D:98:0C:94:3B:AD:D0:9D:8A`。
- 已从 U 盘恢复密钥库和公开证书到隔离临时目录并比较 SHA-256，结果一致；验证后的临时副本已删除。
- GitHub `play-release` Environment 已建立，四个 `ARRIVAL_UPLOAD_*` Secrets 均存在；审批人仅为 `yueqiu666`，管理员不可绕过，只有 `main` 分支可访问。
- 远端签名 [`Build signed Play bundle #1`](https://github.com/yueqiu666/arrival-alarm-android/actions/runs/33254626252) 在 `main@4aa2af4` 上成功。远端 clean build、测试、Release Lint、R8、资源压缩、Bundletool 和 `jarsigner` 全部 PASS。
- 签名产物 `android-play-bundle-signed` 的 GitHub Artifact ID 为 `9715448302`，大小 190,688 bytes，归档摘要为 `sha256:75ddca52a66e423259c1c7bbab69c70af8dffddac77e14a058ead0e7e01064e5`，保留至 2026-09-12。
- 已临时下载该 CI 产物做独立核验：AAB 证书 SHA-256 与正式上传证书完全一致；核验后的本地临时产物已删除，未上传 Google Play。

## 尚未完成

### P0：关闭 P0-1 前必须完成

- [x] 已确认 U 盘为 `D:`、FAT32、57.27 GB、无卷标，并只在专用目录写入备份。
- [x] 已在 OneDrive 建立个人 KeePassXC 主保险库；主密码不进入仓库、聊天、日志或保险库自身。
- [ ] 人工确认纸质/离线主密码恢复记录已经实际存放在与电脑、U 盘分离的位置；该项无法由自动检查证明。
- [x] 已将 KeePassXC 数据库备份到 U 盘，并人工验证主副本均可解锁。
- [x] 已在仓库外创建正式 `arrival-upload` 上传密钥，记录 Alias、有效期和 SHA-256 证书指纹。
- [x] 已将正式上传密钥作为加密附件放入个人 KeePassXC，并在 U 盘保存独立副本。
- [x] 已执行恢复演练：从 U 盘读取并恢复上传密钥和证书，SHA-256 一致；保险库 U 盘副本也已人工解锁。
- [x] 已在 GitHub `play-release` Environment 中配置四个 `ARRIVAL_UPLOAD_*` Secrets，并设置所有者审批和 `main` 分支限制。
- [x] 已完成独立 Diff Review，并将 P0-1 Release 工程隔离为聚焦 Git checkpoint；地点联想和动画优化仍留在工作区，未混入本次提交。
- [x] P0-1 checkpoint 已推送到 GitHub；未签名 [`Android checks #3`](https://github.com/yueqiu666/arrival-alarm-android/actions/runs/33066043677) 在 commit `3ecd12f` 上成功，远端 clean build、test、lint、R8、资源压缩和 Bundletool 均通过。验证产物摘要为 `sha256:cdf1465e1c56c5b86c2085d760005dfdd91b06882b3162446aae41e67f407f3e`，不得作为 Play 上传包使用。
- [x] 已实际运行远端签名工作流，远端 clean build、test、lint、R8、签名和 Bundletool 全部成功。
- [x] 已保存远端签名 CI 的产物摘要，并独立确认 CI AAB 的证书指纹与正式上传证书一致；未使用此前的临时测试 AAB。

### 发布前仍需完成

- [ ] 创建 Google Play Console 应用并启用 Play App Signing；当前没有创建或上传任何 Play 应用。
- [ ] 上传到内部测试轨道，而不是直接生产发布。
- [ ] 使用 Bundletool/Play 生成的安装包，在最低支持版本和当前 Android 版本真机验证提醒、前台服务、通知、Receiver 和权限拒绝路径。
- [ ] 复核 Play 数据安全表、隐私政策、特殊权限声明、商店素材和内容分级。
- [x] 将正式开发者/发布者主体“曾文骏”及联系邮箱 `yueqiu@hrbeu.edu.cn` 补入隐私政策。
- [ ] 确认隐私政策中的开发者/发布者主体与 Play Console 开发者名称完全一致。
- [ ] 推送更新后的公开隐私政策，确认应用内 GitHub HTTPS 链接无需登录即可访问最新内容。
- [ ] 对 Release 地点查询进行抓包，确认 Photon、高德和系统 `Geocoder` 实际发送的域名、搜索关键词和坐标字段，再完成 Data Safety。
- [ ] 在最低支持版本及当前 Android 版本真机验证权限拒绝、主动启动、锁屏/后台守候、到站响铃通知、停止任务和旧版本升级路径。
- [ ] 在最低支持版本及当前 Android 版本真机验证 P0-3/P0-4/P0-5：启动提醒后强制停止/终止进程、重启设备、打开应用出现“需要恢复”、点击一次恢复后前台服务和定位监听重新建立；在 FGS 启动限制、权限撤销、Provider 关闭、Activity 旋转/退出和迟到查询各路径上确认不会假显示监控或回写旧结果。
- [x] 已重新生成 Release Lint 报告，35 条警告已逐条分类与处置，降至 5 条并记录理由，见 [Lint 处置记录](LINT_DISPOSITION.md)。
- [x] 已删除第三方网站链接入口（9 个 `https` 域名 + `BROWSABLE`）。`autoVerify` 只对自有域名有效，该入口无法实现“点击链接直接启动本应用”，只会在系统“打开方式”里出现，同时扩大导出面。目的地导入统一走系统分享、`PROCESS_TEXT`、`arrivalalarm://set` 深链和粘贴。

## 当前停止点

P0-1 的 Release 工程、正式凭据、独立备份、恢复演练、受保护 GitHub Secrets 和远端签名 CI 已完成，技术状态为 `READY FOR INTERNAL TEST`。

下一步不是重新生成 AAB，也不是直接生产发布，而是：确认离线主密码恢复记录已经单独存放，然后在 Google Play Console 创建应用、启用 Play App Signing，并仅把受控签名流程生成的产物用于内部测试轨道。P0-2 仍需核对 Play Console 名称、公开链接、Data Safety、抓包和真机证据；P0-3/P0-4/P0-5 仍缺最低与当前 Android 真机/ instrumentation 生命周期验收，因此整体 Release Readiness 继续为 `NOT READY`。

## 2026-08-30：Play 发布准备补充

- `build-release.ps1 -SdkRoot C:\temp\codex-android-sdk`：`PASS`。核心单测、Release Lint、R8、AAB、Bundletool 和首发权限门禁均通过。
- `assembleRelease`：`PASS`，生成本地未签名 APK，仅用于工程检查，不得上传。
- APK 静态字段：Application ID `com.navobell.arrivalalarm`、`versionCode 8`、`versionName 0.4.3`、minSdk 26、targetSdk 36；未发现 native libraries。
- 当前 SDK 没有 Android Emulator、系统镜像或 AVD，因此模拟器权限、后台定位、模拟位置和通知冒烟仍为 `BLOCKED`。
- 2026-08-30 已接受 Android SDK 许可并安装 Emulator 本体；API 35 `google_apis;x86_64` 系统镜像在 HTTPS 与 `--no_https` 两种模式下均卡在 `Preparing Install`，目标目录未下载有效镜像，判定为 SDK 仓库网络阻塞。未创建 AVD，未执行模拟器运行时测试。
- 公开 GitHub `main/PRIVACY.md` 可访问，但内容仍是旧版原型权限说明，与当前工作区首发隐私政策不一致；在同步公开文件前，不得将该 URL 作为最终 Play 合规材料。
- 已新增 [`play/store-listing.md`](play/store-listing.md) 商店资料草稿；尚未提交 Console。

## 完成判据

只有同时具备以下证据，`P0-1` 才能关闭：

1. 正式上传密钥存在两处独立、可恢复的加密副本。
2. 正式证书指纹已记录并与本地、CI 产物一致。
3. GitHub 受保护环境中的签名 CI 从干净检出成功运行。
4. 测试、Release Lint、R8、资源压缩、AAB 格式和 AAB 签名全部通过。
5. 项目负责人已完成 Diff Review 和 Git Checkpoint。
6. 产物进入 Play 内部测试并完成关键真机冒烟测试；尚未满足时不得称为可生产发布。
