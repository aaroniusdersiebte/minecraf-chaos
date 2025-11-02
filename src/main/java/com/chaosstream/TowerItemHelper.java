package com.chaosstream;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class TowerItemHelper {
    private static final String TOWER_TYPE_KEY = "TowerType";
    private static final String TOWER_ITEM_KEY = "TowerItem";

    /**
     * Create a tower item for the given type
     */
    public static ItemStack createTowerItem(TowerType type) {
        ItemStack item;

        // Use different items for different tower types
        switch (type) {
            case ARCHER:
                item = new ItemStack(Items.BOW);
                break;
            case CANNON:
                item = new ItemStack(Items.TNT);
                break;
            default:
                item = new ItemStack(Items.STONE_BRICKS);
        }

        // Add NBT data
        NbtCompound nbt = item.getOrCreateNbt();
        nbt.putString(TOWER_TYPE_KEY, type.getId());
        nbt.putBoolean(TOWER_ITEM_KEY, true);

        // Set custom name and lore
        item.setCustomName(Text.literal(type.getDisplayName() + " Kit")
                .formatted(Formatting.GOLD, Formatting.BOLD));

        // Note: Lore requires a different approach in newer versions
        // For now, just the name will do

        return item;
    }

    /**
     * Check if an item is a tower item
     */
    public static boolean isTowerItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }

        NbtCompound nbt = item.getNbt();
        return nbt != null && nbt.getBoolean(TOWER_ITEM_KEY);
    }

    /**
     * Get the tower type from an item
     */
    public static TowerType getTowerType(ItemStack item) {
        if (!isTowerItem(item)) {
            return null;
        }

        NbtCompound nbt = item.getNbt();
        if (nbt != null && nbt.contains(TOWER_TYPE_KEY)) {
            String typeId = nbt.getString(TOWER_TYPE_KEY);
            return TowerType.fromString(typeId);
        }

        return null;
    }

    /**
     * Get cost description for a tower type
     */
    public static String getCostDescription(TowerType type) {
        ItemStack[] costs = type.getCosts();
        if (costs == null || costs.length == 0) {
            return "Free";
        }

        List<String> costParts = new ArrayList<>();
        for (ItemStack cost : costs) {
            costParts.add(cost.getCount() + "x " + cost.getItem().getName().getString());
        }

        return String.join(", ", costParts);
    }

    /**
     * Get stats description for a tower type
     */
    public static List<String> getStatsDescription(TowerType type) {
        List<String> stats = new ArrayList<>();
        stats.add("Range: " + (int)type.getRange() + " blocks");
        stats.add("Damage: " + type.getDamage());
        stats.add("Attack Speed: " + (type.getAttackCooldown() / 20.0) + "s");

        if (type.hasAoE()) {
            stats.add("AoE Radius: " + (int)type.getAoeRadius() + " blocks");
        }

        return stats;
    }
}
