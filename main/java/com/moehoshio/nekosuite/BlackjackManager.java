package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Blackjack (21 Points) Game Module - Classic card game against the dealer.
 * 
 * Features:
 * - Standard blackjack rules (hit, stand, double down)
 * - Virtual betting with in-game currency
 * - Multiple bet amounts available
 * - Dealer AI follows standard casino rules (hit on 16 or less, stand on 17+)
 * - Blackjack pays 3:2, normal win pays 1:1
 * - Push (tie) returns the bet
 */
public class BlackjackManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final MenuLayout menuLayout;
    private final File configFile;
    private final Random random = new Random();

    // Game configuration
    private boolean enabled = true;
    private int minBet = 10;
    private int maxBet = 1000;
    private int[] betOptions = {10, 25, 50, 100, 250, 500, 1000};
    private double blackjackMultiplier = 1.5; // Pays 3:2
    private List<String> winRewardCommands = new ArrayList<String>();

    // Active game sessions
    private final Map<String, BlackjackSession> activeSessions = new HashMap<String, BlackjackSession>();
    
    // PvP sessions and invitations
    private final Map<String, PvPBlackjackSession> pvpSessions = new HashMap<String, PvPBlackjackSession>();
    private final Map<String, String> pvpInvitations = new HashMap<String, String>(); // target -> inviter

    // Callback for opening games menu (set by plugin)
    private java.util.function.Consumer<Player> openGamesMenuCallback;

    // Card values for display
    private static final String[] CARD_SUITS = {"♠", "♥", "♦", "♣"};
    private static final String[] CARD_RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};

    public BlackjackManager(JavaPlugin plugin, Messages messages, File configFile, MenuLayout menuLayout) {
        this.plugin = plugin;
        this.messages = messages;
        this.configFile = configFile;
        this.menuLayout = menuLayout;
        loadConfig();
    }

    /**
     * Set callback for opening games menu.
     */
    public void setOpenGamesMenuCallback(java.util.function.Consumer<Player> callback) {
        this.openGamesMenuCallback = callback;
    }

    private void loadConfig() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        enabled = config.getBoolean("game.enabled", true);
        minBet = config.getInt("game.min_bet", 10);
        maxBet = config.getInt("game.max_bet", 1000);
        blackjackMultiplier = config.getDouble("game.blackjack_multiplier", 1.5);
        
        List<Integer> betList = config.getIntegerList("game.bet_options");
        if (!betList.isEmpty()) {
            betOptions = new int[betList.size()];
            for (int i = 0; i < betList.size(); i++) {
                betOptions[i] = betList.get(i);
            }
        }
        
        winRewardCommands = config.getStringList("rewards.win_commands");
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ============ Public API ============

    /**
     * Open the main blackjack menu.
     */
    public void openMenu(Player player) {
        if (!enabled) {
            player.sendMessage(messages.format(player, "blackjack.disabled"));
            return;
        }

        BlackjackSession session = activeSessions.get(player.getName());
        if (session != null && !session.isEnded()) {
            openGameMenu(player, session);
        } else {
            openBetMenu(player);
        }
    }

    /**
     * Start a new game with a specific bet.
     */
    public void startGame(Player player, int betAmount) {
        if (!enabled) {
            player.sendMessage(messages.format(player, "blackjack.disabled"));
            return;
        }

        if (activeSessions.containsKey(player.getName())) {
            player.sendMessage(messages.format(player, "blackjack.already_in_game"));
            return;
        }

        if (betAmount < minBet || betAmount > maxBet) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("min", String.valueOf(minBet));
            map.put("max", String.valueOf(maxBet));
            player.sendMessage(messages.format(player, "blackjack.invalid_bet", map));
            return;
        }

        // Create new session
        BlackjackSession session = new BlackjackSession(player.getName(), betAmount);
        
        // Deal initial cards
        dealInitialCards(session);
        
        activeSessions.put(player.getName(), session);

        // Check for natural blackjack
        if (calculateHandValue(session.getPlayerHand()) == 21) {
            session.setPlayerBlackjack(true);
            // Reveal dealer hand and check for tie
            while (calculateHandValue(session.getDealerHand()) < 17) {
                session.getDealerHand().add(drawCard());
            }
            endGame(player, session);
            return;
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put("bet", String.valueOf(betAmount));
        player.sendMessage(messages.format(player, "blackjack.game_started", map));

        openGameMenu(player, session);
    }

    /**
     * Player hits (draws a card).
     */
    public void hit(Player player) {
        BlackjackSession session = activeSessions.get(player.getName());
        if (session == null || session.isEnded()) {
            player.sendMessage(messages.format(player, "blackjack.no_active_game"));
            return;
        }

        if (session.isPlayerStand()) {
            player.sendMessage(messages.format(player, "blackjack.already_stand"));
            return;
        }

        // Draw a card
        Card card = drawCard();
        session.getPlayerHand().add(card);

        Map<String, String> map = new HashMap<String, String>();
        map.put("card", card.toString());
        player.sendMessage(messages.format(player, "blackjack.card_drawn", map));

        // Check for bust
        int handValue = calculateHandValue(session.getPlayerHand());
        if (handValue > 21) {
            session.setPlayerBust(true);
            endGame(player, session);
            return;
        }

        openGameMenu(player, session);
    }

    /**
     * Player stands (ends their turn).
     */
    public void stand(Player player) {
        BlackjackSession session = activeSessions.get(player.getName());
        if (session == null || session.isEnded()) {
            player.sendMessage(messages.format(player, "blackjack.no_active_game"));
            return;
        }

        session.setPlayerStand(true);
        player.sendMessage(messages.format(player, "blackjack.player_stand"));

        // Dealer's turn
        dealerPlay(session);
        endGame(player, session);
    }

    /**
     * Player doubles down (doubles bet, draws one card, then stands).
     */
    public void doubleDown(Player player) {
        BlackjackSession session = activeSessions.get(player.getName());
        if (session == null || session.isEnded()) {
            player.sendMessage(messages.format(player, "blackjack.no_active_game"));
            return;
        }

        if (session.getPlayerHand().size() != 2) {
            player.sendMessage(messages.format(player, "blackjack.cannot_double"));
            return;
        }

        // Double the bet
        session.setBet(session.getBet() * 2);
        session.setDoubledDown(true);

        // Draw one card
        Card card = drawCard();
        session.getPlayerHand().add(card);

        Map<String, String> map = new HashMap<String, String>();
        map.put("card", card.toString());
        map.put("bet", String.valueOf(session.getBet()));
        player.sendMessage(messages.format(player, "blackjack.doubled_down", map));

        // Check for bust
        int handValue = calculateHandValue(session.getPlayerHand());
        if (handValue > 21) {
            session.setPlayerBust(true);
            endGame(player, session);
            return;
        }

        // Player automatically stands
        session.setPlayerStand(true);
        dealerPlay(session);
        endGame(player, session);
    }

    // ============ PvP Mode ============

    /**
     * Send PvP invitation to another player.
     */
    public void invitePvP(Player inviter, String targetName) {
        if (!enabled) {
            inviter.sendMessage(messages.format(inviter, "blackjack.disabled"));
            return;
        }

        if (activeSessions.containsKey(inviter.getName()) || pvpSessions.containsKey(inviter.getName())) {
            inviter.sendMessage(messages.format(inviter, "blackjack.already_in_game"));
            return;
        }

        if (inviter.getName().equalsIgnoreCase(targetName)) {
            inviter.sendMessage(messages.format(inviter, "blackjack.cannot_invite_self"));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            inviter.sendMessage(messages.format(inviter, "blackjack.player_not_found"));
            return;
        }

        if (activeSessions.containsKey(target.getName()) || pvpSessions.containsKey(target.getName())) {
            inviter.sendMessage(messages.format(inviter, "blackjack.target_in_game"));
            return;
        }

        pvpInvitations.put(target.getName(), inviter.getName());

        Map<String, String> map = new HashMap<String, String>();
        map.put("target", target.getName());
        inviter.sendMessage(messages.format(inviter, "blackjack.pvp_invite_sent", map));

        Map<String, String> targetMap = new HashMap<String, String>();
        targetMap.put("player", inviter.getName());
        target.sendMessage(messages.format(target, "blackjack.pvp_invite_received", targetMap));
    }

    /**
     * Accept PvP invitation.
     */
    public void acceptPvP(Player player) {
        String inviterName = pvpInvitations.remove(player.getName());
        if (inviterName == null) {
            player.sendMessage(messages.format(player, "blackjack.no_pending_invite"));
            return;
        }

        Player inviter = Bukkit.getPlayer(inviterName);
        if (inviter == null || !inviter.isOnline()) {
            player.sendMessage(messages.format(player, "blackjack.inviter_offline"));
            return;
        }

        // Start PvP game
        startPvPGame(inviter, player);
    }

    /**
     * Decline PvP invitation.
     */
    public void declinePvP(Player player) {
        String inviterName = pvpInvitations.remove(player.getName());
        if (inviterName == null) {
            player.sendMessage(messages.format(player, "blackjack.no_pending_invite"));
            return;
        }

        Player inviter = Bukkit.getPlayer(inviterName);
        if (inviter != null && inviter.isOnline()) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("player", player.getName());
            inviter.sendMessage(messages.format(inviter, "blackjack.pvp_invite_declined", map));
        }

        player.sendMessage(messages.format(player, "blackjack.decline_success"));
    }

    /**
     * Start a PvP blackjack game.
     */
    private void startPvPGame(Player player1, Player player2) {
        PvPBlackjackSession session = new PvPBlackjackSession(player1.getName(), player2.getName());
        
        // Deal initial cards
        session.getPlayer1Hand().add(drawCard());
        session.getPlayer2Hand().add(drawCard());
        session.getPlayer1Hand().add(drawCard());
        session.getPlayer2Hand().add(drawCard());

        pvpSessions.put(player1.getName(), session);
        pvpSessions.put(player2.getName(), session);

        Map<String, String> map1 = new HashMap<String, String>();
        map1.put("opponent", player2.getName());
        player1.sendMessage(messages.format(player1, "blackjack.pvp_started", map1));

        Map<String, String> map2 = new HashMap<String, String>();
        map2.put("opponent", player1.getName());
        player2.sendMessage(messages.format(player2, "blackjack.pvp_started", map2));

        // Check for natural blackjacks
        int p1Value = calculateHandValue(session.getPlayer1Hand());
        int p2Value = calculateHandValue(session.getPlayer2Hand());

        if (p1Value == 21) session.setPlayer1Blackjack(true);
        if (p2Value == 21) session.setPlayer2Blackjack(true);

        if (session.isPlayer1Blackjack() || session.isPlayer2Blackjack()) {
            // If either has blackjack, end immediately
            endPvPGame(session);
            return;
        }

        // Open game menu for current player (player 1 goes first)
        openPvPGameMenu(player1, session);
        
        // Notify player 2 to wait
        player2.sendMessage(messages.format(player2, "blackjack.pvp_opponent_turn"));
    }

    /**
     * PvP hit action.
     */
    public void pvpHit(Player player) {
        PvPBlackjackSession session = pvpSessions.get(player.getName());
        if (session == null || session.isEnded()) {
            player.sendMessage(messages.format(player, "blackjack.no_active_game"));
            return;
        }

        boolean isPlayer1 = player.getName().equals(session.getPlayer1Name());
        if (!session.isPlayerTurn(isPlayer1)) {
            player.sendMessage(messages.format(player, "blackjack.not_your_turn"));
            return;
        }

        List<Card> hand = isPlayer1 ? session.getPlayer1Hand() : session.getPlayer2Hand();
        Card card = drawCard();
        hand.add(card);

        Map<String, String> map = new HashMap<String, String>();
        map.put("card", card.toString());
        player.sendMessage(messages.format(player, "blackjack.card_drawn", map));

        int handValue = calculateHandValue(hand);
        if (handValue > 21) {
            if (isPlayer1) {
                session.setPlayer1Bust(true);
            } else {
                session.setPlayer2Bust(true);
            }
            // Auto-stand when bust
            if (isPlayer1) {
                session.setPlayer1Stand(true);
            } else {
                session.setPlayer2Stand(true);
            }
            checkPvPTurnEnd(session, player);
            return;
        }

        openPvPGameMenu(player, session);
    }

    /**
     * PvP stand action.
     */
    public void pvpStand(Player player) {
        PvPBlackjackSession session = pvpSessions.get(player.getName());
        if (session == null || session.isEnded()) {
            player.sendMessage(messages.format(player, "blackjack.no_active_game"));
            return;
        }

        boolean isPlayer1 = player.getName().equals(session.getPlayer1Name());
        if (!session.isPlayerTurn(isPlayer1)) {
            player.sendMessage(messages.format(player, "blackjack.not_your_turn"));
            return;
        }

        if (isPlayer1) {
            session.setPlayer1Stand(true);
        } else {
            session.setPlayer2Stand(true);
        }

        player.sendMessage(messages.format(player, "blackjack.player_stand"));
        checkPvPTurnEnd(session, player);
    }

    private void checkPvPTurnEnd(PvPBlackjackSession session, Player currentPlayer) {
        boolean isPlayer1 = currentPlayer.getName().equals(session.getPlayer1Name());
        
        // Check if both players have finished
        if (session.isPlayer1Stand() && session.isPlayer2Stand()) {
            endPvPGame(session);
            return;
        }

        // Switch to next player
        if (isPlayer1) {
            session.setPlayer1Turn(false);
            Player player2 = Bukkit.getPlayer(session.getPlayer2Name());
            if (player2 != null && player2.isOnline()) {
                player2.sendMessage(messages.format(player2, "blackjack.pvp_your_turn"));
                openPvPGameMenu(player2, session);
            }
        } else {
            session.setPlayer1Turn(true);
            Player player1 = Bukkit.getPlayer(session.getPlayer1Name());
            if (player1 != null && player1.isOnline()) {
                player1.sendMessage(messages.format(player1, "blackjack.pvp_your_turn"));
                openPvPGameMenu(player1, session);
            }
        }
    }

    private void endPvPGame(PvPBlackjackSession session) {
        session.setEnded(true);

        int p1Value = calculateHandValue(session.getPlayer1Hand());
        int p2Value = calculateHandValue(session.getPlayer2Hand());

        String winner = null;
        String loser = null;

        if (session.isPlayer1Bust()) {
            winner = session.getPlayer2Name();
            loser = session.getPlayer1Name();
        } else if (session.isPlayer2Bust()) {
            winner = session.getPlayer1Name();
            loser = session.getPlayer2Name();
        } else if (session.isPlayer1Blackjack() && !session.isPlayer2Blackjack()) {
            winner = session.getPlayer1Name();
            loser = session.getPlayer2Name();
        } else if (session.isPlayer2Blackjack() && !session.isPlayer1Blackjack()) {
            winner = session.getPlayer2Name();
            loser = session.getPlayer1Name();
        } else if (p1Value > p2Value) {
            winner = session.getPlayer1Name();
            loser = session.getPlayer2Name();
        } else if (p2Value > p1Value) {
            winner = session.getPlayer2Name();
            loser = session.getPlayer1Name();
        }
        // else it's a tie

        Player player1 = Bukkit.getPlayer(session.getPlayer1Name());
        Player player2 = Bukkit.getPlayer(session.getPlayer2Name());

        Map<String, String> resultMap = new HashMap<String, String>();
        resultMap.put("player1_value", String.valueOf(p1Value));
        resultMap.put("player2_value", String.valueOf(p2Value));
        resultMap.put("player1", session.getPlayer1Name());
        resultMap.put("player2", session.getPlayer2Name());

        if (winner != null) {
            if (player1 != null && player1.isOnline()) {
                if (winner.equals(session.getPlayer1Name())) {
                    player1.sendMessage(messages.format(player1, "blackjack.pvp_you_won", resultMap));
                } else {
                    resultMap.put("winner", winner);
                    player1.sendMessage(messages.format(player1, "blackjack.pvp_you_lost", resultMap));
                }
            }
            if (player2 != null && player2.isOnline()) {
                if (winner.equals(session.getPlayer2Name())) {
                    player2.sendMessage(messages.format(player2, "blackjack.pvp_you_won", resultMap));
                } else {
                    resultMap.put("winner", winner);
                    player2.sendMessage(messages.format(player2, "blackjack.pvp_you_lost", resultMap));
                }
            }
        } else {
            // Tie
            if (player1 != null && player1.isOnline()) {
                player1.sendMessage(messages.format(player1, "blackjack.pvp_tie", resultMap));
            }
            if (player2 != null && player2.isOnline()) {
                player2.sendMessage(messages.format(player2, "blackjack.pvp_tie", resultMap));
            }
        }

        // Clean up
        pvpSessions.remove(session.getPlayer1Name());
        pvpSessions.remove(session.getPlayer2Name());
    }

    private void openPvPGameMenu(Player player, PvPBlackjackSession session) {
        String title = messages.format(player, "menu.blackjack.game_title");
        Inventory inv = Bukkit.createInventory(new BlackjackMenuHolder(MenuType.PVP_GAME), 54, title);

        boolean isPlayer1 = player.getName().equals(session.getPlayer1Name());
        List<Card> myHand = isPlayer1 ? session.getPlayer1Hand() : session.getPlayer2Hand();
        List<Card> opponentHand = isPlayer1 ? session.getPlayer2Hand() : session.getPlayer1Hand();
        String opponentName = isPlayer1 ? session.getPlayer2Name() : session.getPlayer1Name();
        int myValue = calculateHandValue(myHand);
        boolean isMyTurn = session.isPlayerTurn(isPlayer1);

        // Opponent info (top row)
        Map<String, String> opponentMap = new HashMap<String, String>();
        opponentMap.put("opponent", opponentName);
        opponentMap.put("count", String.valueOf(opponentHand.size()));
        safeSet(inv, 0, createItem(Material.PLAYER_HEAD,
            messages.format(player, "menu.blackjack.pvp_opponent_status", opponentMap),
            new String[]{
                messages.format(player, "menu.blackjack.pvp_opponent_cards", opponentMap)
            }));

        // Opponent's cards (hidden)
        for (int i = 0; i < opponentHand.size() && i < 5; i++) {
            safeSet(inv, 2 + i, createItem(Material.BLACK_STAINED_GLASS_PANE,
                messages.format(player, "menu.blackjack.hidden_card"),
                new String[]{}));
        }

        // Player's hand (middle row)
        Map<String, String> playerMap = new HashMap<String, String>();
        playerMap.put("value", String.valueOf(myValue));
        safeSet(inv, 18, createItem(Material.PLAYER_HEAD,
            messages.format(player, "menu.blackjack.your_hand"),
            new String[]{
                messages.format(player, "menu.blackjack.your_value", playerMap)
            }));

        int[] playerSlots = {20, 21, 22, 23, 24, 25, 26};
        for (int i = 0; i < myHand.size() && i < playerSlots.length; i++) {
            safeSet(inv, playerSlots[i], createCardItem(player, myHand.get(i), true));
        }

        // Turn indicator
        String turnMsg = isMyTurn ? 
            messages.format(player, "menu.blackjack.pvp_your_turn") : 
            messages.format(player, "menu.blackjack.pvp_opponent_turn");
        safeSet(inv, 4, createItem(Material.CLOCK, turnMsg, new String[]{}));

        // Action buttons (only if it's my turn and I haven't stood)
        boolean hasStood = isPlayer1 ? session.isPlayer1Stand() : session.isPlayer2Stand();
        if (isMyTurn && !hasStood) {
            // Hit button
            safeSet(inv, 37, createItem(Material.LIME_WOOL,
                messages.format(player, "menu.blackjack.hit_button"),
                new String[]{
                    messages.format(player, "menu.blackjack.hit_lore"),
                    "ID:pvp_hit"
                }));

            // Stand button
            safeSet(inv, 40, createItem(Material.RED_WOOL,
                messages.format(player, "menu.blackjack.stand_button"),
                new String[]{
                    messages.format(player, "menu.blackjack.stand_lore"),
                    "ID:pvp_stand"
                }));
        }

        // Close button
        safeSet(inv, 53, createItem(Material.BARRIER,
            messages.format(player, "menu.close"),
            new String[]{"ID:close"}));

        player.openInventory(inv);
    }

    /**
     * Check if player is in PvP game.
     */
    public boolean isInPvPGame(String playerName) {
        return pvpSessions.containsKey(playerName);
    }

    /**
     * Handle menu clicks.
     */
    public void handleMenuClick(Player player, ItemStack clicked, BlackjackMenuHolder holder) {
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
            case BET:
                handleBetMenuClick(player, id);
                break;
            case GAME:
                handleGameMenuClick(player, id);
                break;
            case RESULT:
                handleResultMenuClick(player, id);
                break;
            case PVP_GAME:
                handlePvPGameMenuClick(player, id);
                break;
            case PVP_SELECT_PLAYER:
                handlePvPSelectMenuClick(player, id);
                break;
        }
    }

    private void handlePvPGameMenuClick(Player player, String id) {
        switch (id) {
            case "pvp_hit":
                pvpHit(player);
                break;
            case "pvp_stand":
                pvpStand(player);
                break;
            case "close":
                player.closeInventory();
                break;
        }
    }

    // ============ Menu Methods ============

    private void openBetMenu(Player player) {
        String title = messages.format(player, "menu.blackjack.title");
        Inventory inv = Bukkit.createInventory(new BlackjackMenuHolder(MenuType.BET), 27, title);

        // Game info
        ItemStack infoItem = createItem(Material.BOOK,
            messages.format(player, "menu.blackjack.info_title"),
            new String[]{
                messages.format(player, "menu.blackjack.info_desc1"),
                messages.format(player, "menu.blackjack.info_desc2"),
                messages.format(player, "menu.blackjack.info_desc3")
            });
        safeSet(inv, 4, infoItem);

        // Bet options
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < betOptions.length && i < slots.length; i++) {
            int bet = betOptions[i];
            Map<String, String> map = new HashMap<String, String>();
            map.put("amount", String.valueOf(bet));

            ItemStack betItem = createItem(Material.GOLD_NUGGET,
                messages.format(player, "menu.blackjack.bet_button", map),
                new String[]{
                    messages.format(player, "menu.blackjack.bet_lore", map),
                    "",
                    messages.format(player, "menu.blackjack.click_to_bet"),
                    "ID:bet_" + bet
                });
            safeSet(inv, slots[i], betItem);
        }

        // PvP button - opens player selection menu
        ItemStack pvpItem = createItem(Material.PLAYER_HEAD,
            messages.format(player, "menu.blackjack.pvp_button"),
            new String[]{
                messages.format(player, "menu.blackjack.pvp_lore"),
                "",
                messages.format(player, "menu.blackjack.click_to_select_player"),
                "ID:pvp_menu"
            });
        safeSet(inv, 22, pvpItem);

        // Back to games button
        ItemStack backItem = createItem(Material.ARROW,
            messages.format(player, "menu.blackjack.back_to_games"),
            new String[]{
                messages.format(player, "menu.blackjack.back_to_games_lore"),
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
        String title = messages.format(player, "menu.blackjack.pvp_select_title");
        Inventory inv = Bukkit.createInventory(new BlackjackMenuHolder(MenuType.PVP_SELECT_PLAYER), 54, title);

        // Info item
        ItemStack infoItem = createItem(Material.BOOK,
            messages.format(player, "menu.blackjack.pvp_select_info"),
            new String[]{
                messages.format(player, "menu.blackjack.pvp_select_desc")
            });
        safeSet(inv, 4, infoItem);

        // Get online players and display their heads (async for skull meta)
        int slot = 10;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (slot > 43) break;
            if (onlinePlayer.getName().equals(player.getName())) continue; // Skip self
            if (activeSessions.containsKey(onlinePlayer.getName()) || pvpSessions.containsKey(onlinePlayer.getName())) {
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
                lore.add(messages.format(player, "menu.blackjack.pvp_click_to_invite", map));
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
            messages.format(player, "menu.blackjack.back"),
            new String[]{
                messages.format(player, "menu.blackjack.back_lore"),
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

    private void openGameMenu(Player player, BlackjackSession session) {
        String title = messages.format(player, "menu.blackjack.game_title");
        Inventory inv = Bukkit.createInventory(new BlackjackMenuHolder(MenuType.GAME), 54, title);

        List<Card> playerHand = session.getPlayerHand();
        List<Card> dealerHand = session.getDealerHand();
        int playerValue = calculateHandValue(playerHand);
        int dealerVisibleValue = dealerHand.isEmpty() ? 0 : getCardValue(dealerHand.get(0), 0);

        // Dealer's hand (top row, slots 2-6) - only first card visible during play
        safeSet(inv, 0, createItem(Material.ZOMBIE_HEAD,
            messages.format(player, "menu.blackjack.dealer_hand"),
            new String[]{
                messages.format(player, "menu.blackjack.visible_value", 
                    Collections.singletonMap("value", String.valueOf(dealerVisibleValue)))
            }));

        // Show dealer's first card
        if (!dealerHand.isEmpty()) {
            Card visibleCard = dealerHand.get(0);
            safeSet(inv, 2, createCardItem(player, visibleCard, true));
        }
        // Hidden card
        if (dealerHand.size() > 1) {
            safeSet(inv, 3, createItem(Material.BLACK_STAINED_GLASS_PANE,
                messages.format(player, "menu.blackjack.hidden_card"),
                new String[]{}));
        }

        // Player's hand (middle row, slots 18-26)
        Map<String, String> playerMap = new HashMap<String, String>();
        playerMap.put("value", String.valueOf(playerValue));
        safeSet(inv, 18, createItem(Material.PLAYER_HEAD,
            messages.format(player, "menu.blackjack.your_hand"),
            new String[]{
                messages.format(player, "menu.blackjack.your_value", playerMap)
            }));

        int[] playerSlots = {20, 21, 22, 23, 24, 25, 26};
        for (int i = 0; i < playerHand.size() && i < playerSlots.length; i++) {
            safeSet(inv, playerSlots[i], createCardItem(player, playerHand.get(i), true));
        }

        // Bet info
        Map<String, String> betMap = new HashMap<String, String>();
        betMap.put("bet", String.valueOf(session.getBet()));
        safeSet(inv, 4, createItem(Material.GOLD_INGOT,
            messages.format(player, "menu.blackjack.current_bet", betMap),
            new String[]{}));

        // Action buttons (bottom row)
        if (!session.isPlayerStand() && !session.isPlayerBust()) {
            // Turn prompt
            safeSet(inv, 31, createItem(Material.PAPER,
                messages.format(player, "menu.blackjack.turn_prompt"),
                new String[]{}));

            // Hit button
            safeSet(inv, 37, createItem(Material.LIME_WOOL,
                messages.format(player, "menu.blackjack.hit_button"),
                new String[]{
                    messages.format(player, "menu.blackjack.hit_lore"),
                    messages.format(player, "menu.blackjack.hit_lore2"),
                    "ID:hit"
                }));

            // Stand button
            safeSet(inv, 40, createItem(Material.RED_WOOL,
                messages.format(player, "menu.blackjack.stand_button"),
                new String[]{
                    messages.format(player, "menu.blackjack.stand_lore"),
                    messages.format(player, "menu.blackjack.stand_lore2"),
                    "ID:stand"
                }));

            // Double down (only if 2 cards)
            if (playerHand.size() == 2) {
                safeSet(inv, 43, createItem(Material.YELLOW_WOOL,
                    messages.format(player, "menu.blackjack.double_button"),
                    new String[]{
                        messages.format(player, "menu.blackjack.double_lore"),
                        messages.format(player, "menu.blackjack.double_lore2"),
                        messages.format(player, "menu.blackjack.double_lore3"),
                        "ID:double"
                    }));
            }
        }

        // Close button
        safeSet(inv, 53, createItem(Material.BARRIER,
            messages.format(player, "menu.close"),
            new String[]{"ID:close"}));

        player.openInventory(inv);
    }

    private void openResultMenu(Player player, BlackjackSession session) {
        String title = messages.format(player, "menu.blackjack.result_title");
        Inventory inv = Bukkit.createInventory(new BlackjackMenuHolder(MenuType.RESULT), 54, title);

        List<Card> playerHand = session.getPlayerHand();
        List<Card> dealerHand = session.getDealerHand();
        int playerValue = calculateHandValue(playerHand);
        int dealerValue = calculateHandValue(dealerHand);

        // Result message
        String resultKey;
        Material resultMaterial;
        if (session.isPlayerBust()) {
            resultKey = "menu.blackjack.result_bust";
            resultMaterial = Material.BARRIER;
        } else if (session.isDealerBust()) {
            resultKey = "menu.blackjack.result_dealer_bust";
            resultMaterial = Material.EMERALD;
        } else if (session.isPlayerBlackjack() && !session.isDealerBlackjack()) {
            resultKey = "menu.blackjack.result_blackjack";
            resultMaterial = Material.NETHER_STAR;
        } else if (session.isPlayerBlackjack() && session.isDealerBlackjack()) {
            resultKey = "menu.blackjack.result_push";
            resultMaterial = Material.IRON_INGOT;
        } else if (playerValue > dealerValue) {
            resultKey = "menu.blackjack.result_win";
            resultMaterial = Material.EMERALD;
        } else if (playerValue < dealerValue) {
            resultKey = "menu.blackjack.result_lose";
            resultMaterial = Material.BARRIER;
        } else {
            resultKey = "menu.blackjack.result_push";
            resultMaterial = Material.IRON_INGOT;
        }

        Map<String, String> resultMap = new HashMap<String, String>();
        resultMap.put("payout", String.valueOf(session.getPayout()));
        resultMap.put("player_value", String.valueOf(playerValue));
        resultMap.put("dealer_value", String.valueOf(dealerValue));

        safeSet(inv, 4, createItem(resultMaterial,
            messages.format(player, resultKey),
            new String[]{
                messages.format(player, "menu.blackjack.payout_lore", resultMap),
                messages.format(player, "menu.blackjack.final_hands", resultMap)
            }));

        // Dealer's full hand (top row)
        safeSet(inv, 0, createItem(Material.ZOMBIE_HEAD,
            messages.format(player, "menu.blackjack.dealer_final"),
            new String[]{
                messages.format(player, "menu.blackjack.dealer_value", 
                    Collections.singletonMap("value", String.valueOf(dealerValue)))
            }));

        int[] dealerSlots = {2, 3, 4, 5, 6, 7, 8};
        for (int i = 0; i < dealerHand.size() && i < dealerSlots.length; i++) {
            safeSet(inv, dealerSlots[i], createCardItem(player, dealerHand.get(i), true));
        }

        // Player's hand (middle row)
        Map<String, String> playerMap = new HashMap<String, String>();
        playerMap.put("value", String.valueOf(playerValue));
        safeSet(inv, 18, createItem(Material.PLAYER_HEAD,
            messages.format(player, "menu.blackjack.your_final"),
            new String[]{
                messages.format(player, "menu.blackjack.your_value", playerMap)
            }));

        int[] playerSlots = {20, 21, 22, 23, 24, 25, 26};
        for (int i = 0; i < playerHand.size() && i < playerSlots.length; i++) {
            safeSet(inv, playerSlots[i], createCardItem(player, playerHand.get(i), true));
        }

        // Play again button
        safeSet(inv, 40, createItem(Material.LIME_WOOL,
            messages.format(player, "menu.blackjack.play_again"),
            new String[]{
                messages.format(player, "menu.blackjack.play_again_lore"),
                "ID:play_again"
            }));

        // Close button
        safeSet(inv, 53, createItem(Material.BARRIER,
            messages.format(player, "menu.close"),
            new String[]{"ID:close"}));

        player.openInventory(inv);
    }

    // ============ Game Logic ============

    private void dealInitialCards(BlackjackSession session) {
        // Deal 2 cards to player, 2 to dealer
        session.getPlayerHand().add(drawCard());
        session.getDealerHand().add(drawCard());
        session.getPlayerHand().add(drawCard());
        session.getDealerHand().add(drawCard());
    }

    private Card drawCard() {
        int suit = random.nextInt(4);
        int rank = random.nextInt(13);
        return new Card(suit, rank);
    }

    private int calculateHandValue(List<Card> hand) {
        int value = 0;
        int aces = 0;

        for (Card card : hand) {
            int cardValue = getCardValue(card, aces);
            if (card.getRank() == 0) { // Ace
                aces++;
            }
            value += cardValue;
        }

        // Adjust for aces
        while (value > 21 && aces > 0) {
            value -= 10;
            aces--;
        }

        return value;
    }

    private int getCardValue(Card card, int acesAlreadyCounted) {
        int rank = card.getRank();
        if (rank == 0) { // Ace
            return 11;
        } else if (rank >= 10) { // J, Q, K
            return 10;
        } else {
            return rank + 1;
        }
    }

    private void dealerPlay(BlackjackSession session) {
        // Dealer hits on 16 or less, stands on 17+
        while (calculateHandValue(session.getDealerHand()) < 17) {
            session.getDealerHand().add(drawCard());
        }

        if (calculateHandValue(session.getDealerHand()) > 21) {
            session.setDealerBust(true);
        }

        // Check for dealer blackjack
        if (session.getDealerHand().size() == 2 && calculateHandValue(session.getDealerHand()) == 21) {
            session.setDealerBlackjack(true);
        }
    }

    private void endGame(Player player, BlackjackSession session) {
        session.setEnded(true);

        int playerValue = calculateHandValue(session.getPlayerHand());
        int dealerValue = calculateHandValue(session.getDealerHand());
        int bet = session.getBet();
        int payout = 0;

        if (session.isPlayerBust()) {
            // Player busts - loses bet
            payout = 0;
        } else if (session.isDealerBust()) {
            // Dealer busts - player wins
            payout = bet * 2;
        } else if (session.isPlayerBlackjack() && !session.isDealerBlackjack()) {
            // Player blackjack - pays 3:2
            payout = bet + (int)(bet * blackjackMultiplier);
        } else if (session.isPlayerBlackjack() && session.isDealerBlackjack()) {
            // Both blackjack - push
            payout = bet;
        } else if (playerValue > dealerValue) {
            // Player wins
            payout = bet * 2;
        } else if (playerValue < dealerValue) {
            // Dealer wins
            payout = 0;
        } else {
            // Push (tie)
            payout = bet;
        }

        session.setPayout(payout);

        // Grant rewards if player won
        if (payout > bet) {
            grantRewards(player, payout - bet);
        }

        // Show result
        Map<String, String> map = new HashMap<String, String>();
        map.put("payout", String.valueOf(payout));
        map.put("player_value", String.valueOf(playerValue));
        map.put("dealer_value", String.valueOf(dealerValue));

        if (payout > bet) {
            player.sendMessage(messages.format(player, "blackjack.you_won", map));
        } else if (payout == bet) {
            player.sendMessage(messages.format(player, "blackjack.push", map));
        } else {
            player.sendMessage(messages.format(player, "blackjack.you_lost", map));
        }

        activeSessions.remove(player.getName());
        openResultMenu(player, session);
    }

    private void grantRewards(Player player, int winAmount) {
        for (String command : winRewardCommands) {
            String cmd = command
                .replace("{player}", player.getName())
                .replace("{amount}", String.valueOf(winAmount));
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    // ============ Menu Click Handlers ============

    private void handleBetMenuClick(Player player, String id) {
        if (id.startsWith("bet_")) {
            try {
                int bet = Integer.parseInt(id.substring(4));
                player.closeInventory();
                startGame(player, bet);
            } catch (NumberFormatException ignored) {
            }
        } else if ("pvp_menu".equals(id)) {
            openPvPPlayerSelectMenu(player);
        } else if ("back_games".equals(id)) {
            player.closeInventory();
            // Notify plugin to open games menu (handled via callback pattern)
            if (openGamesMenuCallback != null) {
                openGamesMenuCallback.accept(player);
            }
        } else if ("close".equals(id)) {
            player.closeInventory();
        }
    }

    private void handlePvPSelectMenuClick(Player player, String id) {
        if (id.startsWith("invite_")) {
            String targetName = id.substring(7);
            player.closeInventory();
            invitePvP(player, targetName);
        } else if ("back".equals(id)) {
            openBetMenu(player);
        } else if ("close".equals(id)) {
            player.closeInventory();
        }
    }

    private void handleGameMenuClick(Player player, String id) {
        switch (id) {
            case "hit":
                hit(player);
                break;
            case "stand":
                stand(player);
                break;
            case "double":
                doubleDown(player);
                break;
            case "close":
                player.closeInventory();
                break;
        }
    }

    private void handleResultMenuClick(Player player, String id) {
        if ("play_again".equals(id)) {
            player.closeInventory();
            openBetMenu(player);
        } else if ("close".equals(id)) {
            player.closeInventory();
        }
    }

    // ============ Utility Methods ============

    private ItemStack createCardItem(Player player, Card card, boolean faceUp) {
        if (!faceUp) {
            return createItem(Material.BLACK_STAINED_GLASS_PANE,
                messages.format(player, "menu.blackjack.hidden_card"),
                new String[]{});
        }

        // Determine material based on card rank
        Material mat;
        int rank = card.getRank();
        if (rank == 0) {
            mat = Material.NETHER_STAR; // Ace
        } else if (rank >= 10) {
            mat = Material.EMERALD; // Face cards
        } else {
            mat = Material.PAPER; // Number cards
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put("card", card.toString());
        map.put("value", String.valueOf(rank == 0 ? 11 : Math.min(rank + 1, 10)));

        return createItem(mat,
            messages.format(player, "menu.blackjack.card_display", map),
            new String[]{
                messages.format(player, "menu.blackjack.card_value", map)
            });
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

    // ============ Inner Classes ============

    public enum MenuType {
        BET, GAME, RESULT, PVP_GAME, PVP_SELECT_PLAYER
    }

    public static class BlackjackMenuHolder implements InventoryHolder {
        private final MenuType menuType;

        public BlackjackMenuHolder(MenuType menuType) {
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

    private static class Card {
        private final int suit; // 0=spades, 1=hearts, 2=diamonds, 3=clubs
        private final int rank; // 0=A, 1-9=2-10, 10=J, 11=Q, 12=K

        Card(int suit, int rank) {
            this.suit = suit;
            this.rank = rank;
        }

        int getSuit() { return suit; }
        int getRank() { return rank; }

        @Override
        public String toString() {
            return CARD_RANKS[rank] + CARD_SUITS[suit];
        }
    }

    private static class BlackjackSession {
        private final String playerName;
        private int bet;
        private final List<Card> playerHand;
        private final List<Card> dealerHand;
        private boolean playerStand;
        private boolean playerBust;
        private boolean dealerBust;
        private boolean playerBlackjack;
        private boolean dealerBlackjack;
        private boolean doubledDown;
        private boolean ended;
        private int payout;

        BlackjackSession(String playerName, int bet) {
            this.playerName = playerName;
            this.bet = bet;
            this.playerHand = new ArrayList<Card>();
            this.dealerHand = new ArrayList<Card>();
            this.playerStand = false;
            this.playerBust = false;
            this.dealerBust = false;
            this.playerBlackjack = false;
            this.dealerBlackjack = false;
            this.doubledDown = false;
            this.ended = false;
            this.payout = 0;
        }

        String getPlayerName() { return playerName; }
        int getBet() { return bet; }
        void setBet(int bet) { this.bet = bet; }
        List<Card> getPlayerHand() { return playerHand; }
        List<Card> getDealerHand() { return dealerHand; }
        boolean isPlayerStand() { return playerStand; }
        void setPlayerStand(boolean stand) { this.playerStand = stand; }
        boolean isPlayerBust() { return playerBust; }
        void setPlayerBust(boolean bust) { this.playerBust = bust; }
        boolean isDealerBust() { return dealerBust; }
        void setDealerBust(boolean bust) { this.dealerBust = bust; }
        boolean isPlayerBlackjack() { return playerBlackjack; }
        void setPlayerBlackjack(boolean bj) { this.playerBlackjack = bj; }
        boolean isDealerBlackjack() { return dealerBlackjack; }
        void setDealerBlackjack(boolean bj) { this.dealerBlackjack = bj; }
        boolean isDoubledDown() { return doubledDown; }
        void setDoubledDown(boolean dd) { this.doubledDown = dd; }
        boolean isEnded() { return ended; }
        void setEnded(boolean ended) { this.ended = ended; }
        int getPayout() { return payout; }
        void setPayout(int payout) { this.payout = payout; }
    }

    private static class PvPBlackjackSession {
        private final String player1Name;
        private final String player2Name;
        private final List<Card> player1Hand;
        private final List<Card> player2Hand;
        private boolean player1Stand;
        private boolean player2Stand;
        private boolean player1Bust;
        private boolean player2Bust;
        private boolean player1Blackjack;
        private boolean player2Blackjack;
        private boolean player1Turn; // true = player1's turn, false = player2's turn
        private boolean ended;

        PvPBlackjackSession(String player1Name, String player2Name) {
            this.player1Name = player1Name;
            this.player2Name = player2Name;
            this.player1Hand = new ArrayList<Card>();
            this.player2Hand = new ArrayList<Card>();
            this.player1Stand = false;
            this.player2Stand = false;
            this.player1Bust = false;
            this.player2Bust = false;
            this.player1Blackjack = false;
            this.player2Blackjack = false;
            this.player1Turn = true; // Player1 goes first
            this.ended = false;
        }

        String getPlayer1Name() { return player1Name; }
        String getPlayer2Name() { return player2Name; }
        List<Card> getPlayer1Hand() { return player1Hand; }
        List<Card> getPlayer2Hand() { return player2Hand; }
        boolean isPlayer1Stand() { return player1Stand; }
        void setPlayer1Stand(boolean stand) { this.player1Stand = stand; }
        boolean isPlayer2Stand() { return player2Stand; }
        void setPlayer2Stand(boolean stand) { this.player2Stand = stand; }
        boolean isPlayer1Bust() { return player1Bust; }
        void setPlayer1Bust(boolean bust) { this.player1Bust = bust; }
        boolean isPlayer2Bust() { return player2Bust; }
        void setPlayer2Bust(boolean bust) { this.player2Bust = bust; }
        boolean isPlayer1Blackjack() { return player1Blackjack; }
        void setPlayer1Blackjack(boolean bj) { this.player1Blackjack = bj; }
        boolean isPlayer2Blackjack() { return player2Blackjack; }
        void setPlayer2Blackjack(boolean bj) { this.player2Blackjack = bj; }
        boolean isPlayer1Turn() { return player1Turn; }
        void setPlayer1Turn(boolean turn) { this.player1Turn = turn; }
        boolean isPlayerTurn(boolean isPlayer1) { return isPlayer1 ? player1Turn : !player1Turn; }
        boolean isEnded() { return ended; }
        void setEnded(boolean ended) { this.ended = ended; }
    }
}
