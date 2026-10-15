package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.world.entity.player.Player;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;
import org.isaachhk02.playerbot.util.BotUtils;

/**
 * Tarea de seguimiento: sigue a un jugador humano u otro bot
 */
public class FollowTask extends AITask {
    private Player followTarget;
    private static final double FOLLOW_DISTANCE = 3.0;
    private static final double STOP_DISTANCE = 1.5;
    private static final double MAX_FOLLOW_DISTANCE = 32.0;
    private int checkTicks = 0;

    public FollowTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = Integer.MAX_VALUE; // Puede durar indefinidamente
    }

    @Override
    public void start() {
        super.start();
        this.followTarget = bot.getFollowTarget();
        if (followTarget == null) {
            fail();
            return;
        }
        bot.say("Following " + followTarget.getName().getString() + "!");
    }

    @Override
    public void tick() {
        if (followTarget == null || !followTarget.isAlive()) {
            bot.say("Stop following.");
            complete();
            return;
        }

        // Verificar cada 10 ticks si el objetivo sigue siendo válido
        if (++checkTicks % 10 == 0) {
            if (bot.getFollowTarget() != followTarget) {
                followTarget = bot.getFollowTarget();
                if (followTarget == null) {
                    complete();
                    return;
                }
            }
        }

        double distance = bot.distanceTo(followTarget);

        // Si está muy lejos, correr más rápido
        if (distance > MAX_FOLLOW_DISTANCE) {
            bot.getNavigation().moveTo(followTarget, 1.5);
            return;
        }

        // Si está cerca, detenerse
        if (distance < STOP_DISTANCE) {
            bot.getNavigation().stop();
            // Mirar al jugador usando BotUtils
            BotUtils.lookAtEntity(bot, followTarget);
            return;
        }

        // Si está a distancia media, caminar
        if (distance < FOLLOW_DISTANCE) {
            bot.getNavigation().stop();
        } else {
            // Seguir al objetivo
            bot.getNavigation().moveTo(followTarget, 1.0);
        }
    }

    private void interactWithFollowed() {
        // Occasionalmente decir algo o ayudar
        String[] messages = {
                "I'm right behind you!",
                "Need any help?",
                "Watch your back!",
                "I've got your six!"
        };
        bot.say(messages[(int) (Math.random() * messages.length)]);
    }

    public void setFollowTarget(Player target) {
        this.followTarget = target;
    }

    @Override
    public int getPriority() {
        return bot.getFollowTarget() != null ? 40 : 0;
    }

    @Override
    public String getName() {
        return "Following " + (followTarget != null ? followTarget.getName().getString() : "unknown");
    }

    @Override
    public AIState getState() {
        return AIState.FOLLOWING;
    }

    @Override
    public boolean canExecute() {
        return bot.getFollowTarget() != null;
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
    }
}
