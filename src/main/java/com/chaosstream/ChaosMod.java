package com.chaosstream;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaosMod implements ModInitializer {
    public static final String MOD_ID = "chaosstream";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ChaosManager chaosManager;
    private static VillageManager villageManager;
    private static FileWatcher fileWatcher;
    private static SpawnHandler spawnHandler;

    @Override
    public void onInitialize() {
        LOGGER.info("Chaos Stream Mod initializing...");

        // Initialize managers
        chaosManager = new ChaosManager();
        villageManager = new VillageManager();
        spawnHandler = new SpawnHandler();
        fileWatcher = new FileWatcher(chaosManager, spawnHandler);

        // Register commands
        CommandRegistrationCallback.EVENT.register(CommandHandler::register);

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Server started - Starting file watcher...");
            fileWatcher.start();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Server stopping - Stopping file watcher...");
            fileWatcher.stop();
            chaosManager.save();
            villageManager.save();
        });

        // Register tick events for monster spawning
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            spawnHandler.onServerTick(server, chaosManager);
        });

        LOGGER.info("Chaos Stream Mod initialized!");
    }

    public static ChaosManager getChaosManager() {
        return chaosManager;
    }

    public static VillageManager getVillageManager() {
        return villageManager;
    }

    public static SpawnHandler getSpawnHandler() {
        return spawnHandler;
    }
}
