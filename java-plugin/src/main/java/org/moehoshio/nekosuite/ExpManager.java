package org.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final File storageDir;
    private final List<Integer> depositAmounts = new ArrayList<Integer>();
    private final List<Integer> withdrawAmounts = new ArrayList<Integer>();
    private final List<ExchangeItem> exchanges = new ArrayList<ExchangeItem>();

    ExpManager(JavaPlugin plugin, Messages messages, File configFile) {
        this.plugin = plugin;
        this.messages = messages;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String dataDir = config.getString("storage.data_dir", "userdata");
        storageDir = new File(plugin.getDataFolder(), dataDir);
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            plugin.getLogger().warning("無法創建數據目錄: " + storageDir.getAbsolutePath());
        }
        loadConfig(config);
    }

    private void loadConfig(YamlConfiguration config) {
        depositAmounts.clear();
        withdrawAmounts.clear();
        List<Integer> dep = config.getIntegerList("menu.deposit_amounts");
        List<Integer> wit = config.getIntegerList("menu.withdraw_amounts");
        if (dep.isEmpty()) {
            depositAmounts.add(Integer.valueOf(10));
            depositAmounts.add(Integer.valueOf(100));
        } else {
            depositAmounts.addAll(dep);
        }
        if (wit.isEmpty()) {
            withdrawAmounts.add(Integer.valueOf(10));
            withdrawAmounts.add(Integer.valueOf(100));
        } else {
            withdrawAmounts.addAll(wit);
        }

        exchanges.clear();
        List<Map<?, ?>> list = config.getMapList("exchanges");
        for (Map<?, ?> raw : list) {
            ExchangeItem item = ExchangeItem.fromMap(raw);
            if (item != null) {
                exchanges.add(item);
            }
        }
    }

    public long getStored(String playerName) {
        YamlConfiguration data = loadUserData(playerName);
        return data.getLong("exp.balance", 0L);
    }

    public void setStored(String playerName, long value) {
        YamlConfiguration data = loadUserData(playerName);
        data.set("exp.balance", value);
        saveUserData(playerName, data);
    }

    public boolean deposit(Player player, long amount) {
        if (amount <= 0) {
            player.sendMessage(messages.format("exp.amount_invalid"));
            return false;
        }
        int current = player.getTotalExperience();
        if (amount > current) {
            player.sendMessage(messages.format("exp.not_enough_player"));
            return false;
        }
        player.giveExp((int) -amount);
        long stored = getStored(player.getName()) + amount;
        setStored(player.getName(), stored);
        Map<String, String> map = new HashMap<String, String>();
        map.put("amount", String.valueOf(amount));
        map.put("stored", String.valueOf(stored));
        player.sendMessage(messages.format("exp.deposit.success", map));
        return true;
    }

    public boolean withdraw(Player player, long amount) {
        if (amount <= 0) {
            player.sendMessage(messages.format("exp.amount_invalid"));
            return false;
        }
        long stored = getStored(player.getName());
        if (amount > stored) {
            player.sendMessage(messages.format("exp.not_enough_stored"));
            return false;
        }
        stored -= amount;
        setStored(player.getName(), stored);
        player.giveExp((int) amount);
        Map<String, String> map = new HashMap<String, String>();
        map.put("amount", String.valueOf(amount));
        map.put("stored", String.valueOf(stored));
        player.sendMessage(messages.format("exp.withdraw.success", map));
        return true;
    }

    public boolean transfer(Player player, String targetName, long amount) {
        if (amount <= 0) {
            player.sendMessage(messages.format("exp.amount_invalid"));
            return false;
        }
        if (player.getName().equalsIgnoreCase(targetName)) {
            player.sendMessage(messages.format("exp.transfer.self"));
            return false;
        }
        long stored = getStored(player.getName());
        if (amount > stored) {
            player.sendMessage(messages.format("exp.not_enough_stored"));
            return false;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (target.getName() == null)) {
            player.sendMessage(messages.format("exp.transfer.invalid_target"));
            return false;
        }
        setStored(player.getName(), stored - amount);
        long targetStored = getStored(target.getName()) + amount;
        setStored(target.getName(), targetStored);
        Map<String, String> map = new HashMap<String, String>();
        map.put("target", target.getName());
        map.put("amount", String.valueOf(amount));
        map.put("stored", String.valueOf(stored - amount));
        player.sendMessage(messages.format("exp.transfer.success", map));
        if (target.isOnline()) {
            Player online = target.getPlayer();
            if (online != null) {
                Map<String, String> rev = new HashMap<String, String>();
                rev.put("target", player.getName());
                rev.put("amount", String.valueOf(amount));
                rev.put("stored", String.valueOf(targetStored));
                online.sendMessage(messages.format("exp.transfer.success", rev));
            }
        }
        return true;
    }

    public boolean exchange(Player player, String id) {
        ExchangeItem item = findExchange(id);
        if (item == null) {
            return false;
        }
        YamlConfiguration data = loadUserData(player.getName());
        long stored = data.getLong("exp.balance", 0L);
        if (stored < item.getCost()) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("cost", String.valueOf(item.getCost()));
            player.sendMessage(messages.format("exp.exchange.insufficient", map));
            return false;
        }
        String base = "exp.exchange." + item.getId();
        int totalUsed = data.getInt(base + ".total", 0);
        int dailyUsed = data.getInt(base + ".daily.count", 0);
        String storedDate = data.getString(base + ".daily.date", "");
        String today = LocalDate.now().toString();
        if (!today.equals(storedDate)) {
            dailyUsed = 0;
        }
        if (item.getLimitDaily() > 0 && dailyUsed >= item.getLimitDaily()) {
            player.sendMessage(messages.format("exp.exchange.limit_daily"));
            return false;
        }
        if (item.getLimitTotal() > 0 && totalUsed >= item.getLimitTotal()) {
            player.sendMessage(messages.format("exp.exchange.limit_total"));
            return false;
        }
        stored -= item.getCost();
        data.set("exp.balance", stored);
        data.set(base + ".total", totalUsed + 1);
        data.set(base + ".daily.count", dailyUsed + 1);
        data.set(base + ".daily.date", today);
        saveUserData(player.getName(), data);

        String cmd = item.getCommand().replace("{player}", player.getName()).replace("{id}", item.getId());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        Map<String, String> map = new HashMap<String, String>();
        map.put("id", item.getId());
        map.put("cost", String.valueOf(item.getCost()));
        map.put("stored", String.valueOf(stored));
        player.sendMessage(messages.format("exp.exchange.success", map));
        return true;
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new ExpMenuHolder(), 27, messages.format("menu.exp.title"));
        int slot = 0;
        long stored = getStored(player.getName());

        for (int i = 0; i < depositAmounts.size() && slot < 9; i++) {
            int amount = depositAmounts.get(i);
            ItemStack stack = createItem(Material.LIME_DYE, "&a存入 " + amount + " xp", new String[]{"存入 " + amount + " xp"});
            inv.setItem(slot++, stack);
        }
        for (int i = 0; i < withdrawAmounts.size() && slot < 18; i++) {
            int amount = withdrawAmounts.get(i);
            ItemStack stack = createItem(Material.ORANGE_DYE, "&6取出 " + amount + " xp", new String[]{"取出 " + amount + " xp"});
            inv.setItem(slot++, stack);
        }

        for (ExchangeItem item : exchanges) {
            ItemStack stack = createItem(item.getMaterial(), item.getDisplay(), new String[]{"Cost: " + item.getCost() + " xp", "ID: " + item.getId()});
            inv.setItem(slot++, stack);
        }

        ItemStack balance = createItem(Material.BOOK, messages.format("exp.balance", createMap("stored", String.valueOf(stored), "carried", String.valueOf(player.getTotalExperience()))), new String[]{"EXP"});
        inv.setItem(25, balance);
        inv.setItem(26, createItem(Material.BARRIER, messages.format("menu.close"), new String[0]));
        player.openInventory(inv);
    }

    private ItemStack createItem(Material mat, String name, String[] loreArr) {
        ItemStack item = new ItemStack(mat == null ? Material.PAPER : mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> lore = new ArrayList<String>();
            for (int i = 0; i < loreArr.length; i++) {
                lore.add(ChatColor.translateAlternateColorCodes('&', loreArr[i]));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    boolean handleMenuClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return true;
        }
        String name = "";
        if (clicked.hasItemMeta() && clicked.getItemMeta().getDisplayName() != null) {
            name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        }
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return true;
        }
        if (name.contains("存入")) {
            long amount = parseLastNumber(name);
            if (amount > 0) {
                deposit(player, amount);
            }
            return true;
        }
        if (name.contains("取出")) {
            long amount = parseLastNumber(name);
            if (amount > 0) {
                withdraw(player, amount);
            }
            return true;
        }
        List<String> lore = clicked.getItemMeta() != null ? clicked.getItemMeta().getLore() : null;
        if (lore != null) {
            for (String line : lore) {
                if (line != null && line.contains("ID:")) {
                    String id = line.substring(line.indexOf("ID:") + 3).trim();
                    exchange(player, id);
                    return true;
                }
            }
        }
        return true;
    }

    private long parseLastNumber(String text) {
        String digits = "";
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits = c + digits;
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Map<String, String> createMap(String k1, String v1, String k2, String v2) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
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
            plugin.getLogger().warning("保存用戶數據失敗: " + e.getMessage());
        }
    }

    private ExchangeItem findExchange(String id) {
        for (ExchangeItem item : exchanges) {
            if (item.getId().equalsIgnoreCase(id)) {
                return item;
            }
        }
        return null;
    }

    public static class ExpMenuHolder implements InventoryHolder {
        public Inventory getInventory() {
            return null;
        }
    }

    private static class ExchangeItem {
        private final String id;
        private final String display;
        private final long cost;
        private final String command;
        private final int limitDaily;
        private final int limitTotal;
        private final Material material;

        ExchangeItem(String id, String display, long cost, String command, int limitDaily, int limitTotal, Material material) {
            this.id = id;
            this.display = display == null ? id : display;
            this.cost = cost;
            this.command = command == null ? "" : command;
            this.limitDaily = limitDaily;
            this.limitTotal = limitTotal;
            this.material = material == null ? Material.PAPER : material;
        }

        static ExchangeItem fromMap(Map<?, ?> raw) {
            if (raw == null || raw.get("id") == null) {
                return null;
            }
            String id = raw.get("id").toString();
            String display = raw.get("display") == null ? id : raw.get("display").toString();
            long cost = 0;
            Object costObj = raw.get("cost");
            if (costObj != null) {
                try {
                    cost = Long.parseLong(costObj.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            String cmd = raw.get("command") == null ? "" : raw.get("command").toString();
            int limitDaily = parseInt(raw.get("limit_daily"));
            int limitTotal = parseInt(raw.get("limit_total"));
            Material mat = Material.PAPER;
            if (raw.get("material") != null) {
                mat = Material.matchMaterial(raw.get("material").toString().toUpperCase());
                if (mat == null) {
                    mat = Material.PAPER;
                }
            }
            return new ExchangeItem(id, display, cost, cmd, limitDaily, limitTotal, mat);
        }

        private static int parseInt(Object o) {
            if (o == null) {
                return 0;
            }
            try {
                return Integer.parseInt(o.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        String getId() {
            return id;
        }

        String getDisplay() {
            return display;
        }

        long getCost() {
            return cost;
        }

        String getCommand() {
            return command;
        }

        int getLimitDaily() {
            return limitDaily;
        }

        int getLimitTotal() {
            return limitTotal;
        }

        Material getMaterial() {
            return material;
        }
    }
}
