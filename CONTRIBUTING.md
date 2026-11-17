# 參與貢獻 / Contributing

歡迎各種形式的參與：回報問題、提功能建議、送 Pull Request。

## 回報問題

請用 issue 模板，盡量附上：

- TuxunDL 版本（關於頁可看）、標準版還是老機型版
- 手機型號與 Android 版本
- 重現步驟、期望結果與實際結果
- 相關截圖或日誌（設定 → 日誌，可先搜尋篩選後再截）

## 功能建議

先搜一下既有 issue 有沒有人提過。描述時請說明使用情境：你想解決什麼問題，而不是只要某個按鈕。

## Pull Request

1. Fork 後從 `main` 開新分支
2. 改動原則（本專案的紅線）：
   - 不改 `core/` 與 `service/clash/` 的核心邏輯
   - 不改 Binder 介面簽名（`IClashManager` / `IProfileManager`）
   - UI 狀態從既有 `queryTunnelState()` / `queryTrafficTotal()` 等介面讀取
3. 新增文案請同時補上 `values`、`values-zh-rTW`、`values-zh` 三份字串；英文文案裡的撇號記得轉義（`\'`）
4. 版本號在根目錄 `build.gradle.kts`，由維護者統一調整，PR 裡請不要改
5. 提交前請確認 GitHub Actions 的 Build Debug 能通過

## 風格

- 介面：Kotlin + Jetpack Compose + Material 3，動畫請沿用 `ui/TuxunAnim.kt` 的地基與動畫風格三檔
- 註解與提交訊息用中文或英文都可以，寫清楚「為什麼」比「改了什麼」重要
