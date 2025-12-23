package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Survival Arena Game Module - Wave-based monster defense game.
 * 
 * Features:
 * - Multiple waves of progressively harder monsters
 * - Arena-based gameplay
 * - Score tracking and rewards
 * - Configurable wave composition
 * - Multi-player support
 */
public class SurvivalArenaManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final MenuLayout menuLayout;
    private final File configFile;
    private final File storageDir;
    private final Random random = new Random();

    // Game configuration
    private boolean enabled = true;
    private int waveCooldown = 10; // Seconds between waves
    private int maxWaves = 10;
    private int baseScore = 10;
    private int waveScoreMultiplier = 5;
    private double healthMultiplier = 1.2;
    private double damageMultiplier = 1.1;
    private int spawnRadius = 10;
    private int arenaRadius = 20;
    private int waveTimeout = 120; // Seconds before wave times out (0 = no timeout)
    private boolean highlightMobs = true; // Make arena mobs glow
    private List<WaveConfig> waveConfigs = new ArrayList<WaveConfig>();
    private List<String> commandRewards = new ArrayList<String>();

    // Active arena sessions
    private final Map<String, ArenaSession> activeSessions = new HashMap<String, ArenaSession>();
    // Arena locations (configured spawn points)
    private final Map<String, Location> arenaLocations = new HashMap<String, Location>();

    // Callback for opening games menu (set by plugin)
    private java.util.function.Consumer<Player> openGamesMenuCallback;

    public SurvivalArenaManager(JavaPlugin plugin, Messages messages, File configFile, MenuLayout menuLayout) {
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
        waveCooldown = config.getInt("game.wave_cooldown", 10);
        maxWaves = config.getInt("game.max_waves", 10);
        baseScore = config.getInt("game.base_score", 10);
        waveScoreMultiplier = config.getInt("game.wave_score_multiplier", 5);
        healthMultiplier = config.getDouble("game.health_multiplier", 1.2);
        damageMultiplier = config.getDouble("game.damage_multiplier", 1.1);
        spawnRadius = config.getInt("game.spawn_radius", 10);
        arenaRadius = config.getInt("game.arena_radius", 20);
        waveTimeout = config.getInt("game.wave_timeout", 120);
        highlightMobs = config.getBoolean("game.highlight_mobs", true);
        commandRewards = config.getStringList("rewards.commands");

        // Load wave configurations
        waveConfigs.clear();
        ConfigurationSection wavesSection = config.getConfigurationSection("waves");
        if (wavesSection != null) {
            for (String key : wavesSection.getKeys(false)) {
                ConfigurationSection waveSection = wavesSection.getConfigurationSection(key);
                if (waveSection != null) {
                    WaveConfig waveConfig = WaveConfig.fromSection(waveSection);
                    if (waveConfig != null) {
                        waveConfigs.add(waveConfig);
                    }
                }
            }
        }

        // Default wave configs if none loaded
        if (waveConfigs.isEmpty()) {
            waveConfigs.add(new WaveConfig(1, 5, new EntityType[]{EntityType.ZOMBIE}));
            waveConfigs.add(new WaveConfig(2, 7, new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON}));
            waveConfigs.add(new WaveConfig(3, 10, new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER}));
        }

        // Load arena locations
        arenaLocations.clear();
        ConfigurationSection arenasSection = config.getConfigurationSection("arenas");
        if (arenasSection != null) {
            for (String arenaId : arenasSection.getKeys(false)) {
                ConfigurationSection arenaSection = arenasSection.getConfigurationSection(arenaId);
                if (arenaSection != null) {
                    String worldName = arenaSection.getString("world", "world");
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        double x = arenaSection.getDouble("x", 0);
                        double y = arenaSection.getDouble("y", 64);
                        double z = arenaSection.getDouble("z", 0);
                        arenaLocations.put(arenaId, new Location(world, x, y, z));
                    }
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set callback for opening games menu.
     */
    public void setOpenGamesMenuCallback(java.util.function.Consumer<Player> callback) {
        this.openGamesMenuCallback = callback;
    }

    // ============ Public API ============

    /**
     * Start a new arena game for a player.
     */
    public void startGame(Player player) {
        startGame(player, "default");
    }

    /**
     * Start a new arena game at a specific arena.
     */
    public void startGame(Player player, String arenaId) {
        if (!enabled) {
            player.sendMessage(messages.format(player, "arena.disabled"));
            return;
        }

        String playerName = player.getName();
        if (activeSessions.containsKey(playerName)) {
            player.sendMessage(messages.format(player, "arena.already_in_game"));
            return;
        }

        Location arenaLoc = arenaLocations.get(arenaId);
        if (arenaLoc == null) {
            // Use player's current location as arena center
            arenaLoc = player.getLocation().clone();
        }

        // Create game session
        ArenaSession session = new ArenaSession(playerName, arenaLoc, maxWaves);
        activeSessions.put(playerName, session);

        // Teleport player to arena
        player.teleport(arenaLoc);

        // Send game start message
        Map<String, String> map = new HashMap<String, String>();
        map.put("max_waves", String.valueOf(maxWaves));
        map.put("cooldown", String.valueOf(waveCooldown));
        player.sendMessage(messages.format(player, "arena.game_started", map));

        // Start first wave after cooldown
        startNextWave(player, session);
    }

    /**
     * Handle mob kill by player in arena.
     */
    public void onMobKill(Player player, Entity entity) {
        String playerName = player.getName();
        ArenaSession session = activeSessions.get(playerName);
        if (session == null || session.isEnded()) {
            return;
        }

        // Check if this mob belongs to the arena
        if (session.isArenaMob(entity.getUniqueId())) {
            session.removeMob(entity.getUniqueId());
            int points = calculateKillScore(session.getCurrentWave());
            session.addScore(points);
            session.incrementKills();

            Map<String, String> map = new HashMap<String, String>();
            map.put("points", String.valueOf(points));
            map.put("total", String.valueOf(session.getScore()));
            player.sendMessage(messages.format(player, "arena.mob_killed", map));

            // Check if wave is complete
            if (session.getMobCount() == 0) {
                // Cancel mob check task to prevent duplicate completeWave call
                if (session.getMobCheckTask() != null) {
                    session.getMobCheckTask().cancel();
                    session.setMobCheckTask(null);
                }
                completeWave(player, session);
            }
        }
    }

    /**
     * Handle player death in arena.
     */
    public void onPlayerDeath(Player player) {
        String playerName = player.getName();
        ArenaSession session = activeSessions.get(playerName);
        if (session == null || session.isEnded()) {
            return;
        }

        endGame(player, false);
    }

    /**
     * End the game.
     */
    public void endGame(Player player) {
        endGame(player, false);
    }

    private void endGame(Player player, boolean completed) {
        String playerName = player.getName();
        ArenaSession session = activeSessions.get(playerName);
        if (session == null) {
            player.sendMessage(messages.format(player, "arena.no_active_game"));
            return;
        }

        session.setEnded(true);

        // Cancel any running tasks
        if (session.getWaveTask() != null) {
            session.getWaveTask().cancel();
        }
        if (session.getMobCheckTask() != null) {
            session.getMobCheckTask().cancel();
        }
        if (session.getWaveTimeoutTask() != null) {
            session.getWaveTimeoutTask().cancel();
        }

        // Remove all arena mobs
        clearArenaMobs(session);

        // Calculate and grant rewards
        if (completed || session.getScore() > 0) {
            grantRewards(player, session);
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put("waves", String.valueOf(session.getCurrentWave()));
        map.put("score", String.valueOf(session.getScore()));
        map.put("kills", String.valueOf(session.getTotalKills()));

        if (completed) {
            player.sendMessage(messages.format(player, "arena.game_completed", map));
        } else {
            player.sendMessage(messages.format(player, "arena.game_over", map));
        }

        // Remove session
        activeSessions.remove(playerName);
    }

    /**
     * Show game status.
     */
    public void showStatus(Player player) {
        String playerName = player.getName();
        ArenaSession session = activeSessions.get(playerName);
        if (session == null) {
            player.sendMessage(messages.format(player, "arena.no_active_game"));
            return;
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put("wave", String.valueOf(session.getCurrentWave()));
        map.put("max_waves", String.valueOf(session.getMaxWaves()));
        map.put("score", String.valueOf(session.getScore()));
        map.put("kills", String.valueOf(session.getTotalKills()));
        map.put("remaining", String.valueOf(session.getMobCount()));

        player.sendMessage(messages.format(player, "arena.status", map));
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
            player.sendMessage(messages.format(player, "arena.disabled"));
            return;
        }

        ArenaSession session = activeSessions.get(player.getName());
        if (session != null && !session.isEnded()) {
            openStatusMenu(player, session);
        } else {
            openStartMenu(player);
        }
    }

    // ============ Wave Management ============

    private void startNextWave(Player player, ArenaSession session) {
        if (session.getCurrentWave() >= session.getMaxWaves()) {
            endGame(player, true);
            return;
        }

        session.incrementWave();
        int wave = session.getCurrentWave();

        Map<String, String> map = new HashMap<String, String>();
        map.put("wave", String.valueOf(wave));
        map.put("cooldown", String.valueOf(waveCooldown));
        player.sendMessage(messages.format(player, "arena.wave_starting", map));

        // Schedule wave start after cooldown
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || session.isEnded()) {
                    return;
                }
                spawnWave(player, session);
            }
        }.runTaskLater(plugin, waveCooldown * 20L);

        session.setWaveTask(task);
    }

    private void spawnWave(Player player, ArenaSession session) {
        int wave = session.getCurrentWave();
        WaveConfig config = getWaveConfig(wave);

        Location center = session.getArenaCenter();
        int mobCount = config.getMobCount();

        // Apply wave scaling
        double healthScale = Math.pow(healthMultiplier, wave - 1);
        double damageScale = Math.pow(damageMultiplier, wave - 1);

        Map<String, String> map = new HashMap<String, String>();
        map.put("wave", String.valueOf(wave));
        map.put("count", String.valueOf(mobCount));
        player.sendMessage(messages.format(player, "arena.wave_spawned", map));

        // Spawn mobs
        for (int i = 0; i < mobCount; i++) {
            EntityType type = config.getRandomMobType(random);
            Location spawnLoc = getRandomSpawnLocation(center);

            Entity entity = center.getWorld().spawnEntity(spawnLoc, type);
            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                // Scale health
                double baseHealth = living.getMaxHealth();
                double newHealth = Math.min(baseHealth * healthScale, 2048);
                living.setMaxHealth(newHealth);
                living.setHealth(newHealth);
                // Highlight arena mobs with glowing effect
                if (highlightMobs) {
                    living.setGlowing(true);
                }
            }

            session.addMob(entity.getUniqueId());
        }

        // Start mob check task to handle mobs that die from non-player causes (e.g., sunlight)
        startMobCheckTask(player, session);

        // Start wave timeout task if configured
        if (waveTimeout > 0) {
            startWaveTimeoutTask(player, session);
        }
    }

    /**
     * Start a periodic task to check for dead mobs that weren't killed by player.
     * This handles cases like mobs burning in sunlight.
     */
    private void startMobCheckTask(Player player, ArenaSession session) {
        // Cancel any existing mob check task
        if (session.getMobCheckTask() != null) {
            session.getMobCheckTask().cancel();
        }

        BukkitTask checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || session.isEnded()) {
                    cancel();
                    return;
                }

                // Check all tracked mobs and remove dead ones
                List<UUID> deadMobs = new ArrayList<UUID>();
                for (UUID mobId : session.getMobIds()) {
                    Entity entity = Bukkit.getEntity(mobId);
                    if (entity == null || entity.isDead()) {
                        deadMobs.add(mobId);
                    }
                }

                // Remove dead mobs from session and award points
                // (Mobs killed by environment like sunlight are counted as successful kills)
                for (UUID deadId : deadMobs) {
                    session.removeMob(deadId);
                    int points = calculateKillScore(session.getCurrentWave());
                    session.addScore(points);
                    session.incrementKills();
                }

                // Notify player of environmental kills if any
                if (!deadMobs.isEmpty() && player.isOnline()) {
                    Map<String, String> map = new HashMap<String, String>();
                    int totalPoints = deadMobs.size() * calculateKillScore(session.getCurrentWave());
                    map.put("points", String.valueOf(totalPoints));
                    map.put("total", String.valueOf(session.getScore()));
                    map.put("count", String.valueOf(deadMobs.size()));
                    player.sendMessage(messages.format(player, "arena.mob_died_environment", map));
                }

                // Check if wave is complete
                if (session.getMobCount() == 0) {
                    cancel();
                    completeWave(player, session);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second

        session.setMobCheckTask(checkTask);
    }

    /**
     * Start a timeout task for the current wave.
     * If the wave is not completed within the timeout, the game ends.
     */
    private void startWaveTimeoutTask(Player player, ArenaSession session) {
        // Cancel any existing timeout task
        if (session.getWaveTimeoutTask() != null) {
            session.getWaveTimeoutTask().cancel();
        }

        BukkitTask timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || session.isEnded() || session.getMobCount() == 0) {
                    return;
                }

                // Wave timed out - end the game
                Map<String, String> map = new HashMap<String, String>();
                map.put("wave", String.valueOf(session.getCurrentWave()));
                map.put("remaining", String.valueOf(session.getMobCount()));
                map.put("timeout", String.valueOf(waveTimeout));
                player.sendMessage(messages.format(player, "arena.wave_timeout", map));

                endGame(player, false);
            }
        }.runTaskLater(plugin, waveTimeout * 20L);

        session.setWaveTimeoutTask(timeoutTask);
    }

    private void completeWave(Player player, ArenaSession session) {
        // Cancel timeout task when wave is completed
        if (session.getWaveTimeoutTask() != null) {
            session.getWaveTimeoutTask().cancel();
            session.setWaveTimeoutTask(null);
        }

        int wave = session.getCurrentWave();
        int bonus = wave * waveScoreMultiplier;
        session.addScore(bonus);

        Map<String, String> map = new HashMap<String, String>();
        map.put("wave", String.valueOf(wave));
        map.put("bonus", String.valueOf(bonus));
        map.put("total", String.valueOf(session.getScore()));
        player.sendMessage(messages.format(player, "arena.wave_complete", map));

        // Start next wave
        startNextWave(player, session);
    }

    private WaveConfig getWaveConfig(int wave) {
        // Find matching wave config or use last one
        for (WaveConfig config : waveConfigs) {
            if (config.getWaveNumber() == wave) {
                return config;
            }
        }
        // Return last config for higher waves
        if (!waveConfigs.isEmpty()) {
            WaveConfig last = waveConfigs.get(waveConfigs.size() - 1);
            // Scale mob count for higher waves with a maximum cap to prevent performance issues
            int scaledCount = last.getMobCount() + (wave - last.getWaveNumber()) * 2;
            int maxMobCount = 100; // Maximum mobs per wave to prevent lag
            scaledCount = Math.min(scaledCount, maxMobCount);
            return new WaveConfig(wave, scaledCount, last.getMobTypes());
        }
        return new WaveConfig(wave, 5, new EntityType[]{EntityType.ZOMBIE});
    }

    private Location getRandomSpawnLocation(Location center) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = spawnRadius + random.nextDouble() * (arenaRadius - spawnRadius);
        
        double x = center.getX() + Math.cos(angle) * distance;
        double z = center.getZ() + Math.sin(angle) * distance;
        double y = center.getWorld().getHighestBlockYAt((int) x, (int) z) + 1;

        return new Location(center.getWorld(), x, y, z);
    }

    private void clearArenaMobs(ArenaSession session) {
        for (UUID mobId : session.getMobIds()) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        session.clearMobs();
    }

    private int calculateKillScore(int wave) {
        return baseScore + (wave - 1) * 2;
    }

    private void grantRewards(Player player, ArenaSession session) {
        for (String command : commandRewards) {
            String cmd = command
                .replace("{player}", player.getName())
                .replace("%player%", player.getName())
                .replace("{score}", String.valueOf(session.getScore()))
                .replace("{waves}", String.valueOf(session.getCurrentWave()))
                .replace("{kills}", String.valueOf(session.getTotalKills()));
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    // ============ Menu Methods ============

    private void openStartMenu(Player player) {
        String title = messages.format(player, "menu.arena.title");
        Inventory inv = Bukkit.createInventory(new ArenaMenuHolder(MenuType.START), 27, title);

        // Game info
        Map<String, String> infoMap = new HashMap<String, String>();
        infoMap.put("max_waves", String.valueOf(maxWaves));
        ItemStack infoItem = createItem(Material.IRON_SWORD,
            messages.format(player, "menu.arena.info_title"),
            new String[]{
                messages.format(player, "menu.arena.info_desc1"),
                messages.format(player, "menu.arena.info_desc2", infoMap),
                messages.format(player, "menu.arena.info_desc3")
            });
        safeSet(inv, 4, infoItem);

        // Start button
        ItemStack startItem = createItem(Material.LIME_WOOL,
            messages.format(player, "menu.arena.start_button"),
            new String[]{
                messages.format(player, "menu.arena.start_lore"),
                "",
                messages.format(player, "menu.arena.click_to_start"),
                "ID:start_game"
            });
        safeSet(inv, 13, startItem);

        // Back to games button
        ItemStack backItem = createItem(Material.ARROW,
            messages.format(player, "menu.arena.back_to_games"),
            new String[]{
                messages.format(player, "menu.arena.back_to_games_lore"),
                "ID:back_games"
            });
        safeSet(inv, 18, backItem);

        // Close button
        ItemStack closeItem = createItem(Material.BARRIER,
            messages.format(player, "menu.close"),
            new String[]{"ID:close"});
        safeSet(inv, 26, closeItem);

        player.openInventory(inv);
    }

    private void openStatusMenu(Player player, ArenaSession session) {
        String title = messages.format(player, "menu.arena.status_title");
        Inventory inv = Bukkit.createInventory(new ArenaMenuHolder(MenuType.STATUS), 27, title);

        // Status info
        Map<String, String> statusMap = new HashMap<String, String>();
        statusMap.put("wave", String.valueOf(session.getCurrentWave()));
        statusMap.put("max_waves", String.valueOf(session.getMaxWaves()));
        statusMap.put("score", String.valueOf(session.getScore()));
        statusMap.put("kills", String.valueOf(session.getTotalKills()));
        statusMap.put("remaining", String.valueOf(session.getMobCount()));

        ItemStack statusItem = createItem(Material.CLOCK,
            messages.format(player, "menu.arena.wave_info", statusMap),
            new String[]{
                messages.format(player, "menu.arena.score_lore", statusMap),
                messages.format(player, "menu.arena.kills_lore", statusMap),
                messages.format(player, "menu.arena.remaining_lore", statusMap)
            });
        safeSet(inv, 4, statusItem);

        // End game button
        ItemStack endItem = createItem(Material.RED_WOOL,
            messages.format(player, "menu.arena.end_button"),
            new String[]{
                messages.format(player, "menu.arena.end_lore"),
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

    public void handleMenuClick(Player player, ItemStack clicked, ArenaMenuHolder holder) {
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
            case "back_games":
                player.closeInventory();
                if (openGamesMenuCallback != null) {
                    openGamesMenuCallback.accept(player);
                }
                break;
            case "close":
                player.closeInventory();
                break;
        }
    }

    /**
     * Handle player disconnect.
     */
    public void onPlayerQuit(Player player) {
        String playerName = player.getName();
        ArenaSession session = activeSessions.get(playerName);
        if (session != null && !session.isEnded()) {
            if (session.getWaveTask() != null) {
                session.getWaveTask().cancel();
            }
            if (session.getMobCheckTask() != null) {
                session.getMobCheckTask().cancel();
            }
            if (session.getWaveTimeoutTask() != null) {
                session.getWaveTimeoutTask().cancel();
            }
            clearArenaMobs(session);
            activeSessions.remove(playerName);
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

    // ============ Inner Classes ============

    public enum MenuType {
        START, STATUS
    }

    public static class ArenaMenuHolder implements InventoryHolder {
        private final MenuType menuType;

        public ArenaMenuHolder(MenuType menuType) {
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

    private static class ArenaSession {
        private final String playerName;
        private final Location arenaCenter;
        private final int maxWaves;
        private int currentWave;
        private int score;
        private int totalKills;
        private boolean ended;
        private BukkitTask waveTask;
        private BukkitTask mobCheckTask;
        private BukkitTask waveTimeoutTask;
        private final Set<UUID> mobIds;

        ArenaSession(String playerName, Location arenaCenter, int maxWaves) {
            this.playerName = playerName;
            this.arenaCenter = arenaCenter;
            this.maxWaves = maxWaves;
            this.currentWave = 0;
            this.score = 0;
            this.totalKills = 0;
            this.ended = false;
            this.mobIds = new HashSet<UUID>();
        }

        String getPlayerName() { return playerName; }
        Location getArenaCenter() { return arenaCenter; }
        int getMaxWaves() { return maxWaves; }
        int getCurrentWave() { return currentWave; }
        void incrementWave() { currentWave++; }
        int getScore() { return score; }
        void addScore(int points) { score += points; }
        int getTotalKills() { return totalKills; }
        void incrementKills() { totalKills++; }
        boolean isEnded() { return ended; }
        void setEnded(boolean ended) { this.ended = ended; }
        BukkitTask getWaveTask() { return waveTask; }
        void setWaveTask(BukkitTask task) { this.waveTask = task; }
        BukkitTask getMobCheckTask() { return mobCheckTask; }
        void setMobCheckTask(BukkitTask task) { this.mobCheckTask = task; }
        BukkitTask getWaveTimeoutTask() { return waveTimeoutTask; }
        void setWaveTimeoutTask(BukkitTask task) { this.waveTimeoutTask = task; }

        void addMob(UUID id) { mobIds.add(id); }
        void removeMob(UUID id) { mobIds.remove(id); }
        boolean isArenaMob(UUID id) { return mobIds.contains(id); }
        int getMobCount() { return mobIds.size(); }
        Set<UUID> getMobIds() { return mobIds; }
        void clearMobs() { mobIds.clear(); }
    }

    private static class WaveConfig {
        private final int waveNumber;
        private final int mobCount;
        private final EntityType[] mobTypes;

        WaveConfig(int waveNumber, int mobCount, EntityType[] mobTypes) {
            this.waveNumber = waveNumber;
            this.mobCount = mobCount;
            this.mobTypes = mobTypes;
        }

        static WaveConfig fromSection(ConfigurationSection section) {
            int wave = section.getInt("wave", 1);
            int count = section.getInt("count", 5);
            List<String> typeList = section.getStringList("mobs");
            List<EntityType> types = new ArrayList<EntityType>();
            for (String typeName : typeList) {
                try {
                    EntityType type = EntityType.valueOf(typeName.toUpperCase());
                    types.add(type);
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (types.isEmpty()) {
                types.add(EntityType.ZOMBIE);
            }
            return new WaveConfig(wave, count, types.toArray(new EntityType[0]));
        }

        int getWaveNumber() { return waveNumber; }
        int getMobCount() { return mobCount; }
        EntityType[] getMobTypes() { return mobTypes; }

        EntityType getRandomMobType(Random random) {
            return mobTypes[random.nextInt(mobTypes.length)];
        }
    }
}
