# NekoSuite API 文檔

NekoSuite 是一個模塊化的 Minecraft Server 拓展功能專案，使用 MyCommand 作為前端，Go 作為後端。

## API 概述

- **基礎 URL**: `http://localhost:8080`
- **響應格式**: 遊戲命令

## 編碼規範

- 所有 API 響應必須只包含 ASCII 字符
- 響應格式為可執行的 MyCommand 命令：`/command_name param1 param2`
- 錯誤響應格式：`/module_error_text error_type`  
- 成功響應格式：根據具體模塊的命令格式
- 中文內容由前端 MyCommand 腳本處理
- 禁止返回 JSON 格式響應（MyCommand call_url 不支援 UTF8）

## 通用錯誤類型

所有模塊共享以下錯誤類型：

| 錯誤類型 | 描述 | 前端處理建議 |
|---------|------|-------------|
| `invalid_user_name` | 用戶名為空或無效 | 提示用戶輸入有效用戶名 |
| `invalid_action` | 操作類型無效或不支援 | 提示用戶使用正確的操作類型 |

## Wish 模塊 API

### 基礎路徑
```
GET /wish/
```

### 操作類型

#### 1. 祈願操作 (`action=wish`)

**請求參數**:
- `action=wish` (必需)
- `user` (必需): 用戶名
- `pool` (必需): 祈願池名稱
- `value` (可選): 祈願次數，默認為 1

**祈願池名稱**:
- `GlimmeringPrayer` - 微光之願（每日）
- `StarPath` - 星途（常駐）
- `StarryMirage` - 星光如梦
- `OathOfTheRedRose` - 红玫瑰誓约

**成功響應格式**:
```
/handle_wish_<池名> <費用> <次數> <物品1> <物品2> <物品3> <物品4> <物品5>
```

**響應示例**:
```
/handle_wish_GlimmeringPrayer 0 1 iron_ingot5 diamond2
/handle_wish_StarPath 80 1 netherite_scrap potion_power
```

**錯誤響應**:
```
/wish_error_text <錯誤類型>
```

**錯誤類型**:

| 錯誤類型 | 描述 | 觸發條件 |
|---------|------|----------|
| `invalid_user_name` | 用戶名無效 | user 參數為空 |
| `invalid_action` | 操作無效 | action 參數不是支援的操作 |
| `invalid_wish_type` | 祈願池無效 | pool 參數不是有效的祈願池 |
| `invalid_wish_value` | 祈願次數無效 | value 不是正整數 |
| `invalid_wish_count` | 祈願次數超限 | 超過單次祈願最大次數限制 |
| `insufficient_balance` | 餘額不足 | 用戶餘額不足以支付費用 |
| `insufficient_tickets` | 祈願券不足 | 用戶祈願券不足 |
| `wish_failed` | 祈願失敗 | 其他祈願處理錯誤 |

#### 2. 查詢操作 (`action=query`)

**請求參數**:
- `action=query` (必需)
- `user` (必需): 用戶名
- `pool` (必需): 祈願池名稱
- `mode` (可選): 查詢模式，`text` 或 `gui`，默認 `text`

**成功響應格式**:
```
/wish_query_result <池名> <已祈願次數> <券數量>
```

**響應示例**:
```
/wish_query_result GlimmeringPrayer 5 3
/wish_query_result StarPath 45 10
```

**錯誤類型**:
| 錯誤類型 | 描述 |
|---------|------|
| `invalid_wish_type` | 祈願池無效 |
| `status_query_failed` | 查詢狀態失敗 |
| `query_failed` | 查詢操作失敗 |

### 祈願券類型映射

| 祈願池 | 祈願券類型標識符 | 原中文名稱 |
|-------|----------------|-----------|
| `GlimmeringPrayer` | `glimmering_ticket` | 曦光纸签 |
| `StarPath`, `StarryMirage` | `star_wish_ticket` | 星纹祈愿券 |
| `OathOfTheRedRose` | `red_oath_ticket` | 红誓之符 |

### API 調用示例

```bash
# 執行祈願
curl "http://localhost:8080/wish/?action=wish&user=player1&pool=GlimmeringPrayer&value=1"
# 響應: /handle_wish_GlimmeringPrayer 0 1 iron_ingot5

# 查詢祈願狀態
curl "http://localhost:8080/wish/?action=query&user=player1&pool=StarPath&mode=text"
# 響應: /wish_query_result StarPath 23 5

# 錯誤示例 - 無效用戶名
curl "http://localhost:8080/wish/?action=wish&user=&pool=StarPath&value=1"
# 響應: /wish_error_text invalid_user_name
```

## Event 模塊 API

### 基礎路徑
```
GET /event/
```

### 操作類型

