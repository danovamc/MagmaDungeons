package us.magmamc.magmadungeons.guis;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import us.magmamc.magmadungeons.Main;
import us.magmamc.magmadungeons.managers.DungeonManager;
import us.magmamc.magmadungeons.models.DungeonPreset;
import us.magmamc.magmadungeons.utils.NBTUtils;

public class EditPresetGUI implements Listener {
    private final Main plugin;
    private final DungeonManager dungeonManager;
    private final Map<Inventory, String> activeInventories = new HashMap();
    private final MiniMessage mm = Main.getInstance().getMiniMessage();
    private static final int HEAD_SLOT = 1;
    private static final int CHEST_SLOT = 10;
    private static final int LEGS_SLOT = 19;
    private static final int FEET_SLOT = 28;
    private static final int HAND_SLOT = 13;
    private static final int OFF_HAND_SLOT = 22;
    private static final int SAVE_SLOT = 49;

    public EditPresetGUI(Main plugin, DungeonManager dungeonManager) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
    }

    private Inventory createInventory(String presetId) {
        DungeonPreset preset = this.dungeonManager.getPresetManager().getPreset(presetId);

        // MODIFICACIÓN: Título de inventario con MiniMessage y fix de itálica
        Component title = this.mm.deserialize("<!i><dark_gray><u>Editando:</u> " + presetId);
        Inventory inventory = Bukkit.createInventory((InventoryHolder)null, 54, title);

        // MODIFICACIÓN: Ítem de relleno con MiniMessage
        ItemStack filler = this.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", new String[]{});

        for(int i = 0; i < inventory.getSize(); ++i) {
            inventory.setItem(i, filler);
        }

        this.loadEquipment(preset, inventory);

        // MODIFICACIÓN: Botón de guardar con MiniMessage
        ItemStack saveButton = this.createItem(
                Material.EMERALD,
                "<!i><green><bold>GUARDAR CAMBIOS",
                new String[]{
                        "<!i><gray>Haz clic para guardar el equipo.",
                        "<!i><gray>Los cambios se aplicarán al instante."
                }
        );
        inventory.setItem(49, saveButton);
        return inventory;
    }

    private void loadEquipment(DungeonPreset preset, Inventory inventory) {
        if (preset != null) {
            preset.getEquipment().forEach((slot, itemData) -> {
                ItemStack item = new ItemStack(itemData.material);
                ItemMeta meta = item.getItemMeta();
                if (item.getType() == Material.PLAYER_HEAD && itemData.customNBT != null) {
                    item = NBTUtils.setSkullTexture(item, itemData.customNBT);
                }

                if (itemData.enchants != null && meta != null) {
                    itemData.enchants.forEach((enchantName, level) -> {
                        Enchantment enchant = Enchantment.getByName(enchantName);
                        if (enchant != null) {
                            meta.addEnchant(enchant, level, true);
                        } else {
                            this.plugin.getLogger().warning("Encantamiento desconocido: " + enchantName + " en preset " + preset.getId());
                        }

                    });
                    item.setItemMeta(meta);
                }

                int slotIndex = this.getSlotIndex(slot);
                if (slotIndex != -1) {
                    inventory.setItem(slotIndex, item);
                }

            });
        }
    }

    public void open(Player player, String presetId) {
        Inventory inventory = this.createInventory(presetId);
        this.activeInventories.put(inventory, presetId);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = event.getInventory();
        if (this.activeInventories.containsKey(clickedInventory)) {
            Player player = (Player)event.getWhoClicked();
            if (event.getRawSlot() == 49) {
                event.setCancelled(true);
                String presetId = (String)this.activeInventories.get(clickedInventory);
                this.saveEquipmentToPreset(player, clickedInventory, presetId);
                player.closeInventory();
            } else if (this.isEquipmentSlot(event.getRawSlot())) {
                event.setCancelled(false);
            } else if (event.getClickedInventory().equals(player.getInventory())) {
                event.setCancelled(false);
            } else {
                event.setCancelled(true);
            }

        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        this.activeInventories.remove(event.getInventory());
    }

    private boolean isEquipmentSlot(int slot) {
        List<Integer> slots = Arrays.asList(1, 10, 19, 28, 13, 22);
        return slots.contains(slot);
    }

    private int getSlotIndex(DungeonPreset.EquipmentSlot slot) {
        byte var10000;
        switch (slot) {
            case HEAD -> var10000 = 1;
            case CHEST -> var10000 = 10;
            case LEGS -> var10000 = 19;
            case FEET -> var10000 = 28;
            case HAND -> var10000 = 13;
            case OFF_HAND -> var10000 = 22;
            default -> throw new MatchException((String)null, (Throwable)null);
        }

        return var10000;
    }

    private void saveEquipmentToPreset(Player player, Inventory inventory, String presetId) {
        File presetFile = new File(String.valueOf(this.plugin.getDataFolder()) + "/spawners", presetId + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(presetFile);
        if (config == null) {
            // MODIFICACIÓN: Uso de MiniMessage
            Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<red>Error: No se pudo cargar el archivo del preset."));
        } else {
            this.saveItemToConfig(inventory.getItem(1), config, "equipment.head");
            this.saveItemToConfig(inventory.getItem(10), config, "equipment.chest");
            this.saveItemToConfig(inventory.getItem(19), config, "equipment.legs");
            this.saveItemToConfig(inventory.getItem(28), config, "equipment.feet");
            this.saveItemToConfig(inventory.getItem(13), config, "equipment.hand");
            this.saveItemToConfig(inventory.getItem(22), config, "equipment.off_hand");

            try {
                config.save(presetFile);
                this.dungeonManager.getPresetManager().loadPresets();
                // MODIFICACIÓN: Uso de MiniMessage
                Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<green>Equipo del preset '<aqua>" + presetId + "</aqua>' guardado y actualizado."));
            } catch (IOException e) {
                this.plugin.getLogger().log(Level.SEVERE, "No se pudo guardar el preset: " + presetId, e);
                // MODIFICACIÓN: Uso de MiniMessage
                Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<red>Error al guardar el preset. Revisa la consola."));
            }

        }
    }

    private void saveItemToConfig(ItemStack item, FileConfiguration config, String path) {
        if (item != null && item.getType() != Material.AIR && !item.getType().name().contains("GLASS_PANE")) {
            String itemData = item.getType().name();
            if (item.getType() == Material.PLAYER_HEAD) {
                String textureValue = NBTUtils.getSkullTexture(item);
                if (textureValue != null) {
                    itemData = itemData + ";" + textureValue;
                } else {
                    itemData = itemData + ";";
                }
            } else {
                itemData = itemData + ";";
            }

            if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                String enchantString = (String)item.getItemMeta().getEnchants().entrySet().stream().map((entry) -> {
                    String var10000 = ((Enchantment)entry.getKey()).getName();
                    return var10000 + ":" + String.valueOf(entry.getValue());
                }).collect(Collectors.joining(","));
                itemData = itemData + ";" + enchantString;
            } else {
                itemData = itemData + ";";
            }

            config.set(path, itemData);
        } else {
            config.set(path, (Object)null);
        }
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // MODIFICACIÓN: Usar MiniMessage para el nombre.
            meta.displayName(this.mm.deserialize(name));

            // MODIFICACIÓN: Usar MiniMessage para cada línea de lore.
            List<Component> loreComponents = Arrays.stream(lore)
                    .map(this.mm::deserialize)
                    .collect(Collectors.toList());

            meta.lore(loreComponents);

            item.setItemMeta(meta);
        }

        return item;
    }
}