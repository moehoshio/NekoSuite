# 釣魚大賽（Fishing Contest）

- **指令**：`/ngame fishing [menu|...]`
- **管理器**：`FishingContestManager`
- **配置檔**：`fishing_contest_config.yml`
- **語言鍵前綴**：`ngame.fishing.*`
- **故事線**：休閒娛樂線（氛圍敘事）

---

## 故事設定

村莊碼頭的休閒競賽：玩家在限定時間內比拼釣獲，依魚種、稀有度與體型計分，名次決定獎勵。與主線無直接關聯，適合作為定時舉辦的社群活動。

---

## 玩法規則

- 限時釣魚競賽（`contest_duration`），到時自動結算。
- 分數依魚種基礎分 × 體型加成（隨機 1.0～`size_multiplier`）×（稀有魚另加 `rarity_multiplier`）。
- 提供排行榜與名次。
- 依名次（第一／二／三名與參與獎）發放獎勵。
- 支援自動開始（`auto_start`）與人數限制。

---

## 主要配置項（`fishing_contest_config.yml`）

| 項目 | 說明 |
|------|------|
| `game.enabled` | 是否啟用 |
| `game.contest_duration` | 比賽時長（秒，預設 600） |
| `game.min_players` / `max_players` | 最少 / 最多參與人數 |
| `game.auto_start` / `auto_start_interval` | 是否自動開始 / 間隔（秒） |
| `scoring.default_points` | 預設魚類分數 |
| `scoring.size_multiplier` / `rarity_multiplier` | 體型 / 稀有度加成倍率 |
| `scoring.fish.*` | 各魚種基礎分數（如 `COD`、`SALMON`、`TROPICAL_FISH`、`PUFFERFISH`） |
| `rewards.first_place` … `participation` | 各名次與參與獎的主控台指令 |

---

## 開發進度

- [x] 限時競賽流程
- [x] 依魚種 / 稀有度 / 體型計分
- [x] 排行榜與名次
- [x] 名次獎勵 + 參與獎
- [x] 自訂魚種與分數

## 待辦 / 延伸

- [ ] 自動開始排程的實戰驗證與廣播提示
- [ ] 賽季 / 累積積分榜
- [ ] 特殊「傳說魚」彩蛋與限定獎勵
