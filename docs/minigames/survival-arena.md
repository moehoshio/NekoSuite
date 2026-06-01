# 生存競技場（Survival Arena）

- **指令**：`/ngame arena [menu|start|status]`
- **管理器**：`SurvivalArenaManager`
- **配置檔**：`survival_arena_config.yml`
- **語言鍵前綴**：`ngame.arena.*`
- **故事線**：競技挑戰線（輕量敘事）

---

## 故事設定

「試煉場」性質的波次防守挑戰：勇者在競技場中迎戰一波又一波逐漸增強的怪物，考驗戰鬥與生存能力。可作為策略冒險線之外的純操作試煉。

---

## 玩法規則

- 多波（`max_waves`）怪物依配置生成，難度逐波提升（生命／傷害倍率遞增）。
- 在競技場半徑內作戰；每波之間有冷卻時間。
- 擊殺得分 + 每波完成的額外分數；超過 `wave_timeout` 則失敗。
- 支援多人遊玩；可選擇讓怪物發光以便辨識目標。
- 結束依分數結算獎勵。

---

## 主要配置項（`survival_arena_config.yml`）

| 項目 | 說明 |
|------|------|
| `game.enabled` | 是否啟用 |
| `game.max_waves` | 最大波次數（預設 10） |
| `game.wave_cooldown` | 波次間冷卻（秒） |
| `game.base_score` / `wave_score_multiplier` | 擊殺基礎分 / 每波額外分乘數 |
| `game.health_multiplier` / `damage_multiplier` | 每波怪物生命 / 傷害遞增倍率 |
| `game.spawn_radius` / `arena_radius` | 生成距離 / 競技場半徑 |
| `game.wave_timeout` | 每波時間限制（秒，0 = 無限制） |
| `game.highlight_mobs` | 是否讓怪物發光 |
| `waves.*` | 各波的 `wave` 編號、`count` 數量、`mobs` 怪物種類列表 |

---

## 開發進度

- [x] 波次生成與遞增難度
- [x] 計分與獎勵結算
- [x] 可配置波次組成（數量／種類）
- [x] 多人支援、怪物發光標記
- [x] 每波超時失敗判定

## 待辦 / 延伸

- [ ] Boss 波 / 特殊精英怪
- [ ] 競技場區域邊界保護（防止離場 / 外部干擾）
- [ ] 排行榜與歷史最高波次紀錄
