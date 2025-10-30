package us.magmamc.magmadungeons.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import us.magmamc.magmadungeons.managers.DungeonManager;

public class SetupListener implements Listener {
    private final DungeonManager dungeonManager;
    private final String PREFIX;
    private final String SETUP_AXE_NAME;

    public SetupListener(DungeonManager dungeonManager) {
        String var10001 = String.valueOf(ChatColor.GOLD);
        this.PREFIX = var10001 + "[MagmaDungeons] " + String.valueOf(ChatColor.YELLOW);
        var10001 = String.valueOf(ChatColor.AQUA);
        this.SETUP_AXE_NAME = var10001 + String.valueOf(ChatColor.BOLD) + "Herramienta de Selección de Dungeon";
        this.dungeonManager = dungeonManager;
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
                player.sendMessage(String.valueOf(ChatColor.RED) + "Debes usar la Herramienta de Selección de Dungeon (Hacha de Oro) para seleccionar la región.");
            } else {
                ItemMeta meta = heldItem.getItemMeta();
                if (meta != null && meta.hasDisplayName() && ChatColor.stripColor(meta.getDisplayName()).equals(ChatColor.stripColor(this.SETUP_AXE_NAME))) {
                    event.setCancelled(true);
                    Block clickedBlock = event.getClickedBlock();
                    if (clickedBlock != null) {
                        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                            this.dungeonManager.setSetupLocation(player, clickedBlock.getLocation(), 0);
                            String var10001 = this.PREFIX;
                            player.sendMessage(var10001 + "Punto 1 (" + String.valueOf(ChatColor.AQUA) + "Izquierdo" + String.valueOf(ChatColor.YELLOW) + ") fijado en: " + String.valueOf(ChatColor.GRAY) + clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ());
                            var10001 = this.PREFIX;
                            player.sendMessage(var10001 + "Ahora haz clic derecho para fijar el " + String.valueOf(ChatColor.RED) + "Punto 2" + String.valueOf(ChatColor.YELLOW) + ".");
                        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                            this.dungeonManager.setSetupLocation(player, clickedBlock.getLocation(), 1);
                            String var7 = this.PREFIX;
                            player.sendMessage(var7 + "Punto 2 (" + String.valueOf(ChatColor.AQUA) + "Derecho" + String.valueOf(ChatColor.YELLOW) + ") fijado en: " + String.valueOf(ChatColor.GRAY) + clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ());
                            if (this.dungeonManager.canFinishSetup(player)) {
                                var7 = this.PREFIX;
                                player.sendMessage(var7 + String.valueOf(ChatColor.GREEN) + "¡Región de Dungeon establecida con éxito! Guardando...");
                                this.dungeonManager.endSetupMode(player, true);
                                if (player.getInventory().getItemInMainHand().equals(heldItem)) {
                                    player.getInventory().setItemInMainHand((ItemStack)null);
                                    var7 = this.PREFIX;
                                    player.sendMessage(var7 + String.valueOf(ChatColor.GREEN) + "Herramienta removida. Configuración completada.");
                                }
                            } else {
                                player.sendMessage(String.valueOf(ChatColor.RED) + "Error: Asegúrate de haber seleccionado ambos puntos (Izquierdo y Derecho).");
                            }
                        }

                    }
                } else {
                    player.sendMessage(String.valueOf(ChatColor.RED) + "Esa no es la herramienta correcta. Usa la que se te proporcionó mediante /md setup.");
                }
            }
        }
    }
}
