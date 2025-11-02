package com.chaosstream;

import net.minecraft.util.math.BlockPos;
import com.google.gson.JsonObject;

import java.util.UUID;

public class Tower {
    private final UUID id;
    private final BlockPos position;
    private final TowerType type;
    private int currentCooldown;
    private UUID ownerUUID;

    public Tower(BlockPos position, TowerType type, UUID ownerUUID) {
        this.id = UUID.randomUUID();
        this.position = position;
        this.type = type;
        this.currentCooldown = 0;
        this.ownerUUID = ownerUUID;
    }

    // Constructor for deserialization
    public Tower(UUID id, BlockPos position, TowerType type, UUID ownerUUID) {
        this.id = id;
        this.position = position;
        this.type = type;
        this.currentCooldown = 0;
        this.ownerUUID = ownerUUID;
    }

    public UUID getId() {
        return id;
    }

    public BlockPos getPosition() {
        return position;
    }

    public TowerType getType() {
        return type;
    }

    public int getCurrentCooldown() {
        return currentCooldown;
    }

    public void setCurrentCooldown(int cooldown) {
        this.currentCooldown = cooldown;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public boolean canAttack() {
        return currentCooldown <= 0;
    }

    public void resetCooldown() {
        this.currentCooldown = type.getAttackCooldown();
    }

    public void tickCooldown() {
        if (currentCooldown > 0) {
            currentCooldown--;
        }
    }

    // Serialization
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("x", position.getX());
        json.addProperty("y", position.getY());
        json.addProperty("z", position.getZ());
        json.addProperty("type", type.getId());
        if (ownerUUID != null) {
            json.addProperty("owner", ownerUUID.toString());
        }
        return json;
    }

    // Deserialization
    public static Tower fromJson(JsonObject json) {
        UUID id = UUID.fromString(json.get("id").getAsString());
        int x = json.get("x").getAsInt();
        int y = json.get("y").getAsInt();
        int z = json.get("z").getAsInt();
        BlockPos pos = new BlockPos(x, y, z);
        TowerType type = TowerType.fromString(json.get("type").getAsString());
        UUID owner = json.has("owner") ? UUID.fromString(json.get("owner").getAsString()) : null;
        return new Tower(id, pos, type, owner);
    }
}
