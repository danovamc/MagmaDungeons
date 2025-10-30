package us.magmamc.magmadungeons.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import us.magmamc.magmadungeons.Main;
import us.magmamc.magmadungeons.models.DungeonInstance;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import java.util.Arrays;

import java.util.*;
import java.util.stream.Collectors;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import java.util.HashSet;
import java.util.Set;

public class DungeonManager {

    private final Main plugin;
    private final ConfigManager configManager;
    private final PresetManager presetManager;

    private final Map<UUID, TextDisplay> mobHealthDisplays = new HashMap<>();
    private final Map<Player, List<Object>> setupData = new HashMap<>();

    private final Set<DungeonInstance> activeDungeons = new HashSet<>();
    private final Map<UUID, Long> lastPlayerExitTime = new HashMap<>();

    public static final String DUNGEON_ID_KEY = "dungeon_id";
    private final NamespacedKey dungeonIdKey;

    // Campos de estado de Setup
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, String> setupPresets = new HashMap<>();
    private final Set<UUID> viewingPresetGUI = new HashSet<>();

    public DungeonManager(Main plugin, ConfigManager configManager, PresetManager presetManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.presetManager = presetManager;
        this.dungeonIdKey = new NamespacedKey(plugin, DUNGEON_ID_KEY);
        loadDungeons();
    }

    public NamespacedKey getDungeonIdKey() {
        return dungeonIdKey;
    }

