# Engineering Retrospective

本文件记录已经发生且有证据支持的工程经验。它不是待办清单；未完成工作应进入 Issue 或 Release Readiness Report。不要根据结果倒推未经验证的原因。

## 当前状态

截至 2026-08-26，仓库没有可迁移的正式历史复盘记录。后续在以下情况追加条目：

- 线上、内测或真机问题被确认并关闭。
- 同类缺陷重复出现。
- 一项方案被证据否定或产生意外副作用。
- Release、回滚或审计暴露出可复用的流程经验。

## 记录规则

- 一条记录对应一个事件或一个可复用教训。
- 区分事实、根因、推测和仍待验证的部分。
- 链接对应 Issue、commit、测试或 Release 证据。
- 经验应转化为具体动作：测试、门禁、文档、监控或设计约束。
- 后续证据推翻结论时，保留原文并追加更正。

## 2026-08-27：冷缓存 CI 发现依赖校验元数据缺口

### Context

P0-1 Release 工程首次推送后，GitHub `Android checks #1` 在配置阶段失败，本地完整构建此前通过。

### Evidence

CI 和本地全新 `GRADLE_USER_HOME` 均报告同样两个未登记文件：`guava-parent-33.3.1-jre.pom` 与 `junit-bom-5.11.0-M2.module`。补入经 Maven Central 独立下载核对的 SHA-256 后，同一冷缓存复现命令通过。

### Root cause

最初的 `verification-metadata.xml` 从已有 Gradle 缓存生成；两个只在冷解析时读取的传递元数据文件没有进入清单。热缓存构建因此给出了不完整的可复现性证据。

### Actions

- 依赖校验清单新增上述两个文件的 SHA-256。
- 后续新增或升级构建依赖时，必须至少用一个全新的 Gradle 用户缓存运行配置和 Release 检查，再接受校验清单。

### Reusable lesson

依赖校验必须在冷缓存环境验证；“本机已经下载过所以能构建”不能证明一台新机器能通过供应链门禁。

## 2026-08-30：持久化任务不能代替后台服务运行态

### Context

P0-3 需要在设备重启或监控进程终止后让用户明确手动恢复，同时保留 P0-2 的首发权限和后台执行边界。

### Evidence

`AppPrefs` 会保留到站任务，但 Android 进程重启后 `TrackingService` 的运行态不会保留。此前页面仅在心跳超过阈值后才发现中断，且 `START_STICKY` 允许系统尝试重建服务。最终状态机测试覆盖无服务、心跳过期、恢复后监控和到站已触发；Debug/Release 构建与权限范围门禁均通过。

### Root cause

把“任务仍在本地”和“定位服务仍在运行”当成了同一个事实；同时使用 Sticky 服务会让系统生命周期行为绕开用户主动恢复决策。

### Actions

- [x] 使用运行时服务标记和心跳共同判定“正在监控”。
- [x] App 打开时将无服务的活动任务立即转为 `interrupted`，显示一个恢复按钮。
- [x] 将服务改为 `START_NOT_STICKY`，恢复只由 Activity 或用户点击通知触发。
- [x] 用纯 Java 状态机回归测试固定状态转换。

### Reusable lesson

所有需要跨进程或重启保持的后台任务，都必须把持久化意图、运行时存活和用户可见恢复状态分开建模；不能用数据库里的 `active` 推断服务还活着。

## 2026-08-31：P0-3 状态修复的二次生命周期复查

### Context

在实现手动恢复后继续进行极端生命周期复查，重点验证定位 Provider 回调乱序、服务停止与恢复同时发生，以及服务停止后的迟到定位回调。

### Evidence

原实现用整数记录已注册 Provider，并在每次 `onProviderDisabled()` 时直接递减；重复或未注册 Provider 的回调可能把仍有监听的服务误判为无 Provider。恢复协调器先调用 `stopService()` 再调用 `startForegroundService()`；两者都是异步生命周期操作，旧服务的 `onDestroy()` 可能覆盖新的 `STARTING` 快照。原 `onLocationChanged()` 也会在没有确认任务和监听仍有效时改写运行快照。

