package org.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuyManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final File storageDir;
    private final Map<String, Product> products = new HashMap<String, Product>();

    BuyManager(JavaPlugin plugin, Messages messages, File configFile) {
        this.plugin = plugin;
        this.messages = messages;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String dataDir = config.getString("storage.data_dir", "userdata");
        storageDir = new File(plugin.getDataFolder(), dataDir);
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            plugin.getLogger().warning("無法創建數據目錄: " + storageDir.getAbsolutePath());
        }
        loadProducts(config.getConfigurationSection("products"));
    }

    private void loadProducts(ConfigurationSection section) {
        products.clear();
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection cs = section.getConfigurationSection(id);
            if (cs == null) {
                continue;
            }
            Product product = Product.fromSection(id, cs);
            if (product != null) {
                products.put(id.toLowerCase(), product);
            }
        }
    }

    public Map<String, Product> getProducts() {
        return products;
    }

    public List<String> purchase(Player player, String productId) throws BuyException {
        Product product = products.get(productId.toLowerCase());
        if (product == null) {
            throw new BuyException(messages.format("buy.not_found"));
        }
        YamlConfiguration data = loadUserData(player.getName());
        String key = "buy." + product.getId();
        long expiry = data.getLong(key + ".expiry", 0L);
        long now = System.currentTimeMillis();
        if (expiry == 0L || now > expiry) {
            // expired or never owned; proceed
        } else {
            throw new BuyException(messages.format("buy.already_active"));
        }

        // cost
        for (String cmd : product.getCostCommands()) {
            dispatch(cmd, player, 0);
        }

        // grant
        for (String cmd : product.getGrantCommands()) {
            dispatch(cmd, player, product.getDurationDays());
        }

        if (product.getDurationDays() > 0) {
            Instant exp = Instant.ofEpochMilli(now).plus(product.getDurationDays(), ChronoUnit.DAYS);
            data.set(key + ".expiry", exp.toEpochMilli());
        } else {
            data.set(key + ".expiry", 0L);
        }
        saveUserData(player.getName(), data);

        List<String> granted = new ArrayList<String>();
        granted.add(product.getId());
        return granted;
    }

    public void check(Player player) {
        YamlConfiguration data = loadUserData(player.getName());
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (String key : data.getConfigurationSection("buy") != null ? data.getConfigurationSection("buy").getKeys(false) : new ArrayList<String>()) {
            long expiry = data.getLong("buy." + key + ".expiry", 0L);
            if (expiry > 0 && now > expiry) {
                Product product = products.get(key.toLowerCase());
                if (product != null) {
                    for (String cmd : product.getRevokeCommands()) {
                        dispatch(cmd, player, product.getDurationDays());
                    }
                    player.sendMessage(messages.format("buy.expired", singleton("product", product.getId())));
                }
                data.set("buy." + key, null);
                changed = true;
            } else {
                Product product = products.get(key.toLowerCase());
                if (product != null) {
                    for (String cmd : product.getGrantCommands()) {
                        dispatch(cmd, player, product.getDurationDays());
                    }
                }
            }
        }
        if (changed) {
            saveUserData(player.getName(), data);
        }
    }

    private void dispatch(String raw, Player player, long durationDays) {
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        String cmd = raw.replace("{player}", player.getName())
                .replace("{duration}", String.valueOf(durationDays))
                .replace("%player%", player.getName());
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
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

    private Map<String, String> singleton(String k, String v) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(k, v);
        return map;
    }

    static class Product {
        private final String id;
        private final long durationDays;
        private final List<String> costCommands;
        private final List<String> grantCommands;
        private final List<String> revokeCommands;

        Product(String id, long durationDays, List<String> costCommands, List<String> grantCommands, List<String> revokeCommands) {
            this.id = id;
            this.durationDays = durationDays;
            this.costCommands = costCommands;
            this.grantCommands = grantCommands;
            this.revokeCommands = revokeCommands;
        }

        static Product fromSection(String id, ConfigurationSection section) {
            long duration = section.getLong("duration_days", 0L);
            List<String> cost = section.getStringList("cost_commands");
            List<String> grant = section.getStringList("grant_commands");
            List<String> revoke = section.getStringList("revoke_commands");
            return new Product(id, duration, cost, grant, revoke);
        }

        String getId() {
            return id;
        }

        long getDurationDays() {
            return durationDays;
        }

        List<String> getCostCommands() {
            return costCommands;
        }

        List<String> getGrantCommands() {
            return grantCommands;
        }

        List<String> getRevokeCommands() {
            return revokeCommands;
        }
    }

    static class BuyException extends Exception {
        BuyException(String message) {
            super(message);
        }
    }
}
