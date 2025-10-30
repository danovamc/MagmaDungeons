package us.magmamc.magmadungeons.managers;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final File spawnerDirectory;
    private final File dungeonFile;
    private FileConfiguration dungeonConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.spawnerDirectory = new File(plugin.getDataFolder(), "spawners");
        this.dungeonFile = new File(plugin.getDataFolder(), "dungeons.yml");
        this.createDirectories();
        this.loadDungeonConfig();
    }

    private void createDirectories() {
        if (!this.spawnerDirectory.exists()) {
            this.spawnerDirectory.mkdirs();
        }

    }

    private void loadDungeonConfig() {
        if (!this.dungeonFile.exists()) {
            try {
                this.dungeonFile.createNewFile();
            } catch (IOException e) {
                this.plugin.getLogger().log(Level.SEVERE, "No se pudo crear dungeons.yml", e);
            }
        }

        this.dungeonConfig = YamlConfiguration.loadConfiguration(this.dungeonFile);
    }

    public void saveDungeonConfig() {
        try {
            this.dungeonConfig.save(this.dungeonFile);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.SEVERE, "No se pudo guardar dungeons.yml", e);
        }

    }

    public void reloadDungeonConfigFromDisk() {
        this.dungeonConfig = YamlConfiguration.loadConfiguration(this.dungeonFile);
    }

    public FileConfiguration getDungeonConfig() {
        return this.dungeonConfig;
    }

    public FileConfiguration getPresetConfig(String presetId) {
        File presetFile = new File(this.spawnerDirectory, presetId + ".yml");
        return !presetFile.exists() ? null : YamlConfiguration.loadConfiguration(presetFile);
    }

    public File getSpawnerDirectory() {
        return this.spawnerDirectory;
    }

    public File getOrCreatePresetFile(String presetId) throws IOException {
        File presetFile = new File(this.spawnerDirectory, presetId + ".yml");
        if (!presetFile.exists()) {
            presetFile.createNewFile();
            FileConfiguration config = YamlConfiguration.loadConfiguration(presetFile);
            config.set("mob_type", "ZOMBIE");
            config.set("base_health", (double)20.0F);
            config.set("damage_modifier", (double)1.0F);
            config.set("equipment.helmet", (Object)null);
            config.set("equipment.mainhand", (Object)null);
            config.set("rewards.commands", (Object)null);
            config.save(presetFile);
        }

        return presetFile;
    }
}
