<p align="center">
  <img src="docs/assets/icon.webp" width="120" alt="TuxunDL icon">
</p>

<h1 align="center">TuxunDL</h1>

<p align="center">
  高自訂、強個性化的開源代理工具，Android 與 Windows 雙平台，基於 mihomo 核心。<br>
  An open-source, highly customizable proxy client for Android and Windows, powered by the mihomo core.
</p>

<p align="center">
  <a href="https://github.com/liyuzhou79-star/TuxunDL/releases"><img src="https://img.shields.io/github/v/release/liyuzhou79-star/TuxunDL" alt="Latest release"></a>
  <a href="https://github.com/liyuzhou79-star/TuxunDL/releases"><img src="https://img.shields.io/github/downloads/liyuzhou79-star/TuxunDL/total" alt="Downloads"></a>
  <a href="https://github.com/liyuzhou79-star/TuxunDL/actions/workflows/build-debug.yaml"><img src="https://img.shields.io/github/actions/workflow/status/liyuzhou79-star/TuxunDL/build-debug.yaml?branch=main&label=build" alt="Build status"></a>
  <img src="https://img.shields.io/badge/Android-5.0%2B-3DDC84" alt="Android 5.0+">
  <img src="https://img.shields.io/badge/Windows-10%2F11-0078D6" alt="Windows 10/11">
  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF" alt="Kotlin">
  <img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="GPL-3.0">
  <a href="https://github.com/liyuzhou79-star/TuxunDL/commits/main"><img src="https://img.shields.io/github/last-commit/liyuzhou79-star/TuxunDL" alt="Last commit"></a>
</p>

<p align="center">
  <a href="https://github.com/liyuzhou79-star/TuxunDL/releases/latest"><b>下載最新版 / Download</b></a> ·
  <a href="#功能總覽--features">功能</a> ·
  <a href="#版本一覽--editions">版本一覽</a> ·
  <a href="#更新紀錄--changelog">更新紀錄</a> ·
  <a href="CONTRIBUTING.md">參與貢獻</a>
</p>

---

<p align="center">
  <img src="docs/assets/screenshot-home.png" width="300" alt="TuxunDL home screen">
</p>

## 這是什麼 / About

