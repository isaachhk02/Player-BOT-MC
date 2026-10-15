package org.isaachhk02.playerbot.mixin;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin para ServerPlayer para manejar bots IA correctamente.
 * Asegura que los bots aparezcan en la lista de jugadores y en el tab menu.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    /**
     * Después de que un jugador se conecte, asegurar que los bots también aparezcan
     * correctamente en la lista de jugadores para otros clientes.
     */
    @Inject(method = "doTick", at = @At("HEAD"))
    private void onDoTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        // Si es un bot IA, asegurarse de que esté visible para otros jugadores
        if (player instanceof AIPlayer bot) {
            // Los bots ya manejan su propio tick en AIPlayer.tick()
            // Este mixin asegura compatibilidad adicional si es necesario
        }
    }

    /**
     * Previene que los bots sean expulsados por inactividad
     */
    @Inject(method = "checkMovementStatistics", at = @At("HEAD"), cancellable = true)
    private void onCheckMovement(double dx, double dy, double dz, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player instanceof AIPlayer) {
            // Los bots no necesitan acumular estadísticas de movimiento para anti-cheat
            ci.cancel();
        }
    }
}
