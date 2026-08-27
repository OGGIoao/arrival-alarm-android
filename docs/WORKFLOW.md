# Engineering Workflow

## 共同门禁

每个任务只解决一个明确问题。开始前先读取相关产品、架构、决策和历史经验，追踪当前实现调用链，并写出可验证的完成条件。涉及安全、权限、位置、用户数据、后台服务、网络、并发或 Release 配置时，实施前先向项目负责人说明方案、影响和回滚边界。

## Feature

Problem
↓
Requirements
↓
Acceptance Criteria
↓
Codex Plan
↓
Human Review
↓
Implementation
↓
Automated Tests
↓
Manual Test
↓
Diff Review
↓
Commit / PR
↓
Feedback

执行要求：

1. 在 `docs/PRODUCT.md` 的现有边界内定义问题、非目标和验收标准。
2. 对照 `docs/ARCHITECTURE.md` 确认组件、接口、权限和数据边界。
3. 需要产品取舍、改变现有行为或扩大范围时，在实现前等待 Human Review。
4. 实现最小完整切片；核心逻辑补充防回归测试。
5. 完成自动检查、真机路径和 diff review 后，提出聚焦的 commit message；未经明确授权不代替项目负责人发布。

## Bug

Reproduce
↓
Find root cause
↓
Create regression test
↓
Minimal fix
↓
Run tests
↓
Manual verification
↓
Commit

执行要求：

1. 用当前代码、日志或失败测试建立可重复证据。
2. 追踪完整调用链并确认根因；未知部分标为“需要进一步验证”。
3. 让回归测试在修复前能够证明问题，或说明无法自动化的原因。
4. 只修改根因及同一调用链上不可分割的边界情况。
5. 验证正常路径、失败路径和最可能的副作用，再提出 commit message。

## Release

Finish planned issues
↓
Release Readiness Audit
↓
Fix P0
↓
Fix P1
↓
Regression Test
↓
Release Build
↓
Real-user Test
↓
Release
↓
Monitor
↓
Retrospective

Release Readiness Audit 和 P0/P1 的分级、门禁与报告格式以根目录 `AGENTS.md` 为准。发布结论必须由当前 build、签名、版本、权限、隐私、测试和真机证据支持。

## 当前验证入口

当前受支持的 Debug 本地检查是：

```powershell
.\android-app\build-apk.ps1 -SdkRoot "C:\path\to\android-sdk"
```

该脚本调用固定版本的 Gradle Wrapper，运行核心测试、Debug Lint 和 Debug APK 构建，再复制兼容文件名到 `outputs/arrival-alarm-debug.apk`。

Release 构建与静态验证入口是：

```powershell
.\android-app\build-release.ps1 -SdkRoot "C:\path\to\android-sdk"
```

脚本会在含中文用户名的 Windows 机器上自动使用项目专用的 ASCII Gradle 缓存目录。未配置上传密钥时生成的是用于工程验证的未签名 AAB。只有安全配置四个 `ARRIVAL_UPLOAD_*` 环境变量并通过 `.\android-app\build-release.ps1 -VerifyPlayBundle`，才可把产物称为“签名验证通过”；即使如此，也仍需真机和 Play 内部测试验收。

## Definition of Done

Implementation complete
→ Relevant tests pass
→ Build/check passes
→ Diff reviewed
→ Manual verification instructions provided

如果环境缺失导致任何检查不能运行，任务报告必须明确写出未验证项、原因和剩余风险，不能把“代码已写”当成完成。

## 完成报告

每个任务至少报告：

- 修改文件及原因。
- 根因或需求如何被满足。
- 自动测试、build、lint 和手动验证的实际结果。
- 最值得项目负责人检查的 diff。
- 副作用、剩余风险和未验证项。
- 项目负责人可执行的手动验收步骤。
- 一个聚焦、可独立回滚的推荐 commit message。
