package com.chaosstream.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → Client Packet für Defender-Inventar-Synchronisation
 *
 * Sendet Inventar-Daten eines einzelnen Defenders zum Client für
 * Darstellung im DefenderManagementScreen GUI.
 *
 * Update-Rate: Bei GUI-Öffnung + nach jeder Inventar-Änderung
 */
public class DefenderInventoryPacket {
    public static final Identifier ID = new Identifier("chaosstream", "defender_inventory");

    private final UUID defenderUUID;
    private final List<ItemStack> inventoryItems;  // 9 Slots
    private final EquipmentData equipment;

    public DefenderInventoryPacket(UUID defenderUUID, List<ItemStack> inventoryItems, EquipmentData equipment) {
        this.defenderUUID = defenderUUID;
        this.inventoryItems = inventoryItems;
        this.equipment = equipment;
    }

    public UUID defenderUUID() {
        return defenderUUID;
    }

    public List<ItemStack> inventoryItems() {
        return inventoryItems;
    }

    public EquipmentData equipment() {
        return equipment;
    }

    /**
     * Schreibt Packet-Daten in Buffer
     */
    public void write(PacketByteBuf buf) {
        // Defender-UUID
        buf.writeUuid(defenderUUID);

        // Inventar (9 Slots)
        buf.writeInt(inventoryItems.size());
        for (ItemStack stack : inventoryItems) {
            buf.writeItemStack(stack);
        }

        // Equipment
        buf.writeItemStack(equipment.helmet);
        buf.writeItemStack(equipment.chestplate);
        buf.writeItemStack(equipment.leggings);
        buf.writeItemStack(equipment.boots);
        buf.writeItemStack(equipment.mainHand);
    }

    /**
     * Liest Packet-Daten aus Buffer
     */
    public static DefenderInventoryPacket read(PacketByteBuf buf) {
        UUID defenderUUID = buf.readUuid();

        // Inventar
        int inventorySize = buf.readInt();
        List<ItemStack> inventoryItems = new ArrayList<>(inventorySize);
        for (int i = 0; i < inventorySize; i++) {
            inventoryItems.add(buf.readItemStack());
        }

        // Equipment
        ItemStack helmet = buf.readItemStack();
        ItemStack chestplate = buf.readItemStack();
        ItemStack leggings = buf.readItemStack();
        ItemStack boots = buf.readItemStack();
        ItemStack mainHand = buf.readItemStack();

        EquipmentData equipment = new EquipmentData(helmet, chestplate, leggings, boots, mainHand);

        return new DefenderInventoryPacket(defenderUUID, inventoryItems, equipment);
    }

    /**
     * Equipment-Daten (Rüstung + Waffe)
     */
    public record EquipmentData(
        ItemStack helmet,
        ItemStack chestplate,
        ItemStack leggings,
        ItemStack boots,
        ItemStack mainHand
    ) {}
}
