# NekoSuite Java Plugin

NekoSuite 現在以 **Bukkit/Spigot (JDK 1.8)** 插件形式直接提供功能，無需再透過 HTTP、PHP、Go 或 MyCommand 腳本。

## 模組

- **wish**：祈願池、保底、券、GUI（/wish, /wishquery, /wishmenu）
- **event**：活動獎勵、權重/全發放、期間限制（/eventcheck, /eventparticipate, /eventmenu）
- **exp**：經驗存取/轉帳/兌換，含 GUI（/exp, /expmenu）
- **cdk**：單次/限次/無限 CDK，期間限制、全局與每人次數（/cdk）
- **buy**：特權購買，扣款與 GroupManager 指令授權、到期回收，含可選 GUI（/buy, /buymenu）

所有模組使用統一的獎勵模型，支援多動作、多 `commands`、數量範圍與子列表。

## 安裝與構建

1. 需求：JDK 1.8，Maven，可存取 Paper/Spigot Maven 倉庫。
2. 構建：
   ```bash
   cd java-plugin
   mvn package -DskipTests
   ```
3. 將產物 `java-plugin/target/nekosuite-bukkit-*.jar` 放入伺服器 `plugins/`。
4. 啟動伺服器後，於 `plugins/NekoSuite/` 下調整：
   - `wish_config.yml`
   - `event_config.yml`
   - `exp_config.yml`
   - `cdk_config.yml`
   - `buy_config.yml`
   - `messages.yml`

## 指令摘要

- `/wish <pool> [count]`，`/wishquery <pool>`，`/wishmenu`
- `/eventcheck`，`/eventparticipate <id>`，`/eventmenu`
- `/exp <balance|save|withdraw|pay|menu|exchange>`，`/expmenu`
- `/cdk <code>`
- `/buy <productId>`，`/buymenu`

## 測試

目前無自動化測試；建議在可連網的環境下使用相依的 Paper/Spigot 介面與 Vault 進行實機驗證。
