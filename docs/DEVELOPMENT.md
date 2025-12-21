# 開發指南 (NekoSuite Bukkit)

本文件說明開發環境準備、程式架構與常見流程，協助在 Bukkit/Spigot 平台上維護與擴充 NekoSuite 模組。

## 環境與依賴
- JDK 1.8（與 Spigot 1.16.5 相容）
- Maven 3.6+（本專案使用 Maven 建置）
- Spigot/Paper API 1.16.5（`provided` 依賴）
- Vault API 1.7（`provided`，經濟/權限）

安裝依賴後，執行 `mvn package -q` 產生 `target/nekosuite-bukkit-0.1.0-SNAPSHOT.jar`，並部署到測試伺服器的 `plugins/`。

## 專案結構
- `main/java/com/moehoshio/nekosuite`：主要程式碼
  - `NekoSuitePlugin`：入口插件類，註冊指令/事件並載入各 Manager。
  - `Messages`：多語系訊息處理。
  - `MenuLayout`：菜單定義與渲染。
  - 模組管理器：`WishManager` (祈願)、`EventManager`、`ExpManager`、`CdkManager`、`BuyManager`、`MailManager`、`TeleportManager`、`SkillManager`、`StrategyGameManager`、`RandomTeleportGameManager`、`SurvivalArenaManager`、`FishingContestManager`、`ArtifactRewardsManager`、`AnnouncementManager`、`JoinQuitManager`、`TabConfig` 等。
- `main/resources`：預設配置與語言檔，啟動時會以 `saveResource` 複製到插件資料夾。
- `help/neko_help.yml`：幫助 GUI 配置。
- `target/`：編譯產物與複製出的資源（忽略，勿手動修改）。

## 指令與權限
指令在 `main/resources/plugin.yml` 宣告。開發時若新增指令，需同時：
1) 在 `NekoSuitePlugin#onEnable` 註冊 `setExecutor` / `setTabCompleter`。
2) 在 `plugin.yml` 加入 `commands` 配置並設定 `permission`。
3) 視需要在 `help/neko_help.yml` 加入 GUI 條目。

常用指令示例：
- `/wish <pool> [count]`、`/wish menu`
- `/event [id]`、`/event menu`
- `/exp [menu|deposit|withdraw|pay|exchange]`
- `/buy <type> <level>`、`/buy menu`
- `/mail [menu|claim|delete] [mailId]`、`/mailsend <player> <subject> [content]`
- `/ntp <player|accept|deny|toggle|cancel>`、`/ntpadmin <lock|unlock|status> <player>`
- `/skill [list|info <id>]`
- `/ngame <rtp|arena|fishing> [subcommand]`

## 配置與國際化
- 配置檔位於 `main/resources/*.yml`，首次啟動會複製到插件資料夾；修改後須重載 `/nekoreload`。
- 語系：`language.yml` 控制使用語言，實際文本在 `lang/*.yml`；`Messages` 類處理格式化與佔位符替換。
- 菜單：`menu_layout.yml`、`help/neko_help.yml` 定義圖示位置/點擊行為。
- 其他模組配置：`wish_config.yml`、`event_config.yml`、`exp_config.yml`、`buy_config.yml`、`mail_config.yml`、`artifact_rewards_config.yml`、`random_teleport_config.yml`、`survival_arena_config.yml`、`fishing_contest_config.yml` 等。

## 開發流程建議
1. **建立測試伺服器**：以 Paper 1.16.5 啟動，安裝 Vault 與經濟插件（如 EssentialsX + Vault）。
2. **同步資源**：每次調整 YAML 後，刪除伺服器內的 `plugins/NekoSuite/` 再重啟，或使用 `/nekoreload` 重載（留意：重載無法處理所有變更）。
3. **調試指令**：確保指令與 TabComplete 行為一致；需要玩家物件的指令須檢查 `instanceof Player`。
4. **權限驗證**：在非 OP 帳號測試指令與經濟扣款，確保 Vault 權限/經濟呼叫成功。
5. **日誌與錯誤**：使用 `getLogger()` 或傳回語系訊息回報原因；避免吞沒例外。

## 程式碼風格
- 依 JDK 8 編譯，避免使用 1.8 之後的 API。
- 保持對於玩家/指令的前置檢查與錯誤訊息回報。
- 修改配置 key 時，務必同步更新對應 YAML、`Messages` 文案與 GUI 定義。

## 發佈檢查
- `mvn package -q` 應產生無錯誤的 JAR。
- 在乾淨的測試伺服器上驗證：
  - Vault 經濟扣款、權限檢查。
  - 主選單、幫助 GUI 可正常開啟並返回。
  - 祈願/活動/經驗/商店/郵件/傳送/小遊戲等核心指令能正常工作。
- 確認 `plugin.yml` 版本號與實際發佈一致。
