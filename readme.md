# NekoSuite

<!-- - [English](./readme_en.md)，[简体中文](./readme_zh-cn.md) -->

NekoSuite 是一個模塊化的 Minecraft 伺服器功能拓展系統，提供了許多功能實現：

- BUY（購買）
- CDK（兌換碼）
- WISH（祈願/抽獎）
- EXP（經驗）系統

前端是基於 Bukkit 插件 [MyCommand](https://dev.bukkit.org/projects/mycommand) 編寫的腳本，後端功能由Go實現。  

## Note

我們正在使用Go重構後端，並計劃在未來幾個月內完成所有功能的遷移。

## 项目结构

```
NekoSuite/
├── main.go                 # 主程序入口
├── go.mod                  # Go 模块文件
├── build.sh/.bat          # 构建脚本
├── wish/                   # 祈愿模块
│   ├── wish.go            # 模块入口和路由注册
│   ├── config_example.yaml# 範例祈願後端配置
│   ├── wish.yml           # MyCommand 前端脚本
│   ├── wish_gui.yml       # Wish Gui
│   ├── go/                # Go 实现子模块
│   └── API_DOCUMENTATION  # 模块API文档
├── artifacts/
├── buy/                   # 购买系统 （待重構）
├── cdk/                   # CDK 系统 （待重構）
├── exp/                   # 经验系统 （待重構）
└── tools/                 # 工具系统 （待重構）
```

具體來說，目前它有以下功能：


1. **WISH**：
    - `祈願抽獎`，可花費一定貨幣（需要 Vault）來抽取特定獎勵。
    - 累計抽獎N次必定中獎的`保底機制`。
    - `祈願券`功能：可以抵扣祈願費用，並支援按次數和只能用於特定次數的抵扣。
    - 機率和獎品可調整。

2. **CDK**：
    - 輸入CDK兌換玩家的特定獎勵！
    - 三種CDK類型：`僅能使用一次` 、`每位用戶可兌換一次` 、`限制總計可兌換次數（每位用戶一次）`。
    - 可配置`過期時間`和任意獎勵

3. **EXP**：
    - 經驗系統，可以 `存取經驗`、`轉賬經驗`、`經驗商店兌換物品`等。
    - 將經驗存儲到系統以免玩家死亡後經驗消失！同時可以使用經驗兌換指定獎勵。

4. **BUY**：
    - 購買系統，玩家可以使用貨幣（需要 Vault）購買特定服務（例如：vip、mcd等）。
    - Buy系統只提供權益管理，具體權益和功能應自行透過權限組配置。

## 先決條件

- MyCommand Plugin 5.6+
- Go 1.16+
- ~~A Web Server（e.g Nginx）~~
- ~~PHP 7.x/8.1 or other~~

### 安全性提醒

請勿將後端公開暴露，應當只能由Minecraft伺服器訪問。  

## 部署

1. **部署YML檔案**
   將YML檔拷貝到插件資料夾下，例如：`/plugins/MyCommand/commands/xxx.yml`

2. **部署PHP檔案**
   將PHP檔部署到Web Server上，並修改YML文件中的請求URL，例如：
   ```yaml
   url: http://example.com/wish/wish.php?...
   ```

3. **修改PHP的數據存儲路徑**
   將`xxx_core.php`中的配置資訊修改為您的路徑和配置。

4. **修改URL**
   修改YML文件中的`go_xxx`中的URL為你的Web Server地址，例如 `http://127.0.0.1/wish/wish.php?`。

## 使用方法

1. **CDK系統**

- 使用命令：`/cdk ex <cdk_code>`
- 兌換CDK並獲取獎勵。

2. **EXP系統**

- `/xp save` 將所有經驗存入系統。
- `/xp raw <數量>` 從系統取出指定數量的經驗。
- `/xp pay <玩家> <數量>` 轉帳經驗給指定玩家。
- `/xp info` 查詢經驗。
- `/xp shop` 打開經驗商店。

3. **WISH系統**

- `/wish <類型> <次數>` 進行祈願。
- `/wish query <類型>` 查詢卡池資訊。

## 自定義內容

1. **自定CDK**

- 編輯`cdk_core.php`中的`CDK_CONFIG`，添加或修改CDK配置。
- 再將`cdk.yml`中的`give_cdk_reward`更改為您的CDK獎勵即可。

2. **自定EXP兌換物品**

- 編輯`exp.php`中的`ITEMS`，添加或修改兌換物品配置。
- 然後更改`exp.yml`的 `exp_exchange` 中的獎勵即可。

3. **自定WISH卡池**

- 編輯`wish/config_example.yaml`，添加或修改卡池，祈願券等配置。

4. **修改玩家數據**

- 默認數據結構大致如下：
    - root/
        - usr/
            - $user(用戶名)
                - 這裏存儲着該玩家的資訊，比如： `exp.log`代表了該玩家在系統中的經驗數值，`cdk_user_once.log`存儲着該玩家使用過的CDK。
                - 而祈願的記錄則為 `wish_core.php`中配置的 `log_file`，如： `normal.log`則為 normal祈願的次數。

    其餘內容大部分也可自由配置存儲位置，只需要在對應的配置中指定即可。
