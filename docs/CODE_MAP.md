# 代码地图（CODE_MAP）

开工先读这张图：功能 → 文件。路径都按仓库根目录写，模块在前。

## Android — 页面与入口

- App 入口 / 首页状态机：`app/src/main/java/com/tuxundl/android/MainActivity.kt`
  （含模式栏、流量提醒、Tor 模式进出 `exitTorMode`、电源键长按菜单）
- 首页界面与卡片：`app/.../ui/MainScreen.kt`、`app/.../ui/HomeBricks.kt`
  （卡片槽位与布局在 HomeBricks；单卡刷新范围别扩散，见流量卡写法）
- 全局主题/组件：`app/.../ui/theme/`（Theme.kt、Color.kt、Type.kt）、
  `app/.../ui/TuxunCard.kt`、`app/.../ui/Components.kt`、`app/.../ui/Markdown.kt`
- 设置总页：`app/.../SettingsActivity.kt`、`app/.../ui/SettingsScreen.kt`

## Android — 节点、测速、出口

- 节点页：`app/.../ProxyActivity.kt`、`app/.../ui/ProxyScreen.kt`
  （分组、单节点/整组测速、选最快、⋮ 菜单都在这里）
- 测速源（用户可选的测速 URL）：`app/.../util/TestSource.kt`；
  存放在 `design/src/main/java/com/tuxundl/android/design/store/UiStore.kt`
  的 `testSourceUrl`；经 `service/.../remote/IClashManager.kt`（Binder）→
  JNI → Go 侧 `healthCheck` / `healthCheckProxy`
  （`core/src/main/golang/native/tunnel.go` 一带）
- 出口信息卡：`app/.../util/ExitInfo.kt`（查出口 IP/地区，多源兜底）；
  点卡打开进阶统计页 `app/.../ui/TrafficAdvanced.kt`

## Android — Tor（洋葱路由，1.7.3）

全部 Tor 代码只在 `app/src/tor/`，仅 alpha/meta 两种构建装配
（见 `app/build.gradle.kts` 的 sourceSets）；TV 只有桩
`app/src/tv/java/com/tuxundl/android/tor/TorEntryProvider.kt`。

- 入口接口（各 flavor 通用）：`app/src/main/java/com/tuxundl/android/tor/TorEntry.kt`
- Tor 页面：`app/src/tor/java/com/tuxundl/android/tor/TorActivity.kt`
- 地球与线路渲染：同目录 `GlobeView.kt`、`WorldMap.kt`
  （地图数据在 `app/src/tor/assets/`）
- Tor 进程管理（启停、控制口、线路抓取、日志）：同目录 `TorManager.kt`
- 出口切换（不外泄顺序、自检、看门狗配合）：同目录 `TorEgress.kt`
- 本机存储（地区选择、网桥静默保存）：同目录 `TorLocalStore.kt`
- 日志人话版翻译：同目录 `TorLogTranslate.kt`
- Go 侧出口改写：`core/src/main/golang/native/config/tor.go`
  （把 GLOBAL 改写为只含 Tor；注册点在 `config/process.go`）
- 标记字段：`core/src/main/java/com/tuxundl/android/core/model/ConfigurationOverride.kt`
  的 `tuxundl-tor` 字段；看门狗在 `service/.../ClashManager.kt`

## Android — 流量

- 采样器（服务进程、每秒采样、写共享文件）：
  `app/.../util/TrafficSampler.kt`；历史与日流量：
  `app/.../util/TrafficHistory.kt`、`app/.../util/DailyTraffic.kt`
- 流量图页：`app/.../ui/TrafficChartScreen.kt`；连接质量探测：
  `app/.../util/TrafficQuality.kt`
- 核心流量数据：`service/.../ClashManager.kt`、
  `core/src/main/golang/native/tunnel/statistic.go`

## Android — 更新

- 检查更新（统一中心、结果落盘）：`app/.../util/UpdateCheck.kt`
- App 内自更新（下载、验签、调起安装）：`app/.../util/SelfUpdate.kt`
- 更新页：`app/.../ui/UpdateScreen.kt`；「更新全部」跟踪：
  `app/.../util/UpdateAllTracker.kt`
- 订阅更新在 `service/.../ProfileManager.kt`、`ProfileWorker.kt`

## Android — 备份 / 分应用 / 定时 / 应用锁

- 备份导出导入：`app/.../util/Backup.kt`、`app/.../BackupActivity.kt`、
  `app/.../ui/BackupScreen.kt`（网桥不进备份：Tor 存储是独立文件）
- 分应用代理：`app/.../AccessControlActivity.kt`、
  `app/.../ui/AccessControlScreen.kt`、模式定义
  `service/.../model/AccessControlMode.kt`
