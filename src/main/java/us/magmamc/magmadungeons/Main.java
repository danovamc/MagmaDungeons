package us.magmamc.magmadungeons;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import us.magmamc.magmadungeons.commands.DungeonCommand;
import us.magmamc.magmadungeons.guis.EditPresetGUI;
import us.magmamc.magmadungeons.listeners.MobListener;
import us.magmamc.magmadungeons.listeners.SetupListener;
import us.magmamc.magmadungeons.managers.ConfigManager;
import us.magmamc.magmadungeons.managers.DungeonManager;
import us.magmamc.magmadungeons.managers.PresetManager;
import us.magmamc.magmadungeons.tasks.DungeonSpawnRunnable;
import us.magmamc.magmadungeons.tasks.SetupVisualizerRunnable;

public class Main extends JavaPlugin {
    private static Main instance;
    private ConfigManager configManager;
    private PresetManager presetManager;
    private DungeonManager dungeonManager;
    private EditPresetGUI editPresetGUI;
    private BukkitAudiences audiences;
    private BukkitTask spawnTask;

    public void onEnable() {
        instance = this;
        this.getLogger().info("MagmaDungeons ha iniciado el proceso de carga.");
        this.configManager = new ConfigManager(this);
        this.presetManager = new PresetManager(this, this.configManager);
        this.dungeonManager = new DungeonManager(this, this.configManager, this.presetManager);
        this.editPresetGUI = new EditPresetGUI(this, this.dungeonManager);
        this.registerCommands();
        this.registerEvents();
        this.audiences = BukkitAudiences.create(this);
        this.spawnTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                (new DungeonSpawnRunnable(this.dungeonManager, this)).run();
            } catch (Exception e) {
                this.getLogger().severe("Error en DungeonSpawnRunnable (async): " + e.getMessage());
                e.printStackTrace();
            }

        }, 0L, 200L);
        (new SetupVisualizerRunnable(this.dungeonManager)).runTaskTimer(this, 5L, 5L);
        this.getLogger().info("MagmaDungeons ha cargado con éxito.");
    }

    public void onDisable() {
        if (this.spawnTask != null) {
            this.spawnTask.cancel();
        }

        Bukkit.getScheduler().cancelTasks(this);
        if (this.audiences != null) {
            this.audiences.close();
        }

        if (this.dungeonManager != null) {
            this.dungeonManager.cleanUpOnDisable();
        }

        this.getLogger().info("MagmaDungeons ha sido desactivado.");
    }

    private void registerCommands() {
        DungeonCommand dungeonCommand = new DungeonCommand(this.dungeonManager);
        this.getCommand("md").setExecutor(dungeonCommand);
        this.getCommand("md").setTabCompleter(dungeonCommand);
    }

    private void registerEvents() {
        this.getServer().getPluginManager().registerEvents(new SetupListener(this.dungeonManager), this);
        this.getServer().getPluginManager().registerEvents(new MobListener(this.dungeonManager), this);
        this.getServer().getPluginManager().registerEvents(this.editPresetGUI, this);
    }

    public static Main getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    public PresetManager getPresetManager() {
        return this.presetManager;
    }

    public DungeonManager getDungeonManager() {
        return this.dungeonManager;
    }

    public EditPresetGUI getEditPresetGUI() {
        return this.editPresetGUI;
    }

    public BukkitAudiences getAudiences() {
        return this.audiences;
    }

    public MiniMessage getMiniMessage() {
        return MiniMessage.miniMessage();
    }
}
