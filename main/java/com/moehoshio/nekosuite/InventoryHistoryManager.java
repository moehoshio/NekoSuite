package com.moehoshio.nekosuite;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
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

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Two-layer inventory history: an append-only change log plus periodic full
 * keyframes. Supports rewind-by-count or rewind-by-time, a visual preview
 * chest GUI, and a reconciliation pass that detects and corrects drift caused
 * by missed inventory events.
 *
 * <p>This manager is intentionally separate from {@link InventoryBackupManager}
 * so existing snapshot-based backup behavior is unaffected. Both managers
 * share the same {@code storage.data_dir} and persist into the same per-player
 * YAML file under sibling top-level sections:</p>
 * <ul>
 *   <li>{@code inventory_backups.*} - legacy / verified-loss keyframes (owned by InventoryBackupManager)</li>
 *   <li>{@code inventory_history.changes.*} - change records</li>
 *   <li>{@code inventory_history.keyframes.*} - history keyframes</li>
 *   <li>{@code inventory_history.next_seq} - monotonic counter</li>
 * </ul>
 */
public class InventoryHistoryManager {

    // Slot type identifiers used in ChangeEntry slot diffs.
    public static final String SLOT_MAIN = "MAIN";       // player inventory contents 0..35
    public static final String SLOT_ARMOR = "ARMOR";     // 0..3 boots/legs/chest/helmet
    public static final String SLOT_OFFHAND = "OFFHAND"; // single slot

    // Trigger names recorded on change entries.
    public static final String TRIGGER_INVENTORY = "INVENTORY";
    public static final String TRIGGER_PICKUP = "PICKUP";
    public static final String TRIGGER_DROP = "DROP";
    public static final String TRIGGER_CONSUME = "CONSUME";
    public static final String TRIGGER_BREAK = "BREAK";
    public static final String TRIGGER_BLOCK_PLACE = "BLOCK_PLACE";
    public static final String TRIGGER_BLOCK_BREAK = "BLOCK_BREAK";
    public static final String TRIGGER_CRAFT = "CRAFT";
    public static final String TRIGGER_ENCHANT = "ENCHANT";
    public static final String TRIGGER_SWAP_HAND = "SWAP_HAND";
    public static final String TRIGGER_RESPAWN = "RESPAWN";
    public static final String TRIGGER_JOIN = "JOIN";
    public static final String TRIGGER_QUIT = "QUIT";
    public static final String TRIGGER_RECONCILE = "RECONCILE";
    public static final String TRIGGER_REWIND_COMMIT = "REWIND_COMMIT";

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Economy economy;
    private final InventoryBackupManager backupManager;
    private final File storageDir;

    // Configuration
    private boolean enabled;
    private int maxChanges;
    private long expiryMinutes;
    private int keyframeEveryNChanges;
    private long keyframeIntervalMs;
    private long reconcileIntervalMs;
    private int reconcileDriftAdminThreshold;
    private boolean nextTickDiffEnabled;
    private boolean allowScrubbing;
    private double restoreCost;
    private double verifiedLossCost;
    private long restoreCooldown;
    private long restoreCooldownPerBackup;
    private boolean clearBeforeRestore;
    private boolean requireConfirmation;
    private boolean trackRestored;
    private boolean notifyAdmins;
    private boolean requireVerifiedLoss;
    private List<String> blockedItems;

    // Runtime state - all main-thread only.
    /** UUIDs of players for whom a next-tick diff has already been scheduled this tick. */
    private final Set<UUID> pendingDiffPlayers = new HashSet<UUID>();
    /** Pre-event snapshots indexed by player UUID, captured just before scheduling diff. */
    private final Map<UUID, FullSnapshot> pendingPreSnapshots = new HashMap<UUID, FullSnapshot>();
    /** Per-player pending preview state (target seq + holder). */
    private final Map<UUID, PreviewState> previewStates = new HashMap<UUID, PreviewState>();
    /** Per-player pending rewind confirmations (target seq). */
    private final Map<UUID, Long> pendingRewindConfirmations = new HashMap<UUID, Long>();

    public InventoryHistoryManager(JavaPlugin plugin, Messages messages, File configFile, Economy economy,
                                   InventoryBackupManager backupManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.economy = economy;
        this.backupManager = backupManager;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String dataDir = config.getString("storage.data_dir", "userdata");
        this.storageDir = new File(plugin.getDataFolder(), dataDir);
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create history storage directory: " + storageDir.getAbsolutePath());
        }

