package org.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class NekoSuitePlugin extends JavaPlugin implements CommandExecutor {

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
                RewardResult rewardResult;
                if (pool.shouldGuarantee(updatedCount)) {
                    rewardResult = pool.pickGuarantee(random);
                    updatedCount = 0;
                } else {
                    rewardResult = pool.pickReward(random);
                }
                if (rewardResult != null) {
                    dispatchReward(player, rewardResult, plugin);
                    rewards.add(rewardResult.getDisplay());
                }
            }
            data.set("wish.counts." + countsName, updatedCount);
            saveUserData(player.getName(), data);
            return rewards;
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

    private static void dispatchReward(Player player, RewardResult reward, JavaPlugin plugin) {
        if (player == null || reward == null) {
            return;
        }
        int amount = reward.getAmount();
        String rawItemName = reward.getName() == null ? "unknown_reward" : reward.getName();
        String itemName = sanitizeItemName(rawItemName);
        String command = reward.getCommand();
        if (command != null && !command.trim().isEmpty()) {
            String cmd = command
                    .replace("{player}", player.getName())
                    .replace("%player%", player.getName())
                    .replace("$player", player.getName())
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{item}", itemName);
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            return;
        }
        String giveCommand = "minecraft:give " + player.getName() + " " + itemName + " " + amount;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), giveCommand);
    }

    private static String sanitizeItemName(String raw) {
        if (raw == null) {
            return "unknown_reward";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9:_.-]", "");
        if (cleaned.isEmpty()) {
            return "unknown_reward";
        }
        return cleaned;
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

        RewardResult pickReward(Random random) {
            if (items == null) {
                return RewardResult.empty();
            }
            return items.pick(random);
        }

        RewardResult pickGuarantee(Random random) {
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
        private final List<RewardEntry> entries;
        private final double totalWeight;

        WeightedList(List<RewardEntry> entries) {
            this.entries = entries;
            double total = 0.0;
            for (RewardEntry entry : entries) {
                if (entry.getWeight() > 0.0) {
                    total += entry.getWeight();
                }
            }
            this.totalWeight = total;
        }

        static WeightedList fromSection(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            List<RewardEntry> entries = new ArrayList<RewardEntry>();
            for (String key : section.getKeys(false)) {
                RewardEntry entry = RewardEntry.fromConfig(key, section.get(key), section.getConfigurationSection(key));
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return new WeightedList(entries);
        }

        RewardResult pick(Random random) {
            if (entries.isEmpty() || totalWeight <= 0.0) {
                return RewardResult.empty();
            }
            double target = random.nextDouble() * totalWeight;
            double cumulative = 0.0;
            RewardEntry fallback = entries.get(entries.size() - 1);
            for (RewardEntry entry : entries) {
                if (entry.getWeight() <= 0.0) {
                    continue;
                }
                cumulative += entry.getWeight();
                if (target <= cumulative) {
                    return entry.resolve(random);
                }
            }
            return fallback.resolve(random);
        }

        List<RewardEntry> getEntries() {
            return entries;
        }
    }

    private static class RewardEntry {
        private final String name;
        private final double weight;
        private final WeightedList subList;
        private final String command;
        private final int minAmount;
        private final int maxAmount;

        RewardEntry(String name, double weight, WeightedList subList, String command, int minAmount, int maxAmount) {
            this.name = name;
            this.weight = weight;
            this.subList = subList;
            this.command = command;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }

        static RewardEntry fromConfig(String key, Object rawValue, ConfigurationSection sectionValue) {
            double probability = 0.0;
            WeightedList sub = null;
            String command = null;
            int minAmount = 1;
            int maxAmount = 1;

            if (sectionValue != null) {
                probability = sectionValue.getDouble("probability", 0.0);
                command = sectionValue.getString("command");
                int[] range = parseAmount(sectionValue.get("amount"));
                minAmount = range[0];
                maxAmount = range[1];
                sub = WeightedList.fromSection(sectionValue.getConfigurationSection("subList"));
            } else if (rawValue instanceof Number) {
                probability = ((Number) rawValue).doubleValue();
            } else if (rawValue instanceof String) {
                try {
                    probability = Double.parseDouble(rawValue.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return new RewardEntry(key, probability, sub, command, minAmount, maxAmount);
        }

        private static int[] parseAmount(Object amountObj) {
            int min = 1;
            int max = 1;
            if (amountObj instanceof Number) {
                int value = ((Number) amountObj).intValue();
                if (value > 0) {
                    min = value;
                    max = value;
                }
            } else if (amountObj instanceof String) {
                String raw = ((String) amountObj).trim();
                if (raw.contains("-")) {
                    String[] parts = raw.split("-");
                    if (parts.length >= 2) {
                        try {
                            min = Integer.parseInt(parts[0].trim());
                            max = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else {
                    try {
                        int parsed = Integer.parseInt(raw);
                        if (parsed > 0) {
                            min = parsed;
                            max = parsed;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (min <= 0) {
                min = 1;
            }
            if (max < min) {
                max = min;
            }
            return new int[]{min, max};
        }

        RewardResult resolve(Random random) {
            if (subList != null) {
                return subList.pick(random);
            }
            int amount = minAmount;
            if (maxAmount > minAmount) {
                amount = minAmount + random.nextInt(maxAmount - minAmount + 1);
            }
            if (amount <= 0) {
                amount = 1;
            }
            return new RewardResult(name, amount, command);
        }

        boolean shouldGrant(Random random) {
            if (weight <= 0.0) {
                return false;
            }
            double chance = weight;
            if (chance > 1.0) {
                // Values greater than 1 are treated as percentage inputs (e.g., 50 = 50%).
                chance = chance / 100.0;
            }
            chance = Math.min(chance, 1.0);
            return random.nextDouble() < chance;
        }

        double getWeight() {
            return weight;
        }
    }

    private static class RewardResult {
        private final String name;
        private final int amount;
        private final String command;

        RewardResult(String name, int amount, String command) {
            this.name = name;
            this.amount = amount <= 0 ? 1 : amount;
            this.command = command;
        }

        static RewardResult empty() {
            return new RewardResult("no_reward", 1, null);
        }

        String getName() {
            return name;
        }

        int getAmount() {
            return amount;
        }

        String getCommand() {
            return command;
        }

        String getDisplay() {
            if (command != null && !command.trim().isEmpty()) {
                return name + " x" + amount + " (command)";
            }
            return name + " x" + amount;
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
        private final Random random = new Random();

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
            WeightedList rewardList = def.getRewards();
            if (rewardList != null) {
                if (def.isGrantAll()) {
                    for (RewardEntry entry : rewardList.getEntries()) {
                        if (!entry.shouldGrant(random)) {
                            continue;
                        }
                        RewardResult result = entry.resolve(random);
                        dispatchReward(player, result, plugin);
                        rewardNames.add(result.getDisplay());
                    }
                } else {
                    int rolls = Math.max(1, def.getRewardRolls());
                    for (int i = 0; i < rolls; i++) {
                        RewardResult result = rewardList.pick(random);
                        if (result == null) {
                            continue;
                        }
                        dispatchReward(player, result, plugin);
                        rewardNames.add(result.getDisplay());
                    }
                }
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
        private final WeightedList rewards;
        private final boolean grantAll;
        private final int rewardRolls;

        EventDefinition(String id, String name, boolean enabled, TimeWindow window, EventLimit limit, WeightedList rewards, boolean grantAll, int rewardRolls) {
            this.id = id;
            this.name = name;
            this.enabled = enabled;
            this.window = window;
            this.limit = limit;
            this.rewards = rewards;
            this.grantAll = grantAll;
            this.rewardRolls = rewardRolls;
        }

        static EventDefinition fromSection(String id, ConfigurationSection section, java.util.logging.Logger logger) {
            String name = section.getString("name", id);
            boolean enabled = section.getBoolean("enabled", true);
            TimeWindow window = TimeWindow.fromSection(section.getConfigurationSection("duration"), logger);
            EventLimit limit = EventLimit.fromSection(section.getConfigurationSection("limit_modes"), logger);
            WeightedList rewards = WeightedList.fromSection(section.getConfigurationSection("rewards"));
            boolean grantAll = section.getBoolean("grant_all", true);
            int rewardRolls = section.getInt("reward_rolls", 1);
            if (rewardRolls <= 0) {
                rewardRolls = 1;
            }
            return new EventDefinition(id, name, enabled, window, limit, rewards, grantAll, rewardRolls);
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

        WeightedList getRewards() {
            return rewards;
        }

        boolean isGrantAll() {
            return grantAll;
        }

        int getRewardRolls() {
            return rewardRolls;
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
