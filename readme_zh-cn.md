# NekoSuite

- [English](./readme_en.md)，[繁體中文](./readme.md)

NekoSuite 是一个Minecraft服务器的功能解决方案，目前实现了 `CDK` 兑换码、`EXP` 经验系统和 `WISH` 祈愿抽奖系统。它是基于插件 Bukkit 插件 [MYCommand](https://dev.bukkit.org/projects/mycommand) 编写的脚本范例。核心功能由PHP实现，经过测试的PHP版本为7.x和8.1。

具体来说，目前它有以下功能：

1. **CDK系统**：
    - 输入CDK兑换玩家的特定奖励！
    - 三种CDK类型：`仅能使用一次` 、`每位用户可兑换一次` 、`限制总计可兑换次数（每位用户一次）`。
    - 可配置`过期时间`和任意奖励。

2. **WISH系统**：
    - `祈愿抽奖`，可花费一定货币（需要 Vault）来抽取特定奖励。
    - 具有累计抽奖N次必定中奖的`保底机制`。
    - 概率和奖品可任意调整，包括如：物品奖励、药水或效果、第三方模组内容等。

3. **EXP系统**：
    - 经验系统，可以 `存取经验`、`转账经验`、`经验商店兑换物品`等。
    - 将经验存储到系统以免玩家死亡后经验消失！同时可以使用经验兑换指定奖励。

## 先决条件

- A Web Server（e.g Nginx）
- PHP 7.x/8.1
- MyCommand Plugin 5.x

### 安全性提醒

请勿将PHP公开暴露，应当只能由Minecraft Server服务器访问。  

## 部署

1. **部署YML文件**
   将YML文件拷贝到插件文件夹下，例如：`/plugins/MyCommand/commands/xxx.yml`

2. **部署PHP文件**
   将PHP文件部署到Web Server上，并修改YML文件中的请求URL，例如：
   ```yaml
   url: http://example.com/wish/wish.php?...
   ```

3. **修改PHP的数据存储路径**
   将`xxx_core.php`中的配置信息修改为您的路径和配置。

4. **修改URL**
   修改YML文件中的`go_xxx`中的URL为你的Web Server地址，例如 `http://127.0.0.1/wish/wish.php?`。

## 使用方法

1. **CDK系统**

- 使用命令：`/cdk ex <cdk_code>`
- 兑换CDK并获取奖励。

2. **EXP系统**

- `/xp save` 将所有经验存入系统。
- `/xp raw <数量>` 从系统取出指定数量的经验。
- `/xp pay <玩家> <数量>` 转账经验给指定玩家。
- `/xp info` 查询经验。
- `/xp shop` 打开经验商店。

3. **WISH系统**

- `/wish <类型> <次数>` 进行祈愿。
- `/wish info <类型>` 查询卡池信息。

## 自定义内容

1. **自定CDK**

- 编辑`cdk_core.php`中的`CDK_CONFIG`，添加或修改CDK配置。
- 再将`cdk.yml`中的`give_cdk_reward`更改为您的CDK奖励即可。

2. **自定EXP兑换物品**

- 编辑`exp.php`中的`ITEMS`，添加或修改兑换物品配置。
- 然后更改`exp.yml`的 `exp_exchange` 中的奖励即可。

3. **自定WISH卡池**

- 编辑`wish_core.php`中的`WISH_CONFIG`，添加或修改卡池配置。
- 再将`wish.yml`对应的祈愿类型中的奖励变更即可，通常来说它的名称是`give_wish_xxx`，例如：`give_wish_daily`

4. **修改玩家数据**

- 默认数据结构大致如下：
    - root/
        - usr/
            - $user(用户名)
                - 这里存储着该玩家的信息，比如： `exp.log`代表了该玩家在系统中的经验数值，`cdk_user_once.log`存储着该玩家使用过的CDK。
                - 而祈愿的记录则为 `wish_core.php`中配置的 `log_file`，如： `normal.log`则为 normal祈愿的次数。

    其余内容大部分也可自由配置存储位置，只需要在对应的配置中指定即可。
