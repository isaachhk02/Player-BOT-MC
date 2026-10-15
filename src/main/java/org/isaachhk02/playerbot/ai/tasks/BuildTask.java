package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;
import org.isaachhk02.playerbot.util.ProtectionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Tarea de construcción: construye estructuras básicas (casa, torre, etc.)
 */
public class BuildTask extends AITask {
    private BuildPlan plan;
    private int buildStep = 0;
    private BlockPos buildOrigin;
    private List<BlockPos> buildPositions = new ArrayList<>();
    private boolean isBuilding = false;

    // Marca de tiempo del último refugio terminado. Sirve para no encadenar un
    // refugio detrás de otro en el mismo sitio sin hacer nada más.
    private int lastBuildTick = -100000;

    private static final int BUILD_COOLDOWN_TICKS = 600; // 30 segundos

    public enum BuildPlan {
        SHELTER,      // Refugio básico 3x3
        TOWER,        // Torre de vigilancia
        WALL,         // Muralla
        HOUSE,        // Casa simple 5x5
        BRIDGE,       // Puente
        FARM          // Granja básica
    }

    public BuildTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = 2000;
    }

    public BuildTask(AIPlayer bot, BuildPlan plan) {
        this(bot);
        this.plan = plan;
    }

    @Override
    public void start() {
        super.start();
        buildStep = 0;
        buildOrigin = bot.blockPosition();

        if (plan == null) {
            plan = BuildPlan.SHELTER; // Por defecto, refugio básico
        }

        // Buscar un sitio donde haya al menos una posición edificable. Antes, si el
        // origen del bot caía en una zona protegida (p. ej. al lado de un jugador),
        // se generaban 0 posiciones y el bot se rendía con
        // "All build positions are protected!". Ahora probamos algunos
        // desplazamientos cercanos antes de darnos por vencidos.
        ProtectionManager protection = ProtectionManager.getInstance();
        // Construir un poco por delante del bot, no encima de él, para que tenga
        // que moverse y no se quede encerrado en su propia obra.
        BlockPos base = bot.blockPosition().relative(bot.getDirection(), 2);

        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(base);
        for (int r = 4; r <= 12; r += 4) {
            candidates.add(base.offset(r, 0, 0));
            candidates.add(base.offset(-r, 0, 0));
            candidates.add(base.offset(0, 0, r));
            candidates.add(base.offset(0, 0, -r));
        }

        List<BlockPos> best = null;

        for (BlockPos candidate : candidates) {
            buildOrigin = candidate;
            generateBuildPlan();

            // VERIFICACIÓN: Filtrar posiciones protegidas del plan
            buildPositions.removeIf(pos -> !protection.canPlaceBlock(bot.serverLevel(), pos, bot));

            if (!buildPositions.isEmpty()) {
                best = new ArrayList<>(buildPositions);
                break; // primer sitio viable
            }
        }

        if (best == null) {
            // Puede ser que toda la zona esté protegida O que simplemente no
            // haya hueco (terreno sólido, cueva, etc.). El mensaje anterior
            // decía siempre "protegido", lo cual era engañoso.
            bot.say("I can't find a good spot to build here, moving on.");
            fail();
            return;
        }

        buildPositions = best;
        bot.say("Starting to build: " + plan.name().toLowerCase().replace("_", " ") + "!");
    }

    @Override
    public void tick() {
        checkTimeout();

        if (buildPositions.isEmpty()) {
            bot.say("Construction complete!");
            lastBuildTick = bot.tickCount;
            complete();
            return;
        }

        BlockPos target = buildPositions.get(0);
        double distance = bot.blockPosition().distSqr(target);

        if (distance > 25) {
            // Acercarse primero
            bot.getNavigation().moveTo(
                    target.getX() + 0.5,
                    target.getY(),
                    target.getZ() + 0.5,
                    1.0
            );
            return;
        }

        // Colocar el bloque
        placeBlock(target);
        buildPositions.remove(0);
        buildStep++;
    }

    private void generateBuildPlan() {
        buildPositions.clear();

        switch (plan) {
            case SHELTER -> generateShelter();
            case TOWER -> generateTower();
            case WALL -> generateWall();
            case HOUSE -> generateHouse();
            case BRIDGE -> generateBridge();
            case FARM -> generateFarm();
        }
    }

    private void generateShelter() {
        // Refugio 3x3 con paredes y techo
        int size = 3;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                // Piso
                buildPositions.add(buildOrigin.offset(x, -1, z));

                // Paredes (altura 2)
                if (x == 0 || x == size - 1 || z == 0 || z == size - 1) {
                    buildPositions.add(buildOrigin.offset(x, 0, z));
                    buildPositions.add(buildOrigin.offset(x, 1, z));
                }

                // Techo
                buildPositions.add(buildOrigin.offset(x, 2, z));
            }
        }
    }

    private void generateTower() {
        // Torre 3x3 de altura 10
        int size = 3;
        int height = 10;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    // Solo paredes exteriores y piso cada 3 niveles
                    boolean isWall = (x == 0 || x == size - 1 || z == 0 || z == size - 1);
                    boolean isFloor = (y % 3 == 0);

                    if (isWall || isFloor) {
                        buildPositions.add(buildOrigin.offset(x, y, z));
                    }
                }
            }
        }
    }

    private void generateWall() {
        // Muralla de 10 bloques de largo, 3 de alto
        Direction facing = bot.getDirection();
        int length = 10;
        int height = 3;

        for (int i = 0; i < length; i++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = switch (facing) {
                    case NORTH, SOUTH -> buildOrigin.offset(i, y, 0);
                    case EAST, WEST -> buildOrigin.offset(0, y, i);
                    default -> buildOrigin.offset(i, y, 0); // UP/DOWN - usar dirección por defecto
                };
                buildPositions.add(pos);
            }
        }
    }

    private void generateHouse() {
        // Casa 5x5 con paredes, techo y puerta
        int size = 5;
        int wallHeight = 3;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                // Piso
                buildPositions.add(buildOrigin.offset(x, -1, z));

                boolean isWall = (x == 0 || x == size - 1 || z == 0 || z == size - 1);

                if (isWall) {
                    // Paredes
                    for (int y = 0; y < wallHeight; y++) {
                        // Dejar espacio para la puerta
                        boolean isDoor = (x == size / 2 && z == 0 && y < 2);
                        if (!isDoor) {
                            buildPositions.add(buildOrigin.offset(x, y, z));
                        }
                    }
                }

                // Techo
                buildPositions.add(buildOrigin.offset(x, wallHeight, z));
            }
        }
    }

    private void generateBridge() {
        // Puente de 10 bloques en la dirección que mira el bot
        Direction facing = bot.getDirection();

        for (int i = 0; i < 10; i++) {
            BlockPos base = switch (facing) {
                case NORTH -> buildOrigin.offset(0, 0, -i);
                case SOUTH -> buildOrigin.offset(0, 0, i);
                case EAST -> buildOrigin.offset(i, 0, 0);
                case WEST -> buildOrigin.offset(-i, 0, 0);
                default -> buildOrigin.offset(0, 0, i); // UP/DOWN - usar sur por defecto
            };

            // Plataforma del puente (3 de ancho)
            for (int w = -1; w <= 1; w++) {
                BlockPos pos = switch (facing) {
                    case NORTH, SOUTH -> base.offset(w, 0, 0);
                    case EAST, WEST -> base.offset(0, 0, w);
                    default -> base.offset(w, 0, 0); // UP/DOWN - usar ancho por defecto
                };
                buildPositions.add(pos);
            }
        }
    }

    private void generateFarm() {
        // Granja 5x5 con surcos de cultivo
        int size = 5;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                boolean isWater = (x == size / 2 && z == size / 2);
                boolean isCrop = !isWater && (x + z) % 2 == 0;

                if (isWater) {
                    // Colocar agua
                    buildPositions.add(buildOrigin.offset(x, 0, z));
                } else if (isCrop) {
                    // Colocar tierra de cultivo
                    buildPositions.add(buildOrigin.offset(x, 0, z));
                } else {
                    // Camino de tierra
                    buildPositions.add(buildOrigin.offset(x, 0, z));
                }
            }
        }
    }

    private void placeBlock(BlockPos pos) {
        // VERIFICACIÓN DE PROTECCIÓN: No construir en áreas protegidas
        ProtectionManager protection = ProtectionManager.getInstance();
        if (!protection.canPlaceBlock(bot.serverLevel(), pos, bot)) {
            bot.say("I can't build here, this area is protected!");
            // Saltar esta posición y continuar con la siguiente
            return;
        }

        // Verificar si ya hay un bloque ahí
        if (!bot.level().getBlockState(pos).isAir()) {
            return;
        }

        // Buscar material de construcción en el inventario
        ItemStack material = findBuildingMaterial();
        if (material.isEmpty()) {
            bot.say("Out of building materials!");
            buildPositions.clear();
            return;
        }

        // Colocar el bloque
        bot.setItemInHand(InteractionHand.MAIN_HAND, material.copy());

        // Simular colocación
        var blockState = getBlockStateForMaterial(material);
        bot.level().setBlock(pos, blockState, 3);

        // Recordar que este bloque lo puso un bot: no debe contar como
        // construccion de jugador ni bloquear futuras tareas.
        ProtectionManager.getInstance().markBotPlaced(pos);

        // Consumir material
        material.shrink(1);

        // Efecto visual
        bot.swing(InteractionHand.MAIN_HAND);
        bot.level().playSound(null, pos,
                net.minecraft.sounds.SoundEvents.STONE_PLACE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private ItemStack findBuildingMaterial() {
        // Prioridad: cobblestone > planks > dirt
        ItemStack cobble = findItem(Items.COBBLESTONE);
        if (!cobble.isEmpty()) return cobble;

        ItemStack planks = findItem(Items.OAK_PLANKS);
        if (!planks.isEmpty()) return planks;

        ItemStack dirt = findItem(Items.DIRT);
        if (!dirt.isEmpty()) return dirt;

        return ItemStack.EMPTY;
    }

    private ItemStack findItem(net.minecraft.world.item.Item item) {
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private net.minecraft.world.level.block.state.BlockState getBlockStateForMaterial(ItemStack material) {
        if (material.is(Items.COBBLESTONE)) {
            return Blocks.COBBLESTONE.defaultBlockState();
        } else if (material.is(Items.OAK_PLANKS)) {
            return Blocks.OAK_PLANKS.defaultBlockState();
        } else if (material.is(Items.DIRT)) {
            return Blocks.DIRT.defaultBlockState();
        }
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    public void setPlan(BuildPlan plan) {
        this.plan = plan;
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    public String getName() {
        return "Building " + (plan != null ? plan.name().toLowerCase().replace("_", " ") : "structure");
    }

    @Override
    public AIState getState() {
        return AIState.BUILDING;
    }

    @Override
    public boolean canExecute() {
        // Además de tener materiales, esperar un rato tras construir para no
        // encadenar refugios sin parar en el mismo sitio.
        return bot.getTarget() == null
                && hasBuildingMaterials()
                && bot.tickCount - lastBuildTick > BUILD_COOLDOWN_TICKS;
    }

    private boolean hasBuildingMaterials() {
        return findBuildingMaterial().getCount() >= 10;
    }
}
