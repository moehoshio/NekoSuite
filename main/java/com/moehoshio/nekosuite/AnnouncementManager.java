package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages server announcements displayed in a GUI menu.
 */
public class AnnouncementManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final MenuLayout layout;
    private final List<Announcement> announcements = new ArrayList<Announcement>();

    public AnnouncementManager(JavaPlugin plugin, Messages messages, MenuLayout layout) {
        this.plugin = plugin;
        this.messages = messages;
        this.layout = layout;
        loadAnnouncements();
    }

    private void loadAnnouncements() {
        announcements.clear();
        File file = new File(plugin.getDataFolder(), "announcements.yml");
        if (!file.exists()) {
            // Create default announcements file
            createDefaultAnnouncementsFile(file);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("announcements");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection annSection = section.getConfigurationSection(key);
                if (annSection != null) {
                    Announcement announcement = Announcement.fromSection(key, annSection);
                    if (announcement != null) {
                        announcements.add(announcement);
                    }
                }
            }
        }
        // Sort by priority (higher first) and then by date (newer first)
        Collections.sort(announcements, new Comparator<Announcement>() {
            @Override
            public int compare(Announcement a, Announcement b) {
                int priorityCompare = Integer.compare(b.getPriority(), a.getPriority());
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            }
        });
    }

    private void createDefaultAnnouncementsFile(File file) {
        try {
            file.getParentFile().mkdirs();
            YamlConfiguration config = new YamlConfiguration();
            ConfigurationSection section = config.createSection("announcements");
            
            // Create a sample announcement
            ConfigurationSection sample = section.createSection("welcome");
            sample.set("title_key", "announcement.sample.title");
            sample.set("content_key", "announcement.sample.content");
            sample.set("material", "BOOK");
            sample.set("priority", 100);
            sample.set("enabled", true);
            sample.set("timestamp", System.currentTimeMillis());
            
            config.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not create default announcements file: " + e.getMessage());
        }
    }

    /**
     * Reload announcements from file.
     */
    public void reload() {
        loadAnnouncements();
    }

    /**
     * Get all active announcements.
     */
    public List<Announcement> getAnnouncements() {
        List<Announcement> active = new ArrayList<Announcement>();
        for (Announcement ann : announcements) {
            if (ann.isEnabled()) {
                active.add(ann);
            }
        }
        return active;
    }

    /**
     * Open the announcement menu for a player.
     */
    public void openMenu(Player player) {
        openMenu(player, 1);
    }

    /**
     * Open the announcement menu for a player at a specific page.
     */
    public void openMenu(Player player, int page) {
        MenuLayout.AnnouncementLayout annLayout = layout.getAnnouncementLayout();
        int size = annLayout.getSize();
        List<Integer> contentSlots = annLayout.getContentSlots();
        int itemsPerPage = contentSlots.size();
        
        List<Announcement> active = getAnnouncements();
        int totalPages = Math.max(1, (int) Math.ceil((double) active.size() / itemsPerPage));
        page = Math.max(1, Math.min(page, totalPages));
        
        Inventory inv = Bukkit.createInventory(new AnnouncementMenuHolder(page), size, messages.format(player, annLayout.getTitleKey()));
        
        // Place announcements
        int startIndex = (page - 1) * itemsPerPage;
        int slotIndex = 0;
        for (int i = startIndex; i < Math.min(startIndex + itemsPerPage, active.size()); i++) {
            if (slotIndex >= contentSlots.size()) break;
            Announcement ann = active.get(i);
            int slot = contentSlots.get(slotIndex);
            if (slot < size) {
                inv.setItem(slot, createAnnouncementItem(player, ann));
            }
            slotIndex++;
        }
        
        // Navigation buttons
        if (page > 1) {
            inv.setItem(annLayout.getPrevSlot(), createNavButton(player, "announcement.prev_page", Material.ARROW, page - 1));
        }
        if (page < totalPages) {
            inv.setItem(annLayout.getNextSlot(), createNavButton(player, "announcement.next_page", Material.ARROW, page + 1));
        }
        
        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(messages.format(player, "menu.close"));
            close.setItemMeta(closeMeta);
        }
        inv.setItem(annLayout.getCloseSlot(), close);
        
        // Page info (use a paper in an empty slot near the center)
        int infoSlot = 47;
        if (infoSlot < size && inv.getItem(infoSlot) == null) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta infoMeta = info.getItemMeta();
            if (infoMeta != null) {
                Map<String, String> placeholders = new HashMap<String, String>();
                placeholders.put("page", String.valueOf(page));
                placeholders.put("total_pages", String.valueOf(totalPages));
                infoMeta.setDisplayName(messages.format(player, "announcement.page_info", placeholders));
                info.setItemMeta(infoMeta);
            }
            inv.setItem(infoSlot, info);
        }
        
        // If no announcements, show a message
        if (active.isEmpty()) {
            ItemStack noAnn = new ItemStack(Material.BARRIER);
            ItemMeta noAnnMeta = noAnn.getItemMeta();
            if (noAnnMeta != null) {
                noAnnMeta.setDisplayName(messages.format(player, "announcement.no_announcements"));
                noAnn.setItemMeta(noAnnMeta);
            }
            if (contentSlots.size() > 0 && contentSlots.get(0) < size) {
                inv.setItem(contentSlots.get(0), noAnn);
            }
        }
        
        player.openInventory(inv);
    }

    private ItemStack createAnnouncementItem(Player player, Announcement ann) {
        ItemStack item = new ItemStack(ann.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Get title from language file using title_key
            String title = messages.format(player, ann.getTitleKey());
            meta.setDisplayName(title);
            
            // Get content from language file using content_key
            List<String> content = messages.getList(player, ann.getContentKey());
            List<String> lore = new ArrayList<String>();
            if (content != null && !content.isEmpty()) {
                for (String line : content) {
                    lore.add(messages.colorize(line));
                }
            }
            // Add command indicator and announcement ID for click handling
            if (ann.hasCommands()) {
                lore.add("");
                lore.add(messages.format(player, "announcement.click_to_execute"));
            }
            lore.add(ChatColor.DARK_GRAY + "ANN_ID:" + ann.getId());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavButton(Player player, String nameKey, Material material, int targetPage) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.format(player, nameKey));
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.DARK_GRAY + "PAGE:" + targetPage);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Handle menu click events.
     */
    public boolean handleMenuClick(Player player, ItemStack clicked, int page) {
        if (clicked == null || clicked.getType().isAir()) {
            return true;
        }
        if (clicked.getType() == Material.BARRIER) {
            String closeLabel = ChatColor.stripColor(messages.format(player, "menu.close"));
            String display = clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()
                    ? ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                    : "";
            if (!closeLabel.isEmpty() && closeLabel.equalsIgnoreCase(display)) {
                player.closeInventory();
                return true;
            }
        }
        // Check for navigation or announcement click
        if (clicked.hasItemMeta() && clicked.getItemMeta().hasLore()) {
            List<String> lore = clicked.getItemMeta().getLore();
            if (lore != null) {
                for (String line : lore) {
                    if (line != null && line.contains("PAGE:")) {
                        String pageStr = line.substring(line.indexOf("PAGE:") + 5).trim();
                        try {
                            int targetPage = Integer.parseInt(pageStr);
                            openMenu(player, targetPage);
                            return true;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    // Check for announcement click with commands
                    if (line != null && line.contains("ANN_ID:")) {
                        String annId = line.substring(line.indexOf("ANN_ID:") + 7).trim();
                        executeAnnouncementCommands(player, annId);
                        return true;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Execute commands associated with an announcement.
     */
    private void executeAnnouncementCommands(Player player, String announcementId) {
        Announcement ann = null;
        for (Announcement a : announcements) {
            if (a.getId().equals(announcementId)) {
                ann = a;
                break;
            }
        }
        if (ann == null || !ann.hasCommands()) {
            return;
        }
        for (String command : ann.getCommands()) {
            if (command == null || command.trim().isEmpty()) {
                continue;
            }
            String cmd = command.replace("{player}", player.getName())
                    .replace("%player%", player.getName());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    /**
     * Represents an announcement entry.
     */
    public static class Announcement {
        private final String id;
        private final String titleKey;
        private final String contentKey;
        private final Material material;
        private final int priority;
        private final boolean enabled;
        private final long timestamp;
        private final List<String> commands;

        Announcement(String id, String titleKey, String contentKey, Material material, int priority, boolean enabled, long timestamp, List<String> commands) {
            this.id = id;
            this.titleKey = titleKey;
            this.contentKey = contentKey;
            this.material = material;
            this.priority = priority;
            this.enabled = enabled;
            this.timestamp = timestamp;
            this.commands = commands == null ? new ArrayList<String>() : commands;
        }

        static Announcement fromSection(String id, ConfigurationSection section) {
            String titleKey = section.getString("title_key", "announcement." + id + ".title");
            String contentKey = section.getString("content_key", "announcement." + id + ".content");
            Material material = Material.matchMaterial(section.getString("material", "PAPER"));
            if (material == null) {
                material = Material.PAPER;
            }
            int priority = section.getInt("priority", 0);
            boolean enabled = section.getBoolean("enabled", true);
            long timestamp = section.getLong("timestamp", System.currentTimeMillis());
            List<String> commands = section.getStringList("commands");
            return new Announcement(id, titleKey, contentKey, material, priority, enabled, timestamp, commands);
        }

        public String getId() {
            return id;
        }

        public String getTitleKey() {
            return titleKey;
        }

        public String getContentKey() {
            return contentKey;
        }

        public Material getMaterial() {
            return material;
        }

        public int getPriority() {
            return priority;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public List<String> getCommands() {
            return commands;
        }

        public boolean hasCommands() {
            return commands != null && !commands.isEmpty();
        }
    }

    /**
     * Inventory holder for announcement menu.
     */
    public static class AnnouncementMenuHolder implements InventoryHolder {
        private final int page;

        AnnouncementMenuHolder(int page) {
            this.page = page;
        }

        public int getPage() {
            return page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
