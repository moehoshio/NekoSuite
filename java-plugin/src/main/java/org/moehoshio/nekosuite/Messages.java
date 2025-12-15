package org.moehoshio.nekosuite;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple messages/i18n loader backed by messages.yml with fallback defaults.
 */
public class Messages {

    private static final Map<String, String> DEFAULTS = new HashMap<String, String>();

    static {
        DEFAULTS.put("common.only_player", "&c此命令僅玩家可用。");
        DEFAULTS.put("wish.usage", "&e用法: /wish <pool> [count] 或 /wish query <pool>");
        DEFAULTS.put("wish.query.usage", "&e用法: /wish query <pool>");
        DEFAULTS.put("wish.success", "&a[NekoSuite] 祈願成功，獲得: {rewards}");
        DEFAULTS.put("wish.failure", "&c[NekoSuite] 祈願失敗: {reason}");
        DEFAULTS.put("wish.pool_missing", "&c祈願池不存在。");
        DEFAULTS.put("wish.not_active", "&c祈願池未開啟或已結束");
        DEFAULTS.put("wish.count_invalid", "&c祈願次數無效。");
        DEFAULTS.put("wish.ticket_insufficient", "&c祈願券不足 ({owned}/{needed})");
        DEFAULTS.put("wish.status", "&b[NekoSuite] 池: {pool} 已祈願: {count} 剩餘券: {tickets}");
        DEFAULTS.put("event.no_available", "&e[NekoSuite] 當前沒有可用的活動。");
        DEFAULTS.put("event.header", "&b[NekoSuite] 可參與活動:");
        DEFAULTS.put("event.entry.available", "&f- {name} ({id}): &a可參與");
        DEFAULTS.put("event.entry.limited", "&f- {name} ({id}): &c已達上限");
        DEFAULTS.put("event.participate.usage", "&e用法: /eventparticipate <eventId>");
        DEFAULTS.put("event.reward", "&a[NekoSuite] 活動獎勵: {rewards}");
        DEFAULTS.put("event.failure", "&c[NekoSuite] 無法參與: {reason}");
        DEFAULTS.put("event.error.not_found", "活動不存在");
        DEFAULTS.put("event.error.closed", "活動未開啟");
        DEFAULTS.put("event.error.limit", "已達到參與限制");
        DEFAULTS.put("exp.usage", "&e用法: /exp <balance|save|withdraw|pay|menu|exchange>");
        DEFAULTS.put("exp.balance", "&7系統賬戶: &d{stored} &7xp ，身上: &d{carried} &7xp");
        DEFAULTS.put("exp.deposit.success", "&a已存入 {amount} xp ，賬戶餘額 {stored} xp");
        DEFAULTS.put("exp.withdraw.success", "&a已取出 {amount} xp ，賬戶餘額 {stored} xp");
        DEFAULTS.put("exp.transfer.success", "&a已向 {target} 轉帳 {amount} xp ，餘額 {stored} xp");
        DEFAULTS.put("exp.transfer.invalid_target", "&c無法找到該玩家。");
        DEFAULTS.put("exp.transfer.self", "&c不能轉帳給自己。");
        DEFAULTS.put("exp.not_enough_player", "&c您沒有足夠的經驗。");
        DEFAULTS.put("exp.not_enough_stored", "&c賬戶經驗不足。");
        DEFAULTS.put("exp.amount_invalid", "&c數量無效。");
        DEFAULTS.put("exp.exchange.success", "&a兌換成功: {id} ，消耗 {cost} xp ，餘額 {stored} xp");
        DEFAULTS.put("exp.exchange.limit_daily", "&c達到每日兌換上限。");
        DEFAULTS.put("exp.exchange.limit_total", "&c達到總兌換上限。");
        DEFAULTS.put("exp.exchange.insufficient", "&c經驗不足，需 {cost} xp。");
        DEFAULTS.put("menu.wish.title", "&d祈願選單");
        DEFAULTS.put("menu.event.title", "&d活動選單");
        DEFAULTS.put("menu.exp.title", "&e經驗系統");
        DEFAULTS.put("menu.close", "&c關閉");
        DEFAULTS.put("cdk.usage", "&e用法: /cdk <cdk代碼>");
        DEFAULTS.put("cdk.success", "&a兌換成功: {rewards}");
        DEFAULTS.put("cdk.failure", "&c兌換失敗: {reason}");
        DEFAULTS.put("cdk.invalid", "&c無效的cdk");
        DEFAULTS.put("cdk.expired", "&c該cdk已過期");
        DEFAULTS.put("cdk.used", "&c您已兌換過此cdk");
        DEFAULTS.put("cdk.limit", "&c該cdk已被兌換完");
        DEFAULTS.put("cdk.limit_user", "&c您已達到此cdk的兌換次數限制");
        DEFAULTS.put("cdk.not_active", "&c該cdk當前不可用");
        DEFAULTS.put("buy.usage", "&e用法: /buy <productId>");
        DEFAULTS.put("buy.not_found", "&c未找到此商品");
        DEFAULTS.put("buy.already_active", "&c您已經擁有該特權，尚未過期");
        DEFAULTS.put("buy.success", "&a購買成功: {product}");
        DEFAULTS.put("buy.expired", "&e已移除過期特權: {product}");
    }

    private final JavaPlugin plugin;
    private YamlConfiguration config;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public final void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String getRaw(String key) {
        String value = config.getString(key);
        if (value == null) {
            value = DEFAULTS.get(key);
        }
        if (value == null) {
            value = key;
        }
        return value;
    }

    public String format(String key) {
        return color(getRaw(key));
    }

    public String format(String key, Map<String, String> placeholders) {
        String text = getRaw(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return color(text);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

}
