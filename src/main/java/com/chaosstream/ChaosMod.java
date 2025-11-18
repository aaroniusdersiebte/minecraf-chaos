package com.chaosstream;

import com.chaosstream.network.NetworkHandler;
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
    private static DefenderManager defenderManager;
    private static FileWatcher fileWatcher;
    private static SpawnHandler spawnHandler;
    private static TowerManager towerManager;
    private static TowerAttackLogic towerAttackLogic;
    private static StatsManager statsManager;
    private static ScoreboardManager scoreboardManager;

    // Tick-Counter für Defender-Sync (alle 20 Ticks = 1 Sekunde)
    private static int defenderSyncTicks = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("Chaos Stream Mod initializing...");

        // Initialize networking
        NetworkHandler.initServer();

        // Initialize managers
        chaosManager = new ChaosManager();
        villageManager = new VillageManager();
        defenderManager = DefenderManager.getInstance(); // Singleton
        spawnHandler = new SpawnHandler();
        fileWatcher = new FileWatcher(chaosManager, spawnHandler);
        towerManager = new TowerManager();
        towerAttackLogic = new TowerAttackLogic(towerManager);
        statsManager = StatsManager.getInstance(); // Singleton
        scoreboardManager = new ScoreboardManager();

        // Register tower placement handler
        TowerPlacementHandler placementHandler = new TowerPlacementHandler(towerManager, villageManager);
        placementHandler.register();

        // Register defender interaction handler
        DefenderInteractionHandler interactionHandler = new DefenderInteractionHandler(defenderManager);
        interactionHandler.register();

        // Register commands
        CommandRegistrationCallback.EVENT.register(CommandHandler::register);

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Server started - Starting file watcher...");
            fileWatcher.start();

            // Respawn gespeicherte Defender
            server.getWorlds().forEach(world -> {
                if (villageManager.hasVillageCore()) {
                    defenderManager.respawnAllDefenders(world, villageManager.getVillageCorePos());
                }
            });
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Server stopping - Stopping file watcher...");
            fileWatcher.stop();
            chaosManager.save();
            villageManager.save();
            towerManager.save();
            defenderManager.shutdown(); // Speichert Defender-Daten
        });

        // Register tick events for monster spawning, tower attacks, scoreboard, and stats
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            spawnHandler.onServerTick(server, chaosManager);

            // Process tower attacks for all worlds
            server.getWorlds().forEach(world -> {
                towerAttackLogic.tick(world);
            });

            // Update scoreboard
            scoreboardManager.tick(server);

            // Export stats for OBS
            statsManager.exportStatsToJson(server);

            // Send Defender-Sync to all clients (alle 20 Ticks = 1 Sekunde)
            defenderSyncTicks++;
            if (defenderSyncTicks >= 20) {
                NetworkHandler.sendDefenderSync(server);
                defenderSyncTicks = 0;
            }
        });

        LOGGER.info("Chaos Stream Mod initialized!");
    }

    public static ChaosManager getChaosManager() {
        return chaosManager;
    }

    public static VillageManager getVillageManager() {
        return villageManager;
    }

    public static DefenderManager getDefenderManager() {
        return defenderManager;
    }

    public static SpawnHandler getSpawnHandler() {
        return spawnHandler;
    }

    public static TowerManager getTowerManager() {
        return towerManager;
    }

    public static StatsManager getStatsManager() {
        return statsManager;
    }

    public static ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public static WaveManager getWaveManager() {
        return spawnHandler != null ? spawnHandler.getWaveManager() : null;
    }
}
