package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ArtifactRewardsManager - 特別獎勵發放管理器
 * 專門用於其他插件/命令調用發放特殊物品獎勵
 * 不提供玩家直接使用的功能
 */
public class ArtifactRewardsManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Map<String, ArtifactItem> items = new HashMap<String, ArtifactItem>();

    public ArtifactRewardsManager(JavaPlugin plugin, Messages messages, File configFile) {
        this.plugin = plugin;
        this.messages = messages;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        loadConfig(config);
    }

    private void loadConfig(YamlConfiguration config) {
        items.clear();
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            return;
        }
        Set<String> keys = itemsSection.getKeys(false);
        for (String key : keys) {
            ConfigurationSection section = itemsSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            ArtifactItem item = ArtifactItem.fromSection(key, section);
            if (item != null) {
                items.put(key.toLowerCase(), item);
                items.put(key, item);
            }
        }
    }

    /**
     * 獲取所有可用的物品ID列表
     * @return 物品ID列表
     */
    public List<String> getAvailableItemIds() {
        List<String> ids = new ArrayList<String>();
        for (ArtifactItem item : items.values()) {
            if (!ids.contains(item.getId())) {
                ids.add(item.getId());
            }
        }
        return ids;
    }

    /**
     * 獲取物品信息
     * @param itemId 物品ID
     * @return 物品信息，如果不存在則返回null
     */
    public ArtifactItem getItem(String itemId) {
        ArtifactItem item = items.get(itemId);
        if (item == null) {
            item = items.get(itemId.toLowerCase());
        }
        return item;
    }

    /**
     * 發放物品給玩家
     * @param player 目標玩家
     * @param itemId 物品ID
     * @return 物品顯示名稱
     * @throws ArtifactException 如果物品不存在
     */
    public String giveReward(Player player, String itemId) throws ArtifactException {
        ArtifactItem item = getItem(itemId);
        if (item == null) {
            throw new ArtifactException("Item not found: " + itemId);
        }

        dispatchItem(player, item);
        return item.getDisplayName();
    }

    /**
     * 為其他插件提供的 API：發放物品給指定玩家名
     * @param playerName 玩家名
     * @param itemId 物品ID
     * @return 是否成功
     */
    public boolean giveRewardByName(String playerName, String itemId) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("無法發放物品: 玩家 " + playerName + " 不在線");
            return false;
        }
        try {
            giveReward(player, itemId);
            return true;
        } catch (ArtifactException e) {
            plugin.getLogger().warning("發放物品失敗: " + e.getMessage());
            return false;
        }
    }

    /**
     * 生成 Chat 懸浮信息 JSON (用於 tellraw 命令)
     * @param itemId 物品ID
     * @param playerName 玩家名 (用於替換占位符)
     * @return JSON 格式的懸浮信息文本
     */
    public String generateHoverJson(String itemId, String playerName) {
        ArtifactItem item = getItem(itemId);
        if (item == null) {
            return "{\"text\":\"[Unknown Item]\",\"color\":\"gray\"}";
        }
        return item.generateHoverJson(playerName);
    }

    /**
     * 發送帶有物品懸浮信息的 Chat 訊息
     * @param player 接收訊息的玩家
     * @param itemId 物品ID
     * @param messageTemplate 訊息模板 (使用 {item} 作為物品占位符)
     */
    public void sendItemMessage(Player player, String itemId, String messageTemplate) {
        ArtifactItem item = getItem(itemId);
        if (item == null) {
            player.sendMessage(messageTemplate.replace("{item}", itemId));
            return;
        }
        
        String hoverJson = item.generateHoverJson(player.getName());
        String[] parts = messageTemplate.split("\\{item\\}", 2);
        
        StringBuilder tellraw = new StringBuilder();
        tellraw.append("[");
        
        if (parts.length > 0 && !parts[0].isEmpty()) {
            tellraw.append("{\"text\":\"").append(escapeJson(translateColorCodes(parts[0]))).append("\"},");
        }
        
        tellraw.append(hoverJson);
        
        if (parts.length > 1 && !parts[1].isEmpty()) {
            tellraw.append(",{\"text\":\"").append(escapeJson(translateColorCodes(parts[1]))).append("\"}");
        }
        
        tellraw.append("]");
        
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
            "tellraw " + player.getName() + " " + tellraw.toString());
    }

    private void dispatchItem(Player player, ArtifactItem item) {
        String material = item.getMaterial();
        String nbt = item.getNbt();

        // 替換玩家占位符
        if (nbt != null) {
            nbt = nbt.replace("{player}", player.getName())
                     .replace("%player%", player.getName())
                     .replace("$player", player.getName());
        }

        // 構建並執行 give 命令
        String giveCommand;
        if (nbt != null && !nbt.isEmpty()) {
            giveCommand = "minecraft:give " + player.getName() + " " + material + nbt + " 1";
        } else {
            giveCommand = "minecraft:give " + player.getName() + " " + material + " 1";
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), giveCommand);

        // 執行額外命令
        List<String> commands = item.getCommands();
        if (commands != null) {
            for (String command : commands) {
                if (command == null || command.trim().isEmpty()) {
                    continue;
                }
                String cmd = command
                        .replace("{player}", player.getName())
                        .replace("%player%", player.getName())
                        .replace("$player", player.getName())
                        .replace("{item_name}", item.getDisplayName())
                        .replace("{item_id}", item.getId());
                if (cmd.startsWith("/")) {
                    cmd = cmd.substring(1);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
    }

    private static String translateColorCodes(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (i + 8 <= text.length() && text.charAt(i) == '&' && text.charAt(i + 1) == '#') {
                String hex = text.substring(i + 2, i + 8);
                if (hex.matches("[0-9A-Fa-f]{6}")) {
                    result.append("§x");
                    for (char c : hex.toCharArray()) {
                        result.append("§").append(Character.toLowerCase(c));
                    }
                    i += 8;
                    continue;
                }
            }
            if (text.charAt(i) == '&' && i + 1 < text.length()) {
                result.append(ChatColor.COLOR_CHAR).append(text.charAt(i + 1));
                i += 2;
            } else {
                result.append(text.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    // ========== 內部類 ==========

    /**
     * 特別獎勵物品定義
     */
    public static class ArtifactItem {
        private final String id;
        private final String displayName;
        private final String material;
        private final String description;
        private final String nbt;
        private final List<String> hoverLore;
        private final List<String> commands;

        public ArtifactItem(String id, String displayName, String material, String description,
                          String nbt, List<String> hoverLore, List<String> commands) {
            this.id = id;
            this.displayName = displayName != null ? translateColorCodes(displayName) : id;
            this.material = material != null ? material : "minecraft:stone";
            this.description = description;
            this.nbt = nbt;
            this.hoverLore = hoverLore != null ? hoverLore : new ArrayList<String>();
            this.commands = commands != null ? commands : new ArrayList<String>();
        }

        public static ArtifactItem fromSection(String id, ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            String displayName = section.getString("display_name", id);
            String material = section.getString("material", "minecraft:stone");
            String description = section.getString("description", "");
            String nbt = section.getString("nbt", "");
            List<String> hoverLore = section.getStringList("hover_lore");
            List<String> commands = section.getStringList("commands");

            return new ArtifactItem(id, displayName, material, description, nbt, hoverLore, commands);
        }

        /**
         * 生成 tellraw 用的 JSON 懸浮信息
         * @param playerName 玩家名 (用於替換占位符)
         * @return JSON 格式的文本組件
         */
        public String generateHoverJson(String playerName) {
            StringBuilder json = new StringBuilder();
            json.append("{\"text\":\"").append(escapeJson(displayName)).append("\"");
            
            // 添加懸浮事件
            json.append(",\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[");
            
            // 物品名稱
            json.append("{\"text\":\"").append(escapeJson(displayName)).append("\",\"color\":\"gold\"}");
            
            // 描述
            if (description != null && !description.isEmpty()) {
                String desc = description.replace("{player}", playerName)
                                        .replace("%player%", playerName)
                                        .replace("$player", playerName);
                json.append(",{\"text\":\"\\n\"}");
                json.append(",{\"text\":\"").append(escapeJson(translateColorCodes(desc))).append("\",\"color\":\"gray\"}");
            }
            
            // 額外的 Hover Lore 行
            for (String line : hoverLore) {
                String processedLine = line.replace("{player}", playerName)
                                          .replace("%player%", playerName)
                                          .replace("$player", playerName);
                json.append(",{\"text\":\"\\n\"}");
                json.append(",{\"text\":\"").append(escapeJson(translateColorCodes(processedLine))).append("\"}");
            }
            
            json.append("]}");
            json.append("}");
            
            return json.toString();
        }

        private static String translateColorCodes(String text) {
            if (text == null) {
                return null;
            }
            StringBuilder result = new StringBuilder();
            int i = 0;
            while (i < text.length()) {
                if (i + 8 <= text.length() && text.charAt(i) == '&' && text.charAt(i + 1) == '#') {
                    String hex = text.substring(i + 2, i + 8);
                    if (hex.matches("[0-9A-Fa-f]{6}")) {
                        result.append("§x");
                        for (char c : hex.toCharArray()) {
                            result.append("§").append(Character.toLowerCase(c));
                        }
                        i += 8;
                        continue;
                    }
                }
                if (text.charAt(i) == '&' && i + 1 < text.length()) {
                    result.append(ChatColor.COLOR_CHAR).append(text.charAt(i + 1));
                    i += 2;
                } else {
                    result.append(text.charAt(i));
                    i++;
                }
            }
            return result.toString();
        }

        private static String escapeJson(String text) {
            if (text == null) {
                return "";
            }
            return text.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t");
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getMaterial() {
            return material;
        }

        public String getDescription() {
            return description;
        }

        public String getNbt() {
            return nbt;
        }

        public List<String> getHoverLore() {
            return hoverLore;
        }

        public List<String> getCommands() {
            return commands;
        }
    }

    /**
     * 特別獎勵異常
     */
    public static class ArtifactException extends Exception {
        public ArtifactException(String message) {
            super(message);
        }
    }
}
