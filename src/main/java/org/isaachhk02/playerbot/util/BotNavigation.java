package org.isaachhk02.playerbot.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.isaachhk02.playerbot.ai.AIPlayer;

/**
 * Sistema de navegación para bots.
 *
 * En lugar de teletransportar al bot (lo que atravesaba los bloques), esta clase
 * fija los ejes de movimiento (xxa / zza) y la rotación del bot, y deja que la
 * física de vanilla (LivingEntity.travel -> Entity.move) haga el trabajo: aplica
 * gravedad, resuelve colisiones, desliza contra las paredes y sube escalones.
 */
public class BotNavigation {
    private final AIPlayer bot;

    private Vec3 targetPos;
    private double speed;
    private boolean isMoving;
    private int jumpCooldown = 0;

    public BotNavigation(AIPlayer bot) {
        this.bot = bot;
    }

    /**
     * Mueve el bot hacia una posición específica.
     */
    public void moveTo(double x, double y, double z, double speed) {
        this.targetPos = new Vec3(x, y, z);
        this.speed = speed;
        this.isMoving = true;
    }

    /**
     * Mueve el bot hacia una entidad.
     */
    public void moveTo(Entity target, double speed) {
        if (target == null) {
            stop();
            return;
        }
        moveTo(target.getX(), target.getY(), target.getZ(), speed);
    }

    /**
     * Mueve el bot hacia una posición de bloque.
     */
    public void moveTo(BlockPos pos, double speed) {
        moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
    }

    /**
     * Detiene el movimiento del bot.
     */
    public void stop() {
        this.isMoving = false;
        this.targetPos = null;
        resetInput();
    }

    private void resetInput() {
        bot.xxa = 0.0f;
        bot.zza = 0.0f;
        if (bot.isSprinting()) {
            bot.setSprinting(false);
        }
    }

    /**
     * Distancia horizontal (ignora la altura) hasta el objetivo.
     */
    private double horizontalDistanceToTarget() {
        if (targetPos == null) {
            return Double.MAX_VALUE;
        }
        double dx = targetPos.x - bot.getX();
        double dz = targetPos.z - bot.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Verifica si el bot ya llegó al objetivo.
     */
    public boolean hasArrived(double distance) {
        if (targetPos == null) {
            return true;
        }
        double dy = Math.abs(targetPos.y - bot.getY());
        return horizontalDistanceToTarget() < distance && dy <= 2.5;
    }

    /**
     * Verifica si el bot sigue en movimiento.
     */
    public boolean isMoving() {
        return isMoving && targetPos != null && !hasArrived(1.0);
    }

    /**
     * Se llama cada tick desde AIPlayer.tick(). Fija el giro y los ejes de
     * movimiento para que la física de vanilla mueva al bot en el siguiente tick.
     */
    public void tick() {
        if (!isMoving || targetPos == null) {
            resetInput();
            handleSwimming();
            return;
        }

        if (hasArrived(0.6)) {
            stop();
            handleSwimming();
            return;
        }

        faceTarget();

        // Correr solo cuando se pide una velocidad alta.
        boolean sprint = speed >= 1.3;
        if (bot.isSprinting() != sprint) {
            bot.setSprinting(sprint);
        }

        // Avanzar. Vanilla aplica la velocidad de movimiento y resuelve colisiones.
        bot.xxa = 0.0f;
        bot.zza = 1.0f;

        // Si choca horizontalmente y está en el suelo, saltar para subir
        // obstáculos, como mucho una vez cada medio segundo para no ir
        // rebotando sin parar contra las paredes.
        if (jumpCooldown > 0) {
            jumpCooldown--;
        }
        if (jumpCooldown <= 0 && bot.horizontalCollision && bot.onGround()) {
            bot.jumpFromGround();
            jumpCooldown = 10;
        }

        // Nadar (flotar, subir y adoptar la postura de natación).
        handleSwimming();
    }

    /**
     * Comportamiento en el agua.
     *
     * Los bots no tienen un cliente que pulse el botón de salto, así que sin esto
     * se hundían hasta el fondo y acababan ahogándose (por eso morían con "drowned").
     * Aquí replicamos lo que hace un jugador real al nadar, dejando que la física de
     * vanilla haga el trabajo:
     *
     *  - Si está sumergido (o necesita ir hacia arriba), mantiene "salto" pulsado.
     *    En vanilla eso dispara LivingEntity.aiStep() -> jumpInLiquid(WATER), que
     *    añade empuje vertical y hace subir al bot hasta la superficie.
     *  - Corre mientras está bajo el agua para que Entity.updateSwimming() active
     *    la postura/animación de natación (además reduce la fricción del agua).
     */
    private void handleSwimming() {
        if (!bot.isInWater()) {
            // En tierra soltamos el salto para no provocar saltos automáticos.
            bot.setJumping(false);
            return;
        }

        boolean needToGoUp = bot.isUnderWater()
                || isMoving
                || (targetPos != null && targetPos.y > bot.getY() + 0.5);

        // jumpInLiquid solo actúa si el cuerpo está dentro del agua.
        boolean bodyInWater = bot.getFluidHeight(FluidTags.WATER) > 0.0;
        bot.setJumping(needToGoUp && bodyInWater);

        if (bot.isUnderWater()) {
            // Correr bajo el agua => postura de natación (Entity.updateSwimming).
            bot.setSprinting(true);
        }
    }

    private void faceTarget() {
        double dx = targetPos.x - bot.getX();
        double dz = targetPos.z - bot.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
        // Actualizar las rotaciones previas para que el cliente interpole suavemente.
        bot.yRotO = yaw;
        bot.yHeadRotO = yaw;
    }

    /**
     * Hace que el bot haga swing (animación de ataque/minar).
     * Vanilla diffuse el paquete de animación automáticamente.
     */
    public void swingArm() {
        bot.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * Hace que el bot use el item en la mano.
     */
    public void useItem() {
        bot.startUsingItem(InteractionHand.MAIN_HAND);
    }

    /**
     * Hace que el bot deje de usar el item en la mano.
     */
    public void stopUsingItem() {
        bot.stopUsingItem();
    }
}
