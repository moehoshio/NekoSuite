# NekoSuite Wish 模塊 API 文檔

## 概覽

NekoSuite Wish 模塊提供了一個完整的祈願系統，支持多種祈願池、祈願券、余額檢查和自動成本計算等功能。

### 基礎 URL
```
http://localhost:8080/wish/
```

### 請求格式
所有請求使用 GET 方法，參數通過 Query String 傳遞。

```
GET /wish/?action={操作}&user={用戶名}&type={祈願池類型}&value={祈願次數}&bal={用戶余額}
```

---

## API 端點

### 1. 祈願操作 (wish)

執行祈願操作，支持余額檢查和自動成本計算。

**祈願請求:**
```
GET /wish/?action=wish&user={用戶名}&type={祈願池類型}&value={祈願次數}&bal={用戶余額}
```

**參數說明:**
- `action` (必需): 祈願為 "wish"
- `user` (必需): 用戶名
- `type` (必需): 祈願池類型，可選值：
  - `StarPath` - 星途祈願
  - `StarryMirage` - 星光如梦祈願
  - `OathOfTheRedRose` - 紅玫的誓約祈願
  - `GlimmeringPrayer` - 微光之愿（每日祈願）
- `value` (必需): 祈願次數（正整數）
- `bal` (可選): 用戶余額，用於余額檢查（浮點數）

**響應格式:**
成功時返回：
```
/handle_wish_{type} {cost} {count} {items...}
```

示例：
```
/handle_wish_StarPath 160 2 iron_ingot5 diamond2
```
(注，需要開啓auto_cost，否則只能祈願cost定義的次數)

**錯誤響應:**
```
/wish_error_text {error_type}
```

---

### 2. 查詢祈願次數 (query)

查詢用戶在指定祈願池的祈願次數。

**請求格式:**
```
GET /wish/?action=query&user={用戶名}&type={祈願池類型}
```

**參數說明:**
- `action` (必需): 查詢為 "query"
- `user` (必需): 用戶名
- `type` (必需): 祈願池類型

**響應格式:**
```
/wish_query_result {type} {count} {ticket}
```

示例：
```
/wish_query_result StarPath 45 0
```

---

### 3. 每日祈願 (GlimmeringPrayer)

執行每日祈願操作。

**請求格式:**
```
GET /wish/?action=GlimmeringPrayer&user={用戶名}
```

**參數說明:**
- `action` (必需): 固定為 "GlimmeringPrayer"
- `user` (必需): 用戶名

**響應格式:**
```
/give_wish_daily {item}
```

示例：
```
/give_wish_daily bal50
```

---

### 4. 祈願券添加 (ticket_add)

為用戶添加指定類型的祈願券。

**請求格式:**
```
GET /wish/?action=ticket_add&user={用戶名}&ticket_type={祈願券類型}&amount={數量}
```

**參數說明:**
- `action` (必需): 固定為 "ticket_add"
- `user` (必需): 用戶名
- `ticket_type` (必需): 祈願券類型，可選值：
  - `Sigil of Stars` - 星途印記
  - `Token of Crimson Vow` - 紅誓令牌
  - `Dawnlight Slip` - 曙光憑證
- `amount` (必需): 添加數量（正整數）

**響應格式:**
```
/ticket_add_result {ticket_type} {added_amount} {new_total}
```

示例：
```
/ticket_add_result "Sigil of Stars" 5 15
```

---

### 5. 祈願券刪除 (ticket_remove)

刪除用戶指定類型的祈願券。

**請求格式:**
```
GET /wish/?action=ticket_remove&user={用戶名}&ticket_type={祈願券類型}&amount={數量}
```

**參數說明:**
- `action` (必需): 固定為 "ticket_remove"
- `user` (必需): 用戶名
- `ticket_type` (必需): 祈願券類型
- `amount` (必需): 刪除數量（正整數）

**響應格式:**
```
/ticket_remove_result {ticket_type} {removed_amount} {new_total}
```

