package org.isaachhk02.playerbot.ai;

/**
 * Estados posibles para un jugador controlado por IA
 */
public enum AIState {
    IDLE("Idle"),
    MOVING("Moving"),
    GATHERING("Gathering"),
    CRAFTING("Crafting"),
    BUILDING("Building"),
    FIGHTING("Fighting"),
    DEFENDING("Defending"),
    FLEEING("Fleeing"),
    CHATTING("Chatting"),
    FOLLOWING("Following"),
    TRADING("Trading"),
    EXPLORING("Exploring"),
    SLEEPING("Sleeping"),
    MINING("Mining"),
    FARMING("Farming"),
    HEALING("Healing"),
    HUNTING("Hunting"),
    COOPERATING("Cooperating");

    private final String displayName;

    AIState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Verifica si el estado permite interrupciones inmediatas
     */
    public boolean isInterruptible() {
        return this == IDLE || this == MOVING || this == CHATTING;
    }

    /**
     * Obtiene la prioridad del estado (mayor = más prioritario)
     */
    public int getPriority() {
        return switch (this) {
            case FLEEING -> 100;
            case FIGHTING, DEFENDING -> 90;
            case HEALING -> 80;
            case HUNTING -> 75;
            case GATHERING, MINING -> 70;
            case BUILDING, FARMING -> 60;
            case CRAFTING, TRADING -> 50;
            case FOLLOWING -> 40;
            case COOPERATING -> 35;
            case MOVING, EXPLORING -> 30;
            case SLEEPING -> 20;
            case CHATTING -> 10;
            case IDLE -> 0;
        };
    }
}
