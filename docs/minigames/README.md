# 小遊戲開發總覽（NekoSuite Minigames）

本目錄集中記錄 NekoSuite 各小遊戲的**故事設定、玩法規則、配置項與開發進度**。
每個小遊戲擁有獨立的文檔，可各自迭代與補充，不需要互相耦合：

> 規範：新增或調整任何小遊戲時，請同步維護本目錄對應的單檔文檔與根目錄 [`docs/TODO.md`](../TODO.md) 的進度清單。

## 小遊戲一覽

| 小遊戲 | 指令 | 模組管理器 | 配置檔 | 文檔 |
|--------|------|-----------|--------|------|
| 勇者傳說：終界之戰（策略冒險） | `/sgame` | `StrategyGameManager` | `strategy_game_config.yml` | [strategy-game.md](strategy-game.md) |
| 隨機傳送挑戰 | `/ngame rtp` | `RandomTeleportGameManager` | `random_teleport_config.yml` | [random-teleport.md](random-teleport.md) |
| 生存競技場 | `/ngame arena` | `SurvivalArenaManager` | `survival_arena_config.yml` | [survival-arena.md](survival-arena.md) |
| 釣魚大賽 | `/ngame fishing` | `FishingContestManager` | `fishing_contest_config.yml` | [fishing-contest.md](fishing-contest.md) |
| 超級卡牌對決 | `/ngame cardbattle` | `CardBattleManager` | `card_battle_config.yml` | [card-battle.md](card-battle.md) |
| 21 點（Blackjack） | `/ngame blackjack` | `BlackjackManager` | `blackjack_config.yml` | [blackjack.md](blackjack.md) |

`/sgame` 為獨立正規命令；其餘小遊戲統一掛載於 `/ngame` 之下，並以巢狀子命令分派（見 `command_config.yml` 的 `ngame.nested`）。

---

## 共用架構基礎

所有小遊戲都遵循 NekoSuite 的共用約定（詳見 [`docs/DEVELOPMENT.md`](../DEVELOPMENT.md)）。以下為小遊戲特別需要遵守的基礎設施。

### 1. 模組管理器（Manager）

- 每個小遊戲一個 `XxxManager`，由 `NekoSuitePlugin#loadManagers()` 於啟動／`/nekoreload` 時建立。
- 建構子接收 `(NekoSuitePlugin/ JavaPlugin, Messages, File configFile[, MenuLayout][, Economy])`。
- 對外以 `public XxxManager getXxxManager()` 暴露，禁止公開內部欄位。
- 遊戲進行中的狀態（session）保存在記憶體（如 `Map<UUID, GameSession>`），玩家持久化資料走 `storage.data_dir`（預設 `userdata/<player>.yml`）。

### 2. 命令分派

- `/sgame` 在 `NekoSuitePlugin#onCommand` 的 `switch` 直接有 `case`。
- `/ngame <game> <sub>` 由 `handleNekoGame` 先以 `commandConfig.resolveSubIfKnown("ngame", token)` 正規化遊戲類型，再以 `resolveSubIfKnown("ngame." + gameType, subArgs[0])` 正規化內層子命令。
- **不要**在 handler 內硬編碼 `equals("start")`；一律透過 `CommandConfig` 解析（見 `command_config.yml` 的 `ngame.subcommands` 與 `ngame.nested`）。

### 3. GUI 菜單

- 所有以聊天箱／背包介面呈現的玩法，槽位、外框、導覽按鈕都集中定義在 `menu_layout.yml`，透過 `MenuLayout` 取得；不要在 manager 內硬編碼槽位。
- 點擊行為以 lore 中的 `ACTION:` / `COMMAND:` / `ID:` / `LANG:` 標記驅動，並建立對應 `InventoryHolder` 用以辨識點擊上下文。

### 4. 國際化（i18n）

- 任何面向玩家的文字都必須走 `Messages`（`format` / `getRaw` / `getList`），鍵值放在 `lang/zh_tw.yml`、`lang/zh_cn.yml`、`lang/en_us.yml`。
- 卡牌、事件、敵人、商店等內容以**語言鍵**（如 `cardbattle.cards.slash.name`）儲存於配置，再由 manager 經 `Messages` 翻譯。
- 新增字串時三個語系必須同步補齊；可用根目錄 `compare_lang.py` 檢查缺漏。

### 5. 配置與熱重載

- 配置檔位於 `main/resources/*.yml`，首次啟動以 `saveResource` 複製到插件資料夾。
- 每個小遊戲配置頂層都有 `game.enabled` 開關。
- `/nekoreload` 必須能重建所有狀態（重新 `loadManagers()`，連帶重建 `CommandConfig`）。

### 6. 經濟與獎勵

- 涉及金錢的玩法（如 Blackjack 下注）必須先檢查 `economy != null`，缺失時以使用者面向訊息回覆並返回。
- 結算獎勵統一以「執行主控台指令」方式發放，模板使用 `{player}` / `{amount}` / `{id}` 等占位符，由 manager 替換後送進主控台。

---

## 共用故事基礎（World Foundation）

NekoSuite 的小遊戲共享一個輕量的「貓界（NekoSuite）」休閒世界觀，各小遊戲可在其下自由延伸，不強制統一時間線：

- **策略冒險線**：以《勇者傳說：終界之戰》為主線敘事，發生在破碎的大陸 **Aldoria**，封印終界龍（End Dragon）的封印崩裂，玩家化身年輕勇者踏上討伐之旅。此線承載完整章節劇情。
- **競技挑戰線**：生存競技場、隨機傳送挑戰屬於「試煉場」性質，強調操作與生存，敘事輕量。
- **休閒娛樂線**：釣魚大賽、21 點、卡牌對決屬於村莊／酒館的休閒活動，敘事以氛圍為主。

> 撰寫新小遊戲的故事時，建議在對應單檔的「故事設定」一節說明它屬於上述哪條線，以及與主線的關聯（可以是「無直接關聯的獨立活動」）。

---

## 新增一個小遊戲的步驟（checklist）

1. 建立 `XxxManager`，於 `NekoSuitePlugin#loadManagers()` 註冊並提供 getter。
2. 決定指令形式：獨立命令（更新 `plugin.yml`）或掛載於 `/ngame`（更新 `command_config.yml` 的 `ngame.subcommands` / `ngame.nested`）。
3. 新增 `xxx_config.yml`，至少包含 `game.enabled` 與獎勵區塊。
4. 在三個 `lang/*.yml` 補齊所有面向玩家的字串鍵。
5. 若有 GUI，於 `menu_layout.yml` 定義版面，並在 `tab_config.yml` 補 Tab 補全。
6. 在本目錄新增 `xxx.md`（故事設定 / 玩法規則 / 配置項 / 進度 / 待辦），並登錄到上方「小遊戲一覽」表格。
7. 更新 [`docs/TODO.md`](../TODO.md) 的進度清單。
