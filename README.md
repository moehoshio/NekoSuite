# NekoSuite Bukkit

[正體中文](README_zh_tw.md) | [简体中文](README_zh_cn.md) | [English](README.md)

NekoSuite is an all-in-one Bukkit plugin for survival/casual servers. It bundles wish pools, event hub, experience bank, perks shop, mail, teleportation, skills, and several minigames, with built-in multilingual support and GUI menus.

## Highlights
- Wish pools: multiple pools, batch draws, reward previews.
- Events: browse/join events and claim rewards with permission/condition checks.
- Experience bank: deposit, withdraw, transfer, and exchange XP via GUI.
- Perks shop: level-based perks, integrates with Vault economy.
- Mail center: player mail and admin templates with a GUI inbox.
- Teleportation: TP requests, lock/unlock, status checks, and direct coordinate teleports.
- Skills and minigames: strategy game, random teleport race, survival arena, fishing contest, and more.
- Announcements and menus: broadcast announcements, navigation menu, help GUI.
- Localization: ships zh_tw, zh_cn, en_us; switch with `/language`.

## Requirements
- Java 8 (JDK 1.8)
- Spigot/Paper 1.16.5 (based on `spigot-api:1.16.5-R0.1-SNAPSHOT`)
- Vault (economy/permissions; marked soft-depend but needed for economy features)

## Installation
1. Run `mvn package -q` to produce `target/nekosuite-bukkit-0.1.0-SNAPSHOT.jar`.
2. Drop the JAR into the server `plugins/` folder; ensure Vault and an economy plugin are installed.
3. Start the server; defaults and language files will be copied to `plugins/NekoSuite/`.

## Quick Start
- Main menu: `/neko` or `/nekomenu`
- Help GUI: `/nekohelp`
- Wish: `/wish <pool> [count]` or `/wish menu`
- Events: `/event [eventId]` or `/event menu`
- Experience bank: `/exp [menu|deposit|withdraw|pay|exchange]`
- Perks shop: `/buy <type> <level>` or `/buy menu`
- Mail: `/mail [menu|claim|delete] [mailId]`
- Teleport: `/ntp <player>|accept|deny|toggle|cancel|<x> <y> <z> [world]`

See `main/resources/plugin.yml` for the full command list.

## Configuration & Localization
- Core configs live in `main/resources/*.yml` and are copied to the plugin data folder on first run; key files include:
  - `wish_config.yml`, `event_config.yml`, `exp_config.yml`, `buy_config.yml`, `mail_config.yml`
  - `menu_layout.yml`, `tab_config.yml`, `announcements.yml`, `artifact_rewards_config.yml`
  - `random_teleport_config.yml`, `survival_arena_config.yml`, `fishing_contest_config.yml`
- Language files: `language.yml` selects locale; `lang/zh_tw.yml`, `lang/zh_cn.yml`, `lang/en_us.yml` provide strings.
- Help menu layout: `help/neko_help.yml` defines GUI layout and command links.

## Build & Test
- Build: `mvn package -q`
- Artifact: `target/nekosuite-bukkit-0.1.0-SNAPSHOT.jar`
- Recommended: verify commands and GUIs on a test server as OP, and ensure Vault economy charges correctly.

## License
This project follows the terms in the root `LICENSE` file.
