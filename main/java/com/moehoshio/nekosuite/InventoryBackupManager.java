package com.moehoshio.nekosuite;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages player inventory backups with git-like change tracking.
 * Supports restore with Vault economy cost and anti-duplication protection.
 * 
 * Key features for anti-duplication:
 * 1. Only creates "restorable" backups when items are truly LOST (lava, void, despawn)
 * 2. Tracks dropped items and only marks backup as restorable if item is destroyed
 * 3. Death backups are only restorable if items were lost (not picked up)
 */
public class InventoryBackupManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Economy economy;
    private final File storageDir;
    
    // Configuration
    private int maxBackupsPerPlayer;
    private int expiryDays;
    private boolean autoBackupEnabled;
    private long autoBackupInterval;
    private double restoreCost;
    private double verifiedLossCost; // Lower cost for verified item loss
    private long restoreCooldown;
    private boolean requireConfirmation;
    private boolean clearBeforeRestore;
    private List<String> blockedItems;
    private boolean skipDuplicateContent;
    private boolean trackRestored;
    private boolean notifyAdmins;
    private long restoreCooldownPerBackup;
    private boolean requireVerifiedLoss;
    
    // Runtime state
    private final Map<UUID, Long> lastBackupTime = new HashMap<UUID, Long>();
    private final Map<UUID, String> pendingConfirmations = new HashMap<UUID, String>();
    
    // Track dropped items to detect if they were destroyed vs picked up
    // Key: Item entity UUID, Value: TrackedItem info
    private final Map<UUID, TrackedItem> trackedDroppedItems = new HashMap<UUID, TrackedItem>();
    
    // Track pre-death inventory snapshots
    private final Map<UUID, InventorySnapshot> preDamageSnapshots = new HashMap<UUID, InventorySnapshot>();
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // Loss reasons
    public static final String LOSS_MANUAL = "MANUAL";
    public static final String LOSS_DEATH = "DEATH";
    public static final String LOSS_LAVA = "LAVA";
    public static final String LOSS_VOID = "VOID";
    public static final String LOSS_FIRE = "FIRE";
    public static final String LOSS_DESPAWN = "DESPAWN";
    public static final String LOSS_CACTUS = "CACTUS";
    public static final String LOSS_EXPLOSION = "EXPLOSION";
    public static final String LOSS_UNKNOWN = "UNKNOWN";

    public InventoryBackupManager(JavaPlugin plugin, Messages messages, File configFile, Economy economy) {
        this.plugin = plugin;
        this.messages = messages;
        this.economy = economy;
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String dataDir = config.getString("storage.data_dir", "userdata");
        this.storageDir = new File(plugin.getDataFolder(), dataDir);
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create storage directory: " + storageDir.getAbsolutePath());
        }
        
        loadConfig(config);
    }

    private void loadConfig(YamlConfiguration config) {
        // Backup settings
        maxBackupsPerPlayer = config.getInt("backup.max_backups_per_player", 10);
        expiryDays = config.getInt("backup.expiry_days", 7);
        autoBackupEnabled = config.getBoolean("backup.auto_backup_enabled", true);
        autoBackupInterval = config.getLong("backup.auto_backup_interval", 60) * 1000L;
        
        // Restore settings
        restoreCost = config.getDouble("restore.cost", 500.0);
        verifiedLossCost = config.getDouble("restore.verified_loss_cost", 100.0);
        restoreCooldown = config.getLong("restore.cooldown", 3600) * 1000L;
        requireConfirmation = config.getBoolean("restore.require_confirmation", true);
        clearBeforeRestore = config.getBoolean("restore.clear_before_restore", true);
        blockedItems = config.getStringList("restore.blocked_items");
        
        // Anti-dupe settings
        skipDuplicateContent = config.getBoolean("anti_dupe.skip_duplicate_content", true);
        trackRestored = config.getBoolean("anti_dupe.track_restored", true);
        notifyAdmins = config.getBoolean("anti_dupe.notify_admins", true);
        restoreCooldownPerBackup = config.getLong("anti_dupe.restore_cooldown_per_backup", 86400) * 1000L;
        requireVerifiedLoss = config.getBoolean("anti_dupe.require_verified_loss", false);
    }

    /**
     * Create a backup of the player's inventory.
     * Uses git-like change detection to avoid duplicate backups.
     * 
     * @param player The player whose inventory to backup
     * @param trigger The event that triggered this backup
     * @return true if backup was created, false if skipped (duplicate or rate limited)
     */
    public boolean createBackup(Player player, String trigger) {
        return createBackup(player, trigger, false, null);
    }

    /**
     * Create a backup with verified loss information.
     * 
     * @param player The player whose inventory to backup
     * @param trigger The event that triggered this backup
     * @param verifiedLoss Whether this backup is due to verified item loss
     * @param lossReason The reason for item loss (LAVA, VOID, DESPAWN, etc.)
     * @return true if backup was created, false if skipped
     */
    public boolean createBackup(Player player, String trigger, boolean verifiedLoss, String lossReason) {
        if (!autoBackupEnabled && !LOSS_MANUAL.equals(trigger) && !verifiedLoss) {
            return false;
        }
        
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        // Rate limiting (skip for verified loss events)
        if (!verifiedLoss) {
            Long lastBackup = lastBackupTime.get(uuid);
            if (lastBackup != null && now - lastBackup < autoBackupInterval && !LOSS_MANUAL.equals(trigger)) {
                return false;
            }
        }
        
        // Compute content hash for duplicate detection
        String contentHash = computeInventoryHash(player);
        
        if (skipDuplicateContent && !verifiedLoss) {
            String lastHash = getLastBackupHash(player.getName());
            if (contentHash != null && contentHash.equals(lastHash)) {
                // Content hasn't changed, skip backup
                return false;
            }
        }
        
        // Create backup
        String backupId = UUID.randomUUID().toString().substring(0, 8);
        YamlConfiguration data = loadUserData(player.getName());
        
        ConfigurationSection backupSection = data.createSection("inventory_backups." + backupId);
        backupSection.set("timestamp", now);
        backupSection.set("trigger", trigger);
        backupSection.set("content_hash", contentHash);
        backupSection.set("restored", false);
        backupSection.set("verified_loss", verifiedLoss);
        if (lossReason != null) {
            backupSection.set("loss_reason", lossReason);
        }
        
        // Save inventory contents
        saveInventoryContents(backupSection, player);
        
        // Save armor contents
        saveArmorContents(backupSection, player);
        
        // Save offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            backupSection.set("offhand", offhand);
        }
        
        saveUserData(player.getName(), data);
        lastBackupTime.put(uuid, now);
        
        // Cleanup old backups
        cleanupOldBackups(player.getName());
        
        return true;
    }

    /**
     * Create a manual backup with player feedback.
     */
    public void createManualBackup(Player player) {
        boolean created = createBackup(player, "MANUAL");
        if (created) {
            player.sendMessage(messages.format(player, "invbackup.backup_created"));
        } else {
            player.sendMessage(messages.format(player, "invbackup.backup_skipped"));
        }
    }

    /**
     * Get all backups for a player.
     */
    public List<BackupEntry> getBackups(String playerName) {
        List<BackupEntry> backups = new ArrayList<BackupEntry>();
        YamlConfiguration data = loadUserData(playerName);
        ConfigurationSection section = data.getConfigurationSection("inventory_backups");
        
        if (section == null) {
            return backups;
        }
        
        long now = System.currentTimeMillis();
        long expiryMillis = expiryDays > 0 ? expiryDays * 24L * 60L * 60L * 1000L : 0;
        
        for (String id : section.getKeys(false)) {
            ConfigurationSection backup = section.getConfigurationSection(id);
            if (backup == null) {
                continue;
            }
            
            long timestamp = backup.getLong("timestamp", 0L);
            
            // Skip expired backups
            if (expiryMillis > 0 && now - timestamp > expiryMillis) {
                continue;
            }
            
            String trigger = backup.getString("trigger", "UNKNOWN");
            boolean restored = backup.getBoolean("restored", false);
            int itemCount = countItems(backup);
            boolean verifiedLoss = backup.getBoolean("verified_loss", false);
            String lossReason = backup.getString("loss_reason", null);
            
            backups.add(new BackupEntry(id, timestamp, trigger, restored, itemCount, verifiedLoss, lossReason));
        }
        
        // Sort by timestamp (newest first)
        Collections.sort(backups, new Comparator<BackupEntry>() {
            public int compare(BackupEntry a, BackupEntry b) {
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            }
        });
        
        return backups;
    }

    /**
     * Restore a backup to the player's inventory.
     */
    public boolean restoreBackup(Player player, String backupId) throws BackupException {
        YamlConfiguration data = loadUserData(player.getName());
        ConfigurationSection backup = data.getConfigurationSection("inventory_backups." + backupId);
        
        if (backup == null) {
            throw new BackupException(messages.format(player, "invbackup.not_found"));
        }
        
        boolean isVerifiedLoss = backup.getBoolean("verified_loss", false);
        
        // Check if we require verified loss for restore
        if (requireVerifiedLoss && !isVerifiedLoss && !player.hasPermission("nekosuite.invbackup.bypass")) {
            throw new BackupException(messages.format(player, "invbackup.not_verified_loss"));
        }
        
        // Check cooldown
        long now = System.currentTimeMillis();
        long lastRestore = data.getLong("inventory_backup_last_restore", 0L);
        if (now - lastRestore < restoreCooldown) {
            long remaining = (restoreCooldown - (now - lastRestore)) / 1000;
            Map<String, String> map = new HashMap<String, String>();
            map.put("time", formatDuration(remaining));
            throw new BackupException(messages.format(player, "invbackup.cooldown", map));
        }
        
        // Check per-backup cooldown (anti-dupe)
        if (trackRestored) {
            long lastBackupRestore = backup.getLong("last_restored_at", 0L);
            if (now - lastBackupRestore < restoreCooldownPerBackup) {
                long remaining = (restoreCooldownPerBackup - (now - lastBackupRestore)) / 1000;
                Map<String, String> map = new HashMap<String, String>();
                map.put("time", formatDuration(remaining));
                throw new BackupException(messages.format(player, "invbackup.backup_cooldown", map));
            }
        }
        
        // Calculate cost (verified loss has lower cost)
        double cost = isVerifiedLoss ? verifiedLossCost : restoreCost;
        
        // Check economy
        if (economy != null && cost > 0) {
            double balance = economy.getBalance(player);
            if (balance < cost) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("cost", String.format("%.2f", cost));
                map.put("balance", String.format("%.2f", balance));
                throw new BackupException(messages.format(player, "invbackup.insufficient_balance", map));
            }
        }
        
        // Confirmation check
        if (requireConfirmation) {
            String pending = pendingConfirmations.get(player.getUniqueId());
            if (!backupId.equals(pending)) {
                pendingConfirmations.put(player.getUniqueId(), backupId);
                Map<String, String> confMap = new HashMap<String, String>();
                confMap.put("cost", String.format("%.2f", cost));
                player.sendMessage(messages.format(player, "invbackup.confirm_restore", confMap));
                return false;
            }
            pendingConfirmations.remove(player.getUniqueId());
        }
        
        // Charge economy
        if (economy != null && cost > 0) {
            EconomyResponse response = economy.withdrawPlayer(player, cost);
            if (!response.transactionSuccess()) {
                throw new BackupException(messages.format(player, "invbackup.cost_failure"));
            }
        }
        
        // Clear current inventory if configured
        if (clearBeforeRestore) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(null);
        }
        
        // Restore inventory
        restoreInventoryContents(backup, player);
        restoreArmorContents(backup, player);
        
        // Restore offhand
        if (backup.contains("offhand")) {
            ItemStack offhand = backup.getItemStack("offhand");
            if (offhand != null && !isBlockedItem(offhand)) {
                player.getInventory().setItemInOffHand(offhand);
            }
        }
        
        // Mark as restored
        backup.set("restored", true);
        backup.set("last_restored_at", now);
        data.set("inventory_backup_last_restore", now);
        saveUserData(player.getName(), data);
        
        // Notify admins if configured
        if (notifyAdmins) {
            String lossReason = backup.getString("loss_reason", "N/A");
            String adminMsg = ChatColor.YELLOW + "[NekoSuite] " + player.getName() + " restored backup " + backupId 
                + " (verified=" + isVerifiedLoss + ", reason=" + lossReason + ")";
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("nekosuite.invbackup.admin")) {
                    admin.sendMessage(adminMsg);
                }
            }
        }
        
        Map<String, String> map = new HashMap<String, String>();
        map.put("cost", String.format("%.2f", cost));
        player.sendMessage(messages.format(player, "invbackup.restore_success", map));
        
        return true;
    }

    /**
     * Cancel pending confirmation.
     */
    public void cancelConfirmation(Player player) {
        if (pendingConfirmations.remove(player.getUniqueId()) != null) {
            player.sendMessage(messages.format(player, "invbackup.cancelled"));
        }
    }

    /**
     * Open the backup menu for a player.
     */
    public void openMenu(Player player) {
        openMenu(player, 1);
    }

    /**
     * Open the backup menu with pagination.
     */
    public void openMenu(Player player, int page) {
        List<BackupEntry> backups = getBackups(player.getName());
        int size = 54;
        String title = messages.format(player, "menu.invbackup.title");
        Inventory inv = Bukkit.createInventory(new BackupMenuHolder(page), size, title);
        
        int slotsPerPage = 45; // Leave bottom row for navigation
        int startIndex = (page - 1) * slotsPerPage;
        int totalPages = (int) Math.ceil((double) backups.size() / slotsPerPage);
        if (totalPages == 0) {
            totalPages = 1;
        }
        
        // Add backup items
        int slot = 0;
        for (int i = startIndex; i < backups.size() && slot < slotsPerPage; i++) {
            BackupEntry backup = backups.get(i);
            ItemStack item = createBackupItem(player, backup);
            inv.setItem(slot++, item);
        }
        
        // Navigation
        if (page > 1) {
            ItemStack prev = createItem(Material.ARROW, messages.format(player, "menu.invbackup.prev_page"), 
                new String[]{"ID:prev_" + (page - 1)});
            inv.setItem(45, prev);
        }
        
        if (page < totalPages) {
            ItemStack next = createItem(Material.ARROW, messages.format(player, "menu.invbackup.next_page"), 
                new String[]{"ID:next_" + (page + 1)});
            inv.setItem(53, next);
        }
        
        // Info item
        Map<String, String> infoMap = new HashMap<String, String>();
        infoMap.put("page", String.valueOf(page));
        infoMap.put("total_pages", String.valueOf(totalPages));
        infoMap.put("count", String.valueOf(backups.size()));
        infoMap.put("cost", String.format("%.2f", restoreCost));
        String infoTitle = messages.format(player, "menu.invbackup.info_title", infoMap);
        List<String> infoLore = new ArrayList<String>();
        infoLore.add(messages.format(player, "menu.invbackup.info_lore", infoMap));
        infoLore.add(messages.format(player, "menu.invbackup.cost_lore", infoMap));
        inv.setItem(49, createItem(Material.BOOK, infoTitle, infoLore.toArray(new String[0])));
        
        // Manual backup button
        inv.setItem(47, createItem(Material.CHEST, messages.format(player, "menu.invbackup.create_button"),
            new String[]{messages.format(player, "menu.invbackup.create_lore"), "ID:create_backup"}));
        
        // Close button
        inv.setItem(50, createItem(Material.BARRIER, messages.format(player, "menu.close"), new String[0]));
        
        // Home button
        inv.setItem(51, createHomeButton(player));
        
        player.openInventory(inv);
    }

    /**
     * Handle menu click events.
     */
    public boolean handleMenuClick(Player player, ItemStack clicked, int currentPage) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return true;
        }
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return true;
        }
        
        List<String> lore = clicked.getItemMeta() != null ? clicked.getItemMeta().getLore() : null;
        if (lore != null) {
            for (String line : lore) {
                if (line != null && line.contains("ID:")) {
                    String cleaned = ChatColor.stripColor(line);
                    String id = cleaned.substring(cleaned.indexOf("ID:") + 3).trim();
                    
                    if (id.startsWith("prev_")) {
                        int page = parseInt(id.substring(5));
                        if (page > 0) {
                            openMenu(player, page);
                        }
                        return true;
                    }
                    if (id.startsWith("next_")) {
                        int page = parseInt(id.substring(5));
                        if (page > 0) {
                            openMenu(player, page);
                        }
                        return true;
                    }
                    if (id.equals("create_backup")) {
                        player.closeInventory();
                        createManualBackup(player);
                        return true;
                    }
                    if (id.startsWith("restore_")) {
                        String backupId = id.substring(8);
                        player.closeInventory();
                        try {
                            restoreBackup(player, backupId);
                        } catch (BackupException e) {
                            player.sendMessage(e.getMessage());
                        }
                        return true;
                    }
                }
            }
        }
        return true;
    }

    // Helper methods
    
    private String computeInventoryHash(Player player) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            StringBuilder sb = new StringBuilder();
            
            // Hash main inventory
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    sb.append(item.getType().name()).append(":").append(item.getAmount()).append(";");
                    if (item.hasItemMeta()) {
                        sb.append(item.getItemMeta().toString()).append(";");
                    }
                }
            }
            
            // Hash armor
            for (ItemStack item : player.getInventory().getArmorContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    sb.append(item.getType().name()).append(":").append(item.getAmount()).append(";");
                }
            }
            
            // Hash offhand
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand != null && offhand.getType() != Material.AIR) {
                sb.append(offhand.getType().name()).append(":").append(offhand.getAmount()).append(";");
            }
            
            byte[] digest = md.digest(sb.toString().getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private String getLastBackupHash(String playerName) {
        List<BackupEntry> backups = getBackups(playerName);
        if (backups.isEmpty()) {
            return null;
        }
        
        YamlConfiguration data = loadUserData(playerName);
        String lastId = backups.get(0).getId();
        return data.getString("inventory_backups." + lastId + ".content_hash");
    }

    private void saveInventoryContents(ConfigurationSection section, Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                section.set("inventory." + i, item);
            }
        }
    }

    private void saveArmorContents(ConfigurationSection section, Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack item = armor[i];
            if (item != null && item.getType() != Material.AIR) {
                section.set("armor." + i, item);
            }
        }
    }

    private void restoreInventoryContents(ConfigurationSection backup, Player player) {
        ConfigurationSection invSection = backup.getConfigurationSection("inventory");
        if (invSection == null) {
            return;
        }
        
        for (String key : invSection.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                ItemStack item = invSection.getItemStack(key);
                if (item != null && !isBlockedItem(item)) {
                    player.getInventory().setItem(slot, item);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void restoreArmorContents(ConfigurationSection backup, Player player) {
        ConfigurationSection armorSection = backup.getConfigurationSection("armor");
        if (armorSection == null) {
            return;
        }
        
        ItemStack[] armor = new ItemStack[4];
        for (String key : armorSection.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                if (slot >= 0 && slot < 4) {
                    ItemStack item = armorSection.getItemStack(key);
                    if (item != null && !isBlockedItem(item)) {
                        armor[slot] = item;
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        player.getInventory().setArmorContents(armor);
    }

    private boolean isBlockedItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        String materialName = item.getType().name();
        for (String blocked : blockedItems) {
            if (materialName.equalsIgnoreCase(blocked)) {
                return true;
            }
        }
        return false;
    }

    private int countItems(ConfigurationSection backup) {
        int count = 0;
        ConfigurationSection inv = backup.getConfigurationSection("inventory");
        if (inv != null) {
            count += inv.getKeys(false).size();
        }
        ConfigurationSection armor = backup.getConfigurationSection("armor");
        if (armor != null) {
            count += armor.getKeys(false).size();
        }
        if (backup.contains("offhand")) {
            count++;
        }
        return count;
    }

    private void cleanupOldBackups(String playerName) {
        YamlConfiguration data = loadUserData(playerName);
        ConfigurationSection section = data.getConfigurationSection("inventory_backups");
        if (section == null) {
            return;
        }
        
        List<BackupEntry> backups = getBackups(playerName);
        
        // Remove excess backups
        while (backups.size() > maxBackupsPerPlayer) {
            BackupEntry oldest = backups.get(backups.size() - 1);
            section.set(oldest.getId(), null);
            backups.remove(backups.size() - 1);
        }
        
        // Remove expired backups
        if (expiryDays > 0) {
            long now = System.currentTimeMillis();
            long expiryMillis = expiryDays * 24L * 60L * 60L * 1000L;
            
            for (String id : new ArrayList<String>(section.getKeys(false))) {
                ConfigurationSection backup = section.getConfigurationSection(id);
                if (backup == null) {
                    continue;
                }
                long timestamp = backup.getLong("timestamp", 0L);
                if (now - timestamp > expiryMillis) {
                    section.set(id, null);
                }
            }
        }
        
        saveUserData(playerName, data);
    }

    private ItemStack createBackupItem(Player player, BackupEntry backup) {
        Material mat;
        if (backup.isVerifiedLoss()) {
            mat = Material.ENDER_CHEST; // Verified loss = special icon
        } else if (backup.isRestored()) {
            mat = Material.CHEST_MINECART;
        } else {
            mat = Material.CHEST;
        }
        
        Map<String, String> map = new HashMap<String, String>();
        map.put("time", DATE_FORMAT.format(new Date(backup.getTimestamp())));
        map.put("trigger", backup.getTrigger());
        map.put("items", String.valueOf(backup.getItemCount()));
        map.put("reason", backup.getLossReason() != null ? backup.getLossReason() : "N/A");
        
        String name = messages.format(player, "menu.invbackup.entry_title", map);
        List<String> lore = new ArrayList<String>();
        lore.add(messages.format(player, "menu.invbackup.time_lore", map));
        lore.add(messages.format(player, "menu.invbackup.trigger_lore", map));
        lore.add(messages.format(player, "menu.invbackup.items_lore", map));
        
        if (backup.isVerifiedLoss()) {
            lore.add(messages.format(player, "menu.invbackup.verified_indicator", map));
        }
        
        if (backup.isRestored()) {
            lore.add(messages.format(player, "menu.invbackup.restored_indicator"));
        }
        
        lore.add("");
        lore.add(messages.format(player, "menu.invbackup.click_to_restore"));
        lore.add(ChatColor.DARK_GRAY + "ID:restore_" + backup.getId());
        
        return createItem(mat, name, lore.toArray(new String[0]));
    }

    private ItemStack createItem(Material mat, String name, String[] loreArr) {
        ItemStack item = new ItemStack(mat == null ? Material.PAPER : mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.colorize(name));
            List<String> lore = new ArrayList<String>();
            for (String line : loreArr) {
                lore.add(messages.colorize(line));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHomeButton(Player player) {
        ItemStack item = new ItemStack(Material.COMPASS);
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

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    private int parseInt(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private YamlConfiguration loadUserData(String playerName) {
        File file = new File(storageDir, playerName + ".yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create user data file: " + e.getMessage());
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveUserData(String playerName, YamlConfiguration data) {
        File file = new File(storageDir, playerName + ".yml");
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save user data: " + e.getMessage());
        }
    }

    /**
     * Show backup list to player via chat.
     */
    public void showBackupList(Player player) {
        List<BackupEntry> backups = getBackups(player.getName());
        if (backups.isEmpty()) {
            player.sendMessage(messages.format(player, "invbackup.no_backups"));
            return;
        }
        
        player.sendMessage(messages.format(player, "invbackup.list_header"));
        for (int i = 0; i < Math.min(5, backups.size()); i++) {
            BackupEntry backup = backups.get(i);
            Map<String, String> map = new HashMap<String, String>();
            map.put("id", backup.getId());
            map.put("time", DATE_FORMAT.format(new Date(backup.getTimestamp())));
            map.put("trigger", backup.getTrigger());
            map.put("items", String.valueOf(backup.getItemCount()));
            String status = backup.isRestored() ? 
                messages.format(player, "invbackup.status_restored") : 
                messages.format(player, "invbackup.status_available");
            map.put("status", status);
            player.sendMessage(messages.format(player, "invbackup.list_entry", map));
        }
        
        if (backups.size() > 5) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("count", String.valueOf(backups.size() - 5));
            player.sendMessage(messages.format(player, "invbackup.more_backups", map));
        }
    }

    /**
     * Check if auto backup is enabled.
     */
    public boolean isAutoBackupEnabled() {
        return autoBackupEnabled;
    }

    // =====================================================
    // Item Loss Tracking Methods
    // =====================================================
    
    /**
     * Track a dropped item entity to detect if it's destroyed vs picked up.
     * Called when PlayerDropItemEvent fires.
     */
    public void trackDroppedItem(Player player, Item itemEntity) {
        TrackedItem tracked = new TrackedItem(
            player.getUniqueId(),
            player.getName(),
            itemEntity.getItemStack().clone(),
            System.currentTimeMillis()
        );
        trackedDroppedItems.put(itemEntity.getUniqueId(), tracked);
        
        // Schedule cleanup after 10 minutes (in case item is picked up without event)
        final UUID itemUuid = itemEntity.getUniqueId();
        new BukkitRunnable() {
            @Override
            public void run() {
                trackedDroppedItems.remove(itemUuid);
            }
        }.runTaskLater(plugin, 20L * 60 * 10);
    }
    
    /**
     * Called when a tracked item is destroyed (despawn, lava, fire, cactus, etc.)
     * This triggers a verified loss backup.
     */
    public void onTrackedItemDestroyed(UUID itemEntityUuid, String lossReason) {
        TrackedItem tracked = trackedDroppedItems.remove(itemEntityUuid);
        if (tracked == null) {
            return;
        }
        
        Player player = Bukkit.getPlayer(tracked.getOwnerUuid());
        if (player == null || !player.isOnline()) {
            return;
        }
        
        // Create a verified loss backup using the snapshot we have
        createBackupFromSnapshot(player, tracked, lossReason);
    }
    
    /**
     * Called when a tracked item is picked up (by owner or other player).
     * Simply remove it from tracking - no loss occurred.
     */
    public void onTrackedItemPickedUp(UUID itemEntityUuid) {
        trackedDroppedItems.remove(itemEntityUuid);
    }
    
    /**
     * Called when a tracked item despawns naturally.
     */
    public void onTrackedItemDespawn(UUID itemEntityUuid) {
        onTrackedItemDestroyed(itemEntityUuid, LOSS_DESPAWN);
    }
    
    /**
     * Save a pre-damage snapshot when player takes dangerous damage.
     * Called before player might die from lava, void, etc.
     */
    public void savePreDamageSnapshot(Player player, EntityDamageEvent.DamageCause cause) {
        String lossReason = mapDamageCauseToLossReason(cause);
        if (lossReason == null) {
            return;
        }
        
        InventorySnapshot snapshot = new InventorySnapshot(
            player.getInventory().getContents().clone(),
            player.getInventory().getArmorContents().clone(),
            player.getInventory().getItemInOffHand().clone(),
            System.currentTimeMillis(),
            lossReason
        );
        preDamageSnapshots.put(player.getUniqueId(), snapshot);
    }
    
    /**
     * Called when player dies. Check if they had a pre-damage snapshot.
     * If so, and items were lost in a verified way, create a backup.
     */
    public void onPlayerDeath(Player player, boolean keepInventory) {
        InventorySnapshot snapshot = preDamageSnapshots.remove(player.getUniqueId());
        
        if (keepInventory) {
            // No items lost, no backup needed
            return;
        }
        
        if (snapshot != null) {
            // We have a verified loss snapshot from dangerous damage
            createBackupFromSnapshot(player, snapshot);
        } else {
            // Normal death - create backup but not verified
            createBackup(player, LOSS_DEATH, false, null);
        }
    }
    
    /**
     * Clear pre-damage snapshot if player survives.
     */
    public void clearPreDamageSnapshot(Player player) {
        preDamageSnapshots.remove(player.getUniqueId());
    }
    
    private void createBackupFromSnapshot(Player player, TrackedItem tracked, String lossReason) {
        // For single item loss, we don't create full backup, just log it
        // The main use case is death with pre-damage snapshot
        plugin.getLogger().info("Tracked item lost: " + tracked.getItem().getType().name() 
            + " x" + tracked.getItem().getAmount() + " by " + tracked.getOwnerName() 
            + " (reason: " + lossReason + ")");
    }
    
    private void createBackupFromSnapshot(Player player, InventorySnapshot snapshot) {
        String backupId = UUID.randomUUID().toString().substring(0, 8);
        YamlConfiguration data = loadUserData(player.getName());
        
        ConfigurationSection backupSection = data.createSection("inventory_backups." + backupId);
        backupSection.set("timestamp", snapshot.getTimestamp());
        backupSection.set("trigger", LOSS_DEATH);
        backupSection.set("content_hash", "snapshot");
        backupSection.set("restored", false);
        backupSection.set("verified_loss", true);
        backupSection.set("loss_reason", snapshot.getLossReason());
        
        // Save inventory from snapshot
        ItemStack[] contents = snapshot.getInventoryContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                backupSection.set("inventory." + i, item);
            }
        }
        
        // Save armor from snapshot
        ItemStack[] armor = snapshot.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack item = armor[i];
            if (item != null && item.getType() != Material.AIR) {
                backupSection.set("armor." + i, item);
            }
        }
        
        // Save offhand from snapshot
        ItemStack offhand = snapshot.getOffhand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            backupSection.set("offhand", offhand);
        }
        
        saveUserData(player.getName(), data);
        lastBackupTime.put(player.getUniqueId(), System.currentTimeMillis());
        cleanupOldBackups(player.getName());
        
        // Notify player
        Map<String, String> map = new HashMap<String, String>();
        map.put("reason", snapshot.getLossReason());
        player.sendMessage(messages.format(player, "invbackup.verified_backup_created", map));
    }
    
    private String mapDamageCauseToLossReason(EntityDamageEvent.DamageCause cause) {
        switch (cause) {
            case LAVA:
            case HOT_FLOOR:
                return LOSS_LAVA;
            case VOID:
                return LOSS_VOID;
            case FIRE:
            case FIRE_TICK:
                return LOSS_FIRE;
            case CONTACT: // Cactus
                return LOSS_CACTUS;
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION:
                return LOSS_EXPLOSION;
            default:
                return null; // Not a verified loss cause
        }
    }
    
    /**
     * Periodic cleanup of tracked items (call from scheduler).
     */
    public void cleanupTrackedItems() {
        long now = System.currentTimeMillis();
        long maxAge = 10 * 60 * 1000L; // 10 minutes
        
        Iterator<Map.Entry<UUID, TrackedItem>> it = trackedDroppedItems.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TrackedItem> entry = it.next();
            if (now - entry.getValue().getDropTime() > maxAge) {
                it.remove();
            }
        }
    }

    /**
     * Backup entry data class.
     */
    public static class BackupEntry {
        private final String id;
        private final long timestamp;
        private final String trigger;
        private final boolean restored;
        private final int itemCount;
        private final boolean verifiedLoss;
        private final String lossReason;

        public BackupEntry(String id, long timestamp, String trigger, boolean restored, int itemCount, 
                          boolean verifiedLoss, String lossReason) {
            this.id = id;
            this.timestamp = timestamp;
            this.trigger = trigger;
            this.restored = restored;
            this.itemCount = itemCount;
            this.verifiedLoss = verifiedLoss;
            this.lossReason = lossReason;
        }

        public String getId() {
            return id;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getTrigger() {
            return trigger;
        }

        public boolean isRestored() {
            return restored;
        }

        public int getItemCount() {
            return itemCount;
        }
        
        public boolean isVerifiedLoss() {
            return verifiedLoss;
        }
        
        public String getLossReason() {
            return lossReason;
        }
    }
    
    /**
     * Tracked dropped item info.
     */
    private static class TrackedItem {
        private final UUID ownerUuid;
        private final String ownerName;
        private final ItemStack item;
        private final long dropTime;
        
        TrackedItem(UUID ownerUuid, String ownerName, ItemStack item, long dropTime) {
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.item = item;
            this.dropTime = dropTime;
        }
        
        UUID getOwnerUuid() {
            return ownerUuid;
        }
        
        String getOwnerName() {
            return ownerName;
        }
        
        ItemStack getItem() {
            return item;
        }
        
        long getDropTime() {
            return dropTime;
        }
    }
    
    /**
     * Pre-damage inventory snapshot.
     */
    private static class InventorySnapshot {
        private final ItemStack[] inventoryContents;
        private final ItemStack[] armorContents;
        private final ItemStack offhand;
        private final long timestamp;
        private final String lossReason;
        
        InventorySnapshot(ItemStack[] inventoryContents, ItemStack[] armorContents, 
                         ItemStack offhand, long timestamp, String lossReason) {
            this.inventoryContents = inventoryContents;
            this.armorContents = armorContents;
            this.offhand = offhand;
            this.timestamp = timestamp;
            this.lossReason = lossReason;
        }
        
        ItemStack[] getInventoryContents() {
            return inventoryContents;
        }
        
        ItemStack[] getArmorContents() {
            return armorContents;
        }
        
        ItemStack getOffhand() {
            return offhand;
        }
        
        long getTimestamp() {
            return timestamp;
        }
        
        String getLossReason() {
            return lossReason;
        }
    }

    /**
     * Backup menu holder for inventory identification.
     */
    public static class BackupMenuHolder implements InventoryHolder {
        private final int currentPage;

        public BackupMenuHolder(int currentPage) {
            this.currentPage = currentPage;
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public Inventory getInventory() {
            return null;
        }
    }

    /**
     * Backup exception for error handling.
     */
    public static class BackupException extends Exception {
        public BackupException(String message) {
            super(message);
        }
    }
}
