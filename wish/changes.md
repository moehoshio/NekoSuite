# Wish模組變更記錄

## 2025-08-03: 祈愿券管理功能完善

### 新增功能

#### 祈愿券增删查API
添加了完整的祈愿券管理外部接口，支持通過HTTP請求進行祈愿券的增删查操作：

1. **添加祈愿券** (`action=ticket_add`)
   - 支持給指定用戶添加指定數量的祈愿券
   - 參數：`user`, `ticket_type`, `amount`
   - 響應：`/ticket_add_result [TICKET_TYPE] [ADDED_AMOUNT] [NEW_TOTAL]`

2. **刪除祈愿券** (`action=ticket_remove`)
   - 支持從指定用戶刪除指定數量的祈愿券
   - 包含庫存檢查，防止負數
   - 參數：`user`, `ticket_type`, `amount`
   - 響應：`/ticket_remove_result [TICKET_TYPE] [REMOVED_AMOUNT] [NEW_TOTAL]`

3. **查詢祈愿券** (`action=ticket_query`)
   - 支持查詢特定類型祈愿券：包含`ticket_type`參數
   - 支持查詢所有祈愿券：不包含`ticket_type`參數
   - 響應格式：
     - 單個：`/ticket_query_result [TICKET_TYPE] [AMOUNT] "[NAME]" "[DESCRIPTION]"`
     - 全部：`/ticket_query_all_result [TICKET_LIST]`

#### API設計特點
- **統一接口**：所有操作都通過`/wish/`端點，使用`action`參數區分
- **錯誤處理**：完整的錯誤類型和響應機制
- **數據驗證**：參數驗證、類型檢查、庫存檢查
- **兼容性**：與現有祈愿API保持一致的設計風格

### 代碼結構變更

#### handler.go
- 新增 `handleTicketAddAction()` - 處理祈愿券添加
- 新增 `handleTicketRemoveAction()` - 處理祈愿券刪除  
- 新增 `handleTicketQueryAction()` - 處理祈愿券查詢（單個）
- 新增 `handleTicketQueryAllAction()` - 處理祈愿券查詢（全部）
- 擴展 `HandleWish()` 的action路由，添加對新action的支持

#### ticket.go
- 將 `WishTicketManager.ticketTypes` 字段改為公開的 `TicketTypes`
- 保持向後兼容，所有現有功能不受影響
- 支持外部訪問祈愿券類型信息

#### 測試文件
- 新增 `ticket_test.go` - 祈愿券管理器的單元測試
- 測試覆蓋：基本操作、通用券、固定/靈活模式

#### 文檔
- 新增 `TICKET_API.md` - 詳細的API使用說明和示例
- 包含完整的請求/響應格式、錯誤代碼、配置說明

### 使用場景

1. **管理員操作**
   - 活動獎勵發放祈愿券
   - 補償玩家祈愿券
   - 調整玩家祈愿券庫存

2. **遊戲系統整合**
   - 任務系統獎勵祈愿券
   - 商店購買祈愿券
   - 活動兌換祈愿券

3. **數據查詢**
   - 玩家庫存查詢
   - 管理面板數據展示
   - 統計分析

### 技術實現

- **RESTful設計**：使用HTTP GET請求，參數清晰
- **錯誤處理**：統一的錯誤響應格式
- **數據一致性**：操作前進行充分驗證
- **擴展性**：易於添加新的祈愿券操作類型

### 向後兼容性

- 所有現有的祈愿功能保持不變
- 現有的祈愿券配置格式完全兼容
- 現有的祈愿券使用邏輯不受影響
- API響應格式與現有wish模組保持一致

### 後續計劃

1. 添加批量操作支持（一次操作多種祈愿券類型）
2. 添加祈愿券使用記錄和統計功能
3. 考慮添加祈愿券過期機制
4. 整合到前端腳本（wish.yml）中