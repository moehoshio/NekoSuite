package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Teleport Manager - Handles player-to-player teleportation with cost control and status management.
 * 
 * Features:
 * - Teleport to other players with configurable cost
 * - Toggle TP status (allow/disallow being teleported to)
 * - Pending request system with timeout
 * - Request accept/deny functionality
 */
public class TeleportManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Economy economy;
    private final File storageDir;

    // Configuration
    private double tpCost = 0;
    private int requestTimeout = 60; // seconds
    private boolean defaultTpStatus = true; // default: allow tp

    // Pending TP requests: target UUID -> requester UUID
    private final Map<UUID, TpRequest> pendingRequests = new HashMap<UUID, TpRequest>();

    public TeleportManager(JavaPlugin plugin, Messages messages, File configFile, Economy economy) {
        this.plugin = plugin;
        this.messages = messages;
        this.economy = economy;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String dataDir = config.getString("storage.data_dir", "userdata");
        storageDir = new File(plugin.getDataFolder(), dataDir);
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            plugin.getLogger().warning("Unable to create data directory: " + storageDir.getAbsolutePath());
        }
        loadConfig(config);
    }

    private void loadConfig(YamlConfiguration config) {
        tpCost = config.getDouble("tp.cost", 0);
        requestTimeout = config.getInt("tp.request_timeout", 60);
        defaultTpStatus = config.getBoolean("tp.default_status", true);
    }

    /**
     * Check if a player allows teleportation to them.
     */
    public boolean isTpEnabled(String playerName) {
        YamlConfiguration data = loadUserData(playerName);
        return data.getBoolean("tp.enabled", defaultTpStatus);
    }

    /**
     * Toggle a player's TP status (whether others can tp to them).
     */
    public boolean toggleTpStatus(Player player) {
        YamlConfiguration data = loadUserData(player.getName());
        boolean current = data.getBoolean("tp.enabled", defaultTpStatus);
        boolean newStatus = !current;
        data.set("tp.enabled", newStatus);
        saveUserData(player.getName(), data);
        return newStatus;
    }

    /**
     * Set a player's TP status explicitly.
     */
    public void setTpStatus(Player player, boolean enabled) {
        YamlConfiguration data = loadUserData(player.getName());
        data.set("tp.enabled", enabled);
        saveUserData(player.getName(), data);
    }

    /**
     * Send a TP request from one player to another.
     * Returns true if request was sent successfully.
     */
    public boolean sendTpRequest(Player requester, Player target) {
        // Check if target allows TP
        if (!isTpEnabled(target.getName())) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("target", target.getName());
            requester.sendMessage(messages.format(requester, "tp.target_disabled", map));
            return false;
        }

        // Check cost
        if (tpCost > 0 && economy != null) {
            double balance = economy.getBalance(requester);
            if (balance < tpCost) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("cost", String.valueOf((long) tpCost));
                map.put("balance", String.valueOf((long) balance));
                requester.sendMessage(messages.format(requester, "tp.insufficient_balance", map));
                return false;
            }
        }

        // Create request
        TpRequest request = new TpRequest(requester.getUniqueId(), target.getUniqueId(), System.currentTimeMillis());
        pendingRequests.put(target.getUniqueId(), request);

        // Notify requester
        Map<String, String> requesterMap = new HashMap<String, String>();
        requesterMap.put("target", target.getName());
        requesterMap.put("timeout", String.valueOf(requestTimeout));
        requester.sendMessage(messages.format(requester, "tp.request_sent", requesterMap));

        // Notify target with clickable accept/deny buttons
        Map<String, String> targetMap = new HashMap<String, String>();
        targetMap.put("requester", requester.getName());
        targetMap.put("timeout", String.valueOf(requestTimeout));
        
        // Send interactive message with hover and click (using localized strings)
        String mainText = messages.getRaw(target, "tp.request_received_interactive");
        if (mainText == null || mainText.equals("tp.request_received_interactive")) {
            // Fallback to default format
            mainText = "&e⚡ &6{requester} &7requests to teleport to you! ";
        }
        mainText = mainText.replace("{requester}", requester.getName());
        net.md_5.bungee.api.chat.TextComponent mainMessage = new net.md_5.bungee.api.chat.TextComponent(
            messages.colorize(mainText));
        
        // Accept button with hover (localized)
        String acceptText = messages.getRaw(target, "tp.button_accept");
        if (acceptText == null || acceptText.equals("tp.button_accept")) {
            acceptText = "&a[Accept]";
        }
        String acceptHover = messages.getRaw(target, "tp.button_accept_hover");
        if (acceptHover == null || acceptHover.equals("tp.button_accept_hover")) {
            acceptHover = "&aClick to accept teleport request";
        }
        net.md_5.bungee.api.chat.TextComponent acceptBtn = messages.createHoverClickText(
            acceptText, 
            acceptHover, 
            "/ntp accept"
        );
        
        net.md_5.bungee.api.chat.TextComponent separator = new net.md_5.bungee.api.chat.TextComponent(
            messages.colorize(" &8| "));
        
        // Deny button with hover (localized)
        String denyText = messages.getRaw(target, "tp.button_deny");
        if (denyText == null || denyText.equals("tp.button_deny")) {
            denyText = "&c[Deny]";
        }
        String denyHover = messages.getRaw(target, "tp.button_deny_hover");
        if (denyHover == null || denyHover.equals("tp.button_deny_hover")) {
            denyHover = "&cClick to deny teleport request";
        }
        net.md_5.bungee.api.chat.TextComponent denyBtn = messages.createHoverClickText(
            denyText, 
            denyHover, 
            "/ntp deny"
        );
        
        messages.sendMessage(target, mainMessage, acceptBtn, separator, denyBtn);

        // Schedule timeout
        final UUID targetId = target.getUniqueId();
        final UUID requesterId = request.getRequesterId();
        final long requestTimestamp = request.getTimestamp();
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                TpRequest req = pendingRequests.get(targetId);
                if (req != null && req.getRequesterId().equals(requesterId) && req.getTimestamp() == requestTimestamp) {
                    pendingRequests.remove(targetId);
                    Player requesterPlayer = Bukkit.getPlayer(requesterId);
                    if (requesterPlayer != null && requesterPlayer.isOnline()) {
                        requesterPlayer.sendMessage(messages.format(requesterPlayer, "tp.request_expired"));
                    }
                }
            }
        }, requestTimeout * 20L);

        return true;
    }

    /**
     * Accept a pending TP request.
     */
    public boolean acceptTpRequest(Player target) {
        TpRequest request = pendingRequests.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(messages.format(target, "tp.no_pending_request"));
            return false;
        }

        Player requester = Bukkit.getPlayer(request.getRequesterId());
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(messages.format(target, "tp.requester_offline"));
            return false;
        }

        // Charge cost
        if (tpCost > 0 && economy != null) {
            double balance = economy.getBalance(requester);
            if (balance < tpCost) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("cost", String.valueOf((long) tpCost));
                map.put("balance", String.valueOf((long) balance));
                requester.sendMessage(messages.format(requester, "tp.insufficient_balance", map));
                target.sendMessage(messages.format(target, "tp.requester_no_money"));
                return false;
            }
            EconomyResponse response = economy.withdrawPlayer(requester, tpCost);
            if (response == null || !response.transactionSuccess()) {
                requester.sendMessage(messages.format(requester, "tp.cost_failure"));
                return false;
            }
        }

        // Perform teleport
        requester.teleport(target.getLocation());

        Map<String, String> map = new HashMap<String, String>();
        map.put("target", target.getName());
        map.put("requester", requester.getName());
        if (tpCost > 0) {
            map.put("cost", String.valueOf((long) tpCost));
        }
        requester.sendMessage(messages.format(requester, "tp.success", map));
        target.sendMessage(messages.format(target, "tp.accepted", map));

        return true;
    }

    /**
     * Deny a pending TP request.
     */
    public boolean denyTpRequest(Player target) {
        TpRequest request = pendingRequests.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(messages.format(target, "tp.no_pending_request"));
            return false;
        }

        Player requester = Bukkit.getPlayer(request.getRequesterId());
        if (requester != null && requester.isOnline()) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("target", target.getName());
            requester.sendMessage(messages.format(requester, "tp.denied", map));
        }

        target.sendMessage(messages.format(target, "tp.deny_success"));
        return true;
    }

    /**
     * Cancel an outgoing TP request.
     */
    public boolean cancelTpRequest(Player requester) {
        UUID found = null;
        for (Map.Entry<UUID, TpRequest> entry : pendingRequests.entrySet()) {
            if (entry.getValue().getRequesterId().equals(requester.getUniqueId())) {
                found = entry.getKey();
                break;
            }
        }
        if (found != null) {
            pendingRequests.remove(found);
            requester.sendMessage(messages.format(requester, "tp.cancel_success"));
            Player target = Bukkit.getPlayer(found);
            if (target != null && target.isOnline()) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("requester", requester.getName());
                target.sendMessage(messages.format(target, "tp.request_cancelled", map));
            }
            return true;
        }
        requester.sendMessage(messages.format(requester, "tp.no_outgoing_request"));
        return false;
    }

    /**
     * Get the current TP cost.
     */
    public double getTpCost() {
        return tpCost;
    }

    private YamlConfiguration loadUserData(String playerName) {
        File file = new File(storageDir, playerName + ".yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Unable to create user data file: " + e.getMessage());
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
     * Represents a pending TP request.
     */
    private static class TpRequest {
        private final UUID requesterId;
        private final UUID targetId;
        private final long timestamp;

        TpRequest(UUID requesterId, UUID targetId, long timestamp) {
            this.requesterId = requesterId;
            this.targetId = targetId;
            this.timestamp = timestamp;
        }

        UUID getRequesterId() {
            return requesterId;
        }

        UUID getTargetId() {
            return targetId;
        }

        long getTimestamp() {
            return timestamp;
        }
    }
}
