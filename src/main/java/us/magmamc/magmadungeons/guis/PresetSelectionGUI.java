// File: src/main/java/us/magmamc/magmadungeons/guis/PresetSelectionGUI.java

package us.magmamc.magmadungeons.guis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import us.magmamc.magmadungeons.Main;
import us.magmamc.magmadungeons.managers.DungeonManager;
import us.magmamc.magmadungeons.models.DungeonPreset;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PresetSelectionGUI {

    private static final String INVENTORY_TITLE_RAW = "<!i><dark_aqua>--- Seleccionar Preset ---";

    private static ItemStack createPresetItem(DungeonPreset preset) {
        MiniMessage mm = Main.getInstance().getMiniMessage();

        Material iconMaterial;
        try {
            iconMaterial = Material.valueOf(preset.getMobType().name() + "_SPAWN_EGG");
            if (iconMaterial == Material.AIR) throw new IllegalArgumentException();
        } catch (Exception e) {
            iconMaterial = Material.ZOMBIE_SPAWN_EGG;
        }

        ItemStack item = new ItemStack(iconMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(mm.deserialize("<!i><yellow><bold>" + preset.getName()));

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize(""));
        lore.add(mm.deserialize("<!i><gray>Tipo de Mob: <white>" + preset.getMobType().name()));
        lore.add(mm.deserialize("<!i><gray>Vida Base: <red>" + (int) preset.getBaseHealth()));
        lore.add(mm.deserialize(""));
        // Bandera oculta para extraer el ID
        lore.add(mm.deserialize("<!i><dark_gray>(preset-id:" + preset.getId() + ")"));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static void open(Player player, DungeonManager dungeonManager) {
        MiniMessage mm = Main.getInstance().getMiniMessage();

        Component title = mm.deserialize(INVENTORY_TITLE_RAW);
        Inventory inventory = Bukkit.createInventory((InventoryHolder) null, 54, title);

        Set<String> presetIds = dungeonManager.getPresetManager().getAvailablePresets();
        int slot = 0;

        for (String id : presetIds) {
            DungeonPreset preset = dungeonManager.getPresetManager().getPreset(id);
            if (preset != null && slot < 54) {
                inventory.setItem(slot, createPresetItem(preset));
                slot++;
            }
        }

        // >>> CAMBIO CLAVE: Registrar al jugador en el Manager (Patrón MagmaScan) <<<
        dungeonManager.setViewingPresetGUI(player, true);
        player.openInventory(inventory);
    }
}