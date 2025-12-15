package org.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class NekoSuitePlugin extends JavaPlugin implements CommandExecutor {

    private static final String NON_DIGIT_PATTERN = "\\D+";
    private static final int DAYS_PER_WEEK = 7;
    private static final int DAYS_PER_MONTH = 30;
    private static final int DAYS_PER_YEAR = 365;

    private WishManager wishManager;
    private EventManager eventManager;

    @Override
    public void onEnable() {
        saveResource("wish_config.yml", false);
        saveResource("event_config.yml", false);
        wishManager = new WishManager(this, new File(getDataFolder(), "wish_config.yml"));
        eventManager = new EventManager(this, new File(getDataFolder(), "event_config.yml"));

        if (getCommand("wish") != null) {
            getCommand("wish").setExecutor(this);
        }
        if (getCommand("wishquery") != null) {
            getCommand("wishquery").setExecutor(this);
        }
        if (getCommand("eventcheck") != null) {
            getCommand("eventcheck").setExecutor(this);
        }
        if (getCommand("eventparticipate") != null) {
            getCommand("eventparticipate").setExecutor(this);
        }

        getLogger().info("NekoSuite Bukkit module enabled (JDK 1.8 compatible).");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        switch (name) {
            case "wish":
                return handleWish(sender, args);
            case "wishquery":
                return handleWishQuery(sender, args);
            case "eventcheck":
                return handleEventCheck(sender);
            case "eventparticipate":
                return handleEventParticipate(sender, args);
            default:
                return false;
        }
    }

    private boolean handleWish(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令僅玩家可用。");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /wish <pool> [count] 或 /wish query <pool>");
            return true;
        }
        Player player = (Player) sender;
        if ("query".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + "用法: /wish query <pool>");
                return true;
            }
            return handleWishQuery(sender, new String[]{args[1]});
        }
        String pool = args[0];
        int count = 1;
        if (args.length > 1) {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "祈願次數無效。");
                return true;
            }
        }

        try {
            List<String> rewards = wishManager.performWish(player, pool, count);
            sender.sendMessage(ChatColor.GREEN + "[NekoSuite] 祈願成功，獲得: " + String.join(", ", rewards));
        } catch (WishException e) {
            sender.sendMessage(ChatColor.RED + "[NekoSuite] 祈願失敗: " + e.getMessage());
        }
        return true;
    }

    private boolean handleWishQuery(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令僅玩家可用。");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /wishquery <pool>");
            return true;
        }
        Player player = (Player) sender;
        WishStatus status = wishManager.queryStatus(player.getName(), args[0]);
        if (!status.isValidPool()) {
            sender.sendMessage(ChatColor.RED + "祈願池不存在。");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "[NekoSuite] 池: " + status.getPool()
                + " 已祈願: " + status.getCount()
                + " 剩餘券: " + status.getTicketCount());
        return true;
    }

    private boolean handleEventCheck(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令僅玩家可用。");
            return true;
        }
        Player player = (Player) sender;
        List<EventAvailability> events = eventManager.listAvailableEvents(player);
        if (events.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "[NekoSuite] 當前沒有可用的活動。");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "[NekoSuite] 可參與活動:");
        for (EventAvailability availability : events) {
            String statusText = availability.isCanParticipate() ? ChatColor.GREEN + "可參與" : ChatColor.RED + "已達上限";
            sender.sendMessage(ChatColor.WHITE + "- " + availability.getDisplayName() + " (" + availability.getId() + "): " + statusText);
        }
        return true;
    }

    private boolean handleEventParticipate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令僅玩家可用。");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /eventparticipate <eventId>");
            return true;
        }
        Player player = (Player) sender;
        try {
            List<String> rewards = eventManager.participate(player, args[0]);
            sender.sendMessage(ChatColor.GREEN + "[NekoSuite] 活動獎勵: " + String.join(", ", rewards));
        } catch (EventException e) {
            sender.sendMessage(ChatColor.RED + "[NekoSuite] 無法參與: " + e.getMessage());
        }
        return true;
    }

    private static class WishManager {
        private final JavaPlugin plugin;
        private final File configFile;
        private final File storageDir;
        private final Random random = new Random();
        private final Map<String, WishPool> pools = new HashMap<String, WishPool>();
        private final List<TicketRule> tickets = new ArrayList<TicketRule>();

        WishManager(JavaPlugin plugin, File configFile) {
            this.plugin = plugin;
            this.configFile = configFile;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String dataDir = config.getString("storage.data_dir", "userdata");
            storageDir = new File(plugin.getDataFolder(), dataDir);
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                plugin.getLogger().warning("無法創建數據目錄: " + storageDir.getAbsolutePath());
            }
            loadConfig(config);
        }

        private void loadConfig(YamlConfiguration config) {
            pools.clear();
            ConfigurationSection poolSection = config.getConfigurationSection("pools");
            if (poolSection != null) {
                Set<String> keys = poolSection.getKeys(false);
                for (String key : keys) {
                    ConfigurationSection section = poolSection.getConfigurationSection(key);
                    if (section == null) {
                        continue;
                    }
                    WishPool pool = WishPool.fromSection(key, section, plugin.getLogger());
                    pools.put(key, pool);
                }
            }

            tickets.clear();
            List<Map<?, ?>> ticketList = config.getMapList("tickets");
            for (Map<?, ?> raw : ticketList) {
                TicketRule rule = TicketRule.fromMap(raw);
                if (rule != null) {
                    tickets.add(rule);
                }
            }
        }

        List<String> performWish(Player player, String poolId, int count) throws WishException {
            if (count <= 0) {
                throw new WishException("祈願次數必須大於0");
            }
            WishPool pool = pools.get(poolId);
            if (pool == null) {
                throw new WishException("祈願池不存在");
            }
            if (!pool.isActive(Instant.now())) {
                throw new WishException("祈願池未開啟或已結束");
            }
            YamlConfiguration data = loadUserData(player.getName());
            String countsName = pool.getCountsName();
            int currentCount = data.getInt("wish.counts." + countsName, 0);

            TicketRule ticketRule = findTicket(poolId);
            if (ticketRule != null) {
                int owned = data.getInt("wish.tickets." + ticketRule.getId(), 0);
                int needed = ticketRule.getDeductCount() * count;
                if (owned < needed) {
                    throw new WishException("祈願券不足 (" + owned + "/" + needed + ")");
                }
                data.set("wish.tickets." + ticketRule.getId(), owned - needed);
            }

            List<String> rewards = new ArrayList<String>();
            int updatedCount = currentCount;
            for (int i = 0; i < count; i++) {
                updatedCount++;
                if (pool.shouldGuarantee(updatedCount)) {
                    rewards.add(pool.pickGuarantee(random));
                    updatedCount = 0;
                } else {
                    rewards.add(pool.pickReward(random));
                }
            }
            data.set("wish.counts." + countsName, updatedCount);
            saveUserData(player.getName(), data);
            for (String reward : rewards) {
                deliverReward(player, reward);
            }
            return rewards;
        }

        private void deliverReward(Player player, String reward) {
            if (reward == null || reward.trim().isEmpty()) {
                return;
            }
            String normalized = reward.trim();
            Material material = Material.matchMaterial(normalized.replace("minecraft:", "").toUpperCase());
            if (material != null) {
                ItemStack itemStack = new ItemStack(material, 1);
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(itemStack);
                if (!overflow.isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
                }
                return;
            }
            if (normalized.toLowerCase().startsWith("exp")) {
                try {
                    int value = Integer.parseInt(normalized.replaceAll(NON_DIGIT_PATTERN, ""));
                    player.giveExp(value);
                    return;
                } catch (NumberFormatException ignored) {
                }
            }
            player.sendMessage(ChatColor.GREEN + "[NekoSuite] 獎勵: " + normalized);
        }

        WishStatus queryStatus(String playerName, String poolId) {
            WishPool pool = pools.get(poolId);
            if (pool == null) {
                return WishStatus.invalid(poolId);
            }
            YamlConfiguration data = loadUserData(playerName);
            int count = data.getInt("wish.counts." + pool.getCountsName(), 0);
            TicketRule ticketRule = findTicket(poolId);
            int ticketsLeft = 0;
            if (ticketRule != null) {
                ticketsLeft = data.getInt("wish.tickets." + ticketRule.getId(), 0);
            }
            return new WishStatus(poolId, count, ticketsLeft, true);
        }

        private TicketRule findTicket(String poolId) {
            for (TicketRule rule : tickets) {
                if (rule.getApplicablePools().contains(poolId)) {
                    return rule;
                }
            }
            return null;
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
    }

    private static class WishPool {
        private final String id;
        private final String countsName;
        private final int maxCount;
        private final WeightedList items;
        private final WeightedList guaranteeItems;
        private final TimeWindow window;

        WishPool(String id, String countsName, int maxCount, WeightedList items, WeightedList guaranteeItems, TimeWindow window) {
            this.id = id;
            this.countsName = countsName;
            this.maxCount = maxCount;
            this.items = items;
            this.guaranteeItems = guaranteeItems;
            this.window = window;
        }

        static WishPool fromSection(String id, ConfigurationSection section, java.util.logging.Logger logger) {
            String countsName = section.getString("counts_name", id);
            int maxCount = section.getInt("max_count", 0);
            WeightedList itemList = WeightedList.fromSection(section.getConfigurationSection("items"));
            WeightedList guarantee = WeightedList.fromSection(section.getConfigurationSection("guarantee_items"));
            TimeWindow window = TimeWindow.fromSection(section.getConfigurationSection("duration"), logger);
            return new WishPool(id, countsName, maxCount, itemList, guarantee, window);
        }

        boolean shouldGuarantee(int currentCount) {
            return maxCount > 0 && guaranteeItems != null && currentCount >= maxCount;
        }

        String pickReward(Random random) {
            if (items == null) {
                return "no_reward";
            }
            return items.pick(random);
        }

        String pickGuarantee(Random random) {
            if (guaranteeItems == null) {
                return pickReward(random);
            }
            return guaranteeItems.pick(random);
        }

        String getCountsName() {
            return countsName;
        }

        boolean isActive(Instant now) {
            return window == null || window.contains(now);
        }
    }

    private static class WeightedList {
        private final List<Entry> entries;
        private final double totalWeight;

        WeightedList(List<Entry> entries) {
            this.entries = entries;
            double total = 0.0;
            for (Entry entry : entries) {
                total += entry.weight;
            }
            this.totalWeight = total;
        }

        static WeightedList fromSection(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            List<Entry> entries = new ArrayList<Entry>();
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                if (value instanceof ConfigurationSection) {
                    ConfigurationSection cs = section.getConfigurationSection(key);
                    if (cs != null) {
                        double probability = cs.getDouble("probability", 0.0);
                        WeightedList sub = WeightedList.fromSection(cs.getConfigurationSection("subList"));
                        entries.add(new Entry(key, probability, sub));
                    }
                } else {
                    double probability = section.getDouble(key, 0.0);
                    entries.add(new Entry(key, probability, null));
                }
            }
            return new WeightedList(entries);
        }

        String pick(Random random) {
            if (entries.isEmpty() || totalWeight <= 0.0) {
                return "no_reward";
            }
            double target = random.nextDouble() * totalWeight;
            double cumulative = 0.0;
            for (Entry entry : entries) {
                cumulative += entry.weight;
                if (target <= cumulative) {
                    if (entry.subList != null) {
                        return entry.subList.pick(random);
                    }
                    return entry.name;
                }
            }
            Entry fallback = entries.get(entries.size() - 1);
            if (fallback.subList != null) {
                return fallback.subList.pick(random);
            }
            return fallback.name;
        }

        private static class Entry {
            final String name;
            final double weight;
            final WeightedList subList;

            Entry(String name, double weight, WeightedList subList) {
                this.name = name;
                this.weight = weight;
                this.subList = subList;
            }
        }
    }

    private static class TicketRule {
        private final String id;
        private final List<String> applicablePools;
        private final int deductCount;

        TicketRule(String id, List<String> applicablePools, int deductCount) {
            this.id = id;
            this.applicablePools = applicablePools;
            this.deductCount = deductCount;
        }

        static TicketRule fromMap(Map<?, ?> raw) {
            if (raw == null || raw.get("id") == null) {
                return null;
            }
            String id = raw.get("id").toString();
            Object poolsObj = raw.get("applicable_pools");
            List<String> pools = new ArrayList<String>();
            if (poolsObj instanceof List) {
                for (Object o : (List<?>) poolsObj) {
                    pools.add(o.toString());
                }
            }
            int count = 1;
            Object deduct = raw.get("deduct_count");
            if (deduct != null) {
                try {
                    count = Integer.parseInt(deduct.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            return new TicketRule(id, pools, count);
        }

        String getId() {
            return id;
        }

        List<String> getApplicablePools() {
            return applicablePools;
        }

        int getDeductCount() {
            return deductCount;
        }
    }

    private static class WishStatus {
        private final String pool;
        private final int count;
        private final int ticketCount;
        private final boolean validPool;

        WishStatus(String pool, int count, int ticketCount, boolean validPool) {
            this.pool = pool;
            this.count = count;
            this.ticketCount = ticketCount;
            this.validPool = validPool;
        }

        static WishStatus invalid(String pool) {
            return new WishStatus(pool, 0, 0, false);
        }

        boolean isValidPool() {
            return validPool;
        }

        String getPool() {
            return pool;
        }

        int getCount() {
            return count;
        }

        int getTicketCount() {
            return ticketCount;
        }
    }

    private static class WishException extends Exception {
        WishException(String message) {
            super(message);
        }
    }

    private static class TimeWindow {
        private final Instant start;
        private final Instant end;

        TimeWindow(Instant start, Instant end) {
            this.start = start;
            this.end = end;
        }

        boolean contains(Instant now) {
            if (start != null && now.isBefore(start)) {
                return false;
            }
            if (end != null && now.isAfter(end)) {
                return false;
            }
            return true;
        }

        static TimeWindow fromSection(ConfigurationSection section, java.util.logging.Logger logger) {
            if (section == null) {
                return null;
            }
            Instant start = parseInstant(section.getString("startDate"), logger);
            Instant end = parseInstant(section.getString("endDate"), logger);
            if (start == null && end == null) {
                return null;
            }
            return new TimeWindow(start, end);
        }

        private static Instant parseInstant(String raw, java.util.logging.Logger logger) {
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            try {
                return Instant.parse(raw);
            } catch (DateTimeParseException e) {
                logger.warning("時間格式無效: " + raw);
                return null;
            }
        }
    }

    // ---------------------- Event Module ----------------------

    private static class EventManager {
        private final JavaPlugin plugin;
        private final File storageDir;
        private final Map<String, EventDefinition> events = new HashMap<String, EventDefinition>();

        EventManager(JavaPlugin plugin, File configFile) {
            this.plugin = plugin;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String dataDir = config.getString("storage.data_dir", "userdata");
            storageDir = new File(plugin.getDataFolder(), dataDir);
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                plugin.getLogger().warning("無法創建數據目錄: " + storageDir.getAbsolutePath());
            }
            loadEvents(config.getConfigurationSection("events"));
        }

        private void loadEvents(ConfigurationSection section) {
            events.clear();
            if (section == null) {
                return;
            }
            for (String id : section.getKeys(false)) {
                ConfigurationSection cs = section.getConfigurationSection(id);
                if (cs == null) {
                    continue;
                }
                EventDefinition def = EventDefinition.fromSection(id, cs, plugin.getLogger());
                events.put(id, def);
            }
        }

        List<EventAvailability> listAvailableEvents(Player player) {
            List<EventAvailability> result = new ArrayList<EventAvailability>();
            long now = System.currentTimeMillis();
            YamlConfiguration data = loadUserData(player.getName());
            for (EventDefinition def : events.values()) {
                if (!def.isEnabled() || !def.isActive(now)) {
                    continue;
                }
                boolean can = canParticipate(def, data, now);
                result.add(new EventAvailability(def.getId(), def.getName(), can));
            }
            return result;
        }

        List<String> participate(Player player, String eventId) throws EventException {
            EventDefinition def = events.get(eventId);
            if (def == null) {
                throw new EventException("活動不存在");
            }
            long now = System.currentTimeMillis();
            if (!def.isEnabled() || !def.isActive(now)) {
                throw new EventException("活動未開啟");
            }
            YamlConfiguration data = loadUserData(player.getName());
            if (!canParticipate(def, data, now)) {
                throw new EventException("已達到參與限制");
            }
            markParticipation(def, data, now);
            saveUserData(player.getName(), data);

            List<String> rewardNames = new ArrayList<String>();
            for (EventReward reward : def.getRewards()) {
                reward.apply(player, plugin);
                rewardNames.add(reward.getDisplay());
            }
            return rewardNames;
        }

        private boolean canParticipate(EventDefinition def, YamlConfiguration data, long nowMillis) {
            EventLimit limit = def.getLimit();
            if (limit == null || limit.getCount() <= 0 || limit.getWindowMillis() <= 0) {
                return true;
            }
            String base = "event.limits." + def.getId();
            long windowStart = data.getLong(base + ".windowStart", 0L);
            int used = data.getInt(base + ".count", 0);
            if (nowMillis - windowStart >= limit.getWindowMillis()) {
                return true;
            }
            return used < limit.getCount();
        }

        private void markParticipation(EventDefinition def, YamlConfiguration data, long nowMillis) {
            EventLimit limit = def.getLimit();
            String base = "event.limits." + def.getId();
            if (limit == null || limit.getWindowMillis() <= 0) {
                data.set(base + ".windowStart", nowMillis);
                data.set(base + ".count", 1);
                return;
            }
            long windowStart = data.getLong(base + ".windowStart", 0L);
            int used = data.getInt(base + ".count", 0);
            if (nowMillis - windowStart >= limit.getWindowMillis()) {
                windowStart = nowMillis;
                used = 0;
            }
            used++;
            data.set(base + ".windowStart", windowStart);
            data.set(base + ".count", used);
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
    }

    private static class EventDefinition {
        private final String id;
        private final String name;
        private final boolean enabled;
        private final TimeWindow window;
        private final EventLimit limit;
        private final List<EventReward> rewards;

        EventDefinition(String id, String name, boolean enabled, TimeWindow window, EventLimit limit, List<EventReward> rewards) {
            this.id = id;
            this.name = name;
            this.enabled = enabled;
            this.window = window;
            this.limit = limit;
            this.rewards = rewards;
        }

        static EventDefinition fromSection(String id, ConfigurationSection section, java.util.logging.Logger logger) {
            String name = section.getString("name", id);
            boolean enabled = section.getBoolean("enabled", true);
            TimeWindow window = TimeWindow.fromSection(section.getConfigurationSection("duration"), logger);
            EventLimit limit = EventLimit.fromSection(section.getConfigurationSection("limit_modes"), logger);
            List<EventReward> rewards = EventReward.fromList(section.getMapList("rewards"));
            return new EventDefinition(id, name, enabled, window, limit, rewards);
        }

        String getId() {
            return id;
        }

        String getName() {
            return name;
        }

        boolean isEnabled() {
            return enabled;
        }

        boolean isActive(long nowMillis) {
            return window == null || window.contains(Instant.ofEpochMilli(nowMillis));
        }

        EventLimit getLimit() {
            return limit;
        }

        List<EventReward> getRewards() {
            return rewards;
        }
    }

    private static class EventLimit {
        private final int count;
        private final long windowMillis;

        EventLimit(int count, long windowMillis) {
            this.count = count;
            this.windowMillis = windowMillis;
        }

        static EventLimit fromSection(ConfigurationSection section, java.util.logging.Logger logger) {
            if (section == null) {
                return null;
            }
            int count = section.getInt("count", 0);
            long windowMillis = parseDurationMillis(section.getString("time"), logger);
            if (count <= 0 || windowMillis <= 0) {
                return null;
            }
            return new EventLimit(count, windowMillis);
        }

        int getCount() {
            return count;
        }

        long getWindowMillis() {
            return windowMillis;
        }

        private static long parseDurationMillis(String raw, java.util.logging.Logger logger) {
            if (raw == null || raw.length() < 2) {
                return 0L;
            }
            raw = raw.trim();
            char suffix = Character.toLowerCase(raw.charAt(raw.length() - 1));
            long value;
            try {
                value = Long.parseLong(raw.substring(0, raw.length() - 1));
            } catch (NumberFormatException e) {
                logger.warning("無法解析時間: " + raw);
                return 0L;
            }
            switch (suffix) {
                case 'h':
                    return Duration.ofHours(value).toMillis();
                case 'd':
                    return Duration.ofDays(value).toMillis();
                case 'w':
                    return Duration.ofDays(value * DAYS_PER_WEEK).toMillis();
                case 'm':
                    return Duration.ofDays(value * DAYS_PER_MONTH).toMillis();
                case 'y':
                    return Duration.ofDays(value * DAYS_PER_YEAR).toMillis();
                default:
                    logger.warning("未知時間單位: " + raw);
                    return 0L;
            }
        }
    }

    private static class EventReward {
        private final RewardType type;
        private final String value;
        private final int amount;

        EventReward(RewardType type, String value, int amount) {
            this.type = type;
            this.value = value;
            this.amount = amount;
        }

        static List<EventReward> fromList(List<Map<?, ?>> list) {
            if (list == null) {
                return Collections.emptyList();
            }
            List<EventReward> rewards = new ArrayList<EventReward>();
            for (Map<?, ?> raw : list) {
                String typeStr = raw.get("type") == null ? null : raw.get("type").toString();
                if (typeStr == null) {
                    continue;
                }
                RewardType type = RewardType.fromString(typeStr);
                String value = raw.get("value") == null ? "" : raw.get("value").toString();
                int amount = 1;
                Object amountObj = raw.get("amount");
                if (amountObj != null) {
                    try {
                        amount = Integer.parseInt(amountObj.toString());
                    } catch (NumberFormatException ignored) {
                    }
                }
                rewards.add(new EventReward(type, value, amount));
            }
            return rewards;
        }

        void apply(Player player, JavaPlugin plugin) {
            switch (type) {
                case BALANCE:
                    player.sendMessage(ChatColor.GREEN + "[NekoSuite] 金錢獎勵: " + value);
                    break;
                case EXP:
                    try {
                        int exp = Integer.parseInt(value);
                        player.giveExp(exp);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "[NekoSuite] 經驗值格式錯誤: " + value);
                    }
                    break;
                case ITEM:
                    Material material = Material.matchMaterial(value.replace("minecraft:", "").toUpperCase());
                    if (material != null) {
                        ItemStack itemStack = new ItemStack(material, amount <= 0 ? 1 : amount);
                        Map<Integer, ItemStack> overflow = player.getInventory().addItem(itemStack);
                        if (!overflow.isEmpty()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
                        }
                        player.sendMessage(ChatColor.GREEN + "[NekoSuite] 物品獎勵: " + material.name());
                    } else {
                        player.sendMessage(ChatColor.RED + "[NekoSuite] 物品無法識別: " + value);
                    }
                    break;
                case COMMAND:
                    String command = value.replace("$player", player.getName()).replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    player.sendMessage(ChatColor.GREEN + "[NekoSuite] 已觸發命令獎勵。");
                    break;
                default:
                    player.sendMessage(ChatColor.YELLOW + "[NekoSuite] 獎勵: " + value);
                    break;
            }
        }

        String getDisplay() {
            switch (type) {
                case EXP:
                    return "exp:" + value;
                case ITEM:
                    return "item:" + value + "x" + amount;
                case COMMAND:
                    return "command";
                case BALANCE:
                    return "balance:" + value;
                default:
                    return value;
            }
        }

        private enum RewardType {
            BALANCE, EXP, ITEM, COMMAND, UNKNOWN;

            static RewardType fromString(String raw) {
                String normalized = raw.toLowerCase();
                if (normalized.equals("balance")) {
                    return BALANCE;
                }
                if (normalized.equals("exp")) {
                    return EXP;
                }
                if (normalized.equals("item")) {
                    return ITEM;
                }
                if (normalized.equals("command")) {
                    return COMMAND;
                }
                return UNKNOWN;
            }
        }
    }

    private static class EventAvailability {
        private final String id;
        private final String displayName;
        private final boolean canParticipate;

        EventAvailability(String id, String displayName, boolean canParticipate) {
            this.id = id;
            this.displayName = displayName;
            this.canParticipate = canParticipate;
        }

        String getId() {
            return id;
        }

        String getDisplayName() {
            return displayName;
        }

        boolean isCanParticipate() {
            return canParticipate;
        }
    }

    private static class EventException extends Exception {
        EventException(String message) {
            super(message);
        }
    }
}
