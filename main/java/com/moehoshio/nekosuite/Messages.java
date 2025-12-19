package com.moehoshio.nekosuite;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Simple messages/i18n loader backed by language.yml settings and lang/<code>.yml translations.
 */
public class Messages {

    private static final Map<String, String> DEFAULTS = new HashMap<String, String>();

    static {
        DEFAULTS.put("common.only_player", "&c✖ 此命令僅玩家可用。");
        DEFAULTS.put("common.no_permission", "&c✖ 您沒有權限使用此命令。");
        DEFAULTS.put("common.reload_success", "&a✔ &7配置已重載。");
        DEFAULTS.put("wish.usage", "&7用法: &f/wish &6<pool> [count] &7或 &f/wish query &6<pool> &7或 &f/wish menu");
        DEFAULTS.put("wish.query.usage", "&7用法: &f/wish query &6<pool>");
        DEFAULTS.put("wish.success", "&a✔ &7祈願成功，獲得: &6{rewards}");
        DEFAULTS.put("wish.failure", "&c✖ 祈願失敗: &7{reason}");
        DEFAULTS.put("wish.pool_missing", "&c✖ 祈願池不存在。");
        DEFAULTS.put("wish.not_active", "&c✖ 祈願池未開啟或已結束");
        DEFAULTS.put("wish.count_invalid", "&c✖ 祈願次數無效。");
        DEFAULTS.put("wish.ticket_insufficient", "&c✖ 祈願券不足 &7(&6{owned}&7/&c{needed}&7)");
        DEFAULTS.put("wish.limit_reached", "&c✖ 已達到該祈願池的次數限制。");
        DEFAULTS.put("wish.economy_missing", "&c✖ 未找到經濟插件，無法扣款。");
        DEFAULTS.put("wish.cost_insufficient", "&c✖ 餘額不足，需 &6{needed}&c，當前 &6{balance}&c。");
        DEFAULTS.put("wish.cost_failure", "&c✖ 扣款失敗，請稍後再試。");
        DEFAULTS.put("wish.status", "&7池: &6{pool} &8| &7已祈願: &6{count} &8| &7剩餘券: &6{tickets}");
        DEFAULTS.put("event.no_available", "&e⚠ &7當前沒有可用的活動。");
        DEFAULTS.put("event.header", "&7可參與活動清單:");
        DEFAULTS.put("event.entry.available", "&7- &f{name} &8({id}): &a可參與");
        DEFAULTS.put("event.entry.limited", "&7- &f{name} &8({id}): &c已達上限");
        DEFAULTS.put("event.participate.usage", "&7用法: &f/eventparticipate &6<eventId>");
        DEFAULTS.put("event.reward", "&a✔ &7活動獎勵: &6{rewards}");
        DEFAULTS.put("event.failure", "&c✖ 無法參與: &7{reason}");
        DEFAULTS.put("event.error.not_found", "活動不存在");
        DEFAULTS.put("event.error.closed", "活動未開啟");
        DEFAULTS.put("event.error.limit", "已達到參與限制");
        DEFAULTS.put("exp.usage", "&7用法: &f/exp &6<balance|deposit|withdraw|pay|menu|exchange>");
        DEFAULTS.put("exp.balance", "&7賬戶餘額: &6{stored} &7xp &8| &7身上: &6{carried} &7xp");
        DEFAULTS.put("exp.deposit.success", "&a✔ &7已存入 &6{amount} &7xp，賬戶餘額 &6{stored} &7xp");
        DEFAULTS.put("exp.deposit.button", "&#80ff8a存入 &f{amount} xp");
        DEFAULTS.put("exp.deposit.lore", "&7點擊存入 &6{amount} &7xp");
        DEFAULTS.put("exp.withdraw.success", "&a✔ &7已取出 &6{amount} &7xp，賬戶餘額 &6{stored} &7xp");
        DEFAULTS.put("exp.withdraw.button", "&#ffb366取出 &f{amount} xp");
        DEFAULTS.put("exp.withdraw.lore", "&7點擊取出 &6{amount} &7xp");
        DEFAULTS.put("exp.transfer.success", "&a✔ &7已向 &6{target} &7轉帳 &6{amount} &7xp，餘額 &6{stored} &7xp");
        DEFAULTS.put("exp.transfer.invalid_target", "&c✖ 無法找到該玩家。");
        DEFAULTS.put("exp.transfer.self", "&c✖ 不能轉帳給自己。");
        DEFAULTS.put("exp.not_enough_player", "&c✖ 您沒有足夠的經驗。");
        DEFAULTS.put("exp.not_enough_stored", "&c✖ 賬戶經驗不足。");
        DEFAULTS.put("exp.amount_invalid", "&c✖ 數量無效。");
        DEFAULTS.put("exp.exchange.success", "&a✔ &7兌換成功: &6{id}&7，消耗 &c{cost} &7xp，餘額 &6{stored} &7xp");
        DEFAULTS.put("exp.exchange.limit_daily", "&c✖ 達到每日兌換上限。");
        DEFAULTS.put("exp.exchange.limit_total", "&c✖ 達到總兌換上限。");
        DEFAULTS.put("exp.exchange.insufficient", "&c✖ 經驗不足，還需 &6{diff} &cxp。&7(需 &6{cost}&7，當前 &6{stored}&7)");
        DEFAULTS.put("exp.exchange.cost_lore", "&7消耗: &c{cost} &7xp");
        DEFAULTS.put("menu.wish.title", "&#c084fc✦ 祈願選單");
        DEFAULTS.put("menu.event.title", "&#60a5fa✦ 活動選單");
        DEFAULTS.put("menu.exp.title", "&#fbbf24✦ 經驗系統");
        DEFAULTS.put("menu.exp.balance_title", "&7賬戶資訊");
        DEFAULTS.put("menu.exp.balance_lore", "&7查看您的經驗賬戶");
        DEFAULTS.put("menu.close", "&c✖ 關閉");
        DEFAULTS.put("cdk.usage", "&7用法: &f/cdk &6<cdk代碼>");
        DEFAULTS.put("cdk.success", "&a✔ &7兌換成功: &6{rewards}");
        DEFAULTS.put("cdk.failure", "&c✖ 兌換失敗: &7{reason}");
        DEFAULTS.put("cdk.invalid", "&c✖ 無效的cdk");
        DEFAULTS.put("cdk.expired", "&c✖ 該cdk已過期");
        DEFAULTS.put("cdk.used", "&c✖ 您已兌換過此cdk");
        DEFAULTS.put("cdk.limit", "&c✖ 該cdk已被兌換完");
        DEFAULTS.put("cdk.limit_user", "&c✖ 您已達到此cdk的兌換次數限制");
        DEFAULTS.put("cdk.not_active", "&c✖ 該cdk當前不可用");
        DEFAULTS.put("buy.usage", "&7用法: &f/buy &6<類型> <等級> &7或 &f/buy menu");
        DEFAULTS.put("buy.not_found", "&c✖ 未找到此商品");
        DEFAULTS.put("buy.already_active", "&c✖ 您已經擁有該特權，尚未過期");
        DEFAULTS.put("buy.success", "&a✔ &7購買成功: &6{product}");
        DEFAULTS.put("buy.expired", "&e⚠ &7已移除過期特權: &c{product}");
        DEFAULTS.put("buy.insufficient_balance", "&c✖ 餘額不足，需 &6{cost}&c，當前 &6{balance}&c。");
        DEFAULTS.put("buy.economy_missing", "&c✖ 未找到經濟插件，無法完成購買。");
        DEFAULTS.put("buy.cost_failure", "&c✖ 扣款失敗，請稍後再試。");
        DEFAULTS.put("menu.buy.title", "&#ff9933✦ 特權購買");
        DEFAULTS.put("i18n.usage", "&7用法: &f/language &6<代碼> &8| &f/language list &8| &f/language reset");
        DEFAULTS.put("i18n.available", "&7可用語言: &6{languages}");
        DEFAULTS.put("i18n.updated", "&a✔ &7已切換語言至: &6{language}");
        DEFAULTS.put("i18n.unsupported", "&c✖ 不支援的語言: &6{language}");
        DEFAULTS.put("i18n.current", "&7當前語言: &6{language}");
        DEFAULTS.put("i18n.default", "&7默認語言: &6{language}");
        // Tab completion hints
        DEFAULTS.put("tab.wish.pool", "<祈願池名稱>");
        DEFAULTS.put("tab.wish.query_pool", "<查詢的祈願池>");
        DEFAULTS.put("tab.wish.count", "<祈願次數>");
        DEFAULTS.put("tab.event.id", "<參與的活動ID>");
        DEFAULTS.put("tab.exp.action", "<操作類型>");
        DEFAULTS.put("tab.exp.exchange_id", "<兌換項目ID>");
        DEFAULTS.put("tab.exp.deposit_amount", "<存入的經驗數量>");
        DEFAULTS.put("tab.exp.withdraw_amount", "<取出的經驗數量>");
        DEFAULTS.put("tab.exp.pay_target", "<轉帳對象玩家名>");
        DEFAULTS.put("tab.exp.pay_amount", "<轉帳經驗數量>");
        DEFAULTS.put("tab.cdk.code", "<輸入CDK兌換碼>");
        DEFAULTS.put("tab.buy.type", "<購買的類型>");
        DEFAULTS.put("tab.buy.level", "<開通的等級>");
        DEFAULTS.put("tab.language.code", "<語言代碼>");
    }

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> languageConfigs = new HashMap<String, YamlConfiguration>();
    private final Map<String, String> cachedPlayerLanguages = new HashMap<String, String>();
    private String defaultLanguage;
    private File languageDir;
    private File storageDir;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public final void reload() {
        File settingsFile = new File(plugin.getDataFolder(), "language.yml");
        if (!settingsFile.exists()) {
            plugin.saveResource("language.yml", false);
        }
        YamlConfiguration settings = YamlConfiguration.loadConfiguration(settingsFile);

        defaultLanguage = normalizeLanguage(settings.getString("language.default", "zh_tw"));
        String translationsDir = settings.getString("language.translations_dir", "lang");
        String dataDir = settings.getString("language.storage.data_dir", "userdata");

        languageDir = new File(plugin.getDataFolder(), translationsDir);
        if (!languageDir.exists() && !languageDir.mkdirs()) {
            plugin.getLogger().warning("無法創建語言目錄: " + languageDir.getAbsolutePath());
        }

        File defaultLangFile = new File(languageDir, defaultLanguage + ".yml");
        if (!defaultLangFile.exists()) {
            try {
                plugin.saveResource(translationsDir + "/" + defaultLanguage + ".yml", false);
            } catch (IllegalArgumentException ignored) {
                try {
                    defaultLangFile.getParentFile().mkdirs();
                    defaultLangFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("無法創建默認語言文件: " + e.getMessage());
                }
            }
        }

        storageDir = new File(plugin.getDataFolder(), dataDir);
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            plugin.getLogger().warning("無法創建語言存儲目錄: " + storageDir.getAbsolutePath());
        }

