<p align="center"><b>简体中文</b> | <a href="README.en.md">English</a></p>

<p align="center">
  <img src="docs/assets/icon.webp" width="120" alt="TuxunDL icon">
</p>

<h1 align="center">TuxunDL</h1>

<p align="center">
  一个仓库，四个平台——把「自定义」做到骨子里的开源代理客户端。<br>
  Android、Windows、Linux、Mac 全平台同步开发、同步发布，mihomo 核心驱动，从主题配色到规则细节，想改的都能改。
</p>

<p align="center">
  <a href="https://github.com/liyuzhou79-star/TuxunDL/releases/tag/v1.7.3"><img src="https://img.shields.io/badge/Android-1.7.3-3DDC84" alt="Android 1.6.3"></a>
  <a href="https://github.com/liyuzhou79-star/TuxunDL/releases/tag/win-v1.6.11"><img src="https://img.shields.io/badge/Windows-1.6.11-0078D6" alt="Windows 1.6.11"></a>
  <a href="https://github.com/liyuzhou79-star/TuxunDL/releases/tag/linux-v1.1.1"><img src="https://img.shields.io/badge/Linux-1.1.1-FCC624" alt="Linux 1.1.1"></a>
  <a href="https://github.com/liyuzhou79-star/TuxunDL/releases/tag/mac-v1.0.1"><img src="https://img.shields.io/badge/macOS-1.0.1-555555" alt="Mac 1.0.1"></a>
  <a href="https://github.com/liyuzhou79-star/TuxunDL/releases"><img src="https://img.shields.io/github/downloads/liyuzhou79-star/TuxunDL/total" alt="Downloads"></a>
  <a href="https://github.com/liyuzhou79-star/TuxunDL/actions/workflows/build-debug.yaml"><img src="https://img.shields.io/github/actions/workflow/status/liyuzhou79-star/TuxunDL/build-debug.yaml?branch=main&label=build" alt="Build status"></a>
  <img src="https://img.shields.io/badge/Android-5.0%2B-3DDC84" alt="Android 5.0+">
  <img src="https://img.shields.io/badge/Windows-10%2F11-0078D6" alt="Windows 10/11">
  <img src="https://img.shields.io/badge/Linux-64bit-FCC624" alt="Linux 64-bit">
  <img src="https://img.shields.io/badge/macOS-Apple%20Silicon-555555" alt="macOS Apple Silicon">
  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF" alt="Kotlin">
  <img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="GPL-3.0">
  <a href="https://github.com/liyuzhou79-star/TuxunDL/commits/main"><img src="https://img.shields.io/github/last-commit/liyuzhou79-star/TuxunDL" alt="Last commit"></a>
</p>

<p align="center">
  <a href="https://github.com/liyuzhou79-star/TuxunDL/releases/latest"><b>下载最新版</b></a> ·
  <a href="#快速上手">快速上手</a> ·
  <a href="#功能总览">功能</a> ·
  <a href="#版本一览">版本一览</a> ·
  <a href="#更新记录">更新记录</a> ·
  <a href="CONTRIBUTING.md">参与贡献</a>
</p>

---

<p align="center">
  <img src="docs/assets/screenshot-home.png" width="300" alt="TuxunDL 主界面">
</p>

## 这是什么