示例：
```
/ticket_remove_result "Sigil of Stars" 3 12
```

---

### 6. 祈願券查詢 (ticket_query)

查詢用戶的祈願券數量。

**請求格式:**
```
# 查詢指定類型祈願券
GET /wish/?action=ticket_query&user={用戶名}&ticket_type={祈願券類型}

# 查詢所有祈願券
GET /wish/?action=ticket_query&user={用戶名}
```

**參數說明:**
- `action` (必需): 固定為 "ticket_query"
- `user` (必需): 用戶名
- `ticket_type` (可選): 祈願券類型，不提供則查詢所有

**響應格式:**
指定類型查詢：
```
/ticket_query_result {ticket_type} {amount}
```

所有類型查詢：
```
/ticket_query_all_result {ticket1}:{amount1} {ticket2}:{amount2} ...
```

示例：
```
# 指定類型
/ticket_query_result "Sigil of Stars" 12

# 所有類型
/ticket_query_all_result "Sigil of Stars":12 "Token of Crimson Vow":5 "Dawnlight Slip":8
```

---

## 錯誤代碼

所有錯誤響應格式為：`/wish_error_text {error_type}`

### 常見錯誤類型

| 錯誤代碼 | 說明 |
|---------|------|
| `invalid_action` | 無效的 action 參數 |
| `invalid_user_name` | 無效的用戶名（空或未提供） |
| `invalid_wish_type` | 無效的祈願池類型 |
| `invalid_wish_value` | 無效的祈願次數（非正整數） |
| `invalid_wish_count` | 無效的祈願次數（超出限制） |
| `invalid_balance` | 無效的余額參數 |
| `insufficient_balance` | 余額不足 |
| `invalid_ticket_type` | 無效的祈願券類型 |
| `invalid_amount` | 無效的數量參數 |
| `insufficient_tickets` | 祈願券數量不足 |
| `unknown_ticket_type` | 未知的祈願券類型 |
| `ticket_add_failed` | 祈願券添加失敗 |
| `ticket_remove_failed` | 祈願券刪除失敗 |
| `ticket_query_failed` | 祈願券查詢失敗 |
| `limit_mode_exceeded` | 超出祈願次數限制 |
| `unknown_error` | 未知錯誤 |

---

## 祈願池配置

### 可用祈願池

| 祈願池 ID | 中文名稱 | 說明 | 成本 | 保底 |
|----------|---------|------|------|------|
| `StarPath` | 星途祈願 | 常駐祈願池 | 1次:80, 5次:400 | 200次保底 |
| `StarryMirage` | 星光如夢 | 限時祈願池 | 1次:80, 5次:400 | 200次保底 |
| `OathOfTheRedRose` | 紅玫的誓約 | 情人節限時池 | 1次:80, 5次:400 | 200次保底 |
| `GlimmeringPrayer` | 微光之愿 | 每日免費祈願 | 免費 | 100次保底 |

### 祈願券類型

| 祈願券 ID | 中文名稱 | 適用池 | 抵扣方式 |
|----------|---------|--------|----------|
| `Sigil of Stars` | 星途印記 | StarPath, StarryMirage | 彈性抵扣 |
| `Token of Crimson Vow` | 紅誓令牌 | OathOfTheRedRose | 彈性抵扣 |
| `Dawnlight Slip` | 曙光憑證 | StarPath, StarryMirage | 彈性抵扣 |

---

## 新功能說明

### 1. 自動成本計算 (auto_cost)
- 當祈願池配置 `auto_cost: true` 時：
  - 支持任意次數的祈願（例如：7次、13次等）
  - 系統使用動態規劃算法自動計算最優成本組合
  - 使用 config.yaml 中的 cost 列表作為基礎單元進行組合
