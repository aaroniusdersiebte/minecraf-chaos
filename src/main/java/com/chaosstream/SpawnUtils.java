package com.chaosstream;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

public class SpawnUtils {
    private static final Random RANDOM = new Random();

    public static void spawnMob(ServerWorld world, EntityType<?> type, BlockPos pos) {
        if (type.create(world) instanceof MobEntity mob) {
            mob.refreshPositionAndAngles(pos, 0, 0);
            mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.NATURAL, null, null);
            world.spawnEntity(mob);
        }
    }

    public static void spawnEnhancedMob(ServerWorld world, EntityType<?> type, BlockPos pos, int tier) {
        if (type.create(world) instanceof MobEntity mob) {
            mob.refreshPositionAndAngles(pos, 0, 0);
            mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.NATURAL, null, null);

            // Apply enhancements based on tier
            switch (tier) {
                case 2:
                    applyTier2Enhancements(mob);
                    break;
                case 3:
                    applyTier3Enhancements(mob);
                    break;
                case 4:
                    applyTier4Enhancements(mob);
                    break;
                case 5:
                    applyTier5Enhancements(mob);
                    break;
            }

            world.spawnEntity(mob);
        }
    }

    private static void applyTier2Enhancements(MobEntity mob) {
        // Basic armor
        if (RANDOM.nextFloat() < 0.3f) {
            mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            mob.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        }

        // Slight speed boost
        if (mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED) != null) {
            mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                .setBaseValue(mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).getBaseValue() * 1.1);
        }
    }

    private static void applyTier3Enhancements(MobEntity mob) {
        // Better armor
        if (RANDOM.nextFloat() < 0.5f) {
            mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            mob.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            mob.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        }

        // Speed and strength
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, Integer.MAX_VALUE, 0, false, false));

        // More HP
        if (mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null) {
            mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)
                .setBaseValue(mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).getBaseValue() * 1.5);
            mob.setHealth(mob.getMaxHealth());
        }
    }

    private static void applyTier4Enhancements(MobEntity mob) {
        // Full diamond armor with enchantments
        if (RANDOM.nextFloat() < 0.7f) {
            ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            ItemStack legs = new ItemStack(Items.DIAMOND_LEGGINGS);
            ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);

            helmet.addEnchantment(Enchantments.PROTECTION, 2);
            chest.addEnchantment(Enchantments.PROTECTION, 2);

            mob.equipStack(EquipmentSlot.HEAD, helmet);
            mob.equipStack(EquipmentSlot.CHEST, chest);
            mob.equipStack(EquipmentSlot.LEGS, legs);
            mob.equipStack(EquipmentSlot.FEET, boots);
        }

        // Weapon
        if (RANDOM.nextFloat() < 0.5f) {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.addEnchantment(Enchantments.SHARPNESS, 2);
            mob.equipStack(EquipmentSlot.MAINHAND, sword);
        }

        // Strong effects
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, Integer.MAX_VALUE, 1, false, false));
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, Integer.MAX_VALUE, 0, false, false));

        // Double HP
        if (mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null) {
            mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)
                .setBaseValue(mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).getBaseValue() * 2);
            mob.setHealth(mob.getMaxHealth());
        }
    }

    private static void applyTier5Enhancements(MobEntity mob) {
        // Full netherite armor with high enchantments
        ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
        ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
        ItemStack legs = new ItemStack(Items.NETHERITE_LEGGINGS);
        ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);

        helmet.addEnchantment(Enchantments.PROTECTION, 4);
        chest.addEnchantment(Enchantments.PROTECTION, 4);
        legs.addEnchantment(Enchantments.PROTECTION, 3);
        boots.addEnchantment(Enchantments.PROTECTION, 3);

        mob.equipStack(EquipmentSlot.HEAD, helmet);
        mob.equipStack(EquipmentSlot.CHEST, chest);
        mob.equipStack(EquipmentSlot.LEGS, legs);
        mob.equipStack(EquipmentSlot.FEET, boots);

        // Netherite sword
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.addEnchantment(Enchantments.SHARPNESS, 4);
        sword.addEnchantment(Enchantments.FIRE_ASPECT, 2);
        mob.equipStack(EquipmentSlot.MAINHAND, sword);

        // Max effects
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, Integer.MAX_VALUE, 2, false, false));
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, Integer.MAX_VALUE, 1, false, false));
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, Integer.MAX_VALUE, 1, false, false));
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));

        // Triple HP
        if (mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null) {
            mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)
                .setBaseValue(mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).getBaseValue() * 3);
            mob.setHealth(mob.getMaxHealth());
        }

        // Set as glowing for dramatic effect
        mob.setGlowing(true);
    }
}
