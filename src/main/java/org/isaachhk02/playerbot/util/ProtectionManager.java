package org.isaachhk02.playerbot.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.isaachhk02.playerbot.ai.AIPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de protección de áreas.
 * Evita que los bots destruyan propiedades de otros jugadores, ataquen aliados,
 * o interfieran con áreas protegidas.
 */
public class ProtectionManager {
    private static ProtectionManager instance;

    // Áreas protegidas registradas (claims simples basados en chunks o posiciones)
    private final Map<UUID, Set<ChunkPos>> playerClaims = new ConcurrentHashMap<>();
    private final Set<ChunkPos> protectedChunks = ConcurrentHashMap.newKeySet();

    // Bloques que han colocado los propios bots. NO deben contar como
    // "construccion de jugador": si no, cada bloque que coloca un bot protege su
    // propio alrededor y le impide seguir construyendo o minar ahi.
    private final Set<BlockPos> botPlacedBlocks = ConcurrentHashMap.newKeySet();

    // Radio de protección alrededor de cada jugador humano. Debe ser pequeño:
    // solo evita que los bots coloquen bloques encima del jugador. Con un valor
    // grande (antes 16) los bots no podían construir en ninguna parte mientras
    // hubiera un jugador cerca.
    private static final int PLAYER_PROTECTION_RADIUS = 2;

    // Radio de protección alrededor del spawn del mundo. Se usa el valor por
    // defecto de vanilla (16). Con 32 los bots no podían construir en toda la
    // zona de aparición.
    private static final int SPAWN_PROTECTION_RADIUS = 16;

    // Distancia mínima entre bots y construcciones de otros jugadores. Se mantiene
    // pequeña para que la comprobacion no bloquee zonas enteras ni sea costosa.
    private static final int MIN_DISTANCE_TO_PLAYER_BUILDS = 4;

    private ProtectionManager() {}

    public static ProtectionManager getInstance() {
        if (instance == null) {
            instance = new ProtectionManager();
        }
        return instance;
    }

    /**
     * Verifica si una entidad es un objetivo válido para atacar (solo monstruos hostiles)
     */
    public boolean isValidAttackTarget(AIPlayer bot, Entity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }

        // SOLO monstruos hostiles del juego (zombies, esqueletos, creepers, etc.)
        if (!(target instanceof net.minecraft.world.entity.monster.Monster)) {
            return false;
        }

        // No atacar si el monstruo es de un aliado (raro pero posible con mods)
        if (target instanceof net.minecraft.world.entity.TamableAnimal tamable && tamable.isOwnedBy(bot)) {
            return false;
        }

