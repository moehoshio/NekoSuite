# 超級卡牌對決（Card Battle）

- **指令**：`/ngame cardbattle [menu|...]`
- **管理器**：`CardBattleManager`
- **配置檔**：`card_battle_config.yml`
- **語言鍵前綴**：`cardbattle.*`
- **故事線**：休閒娛樂線（酒館桌遊）

---

## 故事設定

酒館裡流行的桌上卡牌對決：玩家以一副卡牌進行回合制較量，可挑戰 AI（PvE）或其他玩家（PvP）。使用**虛擬生命**，不影響玩家真實血量，純屬娛樂競技。

---

## 玩法規則

- 在背包介面（chest menu）進行回合制卡牌戰鬥。
- 虛擬生命系統（不影響真實玩家血量）。
- 卡牌類型多樣：攻擊、防禦、治療、特殊效果。
- 法力（mana）系統：每回合回復並有上限，決定可出的卡。
- 手牌管理：每回合抽牌，有起始 / 上限手牌數。
- 模式：PvE（對 AI）與 PvP（玩家對戰）。
- 勝利時執行獎勵指令。

---

## 主要配置項（`card_battle_config.yml`）

| 項目 | 說明 |
|------|------|
| `game.enabled` | 是否啟用 |
| `game.starting_health` | 起始虛擬生命（預設 30） |
| `game.starting_hand_size` / `max_hand_size` | 起始 / 最大手牌數 |
| `game.draw_per_turn` | 每回合抽牌數 |
| `game.starting_mana` / `mana_per_turn` / `max_mana` | 法力起始 / 每回合回復 / 上限 |
| `game.turn_time_limit` | 每回合時間限制（秒） |
| `cards.*` | 卡牌定義：`name`/`description`（語言鍵）、`type`、`mana_cost`、`value`、`material` |
| `rewards.win_commands` | 勝利時執行的主控台指令 |

> 卡牌名稱與描述以語言鍵儲存（如 `cardbattle.cards.slash.name`），由 `Messages` 翻譯。

---

## 開發進度

- [x] 回合制戰鬥框架（背包介面）
- [x] 虛擬生命 / 法力 / 手牌系統
- [x] 多類型卡牌（攻擊 / 防禦 / 治療 / 特殊）
- [x] PvE（AI）與 PvP 模式
- [x] 勝利獎勵

## 待辦 / 延伸

- [ ] 卡組自訂 / 牌組收集系統
- [ ] 更豐富的 AI 策略
- [ ] 對戰天梯 / 段位
