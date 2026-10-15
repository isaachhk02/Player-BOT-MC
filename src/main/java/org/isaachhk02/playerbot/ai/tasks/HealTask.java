package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;
import org.isaachhk02.playerbot.util.BotUtils;

/**
 * Tarea de curación: come alimentos para recuperar salud.
 *
 * El consumo real lo hace vanilla dentro de Player.tick() (updatingUsingItem),
 * que ahora sí se ejecuta para los bots gracias a AIPlayer.tick() -> doTick().
 * Como red de seguridad, si el consumo no ocurrió igualmente aplicamos la comida
 * y curamos al bot.
 */
public class HealTask extends AITask {
    /** Ticks máximos que puede durar la animación de comer antes de forzar el final. */
    private static final int MAX_EAT_TICKS = 80;

    private boolean isEating = false;
    private int eatTicks = 0;
    private int foodLevelBefore = 0;
    private float pendingHeal = 0.0f;

    public HealTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = 200;
    }

    @Override
    public void start() {
        super.start();
        this.isEating = false;
        this.eatTicks = 0;
        this.foodLevelBefore = 0;
        this.pendingHeal = 0.0f;
        findAndEatFood();
    }

    @Override
    public void tick() {
        checkTimeout();

        if (!isEating) {
            if (bot.getHealth() >= bot.getMaxHealth() * 0.95f) {
                complete();
                return;
            }
            findAndEatFood();
            return;
        }

        eatTicks++;

        // Vanilla consume la comida tras la duración del item y llama a
        // stopUsingItem() por sí mismo, por lo que isUsingItem() pasa a false.
        boolean finished = !bot.isUsingItem();
        if (finished || eatTicks >= MAX_EAT_TICKS) {
            finishEating();
            complete();
        }
    }

    private void findAndEatFood() {
        ItemStack food = findBestFood();
        if (food.isEmpty()) {
            fail();
            return;
        }

        // Encontrar el slot del item en el inventario para consumirlo correctamente
        int foodSlot = -1;
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (BotUtils.isFood(stack) && ItemStack.isSameItemSameComponents(stack, food)) {
                foodSlot = i;
                break;
            }
        }

        if (foodSlot == -1) {
            fail();
            return;
        }

        // Poner la comida en la mano y empezar a comer (esto activa la animación).
        ItemStack foodStack = bot.getInventory().getItem(foodSlot);
        bot.setItemInHand(InteractionHand.MAIN_HAND, foodStack);
        bot.startUsingItem(InteractionHand.MAIN_HAND);

        this.isEating = true;
        this.eatTicks = 0;
        this.foodLevelBefore = bot.getFoodData().getFoodLevel();
        // Curación de respaldo: si vanilla no consume la comida (p. ej. regeneración
        // natural desactivada), al menos el bot recupera algo de salud al comer.
        this.pendingHeal = BotUtils.getNutritionValue(foodStack);
    }

    private void finishEating() {
        if (isEating) {
            bot.stopUsingItem();
            isEating = false;
        }

        // Si el consumo de vanilla no ocurrió, aplicamos la comida manualmente.
        int foodLevelAfter = bot.getFoodData().getFoodLevel();
        if (foodLevelAfter <= foodLevelBefore) {
            ItemStack held = bot.getItemInHand(InteractionHand.MAIN_HAND);
            if (BotUtils.isFood(held)) {
                bot.eat(bot.level(), held);
            }
        }

        // Garantizar que el bot recupere salud al comer.
        if (pendingHeal > 0.0f && bot.getHealth() < bot.getMaxHealth()) {
            bot.heal(pendingHeal);
        }
        pendingHeal = 0.0f;
    }

    private ItemStack findBestFood() {
        ItemStack bestFood = ItemStack.EMPTY;
        float bestNutrition = 0;

        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (BotUtils.isFood(stack)) {
                float nutrition = BotUtils.getNutritionValue(stack);
                if (nutrition > bestNutrition) {
                    bestNutrition = nutrition;
                    bestFood = stack;
                }
            }
        }

        return bestFood;
    }

    @Override
    public int getPriority() {
        if (bot.getHealth() < bot.getMaxHealth() * 0.5f && hasFood()) {
            return 80;
        }
        return 0;
    }

    private boolean hasFood() {
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (BotUtils.isFood(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return "Healing (eating)";
    }

    @Override
    public AIState getState() {
        return AIState.HEALING;
    }

    @Override
    public boolean canExecute() {
        return bot.getHealth() < bot.getMaxHealth() * 0.7f && hasFood();
    }

    @Override
    public void stop() {
        bot.stopUsingItem();
        isEating = false;
        pendingHeal = 0.0f;
    }
}
