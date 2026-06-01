# 21 點 / Blackjack

- **指令**：`/ngame blackjack [menu|...]`
- **管理器**：`BlackjackManager`
- **配置檔**：`blackjack_config.yml`
- **語言鍵前綴**：`blackjack.*`
- **故事線**：休閒娛樂線（酒館賭桌）

---

## 故事設定

酒館賭桌上的經典紙牌遊戲：玩家以遊戲內貨幣下注，與莊家比拼點數。輕鬆的博弈娛樂，與主線無關聯。

---

## 玩法規則

- 標準 21 點規則：要牌（hit）、停牌（stand）、加倍（double down）。
- 以遊戲內貨幣虛擬下注，提供多種下注額。
- 莊家 AI 依賭場標準規則行動：16 點以下要牌、17 點以上停牌。
- Blackjack 賠 3:2，一般獲勝賠 1:1，平手（push）退回賭注。
- **需要 Vault 經濟**：下注與結算前須確認 `economy != null`。

---

## 主要配置項（`blackjack_config.yml`）

| 項目 | 說明 |
|------|------|
| `game.enabled` | 是否啟用 |
| `game.min_bet` / `max_bet` | 最小 / 最大下注額 |
| `game.blackjack_multiplier` | Blackjack 賠率倍率（預設 1.5，即 3:2） |
| `game.bet_options` | 可選下注額列表 |
| `rewards.win_commands` | 獲勝時執行的主控台指令（支援 `{player}`、`{amount}`，`{amount}` 為淨利不含本金） |

---

## 開發進度

- [x] 標準 21 點規則（hit / stand / double down）
- [x] 虛擬下注與多種下注額
- [x] 莊家 AI（16 要牌、17 停牌）
- [x] 賠率結算（Blackjack 3:2、一般 1:1、平手退注）
- [x] Vault 經濟整合

## 待辦 / 延伸

- [ ] 分牌（split）與保險（insurance）進階規則
- [ ] 連勝 / 統計紀錄
- [ ] 多人同桌
