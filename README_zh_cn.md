# NekoSuite Bukkit

[正體中文](README_zh_tw.md) | [简体中文](README_zh_cn.md) | [English](README.md)

NekoSuite 是一个面向生存/休闲服务器的整合型 Bukkit 插件，提供祈愿池、活动中心、经验银行、特权商店、邮件、传送、技能与多种小游戏等功能模块，内建多语言与可视化菜单。

## 功能亮点
- 祈愿池：可设置多个奖池，支持批量抽取与奖励展示。
- 活动系统：玩家查看/参与活动、领取奖励；支持权限与条件检查。
- 经验银行：存取、转账、兑换经验，含菜单操作。
- 特权商店：按等级购买特权，支持 Vault 经济。
- 邮件中心：玩家互寄或管理员模板群发，含 GUI 邮件盒。
- 传送系统：玩家传送请求、锁定/解锁、状态查询，支持坐标直传。
- 技能与小游戏：策略游戏、随机传送竞赛、生存竞技场、钓鱼比赛等模块。
- 公告与主菜单：公告推送、导航菜单、帮助 GUI。
- 多语言：内建 zh_tw、zh_cn、en_us，可通过 `/language` 切换。

## 运行环境
- Java 8 (JDK 1.8)
- Spigot/Paper 1.16.5（基于 `spigot-api:1.16.5-R0.1-SNAPSHOT`）
- Vault（经济/权限；标记为 softdepend，但经济相关功能需要）

## 安装
1. 在服务器端执行 `mvn package -q` 生成 `target/nekosuite-bukkit-0.1.0-SNAPSHOT.jar`。
2. 将 JAR 放入服务器的 `plugins/` 目录，确保已安装 Vault 及经济插件。
3. 启动服务器，插件会在 `plugins/NekoSuite/` 生成默认配置与语言文件。

## 快速开始
- 主菜单：`/neko` 或 `/nekomenu`
- 帮助 GUI：`/nekohelp`
- 祈愿池：`/wish <池名> [次数]` 或 `/wish menu`
- 活动：`/event [活动ID]` 或 `/event menu`
- 经验银行：`/exp [menu|deposit|withdraw|pay|exchange]`
- 特权商店：`/buy <类型> <等级>` 或 `/buy menu`
- 邮件：`/mail [menu|claim|delete] [邮件ID]`
- 传送：`/ntp <玩家>|accept|deny|toggle|cancel|<x> <y> <z> [世界]`

更多指令请参见 `main/resources/plugin.yml`。

## 配置与语言
- 主要配置位于 `main/resources/*.yml`，启动后会复制到插件数据目录；常用文件：
  - `wish_config.yml`、`event_config.yml`、`exp_config.yml`、`buy_config.yml`、`mail_config.yml`
  - `menu_layout.yml`、`tab_config.yml`、`announcements.yml`、`artifact_rewards_config.yml`
  - `random_teleport_config.yml`、`survival_arena_config.yml`、`fishing_contest_config.yml`
- 语言文件：`language.yml` 为切换表，`lang/zh_tw.yml`、`lang/zh_cn.yml`、`lang/en_us.yml` 提供文本。
- 帮助菜单定义：`help/neko_help.yml` 描述 GUI 布局与指令链接。

## 构建与测试
- 构建：`mvn package -q`
- 产物：`target/nekosuite-bukkit-0.1.0-SNAPSHOT.jar`
- 建议在测试服务器上以 OP 身份验证各模块指令和 GUI 显示，并检查 Vault 经济是否正常扣款。

## 许可证
本项目遵循根目录的 `LICENSE` 条款。
