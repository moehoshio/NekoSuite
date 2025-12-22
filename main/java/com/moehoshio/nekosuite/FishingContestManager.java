package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Fishing Contest Game Module - Timed fishing competition.
 * 
 * Features:
 * - Timed fishing competition
 * - Score based on fish type and rarity
 * - Leaderboard and rankings
 * - Rewards for top players
 * - Support for custom fish with configurable points
 */
public class FishingContestManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final MenuLayout menuLayout;
    private final File configFile;
    private final File storageDir;
    private final Random random = new Random();

    // Game configuration
    private boolean enabled = true;
    private int contestDuration = 600; // 10 minutes in seconds
    private int minPlayers = 1;
    private int maxPlayers = 20;
    private boolean autoStart = false;
    private int autoStartInterval = 3600; // 1 hour

    // Fish point values
    private final Map<Material, Integer> fishPoints = new HashMap<Material, Integer>();
    private int defaultFishPoints = 10;
    private double sizeMultiplier = 1.5; // Bonus for larger fish
    private double rarityMultiplier = 2.0; // Bonus for rare catches

    // Rewards
    private List<String> firstPlaceRewards = new ArrayList<String>();
    private List<String> secondPlaceRewards = new ArrayList<String>();
    private List<String> thirdPlaceRewards = new ArrayList<String>();
    private List<String> participationRewards = new ArrayList<String>();

    // Active contest
    private ContestSession activeContest = null;
    // Player scores in current contest
    private final Map<String, PlayerScore> playerScores = new HashMap<String, PlayerScore>();

    // Callback for opening games menu (set by plugin)
    private java.util.function.Consumer<Player> openGamesMenuCallback;

    public FishingContestManager(JavaPlugin plugin, Messages messages, File configFile, MenuLayout menuLayout) {
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
        contestDuration = config.getInt("game.contest_duration", 600);
        minPlayers = config.getInt("game.min_players", 1);
        maxPlayers = config.getInt("game.max_players", 20);
        autoStart = config.getBoolean("game.auto_start", false);
        autoStartInterval = config.getInt("game.auto_start_interval", 3600);
        defaultFishPoints = config.getInt("scoring.default_points", 10);
        sizeMultiplier = config.getDouble("scoring.size_multiplier", 1.5);
        rarityMultiplier = config.getDouble("scoring.rarity_multiplier", 2.0);

        // Load fish point values
        fishPoints.clear();
        ConfigurationSection fishSection = config.getConfigurationSection("scoring.fish");
        if (fishSection != null) {
            for (String key : fishSection.getKeys(false)) {
                Material mat = Material.matchMaterial(key.toUpperCase());
                if (mat != null) {
                    fishPoints.put(mat, fishSection.getInt(key, defaultFishPoints));
                }
            }
        }

        // Default fish values if not configured
        if (fishPoints.isEmpty()) {
            fishPoints.put(Material.COD, 10);
            fishPoints.put(Material.SALMON, 15);
            fishPoints.put(Material.TROPICAL_FISH, 25);
            fishPoints.put(Material.PUFFERFISH, 30);
        }

        // Load rewards
        firstPlaceRewards = config.getStringList("rewards.first_place");
        secondPlaceRewards = config.getStringList("rewards.second_place");
        thirdPlaceRewards = config.getStringList("rewards.third_place");
        participationRewards = config.getStringList("rewards.participation");
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
     * Start a new fishing contest.
     */
    public void startContest(Player starter) {
        if (!enabled) {
            starter.sendMessage(messages.format(starter, "fishing.disabled"));
            return;
        }

        if (activeContest != null && !activeContest.isEnded()) {
            starter.sendMessage(messages.format(starter, "fishing.already_running"));
            return;
        }

        activeContest = new ContestSession(contestDuration);
        playerScores.clear();

        // Broadcast contest start
        Map<String, String> map = new HashMap<String, String>();
        map.put("duration", formatTime(contestDuration));
        Bukkit.broadcastMessage(messages.format(starter, "fishing.contest_started", map));

        // Start contest timer
        startContestTimer();
    }

    /**
     * Join an active fishing contest.
     */
    public void joinContest(Player player) {
        if (!enabled) {
            player.sendMessage(messages.format(player, "fishing.disabled"));
            return;
        }

        if (activeContest == null || activeContest.isEnded()) {
            player.sendMessage(messages.format(player, "fishing.no_active_contest"));
            return;
        }

        String playerName = player.getName();
        if (playerScores.containsKey(playerName)) {
            player.sendMessage(messages.format(player, "fishing.already_joined"));
            return;
        }

        if (playerScores.size() >= maxPlayers) {
            player.sendMessage(messages.format(player, "fishing.contest_full"));
            return;
        }

        playerScores.put(playerName, new PlayerScore(playerName));
        player.sendMessage(messages.format(player, "fishing.joined"));

        Map<String, String> map = new HashMap<String, String>();
        map.put("player", playerName);
        map.put("players", String.valueOf(playerScores.size()));
        Bukkit.broadcastMessage(messages.format(player, "fishing.player_joined", map));
    }

    /**
     * Handle fish catch by a player.
     */
    public void onFishCatch(Player player, ItemStack caught) {
        if (activeContest == null || activeContest.isEnded()) {
            return;
        }

        String playerName = player.getName();
        PlayerScore score = playerScores.get(playerName);
        if (score == null) {
            // Player not in contest, auto-join if enabled
            if (playerScores.size() < maxPlayers) {
                playerScores.put(playerName, new PlayerScore(playerName));
                score = playerScores.get(playerName);
                player.sendMessage(messages.format(player, "fishing.auto_joined"));
            } else {
                return;
            }
        }

        // Calculate points for catch
        int points = calculatePoints(caught);
        if (points > 0) {
            score.addScore(points);
            score.incrementCatches();

            // Track largest catch
            if (points > score.getLargestCatch()) {
                score.setLargestCatch(points);
            }

            Map<String, String> map = new HashMap<String, String>();
            map.put("fish", caught.getType().name());
            map.put("points", String.valueOf(points));
            map.put("total", String.valueOf(score.getScore()));
            player.sendMessage(messages.format(player, "fishing.fish_caught", map));
        }
    }

    /**
     * End the current contest.
     */
    public void endContest() {
        if (activeContest == null) {
            return;
        }

        activeContest.setEnded(true);

        // Cancel timer
        if (activeContest.getTimerTask() != null) {
            activeContest.getTimerTask().cancel();
        }

        // Calculate rankings
        List<PlayerScore> rankings = new ArrayList<PlayerScore>(playerScores.values());
        Collections.sort(rankings, new Comparator<PlayerScore>() {
            @Override
            public int compare(PlayerScore a, PlayerScore b) {
                return Integer.compare(b.getScore(), a.getScore());
            }
        });

        // Broadcast results - use null for server-wide messages
        Bukkit.broadcastMessage(messages.format((org.bukkit.command.CommandSender) null, "fishing.contest_ended"));
        Bukkit.broadcastMessage(messages.format((org.bukkit.command.CommandSender) null, "fishing.results_header"));

        int rank = 1;
        for (PlayerScore score : rankings) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("rank", String.valueOf(rank));
            map.put("player", score.getPlayerName());
            map.put("score", String.valueOf(score.getScore()));
            map.put("catches", String.valueOf(score.getCatches()));
            Bukkit.broadcastMessage(messages.format((org.bukkit.command.CommandSender) null, "fishing.results_entry", map));

            // Grant rewards
            Player player = Bukkit.getPlayer(score.getPlayerName());
            if (player != null) {
                grantRewards(player, rank, score);
            }
            rank++;
        }

        // Clear contest
        activeContest = null;
        playerScores.clear();
    }

    /**
     * Show contest status.
     */
    public void showStatus(Player player) {
        if (activeContest == null || activeContest.isEnded()) {
            player.sendMessage(messages.format(player, "fishing.no_active_contest"));
            return;
        }

        String playerName = player.getName();
        PlayerScore score = playerScores.get(playerName);

        Map<String, String> map = new HashMap<String, String>();
        map.put("time", formatTime(activeContest.getRemainingTime()));
        map.put("players", String.valueOf(playerScores.size()));

        if (score != null) {
            map.put("score", String.valueOf(score.getScore()));
            map.put("catches", String.valueOf(score.getCatches()));
            map.put("rank", String.valueOf(getRank(playerName)));
            player.sendMessage(messages.format(player, "fishing.status_participating", map));
        } else {
            player.sendMessage(messages.format(player, "fishing.status_spectating", map));
        }
    }

    /**
     * Show leaderboard.
     */
    public void showLeaderboard(Player player) {
        if (activeContest == null || activeContest.isEnded()) {
            player.sendMessage(messages.format(player, "fishing.no_active_contest"));
            return;
        }

        List<PlayerScore> rankings = new ArrayList<PlayerScore>(playerScores.values());
        Collections.sort(rankings, new Comparator<PlayerScore>() {
            @Override
            public int compare(PlayerScore a, PlayerScore b) {
                return Integer.compare(b.getScore(), a.getScore());
            }
        });

        player.sendMessage(messages.format(player, "fishing.leaderboard_header"));
        int rank = 1;
        for (PlayerScore score : rankings) {
            if (rank > 10) break;
            Map<String, String> map = new HashMap<String, String>();
            map.put("rank", String.valueOf(rank));
            map.put("player", score.getPlayerName());
            map.put("score", String.valueOf(score.getScore()));
            player.sendMessage(messages.format(player, "fishing.leaderboard_entry", map));
            rank++;
        }
    }

    /**
     * Check if there's an active contest.
     */
    public boolean isContestActive() {
        return activeContest != null && !activeContest.isEnded();
    }

    /**
     * Check if player is in contest.
     */
    public boolean isInContest(String playerName) {
        return playerScores.containsKey(playerName);
    }

    /**
     * Open the contest menu.
     */
    public void openMenu(Player player) {
        if (!enabled) {
            player.sendMessage(messages.format(player, "fishing.disabled"));
            return;
        }

        if (activeContest != null && !activeContest.isEnded()) {
            openContestMenu(player);
        } else {
            openStartMenu(player);
        }
    }

    // ============ Internal Methods ============

    private void startContestTimer() {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeContest == null || activeContest.isEnded()) {
                    cancel();
                    return;
                }

                activeContest.decrementTime();

                // Broadcast time warnings
                int remaining = activeContest.getRemainingTime();
                if (remaining == 300 || remaining == 60 || remaining == 30 || remaining == 10) {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("time", formatTime(remaining));
                    Bukkit.broadcastMessage(messages.format((org.bukkit.command.CommandSender) null, "fishing.time_warning", map));
                }

                // Check timeout
                if (remaining <= 0) {
                    endContest();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        activeContest.setTimerTask(task);
    }

    private int calculatePoints(ItemStack item) {
        if (item == null) {
            return 0;
        }

        Material type = item.getType();
        int basePoints = fishPoints.getOrDefault(type, defaultFishPoints);

        // Only give points for fish items
        if (!isFish(type)) {
            return 0;
        }

        // Apply size multiplier (random variation)
        double sizeBonus = 1.0 + (random.nextDouble() * (sizeMultiplier - 1.0));

        // Apply rarity multiplier for special fish
        double rarityBonus = isRareFish(type) ? rarityMultiplier : 1.0;

        return (int) Math.round(basePoints * sizeBonus * rarityBonus);
    }

    private boolean isFish(Material type) {
        return type == Material.COD || type == Material.SALMON ||
               type == Material.TROPICAL_FISH || type == Material.PUFFERFISH;
    }

    private boolean isRareFish(Material type) {
        return type == Material.TROPICAL_FISH || type == Material.PUFFERFISH;
    }

    private int getRank(String playerName) {
        List<PlayerScore> rankings = new ArrayList<PlayerScore>(playerScores.values());
        Collections.sort(rankings, new Comparator<PlayerScore>() {
            @Override
            public int compare(PlayerScore a, PlayerScore b) {
                return Integer.compare(b.getScore(), a.getScore());
            }
        });

        int rank = 1;
        for (PlayerScore score : rankings) {
            if (score.getPlayerName().equals(playerName)) {
                return rank;
            }
            rank++;
        }
        return -1;
    }

    private void grantRewards(Player player, int rank, PlayerScore score) {
        List<String> rewards;
        switch (rank) {
            case 1:
                rewards = firstPlaceRewards;
                break;
            case 2:
                rewards = secondPlaceRewards;
                break;
            case 3:
                rewards = thirdPlaceRewards;
                break;
            default:
                rewards = participationRewards;
                break;
        }

        for (String command : rewards) {
            String cmd = command
                .replace("{player}", player.getName())
                .replace("%player%", player.getName())
                .replace("{score}", String.valueOf(score.getScore()))
                .replace("{rank}", String.valueOf(rank))
                .replace("{catches}", String.valueOf(score.getCatches()));
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
        String title = messages.format(player, "menu.fishing.title");
        Inventory inv = Bukkit.createInventory(new FishingMenuHolder(MenuType.START), 27, title);

        // Contest info
        Map<String, String> infoMap = new HashMap<String, String>();
        infoMap.put("duration", formatTime(contestDuration));
        ItemStack infoItem = createItem(Material.FISHING_ROD,
            messages.format(player, "menu.fishing.info_title"),
            new String[]{
                messages.format(player, "menu.fishing.info_desc1"),
                messages.format(player, "menu.fishing.info_desc2", infoMap),
                messages.format(player, "menu.fishing.info_desc3")
            });
        safeSet(inv, 4, infoItem);

        // Start contest button
        ItemStack startItem = createItem(Material.LIME_WOOL,
            messages.format(player, "menu.fishing.start_button"),
            new String[]{
                messages.format(player, "menu.fishing.start_lore"),
                "",
                messages.format(player, "menu.fishing.click_to_start"),
                "ID:start_contest"
            });
        safeSet(inv, 13, startItem);

        // Fish points info
        ItemStack pointsItem = createItem(Material.TROPICAL_FISH,
            messages.format(player, "menu.fishing.points_title"),
            new String[]{
                messages.format(player, "menu.fishing.points_cod"),
                messages.format(player, "menu.fishing.points_salmon"),
                messages.format(player, "menu.fishing.points_tropical"),
                messages.format(player, "menu.fishing.points_puffer")
            });
        safeSet(inv, 11, pointsItem);

        // Rewards info
        ItemStack rewardsItem = createItem(Material.GOLD_INGOT,
            messages.format(player, "menu.fishing.rewards_title"),
            new String[]{
                messages.format(player, "menu.fishing.rewards_first"),
                messages.format(player, "menu.fishing.rewards_second"),
                messages.format(player, "menu.fishing.rewards_third")
            });
        safeSet(inv, 15, rewardsItem);

        // Back to games button
        ItemStack backItem = createItem(Material.ARROW,
            messages.format(player, "menu.fishing.back_to_games"),
            new String[]{
                messages.format(player, "menu.fishing.back_to_games_lore"),
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

    private void openContestMenu(Player player) {
        String title = messages.format(player, "menu.fishing.contest_title");
        Inventory inv = Bukkit.createInventory(new FishingMenuHolder(MenuType.CONTEST), 27, title);

        String playerName = player.getName();
        PlayerScore score = playerScores.get(playerName);

        // Time remaining
        Map<String, String> timeMap = new HashMap<String, String>();
        timeMap.put("time", formatTime(activeContest.getRemainingTime()));
        timeMap.put("players", String.valueOf(playerScores.size()));
        ItemStack timeItem = createItem(Material.CLOCK,
            messages.format(player, "menu.fishing.time_remaining", timeMap),
            new String[]{
                messages.format(player, "menu.fishing.players_count", timeMap)
            });
        safeSet(inv, 4, timeItem);

        // Player score
        if (score != null) {
            Map<String, String> scoreMap = new HashMap<String, String>();
            scoreMap.put("score", String.valueOf(score.getScore()));
            scoreMap.put("catches", String.valueOf(score.getCatches()));
            scoreMap.put("rank", String.valueOf(getRank(playerName)));
            ItemStack scoreItem = createItem(Material.COD,
                messages.format(player, "menu.fishing.your_score", scoreMap),
                new String[]{
                    messages.format(player, "menu.fishing.catches_lore", scoreMap),
                    messages.format(player, "menu.fishing.rank_lore", scoreMap)
                });
            safeSet(inv, 11, scoreItem);
        } else {
            // Join button
            ItemStack joinItem = createItem(Material.LIME_WOOL,
                messages.format(player, "menu.fishing.join_button"),
                new String[]{
                    messages.format(player, "menu.fishing.join_lore"),
                    "ID:join_contest"
                });
            safeSet(inv, 11, joinItem);
        }

        // Leaderboard
        ItemStack leaderItem = createItem(Material.BOOK,
            messages.format(player, "menu.fishing.leaderboard_button"),
            new String[]{
                messages.format(player, "menu.fishing.leaderboard_lore"),
                "ID:leaderboard"
            });
        safeSet(inv, 15, leaderItem);

        // Close button
        ItemStack closeItem = createItem(Material.BARRIER,
            messages.format(player, "menu.close"),
            new String[]{"ID:close"});
        safeSet(inv, 26, closeItem);

        player.openInventory(inv);
    }

    public void handleMenuClick(Player player, ItemStack clicked, FishingMenuHolder holder) {
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
            case "start_contest":
                player.closeInventory();
                startContest(player);
                break;
            case "join_contest":
                player.closeInventory();
                joinContest(player);
                break;
            case "leaderboard":
                player.closeInventory();
                showLeaderboard(player);
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
        START, CONTEST
    }

    public static class FishingMenuHolder implements InventoryHolder {
        private final MenuType menuType;

        public FishingMenuHolder(MenuType menuType) {
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

    private static class ContestSession {
        private int remainingTime;
        private boolean ended;
        private BukkitTask timerTask;

        ContestSession(int duration) {
            this.remainingTime = duration;
            this.ended = false;
        }

        int getRemainingTime() { return remainingTime; }
        void decrementTime() { remainingTime--; }
        boolean isEnded() { return ended; }
        void setEnded(boolean ended) { this.ended = ended; }
        BukkitTask getTimerTask() { return timerTask; }
        void setTimerTask(BukkitTask task) { this.timerTask = task; }
    }

    private static class PlayerScore {
        private final String playerName;
        private int score;
        private int catches;
        private int largestCatch;

        PlayerScore(String playerName) {
            this.playerName = playerName;
            this.score = 0;
            this.catches = 0;
            this.largestCatch = 0;
        }

        String getPlayerName() { return playerName; }
        int getScore() { return score; }
        void addScore(int points) { score += points; }
        int getCatches() { return catches; }
        void incrementCatches() { catches++; }
        int getLargestCatch() { return largestCatch; }
        void setLargestCatch(int points) { largestCatch = points; }
    }
}
