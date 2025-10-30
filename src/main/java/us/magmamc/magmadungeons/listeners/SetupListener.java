package us.magmamc.magmadungeons.listeners;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import us.magmamc.magmadungeons.Main;
import us.magmamc.magmadungeons.models.DungeonInstance;
import us.magmamc.magmadungeons.managers.DungeonManager;

public class SetupListener implements Listener {
    private final DungeonManager dungeonManager;
    private final MiniMessage mm = Main.getInstance().getMiniMessage();

    private static final String SETUP_AXE_DISPLAY_NAME_RAW = "<!i><aqua><bold>Herramienta de Selección de Dungeon";

    public SetupListener(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    private void sendMessage(Player player, String message) {
        Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<gold>[MagmaDungeons] <yellow>" + message));
    }

    private void sendError(Player player, String message) {
        Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<red>" + message));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // 1. Si no está en modo setup, no hacemos nada (permitir interacciones normales)
        if (!this.dungeonManager.isInSetupMode(player)) {
            return;
        }

        ItemStack heldItem = event.getItem();

        // 2. Si no lleva el hacha de setup, el evento es libre (puede colocar bloques, etc.)
        if (heldItem == null || heldItem.getType() != Material.GOLDEN_AXE || !heldItem.hasItemMeta()) {
            event.setCancelled(false);
            return;
        }

        // --- PROCESAMIENTO DEL HACHA DE SETUP ---
        ItemMeta meta = heldItem.getItemMeta();

        // Verificamos si es nuestra herramienta especial (no un hacha normal)
        String actualStripped = this.mm.stripTags(this.mm.serialize(meta.displayName()));
        String expectedStrippedName = this.mm.stripTags(SETUP_AXE_DISPLAY_NAME_RAW);

        if (actualStripped.equals(expectedStrippedName)) {
            // Es el hacha especial: Cancelar la interacción (evita romper/colocar con el hacha)
            event.setCancelled(true);
            Block clickedBlock = event.getClickedBlock();

            if (clickedBlock != null) {
                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    this.dungeonManager.setSetupLocation(player, clickedBlock.getLocation(), 0);
                    this.sendMessage(player, "Punto 1 (<aqua>Izquierdo</aqua>) fijado en: <gray>" + clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ());
                    this.sendMessage(player, "Ahora haz clic derecho para fijar el <red>Punto 2</red>.");

                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    this.dungeonManager.setSetupLocation(player, clickedBlock.getLocation(), 1);
                    this.sendMessage(player, "Punto 2 (<aqua>Derecho</aqua>) fijado en: <gray>" + clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ());

                    if (this.dungeonManager.canFinishSetup(player)) {
                        this.sendMessage(player, "<green>¡Región de Dungeon establecida con éxito! Guardando...");

                        // >>> LÓGICA DE GUARDADO (NO SALE, NO RESTAURA INVENTARIO) <<<
                        DungeonInstance newDungeon = dungeonManager.saveDungeonRegion(player);

                        if(newDungeon != null) {
                            this.sendMessage(player, "<green>¡Dungeon " + newDungeon.getId().toString().substring(0, 8) + " guardada y activa!");
                            this.sendMessage(player, "<gray>Los puntos de selección han sido reiniciados.");
                            // NOTA: Los puntos se resetean dentro de saveDungeonRegion en DungeonManager.
                        } else {
                            this.sendError(player, "Error al guardar la región. Asegúrate de haber seleccionado ambos puntos.");
                        }

                    } else {
                        this.sendError(player, "Error: Asegúrate de haber seleccionado ambos puntos (Izquierdo y Derecho).");
                    }
                }
            }
        }
        // Si no es el hacha especial (el ítem tenía meta pero no el nombre), permitir interacción.
        else {
            event.setCancelled(false);
        }
    }
}