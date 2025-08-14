# NekoSuite 項目結構

NekoSuite 是一個模塊化的 Minecraft Server 拓展功能專案，採用前後端分離架構。

## 架構概述

- **前端**: MyCommand 插件腳本 (YAML)
- **後端**: Go 語言 HTTP API 服務
- **存儲**: YAML 文件存儲用戶數據
- **通信**: HTTP API 調用，返回命令格式

## 項目根目錄結構

```
NekoSuite/
├── main.go                    # 主程序入口
├── go.mod                     # Go 模塊依賴
├── go.sum                     # 依賴校驗和
├── neko-suite.exe            # 編譯後的可執行文件
├── build.bat                 # Windows 構建腳本
├── build.sh                  # Linux 構建腳本
├── API_DOCUMENTATION.md      # 統一 API 文檔
├── PROJECT_STRUCTURE.md      # 項目結構說明（本文件）
├── README.md                 # 項目說明
└── 模塊目錄/                  # 各個功能模塊
```

## 模塊結構規範

每個功能模塊遵循以下標準結構：

```
/<模塊名>/
├── <模塊名>.go               # 模塊入口和路由註冊
├── <模塊名>.yml              # MyCommand 前端腳本
├── config.yaml               # 模塊配置文件
├── config_example.yaml       # 配置示例文件
└── go/                       # Go 後端實現
    ├── config.go             # 配置結構定義
    ├── handler.go            # HTTP 請求處理器
    ├── service.go            # 業務邏輯層
    ├── storage.go            # 數據存儲層
    └── 其他實現文件...
```

## 共用模塊

通用的共用模塊放在根目錄下的 `/go/` 目錄：

```
/go/
├── common_storage.go         # 通用存儲接口
├── storage_factory.go       # 存儲工廠
├── yaml_storage.go          # YAML 存儲實現
├── limit_mode.go            # 限制模式實現
└── reward_manager.go        # 獎勵管理器
```

## 現有模塊

### 1. Wish 模塊（祈願系統）

```
/wish/
├── wish.go                   # 模塊入口
├── wish.yml                 # MyCommand 腳本
├── config.yaml              # 祈願配置
├── config_example.yaml      # 配置示例
└── go/                      # 後端實現
    ├── config.go            # 配置結構
    ├── handler.go           # 舊版處理器
    ├── unified_handler.go   # 統一處理器
    ├── service.go           # 業務邏輯
    ├── unified_service.go   # 統一業務邏輯
    ├── storage.go           # 存儲接口
    ├── unified_storage.go   # 統一存儲
    ├── sqlite_storage.go    # SQLite 存儲
    ├── storage_factory.go   # 存儲工廠
    ├── limit_mode.go        # 限制模式
    └── ticket.go            # 祈願券系統
```

### 2. Event 模塊（活動系統）

```
/event/
├── event.go                 # 模塊入口
├── event.yml                # MyCommand 腳本
├── config.yaml              # 活動配置
├── config_example.yaml      # 配置示例
└── go/                      # 後端實現
    ├── config.go            # 配置結構
    ├── handler.go           # 請求處理器
    ├── unified_service.go   # 統一業務邏輯
    └── unified_storage.go   # 統一存儲
```

### 3. 其他模塊（PHP 實現）

以下模塊目前仍使用 PHP 實現，計劃遷移到 Go：

```
/buy/                        # 購買系統
├── buy.php
├── buy.yml
└── buy_core.php

/cdk/                        # CDK 系統
├── cdk.php
├── cdk.yml
└── cdk_core.php

/exp/                        # 經驗系統
├── exp.php
└── exp.yml

/tools/                      # 工具系統
├── tp.php
├── tp.yml
└── tools.yml
```

## 數據存儲

### 用戶數據目錄

```
/userdata/
├── <用戶名>.yml             # 用戶數據文件
├── test_user.yml            # 測試用戶數據
└── testplayer.yml           # 測試玩家數據
```

### 用戶數據結構示例

```yaml
user_id: "player1"
wish:                        # 祈願數據
  wish_counts:               # 祈願次數統計
    GlimmeringPrayer: 5
    StarPath: 23
  wish_tickets:              # 祈願券數量
    glimmering_ticket: 3
    star_wish_ticket: 10
event:                       # 活動數據
  participated_events:       # 已參與活動
    daily_login: "2025-01-14"
  claimed_rewards:           # 已領取獎勵
    - "daily_login_2025_01_14"
```

## 配置文件

### 全局配置

各模塊獨立配置，配置文件位於模塊目錄下：
- `config.yaml` - 實際配置文件
- `config_example.yaml` - 配置示例和模板

### 配置結構示例

```yaml
# 祈願池配置示例
pools:
  GlimmeringPrayer:
    counts_name: "GlimmeringPrayer"
    max_count: 100
    cost:
      "1": 0
    items:
      iron_ingot5: 25.0
      diamond2: 15.0
    limit_modes:
      count: 1
      time: 1d
      refresh_at_time: "00:00"
```

## 構建和部署

### 構建命令

```bash
# Windows
.\build.bat

# Linux/Mac
./build.sh

# 手動構建
go build -o neko-suite.exe main.go
```

### 運行

```bash
# 直接運行
./neko-suite.exe

# 後台運行
nohup ./neko-suite.exe &

# 指定端口
./neko-suite.exe -port=8080
```

## 開發工作流

### 添加新模塊

1. 創建模塊目錄結構
2. 實現 Go 後端（handler, service, storage）
3. 編寫 MyCommand 前端腳本
4. 創建配置文件和示例
5. 在 main.go 中註冊路由
6. 更新 API 文檔
7. 編寫測試

### 模塊間通信

- 模塊之間通過共用的存儲接口通信
- 使用統一的錯誤處理機制
- 共享通用的業務邏輯（如限制模式、獎勵管理）

### 版本控制

- 主分支: `main`
- 功能分支: `feature/<模塊名>-<功能描述>`
- 修復分支: `fix/<問題描述>`

## 文檔維護

- `API_DOCUMENTATION.md` - 統一的 API 接口文檔
- `PROJECT_STRUCTURE.md` - 項目結構說明（本文件）
- `README.md` - 項目概述和快速開始
- 各模塊目錄下可包含特定的技術文檔