### Root cause

把外部回调次数当成订阅集合，把服务启动请求当成服务已停止，并缺少旧生命周期与新生命周期之间的身份隔离。

### Actions

- [x] 用 `ReminderProviderRegistry` 按 Provider 名称集合维护实际监听，重复和未知回调保持幂等。
- [x] 用运行代次隔离旧服务的延迟销毁、心跳和定位回调。
- [x] 恢复时向已有服务发送幂等启动动作，移除停止后立即启动的竞态窗口。
- [x] 定位回调只有在当前服务、有效任务、有效监听都成立时才能更新运行态。
- [x] 新增 Provider 生命周期回归测试；纯 Java 测试结果为 `55 tests passed`。

### What remains unknown

当前没有可用的 Android 真机或模拟器；Gradle 复验还被 SDK 缺失 `build-tools/36.0.0/aidl.exe` 阻断。因此真实厂商省电策略、系统重启后的通知可见性和 FGS 启动限制仍需设备验证。

### Reusable lesson

后台服务状态不能由“调用过启动 API”或“收到过几次回调”推断。应为订阅资源建模集合，为生命周期请求分配身份，并让所有回调先验证当前身份再更新共享状态。

## 2026-09-01：完整 SDK 复验暴露内部动态 Receiver 边界

### Context

P0-3 纯 Java 测试通过后，需要重新建立完整、固定的 Android SDK 环境并执行 Debug/Release 全链，确认 Android 工具链能够验证当前实现。

### Evidence

原 Build Tools `36.0.0` 缺少 `aidl.exe`。重装同一固定版本后，构建越过原阻断点，第一次 `lintDebug` 报告 API 26–32 内部状态 Receiver 缺少显式导出保护。加入 `signature` 权限后，Lint 仍无法识别低版本兼容分支的权限约束；局部解释性抑制后全链通过。独立 Review 又确认：若权限名固定为正式 Application ID，不同签名的 Debug/Release 并存时可能发生同名权限冲突。

### Root cause

环境层面是 SDK 包目录不完整但包清单仍声称已安装。代码层面是内部广播只依赖包名定向，没有为 API 26–32 建立发送者权限边界；第一次修复又遗漏了 Android 自定义权限由安装包签名拥有、同名权限不能由不同签名应用共同声明的安装约束。

### Actions

- [x] 保留损坏目录作为外部备份，并从 SDK Manager 重装固定 Build Tools `36.0.0`。
- [x] 用按 Application ID 派生的 `signature` 权限保护内部状态广播。
- [x] API 33+ 同时使用 `RECEIVER_NOT_EXPORTED`；API 26–32 仅对受权限保护的兼容分支做局部 Lint 抑制。
- [x] 核对 Debug/Release 合并 Manifest 的权限名互不冲突。
- [x] 重跑 Debug 与 Release 全链；55 个测试、两种 Lint、R8、权限门禁、APK/AAB 和 Bundletool 均通过。
- [ ] 在最低与当前 Android 真机完成 P0-3 生命周期验收。

### Reusable lesson

固定版本不等于完整安装，SDK 必须检查关键工具文件并用真实构建证明。自定义 `signature` 权限还必须按变体 Application ID 命名，否则安全修复本身可能破坏 Debug/Release 并存安装。

## 复盘模板

```markdown
## YYYY-MM-DD：事件标题

### Context

发生了什么，影响了谁，任务或版本是什么。

### Evidence

复现、日志、测试、代码路径、commit 或发布证据。

### Root cause

最终确认的技术和流程根因；未知项明确标记。

### What worked

有效的诊断、保护或恢复措施。

### What failed

无效假设、遗漏检查或造成副作用的做法。

### Actions

- [ ] 负责人 / 动作 / 完成判据

### Reusable lesson

一个可用于未来项目的具体经验。
```