#### 1. 檢查活動 (`action=check`)

**請求參數**:
- `action=check` (必需)
- `user` (必需): 用戶名

**成功響應**:
- 如果有可執行命令: 返回第一個可執行命令
- 如果沒有可用活動: `/event_error_text no_available_events`
- 如果沒有命令可執行: `/event_error_text no_commands_to_execute`

#### 2. 參與活動 (`action=participate`)

**請求參數**:
- `action=participate` (必需)
- `user` (必需): 用戶名
- `event_id` (必需): 活動ID

**成功響應**:
- 如果有命令可執行: 返回第一個命令
- 否則: `/event_participate_success`

#### 3. 領取獎勵 (`action=claim`)

**請求參數**:
- `action=claim` (必需)
- `user` (必需): 用戶名
- `event_id` (必需): 活動ID

**成功響應**:
- 如果有命令可執行: 返回第一個命令
- 否則: `/event_claim_success`

#### 4. 查詢狀態 (`action=status`)

**請求參數**:
- `action=status` (必需)
- `user` (必需): 用戶名
- `event_id` (必需): 活動ID

**成功響應**:
```
/event_status_success     # 活動狀態正常
/event_status_failed      # 活動狀態異常
```

#### 5. 列出活動 (`action=list`)

**請求參數**:
- `action=list` (必需)
- `user` (必需): 用戶名

**成功響應**:
```
/event_show_list <用戶名>
```

### 錯誤響應格式
```
/event_error_text <錯誤類型>
```

### 錯誤類型

| 錯誤類型 | 描述 | 觸發條件 |
|---------|------|----------|
| `invalid_user_name` | 用戶名無效 | user 參數為空 |
| `invalid_action` | 操作無效 | action 參數不支援 |
| `invalid_event_id` | 活動ID無效 | event_id 參數為空 |
| `unknown_action` | 未知操作 | action 參數不在支援列表中 |
| `no_available_events` | 無可用活動 | 沒有找到可用的活動 |
| `no_commands_to_execute` | 無命令可執行 | 沒有需要執行的命令 |
| `participation_failed` | 參與失敗 | 參與活動時發生錯誤 |
| `claim_failed` | 領取失敗 | 領取獎勵時發生錯誤 |
| `status_query_failed` | 狀態查詢失敗 | 查詢活動狀態失敗 |

### API 調用示例

```bash
# 檢查活動
curl "http://localhost:8080/event/?action=check&user=player1"
# 響應: /give player1 diamond 1 或 /event_error_text no_available_events

# 參與活動
curl "http://localhost:8080/event/?action=participate&user=player1&event_id=daily_login"
# 響應: /event_participate_success

# 查詢狀態
curl "http://localhost:8080/event/?action=status&user=player1&event_id=daily_login"
# 響應: /event_status_success

# 錯誤示例
curl "http://localhost:8080/event/?action=invalid&user=player1"
# 響應: /event_error_text unknown_action
```

## 系統 API

### 健康檢查
```
GET /health
```

**響應格式**: JSON
```json
{"status": "ok"}
```

### 首頁
```
GET /
```

**響應格式**: JSON
```json
{
  "message": "NekoSuite API Server",
  "modules": ["wish", "event"]
}
```

## 前端集成指南

### MyCommand 腳本集成

1. **錯誤處理**: 根據返回的錯誤類型顯示對應的中文錯誤信息
2. **命令執行**: 直接執行返回的命令
3. **參數處理**: 確保所有參數正確 URL 編碼

### 示例 MyCommand 腳本

```yaml
wish_command:
  command: /wish
  actions:
    default:
      - call_url: 'http://localhost:8080/wish/?action=wish&user=$player&pool=GlimmeringPrayer&value=1'
      - if: '$result contains wish_error_text'
        then:
          - if: '$result contains invalid_user_name'
            then: tell: '&c用戶名無效！'
          - if: '$result contains insufficient_balance'
            then: tell: '&c餘額不足！'
        else:
          - run: '$result'
```

## 開發規範

### 新增錯誤類型流程

1. 在此文檔中添加新的錯誤類型定義
2. 更新後端代碼，使用新的錯誤類型
3. 更新前端腳本，添加對新錯誤類型的處理
4. 更新測試腳本

### 命名規範

- **錯誤類型**: 使用下劃線命名，全小寫，如 `invalid_user_name`
- **祈願池名**: 使用 PascalCase，如 `GlimmeringPrayer`
- **祈願券標識符**: 使用下劃線命名，全小寫，如 `glimmering_ticket`

## 版本信息

- **當前版本**: v1.0.0
- **Go 版本**: Go 1.19+
- **MyCommand 版本**: 5.7+
- **最後更新**: 2025-01-14
