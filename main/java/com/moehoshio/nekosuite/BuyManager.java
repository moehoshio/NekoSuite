package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.permission.Permission;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuyManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final File storageDir;
    private final Map<String, Product> products = new HashMap<String, Product>();
    private final MenuLayout layout;
    private final Economy economy;
    private final Permission permission;

    BuyManager(JavaPlugin plugin, Messages messages, File configFile, MenuLayout layout, Economy economy, Permission permission) {
        this.plugin = plugin;
        this.messages = messages;
        this.layout = layout == null ? new MenuLayout(plugin) : layout;
        this.economy = economy;
        this.permission = permission;
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
            throw new BuyException(messages.format(player, "buy.not_found"));
        }
        YamlConfiguration data = loadUserData(player.getName());
        String key = "buy." + product.getId();
        long expiry = data.getLong(key + ".expiry", 0L);
        long now = System.currentTimeMillis();
        if (expiry == 0L || now > expiry) {
            // expired or never owned; proceed
        } else {
            throw new BuyException(messages.format(player, "buy.already_active"));
        }

        // balance check (Vault economy)
        if (product.getPrice() > 0) {
            if (economy == null) {
                throw new BuyException(messages.format(player, "buy.economy_missing"));
            }
            double balance = economy.getBalance(player);
            if (balance < product.getPrice()) {
                Map<String, String> map = singleton("cost", String.valueOf(product.getPrice())) ;
                map.put("balance", String.valueOf((long) balance));
                throw new BuyException(messages.format(player, "buy.insufficient_balance", map));
            }
            EconomyResponse response = economy.withdrawPlayer(player, product.getPrice());
            if (response == null || !response.transactionSuccess()) {
                throw new BuyException(messages.format(player, "buy.cost_failure"));
            }
        } else {
            // fallback: run configured cost commands
            for (String cmd : product.getCostCommands()) {
                dispatch(cmd, player, 0);
            }
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

        syncPermissions(player, data);

        List<String> granted = new ArrayList<String>();
        granted.add(product.getId());
        return granted;
    }

    public void openMenu(Player player) {
        MenuLayout.BuyLayout buyLayout = layout.getBuyLayout();
        Inventory inv = Bukkit.createInventory(new BuyMenuHolder(), buyLayout.getSize(), messages.format(player, "menu.buy.title"));
        int slotIndex = 0;
        
        // Sort products by type (vip, mcd, bag) and level
        List<Product> sortedProducts = new ArrayList<Product>(products.values());
        Collections.sort(sortedProducts, new java.util.Comparator<Product>() {
            public int compare(Product a, Product b) {
                int typeOrderA = getTypeOrder(a.getId());
                int typeOrderB = getTypeOrder(b.getId());
                if (typeOrderA != typeOrderB) {
                    return typeOrderA - typeOrderB;
                }
                // Same type, sort by level number
                int levelA = extractLevel(a.getId());
                int levelB = extractLevel(b.getId());
                return levelA - levelB;
            }
            
            private int getTypeOrder(String id) {
                String lower = id.toLowerCase();
                if (lower.startsWith("vip")) return 0;
                if (lower.startsWith("mcd")) return 1;
                if (lower.startsWith("bag")) return 2;
                return 3;
            }
            
            private int extractLevel(String id) {
                StringBuilder sb = new StringBuilder();
                for (int i = id.length() - 1; i >= 0; i--) {
                    char c = id.charAt(i);
                    if (Character.isDigit(c)) {
                        sb.insert(0, c);
                    } else if (sb.length() > 0) {
                        break;
                    }
                }
                if (sb.length() == 0) return 0;
                try {
                    return Integer.parseInt(sb.toString());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        });
        
        for (Product product : sortedProducts) {
            if (slotIndex >= buyLayout.getProductSlots().size()) {
                break;
            }
            ItemStack stack = new ItemStack(product.getMaterial());
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                String nameKey = "buy.product." + product.getId() + ".name";
                String name = messages.getRaw(player, nameKey);
                if (name == null || nameKey.equals(name)) {
                    name = product.getDisplayName();
                }
                meta.setDisplayName(messages.colorize(name));

                List<String> lore = messages.getList(player, "buy.product." + product.getId() + ".lore");
                if (lore == null || lore.isEmpty()) {
                    lore = product.getLore();
                }
                lore = messages.colorize(lore);
                lore.add(ChatColor.DARK_GRAY + "ID: " + product.getId());
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                stack.setItemMeta(meta);
            }
            int slot = buyLayout.getProductSlots().get(slotIndex++);
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, stack);
            }
        }
        if (buyLayout.getCloseSlot() >= 0 && buyLayout.getCloseSlot() < inv.getSize()) {
            ItemStack close = new ItemStack(org.bukkit.Material.BARRIER);
            ItemMeta meta = close.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(messages.format(player, "menu.close"));
                close.setItemMeta(meta);
            }
            inv.setItem(buyLayout.getCloseSlot(), close);
        } else {
            int fallback = inv.getSize() - 1;
            ItemStack close = new ItemStack(org.bukkit.Material.BARRIER);
            ItemMeta meta = close.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(messages.format(player, "menu.close"));
                close.setItemMeta(meta);
            }
            inv.setItem(fallback, close);
        }
        player.openInventory(inv);
    }

    boolean handleMenuClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) {
            return true;
        }
        if (clicked.getType() == org.bukkit.Material.BARRIER) {
            player.closeInventory();
            return true;
        }
        String closeLabel = ChatColor.stripColor(messages.format(player, "menu.close"));
        String display = clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()
                ? ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                : "";
        if (!closeLabel.isEmpty() && closeLabel.equalsIgnoreCase(display)) {
            player.closeInventory();
            return true;
        }
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getLore() == null) {
            return true;
        }
        for (String line : clicked.getItemMeta().getLore()) {
            if (line != null && line.contains("ID:")) {
                String id = line.substring(line.indexOf("ID:") + 3).trim();
                try {
                    purchase(player, id);
                    player.sendMessage(messages.format(player, "buy.success", singleton("product", id)));
                } catch (BuyException e) {
                    player.sendMessage(e.getMessage());
                }
                return true;
            }
        }
        return true;
    }

    public void check(Player player) {
        YamlConfiguration data = loadUserData(player.getName());
        boolean changed = false;
        long now = System.currentTimeMillis();
        Map<String, Boolean> active = new HashMap<String, Boolean>();
        for (String key : data.getConfigurationSection("buy") != null ? data.getConfigurationSection("buy").getKeys(false) : new ArrayList<String>()) {
            long expiry = data.getLong("buy." + key + ".expiry", 0L);
            if (expiry > 0 && now > expiry) {
                Product product = products.get(key.toLowerCase());
                if (product != null) {
                    for (String cmd : product.getRevokeCommands()) {
                        dispatch(cmd, player, product.getDurationDays());
                    }
                    player.sendMessage(messages.format(player, "buy.expired", singleton("product", product.getId())));
                }
                data.set("buy." + key, null);
                changed = true;
            } else {
                Product product = products.get(key.toLowerCase());
                if (product != null) {
                    for (String cmd : product.getGrantCommands()) {
                        dispatch(cmd, player, product.getDurationDays());
                    }
                    active.put(product.getId().toLowerCase(), true);
                }
            }
        }
        if (changed) {
            saveUserData(player.getName(), data);
        }
        syncPermissions(player, data, active);
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

    private void syncPermissions(Player player, YamlConfiguration data) {
        Map<String, Boolean> active = new HashMap<String, Boolean>();
        ConfigurationSection buy = data.getConfigurationSection("buy");
        if (buy != null) {
            long now = System.currentTimeMillis();
            for (String key : buy.getKeys(false)) {
                long expiry = buy.getLong(key + ".expiry", 0L);
                if (expiry == 0L || now <= expiry) {
                    active.put(key.toLowerCase(), true);
                }
            }
        }
        syncPermissions(player, data, active);
    }

    private void syncPermissions(Player player, YamlConfiguration data, Map<String, Boolean> activeProducts) {
        if (permission == null) {
            return;
        }
        String world = player.getWorld().getName();
        boolean groupSupported;
        try {
            groupSupported = permission.hasGroupSupport();
        } catch (UnsupportedOperationException e) {
            groupSupported = false;
        }

        for (Product product : products.values()) {
            boolean shouldHave = activeProducts.getOrDefault(product.getId().toLowerCase(), false);
            for (String node : product.getPermissions()) {
                boolean has = permission.playerHas(world, player, node);
                if (shouldHave && !has) {
                    permission.playerAdd(world, player, node);
                } else if (!shouldHave && has) {
                    permission.playerRemove(world, player, node);
                }
            }
            if (groupSupported) {
                for (String group : product.getGroups()) {
                    boolean inGroup = permission.playerInGroup(world, player, group);
                    if (shouldHave && !inGroup) {
                        permission.playerAddGroup(world, player, group);
                    } else if (!shouldHave && inGroup) {
                        permission.playerRemoveGroup(world, player, group);
                    }
                }
            }
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
        private final double price;
        private final List<String> costCommands;
        private final List<String> grantCommands;
        private final List<String> revokeCommands;
        private final String displayName;
        private final List<String> lore;
        private final org.bukkit.Material material;
        private final List<String> permissions;
        private final List<String> groups;

        Product(String id, long durationDays, double price, List<String> costCommands, List<String> grantCommands, List<String> revokeCommands, String displayName, List<String> lore, org.bukkit.Material material, List<String> permissions, List<String> groups) {
            this.id = id;
            this.durationDays = durationDays;
            this.price = price;
            this.costCommands = costCommands;
            this.grantCommands = grantCommands;
            this.revokeCommands = revokeCommands;
            this.displayName = displayName;
            this.lore = lore;
            this.material = material;
            this.permissions = permissions == null ? new ArrayList<String>() : permissions;
            this.groups = groups == null ? new ArrayList<String>() : groups;
        }

        static Product fromSection(String id, ConfigurationSection section) {
            long duration = section.getLong("duration_days", 0L);
            double price = section.getDouble("price", section.getDouble("cost", 0.0));
            List<String> cost = section.getStringList("cost_commands");
            List<String> grant = section.getStringList("grant_commands");
            List<String> revoke = section.getStringList("revoke_commands");
            String name = section.getString("display_name", id);
            List<String> lore = section.getStringList("lore");
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(section.getString("material", "PAPER"));
            if (material == null) {
                material = org.bukkit.Material.PAPER;
            }
            List<String> permissions = section.getStringList("permissions");
            List<String> groups = section.getStringList("groups");
            return new Product(id, duration, price, cost, grant, revoke, name, lore, material, permissions, groups);
        }

        String getId() {
            return id;
        }

        long getDurationDays() {
            return durationDays;
        }

        double getPrice() {
            return price;
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

        String getDisplayName() {
            return displayName;
        }

        List<String> getLore() {
            return lore;
        }

        org.bukkit.Material getMaterial() {
            return material;
        }

        List<String> getPermissions() {
            return permissions;
        }

        List<String> getGroups() {
            return groups;
        }
    }

    static class BuyException extends Exception {
        BuyException(String message) {
            super(message);
        }
    }

    static class BuyMenuHolder implements InventoryHolder {
        public Inventory getInventory() {
            return null;
        }
    }
}
