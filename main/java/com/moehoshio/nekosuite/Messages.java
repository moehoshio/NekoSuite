package com.moehoshio.nekosuite;

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
        DEFAULTS.put("common.only_player", "&c此命令僅玩家可用。");
        DEFAULTS.put("common.no_permission", "&c您沒有權限使用此命令。");
        DEFAULTS.put("common.reload_success", "&a[NekoSuite] 配置已重載。");
        DEFAULTS.put("wish.usage", "&e用法: /wish <pool> [count] 或 /wish query <pool>");
        DEFAULTS.put("wish.query.usage", "&e用法: /wish query <pool>");
        DEFAULTS.put("wish.success", "&#4ade80祈願成功 &7>> &f{rewards}");
        DEFAULTS.put("wish.failure", "&#f87171祈願失敗 &7>> &f{reason}");
        DEFAULTS.put("wish.pool_missing", "&c祈願池不存在。");
        DEFAULTS.put("wish.not_active", "&c祈願池未開啟或已結束");
        DEFAULTS.put("wish.count_invalid", "&c祈願次數無效。");
        DEFAULTS.put("wish.ticket_insufficient", "&c祈願券不足 ({owned}/{needed})");
        DEFAULTS.put("wish.limit_reached", "&c已達到該祈願池的次數限制。");
        DEFAULTS.put("wish.economy_missing", "&c未找到經濟插件，無法扣款。");
        DEFAULTS.put("wish.cost_insufficient", "&c餘額不足，需 {needed} ，當前 {balance}。");
        DEFAULTS.put("wish.cost_failure", "&c扣款失敗，請稍後再試。");
        DEFAULTS.put("wish.status", "&#7dd3fc池:&f {pool}  &#a3e635次數:&f {count}  &#fbbf24券:&f {tickets}");
        DEFAULTS.put("event.no_available", "&e[NekoSuite] 當前沒有可用的活動。");
        DEFAULTS.put("event.header", "&#7dd3fc可參與活動清單");
        DEFAULTS.put("event.entry.available", "&#a3e635可參與 &7- &f{name} &7({id})");
        DEFAULTS.put("event.entry.limited", "&#f97316已達上限 &7- &f{name} &7({id})");
        DEFAULTS.put("event.participate.usage", "&e用法: /eventparticipate <eventId>");
        DEFAULTS.put("event.reward", "&a[NekoSuite] 活動獎勵: {rewards}");
        DEFAULTS.put("event.failure", "&c[NekoSuite] 無法參與: {reason}");
        DEFAULTS.put("event.error.not_found", "活動不存在");
        DEFAULTS.put("event.error.closed", "活動未開啟");
        DEFAULTS.put("event.error.limit", "已達到參與限制");
        DEFAULTS.put("exp.usage", "&e用法: /exp <balance|save|withdraw|pay|menu|exchange>");
        DEFAULTS.put("exp.balance", "&#7dd3fc帳戶 &f{stored}xp  &#a3e635身上 &f{carried}xp");
        DEFAULTS.put("exp.deposit.success", "&a已存入 {amount} xp ，賬戶餘額 {stored} xp");
        DEFAULTS.put("exp.deposit.button", "&a存入 {amount} xp");
        DEFAULTS.put("exp.deposit.lore", "&7點擊存入 {amount} xp");
        DEFAULTS.put("exp.withdraw.success", "&a已取出 {amount} xp ，賬戶餘額 {stored} xp");
        DEFAULTS.put("exp.withdraw.button", "&6取出 {amount} xp");
        DEFAULTS.put("exp.withdraw.lore", "&7點擊取出 {amount} xp");
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
        DEFAULTS.put("exp.exchange.cost_lore", "&7消耗: {cost} xp");
        DEFAULTS.put("menu.wish.title", "&#c084fc祈願選單");
        DEFAULTS.put("menu.event.title", "&#60a5fa活動選單");
        DEFAULTS.put("menu.exp.title", "&#fbbf24經驗系統");
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
        DEFAULTS.put("buy.usage", "&e用法: /buy <類型> <等級>");
        DEFAULTS.put("buy.not_found", "&c未找到此商品");
        DEFAULTS.put("buy.already_active", "&c您已經擁有該特權，尚未過期");
        DEFAULTS.put("buy.success", "&#4ade80購買成功 &7>> &f{product}");
        DEFAULTS.put("buy.expired", "&#fbbf24已移除過期特權 &7>> &f{product}");
        DEFAULTS.put("buy.insufficient_balance", "&c餘額不足，需 {cost} ，當前 {balance}。");
        DEFAULTS.put("buy.economy_missing", "&c未找到經濟插件，無法完成購買。");
        DEFAULTS.put("buy.cost_failure", "&c扣款失敗，請稍後再試。");
        DEFAULTS.put("menu.buy.title", "&6特權購買");
        DEFAULTS.put("i18n.usage", "&e用法: /language <代碼> | /language list | /language reset");
        DEFAULTS.put("i18n.available", "&b可用語言: {languages}");
        DEFAULTS.put("i18n.updated", "&a已切換語言至: {language}");
        DEFAULTS.put("i18n.unsupported", "&c不支援的語言: {language}");
        DEFAULTS.put("i18n.current", "&7當前語言: {language}");
        DEFAULTS.put("i18n.default", "&7默認語言: {language}");
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
     * Translate '&' color codes into Minecraft formatting codes.
     */
    public String colorize(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
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
        return ChatColor.translateAlternateColorCodes('&', text);
    }

}
