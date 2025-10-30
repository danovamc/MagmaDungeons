package us.magmamc.magmadungeons.tasks;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Color;

import us.magmamc.magmadungeons.Main;
import us.magmamc.magmadungeons.managers.DungeonManager;
import us.magmamc.magmadungeons.models.DungeonInstance;
import us.magmamc.magmadungeons.models.DungeonPreset;
import org.bukkit.Material;
import us.magmamc.magmadungeons.utils.NBTUtils;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.potion.PotionEffect;

import org.bukkit.Sound;
import org.bukkit.Particle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

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

        // LLAMADA A LAS ACCIONES DE SPAWNEO: se añade 0.5 a X, Y, Z para centrar los efectos en el bloque
        executeSpawnActions(spawnLoc.clone().add(0.5, 0.5, 0.5), preset);

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

        MiniMessage mm = plugin.getMiniMessage();

        String nameLineRaw = preset.getName();
        String healthLineRaw = "<red>" + (int)maxHealth + "<gray> / " + (int)maxHealth + "<red>❤<!i>";
        Component nameComponent = mm.deserialize(nameLineRaw + "<!i>");
        Component healthComponent = mm.deserialize(healthLineRaw);
        Component combinedText = nameComponent.append(Component.newline()).append(healthComponent);

        healthDisplay.text(combinedText);

        healthDisplay.setMetadata("DungeonDisplay", new FixedMetadataValue(plugin, mob.getUniqueId().toString()));

        return healthDisplay;
    }

    private Location getSafeSpawnLocation(DungeonInstance dungeon) {
        DungeonPreset preset = dungeonManager.getPresetManager().getPreset(dungeon.getPresetId());
        if (preset == null) return null;
        List<String> blacklist = preset.getBlockBlacklist();

        Location min = dungeon.getMin();
        Location max = dungeon.getMax();

        // 1. Definir las restricciones de altura deseadas.
        final double SPAWN_Y_MIN = 123.0;
        final double SPAWN_Y_MAX = 135.0;

        // 2. Calcular el rango efectivo de spawneo (la intersección de su región y la restricción).
        double effectiveMaxY = Math.min(max.getY(), SPAWN_Y_MAX);
        double effectiveMinY = Math.max(min.getY(), SPAWN_Y_MIN);

        // Si no hay superposición, no se puede spawnear.
        if (effectiveMaxY < effectiveMinY) {
            return null;
        }

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

            // 3. La búsqueda vertical comienza desde el máximo Y efectivo.
            Location searchLoc = new Location(dungeon.getWorld(), x, effectiveMaxY, z);

            // 4. La búsqueda vertical se detiene en el mínimo Y efectivo.
            while (searchLoc.getBlockY() >= effectiveMinY) {

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

    private void executeSpawnActions(Location loc, DungeonPreset preset) {
        for (String action : preset.getSpawnActions()) {
            if (action == null || action.isEmpty()) continue;

            String[] parts = action.trim().split(";");

            if (parts.length == 0) continue;
            String actionType = parts[0].toUpperCase();

            try {
                switch (actionType) {
                    case "[SOUND]":
                        // Formato esperado: [SOUND];SOUND_NAME;VOLUME;PITCH
                        if (parts.length >= 2) {
                            String soundName = parts[1];

                            float volume = 1.0f;
                            if (parts.length > 2 && !parts[2].isEmpty()) {
                                volume = Float.parseFloat(parts[2].trim());
                            }

                            float pitch = 1.0f;
                            if (parts.length > 3 && !parts[3].isEmpty()) {
                                pitch = Float.parseFloat(parts[3].trim());
                            }

                            Sound sound = Sound.valueOf(soundName.toUpperCase());
                            loc.getWorld().playSound(loc, sound, volume, pitch);
                        }
                        break;

                    case "[PARTICLE]":
                        // Formato: [PARTICLE];PARTICLE_NAME;COUNT;OFFSET_X,OFFSET_Y,OFFSET_Z;SPEED
                        if (parts.length >= 3) {
                            String particleName = parts[1];
                            int count = Integer.parseInt(parts[2].trim());

                            Particle particle = Particle.valueOf(particleName.toUpperCase());

                            double offsetX = 0.0;
                            double offsetY = 0.0;
                            double offsetZ = 0.0;
                            double speed = 0.0;

                            if (parts.length > 3 && !parts[3].isEmpty()) {
                                String[] offsetParts = parts[3].split(",");
                                if (offsetParts.length == 3) {
                                    offsetX = Double.parseDouble(offsetParts[0].trim());
                                    offsetY = Double.parseDouble(offsetParts[1].trim());
                                    offsetZ = Double.parseDouble(offsetParts[2].trim());
                                }
                            }

                            if (parts.length > 4 && !parts[4].isEmpty()) {
                                speed = Double.parseDouble(parts[4].trim());
                            }

                            // >>> LÓGICA CORREGIDA PARA PARTÍCULAS DE COLOR (DUST) <<<
                            if (particle == Particle.DUST) {

                                // Corrección 1: Usar Color.fromRGB.
                                // Corrección 2: Se usa DUST, por lo que los offsets son R, G, B y speed es el tamaño.
                                Color color = Color.fromRGB(
                                        (int) (offsetX * 255.0),
                                        (int) (offsetY * 255.0),
                                        (int) (offsetZ * 255.0)
                                );

                                float size = (float) speed;
                                if (size < 0.01f) size = 0.01f;

                                Particle.DustOptions dustOptions = new Particle.DustOptions(color, size);

                                loc.getWorld().spawnParticle(
                                        particle,
                                        loc.getX(), loc.getY(), loc.getZ(),
                                        count,
                                        dustOptions
                                );

                            } else {
                                // Para partículas normales (CRIT, EXPLOSION, etc.)
                                loc.getWorld().spawnParticle(
                                        particle,
                                        loc.getX(), loc.getY(), loc.getZ(),
                                        count,
                                        offsetX, offsetY, offsetZ,
                                        speed
                                );
                            }
                            // >>> FIN LÓGICA CORREGIDA <<<
                        }
                        break;

                    case "[LIGHTNING]":
                        loc.getWorld().strikeLightningEffect(loc);
                        break;
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Preset " + preset.getId() + ": Error al ejecutar la acción '" + action + "'. Revisa el formato de sonido/partícula/número. Mensaje: " + e.getMessage());
            }
        }
    }
}