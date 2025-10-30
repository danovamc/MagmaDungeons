package us.magmamc.magmadungeons.tasks;

import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import us.magmamc.magmadungeons.managers.DungeonManager;

public class SetupVisualizerRunnable extends BukkitRunnable {
    private final DungeonManager dungeonManager;

    public SetupVisualizerRunnable(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    public void run() {
        Map<Player, List<Object>> setupData = this.dungeonManager.getSetupData();
        if (!setupData.isEmpty()) {
            for(Map.Entry<Player, List<Object>> entry : setupData.entrySet()) {
                Player player = (Player)entry.getKey();
                List<Object> data = (List)entry.getValue();
                Location loc1 = (Location)data.get(0);
                Location loc2 = (Location)data.get(1);
                if (loc1 != null || loc2 != null) {
                    this.drawBoundingBox(player, loc1, loc2);
                }
            }

        }
    }

    private void drawBoundingBox(Player player, Location loc1, Location loc2) {
        Location point1 = loc1 != null ? loc1 : player.getLocation().getBlock().getLocation();
        Location point2 = loc2 != null ? loc2 : point1;
        double minX = Math.min(point1.getX(), point2.getX());
        double minY = Math.min(point1.getY(), point2.getY());
        double minZ = Math.min(point1.getZ(), point2.getZ());
        double maxX = Math.max(point1.getX(), point2.getX());
        double maxY = Math.max(point1.getY(), point2.getY());
        double maxZ = Math.max(point1.getZ(), point2.getZ());
        double step = (double)0.5F;
        this.drawLines(player, minX, minY, minZ, maxX, minY, minZ, (double)0.5F);
        this.drawLines(player, minX, maxY, minZ, maxX, maxY, minZ, (double)0.5F);
        this.drawLines(player, minX, minY, maxZ, maxX, minY, maxZ, (double)0.5F);
        this.drawLines(player, minX, maxY, maxZ, maxX, maxY, maxZ, (double)0.5F);
        this.drawLines(player, minX, minY, minZ, minX, minY, maxZ, (double)0.5F);
        this.drawLines(player, maxX, minY, minZ, maxX, minY, maxZ, (double)0.5F);
        this.drawLines(player, minX, maxY, minZ, minX, maxY, maxZ, (double)0.5F);
        this.drawLines(player, maxX, maxY, minZ, maxX, maxY, maxZ, (double)0.5F);
        this.drawLines(player, minX, minY, minZ, minX, maxY, minZ, (double)0.5F);
        this.drawLines(player, maxX, minY, minZ, maxX, maxY, minZ, (double)0.5F);
        this.drawLines(player, minX, minY, maxZ, minX, maxY, maxZ, (double)0.5F);
        this.drawLines(player, maxX, minY, maxZ, maxX, maxY, maxZ, (double)0.5F);
    }

    private void drawLines(Player player, double x1, double y1, double z1, double x2, double y2, double z2, double step) {
        double length = Math.sqrt(Math.pow(x2 - x1, (double)2.0F) + Math.pow(y2 - y1, (double)2.0F) + Math.pow(z2 - z1, (double)2.0F));
        int totalSteps = (int)Math.ceil(length / step);
        if (totalSteps <= 0) {
            player.spawnParticle(Particle.WAX_ON, x1 + (double)0.5F, y1 + (double)0.5F, z1 + (double)0.5F, 1);
        } else {
            double dx = (x2 - x1) / (double)totalSteps;
            double dy = (y2 - y1) / (double)totalSteps;
            double dz = (z2 - z1) / (double)totalSteps;

            for(int i = 0; i <= totalSteps; ++i) {
                double x = x1 + (double)i * dx + (double)0.5F;
                double y = y1 + (double)i * dy + (double)0.5F;
                double z = z1 + (double)i * dz + (double)0.5F;
                player.spawnParticle(Particle.WAX_ON, x, y, z, 1);
            }

        }
    }
}
