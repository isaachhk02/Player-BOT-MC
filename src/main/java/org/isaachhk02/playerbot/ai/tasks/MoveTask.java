package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;

/**
 * Tarea de movimiento: mueve al bot hacia una posición objetivo
 */
public class MoveTask extends AITask {
    private BlockPos destination;
    private int repathTicks = 0;
    private static final int REPATH_INTERVAL = 20;
    private static final double ARRIVAL_DISTANCE = 1.5;

    public MoveTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = 600; // 30 segundos máximo
    }

    @Override
    public void start() {
        super.start();
        this.destination = bot.getTargetPos();
        if (destination == null) {
            fail();
            return;
        }
    }

    @Override
    public void tick() {
        if (destination == null) {
            fail();
            return;
        }

        checkTimeout();

        // Verificar si llegamos
        Vec3 botPos = bot.position();
        double distance = Math.sqrt(
                Math.pow(botPos.x - (destination.getX() + 0.5), 2) +
                Math.pow(botPos.y - destination.getY(), 2) +
                Math.pow(botPos.z - (destination.getZ() + 0.5), 2)
        );

        if (distance < ARRIVAL_DISTANCE) {
            bot.getNavigation().stop();
            complete();
            return;
        }

        // Moverse hacia el objetivo usando BotNavigation
        bot.getNavigation().moveTo(destination, 1.0);
    }

    @Override
    public int getPriority() {
        return bot.getTargetPos() != null ? 30 : 0;
    }

    @Override
    public String getName() {
        return "Moving to " + (destination != null ? destination.toShortString() : "unknown");
    }

    @Override
    public AIState getState() {
        return AIState.MOVING;
    }

    @Override
    public boolean canExecute() {
        return bot.getTargetPos() != null;
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
    }
}
