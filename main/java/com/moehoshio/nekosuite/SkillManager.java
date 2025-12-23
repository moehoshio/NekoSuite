package com.moehoshio.nekosuite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * SkillManager - 技能系統管理器
 * 根據物品 Lore 觸發技能效果
 */
public class SkillManager implements Listener {

    // Constants
    private static final int PARTICLE_COUNT = 20;
    private static final double FIRE_IGNITE_CHANCE = 0.3;

    private final JavaPlugin plugin;
    private final Messages messages;
    private final File storageDir;
    private final Map<String, SkillDefinition> skills = new HashMap<String, SkillDefinition>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<UUID, Map<String, Long>>();
    private final Random random = new Random();
    private boolean enabled = true;
    private int defaultCooldown = 5;
    private boolean showCastMessage = true;

    public SkillManager(JavaPlugin plugin, Messages messages, File configFile) {
        this.plugin = plugin;
        this.messages = messages;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        String dataDir = config.getString("storage.data_dir", "userdata");
        storageDir = new File(plugin.getDataFolder(), dataDir);
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create skill data directory: " + storageDir.getAbsolutePath());
        }
        
        loadConfig(config);
        
        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig(YamlConfiguration config) {
        skills.clear();
        
        // Load settings
        ConfigurationSection settings = config.getConfigurationSection("settings");
        if (settings != null) {
            enabled = settings.getBoolean("enabled", true);
            defaultCooldown = settings.getInt("default_cooldown", 5);
            showCastMessage = settings.getBoolean("show_cast_message", true);
        }
        
        // Load skills
        ConfigurationSection skillsSection = config.getConfigurationSection("skills");
        if (skillsSection == null) {
            return;
        }
        
        Set<String> skillIds = skillsSection.getKeys(false);
        for (String skillId : skillIds) {
            ConfigurationSection skillSection = skillsSection.getConfigurationSection(skillId);
            if (skillSection == null) {
                continue;
            }
            SkillDefinition skill = SkillDefinition.fromSection(skillId, skillSection, defaultCooldown);
            if (skill != null) {
                skills.put(skillId, skill);
            }
        }
        
        plugin.getLogger().info("Loaded " + skills.size() + " skill(s).");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!enabled) {
            return;
        }
        
        Player player = event.getPlayer();
        Action action = event.getAction();
        
        // Determine trigger type
        String trigger = determineTrigger(action, player.isSneaking());
        if (trigger == null) {
            return;
        }
        
