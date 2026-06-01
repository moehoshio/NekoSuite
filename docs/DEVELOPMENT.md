# 開發指南 (NekoSuite Bukkit)

本文件說明開發環境準備、程式架構與常見流程，協助在 Bukkit/Spigot 平台上維護與擴充 NekoSuite 模組。
**所有新增模組或子功能都必須遵守本指南的規範**，特別是「命令自定義」與「共用模組」兩節。

---

## 環境與依賴

- JDK 1.8（與 Spigot 1.16.5 相容）
- Maven 3.6+（本專案使用 Maven 建置）
- Spigot/Paper API 1.16.5（`provided` 依賴）
- Vault API 1.7（`provided`，經濟/權限）

安裝依賴後，執行 `mvn package -q` 產生 `target/nekosuite-bukkit-0.1.0-SNAPSHOT.jar`，並部署到測試伺服器的 `plugins/`。

---

## 專案結構

- `main/java/com/moehoshio/nekosuite/`：主要程式碼
  - `NekoSuitePlugin`：入口插件類，註冊指令/事件、載入各 Manager、集中執行所有 `onCommand` 派發。
  - `Messages`：多語系訊息處理（玩家語言、佔位符、`&#RRGGBB` 色碼、`getRaw` / `format` / `getList`）。
  - `MenuLayout` + `menu_layout.yml`：菜單尺寸、槽位、外框、導覽圖示集中定義。
  - `TabConfig` + `tab_config.yml`：可配置的多層 Tab 補全建議。
  - `CommandConfig` + `command_config.yml`：可配置的命令與子命令別名（**所有新指令必須在此登錄**）。
  - 模組管理器：`WishManager`、`EventManager`、`ExpManager`、`CdkManager`、`BuyManager`、`MailManager`、`TeleportManager`、`SkillManager`、`StrategyGameManager`、`RandomTeleportGameManager`、`SurvivalArenaManager`、`FishingContestManager`、`CardBattleManager`、`BlackjackManager`、`ArtifactRewardsManager`、`AnnouncementManager`、`JoinQuitManager`、`InventoryBackupManager`、`InventoryHistoryManager` 等。
- `main/resources/`：預設配置與語言檔，啟動時會以 `saveResource` 複製到插件資料夾。
- `target/`：編譯產物與複製出的資源（忽略，勿手動修改）。

---

## 命令自定義（必讀）

NekoSuite 的指令名稱與子命令名稱**完全可由伺服器擁有者透過配置調整**，避免與其他插件衝突或滿足多語/品牌化需求。所有現有模組與子功能（祈願/活動/經驗/商店/郵件/傳送/技能/策略遊戲/小遊戲/背包備份…）都已納入此機制。

### 架構

| 元件 | 角色 |
|------|------|
| `plugin.yml` | 宣告**正規（canonical）命令名稱**、預設用法、權限。此處名稱即為派發時 `command.getName()` 的回傳值，請勿任意更名。 |
| `main/resources/command_config.yml` | 為每個正規命令配置**額外別名**與**子命令別名（含巢狀）**。 |
| `CommandConfig` | 啟動時載入 `command_config.yml`，向 Bukkit `CommandMap` 註冊別名、提供子命令解析 API。 |
| `NekoSuitePlugin#onCommand` | 接收命令後，依 `CommandConfig.resolveSubIfKnown(...)` 把使用者輸入的子命令別名改寫為正規名稱，**因此各 `handleXxx` 內部仍可用既有的 `switch (sub) { case "menu": ... }` 等寫法**。 |

### 配置範例

```yaml
commands:
  exp:
    aliases: [experience]          # /experience 等同 /exp
    subcommands:
      deposit: [deposit, dep, save]
      withdraw: [withdraw, raw, take]
      pay: [pay, transfer, send]
      exchange: [exchange, ex, redeem]
  ngame:
    aliases: [game]
    subcommands:
      rtp: [rtp, randomtp]
    nested:                        # /ngame rtp <action>
      rtp:
        subcommands:
          start: [start, begin, go]
          end: [end, stop, finish]
```

說明：
- `aliases`：與正規命令並列的替代命令名；會經由反射註冊到 Bukkit 的 `CommandMap`，因此 `/qiyuan` 與 `/wish` 完全等價。
- `subcommands`：把使用者輸入的 token 映射為正規子命令名稱。正規鍵本身（如 `deposit`）一律有效，無須額外加入清單。
- `nested`：當某子命令還有自己的子層（例如 `/wish ticket add`、`/ngame rtp start`）時使用。命名空間以 `<command>.<subcommand>` 表示。

