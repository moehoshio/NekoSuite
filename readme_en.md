# NekoSuite

- [繁體中文](./readme.md)，[简体中文](./readme_zh-cn.md)

NekoSuite is a Minecraft server solution that currently implements `CDK` redemption codes, `EXP` experience system, and `WISH` gacha system. It is a script example based on the Bukkit plugin [MYCommand](https://dev.bukkit.org/projects/mycommand). The core functionality is implemented in PHP, tested with PHP versions 7.x and 8.1.

Specifically, it currently has the following features:

1. **CDK System**:
    - Enter CDK to redeem specific rewards for players!
    - Three types of CDKs: `one-time use`, `one-time per user`, `limited total uses (one-time per user)`.
    - Configurable `expiry date` and any rewards.

2. **WISH System**:
    - `Gacha`, spend a certain amount of currency (requires Vault) to draw specific rewards.
    - Has a `guaranteed mechanism` where a certain number of draws guarantees a win.
    - Probability and rewards can be adjusted arbitrarily, including items, potions or effects, third-party mod content, etc.

3. **EXP System**:
    - Experience system, can `store experience`, `transfer experience`, `exchange items in the experience shop`, etc.
    - Store experience in the system to prevent loss upon player death! You can also use experience to exchange for specific rewards.

## Prerequisites

- A Web Server (e.g., Nginx)
- PHP 7.x/8.1
- MyCommand Plugin 5.x

### Security Reminder

Do not expose PHP publicly, it should only be accessible by the Minecraft Server.

## Deployment

1. **Deploy YML Files**
   Copy the YML files to the plugin folder, e.g., `/plugins/MyCommand/commands/xxx.yml`

2. **Deploy PHP Files**
   Deploy the PHP files to the Web Server and modify the request URL in the YML files, e.g.:
   ```yaml
   url: http://example.com/wish/wish.php?...
   ```

3. **Modify PHP Data Storage Path**
   Modify the configuration information in `xxx_core.php` to your path and configuration.

4. **Modify URL**
   Modify the URL in the `go_xxx` section of the YML files to your Web Server address, e.g., `http://127.0.0.1/wish/wish.php?`.

## Usage

1. **CDK System**

- Command: `/cdk ex <cdk_code>`
- Redeem CDK and get rewards.

2. **EXP System**

- `/xp save` Store all experience in the system.
- `/xp raw <amount>` Withdraw a specified amount of experience from the system.
- `/xp pay <player> <amount>` Transfer experience to a specified player.
- `/xp info` Check experience.
- `/xp shop` Open the experience shop.

3. **WISH System**

- `/wish <type> <amount>` Perform a wish.
- `/wish info <type>` Check pool information.

## Customization

1. **Customize CDK**

- Edit `cdk_core.php` in `CDK_CONFIG` to add or modify CDK configurations.
- Then change the `give_cdk_reward` in `cdk.yml` to your CDK rewards.

2. **Customize EXP Exchange Items**

- Edit `ITEMS` in `exp.php` to add or modify exchange item configurations.
- Then change the rewards in `exp_exchange` in `exp.yml`.

3. **Customize WISH Pools**

- Edit `WISH_CONFIG` in `wish_core.php` to add or modify pool configurations.
- Then change the rewards in the corresponding wish type in `wish.yml`, usually named `give_wish_xxx`, e.g., `give_wish_daily`.

4. **Modify Player Data**

- The default data structure is roughly as follows:
    - root/
        - usr/
            - $user (username)
                - Stores the player's information, e.g., `exp.log` represents the player's experience value in the system, `cdk_user_once.log` stores the CDKs used by the player.
                - The wish records are configured in `wish_core.php` as `log_file`, e.g., `normal.log` represents the number of normal wishes.

    Most other content can also be freely configured for storage location, just specify it in the corresponding configuration.
