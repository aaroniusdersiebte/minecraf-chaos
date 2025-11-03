package com.chaosstream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileWatcher {
    private static final String COMMANDS_DIR = "chaos-commands";
    private static final Gson GSON = new Gson();

    private final ChaosManager chaosManager;
    private final SpawnHandler spawnHandler;
    private Thread watcherThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FileWatcher(ChaosManager chaosManager, SpawnHandler spawnHandler) {
        this.chaosManager = chaosManager;
        this.spawnHandler = spawnHandler;

        // Create commands directory if it doesn't exist
        File dir = new File(COMMANDS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            ChaosMod.LOGGER.info("Created commands directory: {}", dir.getAbsolutePath());
        }
    }

    public void start() {
        if (running.get()) {
            ChaosMod.LOGGER.warn("File watcher already running");
            return;
        }

        running.set(true);
        watcherThread = new Thread(this::watchDirectory, "ChaosStream-FileWatcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
        ChaosMod.LOGGER.info("File watcher started, monitoring: {}", new File(COMMANDS_DIR).getAbsolutePath());
    }

    public void stop() {
        running.set(false);
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
        ChaosMod.LOGGER.info("File watcher stopped");
    }

    private void watchDirectory() {
        try {
            Path path = Paths.get(COMMANDS_DIR);
            WatchService watchService = FileSystems.getDefault().newWatchService();
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);

            ChaosMod.LOGGER.info("Watching for command files in: {}", path.toAbsolutePath());

            while (running.get()) {
                WatchKey key;
                try {
                    key = watchService.poll(java.util.concurrent.TimeUnit.SECONDS.toMillis(1),
                                          java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    break;
                }

                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    // Only process .json files
                    if (filename.toString().endsWith(".json")) {
                        Path fullPath = path.resolve(filename);
                        processCommandFile(fullPath.toFile());
                    }
                }

                key.reset();
            }

            watchService.close();
        } catch (Exception e) {
            ChaosMod.LOGGER.error("Error in file watcher", e);
        }
    }

    private void processCommandFile(File file) {
        try {
            // Wait a bit to ensure file is fully written
            Thread.sleep(100);

            if (!file.exists()) return;

            try (FileReader reader = new FileReader(file)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                if (json == null || !json.has("command")) {
                    ChaosMod.LOGGER.warn("Invalid command file: {}", file.getName());
                    return;
                }

                String command = json.get("command").getAsString();
                ChaosMod.LOGGER.info("Processing command: {} from file: {}", command, file.getName());

                // Process the command
                handleCommand(command, json);

                // Delete the processed file
                if (file.delete()) {
                    ChaosMod.LOGGER.debug("Deleted processed command file: {}", file.getName());
                }
            }
        } catch (Exception e) {
            ChaosMod.LOGGER.error("Error processing command file: {}", file.getName(), e);
            // Delete corrupted files
            file.delete();
        }
    }

    private void handleCommand(String command, JsonObject data) {
        switch (command.toLowerCase()) {
            case "add_chaos":
                int amount = data.has("amount") ? data.get("amount").getAsInt() : 10;
                String source = data.has("source") ? data.get("source").getAsString() : "unknown";
                chaosManager.addChaos(amount);
                ChaosMod.LOGGER.info("Added {} chaos from source: {}", amount, source);
                break;

            case "spawn_creeper":
                String creeperPlayer = data.has("player") ? data.get("player").getAsString() : null;
                spawnHandler.queueCreeperSpawn(creeperPlayer);
                ChaosMod.LOGGER.info("Queued creeper spawn for player: {}", creeperPlayer);
                break;

            case "spawn_lootbox":
                String lootboxPlayer = data.has("player") ? data.get("player").getAsString() : null;
                spawnHandler.queueLootboxSpawn(lootboxPlayer);
                ChaosMod.LOGGER.info("Queued lootbox spawn for player: {}", lootboxPlayer);
                break;

            case "create_villager":
                String villagerName = data.has("name") ? data.get("name").getAsString() : "Viewer";
                String villagerPlayer = data.has("player") ? data.get("player").getAsString() : null;
                spawnHandler.queueVillagerSpawn(villagerName, villagerPlayer);
                ChaosMod.LOGGER.info("Queued villager spawn with name: {} for player: {}", villagerName, villagerPlayer);
                break;

            case "spawn_defender":
                String defenderName = data.has("villager_name") ? data.get("villager_name").getAsString() : "Defender";
                String defenderClass = data.has("class") ? data.get("class").getAsString() : "warrior";
                String defenderPlayer = data.has("player") ? data.get("player").getAsString() : null;
                spawnHandler.queueDefenderSpawn(defenderName, defenderClass, defenderPlayer);
                ChaosMod.LOGGER.info("Queued defender spawn: {} (Class: {}) for player: {}", defenderName, defenderClass, defenderPlayer);
                break;

            case "reset_chaos":
                chaosManager.reset();
                ChaosMod.LOGGER.info("Chaos level reset");
                break;

            case "get_chaos":
                ChaosMod.LOGGER.info("Current chaos level: {} (Total: {})",
                    chaosManager.getChaosLevel(), chaosManager.getTotalChaos());
                break;

            // Trolling Commands
            case "spawn_tnt":
                String tntPlayer = data.has("player") ? data.get("player").getAsString() : null;
                int tntCount = data.has("count") ? data.get("count").getAsInt() : 3;
                int fuseTicks = data.has("fuse") ? data.get("fuse").getAsInt() : 80;
                spawnHandler.queueTNTSpawn(tntPlayer, tntCount, fuseTicks);
                ChaosMod.LOGGER.info("Queued TNT spawn (count: {}) for player: {}", tntCount, tntPlayer);
                break;

            case "random_teleport":
                String teleportPlayer = data.has("player") ? data.get("player").getAsString() : null;
                int teleportRadius = data.has("radius") ? data.get("radius").getAsInt() : 150;
                spawnHandler.queueRandomTeleport(teleportPlayer, teleportRadius);
                ChaosMod.LOGGER.info("Queued random teleport (radius: {}) for player: {}", teleportRadius, teleportPlayer);
                break;

            case "change_weather":
                String weatherType = data.has("type") ? data.get("type").getAsString() : "thunder";
                spawnHandler.queueWeatherChange(weatherType);
                ChaosMod.LOGGER.info("Queued weather change to: {}", weatherType);
                break;

            case "spawn_lightning":
                String lightningPlayer = data.has("player") ? data.get("player").getAsString() : null;
                spawnHandler.queueLightningStrike(lightningPlayer);
                ChaosMod.LOGGER.info("Queued lightning strike for player: {}", lightningPlayer);
                break;

            // Helpful Commands
            case "spawn_helper":
                String helperType = data.has("type") ? data.get("type").getAsString() : "iron_golem";
                String helperPlayer = data.has("player") ? data.get("player").getAsString() : null;
                spawnHandler.queueHelperSpawn(helperType, helperPlayer);
                ChaosMod.LOGGER.info("Queued {} helper spawn for player: {}", helperType, helperPlayer);
                break;

            case "give_buff":
                String buffType = data.has("type") ? data.get("type").getAsString() : "speed";
                String buffPlayer = data.has("player") ? data.get("player").getAsString() : null;
                int buffDuration = data.has("duration") ? data.get("duration").getAsInt() : 60;
                int buffAmplifier = data.has("amplifier") ? data.get("amplifier").getAsInt() : 0;
                spawnHandler.queueBuffEffect(buffType, buffPlayer, buffDuration, buffAmplifier);
                ChaosMod.LOGGER.info("Queued {} buff (duration: {}s) for player: {}", buffType, buffDuration, buffPlayer);
                break;

            case "spawn_food":
                String foodPlayer = data.has("player") ? data.get("player").getAsString() : null;
                spawnHandler.queueFoodSpawn(foodPlayer);
                ChaosMod.LOGGER.info("Queued food spawn for player: {}", foodPlayer);
                break;

            case "heal_player":
                String healPlayer = data.has("player") ? data.get("player").getAsString() : null;
                spawnHandler.queueHealPlayer(healPlayer);
                ChaosMod.LOGGER.info("Queued heal for player: {}", healPlayer);
                break;

            default:
                ChaosMod.LOGGER.warn("Unknown command: {}", command);
                break;
        }
    }
}
