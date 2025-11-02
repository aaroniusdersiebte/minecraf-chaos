package com.chaosstream;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TowerPlacementHandler {
    private static final int MIN_DISTANCE_FROM_CORE = 10;
    private static final int MAX_DISTANCE_FROM_CORE = 100;
    private static final int MIN_DISTANCE_BETWEEN_TOWERS = 5;

    private final TowerManager towerManager;
    private final VillageManager villageManager;

    public TowerPlacementHandler(TowerManager towerManager, VillageManager villageManager) {
        this.towerManager = towerManager;
        this.villageManager = villageManager;
    }

    /**
     * Register the placement handler
     */
    public void register() {
        UseBlockCallback.EVENT.register(this::onUseBlock);
    }

    /**
     * Handle block use (right-click)
     */
    private ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        // Only handle server-side
        if (world.isClient()) {
            return ActionResult.PASS;
        }

        ItemStack itemInHand = player.getStackInHand(hand);

        // Check if it's a tower item
        if (!TowerItemHelper.isTowerItem(itemInHand)) {
            return ActionResult.PASS;
        }

        TowerType towerType = TowerItemHelper.getTowerType(itemInHand);
        if (towerType == null) {
            return ActionResult.FAIL;
        }

        // Get placement position (on top of clicked block)
        BlockPos clickedPos = hitResult.getBlockPos();
        BlockPos placementPos = clickedPos.up();

        // Validate placement
        String error = validatePlacement(world, placementPos, player);
        if (error != null) {
            player.sendMessage(Text.literal("[TD] " + error).formatted(Formatting.RED), false);
            return ActionResult.FAIL;
        }

        // Check if player has required resources
        if (!hasRequiredResources(player, towerType)) {
            String costDesc = TowerItemHelper.getCostDescription(towerType);
            player.sendMessage(Text.literal("[TD] Not enough resources! Need: " + costDesc)
                    .formatted(Formatting.RED), false);
            return ActionResult.FAIL;
        }

        // Deduct resources
        deductResources(player, towerType);

        // Place tower structure
        buildTowerStructure(world, placementPos, towerType);

        // Create and register tower
        Tower tower = new Tower(placementPos, towerType, player.getUuid());
        towerManager.addTower(tower);

        // Visual and audio feedback
        spawnPlacementEffects((ServerWorld) world, placementPos, towerType);

        // Consume item
        if (!player.isCreative()) {
            itemInHand.decrement(1);
        }

        // Send success message
        player.sendMessage(Text.literal("[TD] " + towerType.getDisplayName() + " placed!")
                .formatted(Formatting.GREEN), false);

        return ActionResult.SUCCESS;
    }

    /**
     * Validate tower placement
     */
    private String validatePlacement(World world, BlockPos pos, PlayerEntity player) {
        // Check if village core is set
        if (!villageManager.hasVillageCore()) {
            return "Village core not set! Use /chaos setvillage first.";
        }

        BlockPos corePos = villageManager.getVillageCorePos();

        // Check distance from core
        double distanceFromCore = Math.sqrt(pos.getSquaredDistance(corePos));
        if (distanceFromCore < MIN_DISTANCE_FROM_CORE) {
            return "Too close to village core! Min distance: " + MIN_DISTANCE_FROM_CORE + " blocks.";
        }
        if (distanceFromCore > MAX_DISTANCE_FROM_CORE) {
            return "Too far from village core! Max distance: " + MAX_DISTANCE_FROM_CORE + " blocks.";
        }

        // Check if position is occupied
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
            return "Position occupied! Clear space needed.";
        }

        // Check distance from other towers
        for (Tower existingTower : towerManager.getAllTowers()) {
            double distance = Math.sqrt(pos.getSquaredDistance(existingTower.getPosition()));
            if (distance < MIN_DISTANCE_BETWEEN_TOWERS) {
                return "Too close to another tower! Min distance: " + MIN_DISTANCE_BETWEEN_TOWERS + " blocks.";
            }
        }

        return null; // Placement valid
    }

    /**
     * Check if player has required resources
     */
    private boolean hasRequiredResources(PlayerEntity player, TowerType type) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] costs = type.getCosts();

        for (ItemStack cost : costs) {
            if (!inventory.contains(cost)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Deduct resources from player inventory
     */
    private void deductResources(PlayerEntity player, TowerType type) {
        if (player.isCreative()) {
            return; // Don't deduct in creative mode
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] costs = type.getCosts();

        for (ItemStack cost : costs) {
            int remaining = cost.getCount();

            for (int i = 0; i < inventory.size() && remaining > 0; i++) {
                ItemStack stack = inventory.getStack(i);
                if (stack.getItem() == cost.getItem()) {
                    int toRemove = Math.min(remaining, stack.getCount());
                    stack.decrement(toRemove);
                    remaining -= toRemove;
                }
            }
        }
    }

    /**
     * Build the visual tower structure
     */
    private void buildTowerStructure(World world, BlockPos pos, TowerType type) {
        switch (type) {
            case ARCHER:
                // Archer tower: Magical bow tower with glowing elements
                world.setBlockState(pos, Blocks.PURPUR_BLOCK.getDefaultState());
                world.setBlockState(pos.up(), Blocks.PURPUR_PILLAR.getDefaultState());
                world.setBlockState(pos.up(2), Blocks.END_ROD.getDefaultState());
                world.setBlockState(pos.up(3), Blocks.SEA_LANTERN.getDefaultState());
                break;

            case CANNON:
                // Cannon tower: Dark explosive tower with glowing magma
                world.setBlockState(pos, Blocks.BLACKSTONE.getDefaultState());
                world.setBlockState(pos.up(), Blocks.PURPUR_PILLAR.getDefaultState());
                world.setBlockState(pos.up(2), Blocks.MAGMA_BLOCK.getDefaultState());
                world.setBlockState(pos.up(3), Blocks.SEA_LANTERN.getDefaultState());
                break;
        }
    }

    /**
     * Spawn visual effects at placement location
     */
    private void spawnPlacementEffects(ServerWorld world, BlockPos pos, TowerType type) {
        // Placement particles (upward spiral)
        for (int i = 0; i < 20; i++) {
            double offsetX = (world.getRandom().nextDouble() - 0.5) * 2;
            double offsetY = world.getRandom().nextDouble() * 3;
            double offsetZ = (world.getRandom().nextDouble() - 0.5) * 2;

            world.spawnParticles(
                    ParticleTypes.ENCHANT,
                    pos.getX() + 0.5 + offsetX,
                    pos.getY() + offsetY,
                    pos.getZ() + 0.5 + offsetZ,
                    1, 0, 0, 0, 0
            );
        }

        // Range indicator (particle circle)
        double range = type.getRange();
        int particleCount = (int)(range * 4); // More particles for larger range

        for (int i = 0; i < particleCount; i++) {
            double angle = (2 * Math.PI * i) / particleCount;
            double x = pos.getX() + 0.5 + Math.cos(angle) * range;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * range;

            world.spawnParticles(
                    ParticleTypes.END_ROD,
                    x,
                    pos.getY() + 0.5,
                    z,
                    1, 0, 0.2, 0, 0
            );
        }

        // Sound
        world.playSound(
                null,
                pos,
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                SoundCategory.BLOCKS,
                1.0f,
                1.2f
        );

        world.playSound(
                null,
                pos,
                SoundEvents.BLOCK_BEACON_ACTIVATE,
                SoundCategory.BLOCKS,
                0.5f,
                1.5f
        );
    }
}
