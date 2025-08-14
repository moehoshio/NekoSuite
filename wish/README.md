# Wish 模塊 - 祈願系統

## 概述

Wish 模塊是 NekoSuite 的祈願系統，支持多種祈願池、祈願券、保底機制等功能。

## 功能特性

- 🎲 **多祈願池**: 支持不同類型的祈願池
- 🎫 **祈願券系統**: 可用祈願券抵扣費用
- 🛡️ **保底機制**: 祈願次數達到上限必得指定獎勵
- 📊 **統計查詢**: 查詢祈願歷史和統計信息
- ⚙️ **靈活配置**: 通過 YAML 配置文件自定義所有內容

## API 接口

### 基礎 URL
```
GET /wish/?param1=value1&param2=value2
```

### 1. 祈願操作

**端點**: `/wish/?action=wish`

**參數**:
- `user` (必需): 用戶名
- `pool` (必需): 祈願池名稱
- `value` (必需): 祈願次數

**示例**:
```
GET /wish/?action=wish&user=player1&pool=GlimmeringPrayer&value=1
```

**成功響應**:
```
/handle_wish_GlimmeringPrayer 100 1 iron_ingot diamond emerald
```

**錯誤響應**:
```
/wish_error_text insufficient_balance
/wish_error_text invalid_wish_count
```

### 2. 查詢操作

**端點**: `/wish/?action=query`

**參數**:
- `user` (必需): 用戶名
- `pool` (必需): 祈願池名稱
- `mode` (可選): `text` 或 `gui`，默認 `text`

**示例**:
```
GET /wish/?action=query&user=player1&pool=StarPath&mode=text
```

**響應**:
```
/wish_query_result StarPath 45 3
```
格式: `/wish_query_result <池名> <已祈願次數> <可用祈願券數量>`

## 錯誤類型

| 錯誤類型 | 描述 |
|---------|------|
| `invalid_user_name` | 用戶名無效或為空 |
| `invalid_action` | 無效的操作類型 |
| `invalid_wish_type` | 無效的祈願池類型 |
| `invalid_wish_value` | 無效的祈願次數 |
| `invalid_wish_count` | 祈願次數超出限制 |
| `insufficient_balance` | 餘額不足 |
| `insufficient_tickets` | 祈願券不足 |
| `wish_failed` | 祈願處理失敗 |
| `query_failed` | 查詢失敗 |
| `status_query_failed` | 狀態查詢失敗 |

## 祈願券類型

| 中文名稱 | 英文標識符 | 適用池 |
|---------|-----------|-------|
| 曦光纸签 | `glimmering_ticket` | GlimmeringPrayer |
| 星纹祈愿券 | `star_wish_ticket` | StarPath, StarryMirage |
| 红誓之符 | `red_oath_ticket` | OathOfTheRedRose |

## 祈願池配置

### 池類型

1. **StarPath** (星途) - 常驻池
2. **GlimmeringPrayer** (微光之愿) - 每日池
3. **StarryMirage** (星光如梦) - 限時池
4. **OathOfTheRedRose** (红玫瑰誓约) - 節日池

### 配置文件

編輯 `wish/config.yaml` 來配置祈願池:

```yaml
pools:
  StarPath:
    max_count: 200        # 保底次數
    cost:
      "1": 80            # 單抽費用
      "5": 400           # 五連抽費用
    items:
      iron_ingot5: 25.0  # 物品: 概率
      diamond2: 15.0
      # ...
    guarantee_items:     # 保底物品
      KirinOverlordBlazingArmor: 100.0
```

## MyCommand 腳本

前端腳本位於 `wish/wish.yml`，包含以下主要命令:

- `/wish` - 祈願主命令
- `/go_wish <池> <次數>` - 執行祈願
- `/go_wish_query <池> <模式>` - 查詢祈願信息

### 回調命令

祈願成功後會調用相應的回調命令:

```yaml
handle_wish_GlimmeringPrayer:
  command: /handle_wish_GlimmeringPrayer
  permission-required: false

handle_wish_StarPath:
  command: /handle_wish_StarPath
  permission-required: false
```

## 數據存儲

用戶數據存儲在 `userdata/<用戶名>.yml`:

```yaml
wish:
  counts:
    StarPath: 45        # 祈願次數記錄
    GlimmeringPrayer: 2
  wish_tickets:
    star_wish_ticket: 3 # 祈願券數量
    glimmering_ticket: 1
```

## 使用示例

### 基本祈願流程

1. 用戶在遊戲中執行 `/wish StarPath 1`
2. MyCommand 調用 `/wish/?action=wish&user=player1&pool=StarPath&value=1`
3. 後端處理祈願邏輯，返回獎勵命令
4. MyCommand 執行返回的命令給予玩家獎勵

### 查詢祈願狀態

1. 用戶執行 `/wish query StarPath`  
2. MyCommand 調用 `/wish/?action=query&user=player1&pool=StarPath`
3. 後端返回 `/wish_query_result StarPath 45 3`
4. MyCommand 解析並顯示: "星途祈願: 已祈願45次，剩餘祈願券3張"

## 開發指南

### 添加新祈願池

1. 在 `config.yaml` 中添加新池配置
2. 在 `wish.yml` 中添加對應的回調命令
3. 在 `getTicketTypeByPool` 方法中添加祈願券映射 (如需要)

### 自定義物品獎勵

編輯池配置中的 `items` 部分:

```yaml
items:
  minecraft:diamond: 10.0     # MC 原版物品
  customitem:sword: 5.0       # 自定義物品
  "§6黃金劍": 2.0              # 帶格式的物品名
```

## 故障排除

### 常見問題

1. **祈願無響應**: 檢查用戶名是否正確，池名是否存在
2. **餘額扣除但無獎勵**: 檢查 MyCommand 回調命令配置
3. **祈願券無法使用**: 確認祈願券類型映射是否正確

### 調試

啟用 Go 服務器日誌查看詳細錯誤信息:

```bash
./neko-suite.exe -debug
```
