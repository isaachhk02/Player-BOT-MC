package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;
import org.isaachhk02.playerbot.PlayerBOT;
import org.isaachhk02.playerbot.util.BotUtils;
import org.isaachhk02.playerbot.util.ProtectionManager;

/**
 * Tarea de escape: cuando el bot se queda atrapado (en un hoyo, una zanja o bajo
 * tierra) rompe los bloques necesarios para salir.
 *
 * Va construyendo una "escalera" hacia arriba en la dirección en la que mira:
 *  - rompe el techo sobre su cabeza para poder saltar,
 *  - rompe los bloques por encima del bloque al que va a subir,
 *  - y avanza; la física de vanilla se encarga del salto sobre el escalón.
 *
 * Si la columna de al lado está despejada a la altura de los pies simplemente
 * avanza sin cavar. Repite hasta que deja de estar atascado.
 */
public class EscapeTask extends AITask {
    private static final int MAX_ESCAPE_TICKS = 600; // 30 s de margen
    // Una vez empieza a cavar, no abandona hasta pasar este tiempo: si no, cada
    // pequeño escalón (progreso momentáneo) la haría terminar y volvería a
    // quedarse atascado al instante, alternando tareas sin salir del pozo.
    private static final int MIN_ESCAPE_TICKS = 120;

    private Direction dir;
    private int stepCooldown = 0;
    private int escapeTicks = 0;

    public EscapeTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = MAX_ESCAPE_TICKS;
    }

    @Override
    public void start() {
        super.start();
        bot.getNavigation().stop();
        dir = bot.getDirection();
        stepCooldown = 0;
        escapeTicks = 0;
    }

    @Override
    public void tick() {
        checkTimeout();
        escapeTicks++;

        // Si ya no está atascado (consiguió salir), terminamos, pero no antes de
        // haber intentado salir un mínimo de tiempo.
        if (!bot.isStuck() && escapeTicks >= MIN_ESCAPE_TICKS) {
            bot.getNavigation().stop();
            complete();
            return;
        }

        if (stepCooldown > 0) {
            stepCooldown--;
            return;
        }

        if (dir == null) {
            dir = bot.getDirection();
        }

        // Si el bloque que hay que romper está protegido, no podemos cavar por
        // ahí: giramos para buscar otra salida.
        if (!canDig()) {
            dir = dir.getClockWise();
            return;
        }

        BlockPos feet = bot.blockPosition();
        BlockPos ahead = feet.relative(dir);
        boolean stepUp = isSolid(ahead);           // ¿muro/escalón que superar?
        BlockPos target = stepUp ? ahead.above() : ahead;

        // 1) Hacer hueco sobre la cabeza (hace falta para saltar o avanzar).
        if (breakIfSolid(feet.above(2))) {
            stepCooldown = 2;
            return;
        }
        // 2) Hacer hueco en la posición destino y justo por encima.
        if (breakIfSolid(target)) {
            stepCooldown = 2;
            return;
        }
        if (breakIfSolid(target.above())) {
            stepCooldown = 2;
            return;
        }

        // 3) Avanzar hacia el hueco. La física resuelve el salto si hay escalón.
        bot.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
        stepCooldown = 6;
    }

    /**
     * Comprueba que los bloques que necesitamos romper este tick se pueden
     * modificar. La tarea de escape puede saltarse la protección del spawn (si
     * no, un bot atrapado cerca del spawn nunca podría salir), pero sigue
     * respetando las construcciones y claims de jugadores.
     */
    private boolean canDig() {
        ProtectionManager protection = ProtectionManager.getInstance();
        BlockPos feet = bot.blockPosition();
        BlockPos ahead = feet.relative(dir);
        boolean stepUp = isSolid(ahead);
        BlockPos target = stepUp ? ahead.above() : ahead;

        return canBreak(feet.above(2), protection)
                && canBreak(target, protection)
                && canBreak(target.above(), protection);
    }

    private boolean canBreak(BlockPos pos, ProtectionManager protection) {
        BlockState state = bot.level().getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            return true; // ya está libre, no hay nada que romper
        }
        return protection.canModifyBlockAllowSpawn(bot.serverLevel(), pos, bot);
    }

    /**
     * Rompe el bloque si es sólido. Devuelve true si ha hecho algo en este tick
     * (para esperar antes de continuar).
     */
    private boolean breakIfSolid(BlockPos pos) {
        BlockState state = bot.level().getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            return false;
        }

        BotUtils.lookAt(bot, Vec3.atCenterOf(pos));
        bot.swing(InteractionHand.MAIN_HAND);
        bot.gameMode.destroyBlock(pos);
        PlayerBOT.LOGGER.info("Bot {} dug {} while escaping at {}",
                bot.getName().getString(), pos.toShortString(), bot.blockPosition().toShortString());
        return true;
    }

    /**
     * Indica si una posición tiene colisión (es un bloque sólido donde el bot
     * podría pisar o contra el que choca).
     */
    private boolean isSolid(BlockPos pos) {
        BlockState state = bot.level().getBlockState(pos);
        return !state.getCollisionShape(bot.level(), pos).isEmpty();
    }

    @Override
    public int getPriority() {
        // Por encima de minar/recolectar/construir, por debajo de huir (100) y
        // luchar (90): sales primero si te están pegando, pero escapar manda
        // sobre el trabajo normal.
        return 95;
    }

    @Override
    public String getName() {
        return "Escaping at " + bot.blockPosition().toShortString();
    }

    @Override
    public AIState getState() {
        return AIState.MOVING;
    }

    @Override
    public boolean canExecute() {
        // Solo cuando el bot está realmente atascado.
        return bot.isStuck();
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
    }
}