### 新增模組／子功能的必要步驟

1. 在 `plugin.yml` 宣告正規命令與權限（若是既有命令的子功能則略過）。
2. 在 `NekoSuitePlugin#onEnable` 為新命令呼叫 `setExecutor(this)` 與 `setTabCompleter(this)`。
3. **必須**在 `command_config.yml` 為新指令/新子命令補上 `subcommands` 條目；若有巢狀層級也須補 `nested`。預設清單至少包含一個正規鍵；別名可留空。
4. 在 `NekoSuitePlugin#onCommand` 的 `switch` 加上對應 `case`，並寫對應的 `handleXxx`。**在 `handleXxx` 內部請用既有的 `switch (args[0].toLowerCase())` 寫法**，因為框架已先正規化 `args[0]`。
5. 若有再下一層的派發（如本檔 `handleNekoGame` 中以 `commandConfig.resolveSubIfKnown("ngame." + gameType, subArgs[0])` 正規化內層子命令的做法），請套用同一模式，**不要**在 handler 裡直接硬編碼 `equals("start")` 等比對未經過 `CommandConfig` 解析。
6. 視需要在 `tab_config.yml` 補上 Tab 補全建議、在 `lang/*.yml` 補上對應的 `xxx.usage`、`xxx.success`、`xxx.failure` 等訊息鍵、並在 `menu_layout.yml` / `help/neko_help.yml` 補上對應的 GUI 條目。
7. `/nekoreload` 必須能熱重載新功能：所有狀態都應交由 `loadManagers()` 重建（這也會重建 `CommandConfig`）。

### 派發時的呼叫慣例

- 玩家專屬命令：開頭即檢查 `sender instanceof Player`，失敗時回覆 `messages.format(sender, "common.only_player")`。
- 權限：以 `nekosuite.<module>[.<action>]` 命名，並使用 `messages.format(sender, "common.no_permission")` 回覆缺失。
- 不要硬編碼中英文文案，使用 `Messages` 取得。
- 用 `try/catch` 包住經濟扣款／資料寫入，遇到異常以 `xxx.failure` 訊息鍵回覆。

---

## 共用模組與公共方法（NekoSuite 專屬）

下列工具屬於 NekoSuite 的**公共 API**，所有新模組與子功能都應優先使用而**不要自行重新實作**。

### `Messages`
- `format(CommandSender, String key)` / `format(..., Map<String,String> placeholders)`：取得已色碼化、佔位符替換後的單行訊息。
- `getRaw(CommandSender, String key)`：取得未色碼化的原文（適合進一步加工後再 `colorize`）。
- `getList(CommandSender, String key)`：取得 lore 陣列。
- `colorize(String)`：套用 `&` 與 `&#RRGGBB` 十六進位色碼。
- `setPlayerLanguage` / `getPlayerLanguage` / `getDefaultLanguage` / `getSupportedLanguages`：玩家語言偏好（儲存於 `userdata/`）。
- 物品翻譯：以 `items."minecraft:foo"` 鍵存放，搭配 `messages.format` 使用。

### `MenuLayout` + `menu_layout.yml`
- 集中定義所有 GUI 的尺寸、外框槽、導覽按鈕位置。**新菜單一律走 `MenuLayout`**，避免在 manager 中硬編碼槽位。
- Lore 約定：含 `ACTION:`、`COMMAND:`、`ID:`、`LANG:` 的字串會被 `NekoSuitePlugin` 的 `InventoryClickEvent` handler 解析；新菜單需沿用此格式，並建立對應 `InventoryHolder` 用以辨識點擊上下文。

### `TabConfig` + `tab_config.yml`
- 任意深度的 Tab 補全資料來源。新增命令時請在 `tab_config.yml` 補上 `_root` 提示；不要把字面提示寫死在 Java 程式碼。

### `CommandConfig` + `command_config.yml`
- 命令／子命令別名解析（見上節）。新增任何 `handle*` 之前先決定其正規子命令清單。
- API：
  - `resolveSub(namespace, input)`：找到別名回正規名稱；找不到則回 `input.toLowerCase()`。
  - `resolveSubIfKnown(namespace, input)`：找到別名回正規名稱；找不到回 `null`（適合判斷「使用者輸入是子命令還是資料」）。
  - `applyAliasRegistration(JavaPlugin)`：把 `aliases:` 列表註冊到 Bukkit `CommandMap`。