- 當祈願池配置 `auto_cost: false` 時：
  - **只能祈願 cost 列表中預定義的次數**
  - 發送未在 cost 列表中的 value 將返回 `invalid_wish_count` 錯誤
  - 適用於需要嚴格控制祈願次數的場景

**重要提醒**: 向 `auto_cost: false` 的池發送非預定義次數會導致錯誤！

### 2. 余額檢查 (bal 參數)
- 在祈願前檢查用戶余額是否足夠支付成本
- 如果余額不足，返回 `insufficient_balance` 錯誤
- 余額檢查在祈願券抵扣後進行

### 3. 移除 name 和 description 字段
- 祈願券配置中不再包含 name 和 description 字段
- 提高與非 ASCII 字符的 Web 兼容性
- 僅保留功能必需的字段

---

## 示例用法

### 配置示例

```yaml
# config.yaml 示例
wish_pools:
  StarPath:
    auto_cost: true    # 支持任意次數祈願
    cost:
      1: 80
      5: 400
      10: 800
    # ... 其他配置
  
  OathOfTheRedRose:
    auto_cost: false   # 只能祈願 1, 5, 10 次
    cost:
      1: 100
      5: 500
      10: 1000
    # ... 其他配置
```

### 普通祈願

```bash
# StarPath (auto_cost: true) - 支持任意次數
curl "http://localhost:8080/wish/?action=wish&user=player1&type=StarPath&value=7&bal=1000"
# ✅ 成功：自動計算 7 次祈願的最優成本組合

# OathOfTheRedRose (auto_cost: false) - 只能祈願預定義次數
curl "http://localhost:8080/wish/?action=wish&user=player1&type=OathOfTheRedRose&value=5&bal=1000"
# ✅ 成功：5 次在 cost 列表中

curl "http://localhost:8080/wish/?action=wish&user=player1&type=OathOfTheRedRose&value=7&bal=1000"
# ❌ 失敗：返回 /wish_error_text invalid_wish_count

# 響應示例
/handle_wish_StarPath 560 7 iron_ingot5 exp50 gold_ingot5 diamond2 purified_water_canteen p_point5 netherite_scrap
```

### 每日祈願
```bash
# 執行每日祈願
curl "http://localhost:8080/wish/?action=GlimmeringPrayer&user=player1"

# 響應示例
/give_wish_daily bal50
```

### 祈願券管理
```bash
# 添加祈願券
curl "http://localhost:8080/wish/?action=ticket_add&user=player1&ticket_type=Sigil%20of%20Stars&amount=10"

# 查詢祈願券
curl "http://localhost:8080/wish/?action=ticket_query&user=player1"

# 響應示例
/ticket_query_all_result "Sigil of Stars":10 "Token of Crimson Vow":0 "Dawnlight Slip":0
```

### 查詢祈願次數
```bash
# 查詢星途祈願次數
curl "http://localhost:8080/wish/?action=query&user=player1&type=StarPath"

# 響應示例
/wish_query_result StarPath 45 0
```

---

## 注意事項

1. **字符編碼**: 所有參數應使用 URL 編碼，特別是包含空格的祈願券名稱
2. **錯誤處理**: 前端應妥善處理所有錯誤響應，並向用戶顯示友好的錯誤信息
3. **余額檢查**: 使用 `bal` 參數進行余額檢查時，確保傳入準確的用戶余額
4. **祈願次數**: 支持任意正整數的祈願次數，系統會自動計算最優成本
5. **限時池**: 注意檢查祈願池的活動時間和每日限制次數

---

## 更新日志

### v2.0.0 (2025-01-08)
- ✅ 移除祈願券 name 和 description 字段
- ✅ 實現 auto_cost 自動成本計算功能
- ✅ 添加 bal 參數支持余額檢查
- ✅ 移除祈願次數硬編碼限制（1,5,10）
- ✅ 支持任意次數祈願的最優成本計算
- ✅ 遷移到純 Go SQLite 驅動程式 (modernc.org/sqlite)
- ✅ 完善錯誤處理和響應格式