        loadConfig(config);
    }

    private void loadConfig(YamlConfiguration config) {
        enabled = config.getBoolean("history.enabled", true);
        maxChanges = config.getInt("history.max_changes_per_player", 500);
        expiryMinutes = config.getLong("history.expiry_minutes", 1440);
        keyframeEveryNChanges = config.getInt("history.keyframe_every_n_changes", 25);
        keyframeIntervalMs = config.getLong("history.keyframe_interval_seconds", 300) * 1000L;
        reconcileIntervalMs = config.getLong("history.reconcile_interval_seconds", 300) * 1000L;
        reconcileDriftAdminThreshold = config.getInt("history.reconcile_drift_admin_threshold", 5);
        nextTickDiffEnabled = config.getBoolean("history.next_tick_diff_enabled", true);
        allowScrubbing = config.getBoolean("history.preview.allow_scrubbing", true);

        restoreCost = config.getDouble("restore.cost", 500.0);
        verifiedLossCost = config.getDouble("restore.verified_loss_cost", 100.0);
        restoreCooldown = config.getLong("restore.cooldown", 3600) * 1000L;
        restoreCooldownPerBackup = config.getLong("anti_dupe.restore_cooldown_per_backup", 86400) * 1000L;
        clearBeforeRestore = config.getBoolean("restore.clear_before_restore", true);
        requireConfirmation = config.getBoolean("restore.require_confirmation", true);
        trackRestored = config.getBoolean("anti_dupe.track_restored", true);
        notifyAdmins = config.getBoolean("anti_dupe.notify_admins", true);
        requireVerifiedLoss = config.getBoolean("anti_dupe.require_verified_loss", false);
        blockedItems = config.getStringList("restore.blocked_items");
        if (blockedItems == null) {
            blockedItems = new ArrayList<String>();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // =====================================================
    // Event capture: schedules a next-tick diff for the player.
    // =====================================================

    /**
     * Schedule a next-tick diff for the given player. If a diff is already
     * scheduled for this tick, this call is a no-op (debounced).
     *
     * @param player  the affected player
     * @param trigger the originating event identifier (PICKUP/DROP/...)
     */
    public void scheduleDiff(final Player player, final String trigger) {
        if (!enabled || player == null) {
            return;
        }
        // Skip creative/spectator to avoid recording duplication-trivial inventories.
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        if (pendingDiffPlayers.contains(uuid)) {
            return;
        }
        // Capture the pre-event snapshot ONCE per tick for this player.
        pendingPreSnapshots.put(uuid, FullSnapshot.capture(player));
        pendingDiffPlayers.add(uuid);

        if (!nextTickDiffEnabled) {
            // Run inline against the current state - the "post" snapshot is
            // the same as pre, so this is effectively a reconcile hint.
            runDiff(player, trigger);
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                runDiff(player, trigger);
            }
        }.runTask(plugin);
    }

    private void runDiff(Player player, String trigger) {
        UUID uuid = player.getUniqueId();
        pendingDiffPlayers.remove(uuid);
        FullSnapshot pre = pendingPreSnapshots.remove(uuid);
        if (pre == null || !player.isOnline()) {
            return;
        }
        FullSnapshot post = FullSnapshot.capture(player);
        List<SlotDiff> diffs = pre.diffTo(post);
        if (diffs.isEmpty()) {
            return;
        }
        appendChange(player, trigger, diffs);
    }

    /**
     * Force a synchronous keyframe for this player (no diff). Used on join,
     * quit, respawn, and reconcile.
     */
    public void forceKeyframe(Player player, String trigger) {
        if (!enabled || player == null) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        YamlConfiguration data = loadUserData(player.getName());
        long seq = nextSeq(data);
        FullSnapshot snap = FullSnapshot.capture(player);
        writeKeyframe(data, seq, System.currentTimeMillis(), trigger, snap);
        saveUserData(player.getName(), data);
    }

    /**
     * Periodic reconcile: compute the expected state by replaying the change
     * log forward from the most recent keyframe, diff against the live
     * inventory, and if drift is detected, append a RECONCILE change and a
     * fresh keyframe. Notifies admins if drift is large.
     */
    public void reconcile(Player player) {
        if (!enabled || player == null || !player.isOnline()) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        YamlConfiguration data = loadUserData(player.getName());
        Keyframe latest = readLatestKeyframe(data);
        if (latest == null) {
            // No keyframe yet - just install one.
            long seq = nextSeq(data);
            writeKeyframe(data, seq, System.currentTimeMillis(), TRIGGER_RECONCILE, FullSnapshot.capture(player));
            saveUserData(player.getName(), data);
            return;
        }
        // Build expected state by replaying changes after latest.seq.
        FullSnapshot expected = replay(data, latest, Long.MAX_VALUE);
        FullSnapshot live = FullSnapshot.capture(player);
        List<SlotDiff> drift = expected.diffTo(live);
        if (drift.isEmpty()) {
            // No drift - still insert a periodic keyframe if interval elapsed.
            if (System.currentTimeMillis() - latest.timestamp >= keyframeIntervalMs) {
                long seq = nextSeq(data);
                writeKeyframe(data, seq, System.currentTimeMillis(), TRIGGER_RECONCILE, live);
                saveUserData(player.getName(), data);
            }
            return;
        }
        // Drift detected: append RECONCILE change carrying corrective diffs,
        // then install a fresh keyframe so future replays start clean.
        long now = System.currentTimeMillis();
        long changeSeq = nextSeq(data);
        writeChange(data, changeSeq, now, TRIGGER_RECONCILE, drift, latest.seq);
        long keySeq = nextSeq(data);
        writeKeyframe(data, keySeq, now, TRIGGER_RECONCILE, live);
        trim(data);
        saveUserData(player.getName(), data);

        if (notifyAdmins && drift.size() >= reconcileDriftAdminThreshold) {
            String adminMsg = ChatColor.YELLOW + "[NekoSuite] Drift detected for " + player.getName()
                + " (" + drift.size() + " slots). RECONCILE seq=" + changeSeq;
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("nekosuite.invbackup.admin")) {
                    admin.sendMessage(adminMsg);
                }
            }
        }
    }

    // =====================================================
    // Append a change record. Inserts a periodic keyframe if needed.
    // =====================================================

    private void appendChange(Player player, String trigger, List<SlotDiff> diffs) {
        YamlConfiguration data = loadUserData(player.getName());
        long now = System.currentTimeMillis();
        Keyframe latest = readLatestKeyframe(data);
        long keyframeSeq = latest == null ? -1L : latest.seq;

        // Insert a keyframe if we have none yet or the interval/N elapsed.
        if (latest == null) {
            long seq = nextSeq(data);
            writeKeyframe(data, seq, now, TRIGGER_JOIN, FullSnapshot.capture(player));
            keyframeSeq = seq;
        } else {
            int changesSinceKeyframe = countChangesSince(data, latest.seq);
            boolean dueByCount = keyframeEveryNChanges > 0 && changesSinceKeyframe >= keyframeEveryNChanges;
            boolean dueByTime = keyframeIntervalMs > 0 && now - latest.timestamp >= keyframeIntervalMs;
            if (dueByCount || dueByTime) {
                long seq = nextSeq(data);
                // Take a keyframe of the inventory BEFORE applying this change so
                // replay forward from the new keyframe stays consistent.
                writeKeyframe(data, seq, now, TRIGGER_RECONCILE, FullSnapshot.capture(player));
                keyframeSeq = seq;
            }
        }

        long seq = nextSeq(data);
        writeChange(data, seq, now, trigger, diffs, keyframeSeq);
        trim(data);
        saveUserData(player.getName(), data);
    }

    // =====================================================
    // YAML I/O
    // =====================================================

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

    private long nextSeq(YamlConfiguration data) {
        long seq = data.getLong("inventory_history.next_seq", 0L);
        data.set("inventory_history.next_seq", seq + 1);
        return seq;
    }

    private void writeChange(YamlConfiguration data, long seq, long timestamp,
                             String trigger, List<SlotDiff> diffs, long keyframeSeq) {
        String path = "inventory_history.changes." + seq;
        data.set(path + ".timestamp", timestamp);
        data.set(path + ".trigger", trigger);
        data.set(path + ".keyframe_seq", keyframeSeq);
        for (int i = 0; i < diffs.size(); i++) {
            SlotDiff d = diffs.get(i);
            String dp = path + ".diffs." + i;
            data.set(dp + ".slot_type", d.slotType);
            data.set(dp + ".slot_index", d.slotIndex);
            if (d.before != null) {
                data.set(dp + ".before", d.before);
            }
            if (d.after != null) {
                data.set(dp + ".after", d.after);
            }
        }
    }

    private void writeKeyframe(YamlConfiguration data, long seq, long timestamp,
                               String trigger, FullSnapshot snap) {
        String path = "inventory_history.keyframes." + seq;
        data.set(path + ".timestamp", timestamp);
        data.set(path + ".trigger", trigger);
        for (int i = 0; i < snap.main.length; i++) {
            ItemStack it = snap.main[i];
            if (it != null && it.getType() != Material.AIR) {
                data.set(path + ".main." + i, it);
            }
        }
        for (int i = 0; i < snap.armor.length; i++) {
            ItemStack it = snap.armor[i];
            if (it != null && it.getType() != Material.AIR) {
                data.set(path + ".armor." + i, it);
            }
        }
        if (snap.offhand != null && snap.offhand.getType() != Material.AIR) {
            data.set(path + ".offhand", snap.offhand);
        }
    }

    private Keyframe readKeyframe(YamlConfiguration data, long seq) {
        ConfigurationSection sec = data.getConfigurationSection("inventory_history.keyframes." + seq);
        if (sec == null) {
            return null;
        }
        return Keyframe.fromSection(seq, sec);
    }

    private Keyframe readLatestKeyframe(YamlConfiguration data) {
        ConfigurationSection sec = data.getConfigurationSection("inventory_history.keyframes");
        if (sec == null) {
            return null;
        }
        long best = -1L;
        for (String key : sec.getKeys(false)) {
            try {
                long s = Long.parseLong(key);
                if (s > best) {
                    best = s;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (best < 0) {
            return null;
        }
        return readKeyframe(data, best);
    }

    /** Returns the keyframe with the largest seq &lt;= targetSeq, or null. */
    private Keyframe readKeyframeAtOrBefore(YamlConfiguration data, long targetSeq) {
        ConfigurationSection sec = data.getConfigurationSection("inventory_history.keyframes");
        if (sec == null) {
            return null;
        }
        long best = -1L;
        for (String key : sec.getKeys(false)) {
            try {
                long s = Long.parseLong(key);
                if (s <= targetSeq && s > best) {
                    best = s;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (best < 0) {
            return null;
        }
        return readKeyframe(data, best);
    }

    private int countChangesSince(YamlConfiguration data, long sinceSeq) {
        ConfigurationSection sec = data.getConfigurationSection("inventory_history.changes");
        if (sec == null) {
            return 0;
        }
        int count = 0;
        for (String key : sec.getKeys(false)) {
            try {
                long s = Long.parseLong(key);
                if (s > sinceSeq) {
                    count++;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return count;
    }

    /**
     * Replay forward from {@code from} onto the keyframe state, stopping after
     * applying the largest change whose {@code seq <= targetSeq}.
     */
    private FullSnapshot replay(YamlConfiguration data, Keyframe from, long targetSeq) {
        FullSnapshot state = from.snapshot.copy();
        ConfigurationSection sec = data.getConfigurationSection("inventory_history.changes");
        if (sec == null) {
            return state;
        }
        // Collect and sort seqs.
        List<Long> seqs = new ArrayList<Long>();
        for (String key : sec.getKeys(false)) {
            try {
                long s = Long.parseLong(key);
                if (s > from.seq && s <= targetSeq) {
                    seqs.add(s);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        Collections.sort(seqs);
        for (Long s : seqs) {
            ConfigurationSection ch = sec.getConfigurationSection(String.valueOf(s));
            if (ch == null) {
                continue;
            }
            applyChangeForward(state, ch);
        }
        return state;
    }

    private void applyChangeForward(FullSnapshot state, ConfigurationSection ch) {
        ConfigurationSection diffs = ch.getConfigurationSection("diffs");
        if (diffs == null) {
            return;
        }
        for (String idx : diffs.getKeys(false)) {
            ConfigurationSection d = diffs.getConfigurationSection(idx);
            if (d == null) {
                continue;
            }
            String slotType = d.getString("slot_type", SLOT_MAIN);
            int slotIndex = d.getInt("slot_index", 0);
            ItemStack after = d.contains("after") ? d.getItemStack("after") : null;
            state.set(slotType, slotIndex, after);
        }
    }

    // =====================================================
    // Trim: enforce capacity and expiry on the change log.
    // =====================================================

    private void trim(YamlConfiguration data) {
        ConfigurationSection sec = data.getConfigurationSection("inventory_history.changes");
        if (sec == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long expiryMs = expiryMinutes > 0 ? expiryMinutes * 60L * 1000L : 0L;

        // Collect seqs in ascending order.
        List<Long> seqs = new ArrayList<Long>();
        for (String key : sec.getKeys(false)) {
            try {
                seqs.add(Long.parseLong(key));
            } catch (NumberFormatException ignored) {
            }
        }
        Collections.sort(seqs);

        // Determine the smallest keyframe seq that remains valid; we never drop
        // a change whose enclosing keyframe is no longer present, because the
        // record would become un-replayable.
        Set<Long> keyframeSeqs = new HashSet<Long>();
        ConfigurationSection kfSec = data.getConfigurationSection("inventory_history.keyframes");
        if (kfSec != null) {
            for (String key : kfSec.getKeys(false)) {
                try {
                    keyframeSeqs.add(Long.parseLong(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // 1. Age trim.
        if (expiryMs > 0) {
            for (Long s : new ArrayList<Long>(seqs)) {
                ConfigurationSection ch = sec.getConfigurationSection(String.valueOf(s));
                if (ch == null) {
                    continue;
                }
                long ts = ch.getLong("timestamp", 0L);
                long kfSeq = ch.getLong("keyframe_seq", -1L);
                // Never drop a record unless a still-existing keyframe at >= its keyframe_seq exists.
                if (now - ts > expiryMs && hasKeyframeAtOrAfter(keyframeSeqs, kfSeq)) {
                    sec.set(String.valueOf(s), null);
                    seqs.remove(s);
                }
            }
        }

        // 2. Capacity trim (oldest first).
        if (maxChanges > 0) {
            while (seqs.size() > maxChanges) {
                Long oldest = seqs.get(0);
                ConfigurationSection ch = sec.getConfigurationSection(String.valueOf(oldest));
                long kfSeq = ch == null ? -1L : ch.getLong("keyframe_seq", -1L);
                if (!hasKeyframeAtOrAfter(keyframeSeqs, kfSeq)) {
                    break; // Cannot safely drop further; abort trim.
                }
                sec.set(String.valueOf(oldest), null);
                seqs.remove(0);
            }
        }

        // 3. Keyframe trim: drop keyframes older than the oldest remaining change's keyframe_seq.
        long minKeyframeSeqStillNeeded = Long.MAX_VALUE;
        for (Long s : seqs) {
            ConfigurationSection ch = sec.getConfigurationSection(String.valueOf(s));
            if (ch == null) {
                continue;
            }
            long kfSeq = ch.getLong("keyframe_seq", -1L);
            if (kfSeq >= 0 && kfSeq < minKeyframeSeqStillNeeded) {
                minKeyframeSeqStillNeeded = kfSeq;
            }
        }
        if (kfSec != null && minKeyframeSeqStillNeeded != Long.MAX_VALUE) {
            // Keep the newest keyframe at all times to anchor future appends.
            long newest = -1L;
            for (Long k : keyframeSeqs) {
                if (k > newest) {
                    newest = k;
                }
            }
            for (Long k : new ArrayList<Long>(keyframeSeqs)) {
                if (k < minKeyframeSeqStillNeeded && k != newest) {
                    kfSec.set(String.valueOf(k), null);
                }
            }
        }
    }

    private boolean hasKeyframeAtOrAfter(Set<Long> keyframeSeqs, long target) {
        for (Long k : keyframeSeqs) {
            if (k >= target) {
                return true;
            }
        }
        return false;
    }

    // =====================================================
    // Rewind target resolution
    // =====================================================

    /**
     * Resolve a rewind target by "undo last N changes" semantics.
     * Returns the seq of the most recent change that should still be applied
     * (i.e. the state AFTER seq is what the player wants to see), or -1 if
     * impossible (no history).
     */
    public RewindTarget resolveByCount(String playerName, int n) {
        if (n <= 0) {
            return null;
        }
        YamlConfiguration data = loadUserData(playerName);
        ConfigurationSection sec = data.getConfigurationSection("inventory_history.changes");
        if (sec == null) {
            return null;
        }
        List<Long> seqs = new ArrayList<Long>();
        for (String key : sec.getKeys(false)) {
            try {
                seqs.add(Long.parseLong(key));
            } catch (NumberFormatException ignored) {
            }
        }
        if (seqs.isEmpty()) {
            return null;
        }
        Collections.sort(seqs);
        // Drop the last n changes - target is the state after the change at index (size - n - 1).
        int idx = seqs.size() - n - 1;
        if (idx < 0) {
            // Target precedes all known changes - rewind to the oldest keyframe state.
            Keyframe kf = readKeyframeAtOrBefore(data, seqs.get(0) - 1);
            if (kf == null) {
                return null;
            }
            return new RewindTarget(kf.seq, kf.timestamp, n);
        }
        long targetSeq = seqs.get(idx);
        ConfigurationSection ch = sec.getConfigurationSection(String.valueOf(targetSeq));
        long ts = ch == null ? 0L : ch.getLong("timestamp", 0L);
        return new RewindTarget(targetSeq, ts, n);
    }

    /**
     * Resolve by absolute timestamp (ms). Selects the largest seq whose
     * timestamp &lt;= targetTime.
     */
    public RewindTarget resolveByTime(String playerName, long targetTime) {
        YamlConfiguration data = loadUserData(playerName);
        ConfigurationSection sec = data.getConfigurationSection("inventory_history.changes");
        long bestSeq = -1L;
        long bestTs = 0L;
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection ch = sec.getConfigurationSection(key);
                if (ch == null) {
                    continue;
                }
                long ts = ch.getLong("timestamp", 0L);
                try {
                    long s = Long.parseLong(key);
                    if (ts <= targetTime && s > bestSeq) {
                        bestSeq = s;
                        bestTs = ts;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (bestSeq < 0) {
            // Use the oldest keyframe <= targetTime.
            ConfigurationSection kfSec = data.getConfigurationSection("inventory_history.keyframes");
            if (kfSec == null) {
                return null;
            }
            for (String key : kfSec.getKeys(false)) {
                ConfigurationSection k = kfSec.getConfigurationSection(key);
                if (k == null) {
                    continue;
                }
                long ts = k.getLong("timestamp", 0L);
                try {
                    long s = Long.parseLong(key);
                    if (ts <= targetTime && s > bestSeq) {
                        bestSeq = s;
                        bestTs = ts;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (bestSeq < 0) {
            return null;
        }
        return new RewindTarget(bestSeq, bestTs, -1);
    }

    /**
     * Compute the previewed inventory state at the given target.
     */
    public FullSnapshot computeState(String playerName, RewindTarget target) {
        if (target == null) {
            return null;
        }
        YamlConfiguration data = loadUserData(playerName);
        Keyframe base = readKeyframeAtOrBefore(data, target.seq);
        if (base == null) {
            return null;
        }
        return replay(data, base, target.seq);
    }

    /**
     * Advance the target by {@code direction} (+1 = newer, -1 = older).
     * Returns null when no more entries exist in that direction.
     */
    public RewindTarget scrub(String playerName, RewindTarget current, int direction) {
        if (current == null || !allowScrubbing) {
            return current;
        }
        YamlConfiguration data = loadUserData(playerName);
        ConfigurationSection sec = data.getConfigurationSection("inventory_history.changes");
        if (sec == null) {
            return current;
        }
        List<Long> seqs = new ArrayList<Long>();
        for (String key : sec.getKeys(false)) {
            try {
                seqs.add(Long.parseLong(key));
            } catch (NumberFormatException ignored) {
            }
        }
        Collections.sort(seqs);
        if (seqs.isEmpty()) {
            return current;
        }
        int idx = Collections.binarySearch(seqs, current.seq);
        if (idx < 0) {
            // Not exact match (e.g. current seq is a keyframe). Use insertion point.
            idx = -idx - 1;
        }
        idx += direction;
        if (idx < 0 || idx >= seqs.size()) {
            return null;
        }
        long newSeq = seqs.get(idx);
        ConfigurationSection ch = sec.getConfigurationSection(String.valueOf(newSeq));
        long ts = ch == null ? 0L : ch.getLong("timestamp", 0L);
        return new RewindTarget(newSeq, ts, -1);
    }

    // =====================================================
    // Restore (rewind) execution
    // =====================================================

    /**
     * Apply a rewind to the player's inventory after enforcing economy,
     * cooldowns, blocked items, and (optionally) confirmation. On success a
     * {@link #TRIGGER_REWIND_COMMIT} change is appended so the rewind itself
     * is reversible.
     *
     * @throws InventoryBackupManager.BackupException with a user-facing message on failure
     */
    public void applyRewind(Player player, RewindTarget target) throws InventoryBackupManager.BackupException {
        if (target == null) {
            throw new InventoryBackupManager.BackupException(messages.format(player, "invbackup.not_found"));
        }
        YamlConfiguration data = loadUserData(player.getName());
        long now = System.currentTimeMillis();

        // Global restore cooldown (shared with InventoryBackupManager).
        long lastRestore = data.getLong("inventory_backup_last_restore", 0L);
        if (now - lastRestore < restoreCooldown) {
            long remaining = (restoreCooldown - (now - lastRestore)) / 1000;
            Map<String, String> map = new HashMap<String, String>();
            map.put("time", formatDuration(remaining));
            throw new InventoryBackupManager.BackupException(messages.format(player, "invbackup.cooldown", map));
        }

        // Per-target cooldown.
        if (trackRestored) {
            long last = data.getLong("inventory_history.last_rewind_at." + target.seq, 0L);
            if (now - last < restoreCooldownPerBackup) {
                long remaining = (restoreCooldownPerBackup - (now - last)) / 1000;
                Map<String, String> map = new HashMap<String, String>();
                map.put("time", formatDuration(remaining));
                throw new InventoryBackupManager.BackupException(messages.format(player, "invbackup.backup_cooldown", map));
            }
        }

        // Detect whether any verified-loss reason sits inside the rewound window.
        boolean verifiedLossInWindow = false;
        ConfigurationSection chSec = data.getConfigurationSection("inventory_history.changes");
        if (chSec != null) {
            for (String key : chSec.getKeys(false)) {
                ConfigurationSection ch = chSec.getConfigurationSection(key);
                if (ch == null) {
                    continue;
                }
                try {
                    long s = Long.parseLong(key);
                    if (s > target.seq) {
                        String tr = ch.getString("trigger", "");
                        if (InventoryBackupManager.LOSS_LAVA.equals(tr)
                            || InventoryBackupManager.LOSS_VOID.equals(tr)
                            || InventoryBackupManager.LOSS_FIRE.equals(tr)
                            || InventoryBackupManager.LOSS_DESPAWN.equals(tr)
                            || InventoryBackupManager.LOSS_CACTUS.equals(tr)
                            || InventoryBackupManager.LOSS_EXPLOSION.equals(tr)
                            || InventoryBackupManager.LOSS_DEATH.equals(tr)) {
                            verifiedLossInWindow = true;
                            break;
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (requireVerifiedLoss && !verifiedLossInWindow && !player.hasPermission("nekosuite.invbackup.bypass")) {
            throw new InventoryBackupManager.BackupException(messages.format(player, "invbackup.not_verified_loss"));
        }

        double cost = verifiedLossInWindow ? verifiedLossCost : restoreCost;

        // Confirmation.
        if (requireConfirmation) {
            Long pending = pendingRewindConfirmations.get(player.getUniqueId());
            if (pending == null || pending.longValue() != target.seq) {
                pendingRewindConfirmations.put(player.getUniqueId(), target.seq);
                Map<String, String> map = new HashMap<String, String>();
                map.put("cost", String.format("%.2f", cost));
                player.sendMessage(messages.format(player, "invbackup.confirm_restore", map));
                return;
            }
            pendingRewindConfirmations.remove(player.getUniqueId());
        }

        // Economy.
        if (economy != null && cost > 0) {
            double balance = economy.getBalance(player);
            if (balance < cost) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("cost", String.format("%.2f", cost));
                map.put("balance", String.format("%.2f", balance));
                throw new InventoryBackupManager.BackupException(messages.format(player, "invbackup.insufficient_balance", map));
            }
            EconomyResponse response = economy.withdrawPlayer(player, cost);
            if (!response.transactionSuccess()) {
                throw new InventoryBackupManager.BackupException(messages.format(player, "invbackup.cost_failure"));
            }
        }

        // Compute target state and apply.
        FullSnapshot targetState = computeState(player.getName(), target);
        if (targetState == null) {
            throw new InventoryBackupManager.BackupException(messages.format(player, "invbackup.not_found"));
        }

        // Suppress next-tick diff caused by our own inventory mutations.
        UUID uuid = player.getUniqueId();
        pendingDiffPlayers.add(uuid);
        try {
            if (clearBeforeRestore) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(new ItemStack[4]);
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            }
            // Main inventory.
            for (int i = 0; i < targetState.main.length; i++) {
                ItemStack it = targetState.main[i];
                if (it != null && !isBlockedItem(it)) {
                    player.getInventory().setItem(i, it);
                }
            }
            // Armor.
            ItemStack[] armor = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                ItemStack it = targetState.armor[i];
                if (it != null && !isBlockedItem(it)) {
                    armor[i] = it;
                }
            }
            player.getInventory().setArmorContents(armor);
            // Offhand.
            if (targetState.offhand != null && !isBlockedItem(targetState.offhand)) {
                player.getInventory().setItemInOffHand(targetState.offhand);
            }
        } finally {
            // Release the diff guard on the next tick so subsequent real
            // events can be captured normally.
            new BukkitRunnable() {
                @Override
                public void run() {
                    pendingDiffPlayers.remove(uuid);
                    pendingPreSnapshots.remove(uuid);
                }
            }.runTask(plugin);
        }

        // Bookkeeping.
        data.set("inventory_backup_last_restore", now);
        if (trackRestored) {
            data.set("inventory_history.last_rewind_at." + target.seq, now);
        }
        // Append a REWIND_COMMIT marker so the rewind itself is auditable,
        // then anchor a fresh keyframe so future change records replay
        // correctly from the post-rewind state.
        long commitSeq = nextSeq(data);
        List<SlotDiff> markerDiffs = new ArrayList<SlotDiff>();
        Keyframe latestKf = readLatestKeyframe(data);
        writeChange(data, commitSeq, now, TRIGGER_REWIND_COMMIT, markerDiffs,
            latestKf == null ? -1L : latestKf.seq);
        long postKfSeq = nextSeq(data);
        writeKeyframe(data, postKfSeq, now, TRIGGER_REWIND_COMMIT, targetState);
        saveUserData(player.getName(), data);

        Map<String, String> map = new HashMap<String, String>();
        map.put("cost", String.format("%.2f", cost));
        player.sendMessage(messages.format(player, "invbackup.restore_success", map));

        if (notifyAdmins) {
            String adminMsg = ChatColor.YELLOW + "[NekoSuite] " + player.getName()
                + " rewound to seq=" + target.seq + " (verified=" + verifiedLossInWindow + ")";
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("nekosuite.invbackup.admin")) {
                    admin.sendMessage(adminMsg);
                }
            }
        }
    }

    private boolean isBlockedItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        String n = item.getType().name();
        for (String b : blockedItems) {
            if (n.equalsIgnoreCase(b)) {
                return true;
            }
        }
        return false;
    }

    // =====================================================
    // Preview chest GUI
    // =====================================================

    /**
     * Open the preview chest for the given rewind target.
     */
    public void openPreview(Player player, RewindTarget target) {
        FullSnapshot targetState = computeState(player.getName(), target);
        if (targetState == null) {
            player.sendMessage(messages.format(player, "invbackup.not_found"));
            return;
        }
        FullSnapshot live = FullSnapshot.capture(player);
        PreviewState state = new PreviewState(target, targetState, live);
        previewStates.put(player.getUniqueId(), state);

        String title = messages.format(player, "menu.invbackup.preview.title");
        PreviewMenuHolder holder = new PreviewMenuHolder(target.seq);
        Inventory inv = Bukkit.createInventory(holder, 54, title);

        // Layout:
        // Rows 1-3 (0..26)   -> main inventory 9..35
        // Row 4   (27..35)   -> hotbar 0..8
        // Row 5   (36..44)   -> 36=helmet 37=chest 38=legs 39=boots 40=offhand 41..44 spacer
        // Row 6   (45..53)   -> 45 Older  46 ?  47 Confirm  49 Info  51 Cancel  52 ?  53 Newer
        for (int i = 0; i < 27; i++) {
            int srcSlot = 9 + i;
            inv.setItem(i, displayCopy(targetState.main[srcSlot]));
        }
        for (int i = 0; i < 9; i++) {
            inv.setItem(27 + i, displayCopy(targetState.main[i]));
        }
        // Armor (Bukkit getArmorContents() returns [boots, leggings, chestplate, helmet])
        inv.setItem(36, displayCopy(targetState.armor[3])); // helmet
        inv.setItem(37, displayCopy(targetState.armor[2])); // chestplate
        inv.setItem(38, displayCopy(targetState.armor[1])); // leggings
        inv.setItem(39, displayCopy(targetState.armor[0])); // boots
        inv.setItem(40, displayCopy(targetState.offhand));

        // Older / Newer
        if (allowScrubbing) {
            inv.setItem(45, createItem(Material.ARROW, messages.format(player, "menu.invbackup.preview.older"),
                new String[] { "ID:older" }));
            inv.setItem(53, createItem(Material.ARROW, messages.format(player, "menu.invbackup.preview.newer"),
                new String[] { "ID:newer" }));
        }
        // Confirm
        inv.setItem(47, createItem(Material.LIME_WOOL,
            messages.format(player, "menu.invbackup.preview.confirm"),
            new String[] { messages.format(player, "menu.invbackup.preview.confirm_lore"), "ID:confirm" }));
        // Cancel
        inv.setItem(51, createItem(Material.RED_WOOL,
            messages.format(player, "menu.invbackup.preview.cancel"),
            new String[] { "ID:cancel" }));
        // Info / diff legend
        Map<String, String> map = new HashMap<String, String>();
        map.put("time", DATE_FORMAT.format(new Date(target.timestamp)));
        map.put("seq", String.valueOf(target.seq));
        List<String> legend = buildDiffLegend(player, live, targetState);
        String[] lore = new String[legend.size() + 2];
        lore[0] = messages.format(player, "menu.invbackup.preview.info_time", map);
        lore[1] = messages.format(player, "menu.invbackup.preview.legend_header");
        for (int i = 0; i < legend.size(); i++) {
            lore[2 + i] = legend.get(i);
        }
        inv.setItem(49, createItem(Material.BOOK,
            messages.format(player, "menu.invbackup.preview.info_title", map), lore));

        player.openInventory(inv);
    }

    public boolean handlePreviewClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return true;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return true;
        }
        String id = null;
        for (String line : meta.getLore()) {
            if (line == null) {
                continue;
            }
            String c = ChatColor.stripColor(line);
            int idx = c.indexOf("ID:");
            if (idx >= 0) {
                id = c.substring(idx + 3).trim();
                break;
            }
        }
        if (id == null) {
            return true;
        }
        PreviewState state = previewStates.get(player.getUniqueId());
        if (state == null) {
            player.closeInventory();
            return true;
        }
        if ("confirm".equals(id)) {
            player.closeInventory();
            try {
                applyRewind(player, state.target);
            } catch (InventoryBackupManager.BackupException e) {
                player.sendMessage(e.getMessage());
            }
            previewStates.remove(player.getUniqueId());
            return true;
        }
        if ("cancel".equals(id)) {
            player.closeInventory();
            previewStates.remove(player.getUniqueId());
            player.sendMessage(messages.format(player, "invbackup.cancelled"));
            return true;
        }
        if ("older".equals(id) || "newer".equals(id)) {
            int dir = "older".equals(id) ? -1 : 1;
            RewindTarget t = scrub(player.getName(), state.target, dir);
            if (t == null) {
                player.sendMessage(messages.format(player, "invbackup.history_at_edge"));
                return true;
            }
            player.closeInventory();
            openPreview(player, t);
            return true;
        }
        return true;
    }

    public void closePreview(Player player) {
        previewStates.remove(player.getUniqueId());
    }

    private List<String> buildDiffLegend(Player player, FullSnapshot live, FullSnapshot target) {
        // Aggregate item-count deltas keyed by Material name.
        Map<String, Integer> liveCounts = countItems(live);
        Map<String, Integer> targetCounts = countItems(target);
        Set<String> keys = new HashSet<String>();
        keys.addAll(liveCounts.keySet());
        keys.addAll(targetCounts.keySet());
        List<String> out = new ArrayList<String>();
        List<String> sortedKeys = new ArrayList<String>(keys);
        Collections.sort(sortedKeys);
        for (String key : sortedKeys) {
            int l = liveCounts.containsKey(key) ? liveCounts.get(key) : 0;
            int t = targetCounts.containsKey(key) ? targetCounts.get(key) : 0;
            if (l == t) {
                continue;
            }
            Map<String, String> map = new HashMap<String, String>();
            map.put("item", key);
            map.put("delta", String.valueOf(t - l));
            map.put("before", String.valueOf(l));
            map.put("after", String.valueOf(t));
            String tmpl;
            if (l == 0) {
                tmpl = "menu.invbackup.preview.legend_add";
            } else if (t == 0) {
                tmpl = "menu.invbackup.preview.legend_remove";
            } else {
                tmpl = "menu.invbackup.preview.legend_change";
            }
            out.add(messages.format(player, tmpl, map));
            if (out.size() >= 8) {
                out.add(messages.format(player, "menu.invbackup.preview.legend_more"));
                break;
            }
        }
        if (out.isEmpty()) {
            out.add(messages.format(player, "menu.invbackup.preview.legend_none"));
        }
        return out;
    }

    private Map<String, Integer> countItems(FullSnapshot snap) {
        Map<String, Integer> m = new HashMap<String, Integer>();
        for (ItemStack it : snap.main) {
            addCount(m, it);
        }
        for (ItemStack it : snap.armor) {
            addCount(m, it);
        }
        addCount(m, snap.offhand);
        return m;
    }

    private void addCount(Map<String, Integer> m, ItemStack it) {
        if (it == null || it.getType() == Material.AIR) {
            return;
        }
        String key = it.getType().name();
        Integer cur = m.get(key);
        m.put(key, (cur == null ? 0 : cur) + it.getAmount());
    }

    private ItemStack displayCopy(ItemStack source) {
        if (source == null || source.getType() == Material.AIR) {
            return null;
        }
        return source.clone();
    }

    private ItemStack createItem(Material mat, String name, String[] loreArr) {
        ItemStack item = new ItemStack(mat, 1);
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

    // =====================================================
    // History listing (chat).
    // =====================================================

    public void showHistory(Player player, int page) {
        int perPage = 10;
        YamlConfiguration data = loadUserData(player.getName());
        ConfigurationSection sec = data.getConfigurationSection("inventory_history.changes");
        if (sec == null) {
            player.sendMessage(messages.format(player, "invbackup.no_history"));
            return;
        }
        List<Long> seqs = new ArrayList<Long>();
        for (String key : sec.getKeys(false)) {
            try {
                seqs.add(Long.parseLong(key));
            } catch (NumberFormatException ignored) {
            }
        }
        if (seqs.isEmpty()) {
            player.sendMessage(messages.format(player, "invbackup.no_history"));
            return;
        }
        Collections.sort(seqs, new Comparator<Long>() {
            public int compare(Long a, Long b) { return Long.compare(b, a); }
        });
        int total = (int) Math.ceil((double) seqs.size() / perPage);
        if (page < 1) page = 1;
        if (page > total) page = total;
        Map<String, String> hmap = new HashMap<String, String>();
        hmap.put("page", String.valueOf(page));
        hmap.put("total", String.valueOf(total));
        hmap.put("count", String.valueOf(seqs.size()));
        player.sendMessage(messages.format(player, "invbackup.history_header", hmap));
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, seqs.size());
        for (int i = start; i < end; i++) {
            long s = seqs.get(i);
            ConfigurationSection ch = sec.getConfigurationSection(String.valueOf(s));
            if (ch == null) {
                continue;
            }
            Map<String, String> m = new HashMap<String, String>();
            m.put("seq", String.valueOf(s));
            m.put("time", DATE_FORMAT.format(new Date(ch.getLong("timestamp", 0L))));
            m.put("trigger", ch.getString("trigger", "?"));
            ConfigurationSection diffs = ch.getConfigurationSection("diffs");
            m.put("diffs", String.valueOf(diffs == null ? 0 : diffs.getKeys(false).size()));
            player.sendMessage(messages.format(player, "invbackup.history_entry", m));
        }
    }

    // =====================================================
    // Reconciliation scheduler entry-point.
    // =====================================================

    private long lastReconcileTickStart = 0L;

    public void runReconcileSweep() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastReconcileTickStart < reconcileIntervalMs) {
            return;
        }
        lastReconcileTickStart = now;
        for (Player p : Bukkit.getOnlinePlayers()) {
            reconcile(p);
        }
    }

    public long getReconcileIntervalMs() {
        return reconcileIntervalMs;
    }

    // =====================================================
    // Inner types
    // =====================================================

    /**
     * Per-slot inventory diff record.
     */
    public static class SlotDiff {
        public final String slotType;
        public final int slotIndex;
        public final ItemStack before;
        public final ItemStack after;

        public SlotDiff(String slotType, int slotIndex, ItemStack before, ItemStack after) {
            this.slotType = slotType;
            this.slotIndex = slotIndex;
            this.before = before;
            this.after = after;
        }
    }

    /**
     * Identifies a point in the change log to rewind to.
     */
    public static class RewindTarget {
        public final long seq;
        public final long timestamp;
        /** -1 if not specified, otherwise the count argument from "rewind N". */
        public final int countArg;

        public RewindTarget(long seq, long timestamp, int countArg) {
            this.seq = seq;
            this.timestamp = timestamp;
            this.countArg = countArg;
        }
    }

    /**
     * Full inventory snapshot, also used as the keyframe value type.
     */
    public static class FullSnapshot {
        public ItemStack[] main;   // 36 slots
        public ItemStack[] armor;  // 4 slots
        public ItemStack offhand;

        public FullSnapshot(ItemStack[] main, ItemStack[] armor, ItemStack offhand) {
            this.main = main;
            this.armor = armor;
            this.offhand = offhand;
        }

        public static FullSnapshot capture(Player player) {
            ItemStack[] m = new ItemStack[36];
            ItemStack[] live = player.getInventory().getStorageContents();
            for (int i = 0; i < m.length && i < live.length; i++) {
                m[i] = live[i] == null ? null : live[i].clone();
            }
            ItemStack[] a = new ItemStack[4];
            ItemStack[] liveArmor = player.getInventory().getArmorContents();
            for (int i = 0; i < a.length && i < liveArmor.length; i++) {
                a[i] = liveArmor[i] == null ? null : liveArmor[i].clone();
            }
            ItemStack off = player.getInventory().getItemInOffHand();
            return new FullSnapshot(m, a, off == null ? null : off.clone());
        }

        public FullSnapshot copy() {
            ItemStack[] m = new ItemStack[main.length];
            for (int i = 0; i < m.length; i++) {
                m[i] = main[i] == null ? null : main[i].clone();
            }
            ItemStack[] a = new ItemStack[armor.length];
            for (int i = 0; i < a.length; i++) {
                a[i] = armor[i] == null ? null : armor[i].clone();
            }
            return new FullSnapshot(m, a, offhand == null ? null : offhand.clone());
        }

        public List<SlotDiff> diffTo(FullSnapshot other) {
            List<SlotDiff> out = new ArrayList<SlotDiff>();
            for (int i = 0; i < main.length; i++) {
                if (!stackEquals(main[i], other.main[i])) {
                    out.add(new SlotDiff(SLOT_MAIN, i, main[i], other.main[i]));
                }
            }
            for (int i = 0; i < armor.length; i++) {
                if (!stackEquals(armor[i], other.armor[i])) {
                    out.add(new SlotDiff(SLOT_ARMOR, i, armor[i], other.armor[i]));
                }
            }
            if (!stackEquals(offhand, other.offhand)) {
                out.add(new SlotDiff(SLOT_OFFHAND, 0, offhand, other.offhand));
            }
            return out;
        }

        private static boolean stackEquals(ItemStack a, ItemStack b) {
            boolean aEmpty = a == null || a.getType() == Material.AIR;
            boolean bEmpty = b == null || b.getType() == Material.AIR;
            if (aEmpty && bEmpty) {
                return true;
            }
            if (aEmpty != bEmpty) {
                return false;
            }
            // isSimilar handles material + metadata; amount checked separately.
            return a.isSimilar(b) && a.getAmount() == b.getAmount();
        }

        public void set(String slotType, int slotIndex, ItemStack value) {
            if (SLOT_MAIN.equals(slotType)) {
                if (slotIndex >= 0 && slotIndex < main.length) {
                    main[slotIndex] = value == null ? null : value.clone();
                }
            } else if (SLOT_ARMOR.equals(slotType)) {
                if (slotIndex >= 0 && slotIndex < armor.length) {
                    armor[slotIndex] = value == null ? null : value.clone();
                }
            } else if (SLOT_OFFHAND.equals(slotType)) {
                offhand = value == null ? null : value.clone();
            }
        }
    }

    /**
     * Keyframe value type.
     */
    public static class Keyframe {
        public final long seq;
        public final long timestamp;
        public final String trigger;
        public final FullSnapshot snapshot;

        public Keyframe(long seq, long timestamp, String trigger, FullSnapshot snapshot) {
            this.seq = seq;
            this.timestamp = timestamp;
            this.trigger = trigger;
            this.snapshot = snapshot;
        }

        public static Keyframe fromSection(long seq, ConfigurationSection sec) {
            long ts = sec.getLong("timestamp", 0L);
            String trigger = sec.getString("trigger", "?");
            ItemStack[] m = new ItemStack[36];
            ConfigurationSection ms = sec.getConfigurationSection("main");
            if (ms != null) {
                for (String key : ms.getKeys(false)) {
                    try {
                        int i = Integer.parseInt(key);
                        if (i >= 0 && i < m.length) {
                            m[i] = ms.getItemStack(key);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            ItemStack[] a = new ItemStack[4];
            ConfigurationSection as = sec.getConfigurationSection("armor");
            if (as != null) {
                for (String key : as.getKeys(false)) {
                    try {
                        int i = Integer.parseInt(key);
                        if (i >= 0 && i < a.length) {
                            a[i] = as.getItemStack(key);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            ItemStack off = sec.contains("offhand") ? sec.getItemStack("offhand") : null;
            return new Keyframe(seq, ts, trigger, new FullSnapshot(m, a, off));
        }
    }

    /**
     * Per-player in-flight preview state (target plus captured live state for diff legend).
     */
    private static class PreviewState {
        final RewindTarget target;
        final FullSnapshot targetState;
        final FullSnapshot liveState;
        PreviewState(RewindTarget target, FullSnapshot targetState, FullSnapshot liveState) {
            this.target = target;
            this.targetState = targetState;
            this.liveState = liveState;
        }
    }

    /**
     * Marker holder for preview chest GUIs.
     */
    public static class PreviewMenuHolder implements InventoryHolder {
        private final long targetSeq;
        public PreviewMenuHolder(long targetSeq) {
            this.targetSeq = targetSeq;
        }
        public long getTargetSeq() {
            return targetSeq;
        }
        public Inventory getInventory() {
            return null;
        }
    }
}
