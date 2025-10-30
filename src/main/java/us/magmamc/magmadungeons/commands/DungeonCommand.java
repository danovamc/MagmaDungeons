package us.magmamc.magmadungeons.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import us.magmamc.magmadungeons.Main;
import us.magmamc.magmadungeons.managers.DungeonManager;
import us.magmamc.magmadungeons.guis.PresetSelectionGUI; // Importación necesaria

public class DungeonCommand implements CommandExecutor, TabCompleter {
    private final DungeonManager dungeonManager;
    private final MiniMessage mm = Main.getInstance().getMiniMessage();
    private static final List<String> HOSTILE_MOBS = Arrays.asList("ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "ENDERMAN", "BLAZE", "WITHER_SKELETON", "PIGLIN", "SLIME", "MAGMA_CUBE", "GUARDIIN", "VINDICATOR", "EVOKER", "RAVAGER", "GHAST", "WITHER", "ENDER_DRAGON", "IRON_GOLEM", "ENDERMITE", "HUSK", "STRAY");

    public DungeonCommand(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    private void sendMessage(CommandSender sender, String message) {
        Main.getInstance().getAudiences().sender(sender).sendMessage(this.mm.deserialize("<gold>[MagmaDungeons] <yellow>" + message));
    }

    private void sendError(CommandSender sender, String message) {
        Main.getInstance().getAudiences().sender(sender).sendMessage(this.mm.deserialize("<red>" + message));
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            this.sendError(sender, "Solo los jugadores pueden usar este comando.");
            return true;
        } else if (!player.hasPermission("magmadungeons.admin")) {
            this.sendError(player, "No tienes permiso para usar este comando.");
            return true;
        }

        // Check if arguments are present and not "help"
        if (args.length != 0 && !args[0].equalsIgnoreCase("help")) {
            switch (args[0].toLowerCase()) {
                case "setup":
                    // >>> LÓGICA DE SETUP MODIFICADA <<<
                    if (args.length > 1) {
                        this.sendError(player, "Uso: /md setup (abre el selector de presets)");
                        return true;
                    }

                    if (this.dungeonManager.isInSetupMode(player)) {
                        this.sendError(player, "Ya estás en modo setup. Usa el <red>Ítem Salir</red> en tu hotbar.");
                        return true;
                    }

                    // Abre el GUI de selección de presets
                    PresetSelectionGUI.open(player, this.dungeonManager);
                    this.sendMessage(player, "Abriendo selector de presets...");
                    return true;
                // <<< FIN LÓGICA DE SETUP MODIFICADA >>>

                case "edit":
                    this.handleEditCommand(player, args);
                    break;
                case "reload":
                    this.dungeonManager.reloadAllConfigs();
                    this.sendMessage(player, "Configuraciones y dungeons recargadas.");
                    break;
                case "clear":
                    this.handleClearCommand(player, args);
                    break;
                case "remove":
                    this.handleRemoveCommand(player, args);
                    break;
                default:
                    this.sendError(player, "Subcomando desconocido. Usa /md help.");
            }

            return true;
        } else {
            this.sendHelp(player);
            return true;
        }
    }

    private void handleRemoveCommand(Player player, String[] args) {
        if (args.length < 2) {
            this.sendError(player, "Uso: /md remove <id_dungeon>");
        } else {
            String targetId = args[1];
            if (this.dungeonManager.removeDungeon(targetId)) {
                this.dungeonManager.loadDungeons();
                // MODIFICACIÓN: Uso completo de MiniMessage
                this.sendMessage(player, "<green>Dungeon con ID <aqua>" + targetId.substring(0, 8) + "</aqua><green> eliminada y limpiada correctamente.");
            } else {
                this.sendError(player, "No se encontró ninguna dungeon activa con esa ID parcial.");
            }

        }
    }

    // ELIMINADO: handleSetupCommand (Función reemplazada por PresetSelectionGUI)

    private void handleEditCommand(Player player, String[] args) {
        if (args.length < 2) {
            this.sendError(player, "Uso: /md edit <preset_id>");
        } else {
            String presetId = args[1].toLowerCase();
            if (!this.dungeonManager.getPresetManager().presetExists(presetId)) {
                this.sendError(player, "El preset '" + presetId + "' no existe.");
            } else {
                Main.getInstance().getEditPresetGUI().open(player, presetId);
                // MODIFICACIÓN: Uso completo de MiniMessage
                this.sendMessage(player, "Abriendo editor de equipo para preset: <aqua>" + presetId + "</aqua>");
            }
        }
    }

    private void handleClearCommand(Player player, String[] args) {
        if (args.length < 2) {
            this.sendError(player, "Uso: /md clear <id_dungeon | ALL>");
        } else {
            String target = args[1];
            int cleanedCount = this.dungeonManager.cleanMobs(target);
            if (cleanedCount > 0) {
                // MODIFICACIÓN: Uso completo de MiniMessage
                this.sendMessage(player, "<green>Limpiados " + cleanedCount + " mobs de Dungeon" + (target.equalsIgnoreCase("ALL") ? " en todas las zonas." : " en la zona: <aqua>" + target + "</aqua>."));
            } else if (target.equalsIgnoreCase("ALL")) {
                this.sendMessage(player, "No se encontraron mobs de Dungeon para limpiar.");
            } else {
                this.sendError(player, "No se encontró la zona o la ID no es válida.");
            }

        }
    }

    private void sendHelp(Player player) {
        Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<gray>--- <gold>MagmaDungeons Help<gray> ---"));
        // Modificación de la descripción de /md setup
        Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<yellow>/md setup<gray> - Abre la GUI para seleccionar la zona de dungeon."));
        Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<yellow>/md remove <id><gray> - Elimina una dungeon por su ID."));
        Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<yellow>/md clear <id/ALL><gray> - Limpia mobs de una zona o todas."));
        Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<yellow>/md edit <preset_id><gray> - Abre la GUI para editar equipo y stats."));
        Main.getInstance().getAudiences().player(player).sendMessage(this.mm.deserialize("<yellow>/md reload<gray> - Recarga todas las configuraciones."));
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("setup", "edit", "reload", "clear", "remove"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            // Ya no sugerimos presets en /md setup, pues usa una GUI.
            return completions;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            completions.addAll(this.dungeonManager.getPresetManager().getAvailablePresets());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            completions.add("ALL");
            completions.addAll((Collection)this.dungeonManager.getActiveDungeons().stream().map((d) -> d.getId().toString().substring(0, 8)).collect(Collectors.toList()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            completions.addAll((Collection)this.dungeonManager.getActiveDungeons().stream().map((d) -> d.getId().toString().substring(0, 8)).collect(Collectors.toList()));
        }

        String partialArg = args[args.length - 1].toLowerCase();
        return partialArg.isEmpty() ? completions : (List)completions.stream().filter((s) -> s.toLowerCase().startsWith(partialArg)).collect(Collectors.toList());
    }
}