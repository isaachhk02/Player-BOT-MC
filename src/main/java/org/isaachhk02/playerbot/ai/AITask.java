package org.isaachhk02.playerbot.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Clase base abstracta para todas las tareas de la IA.
 * Cada tarea representa una acción específica que el bot puede realizar.
 */
public abstract class AITask {
    protected final AIPlayer bot;
    protected boolean completed = false;
    protected boolean failed = false;
    protected int timeoutTicks = 0;
    protected int maxTimeoutTicks = 200; // 10 segundos por defecto

    public AITask(AIPlayer bot) {
        this.bot = bot;
    }

    /**
     * Se ejecuta cada tick para actualizar la tarea
     */
    public abstract void tick();

    /**
     * Se ejecuta cuando la tarea comienza
     */
    public void start() {
        this.completed = false;
        this.failed = false;
        this.timeoutTicks = 0;
    }

    /**
     * Se ejecuta cuando la tarea termina
     */
    public void stop() {
        // Limpieza opcional
    }

    /**
     * Verifica si la tarea está completada
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Verifica si la tarea ha fallado
     */
    public boolean hasFailed() {
        return failed;
    }

    /**
     * Verifica si la tarea está activa
     */
    public boolean isActive() {
        return !completed && !failed;
    }

    /**
     * Cancela la tarea
     */
    public void cancel() {
        this.completed = true;
        this.stop();
    }

    /**
     * Prioridad de la tarea (mayor = más prioritaria)
     */
    public abstract int getPriority();

    /**
     * Nombre descriptivo de la tarea
     */
    public abstract String getName();

    /**
     * Estado al que cambia el bot cuando ejecuta esta tarea
     */
    public abstract AIState getState();

    /**
     * Verifica si la tarea puede ejecutarse actualmente
     */
    public boolean canExecute() {
        return true;
    }

    /**
     * Manejo de timeout
     */
    protected void checkTimeout() {
        if (++timeoutTicks > maxTimeoutTicks) {
            failed = true;
            completed = true;
        }
    }

    /**
     * Utilidad para marcar como completada
     */
    protected void complete() {
        this.completed = true;
        this.stop();
    }

    /**
     * Utilidad para marcar como fallida
     */
    protected void fail() {
        this.failed = true;
        this.completed = true;
        this.stop();
    }

    // Getters útiles para subclases
    protected BlockPos getBotPos() {
        return bot.blockPosition();
    }

    protected Entity getTarget() {
        return bot.getTarget();
    }

    protected ItemStack getMainHandItem() {
        return bot.getMainHandItem();
    }
}
