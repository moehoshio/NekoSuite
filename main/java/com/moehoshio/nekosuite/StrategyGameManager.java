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
    
    // Shop configuration constants
    private static final int MAX_SHOP_ITEMS = 5;
    private static final int DISCOUNT_CHANCE = 30; // Percentage chance for discount
    private static final int MIN_DISCOUNT = 10;
    private static final int MAX_DISCOUNT = 50;

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
     * Continue an existing game or show start menu if no game exists.
     */
    public void continueGame(Player player) {
        String playerName = player.getName();
        GameSession session = getOrLoadSession(playerName);
        if (session == null || session.isEnded()) {
            // No active game, show start game menu
            openStartGameMenu(player);
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
     * Open the main game menu - shows start game if no active game,
     * otherwise restores player to their current state in the game.
     * This prevents players from re-rolling by closing menus.
     */
    public void openMainMenu(Player player) {
        GameSession session = getOrLoadSession(player.getName());
        
        // If no active game or game ended, show start game menu
        if (session == null || session.isEnded()) {
            openStartGameMenu(player);
            return;
        }

        // Check if player has completed enough stages
        if (session.getCurrentStage() >= maxStages) {
            openVictoryMenu(player, session);
            return;
        }

        // IMPORTANT: Check and restore intermediate states to prevent re-rolling
        
        // 1. Check if in active battle (has enemy with HP)
        if (session.getCurrentEnemyId() != null && session.getCurrentEnemyHp() > 0) {
            // Resume battle action selection
            openBattleActionMenu(player);
            return;
        }
        
        // 2. Check if currently in an event (story/shop/battle)
        String currentEventId = session.getCurrentEventId();
        if (currentEventId != null) {
            GameEvent event = findEvent(currentEventId);
            if (event != null) {
                String eventType = event.getEventType();
                if ("battle".equals(eventType)) {
                    // Battle event but no enemy HP - need to start battle
                    openBattleMenu(player);
                } else if ("shop".equals(eventType)) {
                    openShopMenu(player);
                } else {
                    openEventMenu(player);
                }
                return;
            }
        }

        // No intermediate state - show event selection
        openEventSelectionMenu(player);
    }

    /**
     * Open the start game menu - shown when player has no active game.
     * Allows starting a new game from the menu interface.
     */
    private void openStartGameMenu(Player player) {
        MenuLayout.StrategyGameLayout layout = menuLayout.getStrategyGameLayout();
        String title = messages.format(player, "menu.sgame.start_game_title");
        Inventory inv = Bukkit.createInventory(new StrategyGameMenuHolder(MenuType.START_GAME), layout.getSize(), title);

        // Game introduction / story
        ItemStack storyItem = createItem(Material.WRITTEN_BOOK,
            messages.format(player, "menu.sgame.story_title"),
            new String[]{
                messages.format(player, "menu.sgame.story_desc1"),
                messages.format(player, "menu.sgame.story_desc2"),
                messages.format(player, "menu.sgame.story_desc3")
            });
        safeSet(inv, 4, storyItem);

        // Start new game button
        Map<String, String> startMap = new HashMap<String, String>();
        startMap.put("starting_gold", String.valueOf(startingGold));
        startMap.put("starting_health", String.valueOf(startingHealth));
        startMap.put("max_stages", String.valueOf(maxStages));
        
        ItemStack startItem = createItem(Material.LIME_WOOL,
            messages.format(player, "menu.sgame.start_button"),
            new String[]{
                messages.format(player, "menu.sgame.start_button_lore1", startMap),
                messages.format(player, "menu.sgame.start_button_lore2", startMap),
                messages.format(player, "menu.sgame.start_button_lore3", startMap),
                "",
                messages.format(player, "menu.sgame.click_to_start"),
                "ID:start_new_game"
            });
        safeSet(inv, 13, startItem);

        // Close button
        ItemStack closeItem = createItem(Material.BARRIER,
            messages.format(player, "menu.sgame.close"),
            new String[]{"ID:close"});
        safeSet(inv, layout.getCloseSlot(), closeItem);

        player.openInventory(inv);
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

        // Only generate events if not already generated for this stage
        List<String> eventIds = session.getCurrentStageEvents();
        if (!session.isStageEventsGenerated() || eventIds.isEmpty()) {
            List<GameEvent> selectedEvents = selectEventsForStage(session, 3);
            session.clearCurrentStageEvents();
            for (GameEvent evt : selectedEvents) {
                session.addCurrentStageEvent(evt.getId());
            }
            session.setStageEventsGenerated(true);
            eventIds = session.getCurrentStageEvents();
            saveSession(session);
        }
        
        // Get the actual events from stored IDs
        List<GameEvent> selectedEvents = new ArrayList<GameEvent>();
        for (String eventId : eventIds) {
            GameEvent evt = findEvent(eventId);
            if (evt != null) {
                selectedEvents.add(evt);
            }
        }
        
        int[] eventSlots = {11, 13, 15};
        for (int i = 0; i < selectedEvents.size(); i++) {
            GameEvent event = selectedEvents.get(i);
            
            // Check if event has requirements and if player meets them
            boolean meetsRequirements = true;
            List<String> loreList = new ArrayList<String>();
            // Resolve i18n keys for description
            loreList.addAll(resolveI18nList(player, event.getDescription()));
            
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
                resolveI18n(player, event.getName()),
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
            resolveI18n(player, event.getName()),
            resolveI18nList(player, event.getDescription()).toArray(new String[0]));
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
                resolveI18n(player, choice.getText()),
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
        // Initialize enemy HP for multi-round combat
        session.setCurrentEnemyHp(enemy.getHealth());
        session.setCurrentEnemyMaxHp(enemy.getHealth());
        session.setBattleRound(1);
        saveSession(session);

        MenuLayout.StrategyGameLayout layout = menuLayout.getStrategyGameLayout();
        String title = messages.format(player, "menu.sgame.battle_menu_title");
        Inventory inv = Bukkit.createInventory(new StrategyGameMenuHolder(MenuType.BATTLE), layout.getSize(), title);

        // Enemy display with combat stats
        Map<String, String> enemyMap = new HashMap<String, String>();
        enemyMap.put("enemy", resolveI18n(player, enemy.getName()));
        enemyMap.put("power", String.valueOf(enemy.getPower()));
        enemyMap.put("attack", String.valueOf(enemy.getAttack()));
        enemyMap.put("defense", String.valueOf(enemy.getDefense()));
        enemyMap.put("health", String.valueOf(enemy.getHealth()));
        enemyMap.put("magic", String.valueOf(enemy.getMagic()));
        
        ItemStack enemyItem = createItem(Material.ZOMBIE_HEAD,
            messages.format(player, "menu.sgame.enemy_title", enemyMap),
            new String[]{
                messages.format(player, "menu.sgame.enemy_stats_lore", enemyMap),
                "&7" + resolveI18n(player, enemy.getDescription())
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

    /**
     * Open the battle action selection menu - Rock-Paper-Scissors style combat.
     * Shows both HP bars and allows player to choose Attack, Defense, or Skill.
     */
    public void openBattleActionMenu(Player player) {
        GameSession session = getOrLoadSession(player.getName());
        if (session == null || session.isEnded()) {
            return;
        }

        String enemyId = session.getCurrentEnemyId();
        BattleEnemy enemy = findEnemy(enemyId);
        if (enemy == null) {
            openMainMenu(player);
            return;
        }

        MenuLayout.StrategyGameLayout layout = menuLayout.getStrategyGameLayout();
        String title = messages.format(player, "menu.sgame.battle_action_title");
        Inventory inv = Bukkit.createInventory(new StrategyGameMenuHolder(MenuType.BATTLE_ACTION), layout.getSize(), title);

        // Round indicator at top center
        Map<String, String> roundMap = new HashMap<String, String>();
        roundMap.put("round", String.valueOf(session.getBattleRound()));
        ItemStack roundItem = createItem(Material.CLOCK,
            messages.format(player, "menu.sgame.battle_round", roundMap),
            new String[]{
                messages.format(player, "menu.sgame.battle_round_lore", roundMap)
            });
        safeSet(inv, 4, roundItem);

        // Player HP bar (left side, slot 0-2)
        int playerHp = session.getHealth();
        int playerMaxHp = session.getMaxHealth();
        String playerHpBar = createHpBar(playerHp, playerMaxHp);
        Map<String, String> playerMap = new HashMap<String, String>();
        playerMap.put("hp", String.valueOf(playerHp));
        playerMap.put("max_hp", String.valueOf(playerMaxHp));
        playerMap.put("hp_bar", playerHpBar);
        playerMap.put("attack", String.valueOf(session.getAttack() + getEquipmentAttackBonus(session)));
        playerMap.put("defense", String.valueOf(session.getDefense() + getEquipmentDefenseBonus(session)));
        playerMap.put("magic", String.valueOf(session.getMagic()));
        
        ItemStack playerItem = createItem(Material.PLAYER_HEAD,
            messages.format(player, "menu.sgame.your_status"),
            new String[]{
                messages.format(player, "menu.sgame.hp_bar_lore", playerMap),
                messages.format(player, "menu.sgame.combat_stats_lore", playerMap)
            });
        safeSet(inv, 0, playerItem);

        // Enemy HP bar (right side, slot 8)
        int enemyHp = session.getCurrentEnemyHp();
        int enemyMaxHp = session.getCurrentEnemyMaxHp();
        String enemyHpBar = createHpBar(enemyHp, enemyMaxHp);
        Map<String, String> enemyMap = new HashMap<String, String>();
        enemyMap.put("enemy", resolveI18n(player, enemy.getName()));
        enemyMap.put("hp", String.valueOf(enemyHp));
        enemyMap.put("max_hp", String.valueOf(enemyMaxHp));
        enemyMap.put("hp_bar", enemyHpBar);
        enemyMap.put("attack", String.valueOf(enemy.getAttack()));
        enemyMap.put("defense", String.valueOf(enemy.getDefense()));

        ItemStack enemyItem = createItem(Material.ZOMBIE_HEAD,
            messages.format(player, "menu.sgame.enemy_title", enemyMap),
            new String[]{
                messages.format(player, "menu.sgame.enemy_hp_bar_lore", enemyMap),
                messages.format(player, "menu.sgame.enemy_stats_lore", enemyMap)
            });
        safeSet(inv, 8, enemyItem);

        // Action buttons (row 2: slots 10, 13, 16)
        // Attack button - beats Skill
        ItemStack attackItem = createItem(Material.IRON_SWORD,
            messages.format(player, "menu.sgame.action_attack"),
            new String[]{
                messages.format(player, "menu.sgame.action_attack_lore"),
                messages.format(player, "menu.sgame.action_attack_hint"),
                "ID:action_attack"
            });
        safeSet(inv, 10, attackItem);

        // Defense button - beats Attack
        ItemStack defenseItem = createItem(Material.SHIELD,
            messages.format(player, "menu.sgame.action_defense"),
            new String[]{
                messages.format(player, "menu.sgame.action_defense_lore"),
                messages.format(player, "menu.sgame.action_defense_hint"),
                "ID:action_defense"
            });
        safeSet(inv, 13, defenseItem);

        // Skill button - beats Defense, requires magic
        boolean canUseSkill = session.getMagic() >= 10;
        Map<String, String> skillMap = new HashMap<String, String>();
        skillMap.put("magic_cost", "10");
        skillMap.put("current_magic", String.valueOf(session.getMagic()));
        ItemStack skillItem = createItem(canUseSkill ? Material.BLAZE_POWDER : Material.GUNPOWDER,
            messages.format(player, "menu.sgame.action_skill"),
            new String[]{
                messages.format(player, "menu.sgame.action_skill_lore"),
                messages.format(player, "menu.sgame.action_skill_hint"),
                messages.format(player, "menu.sgame.action_skill_cost", skillMap),
                canUseSkill ? "" : messages.format(player, "menu.sgame.not_enough_magic"),
                "ID:action_skill"
            });
        safeSet(inv, 16, skillItem);

        // VS indicator in the center
        ItemStack vsItem = createItem(Material.NETHER_STAR,
            "&c⚔ VS ⚔",
            new String[]{
                messages.format(player, "menu.sgame.vs_hint")
            });
        safeSet(inv, 22, vsItem);

        player.openInventory(inv);
    }

    /**
     * Create a visual HP bar using colored characters.
     * Color is based on overall HP percentage.
     */
    private String createHpBar(int current, int max) {
        int barLength = 10;
        int filledLength = max > 0 ? (int) Math.ceil((double) current / max * barLength) : 0;
        double hpPercent = max > 0 ? (double) current / max : 0;
        
        // Determine color based on overall HP percentage
        String color;
        if (hpPercent > 0.66) {
            color = "&a"; // Green for high HP (>66%)
        } else if (hpPercent > 0.33) {
            color = "&e"; // Yellow for medium HP (33-66%)
        } else {
            color = "&c"; // Red for low HP (<33%)
        }
        
        StringBuilder bar = new StringBuilder();
        bar.append(color);
        for (int i = 0; i < barLength; i++) {
            if (i < filledLength) {
                bar.append("█");
            } else {
                bar.append("&8░");
            }
        }
        return bar.toString();
    }

    public void openShopMenu(Player player) {
        GameSession session = getOrLoadSession(player.getName());
        if (session == null || session.isEnded()) {
            return;
        }

        if (shopItems.isEmpty() && equipments.isEmpty()) {
            player.sendMessage(messages.format(player, "sgame.shop_empty"));
            openMainMenu(player);
            return;
        }

        // Generate random shop offerings if not already generated for this shop visit
        if (session.getCurrentShopOfferings().isEmpty()) {
            generateShopOfferings(session);
            saveSession(session);
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

        // Display current shop offerings (randomly selected items/equipment with possible discounts)
        int[] itemSlots = {10, 11, 12, 13, 14, 15, 16};
        List<ShopOffering> offerings = session.getCurrentShopOfferings();
        for (int i = 0; i < offerings.size() && i < itemSlots.length; i++) {
            ShopOffering offering = offerings.get(i);
            ItemStack offeringItem = createShopOfferingItem(player, session, offering);
            safeSet(inv, itemSlots[i], offeringItem);
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
     * Generate random shop offerings from available items and equipment.
     * Each offering may have a random discount.
     */
    private void generateShopOfferings(GameSession session) {
        session.clearShopOfferings();
        
        // Combine all available items and equipment into a pool
        List<Object[]> pool = new ArrayList<Object[]>(); // [id, type]
        for (ShopItem item : shopItems) {
            pool.add(new Object[]{item.getId(), "item"});
        }
        for (Equipment eq : equipments) {
            pool.add(new Object[]{eq.getId(), "equipment"});
        }
        
        // Shuffle the pool and select items
        java.util.Collections.shuffle(pool, random);
        int itemCount = Math.min(MAX_SHOP_ITEMS, pool.size());
        
        for (int i = 0; i < itemCount; i++) {
            Object[] entry = pool.get(i);
            String id = (String) entry[0];
            String type = (String) entry[1];
            
            // Random discount based on configured chance and range
            int discount = 0;
            if (random.nextInt(100) < DISCOUNT_CHANCE) {
                discount = MIN_DISCOUNT + random.nextInt(MAX_DISCOUNT - MIN_DISCOUNT + 1);
            }
            
            session.addShopOffering(new ShopOffering(id, type, discount));
        }
    }

    /**
     * Create an ItemStack for displaying a shop offering with discount info.
     */
    private ItemStack createShopOfferingItem(Player player, GameSession session, ShopOffering offering) {
        if (offering.isEquipment()) {
            Equipment eq = findEquipment(offering.getId());
            if (eq == null) return createItem(Material.BARRIER, "&cError", new String[]{});
            
            int originalPrice = eq.getPrice();
            int finalPrice = offering.getDiscountedPrice(originalPrice);
            boolean canAfford = session.getGold() >= finalPrice;
            
            List<String> lore = new ArrayList<String>();
            
            // Show discount if applicable
            if (offering.hasDiscount()) {
                Map<String, String> discountMap = new HashMap<String, String>();
                discountMap.put("discount", String.valueOf(offering.getDiscount()));
                discountMap.put("original_price", String.valueOf(originalPrice));
                discountMap.put("final_price", String.valueOf(finalPrice));
                lore.add(messages.format(player, "menu.sgame.discount_lore", discountMap));
            }
            
            Map<String, String> priceMap = new HashMap<String, String>();
            priceMap.put("price", String.valueOf(finalPrice));
            String priceColor = canAfford ? "&a" : "&c";
            lore.add(priceColor + messages.format(player, "menu.sgame.item_price_lore", priceMap));
            
            // Equipment stats
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
            
            // Slot type
            Map<String, String> typeMap = new HashMap<String, String>();
            if ("weapon".equals(eq.getSlot())) {
                typeMap.put("type", messages.format(player, "menu.sgame.type_weapon"));
            } else if ("armor".equals(eq.getSlot())) {
                typeMap.put("type", messages.format(player, "menu.sgame.type_armor"));
            } else {
                typeMap.put("type", messages.format(player, "menu.sgame.type_accessory"));
            }
            lore.add(messages.format(player, "menu.sgame.equip_type_lore", typeMap));
            
            lore.add(canAfford ? messages.format(player, "menu.sgame.click_to_buy") : messages.format(player, "menu.sgame.not_enough_gold"));
            lore.add("ID:buy_" + offering.getId());
            
            return createItem(eq.getMaterial(), resolveI18n(player, eq.getName()), lore.toArray(new String[0]));
        } else {
            ShopItem item = findShopItem(offering.getId());
            if (item == null) return createItem(Material.BARRIER, "&cError", new String[]{});
            
            int originalPrice = item.getPrice();
            int finalPrice = offering.getDiscountedPrice(originalPrice);
            boolean canAfford = session.getGold() >= finalPrice;
            
            List<String> lore = new ArrayList<String>();
            
            // Show discount if applicable
            if (offering.hasDiscount()) {
                Map<String, String> discountMap = new HashMap<String, String>();
                discountMap.put("discount", String.valueOf(offering.getDiscount()));
                discountMap.put("original_price", String.valueOf(originalPrice));
                discountMap.put("final_price", String.valueOf(finalPrice));
                lore.add(messages.format(player, "menu.sgame.discount_lore", discountMap));
            }
            
            Map<String, String> priceMap = new HashMap<String, String>();
            priceMap.put("price", String.valueOf(finalPrice));
            String priceColor = canAfford ? "&a" : "&c";
            lore.add(priceColor + messages.format(player, "menu.sgame.item_price_lore", priceMap));
            
            lore.add("&7" + resolveI18n(player, item.getEffectDescription()));
            lore.add(canAfford ? messages.format(player, "menu.sgame.click_to_buy") : messages.format(player, "menu.sgame.not_enough_gold"));
            lore.add("ID:buy_" + offering.getId());
            
            return createItem(item.getMaterial(), resolveI18n(player, item.getName()), lore.toArray(new String[0]));
        }
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
            weaponMap.put("name", resolveI18n(player, weapon.getName()));
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
            armorMap.put("name", resolveI18n(player, armor.getName()));
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
            accessoryMap.put("name", resolveI18n(player, accessory.getName()));
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
            
            ItemStack eqItem = createItem(eq.getMaterial(), resolveI18n(player, eq.getName()), lore.toArray(new String[0]));
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
        
        // Handle start game menu separately since there's no active session
        if (menuType == MenuType.START_GAME) {
            handleStartGameMenuClick(player, id);
            return;
        }
        
        GameSession session = getOrLoadSession(player.getName());
        if (session == null || session.isEnded()) {
            player.closeInventory();
            return;
        }

        switch (menuType) {
            case MAIN:
                handleMainMenuClick(player, session, id);
                break;
            case START_GAME:
                // Already handled above
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
            case BATTLE_ACTION:
                handleBattleActionClick(player, session, id);
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

    /**
     * Handle clicks in the start game menu.
     */
    private void handleStartGameMenuClick(Player player, String id) {
        if ("start_new_game".equals(id)) {
            player.closeInventory();
            startGame(player);
        } else if ("close".equals(id)) {
            player.closeInventory();
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

            // Check and apply requirements
            if (event.hasRequirement()) {
                EventRequirement req = event.getRequirement();
                if (!req.checkRequirements(session)) {
                    player.sendMessage(messages.colorize(resolveI18n(player, req.getFailText())));
                    return;
                }
                // Apply gold cost
                req.applyGoldCost(session);
            }

            // Immediately set current event and save - player is now committed
            session.setCurrentEventId(eventId);
            session.addVisitedEvent(eventId);
            session.setLastEventId(eventId);
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
            // Open the multi-round battle action menu
            openBattleActionMenu(player);
        } else if ("flee".equals(id)) {
            resolveFlee(player, session, enemy);
        }
    }

    /**
     * Handle clicks in the battle action selection menu.
     * Processes Rock-Paper-Scissors style combat actions.
     */
    private void handleBattleActionClick(Player player, GameSession session, String id) {
        String enemyId = session.getCurrentEnemyId();
        BattleEnemy enemy = findEnemy(enemyId);
        if (enemy == null) {
            openMainMenu(player);
            return;
        }

        BattleAction playerAction = null;
        if ("action_attack".equals(id)) {
            playerAction = BattleAction.ATTACK;
        } else if ("action_defense".equals(id)) {
            playerAction = BattleAction.DEFENSE;
        } else if ("action_skill".equals(id)) {
            // Check if player has enough magic
            if (session.getMagic() < 10) {
                player.sendMessage(messages.format(player, "sgame.not_enough_magic"));
                return;
            }
            playerAction = BattleAction.SKILL;
        }

        if (playerAction != null) {
            resolveBattleRound(player, session, enemy, playerAction);
        }
    }

    private void handleShopMenuClick(Player player, GameSession session, String id) {
        // No back button - must leave shop properly

        if ("shop_leave".equals(id)) {
            // Leave shop and advance to next stage
            // Clear shop offerings for next shop visit
            session.clearShopOfferings();
            session.setCurrentEventId(null);
            session.incrementStage();
            saveSession(session);
            player.sendMessage(messages.format(player, "sgame.shop_left"));
            openMainMenu(player); // Will show next event selection or victory
            return;
        }

        if (id.startsWith("buy_")) {
            String itemId = id.substring(4);
            
            // Find the offering to get the discount
            ShopOffering offering = session.findShopOffering(itemId);
            if (offering == null) {
                return;
            }
            
            if (offering.isEquipment()) {
                // Handle equipment purchase from shop
                Equipment eq = findEquipment(itemId);
                if (eq == null) {
                    return;
                }
                
                int finalPrice = offering.getDiscountedPrice(eq.getPrice());
                if (session.getGold() < finalPrice) {
                    player.sendMessage(messages.format(player, "sgame.not_enough_gold"));
                    return;
                }
                
                // Purchase and equip
                session.addGold(-finalPrice);
                
                if ("weapon".equals(eq.getSlot())) {
                    session.setEquippedWeapon(itemId);
                } else if ("armor".equals(eq.getSlot())) {
                    session.setEquippedArmor(itemId);
                } else {
                    session.setEquippedAccessory(itemId);
                }
                
                saveSession(session);
                
                Map<String, String> map = new HashMap<String, String>();
                map.put("item", resolveI18n(player, eq.getName()));
                player.sendMessage(messages.format(player, "sgame.equipped", map));
            } else {
                // Handle regular item purchase
                ShopItem item = findShopItem(itemId);
                if (item == null) {
                    return;
                }
                
                int finalPrice = offering.getDiscountedPrice(item.getPrice());
                if (session.getGold() < finalPrice) {
                    player.sendMessage(messages.format(player, "sgame.not_enough_gold"));
                    return;
                }
                
                session.addGold(-finalPrice);
                applyShopItem(player, session, item);
                saveSession(session);
                
                Map<String, String> map = new HashMap<String, String>();
                map.put("item", resolveI18n(player, item.getName()));
                player.sendMessage(messages.format(player, "sgame.item_purchased", map));
            }
            
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
                map.put("item", resolveI18n(player, eq.getName()));
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
                map.put("item", resolveI18n(player, eq.getName()));
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

        // Show result - resolve i18n for result text
        Map<String, String> map = new HashMap<String, String>();
        map.put("result", resultText != null ? resolveI18n(player, resultText) : "");
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
        map.put("enemy", resolveI18n(player, enemy.getName()));
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

        // Clear battle and event state
        session.setCurrentEnemyId(null);
        session.setCurrentEventId(null);
        saveSession(session);
        
        player.closeInventory();
        openMainMenu(player);
    }

    /**
     * Resolve a single round of multi-round Rock-Paper-Scissors style combat.
     * - ATTACK beats SKILL (interrupts skill casting)
     * - SKILL beats DEFENSE (bypasses defense)
     * - DEFENSE beats ATTACK (blocks damage)
     * 
     * Enhanced with:
     * - Enemy special skills
     * - Status effects (poison, burn, freeze, stun)
     * - Critical hits and dodge mechanics
     */
    private void resolveBattleRound(Player player, GameSession session, BattleEnemy enemy, BattleAction playerAction) {
        Map<String, String> resultMap = new HashMap<String, String>();
        resultMap.put("enemy", resolveI18n(player, enemy.getName()));
        resultMap.put("round", String.valueOf(session.getBattleRound()));
        
        // Process player's status effects at the start of the round
        int statusDamage = session.processStatusEffects();
        if (statusDamage > 0) {
            session.addHealth(-statusDamage);
            Map<String, String> statusMap = new HashMap<String, String>();
            statusMap.put("damage", String.valueOf(statusDamage));
            player.sendMessage(messages.format(player, "sgame.status_damage", statusMap));
            
            // Check if player died from status damage
            if (session.getHealth() <= 0) {
                handleGameOverDeath(player, session);
                return;
            }
        }
        
        // Check if player is stunned or frozen
        if (session.isStunned() || session.isFrozen()) {
            String statusType = session.isStunned() ? "stun" : "freeze";
            player.sendMessage(messages.format(player, "sgame.status_skip_turn_" + statusType));
            
            // Enemy gets a free attack
            int freeDamage = Math.max(1, enemy.getAttack() / 2);
            session.addHealth(-freeDamage);
            
            Map<String, String> freeMap = new HashMap<String, String>();
            freeMap.put("damage", String.valueOf(freeDamage));
            player.sendMessage(messages.format(player, "sgame.enemy_free_attack", freeMap));
            
            if (session.getHealth() <= 0) {
                handleGameOverDeath(player, session);
                return;
            }
            
            session.incrementBattleRound();
            saveSession(session);
            openBattleActionMenu(player);
            return;
        }
        
        // Check if enemy uses a special skill
        EnemySkill usedSkill = tryUseEnemySkill(enemy);
        
        // Determine enemy action
        BattleAction enemyAction;
        if (usedSkill != null) {
            // Enemy is using a skill
            enemyAction = BattleAction.SKILL;
        } else {
            enemyAction = pickEnemyAction(enemy);
        }
        
        // Calculate base damage values
        int playerAttack = session.getAttack() + getEquipmentAttackBonus(session);
        int playerDefense = session.getDefense() + getEquipmentDefenseBonus(session);
        int playerMagic = session.getMagic() + getEquipmentMagicBonus(session);
        
        int enemyAttackStat = enemy.getAttack();
        int enemyDefense = enemy.getDefense();
        
        // Consistent random damage variance for balance
        int damageVariance = 6;
        
        // Determine round outcome based on RPS logic
        int playerDamageDealt = 0;
        int enemyDamageDealt = 0;
        String roundResult;
        boolean playerCrit = false;
        boolean enemyCrit = false;
        boolean playerDodged = false;
        boolean enemyDodged = false;
        
        // Check for player critical hit (10% base chance)
        int playerCritChance = 10 + (playerMagic / 10);
        playerCrit = random.nextInt(100) < playerCritChance;
        
        // Check for enemy critical hit
        enemyCrit = random.nextInt(100) < enemy.getCritChance();
        
        // Check for enemy dodge
        enemyDodged = random.nextInt(100) < enemy.getDodgeChance();
        
        // Player dodge based on defense stat
        playerDodged = random.nextInt(100) < (playerDefense / 4);
        
        // Determine advantage: 0 = tie, 1 = player wins RPS, -1 = enemy wins RPS
        int advantage = determineRpsAdvantage(playerAction, enemyAction);
        
        resultMap.put("player_action", getActionName(player, playerAction));
        resultMap.put("enemy_action", getActionName(player, enemyAction));
        
        if (advantage == 1) {
            // Player wins RPS - deal increased damage, take reduced damage
            if (playerAction == BattleAction.ATTACK) {
                // Attack beats Skill - interrupt enemy and deal full damage
                if (!enemyDodged) {
                    playerDamageDealt = Math.max(1, playerAttack - (enemyDefense / 4) + random.nextInt(damageVariance));
                    if (playerCrit) {
                        playerDamageDealt = (int)(playerDamageDealt * 1.5);
                    }
                }
                enemyDamageDealt = 0;
                roundResult = messages.format(player, "sgame.round_attack_beats_skill");
            } else if (playerAction == BattleAction.SKILL) {
                // Skill beats Defense - bypass defense and deal magic damage
                session.addMagic(-10); // Consume magic
                if (!enemyDodged) {
                    playerDamageDealt = Math.max(1, (playerMagic / 2) + random.nextInt(damageVariance));
                    if (playerCrit) {
                        playerDamageDealt = (int)(playerDamageDealt * 1.5);
                    }
                }
                enemyDamageDealt = 0;
                roundResult = messages.format(player, "sgame.round_skill_beats_defense");
            } else {
                // Defense beats Attack - block enemy and counter attack
                if (!enemyDodged) {
                    playerDamageDealt = Math.max(1, playerDefense / 2);
                }
                enemyDamageDealt = 0;
                roundResult = messages.format(player, "sgame.round_defense_beats_attack");
            }
        } else if (advantage == -1) {
            // Enemy wins RPS - enemy deals increased damage
            if (enemyAction == BattleAction.ATTACK) {
                // Enemy attack beats player skill
                playerDamageDealt = 0;
                if (!playerDodged) {
                    enemyDamageDealt = Math.max(1, enemyAttackStat - (playerDefense / 4) + random.nextInt(damageVariance));
                    if (enemyCrit) {
                        enemyDamageDealt = (int)(enemyDamageDealt * enemy.getCritMultiplier());
                    }
                }
                if (playerAction == BattleAction.SKILL) {
                    session.addMagic(-5); // Partial magic cost for interrupted skill
                }
                roundResult = messages.format(player, "sgame.round_enemy_attack_beats_skill");
            } else if (enemyAction == BattleAction.SKILL) {
                // Enemy skill beats player defense
                playerDamageDealt = 0;
                if (!playerDodged) {
                    if (usedSkill != null) {
                        enemyDamageDealt = Math.max(1, usedSkill.getPower() + random.nextInt(damageVariance));
                        // Apply status effect from skill
                        if (usedSkill.hasStatusEffect()) {
                            session.addStatusEffect(usedSkill.getStatusEffect(), 
                                usedSkill.getStatusDuration(), usedSkill.getStatusPower());
                            Map<String, String> effectMap = new HashMap<String, String>();
                            effectMap.put("effect", messages.format(player, "sgame.status_" + usedSkill.getStatusEffect()));
                            effectMap.put("duration", String.valueOf(usedSkill.getStatusDuration()));
                            player.sendMessage(messages.format(player, "sgame.status_applied", effectMap));
                        }
                    } else {
                        enemyDamageDealt = Math.max(1, (enemy.getMagic() / 2) + random.nextInt(damageVariance));
                    }
                    if (enemyCrit) {
                        enemyDamageDealt = (int)(enemyDamageDealt * enemy.getCritMultiplier());
                    }
                }
                roundResult = messages.format(player, "sgame.round_enemy_skill_beats_defense");
            } else {
                // Enemy defense beats player attack
                playerDamageDealt = 0;
                if (!playerDodged) {
                    enemyDamageDealt = Math.max(1, enemyDefense / 3);
                }
                roundResult = messages.format(player, "sgame.round_enemy_defense_beats_attack");
            }
        } else {
            // Tie - both deal reduced damage
            if (playerAction == BattleAction.SKILL) {
                session.addMagic(-10);
                if (!enemyDodged) {
                    playerDamageDealt = Math.max(1, playerMagic / 4 + random.nextInt(3));
                }
            } else {
                if (!enemyDodged) {
                    playerDamageDealt = Math.max(1, playerAttack / 3 + random.nextInt(3));
                }
            }
            if (!playerDodged) {
                enemyDamageDealt = Math.max(1, enemyAttackStat / 3 + random.nextInt(3));
            }
            roundResult = messages.format(player, "sgame.round_tie");
        }
        
        // Apply damage
        session.addCurrentEnemyHp(-playerDamageDealt);
        session.addHealth(-enemyDamageDealt);
        
        resultMap.put("player_damage", String.valueOf(playerDamageDealt));
        resultMap.put("enemy_damage", String.valueOf(enemyDamageDealt));
        resultMap.put("result", roundResult);
        
        // Add crit/dodge info to message
        if (playerCrit && playerDamageDealt > 0) {
            resultMap.put("crit_info", messages.format(player, "sgame.player_crit"));
        } else if (enemyCrit && enemyDamageDealt > 0) {
            resultMap.put("crit_info", messages.format(player, "sgame.enemy_crit"));
        } else if (enemyDodged && playerDamageDealt == 0 && advantage >= 0) {
            resultMap.put("crit_info", messages.format(player, "sgame.enemy_dodged"));
        } else if (playerDodged && enemyDamageDealt == 0 && advantage <= 0) {
            resultMap.put("crit_info", messages.format(player, "sgame.player_dodged"));
        } else {
            resultMap.put("crit_info", "");
        }
        
        // Send round result message
        player.sendMessage(messages.format(player, "sgame.battle_round_result", resultMap));
        
        // Check if battle ended
        if (session.getCurrentEnemyHp() <= 0) {
            // Victory! Clear status effects
            session.clearStatusEffects();
            int goldReward = enemy.getGoldReward();
            session.addGold(goldReward);
            session.incrementBattleVictories();
            session.incrementStage();
            // Clear battle and event state - battle is complete
            session.setCurrentEnemyId(null);
            session.setCurrentEnemyHp(0);
            session.setCurrentEventId(null);
            saveSession(session);
            
            Map<String, String> victoryMap = new HashMap<String, String>();
            victoryMap.put("enemy", resolveI18n(player, enemy.getName()));
            victoryMap.put("gold", String.valueOf(goldReward));
            victoryMap.put("rounds", String.valueOf(session.getBattleRound()));
            player.sendMessage(messages.format(player, "sgame.battle_victory_multiround", victoryMap));
            
            player.closeInventory();
            openMainMenu(player);
            return;
        }
        
        if (session.getHealth() <= 0) {
            // Player died
            handleGameOverDeath(player, session);
            return;
        }
        
        // Continue to next round
        session.incrementBattleRound();
        saveSession(session);
        
        // Refresh the battle action menu for next round
        openBattleActionMenu(player);
    }

    /**
     * Determine RPS advantage: 1 = player wins, -1 = enemy wins, 0 = tie
     * ATTACK beats SKILL, SKILL beats DEFENSE, DEFENSE beats ATTACK
     */
    private int determineRpsAdvantage(BattleAction playerAction, BattleAction enemyAction) {
        if (playerAction == enemyAction) {
            return 0; // Tie
        }
        
        if ((playerAction == BattleAction.ATTACK && enemyAction == BattleAction.SKILL) ||
            (playerAction == BattleAction.SKILL && enemyAction == BattleAction.DEFENSE) ||
            (playerAction == BattleAction.DEFENSE && enemyAction == BattleAction.ATTACK)) {
            return 1; // Player wins
        }
        
        return -1; // Enemy wins
    }

    /**
     * Pick an enemy action based on enemy stats.
     * Enemies with more magic tend to use skill, more defense tends to use defense, etc.
     */
    private BattleAction pickEnemyAction(BattleEnemy enemy) {
        int attackWeight = enemy.getAttack() + 10;
        int defenseWeight = enemy.getDefense() + 5;
        int skillWeight = enemy.getMagic() > 0 ? enemy.getMagic() + 5 : 2;
        
        int total = attackWeight + defenseWeight + skillWeight;
        int roll = random.nextInt(total);
        
        if (roll < attackWeight) {
            return BattleAction.ATTACK;
        } else if (roll < attackWeight + defenseWeight) {
            return BattleAction.DEFENSE;
        } else {
            return BattleAction.SKILL;
        }
    }

    /**
     * Try to use one of the enemy's special skills.
     * Returns the skill if one is used, null otherwise.
     */
    private EnemySkill tryUseEnemySkill(BattleEnemy enemy) {
        if (!enemy.hasSkills()) {
            return null;
        }
        
        for (EnemySkill skill : enemy.getSkills()) {
            if (random.nextInt(100) < skill.getChance()) {
                return skill;
            }
        }
        return null;
    }

    /**
     * Get localized action name for display.
     */
    private String getActionName(Player player, BattleAction action) {
        switch (action) {
            case ATTACK:
                return messages.format(player, "menu.sgame.action_attack_name");
            case DEFENSE:
                return messages.format(player, "menu.sgame.action_defense_name");
            case SKILL:
                return messages.format(player, "menu.sgame.action_skill_name");
            default:
                return "?";
        }
    }

    private void resolveFlee(Player player, GameSession session, BattleEnemy enemy) {
        // Flee success based on player speed (defense stat) vs enemy power
        int fleeChance = 50 + (session.getDefense() * 2) - (enemy.getPower() / 3);
        fleeChance = Math.min(90, Math.max(20, fleeChance)); // Clamp between 20-90%
        
        if (random.nextInt(100) < fleeChance) {
            // Clear status effects on successful flee
            session.clearStatusEffects();
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
                rewardNames.add(resolveI18n(player, reward.getName()));
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
     * - Fixed stage events (must appear at specific stages)
     * - Exclusive events (only that event appears at the stage)
     */
    private List<GameEvent> selectEventsForStage(GameSession session, int count) {
        int currentStage = session.getCurrentStage();
        
        // First, check for exclusive fixed-stage events (like final boss)
        for (GameEvent event : gameEvents) {
            if (event.hasFixedStage() && event.getFixedStage() == currentStage && event.isExclusive()) {
                // This is an exclusive event for this stage - only return this event
                List<GameEvent> exclusive = new ArrayList<GameEvent>();
                exclusive.add(event);
                return exclusive;
            }
        }
        
        List<GameEvent> available = new ArrayList<GameEvent>();
        List<GameEvent> fixedForThisStage = new ArrayList<GameEvent>();
        Map<GameEvent, Integer> weights = new HashMap<GameEvent, Integer>();
        
        String lastEventId = session.getLastEventId();
        GameEvent lastEvent = lastEventId != null ? findEvent(lastEventId) : null;
        
        for (GameEvent event : gameEvents) {
            // Skip events with fixed stages that don't match current stage
            if (event.hasFixedStage() && event.getFixedStage() != currentStage) {
                continue;
            }
            
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
            
            // If this is a fixed-stage event for current stage, add to priority list
            if (event.hasFixedStage() && event.getFixedStage() == currentStage) {
                fixedForThisStage.add(event);
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
        
        // Start with fixed events for this stage (they must appear)
        List<GameEvent> selected = new ArrayList<GameEvent>();
        for (GameEvent fixed : fixedForThisStage) {
            if (selected.size() < count) {
                selected.add(fixed);
                available.remove(fixed);
            }
        }
        
        // Fill remaining slots with weighted random selection
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

    /**
     * Resolve an i18n key to its translated text for the given player.
     * If the key doesn't exist in the language file, returns the key itself as fallback.
     */
    private String resolveI18n(Player player, String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        // Try to resolve as i18n key
        String resolved = messages.format(player, key);
        // If the key wasn't found, messages.format returns the key with "missing:" prefix or similar
        // In that case, just return the original key as fallback (might be hardcoded text)
        if (resolved.equals(key) || resolved.contains("missing")) {
            return key;
        }
        return resolved;
    }

    /**
     * Resolve a list of i18n keys to their translated texts.
     */
    private List<String> resolveI18nList(Player player, List<String> keys) {
        List<String> result = new ArrayList<String>();
        for (String key : keys) {
            result.add(resolveI18n(player, key));
        }
        return result;
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
        
        // Load multi-round battle state
        session.setCurrentEnemyHp(data.getInt("sgame.current_enemy_hp", 0));
        session.setCurrentEnemyMaxHp(data.getInt("sgame.current_enemy_max_hp", 0));
        session.setBattleRound(data.getInt("sgame.battle_round", 1));
        
        // Load current stage events
        List<String> stageEvents = data.getStringList("sgame.current_stage_events");
        for (String eventId : stageEvents) {
            session.addCurrentStageEvent(eventId);
        }
        session.setStageEventsGenerated(data.getBoolean("sgame.stage_events_generated", false));
        
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
        
        // Save multi-round battle state
        data.set("sgame.current_enemy_hp", session.getCurrentEnemyHp());
        data.set("sgame.current_enemy_max_hp", session.getCurrentEnemyMaxHp());
        data.set("sgame.battle_round", session.getBattleRound());
        
        // Save current stage events
        data.set("sgame.current_stage_events", session.getCurrentStageEvents());
        data.set("sgame.stage_events_generated", session.isStageEventsGenerated());
        
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
        MAIN, START_GAME, EVENT_SELECTION, EVENT, BATTLE, BATTLE_ACTION, SHOP, EQUIPMENT
    }

    /**
     * Battle actions for Rock-Paper-Scissors style combat.
     * - ATTACK beats SKILL (interrupts skill casting)
     * - SKILL beats DEFENSE (bypasses defense)
     * - DEFENSE beats ATTACK (blocks damage)
     */
    public enum BattleAction {
        ATTACK, DEFENSE, SKILL
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
        
        // Multi-round battle tracking
        private int currentEnemyHp;
        private int currentEnemyMaxHp;
        private int battleRound;
        
        // Shop offering tracking - stores current shop items with discounts
        private final List<ShopOffering> currentShopOfferings;
        
        // Current stage event options - only refreshed when entering new stage
        private final List<String> currentStageEvents;
        private boolean stageEventsGenerated;
        
        // Status effects tracking (effect_name -> [duration, power])
        private final Map<String, int[]> statusEffects;

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
            this.currentShopOfferings = new ArrayList<ShopOffering>();
            this.currentStageEvents = new ArrayList<String>();
            this.stageEventsGenerated = false;
            this.statusEffects = new HashMap<String, int[]>();
            
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
        void incrementStage() { 
            this.currentStage++; 
            // Clear current stage events so new ones are generated for next stage
            clearCurrentStageEvents();
        }
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
        
        // Multi-round battle tracking
        int getCurrentEnemyHp() { return currentEnemyHp; }
        void setCurrentEnemyHp(int hp) { this.currentEnemyHp = Math.max(0, hp); }
        void addCurrentEnemyHp(int amount) { this.currentEnemyHp = Math.max(0, this.currentEnemyHp + amount); }
        int getCurrentEnemyMaxHp() { return currentEnemyMaxHp; }
        void setCurrentEnemyMaxHp(int hp) { this.currentEnemyMaxHp = hp; }
        int getBattleRound() { return battleRound; }
        void setBattleRound(int round) { this.battleRound = round; }
        void incrementBattleRound() { this.battleRound++; }
        
        // Shop offering management
        List<ShopOffering> getCurrentShopOfferings() { return currentShopOfferings; }
        void clearShopOfferings() { currentShopOfferings.clear(); }
        void addShopOffering(ShopOffering offering) { currentShopOfferings.add(offering); }
        ShopOffering findShopOffering(String id) {
            for (ShopOffering offering : currentShopOfferings) {
                if (offering.getId().equals(id)) {
                    return offering;
                }
            }
            return null;
        }
        
        // Current stage events management
        List<String> getCurrentStageEvents() { return currentStageEvents; }
        void clearCurrentStageEvents() { 
            currentStageEvents.clear(); 
            stageEventsGenerated = false;
        }
        void addCurrentStageEvent(String eventId) { currentStageEvents.add(eventId); }
        boolean isStageEventsGenerated() { return stageEventsGenerated; }
        void setStageEventsGenerated(boolean generated) { this.stageEventsGenerated = generated; }
        
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
        
        // Status effects management
        Map<String, int[]> getStatusEffects() { return statusEffects; }
        boolean hasStatusEffect(String effect) { 
            return statusEffects.containsKey(effect) && statusEffects.get(effect)[0] > 0; 
        }
        void addStatusEffect(String effect, int duration, int power) {
            statusEffects.put(effect, new int[]{duration, power});
        }
        void removeStatusEffect(String effect) {
            statusEffects.remove(effect);
        }
        void clearStatusEffects() {
            statusEffects.clear();
        }
        /**
         * Process status effects at the start of each round.
         * Returns total damage taken from DoT effects.
         */
        int processStatusEffects() {
            int totalDamage = 0;
            List<String> expired = new ArrayList<String>();
            
            for (Map.Entry<String, int[]> entry : statusEffects.entrySet()) {
                String effect = entry.getKey();
                int[] data = entry.getValue();
                int duration = data[0];
                int power = data[1];
                
                // Apply damage for DoT effects
                if ("poison".equals(effect) || "burn".equals(effect)) {
                    totalDamage += power;
                }
                
                // Reduce duration
                data[0] = duration - 1;
                if (data[0] <= 0) {
                    expired.add(effect);
                }
            }
            
            // Remove expired effects
            for (String effect : expired) {
                statusEffects.remove(effect);
            }
            
            return totalDamage;
        }
        boolean isStunned() {
            return hasStatusEffect("stun");
        }
        boolean isFrozen() {
            return hasStatusEffect("freeze");
        }
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
        private final int fixedStage; // Stage where this event MUST appear (-1 = no fixed stage)
        private final boolean exclusive; // If true, only this event appears at fixed stage

        GameEvent(String id, String name, List<String> description, List<EventChoice> choices, 
                  String eventType, EventRequirement requirement, int weight, 
                  List<String> followupEvents, List<String> prerequisiteEvents,
                  int fixedStage, boolean exclusive) {
            this.id = id;
            this.name = name;
            this.description = description != null ? description : new ArrayList<String>();
            this.choices = choices != null ? choices : new ArrayList<EventChoice>();
            this.eventType = eventType != null ? eventType : "story";
            this.requirement = requirement;
            this.weight = weight > 0 ? weight : 10;
            this.followupEvents = followupEvents != null ? followupEvents : new ArrayList<String>();
            this.prerequisiteEvents = prerequisiteEvents != null ? prerequisiteEvents : new ArrayList<String>();
            this.fixedStage = fixedStage;
            this.exclusive = exclusive;
        }

        static GameEvent fromSection(String id, ConfigurationSection section) {
            String name = section.getString("name", id);
            List<String> desc = section.getStringList("description");
            String eventType = section.getString("type", "story");
            EventRequirement requirement = EventRequirement.fromSection(section.getConfigurationSection("requirement"));
            int weight = section.getInt("weight", 10);
            List<String> followups = section.getStringList("followup_events");
            List<String> prereqs = section.getStringList("prerequisite_events");
            int fixedStage = section.getInt("fixed_stage", -1);
            boolean exclusive = section.getBoolean("exclusive", false);
            
            List<EventChoice> choices = new ArrayList<EventChoice>();
            List<Map<?, ?>> choiceList = section.getMapList("choices");
            for (Map<?, ?> raw : choiceList) {
                EventChoice choice = EventChoice.fromMap(raw);
                if (choice != null) {
                    choices.add(choice);
                }
            }
            
            return new GameEvent(id, name, desc, choices, eventType, requirement, weight, followups, prereqs, fixedStage, exclusive);
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
        int getFixedStage() { return fixedStage; }
        boolean hasFixedStage() { return fixedStage >= 0; }
        boolean isExclusive() { return exclusive; }
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

    /**
     * Represents an item currently offered in the shop with potential discount.
     * Can be either a ShopItem (consumable) or Equipment.
     */
    private static class ShopOffering {
        private final String id;
        private final String type; // "item" or "equipment"
        private final int discount; // Discount percentage
        
        ShopOffering(String id, String type, int discount) {
            this.id = id;
            this.type = type;
            this.discount = Math.min(MAX_DISCOUNT, Math.max(0, discount));
        }
        
        String getId() { return id; }
        String getType() { return type; }
        int getDiscount() { return discount; }
        boolean isEquipment() { return "equipment".equals(type); }
        boolean hasDiscount() { return discount > 0; }
        
        /**
         * Calculate discounted price from original price.
         */
        int getDiscountedPrice(int originalPrice) {
            return originalPrice - (originalPrice * discount / 100);
        }
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
        // Enemy special abilities
        private final List<EnemySkill> skills;
        private final int critChance; // Percentage chance for critical hit
        private final double critMultiplier; // Damage multiplier on crit
        private final int dodgeChance; // Percentage chance to dodge

        BattleEnemy(String id, String name, String description, int power, int damage, int goldReward, int minStage,
                   int health, int attack, int defense, int magic,
                   List<EnemySkill> skills, int critChance, double critMultiplier, int dodgeChance) {
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
            this.skills = skills != null ? skills : new ArrayList<EnemySkill>();
            this.critChance = Math.min(50, Math.max(0, critChance));
            this.critMultiplier = critMultiplier > 1.0 ? critMultiplier : 1.5;
            this.dodgeChance = Math.min(40, Math.max(0, dodgeChance));
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
            int critChance = parseInt(raw.get("crit_chance"));
            double critMult = parseDouble(raw.get("crit_multiplier"));
            int dodgeChance = parseInt(raw.get("dodge_chance"));
            
            // Parse skills
            List<EnemySkill> skills = new ArrayList<EnemySkill>();
            Object skillsObj = raw.get("skills");
            if (skillsObj instanceof List) {
                for (Object skillRaw : (List<?>) skillsObj) {
                    if (skillRaw instanceof Map) {
                        EnemySkill skill = EnemySkill.fromMap((Map<?, ?>) skillRaw);
                        if (skill != null) {
                            skills.add(skill);
                        }
                    }
                }
            }
            
            return new BattleEnemy(id, name, desc, power, damage, gold, minStage, health, attack, defense, magic,
                                  skills, critChance, critMult, dodgeChance);
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
        List<EnemySkill> getSkills() { return skills; }
        boolean hasSkills() { return !skills.isEmpty(); }
        int getCritChance() { return critChance; }
        double getCritMultiplier() { return critMultiplier; }
        int getDodgeChance() { return dodgeChance; }
    }

    /**
     * Represents an enemy special skill/ability.
     */
    private static class EnemySkill {
        private final String id;
        private final String name;
        private final String description;
        private final String type; // "damage", "heal", "buff", "debuff", "status"
        private final int power; // Damage/heal amount
        private final int chance; // Percentage chance to use this skill
        private final String statusEffect; // "poison", "burn", "freeze", "stun" etc.
        private final int statusDuration; // Number of rounds the effect lasts
        private final int statusPower; // Damage per turn for DoT effects
        
        EnemySkill(String id, String name, String description, String type, int power, int chance,
                   String statusEffect, int statusDuration, int statusPower) {
            this.id = id;
            this.name = name != null ? name : id;
            this.description = description != null ? description : "";
            this.type = type != null ? type : "damage";
            this.power = power;
            this.chance = Math.min(100, Math.max(1, chance));
            this.statusEffect = statusEffect;
            this.statusDuration = statusDuration > 0 ? statusDuration : 2;
            this.statusPower = statusPower;
        }
        
        static EnemySkill fromMap(Map<?, ?> raw) {
            if (raw == null) return null;
            String id = raw.get("id") != null ? raw.get("id").toString() : "unknown";
            String name = raw.get("name") != null ? raw.get("name").toString() : id;
            String desc = raw.get("description") != null ? raw.get("description").toString() : "";
            String type = raw.get("type") != null ? raw.get("type").toString() : "damage";
            int power = parseInt(raw.get("power"));
            int chance = parseInt(raw.get("chance"));
            String statusEffect = raw.get("status_effect") != null ? raw.get("status_effect").toString() : null;
            int statusDuration = parseInt(raw.get("status_duration"));
            int statusPower = parseInt(raw.get("status_power"));
            return new EnemySkill(id, name, desc, type, power, chance, statusEffect, statusDuration, statusPower);
        }
        
        String getId() { return id; }
        String getName() { return name; }
        String getDescription() { return description; }
        String getType() { return type; }
        int getPower() { return power; }
        int getChance() { return chance; }
        String getStatusEffect() { return statusEffect; }
        int getStatusDuration() { return statusDuration; }
        int getStatusPower() { return statusPower; }
        boolean hasStatusEffect() { return statusEffect != null && !statusEffect.isEmpty(); }
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
    
    private static double parseDouble(Object o) {
        if (o == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
