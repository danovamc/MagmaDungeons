package us.magmamc.magmadungeons.utils;

import de.tr7zw.nbtapi.NBTCompound;
import de.tr7zw.nbtapi.NBTCompoundList;
import de.tr7zw.nbtapi.NBTItem;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class NBTUtils {
    public static ItemStack setSkullTexture(ItemStack skull, String textureValue) {
        if (skull != null && skull.getType() == Material.PLAYER_HEAD && textureValue != null && !textureValue.isEmpty()) {
            NBTItem nbtItem = new NBTItem(skull);
            NBTCompound skullOwner = nbtItem.addCompound("SkullOwner");
            skullOwner.setString("Id", UUID.randomUUID().toString());
            NBTCompound properties = skullOwner.addCompound("Properties");
            NBTCompoundList texturesList = properties.getCompoundList("textures");
            NBTCompound texture = texturesList.addCompound();
            texture.setString("Value", textureValue);
            return nbtItem.getItem();
        } else {
            return skull;
        }
    }

    public static String getSkullTexture(ItemStack skull) {
        if (skull != null && skull.getType() == Material.PLAYER_HEAD) {
            NBTItem nbtItem = new NBTItem(skull);
            NBTCompound skullOwner = nbtItem.getCompound("SkullOwner");
            if (skullOwner == null) {
                return null;
            } else {
                NBTCompound properties = skullOwner.getCompound("Properties");
                if (properties == null) {
                    return null;
                } else {
                    NBTCompoundList texturesList = properties.getCompoundList("textures");
                    if (texturesList.size() > 0) {
                        NBTCompound texture = texturesList.get(0);
                        return texture.getString("Value");
                    } else {
                        return null;
                    }
                }
            }
        } else {
            return null;
        }
    }
}
