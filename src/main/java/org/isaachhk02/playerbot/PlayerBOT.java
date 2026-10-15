package org.isaachhk02.playerbot;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.isaachhk02.playerbot.ai.AIPlayerManager;
import org.isaachhk02.playerbot.commands.AIBotCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clase principal del mod Player BOT.
 * Crea y gestiona jugadores controlados por IA en Minecraft.
 */
public class PlayerBOT implements ModInitializer {
    public static final String MOD_ID = "player-bot";

    // Logger para el mod
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Instancia del gestor de bots
    private static AIPlayerManager botManager;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Player BOT mod...");

        // Registrar comandos
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AIBotCommands.register(dispatcher);
            LOGGER.info("Registered bot commands");
        });

        // Eventos del servidor
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (botManager != null) {
                botManager.syncBotsTo(handler.getPlayer());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        // Tick del servidor para actualizar bots
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        LOGGER.info("Player BOT mod initialized successfully!");
    }

    private void onServerStarted(MinecraftServer server) {
        LOGGER.info("Server started, initializing bot manager...");
        botManager = AIPlayerManager.getInstance(server);
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("Server stopping, cleaning up bots...");
        if (botManager != null) {
            botManager.removeAllBots();
        }
    }

    private void onServerTick(MinecraftServer server) {
        if (botManager != null) {
            botManager.tick();
        }
    }

    /**
     * Obtiene la instancia del gestor de bots
     */
    public static AIPlayerManager getBotManager() {
        return botManager;
    }

    /**
     * Crea un ResourceLocation para este mod
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
