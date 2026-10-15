package org.isaachhk02.playerbot.network;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Conexion dummy para bots IA.
 * Extiende ServerGamePacketListenerImpl pero no envia paquetes reales.
 * Esto evita NullPointerException cuando el servidor intenta comunicarse con el bot.
 */
public class BotConnection extends ServerGamePacketListenerImpl {

    public BotConnection(MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        super(server, connection, player, cookie);
    }

    @Override
    public void send(Packet<?> packet) {
        // No enviar nada - los bots no tienen cliente real
    }

    /**
     * Crea una conexion dummy para un bot
     */
    public static BotConnection createDummy(MinecraftServer server, ServerPlayer player, ServerLevel level) {
        try {
            // Crear una conexion de red dummy
            Connection dummyConnection = new Connection(PacketFlow.SERVERBOUND);
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
            return new BotConnection(server, dummyConnection, player, cookie);
        } catch (Exception e) {
            // Si falla, devolver null y manejar en el llamador
            return null;
        }
    }
}
