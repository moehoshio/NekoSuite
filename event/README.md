# Event 模塊 - 活動系統

## 概述
Event 模塊提供完整的活動管理功能，支持多種活動類型和獎勵機制。

## 功能
- 多種活動類型 (每日、週期、限時)
- 自動獎勵發放
- 活動進度追踪  
- 靈活配置系統

## API 端點

### 檢查活動
```
GET /event/?action=check&user=player1
```

### 參與活動  
```
GET /event/?action=participate&user=player1&event_id=daily_login
```

### 查詢狀態
```
GET /event/?action=status&user=player1&event_id=daily_login
```

## 錯誤類型
- `invalid_user_name` - 用戶名無效
- `invalid_action` - 操作無效  
- `participation_failed` - 參與失敗
- `no_available_events` - 無可用活動

## 配置示例
```yaml
events:
  daily_login:
    type: "daily"
    rewards:
      - "give %player% diamond 1"
    limit_modes:
      count: 1
      time: "24h"
```

## 使用方式

1. 配置活動參數
2. 用戶觸發活動檢查  
3. 系統自動發放獎勵

### 常見錯誤

1. **`limit_mode_exceeded`** - 活動已達到參與次數限制
2. **`event_not_available`** - 活動不在有效期內或不存在
3. **`already_participated`** - 用戶已參與該活動
4. **`invalid_event_id`** - 活動ID無效

### 日誌查看

服務器啟動時會顯示載入的活動配置，檢查配置是否正確載入。

### 存儲問題

如果使用YAML存儲出現性能問題，建議切換到SQLite存儲。

## 總結

Event 模塊提供了完整的活動管理解決方案，具有良好的擴展性和配置靈活性。通過合理配置，可以實現各種複雜的活動需求。