        return true;
    }

    /**
     * Verifica si un bot puede atacar a otro jugador/bot
     * Por defecto, los bots NO atacan a jugadores ni a otros bots
     */
    public boolean canAttackPlayer(AIPlayer bot, Player target) {
        // Los bots nunca atacan a jugadores humanos por defecto
        if (!(target instanceof AIPlayer)) {
            return false; // No atacar jugadores humanos
        }

        // No atacar a otros bots aliados
        AIPlayer targetBot = (AIPlayer) target;
        if (bot.isAlly(targetBot)) {
            return false;
        }

        // Solo atacar a otros bots si están en modo PvP explícito (desactivado por defecto)
        return false;
    }

    /**
     * Verifica si una posición está protegida (no se puede modificar)
     */
    public boolean isProtected(ServerLevel level, BlockPos pos, AIPlayer bot) {
        // 1. Verificar protección del spawn
        BlockPos spawnPos = level.getSharedSpawnPos();
        if (pos.distSqr(spawnPos) < SPAWN_PROTECTION_RADIUS * SPAWN_PROTECTION_RADIUS) {
            return true;
        }

        return isProtectedByPlayersOrClaims(level, pos, bot);
    }

    /**
     * Protecciones que NO son del spawn: jugadores humanos cerca, construcciones
     * de jugadores, claims registrados y letreros de propiedad.
     */
    private boolean isProtectedByPlayersOrClaims(ServerLevel level, BlockPos pos, AIPlayer bot) {
        // Verificar si está cerca de un jugador humano (protección personal)
        List<ServerPlayer> nearbyPlayers = level.getPlayers(player ->
                !(player instanceof AIPlayer) &&
                player.blockPosition().distSqr(pos) < PLAYER_PROTECTION_RADIUS * PLAYER_PROTECTION_RADIUS
        );
        if (!nearbyPlayers.isEmpty()) {
            return true;
        }

        // 3. Verificar si está cerca de construcciones de otros jugadores
        if (isNearPlayerConstruction(level, pos)) {
            return true;
        }

        // 4. Verificar claims registrados
        ChunkPos chunkPos = new ChunkPos(pos);
        if (protectedChunks.contains(chunkPos)) {
            return true;
        }

        // 5. Verificar si hay signos de "propiedad" cercanos
        if (hasOwnershipSign(level, pos)) {
            return true;
        }

        return false;
    }

    /**
     * Verifica si una posición está cerca de construcciones de jugadores
     */
    private boolean isNearPlayerConstruction(ServerLevel level, BlockPos pos) {
        // Buscar bloques "humanos" (no naturales) en un radio
        int radius = MIN_DISTANCE_TO_PLAYER_BUILDS;

        // Reutilizamos una posición mutable para no crear 729 BlockPos por
        // comprobación (esto se llamaba miles de veces por intento de construir).
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseX = pos.getX();
        int baseY = pos.getY();
        int baseZ = pos.getZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(baseX + dx, baseY + dy, baseZ + dz);
                    if (isPlayerMadeBlock(level, cursor)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Verifica si un bloque parece hecho por un jugador (no natural)
     */
    private boolean isPlayerMadeBlock(ServerLevel level, BlockPos pos) {
        // Un bloque colocado por un bot nunca cuenta como construccion de jugador.
        if (botPlacedBlocks.contains(pos)) {
            return false;
        }

        var state = level.getBlockState(pos);

        // El aire (y los bloques que no ocupan nada) no aportan nada.
        if (state.isAir()) {
            return false;
        }

        // Bloques que los jugadores colocan comúnmente
        return state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE) ||
               state.is(net.minecraft.world.level.block.Blocks.STONE_BRICKS) ||
               state.is(net.minecraft.world.level.block.Blocks.BRICKS) ||
               state.is(net.minecraft.world.level.block.Blocks.GLASS) ||
               state.is(net.minecraft.world.level.block.Blocks.GLASS_PANE) ||
               state.is(net.minecraft.world.level.block.Blocks.OAK_PLANKS) ||
               state.is(net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS) ||
               state.is(net.minecraft.world.level.block.Blocks.BIRCH_PLANKS) ||
               state.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE) ||
               state.is(net.minecraft.world.level.block.Blocks.FURNACE) ||
               state.is(net.minecraft.world.level.block.Blocks.CHEST) ||
               state.is(net.minecraft.world.level.block.Blocks.OAK_DOOR) ||
               state.is(net.minecraft.world.level.block.Blocks.OAK_FENCE) ||
               state.is(net.minecraft.world.level.block.Blocks.TORCH) ||
               state.is(net.minecraft.world.level.block.Blocks.LANTERN) ||
               state.is(net.minecraft.world.level.block.Blocks.BOOKSHELF) ||
               state.is(net.minecraft.world.level.block.Blocks.IRON_BLOCK);
    }

    /**
     * Verifica si hay un letrero de propiedad cercano
     */
    private boolean hasOwnershipSign(ServerLevel level, BlockPos pos) {
        // Radio pequeño y a la altura del bot: los letreros de propiedad se
        // colocan a la vista. Un radio grande (8) hacía miles de consultas de
        // block-entity por cada posición evaluada.
        int radius = 4;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseX = pos.getX();
        int baseY = pos.getY();
        int baseZ = pos.getZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(baseX + dx, baseY + dy, baseZ + dz);
                    BlockEntity be = level.getBlockEntity(cursor);

                    if (be instanceof SignBlockEntity sign) {
                        // Obtener texto del letrero de forma segura
                        String text = "";
                        try {
                            var frontText = sign.getText(false);
                            if (frontText != null) {
                                text = frontText.getMessage(0, false).getString().toLowerCase();
                            }
                        } catch (Exception e) {
                            // Ignorar errores de lectura de letreros
                        }
                        // Detectar letreros de propiedad comunes
                        if (text.contains("private") || text.contains("property") ||
                            text.contains("no grief") || text.contains("keep out") ||
                            text.contains("casa") || text.contains("home") ||
                            text.contains("propiedad") || text.contains("privado")) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Verifica si un bot puede modificar (romper/colocar) un bloque en una posición
     */
    public boolean canModifyBlock(ServerLevel level, BlockPos pos, AIPlayer bot) {
        // No modificar si está protegido
        if (isProtected(level, pos, bot)) {
            return false;
        }

        // No modificar bloques de contenedores (cofres, hornos, etc.)
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity ||
            be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) {
            return false;
        }

        return true;
    }

    /**
     * Variante usada por la tarea de escape: permite romper bloques dentro de la
     * protección del spawn (si no, un bot atrapado cerca del spawn nunca podría
     * cavar para salir), pero mantiene el resto de protecciones (jugadores,
     * construcciones, claims y contenedores).
     */
    public boolean canModifyBlockAllowSpawn(ServerLevel level, BlockPos pos, AIPlayer bot) {
        if (isProtectedByPlayersOrClaims(level, pos, bot)) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity ||
            be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) {
            return false;
        }

        return true;
    }

    /**
     * Verifica si un bot puede colocar un bloque en una posición
     */
    public boolean canPlaceBlock(ServerLevel level, BlockPos pos, AIPlayer bot) {
        // No colocar si la posición está protegida
        if (isProtected(level, pos, bot)) {
            return false;
        }

        // No colocar sobre bloques importantes
        var state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) {
            return false;
        }

        return true;
    }

    /**
     * Registra un bloque colocado por un bot para que no se confunda con una
     * construccion de jugador (y no bloquee a los demas bots).
     */
    public void markBotPlaced(BlockPos pos) {
        botPlacedBlocks.add(pos.immutable());
    }

    /**
     * Quita del registro un bloque que el bot dejo de ocupar (p. ej. al minarlo).
     */
    public void unmarkBotPlaced(BlockPos pos) {
        botPlacedBlocks.remove(pos);
    }

    /**
     * Registra un área protegida (claim)
     */
    public void addProtectedArea(UUID ownerId, BlockPos min, BlockPos max) {
        // Convertir a chunks y proteger
        int minChunkX = min.getX() >> 4;
        int maxChunkX = max.getX() >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkZ = max.getZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                protectedChunks.add(new ChunkPos(cx, cz));
            }
        }
    }

    /**
     * Elimina un área protegida
     */
    public void removeProtectedArea(BlockPos min, BlockPos max) {
        int minChunkX = min.getX() >> 4;
        int maxChunkX = max.getX() >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkZ = max.getZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                protectedChunks.remove(new ChunkPos(cx, cz));
            }
        }
    }

    /**
     * Clase auxiliar para representar coordenadas de chunk
     */
    public record ChunkPos(int x, int z) {
        public ChunkPos(BlockPos pos) {
            this(pos.getX() >> 4, pos.getZ() >> 4);
        }
    }
}
