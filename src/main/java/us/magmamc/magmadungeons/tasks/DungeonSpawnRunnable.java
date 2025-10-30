package us.magmamc.magmadungeons.tasks;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import us.magmamc.magmadungeons.Main;
import us.magmamc.magmadungeons.managers.DungeonManager;
import us.magmamc.magmadungeons.models.DungeonInstance;
import us.magmamc.magmadungeons.models.DungeonPreset; // <-- ¡Línea corregida!
import org.bukkit.Material;
import us.magmamc.magmadungeons.utils.NBTUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.potion.PotionEffect;

public class DungeonSpawnRunnable extends BukkitRunnable {

    private final DungeonManager dungeonManager;
    private final Main plugin;
    private final Random random = new Random();
    private static final long DESPAWN_DELAY_MS = 20000L;
    private static final double MIN_DISTANCE_SQ = 16.0 * 16.0;

    public DungeonSpawnRunnable(DungeonManager dungeonManager, Main plugin) {
        this.dungeonManager = dungeonManager;
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long currentTime = System.currentTimeMillis();
        Map<UUID, Long> exitTimes = dungeonManager.getLastPlayerExitTime();

        for (DungeonInstance dungeon : dungeonManager.getActiveDungeons()) {
            DungeonPreset preset = dungeonManager.getPresetManager().getPreset(dungeon.getPresetId());

            if (preset == null) continue;

            boolean playerPresent = isPlayerPresent(dungeon);

            final UUID dungeonId = dungeon.getId(); // Variable hecha final para la lambda

            if (playerPresent) {
                exitTimes.remove(dungeonId);

                if (countMobsInArea(dungeon) < preset.getMaxMobsInRange()) {
                    final Location spawnLoc = getSafeSpawnLocation(dungeon);

                    if (spawnLoc != null) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            spawnCustomMobSynchronous(dungeon, preset, spawnLoc);
                        });
                    }
                }
            } else {
                if (!exitTimes.containsKey(dungeonId)) {
                    exitTimes.put(dungeonId, currentTime);
                } else {
                    long exitTime = exitTimes.get(dungeonId);

                    if (currentTime >= exitTime + DESPAWN_DELAY_MS) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            int count = dungeonManager.cleanMobs(dungeonId.toString());
                            if (count > 0) {
                                plugin.getLogger().info(String.format("Limpiados %d mobs de dungeon %s por falta de jugadores (Tiempo agotado).", count, dungeonId.toString().substring(0, 8)));
                            }
                        });
                        exitTimes.remove(dungeonId);
                    }
                }
            }
        }
    }

    private boolean isPlayerPresent(DungeonInstance dungeon) {
        for (Player player : dungeon.getWorld().getPlayers()) {
            if (dungeon.isLocationInside(player.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private long countMobsInArea(DungeonInstance dungeon) {
        NamespacedKey dungeonIdKey = dungeonManager.getDungeonIdKey();
        return dungeon.getWorld().getEntities().stream()
                .filter(entity -> entity.getPersistentDataContainer().has(dungeonIdKey, PersistentDataType.STRING))
                .filter(entity -> {
                    String id = entity.getPersistentDataContainer().get(dungeonIdKey, PersistentDataType.STRING);
                    return id.equals(dungeon.getId().toString()) && dungeon.isLocationInside(entity.getLocation());
                })
                .count();
    }

    private void spawnCustomMobSynchronous(DungeonInstance dungeon, DungeonPreset preset, Location spawnLoc) {

        Entity spawnedEntity = dungeon.getWorld().spawnEntity(spawnLoc, preset.getMobType());
        if (!(spawnedEntity instanceof LivingEntity mob)) return;

        mob.setPersistent(true);
        mob.setCanPickupItems(false);
        mob.setCustomNameVisible(false);

        NamespacedKey dungeonIdKey = dungeonManager.getDungeonIdKey();
        mob.getPersistentDataContainer().set(dungeonIdKey, PersistentDataType.STRING, dungeon.getId().toString());

        applyAttributes(mob, preset);
        applyEquipment(mob, preset);
        applyEffects(mob, preset);

        TextDisplay healthDisplay = createHealthDisplay(mob, preset);

        mob.addPassenger(healthDisplay);

        dungeonManager.getMobHealthDisplays().put(mob.getUniqueId(), healthDisplay);
    }

    private void applyAttributes(LivingEntity mob, DungeonPreset preset) {
        try {
            if (mob.getAttribute(Attribute.valueOf("GENERIC_MAX_HEALTH")) != null) {
                mob.getAttribute(Attribute.valueOf("GENERIC_MAX_HEALTH")).setBaseValue(preset.getBaseHealth());
            }
            mob.setHealth(preset.getBaseHealth());

            if (mob.getAttribute(Attribute.valueOf("GENERIC_ATTACK_DAMAGE")) != null) {
                double baseDamage = mob.getAttribute(Attribute.valueOf("GENERIC_ATTACK_DAMAGE")).getBaseValue();
                mob.getAttribute(Attribute.valueOf("GENERIC_ATTACK_DAMAGE")).setBaseValue(
                        baseDamage * preset.getDamageModifier()
                );
            }
        } catch (IllegalArgumentException ignored) {}
    }

    private void applyEffects(LivingEntity mob, DungeonPreset preset) {
        for (DungeonPreset.EffectData effectData : preset.getEffects()) {
            PotionEffect potionEffect = new PotionEffect(
                    effectData.type,
                    effectData.durationTicks,
                    effectData.amplifier,
                    false,
                    true
            );
            mob.addPotionEffect(potionEffect);
        }
    }

    private void applyEquipment(LivingEntity mob, DungeonPreset preset) {
        preset.getEquipment().forEach((slot, itemData) -> {
            ItemStack item = new ItemStack(itemData.material);

            if (item.getType() == Material.PLAYER_HEAD && itemData.customNBT != null) {
                item = NBTUtils.setSkullTexture(item, itemData.customNBT);
            }

            if (itemData.enchants != null) {
                final ItemStack enchantedItem = item;
                itemData.enchants.forEach((enchantName, level) -> {
                    try {
                        enchantedItem.addEnchantment(org.bukkit.enchantments.Enchantment.getByName(enchantName), level);
                    } catch (IllegalArgumentException ignored) {}
                });
                item = enchantedItem;
            }

            org.bukkit.inventory.EquipmentSlot bukkitSlot = slot.toEquipmentSlot();

            if (mob.getEquipment() != null) {
                mob.getEquipment().setItem(bukkitSlot, item);
                mob.getEquipment().setDropChance(bukkitSlot, 0f);
            }
        });
    }

    private TextDisplay createHealthDisplay(LivingEntity mob, DungeonPreset preset) {
        Location displayLoc = mob.getLocation().add(0, mob.getEyeHeight() + 0.3, 0);

        TextDisplay healthDisplay = mob.getWorld().spawn(
                displayLoc,
                TextDisplay.class,
                (display) -> {
                    display.setBillboard(Display.Billboard.CENTER);
                    display.setShadowed(true);
                    display.setPersistent(false);
                    display.setAlignment(TextDisplay.TextAlignment.CENTER);
                    display.setLineWidth(300);
                    display.setBackgroundColor(null);
                }
        );

        double maxHealth = mob.getAttribute(Attribute.valueOf("GENERIC_MAX_HEALTH")).getBaseValue();

        String nameLine = preset.getName();
        String healthLine = ChatColor.RED.toString() + (int)maxHealth + ChatColor.GRAY + " / " + (int)maxHealth + ChatColor.RED + "❤";

        healthDisplay.setText(nameLine + "\n" + healthLine);

        healthDisplay.setMetadata("DungeonDisplay", new FixedMetadataValue(plugin, mob.getUniqueId().toString()));

        return healthDisplay;
    }

    private Location getSafeSpawnLocation(DungeonInstance dungeon) {
        DungeonPreset preset = dungeonManager.getPresetManager().getPreset(dungeon.getPresetId());
        if (preset == null) return null;
        List<String> blacklist = preset.getBlockBlacklist();

        Location min = dungeon.getMin();
        Location max = dungeon.getMax();

        List<Player> playersInDungeon = dungeon.getWorld().getPlayers().stream()
                .filter(p -> dungeon.isLocationInside(p.getLocation()))
                .collect(Collectors.toList());

        for (int i = 0; i < 10; i++) {
            double x = min.getX() + random.nextDouble() * (max.getX() - min.getX());
            double z = min.getZ() + random.nextDouble() * (max.getZ() - min.getZ());

            Location checkLoc = new Location(dungeon.getWorld(), x, 0, z);

            boolean tooClose = false;
            for (Player player : playersInDungeon) {
                Location playerLocXZ = player.getLocation().clone();
                playerLocXZ.setY(0);

                if (playerLocXZ.distanceSquared(checkLoc) < MIN_DISTANCE_SQ) {
                    tooClose = true;
                    break;
                }
            }

            if (tooClose) {
                continue;
            }

            Location searchLoc = new Location(dungeon.getWorld(), x, max.getY(), z);

            while (searchLoc.getBlockY() >= min.getY()) {

                org.bukkit.block.Block currentBlock = searchLoc.getBlock();
                org.bukkit.block.Block blockBelow = searchLoc.clone().subtract(0, 1, 0).getBlock();
                org.bukkit.block.Block blockAbove = searchLoc.clone().add(0, 1, 0).getBlock();

                boolean blockBelowIsForbidden = blacklist.contains(blockBelow.getType().name());

                if (!currentBlock.getType().isSolid() &&
                        !blockAbove.getType().isSolid() &&
                        blockBelow.getType().isSolid() &&
                        !blockBelowIsForbidden) {

                    return searchLoc.add(0.5, 0, 0.5);
                }

                searchLoc.subtract(0, 1, 0);
            }
        }

        return null;
    }
}