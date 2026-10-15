package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.core.BlockPos;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;

import java.util.Random;

/**
 * Tarea de exploración: explora el área circundante buscando recursos y peligros
 */
public class ExploreTask extends AITask {
    private BlockPos exploreTarget;
    private int exploreTicks = 0;
    private int wanderDirection = 0;
    private final Random random = new Random();
    private static final int MAX_EXPLORE_TICKS = 600; // 30 segundos
    private static final double EXPLORE_RADIUS = 32.0;

    public ExploreTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = MAX_EXPLORE_TICKS;
    }

    @Override
    public void start() {
        super.start();
        exploreTicks = 0;
        wanderDirection = random.nextInt(360);
        chooseNewTarget();
    }

    @Override
    public void tick() {
        checkTimeout();
        exploreTicks++;

        if (exploreTarget == null || bot.blockPosition().closerThan(exploreTarget, 3)) {
            // Llegamos al objetivo o no hay objetivo, elegir uno nuevo
            if (exploreTicks > MAX_EXPLORE_TICKS / 4) {
                // Cambiar de dirección cada cierto tiempo
                wanderDirection = (wanderDirection + 90 + random.nextInt(90)) % 360;
            }
            chooseNewTarget();
        }

        if (exploreTarget != null) {
            // Moverse hacia el objetivo de exploración. La navegación ya orienta
            // al bot hacia donde camina, así que no giramos la cabeza al azar
            // (eso producía tirones).
            bot.getNavigation().moveTo(
                    exploreTarget.getX() + 0.5,
                    exploreTarget.getY(),
                    exploreTarget.getZ() + 0.5,
                    0.8 // Velocidad de exploración
            );
        }

        // La exploración NO se interrumpe al detectar recursos cercanos. Antes sí
        // lo hacía y, como el bot casi siempre tiene objetos tirados o minerales
        // cerca, terminaba la tarea cada pocos segundos y apenas se movía.
    }

    private void chooseNewTarget() {
        // Elegir un punto aleatorio dentro del radio de exploración
        double angle = Math.toRadians(wanderDirection);
        double distance = 10 + random.nextDouble() * (EXPLORE_RADIUS - 10);

        double targetX = bot.getX() + Math.cos(angle) * distance;
        double targetZ = bot.getZ() + Math.sin(angle) * distance;
        int targetY = bot.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) targetX, (int) targetZ);

        exploreTarget = BlockPos.containing(targetX, targetY, targetZ);
    }

    @Override
    public int getPriority() {
        // Prioridad baja, solo cuando no hay nada más que hacer
        return 25;
    }

    @Override
    public String getName() {
        return "Exploring " + (exploreTarget != null ? exploreTarget.toShortString() : "area");
    }

    @Override
    public AIState getState() {
        return AIState.EXPLORING;
    }

    @Override
    public boolean canExecute() {
        // Solo explorar si no hay enemigos y no está ocupado con otra cosa prioritaria
        return bot.getTarget() == null && bot.getFollowTarget() == null;
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
    }
}
