package us.magmamc.magmadungeons.tasks;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.World;
import org.bukkit.util.Vector;
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
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.potion.PotionEffect;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.Color;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class DungeonSpawnRunnable extends BukkitRunnable {

    private final DungeonManager dungeonManager;
    private final Main plugin;
    private final Random random = new Random();
    private static final long DESPAWN_DELAY_MS = 20000L;
    private static final double MIN_DISTANCE_SQ = 16.0 * 16.0;

    // Constantes de centrado y atributos cacheados
    private static final double MAX_DISTANCE_THRESHOLD = 25.0;
    private static final double CENTERING_FORCE = 0.5;
    private static final Attribute MAX_HEALTH_ATTRIBUTE = Attribute.valueOf("GENERIC_MAX_HEALTH");
    private static final Attribute ATTACK_DAMAGE_ATTRIBUTE = Attribute.valueOf("GENERIC_ATTACK_DAMAGE");

    public DungeonSpawnRunnable(DungeonManager dungeonManager, Main plugin) {
        this.dungeonManager = dungeonManager;
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long currentTime = System.currentTimeMillis();
        Map<UUID, Long> exitTimes = dungeonManager.getLastPlayerExitTime();

        final org.bukkit.scheduler.BukkitScheduler scheduler = plugin.getServer().getScheduler();

        for (DungeonInstance dungeon : dungeonManager.getActiveDungeons()) {
            DungeonPreset preset = dungeonManager.getPresetManager().getPreset(dungeon.getPresetId());

            if (preset == null) continue;

            boolean playerPresent = isPlayerPresent(dungeon);

            final UUID dungeonId = dungeon.getId();

            if (playerPresent) {
                exitTimes.remove(dungeonId);

                // Chequeo de centrado (SYNC)
                scheduler.runTask(plugin, () -> {
                    checkAndCenterMobs(dungeon);
                });

                // >>> LÓGICA DE CONTROL DE CAPACIDAD ESTRICTO (SYNC) <<<
                scheduler.runTask(plugin, () -> {
                    List<LivingEntity> activeMobs = getActiveDungeonMobs(dungeon);
                    int maxMobs = preset.getMaxMobsInRange();
                    int excessMobs = activeMobs.size() - maxMobs;

                    // 1. Eliminar mobs excedentes
                    if (excessMobs > 0) {
                        for (int i = 0; i < excessMobs; i++) {
                            LivingEntity mobToRemove = activeMobs.get(i);
                            if (dungeonManager.getMobHealthDisplays().containsKey(mobToRemove.getUniqueId())) {
                                dungeonManager.getMobHealthDisplays().remove(mobToRemove.getUniqueId()).remove();
                            }
                            mobToRemove.remove();
                            plugin.getLogger().warning("Eliminado mob excedente en dungeon " + dungeonId.toString().substring(0, 8) + " para mantener el límite de " + maxMobs + ".");
                        }
                    }

                    // 2. Spawnear un nuevo mob si hay espacio
                    if (activeMobs.size() <= maxMobs) {
                        final Location spawnLoc = getSafeSpawnLocation(dungeon);
                        if (spawnLoc != null) {
                            spawnCustomMobSynchronous(dungeon, preset, spawnLoc);
                        }
                    }
                });
                // <<< FIN LÓGICA DE CONTROL DE CAPACIDAD ESTRICTO >>>

            } else {
                if (!exitTimes.containsKey(dungeonId)) {
                    exitTimes.put(dungeonId, currentTime);
                } else {
                    long exitTime = exitTimes.get(dungeonId);

                    if (currentTime >= exitTime + DESPAWN_DELAY_MS) {
                        scheduler.runTask(plugin, () -> {
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


    private Location getSafeSpawnLocation(DungeonInstance dungeon) {
        DungeonPreset preset = dungeonManager.getPresetManager().getPreset(dungeon.getPresetId());
        if (preset == null) return null;
        List<String> blacklist = preset.getBlockBlacklist();

        Location minLoc = dungeon.getMin();
        Location maxLoc = dungeon.getMax();
        World world = dungeon.getWorld();

        // LÍMITES DE ALTURA Y DISTRIBUCIÓN
        final double SPAWN_Y_MIN = 123.0;
        final double SPAWN_Y_MAX = 135.0;
        final double SPAWN_RADIUS = 25.0;

        double effectiveMaxY = Math.min(maxLoc.getY(), SPAWN_Y_MAX);
        double effectiveMinY = Math.max(minLoc.getY(), SPAWN_Y_MIN);

        if (effectiveMaxY < effectiveMinY) {
            return null;
        }

        // Calcular el centro XZ para la distribución polar
        double centerX = (minLoc.getX() + maxLoc.getX()) / 2.0;
        double centerZ = (minLoc.getZ() + maxLoc.getZ()) / 2.0;


        Set<Player> playersInDungeon = world.getPlayers().stream()
                .filter(p -> dungeon.isLocationInside(p.getLocation()))
                .collect(Collectors.toSet());

        // OPTIMIZACIÓN: Reutilizar objetos mutables
        Location playerLocXZ = new Location(world, 0, 0, 0);
        Location checkLoc = new Location(world, 0, 0, 0);
        Location searchLoc = new Location(world, 0, 0, 0);
        Location blockBelowLoc = new Location(world, 0, 0, 0);
        Location blockAboveLoc = new Location(world, 0, 0, 0);

        for (int i = 0; i < 10; i++) {
            // LÓGICA DE DISTRIBUCIÓN UNIFORME EN EL CÍRCULO (POLAR)
            double angle = 2 * Math.PI * random.nextDouble();
            double radius = SPAWN_RADIUS * Math.sqrt(random.nextDouble());

            double x = centerX + radius * Math.cos(angle);
            double z = centerZ + radius * Math.sin(angle);

            checkLoc.setX(x);
            checkLoc.setY(0);
            checkLoc.setZ(z);

            boolean tooClose = false;
            for (Player player : playersInDungeon) {
                Location currentPLoc = player.getLocation();

                playerLocXZ.setX(currentPLoc.getX());
                playerLocXZ.setY(0);
                playerLocXZ.setZ(currentPLoc.getZ());

                if (playerLocXZ.distanceSquared(checkLoc) < MIN_DISTANCE_SQ) {
                    tooClose = true;
                    break;
                }
            }

            if (tooClose) {
                continue;
            }

            // Inicia la búsqueda desde el Y más alto.
            double startY = Math.floor(effectiveMaxY);

            searchLoc.setX(x);
            searchLoc.setY(startY);
            searchLoc.setZ(z);

            // La Y mínima sigue siendo el límite inferior.
            while (searchLoc.getBlockY() >= effectiveMinY) {

                org.bukkit.block.Block currentBlock = searchLoc.getBlock();

                blockBelowLoc.setX(searchLoc.getX());
                blockBelowLoc.setY(searchLoc.getY() - 1);
                blockBelowLoc.setZ(searchLoc.getZ());
                org.bukkit.block.Block blockBelow = blockBelowLoc.getBlock();

                blockAboveLoc.setX(searchLoc.getX());
                blockAboveLoc.setY(searchLoc.getY() + 1);
                blockAboveLoc.setZ(searchLoc.getZ());
                org.bukkit.block.Block blockAbove = blockAboveLoc.getBlock();

                boolean blockBelowIsForbidden = blacklist.contains(blockBelow.getType().name());

                if (!currentBlock.getType().isSolid() &&
                        !blockAbove.getType().isSolid() &&
                        blockBelow.getType().isSolid() &&
                        !blockBelowIsForbidden) {

                    return searchLoc.clone().add(0.5, 0, 0.5);
                }

                searchLoc.subtract(0, 1, 0);
            }
        }

        return null;
    }


    private List<LivingEntity> getActiveDungeonMobs(DungeonInstance dungeon) {
        NamespacedKey dungeonIdKey = dungeonManager.getDungeonIdKey();

        return dungeon.getWorld().getEntities().stream()
                // 1. Asegurar que es una LivingEntity (un mob real)
                .filter(entity -> entity instanceof LivingEntity)
                .map(entity -> (LivingEntity) entity)
                // 2. Asegurar que el mob está vivo y es válido
                .filter(mob -> mob.isValid() && !mob.isDead())
                // 3. Comprobar si tiene el PDC (tag de dungeon)
                .filter(mob -> mob.getPersistentDataContainer().has(dungeonIdKey, PersistentDataType.STRING))
                .filter(mob -> {
                    String id = mob.getPersistentDataContainer().get(dungeonIdKey, PersistentDataType.STRING);
                    // 4. Comprobar que la ID y la ubicación coincidan
                    return id.equals(dungeon.getId().toString()) && dungeon.isLocationInside(mob.getLocation());
                })
                // Recolectar la lista
                .collect(Collectors.toList());
    }

    private void checkAndCenterMobs(DungeonInstance dungeon) {
        NamespacedKey dungeonIdKey = dungeonManager.getDungeonIdKey();

        // Cálculo manual del centro
        Location minLoc = dungeon.getMin();
        Location maxLoc = dungeon.getMax();

        double centerX = (minLoc.getX() + maxLoc.getX()) / 2.0;
        double centerY = (minLoc.getY() + maxLoc.getY()) / 2.0;
        double centerZ = (minLoc.getZ() + maxLoc.getZ()) / 2.0;

        Location center = new Location(dungeon.getWorld(), centerX, centerY, centerZ);

        // Iterar sobre todas las entidades de la dungeon
        dungeon.getWorld().getEntities().stream()
                .filter(entity -> entity instanceof LivingEntity)
                .map(entity -> (LivingEntity) entity)
                .filter(mob -> {
                    String id = mob.getPersistentDataContainer().get(dungeonIdKey, PersistentDataType.STRING);
                    return mob.isValid() && !mob.isDead() && dungeon.getId().toString().equals(id);
                })
                .forEach(mob -> {
                    Location mobLoc = mob.getLocation();

                    Location mobLocXZ = mobLoc.clone();
                    mobLocXZ.setY(center.getY());

                    double distance = mobLocXZ.distance(center);

                    if (distance > MAX_DISTANCE_THRESHOLD) {

                        Vector centerVector = center.toVector();
                        Vector mobVector = mobLocXZ.toVector();

                        Vector direction = centerVector.clone().subtract(mobVector).normalize();

                        mob.setVelocity(mob.getVelocity().add(direction.multiply(CENTERING_FORCE)));
                    }
                });
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
        return getActiveDungeonMobs(dungeon).size();
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

        executeSpawnActions(spawnLoc.clone().add(0.5, 0.5, 0.5), preset);

        TextDisplay healthDisplay = createHealthDisplay(mob, preset);

        mob.addPassenger(healthDisplay);

        dungeonManager.getMobHealthDisplays().put(mob.getUniqueId(), healthDisplay);
    }

    private void applyAttributes(LivingEntity mob, DungeonPreset preset) {
        try {
            if (mob.getAttribute(MAX_HEALTH_ATTRIBUTE) != null) {
                mob.getAttribute(MAX_HEALTH_ATTRIBUTE).setBaseValue(preset.getBaseHealth());
            }
            mob.setHealth(preset.getBaseHealth());

            if (mob.getAttribute(ATTACK_DAMAGE_ATTRIBUTE) != null) {
                double baseDamage = mob.getAttribute(ATTACK_DAMAGE_ATTRIBUTE).getBaseValue();
                mob.getAttribute(ATTACK_DAMAGE_ATTRIBUTE).setBaseValue(
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

    private void executeSpawnActions(Location loc, DungeonPreset preset) {
        for (String action : preset.getSpawnActions()) {
            if (action == null || action.isEmpty()) continue;

            String[] parts = action.trim().split(";");

            if (parts.length == 0) continue;
            String actionType = parts[0].toUpperCase();

            try {
                switch (actionType) {
                    case "[SOUND]":
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

                            if (particle == Particle.DUST) {

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
                                loc.getWorld().spawnParticle(
                                        particle,
                                        loc.getX(), loc.getY(), loc.getZ(),
                                        count,
                                        offsetX, offsetY, offsetZ,
                                        speed
                                );
                            }
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