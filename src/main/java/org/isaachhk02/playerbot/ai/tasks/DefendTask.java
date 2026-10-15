package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;
import org.isaachhk02.playerbot.util.BotUtils;

/**
 * Tarea de defensa: se defiende de ataques usando escudo o esquivando.
 * NUNCA ataca de vuelta - solo se defiende pasivamente.
 */
public class DefendTask extends AITask {
    private Entity attacker;
    private int defendTicks = 0;
    private static final int MAX_DEFEND_TICKS = 100; // 5 segundos
    private boolean hasShield = false;

    public DefendTask(AIPlayer bot) {
        super(bot);
    }

    @Override
    public void start() {
        super.start();
        this.attacker = bot.getTarget();
        this.defendTicks = 0;

        // Verificar que el atacante sea un monstruo hostil (no un jugador/aliado)
        if (attacker instanceof net.minecraft.world.entity.player.Player) {
            // Si nos ataca un jugador, solo huir, no defenderse agresivamente
            bot.say("I won't fight you!");
            fail();
            return;
        }

        // Buscar escudo en el inventario
        hasShield = findShield();
        if (hasShield) {
            bot.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            bot.say("Blocking with shield!");
        } else {
            bot.say("No shield, evading!");
        }
    }

    @Override
    public void tick() {
        checkTimeout();
        defendTicks++;

        if (attacker == null || !attacker.isAlive() || defendTicks > MAX_DEFEND_TICKS) {
            complete();
            return;
        }

        // Mantener el escudo activo
        if (hasShield) {
            bot.startUsingItem(InteractionHand.OFF_HAND);
            // Mirar al atacante para bloquear usando BotUtils
            BotUtils.lookAtEntity(bot, attacker);
        } else {
            // Esquive: moverse perpendicular al atacante
            evade();
        }

        // Si la salud es baja, considerar huir en lugar de defenderse
        if (bot.getHealth() < bot.getMaxHealth() * 0.2f) {
            complete(); // Dejar que FleeTask tome el control
        }
    }

    private boolean findShield() {
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (stack.is(Items.SHIELD)) {
                return true;
            }
        }
        return false;
    }

    private void evade() {
        if (attacker == null) return;

        // Calcular dirección perpendicular al atacante
        double dx = bot.getX() - attacker.getX();
        double dz = bot.getZ() - attacker.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);

        if (length > 0) {
            // Dirección perpendicular
            double perpX = -dz / length;
            double perpZ = dx / length;

            // Moverse en esa dirección
            bot.getNavigation().moveTo(
                    bot.getX() + perpX * 3,
                    bot.getY(),
                    bot.getZ() + perpZ * 3,
                    1.0
            );
        }
    }

    @Override
    public int getPriority() {
        // Prioridad alta si nos están atacando y tenemos poca salud
        Entity target = bot.getTarget();
        if (target != null && bot.getHealth() < bot.getMaxHealth() * 0.5f) {
            return 85;
        }
        return 0;
    }

    @Override
    public String getName() {
        return "Defending" + (hasShield ? " with shield" : " by evading");
    }

    @Override
    public AIState getState() {
        return AIState.DEFENDING;
    }

    @Override
    public boolean canExecute() {
        Entity target = bot.getTarget();

        // Solo defenderse de monstruos hostiles, NO de jugadores
        if (target instanceof net.minecraft.world.entity.player.Player) {
            return false; // No defenderse de jugadores, mejor huir
        }

        // Verificar que sea un monstruo hostil
        if (!(target instanceof net.minecraft.world.entity.monster.Monster)) {
            return false;
        }

        return target != null && target.isAlive() && bot.getHealth() < bot.getMaxHealth() * 0.6f;
    }

    @Override
    public void stop() {
        bot.stopUsingItem();
    }
}
