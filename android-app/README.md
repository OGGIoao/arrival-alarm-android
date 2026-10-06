# Android 原生版

这是“别坐过站”的 Android 原生实现，正式 Application ID 为 `com.navobell.arrivalalarm`，最低 Android 8.0（API 26）。项目使用固定版本的 Gradle/AGP，但不依赖 AndroidX、Google Play Services 或第三方运行时 SDK。

## 构建

需要 JDK 17 或更高版本、Android Platform 36 和 Build Tools 36.0.0。Debug 完整检查：

```powershell
.\android-app\build-apk.ps1 -SdkRoot "C:\path\to\android-sdk"
```

脚本会调用项目自带的 Gradle Wrapper，运行核心测试和 Debug Lint，再生成 Debug APK 并复制到已忽略的 `outputs/arrival-alarm-debug.apk`。

Release 工程检查：

```powershell
.\android-app\build-release.ps1 -SdkRoot "C:\path\to\android-sdk"
```

Release 默认启用 R8 和资源压缩；脚本会自动规避中文 Windows 用户目录导致的 Gradle 测试进程问题。未设置上传密钥时，AAB 仅用于工程验证，不能上传 Google Play。正式签名从以下环境变量读取：

- `ARRIVAL_UPLOAD_KEYSTORE`
- `ARRIVAL_UPLOAD_STORE_PASSWORD`
- `ARRIVAL_UPLOAD_KEY_ALIAS`
- `ARRIVAL_UPLOAD_KEY_PASSWORD`

四项必须全部设置，且密钥文件和密码不得提交到 Git。设置后运行 `.\android-app\build-release.ps1 -SdkRoot "C:\path\to\android-sdk" -VerifyPlayBundle`，同时检查 AAB 格式和签名。

## 已实现

- iOS 风格的分组背景、系统蓝主操作、圆角卡片、开关式设置和即时按压/触觉反馈。
- 原生单页地点输入；输入简称时即时匹配最近地点并异步显示候选。默认自动调用 Photon/OpenStreetMap POI 服务，不要求用户配置 Key；开发者构建时若提供应用级高德 Key则优先使用高德，最后回退系统 `Geocoder`。
- 联想搜索采用 100–180 毫秒可取消防抖、双后台线程和 32 项会话缓存；旧请求结果不会覆盖新关键词。
- v0.4.3 将联想等待缩短到 80–120 毫秒；缓存本机候选，跳过重复候选/状态的布局重建，并取消叠加的按压动画。连续输入时不再反复解析日程与最近地点；系统搜索仍只保留与关键词高度相关的候选。
- 适配 Android 15+ 强制 edge-to-edge：标题、设置按钮和滚动内容会使用状态栏、刘海及底部导航栏安全区，不再与系统图标重叠；键盘弹出时使用可调整窗口。
- 高德返回的 GCJ-02 坐标会在本机转换后再参与到站距离计算，避免坐标系偏移直接影响提醒半径。
- 接收地图/打车软件的 `text/plain` 系统分享，以及 `arrivalalarm://set` 深链。
- 识别常见地图 URI、经纬度和被选中的地点文字；高德与腾讯链接的 GCJ-02、百度链接的 BD-09 会先在本机转换成 WGS-84，来源无法识别时保持原值并在确认框标注；外部坐标启动前反查地址并要求用户确认。
- `arrivalalarm://set?name=&lat=&lng=` 由本应用定义，坐标一律按 WGS-84 处理，不做坐标系转换。
- 不再注册第三方网站的 Android 链接入口。第三方域名无法由本应用验证，拦截只会让本 App 出现在系统“打开方式”里，无法做到直接启动；导入目的地统一走系统分享、深链和粘贴。
- Android 前台定位服务；任何一次可靠定位进入或落在预设半径内都会立即触发到站闹钟，不再等待第二次确认。
- 高优先级可点击通知、声音、振动和“我已到达”操作；每 30 秒重复，最多 5 分钟。
- 最近地点、提醒半径、本机清空和隐私说明。

## 隐私

应用声明 `INTERNET` 权限用于地点联想，没有账号、自建后端、广告或分析。关键词和可选的附近坐标会直接发送给 Photon/OpenStreetMap，或开发者构建时配置的高德服务；搜索缓存和最近地点只保存在本机，不写入日志。首发版不申请后台位置、使用情况访问、通知使用权、精确闹钟或全屏通知。

## 限制

- 用户无需配置地图 Key。开发者若有高德 Web 服务 Key，可在构建前设置环境变量 `ARRIVAL_AMAP_WEB_KEY`；密钥会由构建脚本注入 APK，不出现在设置页。
- Photon 公共服务只适合合理用量并可能限流；正式大规模发行应部署自己的 Photon 实例，或使用由开发团队统一管理、绑定应用身份的商业地图服务。
- 长期车站识别、其他 App 信号识别和固定日程自动启动属于后续实验能力，不在首个 Play 发布范围内。
- 厂商省电、强制结束应用、关闭定位、静音/勿扰或地下无信号都可能影响提醒，不能宣传为百分之百触发。
