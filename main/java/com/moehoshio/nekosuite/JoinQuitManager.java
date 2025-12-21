package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages actions to be executed when players join or quit the server.
 */
public class JoinQuitManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final BuyManager buyManager;
    private final YamlConfiguration config;
    private boolean enabled;
    private final List<JoinAction> joinActions = new ArrayList<JoinAction>();
    private final List<QuitAction> quitActions = new ArrayList<QuitAction>();

    public JoinQuitManager(JavaPlugin plugin, Messages messages, BuyManager buyManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.buyManager = buyManager;

        File configFile = new File(plugin.getDataFolder(), "join_quit_config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("join_quit_config.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
        loadConfig();
    }

    private void loadConfig() {
        this.enabled = config.getBoolean("enabled", true);
        joinActions.clear();
        quitActions.clear();

        // Load join actions
        List<?> joinList = config.getList("join_actions");
        if (joinList != null) {
            for (Object obj : joinList) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> actionMap = (Map<String, Object>) obj;
                    JoinAction action = parseJoinAction(actionMap);
                    if (action != null && action.isEnabled()) {
                        joinActions.add(action);
                    }
                }
            }
        }

        // Load quit actions
        List<?> quitList = config.getList("quit_actions");
        if (quitList != null) {
            for (Object obj : quitList) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> actionMap = (Map<String, Object>) obj;
                    QuitAction action = parseQuitAction(actionMap);
                    if (action != null && action.isEnabled()) {
                        quitActions.add(action);
                    }
                }
            }
        }
    }

    private JoinAction parseJoinAction(Map<String, Object> actionMap) {
        String type = (String) actionMap.get("type");
        boolean enabled = actionMap.containsKey("enabled") ? (Boolean) actionMap.get("enabled") : true;
        int delay = actionMap.containsKey("delay") ? ((Number) actionMap.get("delay")).intValue() : 0;

        if ("check_buy_expiry".equals(type)) {
            return new CheckBuyExpiryAction(enabled, delay);
        } else if ("message".equals(type)) {
            String messageKey = (String) actionMap.get("message_key");
            return new MessageAction(enabled, delay, messageKey);
        } else if ("command".equals(type)) {
            String command = (String) actionMap.get("command");
            return new CommandAction(enabled, delay, command);
        } else if ("permission_add".equals(type)) {
            String permission = (String) actionMap.get("permission");
            String world = (String) actionMap.get("world");
            return new PermissionAddAction(enabled, delay, permission, world);
        } else if ("permission_remove".equals(type)) {
            String permission = (String) actionMap.get("permission");
            String world = (String) actionMap.get("world");
            return new PermissionRemoveAction(enabled, delay, permission, world);
        }
        return null;
    }

    private QuitAction parseQuitAction(Map<String, Object> actionMap) {
        String type = (String) actionMap.get("type");
        boolean enabled = actionMap.containsKey("enabled") ? (Boolean) actionMap.get("enabled") : true;

        if ("command".equals(type)) {
            String command = (String) actionMap.get("command");
            return new QuitCommandAction(enabled, command);
        } else if ("log_quit".equals(type)) {
            return new LogQuitAction(enabled);
        }
        return null;
    }

    /**
     * Called when a player joins the server.
     */
    public void onPlayerJoin(Player player) {
        if (!enabled) {
            return;
        }
        for (JoinAction action : joinActions) {
            if (action.getDelay() > 0) {
                final JoinAction actionFinal = action;
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            executeJoinAction(player, actionFinal);
                        }
                    }
                }, action.getDelay());
            } else {
                executeJoinAction(player, action);
            }
        }
    }

    /**
     * Called when a player quits the server.
     */
    public void onPlayerQuit(Player player) {
        if (!enabled) {
            return;
        }
        for (QuitAction action : quitActions) {
            executeQuitAction(player, action);
        }
    }

    private void executeJoinAction(Player player, JoinAction action) {
        if (action instanceof CheckBuyExpiryAction) {
            if (buyManager != null) {
                buyManager.check(player);
            }
        } else if (action instanceof MessageAction) {
            MessageAction msgAction = (MessageAction) action;
            if (msgAction.getMessageKey() != null && !msgAction.getMessageKey().isEmpty()) {
                Map<String, String> placeholders = new HashMap<String, String>();
                placeholders.put("player", player.getName());
                player.sendMessage(messages.format(player, msgAction.getMessageKey(), placeholders));
            }
        } else if (action instanceof CommandAction) {
            CommandAction cmdAction = (CommandAction) action;
            if (cmdAction.getCommand() != null && !cmdAction.getCommand().isEmpty()) {
                String cmd = cmdAction.getCommand().replace("{player}", player.getName());
                if (cmd.startsWith("/")) {
                    cmd = cmd.substring(1);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        } else if (action instanceof PermissionAddAction) {
            // Permission actions would need Vault integration
            // This is a placeholder for future implementation
        }
    }

    private void executeQuitAction(Player player, QuitAction action) {
        if (action instanceof QuitCommandAction) {
            QuitCommandAction cmdAction = (QuitCommandAction) action;
            if (cmdAction.getCommand() != null && !cmdAction.getCommand().isEmpty()) {
                String cmd = cmdAction.getCommand().replace("{player}", player.getName());
                if (cmd.startsWith("/")) {
                    cmd = cmd.substring(1);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        } else if (action instanceof LogQuitAction) {
            plugin.getLogger().info("Player " + player.getName() + " has quit the server.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // Action interfaces and classes
    private interface JoinAction {
        boolean isEnabled();
        int getDelay();
    }

    private interface QuitAction {
        boolean isEnabled();
    }

    private static class CheckBuyExpiryAction implements JoinAction {
        private final boolean enabled;
        private final int delay;

        CheckBuyExpiryAction(boolean enabled, int delay) {
            this.enabled = enabled;
            this.delay = delay;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public int getDelay() {
            return delay;
        }
    }

    private static class MessageAction implements JoinAction {
        private final boolean enabled;
        private final int delay;
        private final String messageKey;

        MessageAction(boolean enabled, int delay, String messageKey) {
            this.enabled = enabled;
            this.delay = delay;
            this.messageKey = messageKey;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public int getDelay() {
            return delay;
        }

        String getMessageKey() {
            return messageKey;
        }
    }

    private static class CommandAction implements JoinAction {
        private final boolean enabled;
        private final int delay;
        private final String command;

        CommandAction(boolean enabled, int delay, String command) {
            this.enabled = enabled;
            this.delay = delay;
            this.command = command;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public int getDelay() {
            return delay;
        }

        String getCommand() {
            return command;
        }
    }

    private static class PermissionAddAction implements JoinAction {
        private final boolean enabled;
        private final int delay;
        private final String permission;
        private final String world;

        PermissionAddAction(boolean enabled, int delay, String permission, String world) {
            this.enabled = enabled;
            this.delay = delay;
            this.permission = permission;
            this.world = world;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public int getDelay() {
            return delay;
        }

        String getPermission() {
            return permission;
        }

        String getWorld() {
            return world;
        }
    }

    private static class PermissionRemoveAction implements JoinAction {
        private final boolean enabled;
        private final int delay;
        private final String permission;
        private final String world;

        PermissionRemoveAction(boolean enabled, int delay, String permission, String world) {
            this.enabled = enabled;
            this.delay = delay;
            this.permission = permission;
            this.world = world;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public int getDelay() {
            return delay;
        }

        String getPermission() {
            return permission;
        }

        String getWorld() {
            return world;
        }
    }

    private static class QuitCommandAction implements QuitAction {
        private final boolean enabled;
        private final String command;

        QuitCommandAction(boolean enabled, String command) {
            this.enabled = enabled;
            this.command = command;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        String getCommand() {
            return command;
        }
    }

    private static class LogQuitAction implements QuitAction {
        private final boolean enabled;

        LogQuitAction(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }
}
