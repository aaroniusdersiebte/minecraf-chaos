package com.chaosstream.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side Mod-Entrypoint für Chaos Stream Mod
 * Verantwortlich für:
 * - Client-Side Networking (Packet-Empfang vom Server)
 * - Zukünftige Client-Features (Rechtsklick-Menü, JourneyMap Integration)
 */
@Environment(EnvType.CLIENT)
public class ChaosClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("chaosstream-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Chaos Stream Client initialisiert!");

        // Initialize Client-Side Networking (DefenderSyncPacket, DefenderInventoryPacket)
        ClientNetworkHandler.initClient();
        LOGGER.info("Client-Side Networking bereit!");

        // Registriere Keybinds ('K' für Defender Management)
        ChaosKeybinds.register();
        LOGGER.info("Keybinds registriert!");

        // TODO: Client-Side Rechtsklick-Event für Defender-GUI (UseEntityCallback ist Server-Side)
        // Minecraft 1.20.1 hat kein Client-Side UseEntityCallback Event
        // Rechtsklick auf Defender öffnet aktuell nur über Keybind 'K'

        // TODO: JourneyMap Plugin initialisieren
    }
}
