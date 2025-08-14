# NekoSuite 項目架構文檔

## 項目概述

NekoSuite 是一個模塊化的 Minecraft 服務器擴展系統，採用 Golang 後端架構。項目設計遵循模塊化、可擴展、數據隔離的原則，支持多種遊戲功能模塊的統一管理。

## 核心設計理念

### 1. 模塊化架構
- **共用模塊**：位於 `/go/` 目錄，提供所有模塊共享的基礎功能
- **特定模塊**：位於各模塊的 `/go/` 子目錄，實現模塊特有的業務邏輯
- **接口驅動**：通過統一接口實現模塊間的解耦

### 2. 數據隔離
- 每個模塊的數據存儲在獨立的命名空間下
- 統一的用戶數據結構，支持模塊級別的數據隔離
- 靈活的存儲後端支持（YAML、SQLite 等）

### 3. 統一管理
- 共享的限制模式管理（次數限制、時間週期等）
- 統一的獎勵系統
- 標準化的配置結構

## 目錄結構

```
NekoSuite/
├── go/                          # 共用模塊目錄
│   ├── common_storage.go        # 統一存儲接口定義
│   ├── yaml_storage.go          # YAML 存儲實現
│   ├── storage_factory.go       # 存儲工廠
│   ├── limit_mode.go           # 限制模式管理
│   ├── reward_manager.go       # 獎勵管理器
│   └── ...
├── wish/                        # 祈願模塊
│   ├── go/                     # 祈願模塊特定實現
│   │   ├── config.go           # 模塊配置
│   │   ├── unified_service.go  # 統一服務層
│   │   ├── unified_storage.go  # 存儲適配器
│   │   ├── unified_handler.go  # HTTP 處理器
│   │   └── ...
│   ├── wish.go                 # 模塊入口點
│   ├── config.yml              # 模塊配置文件
│   └── ...
├── event/                       # 活動模塊
│   ├── go/                     # 活動模塊特定實現
│   │   ├── config.go
│   │   ├── unified_service.go
│   │   ├── unified_storage.go
│   │   ├── handler.go
│   │   └── ...
│   ├── event.go                # 模塊入口點
│   ├── config.yaml             # 模塊配置文件
│   └── ...
└── main.go                     # 主程序入口
```

## 共用模塊設計 (`/go/`)

### 1. 存儲抽象層

#### CommonStorage 接口
```go
type CommonStorage interface {
    // 基本用戶數據操作
    GetUserData(userID string) (*UserData, error)
    SaveUserData(userID string, data *UserData) error
    
    // 模塊特定數據操作
    GetModuleData(userID, moduleName string, result interface{}) error
    SetModuleData(userID, moduleName string, data interface{}) error
    
    // 限制模式記錄管理
    GetLimitModeRecord(userID, moduleName, poolName string) (*LimitModeRecord, error)
    UpdateLimitModeRecord(userID, moduleName, poolName string, record *LimitModeRecord) error
}
```

#### 統一用戶數據結構
```go
type UserData struct {
    CreatedAt  string                 `yaml:"created_at"`
    UpdatedAt  string                 `yaml:"updated_at"`
    ModuleData map[string]interface{} `yaml:",inline"`
}
```

**設計思路**：
- 使用 `ModuleData` 映射實現模塊級別的數據隔離
- `yaml:",inline"` 標籤使得模塊數據直接展開到根級別
- 每個模塊的數據存儲在對應的鍵下（如 "wish", "event"）

### 2. 限制模式管理

#### LimitModeManager
```go
type LimitModeManager struct {
    storage CommonStorage
}

func (lm *LimitModeManager) CanPerform(userID, moduleName, poolName string, count int, config *LimitModeConfig) (bool, error)
func (lm *LimitModeManager) RecordUsage(userID, moduleName, poolName string, count int, config *LimitModeConfig) error
```

**設計思路**：
- 統一管理所有模塊的使用次數限制
- 支持多種週期類型：每日、每週、每月
- 模塊前綴設計避免不同模塊間的數據衝突

### 3. 獎勵系統

#### RewardManager
```go
type RewardManager struct{}

func (rm *RewardManager) ProcessRewards(rewards []RewardConfig) ([]string, error)
```

**設計思路**：
- 統一的獎勵處理邏輯
- 支持多種獎勵類型：余額、經驗、物品、命令
- 可擴展的獎勵類型系統

### 4. 存儲工廠

#### StorageFactory
```go
func NewStorage(config StorageConfig) (CommonStorage, error)
```

**設計思路**：
- 根據配置創建相應的存儲實現
- 支持多種存儲後端（YAML、SQLite 等）
- 統一的配置接口

## 模塊實現指南

### 1. 模塊目錄結構

