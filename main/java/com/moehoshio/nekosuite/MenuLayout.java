package com.moehoshio.nekosuite;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads configurable chest GUI layouts for menus to allow easy reordering/spacing.
 */
public class MenuLayout {

    private final YamlConfiguration config;

    private final WishLayout wishLayout;
    private final EventLayout eventLayout;
    private final ExpLayout expLayout;
    private final BuyLayout buyLayout;
    private final MailLayout mailLayout;
    private final StrategyGameLayout strategyGameLayout;
    private final NavigationLayout navigationLayout;
    private final HelpLayout helpLayout;
    private final GamesLayout gamesLayout;

    public MenuLayout(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "menu_layout.yml");
        if (!file.exists()) {
            plugin.saveResource("menu_layout.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        this.wishLayout = new WishLayout(config.getConfigurationSection("wish"));
        this.eventLayout = new EventLayout(config.getConfigurationSection("event"));
        this.expLayout = new ExpLayout(config.getConfigurationSection("exp"));
        this.buyLayout = new BuyLayout(config.getConfigurationSection("buy"));
        this.mailLayout = new MailLayout(config.getConfigurationSection("mail"));
        this.strategyGameLayout = new StrategyGameLayout(config.getConfigurationSection("strategy_game"));
        this.navigationLayout = new NavigationLayout(config.getConfigurationSection("navigation"));
        this.helpLayout = new HelpLayout(config.getConfigurationSection("help"));
        this.gamesLayout = new GamesLayout(config.getConfigurationSection("games"));
    }

    public WishLayout getWishLayout() {
        return wishLayout;
    }

    public EventLayout getEventLayout() {
        return eventLayout;
    }

    public ExpLayout getExpLayout() {
        return expLayout;
    }

    public BuyLayout getBuyLayout() {
        return buyLayout;
    }

    public MailLayout getMailLayout() {
        return mailLayout;
    }

    public StrategyGameLayout getStrategyGameLayout() {
        return strategyGameLayout;
    }

    public NavigationLayout getNavigationLayout() {
        return navigationLayout;
    }

    public HelpLayout getHelpLayout() {
        return helpLayout;
    }

    public GamesLayout getGamesLayout() {
        return gamesLayout;
    }

    public static class WishLayout {
        private final int size;
        private final List<Integer> itemSlots;
        private final int closeSlot;

        WishLayout(ConfigurationSection section) {
            this.size = section != null ? section.getInt("size", 27) : 27;
            this.itemSlots = safeIntList(section, "item_slots", defaultRange(0, size - 2));
            this.closeSlot = section != null ? section.getInt("close_slot", size - 1) : size - 1;
        }

        public int getSize() {
            return size;
        }

        public List<Integer> getItemSlots() {
            return itemSlots;
        }

        public int getCloseSlot() {
            return closeSlot;
        }
    }

    public static class EventLayout {
        private final int size;
        private final List<Integer> itemSlots;
        private final int closeSlot;

        EventLayout(ConfigurationSection section) {
            this.size = section != null ? section.getInt("size", 27) : 27;
            this.itemSlots = safeIntList(section, "item_slots", defaultRange(0, size - 2));
            this.closeSlot = section != null ? section.getInt("close_slot", size - 1) : size - 1;
        }

        public int getSize() {
            return size;
        }

        public List<Integer> getItemSlots() {
            return itemSlots;
        }

        public int getCloseSlot() {
            return closeSlot;
        }
    }

    public static class ExpLayout {
        private final int size;
        private final List<Integer> depositSlots;
        private final List<Integer> withdrawSlots;
        private final List<Integer> exchangeSlots;
        private final int balanceSlot;
        private final int closeSlot;

        ExpLayout(ConfigurationSection section) {
            this.size = section != null ? section.getInt("size", 27) : 27;
            this.depositSlots = safeIntList(section, "deposit_slots", defaultRange(0, 8));
            this.withdrawSlots = safeIntList(section, "withdraw_slots", defaultRange(9, 17));
            this.exchangeSlots = safeIntList(section, "exchange_slots", defaultRange(18, Math.min(24, size - 1)));
            this.balanceSlot = section != null ? section.getInt("balance_slot", Math.min(25, size - 1)) : Math.min(25, size - 1);
            this.closeSlot = section != null ? section.getInt("close_slot", Math.min(26, size - 1)) : Math.min(26, size - 1);
        }

        public int getSize() {
            return size;
        }

        public List<Integer> getDepositSlots() {
            return depositSlots;
        }

        public List<Integer> getWithdrawSlots() {
            return withdrawSlots;
        }

        public List<Integer> getExchangeSlots() {
            return exchangeSlots;
        }

        public int getBalanceSlot() {
            return balanceSlot;
        }

        public int getCloseSlot() {
            return closeSlot;
        }
    }

    public static class BuyLayout {
        private final int size;
        private final List<Integer> productSlots;
        private final int closeSlot;

        BuyLayout(ConfigurationSection section) {
            this.size = section != null ? section.getInt("size", 27) : 27;
            this.productSlots = safeIntList(section, "product_slots", defaultRange(0, size - 2));
            this.closeSlot = section != null ? section.getInt("close_slot", size - 1) : size - 1;
        }

        public int getSize() {
            return size;
        }

        public List<Integer> getProductSlots() {
            return productSlots;
        }

        public int getCloseSlot() {
            return closeSlot;
        }
    }

    public static class MailLayout {
        private final int size;
        private final List<Integer> itemSlots;
        private final int prevSlot;
        private final int nextSlot;
        private final int infoSlot;
        private final int closeSlot;

        MailLayout(ConfigurationSection section) {
            this.size = section != null ? section.getInt("size", 54) : 54;
            this.itemSlots = safeIntList(section, "item_slots", defaultRange(0, 44));
            this.prevSlot = section != null ? section.getInt("prev_slot", 45) : 45;
            this.nextSlot = section != null ? section.getInt("next_slot", 53) : 53;
            this.infoSlot = section != null ? section.getInt("info_slot", 49) : 49;
            this.closeSlot = section != null ? section.getInt("close_slot", 50) : 50;
        }

        public int getSize() {
            return size;
        }

        public List<Integer> getItemSlots() {
            return itemSlots;
        }

        public int getPrevSlot() {
            return prevSlot;
        }

        public int getNextSlot() {
            return nextSlot;
        }

        public int getInfoSlot() {
            return infoSlot;
        }

        public int getCloseSlot() {
            return closeSlot;
        }
    }

    /**
     * Represents a configurable menu item with material, slot, translation keys, and action.
     */
    public static class MenuItem {
        private final String id;
        private final int slot;
        private final String material;
        private final String nameKey;
        private final String loreKey;
        private final String action;
        private final String command;

        MenuItem(String id, ConfigurationSection section) {
            this.id = id;
            this.slot = section != null ? section.getInt("slot", 0) : 0;
            this.material = section != null ? section.getString("material", "STONE") : "STONE";
            this.nameKey = section != null ? section.getString("name_key", "") : "";
            this.loreKey = section != null ? section.getString("lore_key", "") : "";
            this.action = section != null ? section.getString("action", "") : "";
            this.command = section != null ? section.getString("command", "") : "";
        }

        public String getId() {
            return id;
        }

        public int getSlot() {
            return slot;
        }

        public String getMaterial() {
            return material;
        }

        public String getNameKey() {
            return nameKey;
        }

        public String getLoreKey() {
            return loreKey;
        }

        public String getAction() {
            return action;
        }

        public String getCommand() {
            return command;
        }

        public boolean hasAction() {
            return action != null && !action.isEmpty();
        }

        public boolean hasCommand() {
            return command != null && !command.isEmpty();
        }
    }

    /**
     * Navigation menu layout configuration.
     */
    public static class NavigationLayout {
        private final int size;
        private final String titleKey;
        private final int closeSlot;
        private final Map<String, MenuItem> items;

        NavigationLayout(ConfigurationSection section) {
            this.size = section != null ? section.getInt("size", 27) : 27;
            this.titleKey = section != null ? section.getString("title_key", "navigation.title") : "navigation.title";
            this.closeSlot = section != null ? section.getInt("close_slot", 26) : 26;
            this.items = new HashMap<String, MenuItem>();
            if (section != null) {
                ConfigurationSection itemsSection = section.getConfigurationSection("items");
                if (itemsSection != null) {
                    for (String key : itemsSection.getKeys(false)) {
                        ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                        if (itemSection != null) {
                            items.put(key, new MenuItem(key, itemSection));
                        }
                    }
                }
            }
        }

        public int getSize() {
            return size;
        }

        public String getTitleKey() {
            return titleKey;
        }

        public int getCloseSlot() {
            return closeSlot;
        }

        public Map<String, MenuItem> getItems() {
            return Collections.unmodifiableMap(items);
        }
    }

    /**
     * Help menu layout configuration.
     */
    public static class HelpLayout {
        private final int size;
        private final String titleKey;
        private final int closeSlot;
        private final Map<String, MenuItem> items;

        HelpLayout(ConfigurationSection section) {
            this.size = section != null ? section.getInt("size", 27) : 27;
            this.titleKey = section != null ? section.getString("title_key", "help.title") : "help.title";
            this.closeSlot = section != null ? section.getInt("close_slot", 26) : 26;
            this.items = new HashMap<String, MenuItem>();
            if (section != null) {
                ConfigurationSection itemsSection = section.getConfigurationSection("items");
                if (itemsSection != null) {
                    for (String key : itemsSection.getKeys(false)) {
                        ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                        if (itemSection != null) {
                            items.put(key, new MenuItem(key, itemSection));
                        }
                    }
                }
            }
        }

        public int getSize() {
            return size;
        }

        public String getTitleKey() {
            return titleKey;
        }

        public int getCloseSlot() {
            return closeSlot;
        }

        public Map<String, MenuItem> getItems() {
            return Collections.unmodifiableMap(items);
        }
    }

    private static List<Integer> safeIntList(ConfigurationSection section, String path, List<Integer> fallback) {
        if (section == null) {
            return fallback;
        }
        List<Integer> list = new ArrayList<Integer>();
        List<?> raw = section.getList(path);
        if (raw != null) {
            for (Object o : raw) {
                if (o == null) {
                    continue;
                }
                try {
                    int v = Integer.parseInt(o.toString());
                    if (v >= 0) {
                        list.add(v);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (list.isEmpty()) {
            list.addAll(fallback);
        }
        return Collections.unmodifiableList(list);
    }

    private static List<Integer> defaultRange(int start, int endInclusive) {
        List<Integer> list = new ArrayList<Integer>();
        for (int i = start; i <= endInclusive; i++) {
            if (i < 0) {
                continue;
            }
            list.add(i);
        }
        return list;
    }

    public static class StrategyGameLayout {
        private final int size;
        private final int statusSlot;
        private final int adventureSlot;
        private final int battleSlot;
        private final int shopSlot;
        private final int endGameSlot;
        private final int closeSlot;

        StrategyGameLayout(ConfigurationSection section) {
            this.size = section != null ? section.getInt("size", 27) : 27;
            this.statusSlot = section != null ? section.getInt("status_slot", 4) : 4;
            this.adventureSlot = section != null ? section.getInt("adventure_slot", 11) : 11;
            this.battleSlot = section != null ? section.getInt("battle_slot", 13) : 13;
            this.shopSlot = section != null ? section.getInt("shop_slot", 15) : 15;
            this.endGameSlot = section != null ? section.getInt("end_game_slot", 22) : 22;
            this.closeSlot = section != null ? section.getInt("close_slot", 26) : 26;
        }

        public int getSize() {
            return size;
        }

        public int getStatusSlot() {
            return statusSlot;
        }

        public int getAdventureSlot() {
            return adventureSlot;
        }

        public int getBattleSlot() {
            return battleSlot;
        }

        public int getShopSlot() {
            return shopSlot;
        }

        public int getEndGameSlot() {
            return endGameSlot;
        }

        public int getCloseSlot() {
            return closeSlot;
        }
    }

    /**
     * Games menu layout configuration.
     */
    public static class GamesLayout {
        private final int size;
        private final String titleKey;
        private final int closeSlot;
        private final Map<String, MenuItem> items;

        GamesLayout(ConfigurationSection section) {
            this.size = section != null ? section.getInt("size", 27) : 27;
            this.titleKey = section != null ? section.getString("title_key", "games.title") : "games.title";
            this.closeSlot = section != null ? section.getInt("close_slot", 26) : 26;
            this.items = new HashMap<String, MenuItem>();
            if (section != null) {
                ConfigurationSection itemsSection = section.getConfigurationSection("items");
                if (itemsSection != null) {
                    for (String key : itemsSection.getKeys(false)) {
                        ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                        if (itemSection != null) {
                            items.put(key, new MenuItem(key, itemSection));
                        }
                    }
                }
            }
        }

        public int getSize() {
            return size;
        }

        public String getTitleKey() {
            return titleKey;
        }

        public int getCloseSlot() {
            return closeSlot;
        }

        public Map<String, MenuItem> getItems() {
            return Collections.unmodifiableMap(items);
        }
    }
}
