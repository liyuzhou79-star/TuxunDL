## TuxunDL 1.0.0（正式版）

TuxunDL 是基於 [mihomo](https://github.com/MetaCubeX/mihomo) 核心的開源 Android 代理工具，完全開源（GPL-3.0）。1.0.0 是第一個正式版：功能完整、介面自訂程度高，適合日常使用。

TuxunDL is an open-source Android proxy client based on the mihomo core (GPL-3.0). Version 1.0.0 is the first official stable release.

### 下載 / Download

請到 [Releases](https://github.com/liyuzhou79-star/TuxunDL/releases) 下載最新版：

- 標準版（arm64 新機型）：`tuxundl-1.0.0-arm64-v8a.apk`（包名 `com.tuxundl.android`）
- 老機型版（32 位舊機型）：`tuxundl-1.0.0-legacy-armeabi-v7a.apk`（包名 `com.tuxundl.android.legacy`，可與標準版共存）

兩個版本同版本號、同一個發布頁；App 內的檢查更新會按你的版本自動挑對的安裝包。

### 主要功能 / Features

- 首頁流量監控：即時速率折線圖、總轉發流量、連接時長、每日流量統計
- 訂閱流量顯示：首頁直接看已用 / 總量 / 到期日
- 分流規則編輯器：不用改訂閱檔，直接在 App 裡寫規則
- 自訂 DNS：Nameserver / Fallback 自由填
- 節點收藏置頂、節點搜尋、只顯示第三方應用
- 桌面小組件：一鍵啟停、看目前節點
- 自訂主題色：12 預設色＋任意顏色調色器，卡片開關與排序自訂
- 備份還原：設定檔、規則、DNS、收藏與外觀一鍵匯出 / 還原
- 檢查更新：新版發布後首頁自動提醒
- 相簿掃碼、首次免責聲明、深色模式

### Requirement

- Android 5.0+ (minimum)
- Android 7.0+ (recommend)
- `armeabi-v7a` , `arm64-v8a`, `x86` or `x86_64` Architecture

### Build

1. Update submodules

   ```bash
   git submodule update --init --recursive
   ```

2. Install **OpenJDK 11**, **Android SDK**, **CMake** and **Golang**

3. Create `local.properties` in project root with

   ```properties
   sdk.dir=/path/to/android-sdk
   ```

4. (Optional) Custom app package name. Add the following configuration to `local.properties`.

   ```properties
   # config your own applicationId, or it will be 'com.tuxundl.android'
   custom.application.id=com.my.compile.clash

5. Create `signing.properties` in project root with

   ```properties
   keystore.path=/path/to/keystore/file
   keystore.password=<key store password>
   key.alias=<key alias>
   key.password=<key password>
   ```

6. Build

   ```bash
   ./gradlew app:assembleAlphaRelease
   ```

### Automation

APP package name is `com.tuxundl.android`

- Toggle Clash.Meta service status
  - Send intent to activity `com.tuxundl.android.ExternalControlActivity` with action `com.tuxundl.android.action.TOGGLE_CLASH`
- Start Clash.Meta service
  - Send intent to activity `com.tuxundl.android.ExternalControlActivity` with action `com.tuxundl.android.action.START_CLASH`
- Stop Clash.Meta service
  - Send intent to activity `com.tuxundl.android.ExternalControlActivity` with action `com.tuxundl.android.action.STOP_CLASH`
- Import a profile
  - URL Scheme `tuxundl://install-config?url=<encoded URI>`, `clash://install-config?url=<encoded URI>` or `clashmeta://install-config?url=<encoded URI>`

### Contribution and Project Maintenance

#### Meta Kernel

- CMFA uses the kernel from `android-real` branch under `MetaCubeX/Clash.Meta`, which is a merge of the main `Alpha` branch and `android-open`.
  - If you want to contribute to the kernel, make PRs to `Alpha` branch of the Meta kernel repository.
  - If you want to contribute Android-specific patches to the kernel, make PRs to  `android-open` branch of the Meta kernel repository.

#### Maintenance

- When `MetaCubeX/Clash.Meta` kernel is updated to a new version, the `Update Dependencies` actions in this repo will be triggered automatically.
  - It will pull the new version of the meta kernel, update all the golang dependencies, and create a PR without manual intervention.
  - If there is any compile error in PR, you need to fix it before merging. Alternatively, you may merge the PR directly.
- Manually triggering `Build Pre-Release` actions will compile and publish a `PreRelease` version.
- Manually triggering `Build Release` actions will compile, tag and publish a `Release` version.
  - You must fill the blank `Release Tag` with the tag you want to release in the format of `v1.2.3`.
  - `versionName` and `versionCode` in `build.gradle.kts` will be automatically bumped to the tag you filled above.
