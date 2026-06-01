# NekoSuite 開發進度與待辦（TODO）

本檔記錄 NekoSuite 的**整體進度、剩餘工作、故事與架構基礎**的索引。
小遊戲的細節（故事設定 / 玩法 / 配置 / 各自待辦）拆分至 [`docs/minigames/`](minigames/README.md)，可獨立維護。

> 規範：完成或新增任何功能時，請同步更新本清單與對應的單檔文檔。

---

## 架構基礎（已建立）

- [x] 入口 `NekoSuitePlugin`：集中註冊指令／事件、`loadManagers()` 統一載入各 Manager、集中 `onCommand` 派發。
- [x] 多語系 `Messages`（玩家語言、佔位符、`&#RRGGBB` 色碼），語系檔 `lang/zh_tw.yml`、`lang/zh_cn.yml`、`lang/en_us.yml`。
- [x] `MenuLayout` + `menu_layout.yml`：GUI 版面集中定義。
- [x] `CommandConfig` + `command_config.yml`：命令／子命令別名（含巢狀），**所有新指令必須登錄**。
- [x] `TabConfig` + `tab_config.yml`：可配置 Tab 補全。
- [x] `/nekoreload` 熱重載：狀態由 `loadManagers()` 重建。
- 詳見 [`docs/DEVELOPMENT.md`](DEVELOPMENT.md)。

---

## 核心模組（已實作）

- [x] 祈願 `WishManager` — `/wish`
- [x] 活動 `EventManager` — `/event`
- [x] 經驗銀行 `ExpManager` — `/exp`
- [x] CDK `CdkManager` — `/cdk`
- [x] 福利商店 `BuyManager` — `/buy`
- [x] 郵件 `MailManager` — `/mail`、`/mailsend`、`/mailadmin`
- [x] 傳送 `TeleportManager` — `/ntp`、`/ntpadmin`
- [x] 技能 `SkillManager` — `/skill`
- [x] 神器獎勵 `ArtifactRewardsManager` — `/artifact`
- [x] 公告 `AnnouncementManager` — `/announce`
- [x] 進出提示 `JoinQuitManager`
- [x] 背包備份 `InventoryBackupManager`（快照）＋ 背包歷史 `InventoryHistoryManager`（變更日誌 / 關鍵幀）

---

## 小遊戲（進度總覽）

各小遊戲的故事設定、玩法、配置與各自待辦見 [`docs/minigames/`](minigames/README.md)。

| 小遊戲 | 指令 | 狀態 | 文檔 |
|--------|------|------|------|
| 勇者傳說：終界之戰 | `/sgame` | ✅ 可玩（菜單戰鬥）；🔜 真實生怪戰鬥（plan-B） | [strategy-game.md](minigames/strategy-game.md) |
| 隨機傳送挑戰 | `/ngame rtp` | ✅ 可玩；🔜 真實權限移除需 Vault | [random-teleport.md](minigames/random-teleport.md) |
| 生存競技場 | `/ngame arena` | ✅ 可玩 | [survival-arena.md](minigames/survival-arena.md) |
| 釣魚大賽 | `/ngame fishing` | ✅ 可玩 | [fishing-contest.md](minigames/fishing-contest.md) |
| 超級卡牌對決 | `/ngame cardbattle` | ✅ 可玩（PvE / PvP） | [card-battle.md](minigames/card-battle.md) |
| 21 點 | `/ngame blackjack` | ✅ 可玩（需 Vault） | [blackjack.md](minigames/blackjack.md) |

---

## 剩餘工作（跨模組）

### 小遊戲重點待辦
- [ ] **策略遊戲**：實作「真實在世界中生成怪物」的戰鬥模式（plan-B，獨立 PR）。
- [ ] **隨機傳送**：以 Vault Permission API 完成遊戲期間的權限**實際移除與還原**（目前僅追蹤清單；替代方案為 `/ntpadmin lock`）。
- [ ] 各小遊戲的排行榜 / 歷史紀錄（生存競技場、釣魚、卡牌、21 點、策略遊戲共通需求）。

### 平台 / 整合
- [ ] `JoinQuitManager` 的權限新增／移除動作目前保留待 Vault 整合（程式碼已標註 reserved for future Vault integration）。
- [ ] 在地化覆蓋率檢查：以 `compare_lang.py` 驗證三語系鍵值無缺漏。

### 品質 / 發佈
- [ ] 在乾淨測試伺服器（Paper 1.16.5 + Vault + 經濟插件）驗證所有指令、別名（含巢狀子命令）與 GUI。
- [ ] `mvn package -q` 無錯誤產出 JAR，確認 `plugin.yml` 版本號與發佈一致。

---

## 如何擴充

- 新增模組／子功能：見 [`docs/DEVELOPMENT.md`](DEVELOPMENT.md)「命令自定義」與「共用模組」。
- 新增小遊戲：見 [`docs/minigames/README.md`](minigames/README.md) 末段的「新增一個小遊戲的步驟」。