### Manager 慣例
- 每個 Manager 透過建構子接收 `(NekoSuitePlugin, Messages, File configFile, ...)`，必要時加入 `MenuLayout`、`Economy`、`Permission`。
- 多數模組以 `storage.data_dir`（預設 `userdata`）+ `YamlConfiguration` 儲存玩家資料，請用 manager 內既有的 `saveUserData` / `loadUserData` 助手，**勿自行 new YamlConfiguration**。
- 對外暴露的 API：在 `NekoSuitePlugin` 中以 `public XxxManager getXxxManager()` 形式提供，避免直接公開內部欄位。
- 在所有需要金錢的功能前先檢查 `economy != null`；缺失時用使用者面向訊息回覆並直接返回。

### 占位符約定
- 多數獎勵指令使用 `{player}` / `{id}` / `{amount}` 等占位符，由 manager 在送進主控台前替換。新功能延用相同占位符以方便伺服器擁有者複用模板。

---

## 小遊戲開發

NekoSuite 內建多個小遊戲，其**故事設定、玩法規則、配置項與各自的開發進度／待辦**集中記錄於 [`docs/minigames/`](minigames/README.md)，每個小遊戲擁有獨立文檔，可各自迭代補充。整體進度與剩餘工作索引見 [`docs/TODO.md`](TODO.md)。

- 共用架構基礎（Manager、命令分派、GUI、i18n、配置熱重載、經濟獎勵）與共用故事基礎：見 [`docs/minigames/README.md`](minigames/README.md)。
- 新增小遊戲時，請依該檔末段的「新增一個小遊戲的步驟」操作，並同步維護對應單檔文檔與 [`docs/TODO.md`](TODO.md)。

---

## 配置與國際化

- 配置檔位於 `main/resources/*.yml`，首次啟動會複製到插件資料夾；修改後須重載 `/nekoreload`。
- 語系：`language.yml` 控制使用語言，實際文本在 `lang/*.yml`；`Messages` 類處理格式化與佔位符替換。
- 菜單：`menu_layout.yml`、`help/neko_help.yml` 定義圖示位置/點擊行為。
- 其他模組配置：`wish_config.yml`、`event_config.yml`、`exp_config.yml`、`buy_config.yml`、`mail_config.yml`、`artifact_rewards_config.yml`、`random_teleport_config.yml`、`survival_arena_config.yml`、`fishing_contest_config.yml`、`card_battle_config.yml`、`blackjack_config.yml`、`inventory_backup_config.yml`、`command_config.yml`、`tab_config.yml` 等。

---

## 開發流程建議

1. **建立測試伺服器**：以 Paper 1.16.5 啟動，安裝 Vault 與經濟插件（如 EssentialsX + Vault）。
2. **同步資源**：每次調整 YAML 後，刪除伺服器內的 `plugins/NekoSuite/` 再重啟，或使用 `/nekoreload` 重載（注意：重載無法處理 plugin.yml 的命令註冊變更，新增命令需重啟）。
3. **調試指令**：確保指令與 Tab 補全行為一致；需要玩家物件的指令須檢查 `instanceof Player`。
4. **驗證命令自定義**：分別用正規名稱、配置別名、子命令別名測試你的新指令，確認三者都能進入相同 handler。
5. **權限驗證**：在非 OP 帳號測試指令與經濟扣款，確保 Vault 權限/經濟呼叫成功。
6. **日誌與錯誤**：使用 `getLogger()` 或回傳語系訊息回報原因；避免吞沒例外。

---

## 程式碼風格

- 依 JDK 8 編譯，避免使用 1.8 之後的 API（不可使用 `var`、`switch` 表達式、`record` 等）。
- 保持對於玩家/指令的前置檢查與錯誤訊息回報。
- 修改配置 key 時，務必同步更新對應 YAML、`Messages` 文案、Tab 補全與 GUI 定義。
- **絕不**直接在 handler 中字面比對中英文子命令（如 `args[0].equals("存入")`）—請改用 `command_config.yml` + `CommandConfig.resolveSub(...)` 處理多語別名。

---

## 發佈檢查

- `mvn package -q` 應產生無錯誤的 JAR。
- 在乾淨的測試伺服器上驗證：
  - Vault 經濟扣款、權限檢查。
  - 主選單、幫助 GUI 可正常開啟並返回。
  - 祈願/活動/經驗/商店/郵件/傳送/小遊戲/背包備份等核心指令能正常工作。
  - `command_config.yml` 內的別名能被正確路由（包含巢狀子命令）。
- 確認 `plugin.yml` 版本號與實際發佈一致。