- 定时与夜间（自动开关、夜间切模式）：`app/.../util/ProxyScheduler.kt`、
  `app/.../ScheduleReceiver.kt`
- 应用锁（密码+指纹）：`app/.../AppLockActivity.kt`、
  `app/.../ui/AppLockScreen.kt`、`app/.../util/AppLock.kt`、
  `app/.../util/BiometricUnlock.kt`

## Android — 配置与服务进程

- 订阅/配置管理：`service/src/main/java/com/tuxundl/android/service/`
  下 `ProfileManager.kt`、`ProfileProcessor.kt`、`ClashService.kt`、
  `TunService.kt`（VPN 隧道）、`ClashManager.kt`（UI 进程的调用面）
- 配置覆写（override）落盘与重放：`service/.../clash/module/ConfigurationModule.kt`
- UI 偏好存储：`design/.../store/UiStore.kt`；通用存储框架：
  `common/src/main/java/com/tuxundl/android/common/store/`

## core — Go 侧（mihomo 桥）

目录 `core/src/main/golang/native/`：

- JNI 入口与隧道：`main.go`、`tunnel.go`、`proxy.go`、`tun.go`
- 配置处理链：`config/process.go`（入口）→ `config/override.go`
  （覆写合并）→ `config/tor.go`（Tor 出口改写）；`config/load.go`、
  `config/fetch.go`（订阅拉取）
- 隧道内部：`tunnel/`（proxies.go 节点与分组、connectivity.go 健康检查、
  statistic.go 流量统计、state.go 状态）
- Kotlin 桥：`core/src/main/java/com/tuxundl/android/core/Clash.kt`、
  `core/.../bridge/Bridge.kt`；数据模型在 `core/.../model/`

## 构建与版本（Android）

- 根 `build.gradle.kts`：全局配置——版本号、flavor（alpha / meta / tv）、
  ABI splits（`-PreleaseAbis=true` 时只出 arm64-v8a + armeabi-v7a，
  仅 build-release 传）、签名回退、TV 的 applicationId 与独立版本号
- `app/build.gradle.kts`：Tor 的 sourceSets 装配、依赖、geo 数据下载任务
- `gradle.properties`：并行与构建缓存（`org.gradle.caching=true`）开关

## TV 版

- flavor 定义：根 `build.gradle.kts` 的 `create("tv")`（包名
  com.tuxundl.android.tv，IS_TV=true，版本 0.1.0 独立线）
- 遥控器焦点适配：`app/.../ui/TvFocus.kt`；Tor 桩见上文 Tor 一节
- 构建：`.github/workflows/build-tv.yaml`（只出 arm64 + armeabi-v7a）

## desktop — Windows / Linux / Mac（同一套代码）

目录 `desktop/src/main/kotlin/com/tuxundl/windows/`（独立 Gradle 构建，
按构建机 OS 分流打包与识别名）：

- 入口：`Main.kt`；平台差异（代理、TUN、自启、数据目录）：`Platform.kt`
- mihomo 边车桥（回环 REST、数据目录指针）：`CoreBridge.kt`、`MihomoApi.kt`
- 页面：`NodesPage.kt`、`ConnectionsPage.kt`、`RulesPage.kt`、
  `SubscriptionsPage.kt`、`LogsPage.kt`、`SettingsPage.kt`
- 首次引导：`Onboarding.kt`；检查更新（按 OS 分流标签）：`UpdateCheck.kt`
- 系统代理：`SystemProxy.kt`；提权（TUN）：`Elevation.kt`；
  配置生成：`ConfigFactory.kt`；备份：`Backup.kt`

## .github/workflows — 谁干什么

- `build-debug.yaml`：推 main / PR 自动跑的调试构建（alpha 全 ABI + legacy）
- `build-release.yaml`：正式发版（手动触发，带 release-tag；升版本、
  签名、建发布页；只编 arm64 + armeabi-v7a）
- `build-tv.yaml`：TV Beta 构建（手动触发，只出两个 TV 包，不建发布页）
- `build-pre-release.yaml`：预发布构建
- `build-windows.yaml` / `build-linux.yaml` / `build-mac.yaml`：
  桌面三平台各自打包（只产出 artifact，不建发布页）
- `publish-package.yaml`：把发布页的 arm64 包同步到 GitHub Packages
- `update-dependencies.yaml`：更新 Clash 内核与 Go 模块
- `preview-windows-ui.yaml`：桌面界面预览图

## 其他

- 发布说明：`release-notes/`（每版一份，文件名带平台与版本）
- 变更日志：根目录 `CHANGELOG.md`；双语说明：`README.md` / `README.en.md`
  （徽章与下载区的版本号是写死的，发版时要同步改）
