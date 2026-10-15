package org.isaachhk02.playerbot.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIPlayerManager;
import org.isaachhk02.playerbot.ai.tasks.BuildTask;
import org.isaachhk02.playerbot.util.ProtectionManager;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Comandos para controlar los bots IA
 */
public class AIBotCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("bot")
                        .requires(source -> source.hasPermission(2)) // Requiere permiso de OP nivel 2

                        // /bot create <name> [gamemode]
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(AIBotCommands::createBot)
                                        .then(Commands.argument("gamemode", StringArgumentType.word())
                                                .suggests(AIBotCommands::suggestGameModes)
                                                .executes(AIBotCommands::createBotWithGameMode)
                                        )
                                )
                        )

                        // /bot createat <name> <pos> [gamemode]
                        .then(Commands.literal("createat")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(AIBotCommands::createBotAt)
                                                .then(Commands.argument("gamemode", StringArgumentType.word())
                                                        .suggests(AIBotCommands::suggestGameModes)
                                                        .executes(AIBotCommands::createBotAtWithGameMode)
                                                )
                                        )
                                )
                        )

                        // /bot remove <name>
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(AIBotCommands::suggestBotNames)
                                        .executes(AIBotCommands::removeBot)
                                )
                        )

                        // /bot removeall
                        .then(Commands.literal("removeall")
                                .executes(AIBotCommands::removeAllBots)
                        )

                        // /bot list
                        .then(Commands.literal("list")
                                .executes(AIBotCommands::listBots)
                        )

                        // /bot stats [name]
                        .then(Commands.literal("stats")
                                .executes(AIBotCommands::showAllStats)
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(AIBotCommands::suggestBotNames)
                                        .executes(AIBotCommands::showBotStats)
                                )
                        )

                        // /bot say <name> <message>
                        .then(Commands.literal("say")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(AIBotCommands::suggestBotNames)
                                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(AIBotCommands::botSay)
                                        )
                                )
                        )

                        // /bot follow <name> <player>
                        .then(Commands.literal("follow")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(AIBotCommands::suggestBotNames)
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(AIBotCommands::botFollow)
                                        )
                                )
                        )

                        // /bot stop <name>
                        .then(Commands.literal("stop")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(AIBotCommands::suggestBotNames)
                                        .executes(AIBotCommands::botStop)
                                )
                        )

                        // /bot stopall
                        .then(Commands.literal("stopall")
                                .executes(AIBotCommands::stopAllBots)
                        )

                        // /bot goto <name> <pos>
                        .then(Commands.literal("goto")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(AIBotCommands::suggestBotNames)
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(AIBotCommands::botGoto)
                                        )
                                )
                        )

                        // /bot attack <name> <entity>
                        .then(Commands.literal("attack")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(AIBotCommands::suggestBotNames)
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(AIBotCommands::botAttack)
                                        )
                                )
                        )

                        // /bot build <name> <type>
                        .then(Commands.literal("build")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(AIBotCommands::suggestBotNames)
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(AIBotCommands::suggestBuildTypes)
                                                .executes(AIBotCommands::botBuild)
                                        )
                                )
                        )

                        // /bot group <name1> <name2> [name3...]
                        .then(Commands.literal("group")
                                .then(Commands.argument("name1", StringArgumentType.word())
                                        .suggests(AIBotCommands::suggestBotNames)
                                        .then(Commands.argument("name2", StringArgumentType.word())
                                                .suggests(AIBotCommands::suggestBotNames)
                                                .executes(AIBotCommands::createGroup)
                                        )
                                )
                        )

                        // /bot tp <name> <pos>
                        .then(Commands.literal("tp")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(AIBotCommands::suggestBotNames)
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(AIBotCommands::botTeleport)
                                        )
                                )
                        )

                        // /bot come <name>  (hacer que venga al ejecutor)
                        .then(Commands.literal("come")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(AIBotCommands::suggestBotNames)
                                        .executes(AIBotCommands::botCome)
                                )
                        )

                        // /bot protect <pos1> <pos2> - Proteger un área
                        .then(Commands.literal("protect")
                                .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                        .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                .executes(AIBotCommands::protectArea)
                                        )
                                )
                        )

                        // /bot unprotect <pos1> <pos2> - Desproteger un área
                        .then(Commands.literal("unprotect")
                                .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                        .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                .executes(AIBotCommands::unprotectArea)
                                        )
                                )
                        )

                        // /bot check <pos> - Verificar si una posición está protegida
                        .then(Commands.literal("check")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(AIBotCommands::checkProtection)
                                )
                        )

                        // /bot mode <name> <passive|defensive|aggressive> - Cambiar modo de combate
                        .then(Commands.literal("mode")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(AIBotCommands::suggestBotNames)
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    builder.suggest("passive");    // No ataca, solo huye
                                                    builder.suggest("defensive");  // Solo se defiende (por defecto)
                                                    builder.suggest("aggressive"); // Ataca monstruos cercanos
                                                    return builder.buildFuture();
                                                })
                                                .executes(AIBotCommands::setBotMode)
                                        )
                                )
                        )

                        // /bot help
                        .then(Commands.literal("help")
                                .executes(AIBotCommands::showHelp)
                        )
        );
    }

    // ==================== IMPLEMENTACIÓN DE COMANDOS ====================

    private static int createBot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer player = source.getPlayerOrException();
            AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());

            // Verificar slots disponibles
            if (!manager.canCreateBot()) {
                source.sendFailure(Component.literal("§cCannot create bot: Server is full! (Max players: " +
                        manager.getTotalPlayerCount() + "/" + source.getServer().getPlayerList().getMaxPlayers() + ")"));
                return 0;
            }

            AIPlayer bot = manager.createBotNearPlayer(player, name);

            source.sendSuccess(() -> Component.literal("§aCreated bot: " + name + " §7(Occupying player slot: " +
                    manager.getTotalPlayerCount() + "/" + source.getServer().getPlayerList().getMaxPlayers() + ")"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cFailed to create bot: " + e.getMessage()));
            return 0;
        }
    }

    private static int createBotWithGameMode(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        String gameModeStr = StringArgumentType.getString(context, "gamemode");
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer player = source.getPlayerOrException();
            GameType gameType = parseGameType(gameModeStr);
            AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
            AIPlayer bot = manager.createBot(player.serverLevel(), player.blockPosition(), name, gameType);

            source.sendSuccess(() -> Component.literal("§aCreated bot: " + name + " in " + gameModeStr + " mode"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cFailed to create bot: " + e.getMessage()));
            return 0;
        }
    }

    private static int createBotAt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        CommandSourceStack source = context.getSource();

        try {
            // Validar longitud del nombre (max 16 caracteres en Minecraft)
            if (name.length() > 16) {
                source.sendFailure(Component.literal("§cBot name too long! Max 16 characters. Your name: " + name.length() + " chars"));
                return 0;
            }

            ServerLevel level = source.getLevel();
            AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());

            // Verificar slots disponibles
            if (!manager.canCreateBot()) {
                source.sendFailure(Component.literal("§cCannot create bot: Server is full! (Max players: " +
                        manager.getTotalPlayerCount() + "/" + source.getServer().getPlayerList().getMaxPlayers() + ")"));
                return 0;
            }

            AIPlayer bot = manager.createBot(level, pos, name);

            source.sendSuccess(() -> Component.literal("§aCreated bot: " + name + " at " + pos.toShortString() +
                    " §7(Slot: " + manager.getTotalPlayerCount() + "/" + source.getServer().getPlayerList().getMaxPlayers() + ")"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cFailed to create bot: " + e.getMessage()));
            return 0;
        }
    }

    private static int createBotAtWithGameMode(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        String gameModeStr = StringArgumentType.getString(context, "gamemode");
        CommandSourceStack source = context.getSource();

        try {
            ServerLevel level = source.getLevel();
            GameType gameType = parseGameType(gameModeStr);
            AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
            AIPlayer bot = manager.createBot(level, pos, name, gameType);

            source.sendSuccess(() -> Component.literal("§aCreated bot: " + name + " at " + pos.toShortString()), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cFailed to create bot: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeBot(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();

        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        boolean removed = manager.removeBotByName(name);

        if (removed) {
            source.sendSuccess(() -> Component.literal("§aRemoved bot: " + name), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("§cBot not found: " + name));
            return 0;
        }
    }

    private static int removeAllBots(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        int count = manager.getBotCount();
        manager.removeAllBots();

        source.sendSuccess(() -> Component.literal("§aRemoved all " + count + " bots"), true);
        return 1;
    }

    private static int listBots(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());

        Collection<AIPlayer> bots = manager.getAllBots();

        int totalPlayers = manager.getTotalPlayerCount();
        int maxPlayers = source.getServer().getPlayerList().getMaxPlayers();

        source.sendSuccess(() -> Component.literal("§6=== Server Players (" + totalPlayers + "/" + maxPlayers + ") ==="), false);

        // Listar jugadores humanos
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (!(player instanceof AIPlayer)) {
                source.sendSuccess(() -> Component.literal("§f- §a👤 " + player.getName().getString() +
                        " §7(Human)"), false);
            }
        }

        // Listar bots
        if (bots.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§eNo bots active"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§6=== Bots (" + bots.size() + ") ==="), false);
            for (AIPlayer bot : bots) {
                BlockPos pos = bot.blockPosition();
                source.sendSuccess(() -> Component.literal(String.format("§f- §b🤖 %s §7at %s (HP: %.1f) §8[BOT - occupies slot]",
                        bot.getName().getString(), pos.toShortString(), bot.getHealth())), false);
            }
        }

        source.sendSuccess(() -> Component.literal("§7Slots: " + totalPlayers + "/" + maxPlayers +
                " (Bots count as players)"), false);

        return totalPlayers;
    }

    private static int showAllStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());

        String stats = manager.getAllStats();
        source.sendSuccess(() -> Component.literal(stats), false);

        return 1;
    }

    private static int showBotStats(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();

        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        AIPlayer bot = manager.getBotByName(name);

        if (bot == null) {
            source.sendFailure(Component.literal("§cBot not found: " + name));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6" + bot.getBotStats()), false);
        return 1;
    }

    private static int botSay(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        String message = StringArgumentType.getString(context, "message");
        CommandSourceStack source = context.getSource();

        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        AIPlayer bot = manager.getBotByName(name);

        if (bot == null) {
            source.sendFailure(Component.literal("§cBot not found: " + name));
            return 0;
        }

        bot.forceSay(message);
        source.sendSuccess(() -> Component.literal("§a" + name + " says: " + message), false);
        return 1;
    }

    private static int botFollow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        CommandSourceStack source = context.getSource();

        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        AIPlayer bot = manager.getBotByName(name);

        if (bot == null) {
            source.sendFailure(Component.literal("§cBot not found: " + name));
            return 0;
        }

        bot.follow(target);
        source.sendSuccess(() -> Component.literal("§a" + name + " is now following " + target.getName().getString()), true);
        return 1;
    }

    private static int botStop(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();

        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        AIPlayer bot = manager.getBotByName(name);

        if (bot == null) {
            source.sendFailure(Component.literal("§cBot not found: " + name));
            return 0;
        }

        bot.stopAll();
        source.sendSuccess(() -> Component.literal("§a" + name + " stopped"), true);
        return 1;
    }

    private static int stopAllBots(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        manager.stopAllBots();

        source.sendSuccess(() -> Component.literal("§aAll bots stopped"), true);
        return 1;
    }

    private static int botGoto(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        CommandSourceStack source = context.getSource();

        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        AIPlayer bot = manager.getBotByName(name);

        if (bot == null) {
            source.sendFailure(Component.literal("§cBot not found: " + name));
            return 0;
        }

        bot.moveTo(pos);
        source.sendSuccess(() -> Component.literal("§a" + name + " is moving to " + pos.toShortString()), true);
        return 1;
    }

    private static int botAttack(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        Entity target = EntityArgument.getEntity(context, "target");
        CommandSourceStack source = context.getSource();

        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        AIPlayer bot = manager.getBotByName(name);

        if (bot == null) {
            source.sendFailure(Component.literal("§cBot not found: " + name));
            return 0;
        }

        bot.setAttackTarget(target);
        source.sendSuccess(() -> Component.literal("§a" + name + " is attacking " + target.getName().getString()), true);
        return 1;
    }

    private static int botBuild(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        String type = StringArgumentType.getString(context, "type");
        CommandSourceStack source = context.getSource();

        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        AIPlayer bot = manager.getBotByName(name);

        if (bot == null) {
            source.sendFailure(Component.literal("§cBot not found: " + name));
            return 0;
        }

        try {
            BuildTask.BuildPlan plan = BuildTask.BuildPlan.valueOf(type.toUpperCase());
            BuildTask task = new BuildTask(bot, plan);
            bot.forceTask(task);

            source.sendSuccess(() -> Component.literal("§a" + name + " is building: " + type), true);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("§cInvalid build type: " + type));
            return 0;
        }
    }

    private static int createGroup(CommandContext<CommandSourceStack> context) {
        String name1 = StringArgumentType.getString(context, "name1");
        String name2 = StringArgumentType.getString(context, "name2");
        CommandSourceStack source = context.getSource();

        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        manager.createBotGroup(name1, name2);

        source.sendSuccess(() -> Component.literal("§aCreated group: " + name1 + " & " + name2), true);
        return 1;
    }

    private static int botTeleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        CommandSourceStack source = context.getSource();

        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        AIPlayer bot = manager.getBotByName(name);

        if (bot == null) {
            source.sendFailure(Component.literal("§cBot not found: " + name));
            return 0;
        }

        bot.teleportTo(pos);
        source.sendSuccess(() -> Component.literal("§aTeleported " + name + " to " + pos.toShortString()), true);
        return 1;
    }

    private static int botCome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer player = source.getPlayerOrException();
            AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
            AIPlayer bot = manager.getBotByName(name);

            if (bot == null) {
                source.sendFailure(Component.literal("§cBot not found: " + name));
                return 0;
            }

            bot.moveTo(player.blockPosition());
            source.sendSuccess(() -> Component.literal("§a" + name + " is coming to you!"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cThis command must be run by a player"));
            return 0;
        }
    }

    private static int protectArea(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos1 = BlockPosArgument.getLoadedBlockPos(context, "pos1");
        BlockPos pos2 = BlockPosArgument.getLoadedBlockPos(context, "pos2");
        CommandSourceStack source = context.getSource();

        // Calcular min/max
        BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );

        ProtectionManager protection = ProtectionManager.getInstance();
        UUID ownerId = source.getPlayerOrException().getUUID();
        protection.addProtectedArea(ownerId, min, max);

        source.sendSuccess(() -> Component.literal(String.format(
                "§aProtected area from %s to %s. Bots will not build or mine here!",
                min.toShortString(), max.toShortString()
        )), true);
        return 1;
    }

    private static int unprotectArea(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos1 = BlockPosArgument.getLoadedBlockPos(context, "pos1");
        BlockPos pos2 = BlockPosArgument.getLoadedBlockPos(context, "pos2");
        CommandSourceStack source = context.getSource();

        BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );

        ProtectionManager protection = ProtectionManager.getInstance();
        protection.removeProtectedArea(min, max);

        source.sendSuccess(() -> Component.literal(String.format(
                "§aUnprotected area from %s to %s",
                min.toShortString(), max.toShortString()
        )), true);
        return 1;
    }

    private static int checkProtection(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        CommandSourceStack source = context.getSource();

        ProtectionManager protection = ProtectionManager.getInstance();
        boolean isProtected = protection.isProtected(source.getLevel(), pos, null);

        if (isProtected) {
            source.sendSuccess(() -> Component.literal("§cThis position is PROTECTED. Bots cannot modify it."), false);
        } else {
            source.sendSuccess(() -> Component.literal("§aThis position is NOT protected. Bots can build/mine here."), false);
        }

        return isProtected ? 0 : 1;
    }

    private static int setBotMode(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        String modeStr = StringArgumentType.getString(context, "mode");
        CommandSourceStack source = context.getSource();

        AIPlayerManager manager = AIPlayerManager.getInstance(source.getServer());
        AIPlayer bot = manager.getBotByName(name);

        if (bot == null) {
            source.sendFailure(Component.literal("§cBot not found: " + name));
            return 0;
        }

        try {
            AIPlayer.CombatMode mode = AIPlayer.CombatMode.valueOf(modeStr.toUpperCase());
            bot.setCombatMode(mode);
            source.sendSuccess(() -> Component.literal("§aSet " + name + " to " + modeStr + " mode"), true);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("§cInvalid mode: " + modeStr + ". Use: passive, defensive, or aggressive"));
            return 0;
        }
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal("§6=== Bot Commands ==="), false);
        source.sendSuccess(() -> Component.literal("§e/bot create <name> §7- Create a bot near you"), false);
        source.sendSuccess(() -> Component.literal("§e/bot createat <name> <pos> §7- Create a bot at position"), false);
        source.sendSuccess(() -> Component.literal("§e/bot remove <name> §7- Remove a bot"), false);
        source.sendSuccess(() -> Component.literal("§e/bot removeall §7- Remove all bots"), false);
        source.sendSuccess(() -> Component.literal("§e/bot list §7- List all bots"), false);
        source.sendSuccess(() -> Component.literal("§e/bot stats [name] §7- Show bot statistics"), false);
        source.sendSuccess(() -> Component.literal("§e/bot say <name> <msg> §7- Make a bot say something"), false);
        source.sendSuccess(() -> Component.literal("§e/bot follow <name> <player> §7- Make bot follow player"), false);
        source.sendSuccess(() -> Component.literal("§e/bot stop <name> §7- Stop a bot"), false);
        source.sendSuccess(() -> Component.literal("§e/bot stopall §7- Stop all bots"), false);
        source.sendSuccess(() -> Component.literal("§e/bot goto <name> <pos> §7- Move bot to position"), false);
        source.sendSuccess(() -> Component.literal("§e/bot attack <name> <entity> §7- Attack an entity"), false);
        source.sendSuccess(() -> Component.literal("§e/bot build <name> <type> §7- Build structure"), false);
        source.sendSuccess(() -> Component.literal("§e/bot group <name1> <name2> §7- Create bot group"), false);
        source.sendSuccess(() -> Component.literal("§e/bot tp <name> <pos> §7- Teleport bot"), false);
        source.sendSuccess(() -> Component.literal("§e/bot come <name> §7- Bot comes to you"), false);
        source.sendSuccess(() -> Component.literal("§6--- Protection Commands ---"), false);
        source.sendSuccess(() -> Component.literal("§e/bot protect <pos1> <pos2> §7- Protect an area from bots"), false);
        source.sendSuccess(() -> Component.literal("§e/bot unprotect <pos1> <pos2> §7- Remove area protection"), false);
        source.sendSuccess(() -> Component.literal("§e/bot check <pos> §7- Check if position is protected"), false);
        source.sendSuccess(() -> Component.literal("§e/bot mode <name> <mode> §7- Set combat mode (passive/defensive/aggressive)"), false);

        return 1;
    }

    // ==================== UTILIDADES ====================

    private static GameType parseGameType(String str) {
        return switch (str.toLowerCase()) {
            case "survival", "s" -> GameType.SURVIVAL;
            case "creative", "c" -> GameType.CREATIVE;
            case "adventure", "a" -> GameType.ADVENTURE;
            case "spectator", "sp" -> GameType.SPECTATOR;
            default -> GameType.SURVIVAL;
        };
    }

    private static CompletableFuture<Suggestions> suggestBotNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        AIPlayerManager manager = AIPlayerManager.getInstance(context.getSource().getServer());
        for (AIPlayer bot : manager.getAllBots()) {
            String name = bot.getName().getString().replace("[BOT] ", "");
            builder.suggest(name);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestGameModes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        builder.suggest("survival");
        builder.suggest("creative");
        builder.suggest("adventure");
        builder.suggest("spectator");
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestBuildTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (BuildTask.BuildPlan plan : BuildTask.BuildPlan.values()) {
            builder.suggest(plan.name().toLowerCase());
        }
        return builder.buildFuture();
    }
}
