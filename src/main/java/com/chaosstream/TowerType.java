package com.chaosstream;

import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;

public enum TowerType {
    ARCHER(
        "archer",
        "Archer Tower",
        22.0,           // Range in blocks
        40,             // Attack cooldown in ticks (2 seconds)
        4.0f,           // Damage
        false,          // No AoE
        0.0,            // AoE radius (unused)
        new ItemStack(Items.IRON_INGOT, 10)  // Cost: 10 Iron
    ),
    CANNON(
        "cannon",
        "Cannon Tower",
        18.0,           // Range in blocks (shorter than archer!)
        100,            // Attack cooldown in ticks (5 seconds)
        8.0f,           // Damage
        true,           // Has AoE
        3.0,            // AoE radius in blocks
        new ItemStack(Items.GOLD_INGOT, 5),  // Cost: 5 Gold + 20 Iron
        new ItemStack(Items.IRON_INGOT, 20)
    );

    private final String id;
    private final String displayName;
    private final double range;
    private final int attackCooldown;
    private final float damage;
    private final boolean hasAoE;
    private final double aoeRadius;
    private final ItemStack[] costs;

    TowerType(String id, String displayName, double range, int attackCooldown,
              float damage, boolean hasAoE, double aoeRadius, ItemStack... costs) {
        this.id = id;
        this.displayName = displayName;
        this.range = range;
        this.attackCooldown = attackCooldown;
        this.damage = damage;
        this.hasAoE = hasAoE;
        this.aoeRadius = aoeRadius;
        this.costs = costs;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getRange() {
        return range;
    }

    public int getAttackCooldown() {
        return attackCooldown;
    }

    public float getDamage() {
        return damage;
    }

    public boolean hasAoE() {
        return hasAoE;
    }

    public double getAoeRadius() {
        return aoeRadius;
    }

    public ItemStack[] getCosts() {
        return costs;
    }

    public static TowerType fromString(String id) {
        for (TowerType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
