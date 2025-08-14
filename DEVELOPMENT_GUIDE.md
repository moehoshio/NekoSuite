# 新模塊開發指南

## 編碼規範

- 所有 API 響應必須只包含 ASCII 字符
- 響應格式為可執行的 MyCommand 命令：`/command_name param1 param2`
- 錯誤響應格式：`/module_error_text error_type`  
- 成功響應格式：`/module_success_text result_data`
- 中文內容由前端 MyCommand 腳本處理
- 禁止返回 JSON 格式響應（MyCommand call_url 不支援 UTF8）

## 🚀 快速開始

### 1. 目錄結構
創建新模塊時，請按以下結構組織：

```
<new_module>/
├── go/
│   ├── config.go           # 配置管理
│   ├── unified_service.go  # 業務邏輯
│   ├── unified_storage.go  # 數據存儲
│   └── handler.go          # API 處理
├── <new_module>.go         # 模塊入口
└── config.yaml             # 配置文件
```

### 2. 核心實現步驟

#### Step 1: 配置結構 (config.go)
```go
package newmodule

import (
    "gopkg.in/yaml.v3"
    common "neko-suite/go"
)

type Config struct {
    Storage    common.StorageConfig      `yaml:"storage"`
    LimitModes *common.LimitModeConfig   `yaml:"limit_modes,omitempty"`
    // 添加模塊特定配置
}

func LoadConfig(path string) (*Config, error) {
    // 標準配置加載邏輯
}
```

#### Step 2: 存儲適配器 (unified_storage.go)
```go
package newmodule

import (
    "time"
    common "neko-suite/go"
)

// 模塊數據結構
type ModuleData struct {
    Stats     map[string]int    `yaml:"stats,omitempty"`
    Records   []Record          `yaml:"records,omitempty"`
    Settings  map[string]interface{} `yaml:"settings,omitempty"`
    CreatedAt string           `yaml:"created_at"`
    UpdatedAt string           `yaml:"updated_at"`
}

// 存儲適配器
type ModuleStorageAdapter struct {
    storage common.CommonStorage
}

func NewModuleStorageAdapter(storage common.CommonStorage) *ModuleStorageAdapter {
    return &ModuleStorageAdapter{storage: storage}
}

func (s *ModuleStorageAdapter) GetModuleData(userID string) (*ModuleData, error) {
    var data ModuleData
    err := s.storage.GetModuleData(userID, "new_module", &data)
    if err != nil {
        // 返回默認數據
        return &ModuleData{
            Stats:     make(map[string]int),
            Records:   make([]Record, 0),
            Settings:  make(map[string]interface{}),
            CreatedAt: time.Now().Format(time.RFC3339),
            UpdatedAt: time.Now().Format(time.RFC3339),
        }, nil
    }
    return &data, nil
}

func (s *ModuleStorageAdapter) SaveModuleData(userID string, data *ModuleData) error {
    data.UpdatedAt = time.Now().Format(time.RFC3339)
    return s.storage.SetModuleData(userID, "new_module", data)
}
```

#### Step 3: 業務服務 (unified_service.go)
```go
package newmodule

import (
    "fmt"
    common "neko-suite/go"
)

type UnifiedService struct {
    config        *Config
    storage       *ModuleStorageAdapter
    limitManager  *common.LimitModeManager
    rewardManager *common.RewardManager
}

func NewUnifiedService(config *Config, storage *ModuleStorageAdapter) *UnifiedService {
    return &UnifiedService{
        config:        config,
        storage:       storage,
        limitManager:  common.NewLimitModeManager(storage.storage),
        rewardManager: common.NewRewardManager(),
    }
}

func (s *UnifiedService) ProcessAction(userID string, action string, params map[string]interface{}) (*ActionResult, error) {
    // 1. 檢查限制模式
    if s.config.LimitModes != nil {
        canPerform, err := s.limitManager.CanPerform(userID, "new_module", action, 1, s.config.LimitModes)
        if err != nil || !canPerform {
            return nil, fmt.Errorf("exceeded_usage_limit")
        }
    }
    
    // 2. 執行業務邏輯
    result, err := s.executeAction(userID, action, params)
    if err != nil {
        return nil, err
    }
    
    // 3. 記錄使用
    if s.config.LimitModes != nil {
        s.limitManager.RecordUsage(userID, "new_module", action, 1, s.config.LimitModes)
    }
    
    return result, nil
}

func (s *UnifiedService) executeAction(userID string, action string, params map[string]interface{}) (*ActionResult, error) {
    // 實現具體的業務邏輯
    return &ActionResult{}, nil
}

type ActionResult struct {
    Success   bool                   `yaml:"success"`
    Message   string                 `yaml:"message"`
    Data      map[string]interface{} `yaml:"data,omitempty"`
    Rewards   []common.RewardConfig  `yaml:"rewards,omitempty"`
    Commands  []string               `yaml:"commands,omitempty"`
}
```

