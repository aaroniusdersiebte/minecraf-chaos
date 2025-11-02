package com.chaosstream;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

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
}
