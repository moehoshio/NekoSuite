# NekoSuite 架構設計

## 整體架構

NekoSuite 採用前後端分離的模塊化架構：

```
┌─────────────────┐    HTTP API    ┌─────────────────┐
│   MyCommand     │ ──────────────► │   Go Backend    │
│   (前端腳本)     │ ◄────────────── │   (核心邏輯)     │
└─────────────────┘                └─────────────────┘
                                           │
                                           ▼
                                   ┌─────────────────┐
                                   │   YAML 存儲     │
                                   │   (用戶數據)     │
                                   └─────────────────┘
```

## 核心設計原則

### 1. 模塊化
- 每個功能模塊獨立開發和部署
- 共用基礎設施（存儲、限制管理、獎勵系統）
- 統一的 API 設計規範

### 2. UTF8 兼容性
- MyCommand call_url 不支持 UTF8 編碼
- **解決方案**: 後端返回英文命令，前端腳本處理中文顯示
- 所有 API 響應只包含 ASCII 字符

### 3. 數據隔離
- 用戶數據按模塊分隔存儲
- 防止模塊間數據衝突
- 靈活的配置管理

## 技術棧

### 前端 (MyCommand)
- **語言**: YAML 配置
- **功能**: 遊戲命令處理、用戶交互、中文顯示
- **通信**: HTTP GET 請求到後端 API

### 後端 (Go)
- **語言**: Go 1.16+
- **功能**: 業務邏輯、數據處理、API 服務
- **存儲**: YAML 文件存儲
- **端口**: 8080 (預設)

## 通信協議

### 請求格式
```
GET /module/?param1=value1&param2=value2
```

### 響應格式
```
成功: /command_name result_data
錯誤: /module_error_text error_type
```

### 範例
```
請求: GET /wish/?user=player1&action=wish&pool=StarPath&value=1
響應: /handle_wish_StarPath 160 1 diamond sword bow apple bread
```

## 模塊結構

### 標準模塊結構
```
module_name/
├── config.yaml           # 模塊配置
├── config_example.yaml   # 配置範例
├── module_name.go        # 模塊入口
├── module_name.yml       # MyCommand 腳本
├── README.md            # 模塊文檔和 API
└── go/                  # Go 實現
    ├── config.go        # 配置管理
    ├── handler.go       # HTTP 處理
    ├── unified_service.go  # 業務邏輯
    └── unified_storage.go  # 數據存儲
```

### 共用模塊 (go/)
```
go/
├── common_storage.go     # 統一存儲接口
├── yaml_storage.go      # YAML 存儲實現
├── storage_factory.go   # 存儲工廠
├── limit_mode.go        # 限制模式管理
└── reward_manager.go    # 獎勵系統管理
```

## 數據管理

### 用戶數據結構
```yaml
# userdata/username.yml
wish:
  counts:
    GlimmeringPrayer: 5
    StarPath: 12
  tickets:
    standard: 10
    premium: 3

event:
  participation:
    - event_id: "summer2024"
      completed: true
  rewards:
    claimed: ["reward1", "reward2"]
```

### 配置文件結構
```yaml
# config.yaml
storage:
  type: "yaml"
  data_path: "./userdata"

limit_modes:
  count: 10
  time: "1d"
  refresh_at_time: "00:00"

# 模塊特定配置...
```

## 錯誤處理

### 統一錯誤類型
- `user_not_found` - 用戶不存在
- `invalid_parameters` - 參數無效
- `limit_exceeded` - 超出限制
- `insufficient_resources` - 資源不足
- `config_error` - 配置錯誤

### 錯誤處理流程
1. 後端檢測錯誤，返回英文錯誤類型
2. 前端腳本接收錯誤類型
3. 前端根據錯誤類型顯示中文錯誤訊息

## 部署架構

### 開發環境
```
Minecraft Server (MyCommand) → localhost:8080 (Go Backend)
```

### 生產環境
```
Minecraft Server → 反向代理 → Go Backend 集群
                       ↓
                   共享存儲 (NFS/DB)
```

## 性能考慮

### 緩存策略
- 配置文件緩存（減少 I/O）
- 用戶數據懶加載
- HTTP 長連接復用

### 並發安全
- 用戶數據讀寫鎖
- 原子操作計數器
- Go 協程池管理

### 存儲優化
- YAML 文件壓縮
- 批量數據更新
- 定期數據清理

## 擴展性

### 新增模塊步驟
1. 創建模塊目錄結構
2. 實現 Go 後端邏輯
3. 編寫 MyCommand 前端腳本
4. 配置路由和 API
5. 編寫模塊文檔

### 模塊間通信
- 通過共用存儲接口
- 事件系統（未來考慮）
- API 網關模式（大型部署）

---

這個架構設計確保了 NekoSuite 的可維護性、可擴展性和 UTF8 兼容性。
