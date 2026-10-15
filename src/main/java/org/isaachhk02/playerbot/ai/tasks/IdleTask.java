package org.isaachhk02.playerbot.ai.tasks;

import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;

import java.util.Random;

/**
 * Tarea de inactividad: el bot no tiene nada prioritario que hacer. En lugar de
 * quedarse clavado en el sitio (antes saltaba y giraba solo el cuerpo, lo que
 * parecía un bot roto "moviendo la cabeza"), da pequeños paseos y mira alrededor
 * de forma natural.
 */
public class IdleTask extends AITask {
    private int idleTicks = 0;
    private final Random random = new Random();

    // Cada cuánto da un pequeño paseo mientras está inactivo.
    private static final int WANDER_INTERVAL = 80;

    private static final String[] IDLE_PHRASES = {
            "Hmm...",
            "What should I do next?",
            "Just chilling.",
            "Beautiful day!"
    };

    public IdleTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = 200; // 10 segundos de inactividad máxima
    }

    @Override
    public void start() {
        super.start();
        idleTicks = 0;
        bot.getNavigation().stop();
    }

    @Override
    public void tick() {
        idleTicks++;

        // Mirar alrededor de forma natural. Es imprescindible girar cuerpo Y
        // cabeza: si solo se gira el cuerpo, el cliente deja la cabeza mirando a
        // otro lado y el bot parece atascado.
        if (idleTicks % 60 == 0) {
            float yaw = random.nextFloat() * 360f;
            bot.setYRot(yaw);
            bot.setYHeadRot(yaw);
            bot.setXRot(random.nextFloat() * 40f - 20f);
        }

        // Charlar muy de vez en cuando (casi nunca).
        if (random.nextInt(4000) == 0) {
            bot.say(IDLE_PHRASES[random.nextInt(IDLE_PHRASES.length)]);
        }

        // Dar un paseo corto para no quedarse parado en el mismo bloque.
        if (idleTicks % WANDER_INTERVAL == 1) {
            wander();
        }

        // Si ha estado inactivo mucho tiempo, buscar algo que hacer.
        if (idleTicks > 160) {
            complete(); // Forzar reevaluación de tareas
        }
    }

    private void wander() {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 4.0 + random.nextDouble() * 6.0;
        double targetX = bot.getX() + Math.cos(angle) * distance;
        double targetZ = bot.getZ() + Math.sin(angle) * distance;
        int targetY = bot.level().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(targetX), (int) Math.floor(targetZ));
        bot.getNavigation().moveTo(targetX, targetY, targetZ, 0.6);
    }

    @Override
    public int getPriority() {
        // Prioridad más baja, solo cuando no hay nada más que hacer
        return 0;
    }

    @Override
    public String getName() {
        return "Idling";
    }

    @Override
    public AIState getState() {
        return AIState.IDLE;
    }

    @Override
    public boolean canExecute() {
        // Siempre puede ejecutarse como último recurso
        return true;
    }

    @Override
    public void stop() {
        bot.setShiftKeyDown(false);
    }
}
