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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Card Battle Game Module - A virtual card battle game with PvP and PvE modes.
 * Players engage in turn-based card combat in a chest menu interface.
 * 
 * Features:
 * - Virtual health system (not affecting real player health)
 * - Various card types with different effects (attack, defense, heal, special)
 * - PvE mode against AI opponents
 * - PvP mode for player vs player battles
 * - Turn-based combat with card hand management
 */
public class CardBattleManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final MenuLayout menuLayout;
    private final File configFile;
    private final File storageDir;
    private final Random random = new Random();

    // Game configuration
    private boolean enabled = true;
    private int startingHealth = 30;
    private int startingHandSize = 5;
    private int maxHandSize = 7;
    private int drawPerTurn = 1;
    private int startingMana = 3;
    private int manaPerTurn = 1;
    private int maxMana = 10;
    private int turnTimeLimit = 60; // seconds
    private List<String> winRewardCommands = new ArrayList<String>();

    // Card definitions
    private final Map<String, CardDefinition> cards = new HashMap<String, CardDefinition>();

    // AI opponent definitions
    private final Map<String, AIOpponent> aiOpponents = new HashMap<String, AIOpponent>();

    // Active game sessions
    private final Map<String, BattleSession> activeSessions = new HashMap<String, BattleSession>();

    // PvP pending invitations (inviter -> target)
    private final Map<String, String> pendingInvitations = new HashMap<String, String>();

    // Callback for opening games menu (set by plugin)
    private java.util.function.Consumer<Player> openGamesMenuCallback;

    public CardBattleManager(JavaPlugin plugin, Messages messages, File configFile, MenuLayout menuLayout) {
        this.plugin = plugin;
        this.messages = messages;
        this.configFile = configFile;
        this.menuLayout = menuLayout;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String dataDir = config.getString("storage.data_dir", "userdata");
        storageDir = new File(plugin.getDataFolder(), dataDir);
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            plugin.getLogger().warning("無法創建數據目錄: " + storageDir.getAbsolutePath());
        }
        loadConfig(config);
    }

    /**
     * Set callback for opening games menu.
     */
    public void setOpenGamesMenuCallback(java.util.function.Consumer<Player> callback) {
        this.openGamesMenuCallback = callback;
    }

    private void loadConfig(YamlConfiguration config) {
        enabled = config.getBoolean("game.enabled", true);
        startingHealth = config.getInt("game.starting_health", 30);
        startingHandSize = config.getInt("game.starting_hand_size", 5);
        maxHandSize = config.getInt("game.max_hand_size", 7);
        drawPerTurn = config.getInt("game.draw_per_turn", 1);
        startingMana = config.getInt("game.starting_mana", 3);
        manaPerTurn = config.getInt("game.mana_per_turn", 1);
        maxMana = config.getInt("game.max_mana", 10);
        turnTimeLimit = config.getInt("game.turn_time_limit", 60);
        winRewardCommands = config.getStringList("rewards.win_commands");

        // Load cards
        cards.clear();
        ConfigurationSection cardsSection = config.getConfigurationSection("cards");
        if (cardsSection != null) {
            for (String cardId : cardsSection.getKeys(false)) {
                ConfigurationSection cardSection = cardsSection.getConfigurationSection(cardId);
                if (cardSection != null) {
                    CardDefinition card = CardDefinition.fromSection(cardId, cardSection);
                    if (card != null) {
                        cards.put(cardId, card);
                    }
                }
            }
        }

        // Load AI opponents
        aiOpponents.clear();
        ConfigurationSection aiSection = config.getConfigurationSection("ai_opponents");
        if (aiSection != null) {
            for (String aiId : aiSection.getKeys(false)) {
                ConfigurationSection opponentSection = aiSection.getConfigurationSection(aiId);
                if (opponentSection != null) {
                    AIOpponent opponent = AIOpponent.fromSection(aiId, opponentSection);
                    if (opponent != null) {
                        aiOpponents.put(aiId, opponent);
                    }
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ============ Public API ============

    /**
     * Open the main card battle menu.
     */
    public void openMenu(Player player) {
        if (!enabled) {
            player.sendMessage(messages.format(player, "cardbattle.disabled"));
            return;
        }

        BattleSession session = activeSessions.get(player.getName());
        if (session != null && !session.isEnded()) {
            openBattleMenu(player, session);
        } else {
            openMainMenu(player);
        }
    }

    /**
     * Start a PvE game against an AI opponent.
     */
    public void startPvEGame(Player player, String opponentId) {
        if (!enabled) {
            player.sendMessage(messages.format(player, "cardbattle.disabled"));
            return;
        }

        if (activeSessions.containsKey(player.getName())) {
            player.sendMessage(messages.format(player, "cardbattle.already_in_game"));
            return;
        }

        AIOpponent opponent = aiOpponents.get(opponentId);
        if (opponent == null) {
            opponent = getDefaultAI();
        }

        BattleSession session = new BattleSession(player.getName(), null, opponent, startingHealth, startingMana);
        initializePlayerHand(session, true);
        initializeAIHand(session);
        activeSessions.put(player.getName(), session);

        Map<String, String> map = new HashMap<String, String>();
        map.put("opponent", resolveI18n(player, opponent.getName()));
        player.sendMessage(messages.format(player, "cardbattle.game_started_pve", map));

        openBattleMenu(player, session);
    }

    /**
     * Invite another player to a PvP battle.
     */
    public void invitePvP(Player player, String targetName) {
        if (!enabled) {
            player.sendMessage(messages.format(player, "cardbattle.disabled"));
            return;
        }

        if (activeSessions.containsKey(player.getName())) {
            player.sendMessage(messages.format(player, "cardbattle.already_in_game"));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage(messages.format(player, "cardbattle.player_not_found"));
            return;
        }

        if (target.getName().equals(player.getName())) {
            player.sendMessage(messages.format(player, "cardbattle.cannot_self"));
            return;
        }

        if (activeSessions.containsKey(target.getName())) {
            player.sendMessage(messages.format(player, "cardbattle.target_in_game"));
            return;
        }

        pendingInvitations.put(player.getName(), target.getName());

        Map<String, String> map = new HashMap<String, String>();
        map.put("player", player.getName());
        map.put("target", target.getName());
        player.sendMessage(messages.format(player, "cardbattle.invite_sent", map));
        target.sendMessage(messages.format(target, "cardbattle.invite_received", map));
    }

    /**
     * Accept a PvP invitation.
     */
    public void acceptPvP(Player player) {
        String inviter = null;
        for (Map.Entry<String, String> entry : pendingInvitations.entrySet()) {
            if (entry.getValue().equals(player.getName())) {
                inviter = entry.getKey();
                break;
            }
        }

        if (inviter == null) {
            player.sendMessage(messages.format(player, "cardbattle.no_pending_invite"));
            return;
        }

        Player inviterPlayer = Bukkit.getPlayer(inviter);
        if (inviterPlayer == null || !inviterPlayer.isOnline()) {
            pendingInvitations.remove(inviter);
            player.sendMessage(messages.format(player, "cardbattle.inviter_offline"));
            return;
        }

        pendingInvitations.remove(inviter);

        // Create PvP session
        BattleSession session = new BattleSession(inviter, player.getName(), null, startingHealth, startingMana);
        initializePlayerHand(session, true);
        initializePlayerHand(session, false);
        activeSessions.put(inviter, session);
        activeSessions.put(player.getName(), session);

        Map<String, String> map = new HashMap<String, String>();
        map.put("opponent", player.getName());
        inviterPlayer.sendMessage(messages.format(inviterPlayer, "cardbattle.pvp_started", map));
        map.put("opponent", inviter);
        player.sendMessage(messages.format(player, "cardbattle.pvp_started", map));

        openBattleMenu(inviterPlayer, session);
        openBattleMenu(player, session);
    }

    /**
     * Decline a PvP invitation.
     */
    public void declinePvP(Player player) {
        String inviter = null;
        for (Map.Entry<String, String> entry : pendingInvitations.entrySet()) {
            if (entry.getValue().equals(player.getName())) {
                inviter = entry.getKey();
                break;
            }
        }

        if (inviter == null) {
            player.sendMessage(messages.format(player, "cardbattle.no_pending_invite"));
            return;
        }

        pendingInvitations.remove(inviter);

        Player inviterPlayer = Bukkit.getPlayer(inviter);
        if (inviterPlayer != null && inviterPlayer.isOnline()) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("player", player.getName());
            inviterPlayer.sendMessage(messages.format(inviterPlayer, "cardbattle.invite_declined", map));
        }

        player.sendMessage(messages.format(player, "cardbattle.decline_success"));
    }

    /**
     * Play a card from hand.
     */
    public void playCard(Player player, int handIndex) {
        BattleSession session = activeSessions.get(player.getName());
        if (session == null || session.isEnded()) {
            player.sendMessage(messages.format(player, "cardbattle.no_active_game"));
            return;
        }

        boolean isPlayer1 = player.getName().equals(session.getPlayer1Name());
        if (!session.isPlayerTurn(isPlayer1)) {
            player.sendMessage(messages.format(player, "cardbattle.not_your_turn"));
            return;
        }

        List<String> hand = isPlayer1 ? session.getPlayer1Hand() : session.getPlayer2Hand();
        if (handIndex < 0 || handIndex >= hand.size()) {
            player.sendMessage(messages.format(player, "cardbattle.invalid_card"));
            return;
        }

        String cardId = hand.get(handIndex);
        CardDefinition card = cards.get(cardId);
        if (card == null) {
            player.sendMessage(messages.format(player, "cardbattle.invalid_card"));
            return;
        }

        int currentMana = isPlayer1 ? session.getPlayer1Mana() : session.getPlayer2Mana();
        if (card.getManaCost() > currentMana) {
            player.sendMessage(messages.format(player, "cardbattle.not_enough_mana"));
            return;
        }

        // Remove card from hand and deduct mana
        hand.remove(handIndex);
        if (isPlayer1) {
            session.setPlayer1Mana(currentMana - card.getManaCost());
        } else {
            session.setPlayer2Mana(currentMana - card.getManaCost());
        }

        // Apply card effect
        applyCardEffect(player, session, card, isPlayer1);

        // Check for game end
        if (checkGameEnd(session)) {
            endGame(session);
            return;
        }

        // Refresh menu
        openBattleMenu(player, session);
    }

    /**
     * End turn and pass to opponent.
     */
    public void endTurn(Player player) {
        BattleSession session = activeSessions.get(player.getName());
        if (session == null || session.isEnded()) {
            player.sendMessage(messages.format(player, "cardbattle.no_active_game"));
            return;
        }

        boolean isPlayer1 = player.getName().equals(session.getPlayer1Name());
        if (!session.isPlayerTurn(isPlayer1)) {
            player.sendMessage(messages.format(player, "cardbattle.not_your_turn"));
            return;
        }

        // Switch turn
        session.setPlayer1Turn(!session.isPlayer1Turn());
        session.incrementTurnCount();

        // Give mana to next player
        if (session.isPlayer1Turn()) {
            int newMana = Math.min(maxMana, session.getPlayer1Mana() + manaPerTurn);
            session.setPlayer1Mana(newMana);
            drawCards(session, true, drawPerTurn);
        } else {
            int newMana = Math.min(maxMana, session.getPlayer2Mana() + manaPerTurn);
            session.setPlayer2Mana(newMana);
            drawCards(session, false, drawPerTurn);
        }

        player.sendMessage(messages.format(player, "cardbattle.turn_ended"));

        // If PvE and it's AI turn, process AI turn
        if (session.isPvE() && !session.isPlayer1Turn()) {
            processAITurn(session);
            // After AI turn, switch back to player
            session.setPlayer1Turn(true);
            session.incrementTurnCount();
            int newMana = Math.min(maxMana, session.getPlayer1Mana() + manaPerTurn);
            session.setPlayer1Mana(newMana);
            drawCards(session, true, drawPerTurn);

            if (checkGameEnd(session)) {
                endGame(session);
                return;
            }

            Player p1 = Bukkit.getPlayer(session.getPlayer1Name());
            if (p1 != null && p1.isOnline()) {
                openBattleMenu(p1, session);
            }
        } else if (!session.isPvE()) {
            // PvP - notify other player about turn end and that it's their turn
            String otherName = session.isPlayer1Turn() ? session.getPlayer1Name() : session.getPlayer2Name();
            Player other = Bukkit.getPlayer(otherName);
            if (other != null && other.isOnline()) {
                Map<String, String> endTurnMap = new HashMap<String, String>();
                endTurnMap.put("player", player.getName());
                other.sendMessage(messages.format(other, "cardbattle.opponent_end_turn", endTurnMap));
                other.sendMessage(messages.format(other, "cardbattle.your_turn"));
                openBattleMenu(other, session);
            }
        }

        // Save session for PvE games
        if (session.isPvE()) {
            saveSession(session);
        }

        openBattleMenu(player, session);
    }

    /**
     * Surrender the game.
     */
    public void surrender(Player player) {
        BattleSession session = activeSessions.get(player.getName());
        if (session == null || session.isEnded()) {
            player.sendMessage(messages.format(player, "cardbattle.no_active_game"));
            return;
        }

        boolean isPlayer1 = player.getName().equals(session.getPlayer1Name());
        if (isPlayer1) {
            session.setPlayer1Health(0);
        } else {
            session.setPlayer2Health(0);
        }

        player.sendMessage(messages.format(player, "cardbattle.surrendered"));
        
        // Notify opponent about surrender in PvP
        if (!session.isPvE()) {
            String opponentName = isPlayer1 ? session.getPlayer2Name() : session.getPlayer1Name();
            Player opponent = Bukkit.getPlayer(opponentName);
            if (opponent != null && opponent.isOnline()) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("player", player.getName());
                opponent.sendMessage(messages.format(opponent, "cardbattle.opponent_surrendered", map));
            }
        }
        
        endGame(session);
    }

    /**
     * Discard a card from hand.
     */
    public void discardCard(Player player, int handIndex) {
        BattleSession session = activeSessions.get(player.getName());
        if (session == null || session.isEnded()) {
            player.sendMessage(messages.format(player, "cardbattle.no_active_game"));
            return;
        }

        boolean isPlayer1 = player.getName().equals(session.getPlayer1Name());
        if (!session.isPlayerTurn(isPlayer1)) {
            player.sendMessage(messages.format(player, "cardbattle.not_your_turn"));
            return;
        }

        List<String> hand = isPlayer1 ? session.getPlayer1Hand() : session.getPlayer2Hand();
        if (handIndex < 0 || handIndex >= hand.size()) {
            player.sendMessage(messages.format(player, "cardbattle.invalid_card"));
            return;
        }

        String cardId = hand.get(handIndex);
        CardDefinition card = cards.get(cardId);
        String cardName = card != null ? resolveI18n(player, card.getName()) : cardId;

        // Remove card from hand
        hand.remove(handIndex);

        Map<String, String> map = new HashMap<String, String>();
        map.put("card", cardName);
        player.sendMessage(messages.format(player, "cardbattle.card_discarded", map));

        // Refresh menu
        openBattleMenu(player, session);
    }

    /**
     * Handle menu clicks.
     */
    public void handleMenuClick(Player player, ItemStack clicked, CardBattleMenuHolder holder) {
        handleMenuClick(player, clicked, holder, false);
    }

    /**
     * Handle menu clicks with shift-click support.
     */
    public void handleMenuClick(Player player, ItemStack clicked, CardBattleMenuHolder holder, boolean isShiftClick) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String id = extractId(clicked);
        if (id == null) {
            if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
            }
            return;
        }

        switch (holder.getMenuType()) {
            case MAIN:
                handleMainMenuClick(player, id);
                break;
            case SELECT_AI:
                handleSelectAIClick(player, id);
                break;
            case BATTLE:
                handleBattleClick(player, id, isShiftClick);
                break;
            case PVP_SELECT_PLAYER:
                handlePvPSelectMenuClick(player, id);
                break;
        }
    }

    // ============ Menu Methods ============

    private void openMainMenu(Player player) {
        String title = messages.format(player, "menu.cardbattle.title");
        Inventory inv = Bukkit.createInventory(new CardBattleMenuHolder(MenuType.MAIN), 27, title);

        // Game info
        ItemStack infoItem = createItem(Material.BOOK,
            messages.format(player, "menu.cardbattle.info_title"),
            new String[]{
                messages.format(player, "menu.cardbattle.info_desc1"),
                messages.format(player, "menu.cardbattle.info_desc2"),
                messages.format(player, "menu.cardbattle.info_desc3")
            });
        safeSet(inv, 4, infoItem);

        // PvE button
        ItemStack pveItem = createItem(Material.ZOMBIE_HEAD,
            messages.format(player, "menu.cardbattle.pve_button"),
            new String[]{
                messages.format(player, "menu.cardbattle.pve_lore"),
                "ID:pve"
            });
        safeSet(inv, 11, pveItem);

        // PvP button - opens player selection menu
        ItemStack pvpItem = createItem(Material.PLAYER_HEAD,
            messages.format(player, "menu.cardbattle.pvp_button"),
            new String[]{
                messages.format(player, "menu.cardbattle.pvp_lore"),
                messages.format(player, "menu.cardbattle.click_to_select_player"),
                "ID:pvp_menu"
            });
        safeSet(inv, 15, pvpItem);

        // Resume game button (only if saved game exists)
        if (hasSavedSession(player.getName())) {
            ItemStack resumeItem = createItem(Material.WRITABLE_BOOK,
                messages.format(player, "menu.cardbattle.resume_button"),
                new String[]{
                    messages.format(player, "menu.cardbattle.resume_lore"),
                    "ID:resume"
                });
            safeSet(inv, 13, resumeItem);
        }

        // Back to games button
        ItemStack backItem = createItem(Material.ARROW,
            messages.format(player, "menu.cardbattle.back_to_games"),
            new String[]{
                messages.format(player, "menu.cardbattle.back_to_games_lore"),
                "ID:back_games"
            });
        safeSet(inv, 18, backItem);

        // Close button
        ItemStack closeItem = createItem(Material.BARRIER,
            messages.format(player, "menu.close"),
            new String[]{"ID:close"});
        safeSet(inv, 26, closeItem);

        player.openInventory(inv);
    }

    /**
     * Open PvP player selection menu with online players' heads.
     */
    public void openPvPPlayerSelectMenu(Player player) {
        String title = messages.format(player, "menu.cardbattle.pvp_select_title");
        Inventory inv = Bukkit.createInventory(new CardBattleMenuHolder(MenuType.PVP_SELECT_PLAYER), 54, title);

        // Info item
        ItemStack infoItem = createItem(Material.BOOK,
            messages.format(player, "menu.cardbattle.pvp_select_info"),
            new String[]{
                messages.format(player, "menu.cardbattle.pvp_select_desc")
            });
        safeSet(inv, 4, infoItem);

        // Get online players and display their heads
        int slot = 10;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (slot > 43) break;
            if (onlinePlayer.getName().equals(player.getName())) continue; // Skip self
            if (activeSessions.containsKey(onlinePlayer.getName())) {
                continue; // Skip players already in a game
            }

            Map<String, String> map = new HashMap<String, String>();
            map.put("player", onlinePlayer.getName());

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = skull.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.SkullMeta) {
                ((org.bukkit.inventory.meta.SkullMeta) meta).setOwningPlayer(onlinePlayer);
            }
            if (meta != null) {
                meta.setDisplayName(messages.colorize("&e" + onlinePlayer.getName()));
                List<String> lore = new ArrayList<String>();
                lore.add(messages.format(player, "menu.cardbattle.pvp_click_to_invite", map));
                lore.add("ID:invite_" + onlinePlayer.getName());
                meta.setLore(lore);
                skull.setItemMeta(meta);
            }
            safeSet(inv, slot, skull);
            slot++;
            if (slot == 17) slot = 19; // Skip edges
            if (slot == 26) slot = 28;
            if (slot == 35) slot = 37;
        }

        // Back button
        ItemStack backItem = createItem(Material.ARROW,
            messages.format(player, "menu.cardbattle.back"),
            new String[]{
                messages.format(player, "menu.cardbattle.back_lore"),
                "ID:back"
            });
        safeSet(inv, 45, backItem);

        // Close button
        ItemStack closeItem = createItem(Material.BARRIER,
            messages.format(player, "menu.close"),
            new String[]{"ID:close"});
        safeSet(inv, 53, closeItem);

        player.openInventory(inv);
    }

    private void openSelectAIMenu(Player player) {
        String title = messages.format(player, "menu.cardbattle.select_ai_title");
        Inventory inv = Bukkit.createInventory(new CardBattleMenuHolder(MenuType.SELECT_AI), 27, title);

        int slot = 10;
        for (AIOpponent opponent : aiOpponents.values()) {
            if (slot > 16) break;
            Map<String, String> map = new HashMap<String, String>();
            map.put("health", String.valueOf(opponent.getHealth()));
            map.put("difficulty", opponent.getDifficulty());

            ItemStack aiItem = createItem(Material.SKELETON_SKULL,
                resolveI18n(player, opponent.getName()),
                new String[]{
                    messages.format(player, "menu.cardbattle.ai_health", map),
                    messages.format(player, "menu.cardbattle.ai_difficulty", map),
                    "&7" + resolveI18n(player, opponent.getDescription()),
                    "",
                    messages.format(player, "menu.cardbattle.click_to_battle"),
                    "ID:ai_" + opponent.getId()
                });
            safeSet(inv, slot++, aiItem);
        }

        // Back button
        ItemStack backItem = createItem(Material.ARROW,
            messages.format(player, "help.back_button"),
            new String[]{
                messages.format(player, "help.back_lore"),
                "ID:back"
            });
        safeSet(inv, 18, backItem);

        // Close button
        ItemStack closeItem = createItem(Material.BARRIER,
            messages.format(player, "menu.close"),
            new String[]{"ID:close"});
        safeSet(inv, 26, closeItem);

        player.openInventory(inv);
    }

    private void openBattleMenu(Player player, BattleSession session) {
        String title = messages.format(player, "menu.cardbattle.battle_title");
        Inventory inv = Bukkit.createInventory(new CardBattleMenuHolder(MenuType.BATTLE), 54, title);

        boolean isPlayer1 = player.getName().equals(session.getPlayer1Name());
        int myHealth = isPlayer1 ? session.getPlayer1Health() : session.getPlayer2Health();
        int myMana = isPlayer1 ? session.getPlayer1Mana() : session.getPlayer2Mana();
        int oppHealth = isPlayer1 ? session.getPlayer2Health() : session.getPlayer1Health();
        List<String> myHand = isPlayer1 ? session.getPlayer1Hand() : session.getPlayer2Hand();
        boolean isMyTurn = session.isPlayerTurn(isPlayer1);

        String oppName = session.isPvE() ? resolveI18n(player, session.getAiOpponent().getName()) 
            : (isPlayer1 ? session.getPlayer2Name() : session.getPlayer1Name());

        // My status (top left)
        Map<String, String> myMap = new HashMap<String, String>();
        myMap.put("health", String.valueOf(myHealth));
        myMap.put("max_health", String.valueOf(startingHealth));
        myMap.put("mana", String.valueOf(myMana));
        myMap.put("hp_bar", createHpBar(myHealth, startingHealth));

        ItemStack myStatus = createItem(Material.PLAYER_HEAD,
            messages.format(player, "menu.cardbattle.your_status"),
            new String[]{
                messages.format(player, "menu.cardbattle.health_lore", myMap),
                messages.format(player, "menu.cardbattle.mana_lore", myMap)
            });
        safeSet(inv, 0, myStatus);

        // Opponent status (top right)
        Map<String, String> oppMap = new HashMap<String, String>();
        oppMap.put("opponent", oppName);
        oppMap.put("health", String.valueOf(oppHealth));
        oppMap.put("max_health", String.valueOf(startingHealth));
        oppMap.put("hp_bar", createHpBar(oppHealth, startingHealth));

        ItemStack oppStatus = createItem(Material.ZOMBIE_HEAD,
            messages.format(player, "menu.cardbattle.opponent_status", oppMap),
            new String[]{
                messages.format(player, "menu.cardbattle.opponent_health_lore", oppMap)
            });
        safeSet(inv, 8, oppStatus);

        // Turn indicator (top center)
        Map<String, String> turnMap = new HashMap<String, String>();
        turnMap.put("turn", String.valueOf(session.getTurnCount()));
        ItemStack turnItem = createItem(isMyTurn ? Material.LIME_WOOL : Material.RED_WOOL,
            isMyTurn ? messages.format(player, "menu.cardbattle.your_turn") 
                     : messages.format(player, "menu.cardbattle.opponent_turn"),
            new String[]{
                messages.format(player, "menu.cardbattle.turn_count", turnMap)
            });
        safeSet(inv, 4, turnItem);

        // Hand cards (middle row, slots 18-26)
        int[] handSlots = {18, 19, 20, 21, 22, 23, 24, 25, 26};
        for (int i = 0; i < myHand.size() && i < handSlots.length; i++) {
            String cardId = myHand.get(i);
            CardDefinition card = cards.get(cardId);
            if (card == null) continue;

            boolean canPlay = isMyTurn && myMana >= card.getManaCost();
            List<String> lore = new ArrayList<String>();
            
            Map<String, String> cardMap = new HashMap<String, String>();
            cardMap.put("cost", String.valueOf(card.getManaCost()));
            cardMap.put("value", String.valueOf(card.getValue()));
            
            lore.add(messages.format(player, "menu.cardbattle.card_cost", cardMap));
            lore.add(messages.format(player, "menu.cardbattle.card_type_" + card.getType().toLowerCase()));
            lore.add("&7" + resolveI18n(player, card.getDescription()));
            lore.add("");
            if (canPlay) {
                lore.add(messages.format(player, "menu.cardbattle.click_to_play"));
            } else if (!isMyTurn) {
                lore.add(messages.format(player, "menu.cardbattle.wait_your_turn"));
            } else {
                lore.add(messages.format(player, "menu.cardbattle.not_enough_mana_lore"));
            }
            if (isMyTurn) {
                lore.add(messages.format(player, "menu.cardbattle.shift_click_to_discard"));
            }
            lore.add("ID:play_" + i);

            ItemStack cardItem = createItem(card.getMaterial(),
                resolveI18n(player, card.getName()),
                lore.toArray(new String[0]));
            safeSet(inv, handSlots[i], cardItem);
        }

        // End turn button (bottom row)
        if (isMyTurn) {
            ItemStack endTurnItem = createItem(Material.CLOCK,
                messages.format(player, "menu.cardbattle.end_turn_button"),
                new String[]{
                    messages.format(player, "menu.cardbattle.end_turn_lore"),
                    "ID:end_turn"
                });
            safeSet(inv, 49, endTurnItem);
        }

        // Surrender button
        ItemStack surrenderItem = createItem(Material.WHITE_BANNER,
            messages.format(player, "menu.cardbattle.surrender_button"),
            new String[]{
                messages.format(player, "menu.cardbattle.surrender_lore"),
                "ID:surrender"
            });
        safeSet(inv, 45, surrenderItem);

        // Close button (just closes menu, game continues)
        ItemStack closeItem = createItem(Material.BARRIER,
            messages.format(player, "menu.close"),
            new String[]{"ID:close"});
        safeSet(inv, 53, closeItem);

        player.openInventory(inv);
    }

    // ============ Game Logic ============

    private void initializePlayerHand(BattleSession session, boolean isPlayer1) {
        List<String> deck = new ArrayList<String>(cards.keySet());
        List<String> hand = isPlayer1 ? session.getPlayer1Hand() : session.getPlayer2Hand();
        hand.clear();

        for (int i = 0; i < startingHandSize && !deck.isEmpty(); i++) {
            int idx = random.nextInt(deck.size());
            hand.add(deck.get(idx));
        }
    }

    private void initializeAIHand(BattleSession session) {
        List<String> deck = new ArrayList<String>(cards.keySet());
        session.getPlayer2Hand().clear();

        for (int i = 0; i < startingHandSize && !deck.isEmpty(); i++) {
            int idx = random.nextInt(deck.size());
            session.getPlayer2Hand().add(deck.get(idx));
        }
    }

    private void drawCards(BattleSession session, boolean isPlayer1, int count) {
        List<String> deck = new ArrayList<String>(cards.keySet());
        List<String> hand = isPlayer1 ? session.getPlayer1Hand() : session.getPlayer2Hand();

        for (int i = 0; i < count && hand.size() < maxHandSize && !deck.isEmpty(); i++) {
            int idx = random.nextInt(deck.size());
            hand.add(deck.get(idx));
        }
    }

    private void applyCardEffect(Player player, BattleSession session, CardDefinition card, boolean isPlayer1) {
        String type = card.getType().toLowerCase();
        int value = card.getValue();

        Map<String, String> map = new HashMap<String, String>();
        map.put("card", resolveI18n(player, card.getName()));
        map.put("value", String.valueOf(value));

        // Prepare opponent notification for PvP
        Player opponent = null;
        String opponentCardName = null;
        if (!session.isPvE()) {
            String opponentName = isPlayer1 ? session.getPlayer2Name() : session.getPlayer1Name();
            opponent = Bukkit.getPlayer(opponentName);
            if (opponent != null && opponent.isOnline()) {
                opponentCardName = resolveI18n(opponent, card.getName());
            }
        }

        switch (type) {
            case "attack":
                if (isPlayer1) {
                    session.setPlayer2Health(session.getPlayer2Health() - value);
                } else {
                    session.setPlayer1Health(session.getPlayer1Health() - value);
                }
                player.sendMessage(messages.format(player, "cardbattle.card_attack", map));
                notifyOpponent(opponent, player.getName(), opponentCardName, value, "cardbattle.opponent_attack");
                break;
            case "heal":
                if (isPlayer1) {
                    session.setPlayer1Health(Math.min(startingHealth, session.getPlayer1Health() + value));
                } else {
                    session.setPlayer2Health(Math.min(startingHealth, session.getPlayer2Health() + value));
                }
                player.sendMessage(messages.format(player, "cardbattle.card_heal", map));
                notifyOpponent(opponent, player.getName(), opponentCardName, value, "cardbattle.opponent_heal");
                break;
            case "defense":
                // Defense could add temporary shield (simplified: heals half the value)
                int healAmount = value / 2;
                if (isPlayer1) {
                    session.setPlayer1Health(Math.min(startingHealth, session.getPlayer1Health() + healAmount));
                } else {
                    session.setPlayer2Health(Math.min(startingHealth, session.getPlayer2Health() + healAmount));
                }
                map.put("value", String.valueOf(healAmount));
                player.sendMessage(messages.format(player, "cardbattle.card_defense", map));
                notifyOpponent(opponent, player.getName(), opponentCardName, healAmount, "cardbattle.opponent_defense");
                break;
            case "draw":
                drawCards(session, isPlayer1, value);
                player.sendMessage(messages.format(player, "cardbattle.card_draw", map));
                notifyOpponent(opponent, player.getName(), opponentCardName, value, "cardbattle.opponent_draw");
                break;
            case "mana":
                if (isPlayer1) {
                    session.setPlayer1Mana(Math.min(maxMana, session.getPlayer1Mana() + value));
                } else {
                    session.setPlayer2Mana(Math.min(maxMana, session.getPlayer2Mana() + value));
                }
                player.sendMessage(messages.format(player, "cardbattle.card_mana", map));
                notifyOpponent(opponent, player.getName(), opponentCardName, value, "cardbattle.opponent_mana");
                break;
            case "discard":
                // Force opponent to discard cards
                List<String> oppHand = isPlayer1 ? session.getPlayer2Hand() : session.getPlayer1Hand();
                for (int i = 0; i < value && !oppHand.isEmpty(); i++) {
                    oppHand.remove(random.nextInt(oppHand.size()));
                }
                player.sendMessage(messages.format(player, "cardbattle.card_discard", map));
                notifyOpponent(opponent, player.getName(), opponentCardName, value, "cardbattle.opponent_discard");
                break;
            default:
                player.sendMessage(messages.format(player, "cardbattle.card_played", map));
                notifyOpponent(opponent, player.getName(), opponentCardName, value, "cardbattle.opponent_played");
                break;
        }
    }

    /**
     * Notify opponent about a card played by the current player.
     */
    private void notifyOpponent(Player opponent, String playerName, String cardName, int value, String messageKey) {
        if (opponent == null || !opponent.isOnline() || cardName == null) {
            return;
        }
        Map<String, String> map = new HashMap<String, String>();
        map.put("player", playerName);
        map.put("card", cardName);
        map.put("value", String.valueOf(value));
        opponent.sendMessage(messages.format(opponent, messageKey, map));
    }

    private void processAITurn(BattleSession session) {
        AIOpponent ai = session.getAiOpponent();
        List<String> aiHand = session.getPlayer2Hand();
        int aiMana = session.getPlayer2Mana();

        // Simple AI: play cards until out of mana
        List<String> playableCards = new ArrayList<String>();
        for (String cardId : aiHand) {
            CardDefinition card = cards.get(cardId);
            if (card != null && card.getManaCost() <= aiMana) {
                playableCards.add(cardId);
            }
        }

        // Play one card based on AI difficulty
        if (!playableCards.isEmpty()) {
            String cardToPlay;
            if ("hard".equals(ai.getDifficulty())) {
                // Hard: prioritize attack cards
                cardToPlay = findBestCard(playableCards, "attack");
            } else if ("easy".equals(ai.getDifficulty())) {
                // Easy: random card
                cardToPlay = playableCards.get(random.nextInt(playableCards.size()));
            } else {
                // Normal: balanced
                cardToPlay = playableCards.get(random.nextInt(playableCards.size()));
            }

            CardDefinition card = cards.get(cardToPlay);
            if (card != null) {
                aiHand.remove(cardToPlay);
                session.setPlayer2Mana(aiMana - card.getManaCost());
                applyAICardEffect(session, card);
            }
        }
    }

    private String findBestCard(List<String> hand, String preferredType) {
        for (String cardId : hand) {
            CardDefinition card = cards.get(cardId);
            if (card != null && card.getType().equalsIgnoreCase(preferredType)) {
                return cardId;
            }
        }
        return hand.get(0);
    }

    private void applyAICardEffect(BattleSession session, CardDefinition card) {
        String type = card.getType().toLowerCase();
        int value = card.getValue();

        switch (type) {
            case "attack":
                session.setPlayer1Health(session.getPlayer1Health() - value);
                break;
            case "heal":
                session.setPlayer2Health(Math.min(startingHealth, session.getPlayer2Health() + value));
                break;
            case "defense":
                session.setPlayer2Health(Math.min(startingHealth, session.getPlayer2Health() + value / 2));
                break;
            case "draw":
                drawCards(session, false, value);
                break;
            case "mana":
                session.setPlayer2Mana(Math.min(maxMana, session.getPlayer2Mana() + value));
                break;
            case "discard":
                List<String> p1Hand = session.getPlayer1Hand();
                for (int i = 0; i < value && !p1Hand.isEmpty(); i++) {
                    p1Hand.remove(random.nextInt(p1Hand.size()));
                }
                break;
        }

        // Notify player about AI action
        Player p1 = Bukkit.getPlayer(session.getPlayer1Name());
        if (p1 != null && p1.isOnline()) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("card", resolveI18n(p1, card.getName()));
            map.put("value", String.valueOf(value));
            p1.sendMessage(messages.format(p1, "cardbattle.ai_played", map));
        }
    }

    private boolean checkGameEnd(BattleSession session) {
        return session.getPlayer1Health() <= 0 || session.getPlayer2Health() <= 0;
    }

    private void endGame(BattleSession session) {
        session.setEnded(true);

        boolean player1Won = session.getPlayer1Health() > 0;
        String winnerName = player1Won ? session.getPlayer1Name() : 
            (session.isPvE() ? "AI" : session.getPlayer2Name());
        String loserName = player1Won ? 
            (session.isPvE() ? "AI" : session.getPlayer2Name()) : session.getPlayer1Name();

        // Notify players
        Player p1 = Bukkit.getPlayer(session.getPlayer1Name());
        if (p1 != null && p1.isOnline()) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("winner", winnerName);
            if (player1Won) {
                p1.sendMessage(messages.format(p1, "cardbattle.you_won"));
                grantRewards(p1);
            } else {
                p1.sendMessage(messages.format(p1, "cardbattle.you_lost", map));
            }
            p1.closeInventory();
        }

        if (!session.isPvE()) {
            Player p2 = Bukkit.getPlayer(session.getPlayer2Name());
            if (p2 != null && p2.isOnline()) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("winner", winnerName);
                if (!player1Won) {
                    p2.sendMessage(messages.format(p2, "cardbattle.you_won"));
                    grantRewards(p2);
                } else {
                    p2.sendMessage(messages.format(p2, "cardbattle.you_lost", map));
                }
                p2.closeInventory();
            }
        }

        // Clean up sessions
        activeSessions.remove(session.getPlayer1Name());
        if (!session.isPvE()) {
            activeSessions.remove(session.getPlayer2Name());
        }

        // Clear saved session file
        if (session.isPvE()) {
            clearSessionFile(session.getPlayer1Name());
        }
    }

    private void grantRewards(Player player) {
        for (String command : winRewardCommands) {
            String cmd = command.replace("{player}", player.getName());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    // ============ Menu Click Handlers ============

    private void handleMainMenuClick(Player player, String id) {
        switch (id) {
            case "pve":
                player.closeInventory();
                openSelectAIMenu(player);
                break;
            case "pvp":
                player.closeInventory();
                player.sendMessage(messages.format(player, "cardbattle.pvp_usage"));
                break;
            case "pvp_menu":
                openPvPPlayerSelectMenu(player);
                break;
            case "resume":
                player.closeInventory();
                resumeGame(player);
                break;
            case "back_games":
                player.closeInventory();
                if (openGamesMenuCallback != null) {
                    openGamesMenuCallback.accept(player);
                }
                break;
            case "close":
                player.closeInventory();
                break;
        }
    }

    private void handlePvPSelectMenuClick(Player player, String id) {
        if (id.startsWith("invite_")) {
            String targetName = id.substring(7);
            player.closeInventory();
            invitePvP(player, targetName);
        } else if ("back".equals(id)) {
            openMainMenu(player);
        } else if ("close".equals(id)) {
            player.closeInventory();
        }
    }

    private void handleSelectAIClick(Player player, String id) {
        if (id.startsWith("ai_")) {
            String aiId = id.substring(3);
            player.closeInventory();
            startPvEGame(player, aiId);
        } else if ("back".equals(id)) {
            player.closeInventory();
            openMainMenu(player);
        } else if ("close".equals(id)) {
            player.closeInventory();
        }
    }

    private void handleBattleClick(Player player, String id, boolean isShiftClick) {
        if (id.startsWith("play_")) {
            try {
                int index = Integer.parseInt(id.substring(5));
                if (isShiftClick) {
                    discardCard(player, index);
                } else {
                    playCard(player, index);
                }
            } catch (NumberFormatException ignored) {
            }
        } else if ("end_turn".equals(id)) {
            endTurn(player);
        } else if ("surrender".equals(id)) {
            player.closeInventory();
            surrender(player);
        } else if ("close".equals(id)) {
            player.closeInventory();
        }
    }

    // ============ Utility Methods ============

    private String createHpBar(int current, int max) {
        int barLength = 10;
        int filledLength = max > 0 ? (int) Math.ceil((double) current / max * barLength) : 0;
        double hpPercent = max > 0 ? (double) current / max : 0;

        String color;
        if (hpPercent > 0.66) {
            color = "&a";
        } else if (hpPercent > 0.33) {
            color = "&e";
        } else {
            color = "&c";
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

    private String resolveI18n(Player player, String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String resolved = messages.format(player, key);
        if (resolved.equals(key) || resolved.contains("missing")) {
            return key;
        }
        return resolved;
    }

    private AIOpponent getDefaultAI() {
        if (!aiOpponents.isEmpty()) {
            return aiOpponents.values().iterator().next();
        }
        return new AIOpponent("default", "menu.cardbattle.default_ai", "", startingHealth, "normal");
    }

    // ============ Session Persistence ============

    /**
     * Save a PvE game session for later resumption.
     */
    private void saveSession(BattleSession session) {
        if (session == null || !session.isPvE()) {
            return; // Only save PvE sessions
        }

        File file = new File(storageDir, session.getPlayer1Name() + ".yml");
        YamlConfiguration data;
        if (file.exists()) {
            data = YamlConfiguration.loadConfiguration(file);
        } else {
            data = new YamlConfiguration();
        }

        data.set("cardbattle.active", !session.isEnded());
        data.set("cardbattle.ai_opponent", session.getAiOpponent().getId());
        data.set("cardbattle.player_health", session.getPlayer1Health());
        data.set("cardbattle.ai_health", session.getPlayer2Health());
        data.set("cardbattle.player_mana", session.getPlayer1Mana());
        data.set("cardbattle.ai_mana", session.getPlayer2Mana());
        data.set("cardbattle.player_hand", session.getPlayer1Hand());
        data.set("cardbattle.ai_hand", session.getPlayer2Hand());
        data.set("cardbattle.player_turn", session.isPlayer1Turn());
        data.set("cardbattle.turn_count", session.getTurnCount());

        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("無法保存卡牌對決遊戲會話: " + e.getMessage());
        }
    }

    /**
     * Load a saved PvE game session.
     */
    private BattleSession loadSession(String playerName) {
        File file = new File(storageDir, playerName + ".yml");
        if (!file.exists()) {
            return null;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        if (!data.contains("cardbattle.active")) {
            return null;
        }
        if (!data.getBoolean("cardbattle.active", false)) {
            return null;
        }

        String aiId = data.getString("cardbattle.ai_opponent", "default");
        AIOpponent ai = aiOpponents.get(aiId);
        if (ai == null) {
            ai = getDefaultAI();
        }

        BattleSession session = new BattleSession(playerName, null, ai, startingHealth, startingMana);
        session.setPlayer1Health(data.getInt("cardbattle.player_health", startingHealth));
        session.setPlayer2Health(data.getInt("cardbattle.ai_health", ai.getHealth()));
        session.setPlayer1Mana(data.getInt("cardbattle.player_mana", startingMana));
        session.setPlayer2Mana(data.getInt("cardbattle.ai_mana", startingMana));
        session.setPlayer1Turn(data.getBoolean("cardbattle.player_turn", true));

        List<String> playerHand = data.getStringList("cardbattle.player_hand");
        List<String> aiHand = data.getStringList("cardbattle.ai_hand");
        session.getPlayer1Hand().addAll(playerHand);
        session.getPlayer2Hand().addAll(aiHand);

        // Restore turn count
        int turnCount = data.getInt("cardbattle.turn_count", 1);
        while (session.getTurnCount() < turnCount) {
            session.incrementTurnCount();
        }

        return session;
    }

    /**
     * Clear saved session file.
     */
    private void clearSessionFile(String playerName) {
        File file = new File(storageDir, playerName + ".yml");
        if (file.exists()) {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            data.set("cardbattle", null);
            try {
                data.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("無法清除卡牌對決遊戲會話: " + e.getMessage());
            }
        }
    }

    /**
     * Check if a player has a saved game session.
     */
    public boolean hasSavedSession(String playerName) {
        File file = new File(storageDir, playerName + ".yml");
        if (!file.exists()) {
            return false;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        return data.getBoolean("cardbattle.active", false);
    }

    /**
     * Resume a saved game for a player.
     */
    public void resumeGame(Player player) {
        if (!enabled) {
            player.sendMessage(messages.format(player, "cardbattle.disabled"));
            return;
        }

        String playerName = player.getName();
        if (activeSessions.containsKey(playerName)) {
            player.sendMessage(messages.format(player, "cardbattle.already_in_game"));
            return;
        }

        BattleSession session = loadSession(playerName);
        if (session == null) {
            player.sendMessage(messages.format(player, "cardbattle.no_saved_game"));
            return;
        }

        activeSessions.put(playerName, session);
        player.sendMessage(messages.format(player, "cardbattle.game_resumed"));
        openBattleMenu(player, session);
    }

    // ============ Inner Classes ============

    public enum MenuType {
        MAIN, SELECT_AI, BATTLE, PVP_SELECT_PLAYER
    }

    public static class CardBattleMenuHolder implements InventoryHolder {
        private final MenuType menuType;

        public CardBattleMenuHolder(MenuType menuType) {
            this.menuType = menuType;
        }

        public MenuType getMenuType() {
            return menuType;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static class BattleSession {
        private final String player1Name;
        private final String player2Name; // null for PvE
        private final AIOpponent aiOpponent; // null for PvP
        private int player1Health;
        private int player2Health;
        private int player1Mana;
        private int player2Mana;
        private final List<String> player1Hand;
        private final List<String> player2Hand;
        private boolean player1Turn;
        private int turnCount;
        private boolean ended;

        BattleSession(String player1Name, String player2Name, AIOpponent aiOpponent, int startingHealth, int startingMana) {
            this.player1Name = player1Name;
            this.player2Name = player2Name;
            this.aiOpponent = aiOpponent;
            this.player1Health = startingHealth;
            this.player2Health = aiOpponent != null ? aiOpponent.getHealth() : startingHealth;
            this.player1Mana = startingMana;
            this.player2Mana = startingMana;
            this.player1Hand = new ArrayList<String>();
            this.player2Hand = new ArrayList<String>();
            this.player1Turn = true;
            this.turnCount = 1;
            this.ended = false;
        }

        boolean isPvE() { return aiOpponent != null; }
        String getPlayer1Name() { return player1Name; }
        String getPlayer2Name() { return player2Name; }
        AIOpponent getAiOpponent() { return aiOpponent; }
        int getPlayer1Health() { return player1Health; }
        void setPlayer1Health(int health) { this.player1Health = Math.max(0, health); }
        int getPlayer2Health() { return player2Health; }
        void setPlayer2Health(int health) { this.player2Health = Math.max(0, health); }
        int getPlayer1Mana() { return player1Mana; }
        void setPlayer1Mana(int mana) { this.player1Mana = mana; }
        int getPlayer2Mana() { return player2Mana; }
        void setPlayer2Mana(int mana) { this.player2Mana = mana; }
        List<String> getPlayer1Hand() { return player1Hand; }
        List<String> getPlayer2Hand() { return player2Hand; }
        boolean isPlayer1Turn() { return player1Turn; }
        void setPlayer1Turn(boolean turn) { this.player1Turn = turn; }
        boolean isPlayerTurn(boolean isPlayer1) { return isPlayer1 == player1Turn; }
        int getTurnCount() { return turnCount; }
        void incrementTurnCount() { turnCount++; }
        boolean isEnded() { return ended; }
        void setEnded(boolean ended) { this.ended = ended; }
    }

    private static class CardDefinition {
        private final String id;
        private final String name;
        private final String description;
        private final String type; // attack, heal, defense, draw, mana, discard
        private final int manaCost;
        private final int value;
        private final Material material;

        CardDefinition(String id, String name, String description, String type, int manaCost, int value, Material material) {
            this.id = id;
            this.name = name != null ? name : id;
            this.description = description != null ? description : "";
            this.type = type != null ? type : "attack";
            this.manaCost = Math.max(0, manaCost);
            this.value = value;
            this.material = material != null ? material : Material.PAPER;
        }

        static CardDefinition fromSection(String id, ConfigurationSection section) {
            String name = section.getString("name", id);
            String desc = section.getString("description", "");
            String type = section.getString("type", "attack");
            int cost = section.getInt("mana_cost", 1);
            int value = section.getInt("value", 1);
            String matStr = section.getString("material", "PAPER");
            Material mat = Material.matchMaterial(matStr.toUpperCase());
            return new CardDefinition(id, name, desc, type, cost, value, mat);
        }

        String getId() { return id; }
        String getName() { return name; }
        String getDescription() { return description; }
        String getType() { return type; }
        int getManaCost() { return manaCost; }
        int getValue() { return value; }
        Material getMaterial() { return material; }
    }

    private static class AIOpponent {
        private final String id;
        private final String name;
        private final String description;
        private final int health;
        private final String difficulty; // easy, normal, hard

        AIOpponent(String id, String name, String description, int health, String difficulty) {
            this.id = id;
            this.name = name != null ? name : id;
            this.description = description != null ? description : "";
            this.health = health > 0 ? health : 30;
            this.difficulty = difficulty != null ? difficulty : "normal";
        }

        static AIOpponent fromSection(String id, ConfigurationSection section) {
            String name = section.getString("name", id);
            String desc = section.getString("description", "");
            int health = section.getInt("health", 30);
            String difficulty = section.getString("difficulty", "normal");
            return new AIOpponent(id, name, desc, health, difficulty);
        }

        String getId() { return id; }
        String getName() { return name; }
        String getDescription() { return description; }
        int getHealth() { return health; }
        String getDifficulty() { return difficulty; }
    }
}
