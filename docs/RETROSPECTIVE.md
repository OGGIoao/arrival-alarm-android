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