#### Step 4: API 處理器 (handler.go)
```go
package newmodule

import (
    "net/http"
    "github.com/gin-gonic/gin"
)

type Handler struct {
    service *UnifiedService
}

func NewHandler(service *UnifiedService) *Handler {
    return &Handler{service: service}
}

func (h *Handler) HandleAction(c *gin.Context) {
    // 1. 獲取參數
    user := c.Query("user")
    action := c.Query("action")
    
    if user == "" || action == "" {
        h.respondError(c, "invalid_parameters")
        return
    }
    
    // 2. 處理請求
    result, err := h.service.ProcessAction(user, action, nil)
    if err != nil {
        h.respondError(c, err.Error())
        return
    }
    
    // 3. 返回響應
    h.respondSuccess(c, result)
}

func (h *Handler) respondError(c *gin.Context, errorType string) {
    // 返回可執行的 MyCommand 命令格式
    c.String(http.StatusOK, "/module_error_text %s", errorType)
}

func (h *Handler) respondSuccess(c *gin.Context, data interface{}) {
    // 返回可執行的 MyCommand 命令格式
    c.String(http.StatusOK, "/module_success_text %v", data)
}
```

#### Step 5: 模塊入口 (new_module.go)
```go
package newmodule

import (
    "log"
    "github.com/gin-gonic/gin"
    common "neko-suite/go"
)

func RegisterRoutes(router *gin.Engine, configPath string) {
    // 1. 加載配置
    config, err := LoadConfig(configPath)
    if err != nil {
        log.Fatal("加載配置失敗:", err)
    }
    
    // 2. 創建存儲
    commonStorage, err := common.NewStorage(config.Storage)
    if err != nil {
        log.Fatal("創建存儲失敗:", err)
    }
    
    storage := NewModuleStorageAdapter(commonStorage)
    
    // 3. 創建服務
    service := NewUnifiedService(config, storage)
    
    // 4. 創建處理器
    handler := NewHandler(service)
    
    // 5. 註冊路由
    moduleRoutes := router.Group("/new_module")
    {
        moduleRoutes.GET("/action", handler.HandleAction)
        // 添加更多路由...
    }
}
```

### 3. 配置文件模板 (config.yaml)
```yaml
# 統一存儲配置
storage:
  type: "yaml"
  data_path: "./userdata"

# 限制模式配置（可選）
limit_modes:
  count: 10          # 次數限制
  time: "1d"         # 時間週期：1d(天), 1w(週), 1m(月)
  refresh_at_time: "00:00"  # 每日重置時間

# 模塊特定配置
module_settings:
  feature_enabled: true
  max_value: 100
```

## 🔧 集成到主程序

在 `main.go` 中添加：
```go
import "neko-suite/new_module"

func main() {
    router := gin.Default()
    
    // 註冊新模塊
    newmodule.RegisterRoutes(router, "./new_module/config.yaml")
    
    router.Run(":8080")
}
```

## ✅ 檢查清單

開發新模塊時，請確保：

- [ ] 使用統一的存儲接口 (`common.CommonStorage`)
- [ ] 集成限制模式管理器
- [ ] 集成獎勵管理器  
- [ ] 遵循命名規範 (`模塊名:功能名`)
- [ ] 提供配置文件和示例
- [ ] 統一的 API 響應格式
- [ ] 適當的錯誤處理
- [ ] 編寫基本測試

## 📚 參考現有模塊

- **Wish 模塊** (`./wish/`): 抽獎系統實現參考
- **Event 模塊** (`./event/`): 活動系統實現參考

## 🤝 開發支持

如有問題，請參考：
- 完整架構文檔：`ARCHITECTURE.md`
- 項目結構說明：`PROJECT_STRUCTURE.md`
- 現有模塊實現代碼

---
**按照這個指南，您可以快速創建符合項目架構的新功能模塊！**
