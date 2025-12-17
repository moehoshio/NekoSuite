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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Strategy Game Module - A text-based adventure game with events, battles, and trading.
 * 
 * Background Story:
 * In a mystical realm called Nekoria, an ancient darkness threatens the land.
 * Players become brave adventurers who must explore, make choices, fight monsters,
 * and trade with merchants to gather resources and save the kingdom.
 * 
 * Game features:
 * - Virtual currency (Gold Coins) used within the game
 * - Events with multiple choices that affect outcomes
 * - Simulated card-like battles
 * - Trading with NPC merchants
 * - Real rewards given at game end based on progress
 */
public class StrategyGameManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final MenuLayout menuLayout;
    private final File storageDir;
    private final Random random = new Random();

    // Default values for game configuration
    private static final int DEFAULT_STARTING_GOLD = 100;
    private static final int DEFAULT_STARTING_HEALTH = 100;
    private static final int DEFAULT_MAX_STAGES = 10;
    private static final int DEFAULT_ENEMY_POWER = 30;
    private static final int DEFAULT_ENEMY_DAMAGE = 10;

    // Game configuration
    private int startingGold = DEFAULT_STARTING_GOLD;
    private int startingHealth = DEFAULT_STARTING_HEALTH;
    private int maxStages = DEFAULT_MAX_STAGES;
    private int startingAttack = 10;
    private int startingDefense = 5;
    private int startingMagic = 20;
    private final List<GameEvent> gameEvents = new ArrayList<GameEvent>();
    private final List<ShopItem> shopItems = new ArrayList<ShopItem>();
    private final List<BattleEnemy> enemies = new ArrayList<BattleEnemy>();
    private final List<EndReward> endRewards = new ArrayList<EndReward>();
    private final List<Equipment> equipments = new ArrayList<Equipment>();

    // Active game sessions
    private final Map<String, GameSession> activeSessions = new HashMap<String, GameSession>();

    public StrategyGameManager(JavaPlugin plugin, Messages messages, File configFile, MenuLayout menuLayout) {
        this.plugin = plugin;
        this.messages = messages;
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
        startingGold = config.getInt("game.starting_gold", 100);
        startingHealth = config.getInt("game.starting_health", 100);
        maxStages = config.getInt("game.max_stages", 10);
        startingAttack = config.getInt("game.starting_attack", 10);
        startingDefense = config.getInt("game.starting_defense", 5);
        startingMagic = config.getInt("game.starting_magic", 20);

        // Load events
        gameEvents.clear();
        ConfigurationSection eventSection = config.getConfigurationSection("events");
        if (eventSection != null) {
            for (String key : eventSection.getKeys(false)) {
                ConfigurationSection es = eventSection.getConfigurationSection(key);
                if (es != null) {
                    GameEvent event = GameEvent.fromSection(key, es);
                    if (event != null) {
                        gameEvents.add(event);
                    }
                }
            }
        }

        // Load shop items
        shopItems.clear();
        List<Map<?, ?>> shopList = config.getMapList("shop.items");
        for (Map<?, ?> raw : shopList) {
            ShopItem item = ShopItem.fromMap(raw);
            if (item != null) {
                shopItems.add(item);
            }
        }

        // Load equipment
        equipments.clear();
        List<Map<?, ?>> equipList = config.getMapList("equipment.items");
        for (Map<?, ?> raw : equipList) {
            Equipment eq = Equipment.fromMap(raw);
            if (eq != null) {
                equipments.add(eq);
            }
        }

        // Load enemies
        enemies.clear();
        List<Map<?, ?>> enemyList = config.getMapList("battles.enemies");
        for (Map<?, ?> raw : enemyList) {
            BattleEnemy enemy = BattleEnemy.fromMap(raw);
            if (enemy != null) {
                enemies.add(enemy);
            }
        }

        // Load end rewards
        endRewards.clear();
        List<Map<?, ?>> rewardList = config.getMapList("end_rewards");
        for (Map<?, ?> raw : rewardList) {
            EndReward reward = EndReward.fromMap(raw);
            if (reward != null) {
                endRewards.add(reward);
            }
        }
    }

    // ============ Public API ============

    /**
     * Start a new game session for a player.
     */
    public void startGame(Player player) {
        String playerName = player.getName();
        if (activeSessions.containsKey(playerName)) {
            player.sendMessage(messages.format(player, "sgame.already_in_game"));
            return;
        }

        GameSession session = new GameSession(playerName, startingGold, startingHealth);
        // Initialize combat stats
        session.setAttack(startingAttack);
        session.setDefense(startingDefense);
        session.setMagic(startingMagic);
        session.setMaxMagic(startingMagic);
        
        activeSessions.put(playerName, session);
        saveSession(session);

        player.sendMessage(messages.format(player, "sgame.game_started"));
        player.sendMessage(messages.format(player, "sgame.story_intro"));
        openMainMenu(player);
    }

    /**
     * Continue an existing game or show status.
     */
    public void continueGame(Player player) {
        String playerName = player.getName();
        GameSession session = getOrLoadSession(playerName);
        if (session == null) {
            player.sendMessage(messages.format(player, "sgame.no_active_game"));
            return;
        }
        if (session.isEnded()) {
            player.sendMessage(messages.format(player, "sgame.game_already_ended"));
            return;
        }
        openMainMenu(player);
    }

    /**
     * Show player's game status.
     */
    public void showStatus(Player player) {
        String playerName = player.getName();
        GameSession session = getOrLoadSession(playerName);
        if (session == null) {
            player.sendMessage(messages.format(player, "sgame.no_active_game"));
            return;
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put("gold", String.valueOf(session.getGold()));
        map.put("health", String.valueOf(session.getHealth()));
        map.put("stage", String.valueOf(session.getCurrentStage()));
        map.put("max_stage", String.valueOf(maxStages));
        map.put("victories", String.valueOf(session.getBattleVictories()));
        player.sendMessage(messages.format(player, "sgame.status", map));
    }

    /**
     * End the game and claim rewards.
     */
    public void endGame(Player player) {
        String playerName = player.getName();
        GameSession session = getOrLoadSession(playerName);
        if (session == null) {
            player.sendMessage(messages.format(player, "sgame.no_active_game"));
            return;
        }
        if (session.isEnded()) {
            player.sendMessage(messages.format(player, "sgame.game_already_ended"));
            return;
        }

        session.setEnded(true);
        saveSession(session);

        // Calculate and give rewards
        List<String> rewardNames = grantEndRewards(player, session);
        
        Map<String, String> map = new HashMap<String, String>();
        map.put("rewards", rewardNames.isEmpty() ? messages.format(player, "sgame.no_rewards") : String.join(", ", rewardNames));
        player.sendMessage(messages.format(player, "sgame.game_ended", map));

        // Clear the session
        activeSessions.remove(playerName);
        clearSessionFile(playerName);
    }

    /**
     * Abandon current game without rewards.
     */
    public void abandonGame(Player player) {
        String playerName = player.getName();
        GameSession session = getOrLoadSession(playerName);
        if (session == null) {
            player.sendMessage(messages.format(player, "sgame.no_active_game"));
            return;
        }

        activeSessions.remove(playerName);
        clearSessionFile(playerName);
        player.sendMessage(messages.format(player, "sgame.game_abandoned"));
    }

    // ============ Menu Operations ============

    /**
     * Open the main game menu - now directly shows event selection.
     * Players must choose from the random events/battles/shops displayed.
     */
    public void openMainMenu(Player player) {
        GameSession session = getOrLoadSession(player.getName());
        if (session == null) {
            player.sendMessage(messages.format(player, "sgame.no_active_game"));
            return;
        }
        if (session.isEnded()) {
            player.sendMessage(messages.format(player, "sgame.game_already_ended"));
            return;
        }

        // Check if player has completed enough stages
        if (session.getCurrentStage() >= maxStages) {
            openVictoryMenu(player, session);
            return;
        }

        // Directly show event selection - no more separate main menu
        openEventSelectionMenu(player);
    }

    /**
     * Open victory menu when player completes all stages.
     */
    private void openVictoryMenu(Player player, GameSession session) {
        MenuLayout.StrategyGameLayout layout = menuLayout.getStrategyGameLayout();
        String title = messages.format(player, "menu.sgame.victory_title");
        Inventory inv = Bukkit.createInventory(new StrategyGameMenuHolder(MenuType.MAIN), layout.getSize(), title);

        // Status display
        Map<String, String> statusMap = new HashMap<String, String>();
        statusMap.put("gold", String.valueOf(session.getGold()));
        statusMap.put("health", String.valueOf(session.getHealth()));
        statusMap.put("stage", String.valueOf(session.getCurrentStage()));
        statusMap.put("max_stage", String.valueOf(maxStages));
        statusMap.put("victories", String.valueOf(session.getVictories()));
        
        ItemStack statusItem = createItem(Material.BOOK, 
            messages.format(player, "menu.sgame.status_title", statusMap),
            new String[]{
                messages.format(player, "menu.sgame.gold_lore", statusMap),
                messages.format(player, "menu.sgame.health_lore", statusMap),
                messages.format(player, "menu.sgame.stage_lore", statusMap)
            });
        safeSet(inv, 4, statusItem);

        // End game button - claim rewards
        ItemStack endItem = createItem(Material.NETHER_STAR,
            messages.format(player, "menu.sgame.end_game_title"),
            new String[]{
                messages.format(player, "menu.sgame.end_game_lore"),
                "ID:end_game"
            });
        safeSet(inv, 13, endItem);

        // Equipment button - can still manage equipment before ending
        ItemStack equipItem = createItem(Material.DIAMOND_CHESTPLATE,
            messages.format(player, "menu.sgame.equipment_title"),
            new String[]{
                messages.format(player, "menu.sgame.equipment_lore"),
                "ID:equipment"
            });
        safeSet(inv, 11, equipItem);

        player.openInventory(inv);
    }

    /**
     * Open event selection menu - show multiple events for the player to choose from.
     */
    public void openEventSelectionMenu(Player player) {
        GameSession session = getOrLoadSession(player.getName());
        if (session == null || session.isEnded()) {
            return;
        }

        if (gameEvents.isEmpty()) {
            player.sendMessage(messages.format(player, "sgame.no_events_available"));
            openMainMenu(player);
            return;
        }

        MenuLayout.StrategyGameLayout layout = menuLayout.getStrategyGameLayout();
        String title = messages.format(player, "menu.sgame.event_selection_title");
        Inventory inv = Bukkit.createInventory(new StrategyGameMenuHolder(MenuType.EVENT_SELECTION), layout.getSize(), title);

        // Status display with combat stats
        Map<String, String> statusMap = new HashMap<String, String>();
        statusMap.put("gold", String.valueOf(session.getGold()));
        statusMap.put("health", String.valueOf(session.getHealth()));
        statusMap.put("max_health", String.valueOf(session.getMaxHealth()));
        statusMap.put("attack", String.valueOf(session.getAttack() + getEquipmentAttackBonus(session)));
        statusMap.put("defense", String.valueOf(session.getDefense() + getEquipmentDefenseBonus(session)));
        statusMap.put("magic", String.valueOf(session.getMagic()));
        statusMap.put("stage", String.valueOf(session.getCurrentStage()));
        statusMap.put("max_stage", String.valueOf(maxStages));
        
        ItemStack statusItem = createItem(Material.BOOK, 
            messages.format(player, "menu.sgame.status_title", statusMap),
            new String[]{
                messages.format(player, "menu.sgame.gold_lore", statusMap),
                messages.format(player, "menu.sgame.health_lore", statusMap),
                messages.format(player, "menu.sgame.combat_stats_lore", statusMap),
                messages.format(player, "menu.sgame.stage_lore", statusMap)
            });
        safeSet(inv, 4, statusItem);

        // Use weighted selection for events based on history
        List<GameEvent> selectedEvents = selectEventsForStage(session, 3);
        
        int[] eventSlots = {11, 13, 15};
        for (int i = 0; i < selectedEvents.size(); i++) {
            GameEvent event = selectedEvents.get(i);
            
            // Check if event has requirements and if player meets them
            boolean meetsRequirements = true;
            List<String> loreList = new ArrayList<String>();
            loreList.addAll(event.getDescription());
            
            // Show event type indicator using i18n
            String eventType = event.getEventType();
            Map<String, String> typeMap = new HashMap<String, String>();
            if ("battle".equals(eventType)) {
                typeMap.put("type", messages.format(player, "menu.sgame.type_battle"));
            } else if ("shop".equals(eventType)) {
                typeMap.put("type", messages.format(player, "menu.sgame.type_shop"));
            } else {
                typeMap.put("type", messages.format(player, "menu.sgame.type_story"));
            }
            loreList.add(messages.format(player, "menu.sgame.event_type_lore", typeMap));
            
            if (event.hasRequirement()) {
                EventRequirement req = event.getRequirement();
                meetsRequirements = req.checkRequirements(session);
                
                // Show requirement info using i18n
                if (req.hasItemRequirement()) {
                    boolean hasItem = session.hasItem(req.getRequiredItem(), req.getRequiredItemAmount());
                    Map<String, String> itemReqMap = new HashMap<String, String>();
                    itemReqMap.put("item", req.getRequiredItem());
                    itemReqMap.put("amount", String.valueOf(req.getRequiredItemAmount()));
                    String reqText = messages.format(player, "menu.sgame.require_item", itemReqMap);
                    loreList.add((hasItem ? "&a✔ " : "&c✖ ") + reqText);
                }
                if (req.hasGoldRequirement()) {
                    boolean hasGold = session.getGold() >= req.getRequiredGold();
                    Map<String, String> goldReqMap = new HashMap<String, String>();
                    goldReqMap.put("gold", String.valueOf(req.getRequiredGold()));
                    String reqText = messages.format(player, "menu.sgame.require_gold", goldReqMap);
                    loreList.add((hasGold ? "&a✔ " : "&c✖ ") + reqText);
                }
                if (req.hasGoldCost()) {
                    boolean canPay = session.getGold() >= req.getGoldCost();
                    Map<String, String> costMap = new HashMap<String, String>();
                    costMap.put("gold", String.valueOf(req.getGoldCost()));
                    String costText = messages.format(player, "menu.sgame.cost_gold", costMap);
                    loreList.add((canPay ? "&e" : "&c") + costText);
                }
            }
            
            if (meetsRequirements) {
                loreList.add(messages.format(player, "menu.sgame.click_enter_event"));
            } else {
                loreList.add(messages.format(player, "menu.sgame.cannot_enter_event"));
            }
            loreList.add("ID:select_event_" + event.getId());
            
            Material eventMaterial = meetsRequirements ? Material.WRITABLE_BOOK : Material.BARRIER;
            ItemStack eventItem = createItem(eventMaterial,
                event.getName(),
                loreList.toArray(new String[0]));
            safeSet(inv, eventSlots[i], eventItem);
        }

        // Equipment button - players can manage equipment before choosing
        ItemStack equipItem = createItem(Material.DIAMOND_CHESTPLATE,
            messages.format(player, "menu.sgame.equipment_title"),
            new String[]{
                messages.format(player, "menu.sgame.equipment_lore"),
                "ID:equipment"
            });
        safeSet(inv, layout.getCloseSlot(), equipItem);

        player.openInventory(inv);
    }

    /**
     * Open event detail menu - show the choices for a specific event.
     */
    public void openEventMenu(Player player) {
        GameSession session = getOrLoadSession(player.getName());
        if (session == null || session.isEnded()) {
            return;
        }

        String eventId = session.getCurrentEventId();
        if (eventId == null) {
            openEventSelectionMenu(player);
            return;
        }

        GameEvent event = findEvent(eventId);
        if (event == null) {
            player.sendMessage(messages.format(player, "sgame.no_events_available"));
            session.setCurrentEventId(null);
            saveSession(session);
            openMainMenu(player);
            return;
        }

        MenuLayout.StrategyGameLayout layout = menuLayout.getStrategyGameLayout();
        String title = messages.format(player, "menu.sgame.event_title");
        Inventory inv = Bukkit.createInventory(new StrategyGameMenuHolder(MenuType.EVENT), layout.getSize(), title);

        // Status display with current gold/health
        Map<String, String> statusMap = new HashMap<String, String>();
        statusMap.put("gold", String.valueOf(session.getGold()));
        statusMap.put("health", String.valueOf(session.getHealth()));
        
        ItemStack statusItem = createItem(Material.GOLD_NUGGET, 
            messages.format(player, "menu.sgame.your_gold", statusMap),
            new String[]{
                messages.format(player, "menu.sgame.health_lore", statusMap)
            });
        safeSet(inv, 0, statusItem);

        // Event description
        ItemStack descItem = createItem(Material.PAPER,
            event.getName(),
            event.getDescription().toArray(new String[0]));
        safeSet(inv, 4, descItem);

        // Choice buttons - no spoilers about outcomes, only show requirements
        List<EventChoice> choices = event.getChoices();
        int[] choiceSlots = {11, 13, 15};
        for (int i = 0; i < choices.size() && i < choiceSlots.length; i++) {
            EventChoice choice = choices.get(i);
            List<String> loreList = new ArrayList<String>();
            
            // Only show item cost if required (player needs to know what they're consuming)
            if (choice.getItemCost() != null) {
                boolean hasItem = session.hasItem(choice.getItemCost(), choice.getItemCostAmount());
                Map<String, String> costMap = new HashMap<String, String>();
                costMap.put("item", choice.getItemCost());
                costMap.put("amount", String.valueOf(choice.getItemCostAmount()));
                String costText = messages.format(player, "menu.sgame.choice_item_cost", costMap);
                loreList.add((hasItem ? "&e" : "&c") + costText);
            }
            
            // Check choice requirements and show them
            boolean meetsReq = true;
            if (choice.hasRequirement()) {
                EventRequirement req = choice.getRequirement();
                meetsReq = req.checkRequirements(session);
                if (req.hasItemRequirement()) {
                    boolean hasItem = session.hasItem(req.getRequiredItem(), req.getRequiredItemAmount());
                    Map<String, String> itemReqMap = new HashMap<String, String>();
                    itemReqMap.put("item", req.getRequiredItem());
                    itemReqMap.put("amount", String.valueOf(req.getRequiredItemAmount()));
                    String reqText = messages.format(player, "menu.sgame.require_item", itemReqMap);
                    loreList.add((hasItem ? "&a✔ " : "&c✖ ") + reqText);
                }
                if (req.hasGoldRequirement()) {
                    boolean hasGold = session.getGold() >= req.getRequiredGold();
                    Map<String, String> goldReqMap = new HashMap<String, String>();
                    goldReqMap.put("gold", String.valueOf(req.getRequiredGold()));
                    String reqText = messages.format(player, "menu.sgame.require_gold", goldReqMap);
                    loreList.add((hasGold ? "&a✔ " : "&c✖ ") + reqText);
                }
                
                if (!meetsReq && choice.hasAltResult()) {
                    loreList.add(messages.format(player, "menu.sgame.alt_result_hint"));
                } else if (!meetsReq) {
                    loreList.add(messages.format(player, "menu.sgame.requirement_not_met"));
                }
            }
            
            loreList.add("");
            loreList.add(messages.format(player, "menu.sgame.make_your_choice"));
            loreList.add("ID:choice_" + i);
            
            Material choiceMat = meetsReq ? Material.OAK_SIGN : (choice.hasAltResult() ? Material.BIRCH_SIGN : Material.OAK_SIGN);
            ItemStack choiceItem = createItem(choiceMat,
                choice.getText(),
                loreList.toArray(new String[0]));
            safeSet(inv, choiceSlots[i], choiceItem);
        }

        // No back button - players must make a choice
        // This is intentional to prevent exploiting by backing out

        player.openInventory(inv);
    }

    public void openBattleMenu(Player player) {
        GameSession session = getOrLoadSession(player.getName());
        if (session == null || session.isEnded()) {
            return;
        }

        if (enemies.isEmpty()) {
            player.sendMessage(messages.format(player, "sgame.no_enemies"));
            openMainMenu(player);
            return;
        }

        // Pick a random enemy based on stage
        BattleEnemy enemy = pickEnemy(session.getCurrentStage());
        session.setCurrentEnemyId(enemy.getId());
        saveSession(session);

        MenuLayout.StrategyGameLayout layout = menuLayout.getStrategyGameLayout();
        String title = messages.format(player, "menu.sgame.battle_menu_title");
        Inventory inv = Bukkit.createInventory(new StrategyGameMenuHolder(MenuType.BATTLE), layout.getSize(), title);

        // Enemy display with combat stats
        Map<String, String> enemyMap = new HashMap<String, String>();
        enemyMap.put("enemy", enemy.getName());
        enemyMap.put("power", String.valueOf(enemy.getPower()));
        enemyMap.put("attack", String.valueOf(enemy.getAttack()));
        enemyMap.put("defense", String.valueOf(enemy.getDefense()));
        enemyMap.put("health", String.valueOf(enemy.getHealth()));
        enemyMap.put("magic", String.valueOf(enemy.getMagic()));
        
        ItemStack enemyItem = createItem(Material.ZOMBIE_HEAD,
            messages.format(player, "menu.sgame.enemy_title", enemyMap),
            new String[]{
                messages.format(player, "menu.sgame.enemy_stats_lore", enemyMap),
                "&7" + enemy.getDescription()
            });
        safeSet(inv, 4, enemyItem);

        // Fight button
        ItemStack fightItem = createItem(Material.DIAMOND_SWORD,
            messages.format(player, "menu.sgame.fight_button"),
            new String[]{
                messages.format(player, "menu.sgame.fight_lore"),
                "ID:fight"
            });
        safeSet(inv, 11, fightItem);

        // Flee button - players can flee but it costs them
        ItemStack fleeItem = createItem(Material.FEATHER,
            messages.format(player, "menu.sgame.flee_button"),
            new String[]{
                messages.format(player, "menu.sgame.flee_lore"),
                "ID:flee"
            });
        safeSet(inv, 15, fleeItem);

        // No back button - must fight or flee

        player.openInventory(inv);
    }

    public void openShopMenu(Player player) {
        GameSession session = getOrLoadSession(player.getName());
        if (session == null || session.isEnded()) {
            return;
        }

        if (shopItems.isEmpty()) {
            player.sendMessage(messages.format(player, "sgame.shop_empty"));
            openMainMenu(player);
            return;
        }

        MenuLayout.StrategyGameLayout layout = menuLayout.getStrategyGameLayout();
        String title = messages.format(player, "menu.sgame.shop_menu_title");
        Inventory inv = Bukkit.createInventory(new StrategyGameMenuHolder(MenuType.SHOP), layout.getSize(), title);

        // Display gold and health status
        Map<String, String> statusMap = new HashMap<String, String>();
        statusMap.put("gold", String.valueOf(session.getGold()));
        statusMap.put("health", String.valueOf(session.getHealth()));
        ItemStack goldItem = createItem(Material.GOLD_NUGGET,
            messages.format(player, "menu.sgame.your_gold", statusMap),
            new String[]{
                messages.format(player, "menu.sgame.health_lore", statusMap)
            });
        safeSet(inv, 4, goldItem);

        // Shop items
        int[] itemSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < shopItems.size() && i < itemSlots.length; i++) {
            ShopItem item = shopItems.get(i);
            Map<String, String> itemMap = new HashMap<String, String>();
            itemMap.put("price", String.valueOf(item.getPrice()));
            itemMap.put("effect", item.getEffectDescription());
            
            boolean canAfford = session.getGold() >= item.getPrice();
            String priceColor = canAfford ? "&a" : "&c";
            
            ItemStack shopItemStack = createItem(item.getMaterial(),
                item.getName(),
                new String[]{
                    priceColor + messages.format(player, "menu.sgame.item_price_lore", itemMap),
                    "&7" + item.getEffectDescription(),
                    canAfford ? messages.format(player, "menu.sgame.click_to_buy") : messages.format(player, "menu.sgame.not_enough_gold"),
                    "ID:buy_" + item.getId()
                });
            safeSet(inv, itemSlots[i], shopItemStack);
        }

        // Leave shop button - proceed to next stage after shopping
        // Shop events count as completed, advancing the stage
        ItemStack leaveItem = createItem(Material.LIME_WOOL,
            messages.format(player, "menu.sgame.shop_leave"),
            new String[]{
                messages.format(player, "menu.sgame.shop_leave_lore"),
                "ID:shop_leave"
            });
        safeSet(inv, 22, leaveItem);

        // No back button - must leave shop properly

        player.openInventory(inv);
    }

    /**
     * Open the equipment management menu.
     */
    public void openEquipmentMenu(Player player) {
        GameSession session = getOrLoadSession(player.getName());
        if (session == null || session.isEnded()) {
            return;
        }

        MenuLayout.StrategyGameLayout layout = menuLayout.getStrategyGameLayout();
        String title = messages.format(player, "menu.sgame.equipment_menu_title");
        Inventory inv = Bukkit.createInventory(new StrategyGameMenuHolder(MenuType.EQUIPMENT), layout.getSize(), title);

        // Status display
        Map<String, String> statusMap = new HashMap<String, String>();
        statusMap.put("gold", String.valueOf(session.getGold()));
        statusMap.put("attack", String.valueOf(session.getAttack() + getEquipmentAttackBonus(session)));
        statusMap.put("defense", String.valueOf(session.getDefense() + getEquipmentDefenseBonus(session)));
        statusMap.put("health", String.valueOf(session.getHealth()));
        statusMap.put("max_health", String.valueOf(session.getMaxHealth()));
        statusMap.put("magic", String.valueOf(session.getMagic()));
        
        ItemStack statusItem = createItem(Material.BOOK, 
            messages.format(player, "menu.sgame.status_title", statusMap),
            new String[]{
                messages.format(player, "menu.sgame.combat_stats_lore", statusMap),
                messages.format(player, "menu.sgame.health_lore", statusMap)
            });
        safeSet(inv, 4, statusItem);

        // Current equipment slots
        // Weapon slot
        String weaponId = session.getEquippedWeapon();
        Equipment weapon = weaponId != null ? findEquipment(weaponId) : null;
        Map<String, String> weaponMap = new HashMap<String, String>();
        if (weapon != null) {
            weaponMap.put("name", weapon.getName());
            weaponMap.put("attack", String.valueOf(weapon.getAttackBonus()));
            weaponMap.put("defense", String.valueOf(weapon.getDefenseBonus()));
        }
        ItemStack weaponSlot = createItem(weapon != null ? weapon.getMaterial() : Material.IRON_SWORD,
            messages.format(player, "menu.sgame.equip_slot_weapon"),
            new String[]{
                weapon != null ? messages.format(player, "menu.sgame.equipped_item", weaponMap) : messages.format(player, "menu.sgame.equip_empty"),
                weapon != null ? messages.format(player, "menu.sgame.equip_weapon_stats", weaponMap) : "",
                messages.format(player, "menu.sgame.equip_click_to_change"),
                "ID:slot_weapon"
            });
        safeSet(inv, 10, weaponSlot);

        // Armor slot
        String armorId = session.getEquippedArmor();
        Equipment armor = armorId != null ? findEquipment(armorId) : null;
        Map<String, String> armorMap = new HashMap<String, String>();
        if (armor != null) {
            armorMap.put("name", armor.getName());
            armorMap.put("defense", String.valueOf(armor.getDefenseBonus()));
            armorMap.put("health", String.valueOf(armor.getHealthBonus()));
        }
        ItemStack armorSlot = createItem(armor != null ? armor.getMaterial() : Material.LEATHER_CHESTPLATE,
            messages.format(player, "menu.sgame.equip_slot_armor"),
            new String[]{
                armor != null ? messages.format(player, "menu.sgame.equipped_item", armorMap) : messages.format(player, "menu.sgame.equip_empty"),
                armor != null ? messages.format(player, "menu.sgame.equip_armor_stats", armorMap) : "",
                messages.format(player, "menu.sgame.equip_click_to_change"),
                "ID:slot_armor"
            });
        safeSet(inv, 13, armorSlot);

        // Accessory slot
        String accessoryId = session.getEquippedAccessory();
        Equipment accessory = accessoryId != null ? findEquipment(accessoryId) : null;
        Map<String, String> accessoryMap = new HashMap<String, String>();
        if (accessory != null) {
            accessoryMap.put("name", accessory.getName());
        }
        ItemStack accessorySlot = createItem(accessory != null ? accessory.getMaterial() : Material.GOLD_INGOT,
            messages.format(player, "menu.sgame.equip_slot_accessory"),
            new String[]{
                accessory != null ? messages.format(player, "menu.sgame.equipped_item", accessoryMap) : messages.format(player, "menu.sgame.equip_empty"),
                accessory != null ? messages.format(player, "menu.sgame.equip_accessory_stats") : "",
                messages.format(player, "menu.sgame.equip_click_to_change"),
                "ID:slot_accessory"
            });
        safeSet(inv, 16, accessorySlot);

        // Available equipment to buy (bottom row)
        int[] equipSlots = {28, 29, 30, 31, 32, 33, 34};
        int slotIndex = 0;
        for (Equipment eq : equipments) {
            if (slotIndex >= equipSlots.length) break;
            
            boolean canAfford = session.getGold() >= eq.getPrice();
            boolean isEquipped = eq.getId().equals(weaponId) || eq.getId().equals(armorId) || eq.getId().equals(accessoryId);
            
            List<String> lore = new ArrayList<String>();
            Map<String, String> eqMap = new HashMap<String, String>();
            eqMap.put("price", String.valueOf(eq.getPrice()));
            
            // Slot type
            if ("weapon".equals(eq.getSlot())) {
                eqMap.put("type", messages.format(player, "menu.sgame.type_weapon"));
            } else if ("armor".equals(eq.getSlot())) {
                eqMap.put("type", messages.format(player, "menu.sgame.type_armor"));
            } else {
                eqMap.put("type", messages.format(player, "menu.sgame.type_accessory"));
            }
            lore.add(messages.format(player, "menu.sgame.equip_type_lore", eqMap));
            
            // Stats
            if (eq.getAttackBonus() > 0) {
                Map<String, String> statMap = new HashMap<String, String>();
                statMap.put("value", String.valueOf(eq.getAttackBonus()));
                lore.add(messages.format(player, "menu.sgame.equip_attack_bonus", statMap));
            }
            if (eq.getDefenseBonus() > 0) {
                Map<String, String> statMap = new HashMap<String, String>();
                statMap.put("value", String.valueOf(eq.getDefenseBonus()));
                lore.add(messages.format(player, "menu.sgame.equip_defense_bonus", statMap));
            }
            if (eq.getHealthBonus() > 0) {
                Map<String, String> statMap = new HashMap<String, String>();
                statMap.put("value", String.valueOf(eq.getHealthBonus()));
                lore.add(messages.format(player, "menu.sgame.equip_health_bonus", statMap));
            }
            if (eq.getMagicBonus() > 0) {
                Map<String, String> statMap = new HashMap<String, String>();
                statMap.put("value", String.valueOf(eq.getMagicBonus()));
                lore.add(messages.format(player, "menu.sgame.equip_magic_bonus", statMap));
            }
            lore.add(messages.format(player, "menu.sgame.equip_price_lore", eqMap));
            
            if (isEquipped) {
                lore.add(messages.format(player, "menu.sgame.already_equipped"));
            } else if (canAfford) {
                lore.add(messages.format(player, "menu.sgame.click_to_buy_equip"));
            } else {
                lore.add(messages.format(player, "menu.sgame.not_enough_gold"));
            }
            lore.add("ID:buy_equip_" + eq.getId());
            
            ItemStack eqItem = createItem(eq.getMaterial(), eq.getName(), lore.toArray(new String[0]));
            safeSet(inv, equipSlots[slotIndex], eqItem);
            slotIndex++;
        }

        // Back button
        ItemStack backItem = createItem(Material.ARROW,
            messages.format(player, "menu.sgame.back"),
            new String[]{"ID:back"});
        safeSet(inv, layout.getCloseSlot(), backItem);

        player.openInventory(inv);
    }

    // ============ Menu Click Handler ============

    public void handleMenuClick(Player player, ItemStack clicked, StrategyGameMenuHolder holder) {
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

        MenuType menuType = holder.getMenuType();
        GameSession session = getOrLoadSession(player.getName());
        if (session == null || session.isEnded()) {
            player.closeInventory();
            return;
        }

        switch (menuType) {
            case MAIN:
                handleMainMenuClick(player, session, id);
                break;
            case EVENT_SELECTION:
                handleEventSelectionClick(player, session, id);
                break;
            case EVENT:
                handleEventMenuClick(player, session, id);
                break;
            case BATTLE:
                handleBattleMenuClick(player, session, id);
                break;
            case SHOP:
                handleShopMenuClick(player, session, id);
                break;
            case EQUIPMENT:
                handleEquipmentMenuClick(player, session, id);
                break;
        }
    }

    private void handleMainMenuClick(Player player, GameSession session, String id) {
        switch (id) {
            case "adventure":
                openEventSelectionMenu(player);
                break;
            case "battle":
                openBattleMenu(player);
                break;
            case "shop":
                openShopMenu(player);
                break;
            case "equipment":
                openEquipmentMenu(player);
                break;
            case "end_game":
                player.closeInventory();
                endGame(player);
                break;
            default:
                break;
        }
    }

    private void handleEventSelectionClick(Player player, GameSession session, String id) {
        // Equipment button in event selection
        if ("equipment".equals(id)) {
            openEquipmentMenu(player);
            return;
        }

        if (id.startsWith("select_event_")) {
            String eventId = id.substring(13);
            GameEvent event = findEvent(eventId);
            if (event == null) {
                player.sendMessage(messages.format(player, "sgame.event_not_found"));
                return;
            }

            // Check requirements
            if (event.hasRequirement()) {
                EventRequirement req = event.getRequirement();
                if (!req.checkRequirements(session)) {
                    player.sendMessage(messages.colorize(req.getFailText()));
                    return;
                }
                // Apply gold cost if any
                req.applyGoldCost(session);
            }

            session.setCurrentEventId(eventId);
            session.addVisitedEvent(eventId); // Track visited event for weighted selection
            session.setLastEventId(eventId); // Track last event for followup weighting
            saveSession(session);
            
            // Route to appropriate menu based on event type
            String eventType = event.getEventType();
            if ("battle".equals(eventType)) {
                openBattleMenu(player);
            } else if ("shop".equals(eventType)) {
                openShopMenu(player);
            } else {
                openEventMenu(player);
            }
        }
    }

    private void handleEventMenuClick(Player player, GameSession session, String id) {
        // No back button - must make a choice
        
        if (id.startsWith("choice_")) {
            int choiceIndex;
            try {
                choiceIndex = Integer.parseInt(id.substring(7));
            } catch (NumberFormatException e) {
                return;
            }

            String eventId = session.getCurrentEventId();
            GameEvent event = findEvent(eventId);
            if (event == null || choiceIndex >= event.getChoices().size()) {
                return;
            }

            EventChoice choice = event.getChoices().get(choiceIndex);
            applyEventChoice(player, session, event, choice);
            session.setCurrentEventId(null);
            session.incrementStage();
            saveSession(session);
            
            // After applying choice effects, check if player survived
            // If health <= 0, handleGameOverDeath was already called in applyEventChoice
            // Only continue to next event selection if player is still alive
            if (session.getHealth() > 0) {
                openMainMenu(player); // Will show next event selection or victory
            }
            // If health <= 0, player already received game over message and rewards
        }
    }

    private void handleBattleMenuClick(Player player, GameSession session, String id) {
        // No back button - must fight or flee

        String enemyId = session.getCurrentEnemyId();
        BattleEnemy enemy = findEnemy(enemyId);
        if (enemy == null) {
            openMainMenu(player);
            return;
        }

        if ("fight".equals(id)) {
            resolveBattle(player, session, enemy);
        } else if ("flee".equals(id)) {
            resolveFlee(player, session, enemy);
        }
    }

    private void handleShopMenuClick(Player player, GameSession session, String id) {
        // No back button - must leave shop properly

        if ("shop_leave".equals(id)) {
            // Leave shop and advance to next stage
            session.setCurrentEventId(null);
            session.incrementStage();
            saveSession(session);
            player.sendMessage(messages.format(player, "sgame.shop_left"));
            openMainMenu(player); // Will show next event selection or victory
            return;
        }

        if (id.startsWith("buy_")) {
            String itemId = id.substring(4);
            ShopItem item = findShopItem(itemId);
            if (item == null) {
                return;
            }

            if (session.getGold() < item.getPrice()) {
                player.sendMessage(messages.format(player, "sgame.not_enough_gold"));
                return;
            }

            session.addGold(-item.getPrice());
            applyShopItem(player, session, item);
            saveSession(session);
            
            Map<String, String> map = new HashMap<String, String>();
            map.put("item", item.getName());
            player.sendMessage(messages.format(player, "sgame.item_purchased", map));
            
            // Refresh shop menu to show updated gold and item availability
            openShopMenu(player);
        }
    }

    private void handleEquipmentMenuClick(Player player, GameSession session, String id) {
        if ("back".equals(id)) {
            openMainMenu(player); // Returns to event selection
            return;
        }

        if (id.startsWith("buy_equip_")) {
            String equipId = id.substring(10);
            Equipment eq = findEquipment(equipId);
            if (eq == null) {
                return;
            }

            // Check if already equipped
            boolean isEquipped = equipId.equals(session.getEquippedWeapon()) || 
                                 equipId.equals(session.getEquippedArmor()) || 
                                 equipId.equals(session.getEquippedAccessory());
            
            if (isEquipped) {
                // Unequip - just remove the equipment reference
                // Bonuses are calculated dynamically, so no need to reverse stats
                if ("weapon".equals(eq.getSlot())) {
                    session.setEquippedWeapon(null);
                } else if ("armor".equals(eq.getSlot())) {
                    session.setEquippedArmor(null);
                } else {
                    session.setEquippedAccessory(null);
                }
                Map<String, String> map = new HashMap<String, String>();
                map.put("item", eq.getName());
                player.sendMessage(messages.format(player, "sgame.unequipped", map));
            } else {
                // Check if can afford
                if (session.getGold() < eq.getPrice()) {
                    player.sendMessage(messages.format(player, "sgame.not_enough_gold"));
                    return;
                }
                
                // Purchase and equip
                // Bonuses (attack, defense) are calculated dynamically via getEquipmentAttackBonus/DefenseBonus
                // Health and magic bonuses are also calculated dynamically
                session.addGold(-eq.getPrice());
                
                if ("weapon".equals(eq.getSlot())) {
                    session.setEquippedWeapon(equipId);
                } else if ("armor".equals(eq.getSlot())) {
                    session.setEquippedArmor(equipId);
                } else {
                    session.setEquippedAccessory(equipId);
                }
                
                Map<String, String> map = new HashMap<String, String>();
                map.put("item", eq.getName());
                player.sendMessage(messages.format(player, "sgame.equipped", map));
            }
            
            saveSession(session);
            openEquipmentMenu(player);
        }
    }

    // ============ Game Logic ============

    private void applyEventChoice(Player player, GameSession session, GameEvent event, EventChoice choice) {
        // Determine if using alternative outcome
        boolean useAltResult = false;
        if (choice.hasRequirement()) {
            EventRequirement req = choice.getRequirement();
            useAltResult = !req.checkRequirements(session) && choice.hasAltResult();
        }
        
        // Consume item cost if required
        if (choice.getItemCost() != null && !choice.getItemCost().isEmpty()) {
            if (session.hasItem(choice.getItemCost(), choice.getItemCostAmount())) {
                session.removeItem(choice.getItemCost(), choice.getItemCostAmount());
            } else if (!useAltResult) {
                // Cannot afford item cost and no alt result
                player.sendMessage(messages.format(player, "sgame.missing_item_cost"));
                return;
            }
        }
        
        // Apply rewards/penalties based on result type
        int goldChange = useAltResult ? choice.getGoldChangeAlt() : choice.getGoldChange();
        int healthChange = useAltResult ? choice.getHealthChangeAlt() : choice.getHealthChange();
        String resultText = useAltResult ? choice.getResultTextAlt() : choice.getResultText();
        
        session.addGold(goldChange);
        session.addHealth(healthChange);
        
        // Add gained items
        if (choice.getItemGain() != null && !choice.getItemGain().isEmpty() && !useAltResult) {
            session.addItem(choice.getItemGain(), choice.getItemGainAmount());
            Map<String, String> itemMap = new HashMap<String, String>();
            itemMap.put("item", choice.getItemGain());
            itemMap.put("amount", String.valueOf(choice.getItemGainAmount()));
            player.sendMessage(messages.format(player, "sgame.item_gained", itemMap));
        }

        // Check for game over
        if (session.getHealth() <= 0) {
            handleGameOverDeath(player, session);
            return;
        }

        // Show result
        Map<String, String> map = new HashMap<String, String>();
        map.put("result", resultText != null ? resultText : "");
        map.put("gold_change", formatChange(goldChange));
        map.put("health_change", formatChange(healthChange));
        player.sendMessage(messages.format(player, "sgame.choice_result", map));
        
        player.closeInventory();
        openMainMenu(player);
    }

    /**
     * Enhanced battle resolution using multi-dimensional combat stats.
     * Formula:
     * - Player Power = Attack + (Health/5) + (Magic/3) + Equipment bonuses + Random(0-20)
     * - Enemy Power = Enemy.Attack + (Enemy.Health/5) + (Enemy.Magic/3) + Random(0-15)
     * - Defense reduces incoming damage
     */
    private void resolveBattle(Player player, GameSession session, BattleEnemy enemy) {
        // Calculate player power with equipment bonuses
        int playerAttack = session.getAttack() + getEquipmentAttackBonus(session);
        int playerDefense = session.getDefense() + getEquipmentDefenseBonus(session);
        int playerPower = playerAttack + (session.getHealth() / 5) + (session.getMagic() / 3) + random.nextInt(21);
        
        // Calculate enemy power
        int enemyPower = enemy.getAttack() + (enemy.getHealth() / 5) + (enemy.getMagic() / 3) + random.nextInt(16);
        
        Map<String, String> map = new HashMap<String, String>();
        map.put("enemy", enemy.getName());
        map.put("player_power", String.valueOf(playerPower));
        map.put("enemy_power", String.valueOf(enemyPower));

        if (playerPower > enemyPower) {
            // Victory
            int goldReward = enemy.getGoldReward();
            session.addGold(goldReward);
            session.incrementBattleVictories();
            session.incrementStage();
            
            // Magic usage costs some magic points
            if (session.getMagic() > 0) {
                session.addMagic(-5);
            }
            
            map.put("gold", String.valueOf(goldReward));
            player.sendMessage(messages.format(player, "sgame.battle_victory", map));
        } else {
            // Defeat - calculate damage reduced by defense
            int baseDamage = enemy.getDamage();
            int reducedDamage = Math.max(1, baseDamage - (playerDefense / 2));
            session.addHealth(-reducedDamage);
            
            map.put("damage", String.valueOf(reducedDamage));
            map.put("base_damage", String.valueOf(baseDamage));
            map.put("defense_reduction", String.valueOf(baseDamage - reducedDamage));
            player.sendMessage(messages.format(player, "sgame.battle_defeat", map));

            if (session.getHealth() <= 0) {
                handleGameOverDeath(player, session);
                return;
            }
        }

        session.setCurrentEnemyId(null);
        saveSession(session);
        
        player.closeInventory();
        openMainMenu(player);
    }

    private void resolveFlee(Player player, GameSession session, BattleEnemy enemy) {
        // Flee success based on player speed (defense stat) vs enemy power
        int fleeChance = 50 + (session.getDefense() * 2) - (enemy.getPower() / 3);
        fleeChance = Math.min(90, Math.max(20, fleeChance)); // Clamp between 20-90%
        
        if (random.nextInt(100) < fleeChance) {
            player.sendMessage(messages.format(player, "sgame.flee_success"));
        } else {
            int damage = Math.max(1, enemy.getDamage() / 2 - session.getDefense() / 3);
            session.addHealth(-damage);
            
            Map<String, String> map = new HashMap<String, String>();
            map.put("damage", String.valueOf(damage));
            player.sendMessage(messages.format(player, "sgame.flee_failed", map));

            if (session.getHealth() <= 0) {
                handleGameOverDeath(player, session);
                return;
            }
        }

        // Clear current event/enemy and go back to event selection
        // Note: Fleeing does NOT advance stage - player must select new events
        session.setCurrentEnemyId(null);
        session.setCurrentEventId(null);
        saveSession(session);
        
        openMainMenu(player); // Will show new random event selection
    }

    private void applyShopItem(Player player, GameSession session, ShopItem item) {
        switch (item.getEffectType()) {
            case "heal":
                session.addHealth(item.getEffectValue());
                break;
            case "max_health":
                session.setMaxHealth(session.getMaxHealth() + item.getEffectValue());
                session.addHealth(item.getEffectValue());
                break;
            case "power":
                // Could add power stat if needed
                break;
            default:
                break;
        }
    }

    /**
     * Handle game over when player's health reaches zero.
     * This is a private helper to avoid calling endGame() which has different logic.
     */
    private void handleGameOverDeath(Player player, GameSession session) {
        session.setHealth(0);
        session.setEnded(true);
        saveSession(session);
        
        player.closeInventory();
        player.sendMessage(messages.format(player, "sgame.game_over_health"));
        
        // Grant end rewards based on progress (even if died)
        List<String> rewardNames = grantEndRewards(player, session);
        
        Map<String, String> map = new HashMap<String, String>();
        map.put("rewards", rewardNames.isEmpty() ? messages.format(player, "sgame.no_rewards") : String.join(", ", rewardNames));
        player.sendMessage(messages.format(player, "sgame.game_ended", map));
        
        // Clear the session
        String playerName = player.getName();
        activeSessions.remove(playerName);
        clearSessionFile(playerName);
    }

    private List<String> grantEndRewards(Player player, GameSession session) {
        List<String> rewardNames = new ArrayList<String>();
        
        int score = calculateScore(session);
        
        for (EndReward reward : endRewards) {
            if (score >= reward.getMinScore()) {
                // Execute reward commands
                for (String cmd : reward.getCommands()) {
                    String command = cmd.replace("{player}", player.getName())
                                        .replace("{score}", String.valueOf(score));
                    if (command.startsWith("/")) {
                        command = command.substring(1);
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
                rewardNames.add(reward.getName());
            }
        }
        
        return rewardNames;
    }

    private int calculateScore(GameSession session) {
        // Score based on gold, health, stages, and victories
        int score = session.getGold() / 10;
        score += session.getHealth();
        score += session.getCurrentStage() * 50;
        score += session.getBattleVictories() * 30;
        return score;
    }

    // ============ Helper Methods ============

    private BattleEnemy pickEnemy(int stage) {
        // Filter enemies by stage if applicable
        List<BattleEnemy> available = new ArrayList<BattleEnemy>();
        for (BattleEnemy e : enemies) {
            if (e.getMinStage() <= stage) {
                available.add(e);
            }
        }
        if (available.isEmpty()) {
            available.addAll(enemies);
        }
        return available.get(random.nextInt(available.size()));
    }

    private GameEvent findEvent(String id) {
        if (id == null) {
            return null;
        }
        for (GameEvent e : gameEvents) {
            if (e.getId().equals(id)) {
                return e;
            }
        }
        return null;
    }

    private BattleEnemy findEnemy(String id) {
        if (id == null) {
            return null;
        }
        for (BattleEnemy e : enemies) {
            if (e.getId().equals(id)) {
                return e;
            }
        }
        return null;
    }

    private ShopItem findShopItem(String id) {
        if (id == null) {
            return null;
        }
        for (ShopItem item : shopItems) {
            if (item.getId().equals(id)) {
                return item;
            }
        }
        return null;
    }

    private Equipment findEquipment(String id) {
        if (id == null) {
            return null;
        }
        for (Equipment eq : equipments) {
            if (eq.getId().equals(id)) {
                return eq;
            }
        }
        return null;
    }

    /**
     * Calculate total attack bonus from equipped items.
     */
    private int getEquipmentAttackBonus(GameSession session) {
        int bonus = 0;
        Equipment weapon = findEquipment(session.getEquippedWeapon());
        if (weapon != null) bonus += weapon.getAttackBonus();
        Equipment armor = findEquipment(session.getEquippedArmor());
        if (armor != null) bonus += armor.getAttackBonus();
        Equipment accessory = findEquipment(session.getEquippedAccessory());
        if (accessory != null) bonus += accessory.getAttackBonus();
        return bonus;
    }

    /**
     * Calculate total defense bonus from equipped items.
     */
    private int getEquipmentDefenseBonus(GameSession session) {
        int bonus = 0;
        Equipment weapon = findEquipment(session.getEquippedWeapon());
        if (weapon != null) bonus += weapon.getDefenseBonus();
        Equipment armor = findEquipment(session.getEquippedArmor());
        if (armor != null) bonus += armor.getDefenseBonus();
        Equipment accessory = findEquipment(session.getEquippedAccessory());
        if (accessory != null) bonus += accessory.getDefenseBonus();
        return bonus;
    }

    /**
     * Calculate total health bonus from equipped items.
     */
    private int getEquipmentHealthBonus(GameSession session) {
        int bonus = 0;
        Equipment weapon = findEquipment(session.getEquippedWeapon());
        if (weapon != null) bonus += weapon.getHealthBonus();
        Equipment armor = findEquipment(session.getEquippedArmor());
        if (armor != null) bonus += armor.getHealthBonus();
        Equipment accessory = findEquipment(session.getEquippedAccessory());
        if (accessory != null) bonus += accessory.getHealthBonus();
        return bonus;
    }

    /**
     * Calculate total magic bonus from equipped items.
     */
    private int getEquipmentMagicBonus(GameSession session) {
        int bonus = 0;
        Equipment weapon = findEquipment(session.getEquippedWeapon());
        if (weapon != null) bonus += weapon.getMagicBonus();
        Equipment armor = findEquipment(session.getEquippedArmor());
        if (armor != null) bonus += armor.getMagicBonus();
        Equipment accessory = findEquipment(session.getEquippedAccessory());
        if (accessory != null) bonus += accessory.getMagicBonus();
        return bonus;
    }

    /**
     * Select events for current stage using weighted randomization.
     * Events are weighted based on:
     * - Base weight from config
     * - Boost if event is a followup to last completed event
     * - Requirements check (excluded if prerequisites not met)
     */
    private List<GameEvent> selectEventsForStage(GameSession session, int count) {
        List<GameEvent> available = new ArrayList<GameEvent>();
        Map<GameEvent, Integer> weights = new HashMap<GameEvent, Integer>();
        
        String lastEventId = session.getLastEventId();
        GameEvent lastEvent = lastEventId != null ? findEvent(lastEventId) : null;
        
        for (GameEvent event : gameEvents) {
            // Check prerequisites - must have visited all required events
            if (event.hasPrerequisites()) {
                boolean meetsPrereqs = true;
                for (String prereqId : event.getPrerequisiteEvents()) {
                    if (!session.hasVisitedEvent(prereqId)) {
                        meetsPrereqs = false;
                        break;
                    }
                }
                if (!meetsPrereqs) {
                    continue; // Skip this event
                }
            }
            
            available.add(event);
            
            // Calculate weight
            int weight = event.getWeight();
            
            // Boost weight if this is a followup to the last event
            if (lastEvent != null && lastEvent.getFollowupEvents().contains(event.getId())) {
                weight *= 3; // Triple the weight for followup events
            }
            
            weights.put(event, weight);
        }
        
        // Select events using weighted random
        List<GameEvent> selected = new ArrayList<GameEvent>();
        while (selected.size() < count && !available.isEmpty()) {
            GameEvent chosen = weightedRandomSelect(available, weights);
            if (chosen != null) {
                selected.add(chosen);
                available.remove(chosen);
            }
        }
        
        return selected;
    }

    /**
     * Weighted random selection from a list of events.
     */
    private GameEvent weightedRandomSelect(List<GameEvent> events, Map<GameEvent, Integer> weights) {
        if (events.isEmpty()) {
            return null;
        }
        
        int totalWeight = 0;
        for (GameEvent e : events) {
            totalWeight += weights.getOrDefault(e, 10);
        }
        
        if (totalWeight <= 0) {
            return events.get(random.nextInt(events.size()));
        }
        
        int randomValue = random.nextInt(totalWeight);
        int currentWeight = 0;
        
        for (GameEvent e : events) {
            currentWeight += weights.getOrDefault(e, 10);
            if (randomValue < currentWeight) {
                return e;
            }
        }
        
        return events.get(events.size() - 1);
    }

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

    private String formatChange(int value) {
        if (value >= 0) {
            return "+" + value;
        }
        return String.valueOf(value);
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

    // ============ Session Persistence ============

    private GameSession getOrLoadSession(String playerName) {
        GameSession session = activeSessions.get(playerName);
        if (session != null) {
            return session;
        }
        // Try to load from file
        session = loadSession(playerName);
        if (session != null) {
            activeSessions.put(playerName, session);
        }
        return session;
    }

    private GameSession loadSession(String playerName) {
        File file = new File(storageDir, playerName + ".yml");
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        if (!data.contains("sgame.active")) {
            return null;
        }
        if (!data.getBoolean("sgame.active", false)) {
            return null;
        }
        
        GameSession session = new GameSession(playerName, 
            data.getInt("sgame.gold", startingGold),
            data.getInt("sgame.health", startingHealth));
        session.setMaxHealth(data.getInt("sgame.max_health", startingHealth));
        session.setCurrentStage(data.getInt("sgame.current_stage", 0));
        session.setBattleVictories(data.getInt("sgame.battle_victories", 0));
        session.setEnded(data.getBoolean("sgame.ended", false));
        session.setCurrentEventId(data.getString("sgame.current_event_id", null));
        session.setCurrentEnemyId(data.getString("sgame.current_enemy_id", null));
        
        // Load combat stats
        session.setAttack(data.getInt("sgame.attack", startingAttack));
        session.setDefense(data.getInt("sgame.defense", startingDefense));
        session.setMagic(data.getInt("sgame.magic", startingMagic));
        session.setMaxMagic(data.getInt("sgame.max_magic", startingMagic));
        
        // Load equipment
        session.setEquippedWeapon(data.getString("sgame.equipped_weapon", null));
        session.setEquippedArmor(data.getString("sgame.equipped_armor", null));
        session.setEquippedAccessory(data.getString("sgame.equipped_accessory", null));
        
        // Load visited events
        List<String> visited = data.getStringList("sgame.visited_events");
        for (String eventId : visited) {
            session.addVisitedEvent(eventId);
        }
        session.setLastEventId(data.getString("sgame.last_event_id", null));
        
        // Load inventory
        ConfigurationSection invSection = data.getConfigurationSection("sgame.inventory");
        if (invSection != null) {
            for (String key : invSection.getKeys(false)) {
                int amount = invSection.getInt(key, 0);
                if (amount > 0) {
                    session.addItem(key, amount);
                }
            }
        }
        
        return session;
    }

    private void saveSession(GameSession session) {
        File file = new File(storageDir, session.getPlayerName() + ".yml");
        YamlConfiguration data;
        if (file.exists()) {
            data = YamlConfiguration.loadConfiguration(file);
        } else {
            data = new YamlConfiguration();
        }
        
        data.set("sgame.active", true);
        data.set("sgame.gold", session.getGold());
        data.set("sgame.health", session.getHealth());
        data.set("sgame.max_health", session.getMaxHealth());
        data.set("sgame.current_stage", session.getCurrentStage());
        data.set("sgame.battle_victories", session.getBattleVictories());
        data.set("sgame.ended", session.isEnded());
        data.set("sgame.current_event_id", session.getCurrentEventId());
        data.set("sgame.current_enemy_id", session.getCurrentEnemyId());
        
        // Save combat stats
        data.set("sgame.attack", session.getAttack());
        data.set("sgame.defense", session.getDefense());
        data.set("sgame.magic", session.getMagic());
        data.set("sgame.max_magic", session.getMaxMagic());
        
        // Save equipment
        data.set("sgame.equipped_weapon", session.getEquippedWeapon());
        data.set("sgame.equipped_armor", session.getEquippedArmor());
        data.set("sgame.equipped_accessory", session.getEquippedAccessory());
        
        // Save visited events
        data.set("sgame.visited_events", session.getVisitedEvents());
        data.set("sgame.last_event_id", session.getLastEventId());
        
        // Save inventory
        data.set("sgame.inventory", null);
        for (Map.Entry<String, Integer> entry : session.getInventory().entrySet()) {
            if (entry.getValue() > 0) {
                data.set("sgame.inventory." + entry.getKey(), entry.getValue());
            }
        }
        
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("無法保存遊戲會話: " + e.getMessage());
        }
    }

    private void clearSessionFile(String playerName) {
        File file = new File(storageDir, playerName + ".yml");
        if (file.exists()) {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            data.set("sgame", null);
            try {
                data.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("無法清除遊戲會話: " + e.getMessage());
            }
        }
    }

    // ============ Inner Classes ============

    public enum MenuType {
        MAIN, EVENT_SELECTION, EVENT, BATTLE, SHOP, EQUIPMENT
    }

    public static class StrategyGameMenuHolder implements InventoryHolder {
        private final MenuType menuType;

        public StrategyGameMenuHolder(MenuType menuType) {
            this.menuType = menuType;
        }

        public MenuType getMenuType() {
            return menuType;
        }

        public Inventory getInventory() {
            return null;
        }
    }

    private static class GameSession {
        private final String playerName;
        private int gold;
        private int health;
        private int maxHealth;
        private int currentStage;
        private int battleVictories;
        private boolean ended;
        private String currentEventId;
        private String currentEnemyId;
        private final Map<String, Integer> inventory; // Virtual items held in this game session
        
        // Combat stats
        private int attack;
        private int defense;
        private int magic;
        private int maxMagic;
        
        // Equipment slots
        private String equippedWeapon;
        private String equippedArmor;
        private String equippedAccessory;
        
        // Event tracking for weighted randomization
        private final List<String> visitedEvents;
        private String lastEventId;

        GameSession(String playerName, int gold, int health) {
            this.playerName = playerName;
            this.gold = gold;
            this.health = health;
            this.maxHealth = health;
            this.currentStage = 0;
            this.battleVictories = 0;
            this.ended = false;
            this.inventory = new HashMap<String, Integer>();
            this.visitedEvents = new ArrayList<String>();
            
            // Default combat stats
            this.attack = 10;
            this.defense = 5;
            this.magic = 20;
            this.maxMagic = 20;
        }

        String getPlayerName() { return playerName; }
        int getGold() { return gold; }
        void addGold(int amount) { this.gold = Math.max(0, this.gold + amount); }
        int getHealth() { return health; }
        void setHealth(int health) { this.health = Math.min(maxHealth, Math.max(0, health)); }
        void addHealth(int amount) { setHealth(this.health + amount); }
        int getMaxHealth() { return maxHealth; }
        void setMaxHealth(int maxHealth) { this.maxHealth = maxHealth; }
        int getCurrentStage() { return currentStage; }
        void setCurrentStage(int stage) { this.currentStage = stage; }
        void incrementStage() { this.currentStage++; }
        int getBattleVictories() { return battleVictories; }
        int getVictories() { return battleVictories; } // Alias for getBattleVictories
        void setBattleVictories(int victories) { this.battleVictories = victories; }
        void incrementBattleVictories() { this.battleVictories++; }
        boolean isEnded() { return ended; }
        void setEnded(boolean ended) { this.ended = ended; }
        String getCurrentEventId() { return currentEventId; }
        void setCurrentEventId(String id) { this.currentEventId = id; }
        String getCurrentEnemyId() { return currentEnemyId; }
        void setCurrentEnemyId(String id) { this.currentEnemyId = id; }
        
        // Combat stats
        int getAttack() { return attack; }
        void setAttack(int attack) { this.attack = Math.max(0, attack); }
        void addAttack(int amount) { this.attack = Math.max(0, this.attack + amount); }
        int getDefense() { return defense; }
        void setDefense(int defense) { this.defense = Math.max(0, defense); }
        void addDefense(int amount) { this.defense = Math.max(0, this.defense + amount); }
        int getMagic() { return magic; }
        void setMagic(int magic) { this.magic = Math.min(maxMagic, Math.max(0, magic)); }
        void addMagic(int amount) { setMagic(this.magic + amount); }
        int getMaxMagic() { return maxMagic; }
        void setMaxMagic(int maxMagic) { this.maxMagic = maxMagic; }
        
        // Equipment management
        String getEquippedWeapon() { return equippedWeapon; }
        void setEquippedWeapon(String weapon) { this.equippedWeapon = weapon; }
        String getEquippedArmor() { return equippedArmor; }
        void setEquippedArmor(String armor) { this.equippedArmor = armor; }
        String getEquippedAccessory() { return equippedAccessory; }
        void setEquippedAccessory(String accessory) { this.equippedAccessory = accessory; }
        
        // Event tracking
        List<String> getVisitedEvents() { return visitedEvents; }
        boolean hasVisitedEvent(String eventId) { return visitedEvents.contains(eventId); }
        void addVisitedEvent(String eventId) { 
            if (!visitedEvents.contains(eventId)) {
                visitedEvents.add(eventId);
            }
            this.lastEventId = eventId;
        }
        String getLastEventId() { return lastEventId; }
        void setLastEventId(String id) { this.lastEventId = id; }
        
        // Inventory management
        Map<String, Integer> getInventory() { return inventory; }
        int getItemCount(String itemId) { 
            return inventory.getOrDefault(itemId, 0); 
        }
        boolean hasItem(String itemId) { 
            return getItemCount(itemId) > 0; 
        }
        boolean hasItem(String itemId, int amount) { 
            return getItemCount(itemId) >= amount; 
        }
        void addItem(String itemId, int amount) {
            int current = getItemCount(itemId);
            inventory.put(itemId, Math.max(0, current + amount));
        }
        void removeItem(String itemId, int amount) {
            addItem(itemId, -amount);
            if (getItemCount(itemId) <= 0) {
                inventory.remove(itemId);
            }
        }
        
        // Calculate total stats including equipment bonuses
        int getTotalAttack() { return attack; } // Equipment bonus added when calculated
        int getTotalDefense() { return defense; }
    }

    private static class GameEvent {
        private final String id;
        private final String name;
        private final List<String> description;
        private final List<EventChoice> choices;
        private final String eventType; // "story", "shop", "battle"
        private final EventRequirement requirement;
        private final int weight; // Base weight for random selection
        private final List<String> followupEvents; // Events that get boosted weight after this one
        private final List<String> prerequisiteEvents; // Must have visited these events first

        GameEvent(String id, String name, List<String> description, List<EventChoice> choices, 
                  String eventType, EventRequirement requirement, int weight, 
                  List<String> followupEvents, List<String> prerequisiteEvents) {
            this.id = id;
            this.name = name;
            this.description = description != null ? description : new ArrayList<String>();
            this.choices = choices != null ? choices : new ArrayList<EventChoice>();
            this.eventType = eventType != null ? eventType : "story";
            this.requirement = requirement;
            this.weight = weight > 0 ? weight : 10;
            this.followupEvents = followupEvents != null ? followupEvents : new ArrayList<String>();
            this.prerequisiteEvents = prerequisiteEvents != null ? prerequisiteEvents : new ArrayList<String>();
        }

        static GameEvent fromSection(String id, ConfigurationSection section) {
            String name = section.getString("name", id);
            List<String> desc = section.getStringList("description");
            String eventType = section.getString("type", "story");
            EventRequirement requirement = EventRequirement.fromSection(section.getConfigurationSection("requirement"));
            int weight = section.getInt("weight", 10);
            List<String> followups = section.getStringList("followup_events");
            List<String> prereqs = section.getStringList("prerequisite_events");
            
            List<EventChoice> choices = new ArrayList<EventChoice>();
            List<Map<?, ?>> choiceList = section.getMapList("choices");
            for (Map<?, ?> raw : choiceList) {
                EventChoice choice = EventChoice.fromMap(raw);
                if (choice != null) {
                    choices.add(choice);
                }
            }
            
            return new GameEvent(id, name, desc, choices, eventType, requirement, weight, followups, prereqs);
        }

        String getId() { return id; }
        String getName() { return name; }
        List<String> getDescription() { return description; }
        List<EventChoice> getChoices() { return choices; }
        String getEventType() { return eventType; }
        EventRequirement getRequirement() { return requirement; }
        boolean hasRequirement() { return requirement != null; }
        int getWeight() { return weight; }
        List<String> getFollowupEvents() { return followupEvents; }
        List<String> getPrerequisiteEvents() { return prerequisiteEvents; }
        boolean hasPrerequisites() { return !prerequisiteEvents.isEmpty(); }
    }

    /**
     * Represents requirements for an event or choice.
     * Can require items, gold, or other conditions.
     */
    private static class EventRequirement {
        private final String requiredItem;
        private final int requiredItemAmount;
        private final int requiredGold;
        private final int goldCost; // Gold spent when entering event
        private final String failText; // Message when requirement not met

        EventRequirement(String requiredItem, int requiredItemAmount, int requiredGold, 
                        int goldCost, String failText) {
            this.requiredItem = requiredItem;
            this.requiredItemAmount = requiredItemAmount > 0 ? requiredItemAmount : 1;
            this.requiredGold = requiredGold;
            this.goldCost = goldCost;
            this.failText = failText;
        }

        static EventRequirement fromSection(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            String item = section.getString("item");
            int itemAmount = section.getInt("item_amount", 1);
            int gold = section.getInt("gold", 0);
            int cost = section.getInt("gold_cost", 0);
            String fail = section.getString("fail_text", "&c你不滿足進入條件");
            
            if (item == null && gold <= 0 && cost <= 0) {
                return null;
            }
            return new EventRequirement(item, itemAmount, gold, cost, fail);
        }
        
        static EventRequirement fromMap(Map<?, ?> raw) {
            if (raw == null) {
                return null;
            }
            String item = raw.get("item") != null ? raw.get("item").toString() : null;
            int itemAmount = parseInt(raw.get("item_amount"));
            if (itemAmount <= 0) itemAmount = 1;
            int gold = parseInt(raw.get("gold"));
            int cost = parseInt(raw.get("gold_cost"));
            String fail = raw.get("fail_text") != null ? raw.get("fail_text").toString() : "&c你不滿足條件";
            
            if (item == null && gold <= 0 && cost <= 0) {
                return null;
            }
            return new EventRequirement(item, itemAmount, gold, cost, fail);
        }

        String getRequiredItem() { return requiredItem; }
        int getRequiredItemAmount() { return requiredItemAmount; }
        int getRequiredGold() { return requiredGold; }
        int getGoldCost() { return goldCost; }
        String getFailText() { return failText; }
        
        boolean hasItemRequirement() { return requiredItem != null && !requiredItem.isEmpty(); }
        boolean hasGoldRequirement() { return requiredGold > 0; }
        boolean hasGoldCost() { return goldCost > 0; }
        
        boolean checkRequirements(GameSession session) {
            if (hasItemRequirement() && !session.hasItem(requiredItem, requiredItemAmount)) {
                return false;
            }
            if (hasGoldRequirement() && session.getGold() < requiredGold) {
                return false;
            }
            if (hasGoldCost() && session.getGold() < goldCost) {
                return false;
            }
            return true;
        }
        
        void applyGoldCost(GameSession session) {
            if (hasGoldCost()) {
                session.addGold(-goldCost);
            }
        }
    }

    private static class EventChoice {
        private final String text;
        private final String resultText;
        private final String resultTextAlt; // Alternative result when requirement not met
        private final int goldChange;
        private final int healthChange;
        private final int goldChangeAlt; // Alternative outcome
        private final int healthChangeAlt;
        private final String itemGain; // Item gained from this choice
        private final int itemGainAmount;
        private final String itemCost; // Item consumed by this choice
        private final int itemCostAmount;
        private final EventRequirement requirement;

        EventChoice(String text, String resultText, String resultTextAlt, 
                   int goldChange, int healthChange, int goldChangeAlt, int healthChangeAlt,
                   String itemGain, int itemGainAmount, String itemCost, int itemCostAmount,
                   EventRequirement requirement) {
            this.text = text != null ? text : "選擇";
            this.resultText = resultText != null ? resultText : "";
            this.resultTextAlt = resultTextAlt;
            this.goldChange = goldChange;
            this.healthChange = healthChange;
            this.goldChangeAlt = goldChangeAlt;
            this.healthChangeAlt = healthChangeAlt;
            this.itemGain = itemGain;
            this.itemGainAmount = itemGainAmount > 0 ? itemGainAmount : 1;
            this.itemCost = itemCost;
            this.itemCostAmount = itemCostAmount > 0 ? itemCostAmount : 1;
            this.requirement = requirement;
        }

        static EventChoice fromMap(Map<?, ?> raw) {
            if (raw == null) {
                return null;
            }
            String text = raw.get("text") != null ? raw.get("text").toString() : "選擇";
            String result = raw.get("result") != null ? raw.get("result").toString() : "";
            String resultAlt = raw.get("result_alt") != null ? raw.get("result_alt").toString() : null;
            int gold = parseInt(raw.get("gold_change"));
            int health = parseInt(raw.get("health_change"));
            int goldAlt = parseInt(raw.get("gold_change_alt"));
            int healthAlt = parseInt(raw.get("health_change_alt"));
            String itemGain = raw.get("item_gain") != null ? raw.get("item_gain").toString() : null;
            int itemGainAmt = parseInt(raw.get("item_gain_amount"));
            String itemCost = raw.get("item_cost") != null ? raw.get("item_cost").toString() : null;
            int itemCostAmt = parseInt(raw.get("item_cost_amount"));
            
            EventRequirement req = null;
            if (raw.get("requirement") instanceof Map) {
                req = EventRequirement.fromMap((Map<?, ?>) raw.get("requirement"));
            }
            
            return new EventChoice(text, result, resultAlt, gold, health, goldAlt, healthAlt, 
                                  itemGain, itemGainAmt, itemCost, itemCostAmt, req);
        }

        String getText() { return text; }
        String getResultText() { return resultText; }
        String getResultTextAlt() { return resultTextAlt; }
        int getGoldChange() { return goldChange; }
        int getHealthChange() { return healthChange; }
        int getGoldChangeAlt() { return goldChangeAlt; }
        int getHealthChangeAlt() { return healthChangeAlt; }
        String getItemGain() { return itemGain; }
        int getItemGainAmount() { return itemGainAmount; }
        String getItemCost() { return itemCost; }
        int getItemCostAmount() { return itemCostAmount; }
        EventRequirement getRequirement() { return requirement; }
        boolean hasRequirement() { return requirement != null; }
        boolean hasAltResult() { return resultTextAlt != null && !resultTextAlt.isEmpty(); }
    }

    private static class ShopItem {
        private final String id;
        private final String name;
        private final int price;
        private final Material material;
        private final String effectType;
        private final int effectValue;
        private final String effectDescription;

        ShopItem(String id, String name, int price, Material material, String effectType, int effectValue, String effectDescription) {
            this.id = id;
            this.name = name != null ? name : id;
            this.price = price;
            this.material = material != null ? material : Material.PAPER;
            this.effectType = effectType != null ? effectType : "none";
            this.effectValue = effectValue;
            this.effectDescription = effectDescription != null ? effectDescription : "";
        }

        static ShopItem fromMap(Map<?, ?> raw) {
            if (raw == null || raw.get("id") == null) {
                return null;
            }
            String id = raw.get("id").toString();
            String name = raw.get("name") != null ? raw.get("name").toString() : id;
            int price = parseInt(raw.get("price"));
            Material mat = Material.PAPER;
            if (raw.get("material") != null) {
                mat = Material.matchMaterial(raw.get("material").toString().toUpperCase());
                if (mat == null) {
                    mat = Material.PAPER;
                }
            }
            String effectType = raw.get("effect_type") != null ? raw.get("effect_type").toString() : "none";
            int effectValue = parseInt(raw.get("effect_value"));
            String effectDesc = raw.get("effect_description") != null ? raw.get("effect_description").toString() : "";
            return new ShopItem(id, name, price, mat, effectType, effectValue, effectDesc);
        }

        String getId() { return id; }
        String getName() { return name; }
        int getPrice() { return price; }
        Material getMaterial() { return material; }
        String getEffectType() { return effectType; }
        int getEffectValue() { return effectValue; }
        String getEffectDescription() { return effectDescription; }
    }

    private static class BattleEnemy {
        private final String id;
        private final String name;
        private final String description;
        private final int power;
        private final int damage;
        private final int goldReward;
        private final int minStage;
        // Enhanced combat stats
        private final int health;
        private final int attack;
        private final int defense;
        private final int magic;

        BattleEnemy(String id, String name, String description, int power, int damage, int goldReward, int minStage,
                   int health, int attack, int defense, int magic) {
            this.id = id;
            this.name = name != null ? name : id;
            this.description = description != null ? description : "";
            this.power = power > 0 ? power : DEFAULT_ENEMY_POWER;
            this.damage = damage > 0 ? damage : DEFAULT_ENEMY_DAMAGE;
            this.goldReward = goldReward;
            this.minStage = minStage;
            this.health = health > 0 ? health : 50;
            this.attack = attack > 0 ? attack : 10;
            this.defense = defense > 0 ? defense : 5;
            this.magic = magic > 0 ? magic : 0;
        }

        static BattleEnemy fromMap(Map<?, ?> raw) {
            if (raw == null || raw.get("id") == null) {
                return null;
            }
            String id = raw.get("id").toString();
            String name = raw.get("name") != null ? raw.get("name").toString() : id;
            String desc = raw.get("description") != null ? raw.get("description").toString() : "";
            int power = parseInt(raw.get("power"));
            int damage = parseInt(raw.get("damage"));
            int gold = parseInt(raw.get("gold_reward"));
            int minStage = parseInt(raw.get("min_stage"));
            int health = parseInt(raw.get("health"));
            int attack = parseInt(raw.get("attack"));
            int defense = parseInt(raw.get("defense"));
            int magic = parseInt(raw.get("magic"));
            return new BattleEnemy(id, name, desc, power, damage, gold, minStage, health, attack, defense, magic);
        }

        String getId() { return id; }
        String getName() { return name; }
        String getDescription() { return description; }
        int getPower() { return power; }
        int getDamage() { return damage; }
        int getGoldReward() { return goldReward; }
        int getMinStage() { return minStage; }
        int getHealth() { return health; }
        int getAttack() { return attack; }
        int getDefense() { return defense; }
        int getMagic() { return magic; }
    }

    /**
     * Represents an equippable item that provides stat bonuses.
     */
    private static class Equipment {
        private final String id;
        private final String name;
        private final String slot; // weapon, armor, accessory
        private final int attackBonus;
        private final int defenseBonus;
        private final int healthBonus;
        private final int magicBonus;
        private final Material material;
        private final int price;

        Equipment(String id, String name, String slot, int attackBonus, int defenseBonus, 
                 int healthBonus, int magicBonus, Material material, int price) {
            this.id = id;
            this.name = name != null ? name : id;
            this.slot = slot != null ? slot : "accessory";
            this.attackBonus = attackBonus;
            this.defenseBonus = defenseBonus;
            this.healthBonus = healthBonus;
            this.magicBonus = magicBonus;
            this.material = material != null ? material : Material.IRON_INGOT;
            this.price = price;
        }

        static Equipment fromMap(Map<?, ?> raw) {
            if (raw == null || raw.get("id") == null) {
                return null;
            }
            String id = raw.get("id").toString();
            String name = raw.get("name") != null ? raw.get("name").toString() : id;
            String slot = raw.get("slot") != null ? raw.get("slot").toString() : "accessory";
            int attackBonus = parseInt(raw.get("attack_bonus"));
            int defenseBonus = parseInt(raw.get("defense_bonus"));
            int healthBonus = parseInt(raw.get("health_bonus"));
            int magicBonus = parseInt(raw.get("magic_bonus"));
            int price = parseInt(raw.get("price"));
            
            Material mat = Material.IRON_INGOT;
            if (raw.get("material") != null) {
                mat = Material.matchMaterial(raw.get("material").toString().toUpperCase());
                if (mat == null) {
                    mat = Material.IRON_INGOT;
                }
            }
            return new Equipment(id, name, slot, attackBonus, defenseBonus, healthBonus, magicBonus, mat, price);
        }

        String getId() { return id; }
        String getName() { return name; }
        String getSlot() { return slot; }
        int getAttackBonus() { return attackBonus; }
        int getDefenseBonus() { return defenseBonus; }
        int getHealthBonus() { return healthBonus; }
        int getMagicBonus() { return magicBonus; }
        Material getMaterial() { return material; }
        int getPrice() { return price; }
    }

    private static class EndReward {
        private final String name;
        private final int minScore;
        private final List<String> commands;

        EndReward(String name, int minScore, List<String> commands) {
            this.name = name != null ? name : "獎勵";
            this.minScore = minScore;
            this.commands = commands != null ? commands : new ArrayList<String>();
        }

        static EndReward fromMap(Map<?, ?> raw) {
            if (raw == null) {
                return null;
            }
            String name = raw.get("name") != null ? raw.get("name").toString() : "獎勵";
            int minScore = parseInt(raw.get("min_score"));
            List<String> commands = new ArrayList<String>();
            Object cmdsObj = raw.get("commands");
            if (cmdsObj instanceof List) {
                for (Object o : (List<?>) cmdsObj) {
                    if (o != null) {
                        commands.add(o.toString());
                    }
                }
            }
            return new EndReward(name, minScore, commands);
        }

        String getName() { return name; }
        int getMinScore() { return minScore; }
        List<String> getCommands() { return commands; }
    }

    private static int parseInt(Object o) {
        if (o == null) {
            return 0;
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
