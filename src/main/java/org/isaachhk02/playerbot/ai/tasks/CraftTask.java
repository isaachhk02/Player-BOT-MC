package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.isaachhk02.playerbot.PlayerBOT;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;

import java.util.List;
import java.util.function.Predicate;

/**
 * Tarea de crafteo: ahora craftea de verdad usando las recetas del servidor
 * ({@link RecipeType#CRAFTING}) en lugar de recetas simuladas a mano.
 *
 * Cómo funciona:
 *  - Se decide un "objetivo" (qué craftear) según lo que falte en el inventario.
 *  - Se busca entre todas las recetas 2x2 (o 3x3 si el bot tiene una mesa de
 *    crafteo) una cuyo resultado coincida con el objetivo y cuyos ingredientes
 *    estén disponibles en el inventario.
 *  - Se consume el ingrediente y se añade el resultado (con la cantidad real de
 *    la receta: 4 tablones, 4 palos, etc.).
 *
 * El inventario 2x2 siempre está disponible; las recetas 3x3 (herramientas,
 * cofre, horno...) requieren tener una mesa de crafteo en el inventario, cosa
 * que el bot consigue él mismo como primer objetivo.
 */
public class CraftTask extends AITask {
    /** Un objeto cada cuántos ticks (evita craftear los 16 de golpe en un tick). */
    private static final int CRAFT_INTERVAL = 5;
    /** Máximo de objetos por pasada, para no monopolizar la IA demasiado tiempo. */
    private static final int MAX_CRAFTS_PER_RUN = 16;
    /** Objetivo de reserva de tablones/palos. */
    private static final int STOCK_TARGET = 16;

    private int craftTicks = 0;
    private int craftsDone = 0;
    /** 2 = inventario 2x2; 3 = mesa de crafteo disponible (recetas 3x3). */
    private int gridSize = 2;
    private List<RecipeHolder<CraftingRecipe>> recipes;

