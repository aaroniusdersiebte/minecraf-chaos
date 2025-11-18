package com.chaosstream.client.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;

/**
 * Screen Handler für Defender-Management-GUI
 * Verwaltet Defender-Inventar (9 Slots) + Spieler-Inventar (36 Slots)
 */
public class DefenderScreenHandler extends ScreenHandler {
    private final Inventory defenderInventory;
    private static final int DEFENDER_INVENTORY_SIZE = 9;

    /**
     * Client-Side Konstruktor (ohne echtes Inventar)
     */
    public DefenderScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(DEFENDER_INVENTORY_SIZE));
    }

    /**
     * Server-Side Konstruktor (mit echtem Defender-Inventar)
     */
    public DefenderScreenHandler(int syncId, PlayerInventory playerInventory, Inventory defenderInventory) {
        super(ScreenHandlerType.GENERIC_9X1, syncId);
        this.defenderInventory = defenderInventory;

        checkSize(defenderInventory, DEFENDER_INVENTORY_SIZE);
        defenderInventory.onOpen(playerInventory.player);

        // Defender-Inventar Slots (9 Slots, oben im GUI)
        for (int i = 0; i < DEFENDER_INVENTORY_SIZE; i++) {
            this.addSlot(new Slot(defenderInventory, i, 8 + i * 18, 18));
        }

        // Spieler-Inventar (3 Reihen à 9 Slots)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Spieler-Hotbar (9 Slots)
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.defenderInventory.canPlayerUse(player);
    }

    /**
     * Shift-Click Transfer (Quick-Move)
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();

            if (slotIndex < DEFENDER_INVENTORY_SIZE) {
                // Von Defender-Inventar zu Spieler-Inventar
                if (!this.insertItem(originalStack, DEFENDER_INVENTORY_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Von Spieler-Inventar zu Defender-Inventar
                if (!this.insertItem(originalStack, 0, DEFENDER_INVENTORY_SIZE, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }

        return newStack;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.defenderInventory.onClose(player);
    }

    public Inventory getDefenderInventory() {
        return defenderInventory;
    }
}
