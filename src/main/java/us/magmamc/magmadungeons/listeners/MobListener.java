package us.magmamc.magmadungeons.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import us.magmamc.magmadungeons.Main;
import us.magmamc.magmadungeons.managers.DungeonManager;
import us.magmamc.magmadungeons.models.DungeonInstance;
import us.magmamc.magmadungeons.models.DungeonPreset;

import org.bukkit.util.Vector;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class MobListener implements Listener {

    private final DungeonManager dungeonManager;
    private final Main plugin = Main.getInstance();
    private final Random random = new Random();
    private final MiniMessage mm = Main.getInstance().getMiniMessage();
    private static final Attribute MAX_HEALTH_ATTRIBUTE = Attribute.valueOf("GENERIC_MAX_HEALTH");
    public MobListener(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;

        new BukkitRunnable() {
            @Override
            public void run() {
                updateHealthDisplays();
            }
        }.runTaskTimer(Main.getInstance(), 1L, 1L);

        new BukkitRunnable() {
            @Override
            public void run() {
                checkMobContainment();
            }
        }.runTaskTimer(Main.getInstance(), 10L, 10L);
    }

    private boolean isInDungeonWorld(Entity entity) {
        return dungeonManager.getDungeonWorldNames().contains(entity.getWorld().getName());
    }

    private void updateHealthDisplays() {
        dungeonManager.getMobHealthDisplays().entrySet().removeIf(entry -> {
            UUID mobUUID = entry.getKey();
            TextDisplay display = entry.getValue();

            Entity mob = Bukkit.getEntity(mobUUID);

            if (!(mob instanceof LivingEntity livingMob) || !livingMob.isValid() || display == null || !display.isValid()) {
                if (display != null) display.remove();
                return true;
            }

            DungeonPreset preset = getPresetFromMob(livingMob);

            if (preset != null) {
                double currentHealth = Math.max(0, livingMob.getHealth());

                // Usando la constante estática
                double maxHealth = livingMob.getAttribute(MAX_HEALTH_ATTRIBUTE) != null
                        ? livingMob.getAttribute(MAX_HEALTH_ATTRIBUTE).getBaseValue()
                        : livingMob.getMaxHealth();

                // MODIFICACIÓN: Uso de MiniMessage
                String nameLineRaw = preset.getName();
                String healthLineRaw = "<!i><red>" + (int)Math.round(currentHealth) +
                        "<gray> / " +
                        (int)Math.round(maxHealth) + "<red>❤";

                Component nameComponent = mm.deserialize(nameLineRaw + "<!i>");
                Component healthComponent = mm.deserialize(healthLineRaw);
                Component newTextComponent = nameComponent.append(Component.newline()).append(healthComponent);

                if (!display.text().equals(newTextComponent)) {
                    display.text(newTextComponent);
                }
            }

            return false;
        });
    }

    private void checkMobContainment() {
        // Obtenemos la acción deseada (KILL o PUSH) una sola vez
        String safetyAction = plugin.getSafetyAction();
        dungeonManager.getMobHealthDisplays().keySet().removeIf(mobUUID -> {
            TextDisplay display = dungeonManager.getMobHealthDisplays().get(mobUUID);
            Entity mob = Bukkit.getEntity(mobUUID);

            if (display == null || !display.isValid() || !(mob instanceof LivingEntity)) return true;

            String dungeonIdString = getDungeonIdFromPDC(mob);
            if (dungeonIdString == null) return true;

            DungeonInstance dungeon = dungeonManager.getActiveDungeons().stream()
                    // El tipo de retorno es DungeonInstance, el casting no es necesario
                    .filter(d -> d.getId().toString().equals(dungeonIdString))
                    .findFirst().orElse(null);

            if (dungeon == null) {
                display.remove();
                mob.remove();
                return true;
            }

            if (mob.getLocation().getBlock().getType() == Material.STRUCTURE_VOID) {

                if ("PUSH".equals(safetyAction)) {
                    // Acción: PUSH (Empujar hacia el centro)

                    Location center = dungeon.getCenter();

                    // Usamos la fuerza de centrado que definimos antes
                    final double CENTERING_FORCE = 1.5; // Aumentamos la fuerza para que el empuje sea notorio y saque al mob

                    Vector centerVector = center.toVector();
                    Vector mobVector = mob.getLocation().toVector();

                    // Calcular el vector de empuje (Mob -> Centro)
                    Vector direction = centerVector.clone().subtract(mobVector).normalize().multiply(CENTERING_FORCE);

                    // Aplicar la fuerza
                    mob.setVelocity(direction);

                    // Retorna false: no remover el mob ni el display de las listas.
                    return false;

                } else {
                    // Acción: KILL (Comportamiento original)
                    mob.remove();
                    mob.getWorld().strikeLightningEffect(mob.getLocation());
                    display.remove();
                    return true; // Remover de la lista de displays
                }
            }

            return false;
        });
    }

    @EventHandler
    public void onMobDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        if (!isInDungeonWorld(mob)) return;

        TextDisplay display = dungeonManager.getMobHealthDisplays().get(mob.getUniqueId());
        if (display == null) return;

        DungeonPreset preset = getPresetFromMob(mob);
        if (preset == null) return;

        // MODIFICACIÓN: Uso de MiniMessage
        String nameLineRaw = preset.getName();
        double damage = event.getFinalDamage();
        double currentHealth = mob.getHealth();
        double maxHealth = mob.getAttribute(MAX_HEALTH_ATTRIBUTE) != null
                ? mob.getAttribute(MAX_HEALTH_ATTRIBUTE).getBaseValue()
                : mob.getMaxHealth();

        double newHealth = Math.max(0, currentHealth - damage);

        String healthLineRaw = "<!i><red>" + (int) Math.round(newHealth) +
                "<!i><gray> / " +
                (int) Math.round(maxHealth) + "<!i><red>❤";

        Component nameComponent = mm.deserialize(nameLineRaw + "<!i>");
        Component healthComponent = mm.deserialize(healthLineRaw);
        Component damagedText = nameComponent.append(Component.newline()).append(healthComponent);

        display.text(damagedText);

        if (newHealth <= 0) {
            String deathHealthRaw = "<dark_red>0<gray> / " + (int) Math.round(maxHealth) + "<red>❤<!i>";
            Component deathHealthComponent = mm.deserialize(deathHealthRaw);
            Component deathText = nameComponent.append(Component.newline()).append(deathHealthComponent);
            display.text(deathText);
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (!isInDungeonWorld(mob)) return;

        if (getDungeonIdFromPDC(mob) == null) return;

        event.setDroppedExp(0);
        event.getDrops().clear();

        DungeonPreset preset = getPresetFromMob(mob);

        Player killer = event.getEntity().getKiller(); // Consistencia en la obtención del killer
        Location deathLoc = mob.getLocation();

        if (preset != null) {
            // >>> OPTIMIZACIÓN 1: Calcular el nombre del asesino una sola vez <<<
            final String playerName = killer != null ? killer.getName() : "console_killer";

            // 1. Recompensas de Comandos
            preset.getCommandRewards().forEach(reward -> {
                if (random.nextInt(100) < reward.percentage) {
                    // La formación de la cadena se hace DENTRO del chequeo de probabilidad
                    final String command = reward.value.replace("%player%", playerName);

                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    });
                }
            });

            // 2. Recompensas de Items
            preset.getItemRewards().forEach(reward -> {
                if (random.nextInt(100) < reward.percentage) {
                    String[] parts = reward.value.trim().split(" ");
                    try {
                        // OPTIMIZACIÓN 2: Usar .toUpperCase() solo en la parte necesaria
                        Material material = Material.valueOf(parts[0].toUpperCase());
                        // Uso eficiente de operadores ternarios
                        int amount = (parts.length > 1) ? Integer.parseInt(parts[1]) : 1;
                        deathLoc.getWorld().dropItemNaturally(deathLoc, new ItemStack(material, amount));
                    }
                    catch (IllegalArgumentException e) {
                        Main.getInstance().getLogger().warning("Item inválido o cantidad mal formateada en el preset " + preset.getId() + ": " + reward.value);
                    }
                }
            });

            // 3. Recompensas de XP
            preset.getXpRewards().forEach(reward -> {
                if (random.nextInt(100) < reward.percentage) {
                    // OPTIMIZACIÓN 3: Convertir a mayúsculas una sola vez y usar trim()
                    String rawValue = reward.value.trim().toUpperCase();

                    if (rawValue.endsWith("L")) {
                        try {
                            // Usamos rawValue.replace("L", "") para obtener el nivel numérico
                            int levels = Integer.parseInt(rawValue.replace("L", ""));
                            if (killer != null) killer.giveExpLevels(levels);
                        } catch (NumberFormatException e) {
                            Main.getInstance().getLogger().warning("Valor Nivel XP inválido: " + reward.value);
                        }
                    } else {
                        try {
                            int xpAmount = Integer.parseInt(rawValue);
                            ExperienceOrb orb = deathLoc.getWorld().spawn(deathLoc, ExperienceOrb.class);
                            orb.setExperience(xpAmount);
                        } catch (NumberFormatException e) {
                            Main.getInstance().getLogger().warning("Valor Puntos XP inválido: " + reward.value);
                        }
                    }
                }
            });
        }

        TextDisplay display = dungeonManager.getMobHealthDisplays().remove(mob.getUniqueId());
        if (display != null) {
            display.remove();
        }
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (!isInDungeonWorld(event.getEntity())) return;

        if (getDungeonIdFromPDC(event.getEntity()) != null) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler
    public void onSlimeSplit(SlimeSplitEvent event) {
        if (!isInDungeonWorld(event.getEntity())) return;

        if (getDungeonIdFromPDC(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    private DungeonPreset getPresetFromMob(Entity mob) {
        String dungeonId = getDungeonIdFromPDC(mob);
        if (dungeonId == null) return null;

        // El tipo de retorno del stream es DungeonInstance, no requiere casting
        DungeonInstance dungeon = dungeonManager.getActiveDungeons().stream()
                .filter(d -> d.getId().toString().equals(dungeonId))
                .findFirst()
                .orElse(null);

        if (dungeon == null) return null;

        return dungeonManager.getPresetManager().getPreset(dungeon.getPresetId());
    }

    private String getDungeonIdFromPDC(Entity entity) {
        NamespacedKey key = dungeonManager.getDungeonIdKey();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.get(key, PersistentDataType.STRING);
    }
}