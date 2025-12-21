# NekoSuite Bukkit

NekoSuite 是一個面向生存/休閒伺服器的整合型 Bukkit 插件，提供祈願池、活動中心、經驗銀行、特權商店、郵件、傳送、技能與多種小遊戲等功能模組，並內建多語系與可視化菜單。

## 功能亮點
- 祈願池：可設定多個獎池，支援批量抽取與獎勵展示。
- 活動系統：玩家檢視/參與活動、領取獎勵；支援權限與條件檢查。
- 經驗銀行：存取、轉帳、兌換經驗，含菜單操作。
- 特權商店：依等級購買特權、支援 Vault 經濟。
- 郵件中心：玩家互寄或管理員模板群發，含 GUI 郵件盒。
- 傳送系統：玩家傳送請求、鎖定/解鎖、狀態查詢。
- 技能與小遊戲：策略遊戲、隨機傳送競賽、生存競技場、釣魚比賽等模組。
- 公告與主菜單：公告推送、導航菜單、幫助 GUI。
- 多語系：內建 zh_tw、zh_cn、en_us，透過 `/language` 切換。

## 環境需求
- Java 8 (JDK 1.8)
- Spigot/Paper 1.16.5 (基於 `spigot-api:1.16.5-R0.1-SNAPSHOT`)
- Vault (經濟/權限；標記為 softdepend，但經濟相關功能需要)

## 安裝
1. 在伺服器端執行 `mvn package -q` 產出 `target/nekosuite-bukkit-0.1.0-SNAPSHOT.jar`。
2. 將 JAR 放入伺服器的 `plugins/` 目錄，確保已安裝 Vault 及經濟插件。
3. 啟動伺服器，插件會在 `plugins/NekoSuite/` 產生預設配置與語言檔。

## 快速開始
- 主選單：`/neko` 或 `/nekomenu`
- 幫助 GUI：`/nekohelp`
- 祈願池：`/wish <池名> [次數]` 或 `/wish menu`
- 活動：`/event [活動ID]` 或 `/event menu`
- 經驗銀行：`/exp [menu|deposit|withdraw|pay|exchange]`
- 特權商店：`/buy <類型> <等級>` 或 `/buy menu`
- 郵件：`/mail [menu|claim|delete] [郵件ID]`
- 傳送：`/ntp <玩家>|accept|deny|toggle|cancel`

更多指令詳見 `main/resources/plugin.yml`。

## 配置與語系
- 主要配置位於 `main/resources/*.yml`，啟動後會複製到插件資料夾；常見檔案：
  - `wish_config.yml`、`event_config.yml`、`exp_config.yml`、`buy_config.yml`、`mail_config.yml`
  - `menu_layout.yml`、`tab_config.yml`、`announcements.yml`、`artifact_rewards_config.yml`
  - `random_teleport_config.yml`、`survival_arena_config.yml`、`fishing_contest_config.yml`
- 語言檔：`language.yml` 作為切換表，`lang/zh_tw.yml`、`lang/zh_cn.yml`、`lang/en_us.yml` 提供文本。
- 幫助菜單定義：`help/neko_help.yml` 描述 GUI 佈局與指令連結。

## 建置與測試
- 建置：`mvn package -q`
- 產物：`target/nekosuite-bukkit-0.1.0-SNAPSHOT.jar`
- 推薦在測試伺服器上以 OP 身分驗證各模組指令與 GUI 顯示，並檢查 Vault 經濟是否正常扣款。

## 授權
本專案遵循根目錄的 `LICENSE` 條款。
