package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;
import org.isaachhk02.playerbot.util.BotUtils;
import org.isaachhk02.playerbot.util.ProtectionManager;

import java.util.List;

/**
 * Tarea de minería: mina minerales valiosos que estén al alcance del bot y
 * recoge los drops resultantes.
 *
 * No navega hacia minerales que no puede alcanzar. Antes lo hacía: fijaba como
 * objetivo un mineral enterrado e intentaba "caminar" hasta él, quedándose
 * empujando contra la roca (y, si había cavado antes, atrapado dentro de su
 * propio pozo) sin moverse. Ahora solo mina lo que tiene a mano; si no hay nada,
 * no se selecciona esta tarea y el bot explora.
 */
public class MineTask extends AITask {
    private BlockPos mineTarget;

    // Alcance máximo (al cuadrado) para considerar un mineral "a mano".
    private static final double REACH_SQR = 20.0; // ~4,5 bloques
    // Radio para recoger los drops que quedan en el suelo.
    private static final double PICKUP_RADIUS = 4.5;
    // Cuántos bloques puede minar en una sola pasada antes de volver a decidir.
    private static final int MAX_ORES_PER_RUN = 32;
    private int minedThisRun = 0;

    public MineTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = 600;
    }

    @Override
    public void start() {
        super.start();
        minedThisRun = 0;
        mineTarget = findReachableOre();
        equipPickaxe();
    }

    @Override
    public void tick() {
        checkTimeout();

        // Recoger cualquier drop cercano (los minerales minados sueltan items que
        // hay que meter al inventario; antes simplemente se perdían).
        collectNearbyDrops();

        if (minedThisRun >= MAX_ORES_PER_RUN) {
            complete();
            return;
        }

        if (mineTarget == null || bot.level().getBlockState(mineTarget).isAir()) {
            mineTarget = findReachableOre();
            if (mineTarget == null) {
                complete();
                return;
            }
        }

        mineBlock();
    }

    /**
     * Busca el mineral valioso más rentable dentro del alcance del bot. Los
     * minerales raros puntúan más, así que un diamante cercano gana a la piedra,
     * pero un mineral lejano no eclipsa a uno mucho más cercano.
     */
    private BlockPos findReachableOre() {
        BlockPos center = bot.blockPosition();
        BlockPos best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        ProtectionManager protection = ProtectionManager.getInstance();

        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 2; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    double dist = center.distSqr(pos);
                    if (dist > REACH_SQR) {
                        continue;
                    }
                    int value = oreValue(pos);
                    if (value <= 0) {
                        continue;
                    }
                    double score = value - dist * 3.0;
                    if (score <= bestScore) {
                        continue;
                    }
                    if (protection.canModifyBlock(bot.serverLevel(), pos, bot)) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }

        return best;
    }

    /**
     * Valor del bloque si es un mineral, 0 si no lo es. Cuanto más raro, mayor
     * valor (solo se usa para decidir el orden de minado).
     */
    private int oreValue(BlockPos pos) {
        BlockState state = bot.level().getBlockState(pos);
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            return 100;
        }
        if (state.is(Blocks.ANCIENT_DEBRIS)) {
            return 95;
        }
        if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) {
            return 90;
        }
        if (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE)
                || state.is(Blocks.NETHER_GOLD_ORE)) {
            return 70;
        }
        if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) {
            return 60;
        }
        if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) {
            return 55;
        }
        if (state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) {
            return 50;
        }
        if (state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)) {
            return 30;
        }
        if (state.is(Blocks.NETHER_QUARTZ_ORE)) {
            return 25;
        }
        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            return 20;
        }
        return 0;
    }

    private void mineBlock() {
        // VERIFICACIÓN FINAL antes de minar
        ProtectionManager protection = ProtectionManager.getInstance();
        if (!protection.canModifyBlock(bot.serverLevel(), mineTarget, bot)) {
            mineTarget = null;
            return;
        }

        BlockPos pos = mineTarget;
        BotUtils.lookAt(bot, Vec3.atCenterOf(pos));
        bot.swing(InteractionHand.MAIN_HAND);

        // Usar el gameMode para romper el bloque correctamente
        bot.gameMode.destroyBlock(pos);
        minedThisRun++;

        // Intentar recoger los drops ya mismo (si el servidor aún no ha creado la
        // entidad, el siguiente tick lo hará).
        collectNearbyDrops();

        mineTarget = null; // Buscar siguiente objetivo
    }

    /**
     * Recoge los ItemEntity cercanos y los mete al inventario. Si el objeto no
     * cabe entero, se guarda lo que quepa y el resto se queda en el suelo.
     */
    private void collectNearbyDrops() {
        AABB searchBox = bot.getBoundingBox().inflate(PICKUP_RADIUS);
        List<ItemEntity> drops = bot.level().getEntitiesOfClass(ItemEntity.class, searchBox,
                item -> item.isAlive() && !item.getItem().isEmpty());

        for (ItemEntity drop : drops) {
            ItemStack ground = drop.getItem();
            ItemStack toAdd = ground.copy();
            bot.getInventory().add(toAdd);

            if (toAdd.isEmpty()) {
                // Se recogió todo.
                drop.discard();
            } else if (toAdd.getCount() < ground.getCount()) {
                // Solo cabía una parte: dejar el resto en el suelo.
                ground.setCount(toAdd.getCount());
            }
        }
    }

    private void equipPickaxe() {
        int bestSlot = -1;
        int bestTier = -1;

        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (stack.getItem() instanceof net.minecraft.world.item.PickaxeItem) {
                int tier = toolTier(stack);
                if (tier > bestTier) {
                    bestTier = tier;
                    bestSlot = i;
                }
            }
        }

        BotUtils.equipToMainHand(bot, bestSlot);
    }

    private int toolTier(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        if (stack.is(Items.NETHERITE_PICKAXE)) return 5;
        if (stack.is(Items.DIAMOND_PICKAXE)) return 4;
        if (stack.is(Items.IRON_PICKAXE)) return 3;
        if (stack.is(Items.STONE_PICKAXE)) return 2;
        if (stack.is(Items.GOLDEN_PICKAXE)) return 1;
        return 0; // wooden
    }

    @Override
    public int getPriority() {
        return 65;
    }

    @Override
    public String getName() {
        return "Mining at " + (mineTarget != null ? mineTarget.toShortString() : "unknown");
    }

    @Override
    public AIState getState() {
        return AIState.MINING;
    }

    @Override
    public boolean canExecute() {
        // Solo si hay de verdad un mineral a mano (o drops que recoger): así no
        // monopoliza la IA ni deja al bot parado buscando algo inalcanzable.
        return bot.getTarget() == null && !bot.isInCombat()
                && (findReachableOre() != null || hasNearbyDrops());
    }

    private boolean hasNearbyDrops() {
        AABB searchBox = bot.getBoundingBox().inflate(PICKUP_RADIUS);
        return !bot.level().getEntitiesOfClass(ItemEntity.class, searchBox,
                item -> item.isAlive() && !item.getItem().isEmpty()).isEmpty();
    }
}