TuxunDL 是一個完全開源（GPL-3.0）的代理客戶端，核心採用 [mihomo](https://github.com/MetaCubeX/mihomo)，有 **Android 版**和 **Windows 版**兩個版本，在同一個倉庫裡開發。主打一個詞：**自訂**——主題色、首頁排版、卡片開關、節點收藏、規則編輯、DNS 覆寫，能自己改的全都開放給你改。

Android 版介面以 Kotlin + Jetpack Compose + Material 3 打造，吉祥物是天線連著節點的小機器人；Windows 版是全新編寫的桌面程序（程式碼在 `desktop/` 目錄，全部原創），吉祥物換成窗戶造型，內核同樣是 mihomo，安裝包已內置核心，裝完即用。

## 下載 / Download

到 [Releases](https://github.com/liyuzhou79-star/TuxunDL/releases/latest) 打開最新發布頁下載。**每個發布頁都會同時放齊 Android 和 Windows 兩個平台的最新安裝包**——就算這次只更新了其中一個平台，另一個平台的現行版本也會一併附上，不用翻舊頁面找。

### Android

| 版本 | 檔案 | 包名 | 適合 |
| --- | --- | --- | --- |
| 標準版 | `tuxundl-<版本>-arm64.apk` | `com.tuxundl.android` | 64 位元新機型（最低 Android 5.0） |
| 老機型版 | `tuxundl-<版本>-legacy-armeabi-v7a.apk` | `com.tuxundl.android.legacy` | 32 位元舊機型，可與標準版共存安裝 |

App 內建檢查更新（設定頁手動檢查，或有新版時開啟 App 全屏提醒），會依你的版本自動挑對的安裝包。所有版本都用同一把固定簽名鑰匙簽署，可直接覆蓋安裝。

也可以從 [GitHub Packages](https://github.com/liyuzhou79-star/TuxunDL/pkgs) 取得每個版本的封存（需登入 GitHub 才能下載）。

### Windows

| 檔案 | 適合 |
| --- | --- |
| `TuxunDL-<版本>.msi` | Windows 10 / 11，64 位元（MSI 安裝包） |
| `TuxunDL-<版本>.exe` | Windows 10 / 11，64 位元（EXE 安裝包） |

兩個任選其一。程序內置 mihomo 核心，不需要另外下載。系統代理模式免管理員權限；TUN 模式需以管理員身份運行。Windows 版內建檢查更新，只認 `win-v` 開頭的 Windows 發布。

## 功能總覽 / Features

### Android 版

#### 連接與節點
- 訂閱連結 / 設定檔匯入，相簿掃碼加訂閱
- 節點搜尋、收藏置頂、延遲排序（未測沉底）
- 測速體驗：全部測速時每個節點顯示「測速中…」，超時紅字標示，點延遲數字可單獨重測
- 分流模式：規則 / 全域 / 直連一鍵切換
- 分應用代理：搜尋＋只看第三方應用

#### 流量統計
- 首頁即時圖表：最近 2 分鐘平滑曲線＋峰值標示
- 進階模式（點統計卡開啟）：近 24 小時每小時流量、連接時長、連接品質評級（平均延遲 / 抖動 / 失敗率 / 卡頓次數）
- 每日與每週統計、本次連接收藏（把這次連接的節點、時長、流量、品質存成紀錄卡）
- 訂閱流量與到期提醒（用量 80% 警示、到期前 3 天提醒）

#### 個性化
- 吉祥物小機器人＋連點彩蛋（累計流量 / 連接時長 / 使用天數）
- 大圓啟停按鈕四態動畫：未連接呼吸、連接中旋轉握手、已連接光環心跳、出錯小晃
- 全 App 彈簧動畫系統，逐元素專屬調校；外觀頁可切「動畫風格」：彈簧 / 柔和 / 減少動畫
- 首頁長按拖拽排版：磚塊與卡片位置自己排
- 自訂主題色：16 個精選色＋HSL 三滑桿任意調色；深色模式；首頁卡片開關

#### 進階玩法
- 可視化分流規則編輯器（不用改訂閱檔）
- 自訂 DNS 覆寫（Nameserver / Fallback，可完整還原）
- 桌面小組件：一鍵啟停、看目前節點與速率
- 設定備份還原：設定檔、規則、DNS、收藏、外觀一鍵匯出 / 還原
- 日誌搜尋與等級篩選

### Windows 版

- 首頁大圓按鈕啟停，連接後即時顯示目前節點與上下行速度
- 兩種連接模式：系統代理 / TUN，設定頁切換；本地混合端口可改
- 訂閱管理：添加、更新、切換訂閱，設定保存在本機
- 節點頁：按分組看節點，點擊切換，每個節點可單獨測延遲
- 規則頁與日誌頁：查看目前生效的規則、應用與核心日誌
- 系統匣：關窗口不退出，從系統匣退出會自動恢復系統代理
- 檢查更新只認 Windows 發布（`win-v` 標籤），不會與 Android 版串擾

## 版本一覽 / Editions

- **Android 標準版與老機型版**：功能相同，差別只在處理器架構與包名。每個 Android 版本都同時發布，不會有哪一版被漏掉。
- **Windows 版**：版本號獨立，從 1.0.0 重新開始，發布標籤用 `win-v<版本>`（Android 用 `v<版本>`）。
- **雙平台同頁**：每個 GitHub 發布頁都會同時附上兩個平台的最新安裝包。假如這次只更新了手機端，發布頁裡照樣有電腦端的現行版本；反過來也一樣。發布說明會寫清這次更新的是哪個平台。

## 更新紀錄 / Changelog

完整版本歷史見 [CHANGELOG.md](CHANGELOG.md)，每個版本的詳細說明見對應的 [Release 頁面](https://github.com/liyuzhou79-star/TuxunDL/releases)。

## 從原始碼編譯 / Build

Android 版：

```bash
git clone https://github.com/liyuzhou79-star/TuxunDL.git
cd TuxunDL
./gradlew --no-daemon app:assembleAlphaRelease   # 標準版
./gradlew --no-daemon app:assembleAlphaRelease -PtuxundlLegacy  # 老機型版
```

需要 JDK 21、Android SDK 35 與 Go（核心編譯）。

Windows 版（獨立的 Gradle 構建，在 `desktop/` 目錄，與 Android 構建互不影響）：

```bash
./gradlew -p desktop run            # 運行
./gradlew -p desktop packageMsi     # 打 MSI 安裝包
./gradlew -p desktop packageExe     # 打 EXE 安裝包
```

需要 JDK 17。打包時會自動下載 mihomo 核心與 wintun 打進安裝包（不入庫）。詳見 [desktop/README.md](desktop/README.md)。

本倉庫的每個版本也都由 GitHub Actions 自動編譯。

## 參與貢獻 / Contributing

歡迎 issue 與 PR，請先看 [CONTRIBUTING.md](CONTRIBUTING.md)。安全問題請看 [SECURITY.md](SECURITY.md)。

## 貢獻者 / Contributors

- [liyuzhou79-star](https://github.com/liyuzhou79-star)
- museai

## 致謝 / Credits

- [mihomo](https://github.com/MetaCubeX/mihomo) — 代理核心（Android 與 Windows 版共用）
- [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) — Android 版基礎工程
- [Wintun](https://www.wintun.net) — Windows 版 TUN 模式所用的虛擬網卡驅動（© WireGuard LLC）
- 所有試用並回報問題的使用者

Windows 版的桌面程式碼全部為本專案原創；上游與原創的邊界在 [desktop/README.md](desktop/README.md) 裡有詳細說明。

## 授權 / License

GPL-3.0。本專案僅供學習與個人使用，請遵守所在地法律法規。
