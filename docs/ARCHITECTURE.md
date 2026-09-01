# Architecture

## 系统概览

本项目是单模块 Android 原生应用，正式 Application ID 为 `com.navobell.arrivalalarm`，Java 命名空间暂时保持 `com.example.arrivalalarm`。它不使用 AndroidX、Google Play Services 或第三方运行时 SDK；`android-app/gradlew.bat` 通过固定版本的 Gradle/AGP 完成测试、Lint、Debug APK 和 Release AAB 构建。`android-app/build-apk.ps1` 仅是兼容入口，不再维护第二套编译链。

```text
用户 / 系统分享 / 深链
          ↓
     MainActivity
          ↓
地点搜索与解析 → AppPrefs → TrackingService → AlarmActivity / 通知
```

## 仓库结构

| 路径 | 职责 |
| --- | --- |
| `android-app/src/com/example/arrivalalarm/` | Android 应用源码 |
| `android-app/test/com/example/arrivalalarm/` | 可脱离 Android Runtime 执行的纯 Java 核心测试 |
| `android-app/res/` | 字符串、颜色、主题、图标等资源 |
| `android-app/AndroidManifest.xml` | 权限、Activity、Service、Receiver 和外部入口 |
| `android-app/gradlew.bat` | 唯一构建系统入口；生成 Debug APK、Release AAB 并运行测试和 Lint |
| `android-app/build-apk.ps1` | 调用 Gradle Wrapper 并复制旧文件名 Debug APK 的兼容入口 |
| `outputs/` | 本机构建产物；不作为源码或发布事实来源 |
| `docs/` | 产品、架构、流程、决策和复盘文档 |

根目录的空 `src/`、`tests/` 和 `icons/` 目前不属于 Android 构建调用链。新增实现前应先决定保留、迁移或删除策略，不能形成第二套平行源码结构。

## 主要组件

| 组件 | 当前职责 |
| --- | --- |
| `MainActivity` | 单页 UI、核心权限引导、地点选择、任务管理和外部 Intent 处理 |
| `AmapPlaceSearch` / `PhotonPlaceSearch` | 地点联想与搜索；高德可由构建时 Key 启用，Photon 为默认网络来源 |
| `PlaceInputParser` / `ShareParser` / `PlaceQuality` | 解析外部地点输入并过滤低质量候选 |
| `CoordinateTransform` | 高德 GCJ-02 与提醒计算使用的坐标之间转换 |
| `AppPrefs` | 使用私有 `SharedPreferences` 保存任务、最近地点和设置；升级时保留但停用旧固定日程数据 |
| `TrackingService` | 前台定位、任务状态、出行检测、车站提示和提醒触发 |
| `CoreLogic` / `ReminderStateLogic` | 距离、半径、定位可靠性、出行状态和提醒运行态判断等可纯 Java 测试的核心逻辑 |
| `AlarmActivity` / `ActionReceiver` | 到站页面展示与“已到达/停止”操作 |
| `ScheduleLogic` / `ScheduleManager` 等原型类 | 后续固定日程实验代码；首发无 Manifest Receiver、无 UI 入口且升级时取消旧调度 |
| `TransitNotificationListener` / `JourneyPrompt` 等原型类 | 后续出行识别实验代码；首发无 Manifest 服务、无 UI 入口且服务拒绝恢复该模式 |

## 关键运行流

### 手动到站提醒

1. `MainActivity` 解析输入，并依次尝试高德、Photon 和系统 `Geocoder` 获取候选。
2. 用户确认后，`MainActivity` 通过 `AppPrefs.saveTask` 保存任务。
3. `MainActivity` 以 `ACTION_START_REMINDER` 启动 `TrackingService`。
4. `TrackingService` 作为前台定位服务接收位置，使用 `CoreLogic` 判断定位可靠性和到站条件，并持续保存任务状态。
5. 触发后显示高优先级通知和到站页面；`AlarmActivity` 或 `ActionReceiver` 发送 `ACTION_STOP_REMINDER` 清理任务并停止服务。

