# 隨機傳送挑戰（Random Teleport Game）

- **指令**：`/ngame rtp [menu|start|status|end]`
- **管理器**：`RandomTeleportGameManager`
- **配置檔**：`random_teleport_config.yml`
- **語言鍵前綴**：`ngame.rtp.*`
- **故事線**：競技挑戰線（輕量敘事）

---

## 故事設定

「試煉場」性質的限時定向挑戰：玩家被隨機投放到大地某處，必須在時間耗盡前找到並抵達指定的目標座標。與主線無直接劇情關聯，可作為日常挑戰活動運作。

---

## 玩法規則

- 玩家被傳送到設定範圍內的隨機座標。
- 系統設定一個玩家須抵達的目標位置（與起點維持 `min_distance`～`max_distance` 的距離）。
- 在 `default_time_limit` 內進入目標的 `target_radius` 判定半徑即為成功。
- 遊戲期間暫時移除指定權限（避免使用傳送／飛行作弊）。
- 支援手動結束、超時失敗、抵達目標成功三種結局；成功時發放獎勵。

---

## 主要配置項（`random_teleport_config.yml`）

| 項目 | 說明 |
|------|------|
| `game.enabled` | 是否啟用 |
| `game.default_time_limit` | 預設時間限制（秒，預設 300） |
| `game.target_radius` | 抵達判定半徑（方塊，預設 5） |
| `game.min_distance` / `game.max_distance` | 起點與目標的最小／最大距離 |
| `game.world_border_min` / `world_border_max` | 隨機座標的世界邊界 |
| `game.default_world` | 預設世界 |
| `game.permissions_to_remove` | 遊戲期間暫時移除的權限列表 |
| `rewards.commands` | 成功時執行的主控台指令（支援 `{player}`） |

---

## 開發進度

- [x] 隨機投放 + 目標生成 + 距離限制
- [x] 限時判定（成功／超時／手動結束）
- [x] 成功獎勵（主控台指令）
- [x] 權限移除清單追蹤

## 待辦 / 延伸

- [ ] **真實權限移除／還原**：目前 `removePlayerPermissions` / `restorePlayerPermissions` 僅追蹤待移除權限，實際移除與還原需整合 Vault Permission API（程式碼內 `NOTE` 已標註替代方案：可改用 `TeleportManager` 的 `/ntpadmin lock`）。
- [ ] 指南針 / 方向提示輔助
- [ ] 競速排行榜
