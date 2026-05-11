package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads configurable command/sub-command aliases for NekoSuite.
 *
 * <p>The config lives at {@code plugins/NekoSuite/command_config.yml} and has the structure:
 * <pre>
 * commands:
 *   wish:                       # canonical command name (matches plugin.yml)
 *     aliases: [w, qiyuan]      # extra labels that should also invoke this command
 *     subcommands:
 *       menu: [menu, m, gui]    # first token canonicalises to "menu"
 *       history: [history, hist]
 *       ticket: [ticket, tk]
 *     # Optional nested sub-command groups (e.g. /wish ticket add):
 *     nested:
 *       ticket:
 *         subcommands:
 *           add: [add, +]
 *           remove: [remove, -]
 *           list: [list, ls]
 *   ngame:
 *     aliases: [game]
 *     subcommands:
 *       rtp: [rtp, randomtp]
 *       ...
 *     nested:
 *       rtp:
 *         subcommands:
 *           start: [start, begin]
 * </pre>
 *
 * <p>Two operations are exposed to the dispatcher:
 * <ul>
 *   <li>{@link #applyAliasRegistration(JavaPlugin)} registers every configured alias against the
 *       live Bukkit {@link CommandMap} so the alias label is routed to the canonical command. The
 *       canonical command must already be declared in {@code plugin.yml}.</li>
 *   <li>{@link #resolveSub(String, String)} converts an alias the player typed into the canonical
 *       sub-command name. Calling this in the dispatcher means handler {@code switch} statements
 *       only have to know the canonical name.</li>
 * </ul>
 *
 * <p>If {@code command_config.yml} is missing, the bundled default is copied automatically.
 */
public final class CommandConfig {

    private static final String FILE_NAME = "command_config.yml";

    /** canonical command -> list of extra aliases (lowercase, may be empty). */
    private final Map<String, List<String>> commandAliases = new LinkedHashMap<String, List<String>>();

    /**
     * Sub-command alias map.
     *
     * <p>Key is a dotted namespace ({@code "exp"} for top-level subcommands of {@code /exp},
     * {@code "ngame.rtp"} for nested subcommands under {@code /ngame rtp}, etc.).
     * Value maps each lowercased alias to its canonical lowercase sub-command name.
     */
    private final Map<String, Map<String, String>> subAliases = new HashMap<String, Map<String, String>>();

    /** Tracks which canonical commands had aliases successfully registered, for logging. */
    private final List<String> registeredAliases = new ArrayList<String>();

    public CommandConfig(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            try {
                plugin.saveResource(FILE_NAME, false);
            } catch (IllegalArgumentException ignore) {
                // bundled default missing; we fall back to an empty configuration.
            }
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection cmds = cfg.getConfigurationSection("commands");
        if (cmds == null) {
            return;
        }
        for (String canonical : cmds.getKeys(false)) {
            ConfigurationSection section = cmds.getConfigurationSection(canonical);
            if (section == null) {
                continue;
            }
            String canonicalLower = canonical.toLowerCase();

            // ----- aliases -----
            List<String> aliases = readStringList(section, "aliases");
            List<String> cleaned = new ArrayList<String>();
            for (String a : aliases) {
                if (a == null) continue;
                String s = a.trim();
                if (s.isEmpty()) continue;
                String lower = s.toLowerCase();
                if (lower.equals(canonicalLower)) continue;
                if (!cleaned.contains(lower)) cleaned.add(lower);
            }
            commandAliases.put(canonicalLower, cleaned);

            // ----- subcommands (top-level) -----
            ConfigurationSection subs = section.getConfigurationSection("subcommands");
            if (subs != null) {
                subAliases.put(canonicalLower, buildAliasMap(subs));
            }

            // ----- nested subcommands -----
            ConfigurationSection nested = section.getConfigurationSection("nested");
            if (nested != null) {
                for (String nestedKey : nested.getKeys(false)) {
                    ConfigurationSection nestedSec = nested.getConfigurationSection(nestedKey);
                    if (nestedSec == null) continue;
                    ConfigurationSection nestedSubs = nestedSec.getConfigurationSection("subcommands");
                    if (nestedSubs == null) continue;
                    String nsKey = canonicalLower + "." + nestedKey.toLowerCase();
                    subAliases.put(nsKey, buildAliasMap(nestedSubs));
                }
            }
        }
    }

    private static Map<String, String> buildAliasMap(ConfigurationSection section) {
        Map<String, String> out = new HashMap<String, String>();
        for (String canonical : section.getKeys(false)) {
            String canonicalLower = canonical.toLowerCase();
            List<String> aliases = readStringList(section, canonical);
            // canonical itself is always a valid alias of itself.
            out.put(canonicalLower, canonicalLower);
            for (String a : aliases) {
                if (a == null) continue;
                String s = a.trim();
                if (s.isEmpty()) continue;
                out.put(s.toLowerCase(), canonicalLower);
            }
        }
        return out;
    }

    private static List<String> readStringList(ConfigurationSection sec, String path) {
        List<String> out = new ArrayList<String>();
        List<?> raw = sec.getList(path);
        if (raw != null) {
            for (Object o : raw) {
                if (o != null) out.add(o.toString());
            }
        }
        return out;
    }

    /**
     * Resolve a sub-command alias to its canonical name.
     *
     * @param namespace      either a canonical top-level command (e.g. {@code "exp"}) or a nested
     *                       group (e.g. {@code "ngame.rtp"}).
     * @param input          the raw token the player typed (may be {@code null}).
     * @return the canonical sub-command name (lowercase) if {@code input} is a configured alias,
     *         or {@code input.toLowerCase()} unchanged if no alias mapping matches.
     */
    public String resolveSub(String namespace, String input) {
        if (input == null) return null;
        String lower = input.toLowerCase();
        if (namespace == null) return lower;
        Map<String, String> map = subAliases.get(namespace.toLowerCase());
        if (map == null) return lower;
        String canonical = map.get(lower);
        return canonical != null ? canonical : lower;
    }

    /**
     * Like {@link #resolveSub} but returns {@code null} if {@code input} is not a known alias.
     * Useful when a handler treats unknown tokens as data (e.g. a player name).
     */
    public String resolveSubIfKnown(String namespace, String input) {
        if (input == null || namespace == null) return null;
        Map<String, String> map = subAliases.get(namespace.toLowerCase());
        if (map == null) return null;
        return map.get(input.toLowerCase());
    }

    /**
     * Returns the canonical command names known to the config (lowercase, in declaration order).
     */
    public Set<String> getCanonicalCommands() {
        return Collections.unmodifiableSet(commandAliases.keySet());
    }

    /** Returns the configured extra aliases for the given canonical command (never {@code null}). */
    public List<String> getCommandAliases(String canonical) {
        if (canonical == null) return Collections.emptyList();
        List<String> v = commandAliases.get(canonical.toLowerCase());
        return v == null ? Collections.<String>emptyList() : Collections.unmodifiableList(v);
    }

    /** Returns a list of canonical commands that had aliases registered, suitable for logging. */
    public List<String> getRegisteredAliasReport() {
        return Collections.unmodifiableList(registeredAliases);
    }

    /**
     * Registers every configured alias against the live Bukkit {@link CommandMap}.
     *
     * <p>For each canonical command declared in {@code plugin.yml}, this:
     * <ol>
     *   <li>Appends configured aliases to the {@link PluginCommand#getAliases()} list.</li>
     *   <li>Re-registers the command on the server's command map, which causes new alias labels
     *       to become routable (existing labels are unaffected).</li>
     * </ol>
     *
     * <p>Failures are logged but never propagated — command customization is best-effort and
     * NekoSuite must remain functional even when reflection access is denied.
     */
    public void applyAliasRegistration(JavaPlugin plugin) {
        registeredAliases.clear();
        CommandMap commandMap = lookupCommandMap();
        for (Map.Entry<String, List<String>> entry : commandAliases.entrySet()) {
            String canonical = entry.getKey();
            List<String> extra = entry.getValue();
            if (extra.isEmpty()) continue;
            PluginCommand cmd = plugin.getCommand(canonical);
            if (cmd == null) {
                plugin.getLogger().warning("command_config.yml references unknown command '" + canonical
                        + "' (not declared in plugin.yml).");
                continue;
            }
            Set<String> merged = new LinkedHashSet<String>();
            for (String a : cmd.getAliases()) {
                if (a != null) merged.add(a.toLowerCase());
            }
            for (String a : extra) {
                merged.add(a);
            }
            cmd.setAliases(new ArrayList<String>(merged));
            if (commandMap != null) {
                try {
                    commandMap.register(plugin.getName().toLowerCase(), cmd);
                    registeredAliases.add(canonical + " -> " + extra);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Failed to register aliases for '" + canonical
                            + "': " + ex.getMessage());
                }
            }
        }
    }

    private static CommandMap lookupCommandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            Object value = field.get(Bukkit.getServer());
            if (value instanceof CommandMap) {
                return (CommandMap) value;
            }
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            // Server implementation does not expose commandMap; alias registration is unavailable.
        }
        return null;
    }
}
