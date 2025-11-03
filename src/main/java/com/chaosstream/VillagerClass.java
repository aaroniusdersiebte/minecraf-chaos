package com.chaosstream;

import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Enum für die verschiedenen Defender-Villager-Klassen
 * Jede Klasse hat eigene Stats, Equipment und Verhalten
 */
public enum VillagerClass {
    WARRIOR("Krieger", "§c", 40.0, 4.0, 1.0, true),
    ARCHER("Bogenschütze", "§a", 30.0, 3.0, 1.0, false),
    HEALER("Heiler", "§d", 50.0, 0.0, 0.9, false),
    BUILDER("Baumeister", "§e", 35.0, 2.0, 0.95, false),
    TANK("Tank", "§9", 80.0, 3.0, 0.85, true);

    private final String displayName;
    private final String colorCode;
    private final double baseHealth;
    private final double baseDamage;
    private final double baseSpeed;
    private final boolean isMelee;

    VillagerClass(String displayName, String colorCode, double baseHealth, double baseDamage, double baseSpeed, boolean isMelee) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.baseHealth = baseHealth;
        this.baseDamage = baseDamage;
        this.baseSpeed = baseSpeed;
        this.isMelee = isMelee;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public double getBaseHealth() {
        return baseHealth;
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public double getBaseSpeed() {
        return baseSpeed;
    }

    public boolean isMelee() {
        return isMelee;
    }

    /**
     * Berechnet HP basierend auf Level
     */
    public double getHealthForLevel(int level) {
        double multiplier = 1.0 + (level - 1) * 0.2; // +20% pro Level
        return baseHealth * multiplier;
    }

    /**
     * Berechnet Damage basierend auf Level
     */
    public double getDamageForLevel(int level) {
        return baseDamage + (level - 1); // +1 DMG pro Level
    }

    /**
     * Gibt das Haupt-Equipment für diese Klasse zurück (Waffe)
     */
    public ItemStack getMainWeapon(int level) {
        switch (this) {
            case WARRIOR:
                if (level >= 5) return new ItemStack(Items.NETHERITE_SWORD);
                if (level >= 3) return new ItemStack(Items.DIAMOND_SWORD);
                return new ItemStack(Items.IRON_SWORD);

            case ARCHER:
                ItemStack bow = new ItemStack(Items.BOW);
                if (level >= 2) {
                    bow.addEnchantment(Enchantments.POWER, Math.min(level - 1, 4));
                }
                if (level >= 4) {
                    bow.addEnchantment(Enchantments.PUNCH, 1);
                }
                return bow;

            case HEALER:
                return new ItemStack(Items.GOLDEN_HELMET); // Symbol für Heiler

            case BUILDER:
                return new ItemStack(Items.GOLDEN_PICKAXE); // Symbol für Baumeister

            case TANK:
                ItemStack shield = new ItemStack(Items.SHIELD);
                return shield;

            default:
                return ItemStack.EMPTY;
        }
    }

    /**
     * Gibt die Rüstung für diese Klasse zurück
     */
    public ItemStack[] getArmor(int level) {
        ItemStack[] armor = new ItemStack[4]; // Feet, Legs, Chest, Head

        switch (this) {
            case WARRIOR:
                if (level >= 5) {
                    armor[0] = new ItemStack(Items.NETHERITE_BOOTS);
                    armor[1] = new ItemStack(Items.NETHERITE_LEGGINGS);
                    armor[2] = new ItemStack(Items.NETHERITE_CHESTPLATE);
                    armor[3] = new ItemStack(Items.NETHERITE_HELMET);
                } else if (level >= 3) {
                    armor[0] = new ItemStack(Items.DIAMOND_BOOTS);
                    armor[1] = new ItemStack(Items.DIAMOND_LEGGINGS);
                    armor[2] = new ItemStack(Items.DIAMOND_CHESTPLATE);
                    armor[3] = new ItemStack(Items.DIAMOND_HELMET);
                } else {
                    armor[0] = new ItemStack(Items.IRON_BOOTS);
                    armor[1] = new ItemStack(Items.IRON_LEGGINGS);
                    armor[2] = new ItemStack(Items.IRON_CHESTPLATE);
                    armor[3] = new ItemStack(Items.IRON_HELMET);
                }
                break;

            case ARCHER:
                // Leichte Rüstung für Mobilität
                armor[0] = new ItemStack(Items.LEATHER_BOOTS);
                armor[1] = new ItemStack(Items.LEATHER_LEGGINGS);
                armor[2] = new ItemStack(Items.LEATHER_CHESTPLATE);
                armor[3] = new ItemStack(Items.LEATHER_HELMET);
                break;

            case HEALER:
                // Gold-Helm als Symbol, Rest Leder
                armor[0] = new ItemStack(Items.LEATHER_BOOTS);
                armor[1] = new ItemStack(Items.LEATHER_LEGGINGS);
                armor[2] = new ItemStack(Items.LEATHER_CHESTPLATE);
                armor[3] = new ItemStack(Items.GOLDEN_HELMET);
                break;

            case BUILDER:
                // Leder-Rüstung
                armor[0] = new ItemStack(Items.LEATHER_BOOTS);
                armor[1] = new ItemStack(Items.LEATHER_LEGGINGS);
                armor[2] = new ItemStack(Items.LEATHER_CHESTPLATE);
                armor[3] = new ItemStack(Items.LEATHER_HELMET);
                break;

            case TANK:
                if (level >= 4) {
                    armor[0] = new ItemStack(Items.NETHERITE_BOOTS);
                    armor[1] = new ItemStack(Items.NETHERITE_LEGGINGS);
                    armor[2] = new ItemStack(Items.NETHERITE_CHESTPLATE);
                    armor[3] = new ItemStack(Items.NETHERITE_HELMET);
                } else {
                    armor[0] = new ItemStack(Items.DIAMOND_BOOTS);
                    armor[1] = new ItemStack(Items.DIAMOND_LEGGINGS);
                    armor[2] = new ItemStack(Items.DIAMOND_CHESTPLATE);
                    armor[3] = new ItemStack(Items.DIAMOND_HELMET);
                }

                // Enchantments für Tank-Rüstung
                if (level >= 3) {
                    for (int i = 0; i < 4; i++) {
                        if (armor[i] != null) {
                            armor[i].addEnchantment(Enchantments.PROTECTION, Math.min(level - 2, 3));
                        }
                    }
                }
                break;
        }

        return armor;
    }

    /**
     * Gibt an ob diese Klasse Range-Angriffe nutzt
     */
    public boolean isRanged() {
        return this == ARCHER;
    }

    /**
     * Gibt an ob diese Klasse heilt
     */
    public boolean isHealer() {
        return this == HEALER;
    }

    /**
     * Gibt an ob diese Klasse den Core repariert
     */
    public boolean isBuilder() {
        return this == BUILDER;
    }

    /**
     * Gibt die Beschreibung der Klasse zurück
     */
    public String getDescription() {
        switch (this) {
            case WARRIOR:
                return "Nahkämpfer mit Schwert und Rüstung. Greift Mobs direkt an.";
            case ARCHER:
                return "Fernkämpfer mit Bogen. Bleibt auf Distanz und schießt Pfeile.";
            case HEALER:
                return "Heilt verbündete Villagers und Spieler. Greift nicht an.";
            case BUILDER:
                return "Repariert den Village Core. Schwacher Nahkampf.";
            case TANK:
                return "Schwer gepanzert mit hoher HP. Zieht Mob-Aggro auf sich.";
            default:
                return "";
        }
    }

    /**
     * Parse String zu VillagerClass (case-insensitive)
     */
    public static VillagerClass fromString(String str) {
        if (str == null) return WARRIOR; // Default

        String normalized = str.toUpperCase().trim();

        try {
            return VillagerClass.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // Versuche deutsche Namen
            switch (normalized) {
                case "KRIEGER": return WARRIOR;
                case "BOGENSCHÜTZE": case "BOGENSCHUETZE": return ARCHER;
                case "HEILER": return HEALER;
                case "BAUMEISTER": return BUILDER;
                default: return WARRIOR;
            }
        }
    }
}
