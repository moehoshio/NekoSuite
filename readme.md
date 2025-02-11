# NekoSuite

- [English](./readme_en.md)，[简体中文](./readme_zh-cn.md)

NekoSuite 是一個Minecraft伺服器的功能解決方案，目前實現了 `CDK` 兌換碼、`EXP` 經驗系統和 `WISH` 祈願抽獎系統。它是基於插件 Bukkit 插件 [MYCommand](https://dev.bukkit.org/projects/mycommand) 編寫的腳本範例。核心功能由PHP實現，經過測試的PHP版本為7.x和8.1。

具體來說，目前它有以下功能：

1. **CDK系統**：
    - 輸入CDK兌換玩家的特定獎勵！
    - 三種CDK類型：`僅能使用一次` 、`每位用戶可兌換一次` 、`限制總計可兌換次數（每位用戶一次）`。
    - 可配置`過期時間`和任意獎勵。

2. **WISH系統**：
    - `祈願抽獎`，可花費一定貨幣（需要 Vault）來抽取特定獎勵。
    - 具有累計抽獎N次必定中獎的`保底機制`。
    - 機率和獎品可任意調整，包括如：物品獎勵、藥水或效果、第三方模組內容等。

3. **EXP系統**：
    - 經驗系統，可以 `存取經驗`、`轉賬經驗`、`經驗商店兌換物品`等。
    - 將經驗存儲到系統以免玩家死亡後經驗消失！同時可以使用經驗兌換指定獎勵。

## 先決條件

- A Web Server（e.g Nginx）
- PHP 7.x/8.1
- MyCommand Plugin 5.x

### 安全性提醒

請勿將PHP公開暴露，應當只能由Minecraft Server伺服器訪問。  

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
- `/wish info <類型>` 查詢卡池資訊。

## 自定義內容

1. **自定CDK**

- 編輯`cdk_core.php`中的`CDK_CONFIG`，添加或修改CDK配置。
- 再將`cdk.yml`中的`give_cdk_reward`更改為您的CDK獎勵即可。

2. **自定EXP兌換物品**

- 編輯`exp.php`中的`ITEMS`，添加或修改兌換物品配置。
- 然後更改`exp.yml`的 `exp_exchange` 中的獎勵即可。

3. **自定WISH卡池**

- 編輯`wish_core.php`中的`WISH_CONFIG`，添加或修改卡池配置。
- 再將`wish.yml`對應的祈願類型中的獎勵變更即可，通常來說它的名稱是`give_wish_xxx`，例如：`give_wish_daily`

4. **修改玩家數據**

- 默認數據結構大致如下：
    - root/
        - usr/
            - $user(用戶名)
                - 這裏存儲着該玩家的資訊，比如： `exp.log`代表了該玩家在系統中的經驗數值，`cdk_user_once.log`存儲着該玩家使用過的CDK。
                - 而祈願的記錄則為 `wish_core.php`中配置的 `log_file`，如： `normal.log`則為 normal祈願的次數。

    其餘內容大部分也可自由配置存儲位置，只需要在對應的配置中指定即可。
