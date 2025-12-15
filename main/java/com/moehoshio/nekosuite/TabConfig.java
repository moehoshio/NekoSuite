package com.moehoshio.nekosuite;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads configurable tab-completion suggestions with arbitrary depth.
 * YAML structure:
 * commands:
 *   wish:
 *     _root: [query]             # suggestions for first argument
 *     query:
 *       _root: [pool1, pool2]    # suggestions for second argument when first is "query"
 *       special:
 *         _root: [x, y]          # suggestions for third argument when args are [query, special]
 */
public class TabConfig {

    private static final String ROOT_KEY = "_root";

    private final Map<String, TabNode> commands = new HashMap<String, TabNode>();

    public TabConfig(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "tab_config.yml");
        if (!file.exists()) {
            plugin.saveResource("tab_config.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection cmdSection = config.getConfigurationSection("commands");
        if (cmdSection != null) {
            for (String cmd : cmdSection.getKeys(false)) {
                ConfigurationSection sec = cmdSection.getConfigurationSection(cmd);
                if (sec != null) {
                    commands.put(cmd.toLowerCase(), parseNode(sec));
                }
            }
        }
    }

    /**
     * Suggests entries for the next argument given current args (last element is the partial being typed).
     */
    public List<String> suggest(String command, String[] args) {
        TabNode node = commands.get(command == null ? "" : command.toLowerCase());
        if (node == null) {
            return new ArrayList<String>();
        }
        // Traverse using all arguments except the last (which is being completed)
        for (int i = 0; i < Math.max(0, args.length - 1); i++) {
            String key = args[i] == null ? "" : args[i].toLowerCase();
            if (!node.children.containsKey(key)) {
                return new ArrayList<String>();
            }
            node = node.children.get(key);
        }
        return node.suggestions();
    }

    private TabNode parseNode(ConfigurationSection section) {
        List<String> values = readList(section, ROOT_KEY);
        Map<String, TabNode> children = new HashMap<String, TabNode>();
        for (String key : section.getKeys(false)) {
            if (ROOT_KEY.equalsIgnoreCase(key)) {
                continue;
            }
            ConfigurationSection childSec = section.getConfigurationSection(key);
            if (childSec != null) {
                children.put(key.toLowerCase(), parseNode(childSec));
            } else {
                // If value is list under a simple key
                List<String> list = new ArrayList<String>();
                List<?> raw = section.getList(key);
                if (raw != null) {
                    for (Object o : raw) {
                        if (o != null) {
                            list.add(o.toString());
                        }
                    }
                }
                TabNode leaf = new TabNode(list, new HashMap<String, TabNode>());
                children.put(key.toLowerCase(), leaf);
            }
        }
        return new TabNode(values, children);
    }

    private List<String> readList(ConfigurationSection sec, String path) {
        List<String> list = new ArrayList<String>();
        List<?> raw = sec.getList(path);
        if (raw != null) {
            for (Object o : raw) {
                if (o != null) {
                    list.add(o.toString());
                }
            }
        }
        return list;
    }

    private static class TabNode {
        private final List<String> values;
        private final Map<String, TabNode> children;

        TabNode(List<String> values, Map<String, TabNode> children) {
            this.values = values == null ? new ArrayList<String>() : values;
            this.children = children == null ? new HashMap<String, TabNode>() : children;
        }

        List<String> suggestions() {
            List<String> out = new ArrayList<String>();
            out.addAll(values);
            out.addAll(children.keySet());
            return out;
        }
    }
}
