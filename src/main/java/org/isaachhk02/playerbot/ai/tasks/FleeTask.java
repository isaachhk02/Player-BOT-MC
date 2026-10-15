package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;
import org.isaachhk02.playerbot.util.BotUtils;

/**
 * Tarea de huida: huye de enemigos cuando la salud es baja
 */
public class FleeTask extends AITask {
    private Entity threat;
    private BlockPos fleeTarget;
    private int fleeTicks = 0;
    private static final int MAX_FLEE_TICKS = 200; // 10 segundos
    private static final double FLEE_DISTANCE = 16.0;

    public FleeTask(AIPlayer bot) {
        super(bot);
    }

    @Override
    public void start() {
        super.start();
        this.threat = bot.getTarget();
        this.fleeTicks = 0;

        if (threat != null) {
            bot.callForHelp(); // Pedir ayuda a aliados
            calculateFleePosition();
        } else {
            fail();
        }
    }

    @Override
    public void tick() {
        checkTimeout();
        fleeTicks++;

        if (threat == null || !threat.isAlive() || fleeTicks > MAX_FLEE_TICKS) {
            complete();
            return;
        }

        // Verificar si estamos a salvo
        double distanceToThreat = bot.distanceTo(threat);
        if (distanceToThreat > FLEE_DISTANCE * 1.5) {
            complete();
            return;
        }

        // Continuar huyendo
        if (fleeTarget == null || bot.blockPosition().closerThan(fleeTarget, 2)) {
            calculateFleePosition();
        }

        if (fleeTarget != null) {
            bot.getNavigation().moveTo(fleeTarget, 1.2);
        }

        // Curarse si tiene comida
        tryConsumeFood();
    }

    private void calculateFleePosition() {
        if (threat == null) return;

        // Calcular dirección opuesta a la amenaza
        Vec3 botPos = bot.position();
        Vec3 threatPos = threat.position();
        Vec3 fleeDir = botPos.subtract(threatPos).normalize();

        // Calcular punto de huida
        double targetX = botPos.x + fleeDir.x * FLEE_DISTANCE;
        double targetZ = botPos.z + fleeDir.z * FLEE_DISTANCE;

        fleeTarget = BlockPos.containing(targetX, botPos.y, targetZ);
    }

    private void tryConsumeFood() {
        if (bot.getHealth() < bot.getMaxHealth() * 0.7f) {
            // Buscar comida en el inventario usando BotUtils
            for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
                var stack = bot.getInventory().getItem(i);
                if (BotUtils.isFood(stack)) {
                    bot.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                    bot.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
                    break;
                }
            }
        }
    }

    @Override
    public int getPriority() {
        Entity threat = bot.getTarget();
        if (threat != null && bot.getHealth() < bot.getMaxHealth() * 0.25f) {
            return 100; // Máxima prioridad
        }
        return 0;
    }

    @Override
    public String getName() {
        return "Fleeing from " + (threat != null ? threat.getName().getString() : "danger");
    }

    @Override
    public AIState getState() {
        return AIState.FLEEING;
    }

    @Override
    public boolean canExecute() {
        Entity threat = bot.getTarget();
        return threat != null && threat.isAlive() && bot.getHealth() < bot.getMaxHealth() * 0.3f;
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
        bot.stopUsingItem();
    }
}
