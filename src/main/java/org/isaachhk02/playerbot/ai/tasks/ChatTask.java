package org.isaachhk02.playerbot.ai.tasks;

import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;

import java.util.Random;

/**
 * Tarea de chat: permite al bot comunicarse con otros jugadores y bots
 */
public class ChatTask extends AITask {
    private String message;
    private int chatCooldown = 0;
    private final Random random = new Random();

    // Frases que el bot puede decir espontáneamente
    private static final String[] IDLE_PHRASES = {
            "Nice day for mining!",
            "Anyone need help?",
            "I found some resources!",
            "Watch out for creepers!",
            "This place is beautiful.",
            "I'm getting hungry...",
            "Should we build something?",
            "Stay close, everyone!"
    };

    private static final String[] COMBAT_PHRASES = {
            "Take that!",
            "Enemies incoming!",
            "I've got one!",
            "Fall back!",
            "Cover me!"
    };

    private static final String[] COOPERATION_PHRASES = {
            "Let's work together!",
            "I'll help you with that!",
            "Follow me!",
            "We make a good team!",
            "Need any materials?"
    };

    public ChatTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = 100;
    }

    @Override
    public void start() {
        super.start();
        determineMessage();
    }

    @Override
    public void tick() {
        checkTimeout();

        if (message != null && !message.isEmpty()) {
            bot.say(message);
            message = null;
        }

        complete();
    }

    private void determineMessage() {
        // Determinar qué decir basado en el contexto
        AIState state = bot.getCurrentState();

        String[] phrasePool;

        switch (state) {
            case FIGHTING -> phrasePool = COMBAT_PHRASES;
            case COOPERATING, FOLLOWING -> phrasePool = COOPERATION_PHRASES;
            default -> phrasePool = IDLE_PHRASES;
        }

        // 30% de probabilidad de hablar espontáneamente
        if (random.nextFloat() < 0.3f) {
            message = phrasePool[random.nextInt(phrasePool.length)];
        } else {
            message = null;
            // No decir nada esta vez
        }
    }

    /**
     * Fuerza un mensaje específico
     */
    public void setMessage(String msg) {
        this.message = msg;
    }

    @Override
    public int getPriority() {
        // Baja prioridad, solo para comunicación casual
        return 10;
    }

    @Override
    public String getName() {
        return "Chatting";
    }

    @Override
    public AIState getState() {
        return AIState.CHATTING;
    }

    @Override
    public boolean canExecute() {
        // Chat espontáneo desactivado: los bots ya no hablan solos para no
        // inundar el chat del servidor. Los mensajes pedidos por un jugador
        // (/bot say) se envían igualmente vía AIPlayer.forceSay().
        return false;
    }
}
