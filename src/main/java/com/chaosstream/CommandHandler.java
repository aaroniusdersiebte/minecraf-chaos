package com.chaosstream;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CommandHandler {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {

        // /chaos add <amount>
        dispatcher.register(CommandManager.literal("chaos")
            .then(CommandManager.literal("add")
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 1000))
                    .executes(CommandHandler::addChaos)))
            .then(CommandManager.literal("set")
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 1000))
                    .executes(CommandHandler::setChaos)))
            .then(CommandManager.literal("reset")
                .executes(CommandHandler::resetChaos))
            .then(CommandManager.literal("level")
                .executes(CommandHandler::getChaosLevel))
            .then(CommandManager.literal("wave")
                .executes(CommandHandler::forceWave))
            .then(CommandManager.literal("night")
                .executes(CommandHandler::setNight))
            .then(CommandManager.literal("setvillage")
                .executes(CommandHandler::setVillage))
            .then(CommandManager.literal("resetvillage")
                .executes(CommandHandler::resetVillage))
            .then(CommandManager.literal("corehp")
                .executes(CommandHandler::getCoreHP))
            .then(CommandManager.literal("cleanbars")
                .executes(CommandHandler::cleanBossBars))
            .then(CommandManager.literal("givetower")
                .then(CommandManager.argument("type", StringArgumentType.word())
                    .executes(CommandHandler::giveTower)))
            .then(CommandManager.literal("removetower")
                .executes(CommandHandler::removeTower))
            .then(CommandManager.literal("listtowers")
                .executes(CommandHandler::listTowers))
            .then(CommandManager.literal("cleartowers")
                .executes(CommandHandler::clearTowers))
            .then(CommandManager.literal("leaderboard")
                .executes(context -> showLeaderboard(context, "damage"))
                .then(CommandManager.argument("type", StringArgumentType.word())
                    .executes(context -> showLeaderboard(context,
                        StringArgumentType.getString(context, "type")))))
            .then(CommandManager.literal("scoreboard")
                .executes(CommandHandler::toggleScoreboard))
        );
    }

    private static int addChaos(CommandContext<ServerCommandSource> context) {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        ChaosManager chaosManager = ChaosMod.getChaosManager();
        chaosManager.addChaos(amount);

        context.getSource().sendFeedback(
            () -> Text.literal("§aAdded " + amount + " chaos. Current level: §e" + chaosManager.getChaosLevel()),
            true
        );

        return 1;
    }

    private static int setChaos(CommandContext<ServerCommandSource> context) {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        ChaosManager chaosManager = ChaosMod.getChaosManager();

        // Reset first, then add
        chaosManager.reset();
        if (amount > 0) {
            chaosManager.addChaos(amount);
        }

        context.getSource().sendFeedback(
            () -> Text.literal("§aChaos level set to: §e" + amount),
            true
        );

        return 1;
    }

    private static int resetChaos(CommandContext<ServerCommandSource> context) {
        ChaosManager chaosManager = ChaosMod.getChaosManager();
        chaosManager.reset();

        context.getSource().sendFeedback(
            () -> Text.literal("§aChaos level reset to 0"),
            true
        );

        return 1;
    }

    private static int getChaosLevel(CommandContext<ServerCommandSource> context) {
        ChaosManager chaosManager = ChaosMod.getChaosManager();
        int level = chaosManager.getChaosLevel();
        int total = chaosManager.getTotalChaos();
        double multiplier = chaosManager.getSpawnMultiplier();

        context.getSource().sendFeedback(
            () -> Text.literal("§e=== Chaos Stats ===\n" +
                "§aCurrent Level: §e" + level + "\n" +
                "§aTotal Accumulated: §e" + total + "\n" +
                "§aSpawn Multiplier: §e" + String.format("%.2f", multiplier) + "x"),
            false
        );

        return 1;
    }

    private static int forceWave(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            ChaosManager chaosManager = ChaosMod.getChaosManager();
            SpawnHandler spawnHandler = ChaosMod.getSpawnHandler();

            int chaosLevel = chaosManager.getChaosLevel();
            if (chaosLevel <= 0) {
                context.getSource().sendError(
                    Text.literal("§cChaos level is 0! Use '/chaos add <amount>' first.")
                );
                return 0;
            }

            spawnHandler.getWaveManager().forceStartWave(
                player.getServerWorld(),
                player,
                chaosLevel
            );

            context.getSource().sendFeedback(
                () -> Text.literal("§c§lForcing wave spawn with chaos level: §e" + chaosLevel),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§cError: " + e.getMessage()));
            ChaosMod.LOGGER.error("Error forcing wave", e);
            return 0;
        }
    }

    private static int setNight(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

            // Set time to 13000 (night start)
            player.getServerWorld().setTimeOfDay(13000);

            context.getSource().sendFeedback(
                () -> Text.literal("§aTime set to night (13000). Chaos waves will begin!"),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int setVillage(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            VillageManager villageManager = ChaosMod.getVillageManager();

            // Set village core at player's current position
            villageManager.setVillageCore(player.getBlockPos());

            context.getSource().sendFeedback(
                () -> Text.literal("§a§lVillage core set at: §e" + player.getBlockPos().toShortString() +
                    "\n§aCore HP: §e" + villageManager.getCoreHP() + "/" + villageManager.getMaxCoreHP()),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§cError: " + e.getMessage()));
            ChaosMod.LOGGER.error("Error setting village", e);
            return 0;
        }
    }

    private static int resetVillage(CommandContext<ServerCommandSource> context) {
        VillageManager villageManager = ChaosMod.getVillageManager();
        villageManager.resetVillage();

        context.getSource().sendFeedback(
            () -> Text.literal("§aVillage reset! Use '/chaos setvillage' to create a new village core."),
            true
        );

        return 1;
    }

    private static int getCoreHP(CommandContext<ServerCommandSource> context) {
        VillageManager villageManager = ChaosMod.getVillageManager();

        if (!villageManager.hasVillageCore()) {
            context.getSource().sendError(
                Text.literal("§cNo village core set! Use '/chaos setvillage' to create one.")
            );
            return 0;
        }

        int hp = villageManager.getCoreHP();
        int maxHP = villageManager.getMaxCoreHP();
        boolean gameOver = villageManager.isGameOver();

        String status = gameOver ? "§c§lDESTROYED" : "§a§lOK";

        context.getSource().sendFeedback(
            () -> Text.literal("§e=== Village Core Status ===\n" +
                "§aLocation: §e" + villageManager.getVillageCorePos().toShortString() + "\n" +
                "§aCore HP: §e" + hp + "/" + maxHP + "\n" +
                "§aStatus: " + status),
            false
        );

        return 1;
    }

    private static int cleanBossBars(CommandContext<ServerCommandSource> context) {
        try {
            // Remove all custom boss bars with chaosstream namespace
            var server = context.getSource().getServer();
            var manager = server.getBossBarManager();

            // Remove core HP bar
            var coreId = new net.minecraft.util.Identifier("chaosstream", "core_hp");
            var coreBar = manager.get(coreId);
            if (coreBar != null) {
                manager.remove(coreBar);
            }

            context.getSource().sendFeedback(
                () -> net.minecraft.text.Text.literal("§aAll Chaos Stream boss bars removed! They will be recreated automatically."),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(net.minecraft.text.Text.literal("§cError: " + e.getMessage()));
            ChaosMod.LOGGER.error("Error cleaning boss bars", e);
            return 0;
        }
    }

    private static int giveTower(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            String typeStr = StringArgumentType.getString(context, "type");

            // Parse tower type
            TowerType type = TowerType.fromString(typeStr);
            if (type == null) {
                context.getSource().sendError(
                    Text.literal("§cInvalid tower type! Valid types: archer, cannon")
                );
                return 0;
            }

            // Create and give tower item
            var towerItem = TowerItemHelper.createTowerItem(type);
            player.giveItemStack(towerItem);

            context.getSource().sendFeedback(
                () -> Text.literal("§a[TD] Given " + type.getDisplayName() + " Kit!")
                    .append("\n§eCost: §f" + TowerItemHelper.getCostDescription(type))
                    .append("\n§eRight-click on ground to place!"),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§cError: " + e.getMessage()));
            ChaosMod.LOGGER.error("Error giving tower", e);
            return 0;
        }
    }

    private static int removeTower(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            TowerManager towerManager = ChaosMod.getTowerManager();

            var pos = player.getBlockPos();

            // Check in 3x3 area around player
            boolean removed = false;
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    var checkPos = pos.add(x, 0, z);
                    if (towerManager.removeTower(checkPos)) {
                        // Remove blocks
                        var world = player.getServerWorld();
                        for (int y = 0; y <= 3; y++) {
                            world.removeBlock(checkPos.up(y), false);
                        }

                        context.getSource().sendFeedback(
                            () -> Text.literal("§a[TD] Tower removed at: " + checkPos.toShortString()),
                            true
                        );
                        removed = true;
                        break;
                    }
                }
                if (removed) break;
            }

            if (!removed) {
                context.getSource().sendError(
                    Text.literal("§c[TD] No tower found nearby! Stand next to a tower.")
                );
                return 0;
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§cError: " + e.getMessage()));
            ChaosMod.LOGGER.error("Error removing tower", e);
            return 0;
        }
    }

    private static int listTowers(CommandContext<ServerCommandSource> context) {
        TowerManager towerManager = ChaosMod.getTowerManager();
        var towers = towerManager.getAllTowers();

        if (towers.isEmpty()) {
            context.getSource().sendFeedback(
                () -> Text.literal("§e[TD] No towers placed yet!"),
                false
            );
            return 1;
        }

        StringBuilder message = new StringBuilder("§e=== Tower Defense - Towers ===\n");
        message.append("§aTotal Towers: §e").append(towers.size()).append("\n\n");

        int archerCount = 0;
        int cannonCount = 0;

        for (Tower tower : towers) {
            if (tower.getType() == TowerType.ARCHER) archerCount++;
            else if (tower.getType() == TowerType.CANNON) cannonCount++;
        }

        message.append("§aArcher Towers: §e").append(archerCount).append("\n");
        message.append("§aCannon Towers: §e").append(cannonCount).append("\n\n");

        message.append("§7Use /chaos removetower to remove a tower");

        String finalMessage = message.toString();
        context.getSource().sendFeedback(
            () -> Text.literal(finalMessage),
            false
        );

        return 1;
    }

    private static int clearTowers(CommandContext<ServerCommandSource> context) {
        try {
            TowerManager towerManager = ChaosMod.getTowerManager();
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

            var towers = towerManager.getAllTowers();
            int count = towers.size();

            // Remove tower blocks from world
            var world = player.getServerWorld();
            for (Tower tower : towers) {
                var pos = tower.getPosition();
                for (int y = 0; y <= 3; y++) {
                    world.removeBlock(pos.up(y), false);
                }
            }

            // Clear all towers
            towerManager.clearAllTowers();

            context.getSource().sendFeedback(
                () -> Text.literal("§a[TD] All towers cleared! (Removed " + count + " towers)"),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§cError: " + e.getMessage()));
            ChaosMod.LOGGER.error("Error clearing towers", e);
            return 0;
        }
    }

    private static int showLeaderboard(CommandContext<ServerCommandSource> context, String typeStr) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            StatsManager statsManager = ChaosMod.getStatsManager();

            // Parse type
            StatsManager.LeaderboardType type = StatsManager.LeaderboardType.fromString(typeStr);

            // Zeige Top 10
            statsManager.showLeaderboard(player, type, 10);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§cError: " + e.getMessage()));
            ChaosMod.LOGGER.error("Error showing leaderboard", e);
            return 0;
        }
    }

    private static int toggleScoreboard(CommandContext<ServerCommandSource> context) {
        try {
            ScoreboardManager scoreboardManager = ChaosMod.getScoreboardManager();
            var server = context.getSource().getServer();

            boolean enabled = scoreboardManager.toggle(server);

            String status = enabled ? "§aaktiviert" : "§cdeaktiviert";
            context.getSource().sendFeedback(
                () -> Text.literal("§6Live-Scoreboard " + status + "!"),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§cError: " + e.getMessage()));
            ChaosMod.LOGGER.error("Error toggling scoreboard", e);
            return 0;
        }
    }
}
