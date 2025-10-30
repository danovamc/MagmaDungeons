package us.magmamc.magmadungeons.models;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public class DungeonInstance {
    private final UUID id;
    private final World world;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;
    private final String presetId;

    public DungeonInstance(UUID id, Location loc1, Location loc2, String presetId) {
        this.id = id;
        this.world = loc1.getWorld();
        this.minX = Math.min(loc1.getX(), loc2.getX());
        this.minY = Math.min(loc1.getY(), loc2.getY());
        this.minZ = Math.min(loc1.getZ(), loc2.getZ());
        this.maxX = Math.max(loc1.getX(), loc2.getX());
        this.maxY = Math.max(loc1.getY(), loc2.getY());
        this.maxZ = Math.max(loc1.getZ(), loc2.getZ());
        this.presetId = presetId;
    }

    public DungeonInstance(Location loc1, Location loc2, String presetId) {
        this(UUID.randomUUID(), loc1, loc2, presetId);
    }

    public boolean isLocationInside(Location location) {
        if (location != null && location.getWorld().equals(this.world)) {
            double x = location.getX();
            double y = location.getY();
            double z = location.getZ();
            return x >= this.minX && x < this.maxX + (double)1.0F && y >= this.minY && y < this.maxY + (double)1.0F && z >= this.minZ && z < this.maxZ + (double)1.0F;
        } else {
            return false;
        }
    }

    public UUID getId() {
        return this.id;
    }

    public World getWorld() {
        return this.world;
    }

    public String getPresetId() {
        return this.presetId;
    }

    public Location getMin() {
        return new Location(this.world, this.minX, this.minY, this.minZ);
    }

    public Location getMax() {
        return new Location(this.world, this.maxX, this.maxY, this.maxZ);
    }

    public Location getCenter() {
        double centerX = (this.minX + this.maxX) / 2.0;
        double centerY = (this.minY + this.maxY) / 2.0;
        double centerZ = (this.minZ + this.maxZ) / 2.0;
        // Retorna la ubicación central
        return new Location(this.world, centerX, centerY, centerZ);
    }
}
