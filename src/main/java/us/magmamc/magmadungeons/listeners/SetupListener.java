package us.magmamc.magmadungeons.listeners;

import net.kyori.adventure.text.Component;
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
import us.magmamc.magmadungeons.managers.DungeonManager;

public class SetupListener implements Listener {
    private final DungeonManager dungeonManager;
    private final MiniMessage mm = Main.getInstance().getMiniMessage();
    // Nombre MiniMessage del hacha (debe coincidir con DungeonCommand)
    private static final String SETUP_AXE_DISPLAY_NAME_RAW = "<!i><aqua><bold>Herramienta de Selección de Dungeon";

    public SetupListener(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    // Helper para enviar mensajes con prefijo
    private void sendMessage(Player player, String message) {
        Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<gold>[MagmaDungeons] <yellow>" + message));
    }

    // Helper para enviar mensajes de error
    private void sendError(Player player, String message) {
        Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<red>" + message));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!this.dungeonManager.isInSetupMode(player)) {
            if (this.dungeonManager.getDungeonWorldNames().contains(player.getWorld().getName())) {
                ;
            }
        } else {
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            if (heldItem.getType() != Material.GOLDEN_AXE) {
                ;
            } else {
                ItemMeta meta = heldItem.getItemMeta();

                if (meta != null && meta.hasDisplayName()) {
                    Component actualName = meta.displayName();

                    // Comparamos el texto plano sin tags para una verificación robusta
                    String actualStripped = this.mm.stripTags(this.mm.serialize(actualName));
                    String expectedStrippedName = this.mm.stripTags(SETUP_AXE_DISPLAY_NAME_RAW);

                    if (actualStripped.equals(expectedStrippedName)) {
                        event.setCancelled(true);
                        Block clickedBlock = event.getClickedBlock();
                        if (clickedBlock != null) {
                            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                                this.dungeonManager.setSetupLocation(player, clickedBlock.getLocation(), 0);

                                // MODIFICACIÓN: Uso de sendMessage con tags MiniMessage
                                this.sendMessage(player, "Punto 1 (<aqua>Izquierdo</aqua>) fijado en: <gray>" + clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ());
                                this.sendMessage(player, "Ahora haz clic derecho para fijar el <red>Punto 2</red>.");

                            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                                this.dungeonManager.setSetupLocation(player, clickedBlock.getLocation(), 1);

                                // MODIFICACIÓN: Uso de sendMessage con tags MiniMessage
                                this.sendMessage(player, "Punto 2 (<aqua>Derecho</aqua>) fijado en: <gray>" + clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ());

                                if (this.dungeonManager.canFinishSetup(player)) {
                                    this.sendMessage(player, "<green>¡Región de Dungeon establecida con éxito! Guardando...");
                                    this.dungeonManager.endSetupMode(player, true);
                                    if (player.getInventory().getItemInMainHand().equals(heldItem)) {
                                        player.getInventory().setItemInMainHand((ItemStack)null);
                                        this.sendMessage(player, "<green>Herramienta removida. Configuración completada.");
                                    }
                                } else {
                                    this.sendError(player, "Error: Asegúrate de haber seleccionado ambos puntos (Izquierdo y Derecho).");
                                }
                            }

                        }
                    } else {
                        // MODIFICACIÓN: Uso de sendError
                        this.sendError(player, "Esa no es la herramienta correcta. Usa la que se te proporcionó mediante /md setup.");
                    }
                } else {
                    // MODIFICACIÓN: Uso de sendError
                    this.sendError(player, "Esa no es la herramienta correcta. Usa la que se te proporcionó mediante /md setup.");
                }
            }
        }
    }
}