        // Get item in hand
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return;
        }
        
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return;
        }
        
        // Debug: Log item info if player has debug permission
        if (player.hasPermission("nekosuite.skill.debug")) {
            plugin.getLogger().info("[SkillDebug] Player: " + player.getName() + ", Trigger: " + trigger);
            plugin.getLogger().info("[SkillDebug] Item: " + item.getType().name());
            for (int i = 0; i < lore.size(); i++) {
                String loreLine = lore.get(i);
                // Log with escaped special characters
                String escaped = loreLine.replace(ChatColor.COLOR_CHAR, '&');
                plugin.getLogger().info("[SkillDebug] Lore[" + i + "]: '" + escaped + "' (raw: '" + loreLine + "')");
            }
            
            // Log all skill requirements for comparison
            for (SkillDefinition skill : skills.values()) {
                String req = skill.getLoreRequirement();
                String translatedReq = translateColorCodes(req);
                plugin.getLogger().info("[SkillDebug] Skill '" + skill.getId() + "' trigger: " + skill.getTrigger() + 
                    ", req: '" + req + "' -> '" + translatedReq.replace(ChatColor.COLOR_CHAR, '&') + "'");
            }
        }
        
        // Find matching skill
        SkillDefinition matchedSkill = null;
        for (SkillDefinition skill : skills.values()) {
            if (!skill.getTrigger().equals(trigger)) {
                continue;
            }
            if (hasRequiredLore(lore, skill.getLoreRequirement())) {
                matchedSkill = skill;
                break;
            }
        }
        
        if (matchedSkill == null) {
            return;
        }
        
        // Check permission
        if (matchedSkill.getPermission() != null && !matchedSkill.getPermission().isEmpty()) {
            if (!player.hasPermission(matchedSkill.getPermission())) {
                player.sendMessage(messages.format(player, "skill.no_permission"));
                return;
            }
        }
        
        // Check cooldown
        long remaining = getCooldownRemaining(player, matchedSkill.getId());
        if (remaining > 0) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("seconds", String.valueOf(remaining / 1000));
            player.sendMessage(messages.format(player, "skill.cooldown", map));
            return;
        }
        
        // Cancel the event to prevent normal item use
        event.setCancelled(true);
        
        // Execute skill
        executeSkill(player, matchedSkill);
        
        // Set cooldown
        setCooldown(player, matchedSkill.getId(), matchedSkill.getCooldown() * 1000L);
        
        // Show cast message
        if (showCastMessage) {
            Map<String, String> map = new HashMap<String, String>();
            map.put("skill", matchedSkill.getName());
            player.sendMessage(messages.format(player, "skill.cast", map));
        }
    }

    private String determineTrigger(Action action, boolean sneaking) {
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            return sneaking ? "sneak_right_click" : "right_click";
        }
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            return sneaking ? "sneak_left_click" : "left_click";
        }
        return null;
    }

    private boolean hasRequiredLore(List<String> itemLore, String requirement) {
        if (requirement == null || requirement.isEmpty()) {
            return false;
        }
        // Translate color codes for comparison
        String coloredRequirement = translateColorCodes(requirement);
        // Also strip all color codes for fallback comparison
        String strippedRequirement = ChatColor.stripColor(coloredRequirement);
        
        for (String line : itemLore) {
            String coloredLine = translateColorCodes(line);
            // Try exact match with colors first
            if (coloredLine.contains(coloredRequirement)) {
                return true;
            }
            // Fallback: try matching without color codes
            String strippedLine = ChatColor.stripColor(coloredLine);
            if (strippedLine.contains(strippedRequirement)) {
                return true;
            }
        }
        return false;
    }

    private void executeSkill(Player player, SkillDefinition skill) {
        String type = skill.getType();
        Map<String, Object> effects = skill.getEffects();
        Location loc = player.getLocation();
        
        // Play sound
        String soundName = (String) effects.get("sound");
        if (soundName != null) {
            try {
                Sound sound = Sound.valueOf(soundName);
                player.getWorld().playSound(loc, sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {
            }
        }
        
        switch (type.toLowerCase()) {
            case "lightning":
                executeLightning(player, effects);
                break;
            case "fire":
                executeFire(player, effects);
                break;
            case "ice":
                executeIce(player, effects);
                break;
            case "explosion":
                executeExplosion(player, effects);
                break;
            case "heal":
                executeHeal(player, effects);
                break;
            case "speed":
                executeSpeed(player, effects);
                break;
            case "teleport":
                executeTeleport(player, effects);
                break;
            case "shield":
                executeShield(player, effects);
                break;
            case "poison":
                executePoison(player, effects);
                break;
            default:
                plugin.getLogger().warning("Unknown skill type: " + type);
                break;
        }
    }

    private void executeLightning(Player player, Map<String, Object> effects) {
        Location center = player.getLocation();
        int count = getIntEffect(effects, "count", 5);
        int radius = getIntEffect(effects, "radius", 6);
        boolean setFire = getBooleanEffect(effects, "set_fire", false);
        double chainDamage = getDoubleEffect(effects, "chain_damage", 0);
        
        // Spawn particles
        spawnParticles(center, effects, radius);
        
        for (int i = 0; i < count; i++) {
            // Random offset from player
            double offsetX = (random.nextDouble() - 0.5) * 2 * radius;
            double offsetZ = (random.nextDouble() - 0.5) * 2 * radius;
            Location strikeLocation = center.clone().add(offsetX, 0, offsetZ);
            
            // Strike lightning
            if (setFire) {
                player.getWorld().strikeLightning(strikeLocation);
            } else {
                player.getWorld().strikeLightningEffect(strikeLocation);
            }
            
            // Apply chain damage to nearby entities
            if (chainDamage > 0) {
                Collection<Entity> nearby = strikeLocation.getWorld().getNearbyEntities(strikeLocation, 2, 2, 2);
                for (Entity entity : nearby) {
                    if (entity instanceof LivingEntity && !entity.equals(player)) {
                        ((LivingEntity) entity).damage(chainDamage, player);
                    }
                }
            }
        }
    }

    private void executeFire(Player player, Map<String, Object> effects) {
        Location center = player.getLocation();
        int radius = getIntEffect(effects, "radius", 4);
        double damage = getDoubleEffect(effects, "damage", 4.0);
        boolean igniteGround = getBooleanEffect(effects, "ignite_ground", true);
        int duration = getIntEffect(effects, "duration", 60);
        
        // Spawn particles
        spawnParticles(center, effects, radius);
        
        // Damage nearby entities and set them on fire
        Collection<Entity> nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius);
        for (Entity entity : nearby) {
            if (entity instanceof LivingEntity && !entity.equals(player)) {
                LivingEntity living = (LivingEntity) entity;
                living.damage(damage, player);
                living.setFireTicks(duration);
            }
        }
        
        // Optionally ignite ground blocks
        if (igniteGround) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (random.nextDouble() < FIRE_IGNITE_CHANCE) {
                        Location blockLoc = center.clone().add(x, 0, z);
                        Block block = blockLoc.getBlock();
                        Block above = block.getRelative(0, 1, 0);
                        if (above.getType() == Material.AIR && block.getType().isSolid()) {
                            above.setType(Material.FIRE);
                        }
                    }
                }
            }
        }
    }

    private void executeIce(Player player, Map<String, Object> effects) {
        Location center = player.getLocation();
        int radius = getIntEffect(effects, "radius", 5);
        int slownessLevel = getIntEffect(effects, "slowness_level", 2);
        int slownessDuration = getIntEffect(effects, "slowness_duration", 100);
        double damage = getDoubleEffect(effects, "damage", 2.0);
        
        // Spawn particles
        spawnParticles(center, effects, radius);
        
        // Apply slowness and damage to nearby entities
        // Note: Potion amplifiers are 0-indexed (level 0 = level I, level 1 = level II)
        // Config uses 1-indexed values for user-friendliness
        int slownessAmplifier = Math.max(0, slownessLevel - 1);
        Collection<Entity> nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius);
        for (Entity entity : nearby) {
            if (entity instanceof LivingEntity && !entity.equals(player)) {
                LivingEntity living = (LivingEntity) entity;
                living.damage(damage, player);
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, slownessDuration, slownessAmplifier));
            }
        }
    }

    private void executeExplosion(Player player, Map<String, Object> effects) {
        Location center = player.getLocation();
        int radius = getIntEffect(effects, "radius", 6);
        double knockback = getDoubleEffect(effects, "knockback", 2.5);
        double damage = getDoubleEffect(effects, "damage", 6.0);
        
        // Spawn particles
        spawnParticles(center, effects, radius);
        
        // Apply knockback and damage to nearby entities
        Collection<Entity> nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius);
        for (Entity entity : nearby) {
            if (entity instanceof LivingEntity && !entity.equals(player)) {
                LivingEntity living = (LivingEntity) entity;
                living.damage(damage, player);
                
                // Calculate knockback direction
                Vector direction = living.getLocation().toVector().subtract(center.toVector()).normalize();
                direction.setY(0.5); // Add upward component
                direction.multiply(knockback);
                living.setVelocity(direction);
            }
        }
    }

    private void executeHeal(Player player, Map<String, Object> effects) {
        Location center = player.getLocation();
        double healAmount = getDoubleEffect(effects, "heal_amount", 8.0);
        int radius = getIntEffect(effects, "radius", 5);
        boolean healSelf = getBooleanEffect(effects, "heal_self", true);
        boolean healOthers = getBooleanEffect(effects, "heal_others", true);
        
        // Spawn particles
        spawnParticles(center, effects, radius);
        
        // Heal self
        if (healSelf) {
            double newHealth = Math.min(player.getHealth() + healAmount, player.getMaxHealth());
            player.setHealth(newHealth);
        }
        
        // Heal nearby players
        if (healOthers) {
            Collection<Entity> nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius);
            for (Entity entity : nearby) {
                if (entity instanceof Player && !entity.equals(player)) {
                    Player other = (Player) entity;
                    double newHealth = Math.min(other.getHealth() + healAmount, other.getMaxHealth());
                    other.setHealth(newHealth);
                }
            }
        }
    }

    private void executeSpeed(Player player, Map<String, Object> effects) {
        int speedLevel = getIntEffect(effects, "speed_level", 3);
        int duration = getIntEffect(effects, "duration", 100);
        
        // Spawn particles around player
        spawnParticles(player.getLocation(), effects, 2);
        
        // Apply speed effect
        // Note: Potion amplifiers are 0-indexed (level 0 = level I, level 1 = level II)
        // Config uses 1-indexed values for user-friendliness
        int speedAmplifier = Math.max(0, speedLevel - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, speedAmplifier));
    }

    private void executeTeleport(Player player, Map<String, Object> effects) {
        int distance = getIntEffect(effects, "distance", 10);
        boolean passThroughBlocks = getBooleanEffect(effects, "pass_through_blocks", false);
        
        Location start = player.getLocation();
        Vector direction = start.getDirection().normalize();
        
        Location target = null;
        
        if (passThroughBlocks) {
            // Simply move forward by distance
            target = start.clone().add(direction.multiply(distance));
        } else {
            // Find the furthest safe location
            for (int i = distance; i >= 1; i--) {
                Location check = start.clone().add(direction.clone().multiply(i));
                Block block = check.getBlock();
                Block above = block.getRelative(0, 1, 0);
                Block below = block.getRelative(0, -1, 0);
                
                if (block.getType() == Material.AIR && above.getType() == Material.AIR && below.getType().isSolid()) {
                    target = check;
                    break;
                }
            }
        }
        
        if (target != null) {
            // Keep original yaw and pitch
            target.setYaw(start.getYaw());
            target.setPitch(start.getPitch());
            
            // Spawn particles at start and end
            spawnParticles(start, effects, 1);
            player.teleport(target);
            spawnParticles(target, effects, 1);
        }
    }

    private void executeShield(Player player, Map<String, Object> effects) {
        int absorptionAmount = getIntEffect(effects, "absorption_amount", 8);
        int duration = getIntEffect(effects, "duration", 200);
        
        // Spawn particles
        spawnParticles(player.getLocation(), effects, 2);
        
        // Apply absorption effect
        // Absorption gives 2 absorption hearts per amplifier level (amplifier 0 = 2 hearts, amplifier 1 = 4 hearts, etc.)
        // Config specifies the total absorption amount in half-hearts
        int amplifier = Math.max(0, (absorptionAmount / 4));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, amplifier));
    }

    private void executePoison(Player player, Map<String, Object> effects) {
        Location center = player.getLocation();
        int radius = getIntEffect(effects, "radius", 4);
        int poisonLevel = getIntEffect(effects, "poison_level", 1);
        int poisonDuration = getIntEffect(effects, "poison_duration", 100);
        
        // Spawn particles
        spawnParticles(center, effects, radius);
        
        // Apply poison to nearby entities
        // Note: Potion amplifiers are 0-indexed (level 0 = level I, level 1 = level II)
        // Config uses 1-indexed values for user-friendliness
        int poisonAmplifier = Math.max(0, poisonLevel - 1);
        Collection<Entity> nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius);
        for (Entity entity : nearby) {
            if (entity instanceof LivingEntity && !entity.equals(player)) {
                LivingEntity living = (LivingEntity) entity;
                living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonDuration, poisonAmplifier));
            }
        }
    }

    private void spawnParticles(Location center, Map<String, Object> effects, int radius) {
        String particleName = (String) effects.get("particle");
        if (particleName == null) {
            return;
        }
        try {
            Particle particle = Particle.valueOf(particleName);
            // Spawn particles in a circle
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                double angle = 2 * Math.PI * i / PARTICLE_COUNT;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                Location particleLoc = center.clone().add(x, 0.5, z);
                center.getWorld().spawnParticle(particle, particleLoc, 3, 0.2, 0.2, 0.2, 0);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private int getIntEffect(Map<String, Object> effects, String key, int defaultValue) {
        Object value = effects.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private double getDoubleEffect(Map<String, Object> effects, String key, double defaultValue) {
        Object value = effects.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private boolean getBooleanEffect(Map<String, Object> effects, String key, boolean defaultValue) {
        Object value = effects.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    private long getCooldownRemaining(Player player, String skillId) {
        UUID uuid = player.getUniqueId();
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) {
            return 0;
        }
        Long expiry = playerCooldowns.get(skillId);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? remaining : 0;
    }

    private void setCooldown(Player player, String skillId, long durationMs) {
        UUID uuid = player.getUniqueId();
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) {
            playerCooldowns = new HashMap<String, Long>();
            cooldowns.put(uuid, playerCooldowns);
        }
        playerCooldowns.put(skillId, System.currentTimeMillis() + durationMs);
    }

    /**
     * Get list of all skill IDs
     */
    public List<String> getSkillIds() {
        return new ArrayList<String>(skills.keySet());
    }

    /**
     * Get a skill by ID
     */
    public SkillDefinition getSkill(String skillId) {
        return skills.get(skillId);
    }

    /**
     * Check if skill system is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    private static String translateColorCodes(String text) {
        if (text == null) {
            return null;
        }
        // Handle &#RRGGBB hex colors
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (i + 8 <= text.length() && text.charAt(i) == '&' && text.charAt(i + 1) == '#') {
                String hex = text.substring(i + 2, i + 8);
                if (hex.matches("[0-9A-Fa-f]{6}")) {
                    result.append("§x");
                    for (char c : hex.toCharArray()) {
                        result.append("§").append(Character.toLowerCase(c));
                    }
                    i += 8;
                    continue;
                }
            }
            if (text.charAt(i) == '&' && i + 1 < text.length()) {
                result.append(ChatColor.COLOR_CHAR).append(text.charAt(i + 1));
                i += 2;
            } else {
                result.append(text.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    // ========== Inner Classes ==========

    /**
     * Skill definition from config
     */
    public static class SkillDefinition {
        private final String id;
        private final String name;
        private final String description;
        private final String type;
        private final String trigger;
        private final String loreRequirement;
        private final String permission;
        private final int cooldown;
        private final Map<String, Object> effects;

        public SkillDefinition(String id, String name, String description, String type,
                               String trigger, String loreRequirement, String permission,
                               int cooldown, Map<String, Object> effects) {
            this.id = id;
            this.name = translateColorCodes(name);
            this.description = description;
            this.type = type;
            this.trigger = trigger;
            this.loreRequirement = loreRequirement;
            this.permission = permission;
            this.cooldown = cooldown;
            this.effects = effects;
        }

        public static SkillDefinition fromSection(String id, ConfigurationSection section, int defaultCooldown) {
            if (section == null) {
                return null;
            }
            String name = section.getString("name", id);
            String description = section.getString("description", "");
            String type = section.getString("type", "lightning");
            String trigger = section.getString("trigger", "right_click");
            String loreRequirement = section.getString("lore_requirement", "");
            String permission = section.getString("permission", "");
            int cooldown = section.getInt("cooldown", defaultCooldown);
            
            Map<String, Object> effects = new HashMap<String, Object>();
            ConfigurationSection effectsSection = section.getConfigurationSection("effects");
            if (effectsSection != null) {
                for (String key : effectsSection.getKeys(false)) {
                    effects.put(key, effectsSection.get(key));
                }
            }
            
            return new SkillDefinition(id, name, description, type, trigger, loreRequirement, permission, cooldown, effects);
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getType() {
            return type;
        }

        public String getTrigger() {
            return trigger;
        }

        public String getLoreRequirement() {
            return loreRequirement;
        }

        public String getPermission() {
            return permission;
        }

        public int getCooldown() {
            return cooldown;
        }

        public Map<String, Object> getEffects() {
            return effects;
        }
    }
}
