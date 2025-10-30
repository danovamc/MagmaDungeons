package us.magmamc.magmadungeons.managers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import us.magmamc.magmadungeons.models.DungeonPreset;
import us.magmamc.magmadungeons.models.DungeonPreset.EquipmentSlot;

public class PresetManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, DungeonPreset> presets = new HashMap();
    private static final Pattern REWARD_PATTERN = Pattern.compile("^(.*)\\s(\\d+)%$");

    public PresetManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.loadPresets();
    }

    public void loadPresets() {
        this.presets.clear();
        File spawnerDir = this.configManager.getSpawnerDirectory();
        if (!spawnerDir.exists()) {
            spawnerDir.mkdirs();
        }

        File[] files = spawnerDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            this.plugin.getLogger().warning("No se encontraron archivos de presets. Extrayendo 'default_preset.yml'.");
            this.copyDefaultPreset();
            files = spawnerDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null || files.length == 0) {
                this.plugin.getLogger().severe("No se pudo extraer el preset por defecto. Presets no cargados.");
                return;
            }
        }

        for(File file : files) {
            String id = file.getName().replace(".yml", "").toLowerCase();
            FileConfiguration config = this.configManager.getPresetConfig(id);
            if (config != null) {
                DungeonPreset preset = new DungeonPreset(id);

                try {
                    preset.setMobType(EntityType.valueOf(config.getString("mob_type", "ZOMBIE").toUpperCase()));
                    String rawName = config.getString("name", "Mob de Dungeon");

                    // MODIFICACIÓN: Se almacena el nombre raw de MiniMessage
                    preset.setName(rawName);

                    preset.setBaseHealth(config.getDouble("base_health", (double)20.0F));
                    preset.setDamageModifier(config.getDouble("damage_modifier", (double)1.0F));
                    preset.setMaxMobsInRange(config.getInt("max_mobs", 5));

                    // Lógica para Block Blacklist
                    List<String> rawBlacklist = config.getStringList("block_blacklist");
                    List<String> formattedBlacklist = (List)rawBlacklist.stream().map(String::toUpperCase).collect(Collectors.toList());
                    preset.setBlockBlacklist(formattedBlacklist);

                    // >>> LÓGICA CORREGIDA Y ÚNICA para SPAWN_ACTIONS
                    List<String> rawSpawnActions = config.getStringList("spawn_actions");
                    preset.setSpawnActions(rawSpawnActions != null ? rawSpawnActions : Collections.emptyList());
                    // <<<

                    Map<DungeonPreset.EquipmentSlot, DungeonPreset.ItemData> equipmentMap = this.loadEquipment(config.getConfigurationSection("equipment"), id);
                    preset.setEquipment(equipmentMap);

                    ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
                    if (rewardsSection != null) {
                        preset.setCommandRewards(this.loadRewardList(rewardsSection.getStringList("commands")));
                        preset.setItemRewards(this.loadRewardList(rewardsSection.getStringList("items")));
                        preset.setXpRewards(this.loadRewardList(rewardsSection.getStringList("xp")));
                    } else {
                        preset.setCommandRewards(Collections.emptyList());
                        preset.setItemRewards(Collections.emptyList());
                        preset.setXpRewards(Collections.emptyList());
                    }

                    List<Map<?, ?>> effectsList = config.getMapList("effects");
                    preset.setEffects(this.loadEffects(effectsList, id));
                    this.presets.put(id, preset);
                } catch (IllegalArgumentException e) {
                    this.plugin.getLogger().severe("Error al cargar el preset " + id + ": Tipo de mob o Material inválido. Mensaje: " + e.getMessage());
                }
            }
        }

        this.plugin.getLogger().info("Presets de Dungeons cargados en memoria: " + this.presets.size());
    }

    private void copyDefaultPreset() {
        this.plugin.saveResource("spawners/default_preset.yml", false);
    }

    private List<DungeonPreset.RewardData> loadRewardList(List<String> rawRewards) {
        if (rawRewards != null && !rawRewards.isEmpty()) {
            List<DungeonPreset.RewardData> rewardDataList = new ArrayList();

            for(String rawReward : rawRewards) {
                String trimmedReward = rawReward.trim();
                Matcher matcher = REWARD_PATTERN.matcher(trimmedReward);
                String value = trimmedReward;
                int percentage = 100;
                if (matcher.find()) {
                    String potentialValue = matcher.group(1).trim();
                    String percentString = matcher.group(2);

                    try {
                        int potentialPercentage = Integer.parseInt(percentString);
                        if (potentialPercentage >= 1 && potentialPercentage <= 100) {
                            percentage = potentialPercentage;
                            value = potentialValue;
                        }
                    } catch (NumberFormatException var12) {
                    }
                }

                rewardDataList.add(new DungeonPreset.RewardData(value.isEmpty() ? trimmedReward : value, percentage));
            }

            return rewardDataList;
        } else {
            return Collections.emptyList();
        }
    }

    private List<DungeonPreset.EffectData> loadEffects(List<Map<?, ?>> effectsList, String presetId) {
        if (effectsList != null && !effectsList.isEmpty()) {
            List<DungeonPreset.EffectData> loadedEffects = new ArrayList();

            for(Map<?, ?> effectMap : effectsList) {
                try {
                    String effectName = (String)effectMap.get("effect");
                    PotionEffectType type = PotionEffectType.getByName(effectName.toUpperCase());
                    if (type == null) {
                        this.plugin.getLogger().warning("Preset " + presetId + ": Efecto de poción desconocido: " + effectName);
                    } else {
                        int level = (Integer)effectMap.get("level");
                        int amplifier = Math.max(0, level - 1);
                        int durationTicks;
                        if (effectMap.get("duration") instanceof String && ((String)effectMap.get("duration")).equalsIgnoreCase("infinite")) {
                            durationTicks = Integer.MAX_VALUE;
                        } else {
                            durationTicks = ((Number)effectMap.get("duration")).intValue();
                        }

                        loadedEffects.add(new DungeonPreset.EffectData(type, amplifier, durationTicks));
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Preset " + presetId + ": Error al parsear un efecto: " + e.getMessage());
                }
            }

            return loadedEffects;
        } else {
            return Collections.emptyList();
        }
    }

    private Map<DungeonPreset.EquipmentSlot, DungeonPreset.ItemData> loadEquipment(ConfigurationSection config, String presetId) {
        Map<DungeonPreset.EquipmentSlot, DungeonPreset.ItemData> equipmentMap = new HashMap();
        if (config == null) {
            return equipmentMap;
        } else {
            for(String key : config.getKeys(false)) {
                try {
                    DungeonPreset.EquipmentSlot slot = EquipmentSlot.valueOf(key.toUpperCase());
                    String itemString = config.getString(key);
                    if (itemString != null && !itemString.isEmpty()) {
                        String[] parts = itemString.split(";");
                        Material material = Material.valueOf(parts[0].toUpperCase());
                        String customNBT = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
                        Map<String, Integer> enchants = new HashMap();
                        if (parts.length > 2 && !parts[2].isEmpty()) {
                            String[] enchantPairs = parts[2].split(",");

                            for(String pair : enchantPairs) {
                                String[] data = pair.split(":");
                                if (data.length == 2) {
                                    try {
                                        String enchantName = data[0].toUpperCase();
                                        int level = Integer.parseInt(data[1]);
                                        enchants.put(enchantName, level);
                                    } catch (NumberFormatException var20) {
                                        this.plugin.getLogger().warning("Preset " + presetId + ": Nivel de encantamiento inválido en: " + pair);
                                    }
                                }
                            }
                        }

                        equipmentMap.put(slot, new DungeonPreset.ItemData(material, customNBT, enchants));
                    }
                } catch (IllegalArgumentException e) {
                    this.plugin.getLogger().warning("Preset " + presetId + ": Slot o Material inválido en la clave '" + key + "'. Mensaje: " + e.getMessage());
                }
            }

            return equipmentMap;
        }
    }

    public DungeonPreset getPreset(String id) {
        return (DungeonPreset)this.presets.get(id.toLowerCase());
    }

    public boolean presetExists(String id) {
        return this.presets.containsKey(id.toLowerCase());
    }

    public Set<String> getAvailablePresets() {
        return this.presets.keySet();
    }
}