任务的持久化状态和服务运行态是两个独立事实。`ReminderStateLogic` 只有在任务为 `active`、当前进程中的 `TrackingService` 存在且心跳未过期时才判定为“正在监控”；应用打开时若本地仍有任务但服务不在运行，会立即标记为 `interrupted`，首页显示“需要恢复”和一个恢复按钮。恢复只能由用户在可见 Activity 或恢复通知中主动触发，服务使用 `START_NOT_STICKY`，不会因系统重启或进程终止自行拉起。

运行态由 `ReminderRuntime.Snapshot` 表达，生命周期依次区分“启动请求 (`STARTING`)”、“已进入前台 (`FOREGROUND`)”、“已注册定位监听 (`LOCATION_READY`)”和“已失败 (`FAILED`)”。只有定位监听注册成功、至少一个 Provider 仍被本服务持有、收到过首个定位且单调时钟心跳未过期时，UI 才显示“正在守候”；仅进入前台或仅注册监听都不会被伪装成监控中。Provider 由 `ReminderProviderRegistry` 按名称集合维护，重复或乱序的禁用回调不会错误减少数量。启动请求使用运行代次隔离旧服务的延迟 `onDestroy()` 和迟到定位回调，避免旧实例覆盖新的恢复状态。

`ReminderRuntimeCoordinator` 是 Activity、通知 `ActionReceiver` 和服务之间的统一动作入口：开始、恢复、停止和状态协调都经过它。恢复时向同一服务实例发送幂等启动请求，不先异步停止再启动，避免 `stopService()` 与 `startForegroundService()` 竞态。运行心跳和启动超时使用 `SystemClock.elapsedRealtime()`，不使用可被用户修改的墙上时间；运行快照只保存在进程内，不写入 `AppPrefs`。

服务到 Activity 的进程内状态广播使用按变体 Application ID 派生的 `signature` 权限；API 33+ 还注册为 `RECEIVER_NOT_EXPORTED`。Debug 与 Release 因而拥有不同权限名，可以并存安装，也不会接受其他签名应用伪造的状态刷新。

### 首发禁用的原型调用链

- 长期车站识别、速度/位移出行检测、使用情况访问和通知读取没有首发 UI 或 Manifest 入口。
- 固定日程 Receiver 与开机 Receiver 不注册，已有日程只保留本机数据并取消系统调度。
- `TrackingService` 启动时清除旧出行检测运行状态，不会在任务结束或进程恢复后进入自动检测模式。

当服务在应用仍处于前台时主动发现权限、Provider 或监听初始化失败，会立即将任务标记为 `interrupted` 并发送恢复通知；通知提供用户触发的“恢复”动作。设备重启而应用从未运行的场景不能产生即时通知，必须等用户打开应用触发状态协调，这是 P0-2 禁止开机 Receiver 的明确边界。

## 数据和信任边界

- 本机持久数据：`AppPrefs` 中的提醒任务、最近地点和设置；升级用户可能仍保留不可触发的旧固定日程记录。
- 外部网络：Photon/OpenStreetMap 或构建时配置的高德地点服务；系统 `Geocoder` 的具体实现由设备决定。
- 敏感权限：精确/大致位置和通知；location 前台服务只由用户在可见 Activity 中主动启动。
- 密钥：`ARRIVAL_AMAP_WEB_KEY` 仅作为构建输入；不得写入源码、日志、测试或 Git 历史。
- 发布签名：Debug 与 Release 分离；上传密钥只从安全环境变量读取，Play App Signing 管理面向用户的应用签名密钥。

## 架构修改门禁

以下变更实施前必须更新 `docs/DECISIONS.md`：模块或数据所有权变化、新依赖、新网络服务、权限变化、持久化格式变化、后台执行策略变化、最低/目标 Android 版本变化、构建或签名流程变化。