        languageConfigs.clear();
        loadTranslationFiles();
        cachedPlayerLanguages.clear();
    }

    public Set<String> getSupportedLanguages() {
        return Collections.unmodifiableSet(languageConfigs.keySet());
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public String getRaw(String key) {
        return getRaw((CommandSender) null, key);
    }

    public String getRaw(CommandSender target, String key) {
        String language = resolveLanguage(target);
        String value = getRawForLanguage(language, key);
        if (value == null) {
            value = getRawForLanguage(defaultLanguage, key);
        }
        if (value == null) {
            value = DEFAULTS.get(key);
        }
        if (value == null) {
            value = key;
        }
        return value;
    }

    /**
     * Get translated item name. Looks up in items section using quoted key format.
     * Falls back to the original itemId with cosmetic formatting if no translation exists.
     */
    public String getItemName(CommandSender target, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return "Unknown";
        }
        // YAML files use quoted keys like: "minecraft:iron_ingot": "Iron Ingot"
        // The YamlConfiguration getPath internally handles quoted keys
        String key = "items.\"" + itemId + "\"";
        String language = resolveLanguage(target);
        String translated = getRawForLanguage(language, key);
        if (translated == null) {
            translated = getRawForLanguage(defaultLanguage, key);
        }
        // If still not found, use the itemId as-is but clean it up for display
        if (translated == null) {
            // Remove minecraft: prefix if present and capitalize
            String cleanName = itemId;
            if (cleanName.startsWith("minecraft:")) {
                cleanName = cleanName.substring("minecraft:".length());
            }
            // Convert underscores to spaces and capitalize words
            String[] parts = cleanName.split("_");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                if (part.length() > 0) {
                    sb.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        sb.append(part.substring(1).toLowerCase());
                    }
                }
            }
            translated = sb.toString();
        }
        return translated;
    }

    public String format(String key) {
        return color(getRaw(key));
    }

    public String format(CommandSender target, String key) {
        return color(getRaw(target, key));
    }

    public String format(String key, Map<String, String> placeholders) {
        return format((CommandSender) null, key, placeholders);
    }

    public String format(CommandSender target, String key, Map<String, String> placeholders) {
        String text = getRaw(target, key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return color(text);
    }

    public java.util.List<String> getList(CommandSender target, String key) {
        String language = resolveLanguage(target);
        java.util.List<String> lines = getRawListForLanguage(language, key);
        if (lines == null || lines.isEmpty()) {
            lines = getRawListForLanguage(defaultLanguage, key);
        }
        if (lines == null) {
            lines = new java.util.ArrayList<String>();
        }
        return new java.util.ArrayList<String>(lines);
    }

    /**
     * Translate '&' color codes and &#RRGGBB hex colors into Minecraft formatting codes.
     */
    public String colorize(String text) {
        if (text == null) {
            return "";
        }
        text = translateHexColors(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Translate &#RRGGBB format to Minecraft hex color codes (§x§R§R§G§G§B§B).
     */
    private String translateHexColors(String text) {
        if (text == null) {
            return null;
        }
        // Match &#RRGGBB pattern
        java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("&#([0-9A-Fa-f]{6})");
        java.util.regex.Matcher matcher = hexPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(Character.toLowerCase(c));
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Translate a list of '&' color-coded strings.
     */
    public java.util.List<String> colorize(java.util.List<String> lines) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (lines == null) {
            return out;
        }
        for (String line : lines) {
            out.add(colorize(line));
        }
        return out;
    }

    public boolean setPlayerLanguage(String playerName, String language) {
        String normalized = normalizeLanguage(language);
        if (!isSupportedLanguage(normalized)) {
            return false;
        }
        YamlConfiguration data = loadUserData(playerName);
        data.set("profile.language", normalized);
        saveUserData(playerName, data);
        cachedPlayerLanguages.put(playerName.toLowerCase(), normalized);
        return true;
    }

    public String getPlayerLanguage(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return null;
        }
        String cached = cachedPlayerLanguages.get(playerName.toLowerCase());
        if (cached != null) {
            return cached;
        }
        YamlConfiguration data = loadUserData(playerName);
        String value = data.getString("profile.language", null);
        if (value != null) {
            cachedPlayerLanguages.put(playerName.toLowerCase(), value);
        }
        return value;
    }

    private void loadTranslationFiles() {
        File[] files = languageDir.listFiles();
        if (files == null) {
            return;
        }
        for (File langFile : files) {
            if (langFile == null || !langFile.getName().toLowerCase().endsWith(".yml")) {
                continue;
            }
            String code = normalizeLanguage(langFile.getName().replaceAll("\\.yml$", ""));
            YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);
            languageConfigs.put(code, config);
        }
    }

    private String resolveLanguage(CommandSender target) {
        if (target instanceof Player) {
            Player player = (Player) target;
            String preferred = getPlayerLanguage(player.getName());
            if (isSupportedLanguage(preferred)) {
                return normalizeLanguage(preferred);
            }
        }
        return defaultLanguage;
    }

    private String getRawForLanguage(String language, String key) {
        if (language == null || key == null) {
            return null;
        }
        YamlConfiguration config = languageConfigs.get(normalizeLanguage(language));
        if (config == null) {
            return null;
        }
        return config.getString(key);
    }

    private java.util.List<String> getRawListForLanguage(String language, String key) {
        if (language == null || key == null) {
            return null;
        }
        YamlConfiguration config = languageConfigs.get(normalizeLanguage(language));
        if (config == null) {
            return null;
        }
        return config.getStringList(key);
    }

    private boolean isSupportedLanguage(String language) {
        if (language == null) {
            return false;
        }
        return languageConfigs.containsKey(normalizeLanguage(language));
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            return defaultLanguage == null ? "default" : defaultLanguage;
        }
        return language.toLowerCase().replace('-', '_');
    }

    private YamlConfiguration loadUserData(String playerName) {
        File file = new File(storageDir, playerName + ".yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("無法創建用戶數據文件: " + e.getMessage());
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveUserData(String playerName, YamlConfiguration data) {
        File file = new File(storageDir, playerName + ".yml");
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存用戶語言失敗: " + e.getMessage());
        }
    }

    private String color(String text) {
        if (text == null) {
            return "";
        }
        text = translateHexColors(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Create a TextComponent with hover text that shows when the player hovers over the message.
     * 
     * @param text The main message text
     * @param hoverText The text to show on hover
     * @return TextComponent with hover functionality
     */
    public TextComponent createHoverText(String text, String hoverText) {
        TextComponent component = new TextComponent(colorize(text));
        if (hoverText != null && !hoverText.isEmpty()) {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text(colorize(hoverText))));
        }
        return component;
    }

    /**
     * Create a TextComponent with hover text and a click command.
     * 
     * @param text The main message text
     * @param hoverText The text to show on hover
     * @param command The command to run when clicked (without the leading /)
     * @return TextComponent with hover and click functionality
     */
    public TextComponent createHoverClickText(String text, String hoverText, String command) {
        TextComponent component = createHoverText(text, hoverText);
        if (command != null && !command.isEmpty()) {
            String cmd = command.startsWith("/") ? command : "/" + command;
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
        }
        return component;
    }

    /**
     * Create a TextComponent with hover text that suggests a command when clicked.
     * 
     * @param text The main message text
     * @param hoverText The text to show on hover
     * @param command The command to suggest when clicked
     * @return TextComponent with hover and suggest functionality
     */
    public TextComponent createHoverSuggestText(String text, String hoverText, String command) {
        TextComponent component = createHoverText(text, hoverText);
        if (command != null && !command.isEmpty()) {
            String cmd = command.startsWith("/") ? command : "/" + command;
            component.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd));
        }
        return component;
    }

    /**
     * Send a message with hover text to a player.
     * 
     * @param player The player to send the message to
     * @param text The main message text
     * @param hoverText The text to show on hover
     */
    public void sendHoverMessage(Player player, String text, String hoverText) {
        TextComponent component = createHoverText(text, hoverText);
        player.spigot().sendMessage(component);
    }

    /**
     * Send a message with hover text and click action to a player.
     * 
     * @param player The player to send the message to
     * @param text The main message text
     * @param hoverText The text to show on hover
     * @param command The command to run when clicked
     */
    public void sendHoverClickMessage(Player player, String text, String hoverText, String command) {
        TextComponent component = createHoverClickText(text, hoverText, command);
        player.spigot().sendMessage(component);
    }

    /**
     * Build a composite message with multiple components.
     * Each component can have its own hover and click actions.
     * 
     * @param components Array of TextComponents to combine
     * @return Combined TextComponent
     */
    public TextComponent buildMessage(TextComponent... components) {
        TextComponent result = new TextComponent();
        for (TextComponent comp : components) {
            result.addExtra(comp);
        }
        return result;
    }

    /**
     * Send a composite message to a player.
     * 
     * @param player The player to send the message to
     * @param components Array of TextComponents to combine and send
     */
    public void sendMessage(Player player, TextComponent... components) {
        TextComponent message = buildMessage(components);
        player.spigot().sendMessage(message);
    }

}
