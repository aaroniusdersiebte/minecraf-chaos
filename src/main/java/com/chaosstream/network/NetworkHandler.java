package com.chaosstream.network;

import com.chaosstream.ChaosMod;
import com.chaosstream.DefenderManager;
import com.chaosstream.DefenderVillager;
import com.chaosstream.VillageManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-Side Networking Handler
 * Registriert Packets und verarbeitet eingehende Commands
 */
public class NetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("chaosstream-network");

    /**
     * Initialisiert Server-Side Networking
     * Wird in ChaosMod.onInitialize() aufgerufen
     */
    public static void initServer() {
        // Register Server-Receiver für DefenderCommandPacket (Minecraft 1.20.1 API)
        ServerPlayNetworking.registerGlobalReceiver(DefenderCommandPacket.ID, (server, player, handler, buf, responseSender) -> {
            DefenderCommandPacket packet = DefenderCommandPacket.read(buf);
            server.execute(() -> {
                handleDefenderCommand(packet, player);
            });
        });

        // Register Server-Receiver für DefenderActionPacket (GUI-Actions)
        ServerPlayNetworking.registerGlobalReceiver(DefenderActionPacket.ID, (server, player, handler, buf, responseSender) -> {
            DefenderActionPacket packet = DefenderActionPacket.read(buf);
            server.execute(() -> {
                handleDefenderAction(packet, player);
            });
        });

        LOGGER.info("Server-Side Networking initialisiert!");
    }

    /**
     * Verarbeitet Defender-Commands vom Client (vereinfacht - nur Follow/Stay)
     */
    private static void handleDefenderCommand(DefenderCommandPacket packet, ServerPlayerEntity player) {
        DefenderManager manager = DefenderManager.getInstance();

        LOGGER.info("Befehl empfangen: {} von {} für {} Defender",
            packet.commandType(), player.getName().getString(), packet.defenderUUIDs().size());

        switch (packet.commandType()) {
            case FOLLOW:
                // Defender folgt Spieler
                for (java.util.UUID uuid : packet.defenderUUIDs()) {
                    DefenderVillager defender = manager.getDefender(uuid);
                    if (defender != null) {
                        manager.setDefenderFollowMode(defender, player, true);
                        player.sendMessage(net.minecraft.text.Text.literal(
                            "§a✓ §f" + defender.getViewerName() + " §afolgt dir jetzt!"
                        ), false);
                    }
                }
                break;

            case STAY:
                // Defender patrouilliert am aktuellen Ort (Standard AI)
                for (java.util.UUID uuid : packet.defenderUUIDs()) {
                    DefenderVillager defender = manager.getDefender(uuid);
                    if (defender != null) {
                        manager.setDefenderFollowMode(defender, player, false);
                        player.sendMessage(net.minecraft.text.Text.literal(
                            "§e✓ §f" + defender.getViewerName() + " §epatrouilliert jetzt!"
                        ), false);
                    }
                }
                break;
        }
    }

    /**
     * Sendet Defender-Sync an alle Spieler
     * Sollte vom Server alle 1 Sekunde aufgerufen werden
     */
    public static void sendDefenderSync(net.minecraft.server.MinecraftServer server) {
        DefenderManager defenderManager = DefenderManager.getInstance();
        VillageManager villageManager = ChaosMod.getVillageManager();

        // Sammle alle Defender-Daten
        List<DefenderSyncPacket.DefenderData> defenderDataList = new ArrayList<>();
        for (DefenderVillager defender : defenderManager.getAllDefenders()) {
            if (defender.getLinkedEntity() != null && defender.getLinkedEntity().isAlive()) {
                defenderDataList.add(new DefenderSyncPacket.DefenderData(
                    defender.getUuid(),
                    defender.getViewerName(),
                    defender.getVillagerClass(),
                    defender.getLevel(),
                    defender.getXp(),
                    defender.getKills(),
                    defender.getDamageDealt(),
                    defender.getWavesCompleted(),
                    defender.getHealingDone(),
                    defender.getLinkedEntity().getBlockPos(),
                    defender.getLinkedEntity().getHealth(),
                    defender.getLinkedEntity().getMaxHealth(),
                    defender.isFollowing()
                ));
            }
        }

        // Village Core-Daten
        BlockPos corePos = villageManager.getVillageCorePos();
        int coreHealth = villageManager.getCoreHP();
        int coreMaxHealth = villageManager.getMaxCoreHP();

        // Active Spawn-Locations (von WaveManager)
        List<BlockPos> spawnLocations = ChaosMod.getWaveManager().getActiveSpawnLocations();

        // Sende an alle Spieler (Minecraft 1.20.1 API)
        // Jeder Spieler erhält seine eigene Position
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Erstelle Packet mit Spieler-Position
            DefenderSyncPacket packet = new DefenderSyncPacket(
                defenderDataList,
                corePos,
                coreHealth,
                coreMaxHealth,
                spawnLocations,
                player.getBlockPos() // Spieler-Position für Map
            );

            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            packet.write(buf);
            ServerPlayNetworking.send(player, DefenderSyncPacket.ID, buf);
        }
    }

    /**
     * Verarbeitet Defender-GUI-Actions vom Client
     */
    private static void handleDefenderAction(DefenderActionPacket packet, ServerPlayerEntity player) {
        DefenderManager manager = DefenderManager.getInstance();
        DefenderVillager defender = manager.getDefender(packet.defenderUUID());

        if (defender == null) {
            player.sendMessage(net.minecraft.text.Text.literal("§c✗ Defender nicht gefunden!"), false);
            return;
        }

        LOGGER.info("GUI-Aktion empfangen: {} von {} für Defender {}",
            packet.actionType(), player.getName().getString(), defender.getViewerName());

        switch (packet.actionType()) {
            case UPGRADE:
                // Equipment upgraden (konsumiert Items aus Spieler-Inventar)
                manager.upgradeDefenderEquipment(defender, player);
                break;

            case HEAL:
                // Defender heilen (konsumiert Items aus Spieler-Inventar)
                manager.healDefender(defender, player);
                break;

            case GIVE_ITEM:
                // Item vom Spieler zum Defender-Inventar übertragen
                manager.giveItemToDefender(defender, packet.itemStack(), packet.slotIndex());
                break;

            case TAKE_ITEM:
                // Item vom Defender-Inventar zum Spieler übertragen
                manager.takeItemFromDefender(defender, player, packet.slotIndex());
                break;
        }

        // Nach Action: Inventar-Update an Client senden
        sendDefenderInventory(player, defender);
    }

    /**
     * Sendet Defender-Inventar an Client (für GUI-Update)
     */
    public static void sendDefenderInventory(ServerPlayerEntity player, DefenderVillager defender) {
        if (defender.getLinkedEntity() == null) {
            return;
        }

        // Equipment von Entity holen
        net.minecraft.entity.LivingEntity entity = defender.getLinkedEntity();
        DefenderInventoryPacket.EquipmentData equipment = new DefenderInventoryPacket.EquipmentData(
            entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD),
            entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST),
            entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS),
            entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET),
            entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.MAINHAND)
        );

        // Inventar von Defender holen
        List<net.minecraft.item.ItemStack> inventoryItems = new ArrayList<>();
        net.minecraft.inventory.SimpleInventory inventory = defender.getInventory();
        for (int i = 0; i < 9; i++) {
            inventoryItems.add(inventory.getStack(i));
        }

        // Packet erstellen und senden
        DefenderInventoryPacket packet = new DefenderInventoryPacket(
            defender.getUuid(),
            inventoryItems,
            equipment
        );

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        packet.write(buf);
        ServerPlayNetworking.send(player, DefenderInventoryPacket.ID, buf);
    }
}
