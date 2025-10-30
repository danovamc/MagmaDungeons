package us.magmamc.magmadungeons.listeners;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta; // Asegurado
import us.magmamc.magmadungeons.Main;
import us.magmamc.magmadungeons.managers.DungeonManager;
import us.magmamc.magmadungeons.guis.PresetSelectionGUI;

import java.util.Arrays;
import java.util.List;

public class SetupGUIListener implements Listener {
    private final DungeonManager dungeonManager;
    private final MiniMessage mm = Main.getInstance().getMiniMessage();

    // Constantes para la identificación de ítems
    private static final String SETUP_AXE_DISPLAY_NAME_RAW = "<!i><aqua><bold>Herramienta de Selección de Dungeon";
    private static final String EXIT_ITEM_NAME_RAW = "<!i><red><bold>SALIR DEL MODO SETUP";
    private static final String CHANGE_PRESET_NAME_RAW = "<!i><yellow><bold>CAMBIAR PRESET";
    private static final String PRESET_ID_LORE_PREFIX = "(preset-id:";

    public SetupGUIListener(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    private void sendMessage(Player player, String message) {
        Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<gold>[MagmaDungeons] <yellow>" + message));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getInventory();
        ItemStack clickedItem = event.getCurrentItem();

        // 1. Lógica del Preset Selection GUI (Usando el Manager)
        if (dungeonManager.isViewingPresetGUI(player)) {
            event.setCancelled(true);

            if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) return;

            List<net.kyori.adventure.text.Component> lore = clickedItem.getItemMeta().lore();
            if (lore == null || lore.isEmpty()) return;

            String presetId = extractPresetIdFromLore(lore);

            if (presetId != null && dungeonManager.getPresetManager().presetExists(presetId)) {
                dungeonManager.setViewingPresetGUI(player, false);
                player.closeInventory();

                dungeonManager.startSetupMode(player, presetId);
                giveSetupHotbar(player, presetId);

                sendMessage(player, "Has seleccionado el preset '<aqua>" + presetId + "</aqua>'.");
                sendMessage(player, "Utiliza el <aqua>Hacha</aqua> para seleccionar los puntos.");
            }
            return;
        }

        // 2. Lógica para el Hotbar de Setup (si está en modo setup)
        if (dungeonManager.isInSetupMode(player)) {

            // Solo nos preocupan los clics en el inventario del jugador.
            if (clickedInventory != null && clickedInventory.equals(player.getInventory())) {

                // Slots de la Hotbar (0-8)
                if (event.getSlot() >= 0 && event.getSlot() <= 8) {
                    event.setCancelled(true); // Cancelar clics en la hotbar de herramientas

                    if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

                    // Comprobación de los botones Salir / Cambiar Preset
                    String itemName = mm.stripTags(mm.serialize(clickedItem.getItemMeta().displayName()));
                    String exitStripped = mm.stripTags(EXIT_ITEM_NAME_RAW);
                    String changeStripped = mm.stripTags(CHANGE_PRESET_NAME_RAW);

                    if (itemName.contains(exitStripped)) {
                        // Acción Salir (restaura inventario)
                        player.closeInventory();
                        dungeonManager.endSetupMode(player, false);

                    } else if (itemName.contains(changeStripped)) {
                        // Acción Cambiar Preset (restaura inventario y abre GUI)
                        player.closeInventory();
                        dungeonManager.endSetupMode(player, false);

                        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                            PresetSelectionGUI.open(player, dungeonManager);
                        });
                    }
                } else {
                    // Clic en el INVENTARIO PRINCIPAL (slots 9-35). PERMITIR.
                    event.setCancelled(false);
                }
            } else {
                // Clic en un INVENTARIO EXTERNO (ej. un cofre). PERMITIR.
                event.setCancelled(false);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            if (dungeonManager.isViewingPresetGUI(player)) {
                dungeonManager.setViewingPresetGUI(player, false);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        dungeonManager.onPlayerQuit(event.getPlayer());
    }

    private String extractPresetIdFromLore(List<net.kyori.adventure.text.Component> lore) {
        String lastLineRaw = mm.serialize(lore.get(lore.size() - 1));
        String presetIdRaw = mm.stripTags(lastLineRaw);

        if (!presetIdRaw.contains(PRESET_ID_LORE_PREFIX)) return null;

        try {
            return presetIdRaw.substring(
                    presetIdRaw.indexOf(PRESET_ID_LORE_PREFIX) + PRESET_ID_LORE_PREFIX.length(),
                    presetIdRaw.indexOf(")")
            ).trim();
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public static void giveSetupHotbar(Player player, String presetId) {
        player.getInventory().clear();
        MiniMessage mm = Main.getInstance().getMiniMessage();

        // 1. Herramienta de Selección (Slot 0)
        ItemStack setupAxe = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta axeMeta = setupAxe.getItemMeta();
        if (axeMeta != null) {
            String mobTypeString = Main.getInstance().getDungeonManager().getPresetManager().getPreset(presetId).getMobType().name();

            // Ocultar atributos de arma/herramienta para una apariencia limpia
            axeMeta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

            axeMeta.displayName(mm.deserialize(SETUP_AXE_DISPLAY_NAME_RAW));
            axeMeta.lore(Arrays.asList(
                    mm.deserialize("<!i><gray>Mob: <yellow>" + mobTypeString),
                    mm.deserialize("<!i><gray>Preset: <yellow>" + presetId),
                    mm.deserialize(""),
                    mm.deserialize("<!i><green>Clic Izquierdo: Posición 1"),
                    mm.deserialize("<!i><green>Clic Derecho: Posición 2 (Guardar)")
            ));
            setupAxe.setItemMeta(axeMeta);
        }
        player.getInventory().setItem(0, setupAxe);

        // 2. Cambiar Preset (Slot 7 - BOOK)
        ItemStack changePreset = new ItemStack(Material.BOOK);
        ItemMeta changeMeta = changePreset.getItemMeta();
        if (changeMeta != null) {
            changeMeta.displayName(mm.deserialize(CHANGE_PRESET_NAME_RAW));
            changePreset.setItemMeta(changeMeta);
        }
        player.getInventory().setItem(7, changePreset);

        // 3. Salir (Slot 8 - BARRIER)
        ItemStack exitItem = new ItemStack(Material.BARRIER);
        ItemMeta exitMeta = exitItem.getItemMeta();
        if (exitMeta != null) {
            exitMeta.displayName(mm.deserialize(EXIT_ITEM_NAME_RAW));
            exitItem.setItemMeta(exitMeta);
        }
        player.getInventory().setItem(8, exitItem);

        player.updateInventory();
    }
}