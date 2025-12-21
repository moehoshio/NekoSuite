package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.permission.Permission;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class NekoSuitePlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private static final int DAYS_PER_WEEK = 7;
    private static final int DAYS_PER_MONTH = 30;
    private static final int DAYS_PER_YEAR = 365;

    private Economy economy;
    private Permission permission;
    private Messages messages;
    private WishManager wishManager;
    private EventManager eventManager;
    private ExpManager expManager;
    private CdkManager cdkManager;
    private BuyManager buyManager;
    private MailManager mailManager;
    private MenuLayout menuLayout;
    private StrategyGameManager strategyGameManager;
    private ArtifactRewardsManager artifactRewardsManager;
    private TeleportManager teleportManager;
    private SkillManager skillManager;
    private AnnouncementManager announcementManager;
    private JoinQuitManager joinQuitManager;
    private RandomTeleportGameManager randomTeleportGameManager;
    private SurvivalArenaManager survivalArenaManager;
    private FishingContestManager fishingContestManager;

    @Override
    public void onEnable() {
        saveResource("language.yml", false);
        saveResource("lang/zh_tw.yml", false);
        saveResource("lang/en_us.yml", false);
        saveResource("lang/zh_cn.yml", false);
        saveResource("wish_config.yml", false);
        saveResource("event_config.yml", false);
        saveResource("tp_config.yml", false);
        saveResource("exp_config.yml", false);
        saveResource("cdk_config.yml", false);
        saveResource("buy_config.yml", false);
        saveResource("mail_config.yml", false);
        saveResource("menu_layout.yml", false);
        saveResource("strategy_game_config.yml", false);
        saveResource("artifact_rewards_config.yml", false);
        saveResource("skill_config.yml", false);
        saveResource("join_quit_config.yml", false);
        saveResource("random_teleport_config.yml", false);
        saveResource("survival_arena_config.yml", false);
        saveResource("fishing_contest_config.yml", false);
        setupEconomy();
        setupPermission();
        loadManagers();
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("wish") != null) {
            getCommand("wish").setExecutor(this);
            getCommand("wish").setTabCompleter(this);
        }
        if (getCommand("wishquery") != null) {
            getCommand("wishquery").setExecutor(this);
            getCommand("wishquery").setTabCompleter(this);
        }
        if (getCommand("wishmenu") != null) {
            getCommand("wishmenu").setExecutor(this);
            getCommand("wishmenu").setTabCompleter(this);
        }
        if (getCommand("eventcheck") != null) {
            getCommand("eventcheck").setExecutor(this);
            getCommand("eventcheck").setTabCompleter(this);
        }
        if (getCommand("eventparticipate") != null) {
            getCommand("eventparticipate").setExecutor(this);
            getCommand("eventparticipate").setTabCompleter(this);
        }
        if (getCommand("event") != null) {
            getCommand("event").setExecutor(this);
            getCommand("event").setTabCompleter(this);
        }
        if (getCommand("eventmenu") != null) {
            getCommand("eventmenu").setExecutor(this);
            getCommand("eventmenu").setTabCompleter(this);
        }
        if (getCommand("exp") != null) {
            getCommand("exp").setExecutor(this);
            getCommand("exp").setTabCompleter(this);
        }
        if (getCommand("expmenu") != null) {
            getCommand("expmenu").setExecutor(this);
            getCommand("expmenu").setTabCompleter(this);
        }
        if (getCommand("cdk") != null) {
            getCommand("cdk").setExecutor(this);
            getCommand("cdk").setTabCompleter(this);
        }
        if (getCommand("buy") != null) {
            getCommand("buy").setExecutor(this);
            getCommand("buy").setTabCompleter(this);
        }
        if (getCommand("buymenu") != null) {
            getCommand("buymenu").setExecutor(this);
            getCommand("buymenu").setTabCompleter(this);
        }
        if (getCommand("mail") != null) {
            getCommand("mail").setExecutor(this);
            getCommand("mail").setTabCompleter(this);
        }
        if (getCommand("mailmenu") != null) {
            getCommand("mailmenu").setExecutor(this);
            getCommand("mailmenu").setTabCompleter(this);
        }
        if (getCommand("mailsend") != null) {
            getCommand("mailsend").setExecutor(this);
            getCommand("mailsend").setTabCompleter(this);
        }
        if (getCommand("mailadmin") != null) {
            getCommand("mailadmin").setExecutor(this);
            getCommand("mailadmin").setTabCompleter(this);
        }
        if (getCommand("language") != null) {
            getCommand("language").setTabCompleter(this);
        }
        if (getCommand("language") != null) {
            getCommand("language").setExecutor(this);
        }
        if (getCommand("nekoreload") != null) {
            getCommand("nekoreload").setExecutor(this);
            getCommand("nekoreload").setTabCompleter(this);
        }
        if (getCommand("sgame") != null) {
            getCommand("sgame").setExecutor(this);
            getCommand("sgame").setTabCompleter(this);
        }
        if (getCommand("sgamemenu") != null) {
            getCommand("sgamemenu").setExecutor(this);
            getCommand("sgamemenu").setTabCompleter(this);
        }
        if (getCommand("artifact") != null) {
            getCommand("artifact").setExecutor(this);
            getCommand("artifact").setTabCompleter(this);
        }
        if (getCommand("announce") != null) {
            getCommand("announce").setExecutor(this);
            getCommand("announce").setTabCompleter(this);
        }
        if (getCommand("nekomenu") != null) {
            getCommand("nekomenu").setExecutor(this);
            getCommand("nekomenu").setTabCompleter(this);
        }
        if (getCommand("nekohelp") != null) {
            getCommand("nekohelp").setExecutor(this);
            getCommand("nekohelp").setTabCompleter(this);
        }
        if (getCommand("ntp") != null) {
            getCommand("ntp").setExecutor(this);
            getCommand("ntp").setTabCompleter(this);
        }
        if (getCommand("ntpadmin") != null) {
            getCommand("ntpadmin").setExecutor(this);
            getCommand("ntpadmin").setTabCompleter(this);
        }
        if (getCommand("skill") != null) {
            getCommand("skill").setExecutor(this);
            getCommand("skill").setTabCompleter(this);
        }
        if (getCommand("ngame") != null) {
            getCommand("ngame").setExecutor(this);
            getCommand("ngame").setTabCompleter(this);
        }

        getLogger().info("NekoSuite Bukkit module enabled (JDK 1.8 compatible).");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        switch (name) {
            case "wish":
                return handleWish(sender, args);
            case "event":
                return handleEvent(sender, args);
            case "exp":
                return handleExp(sender, args);
            case "cdk":
                return handleCdk(sender, args);
            case "buy":
                return handleBuy(sender, args);
            case "mail":
                return handleMail(sender, args);
            case "mailsend":
                return handleMailSend(sender, args);
            case "mailadmin":
                return handleMailAdmin(sender, args);
            case "language":
                return handleLanguage(sender, args);
            case "nekoreload":
                return handleReload(sender);
            case "sgame":
                return handleStrategyGame(sender, args);
            case "artifact":
                return handleArtifact(sender, args);
            case "announce":
                return handleAnnounce(sender, args);
            case "nekomenu":
            case "neko":
                return handleNekoMenu(sender, args);
            case "nekohelp":
                return handleNekoHelp(sender);
            case "ntp":
                return handleTeleport(sender, args);
            case "ntpadmin":
                return handleTeleportAdmin(sender, args);
            case "skill":
                return handleSkill(sender, args);
            case "ngame":
                return handleNekoGame(sender, args);
            default:
                return false;
        }
    }

    private boolean handleWish(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            // No args: open menu directly
            openWishMenu(player);
            return true;
        }
        if ("menu".equalsIgnoreCase(args[0])) {
            openWishMenu(player);
            return true;
        }
        String pool = args[0];
        int count = 1;
        if (args.length > 1) {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(messages.format(sender, "wish.count_invalid"));
                return true;
            }
        }

        try {
            List<String> rewards = wishManager.performWish(player, pool, count);
            Map<String, String> map = new HashMap<String, String>();
            map.put("rewards", String.join(", ", rewards));
            sender.sendMessage(messages.format(sender, "wish.success", map));
        } catch (WishException e) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("reason", e.getMessage());
            sender.sendMessage(messages.format(sender, "wish.failure", map));
        }
        return true;
    }

    private boolean handleEventCheck(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        List<EventAvailability> events = eventManager.listAvailableEvents(player);
        if (events.isEmpty()) {
            sender.sendMessage(messages.format(sender, "event.no_available"));
            return true;
        }
        sender.sendMessage(messages.format(sender, "event.header"));
        for (EventAvailability availability : events) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("name", availability.getDisplayName());
            map.put("id", availability.getId());
            if (availability.isCanParticipate()) {
                sender.sendMessage(messages.format(sender, "event.entry.available", map));
            } else {
                sender.sendMessage(messages.format(sender, "event.entry.limited", map));
            }
        }
        return true;
    }

    private boolean handleEvent(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        if (args.length == 0) {
            // Show available events and open menu
            return handleEventMenu(sender);
        }
        String sub = args[0].toLowerCase();
        if ("menu".equals(sub)) {
            return handleEventMenu(sender);
        }
        // Treat first argument as eventId to participate directly
        return handleEventParticipate(sender, new String[]{args[0]});
    }

    private boolean handleEventParticipate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(messages.format(sender, "event.participate.usage"));
            return true;
        }
        Player player = (Player) sender;
        try {
            List<String> rewards = eventManager.participate(player, args[0]);
            Map<String, String> map = new HashMap<String, String>();
            map.put("rewards", String.join(", ", rewards));
            sender.sendMessage(messages.format(sender, "event.reward", map));
        } catch (EventException e) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("reason", e.getMessage());
            sender.sendMessage(messages.format(sender, "event.failure", map));
        }
        return true;
    }

    private boolean handleWishMenu(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        openWishMenu(player);
        return true;
    }

    private boolean handleEventMenu(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        openEventMenu(player);
        return true;
    }

    private boolean handleExpMenu(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        expManager.openMenu(player);
        return true;
    }

    private boolean handleExp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            // No args: open menu directly
            expManager.openMenu(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("menu".equals(sub)) {
            expManager.openMenu(player);
            return true;
        }
        if ("deposit".equals(sub)) {
            long amount = player.getTotalExperience();
            if (args.length > 1 && !"all".equalsIgnoreCase(args[1])) {
                amount = parseLong(args[1]);
            }
            expManager.deposit(player, amount);
            return true;
        }
        if ("withdraw".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(messages.format(sender, "exp.usage"));
                return true;
            }
            long amount = parseLong(args[1]);
            expManager.withdraw(player, amount);
            return true;
        }
        if ("pay".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage(messages.format(sender, "exp.usage"));
                return true;
            }
            String target = args[1];
            long amount = parseLong(args[2]);
            expManager.transfer(player, target, amount);
            return true;
        }
        if ("exchange".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(messages.format(sender, "exp.usage"));
                return true;
            }
            expManager.exchange(player, args[1]);
            return true;
        }
        sender.sendMessage(messages.format(sender, "exp.usage"));
        return true;
    }

    private long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private boolean handleCdk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.format(sender, "cdk.usage"));
            return true;
        }
        Player player = (Player) sender;
        try {
            List<String> rewards = cdkManager.redeem(player, args[0]);
            Map<String, String> map = new HashMap<String, String>();
            map.put("rewards", String.join(", ", rewards));
            sender.sendMessage(messages.format(sender, "cdk.success", map));
        } catch (CdkException e) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("reason", e.getMessage());
            sender.sendMessage(messages.format(sender, "cdk.failure", map));
        }
        return true;
    }

    private boolean handleBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 1) {
            // No args: open menu directly
            buyManager.openMenu(player);
            return true;
        }
        if ("menu".equalsIgnoreCase(args[0])) {
            buyManager.openMenu(player);
            return true;
        }
        String productId;
        if (args.length >= 2) {
            productId = args[0] + args[1];
        } else {
            productId = args[0];
        }
        try {
            List<String> purchased = buyManager.purchase(player, productId);
            Map<String, String> map = new HashMap<String, String>();
            map.put("product", String.join(", ", purchased));
            sender.sendMessage(messages.format(sender, "buy.success", map));
        } catch (BuyManager.BuyException e) {
            sender.sendMessage(e.getMessage());
        }
        return true;
    }

    private boolean handleBuyMenu(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        buyManager.openMenu(player);
        return true;
    }

    private boolean handleMail(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            // No args: open menu directly
            mailManager.openMenu(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("menu".equals(sub)) {
            mailManager.openMenu(player);
            return true;
        }
        if ("claim".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(messages.format(sender, "mail.usage"));
                return true;
            }
            try {
                mailManager.claimMail(player, args[1]);
                sender.sendMessage(messages.format(sender, "mail.claimed"));
            } catch (MailManager.MailException e) {
                sender.sendMessage(e.getMessage());
            }
            return true;
        }
        if ("delete".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(messages.format(sender, "mail.usage"));
                return true;
            }
            try {
                mailManager.deleteMail(player, args[1]);
                sender.sendMessage(messages.format(sender, "mail.deleted"));
            } catch (MailManager.MailException e) {
                sender.sendMessage(e.getMessage());
            }
            return true;
        }
        sender.sendMessage(messages.format(sender, "mail.usage"));
        return true;
    }

    private boolean handleMailMenu(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        mailManager.openMenu(player);
        return true;
    }

    private boolean handleMailSend(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            sender.sendMessage(messages.format(sender, "mail.send_usage"));
            return true;
        }
        String recipient = args[0];
        String subject = args[1];
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (contentBuilder.length() > 0) {
                contentBuilder.append(" ");
            }
            contentBuilder.append(args[i]);
        }
        String content = contentBuilder.toString();
        
        try {
            mailManager.sendPlayerMail(player, recipient, subject, content, null, 0);
            Map<String, String> map = new HashMap<String, String>();
            map.put("recipient", recipient);
            sender.sendMessage(messages.format(sender, "mail.sent", map));
        } catch (MailManager.MailException e) {
            sender.sendMessage(e.getMessage());
        }
        return true;
    }

    private boolean handleMailAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nekosuite.mail.admin")) {
            sender.sendMessage(messages.format(sender, "common.no_permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(messages.format(sender, "mail.admin_usage"));
            return true;
        }
        String sub = args[0].toLowerCase();
        String recipient = args[1];
        
        if ("send".equals(sub)) {
            String subject = args[2];
            StringBuilder contentBuilder = new StringBuilder();
            List<String> commands = new ArrayList<String>();
            boolean parsingCommands = false;
            
            for (int i = 3; i < args.length; i++) {
                String arg = args[i];
                // Commands start with / or minecraft:
                if (arg.startsWith("/") || arg.startsWith("minecraft:")) {
                    parsingCommands = true;
                }
                if (parsingCommands) {
                    commands.add(arg);
                } else {
                    if (contentBuilder.length() > 0) {
                        contentBuilder.append(" ");
                    }
                    contentBuilder.append(arg);
                }
            }
            
            boolean success = mailManager.sendSystemMail(recipient, subject, contentBuilder.toString(), commands);
            if (success) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("recipient", recipient);
                sender.sendMessage(messages.format(sender, "mail.sent", map));
            } else {
                sender.sendMessage(messages.format(sender, "mail.mailbox_full"));
            }
            return true;
        }
        
        if ("template".equals(sub)) {
            // /mailadmin template <templateId> <recipient> [content...]
            // Load template from config and send with optional custom content
            if (args.length < 3) {
                sender.sendMessage(messages.format(sender, "mail.template_usage"));
                return true;
            }
            String templateId = args[1];
            String templateRecipient = args[2];
            
            // Try to load template from mail config
            File mailConfigFile = new File(getDataFolder(), "mail_config.yml");
            if (!mailConfigFile.exists()) {
                sender.sendMessage(messages.format(sender, "mail.template_not_found"));
                return true;
            }
            YamlConfiguration mailConfig = YamlConfiguration.loadConfiguration(mailConfigFile);
            ConfigurationSection templateSection = mailConfig.getConfigurationSection("templates." + templateId);
            if (templateSection == null) {
                Map<String, String> errMap = new HashMap<String, String>();
                errMap.put("template", templateId);
                sender.sendMessage(messages.format(sender, "mail.template_not_found", errMap));
                return true;
            }
            
            String subject = templateSection.getString("subject", "System Mail");
            String templateContent = templateSection.getString("content", "");
            List<String> templateCommands = templateSection.getStringList("commands");
            
            // If custom content is provided, use it instead of template content
            String finalContent = templateContent;
            if (args.length > 3) {
                StringBuilder customContent = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    if (customContent.length() > 0) {
                        customContent.append(" ");
                    }
                    customContent.append(args[i]);
                }
                finalContent = customContent.toString();
            }
            
            // Replace {player} placeholder in commands
            List<String> processedCommands = new ArrayList<String>();
            for (String cmd : templateCommands) {
                processedCommands.add(cmd.replace("{player}", templateRecipient));
            }
            
            boolean success = mailManager.sendSystemMail(templateRecipient, subject, finalContent, processedCommands);
            if (success) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("recipient", templateRecipient);
                map.put("template", templateId);
                sender.sendMessage(messages.format(sender, "mail.template_sent", map));
            } else {
                sender.sendMessage(messages.format(sender, "mail.mailbox_full"));
            }
            return true;
        }
        
        sender.sendMessage(messages.format(sender, "mail.admin_usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        switch (name) {
            case "wish":
                if (args.length == 1) {
                    List<String> options = new ArrayList<String>();
                    options.add("menu");
                    options.addAll(wishManager.getPools().keySet());
                    options.add(messages.getRaw(sender, "tab.wish.select_pool"));
                    return filter(options, args[0]);
                }
                if (args.length == 2) {
                    if ("menu".equalsIgnoreCase(args[0])) {
                        return Arrays.asList(messages.getRaw(sender, "tab.menu.open"));
                    }
                    // Suggest counts for wish pool
                    List<String> counts = new ArrayList<String>(Arrays.asList("1", "5", "10"));
                    counts.add(messages.getRaw(sender, "tab.wish.count"));
                    return filter(counts, args[1]);
                }
                if (args.length == 3) {
                    if (!"menu".equalsIgnoreCase(args[0])) {
                        return Arrays.asList(messages.getRaw(sender, "tab.wish.execute"));
                    }
                }
                break;
            case "event":
                if (args.length == 1) {
                    List<String> options = new ArrayList<String>();
                    options.add("menu");
                    options.addAll(eventManager.getActiveEventIds());
                    options.add(messages.getRaw(sender, "tab.event.select"));
                    return filter(options, args[0]);
                }
                if (args.length == 2) {
                    if ("menu".equalsIgnoreCase(args[0])) {
                        return Arrays.asList(messages.getRaw(sender, "tab.menu.open"));
                    }
                    return Arrays.asList(messages.getRaw(sender, "tab.event.participate"));
                }
                break;
            case "exp":
                if (args.length == 1) {
                    List<String> options = new ArrayList<String>();
                    options.add("menu");
                    options.add("deposit");
                    options.add("withdraw");
                    options.add("pay");
                    options.add("exchange");
                    options.add(messages.getRaw(sender, "tab.exp.select"));
                    return filter(options, args[0]);
                }
                if (args.length == 2) {
                    String sub = args[0].toLowerCase();
                    if ("menu".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.menu.open"));
                    }
                    if ("exchange".equals(sub)) {
                        List<String> exchanges = new ArrayList<String>(expManager.getExchangeIds());
                        exchanges.add(messages.getRaw(sender, "tab.exp.select_exchange"));
                        return filter(exchanges, args[1]);
                    }
                    if ("deposit".equals(sub)) {
                        List<String> opts = new ArrayList<String>();
                        opts.add("all");
                        opts.add(messages.getRaw(sender, "tab.exp.deposit_amount"));
                        return filter(opts, args[1]);
                    }
                    if ("withdraw".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.exp.withdraw_amount"));
                    }
                    if ("pay".equals(sub)) {
                        List<String> players = new ArrayList<String>();
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            players.add(p.getName());
                        }
                        players.add(messages.getRaw(sender, "tab.exp.select_player"));
                        return filter(players, args[1]);
                    }
                }
                if (args.length == 3) {
                    String sub = args[0].toLowerCase();
                    if ("exchange".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.exp.do_exchange"));
                    }
                    if ("deposit".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.exp.do_deposit"));
                    }
                    if ("pay".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.exp.pay_amount"));
                    }
                }
                if (args.length == 4) {
                    if ("pay".equalsIgnoreCase(args[0])) {
                        return Arrays.asList(messages.getRaw(sender, "tab.exp.do_pay"));
                    }
                }
                break;
            case "cdk":
                if (args.length == 1) {
                    return Arrays.asList(messages.getRaw(sender, "tab.cdk.code"));
                }
                if (args.length == 2) {
                    return Arrays.asList(messages.getRaw(sender, "tab.cdk.redeem"));
                }
                break;
            case "buy":
                if (args.length == 1) {
                    List<String> options = new ArrayList<String>();
                    options.add("menu");
                    options.add("vip");
                    options.add("mcd");
                    options.add("bag");
                    options.add(messages.getRaw(sender, "tab.buy.select_type"));
                    return filter(options, args[0]);
                }
                if (args.length == 2) {
                    String type = args[0].toLowerCase();
                    if ("menu".equals(type)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.menu.open"));
                    }
                    // Suggest levels based on type
                    List<String> levels = new ArrayList<String>();
                    if ("vip".equals(type) || "bag".equals(type)) {
                        levels.addAll(Arrays.asList("1", "2", "3", "4", "5", "6"));
                        levels.add(messages.getRaw(sender, "tab.buy.select_level"));
                    } else if ("mcd".equals(type)) {
                        levels.addAll(Arrays.asList("1", "2"));
                        levels.add(messages.getRaw(sender, "tab.buy.select_level"));
                    }
                    return filter(levels, args[1]);
                }
                if (args.length == 3) {
                    if (!"menu".equalsIgnoreCase(args[0])) {
                        return Arrays.asList(messages.getRaw(sender, "tab.buy.purchase"));
                    }
                }
                break;
            case "language":
                if (args.length == 1) {
                    List<String> options = new ArrayList<String>(messages.getSupportedLanguages());
                    options.add("list");
                    options.add("reset");
                    options.add("default");
                    options.add(messages.getRaw(sender, "tab.language.select"));
                    return filter(options, args[0]);
                }
                if (args.length == 2) {
                    String sub = args[0].toLowerCase();
                    if ("list".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.language.list"));
                    }
                    if ("reset".equals(sub) || "default".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.language.reset"));
                    }
                    return Arrays.asList(messages.getRaw(sender, "tab.language.set"));
                }
                break;
            case "mail":
                if (args.length == 1) {
                    List<String> actions = new ArrayList<String>();
                    actions.add("menu");
                    actions.add("claim");
                    actions.add("delete");
                    actions.add(messages.getRaw(sender, "tab.mail.select_action"));
                    return filter(actions, args[0]);
                }
                if (args.length == 2) {
                    String action = args[0].toLowerCase();
                    if ("menu".equals(action)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.menu.open"));
                    }
                    if (!(sender instanceof Player)) {
                        return Collections.emptyList();
                    }
                    Player player = (Player) sender;
                    List<MailManager.Mail> mails = mailManager.getMails(player.getName());
                    List<String> suggestions = new ArrayList<String>();
                    for (MailManager.Mail mail : mails) {
                        boolean claimable = mail.hasCommands() && !mail.isClaimed();
                        boolean deletable = mail.isClaimed() || !mail.hasCommands();

                        if ("claim".equals(action) && !claimable) {
                            continue;
                        }
                        if ("delete".equals(action) && !deletable) {
                            continue;
                        }

                        suggestions.add(mail.getId());
                    }
                    if ("claim".equals(action)) {
                        suggestions.add(messages.getRaw(sender, "tab.mail.select_claim"));
                    } else {
                        suggestions.add(messages.getRaw(sender, "tab.mail.select_delete"));
                    }
                    return filter(suggestions, args[1]);
                }
                if (args.length == 3) {
                    String action = args[0].toLowerCase();
                    if ("claim".equals(action)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.mail.do_claim"));
                    }
                    if ("delete".equals(action)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.mail.do_delete"));
                    }
                }
                break;
            case "mailsend":
                if (args.length == 1) {
                    List<String> players = new ArrayList<String>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        players.add(p.getName());
                    }
                    players.add(messages.getRaw(sender, "tab.mail.select_player"));
                    return filter(players, args[0]);
                }
                if (args.length == 2) {
                    return Arrays.asList(messages.getRaw(sender, "tab.mail.subject"));
                }
                if (args.length == 3) {
                    return Arrays.asList(messages.getRaw(sender, "tab.mail.content"));
                }
                if (args.length >= 4) {
                    return Arrays.asList(messages.getRaw(sender, "tab.mail.do_send"));
                }
                break;
            case "mailadmin":
                if (args.length == 1) {
                    List<String> options = new ArrayList<String>(Arrays.asList("send", "template"));
                    options.add(messages.getRaw(sender, "tab.mail.admin_select"));
                    return filter(options, args[0]);
                }
                if (args.length == 2) {
                    if ("template".equalsIgnoreCase(args[0])) {
                        List<String> templates = new ArrayList<String>(mailManager.getTemplateIds());
                        templates.add(messages.getRaw(sender, "tab.mail.select_template"));
                        return filter(templates, args[1]);
                    }
                    List<String> players = new ArrayList<String>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        players.add(p.getName());
                    }
                    players.add(messages.getRaw(sender, "tab.mail.select_player"));
                    return filter(players, args[1]);
                }
                if (args.length == 3) {
                    if ("template".equalsIgnoreCase(args[0])) {
                        List<String> players = new ArrayList<String>();
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            players.add(p.getName());
                        }
                        players.add(messages.getRaw(sender, "tab.mail.select_player"));
                        return filter(players, args[2]);
                    }
                    return Arrays.asList(messages.getRaw(sender, "tab.mail.subject"));
                }
                if (args.length == 4) {
                    if ("template".equalsIgnoreCase(args[0])) {
                        return Arrays.asList(messages.getRaw(sender, "tab.mail.template_send"));
                    }
                    return Arrays.asList(messages.getRaw(sender, "tab.mail.content"));
                }
                if (args.length >= 5) {
                    return Arrays.asList(messages.getRaw(sender, "tab.mail.content"));
                }
                break;
            case "nekoreload":
                break;
            case "sgame":
                if (args.length == 1) {
                    List<String> actions = new ArrayList<String>();
                    actions.add("menu");
                    actions.add("status");
                    actions.add("abandon");
                    return filter(actions, args[0]);
                }
                if (args.length == 2) {
                    String sub = args[0].toLowerCase();
                    if ("menu".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.menu.open"));
                    }
                    if ("status".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.sgame.status"));
                    }
                    if ("abandon".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.sgame.abandon"));
                    }
                }
                break;
            case "artifact":
                // 此命令僅供管理員使用
                if (!sender.hasPermission("nekosuite.artifact.admin")) {
                    return Collections.emptyList();
                }
                if (args.length == 1) {
                    List<String> options = new ArrayList<String>();
                    options.add("list");
                    options.add("give");
                    return filter(options, args[0]);
                }
                if (args.length == 2) {
                    String sub = args[0].toLowerCase();
                    if ("give".equals(sub)) {
                        List<String> players = new ArrayList<String>();
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            players.add(p.getName());
                        }
                        players.add(messages.getRaw(sender, "tab.artifact.select_player"));
                        return filter(players, args[1]);
                    }
                    if ("list".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.artifact.list"));
                    }
                }
                if (args.length == 3) {
                    String sub = args[0].toLowerCase();
                    if ("give".equals(sub)) {
                        List<String> items = new ArrayList<String>(artifactRewardsManager.getAvailableItemIds());
                        items.add(messages.getRaw(sender, "tab.artifact.select_item"));
                        return filter(items, args[2]);
                    }
                }
                if (args.length == 4) {
                    String sub = args[0].toLowerCase();
                    if ("give".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.artifact.do_give"));
                    }
                }
                break;
            case "ntp":
                if (args.length == 1) {
                    List<String> options = new ArrayList<String>();
                    options.add("accept");
                    options.add("deny");
                    options.add("toggle");
                    options.add("cancel");
                    options.add("status");
                    // Add online player names
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!p.getName().equals(sender.getName())) {
                            options.add(p.getName());
                        }
                    }
                    return filter(options, args[0]);
                }
                break;
            case "ntpadmin":
                if (args.length == 1) {
                    return filter(Arrays.asList("lock", "unlock", "status"), args[0]);
                }
                if (args.length == 2) {
                    List<String> playerNames = new ArrayList<String>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        playerNames.add(p.getName());
                    }
                    return filter(playerNames, args[1]);
                }
                break;
            case "nekomenu":
            case "neko":
                if (args.length == 1) {
                    return filter(Arrays.asList("menu", "help", "game"), args[0]);
                }
                // Handle /neko game <subcommand> tab completion
                if (args.length >= 2 && "game".equalsIgnoreCase(args[0])) {
                    if (args.length == 2) {
                        return filter(Arrays.asList("rtp", "arena", "fishing", "menu"), args[1]);
                    }
                    if (args.length == 3) {
                        String gameType = args[1].toLowerCase();
                        if ("rtp".equals(gameType)) {
                            return filter(Arrays.asList("menu", "start", "status", "end"), args[2]);
                        }
                        if ("arena".equals(gameType)) {
                            return filter(Arrays.asList("menu", "start", "status", "end"), args[2]);
                        }
                        if ("fishing".equals(gameType)) {
                            return filter(Arrays.asList("menu", "start", "join", "status", "leaderboard", "end"), args[2]);
                        }
                    }
                }
                break;
            case "skill":
                if (args.length == 1) {
                    List<String> options = new ArrayList<String>();
                    options.add("list");
                    options.add("info");
                    options.add(messages.getRaw(sender, "tab.skill.select_action"));
                    return filter(options, args[0]);
                }
                if (args.length == 2) {
                    String sub = args[0].toLowerCase();
                    if ("list".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.skill.show_list"));
                    }
                    if ("info".equals(sub)) {
                        List<String> skillIds = new ArrayList<String>(skillManager.getSkillIds());
                        skillIds.add(messages.getRaw(sender, "tab.skill.select_skill"));
                        return filter(skillIds, args[1]);
                    }
                }
                if (args.length == 3) {
                    String sub = args[0].toLowerCase();
                    if ("info".equals(sub)) {
                        return Arrays.asList(messages.getRaw(sender, "tab.skill.show_info"));
                    }
                }
                break;
            case "ngame":
                if (args.length == 1) {
                    return filter(Arrays.asList("rtp", "arena", "fishing", "menu"), args[0]);
                }
                if (args.length == 2) {
                    String sub = args[0].toLowerCase();
                    if ("rtp".equals(sub)) {
                        return filter(Arrays.asList("menu", "start", "status", "end"), args[1]);
                    }
                    if ("arena".equals(sub)) {
                        return filter(Arrays.asList("menu", "start", "status", "end"), args[1]);
                    }
                    if ("fishing".equals(sub)) {
                        return filter(Arrays.asList("menu", "start", "join", "status", "leaderboard", "end"), args[1]);
                    }
                }
                break;
            default:
                break;
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        if (options == null) {
            return Collections.emptyList();
        }
        String low = prefix == null ? "" : prefix.toLowerCase();
        List<String> out = new ArrayList<String>();
        for (String opt : options) {
            if (opt == null) {
                continue;
            }
            if (opt.toLowerCase().startsWith(low)) {
                out.add(opt);
            }
        }
        return out;
    }


    private void setupEconomy() {
        try {
            RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
            if (provider != null) {
                economy = provider.getProvider();
            } else {
                getLogger().warning("Vault economy provider not found; currency costs will be skipped.");
            }
        } catch (NoClassDefFoundError e) {
            getLogger().warning("Vault not found; currency costs will be skipped.");
        }
    }

    private void setupPermission() {
        try {
            RegisteredServiceProvider<Permission> provider = getServer().getServicesManager().getRegistration(Permission.class);
            if (provider != null) {
                permission = provider.getProvider();
            } else {
                getLogger().warning("Vault permission provider not found; permission reconciliation will be skipped.");
            }
        } catch (NoClassDefFoundError e) {
            getLogger().warning("Vault not found; permission reconciliation will be skipped.");
        }
    }

        private void loadManagers() {
        messages = new Messages(this);
        menuLayout = new MenuLayout(this);
        wishManager = new WishManager(this, messages, new File(getDataFolder(), "wish_config.yml"), economy);
        eventManager = new EventManager(this, messages, new File(getDataFolder(), "event_config.yml"));
        expManager = new ExpManager(this, messages, new File(getDataFolder(), "exp_config.yml"), menuLayout);
        cdkManager = new CdkManager(this, messages, new File(getDataFolder(), "cdk_config.yml"));
        buyManager = new BuyManager(this, messages, new File(getDataFolder(), "buy_config.yml"), menuLayout, economy, permission);
        mailManager = new MailManager(this, messages, new File(getDataFolder(), "mail_config.yml"), menuLayout);
        strategyGameManager = new StrategyGameManager(this, messages, new File(getDataFolder(), "strategy_game_config.yml"), menuLayout);
        artifactRewardsManager = new ArtifactRewardsManager(this, messages, new File(getDataFolder(), "artifact_rewards_config.yml"));
        teleportManager = new TeleportManager(this, messages, new File(getDataFolder(), "tp_config.yml"), economy);
        skillManager = new SkillManager(this, messages, new File(getDataFolder(), "skill_config.yml"));
        announcementManager = new AnnouncementManager(this, messages, menuLayout);
        joinQuitManager = new JoinQuitManager(this, messages, buyManager);
        randomTeleportGameManager = new RandomTeleportGameManager(this, messages, new File(getDataFolder(), "random_teleport_config.yml"), menuLayout);
        survivalArenaManager = new SurvivalArenaManager(this, messages, new File(getDataFolder(), "survival_arena_config.yml"), menuLayout);
        fishingContestManager = new FishingContestManager(this, messages, new File(getDataFolder(), "fishing_contest_config.yml"), menuLayout);
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("nekosuite.reload")) {
            sender.sendMessage(messages.format(sender, "common.no_permission"));
            return true;
        }
        loadManagers();
        sender.sendMessage(messages.format(sender, "common.reload_success"));
        return true;
    }

    private boolean handleStrategyGame(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            // No args: open menu (will show start or continue based on game state)
            strategyGameManager.continueGame(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "menu":
                strategyGameManager.continueGame(player);
                break;
            case "status":
                strategyGameManager.showStatus(player);
                break;
            case "abandon":
                strategyGameManager.abandonGame(player);
                break;
            default:
                // Default: open menu
                strategyGameManager.continueGame(player);
                break;
        }
        return true;
    }

    private boolean handleStrategyGameMenu(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        strategyGameManager.continueGame(player);
        return true;
    }

    private boolean handleArtifact(CommandSender sender, String[] args) {
        // 此命令僅供管理員使用
        if (!sender.hasPermission("nekosuite.artifact.admin")) {
            sender.sendMessage(messages.format(sender, "common.no_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(messages.format(sender, "artifact.usage"));
            return true;
        }

        String sub = args[0].toLowerCase();
        
        // 列出可用物品
        if ("list".equals(sub)) {
            List<String> itemIds = artifactRewardsManager.getAvailableItemIds();
            if (itemIds.isEmpty()) {
                sender.sendMessage(messages.format(sender, "artifact.no_items"));
                return true;
            }
            sender.sendMessage(messages.format(sender, "artifact.list_header"));
            for (String id : itemIds) {
                ArtifactRewardsManager.ArtifactItem item = artifactRewardsManager.getItem(id);
                if (item != null) {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("id", id);
                    map.put("name", item.getDisplayName());
                    map.put("description", item.getDescription() != null ? item.getDescription() : "");
                    sender.sendMessage(messages.format(sender, "artifact.list_entry", map));
                }
            }
            return true;
        }

        // 發放物品給玩家: /artifact give <玩家> <物品ID>
        if ("give".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage(messages.format(sender, "artifact.admin_usage"));
                return true;
            }
            String targetName = args[1];
            String itemId = args[2];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null || !target.isOnline()) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("player", targetName);
                sender.sendMessage(messages.format(sender, "artifact.player_not_found", map));
                return true;
            }
            try {
                String displayName = artifactRewardsManager.giveReward(target, itemId);
                Map<String, String> map = new HashMap<String, String>();
                map.put("player", target.getName());
                map.put("item", displayName);
                sender.sendMessage(messages.format(sender, "artifact.admin_success", map));
                // 發送帶懸浮信息的訊息給目標玩家 (使用原始訊息模板，讓 sendItemMessage 處理 {item} 替換)
                artifactRewardsManager.sendItemMessage(target, itemId, 
                    messages.getRaw(target, "artifact.receive"));
            } catch (ArtifactRewardsManager.ArtifactException e) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("item", itemId);
                sender.sendMessage(messages.format(sender, "artifact.not_found", map));
            }
            return true;
        }

        // 未知子命令，顯示用法
        sender.sendMessage(messages.format(sender, "artifact.usage"));
        return true;
    }

    /**
     * 獲取 ArtifactRewardsManager 實例 (供其他插件調用)
     * @return ArtifactRewardsManager 實例
     */
    public ArtifactRewardsManager getArtifactRewardsManager() {
        return artifactRewardsManager;
    }

    private boolean handleAnnounce(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nekosuite.announce")) {
            sender.sendMessage(messages.format(sender, "common.no_permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.format(sender, "announce.usage"));
            return true;
        }
        // Build the announcement message from all arguments
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (messageBuilder.length() > 0) {
                messageBuilder.append(" ");
            }
            messageBuilder.append(args[i]);
        }
        String announcement = messageBuilder.toString();
        
        // Get the prefix from config and broadcast
        String prefix = messages.getRaw(sender, "announce.prefix");
        String fullMessage = messages.colorize(prefix + announcement);
        Bukkit.broadcastMessage(fullMessage);
        
        sender.sendMessage(messages.format(sender, "announce.success"));
        return true;
    }

    private boolean handleNekoMenu(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        
        // Handle /neko help subcommand
        if (args.length > 0 && "help".equalsIgnoreCase(args[0])) {
            openHelpMenu(player);
            return true;
        }
        
        // Handle /neko game subcommand - redirect to game menu
        if (args.length > 0 && "game".equalsIgnoreCase(args[0])) {
            // Shift args for handleNekoGame
            String[] gameArgs = new String[args.length - 1];
            System.arraycopy(args, 1, gameArgs, 0, args.length - 1);
            return handleNekoGame(sender, gameArgs);
        }
        
        // /neko or /neko menu opens navigation
        openNavigationMenu(player);
        return true;
    }

    /**
     * Unified game command handler for /ngame or /neko game
     * Subcommands: rtp, arena, fishing
     */
    private boolean handleNekoGame(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;

        // No args or "menu" - open games menu
        if (args.length == 0 || "menu".equalsIgnoreCase(args[0])) {
            openGamesMenu(player);
            return true;
        }

        String gameType = args[0].toLowerCase();
        // Shift args to get subcommand
        String[] subArgs = new String[args.length - 1];
        if (args.length > 1) {
            System.arraycopy(args, 1, subArgs, 0, args.length - 1);
        }

        switch (gameType) {
            case "rtp":
                return handleRtpGameSubcommand(player, subArgs);
            case "arena":
                return handleArenaSubcommand(player, subArgs);
            case "fishing":
                return handleFishingSubcommand(player, subArgs);
            default:
                // Unknown game type - show usage
                sender.sendMessage(messages.format(sender, "ngame.usage"));
                return true;
        }
    }

    private boolean handleRtpGameSubcommand(Player player, String[] args) {
        if (args.length == 0) {
            randomTeleportGameManager.openMenu(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "menu":
                randomTeleportGameManager.openMenu(player);
                break;
            case "start":
                randomTeleportGameManager.startGame(player);
                break;
            case "status":
                randomTeleportGameManager.showStatus(player);
                break;
            case "end":
            case "stop":
                randomTeleportGameManager.endGame(player);
                break;
            default:
                randomTeleportGameManager.openMenu(player);
                break;
        }
        return true;
    }

    private boolean handleArenaSubcommand(Player player, String[] args) {
        if (args.length == 0) {
            survivalArenaManager.openMenu(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "menu":
                survivalArenaManager.openMenu(player);
                break;
            case "start":
                survivalArenaManager.startGame(player);
                break;
            case "status":
                survivalArenaManager.showStatus(player);
                break;
            case "end":
            case "stop":
                survivalArenaManager.endGame(player);
                break;
            default:
                survivalArenaManager.openMenu(player);
                break;
        }
        return true;
    }

    private boolean handleFishingSubcommand(Player player, String[] args) {
        if (args.length == 0) {
            fishingContestManager.openMenu(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "menu":
                fishingContestManager.openMenu(player);
                break;
            case "start":
                fishingContestManager.startContest(player);
                break;
            case "join":
                fishingContestManager.joinContest(player);
                break;
            case "status":
                fishingContestManager.showStatus(player);
                break;
            case "leaderboard":
            case "top":
                fishingContestManager.showLeaderboard(player);
                break;
            case "end":
            case "stop":
                if (player.hasPermission("nekosuite.fishing.admin")) {
                    fishingContestManager.endContest();
                } else {
                    player.sendMessage(messages.format(player, "common.no_permission"));
                }
                break;
            default:
                fishingContestManager.openMenu(player);
                break;
        }
        return true;
    }

    private boolean handleNekoHelp(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        openHelpMenu(player);
        return true;
    }

    private boolean handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;

        // Check if player is locked from TP
        if (teleportManager.isLocked(player)) {
            player.sendMessage(messages.format(player, "tp.locked_self"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(messages.format(sender, "tp.usage"));
            // Show current TP status
            if (teleportManager.isTpEnabled(player.getName())) {
                sender.sendMessage(messages.format(sender, "tp.status_on"));
            } else {
                sender.sendMessage(messages.format(sender, "tp.status_off"));
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "accept":
            case "yes":
            case "y":
                teleportManager.acceptTpRequest(player);
                return true;
            case "deny":
            case "no":
            case "n":
                teleportManager.denyTpRequest(player);
                return true;
            case "toggle":
                boolean newStatus = teleportManager.toggleTpStatus(player);
                if (newStatus) {
                    player.sendMessage(messages.format(player, "tp.toggle_on"));
                } else {
                    player.sendMessage(messages.format(player, "tp.toggle_off"));
                }
                return true;
            case "cancel":
                teleportManager.cancelTpRequest(player);
                return true;
            case "status":
                if (teleportManager.isTpEnabled(player.getName())) {
                    player.sendMessage(messages.format(player, "tp.status_on"));
                } else {
                    player.sendMessage(messages.format(player, "tp.status_off"));
                }
                return true;
            default:
                // Assume it's a player name - send TP request
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null || !target.isOnline()) {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("player", args[0]);
                    player.sendMessage(messages.format(player, "tp.player_not_found", map));
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage(messages.format(player, "tp.cannot_tp_self"));
                    return true;
                }
                teleportManager.sendTpRequest(player, target);
                return true;
        }
    }

    private boolean handleTeleportAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nekosuite.tp.admin")) {
            sender.sendMessage(messages.format(sender, "common.no_permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messages.format(sender, "tp.admin_usage"));
            return true;
        }

        String action = args[0].toLowerCase();
        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null || !target.isOnline()) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("player", targetName);
            sender.sendMessage(messages.format(sender, "tp.player_not_found", map));
            return true;
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put("player", target.getName());

        switch (action) {
            case "lock":
                teleportManager.lockPlayer(target);
                sender.sendMessage(messages.format(sender, "tp.admin_lock_success", map));
                target.sendMessage(messages.format(target, "tp.locked_by_admin"));
                return true;
            case "unlock":
                teleportManager.unlockPlayer(target);
                sender.sendMessage(messages.format(sender, "tp.admin_unlock_success", map));
                target.sendMessage(messages.format(target, "tp.unlocked_by_admin"));
                return true;
            case "status":
                boolean isLocked = teleportManager.isLocked(target);
                boolean tpEnabled = teleportManager.isTpEnabled(target.getName());
                map.put("locked", isLocked ? "true" : "false");
                map.put("enabled", tpEnabled ? "true" : "false");
                sender.sendMessage(messages.format(sender, "tp.admin_status", map));
                return true;
            default:
                sender.sendMessage(messages.format(sender, "tp.admin_usage"));
                return true;
        }
    }

    private boolean handleSkill(CommandSender sender, String[] args) {
        if (!skillManager.isEnabled()) {
            sender.sendMessage(messages.format(sender, "skill.disabled"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(messages.format(sender, "skill.usage"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if ("list".equals(sub)) {
            List<String> skillIds = skillManager.getSkillIds();
            if (skillIds.isEmpty()) {
                sender.sendMessage(messages.format(sender, "skill.list_header"));
                return true;
            }
            sender.sendMessage(messages.format(sender, "skill.list_header"));
            for (String skillId : skillIds) {
                SkillManager.SkillDefinition skill = skillManager.getSkill(skillId);
                if (skill != null) {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("id", skillId);
                    map.put("name", skill.getName());
                    String trigger = skill.getTrigger();
                    String translatedTrigger = messages.getRaw(sender, "skill.triggers." + trigger);
                    if (translatedTrigger.equals("skill.triggers." + trigger)) {
                        translatedTrigger = trigger;
                    }
                    map.put("trigger", translatedTrigger);
                    sender.sendMessage(messages.format(sender, "skill.list_entry", map));
                }
            }
            return true;
        }

        if ("info".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(messages.format(sender, "skill.usage"));
                return true;
            }
            String skillId = args[1];
            SkillManager.SkillDefinition skill = skillManager.getSkill(skillId);
            if (skill == null) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("skill", skillId);
                sender.sendMessage(messages.format(sender, "skill.not_found", map));
                return true;
            }
            
            // Display skill info
            sender.sendMessage(messages.format(sender, "skill.info_header"));
            
            Map<String, String> nameMap = new HashMap<String, String>();
            nameMap.put("name", skill.getName());
            sender.sendMessage(messages.format(sender, "skill.info_name", nameMap));
            
            Map<String, String> typeMap = new HashMap<String, String>();
            String type = skill.getType();
            String translatedType = messages.getRaw(sender, "skill.types." + type);
            if (translatedType.equals("skill.types." + type)) {
                translatedType = type;
            }
            typeMap.put("type", translatedType);
            sender.sendMessage(messages.format(sender, "skill.info_type", typeMap));
            
            Map<String, String> triggerMap = new HashMap<String, String>();
            String trigger = skill.getTrigger();
            String translatedTrigger = messages.getRaw(sender, "skill.triggers." + trigger);
            if (translatedTrigger.equals("skill.triggers." + trigger)) {
                translatedTrigger = trigger;
            }
            triggerMap.put("trigger", translatedTrigger);
            sender.sendMessage(messages.format(sender, "skill.info_trigger", triggerMap));
            
            Map<String, String> loreMap = new HashMap<String, String>();
            loreMap.put("lore", skill.getLoreRequirement());
            sender.sendMessage(messages.format(sender, "skill.info_lore", loreMap));
            
            Map<String, String> cdMap = new HashMap<String, String>();
            cdMap.put("cooldown", String.valueOf(skill.getCooldown()));
            sender.sendMessage(messages.format(sender, "skill.info_cooldown", cdMap));
            
            Map<String, String> descMap = new HashMap<String, String>();
            descMap.put("description", skill.getDescription());
            sender.sendMessage(messages.format(sender, "skill.info_description", descMap));
            
            return true;
        }

        sender.sendMessage(messages.format(sender, "skill.usage"));
        return true;
    }

    /**
     * Get the TeleportManager instance for API access.
     * Other plugins/features can use this to lock/unlock players.
     */
    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    /**
     * Get the SkillManager instance for API access.
     */
    public SkillManager getSkillManager() {
        return skillManager;
    }

    private void openNavigationMenu(Player player) {
        MenuLayout.NavigationLayout layout = menuLayout.getNavigationLayout();
        Inventory inv = Bukkit.createInventory(new NavigationMenuHolder(), layout.getSize(), messages.format(player, layout.getTitleKey()));
        
        // Add player head with detailed info at player_head_slot
        final int headSlot = layout.getPlayerHeadSlot();
        ItemStack playerHead = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta skullMeta = (org.bukkit.inventory.meta.SkullMeta) playerHead.getItemMeta();
        if (skullMeta != null) {
            Map<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("player", player.getName());
            skullMeta.setDisplayName(messages.format(player, "navigation.player_info", placeholders));
            
            List<String> lore = new ArrayList<String>();
            
            // Add balance info if economy is available
            if (economy != null) {
                double balance = economy.getBalance(player);
                Map<String, String> balancePlaceholders = new HashMap<String, String>();
                balancePlaceholders.put("balance", String.format("%.2f", balance));
                lore.add(messages.format(player, "navigation.balance_info", balancePlaceholders));
            }
            
            // Add health info
            Map<String, String> healthPlaceholders = new HashMap<String, String>();
            healthPlaceholders.put("health", String.format("%.1f", player.getHealth()));
            healthPlaceholders.put("max_health", String.format("%.1f", player.getMaxHealth()));
            lore.add(messages.format(player, "navigation.health_info", healthPlaceholders));
            
            // Add experience level info
            Map<String, String> expPlaceholders = new HashMap<String, String>();
            expPlaceholders.put("level", String.valueOf(player.getLevel()));
            expPlaceholders.put("exp", String.valueOf(player.getTotalExperience()));
            lore.add(messages.format(player, "navigation.exp_info", expPlaceholders));
            
            // Add stored exp if expManager is available
            if (expManager != null) {
                long storedExp = expManager.getStored(player.getName());
                Map<String, String> storedExpPlaceholders = new HashMap<String, String>();
                storedExpPlaceholders.put("stored", String.valueOf(storedExp));
                lore.add(messages.format(player, "navigation.stored_exp_info", storedExpPlaceholders));
            }
            
            // Add playtime info (if available via Statistic)
            try {
                int playTicks = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
                long playMinutes = playTicks / 1200; // 1200 ticks = 1 minute (20 ticks/sec * 60 sec/min)
                long playHours = playMinutes / 60;
                Map<String, String> playtimePlaceholders = new HashMap<String, String>();
                playtimePlaceholders.put("hours", String.valueOf(playHours));
                playtimePlaceholders.put("minutes", String.valueOf(playMinutes % 60));
                lore.add(messages.format(player, "navigation.playtime_info", playtimePlaceholders));
            } catch (Exception ignored) {
                // Statistic not available
            }
            
            skullMeta.setLore(lore);
            
            // Set placeholder first (PLAYER_HEAD without owner shows Steve)
            playerHead.setItemMeta(skullMeta);
        }
        inv.setItem(headSlot, playerHead);
        
        // Asynchronously load player skull texture
        final Player targetPlayer = player;
        final Inventory finalInv = inv;
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                ItemStack headItem = finalInv.getItem(headSlot);
                if (headItem == null || headItem.getType() != org.bukkit.Material.PLAYER_HEAD) {
                    return;
                }
                final org.bukkit.inventory.meta.SkullMeta asyncMeta = (org.bukkit.inventory.meta.SkullMeta) headItem.getItemMeta();
                if (asyncMeta != null) {
                    asyncMeta.setOwningPlayer(targetPlayer);
                    // Update on main thread
                    Bukkit.getScheduler().runTask(NekoSuitePlugin.this, new Runnable() {
                        @Override
                        public void run() {
                            if (targetPlayer.isOnline() && targetPlayer.getOpenInventory().getTopInventory().getHolder() instanceof NavigationMenuHolder) {
                                ItemStack currentHeadItem = finalInv.getItem(headSlot);
                                if (currentHeadItem != null && currentHeadItem.getType() == org.bukkit.Material.PLAYER_HEAD) {
                                    currentHeadItem.setItemMeta(asyncMeta);
                                }
                            }
                        }
                    });
                }
            }
        });
        
        // Render items from config
        for (MenuLayout.MenuItem item : layout.getItems().values()) {
            org.bukkit.Material material = org.bukkit.Material.STONE;
            try {
                material = org.bukkit.Material.valueOf(item.getMaterial().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(messages.format(player, item.getNameKey()));
                List<String> lore = new ArrayList<String>();
                // Check if lore_key is a list or single string
                List<String> loreLines = messages.getList(player, item.getLoreKey());
                if (loreLines != null && !loreLines.isEmpty()) {
                    lore.addAll(messages.colorize(loreLines));
                } else {
                    String singleLore = messages.format(player, item.getLoreKey());
                    if (!singleLore.equals(item.getLoreKey())) {
                        lore.add(singleLore);
                    }
                }
                if (item.hasAction()) {
                    lore.add(ChatColor.DARK_GRAY + "ACTION:" + item.getAction());
                }
                if (item.hasCommand()) {
                    lore.add(ChatColor.DARK_GRAY + "COMMAND:" + item.getCommand());
                }
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }
            inv.setItem(item.getSlot(), stack);
        }
        
        // Close button
        inv.setItem(layout.getCloseSlot(), createCloseItem(player));
        
        player.openInventory(inv);
    }

    private void openHelpMenu(Player player) {
        MenuLayout.HelpLayout layout = menuLayout.getHelpLayout();
        Inventory inv = Bukkit.createInventory(new HelpMenuHolder(), layout.getSize(), messages.format(player, layout.getTitleKey()));
        
        // Render items from config
        for (MenuLayout.MenuItem item : layout.getItems().values()) {
            org.bukkit.Material material = org.bukkit.Material.STONE;
            try {
                material = org.bukkit.Material.valueOf(item.getMaterial().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(messages.format(player, item.getNameKey()));
                List<String> lore = new ArrayList<String>();
                // Check if lore_key is a list or single string
                List<String> loreLines = messages.getList(player, item.getLoreKey());
                if (loreLines != null && !loreLines.isEmpty()) {
                    lore.addAll(messages.colorize(loreLines));
                } else {
                    String singleLore = messages.format(player, item.getLoreKey());
                    if (!singleLore.equals(item.getLoreKey())) {
                        lore.add(singleLore);
                    }
                }
                if (item.hasAction()) {
                    lore.add(ChatColor.DARK_GRAY + "ACTION:" + item.getAction());
                }
                if (item.hasCommand()) {
                    lore.add(ChatColor.DARK_GRAY + "COMMAND:" + item.getCommand());
                }
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }
            inv.setItem(item.getSlot(), stack);
        }
        
        // Close button
        inv.setItem(layout.getCloseSlot(), createCloseItem(player));
        
        player.openInventory(inv);
    }

    // Inventory holders for new menus
    private static class NavigationMenuHolder implements InventoryHolder {
        public Inventory getInventory() { return null; }
    }

    private static class HelpMenuHolder implements InventoryHolder {
        public Inventory getInventory() { return null; }
    }

    private static class GamesMenuHolder implements InventoryHolder {
        public Inventory getInventory() { return null; }
    }

    private void openGamesMenu(Player player) {
        MenuLayout.GamesLayout layout = menuLayout.getGamesLayout();
        Inventory inv = Bukkit.createInventory(new GamesMenuHolder(), layout.getSize(), messages.format(player, layout.getTitleKey()));
        
        // Render items from config
        for (MenuLayout.MenuItem item : layout.getItems().values()) {
            org.bukkit.Material material = org.bukkit.Material.STONE;
            try {
                material = org.bukkit.Material.valueOf(item.getMaterial().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(messages.format(player, item.getNameKey()));
                List<String> lore = new ArrayList<String>();
                // Check if lore_key is a list or single string
                List<String> loreLines = messages.getList(player, item.getLoreKey());
                if (loreLines != null && !loreLines.isEmpty()) {
                    lore.addAll(messages.colorize(loreLines));
                } else {
                    String singleLore = messages.format(player, item.getLoreKey());
                    if (!singleLore.equals(item.getLoreKey())) {
                        lore.add(singleLore);
                    }
                }
                if (item.hasAction()) {
                    lore.add(ChatColor.DARK_GRAY + "ACTION:" + item.getAction());
                }
                if (item.hasCommand()) {
                    lore.add(ChatColor.DARK_GRAY + "COMMAND:" + item.getCommand());
                }
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }
            inv.setItem(item.getSlot(), stack);
        }
        
        // Close button
        inv.setItem(layout.getCloseSlot(), createCloseItem(player));
        
        player.openInventory(inv);
    }

    private boolean handleLanguage(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(sender, "common.only_player"));
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            sendLanguageList(player);
            return true;
        }
        String desired = args[0];
        if ("default".equalsIgnoreCase(desired) || "reset".equalsIgnoreCase(desired)) {
            messages.setPlayerLanguage(player.getName(), messages.getDefaultLanguage());
            Map<String, String> map = new HashMap<String, String>();
            map.put("language", messages.getDefaultLanguage());
            player.sendMessage(messages.format(player, "i18n.updated", map));
            return true;
        }
        if (!messages.setPlayerLanguage(player.getName(), desired)) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("language", desired.toLowerCase());
            player.sendMessage(messages.format(player, "i18n.unsupported", map));
            sendLanguageList(player);
            return true;
        }
        Map<String, String> map = new HashMap<String, String>();
        map.put("language", desired.toLowerCase());
        player.sendMessage(messages.format(player, "i18n.updated", map));
        return true;
    }

    private void sendLanguageList(Player player) {
        Map<String, String> current = new HashMap<String, String>();
        String stored = messages.getPlayerLanguage(player.getName());
        String effective = stored == null || stored.trim().isEmpty() ? messages.getDefaultLanguage() : stored;
        current.put("language", effective);
        player.sendMessage(messages.format(player, "i18n.current", current));

        Map<String, String> def = new HashMap<String, String>();
        def.put("language", messages.getDefaultLanguage());
        player.sendMessage(messages.format(player, "i18n.default", def));

        Map<String, String> available = new HashMap<String, String>();
        available.put("languages", String.join(", ", messages.getSupportedLanguages()));
        player.sendMessage(messages.format(player, "i18n.available", available));
        player.sendMessage(messages.format(player, "i18n.usage"));
    }

    private String extractIdFromMeta(ItemMeta meta) {
        if (meta == null || meta.getLore() == null) {
            return null;
        }
        List<String> lore = meta.getLore();
        for (String line : lore) {
            if (line == null) {
                continue;
            }
            String cleaned = ChatColor.stripColor(line);
            if (cleaned.contains("ID:")) {
                return cleaned.substring(cleaned.indexOf("ID:") + 3).trim();
            }
        }
        return null;
    }

    private String extractActionFromMeta(ItemMeta meta) {
        if (meta == null || meta.getLore() == null) {
            return null;
        }
        List<String> lore = meta.getLore();
        for (String line : lore) {
            if (line == null) {
                continue;
            }
            String cleaned = ChatColor.stripColor(line);
            if (cleaned.startsWith("ACTION:")) {
                return cleaned.substring(7).trim();
            }
        }
        return null;
    }

    private String extractCommandFromMeta(ItemMeta meta) {
        if (meta == null || meta.getLore() == null) {
            return null;
        }
        List<String> lore = meta.getLore();
        for (String line : lore) {
            if (line == null) {
                continue;
            }
            String cleaned = ChatColor.stripColor(line);
            if (cleaned.startsWith("COMMAND:")) {
                return cleaned.substring(8).trim();
            }
        }
        return null;
    }

    private String extractLangFromMeta(ItemMeta meta) {
        if (meta == null || meta.getLore() == null) {
            return null;
        }
        List<String> lore = meta.getLore();
        for (String line : lore) {
            if (line == null) {
                continue;
            }
            String cleaned = ChatColor.stripColor(line);
            if (cleaned.startsWith("LANG:")) {
                return cleaned.substring(5).trim();
            }
        }
        return null;
    }

    /**
     * Handle a menu item click by processing its action and/or command.
     * Returns true if the action was handled.
     */
    private boolean handleMenuAction(Player player, String action, String command) {
        if (action != null && !action.isEmpty()) {
            switch (action) {
                case "OPEN_WISH":
                    openWishMenu(player);
                    return true;
                case "OPEN_EVENT":
                    openEventMenu(player);
                    return true;
                case "OPEN_EXP":
                    expManager.openMenu(player);
                    return true;
                case "OPEN_BUY":
                    buyManager.openMenu(player);
                    return true;
                case "OPEN_MAIL":
                    mailManager.openMenu(player);
                    return true;
                case "OPEN_SGAME":
                    strategyGameManager.continueGame(player);
                    return true;
                case "OPEN_RTP":
                    randomTeleportGameManager.openMenu(player);
                    return true;
                case "OPEN_ARENA":
                    survivalArenaManager.openMenu(player);
                    return true;
                case "OPEN_FISHING":
                    fishingContestManager.openMenu(player);
                    return true;
                case "OPEN_GAMES":
                    openGamesMenu(player);
                    return true;
                case "OPEN_HELP":
                    openHelpMenu(player);
                    return true;
                case "OPEN_NAV":
                    openNavigationMenu(player);
                    return true;
                case "OPEN_LANGUAGE":
                    openLanguageMenu(player);
                    return true;
                case "OPEN_ANNOUNCEMENT":
                    announcementManager.openMenu(player);
                    return true;
                default:
                    break;
            }
        }
        // Execute command if present
        if (command != null && !command.isEmpty()) {
            String cmd = command.replace("{player}", player.getName());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            player.performCommand(cmd);
            return true;
        }
        return false;
    }

    private void openLanguageMenu(Player player) {
        Set<String> languages = messages.getSupportedLanguages();
        int size = Math.max(9, ((languages.size() + 8) / 9) * 9); // Round up to nearest 9
        if (size > 54) size = 54;
        Inventory inv = Bukkit.createInventory(new LanguageMenuHolder(), size, messages.format(player, "help.language.menu_title"));
        
        String currentLang = messages.getPlayerLanguage(player.getName());
        if (currentLang == null || currentLang.isEmpty()) {
            currentLang = messages.getDefaultLanguage();
        }
        String defaultLang = messages.getDefaultLanguage();
        
        int slot = 0;
        for (String lang : languages) {
            if (slot >= size - 1) break;
            
            org.bukkit.Material material = lang.equals(currentLang) ? org.bukkit.Material.LIME_DYE : org.bukkit.Material.GRAY_DYE;
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String displayName = lang.toUpperCase();
                if (lang.equals(currentLang)) {
                    displayName = ChatColor.GREEN + "✓ " + displayName + " " + messages.format(player, "help.language.current_marker");
                } else {
                    displayName = ChatColor.GRAY + displayName;
                }
                if (lang.equals(defaultLang)) {
                    displayName += " " + messages.format(player, "help.language.default_marker");
                }
                meta.setDisplayName(displayName);
                List<String> lore = new ArrayList<String>();
                lore.add(messages.format(player, "help.language.click_to_select"));
                lore.add(ChatColor.DARK_GRAY + "LANG:" + lang);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }
        
        // Back button at last slot
        ItemStack backItem = new ItemStack(org.bukkit.Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(messages.format(player, "help.back_button"));
            List<String> backLore = new ArrayList<String>();
            backLore.add(messages.format(player, "help.back_to_help"));
            backLore.add(ChatColor.DARK_GRAY + "ACTION:OPEN_HELP");
            backMeta.setLore(backLore);
            backItem.setItemMeta(backMeta);
        }
        inv.setItem(size - 1, backItem);
        
        player.openInventory(inv);
    }

    private static class LanguageMenuHolder implements InventoryHolder {
        public Inventory getInventory() { return null; }
    }
    
    /**
     * Get the display name for a wish pool using i18n if available.
     * Looks up "menu.wish.pool.<poolId>.name", falls back to config display name.
     */
    private String getPoolDisplayName(Player player, String poolId, String configDefault) {
        String i18nKey = "menu.wish.pool." + poolId + ".name";
        String translated = messages.getRaw(player, i18nKey);
        // If the key returns the same as input key, use config display
        if (translated == null || translated.equals(i18nKey)) {
            return configDefault;
        }
        return translated;
    }
    
    /**
     * Get the description for a wish pool using i18n if available.
     * Looks up "menu.wish.pool.<poolId>.description", falls back to config description.
     */
    private List<String> getPoolDescription(Player player, String poolId, List<String> configDefault) {
        String i18nKey = "menu.wish.pool." + poolId + ".description";
        List<String> translated = messages.getList(player, i18nKey);
        // If the key returns null or empty, use config description
        if (translated == null || translated.isEmpty()) {
            return configDefault;
        }
        return translated;
    }

    private void openWishMenu(Player player) {
        MenuLayout.WishLayout layout = menuLayout.getWishLayout();
        Inventory inv = Bukkit.createInventory(new WishMenuHolder(), layout.getSize(), messages.format(player, "menu.wish.title"));
        int index = 0;
        Instant now = Instant.now();
        for (WishPool pool : wishManager.getPools().values()) {
            // Only show active pools
            if (!pool.isActive(now)) {
                continue;
            }
            if (index >= layout.getItemSlots().size()) {
                break;
            }
            
            // Use display configuration
            PoolDisplay display = pool.getDisplay();
            org.bukkit.Material material = org.bukkit.Material.NETHER_STAR;
            try {
                material = org.bukkit.Material.valueOf(display.getMaterial().toUpperCase());
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid material for pool " + pool.getId() + ": " + display.getMaterial() + ", using NETHER_STAR");
            }
            
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                // Use i18n for pool name, fallback to config display name
                String poolName = getPoolDisplayName(player, pool.getId(), display.getName());
                meta.setDisplayName(messages.colorize(poolName));
                
                List<String> lore = new ArrayList<String>();
                lore.add(ChatColor.GRAY + "ID: " + pool.getId());
                
                // Use i18n for pool description, fallback to config description
                List<String> poolDesc = getPoolDescription(player, pool.getId(), display.getDescription());
                for (String line : poolDesc) {
                    lore.add(messages.colorize(line));
                }
                
                lore.add("");
                lore.add(messages.format(player, "menu.wish.pool.click_to_view"));
                meta.setLore(lore);
                
                // Set custom model data if configured
                // Note: Custom model data requires a client-side resource pack with 
                // matching JSON models to display custom textures
                if (display.getCustomModelData() > 0) {
                    meta.setCustomModelData(display.getCustomModelData());
                    if (getLogger().isLoggable(java.util.logging.Level.FINE)) {
                        getLogger().fine("Applied custom model data " + display.getCustomModelData() + " to pool " + pool.getId());
                    }
                }
                
                stack.setItemMeta(meta);
            }
            int slot = layout.getItemSlots().get(index++);
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, stack);
            }
        }
        // Add navigation button (back to main menu)
        if (layout.getCloseSlot() > 0 && layout.getCloseSlot() - 1 >= 0 && layout.getCloseSlot() - 1 < inv.getSize()) {
            inv.setItem(layout.getCloseSlot() - 1, createHomeButton(player));
        }
        if (layout.getCloseSlot() >= 0 && layout.getCloseSlot() < inv.getSize()) {
            inv.setItem(layout.getCloseSlot(), createCloseItem(player));
        }
        player.openInventory(inv);
    }

    private void openWishPoolDetailMenu(Player player, String poolId) {
        WishPool pool = wishManager.getPools().get(poolId);
        if (pool == null) {
            player.sendMessage(messages.format(player, "wish.pool_missing"));
            return;
        }

        PoolDisplay display = pool.getDisplay();
        
        // Use i18n for pool name, fallback to config display name
        String poolName = getPoolDisplayName(player, poolId, display.getName());
        
        // Use size 54 for the detail menu to fit rewards preview and buttons
        Map<String, String> titlePlaceholders = new HashMap<String, String>();
        titlePlaceholders.put("pool", messages.colorize(poolName));
        titlePlaceholders.put("pool_id", poolId);
        Inventory inv = Bukkit.createInventory(new WishPoolDetailMenuHolder(poolId), 54, 
            messages.format(player, "menu.wish.pool_detail.title", titlePlaceholders));

        WishStatus status = wishManager.queryStatus(player.getName(), poolId);

        // Slot 0: Player info with pool status - initially with a placeholder, then async update with player head
        ItemStack infoItem = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta skullMeta = (org.bukkit.inventory.meta.SkullMeta) infoItem.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setDisplayName(messages.colorize(poolName));
            List<String> infoLore = new ArrayList<String>();
            Map<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("count", String.valueOf(status.getCount()));
            placeholders.put("max_count", String.valueOf(pool.getMaxCount()));
            placeholders.put("tickets", String.valueOf(status.getTicketCount()));
            infoLore.add(messages.format(player, "menu.wish.pool_detail.fate_mark", placeholders));
            infoLore.add(messages.format(player, "menu.wish.pool_detail.tickets", placeholders));
            // Use i18n for pool description, fallback to config description
            List<String> poolDesc = getPoolDescription(player, poolId, display.getDescription());
            for (String line : poolDesc) {
                infoLore.add(messages.colorize(line));
            }
            skullMeta.setLore(infoLore);
            infoItem.setItemMeta(skullMeta);
        }
        inv.setItem(0, infoItem);
        
        // Async fetch player head to avoid blocking main thread
        final Inventory finalInv = inv;
        final ItemStack finalInfoItem = infoItem.clone();
        final InventoryHolder inventoryHolder = inv.getHolder();
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                // Get the skull with player texture in async thread
                org.bukkit.inventory.meta.SkullMeta asyncMeta = (org.bukkit.inventory.meta.SkullMeta) finalInfoItem.getItemMeta();
                if (asyncMeta != null) {
                    asyncMeta.setOwningPlayer(player);
                    finalInfoItem.setItemMeta(asyncMeta);
                }
                // Update inventory on main thread
                Bukkit.getScheduler().runTask(NekoSuitePlugin.this, new Runnable() {
                    @Override
                    public void run() {
                        // Only update if player still has the same inventory open (check by holder)
                        if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == inventoryHolder) {
                            finalInv.setItem(0, finalInfoItem);
                        }
                    }
                });
            }
        });

        // Slots 9-26: Reward previews
        WeightedList items = pool.getItems();
        if (items != null) {
            List<RewardEntry> entries = items.getEntries();
            double totalWeight = items.getTotalWeight();
            int rewardSlot = 9;
            for (int i = 0; i < entries.size() && rewardSlot <= 26; i++) {
                RewardEntry entry = entries.get(i);
                if (entry.getWeight() <= 0) {
                    continue;
                }
                // Use vanilla Minecraft material if available, otherwise use PAPER with custom_model_data
                org.bukkit.Material displayMaterial = entry.getDisplayMaterial();
                boolean isVanillaItem = displayMaterial != null;
                if (!isVanillaItem) {
                    displayMaterial = org.bukkit.Material.PAPER;
                }
                ItemStack rewardItem = new ItemStack(displayMaterial);
                ItemMeta rewardMeta = rewardItem.getItemMeta();
                if (rewardMeta != null) {
                    double percent = (entry.getWeight() / totalWeight) * 100;
                    String percentStr = String.format("%.2f%%", percent);
                    // Use translated item name instead of raw ID
                    String displayName = messages.getItemName(player, entry.getItemId());
                    rewardMeta.setDisplayName(ChatColor.GOLD + displayName + ChatColor.GRAY + " (" + percentStr + ")");
                    List<String> rewardLore = new ArrayList<String>();
                    Map<String, String> rewardPlaceholders = new HashMap<String, String>();
                    rewardPlaceholders.put("name", displayName);
                    rewardPlaceholders.put("percent", percentStr);
                    rewardLore.add(messages.format(player, "menu.wish.pool_detail.reward_lore", rewardPlaceholders));
                    rewardMeta.setLore(rewardLore);
                    // Only apply custom model data for non-vanilla items without display_material override
                    if (!isVanillaItem && entry.getDisplayModel() > 0) {
                        rewardMeta.setCustomModelData(entry.getDisplayModel());
                    }
                    // Add enchant glint if configured
                    if (entry.isEnchanted()) {
                        rewardMeta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                        rewardMeta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    }
                    rewardItem.setItemMeta(rewardMeta);
                    // Show a representative stack size using the primary action amount (clamped to 64)
                    RewardAction primary = entry.getPrimaryAction();
                    if (primary != null) {
                        int stackAmount = Math.min(64, Math.max(1, primary.getMaxAmount()));
                        rewardItem.setAmount(stackAmount);
                    }
                }
                inv.setItem(rewardSlot++, rewardItem);
            }
        }

        // Wish buttons - check if pool only has cost for 1 (no 5x option)
        Map<Integer, Integer> costs = pool.getCosts();
        int cost1 = costs.getOrDefault(1, 0);
        boolean hasFiveOption = costs.containsKey(5);

        // Get button configs
        ButtonConfig btn1x = wishManager.getWish1xButton();
        ButtonConfig btn5x = wishManager.getWish5xButton();

        // Wish 1x button - centered at slot 40 if no 5x option, otherwise at slot 39
        int wish1Slot = hasFiveOption ? 39 : 40;
        org.bukkit.Material wish1Material = org.bukkit.Material.PAPER;
        try {
            wish1Material = org.bukkit.Material.valueOf(btn1x.getMaterial().toUpperCase());
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid material for wish 1x button: " + btn1x.getMaterial());
        }
        ItemStack wish1Item = new ItemStack(wish1Material);
        ItemMeta wish1Meta = wish1Item.getItemMeta();
        if (wish1Meta != null) {
            wish1Meta.setDisplayName(messages.format(player, "menu.wish.pool_detail.wish_once"));
            List<String> wish1Lore = new ArrayList<String>();
            Map<String, String> wish1Placeholders = new HashMap<String, String>();
            wish1Placeholders.put("count", String.valueOf(status.getCount()));
            wish1Placeholders.put("max_count", String.valueOf(pool.getMaxCount()));
            wish1Placeholders.put("tickets", String.valueOf(status.getTicketCount()));
            wish1Placeholders.put("cost", String.valueOf(cost1));
            wish1Lore.add(messages.format(player, "menu.wish.pool_detail.fate_mark", wish1Placeholders));
            wish1Lore.add(messages.format(player, "menu.wish.pool_detail.tickets", wish1Placeholders));
            wish1Lore.add(messages.format(player, "menu.wish.pool_detail.cost", wish1Placeholders));
            wish1Lore.add("");
            wish1Lore.add(ChatColor.GRAY + "ACTION:WISH:1");
            wish1Meta.setLore(wish1Lore);
            if (btn1x.getCustomModelData() > 0) {
                wish1Meta.setCustomModelData(btn1x.getCustomModelData());
            }
            wish1Item.setItemMeta(wish1Meta);
        }
        inv.setItem(wish1Slot, wish1Item);

        // Wish 5x button at slot 41 - only show if pool has 5x cost option
        if (hasFiveOption) {
            int cost5 = costs.get(5);
            org.bukkit.Material wish5Material = org.bukkit.Material.PAPER;
            try {
                wish5Material = org.bukkit.Material.valueOf(btn5x.getMaterial().toUpperCase());
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid material for wish 5x button: " + btn5x.getMaterial());
            }
            ItemStack wish5Item = new ItemStack(wish5Material);
            ItemMeta wish5Meta = wish5Item.getItemMeta();
            if (wish5Meta != null) {
                wish5Meta.setDisplayName(messages.format(player, "menu.wish.pool_detail.wish_five"));
                List<String> wish5Lore = new ArrayList<String>();
                Map<String, String> wish5Placeholders = new HashMap<String, String>();
                wish5Placeholders.put("count", String.valueOf(status.getCount()));
                wish5Placeholders.put("max_count", String.valueOf(pool.getMaxCount()));
                wish5Placeholders.put("tickets", String.valueOf(status.getTicketCount()));
                wish5Placeholders.put("cost", String.valueOf(cost5));
                wish5Lore.add(messages.format(player, "menu.wish.pool_detail.fate_mark", wish5Placeholders));
                wish5Lore.add(messages.format(player, "menu.wish.pool_detail.tickets", wish5Placeholders));
                wish5Lore.add(messages.format(player, "menu.wish.pool_detail.cost", wish5Placeholders));
                wish5Lore.add("");
                wish5Lore.add(ChatColor.GRAY + "ACTION:WISH:5");
                wish5Meta.setLore(wish5Lore);
                if (btn5x.getCustomModelData() > 0) {
                    wish5Meta.setCustomModelData(btn5x.getCustomModelData());
                }
                wish5Item.setItemMeta(wish5Meta);
            }
            inv.setItem(41, wish5Item);
        }

        // Bottom buttons
        // Back button at slot 45
        ItemStack backItem = new ItemStack(org.bukkit.Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(messages.format(player, "menu.wish.pool_detail.back"));
            List<String> backLore = new ArrayList<String>();
            backLore.add(messages.format(player, "menu.wish.pool_detail.back_lore"));
            backLore.add(ChatColor.GRAY + "ACTION:BACK");
            backMeta.setLore(backLore);
            backItem.setItemMeta(backMeta);
        }
        inv.setItem(45, backItem);

        // Close button at slot 53
        inv.setItem(53, createCloseItem(player));

        player.openInventory(inv);
    }

    private void openEventMenu(Player player) {
        MenuLayout.EventLayout layout = menuLayout.getEventLayout();
        Inventory inv = Bukkit.createInventory(new EventMenuHolder(), layout.getSize(), messages.format(player, "menu.event.title"));
        int slotIndex = 0;
        List<EventAvailability> events = eventManager.listAvailableEvents(player);
        for (EventAvailability availability : events) {
            if (slotIndex >= layout.getItemSlots().size()) {
                break;
            }
            org.bukkit.Material material = availability.isCanParticipate() 
                    ? org.bukkit.Material.LIME_DYE 
                    : org.bukkit.Material.GRAY_DYE;
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (availability.isCanParticipate()) {
                    meta.setDisplayName(ChatColor.GREEN + availability.getDisplayName());
                } else {
                    meta.setDisplayName(ChatColor.GRAY + availability.getDisplayName());
                }
                List<String> lore = new ArrayList<String>();
                lore.add(ChatColor.GRAY + "ID: " + availability.getId());
                
                if (availability.isCanParticipate()) {
                    lore.add(messages.format(player, "event.status.available"));
                    lore.add(messages.format(player, "event.status.click_participate"));
                } else {
                    lore.add(messages.format(player, "event.status.unavailable"));
                    if (availability.getRefreshTimeMillis() > 0) {
                        Map<String, String> placeholders = new HashMap<String, String>();
                        placeholders.put("time", availability.getFormattedRefreshTime());
                        lore.add(messages.format(player, "event.status.refresh_in", placeholders));
                    }
                }
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }
            int slot = layout.getItemSlots().get(slotIndex++);
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, stack);
            }
        }
        // Add navigation button (back to main menu)
        if (layout.getCloseSlot() > 0 && layout.getCloseSlot() - 1 >= 0 && layout.getCloseSlot() - 1 < inv.getSize()) {
            inv.setItem(layout.getCloseSlot() - 1, createHomeButton(player));
        }
        if (layout.getCloseSlot() >= 0 && layout.getCloseSlot() < inv.getSize()) {
            inv.setItem(layout.getCloseSlot(), createCloseItem(player));
        }
        player.openInventory(inv);
    }

    private ItemStack createCloseItem(Player player) {
        ItemStack item = new ItemStack(org.bukkit.Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.format(player, "menu.close"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHomeButton(Player player) {
        ItemStack item = new ItemStack(org.bukkit.Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.format(player, "help.back_button"));
            List<String> lore = new ArrayList<String>();
            lore.add(messages.format(player, "help.back_lore"));
            lore.add(ChatColor.DARK_GRAY + "ACTION:OPEN_NAV");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (joinQuitManager != null) {
            joinQuitManager.onPlayerQuit(player);
        }
        if (randomTeleportGameManager != null) {
            randomTeleportGameManager.onPlayerQuit(player);
        }
        if (survivalArenaManager != null) {
            survivalArenaManager.onPlayerQuit(player);
        }
    }

    @EventHandler
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (survivalArenaManager == null) {
            return;
        }
        org.bukkit.entity.Entity entity = event.getEntity();
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            survivalArenaManager.onMobKill(killer, entity);
        }
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (survivalArenaManager == null) {
            return;
        }
        Player player = event.getEntity();
        survivalArenaManager.onPlayerDeath(player);
    }

    @EventHandler
    public void onPlayerFish(org.bukkit.event.player.PlayerFishEvent event) {
        if (fishingContestManager == null || !fishingContestManager.isContestActive()) {
            return;
        }
        if (event.getState() == org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH) {
            org.bukkit.entity.Entity caught = event.getCaught();
            if (caught instanceof org.bukkit.entity.Item) {
                org.bukkit.entity.Item itemEntity = (org.bukkit.entity.Item) caught;
                ItemStack caughtItem = itemEntity.getItemStack();
                fishingContestManager.onFishCatch(event.getPlayer(), caughtItem);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (randomTeleportGameManager == null) {
            return;
        }
        // Only check if the player actually moved to a different block
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        randomTeleportGameManager.checkPlayerLocation(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() == null) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (holder instanceof WishMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) {
                return;
            }
            if (clicked.getType() == org.bukkit.Material.BARRIER) {
                player.closeInventory();
                return;
            }
            // Check for navigation action (home button)
            ItemMeta meta = clicked.getItemMeta();
            String action = extractActionFromMeta(meta);
            if (action != null && action.equals("OPEN_NAV")) {
                openNavigationMenu(player);
                return;
            }
            String id = extractIdFromMeta(meta);
            if (id != null) {
                // Open the pool detail menu instead of immediately performing a wish
                openWishPoolDetailMenu(player, id);
                return;
            }
            return;
        }
        if (holder instanceof WishPoolDetailMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) {
                return;
            }
            if (clicked.getType() == org.bukkit.Material.BARRIER) {
                player.closeInventory();
                return;
            }
            if (clicked.getType() == org.bukkit.Material.ARROW) {
                // Back button - return to main wish menu
                openWishMenu(player);
                return;
            }
            // Check for wish action in lore
            ItemMeta meta = clicked.getItemMeta();
            String action = extractActionFromMeta(meta);
            if (action != null && action.startsWith("WISH:")) {
                String countStr = action.substring(5);
                int wishCount = 1;
                try {
                    wishCount = Integer.parseInt(countStr);
                } catch (NumberFormatException e) {
                    getLogger().warning("Invalid wish count in action: " + action + ", defaulting to 1");
                }
                WishPoolDetailMenuHolder detailHolder = (WishPoolDetailMenuHolder) holder;
                String poolId = detailHolder.getPoolId();
                try {
                    List<String> rewards = wishManager.performWish(player, poolId, wishCount);
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("rewards", String.join(", ", rewards));
                    player.sendMessage(messages.format(player, "wish.success", map));
                    // Refresh the detail menu to show updated counts
                    openWishPoolDetailMenu(player, poolId);
                } catch (WishException e) {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("reason", e.getMessage());
                    player.sendMessage(messages.format(player, "wish.failure", map));
                }
                return;
            }
            if (action != null && action.equals("BACK")) {
                openWishMenu(player);
                return;
            }
            return;
        }
        if (holder instanceof EventMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) {
                return;
            }
            if (clicked.getType() == org.bukkit.Material.BARRIER) {
                player.closeInventory();
                return;
            }
            ItemMeta meta = clicked.getItemMeta();
            // Check for navigation action (home button)
            String action = extractActionFromMeta(meta);
            if (action != null && action.equals("OPEN_NAV")) {
                openNavigationMenu(player);
                return;
            }
            String id = extractIdFromMeta(meta);
            if (id != null) {
                try {
                    List<String> rewards = eventManager.participate(player, id);
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("rewards", String.join(", ", rewards));
                    player.sendMessage(messages.format(player, "event.reward", map));
                    openEventMenu(player); // Refresh menu state after participation
                } catch (EventException e) {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("reason", e.getMessage());
                    player.sendMessage(messages.format(player, "event.failure", map));
                    openEventMenu(player); // Refresh to show updated availability/cooldown
                }
                return;
            }
            return;
        }
        if (holder instanceof ExpManager.ExpMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) {
                return;
            }
            // Check for navigation action first
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line != null && ChatColor.stripColor(line).startsWith("ACTION:OPEN_NAV")) {
                        openNavigationMenu(player);
                        return;
                    }
                }
            }
            expManager.handleMenuClick(player, clicked);
        }
        if (holder instanceof BuyManager.BuyMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) {
                return;
            }
            // Check for navigation action first
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line != null && ChatColor.stripColor(line).startsWith("ACTION:OPEN_NAV")) {
                        openNavigationMenu(player);
                        return;
                    }
                }
            }
            buyManager.handleMenuClick(player, clicked);
        }
        if (holder instanceof MailManager.MailMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) {
                return;
            }
            // Check for navigation action first
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line != null && ChatColor.stripColor(line).startsWith("ACTION:OPEN_NAV")) {
                        openNavigationMenu(player);
                        return;
                    }
                }
            }
            MailManager.MailMenuHolder mailHolder = (MailManager.MailMenuHolder) holder;
            mailManager.handleMenuClick(player, clicked, event.isShiftClick(), mailHolder.getCurrentPage());
        }
        if (holder instanceof StrategyGameManager.StrategyGameMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) {
                return;
            }
            // Check for navigation action first
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line != null && ChatColor.stripColor(line).startsWith("ACTION:OPEN_NAV")) {
                        openNavigationMenu(player);
                        return;
                    }
                }
            }
            StrategyGameManager.StrategyGameMenuHolder sgHolder = (StrategyGameManager.StrategyGameMenuHolder) holder;
            strategyGameManager.handleMenuClick(player, clicked, sgHolder);
        }
        if (holder instanceof NavigationMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) {
                return;
            }
            if (clicked.getType() == org.bukkit.Material.BARRIER) {
                player.closeInventory();
                return;
            }
            ItemMeta meta = clicked.getItemMeta();
            String action = extractActionFromMeta(meta);
            String command = extractCommandFromMeta(meta);
            handleMenuAction(player, action, command);
            return;
        }
        if (holder instanceof HelpMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) {
                return;
            }
            if (clicked.getType() == org.bukkit.Material.BARRIER) {
                player.closeInventory();
                return;
            }
            ItemMeta meta = clicked.getItemMeta();
            String action = extractActionFromMeta(meta);
            String command = extractCommandFromMeta(meta);
            handleMenuAction(player, action, command);
            return;
        }
        if (holder instanceof GamesMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) {
                return;
            }
            if (clicked.getType() == org.bukkit.Material.BARRIER) {
                player.closeInventory();
                return;
            }
            ItemMeta meta = clicked.getItemMeta();
            String action = extractActionFromMeta(meta);
            String command = extractCommandFromMeta(meta);
            handleMenuAction(player, action, command);
            return;
        }
        if (holder instanceof LanguageMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) {
                return;
            }
            ItemMeta meta = clicked.getItemMeta();
            // Check for back action
            String action = extractActionFromMeta(meta);
            if (action != null) {
                handleMenuAction(player, action, null);
                return;
            }
            // Check for language selection
            String lang = extractLangFromMeta(meta);
            if (lang != null) {
                if (messages.setPlayerLanguage(player.getName(), lang)) {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("language", lang);
                    player.sendMessage(messages.format(player, "i18n.updated", map));
                    // Refresh the language menu to show updated selection
                    openLanguageMenu(player);
                }
            }
            return;
        }
        if (holder instanceof AnnouncementManager.AnnouncementMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            AnnouncementManager.AnnouncementMenuHolder annHolder = (AnnouncementManager.AnnouncementMenuHolder) holder;
            announcementManager.handleMenuClick(player, clicked, annHolder.getPage());
            return;
        }
        if (holder instanceof RandomTeleportGameManager.RTPGameMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            RandomTeleportGameManager.RTPGameMenuHolder rtpHolder = (RandomTeleportGameManager.RTPGameMenuHolder) holder;
            randomTeleportGameManager.handleMenuClick(player, clicked, rtpHolder);
            return;
        }
        if (holder instanceof SurvivalArenaManager.ArenaMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            SurvivalArenaManager.ArenaMenuHolder arenaHolder = (SurvivalArenaManager.ArenaMenuHolder) holder;
            survivalArenaManager.handleMenuClick(player, clicked, arenaHolder);
            return;
        }
        if (holder instanceof FishingContestManager.FishingMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            FishingContestManager.FishingMenuHolder fishingHolder = (FishingContestManager.FishingMenuHolder) holder;
            fishingContestManager.handleMenuClick(player, clicked, fishingHolder);
            return;
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Use JoinQuitManager for configurable join actions
        if (joinQuitManager != null) {
            joinQuitManager.onPlayerJoin(player);
        }
        
        if (mailManager != null) {
            // Delay notification slightly to allow player to fully join
            final Player playerFinal = player;
            Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                public void run() {
                    if (playerFinal.isOnline()) {
                        mailManager.notifyUnreadMail(playerFinal);
                    }
                }
            }, 40L); // 2 seconds delay
        }
    }

    private static class WishMenuHolder implements InventoryHolder {
        public Inventory getInventory() {
            return null;
        }
    }

    private static class WishPoolDetailMenuHolder implements InventoryHolder {
        private final String poolId;

        WishPoolDetailMenuHolder(String poolId) {
            this.poolId = poolId;
        }

        String getPoolId() {
            return poolId;
        }

        public Inventory getInventory() {
            return null;
        }
    }

    private static class EventMenuHolder implements InventoryHolder {
        public Inventory getInventory() {
            return null;
        }
    }

    private static class WishManager {
        private final JavaPlugin plugin;
        private final Messages messages;
        private final File configFile;
        private final File storageDir;
        private final Random random = new Random();
        private final Map<String, WishPool> pools = new HashMap<String, WishPool>();
        private final List<TicketRule> tickets = new ArrayList<TicketRule>();
        private final Economy economy;
        private ButtonConfig wish1xButton;
        private ButtonConfig wish5xButton;

        WishManager(JavaPlugin plugin, Messages messages, File configFile, Economy economy) {
            this.plugin = plugin;
            this.messages = messages;
            this.configFile = configFile;
            this.economy = economy;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String dataDir = config.getString("storage.data_dir", "userdata");
            storageDir = new File(plugin.getDataFolder(), dataDir);
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                plugin.getLogger().warning("無法創建數據目錄: " + storageDir.getAbsolutePath());
            }
            loadConfig(config);
        }

        private void loadConfig(YamlConfiguration config) {
            pools.clear();
            
            // Load menu button configs
            ConfigurationSection buttonsSection = config.getConfigurationSection("menu_buttons");
            if (buttonsSection != null) {
                wish1xButton = ButtonConfig.fromSection(buttonsSection.getConfigurationSection("wish_1x"));
                wish5xButton = ButtonConfig.fromSection(buttonsSection.getConfigurationSection("wish_5x"));
            } else {
                wish1xButton = new ButtonConfig("PAPER", 1101);
                wish5xButton = new ButtonConfig("PAPER", 1100);
            }
            
            ConfigurationSection poolSection = config.getConfigurationSection("pools");
            if (poolSection != null) {
                Set<String> keys = poolSection.getKeys(false);
                for (String key : keys) {
                    ConfigurationSection section = poolSection.getConfigurationSection(key);
                    if (section == null) {
                        continue;
                    }
                    WishPool pool = WishPool.fromSection(key, section, plugin.getLogger());
                    pools.put(key, pool);
                }
            }

            tickets.clear();
            List<Map<?, ?>> ticketList = config.getMapList("tickets");
            for (Map<?, ?> raw : ticketList) {
                TicketRule rule = TicketRule.fromMap(raw);
                if (rule != null) {
                    tickets.add(rule);
                }
            }
        }

        List<String> performWish(Player player, String poolId, int count) throws WishException {
            if (count <= 0) {
                throw new WishException(messages.format(player, "wish.count_invalid"));
            }
            WishPool pool = pools.get(poolId);
            if (pool == null) {
                throw new WishException(messages.format(player, "wish.pool_missing"));
            }
            if (!pool.isActive(Instant.now())) {
                throw new WishException(messages.format(player, "wish.not_active"));
            }
            YamlConfiguration data = loadUserData(player.getName());
            long nowMillis = System.currentTimeMillis();
            if (!canWish(pool, data, nowMillis, count)) {
                throw new WishException(messages.format(player, "wish.limit_reached"));
            }
            String countsName = pool.getCountsName();
            int currentCount = data.getInt("wish.counts." + countsName, 0);

            TicketRule ticketRule = findTicket(poolId);
            int owned = 0;
            int needed = 0;
            int ticketUsed = 0;
            int paidCount = count;
            if (ticketRule != null) {
                owned = data.getInt("wish.tickets." + ticketRule.getId(), 0);
                needed = ticketRule.getDeductCount() * count;
                ticketUsed = Math.min(owned, needed);
                int missing = needed - ticketUsed;
                if (missing > 0) {
                    int per = Math.max(1, ticketRule.getDeductCount());
                    paidCount = (int) Math.ceil((double) missing / (double) per);
                } else {
                    paidCount = 0;
                }
            }

            int currencyCost = pool.calculateCost(paidCount);
            if (currencyCost > 0) {
                if (economy == null) {
                    throw new WishException(messages.format(player, "wish.economy_missing"));
                }
                double balance = economy.getBalance(player);
                if (balance < currencyCost) {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("needed", String.valueOf(currencyCost));
                    map.put("balance", String.valueOf((long) balance));
                    throw new WishException(messages.format(player, "wish.cost_insufficient", map));
                }
                EconomyResponse response = economy.withdrawPlayer(player, currencyCost);
                if (response == null || !response.transactionSuccess()) {
                    throw new WishException(messages.format(player, "wish.cost_failure"));
                }
            }

            if (ticketRule != null) {
                data.set("wish.tickets." + ticketRule.getId(), owned - ticketUsed);
            }

            List<String> rewards = new ArrayList<String>();
            int updatedCount = currentCount;
            for (int i = 0; i < count; i++) {
                updatedCount++;
                RewardResult rewardResult;
                if (pool.shouldGuarantee(updatedCount)) {
                    rewardResult = pool.pickGuarantee(random);
                    updatedCount = 0;
                } else {
                    rewardResult = pool.pickReward(random);
                }
                if (rewardResult != null) {
                    dispatchReward(player, rewardResult, plugin);
                    // Use translated item names for display
                    rewards.add(rewardResult.getTranslatedDisplay(messages, player));
                }
            }
            data.set("wish.counts." + countsName, updatedCount);
            markWish(pool, data, nowMillis, count);
            saveUserData(player.getName(), data);
            return rewards;
        }

        private boolean canWish(WishPool pool, YamlConfiguration data, long nowMillis, int requested) {
            WishLimit limit = pool.getLimit();
            if (limit == null) {
                return true;
            }
            String base = "wish.limits." + pool.getId();
            long windowStart = data.getLong(base + ".windowStart", 0L);
            int used = data.getInt(base + ".count", 0);
            if (windowStart == 0L || nowMillis - windowStart >= limit.getWindowMillis()) {
                return requested <= limit.getCount();
            }
            return used + requested <= limit.getCount();
        }

        private void markWish(WishPool pool, YamlConfiguration data, long nowMillis, int requested) {
            WishLimit limit = pool.getLimit();
            if (limit == null) {
                return;
            }
            String base = "wish.limits." + pool.getId();
            long windowStart = data.getLong(base + ".windowStart", 0L);
            int used = data.getInt(base + ".count", 0);
            if (windowStart == 0L || nowMillis - windowStart >= limit.getWindowMillis()) {
                windowStart = nowMillis;
                used = 0;
            }
            used += requested;
            data.set(base + ".windowStart", windowStart);
            data.set(base + ".count", used);
        }

        WishStatus queryStatus(String playerName, String poolId) {
            WishPool pool = pools.get(poolId);
            if (pool == null) {
                return WishStatus.invalid(poolId);
            }
            YamlConfiguration data = loadUserData(playerName);
            int count = data.getInt("wish.counts." + pool.getCountsName(), 0);
            TicketRule ticketRule = findTicket(poolId);
            int ticketsLeft = 0;
            if (ticketRule != null) {
                ticketsLeft = data.getInt("wish.tickets." + ticketRule.getId(), 0);
            }
            return new WishStatus(poolId, count, ticketsLeft, true);
        }

        private TicketRule findTicket(String poolId) {
            for (TicketRule rule : tickets) {
                if (rule.getApplicablePools().contains(poolId)) {
                    return rule;
                }
            }
            return null;
        }

        private YamlConfiguration loadUserData(String playerName) {
            File file = new File(storageDir, playerName + ".yml");
            if (!file.exists()) {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("無法創建用戶數據文件: " + e.getMessage());
                }
            }
            return YamlConfiguration.loadConfiguration(file);
        }

        private void saveUserData(String playerName, YamlConfiguration data) {
            File file = new File(storageDir, playerName + ".yml");
            try {
                data.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("保存用戶數據失敗: " + e.getMessage());
            }
        }

        Map<String, WishPool> getPools() {
            return pools;
        }

        ButtonConfig getWish1xButton() {
            return wish1xButton;
        }

        ButtonConfig getWish5xButton() {
            return wish5xButton;
        }
    }

    private static class ButtonConfig {
        private final String material;
        private final int customModelData;

        ButtonConfig(String material, int customModelData) {
            this.material = material;
            this.customModelData = customModelData;
        }

        static ButtonConfig fromSection(ConfigurationSection section) {
            if (section == null) {
                return new ButtonConfig("PAPER", 0);
            }
            String material = section.getString("material", "PAPER");
            int customModelData = section.getInt("custom_model_data", 0);
            return new ButtonConfig(material, customModelData);
        }

        String getMaterial() {
            return material;
        }

        int getCustomModelData() {
            return customModelData;
        }
    }

    private static void dispatchReward(Player player, RewardResult reward, JavaPlugin plugin) {
        if (player == null || reward == null) {
            return;
        }
        for (RewardAction action : reward.getActions()) {
            int amount = action.getAmount();
            String rawItemName = action.getName() == null ? "unknown_reward" : action.getName();
            String itemName = sanitizeItemName(rawItemName);
            List<String> commands = action.getCommands();
            if (commands != null && !commands.isEmpty()) {
                for (String command : commands) {
                    if (command == null || command.trim().isEmpty()) {
                        continue;
                    }
                    String cmd = command
                            .replace("{player}", player.getName())
                            .replace("%player%", player.getName())
                            .replace("$player", player.getName())
                            .replace("{amount}", String.valueOf(amount))
                            .replace("{item}", itemName);
                    if (cmd.startsWith("/")) {
                        cmd = cmd.substring(1);
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
                continue;
            }
            String giveCommand = "minecraft:give " + player.getName() + " " + itemName + " " + amount;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), giveCommand);
        }
    }

    private static String sanitizeItemName(String raw) {
        if (raw == null) {
            return "unknown_reward";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9:_.-]", "");
        if (cleaned.isEmpty()) {
            return "unknown_reward";
        }
        return cleaned;
    }

    private static class WishPool {
        private final String id;
        private final String countsName;
        private final int maxCount;
        private final Map<Integer, Integer> costs;
        private final boolean autoCost;
        private final WeightedList items;
        private final WeightedList guaranteeItems;
        private final TimeWindow window;
        private final WishLimit limit;
        private final PoolDisplay display;

        WishPool(String id, String countsName, int maxCount, Map<Integer, Integer> costs, boolean autoCost, WeightedList items, WeightedList guaranteeItems, TimeWindow window, WishLimit limit, PoolDisplay display) {
            this.id = id;
            this.countsName = countsName;
            this.maxCount = maxCount;
            this.costs = costs;
            this.autoCost = autoCost;
            this.items = items;
            this.guaranteeItems = guaranteeItems;
            this.window = window;
            this.limit = limit;
            this.display = display;
        }

        static WishPool fromSection(String id, ConfigurationSection section, java.util.logging.Logger logger) {
            String countsName = section.getString("counts_name", id);
            int maxCount = section.getInt("max_count", 0);
            Map<Integer, Integer> costs = parseCosts(section.getConfigurationSection("cost"));
            boolean autoCost = section.getBoolean("auto_cost", true);
            WeightedList itemList = WeightedList.fromSection(section.getConfigurationSection("items"));
            WeightedList guarantee = WeightedList.fromSection(section.getConfigurationSection("guarantee_items"));
            TimeWindow window = TimeWindow.fromSection(section.getConfigurationSection("duration"), logger);
            WishLimit limit = WishLimit.fromSection(section.getConfigurationSection("limit_modes"), logger);
            PoolDisplay display = PoolDisplay.fromSection(section.getConfigurationSection("display"), id);
            return new WishPool(id, countsName, maxCount, costs, autoCost, itemList, guarantee, window, limit, display);
        }

        int calculateCost(int count) {
            if (costs == null || costs.isEmpty() || count <= 0) {
                return 0;
            }
            if (costs.containsKey(count)) {
                return costs.get(count);
            }
            int single = costs.getOrDefault(1, 0);
            if (autoCost && single > 0) {
                return single * count;
            }
            return 0;
        }

        private static Map<Integer, Integer> parseCosts(ConfigurationSection section) {
            Map<Integer, Integer> out = new HashMap<Integer, Integer>();
            if (section == null) {
                return out;
            }
            for (String key : section.getKeys(false)) {
                try {
                    int draws = Integer.parseInt(key);
                    int value = section.getInt(key, 0);
                    if (draws > 0 && value > 0) {
                        out.put(draws, value);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return out;
        }

        boolean shouldGuarantee(int currentCount) {
            return maxCount > 0 && guaranteeItems != null && currentCount >= maxCount;
        }

        RewardResult pickReward(Random random) {
            if (items == null) {
                return RewardResult.empty();
            }
            return items.pick(random);
        }

        RewardResult pickGuarantee(Random random) {
            if (guaranteeItems == null) {
                return pickReward(random);
            }
            return guaranteeItems.pick(random);
        }

        String getCountsName() {
            return countsName;
        }

        String getId() {
            return id;
        }

        boolean isActive(Instant now) {
            return window == null || window.contains(now);
        }

        WishLimit getLimit() {
            return limit;
        }

        int getMaxCount() {
            return maxCount;
        }

        Map<Integer, Integer> getCosts() {
            return costs;
        }

        WeightedList getItems() {
            return items;
        }

        WeightedList getGuaranteeItems() {
            return guaranteeItems;
        }

        PoolDisplay getDisplay() {
            return display;
        }
    }

    private static class PoolDisplay {
        private final String material;
        private final int customModelData;
        private final String name;
        private final List<String> description;

        PoolDisplay(String material, int customModelData, String name, List<String> description) {
            this.material = material;
            this.customModelData = customModelData;
            this.name = name;
            this.description = description;
        }

        static PoolDisplay fromSection(ConfigurationSection section, String poolId) {
            if (section == null) {
                return new PoolDisplay("NETHER_STAR", 0, poolId, new ArrayList<String>());
            }
            String material = section.getString("material", "NETHER_STAR");
            int customModelData = section.getInt("custom_model_data", 0);
            String name = section.getString("name", poolId);
            List<String> description = section.getStringList("description");
            return new PoolDisplay(material, customModelData, name, description);
        }

        String getMaterial() {
            return material;
        }

        int getCustomModelData() {
            return customModelData;
        }

        String getName() {
            return name;
        }

        List<String> getDescription() {
            return description;
        }
    }

    private static class WishLimit {
        private final int count;
        private final long windowMillis;

        WishLimit(int count, long windowMillis) {
            this.count = count;
            this.windowMillis = windowMillis;
        }

        static WishLimit fromSection(ConfigurationSection section, java.util.logging.Logger logger) {
            if (section == null) {
                return null;
            }
            int count = section.getInt("count", 0);
            long windowMillis = EventLimit.parseDurationMillis(section.getString("time"), logger);
            if (count <= 0 || windowMillis <= 0) {
                return null;
            }
            return new WishLimit(count, windowMillis);
        }

        int getCount() {
            return count;
        }

        long getWindowMillis() {
            return windowMillis;
        }
    }

    private static class WeightedList {
        private final List<RewardEntry> entries;
        private final double totalWeight;

        WeightedList(List<RewardEntry> entries) {
            this.entries = entries;
            double total = 0.0;
            for (RewardEntry entry : entries) {
                if (entry.getWeight() > 0.0) {
                    total += entry.getWeight();
                }
            }
            this.totalWeight = total;
        }

        static WeightedList fromSection(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            List<RewardEntry> entries = new ArrayList<RewardEntry>();
            for (String key : section.getKeys(false)) {
                RewardEntry entry = RewardEntry.fromConfig(key, section.get(key), section.getConfigurationSection(key));
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return new WeightedList(entries);
        }

        RewardResult pick(Random random) {
            if (entries.isEmpty() || totalWeight <= 0.0) {
                return RewardResult.empty();
            }
            double target = random.nextDouble() * totalWeight;
            double cumulative = 0.0;
            RewardEntry fallback = entries.get(entries.size() - 1);
            for (RewardEntry entry : entries) {
                if (entry.getWeight() <= 0.0) {
                    continue;
                }
                cumulative += entry.getWeight();
                if (target <= cumulative) {
                    return entry.resolve(random);
                }
            }
            return fallback.resolve(random);
        }

        List<RewardEntry> getEntries() {
            return entries;
        }

        double getTotalWeight() {
            return totalWeight;
        }
    }

    private static class RewardEntry {
        private final double weight;
        private final WeightedList subList;
        private final List<RewardAction> actions;
        private final int displayModel;  // custom_model_data for display in menu
        private final String itemId;  // original item id from config (e.g., minecraft:iron_ingot)
        private final String displayMaterialOverride;  // explicit display_material override from config
        private final boolean enchanted;  // whether to show enchant glint

        RewardEntry(double weight, WeightedList subList, List<RewardAction> actions, int displayModel, String itemId, String displayMaterialOverride, boolean enchanted) {
            this.weight = weight;
            this.subList = subList;
            this.actions = actions;
            this.displayModel = displayModel;
            this.itemId = itemId;
            this.displayMaterialOverride = displayMaterialOverride;
            this.enchanted = enchanted;
        }

        static RewardEntry fromConfig(String key, Object rawValue, ConfigurationSection sectionValue) {
            double probability = 0.0;
            WeightedList sub = null;
            List<RewardAction> actions = new ArrayList<RewardAction>();
            int displayModel = 0;
            String displayMaterialOverride = null;
            boolean enchanted = false;

            if (sectionValue != null) {
                probability = sectionValue.getDouble("probability", 0.0);
                displayModel = sectionValue.getInt("display_model", 0);
                displayMaterialOverride = sectionValue.getString("display_material", null);
                enchanted = sectionValue.getBoolean("enchanted", false);
                sub = WeightedList.fromSection(sectionValue.getConfigurationSection("subList"));
                List<Map<?, ?>> items = sectionValue.getMapList("items");
                if (items != null && !items.isEmpty()) {
                    for (Map<?, ?> item : items) {
                        RewardAction action = RewardAction.fromMap(item, key);
                        if (action != null) {
                            actions.add(action);
                        }
                    }
                }
                if (actions.isEmpty()) {
                    int[] range = parseAmount(sectionValue.get("amount"));
                    List<String> cmds = parseCommands(sectionValue.get("commands"), sectionValue.getString("command"));
                    actions.add(new RewardAction(sectionValue.getString("name", key), range[0], range[1], cmds));
                }
            } else if (rawValue instanceof Number) {
                probability = ((Number) rawValue).doubleValue();
                actions.add(new RewardAction(key, 1, 1, null));
            } else if (rawValue instanceof String) {
                try {
                    probability = Double.parseDouble(rawValue.toString());
                    actions.add(new RewardAction(key, 1, 1, null));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (actions.isEmpty()) {
                actions.add(new RewardAction(key, 1, 1, null));
            }
            return new RewardEntry(probability, sub, actions, displayModel, key, displayMaterialOverride, enchanted);
        }

        private static List<String> parseCommands(Object commandsObj, String singleCommand) {
            List<String> list = new ArrayList<String>();
            if (commandsObj instanceof List) {
                for (Object o : (List<?>) commandsObj) {
                    if (o != null) {
                        list.add(o.toString());
                    }
                }
            } else if (commandsObj instanceof String) {
                String str = commandsObj.toString().trim();
                if (!str.isEmpty()) {
                    list.add(str);
                }
            } else if (singleCommand != null && singleCommand.trim().length() > 0) {
                list.add(singleCommand);
            }
            return list;
        }

        private static int[] parseAmount(Object amountObj) {
            int min = 1;
            int max = 1;
            if (amountObj instanceof Number) {
                int value = ((Number) amountObj).intValue();
                if (value > 0) {
                    min = value;
                    max = value;
                }
            } else if (amountObj instanceof String) {
                String raw = ((String) amountObj).trim();
                if (raw.contains("-")) {
                    String[] parts = raw.split("-");
                    if (parts.length >= 2) {
                        try {
                            min = Integer.parseInt(parts[0].trim());
                            max = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else {
                    try {
                        int parsed = Integer.parseInt(raw);
                        if (parsed > 0) {
                            min = parsed;
                            max = parsed;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (min <= 0) {
                min = 1;
            }
            if (max < min) {
                max = min;
            }
            return new int[]{min, max};
        }

        RewardResult resolve(Random random) {
            if (subList != null) {
                return subList.pick(random);
            }
            List<RewardAction> resolved = new ArrayList<RewardAction>();
            for (RewardAction action : actions) {
                resolved.add(action.resolve(random));
            }
            return new RewardResult(resolved);
        }

        boolean shouldGrant(Random random, double totalWeight) {
            if (weight <= 0.0 || totalWeight <= 0.0) {
                return false;
            }
            double chance = weight / totalWeight;
            return random.nextDouble() < chance;
        }

        double getWeight() {
            return weight;
        }

        List<RewardAction> getActions() {
            return actions;
        }

        RewardAction getPrimaryAction() {
            if (actions == null || actions.isEmpty()) {
                return null;
            }
            return actions.get(0);
        }

        String getDisplayName() {
            if (actions == null || actions.isEmpty()) {
                return RewardAction.DEFAULT_NAME;
            }
            return actions.get(0).getName();
        }

        int getDisplayModel() {
            return displayModel;
        }

        String getItemId() {
            return itemId;
        }

        boolean isEnchanted() {
            return enchanted;
        }

        /**
         * Get the Material to use for display in the GUI.
         * Priority: 1) display_material override, 2) vanilla minecraft:xxx item, 3) null (use PAPER + custom_model_data)
         */
        org.bukkit.Material getDisplayMaterial() {
            // First check for explicit display_material override
            if (displayMaterialOverride != null && !displayMaterialOverride.isEmpty()) {
                String materialStr = displayMaterialOverride;
                if (materialStr.startsWith("minecraft:")) {
                    materialStr = materialStr.substring("minecraft:".length());
                }
                try {
                    return org.bukkit.Material.valueOf(materialStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Invalid material, fall through
                }
            }
            
            // Then check if itemId is a vanilla Minecraft item
            if (itemId != null && !itemId.isEmpty() && itemId.startsWith("minecraft:")) {
                String materialName = itemId.substring("minecraft:".length()).toUpperCase();
                try {
                    return org.bukkit.Material.valueOf(materialName);
                } catch (IllegalArgumentException e) {
                    // Not a valid Material, return null
                    return null;
                }
            }
            return null;
        }
    }

    private static class RewardAction {
        private static final String DEFAULT_NAME = "no_reward";
        private final String name;
        private final int minAmount;
        private final int maxAmount;
        private final List<String> commands;

        RewardAction(String name, int minAmount, int maxAmount, List<String> commands) {
            this.name = name == null ? DEFAULT_NAME : name;
            this.minAmount = minAmount <= 0 ? 1 : minAmount;
            this.maxAmount = maxAmount < this.minAmount ? this.minAmount : maxAmount;
            this.commands = commands == null ? new ArrayList<String>() : commands;
        }

        static RewardAction fromMap(Map<?, ?> map, String fallbackName) {
            if (map == null) {
                return null;
            }
            String name = map.get("name") == null ? fallbackName : map.get("name").toString();
            int[] range = RewardEntry.parseAmount(map.get("amount"));
            List<String> commands = RewardEntry.parseCommands(map.get("commands"), map.get("command") == null ? null : map.get("command").toString());
            return new RewardAction(name, range[0], range[1], commands);
        }

        RewardAction resolve(Random random) {
            int amount = minAmount;
            if (maxAmount > minAmount) {
                amount = minAmount + random.nextInt(maxAmount - minAmount + 1);
            }
            return new RewardAction(name, amount, amount, commands);
        }

        String getName() {
            return name;
        }

        int getAmount() {
            return minAmount;
        }

        List<String> getCommands() {
            return commands;
        }

        int getMinAmount() {
            return minAmount;
        }

        int getMaxAmount() {
            return maxAmount;
        }
    }

    private static class RewardResult {
        private final List<RewardAction> actions;

        RewardResult(List<RewardAction> actions) {
            this.actions = actions == null ? new ArrayList<RewardAction>() : actions;
        }

        static RewardResult empty() {
            List<RewardAction> list = new ArrayList<RewardAction>();
            list.add(new RewardAction(RewardAction.DEFAULT_NAME, 1, 1, null));
            return new RewardResult(list);
        }

        List<RewardAction> getActions() {
            return actions;
        }

        String getDisplay() {
            List<String> parts = new ArrayList<String>();
            for (RewardAction action : actions) {
                parts.add(action.getName() + " x" + action.getAmount());
            }
            return String.join(", ", parts);
        }

        /**
         * Get display string with translated item names
         */
        String getTranslatedDisplay(Messages messages, org.bukkit.command.CommandSender target) {
            List<String> parts = new ArrayList<String>();
            for (RewardAction action : actions) {
                String translatedName = messages.getItemName(target, action.getName());
                parts.add(translatedName + " x" + action.getAmount());
            }
            return String.join(", ", parts);
        }
    }

    private static class TicketRule {
        private final String id;
        private final List<String> applicablePools;
        private final int deductCount;

        TicketRule(String id, List<String> applicablePools, int deductCount) {
            this.id = id;
            this.applicablePools = applicablePools;
            this.deductCount = deductCount;
        }

        static TicketRule fromMap(Map<?, ?> raw) {
            if (raw == null || raw.get("id") == null) {
                return null;
            }
            String id = raw.get("id").toString();
            Object poolsObj = raw.get("applicable_pools");
            List<String> pools = new ArrayList<String>();
            if (poolsObj instanceof List) {
                for (Object o : (List<?>) poolsObj) {
                    pools.add(o.toString());
                }
            }
            int count = 1;
            Object deduct = raw.get("deduct_count");
            if (deduct != null) {
                try {
                    count = Integer.parseInt(deduct.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            return new TicketRule(id, pools, count);
        }

        String getId() {
            return id;
        }

        List<String> getApplicablePools() {
            return applicablePools;
        }

        int getDeductCount() {
            return deductCount;
        }
    }

    private static class WishStatus {
        private final String pool;
        private final int count;
        private final int ticketCount;
        private final boolean validPool;

        WishStatus(String pool, int count, int ticketCount, boolean validPool) {
            this.pool = pool;
            this.count = count;
            this.ticketCount = ticketCount;
            this.validPool = validPool;
        }

        static WishStatus invalid(String pool) {
            return new WishStatus(pool, 0, 0, false);
        }

        boolean isValidPool() {
            return validPool;
        }

        String getPool() {
            return pool;
        }

        int getCount() {
            return count;
        }

        int getTicketCount() {
            return ticketCount;
        }
    }

    private static class WishException extends Exception {
        WishException(String message) {
            super(message);
        }
    }

    private static class TimeWindow {
        private final Instant start;
        private final Instant end;

        TimeWindow(Instant start, Instant end) {
            this.start = start;
            this.end = end;
        }

        boolean contains(Instant now) {
            if (start != null && now.isBefore(start)) {
                return false;
            }
            if (end != null && now.isAfter(end)) {
                return false;
            }
            return true;
        }

        static TimeWindow fromSection(ConfigurationSection section, java.util.logging.Logger logger) {
            if (section == null) {
                return null;
            }
            Instant start = parseInstant(section.getString("startDate"), logger);
            Instant end = parseInstant(section.getString("endDate"), logger);
            if (start == null && end == null) {
                return null;
            }
            return new TimeWindow(start, end);
        }

        static Instant parseInstant(String raw, java.util.logging.Logger logger) {
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            try {
                return Instant.parse(raw);
            } catch (DateTimeParseException e) {
                logger.warning("時間格式無效: " + raw);
                return null;
            }
        }
    }

    // ---------------------- Event Module ----------------------

    private static class EventManager {
        private final JavaPlugin plugin;
        private final Messages messages;
        private final File storageDir;
        private final Map<String, EventDefinition> events = new HashMap<String, EventDefinition>();
        private final Random random = new Random();

        EventManager(JavaPlugin plugin, Messages messages, File configFile) {
            this.plugin = plugin;
            this.messages = messages;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String dataDir = config.getString("storage.data_dir", "userdata");
            storageDir = new File(plugin.getDataFolder(), dataDir);
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                plugin.getLogger().warning("無法創建數據目錄: " + storageDir.getAbsolutePath());
            }
            loadEvents(config.getConfigurationSection("events"));
        }

        private void loadEvents(ConfigurationSection section) {
            events.clear();
            if (section == null) {
                return;
            }
            for (String id : section.getKeys(false)) {
                ConfigurationSection cs = section.getConfigurationSection(id);
                if (cs == null) {
                    continue;
                }
                EventDefinition def = EventDefinition.fromSection(id, cs, plugin.getLogger());
                events.put(id, def);
            }
        }

        List<EventAvailability> listAvailableEvents(Player player) {
            List<EventAvailability> result = new ArrayList<EventAvailability>();
            long now = System.currentTimeMillis();
            YamlConfiguration data = loadUserData(player.getName());
            for (EventDefinition def : events.values()) {
                if (!def.isEnabled() || !def.isActive(now)) {
                    continue;
                }
                boolean can = canParticipate(def, data, now);
                String displayName = def.getDisplayName(messages, player);
                long refreshTime = getRefreshTimeRemaining(def, data, now);
                result.add(new EventAvailability(def.getId(), displayName, can, refreshTime));
            }
            return result;
        }

        /**
         * Calculate remaining time until event refresh for a player.
         * Returns 0 if no limit or can participate, otherwise returns milliseconds until refresh.
         */
        private long getRefreshTimeRemaining(EventDefinition def, YamlConfiguration data, long nowMillis) {
            EventLimit limit = def.getLimit();
            if (limit == null || limit.getCount() <= 0 || limit.getWindowMillis() <= 0) {
                return 0L;
            }
            
            String base = "event.limits." + def.getId();
            long windowStart = data.getLong(base + ".windowStart", 0L);
            int used = data.getInt(base + ".count", 0);
            
            // If window has passed or not used up limit, no wait needed
            if (nowMillis - windowStart >= limit.getWindowMillis()) {
                return 0L;
            }
            if (used < limit.getCount()) {
                return 0L;
            }
            
            // Calculate remaining time
            long elapsed = nowMillis - windowStart;
            return limit.getWindowMillis() - elapsed;
        }

        List<String> participate(Player player, String eventId) throws EventException {
            EventDefinition def = events.get(eventId);
            if (def == null) {
                throw new EventException(messages.format(player, "event.error.not_found"));
            }
            long now = System.currentTimeMillis();
            if (!def.isEnabled() || !def.isActive(now)) {
                throw new EventException(messages.format(player, "event.error.closed"));
            }
            
            // 檢查是否是允許的星期幾
            EventLimit limit = def.getLimit();
            if (limit != null && !limit.isScheduledDay()) {
                throw new EventException(messages.format(player, "event.error.wrong_day"));
            }
            
            YamlConfiguration data = loadUserData(player.getName());
            if (!canParticipate(def, data, now)) {
                throw new EventException(messages.format(player, "event.error.limit"));
            }
            markParticipation(def, data, now);
            saveUserData(player.getName(), data);

            List<String> rewardNames = new ArrayList<String>();
            WeightedList rewardList = def.getRewards();
            if (rewardList != null) {
                if (def.isGrantAll()) {
                    double total = rewardList.getTotalWeight();
                    for (RewardEntry entry : rewardList.getEntries()) {
                        if (!entry.shouldGrant(random, total)) {
                            continue;
                        }
                        RewardResult result = entry.resolve(random);
                        dispatchReward(player, result, plugin);
                        rewardNames.add(result.getDisplay());
                    }
                } else {
                    int rolls = Math.max(1, def.getRewardRolls());
                    for (int i = 0; i < rolls; i++) {
                        RewardResult result = rewardList.pick(random);
                        if (result == null) {
                            continue;
                        }
                        dispatchReward(player, result, plugin);
                        rewardNames.add(result.getDisplay());
                    }
                }
            }
            return rewardNames;
        }

        private boolean canParticipate(EventDefinition def, YamlConfiguration data, long nowMillis) {
            EventLimit limit = def.getLimit();
            if (limit == null || limit.getCount() <= 0 || limit.getWindowMillis() <= 0) {
                return true;
            }
            
            // 檢查是否是允許的星期幾
            if (!limit.isScheduledDay()) {
                return false;
            }
            
            String base = "event.limits." + def.getId();
            long windowStart = data.getLong(base + ".windowStart", 0L);
            int used = data.getInt(base + ".count", 0);
            if (nowMillis - windowStart >= limit.getWindowMillis()) {
                return true;
            }
            return used < limit.getCount();
        }

        private void markParticipation(EventDefinition def, YamlConfiguration data, long nowMillis) {
            EventLimit limit = def.getLimit();
            String base = "event.limits." + def.getId();
            if (limit == null || limit.getWindowMillis() <= 0) {
                data.set(base + ".windowStart", nowMillis);
                data.set(base + ".count", 1);
                return;
            }
            long windowStart = data.getLong(base + ".windowStart", 0L);
            int used = data.getInt(base + ".count", 0);
            if (nowMillis - windowStart >= limit.getWindowMillis()) {
                windowStart = nowMillis;
                used = 0;
            }
            used++;
            data.set(base + ".windowStart", windowStart);
            data.set(base + ".count", used);
        }

        private YamlConfiguration loadUserData(String playerName) {
            File file = new File(storageDir, playerName + ".yml");
            if (!file.exists()) {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("無法創建用戶數據文件: " + e.getMessage());
                }
            }
            return YamlConfiguration.loadConfiguration(file);
        }

        private void saveUserData(String playerName, YamlConfiguration data) {
            File file = new File(storageDir, playerName + ".yml");
            try {
                data.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("保存用戶數據失敗: " + e.getMessage());
            }
        }

        Set<String> getEventIds() {
            return events.keySet();
        }

        Set<String> getActiveEventIds() {
            Set<String> active = new java.util.HashSet<String>();
            long now = System.currentTimeMillis();
            for (EventDefinition def : events.values()) {
                if (def.isEnabled() && def.isActive(now)) {
                    active.add(def.getId());
                }
            }
            return active;
        }
    }

    private static class EventDefinition {
        private final String id;
        private final String name;
        private final String nameKey;
        private final boolean enabled;
        private final TimeWindow window;
        private final EventLimit limit;
        private final WeightedList rewards;
        private final boolean grantAll;
        private final int rewardRolls;

        EventDefinition(String id, String name, String nameKey, boolean enabled, TimeWindow window, EventLimit limit, WeightedList rewards, boolean grantAll, int rewardRolls) {
            this.id = id;
            this.name = name;
            this.nameKey = nameKey;
            this.enabled = enabled;
            this.window = window;
            this.limit = limit;
            this.rewards = rewards;
            this.grantAll = grantAll;
            this.rewardRolls = rewardRolls;
        }

        static EventDefinition fromSection(String id, ConfigurationSection section, java.util.logging.Logger logger) {
            String name = section.getString("name", id);
            String nameKey = section.getString("name_key", "event.names." + id);
            boolean enabled = section.getBoolean("enabled", true);
            TimeWindow window = TimeWindow.fromSection(section.getConfigurationSection("duration"), logger);
            EventLimit limit = EventLimit.fromSection(section.getConfigurationSection("limit_modes"), logger);
            WeightedList rewards = WeightedList.fromSection(section.getConfigurationSection("rewards"));
            boolean grantAll = section.getBoolean("grant_all", true);
            int rewardRolls = section.getInt("reward_rolls", 1);
            if (rewardRolls <= 0) {
                rewardRolls = 1;
            }
            return new EventDefinition(id, name, nameKey, enabled, window, limit, rewards, grantAll, rewardRolls);
        }

        String getId() {
            return id;
        }

        String getName() {
            return name;
        }

        String getDisplayName(Messages messages, CommandSender target) {
            String translated = messages.format(target, nameKey);
            if (translated == null || translated.equals(nameKey)) {
                return messages.colorize(name);
            }
            return translated;
        }

        boolean isEnabled() {
            return enabled;
        }

        boolean isActive(long nowMillis) {
            return window == null || window.contains(Instant.ofEpochMilli(nowMillis));
        }

        EventLimit getLimit() {
            return limit;
        }

        WeightedList getRewards() {
            return rewards;
        }

        boolean isGrantAll() {
            return grantAll;
        }

        int getRewardRolls() {
            return rewardRolls;
        }
    }

    private static class EventLimit {
        private final int count;
        private final long windowMillis;
        private final int scheduleDayOfWeek; // 1=Monday, 7=Sunday, 0=any day
        private final String refreshAtTime;  // HH:mm format, e.g., "00:00"

        EventLimit(int count, long windowMillis, int scheduleDayOfWeek, String refreshAtTime) {
            this.count = count;
            this.windowMillis = windowMillis;
            this.scheduleDayOfWeek = scheduleDayOfWeek;
            this.refreshAtTime = refreshAtTime;
        }

        static EventLimit fromSection(ConfigurationSection section, java.util.logging.Logger logger) {
            if (section == null) {
                return null;
            }
            int count = section.getInt("count", 0);
            long windowMillis = parseDurationMillis(section.getString("time"), logger);
            int scheduleDayOfWeek = section.getInt("refresh_at_week", 0); // 0 = any day
            String refreshAtTime = section.getString("refresh_at_time", "00:00");
            if (count <= 0 || windowMillis <= 0) {
                return null;
            }
            return new EventLimit(count, windowMillis, scheduleDayOfWeek, refreshAtTime);
        }

        int getCount() {
            return count;
        }

        long getWindowMillis() {
            return windowMillis;
        }

        int getScheduleDayOfWeek() {
            return scheduleDayOfWeek;
        }

        String getRefreshAtTime() {
            return refreshAtTime;
        }

        /**
         * 檢查當前是否是允許參與的日期
         * @return true 如果今天是允許的星期幾（或沒有限制）
         */
        boolean isScheduledDay() {
            if (scheduleDayOfWeek <= 0 || scheduleDayOfWeek > 7) {
                return true; // 沒有星期限制
            }
            // Java DayOfWeek: MONDAY=1, SUNDAY=7
            int today = java.time.LocalDate.now().getDayOfWeek().getValue();
            return today == scheduleDayOfWeek;
        }

        private static long parseDurationMillis(String raw, java.util.logging.Logger logger) {
            if (raw == null || raw.length() < 2) {
                return 0L;
            }
            raw = raw.trim();
            char suffix = Character.toLowerCase(raw.charAt(raw.length() - 1));
            long value;
            try {
                value = Long.parseLong(raw.substring(0, raw.length() - 1));
            } catch (NumberFormatException e) {
                logger.warning("無法解析時間: " + raw);
                return 0L;
            }
            switch (suffix) {
                case 'h':
                    return Duration.ofHours(value).toMillis();
                case 'd':
                    return Duration.ofDays(value).toMillis();
                case 'w':
                    return Duration.ofDays(value * DAYS_PER_WEEK).toMillis();
                case 'm':
                    return Duration.ofDays(value * DAYS_PER_MONTH).toMillis();
                case 'y':
                    return Duration.ofDays(value * DAYS_PER_YEAR).toMillis();
                default:
                    logger.warning("未知時間單位: " + raw);
                    return 0L;
            }
        }
    }

    // ---------------------- CDK Module ----------------------

    private static class CdkManager {
        private final JavaPlugin plugin;
        private final Messages messages;
        private final File storageDir;
        private final File globalFile;
        private final Map<String, CdkCode> codes = new HashMap<String, CdkCode>();
        private final Random random = new Random();

        CdkManager(JavaPlugin plugin, Messages messages, File configFile) {
            this.plugin = plugin;
            this.messages = messages;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String dataDir = config.getString("storage.data_dir", "userdata");
            storageDir = new File(plugin.getDataFolder(), dataDir);
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                plugin.getLogger().warning("無法創建數據目錄: " + storageDir.getAbsolutePath());
            }
            globalFile = new File(storageDir, "cdk.yml");
            loadCodes(config.getConfigurationSection("codes"));
        }

        private void loadCodes(ConfigurationSection section) {
            codes.clear();
            if (section == null) {
                return;
            }
            for (String id : section.getKeys(false)) {
                ConfigurationSection cs = section.getConfigurationSection(id);
                if (cs == null) {
                    continue;
                }
                CdkCode code = CdkCode.fromSection(id, cs, plugin.getLogger());
                codes.put(id.toLowerCase(), code);
            }
        }

        List<String> redeem(Player player, String codeRaw) throws CdkException {
            String codeKey = codeRaw.toLowerCase();
            CdkCode code = codes.get(codeKey);
            if (code == null || !code.isEnabled()) {
                throw new CdkException(messages.format(player, "cdk.invalid"));
            }
            Instant now = Instant.now();
            if (!code.isActive(now)) {
                throw new CdkException(messages.format(player, "cdk.not_active"));
            }

            YamlConfiguration userData = loadUserData(player.getName());
            int usedByUser = userData.getInt("cdk.used." + codeKey, 0);
            if (code.getPerUserLimit() > 0 && usedByUser >= code.getPerUserLimit()) {
                throw new CdkException(messages.format(player, "cdk.limit_user"));
            }

            YamlConfiguration global = loadGlobal();
            int used = global.getInt("codes." + codeKey + ".used", 0);
            if (code.getLimit() > 0 && used >= code.getLimit()) {
                throw new CdkException(messages.format(player, "cdk.limit"));
            }

            List<String> rewardNames = new ArrayList<String>();
            WeightedList rewardList = code.getRewards();
            if (rewardList != null) {
                if (code.isGrantAll()) {
                    double total = rewardList.getTotalWeight();
                    for (RewardEntry entry : rewardList.getEntries()) {
                        if (!entry.shouldGrant(random, total)) {
                            continue;
                        }
                        RewardResult result = entry.resolve(random);
                        dispatchReward(player, result, plugin);
                        rewardNames.add(result.getDisplay());
                    }
                } else {
                    int rolls = Math.max(1, code.getRewardRolls());
                    for (int i = 0; i < rolls; i++) {
                        RewardResult result = rewardList.pick(random);
                        if (result == null) {
                            continue;
                        }
                        dispatchReward(player, result, plugin);
                        rewardNames.add(result.getDisplay());
                    }
                }
            }

            userData.set("cdk.used." + codeKey, usedByUser + 1);
            saveUserData(player.getName(), userData);
            global.set("codes." + codeKey + ".used", used + 1);
            saveGlobal(global);
            return rewardNames;
        }

        private YamlConfiguration loadUserData(String playerName) {
            File file = new File(storageDir, playerName + ".yml");
            if (!file.exists()) {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("無法創建用戶數據文件: " + e.getMessage());
                }
            }
            return YamlConfiguration.loadConfiguration(file);
        }

        private void saveUserData(String playerName, YamlConfiguration data) {
            File file = new File(storageDir, playerName + ".yml");
            try {
                data.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("保存用戶數據失敗: " + e.getMessage());
            }
        }

        private YamlConfiguration loadGlobal() {
            if (!globalFile.exists()) {
                try {
                    globalFile.getParentFile().mkdirs();
                    globalFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("無法創建CDK全局文件: " + e.getMessage());
                }
            }
            return YamlConfiguration.loadConfiguration(globalFile);
        }

        private void saveGlobal(YamlConfiguration data) {
            try {
                data.save(globalFile);
            } catch (IOException e) {
                plugin.getLogger().warning("保存CDK全局數據失敗: " + e.getMessage());
            }
        }

        Set<String> getCodeIds() {
            return codes.keySet();
        }
    }

    private static class CdkCode {
        private final String id;
        private final boolean enabled;
        private final int limit;
        private final TimeWindow window;
        private final int perUserLimit;
        private final WeightedList rewards;
        private final boolean grantAll;
        private final int rewardRolls;

        CdkCode(String id, boolean enabled, int limit, TimeWindow window, int perUserLimit, WeightedList rewards, boolean grantAll, int rewardRolls) {
            this.id = id;
            this.enabled = enabled;
            this.limit = limit;
            this.window = window;
            this.perUserLimit = perUserLimit;
            this.rewards = rewards;
            this.grantAll = grantAll;
            this.rewardRolls = rewardRolls <= 0 ? 1 : rewardRolls;
        }

        static CdkCode fromSection(String id, ConfigurationSection section, java.util.logging.Logger logger) {
            boolean enabled = section.getBoolean("enabled", true);
            int limit = section.getInt("limit", 0);
            TimeWindow window = TimeWindow.fromSection(section.getConfigurationSection("duration"), logger);
            if (window == null) {
                Instant expires = TimeWindow.parseInstant(section.getString("expires"), logger);
                if (expires != null) {
                    window = new TimeWindow(null, expires);
                }
            }
            int perUser = section.getInt("per_user_limit", 1);
            WeightedList rewards = WeightedList.fromSection(section.getConfigurationSection("rewards"));
            boolean grantAll = section.getBoolean("grant_all", true);
            int rewardRolls = section.getInt("reward_rolls", 1);
            if (rewardRolls <= 0) {
                rewardRolls = 1;
            }
            return new CdkCode(id, enabled, limit, window, perUser, rewards, grantAll, rewardRolls);
        }

        boolean isEnabled() {
            return enabled;
        }

        int getLimit() {
            return limit;
        }

        int getPerUserLimit() {
            return perUserLimit;
        }

        WeightedList getRewards() {
            return rewards;
        }

        boolean isGrantAll() {
            return grantAll;
        }

        int getRewardRolls() {
            return rewardRolls;
        }

        boolean isActive(Instant now) {
            return window == null || window.contains(now);
        }
        }

    private static class CdkException extends Exception {
        CdkException(String message) {
            super(message);
        }
    }
    private static class EventAvailability {
        private final String id;
        private final String displayName;
        private final boolean canParticipate;
        private final long refreshTimeMillis; // milliseconds until refresh, 0 if no limit

        EventAvailability(String id, String displayName, boolean canParticipate, long refreshTimeMillis) {
            this.id = id;
            this.displayName = displayName;
            this.canParticipate = canParticipate;
            this.refreshTimeMillis = refreshTimeMillis;
        }

        String getId() {
            return id;
        }

        String getDisplayName() {
            return displayName;
        }

        boolean isCanParticipate() {
            return canParticipate;
        }

        long getRefreshTimeMillis() {
            return refreshTimeMillis;
        }

        /**
         * Get formatted refresh time string.
         */
        String getFormattedRefreshTime() {
            if (refreshTimeMillis <= 0) {
                return "";
            }
            long seconds = refreshTimeMillis / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) {
                return String.format("%dd %dh", days, hours % 24);
            } else if (hours > 0) {
                return String.format("%dh %dm", hours, minutes % 60);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds % 60);
            } else {
                return String.format("%ds", seconds);
            }
        }
    }

    private static class EventException extends Exception {
        EventException(String message) {
            super(message);
        }
    }
}