    public CraftTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = 200;
    }

    @Override
    public void start() {
        super.start();
        craftTicks = 0;
        craftsDone = 0;
        recipes = bot.serverLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        gridSize = hasItem(Items.CRAFTING_TABLE) ? 3 : 2;
    }

    @Override
    public void tick() {
        checkTimeout();
        craftTicks++;

        // Craftear con calma: un objeto cada CRAFT_INTERVAL ticks.
        if (craftTicks % CRAFT_INTERVAL != 0) {
            return;
        }

        Predicate<ItemStack> goal = evaluateGoal();
        if (goal == null) {
            complete();
            return;
        }

        if (!craft(goal)) {
            // No hay ninguna receta que podamos hacer para el objetivo: parar.
            complete();
            return;
        }

        craftsDone++;

        // Al fabricar la mesa de crafteo pasamos a poder usar recetas 3x3.
        if (hasItem(Items.CRAFTING_TABLE)) {
            gridSize = 3;
        }
        if (craftsDone >= MAX_CRAFTS_PER_RUN) {
            complete();
        }
    }

    /**
     * Decide qué queremos craftear según lo que falte en el inventario. Devuelve
     * un predicado sobre el resultado de la receta (para aceptar cualquier tipo
     * de tablón, por ejemplo), o null si no hace falta nada.
     */
    private Predicate<ItemStack> evaluateGoal() {
        int planks = countTag(ItemTags.PLANKS);
        int sticks = countItem(Items.STICK);
        int cobblestone = countItem(Items.COBBLESTONE);
        boolean hasLogs = countTag(ItemTags.LOGS) > 0;

        // 1) Troncos -> tablones.
        if (hasLogs && planks < STOCK_TARGET) {
            return stack -> stack.is(ItemTags.PLANKS);
        }
        // 2) Tablones -> palos.
        if (planks >= 2 && sticks < STOCK_TARGET) {
            return stack -> stack.is(Items.STICK);
        }
        // 3) Mesa de crafteo (desbloquea las recetas 3x3).
        if (planks >= 4 && !hasItem(Items.CRAFTING_TABLE)) {
            return stack -> stack.is(Items.CRAFTING_TABLE);
        }
        // 4) Mejoras de piedra si hay cobblestone (sin repetir si ya tiene algo mejor).
        if (cobblestone >= 3 && sticks >= 2 && !hasPickaxeAtLeastStone()) {
            return stack -> stack.is(Items.STONE_PICKAXE);
        }
        if (cobblestone >= 2 && sticks >= 1 && !hasSwordAtLeastStone()) {
            return stack -> stack.is(Items.STONE_SWORD);
        }
        if (cobblestone >= 3 && sticks >= 2 && !hasAxeAtLeastStone()) {
            return stack -> stack.is(Items.STONE_AXE);
        }
        // 5) Herramientas de madera si no tiene ninguna.
        if (planks >= 3 && sticks >= 2 && !hasAnyPickaxe()) {
            return stack -> stack.is(Items.WOODEN_PICKAXE);
        }
        if (planks >= 2 && sticks >= 1 && !hasAnySword()) {
            return stack -> stack.is(Items.WOODEN_SWORD);
        }
        if (planks >= 3 && sticks >= 2 && !hasAnyAxe()) {
            return stack -> stack.is(Items.WOODEN_AXE);
        }
        // 6) Antorchas si hay carbón.
        if ((hasItem(Items.COAL) || hasItem(Items.CHARCOAL)) && sticks >= 1
                && countItem(Items.TORCH) < 32) {
            return stack -> stack.is(Items.TORCH);
        }
        // 7) Escudo.
        if (planks >= 6 && sticks >= 1 && !hasItem(Items.SHIELD)) {
            return stack -> stack.is(Items.SHIELD);
        }
        // 8) Armadura de cuero con la piel sobrante de la caza.
        int leather = countItem(Items.LEATHER);
        if (leather >= 4 && !hasItem(Items.LEATHER_BOOTS)) {
            return stack -> stack.is(Items.LEATHER_BOOTS);
        }
        if (leather >= 5 && !hasItem(Items.LEATHER_HELMET)) {
            return stack -> stack.is(Items.LEATHER_HELMET);
        }
        if (leather >= 7 && !hasItem(Items.LEATHER_LEGGINGS)) {
            return stack -> stack.is(Items.LEATHER_LEGGINGS);
        }
        if (leather >= 8 && !hasItem(Items.LEATHER_CHESTPLATE)) {
            return stack -> stack.is(Items.LEATHER_CHESTPLATE);
        }
        return null;
    }

    /**
     * Busca una receta cuyo resultado cumpla el objetivo, que quepa en la
     * rejilla disponible y cuyos ingredientes estén en el inventario. Consume lo
     * necesario y añade el resultado.
     */
    private boolean craft(Predicate<ItemStack> goal) {
        if (recipes == null) {
            return false;
        }

        for (RecipeHolder<CraftingRecipe> holder : recipes) {
            CraftingRecipe recipe = holder.value();
            if (!recipe.canCraftInDimensions(gridSize, gridSize)) {
                continue;
            }

            ItemStack result = recipe.getResultItem(bot.registryAccess());
            if (result.isEmpty() || !goal.test(result)) {
                continue;
            }
            if (!consumeIngredients(recipe)) {
                continue;
            }

            bot.getInventory().add(result.copy());
            PlayerBOT.LOGGER.info("Bot {} crafted {} x{}", bot.getName().getString(),
                    result.getHoverName().getString(), result.getCount());
            return true;
        }
        return false;
    }

    /**
     * Comprueba si el inventario tiene todos los ingredientes de la receta y, si
     * es así, los consume. Si falta algo no modifica el inventario.
     */
    private boolean consumeIngredients(CraftingRecipe recipe) {
        Inventory inv = bot.getInventory();
        int size = inv.getContainerSize();

        int[] remaining = new int[size];
        for (int i = 0; i < size; i++) {
            remaining[i] = inv.getItem(i).getCount();
        }

        int[] used = new int[size];
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            boolean matched = false;
            for (int i = 0; i < size; i++) {
                if (remaining[i] > 0 && ingredient.test(inv.getItem(i))) {
                    remaining[i]--;
                    used[i]++;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }

        // Todo disponible: aplicar el consumo.
        for (int i = 0; i < size; i++) {
            if (used[i] > 0) {
                ItemStack stack = inv.getItem(i);
                stack.shrink(used[i]);
                if (stack.isEmpty()) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        return true;
    }

    /** ¿Tiene algún pico (aunque sea de madera)? */
    private boolean hasAnyPickaxe() {
        return hasItem(Items.WOODEN_PICKAXE) || hasPickaxeAtLeastStone();
    }

    /** ¿Tiene un pico de piedra o mejor? (la madera no bloquea la mejora) */
    private boolean hasPickaxeAtLeastStone() {
        return hasItem(Items.STONE_PICKAXE) || hasItem(Items.IRON_PICKAXE)
                || hasItem(Items.GOLDEN_PICKAXE) || hasItem(Items.DIAMOND_PICKAXE)
                || hasItem(Items.NETHERITE_PICKAXE);
    }

    /** ¿Tiene alguna espada (aunque sea de madera)? */
    private boolean hasAnySword() {
        return hasItem(Items.WOODEN_SWORD) || hasSwordAtLeastStone();
    }

    /** ¿Tiene una espada de piedra o mejor? */
    private boolean hasSwordAtLeastStone() {
        return hasItem(Items.STONE_SWORD) || hasItem(Items.IRON_SWORD)
                || hasItem(Items.GOLDEN_SWORD) || hasItem(Items.DIAMOND_SWORD)
                || hasItem(Items.NETHERITE_SWORD);
    }

    /** ¿Tiene algún hacha (aunque sea de madera)? */
    private boolean hasAnyAxe() {
        return hasItem(Items.WOODEN_AXE) || hasAxeAtLeastStone();
    }

    /** ¿Tiene un hacha de piedra o mejor? */
    private boolean hasAxeAtLeastStone() {
        return hasItem(Items.STONE_AXE) || hasItem(Items.IRON_AXE)
                || hasItem(Items.GOLDEN_AXE) || hasItem(Items.DIAMOND_AXE)
                || hasItem(Items.NETHERITE_AXE);
    }

    private boolean hasItem(Item item) {
        return countItem(item) > 0;
    }

    private int countItem(Item item) {
        int count = 0;
        Inventory inv = bot.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                count += inv.getItem(i).getCount();
            }
        }
        return count;
    }

    private int countTag(TagKey<Item> tag) {
        int count = 0;
        Inventory inv = bot.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(tag)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public String getName() {
        return "Crafting";
    }

    @Override
    public AIState getState() {
        return AIState.CRAFTING;
    }

    @Override
    public boolean canExecute() {
        // Solo si hay algo útil que craftear y no estamos en combate.
        return bot.getTarget() == null && evaluateGoal() != null;
    }
}
