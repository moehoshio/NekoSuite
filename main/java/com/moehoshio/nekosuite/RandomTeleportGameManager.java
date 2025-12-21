package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

/**
 * Random Teleport Game Module - Players are teleported to random locations
 * and must reach a target location within a time limit.
 * 
 * Features:
 * - Teleport player to random location within configured bounds
 * - Set target location player must reach
 * - Time limit for reaching target
 * - Temporarily remove player permissions during game
 * - Support manual termination, timeout failure, and success on reaching target
 * - Rewards for successful completion
 */
public class RandomTeleportGameManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final MenuLayout menuLayout;
    private final File configFile;
    private final File storageDir;
    private final Random random = new Random();

    // Game configuration
    private boolean enabled = true;
    private int defaultTimeLimit = 300; // 5 minutes in seconds
    private int targetRadius = 5; // Blocks from target to count as success
    private int minDistance = 500; // Minimum distance from target
    private int maxDistance = 2000; // Maximum distance from target
    private int worldBorderMin = -10000;
    private int worldBorderMax = 10000;
    private String defaultWorld = "world";
    private List<String> permissionsToRemove = new ArrayList<String>();
    private List<String> commandRewards = new ArrayList<String>();

    // Active game sessions
    private final Map<String, GameSession> activeSessions = new HashMap<String, GameSession>();

    public RandomTeleportGameManager(JavaPlugin plugin, Messages messages, File configFile, MenuLayout menuLayout) {
        this.plugin = plugin;
        this.messages = messages;
        this.configFile = configFile;
        this.menuLayout = menuLayout;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String dataDir = config.getString("storage.data_dir", "userdata");
        storageDir = new File(plugin.getDataFolder(), dataDir);
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            plugin.getLogger().warning("無法創建數據目錄: " + storageDir.getAbsolutePath());
        }
        loadConfig(config);
    }

    private void loadConfig(YamlConfiguration config) {
        enabled = config.getBoolean("game.enabled", true);
        defaultTimeLimit = config.getInt("game.default_time_limit", 300);
        targetRadius = config.getInt("game.target_radius", 5);
        minDistance = config.getInt("game.min_distance", 500);
        maxDistance = config.getInt("game.max_distance", 2000);
        worldBorderMin = config.getInt("game.world_border_min", -10000);
        worldBorderMax = config.getInt("game.world_border_max", 10000);
        defaultWorld = config.getString("game.default_world", "world");
        permissionsToRemove = config.getStringList("game.permissions_to_remove");
        commandRewards = config.getStringList("rewards.commands");
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ============ Public API ============

    /**
     * Start a new random teleport game for a player.
     */
    public void startGame(Player player) {
        startGame(player, null, defaultTimeLimit);
    }

    /**
     * Start a new random teleport game with a specific target location.
     */
    public void startGame(Player player, Location targetLocation, int timeLimit) {
        if (!enabled) {
            player.sendMessage(messages.format(player, "rtpgame.disabled"));
            return;
        }

        String playerName = player.getName();
        if (activeSessions.containsKey(playerName)) {
            player.sendMessage(messages.format(player, "rtpgame.already_in_game"));
            return;
        }

        World world = player.getWorld();
        if (targetLocation == null) {
            targetLocation = generateTargetLocation(world);
        }

        Location startLocation = generateRandomStartLocation(world, targetLocation);
        
        // Store original location for potential restoration
        Location originalLocation = player.getLocation().clone();

        // Create game session
        GameSession session = new GameSession(playerName, originalLocation, startLocation, targetLocation, timeLimit);
        activeSessions.put(playerName, session);

        // Store and remove permissions
        removePlayerPermissions(player, session);

        // Teleport player to start location
        player.teleport(startLocation);

        // Start the game timer
        startGameTimer(player, session);

        // Send game start messages
        Map<String, String> map = new HashMap<String, String>();
        map.put("time", formatTime(timeLimit));
        map.put("target_x", String.valueOf(targetLocation.getBlockX()));
        map.put("target_y", String.valueOf(targetLocation.getBlockY()));
        map.put("target_z", String.valueOf(targetLocation.getBlockZ()));
        map.put("radius", String.valueOf(targetRadius));
        
        player.sendMessage(messages.format(player, "rtpgame.game_started", map));
        player.sendMessage(messages.format(player, "rtpgame.target_info", map));

        saveSession(session);
    }

    /**
     * Check if player has reached the target location.
     */
    public void checkPlayerLocation(Player player) {
        String playerName = player.getName();
        GameSession session = activeSessions.get(playerName);
        if (session == null || session.isEnded()) {
            return;
        }

        Location playerLoc = player.getLocation();
        Location targetLoc = session.getTargetLocation();

        if (playerLoc.getWorld() != null && targetLoc.getWorld() != null 
            && playerLoc.getWorld().equals(targetLoc.getWorld())) {
            double distance = playerLoc.distance(targetLoc);
            if (distance <= targetRadius) {
                completeGame(player, true);
            }
        }
    }

    /**
     * End the game manually (player quits or admin ends it).
     */
    public void endGame(Player player) {
        String playerName = player.getName();
        GameSession session = activeSessions.get(playerName);
        if (session == null) {
            player.sendMessage(messages.format(player, "rtpgame.no_active_game"));
            return;
        }

        completeGame(player, false);
        player.sendMessage(messages.format(player, "rtpgame.game_ended_manual"));
    }

    /**
     * Get current game status for a player.
     */
    public void showStatus(Player player) {
        String playerName = player.getName();
        GameSession session = activeSessions.get(playerName);
        if (session == null) {
            player.sendMessage(messages.format(player, "rtpgame.no_active_game"));
            return;
        }

        Location target = session.getTargetLocation();
        int remainingTime = session.getRemainingTime();
        double distance = player.getLocation().distance(target);

        Map<String, String> map = new HashMap<String, String>();
        map.put("time", formatTime(remainingTime));
        map.put("distance", String.format("%.1f", distance));
        map.put("target_x", String.valueOf(target.getBlockX()));
        map.put("target_y", String.valueOf(target.getBlockY()));
        map.put("target_z", String.valueOf(target.getBlockZ()));
        
        player.sendMessage(messages.format(player, "rtpgame.status", map));
    }

    /**
     * Check if player is in an active game.
     */
    public boolean isInGame(String playerName) {
        return activeSessions.containsKey(playerName);
    }

    /**
     * Open the game menu.
     */
    public void openMenu(Player player) {
        if (!enabled) {
            player.sendMessage(messages.format(player, "rtpgame.disabled"));
            return;
        }

        GameSession session = activeSessions.get(player.getName());
        if (session != null && !session.isEnded()) {
            openStatusMenu(player, session);
        } else {
            openStartMenu(player);
        }
    }

    // ============ Internal Methods ============

    private Location generateTargetLocation(World world) {
        int x = randomInRange(worldBorderMin, worldBorderMax);
        int z = randomInRange(worldBorderMin, worldBorderMax);
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private Location generateRandomStartLocation(World world, Location target) {
        double angle = random.nextDouble() * 2 * Math.PI;
        int distance = minDistance + random.nextInt(maxDistance - minDistance);
        
        int x = target.getBlockX() + (int)(Math.cos(angle) * distance);
        int z = target.getBlockZ() + (int)(Math.sin(angle) * distance);
        
        // Clamp to world border
        x = Math.max(worldBorderMin, Math.min(worldBorderMax, x));
        z = Math.max(worldBorderMin, Math.min(worldBorderMax, z));
        
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private int randomInRange(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /**
     * Remove permissions from player during the game.
     * 
     * NOTE: This implementation tracks which permissions should be removed for restoration
     * but actual permission removal requires integration with Vault or a similar permission
     * management plugin. Administrators can alternatively use the TeleportManager's lock
     * feature (/ntpadmin lock) to prevent teleportation during game sessions.
     * 
     * For full permission control, integrate with Vault's Permission API:
     * <pre>
     * Permission perms = getServer().getServicesManager().getRegistration(Permission.class).getProvider();
     * perms.playerRemove(player, permission);
     * </pre>
     */
    private void removePlayerPermissions(Player player, GameSession session) {
        // Store original permissions that will be removed
        for (String perm : permissionsToRemove) {
            if (player.hasPermission(perm)) {
                session.addRemovedPermission(perm);
                // To implement actual permission removal, integrate with Vault:
                // permission.playerRemove(player, perm);
            }
        }
    }

    /**
     * Restore permissions to player after the game ends.
     * 
     * NOTE: This implementation clears the tracking list. Actual permission restoration
     * requires integration with Vault or a similar permission management plugin.
     * See removePlayerPermissions() for integration notes.
     */
    private void restorePlayerPermissions(Player player, GameSession session) {
        // To implement actual permission restoration, integrate with Vault:
        // for (String perm : session.getRemovedPermissions()) {
        //     permission.playerAdd(player, perm);
        // }
        session.getRemovedPermissions().clear();
    }

    private void startGameTimer(Player player, GameSession session) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    completeGame(player, false);
                    cancel();
                    return;
                }

                GameSession currentSession = activeSessions.get(player.getName());
                if (currentSession == null || currentSession.isEnded()) {
                    cancel();
                    return;
                }

                currentSession.decrementTime();
                
                // Check if player reached target
                checkPlayerLocation(player);

                // Check timeout
                if (currentSession.getRemainingTime() <= 0) {
                    completeGame(player, false);
                    player.sendMessage(messages.format(player, "rtpgame.timeout"));
                    cancel();
                    return;
                }

                // Send periodic reminders
                int remaining = currentSession.getRemainingTime();
                if (remaining == 60 || remaining == 30 || remaining == 10) {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("time", formatTime(remaining));
                    player.sendMessage(messages.format(player, "rtpgame.time_warning", map));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second

        session.setTimerTask(task);
    }

    private void completeGame(Player player, boolean success) {
        String playerName = player.getName();
        GameSession session = activeSessions.get(playerName);
        if (session == null) {
            return;
        }

        session.setEnded(true);
        
        // Cancel timer
        if (session.getTimerTask() != null) {
            session.getTimerTask().cancel();
        }

        // Restore permissions
        restorePlayerPermissions(player, session);

        if (success) {
            // Grant rewards
            grantRewards(player, session);
            player.sendMessage(messages.format(player, "rtpgame.success"));
        }

        // Remove session
        activeSessions.remove(playerName);
        clearSessionFile(playerName);
    }

    private void grantRewards(Player player, GameSession session) {
        for (String command : commandRewards) {
            String cmd = command
                .replace("{player}", player.getName())
                .replace("%player%", player.getName());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private String formatTime(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", mins, secs);
    }

    // ============ Menu Methods ============

    private void openStartMenu(Player player) {
        String title = messages.format(player, "menu.rtpgame.title");
        Inventory inv = Bukkit.createInventory(new RTPGameMenuHolder(MenuType.START), 27, title);

        // Game description
        ItemStack infoItem = createItem(Material.COMPASS,
            messages.format(player, "menu.rtpgame.info_title"),
            new String[]{
                messages.format(player, "menu.rtpgame.info_desc1"),
                messages.format(player, "menu.rtpgame.info_desc2"),
                messages.format(player, "menu.rtpgame.info_desc3")
            });
        safeSet(inv, 4, infoItem);

        // Start button
        Map<String, String> startMap = new HashMap<String, String>();
        startMap.put("time", formatTime(defaultTimeLimit));
        ItemStack startItem = createItem(Material.LIME_WOOL,
            messages.format(player, "menu.rtpgame.start_button"),
            new String[]{
                messages.format(player, "menu.rtpgame.start_lore", startMap),
                "",
                messages.format(player, "menu.rtpgame.click_to_start"),
                "ID:start_game"
            });
        safeSet(inv, 13, startItem);

        // Close button
        ItemStack closeItem = createItem(Material.BARRIER,
            messages.format(player, "menu.close"),
            new String[]{"ID:close"});
        safeSet(inv, 26, closeItem);

        player.openInventory(inv);
    }

    private void openStatusMenu(Player player, GameSession session) {
        String title = messages.format(player, "menu.rtpgame.status_title");
        Inventory inv = Bukkit.createInventory(new RTPGameMenuHolder(MenuType.STATUS), 27, title);

        Location target = session.getTargetLocation();
        double distance = player.getLocation().distance(target);

        // Status info
        Map<String, String> statusMap = new HashMap<String, String>();
        statusMap.put("time", formatTime(session.getRemainingTime()));
        statusMap.put("distance", String.format("%.1f", distance));
        statusMap.put("target_x", String.valueOf(target.getBlockX()));
        statusMap.put("target_y", String.valueOf(target.getBlockY()));
        statusMap.put("target_z", String.valueOf(target.getBlockZ()));

        ItemStack statusItem = createItem(Material.CLOCK,
            messages.format(player, "menu.rtpgame.time_remaining", statusMap),
            new String[]{
                messages.format(player, "menu.rtpgame.distance_lore", statusMap),
                messages.format(player, "menu.rtpgame.target_lore", statusMap)
            });
        safeSet(inv, 4, statusItem);

        // Target compass
        ItemStack compassItem = createItem(Material.COMPASS,
            messages.format(player, "menu.rtpgame.target_compass"),
            new String[]{
                messages.format(player, "menu.rtpgame.target_coords", statusMap)
            });
        safeSet(inv, 13, compassItem);

        // End game button
        ItemStack endItem = createItem(Material.RED_WOOL,
            messages.format(player, "menu.rtpgame.end_button"),
            new String[]{
                messages.format(player, "menu.rtpgame.end_lore"),
                "ID:end_game"
            });
        safeSet(inv, 22, endItem);

        // Close button
        ItemStack closeItem = createItem(Material.BARRIER,
            messages.format(player, "menu.close"),
            new String[]{"ID:close"});
        safeSet(inv, 26, closeItem);

        player.openInventory(inv);
    }

    public void handleMenuClick(Player player, ItemStack clicked, RTPGameMenuHolder holder) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        String id = extractId(clicked);
        if (id == null) {
            return;
        }

        switch (id) {
            case "start_game":
                player.closeInventory();
                startGame(player);
                break;
            case "end_game":
                player.closeInventory();
                endGame(player);
                break;
            case "close":
                player.closeInventory();
                break;
        }
    }

    // ============ Utility Methods ============

    private String extractId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return null;
        }
        for (String line : meta.getLore()) {
            if (line != null && line.contains("ID:")) {
                String cleaned = ChatColor.stripColor(line);
                return cleaned.substring(cleaned.indexOf("ID:") + 3).trim();
            }
        }
        return null;
    }

    private void safeSet(Inventory inv, int slot, ItemStack item) {
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, item);
        }
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

    // ============ Session Persistence ============

    private void saveSession(GameSession session) {
        File file = new File(storageDir, session.getPlayerName() + ".yml");
        YamlConfiguration data;
        if (file.exists()) {
            data = YamlConfiguration.loadConfiguration(file);
        } else {
            data = new YamlConfiguration();
        }

        data.set("rtpgame.active", true);
        data.set("rtpgame.target.world", session.getTargetLocation().getWorld().getName());
        data.set("rtpgame.target.x", session.getTargetLocation().getX());
        data.set("rtpgame.target.y", session.getTargetLocation().getY());
        data.set("rtpgame.target.z", session.getTargetLocation().getZ());
        data.set("rtpgame.remaining_time", session.getRemainingTime());
        data.set("rtpgame.ended", session.isEnded());

        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("無法保存RTP遊戲會話: " + e.getMessage());
        }
    }

    private void clearSessionFile(String playerName) {
        File file = new File(storageDir, playerName + ".yml");
        if (file.exists()) {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            data.set("rtpgame", null);
            try {
                data.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("無法清除RTP遊戲會話: " + e.getMessage());
            }
        }
    }

    /**
     * Handle player disconnect - end their game.
     */
    public void onPlayerQuit(Player player) {
        String playerName = player.getName();
        GameSession session = activeSessions.get(playerName);
        if (session != null && !session.isEnded()) {
            // Cancel timer and restore permissions
            if (session.getTimerTask() != null) {
                session.getTimerTask().cancel();
            }
            restorePlayerPermissions(player, session);
            activeSessions.remove(playerName);
            clearSessionFile(playerName);
        }
    }

    // ============ Inner Classes ============

    public enum MenuType {
        START, STATUS
    }

    public static class RTPGameMenuHolder implements InventoryHolder {
        private final MenuType menuType;

        public RTPGameMenuHolder(MenuType menuType) {
            this.menuType = menuType;
        }

        public MenuType getMenuType() {
            return menuType;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static class GameSession {
        private final String playerName;
        private final Location originalLocation;
        private final Location startLocation;
        private final Location targetLocation;
        private int remainingTime;
        private boolean ended;
        private BukkitTask timerTask;
        private final Set<String> removedPermissions;

        GameSession(String playerName, Location originalLocation, Location startLocation, 
                   Location targetLocation, int timeLimit) {
            this.playerName = playerName;
            this.originalLocation = originalLocation;
            this.startLocation = startLocation;
            this.targetLocation = targetLocation;
            this.remainingTime = timeLimit;
            this.ended = false;
            this.removedPermissions = new HashSet<String>();
        }

        String getPlayerName() { return playerName; }
        Location getOriginalLocation() { return originalLocation; }
        Location getStartLocation() { return startLocation; }
        Location getTargetLocation() { return targetLocation; }
        int getRemainingTime() { return remainingTime; }
        void decrementTime() { remainingTime--; }
        boolean isEnded() { return ended; }
        void setEnded(boolean ended) { this.ended = ended; }
        BukkitTask getTimerTask() { return timerTask; }
        void setTimerTask(BukkitTask task) { this.timerTask = task; }
        Set<String> getRemovedPermissions() { return removedPermissions; }
        void addRemovedPermission(String perm) { removedPermissions.add(perm); }
    }
}
