# 祈愿券API使用說明

## 概述

祈愿券系統提供了三個主要的API操作：增加、刪除和查詢用戶的祈愿券。所有操作都通過統一的 `/wish/` 端點進行，使用 `action` 參數來區分不同的操作類型。

## API接口

### 基本請求格式
```
GET /wish/?action=[ACTION]&user=[USERNAME]&[其他參數]
```

### 1. 添加祈愿券 (ticket_add)

**請求格式：**
```
GET /wish/?action=ticket_add&user=[USERNAME]&ticket_type=[TICKET_TYPE]&amount=[AMOUNT]
```

**參數說明：**
- `action`: `ticket_add`
- `user`: 用戶名（必需）
- `ticket_type`: 祈愿券類型ID（必需）
- `amount`: 要添加的數量，必須是正整數（必需）

**響應格式：**
- 成功：`/ticket_add_result [TICKET_TYPE] [ADDED_AMOUNT] [NEW_TOTAL]`
- 失敗：`/wish_error_text [ERROR_TYPE]`

**示例：**
```bash
# 給用戶 "player1" 添加 5 張 "wish_ticket_any" 祈愿券
GET /wish/?action=ticket_add&user=player1&ticket_type=wish_ticket_any&amount=5

# 成功響應
/ticket_add_result wish_ticket_any 5 15
```

### 2. 刪除祈愿券 (ticket_remove)

**請求格式：**
```
GET /wish/?action=ticket_remove&user=[USERNAME]&ticket_type=[TICKET_TYPE]&amount=[AMOUNT]
```

**參數說明：**
- `action`: `ticket_remove`
- `user`: 用戶名（必需）
- `ticket_type`: 祈愿券類型ID（必需）
- `amount`: 要刪除的數量，必須是正整數（必需）

**響應格式：**
- 成功：`/ticket_remove_result [TICKET_TYPE] [REMOVED_AMOUNT] [NEW_TOTAL]`
- 失敗：`/wish_error_text [ERROR_TYPE]`

**示例：**
```bash
# 從用戶 "player1" 刪除 3 張 "wish_ticket_any" 祈愿券
GET /wish/?action=ticket_remove&user=player1&ticket_type=wish_ticket_any&amount=3

# 成功響應
/ticket_remove_result wish_ticket_any 3 12
```

### 3. 查詢祈愿券 (ticket_query)

#### 3.1 查詢特定類型祈愿券

**請求格式：**
```
GET /wish/?action=ticket_query&user=[USERNAME]&ticket_type=[TICKET_TYPE]
```

**參數說明：**
- `action`: `ticket_query`
- `user`: 用戶名（必需）
- `ticket_type`: 祈愿券類型ID（必需）

**響應格式：**
- 成功：`/ticket_query_result [TICKET_TYPE] [AMOUNT] "[NAME]" "[DESCRIPTION]"`
- 失敗：`/wish_error_text [ERROR_TYPE]`

**示例：**
```bash
# 查詢用戶 "player1" 的 "wish_ticket_any" 祈愿券數量
GET /wish/?action=ticket_query&user=player1&ticket_type=wish_ticket_any

# 成功響應
/ticket_query_result wish_ticket_any 12 "任意祈愿券" "可抵扣任意祈愿池代價的祈愿券"
```

#### 3.2 查詢所有祈愿券

**請求格式：**
```
GET /wish/?action=ticket_query&user=[USERNAME]
```

**參數說明：**
- `action`: `ticket_query`
- `user`: 用戶名（必需）
- `ticket_type`: 不提供此參數

**響應格式：**
- 成功：`/ticket_query_all_result [TICKET_LIST]`
- 失敗：`/wish_error_text [ERROR_TYPE]`

其中 `[TICKET_LIST]` 是以空格分隔的祈愿券信息列表，每個祈愿券的格式為：
`[TICKET_TYPE]:[AMOUNT]:"[NAME]":"[DESCRIPTION]"`

**示例：**
```bash
# 查詢用戶 "player1" 的所有祈愿券
GET /wish/?action=ticket_query&user=player1

# 成功響應
/ticket_query_all_result wish_ticket_any:12:"任意祈愿券":"可抵扣任意祈愿池代價的祈愿券" wish_ticket_star_path:5:"星途祈愿券":"可用于星途祈愿池的祈愿券" wish_ticket_starry_mirage_x10:2:"星光如梦10次祈愿券":"可抵扣星光如梦池的十连祈愿代价的祈愿券"
```

## 錯誤代碼

### 通用錯誤
- `invalid_action`: 無效的操作類型
- `invalid_user_name`: 無效的用戶名（空用戶名）
- `invalid_ticket_type`: 無效的祈愿券類型（空類型）
- `unknown_ticket_type`: 未知的祈愿券類型
- `invalid_amount`: 無效的數量（非正整數）

### 刪除操作特有錯誤
- `insufficient_tickets`: 祈愿券數量不足

### 操作失敗錯誤
- `ticket_add_failed`: 添加祈愿券失敗
- `ticket_remove_failed`: 刪除祈愿券失敗
- `ticket_query_failed`: 查詢祈愿券失敗

## 祈愿券類型配置

祈愿券類型在 `config.yaml` 的 `tickets` 部分進行配置：

```yaml
tickets:
    - id: "wish_ticket_any"
      name: "任意祈愿券"
      description: "可抵扣任意祈愿池代價的祈愿券"
      applicable_pools:
          - "StarPath"
          - "StarryMirage"
          - "OathOfTheRedRose"
      deduct_count: 1
      deduct_mode: "flexible"

    - id: "wish_ticket_starry_mirage_x10"
      name: "星光如梦10次祈愿券"
      description: "可抵扣星光如梦池的十连祈愿代价的祈愿券"
      applicable_pools:
          - "StarryMirage"
      deduct_count: 10
      deduct_mode: "fixed"
```

### 配置參數說明

- `id`: 祈愿券的唯一標識符
- `name`: 祈愿券的顯示名稱
- `description`: 祈愿券的描述
- `applicable_pools`: 適用的祈愿池列表，空列表表示適用於所有池
- `deduct_count`: 可抵扣的祈愿次數
- `deduct_mode`: 抵扣模式
  - `flexible`: 靈活模式，可以按需抵扣
  - `fixed`: 固定模式，只能用於特定次數的祈愿

## 使用場景

### 1. 管理員給玩家發放祈愿券
```bash
# 給玩家發放10張任意祈愿券作為活動獎勵
GET /wish/?action=ticket_add&user=player123&ticket_type=wish_ticket_any&amount=10
```

### 2. 玩家使用祈愿券後的扣除
祈愿券在祈愿過程中會自動使用和扣除，但管理員也可以手動扣除：
```bash
# 手動扣除玩家5張祈愿券
GET /wish/?action=ticket_remove&user=player123&ticket_type=wish_ticket_any&amount=5
```

### 3. 查看玩家的祈愿券庫存
```bash
# 查看玩家所有祈愿券
GET /wish/?action=ticket_query&user=player123

# 查看特定類型祈愿券
GET /wish/?action=ticket_query&user=player123&ticket_type=wish_ticket_any
```

## 注意事項

1. 所有數量參數必須是正整數
2. 祈愿券類型必須在配置文件中預先定義
3. 刪除祈愿券時會檢查數量是否足夠
4. 查詢所有祈愿券會返回所有已配置的祈愿券類型，即使數量為0
5. 響應中的名稱和描述會用雙引號包圍，以處理包含空格的情況