    public void loadDungeons() {
        activeDungeons.clear();
        FileConfiguration config = configManager.getDungeonConfig();

        ConfigurationSection section = config.getConfigurationSection("dungeons");
        if (section == null) return;

        for (String idString : section.getKeys(false)) {
            ConfigurationSection dSec = section.getConfigurationSection(idString);
            if (dSec == null) continue;

            try {
                UUID id = UUID.fromString(idString);
                String worldName = dSec.getString("world");
                String presetId = dSec.getString("preset");

                if (Bukkit.getWorld(worldName) == null) {
                    plugin.getLogger().warning("La dungeon ID " + idString + " tiene un mundo inválido (" + worldName + "). Se omitirá.");
                    continue;
                }
                if (!presetManager.presetExists(presetId)) {
                    plugin.getLogger().warning("La dungeon ID " + idString + " usa un preset no existente (" + presetId + "). Se omitirá.");
                    continue;
                }
                Location loc1 = new Location(
                        Bukkit.getWorld(worldName),
                        dSec.getDouble("loc1.x"), dSec.getDouble("loc1.y"), dSec.getDouble("loc1.z")
                );
                Location loc2 = new Location(
                        Bukkit.getWorld(worldName),
                        dSec.getDouble("loc2.x"), dSec.getDouble("loc2.y"), dSec.getDouble("loc2.z")
                );
                DungeonInstance dungeon = new DungeonInstance(id, loc1, loc2, presetId);
                activeDungeons.add(dungeon);

            } catch (Exception e) {
                plugin.getLogger().severe("Error al cargar la dungeon " + idString + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Dungeons cargadas: " + activeDungeons.size());
    }

    public void cleanUpOnDisable() {
        int count = cleanAllMobsAndDisplays();
        plugin.getLogger().info("Limpieza de " + count + " mobs de Dungeon y hologramas completada al apagar.");
    }

    private int cleanAllMobsAndDisplays() {
        int count = 0;
        List<UUID> mobUUIDs = new ArrayList<>(mobHealthDisplays.keySet());

        for (UUID mobId : mobUUIDs) {
            TextDisplay display = mobHealthDisplays.get(mobId);
            if (display != null && !display.isDead()) {
                display.remove();
            }
            Entity mob = Bukkit.getEntity(mobId);
            if (mob != null && !mob.isDead() && mob.getPersistentDataContainer().has(dungeonIdKey, PersistentDataType.STRING)) {
                mob.remove();
                count++;
            }
        }
        mobHealthDisplays.clear();
        return count;
    }

    public int cleanMobs(String target) {
        if (target.equalsIgnoreCase("ALL")) {
            return cleanAllMobsAndDisplays();
        }

        int cleanedCount = 0;
        String targetDungeonId = target;
        List<UUID> mobsToClean = new ArrayList<>(mobHealthDisplays.keySet());

        for (UUID mobId : mobsToClean) {
            Entity mob = Bukkit.getEntity(mobId);

            if (mob != null && mob.getPersistentDataContainer().has(dungeonIdKey, PersistentDataType.STRING)) {
                String dungeonIdString = mob.getPersistentDataContainer().get(dungeonIdKey, PersistentDataType.STRING);

                if (dungeonIdString != null && dungeonIdString.startsWith(targetDungeonId)) {
                    TextDisplay display = mobHealthDisplays.remove(mobId);
                    if (display != null && !display.isDead()) {
                        display.remove();
                    }
                    mob.remove();
                    cleanedCount++;
                }
            }
        }
        return cleanedCount;
    }

    public boolean removeDungeon(String dungeonIdString) {
        Optional<DungeonInstance> dungeonToRemove = activeDungeons.stream()
                .filter(d -> d.getId().toString().startsWith(dungeonIdString))
                .findFirst();

        if (dungeonToRemove.isPresent()) {
            DungeonInstance dungeon = dungeonToRemove.get();
            activeDungeons.remove(dungeon);
            FileConfiguration config = configManager.getDungeonConfig();
            ConfigurationSection dungeonsSection = config.getConfigurationSection("dungeons");

            if (dungeonsSection != null) {
                dungeonsSection.set(dungeon.getId().toString(), null);
                configManager.saveDungeonConfig();
            }
            cleanMobs(dungeon.getId().toString());
            lastPlayerExitTime.remove(dungeon.getId());
            return true;
        }
        return false;
    }

    public void saveDungeons() {
        FileConfiguration config = configManager.getDungeonConfig();
        config.set("dungeons", null);
        ConfigurationSection section = config.createSection("dungeons");

        for (DungeonInstance dungeon : activeDungeons) {
            String path = dungeon.getId().toString();
            section.set(path + ".world", dungeon.getWorld().getName());
            section.set(path + ".preset", dungeon.getPresetId());
            section.set(path + ".loc1.x", dungeon.getMin().getX());
            section.set(path + ".loc1.y", dungeon.getMin().getY());
            section.set(path + ".loc1.z", dungeon.getMin().getZ());
            section.set(path + ".loc2.x", dungeon.getMax().getX());
            section.set(path + ".loc2.y", dungeon.getMax().getY());
            section.set(path + ".loc2.z", dungeon.getMax().getZ());
        }
        configManager.saveDungeonConfig();
    }


    public void startSetupMode(Player player, String presetId) {
        savedInventories.put(player.getUniqueId(), player.getInventory().getContents());
        setupPresets.put(player.getUniqueId(), presetId);
        List<Object> currentData = new ArrayList<>(Arrays.asList(null, null));
        setupData.put(player, currentData);
    }

    public void setSetupLocation(Player player, Location location, int pointIndex) {
        if (!setupData.containsKey(player)) return;
        setupData.get(player).set(pointIndex, location);
    }

    public boolean isInSetupMode(Player player) {
        return setupData.containsKey(player);
    }

    public boolean canFinishSetup(Player player) {
        if (!setupData.containsKey(player)) {
            return false;
        }
        List<Object> data = setupData.get(player);
        return data.get(0) != null && data.get(1) != null;
    }

    // >>> NUEVO MÉTODO PARA GUARDAR LA REGIÓN (Llamado por SetupListener) <<<
    public DungeonInstance saveDungeonRegion(Player player) {
        if (!canFinishSetup(player)) return null;

        String presetId = getSetupPreset(player);
        if (presetId == null) return null;

        List<Object> data = setupData.get(player);
        Location loc1 = (Location) data.get(0);
        Location loc2 = (Location) data.get(1);

        // Obtener mobType (solo para el mensaje de confirmación)
        String mobTypeString = presetManager.getPreset(presetId).getMobType().name();

        DungeonInstance newDungeon = new DungeonInstance(loc1, loc2, presetId);
        activeDungeons.add(newDungeon);
        saveDungeons(); // Guardar en dungeons.yml

        // Mensaje de confirmación de guardado
        Main.getInstance().getAudiences().player(player).sendMessage(
                Main.getInstance().getMiniMessage().deserialize("<gold>[MagmaDungeons] <green>¡Dungeon de tipo " + mobTypeString + " guardada y activada con ID: " + newDungeon.getId().toString().substring(0, 8) + "!")
        );

        return newDungeon;
    }

    // >>> MODIFICADO: endSetupMode AHORA SOLO RESTAURA EL INVENTARIO Y LIMPIA EL ESTADO <<<
    public void endSetupMode(Player player, boolean save) {
        // La lógica de guardado ('save') se maneja AHORA en saveDungeonRegion o SetupListener
        if (!setupData.containsKey(player)) return;

        // Limpiar estados
        setupData.remove(player);
        setupPresets.remove(player.getUniqueId());
        viewingPresetGUI.remove(player.getUniqueId());

        if (savedInventories.containsKey(player.getUniqueId())) {
            player.getInventory().setContents(savedInventories.get(player.getUniqueId()));
            savedInventories.remove(player.getUniqueId());

            // Solo enviar mensaje si NO estábamos guardando (es decir, el jugador usó el botón Salir)
            if (!save) {
                Main.getInstance().getAudiences().player(player).sendMessage(Main.getInstance().getMiniMessage().deserialize("<gold>[MagmaDungeons] <green>Inventario restaurado. Modo setup finalizado."));
            }
        } else {
            player.getInventory().clear();
        }
    }

    public void reloadAllConfigs() {
        this.configManager.reloadDungeonConfigFromDisk();
        this.presetManager.loadPresets();
        this.loadDungeons();
        plugin.getLogger().info("[MagmaDungeons] ¡Recarga completa de Presets y Regiones!");
    }

    // --- GETTERS & STATE CHECKERS ---

    public Map<UUID, TextDisplay> getMobHealthDisplays() {
        return mobHealthDisplays;
    }

    public Set<DungeonInstance> getActiveDungeons() {
        return activeDungeons;
    }

    public Map<UUID, Long> getLastPlayerExitTime() {
        return lastPlayerExitTime;
    }

    public PresetManager getPresetManager() {
        return presetManager;
    }

    public Set<String> getDungeonWorldNames() {
        return activeDungeons.stream()
                .map(d -> d.getWorld().getName())
                .collect(Collectors.toSet());
    }

    public Map<Player, List<Object>> getSetupData() {
        return setupData;
    }

    public String getSetupPreset(Player player) {
        return setupPresets.get(player.getUniqueId());
    }

    public boolean isViewingPresetGUI(Player player) {
        return viewingPresetGUI.contains(player.getUniqueId());
    }

    public void setViewingPresetGUI(Player player, boolean viewing) {
        if (viewing) {
            viewingPresetGUI.add(player.getUniqueId());
        } else {
            viewingPresetGUI.remove(player.getUniqueId());
        }
    }

    public void onPlayerQuit(Player player) {
        setViewingPresetGUI(player, false);
        if (isInSetupMode(player)) {
            endSetupMode(player, false); // No guardar al desconectar
        }
    }
}