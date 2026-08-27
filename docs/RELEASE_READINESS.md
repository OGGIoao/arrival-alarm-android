# Release Readiness Report

- 更新时间：2026-08-27
- 当前范围：`P0-1 没有可提交 Google Play 的 Release 构建`
- 当前结论：`NOT READY`

`NOT READY` 不表示 Release 工程没有进展，而是表示正式上传密钥、远端签名 CI 和 Play 内部测试尚无完成证据。当前产物不得上传 Google Play。

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

- 核心 Java 测试套件已通过，并已接入 Gradle/JUnit；本次 Release 提交不包含尚未单独提交的地点联想性能测试。
- `lintRelease` 已通过，阻断错误为 0；当前仍有 39 条非阻断警告，尚未逐项处理。
- 已生成 `gradle.lockfile` 和依赖 SHA-256 校验元数据。
- Gradle 发行包配置了官方 SHA-256 校验值。
- 已针对中文 Windows 用户目录设置独立 ASCII Gradle 缓存和构建输出路径。
- 本地已完成一次干净的测试、Lint、R8、资源压缩、AAB 和 Bundletool 验证。

### 签名流程验证

- 使用明确不会用于发布的临时非 Debug 测试密钥完成了 `verifyPlayBundle`，证明签名配置、证书检查和 AAB 校验链路可运行。
- 临时测试密钥和临时签名 AAB 不得用于正式上传，也不构成正式上传密钥已建立的证据。
- 已检查仓库，没有发现临时测试密码、测试密钥路径或私钥进入源码。

### CI 和凭据准备

- 仓库中已增加普通 Release 检查工作流和 `play-release` 环境签名工作流。
- 密钥治理方案已确定为：个人 KeePassXC 保险库 + GitHub `play-release` Environment Secrets；普通协作者不接触上传密钥。
- KeePassXC `2.7.12` 已从官方 GitHub Release 下载，并核对 SHA-256 与 Windows 数字签名。
- KeePassXC 已以“仅当前用户”方式安装成功；首次全机安装因缺少管理员权限返回 `Error 1925`，改为用户级安装后通过。

## 尚未完成

### P0：关闭 P0-1 前必须完成

- [ ] U 盘在场后确认盘符、卷标和容量，防止把备份写错磁盘。
- [ ] 在 OneDrive 建立个人 KeePassXC 主保险库，并建立不保存在保险库内部的主密码恢复方式。
- [ ] 将 KeePassXC 数据库备份到 U 盘，验证主副本均可打开；不能只有同一硬盘上的两份文件。
- [ ] 在仓库外创建正式 `arrival-upload` 上传密钥，记录 Alias、有效期和 SHA-256 证书指纹。
- [ ] 将正式上传密钥作为加密附件放入个人 KeePassXC，并在 U 盘保存独立副本。
- [ ] 执行一次恢复演练：从 U 盘副本恢复保险库和上传密钥，并核对证书指纹一致。
- [ ] 在 GitHub `play-release` Environment 中配置四个 `ARRIVAL_UPLOAD_*` Secrets，并设置审批和分支保护。
- [x] 已完成独立 Diff Review，并将 P0-1 Release 工程隔离为聚焦 Git checkpoint；地点联想和动画优化仍留在工作区，未混入本次提交。
- [ ] 将 P0-1 checkpoint 推送到 GitHub，并实际运行一次未签名 `Android checks`，确认远端 clean build、test、lint、R8 和 Bundletool 全部成功。
- [ ] 将签名工作流推送到 GitHub 后实际运行一次，验证远端 clean build、test、lint、R8、签名和 Bundletool 全部成功。
- [ ] 保存远端签名 CI 的产物摘要和证书指纹证据；不得使用此前的临时测试 AAB。

### 发布前仍需完成

- [ ] 创建 Google Play Console 应用并启用 Play App Signing；当前没有创建或上传任何 Play 应用。
- [ ] 上传到内部测试轨道，而不是直接生产发布。
- [ ] 使用 Bundletool/Play 生成的安装包，在最低支持版本和当前 Android 版本真机验证提醒、前台服务、通知、Receiver 和权限拒绝路径。
- [ ] 复核 Play 数据安全表、隐私政策、特殊权限声明、商店素材和内容分级。
- [ ] 评估并处理 39 条非阻断 Lint 警告；若决定延期，需要逐项记录风险接受理由。

## 当前停止点

U 盘目前不在项目负责人身边，因此按安全门禁暂停在“已安装并验证 KeePassXC、尚未创建任何正式凭据”。没有 U 盘时不得提前创建正式上传密钥。

恢复工作时的第一步：插入 U 盘，重新枚举文件系统磁盘，并由项目负责人确认目标盘符和卷标。确认后才能创建主保险库、独立备份和正式上传密钥。

## 完成判据

只有同时具备以下证据，`P0-1` 才能关闭：

1. 正式上传密钥存在两处独立、可恢复的加密副本。
2. 正式证书指纹已记录并与本地、CI 产物一致。
3. GitHub 受保护环境中的签名 CI 从干净检出成功运行。
4. 测试、Release Lint、R8、资源压缩、AAB 格式和 AAB 签名全部通过。
5. 项目负责人已完成 Diff Review 和 Git Checkpoint。
6. 产物进入 Play 内部测试并完成关键真机冒烟测试；尚未满足时不得称为可生产发布。
