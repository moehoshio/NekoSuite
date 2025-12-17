package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the mail system for sending messages, rewards, and items between players and system.
 */
public class MailManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final MenuLayout layout;
    private final File storageDir;
    private final int maxMailsPerPlayer;
    private final int mailExpiryDays;
    private final boolean allowPlayerSending;
    private final List<String> blockedItems;

    public MailManager(JavaPlugin plugin, Messages messages, File configFile, MenuLayout layout) {
        this.plugin = plugin;
        this.messages = messages;
        this.layout = layout == null ? new MenuLayout(plugin) : layout;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String dataDir = config.getString("storage.data_dir", "userdata");
        storageDir = new File(plugin.getDataFolder(), dataDir);
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create data directory: " + storageDir.getAbsolutePath());
        }
        maxMailsPerPlayer = config.getInt("settings.max_mails_per_player", 50);
        mailExpiryDays = config.getInt("settings.mail_expiry_days", 30);
        allowPlayerSending = config.getBoolean("settings.allow_player_sending", true);
        blockedItems = config.getStringList("settings.blocked_items");
    }

    /**
     * Send mail from system to a player.
     */
    public boolean sendSystemMail(String recipient, String subject, String content, List<String> commands) {
        return sendMail("SYSTEM", recipient, subject, content, commands);
    }

    /**
     * Send mail from one player to another.
     */
    public boolean sendPlayerMail(Player sender, String recipient, String subject, String content, String itemId, int amount) throws MailException {
        if (!allowPlayerSending) {
            throw new MailException(messages.format(sender, "mail.player_sending_disabled"));
        }
        if (sender.getName().equalsIgnoreCase(recipient)) {
            throw new MailException(messages.format(sender, "mail.cannot_send_self"));
        }
        
        // Build command for item if provided
        List<String> commands = new ArrayList<String>();
        if (itemId != null && !itemId.isEmpty() && amount > 0) {
            // Check blocked items
            for (String blocked : blockedItems) {
                if (itemId.toLowerCase().contains(blocked.toLowerCase())) {
                    throw new MailException(messages.format(sender, "mail.item_blocked"));
                }
            }
            String sanitizedItem = sanitizeItemName(itemId);
            commands.add("minecraft:give {player} " + sanitizedItem + " " + amount);
        }
        
        return sendMail(sender.getName(), recipient, subject, content, commands);
    }

    /**
     * Internal method to send mail.
     */
    private boolean sendMail(String sender, String recipient, String subject, String content, List<String> commands) {
        YamlConfiguration data = loadUserData(recipient);
        
        // Check mail limit
        List<Mail> mails = loadMails(data);
        if (mails.size() >= maxMailsPerPlayer) {
            return false;
        }
        
        // Create new mail
        String mailId = UUID.randomUUID().toString().substring(0, 8);
        long timestamp = System.currentTimeMillis();
        
        ConfigurationSection mailSection = data.createSection("mail.inbox." + mailId);
        mailSection.set("sender", sender);
        mailSection.set("subject", subject);
        mailSection.set("content", content);
        mailSection.set("timestamp", timestamp);
        mailSection.set("read", false);
        mailSection.set("claimed", false);
        if (commands != null && !commands.isEmpty()) {
            mailSection.set("commands", commands);
        }
        
        saveUserData(recipient, data);
        
        // Notify if online
        Player onlineRecipient = Bukkit.getPlayer(recipient);
        if (onlineRecipient != null && onlineRecipient.isOnline()) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("sender", sender);
            map.put("subject", subject);
            onlineRecipient.sendMessage(messages.format(onlineRecipient, "mail.new_mail_notification", map));
        }
        
        return true;
    }

    /**
     * Get all mails for a player.
     */
    public List<Mail> getMails(String playerName) {
        YamlConfiguration data = loadUserData(playerName);
        return loadMails(data);
    }

    /**
     * Get unread mail count.
     */
    public int getUnreadCount(String playerName) {
        List<Mail> mails = getMails(playerName);
        int count = 0;
        for (Mail mail : mails) {
            if (!mail.isRead()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get unclaimed mail count (mails with commands that haven't been claimed).
     */
    public int getUnclaimedCount(String playerName) {
        List<Mail> mails = getMails(playerName);
        int count = 0;
        for (Mail mail : mails) {
            if (!mail.isClaimed() && mail.hasCommands()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Mark a mail as read.
     */
    public void markAsRead(String playerName, String mailId) {
        YamlConfiguration data = loadUserData(playerName);
        data.set("mail.inbox." + mailId + ".read", true);
        saveUserData(playerName, data);
    }

    /**
     * Claim mail rewards (execute commands).
     */
    public boolean claimMail(Player player, String mailId) throws MailException {
        YamlConfiguration data = loadUserData(player.getName());
        ConfigurationSection mailSection = data.getConfigurationSection("mail.inbox." + mailId);
        
        if (mailSection == null) {
            throw new MailException(messages.format(player, "mail.not_found"));
        }
        
        if (mailSection.getBoolean("claimed", false)) {
            throw new MailException(messages.format(player, "mail.already_claimed"));
        }
        
        List<String> commands = mailSection.getStringList("commands");
        if (commands.isEmpty()) {
            throw new MailException(messages.format(player, "mail.no_rewards"));
        }
        
        // Execute commands
        for (String command : commands) {
            if (command == null || command.trim().isEmpty()) {
                continue;
            }
            String cmd = command
                    .replace("{player}", player.getName())
                    .replace("%player%", player.getName())
                    .replace("$player", player.getName());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        
        // Mark as claimed and read
        mailSection.set("claimed", true);
        mailSection.set("read", true);
        saveUserData(player.getName(), data);
        
        return true;
    }

    /**
     * Delete a mail.
     */
    public boolean deleteMail(Player player, String mailId) throws MailException {
        YamlConfiguration data = loadUserData(player.getName());
        ConfigurationSection mailSection = data.getConfigurationSection("mail.inbox." + mailId);
        
        if (mailSection == null) {
            throw new MailException(messages.format(player, "mail.not_found"));
        }
        
        // Check if there are unclaimed rewards
        boolean claimed = mailSection.getBoolean("claimed", false);
        List<String> commands = mailSection.getStringList("commands");
        if (!claimed && !commands.isEmpty()) {
            throw new MailException(messages.format(player, "mail.delete_unclaimed"));
        }
        
        data.set("mail.inbox." + mailId, null);
        saveUserData(player.getName(), data);
        
        return true;
    }

    /**
     * Open mail GUI for a player.
     */
    public void openMenu(Player player) {
        openMenu(player, 1);
    }

    /**
     * Open mail GUI with pagination.
     */
    public void openMenu(Player player, int page) {
        MenuLayout.MailLayout mailLayout = layout.getMailLayout();
        String title = messages.format(player, "menu.mail.title");
        Inventory inv = Bukkit.createInventory(new MailMenuHolder(page), mailLayout.getSize(), title);
        
        List<Mail> mails = getMails(player.getName());
        // Sort by timestamp (newest first)
        Collections.sort(mails, new Comparator<Mail>() {
            public int compare(Mail a, Mail b) {
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            }
        });
        
        int slotsPerPage = mailLayout.getItemSlots().size();
        int startIndex = (page - 1) * slotsPerPage;
        int totalPages = (int) Math.ceil((double) mails.size() / slotsPerPage);
        if (totalPages == 0) {
            totalPages = 1;
        }
        
        int slotIndex = 0;
        for (int i = startIndex; i < mails.size() && slotIndex < slotsPerPage; i++) {
            Mail mail = mails.get(i);
            ItemStack stack = createMailItem(player, mail);
            int slot = mailLayout.getItemSlots().get(slotIndex++);
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, stack);
            }
        }
        
        // Navigation buttons
        if (page > 1 && mailLayout.getPrevSlot() >= 0 && mailLayout.getPrevSlot() < inv.getSize()) {
            ItemStack prev = createItem(Material.ARROW, messages.format(player, "menu.mail.prev_page"), new String[]{"ID:prev_" + (page - 1)});
            inv.setItem(mailLayout.getPrevSlot(), prev);
        }
        if (page < totalPages && mailLayout.getNextSlot() >= 0 && mailLayout.getNextSlot() < inv.getSize()) {
            ItemStack next = createItem(Material.ARROW, messages.format(player, "menu.mail.next_page"), new String[]{"ID:next_" + (page + 1)});
            inv.setItem(mailLayout.getNextSlot(), next);
        }
        
        // Info item
        if (mailLayout.getInfoSlot() >= 0 && mailLayout.getInfoSlot() < inv.getSize()) {
            Map<String, String> infoMap = new HashMap<String, String>();
            infoMap.put("page", String.valueOf(page));
            infoMap.put("total_pages", String.valueOf(totalPages));
            infoMap.put("unread", String.valueOf(getUnreadCount(player.getName())));
            infoMap.put("unclaimed", String.valueOf(getUnclaimedCount(player.getName())));
            String infoTitle = messages.format(player, "menu.mail.info_title", infoMap);
            List<String> infoLore = new ArrayList<String>();
            infoLore.add(messages.format(player, "menu.mail.info_lore", infoMap));
            ItemStack info = createItem(Material.BOOK, infoTitle, infoLore.toArray(new String[0]));
            inv.setItem(mailLayout.getInfoSlot(), info);
        }
        
        // Close button
        if (mailLayout.getCloseSlot() >= 0 && mailLayout.getCloseSlot() < inv.getSize()) {
            ItemStack close = createItem(Material.BARRIER, messages.format(player, "menu.close"), new String[0]);
            inv.setItem(mailLayout.getCloseSlot(), close);
        }
        
        player.openInventory(inv);
    }

    private ItemStack createMailItem(Player player, Mail mail) {
        Material material;
        String titleKey;
        
        if (!mail.isRead()) {
            material = Material.WRITABLE_BOOK;
            titleKey = "menu.mail.unread_title";
        } else if (mail.hasCommands() && !mail.isClaimed()) {
            material = Material.CHEST;
            titleKey = "menu.mail.unclaimed_title";
        } else {
            material = Material.PAPER;
            titleKey = "menu.mail.read_title";
        }
        
        Map<String, String> map = new HashMap<String, String>();
        map.put("subject", mail.getSubject());
        map.put("sender", mail.getSender());
        
        String displayName = messages.format(player, titleKey, map);
        
        List<String> lore = new ArrayList<String>();
        lore.add(messages.format(player, "menu.mail.sender_lore", map));
        
        // Add content preview (truncated)
        String content = mail.getContent();
        if (content != null && !content.isEmpty()) {
            String preview = content.length() > 30 ? content.substring(0, 30) + "..." : content;
            Map<String, String> contentMap = new HashMap<String, String>();
            contentMap.put("content", preview);
            lore.add(messages.format(player, "menu.mail.content_lore", contentMap));
        }
        
        // Add reward indicator
        if (mail.hasCommands()) {
            if (mail.isClaimed()) {
                lore.add(messages.format(player, "menu.mail.claimed_indicator"));
            } else {
                lore.add(messages.format(player, "menu.mail.has_rewards_indicator"));
            }
        }
        
        // Add action hints
        lore.add("");
        if (mail.hasCommands() && !mail.isClaimed()) {
            lore.add(messages.format(player, "menu.mail.click_to_claim"));
        } else {
            lore.add(messages.format(player, "menu.mail.click_to_view"));
        }
        lore.add(messages.format(player, "menu.mail.shift_click_to_delete"));
        
        // Add ID for identification
        lore.add(ChatColor.DARK_GRAY + "ID:" + mail.getId());
        
        return createItem(material, displayName, lore.toArray(new String[0]));
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

    /**
     * Handle menu click events.
     */
    public boolean handleMenuClick(Player player, ItemStack clicked, boolean isShiftClick, int currentPage) {
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
                    
                    // Navigation
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
                    
                    // Mail actions
                    if (isShiftClick) {
                        // Delete mail
                        try {
                            deleteMail(player, id);
                            player.sendMessage(messages.format(player, "mail.deleted"));
                            openMenu(player, currentPage);
                        } catch (MailException e) {
                            player.sendMessage(e.getMessage());
                        }
                    } else {
                        // Read or claim mail
                        Mail mail = findMail(player.getName(), id);
                        if (mail != null) {
                            if (mail.hasCommands() && !mail.isClaimed()) {
                                // Claim rewards
                                try {
                                    claimMail(player, id);
                                    player.sendMessage(messages.format(player, "mail.claimed"));
                                    openMenu(player, currentPage);
                                } catch (MailException e) {
                                    player.sendMessage(e.getMessage());
                                }
                            } else {
                                // Just mark as read and show content
                                markAsRead(player.getName(), id);
                                showMailContent(player, mail);
                                // Refresh the menu so the read state updates immediately
                                openMenu(player, currentPage);
                            }
                        }
                    }
                    return true;
                }
            }
        }
        return true;
    }

    private void showMailContent(Player player, Mail mail) {
        player.sendMessage("");
        player.sendMessage(messages.format(player, "mail.header"));
        Map<String, String> map = new HashMap<String, String>();
        map.put("subject", mail.getSubject());
        map.put("sender", mail.getSender());
        player.sendMessage(messages.format(player, "mail.subject_line", map));
        player.sendMessage(messages.format(player, "mail.sender_line", map));
        player.sendMessage("");
        if (mail.getContent() != null && !mail.getContent().isEmpty()) {
            player.sendMessage(ChatColor.WHITE + mail.getContent());
        }
        player.sendMessage("");
        player.sendMessage(messages.format(player, "mail.footer"));
    }

    private Mail findMail(String playerName, String mailId) {
        List<Mail> mails = getMails(playerName);
        for (Mail mail : mails) {
            if (mail.getId().equals(mailId)) {
                return mail;
            }
        }
        return null;
    }

    private List<Mail> loadMails(YamlConfiguration data) {
        List<Mail> mails = new ArrayList<Mail>();
        ConfigurationSection inbox = data.getConfigurationSection("mail.inbox");
        if (inbox == null) {
            return mails;
        }
        
        long now = System.currentTimeMillis();
        long expiryMillis = mailExpiryDays * 24L * 60L * 60L * 1000L;
        
        for (String id : inbox.getKeys(false)) {
            ConfigurationSection section = inbox.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            
            long timestamp = section.getLong("timestamp", 0L);
            // Check expiry
            if (expiryMillis > 0 && now - timestamp > expiryMillis) {
                continue; // Skip expired mails
            }
            
            String sender = section.getString("sender", "SYSTEM");
            String subject = section.getString("subject", "");
            String content = section.getString("content", "");
            boolean read = section.getBoolean("read", false);
            boolean claimed = section.getBoolean("claimed", false);
            List<String> commands = section.getStringList("commands");
            
            mails.add(new Mail(id, sender, subject, content, timestamp, read, claimed, commands));
        }
        
        return mails;
    }

    /**
     * Notify player of unread mail on login.
     */
    public void notifyUnreadMail(Player player) {
        int unread = getUnreadCount(player.getName());
        int unclaimed = getUnclaimedCount(player.getName());
        
        if (unread > 0 || unclaimed > 0) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("unread", String.valueOf(unread));
            map.put("unclaimed", String.valueOf(unclaimed));
            player.sendMessage(messages.format(player, "mail.login_notification", map));
        }
    }

    /**
     * Clean up expired mails for a player.
     */
    public void cleanupExpiredMails(String playerName) {
        YamlConfiguration data = loadUserData(playerName);
        ConfigurationSection inbox = data.getConfigurationSection("mail.inbox");
        if (inbox == null) {
            return;
        }
        
        long now = System.currentTimeMillis();
        long expiryMillis = mailExpiryDays * 24L * 60L * 60L * 1000L;
        boolean changed = false;
        
        for (String id : new ArrayList<String>(inbox.getKeys(false))) {
            ConfigurationSection section = inbox.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            
            long timestamp = section.getLong("timestamp", 0L);
            if (expiryMillis > 0 && now - timestamp > expiryMillis) {
                inbox.set(id, null);
                changed = true;
            }
        }
        
        if (changed) {
            saveUserData(playerName, data);
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

    private String sanitizeItemName(String raw) {
        if (raw == null) {
            return "unknown_item";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9:_.-]", "");
        if (cleaned.isEmpty()) {
            return "unknown_item";
        }
        return cleaned;
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

    /**
     * Mail data class.
     */
    public static class Mail {
        private final String id;
        private final String sender;
        private final String subject;
        private final String content;
        private final long timestamp;
        private final boolean read;
        private final boolean claimed;
        private final List<String> commands;

        public Mail(String id, String sender, String subject, String content, long timestamp, boolean read, boolean claimed, List<String> commands) {
            this.id = id;
            this.sender = sender;
            this.subject = subject;
            this.content = content;
            this.timestamp = timestamp;
            this.read = read;
            this.claimed = claimed;
            this.commands = commands == null ? new ArrayList<String>() : new ArrayList<String>(commands);
        }

        public String getId() {
            return id;
        }

        public String getSender() {
            return sender;
        }

        public String getSubject() {
            return subject;
        }

        public String getContent() {
            return content;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isRead() {
            return read;
        }

        public boolean isClaimed() {
            return claimed;
        }

        public boolean hasCommands() {
            return commands != null && !commands.isEmpty();
        }

        public List<String> getCommands() {
            return new ArrayList<String>(commands);
        }
    }

    /**
     * Mail menu holder for inventory identification.
     */
    public static class MailMenuHolder implements InventoryHolder {
        private final int currentPage;

        public MailMenuHolder(int currentPage) {
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
     * Mail exception for error handling.
     */
    public static class MailException extends Exception {
        public MailException(String message) {
            super(message);
        }
    }
}
