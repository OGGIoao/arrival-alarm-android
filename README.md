# 别坐过站（Android 原生版）

这是一个极简的 Android 到站提醒 App：输入地点后开始监控，接近目的地时用声音、振动和高优先级通知提醒。它不显示地图、路线、路况、换乘或 ETA。

## 项目文档

- [产品目标与边界](docs/PRODUCT.md)
- [当前架构与调用流](docs/ARCHITECTURE.md)
- [工程工作流](docs/WORKFLOW.md)
- [技术决策记录](docs/DECISIONS.md)
- [当前发布就绪状态](docs/RELEASE_READINESS.md)
- [工程复盘与经验](docs/RETROSPECTIVE.md)
- [变更记录](CHANGELOG.md)

主要实现位于 `android-app/`：源码在 `android-app/src/`，纯 Java 核心测试在 `android-app/test/`，构建系统入口是 `android-app/gradlew.bat`；`android-app/build-apk.ps1` 是兼容旧 Debug APK 文件名的快捷入口。

## 下载

可直接下载安装调试签名试用包（最低 Android 8.0）：

- 下载 APK（v0.4.2）：https://github.com/yueqiu666/arrival-alarm-android/releases/download/v0.4.2/arrival-alarm-debug.apk
- Release 页面：https://github.com/yueqiu666/arrival-alarm-android/releases/tag/v0.4.2

> 试用包仅供日常通勤试用，并非正式发布签名；正式使用请按下方步骤自行构建。

## 构建与试用

仓库只提交可复现构建的源码，不将调试签名或本机生成的 APK 纳入版本控制。最低支持 Android 8.0；构建步骤见 [android-app/README.md](android-app/README.md)。

首次打开后授予精确位置和通知权限。应用只申请位置、前台位置服务、通知、振动和网络权限；不申请后台位置、使用情况访问、通知使用权、精确闹钟或全屏通知。拒绝精确位置或通知后无法开始守候。

## 产品边界与隐私

- 无账号、自建后端、广告或分析 SDK。默认自动使用 Photon/OpenStreetMap 智能地点联想，不要求用户填写任何 Key；开发者可在构建时配置应用级高德 Key。
- 提醒、最近地点和设置只保存在本机，可一键清空。
- 输入简称或不完整地点时会显示即时地点候选；已有定位权限时优先尝试附近结果，再显示名称、城市和短地址供确认。
- 地点搜索时，关键词和可选的附近坐标会直接发送给地点服务；目的地、最近地点、提醒半径和设置只保存在本机。
- 地点可通过系统“分享”、`arrivalalarm://set` 深链或直接粘贴导入；不再注册第三方网站链接入口。
- 从地图链接或分享文本导入坐标时，高德与腾讯的 GCJ-02、百度的 BD-09 会先在本机转换到 WGS-84 再参与距离计算；来源无法识别时不转换，并在确认框里明确提示。
- 不读取其他 App 的页面或剪贴板，不保存通知正文和完整使用记录。
- 厂商省电、强制结束应用、关闭定位、静音/勿扰或地下无定位信号都可能影响提醒。

源码、构建方法和完整限制见 [android-app/README.md](android-app/README.md)。iOS 尚未开发。

## 开源与安全

- 代码采用 [MIT License](LICENSE) 开源。
- 数据处理方式见 [PRIVACY.md](PRIVACY.md)。
- 请不要在公开 Issue 中提交真实位置、行程、通知正文、API Key 或日志；安全问题见 [SECURITY.md](SECURITY.md)。
- 本项目仍是研发原型，不能用于人身安全、应急或其他要求百分之百可靠的场景。
