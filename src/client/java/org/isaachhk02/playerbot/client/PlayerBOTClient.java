package org.isaachhk02.playerbot.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.isaachhk02.playerbot.PlayerBOT;
import org.lwjgl.glfw.GLFW;

/**
 * Inicializador del lado del cliente para el mod Player BOT.
 * Maneja atajos de teclado y funcionalidades del cliente.
 */
public class PlayerBOTClient implements ClientModInitializer {

    // Tecla para crear un bot rápidamente (por defecto: B)
    private static KeyMapping createBotKey;

    // Tecla para listar bots (por defecto: N)
    private static KeyMapping listBotsKey;

    @Override
    public void onInitializeClient() {
        PlayerBOT.LOGGER.info("Initializing Player BOT client...");

        // Registrar teclas de acceso rápido
        createBotKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.player-bot.create_bot",
                GLFW.GLFW_KEY_B,
                "key.categories.player-bot"
        ));

        listBotsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.player-bot.list_bots",
                GLFW.GLFW_KEY_N,
                "key.categories.player-bot"
        ));

        // Manejar eventos de teclado
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        PlayerBOT.LOGGER.info("Player BOT client initialized!");
    }

    private void onClientTick(Minecraft client) {
        if (client.player == null) return;

        // Tecla para crear bot
        if (createBotKey.consumeClick()) {
            // Enviar comando al servidor para crear un bot
            String botName = "Bot" + (int) (Math.random() * 1000);
            client.player.connection.sendCommand("bot create " + botName);
        }

        // Tecla para listar bots
        if (listBotsKey.consumeClick()) {
            client.player.connection.sendCommand("bot list");
        }
    }
}
