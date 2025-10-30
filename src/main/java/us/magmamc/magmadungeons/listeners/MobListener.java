package us.magmamc.magmadungeons.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class MobListener implements Listener {

    private final DungeonManager dungeonManager;
    private final Main plugin = Main.getInstance();
    private final Random random = new Random();

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

                Attribute maxHealthAttribute = Attribute.valueOf("GENERIC_MAX_HEALTH");
                double maxHealth = livingMob.getAttribute(maxHealthAttribute).getBaseValue();

                String nameLine = preset.getName();
                String healthLine = ChatColor.RED.toString() + (int)Math.round(currentHealth) +
                        ChatColor.GRAY + " / " +
                        (int)Math.round(maxHealth) + ChatColor.RED + "❤";

                String newText = nameLine + "\n" + healthLine;
                if (!display.getText().equals(newText)) {
                    display.setText(newText);
                }
            }

            return false;
        });
    }

    private void checkMobContainment() {
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
                mob.remove();
                mob.getWorld().strikeLightningEffect(mob.getLocation());
                display.remove();
                return true;
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

        String nameLine = preset.getName();
        double damage = event.getFinalDamage();
        double currentHealth = mob.getHealth();

        Attribute maxHealthAttribute = Attribute.valueOf("GENERIC_MAX_HEALTH");
        double maxHealth = mob.getAttribute(maxHealthAttribute) != null
                ? mob.getAttribute(maxHealthAttribute).getBaseValue()
                : mob.getMaxHealth();

        double newHealth = Math.max(0, currentHealth - damage);

        String healthLine = ChatColor.RED.toString() + (int) Math.round(newHealth) +
                ChatColor.GRAY + " / " +
                (int) Math.round(maxHealth) + ChatColor.RED + "❤";

        display.setText(nameLine + "\n" + healthLine);

        if (newHealth <= 0) {
            String deathHealth = ChatColor.DARK_RED.toString() + "0" + ChatColor.GRAY + " / " + (int) Math.round(maxHealth) + ChatColor.RED + "❤";
            display.setText(nameLine + "\n" + deathHealth);
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

        Player killer = mob.getKiller();
        Location deathLoc = mob.getLocation();

        if (preset != null) {
            preset.getCommandRewards().forEach(reward -> {
                if (random.nextInt(100) < reward.percentage) {
                    String playerName = killer != null ? killer.getName() : "console_killer";
                    String command = reward.value.replace("%player%", playerName);

                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    });
                }
            });

            preset.getItemRewards().forEach(reward -> {
                if (random.nextInt(100) < reward.percentage) {
                    String[] parts = reward.value.trim().split(" ");
                    try {
                        Material material = Material.valueOf(parts[0].toUpperCase());
                        int amount = (parts.length > 1) ? Integer.parseInt(parts[1]) : 1;
                        deathLoc.getWorld().dropItemNaturally(deathLoc, new ItemStack(material, amount));
                    }
                    catch (IllegalArgumentException e) {
                        Main.getInstance().getLogger().warning("Item inválido o cantidad mal formateada en el preset " + preset.getId() + ": " + reward.value);
                    }
                }
            });

            preset.getXpRewards().forEach(reward -> {
                if (random.nextInt(100) < reward.percentage) {
                    String value = reward.value.toUpperCase();

                    if (value.endsWith("L")) {
                        try {
                            int levels = Integer.parseInt(value.replace("L", "").trim());
                            if (killer != null) killer.giveExpLevels(levels);
                        } catch (NumberFormatException e) {
                            Main.getInstance().getLogger().warning("Valor Nivel XP inválido: " + reward.value);
                        }
                    } else {
                        try {
                            int xpAmount = Integer.parseInt(value.trim());
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