TuxunDL 是一个完全开源（GPL-3.0）的代理客户端，核心采用 [mihomo](https://github.com/MetaCubeX/mihomo)。它和大多数同类工具最大的不同有两点：

- **一个仓库，四个平台**：**Android、Windows、Linux、Mac** 四个版本在同一个仓库里同步开发、每次发布四个平台的安装包一起到位，另外还有一个 Android TV 测试版。换设备不用换习惯，数据和订阅都能带走。
- **自定义拉满**：主题色、首页排版、卡片开关、节点收藏与排序、规则编辑、DNS 覆写、分应用代理——能开放的全都开放，就是要让你把客户端调成自己最顺手的样子。

功能上也不含糊：实时流量监控与长期统计、定时开关与夜间模式、应用锁与隐私保护、自动备份、订阅到期提醒，各平台该有的都有。所有安装包都由 GitHub Actions 公开构建，代码全开源——用得放心，改得自由。

Android 版界面以 Kotlin + Jetpack Compose + Material 3 打造，吉祥物是天线连着节点的小机器人；桌面版（Windows / Linux / Mac）是全新编写的程序（代码在 `desktop/` 目录，全部原创），吉祥物换成窗户造型，内核同样是 mihomo，安装包已内置核心，装完即用。

## 快速上手

第一次用，三步就够：

1. 在下方「下载」里选你平台的安装包，装好打开。
2. 添加订阅：粘贴你的订阅链接，或者扫码、从文件导入都可以。
3. 回到首页，选一个节点，点中间的大按钮——连上了。

连上之后想折腾的都在设置里：换主题、调首页卡片、开定时与夜间模式、让某个应用单独走代理，慢慢玩。

## 下载

到 [Releases](https://github.com/liyuzhou79-star/TuxunDL/releases) 打开对应平台的最新发布页下载。**每个发布页都会同时放齐 Android、Windows、Linux、Mac 四个平台的最新安装包**——就算这次只更新了其中一个平台，其他平台的现行版本也会一并附上，不用翻旧页面找。

**各平台当前最新版本（直达发布页）：**

- Android：[1.7.3](https://github.com/liyuzhou79-star/TuxunDL/releases/tag/v1.7.3)
- Windows：[1.6.11](https://github.com/liyuzhou79-star/TuxunDL/releases/tag/win-v1.6.11)
- Linux：[1.1.1](https://github.com/liyuzhou79-star/TuxunDL/releases/tag/linux-v1.1.1)
- Mac：[1.0.1](https://github.com/liyuzhou79-star/TuxunDL/releases/tag/mac-v1.0.1)
- Android TV：[0.1.0 Beta](https://github.com/liyuzhou79-star/TuxunDL/releases/tag/tv-v0.1.0-beta)（预发布）

### Android

| 版本 | 文件 | 包名 | 适合 |
| --- | --- | --- | --- |
| 标准版 | `tuxundl-<版本>-arm64.apk` | `com.tuxundl.android` | 64 位新机型（最低 Android 5.0） |
| 老机型版 | `tuxundl-<版本>-legacy-armeabi-v7a.apk` | `com.tuxundl.android.legacy` | 32 位旧机型，可与标准版共存安装 |

App 内置检查更新（设置页手动检查，或有新版时打开 App 全屏提醒），会按你的版本自动挑选对应的安装包。所有版本都用同一把固定签名钥匙签署，可直接覆盖安装。

也可以从 [GitHub Packages](https://github.com/liyuzhou79-star/TuxunDL/pkgs) 取得每个版本的存档（需登录 GitHub 才能下载）。

### Windows

| 文件 | 适合 |
| --- | --- |
| `TuxunDL-<版本>.msi` | Windows 10 / 11，64 位（MSI 安装包） |
| `TuxunDL-<版本>.exe` | Windows 10 / 11，64 位（EXE 安装包） |

两个任选其一。程序内置 mihomo 核心，不需要另外下载。系统代理模式免管理员权限；TUN 模式需以管理员身份运行。Windows 版内置检查更新，只认 `win-v` 开头的 Windows 发布。

### Linux

| 文件 | 适合 |
| --- | --- |
| `TuxunDL-<版本>.deb` | Debian / Ubuntu 系，64 位（deb 安装包，装到 `/opt/com.tuxundl.linux`） |
| `TuxunDL-<版本>.AppImage` | 各主流发行版，64 位（免安装，下载后加可执行权限直接运行） |

与 Windows 版同一套桌面代码（版本号独立，从 1.0.0 起），程序内置 mihomo 核心。系统代理自动设置支持 GNOME 桌面（其他桌面请手动填 127.0.0.1:7890）；TUN 模式通过 pkexec 只把核心以 root 启动，程序本身不提权。Linux 版内置检查更新，只认 `linux-v` 开头的 Linux 发布。

### Mac

| 文件 | 适合 |
| --- | --- |
| `TuxunDL-<版本>.dmg` | macOS，Apple Silicon（M 系列芯片），64 位（DMG 磁盘映像） |

与 Windows / Linux 版同一套桌面代码（版本号独立，从 1.0.0 起），程序内置 mihomo 核心。系统代理自动设置走 `networksetup`（当前网络服务，一般是 Wi-Fi；需要管理员账号）；TUN 模式暂不支持。Mac 版内置检查更新，只认 `mac-v` 开头的 Mac 发布。未签名、未公证，首次打开需右键 → 打开绕过 Gatekeeper；Intel Mac 暂不支持。

## 功能总览

### Android 版

#### 连接与节点
- 订阅链接 / 配置文件导入，相册扫码加订阅
- 节点搜索、收藏置顶、延迟排序（未测沉底）
- 测速体验：全部测速时每个节点显示「测速中…」，超时红字标示，点延迟数字可单独重测
- 分流模式：规则 / 全局 / 直连一键切换
- 分应用代理：搜索 + 只看第三方应用

#### 流量统计
- 首页实时图表：最近 2 分钟平滑曲线 + 峰值标示
- 进阶模式（点统计卡开启）：近 24 小时每小时流量、连接时长、连接质量评级（平均延迟 / 抖动 / 失败率 / 卡顿次数）
- 每日与每周统计、本次连接收藏（把这次连接的节点、时长、流量、质量存成纪录卡）
- 订阅流量与到期提醒（用量 80% 警示、到期前 3 天提醒）

#### 个性化
- 吉祥物小机器人 + 连点彩蛋（累计流量 / 连接时长 / 使用天数）
- 大圆启停按钮四态动画：未连接呼吸、连接中旋转握手、已连接光环心跳、出错小晃
- 全 App 弹簧动画系统，逐元素专属调校；外观页可切「动画风格」：弹簧 / 柔和 / 减少动画
- 首页长按拖拽排版：砖块与卡片位置自己排
- 自定义主题色：16 个精选色 + HSL 三滑杆任意调色；深色模式；首页卡片开关

#### 进阶玩法
- 可视化分流规则编辑器（不用改订阅文件）
- 自定义 DNS 覆写（Nameserver / Fallback，可完整还原）
- 桌面小组件：一键启停、看当前节点与速率
- 设置备份还原：配置文件、规则、DNS、收藏、外观一键导出 / 还原
- 日志搜索与等级筛选

### Windows 版

- 首页大圆按钮启停，连接后实时显示当前节点与上下行速度
- 两种连接模式：系统代理 / TUN，设置页切换；本地混合端口可改
- 订阅管理：添加、更新、切换订阅，设置保存在本机
- 节点页：按分组看节点，点击切换，每个节点可单独测延迟
- 规则页与日志页：查看当前生效的规则、应用与核心日志
- 系统托盘：关窗口不退出，从系统托盘退出会自动恢复系统代理
- 检查更新只认 Windows 发布（`win-v` 标签），不会与 Android 版串扰

### Linux 版

- 与 Windows 版同一套桌面程序，功能一致：大圆按钮启停、订阅管理、节点分组与测速、规则与日志页、首次启动引导、数据目录可改
- 系统代理自动设置支持 GNOME 桌面（gsettings）；其他桌面环境可按设置页说明手动设置代理地址
- TUN 模式通过 pkexec 只把核心以 root 启动，程序本身不提权
- 开机自启走 XDG autostart；默认数据目录为 `~/.local/share/tuxundl`
- 检查更新只认 Linux 发布（`linux-v` 标签）

### Mac 版

- 与 Windows 版同一套桌面程序，功能一致：大圆按钮启停、订阅管理、节点分组与测速、规则与日志页、首次启动引导、数据目录可改
- 系统代理通过 `networksetup` 自动设置；开机自启走 LaunchAgent；默认数据目录为 `~/Library/Application Support/tuxundl`
- 目前仅支持 Apple Silicon；未签名、未公证，首次打开需右键 → 打开放行
- TUN 模式暂不支持，请先使用系统代理模式
- 检查更新只认 Mac 发布（`mac-v` 标签）

### Android TV 版（Beta）

- 与手机版同一套代码的电视分支，独立包名 `com.tuxundl.android.tv`，可与手机版共存安装，显示名同为 TuxunDL
- 同时支持 Google TV 与国内电视系统：不依赖 Google 服务，遥控器焦点操作，兼容 32 位与 64 位电视，安装包文件名带 Beta 字样
- 目前为 Beta 预发布：部分页面（如拖拽排序、图表手势）尚待遥控器适配；电视版暂不提供检查更新

## 版本一览

- **Android 标准版与老机型版**：功能相同，差别只在处理器架构与包名。每个 Android 版本都同时发布，不会有哪一版被漏掉。
- **Windows 版**：版本号独立，从 1.0.0 重新开始，发布标签用 `win-v<版本>`（Android 用 `v<版本>`）。
- **Linux 版**：版本号独立，从 1.0.0 开始，发布标签用 `linux-v<版本>`。
- **Mac 版**：版本号独立，从 1.0.0 开始，发布标签用 `mac-v<版本>`，目前只有 Apple Silicon 版。
- **Android TV 版（Beta）**：独立包名 `com.tuxundl.android.tv`，发布标签用 `tv-v<版本>-beta`，目前为预发布（Beta），安装包文件名带 Beta 字样。
- **四平台同页**：每个 GitHub 发布页都会同时附上四个平台的最新安装包。假如这次只更新了手机端，发布页里照样有电脑端的现行版本；反过来也一样。发布说明会写清这次更新的是哪个平台。

## 更新记录

完整版本历史见 [CHANGELOG.md](CHANGELOG.md)，每个版本的详细说明见对应的 [Release 页面](https://github.com/liyuzhou79-star/TuxunDL/releases)。

## 从源码编译

Android 版：

```bash
git clone https://github.com/liyuzhou79-star/TuxunDL.git
cd TuxunDL
./gradlew --no-daemon app:assembleAlphaRelease   # 标准版
./gradlew --no-daemon app:assembleAlphaRelease -PtuxundlLegacy  # 老机型版
```

需要 JDK 21、Android SDK 35 与 Go（核心编译）。

桌面版（独立的 Gradle 构建，在 `desktop/` 目录，与 Android 构建互不影响）：

```bash
./gradlew -p desktop run            # 运行
./gradlew -p desktop packageMsi     # 打 MSI 安装包（Windows）
./gradlew -p desktop packageExe     # 打 EXE 安装包（Windows）
```

需要 JDK 17。打包时会自动下载 mihomo 核心与 wintun 打进安装包（不入库）。详见 [desktop/README.md](desktop/README.md)。

本仓库的每个版本也都由 GitHub Actions 自动编译。

## 参与贡献

欢迎 issue 与 PR，请先看 [CONTRIBUTING.md](CONTRIBUTING.md)。安全问题请看 [SECURITY.md](SECURITY.md)。

## 贡献者

- [liyuzhou79-star](https://github.com/liyuzhou79-star)
- museai

## 致谢

- [mihomo](https://github.com/MetaCubeX/mihomo) — 代理核心（各平台共用）
- [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) — Android 版基础工程
- [Wintun](https://www.wintun.net) — Windows 版 TUN 模式所用的虚拟网卡驱动（© WireGuard LLC）
- 所有试用并回报问题的使用者

桌面版的桌面代码全部为本项目原创；上游与原创的边界在 [desktop/README.md](desktop/README.md) 里有详细说明。

## 授权

GPL-3.0。本项目仅供学习与个人使用，请遵守所在地法律法规。
