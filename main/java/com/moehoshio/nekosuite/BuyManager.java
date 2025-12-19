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
        boolean owned = data.getBoolean(key + ".owned", false);
        long now = System.currentTimeMillis();
        
        // Determine product category and level
        String productIdLower = product.getId().toLowerCase();
        String category = getProductCategory(productIdLower);
        int level = extractLevel(productIdLower);
        
        // For bag products: don't allow buying lower level if user owns higher level
        if ("bag".equals(category)) {
            int ownedHighestBag = getHighestOwnedLevel(data, "bag");
            if (ownedHighestBag > level) {
                throw new BuyException(messages.format(player, "buy.bag_downgrade_blocked"));
            }
            // Check if already owned this exact level bag
            if (owned && (expiry == 0L || now <= expiry)) {
                throw new BuyException(messages.format(player, "buy.already_active"));
            }
        }
        
        // For vip/mcd: calculate time conversion for upgrade/downgrade
        long convertedDays = 0;
        Product oldProduct = null;
        if (("vip".equals(category) || "mcd".equals(category)) && product.getDurationDays() > 0) {
            // Find currently active subscription in this category
            oldProduct = findActiveProduct(data, category, now);
            if (oldProduct != null && !oldProduct.getId().equalsIgnoreCase(product.getId())) {
                // Calculate remaining days from old product
                String oldKey = "buy." + oldProduct.getId();
                long oldExpiry = data.getLong(oldKey + ".expiry", 0L);
                long remainingMs = oldExpiry - now;
                if (remainingMs > 0) {
                    long remainingDays = remainingMs / (24L * 60L * 60L * 1000L);
                    // Calculate daily value: price / duration
                    double oldDailyValue = (double) oldProduct.getPrice() / oldProduct.getDurationDays();
                    double newDailyValue = (double) product.getPrice() / product.getDurationDays();
                    // Convert old value to new duration: (remaining_days * old_daily_value) / new_daily_value
                    if (newDailyValue > 0) {
                        convertedDays = (long) ((remainingDays * oldDailyValue) / newDailyValue);
                    }
                    // Revoke old product
                    for (String cmd : oldProduct.getRevokeCommands()) {
                        dispatch(cmd, player, oldProduct.getDurationDays());
                    }
                    data.set(oldKey + ".owned", false);
                    data.set(oldKey + ".expiry", 0L);
                }
            } else if (owned && expiry > 0L && now <= expiry) {
                // Already have this exact product active
                throw new BuyException(messages.format(player, "buy.already_active"));
            }
        } else if (!("bag".equals(category))) {
            // For non-bag and non-vip/mcd products, check if already owned
            if (owned && (expiry == 0L || now <= expiry)) {
                throw new BuyException(messages.format(player, "buy.already_active"));
            }
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

        data.set(key + ".owned", true);
        if (product.getDurationDays() > 0) {
            // Add converted days from old subscription + new duration
            long totalDays = product.getDurationDays() + convertedDays;
            Instant exp = Instant.ofEpochMilli(now).plus(totalDays, ChronoUnit.DAYS);
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
    
    /**
     * Get the category of a product (vip, mcd, bag, or other).
     */
    private String getProductCategory(String productId) {
        if (productId.startsWith("vip")) {
            return "vip";
        } else if (productId.startsWith("mcd")) {
            return "mcd";
        } else if (productId.startsWith("bag")) {
            return "bag";
        }
        return "other";
    }
    
    /**
     * Extract the level number from a product id (e.g., "vip3" returns 3).
     */
    private int extractLevel(String productId) {
        int end = productId.length();
        int start = end;
        while (start > 0 && Character.isDigit(productId.charAt(start - 1))) {
            start--;
        }
        if (start == end) return 0;
        try {
            return Integer.parseInt(productId.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Get the highest level bag the user currently owns.
     */
    private int getHighestOwnedLevel(YamlConfiguration data, String category) {
        int highest = 0;
        for (Product p : products.values()) {
            String id = p.getId().toLowerCase();
            if (!getProductCategory(id).equals(category)) {
                continue;
            }
            String key = "buy." + p.getId();
            boolean owned = data.getBoolean(key + ".owned", false);
            long expiry = data.getLong(key + ".expiry", 0L);
            // For bags (permanent), owned is enough; for timed products, check expiry
            if (owned && (expiry == 0L || System.currentTimeMillis() <= expiry)) {
                int level = extractLevel(id);
                if (level > highest) {
                    highest = level;
                }
            }
        }
        return highest;
    }
    
    /**
     * Find the currently active product in a category.
     */
    private Product findActiveProduct(YamlConfiguration data, String category, long now) {
        for (Product p : products.values()) {
            String id = p.getId().toLowerCase();
            if (!getProductCategory(id).equals(category)) {
                continue;
            }
            String key = "buy." + p.getId();
            boolean owned = data.getBoolean(key + ".owned", false);
            long expiry = data.getLong(key + ".expiry", 0L);
            if (owned && expiry > 0L && now <= expiry) {
                return p;
            }
        }
        return null;
    }

    public void openMenu(Player player) {
        MenuLayout.BuyLayout buyLayout = layout.getBuyLayout();
        // Use 54 slots (6 rows) to fit categorized layout, each category gets its own row
        int inventorySize = Math.max(buyLayout.getSize(), 54);
        Inventory inv = Bukkit.createInventory(new BuyMenuHolder(), inventorySize, messages.format(player, "menu.buy.title"));
        
        // Group products by category
        Map<String, List<Product>> categorizedProducts = new HashMap<String, List<Product>>();
        categorizedProducts.put("vip", new ArrayList<Product>());
        categorizedProducts.put("mcd", new ArrayList<Product>());
        categorizedProducts.put("bag", new ArrayList<Product>());
        categorizedProducts.put("other", new ArrayList<Product>());
        
        for (Product product : products.values()) {
            String id = product.getId().toLowerCase();
            if (id.startsWith("vip")) {
                categorizedProducts.get("vip").add(product);
            } else if (id.startsWith("mcd")) {
                categorizedProducts.get("mcd").add(product);
            } else if (id.startsWith("bag")) {
                categorizedProducts.get("bag").add(product);
            } else {
                categorizedProducts.get("other").add(product);
            }
        }
        
        // Sort each category by level number
        java.util.Comparator<Product> levelComparator = new java.util.Comparator<Product>() {
            public int compare(Product a, Product b) {
                return extractLevel(a.getId()) - extractLevel(b.getId());
            }
            
            private int extractLevel(String id) {
                int end = id.length();
                int start = end;
                while (start > 0 && Character.isDigit(id.charAt(start - 1))) {
                    start--;
                }
                if (start == end) return 0;
                try {
                    return Integer.parseInt(id.substring(start, end));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        };
        
        Collections.sort(categorizedProducts.get("vip"), levelComparator);
        Collections.sort(categorizedProducts.get("mcd"), levelComparator);
        Collections.sort(categorizedProducts.get("bag"), levelComparator);
        Collections.sort(categorizedProducts.get("other"), levelComparator);
        
        // Place each category on its own row (9 slots per row)
        // Row 0 (slots 0-8): VIP
        // Row 1 (slots 9-17): MCD
        // Row 2 (slots 18-26): BAG
        // Row 3 (slots 27-35): Other
        placeProductsInRow(inv, categorizedProducts.get("vip"), 0, player);
        placeProductsInRow(inv, categorizedProducts.get("mcd"), 9, player);
        placeProductsInRow(inv, categorizedProducts.get("bag"), 18, player);
        placeProductsInRow(inv, categorizedProducts.get("other"), 27, player);
        
        // Add category labels on the right side of each row (slot 8 of each row)
        addCategoryLabel(inv, 8, org.bukkit.Material.GOLD_BLOCK, messages.format(player, "menu.buy.category.vip"));
        addCategoryLabel(inv, 17, org.bukkit.Material.PAPER, messages.format(player, "menu.buy.category.mcd"));
        addCategoryLabel(inv, 26, org.bukkit.Material.CHEST, messages.format(player, "menu.buy.category.bag"));
        
        // Navigation button (back to main menu)
        int closeSlot = inventorySize - 1;
        if (closeSlot > 0 && closeSlot - 1 >= 0 && closeSlot - 1 < inventorySize) {
            inv.setItem(closeSlot - 1, createHomeButton(player));
        }
        
        // Close button at bottom right
        ItemStack close = new ItemStack(org.bukkit.Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(messages.format(player, "menu.close"));
            close.setItemMeta(closeMeta);
        }
        inv.setItem(closeSlot, close);
        
        player.openInventory(inv);
    }

    private ItemStack createHomeButton(Player player) {
        ItemStack item = new ItemStack(org.bukkit.Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.format(player, "help.back_button"));
            List<String> lore = new ArrayList<String>();
            lore.add(messages.format(player, "help.back_lore"));
            lore.add(ChatColor.DARK_GRAY + "ACTION:OPEN_NAV");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private void placeProductsInRow(Inventory inv, List<Product> productList, int startSlot, Player player) {
        int maxPerRow = 7; // Leave last 2 slots for category label and spacing
        int slotIndex = 0;
        for (Product product : productList) {
            if (slotIndex >= maxPerRow) break;
            int slot = startSlot + slotIndex;
            if (slot >= inv.getSize()) break;
            
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
            inv.setItem(slot, stack);
            slotIndex++;
        }
    }
    
    private void addCategoryLabel(Inventory inv, int slot, org.bukkit.Material material, String name) {
        if (slot >= inv.getSize()) return;
        ItemStack label = new ItemStack(material);
        ItemMeta meta = label.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            label.setItemMeta(meta);
        }
        inv.setItem(slot, label);
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
