package us.magmamc.magmadungeons.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

public class DungeonPreset {
    private final String id;
    private EntityType mobType;
    private double baseHealth;
    private double damageModifier;
    private int maxMobsInRange;
    private Map<EquipmentSlot, ItemData> equipment = new HashMap();
    private String name = "Mob de Dungeon";
    private List<String> blockBlacklist = new ArrayList();
    private List<RewardData> commandRewards = new ArrayList();
    private List<RewardData> itemRewards = new ArrayList();
    private List<RewardData> xpRewards = new ArrayList();
    private List<EffectData> effects = new ArrayList();
    private List<String> spawnActions = new ArrayList();

    public DungeonPreset(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public EntityType getMobType() {
        return this.mobType;
    }

    public double getBaseHealth() {
        return this.baseHealth;
    }

    public double getDamageModifier() {
        return this.damageModifier;
    }

    public int getMaxMobsInRange() {
        return this.maxMobsInRange;
    }

    public String getName() {
        return this.name;
    }

    public Map<EquipmentSlot, ItemData> getEquipment() {
        return Collections.unmodifiableMap(this.equipment);
    }

    public List<String> getBlockBlacklist() {
        return Collections.unmodifiableList(this.blockBlacklist);
    }

    public List<RewardData> getCommandRewards() {
        return Collections.unmodifiableList(this.commandRewards);
    }

    public List<RewardData> getItemRewards() {
        return Collections.unmodifiableList(this.itemRewards);
    }

    public List<RewardData> getXpRewards() {
        return Collections.unmodifiableList(this.xpRewards);
    }

    public List<EffectData> getEffects() {
        return Collections.unmodifiableList(this.effects);
    }

    public List<String> getSpawnActions() {
        return Collections.unmodifiableList(this.spawnActions);
    }

    public void setSpawnActions(List<String> spawnActions) {
        this.spawnActions = spawnActions;
    }

    public void setMobType(EntityType mobType) {
        this.mobType = mobType;
    }

    public void setBaseHealth(double baseHealth) {
        this.baseHealth = baseHealth;
    }

    public void setDamageModifier(double damageModifier) {
        this.damageModifier = damageModifier;
    }

    public void setMaxMobsInRange(int maxMobsInRange) {
        this.maxMobsInRange = maxMobsInRange;
    }

    public void setEquipment(Map<EquipmentSlot, ItemData> equipment) {
        this.equipment = equipment;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setBlockBlacklist(List<String> blockBlacklist) {
        this.blockBlacklist = blockBlacklist;
    }

    public void setCommandRewards(List<RewardData> commandRewards) {
        this.commandRewards = commandRewards;
    }

    public void setItemRewards(List<RewardData> itemRewards) {
        this.itemRewards = itemRewards;
    }

    public void setXpRewards(List<RewardData> xpRewards) {
        this.xpRewards = xpRewards;
    }

    public void setEffects(List<EffectData> effects) {
        this.effects = effects;
    }

    public static class ItemData {
        public Material material;
        public String customNBT;
        public Map<String, Integer> enchants;

        public ItemData(Material material, String customNBT, Map<String, Integer> enchants) {
            this.material = material;
            this.customNBT = customNBT != null ? customNBT : "";
            this.enchants = Collections.unmodifiableMap((Map)(enchants != null ? enchants : new HashMap()));
        }
    }

    public static enum EquipmentSlot {
        HEAD,
        CHEST,
        LEGS,
        FEET,
        HAND,
        OFF_HAND;

        public org.bukkit.inventory.EquipmentSlot toEquipmentSlot() {
            return org.bukkit.inventory.EquipmentSlot.valueOf(this.name());
        }
    }

    public static class RewardData {
        public String value;
        public int percentage;

        public RewardData(String value, int percentage) {
            this.value = value;
            this.percentage = percentage;
        }
    }

    public static class EffectData {
        public final PotionEffectType type;
        public final int amplifier;
        public final int durationTicks;

        public EffectData(PotionEffectType type, int amplifier, int durationTicks) {
            this.type = type;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
        }
    }
}
