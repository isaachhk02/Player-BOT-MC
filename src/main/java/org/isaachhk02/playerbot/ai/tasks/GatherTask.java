package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;
import org.isaachhk02.playerbot.util.BotUtils;
import org.isaachhk02.playerbot.util.ProtectionManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tarea de recolección: recoge items del suelo y recursos naturales
 */
public class GatherTask extends AITask {
    private ItemEntity targetItem;
    private BlockPos targetResource;
    private GatherType type;
    private int gatherTicks = 0;
    // Para detectar si el bot no consigue acercarse a un objeto (p. ej. está al
    // otro lado de un barranco) y dejar de intentarlo en lugar de quedarse
    // atascado hasta el timeout.
    private double lastItemDistance = Double.MAX_VALUE;
    private int itemStallTicks = 0;
    // Recursos talados/minados en esta ejecución, para no quedarse eternamente
    // en un mismo árbol/roca sin volver a explorar.
    private int gatheredThisRun = 0;
    private static final int MAX_RESOURCES_PER_RUN = 16;

    private enum GatherType {
        ITEM_PICKUP,
        WOOD,
        STONE,
        FOOD
    }

    public GatherTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = 400;
    }

    @Override
    public void start() {
        super.start();
        gatherTicks = 0;
        lastItemDistance = Double.MAX_VALUE;
        itemStallTicks = 0;
        gatheredThisRun = 0;
        determineGatherTarget();
    }

    @Override
    public void tick() {
        checkTimeout();
        gatherTicks++;

        switch (type) {
            case ITEM_PICKUP -> tickItemPickup();
            case WOOD -> tickGatherWood();
            case STONE -> tickGatherStone();
            case FOOD -> tickGatherFood();
        }
    }

    private void determineGatherTarget() {
        // Prioridad 1: Recoger items cercanos
        targetItem = findNearestItem();
        if (targetItem != null) {
            type = GatherType.ITEM_PICKUP;
            return;
        }

        // Prioridad 2: Buscar madera si la necesita
        if (countItem(Items.OAK_LOG) < 16) {
            targetResource = findNearestBlock(Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG);
            if (targetResource != null) {
                type = GatherType.WOOD;
                equipTool(Items.WOODEN_AXE);
                return;
            }
        }

        // Prioridad 3: Buscar piedra si la necesita
        if (countItem(Items.COBBLESTONE) < 32) {
            targetResource = findNearestBlock(Blocks.STONE, Blocks.COBBLESTONE, Blocks.DEEPSLATE);
            if (targetResource != null) {
                type = GatherType.STONE;
                equipTool(Items.WOODEN_PICKAXE);
                return;
            }
        }

        // No hay nada que recolectar
        fail();
    }

    private void tickItemPickup() {
        if (targetItem == null || !targetItem.isAlive()) {
            complete();
            return;
        }

        double distance = bot.distanceTo(targetItem);
        if (distance < 2.0) {
            // Recoger el item
            bot.getInventory().add(targetItem.getItem().copy());
            targetItem.discard();
            complete();
            return;
        }

        // ¿Nos estamos acercando? Si llevamos un rato sin progresar, el objeto
        // debe de ser inalcanzable: lo dejamos en lugar de atascarnos.
        if (distance >= lastItemDistance - 0.05) {
            itemStallTicks++;
            if (itemStallTicks > 60) {
                targetItem = null;
                complete();
                return;
            }
        } else {
            itemStallTicks = 0;
        }
        lastItemDistance = distance;

        // Acercarse al item
        bot.getNavigation().moveTo(targetItem, 1.0);
    }

    private void tickGatherWood() {
        // VERIFICACIÓN DE PROTECCIÓN antes de talar
        ProtectionManager protection = ProtectionManager.getInstance();
        if (targetResource == null || !protection.canModifyBlock(bot.serverLevel(), targetResource, bot)) {
            targetResource = findNearestBlock(Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG);
            if (targetResource == null) {
                complete();
            }
            return;
        }

        double distance = bot.blockPosition().distSqr(targetResource);
        if (distance > 9) {
            bot.getNavigation().moveTo(
                    targetResource.getX() + 0.5,
                    targetResource.getY(),
                    targetResource.getZ() + 0.5,
                    1.0
            );
        } else {
            // Romper el bloque
            BotUtils.lookAt(bot, net.minecraft.world.phys.Vec3.atCenterOf(targetResource));
            bot.swing(InteractionHand.MAIN_HAND);
            bot.gameMode.destroyBlock(targetResource);
            gatheredThisRun++;

            // Tras talar unos cuantos troncos, terminar para volver a explorar
            // en lugar de quedarse pegado al mismo árbol.
            if (gatheredThisRun >= MAX_RESOURCES_PER_RUN) {
                complete();
                return;
            }

            // Buscar siguiente bloque de madera cercano
            targetResource = findNearestBlock(Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG);
            if (targetResource == null) {
                complete();
            }
        }
    }

    private void tickGatherStone() {
        // VERIFICACIÓN DE PROTECCIÓN antes de minar
        ProtectionManager protection = ProtectionManager.getInstance();
        if (targetResource == null || !protection.canModifyBlock(bot.serverLevel(), targetResource, bot)) {
            targetResource = findNearestBlock(Blocks.STONE, Blocks.COBBLESTONE, Blocks.DEEPSLATE);
            if (targetResource == null) {
                complete();
            }
            return;
        }

        double distance = bot.blockPosition().distSqr(targetResource);
        if (distance > 9) {
            bot.getNavigation().moveTo(
                    targetResource.getX() + 0.5,
                    targetResource.getY(),
                    targetResource.getZ() + 0.5,
                    1.0
            );
        } else {
            // Minar el bloque
            BotUtils.lookAt(bot, net.minecraft.world.phys.Vec3.atCenterOf(targetResource));
            bot.swing(InteractionHand.MAIN_HAND);
            bot.gameMode.destroyBlock(targetResource);
            gatheredThisRun++;

            if (gatheredThisRun >= MAX_RESOURCES_PER_RUN) {
                complete();
                return;
            }

            targetResource = findNearestBlock(Blocks.STONE, Blocks.COBBLESTONE, Blocks.DEEPSLATE);
            if (targetResource == null) {
                complete();
            }
        }
    }

    private void tickGatherFood() {
        // Buscar comida (plantas, animales, etc.)
        // Por ahora, buscar wheat, carrots, etc.
        complete();
    }

    private ItemEntity findNearestItem() {
        AABB searchBox = AABB.ofSize(bot.position(), 16, 16, 16);
        List<ItemEntity> items = bot.level().getEntitiesOfClass(ItemEntity.class, searchBox,
                item -> item.isAlive() && !item.hasPickUpDelay());

        return items.stream()
                .min(Comparator.comparingDouble(bot::distanceTo))
                .orElse(null);
    }

    private BlockPos findNearestBlock(net.minecraft.world.level.block.Block... blocks) {
        BlockPos botPos = bot.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();

        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -10; dz <= 10; dz++) {
                    BlockPos pos = botPos.offset(dx, dy, dz);
                    BlockState state = bot.level().getBlockState(pos);
                    for (var block : blocks) {
                        if (state.is(block)) {
                            candidates.add(pos);
                            break;
                        }
                    }
                }
            }
        }

        // La proteccion es costosa: solo se comprueba sobre los candidatos mas cercanos
        candidates.sort(Comparator.comparingDouble(p -> botPos.distSqr(p)));
        ProtectionManager protection = ProtectionManager.getInstance();
        int checks = 0;
        for (BlockPos pos : candidates) {
            if (checks++ >= 20) {
                break;
            }
            if (protection.canModifyBlock(bot.serverLevel(), pos, bot)) {
                return pos;
            }
        }
        return null;
    }

    private boolean isLog(BlockPos pos) {
        BlockState state = bot.level().getBlockState(pos);
        boolean isLogType = state.is(Blocks.OAK_LOG) || state.is(Blocks.BIRCH_LOG) || state.is(Blocks.SPRUCE_LOG);
        if (!isLogType) return false;

        // VERIFICACIÓN: No talar si está protegido
        ProtectionManager protection = ProtectionManager.getInstance();
        return protection.canModifyBlock(bot.serverLevel(), pos, bot);
    }

    private boolean isStone(BlockPos pos) {
        BlockState state = bot.level().getBlockState(pos);
        boolean isStoneType = state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.DEEPSLATE);
        if (!isStoneType) return false;

        // VERIFICACIÓN: No minar si está protegido
        ProtectionManager protection = ProtectionManager.getInstance();
        return protection.canModifyBlock(bot.serverLevel(), pos, bot);
    }

    private void equipTool(net.minecraft.world.item.Item item) {
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            if (bot.getInventory().getItem(i).is(item)) {
                BotUtils.equipToMainHand(bot, i);
                return;
            }
        }
        // Sin herramienta el bot rompe bloques a mano (gameMode.destroyBlock
        // funciona igual); la herramienta se la fabricará él mismo.
    }

    private int countItem(net.minecraft.world.item.Item item) {
        int count = 0;
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public int getPriority() {
        // Prioridad media-alta para recolección
        return 70;
    }

    @Override
    public String getName() {
        return switch (type != null ? type : GatherType.ITEM_PICKUP) {
            case ITEM_PICKUP -> "Picking up items";
            case WOOD -> "Gathering wood";
            case STONE -> "Gathering stone";
            case FOOD -> "Gathering food";
        };
    }

    @Override
    public AIState getState() {
        return AIState.GATHERING;
    }

    @Override
    public boolean canExecute() {
        // Solo ejecutar si no está en combate y necesita recursos
        return bot.getTarget() == null || !bot.isInCombat();
    }
}
