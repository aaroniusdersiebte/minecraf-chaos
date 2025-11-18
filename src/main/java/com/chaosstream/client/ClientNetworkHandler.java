package com.chaosstream.client;

import com.chaosstream.client.screen.DefenderManagementScreen;
import com.chaosstream.network.DefenderSyncPacket;
import com.chaosstream.network.DefenderInventoryPacket;
import com.chaosstream.network.OpenDefenderGuiPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-Side Networking Handler
 * Empfängt und verarbeitet DefenderSyncPacket vom Server
 */
@Environment(EnvType.CLIENT)
public class ClientNetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("chaosstream-client-network");

    // Client-Side Cache für Defender-Daten
    private static List<DefenderSyncPacket.DefenderData> cachedDefenders = new ArrayList<>();
    private static DefenderSyncPacket lastSyncPacket = null;

    // Client-Side Cache für Defender-Inventare (UUID -> InventoryPacket)
    private static Map<UUID, DefenderInventoryPacket> cachedInventories = new HashMap<>();

    /**
     * Initialisiert Client-Side Networking
     * Wird in ChaosClient.onInitializeClient() aufgerufen
     */
    public static void initClient() {
        // Register Client-Receiver für DefenderSyncPacket (Minecraft 1.20.1 API)
        ClientPlayNetworking.registerGlobalReceiver(DefenderSyncPacket.ID, (client, handler, buf, responseSender) -> {
            DefenderSyncPacket packet = DefenderSyncPacket.read(buf);
            client.execute(() -> {
                handleDefenderSync(packet);
            });
        });

        // Register Client-Receiver für DefenderInventoryPacket (für GUI-Updates)
        ClientPlayNetworking.registerGlobalReceiver(DefenderInventoryPacket.ID, (client, handler, buf, responseSender) -> {
            DefenderInventoryPacket packet = DefenderInventoryPacket.read(buf);
            client.execute(() -> {
                handleDefenderInventory(packet);
            });
        });

        // Register Client-Receiver für OpenDefenderGuiPacket (Rechtsklick auf Defender)
        ClientPlayNetworking.registerGlobalReceiver(OpenDefenderGuiPacket.ID, (client, handler, buf, responseSender) -> {
            OpenDefenderGuiPacket packet = OpenDefenderGuiPacket.read(buf);
            client.execute(() -> {
                handleOpenDefenderGui(packet);
            });
        });

        LOGGER.info("Client-Side Networking initialisiert!");
    }

    /**
     * Verarbeitet Defender-Sync vom Server
     */
    private static void handleDefenderSync(DefenderSyncPacket packet) {
        lastSyncPacket = packet;
        cachedDefenders = new ArrayList<>(packet.defenders());

        LOGGER.debug("Defender-Sync empfangen: {} Defender, Core HP: {}/{}",
            cachedDefenders.size(),
            packet.coreHealth(),
            packet.coreMaxHealth()
        );

        // TODO: Update JourneyMap Overlays
        // TODO: Update DefenderCommandScreen (falls offen)
    }

    /**
     * Gibt gecachte Defender-Daten zurück
     * Wird von GUI verwendet
     */
    public static List<DefenderSyncPacket.DefenderData> getCachedDefenders() {
        return new ArrayList<>(cachedDefenders);
    }

    /**
     * Gibt letztes Sync-Packet zurück
     * Enthält auch Core & Spawn-Locations
     */
    public static DefenderSyncPacket getLastSyncPacket() {
        return lastSyncPacket;
    }

    /**
     * Prüft ob Defender-Daten verfügbar sind
     */
    public static boolean hasDefenderData() {
        return lastSyncPacket != null && !cachedDefenders.isEmpty();
    }

    /**
     * Verarbeitet Defender-Inventar-Sync vom Server
     */
    private static void handleDefenderInventory(DefenderInventoryPacket packet) {
        cachedInventories.put(packet.defenderUUID(), packet);

        LOGGER.debug("Defender-Inventar empfangen für UUID: {}", packet.defenderUUID());

        // TODO: Update DefenderManagementScreen (falls offen)
    }

    /**
     * Gibt Inventar-Daten für einen Defender zurück
     */
    public static DefenderInventoryPacket getDefenderInventory(UUID defenderUUID) {
        return cachedInventories.get(defenderUUID);
    }

    /**
     * Öffnet Defender-GUI nach Rechtsklick
     */
    private static void handleOpenDefenderGui(OpenDefenderGuiPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            // Konvertiere Packet zu DefenderData
            DefenderSyncPacket.DefenderData defenderData = packet.toDefenderData();

            // Öffne DefenderManagementScreen
            client.setScreen(new DefenderManagementScreen(null, defenderData));

            LOGGER.debug("DefenderManagementScreen geöffnet für Defender: {}", packet.name());
        }
    }
}
