package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;
import org.isaachhk02.playerbot.util.BotUtils;
import org.isaachhk02.playerbot.util.ProtectionManager;

/**
 * Tarea de combate: ataca SOLO a monstruos hostiles del juego.
 * NUNCA ataca a jugadores humanos ni a bots aliados.
 */
public class FightTask extends AITask {
    private Entity target;
    private int attackCooldown = 0;
    private static final int ATTACK_COOLDOWN_TICKS = 10; // 0.5 segundos
    private static final double ATTACK_RANGE = 3.0;
    private static final double CHASE_RANGE = 20.0;

    public FightTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = 1200; // 60 segundos máximo en combate
    }

    @Override
    public void start() {
        super.start();
        this.target = bot.getTarget();

        // VERIFICACIÓN CRÍTICA: Solo atacar monstruos hostiles
        if (!isValidTarget(target)) {
            fail();
            return;
        }

        equipWeapon();
    }

    @Override
    public void tick() {
        checkTimeout();

        // Verificar que el objetivo siga siendo válido
        if (!isValidTarget(target)) {
            complete();
            return;
        }

        if (target == null || !target.isAlive()) {
            complete();
            bot.say("Enemy defeated!");
            return;
        }

        double distance = bot.distanceTo(target);

        // Si está muy lejos, dejar de perseguir
        if (distance > CHASE_RANGE * 2) {
            fail();
            return;
        }

        // Mirar al objetivo usando BotUtils
        BotUtils.lookAtEntity(bot, target);

        if (distance > ATTACK_RANGE) {
            // Acercarse al objetivo
            bot.getNavigation().moveTo(target, 1.2);
        } else {
            // Atacar
            if (--attackCooldown <= 0) {
                if (performAttack()) {
                    attackCooldown = ATTACK_COOLDOWN_TICKS;
                } else {
                    // Todavía recargando la barra de ataque: reintentar pronto.
                    attackCooldown = 1;
                }
            }
            bot.getNavigation().stop();
        }
    }

    /**
     * VERIFICACIÓN CRÍTICA: Solo monstruos hostiles son objetivos válidos.
     */
    private boolean isValidTarget(Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }

        // Usar el ProtectionManager para validar
        ProtectionManager protection = ProtectionManager.getInstance();
        return protection.isValidAttackTarget(bot, entity);
    }

    private void equipWeapon() {
        BotUtils.equipToMainHand(bot, findBestWeaponSlot());
    }

    private int findBestWeaponSlot() {
        // Si no tiene espada, devuelve -1 (pelea con la mano): no se conjura ninguna.
        int bestSlot = -1;
        float bestDamage = 0.0f;

        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (stack.getItem() instanceof net.minecraft.world.item.SwordItem) {
                // En 1.21.1, simplemente preferimos espadas de mejor material
                String itemName = stack.getItem().toString().toLowerCase();
                float damage = 3.0f; // Daño base

                if (itemName.contains("diamond")) damage = 7.0f;
                else if (itemName.contains("iron")) damage = 6.0f;
                else if (itemName.contains("stone")) damage = 5.0f;
                else if (itemName.contains("golden")) damage = 4.0f;
                else if (itemName.contains("wooden")) damage = 4.0f;

                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    /**
     * Realiza un golpe cuerpo a cuerpo REAL (daño de vanilla).
     *
     * @return true si el golpe se aplicó, false si la barra de ataque aún no
     *         estaba cargada (en ese caso conviene reintentar en unos ticks).
     */
    private boolean performAttack() {
        if (!(target instanceof LivingEntity livingTarget)) {
            return false;
        }

        // Igual que un jugador real: hay que esperar a que la barra de ataque se
        // llene antes de golpear, o el daño se escala a la baja (getAttackStrengthScale).
        if (bot.getAttackStrengthScale(0.5f) < 1.0f) {
            return false;
        }

        // Animación de brazo (envía el paquete de swing a quien lo vea).
        bot.swing(InteractionHand.MAIN_HAND);

        // Ataque real: ServerPlayer.attack -> Player.attack, que aplica el daño,
        // el empuje y dispara los eventos correspondientes. Antes esto estaba
        // sombreado por AIPlayer.attack(Entity) y no hacía daño.
        bot.attack(livingTarget);

        // Efecto visual
        bot.level().playSound(null, bot.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    @Override
    public int getPriority() {
        // En modo pasivo, prioridad 0 (nunca ejecutar)
        if (bot.getCombatMode() == AIPlayer.CombatMode.PASSIVE) {
            return 0;
        }

        Entity target = bot.getTarget();

        // Solo alta prioridad si el objetivo es un monstruo hostil válido
        if (isValidTarget(target) && bot.distanceTo(target) < 20) {
            // En modo defensivo, solo prioridad si ya estamos en combate
            if (bot.getCombatMode() == AIPlayer.CombatMode.DEFENSIVE && !bot.isInCombat()) {
                return 0;
            }
            return 90; // Alta prioridad en combate contra monstruos
        }
        return 0;
    }

    @Override
    public String getName() {
        return "Fighting " + (target != null ? target.getName().getString() : "unknown");
    }

    @Override
    public AIState getState() {
        return AIState.FIGHTING;
    }

    @Override
    public boolean canExecute() {
        // Verificar modo de combate del bot
        AIPlayer.CombatMode mode = bot.getCombatMode();

        // En modo pasivo, nunca atacar
        if (mode == AIPlayer.CombatMode.PASSIVE) {
            return false;
        }

        Entity target = bot.getTarget();

        // SOLO ejecutar si el objetivo es un monstruo hostil válido
        if (!isValidTarget(target)) {
            return false;
        }

        // En modo defensivo, solo atacar si ya estamos en combate (nos atacaron primero)
        if (mode == AIPlayer.CombatMode.DEFENSIVE && !bot.isInCombat()) {
            return false;
        }

        // No atacar si es un jugador o aliado
        if (target instanceof Player player) {
            if (player instanceof AIPlayer aiPlayer && bot.isAlly(aiPlayer)) {
                return false; // No atacar aliados
            }
            return false; // No atacar jugadores humanos
        }

        return target.isAlive() && bot.distanceTo(target) < CHASE_RANGE;
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
    }
}