每個新模塊都應該遵循以下結構：
```
module_name/
├── go/                          # 模塊特定實現
│   ├── config.go               # 配置結構和加載
│   ├── unified_service.go      # 業務邏輯層
│   ├── unified_storage.go      # 存儲適配器
│   ├── handler.go             # HTTP 處理器
│   └── types.go               # 模塊特定類型定義
├── module_name.go              # 模塊入口和路由註冊
├── config.yaml                 # 模塊配置文件
└── README.md                   # 模塊文檔
```

### 2. 配置層設計

#### 統一配置結構
```go
type ModuleConfig struct {
    Storage    common.StorageConfig      `yaml:"storage"`     // 統一存儲配置
    LimitModes *common.LimitModeConfig   `yaml:"limit_modes"` // 限制模式配置
    Duration   *common.DurationConfig    `yaml:"duration"`    // 持續時間配置
    // ... 模塊特定配置
}
```

**設計原則**：
- 所有模塊都使用統一的存儲配置
- 共用的配置項使用 common 包的類型
- 模塊特定配置放在同一結構中

### 3. 存儲適配器層

#### 適配器設計模式
```go
type ModuleStorageAdapter struct {
    storage common.CommonStorage
}

func (adapter *ModuleStorageAdapter) GetModuleSpecificData(userID string) (*ModuleData, error) {
    var data ModuleData
    err := adapter.storage.GetModuleData(userID, "module_name", &data)
    return &data, err
}
```

**設計思路**：
- 每個模塊創建自己的存儲適配器
- 適配器負責在通用存儲和模塊特定接口間轉換
- 隱藏底層存儲實現細節

### 4. 服務層設計

#### 統一服務模式
```go
type UnifiedModuleService struct {
    config        *ModuleConfig
    storage       *ModuleStorageAdapter
    limitManager  *common.LimitModeManager
    rewardManager *common.RewardManager
}

func (s *UnifiedModuleService) ProcessRequest(userID string, params RequestParams) (*ModuleResult, error)
```

**設計原則**：
- 集成共用的管理器（限制模式、獎勵等）
- 統一的錯誤處理和日誌記錄
- 業務邏輯與存儲邏輯分離

### 5. HTTP 處理器層

#### RESTful API 設計
```go
type Handler struct {
    service *UnifiedModuleService
}

func (h *Handler) HandleModuleAction(c *gin.Context) {
    // 1. 參數驗證
    // 2. 調用服務層
    // 3. 統一響應格式
}
```

**API 規範**：
- 統一的響應格式：`{"code": int, "msg": string, "data": interface{}}`
- 標準的 HTTP 狀態碼
- 一致的錯誤處理

## 模塊集成步驟

### 1. 創建模塊結構
1. 在項目根目錄創建模塊文件夾
2. 創建 `go/` 子目錄
3. 實現必要的文件（config.go, unified_service.go 等）

### 2. 定義模塊數據結構
```go
type ModuleData struct {
    // 模塊特定的數據字段
    Stats     ModuleStats            `yaml:"stats"`
    Records   []ModuleRecord         `yaml:"records"`
    Settings  map[string]interface{} `yaml:"settings"`
}
```

### 3. 實現存儲適配器
- 繼承 `common.CommonStorage`
- 實現模塊特定的數據操作方法
- 確保數據隔離和命名規範

### 4. 實現服務層
- 集成限制模式管理
- 集成獎勵系統
- 實現業務邏輯

### 5. 實現 HTTP 處理器
- 遵循統一的 API 規範
- 實現標準的路由處理

### 6. 註冊模塊
在 `main.go` 中註冊新模塊：
```go
func main() {
    // ... 其他初始化
    
    // 註冊模塊路由
    newmodule.RegisterRoutes(router, config.NewModule)
    
    // ...
}
```

## 最佳實踐

### 1. 命名規範
- 模塊名稱：小寫，使用下劃線分隔（如 `daily_reward`）
- 結構體：駝峰命名，模塊前綴（如 `WishConfig`）
- 接口：以 "er" 結尾（如 `WishProvider`）

### 2. 錯誤處理
- 使用 `fmt.Errorf` 包裝錯誤
- 提供有意義的錯誤消息
- 區分用戶錯誤和系統錯誤

### 3. 配置管理
- 使用 YAML 格式
- 提供配置示例文件
- 實現配置驗證

### 4. 測試策略
- 為每個模塊編寫單元測試
- 使用模擬存儲進行測試
- 集成測試驗證模塊間交互

### 5. 文檔規範
- 每個模塊提供 README.md
- API 文檔使用 OpenAPI 格式
- 代碼註釋遵循 Go 標準

## 未來擴展

### 1. 存儲後端
- SQLite 支持
- Redis 緩存層
- 分佈式存儲支持

### 2. 消息系統
- 模塊間事件通信
- 異步任務處理
- WebSocket 實時通知

### 3. 管理界面
- Web 管理面板
- 實時監控
- 配置熱更新

### 4. 性能優化
- 數據緩存策略
- 連接池管理
- 異步處理機制

這個架構設計確保了項目的可維護性、可擴展性和模塊間的低耦合，為後續開發新模塊提供了清晰的指導原則。
