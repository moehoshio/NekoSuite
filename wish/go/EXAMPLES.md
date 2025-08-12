# 祈愿券API使用示例

本文檔展示如何使用新的祈愿券管理API。

## 啟動服務

首先確保NekoSuite服務正在運行：

```bash
./neko-suite.exe
# 或在Windows上
neko-suite.exe
```

## 基本操作示例

假設服務運行在默認端口，以下是一些使用示例：

### 1. 給用戶添加祈愿券

```bash
# 給用戶 "testplayer" 添加 10 張任意祈愿券
curl "http://localhost:8080/wish/?action=ticket_add&user=testplayer&ticket_type=wish_ticket_any&amount=10"

# 預期響應: /ticket_add_result wish_ticket_any 10 10
```

### 2. 查詢用戶的祈愿券

```bash
# 查詢用戶所有祈愿券
curl "http://localhost:8080/wish/?action=ticket_query&user=testplayer"

# 預期響應: /ticket_query_all_result wish_ticket_any:10:"任意祈愿券":"可抵扣任意祈愿池代價的祈愿券" ...

# 查詢特定類型祈愿券
curl "http://localhost:8080/wish/?action=ticket_query&user=testplayer&ticket_type=wish_ticket_any"

# 預期響應: /ticket_query_result wish_ticket_any 10 "任意祈愿券" "可抵扣任意祈愿池代價的祈愿券"
```

### 3. 用戶進行祈愿（會自動使用祈愿券）

```bash
# 進行一次星途祈愿，會自動使用祈愿券抵扣費用
curl "http://localhost:8080/wish/?action=wish&user=testplayer&type=StarPath&value=1"

# 如果用戶有適用的祈愿券，費用會被自動抵扣
```

### 4. 手動刪除祈愿券

```bash
# 從用戶刪除 3 張祈愿券
curl "http://localhost:8080/wish/?action=ticket_remove&user=testplayer&ticket_type=wish_ticket_any&amount=3"

# 預期響應: /ticket_remove_result wish_ticket_any 3 7
```

## 錯誤處理示例

### 嘗試刪除超過擁有數量的祈愿券

```bash
# 嘗試刪除 20 張祈愿券（用戶只有 7 張）
curl "http://localhost:8080/wish/?action=ticket_remove&user=testplayer&ticket_type=wish_ticket_any&amount=20"

# 預期響應: /wish_error_text insufficient_tickets
```

### 使用不存在的祈愿券類型

```bash
# 嘗試添加不存在的祈愿券類型
curl "http://localhost:8080/wish/?action=ticket_add&user=testplayer&ticket_type=nonexistent_ticket&amount=5"

# 預期響應: /wish_error_text unknown_ticket_type
```

## 批次操作示例

### 管理員給多個用戶發放獎勵

```bash
# 假設通過腳本給多個用戶發放祈愿券
for user in player1 player2 player3; do
    curl "http://localhost:8080/wish/?action=ticket_add&user=$user&ticket_type=wish_ticket_any&amount=5"
done
```

### 活動結束時清理祈愿券

```bash
# 查詢用戶當前祈愿券數量
current=$(curl -s "http://localhost:8080/wish/?action=ticket_query&user=testplayer&ticket_type=wish_ticket_starry_mirage_x10" | grep -o '[0-9]\+' | head -1)

# 如果有剩餘，則全部刪除
if [ "$current" -gt 0 ]; then
    curl "http://localhost:8080/wish/?action=ticket_remove&user=testplayer&ticket_type=wish_ticket_starry_mirage_x10&amount=$current"
fi
```

## 與現有祈愿系統的整合

祈愿券系統與現有的祈愿功能無縫整合：

1. **自動使用**：在進行祈愿時，系統會自動檢查並使用適用的祈愿券
2. **優先級**：系統會優先使用價值高的祈愿券
3. **靈活性**：支持靈活模式（按需抵扣）和固定模式（特定次數）
4. **兼容性**：完全兼容現有的config.yaml配置

## 注意事項

1. **參數驗證**：所有參數都會進行嚴格驗證
2. **數據一致性**：操作會檢查數據一致性，防止出現負數或無效狀態
3. **錯誤恢復**：如果操作失敗，不會影響用戶數據
4. **日誌記錄**：所有操作都會記錄在系統日誌中

## 疑難排解

### 常見問題

1. **響應格式不符合預期**
   - 檢查請求參數是否正確
   - 確認祈愿券類型是否在config.yaml中定義

2. **權限錯誤**
   - 確保NekoSuite有權限讀寫數據文件
   - 檢查數據目錄權限設置

3. **服務無響應**
   - 檢查服務是否正在運行
   - 確認端口是否被佔用
   - 查看服務日誌獲取錯誤信息
