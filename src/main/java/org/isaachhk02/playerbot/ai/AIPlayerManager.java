package org.isaachhk02.playerbot.ai;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import org.isaachhk02.playerbot.PlayerBOT;
import org.isaachhk02.playerbot.network.BotConnection;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de jugadores IA. Maneja la creación, eliminación y coordinación de bots.
 * Los bots se registran como jugadores reales en el servidor (ocupan slots).
 */
public class AIPlayerManager {
    private static AIPlayerManager instance;

    private final Map<UUID, AIPlayer> bots = new ConcurrentHashMap<>();
    private final Map<String, UUID> botsByName = new ConcurrentHashMap<>();
    private final MinecraftServer server;
    private int botCounter = 0;

    private static final int RESPAWN_DELAY_TICKS = 100;
    private final Map<String, Integer> pendingRespawns = new ConcurrentHashMap<>();

    private AIPlayerManager(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Inicializa la reflexion para acceder a metodos protegidos de PlayerList
     */
    private void initReflection() {
        // No necesitamos reflexion, placeNewPlayer es publico
        PlayerBOT.LOGGER.info("PlayerList methods ready (using placeNewPlayer)");
    }

    public static AIPlayerManager getInstance(MinecraftServer server) {
        if (instance == null) {
            instance = new AIPlayerManager(server);
        }
        return instance;
    }

    public static AIPlayerManager getInstance() {
        return instance;
    }

    /**
     * Verifica si se puede crear un nuevo bot (considerando el limite de slots)
     */
    public boolean canCreateBot() {
        PlayerList playerList = server.getPlayerList();
        // getPlayerCount() YA incluye a los bots: están registrados en la lista de
        // jugadores como cualquier otro jugador (para aparecer en el tab list).
        // Antes se volvían a sumar aquí (currentPlayers + botCount), lo que
        // duplicaba el conteo y el servidor se declaraba "lleno" con la mitad de
        // los slots realmente libres.
        return playerList.getPlayerCount() < playerList.getMaxPlayers();
    }

    /**
     * Obtiene el numero de slots disponibles
     */
    public int getAvailableSlots() {
        PlayerList playerList = server.getPlayerList();
        int maxPlayers = playerList.getMaxPlayers();
        int currentPlayers = playerList.getPlayerCount();
        return Math.max(0, maxPlayers - currentPlayers);
    }

    /**
     * Crea un nuevo bot en el servidor como jugador real (ocupa slot)
     */
    public AIPlayer createBot(ServerLevel level, BlockPos pos, String name) {
        return createBot(level, pos, name, GameType.SURVIVAL);
    }

    /**
     * Crea un nuevo bot con un modo de juego específico
     */
    public AIPlayer createBot(ServerLevel level, BlockPos pos, String name, GameType gameType) {
        // Verificar limite de slots
        if (!canCreateBot()) {
            PlayerBOT.LOGGER.warn("Cannot create bot {}: server is full (max players reached)", name);
            throw new RuntimeException("Server is full. Cannot create more bots.");
        }

        // Validar y truncar nombre (max 16 caracteres en Minecraft)
        if (name == null || name.isEmpty()) {
            name = "Bot" + botCounter;
        }
        if (name.length() > 16) {
            PlayerBOT.LOGGER.warn("Bot name '{}' too long ({} chars), truncating to 16", name, name.length());
            name = name.substring(0, 16);
        }

        // Generar UUID único para el bot
        UUID botUuid = UUID.nameUUIDFromBytes(("BOT:" + name + ":" + botCounter++).getBytes());

        // Crear perfil de juego (sin el prefijo [BOT] para que parezca un jugador normal)
        String displayName = name;
        GameProfile profile = new GameProfile(botUuid, displayName);

        // Crear el bot
        AIPlayer bot = new AIPlayer(server, level, profile, botUuid);
        bot.setGameMode(gameType);

        // Marcar como bot para la tab list
        bot.setBot(true);

        // Posicionar en el mundo - encontrar una posición libre cercana
        BlockPos spawnPos = findFreeSpawnPosition(level, pos);
        int groundY = level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                spawnPos.getX(), spawnPos.getZ());
        bot.moveTo(spawnPos.getX() + 0.5, groundY, spawnPos.getZ() + 0.5, 0, 0);

        // Registrar en el gestor
        bots.put(botUuid, bot);
        botsByName.put(name.toLowerCase(), botUuid);

        // IMPORTANTE (orden): primero hacemos que el bot aparezca en la lista de
        // jugadores y enviamos a los clientes el paquete de informacion de jugador
        // (ADD_PLAYER). Solo despues añadimos el bot al nivel.
        //
        // Al meter la entidad en el nivel, ChunkMap.addEntity() envia de inmediato
        // el paquete de aparicion (AddEntity) a los jugadores cercanos. Si ese
        // paquete llega antes que el ADD_PLAYER, el cliente lo descarta con el
        // aviso "Server attempted to add player prior to sending player info" y el
        // bot queda invisible. Es el mismo orden que usa el servidor vanilla.
        addBotToPlayerList(bot);

        // Añadir al mundo: registra el bot en el tracking del nivel y envia a los
        // jugadores cercanos el paquete de aparicion (AddEntity).
        level.addNewPlayer(bot);

        PlayerBOT.LOGGER.info("Created AI Bot: {} at {} ({}, {}, {}) - Occupying player slot",
                name, spawnPos.toShortString(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());

        // Anunciar en el chat
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§a[BOT] §f" + name + " has joined the game!"),
                false
        );

        return bot;
    }

    /**
     * Encuentra una posición de spawn libre cerca de la posición dada,
     * evitando colisiones con otros bots y jugadores.
     */
    private BlockPos findFreeSpawnPosition(ServerLevel level, BlockPos targetPos) {
        // Primero verificar si la posición objetivo está libre
        if (isPositionFree(level, targetPos)) {
            return targetPos;
        }

        // Buscar en espiral alrededor de la posición objetivo
        int radius = 1;
        int maxRadius = 10;
        Random random = new Random();

        while (radius <= maxRadius) {
            // Probar posiciones en un anillo alrededor del centro
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Solo probar el perímetro del cuadrado
                    if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                        BlockPos testPos = targetPos.offset(dx, 0, dz);
                        if (isPositionFree(level, testPos)) {
                            return testPos;
                        }
                    }
                }
            }
            radius++;
        }

        // Si no se encuentra posición libre, añadir algo de aleatoriedad
        BlockPos fallback = targetPos.offset(
                random.nextInt(5) - 2,
                0,
                random.nextInt(5) - 2
        );
        return fallback;
    }

    /**
     * Verifica si una posición está libre de otros bots y jugadores.
     */
    private boolean isPositionFree(ServerLevel level, BlockPos pos) {
        // Verificar si hay bots en esta posición (con un margen de 1 bloque)
        for (AIPlayer bot : bots.values()) {
            if (bot != null && bot.isAlive()) {
                BlockPos botPos = bot.blockPosition();
                if (botPos.distSqr(pos) < 4) { // Menos de 2 bloques de distancia
                    return false;
                }
            }
        }

        // Verificar si hay jugadores en esta posición
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) < 4) {
                return false;
            }
        }

        // Verificar que el bloque no sea sólido
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.is(BlockTags.FLOWERS) && 
            !state.is(BlockTags.TALL_FLOWERS) &&
            !state.canBeReplaced()) {
            return false;
        }

        // Verificar el bloque de arriba también
        BlockState stateAbove = level.getBlockState(pos.above());
        if (!stateAbove.isAir() && 
            !stateAbove.is(BlockTags.FLOWERS) &&
            !stateAbove.is(BlockTags.TALL_FLOWERS)) {
            return false;
        }

        return true;
    }

    /**
     * Añade el bot a la PlayerList como jugador real.
     * Usa getPlayers() que devuelve una lista modificable.
     */
    private void addBotToPlayerList(AIPlayer bot) {
        try {
            PlayerList playerList = server.getPlayerList();

            // Metodo 1: Obtener la lista de jugadores y añadir directamente
            // getPlayers() devuelve una lista que puede ser modificable
            List<ServerPlayer> players = playerList.getPlayers();
            if (!players.contains(bot)) {
                players.add(bot);
                PlayerBOT.LOGGER.info("Bot {} added to getPlayers() list - now appears in /list", bot.getName().getString());
            }

            // Metodo 2: Usar reflexion para encontrar y modificar campos internos
            Class<?> playerListClass = playerList.getClass();
            boolean addedToField = false;

            // Buscar todos los campos de tipo List que contengan ServerPlayer
            for (java.lang.reflect.Field field : playerListClass.getDeclaredFields()) {
                if (List.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(playerList);
                        if (value instanceof List) {
                            List<?> list = (List<?>) value;
                            // Verificar si la lista contiene ServerPlayer
                            if (list.isEmpty() || list.get(0) instanceof ServerPlayer) {
                                if (!list.contains(bot)) {
                                    @SuppressWarnings({"unchecked", "rawtypes"})
                                    List rawList = list;
                                    rawList.add(bot);
                                    PlayerBOT.LOGGER.info("Bot {} added to field: {}", bot.getName().getString(), field.getName());
                                    addedToField = true;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignorar campos que no se pueden acceder
                    }
                }
            }

            // Buscar campos de tipo Map<UUID, ServerPlayer>
            for (java.lang.reflect.Field field : playerListClass.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(playerList);
                        if (value instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<UUID, ServerPlayer> map = (Map<UUID, ServerPlayer>) value;
                            if (!map.containsKey(bot.getUUID())) {
                                map.put(bot.getUUID(), bot);
                                PlayerBOT.LOGGER.info("Bot {} added to map field: {}", bot.getName().getString(), field.getName());
                            }
                        }
                    } catch (Exception e) {
                        // Ignorar
                    }
                }
            }

            // Metodo 3: Intentar añadir a través del constructor de PlayerList o métodos internos
            // Algunas versiones tienen un metodo add(ServerPlayer) protegido
            try {
                java.lang.reflect.Method addMethod = playerListClass.getDeclaredMethod("add", ServerPlayer.class);
                addMethod.setAccessible(true);
                addMethod.invoke(playerList, bot);
                PlayerBOT.LOGGER.info("Bot {} added via add() method", bot.getName().getString());
            } catch (NoSuchMethodException e) {
                // Metodo no existe, continuar
            } catch (Exception e) {
                PlayerBOT.LOGGER.debug("Could not use add() method: {}", e.getMessage());
            }

            // Enviar paquete de información de jugador a todos los clientes conectados
            broadcastPlayerJoined(bot);

            PlayerBOT.LOGGER.info("✅ Bot {} added to PlayerList - occupies slot ({} players total)",
                    bot.getName().getString(), playerList.getPlayers().size());

        } catch (Exception e) {
            PlayerBOT.LOGGER.error("Failed to add bot to PlayerList", e);
        }
    }

    /**
     * Busca un campo por nombre (puede variar entre versiones)
     */
    private java.lang.reflect.Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                // Intentar siguiente nombre
            }
        }
        // Buscar en clases padre
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
            return findField(superClass, names);
        }
        return null;
    }

    /**
     * Añade el bot manualmente a las listas internas de PlayerList
     */
    private void addBotToPlayerListManual(AIPlayer bot) {
        try {
            PlayerList playerList = server.getPlayerList();

            // Intentar añadir a la lista de jugadores via reflexion
            Class<?> playerListClass = playerList.getClass();

            // Buscar campo players (List<ServerPlayer>)
            try {
                java.lang.reflect.Field playersField = playerListClass.getDeclaredField("players");
                playersField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<ServerPlayer> players = (List<ServerPlayer>) playersField.get(playerList);
                if (!players.contains(bot)) {
                    players.add(bot);
                    PlayerBOT.LOGGER.info("Bot {} added to players list manually", bot.getName().getString());
                }
            } catch (Exception e) {
                PlayerBOT.LOGGER.debug("Could not add to players field: {}", e.getMessage());
            }

            // Buscar campo playersByUUID (Map<UUID, ServerPlayer>)
            try {
                java.lang.reflect.Field playersByUUIDField = playerListClass.getDeclaredField("playersByUUID");
                playersByUUIDField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<UUID, ServerPlayer> playersByUUID = (Map<UUID, ServerPlayer>) playersByUUIDField.get(playerList);
                playersByUUID.put(bot.getUUID(), bot);
                PlayerBOT.LOGGER.info("Bot {} added to playersByUUID map", bot.getName().getString());
            } catch (Exception e) {
                PlayerBOT.LOGGER.debug("Could not add to playersByUUID field: {}", e.getMessage());
            }

            // Enviar paquete de jugador añadido a todos los clientes
            broadcastPlayerJoined(bot);

        } catch (Exception e) {
            PlayerBOT.LOGGER.error("Failed to manually add bot to PlayerList", e);
        }
    }

    /**
     * Envía paquete de jugador unido y paquete de aparición de entidad a todos los clientes
     */
    private void broadcastPlayerJoined(ServerPlayer bot) {
        try {
            // 1. Paquete de información de jugador (para tab list)
            var playerInfo = net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(
                    java.util.List.of(bot)
            );

            // Enviar a todos los jugadores conectados
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player != bot && player.connection != null) {
                    player.connection.send(playerInfo);
                }
            }

            // 2. Este paquete DEBE enviarse antes de ServerLevel.addNewPlayer(bot),
            //    porque al añadir la entidad al nivel se envia en el acto el paquete
            //    de aparicion (AddEntity). El cliente descarta ese paquete si aun no
            //    conoce el perfil del jugador (ADD_PLAYER) y el bot sale invisible.
            //    createBot() respeta ese orden.
        } catch (Exception e) {
            PlayerBOT.LOGGER.debug("Could not broadcast player joined: {}", e.getMessage());
        }
    }

    /**
     * Crea un bot cerca de un jugador (evitando colisiones con otros bots)
     */
    public AIPlayer createBotNearPlayer(ServerPlayer player, String name) {
        ServerLevel level = player.serverLevel();
        BlockPos basePos = player.blockPosition().offset(
                (int) (Math.random() * 8 - 4),
                0,
                (int) (Math.random() * 8 - 4)
        );
        return createBot(level, basePos, name);
    }

    /**
     * Elimina un bot del servidor (libera slot)
     */
    public boolean removeBot(UUID botUuid) {
        AIPlayer bot = bots.remove(botUuid);
        if (bot != null) {
            botsByName.remove(bot.getName().getString().toLowerCase());

            // Quitar de la PlayerList (libera slot)
            removeBotFromPlayerList(bot);

            bot.say("Goodbye!");
            bot.serverLevel().removePlayerImmediately(bot, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§c[BOT] §f" + bot.getName().getString() + " has left the game!"),
                    false
            );

            PlayerBOT.LOGGER.info("Removed AI Bot: {} - Freed player slot", bot.getName().getString());
            return true;
        }
        return false;
    }

    /**
     * Quita el bot de la PlayerList usando reflexion directa
     */
    private void removeBotFromPlayerList(AIPlayer bot) {
        try {
            PlayerList playerList = server.getPlayerList();
            Class<?> playerListClass = playerList.getClass();

            playerList.getPlayers().remove(bot);

            // 1. Quitar de la lista players
            try {
                java.lang.reflect.Field playersField = findField(playerListClass, "players", "field_11196");
                if (playersField != null) {
                    playersField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<ServerPlayer> players = (List<ServerPlayer>) playersField.get(playerList);
                    players.remove(bot);
                    PlayerBOT.LOGGER.info("Bot {} removed from players list", bot.getName().getString());
                }
            } catch (Exception e) {
                PlayerBOT.LOGGER.error("Failed to remove from players list: {}", e.getMessage());
            }

            // 2. Quitar del mapa playersByUUID
            try {
                java.lang.reflect.Field playersByUUIDField = findField(playerListClass, "playersByUUID", "field_11199");
                if (playersByUUIDField != null) {
                    playersByUUIDField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<UUID, ServerPlayer> playersByUUID = (Map<UUID, ServerPlayer>) playersByUUIDField.get(playerList);
                    playersByUUID.remove(bot.getUUID());
                    PlayerBOT.LOGGER.info("Bot {} removed from playersByUUID map", bot.getName().getString());
                }
            } catch (Exception e) {
                PlayerBOT.LOGGER.error("Failed to remove from playersByUUID: {}", e.getMessage());
            }

            // 3. Actualizar contador de jugadores
            try {
                java.lang.reflect.Field playerCountField = findField(playerListClass, "playerCount", "field_11200");
                if (playerCountField != null) {
                    playerCountField.setAccessible(true);
                    int current = playerCountField.getInt(playerList);
                    if (current > 0) {
                        playerCountField.setInt(playerList, current - 1);
                    }
                    PlayerBOT.LOGGER.info("Updated player count to {}", Math.max(0, current - 1));
                }
            } catch (Exception e) {
                PlayerBOT.LOGGER.debug("Could not update playerCount field: {}", e.getMessage());
            }

            // 4. Enviar paquete de jugador desconectado
            broadcastPlayerLeft(bot);

            PlayerBOT.LOGGER.info("✅ Bot {} removed from PlayerList - slot freed", bot.getName().getString());

        } catch (Exception e) {
            PlayerBOT.LOGGER.error("Failed to remove bot from PlayerList", e);
        }
    }

    /**
     * Envía paquete de jugador desconectado a todos los clientes
     */
    private void broadcastPlayerLeft(ServerPlayer bot) {
        try {
            var removePacket = new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(
                    java.util.List.of(bot.getUUID())
            );

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player != bot && player.connection != null) {
                    player.connection.send(removePacket);
                }
            }
        } catch (Exception e) {
            PlayerBOT.LOGGER.debug("Could not broadcast player left: {}", e.getMessage());
        }
    }

    /**
     * Elimina un bot por nombre
     */
    public boolean removeBotByName(String name) {
        UUID uuid = botsByName.get(name.toLowerCase());
        if (uuid != null) {
            return removeBot(uuid);
        }
        return false;
    }

    /**
     * Obtiene un bot por UUID
     */
    public AIPlayer getBot(UUID uuid) {
        return bots.get(uuid);
    }

    /**
     * Obtiene un bot por nombre
     */
    public AIPlayer getBotByName(String name) {
        UUID uuid = botsByName.get(name.toLowerCase());
        return uuid != null ? bots.get(uuid) : null;
    }

    /**
     * Obtiene todos los bots activos
     */
    public Collection<AIPlayer> getAllBots() {
        return Collections.unmodifiableCollection(bots.values());
    }

    /**
     * Obtiene el número de bots activos
     */
    public int getBotCount() {
        return bots.size();
    }

    /**
     * Obtiene el total de jugadores (humanos + bots)
     */
    public int getTotalPlayerCount() {
        try {
            PlayerList playerList = server.getPlayerList();
            // Intentar obtener el conteo real de la lista
            int realCount = playerList.getPlayers().size();
            // Si la lista incluye bots, devolver ese numero
            // Si no, sumar bots manualmente
            if (realCount >= bots.size()) {
                return realCount;
            } else {
                return realCount + bots.size();
            }
        } catch (Exception e) {
            // Fallback: contar bots + jugadores humanos conocidos
            return bots.size() + (int) server.getPlayerList().getPlayers().stream()
                    .filter(p -> !(p instanceof AIPlayer))
                    .count();
        }
    }

    /**
     * Busca bots cercanos a una posición
     */
    public List<AIPlayer> getNearbyBots(BlockPos pos, double radius) {
        List<AIPlayer> nearby = new ArrayList<>();
        for (AIPlayer bot : bots.values()) {
            if (bot.blockPosition().closerThan(pos, radius)) {
                nearby.add(bot);
            }
        }
        return nearby;
    }

    /**
     * Crea un grupo de bots aliados
     */
    public void createBotGroup(String... botNames) {
        List<AIPlayer> group = new ArrayList<>();
        for (String name : botNames) {
            AIPlayer bot = getBotByName(name);
            if (bot != null) {
                group.add(bot);
            }
        }

        // Hacer que todos sean aliados entre sí
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                group.get(i).addAlly(group.get(j));
            }
        }

        if (!group.isEmpty()) {
            group.get(0).say("We are now a team of " + group.size() + "!");
        }
    }

    /**
     * Envía un mensaje a todos los bots
     */
    public void broadcastToAllBots(String message) {
        for (AIPlayer bot : bots.values()) {
            bot.say(message);
        }
    }

    /**
     * Hace que todos los bots sigan a un jugador
     */
    public void allBotsFollow(Player player) {
        for (AIPlayer bot : bots.values()) {
            bot.follow(player);
        }
    }

    /**
     * Hace que todos los bots dejen de seguir
     */
    public void allBotsStopFollowing() {
        for (AIPlayer bot : bots.values()) {
            bot.stopAll();
        }
    }

    /**
     * Detiene todos los bots
     */
    public void stopAllBots() {
        for (AIPlayer bot : bots.values()) {
            bot.stopAll();
        }
    }

    /**
     * Elimina todos los bots (libera todos los slots)
     */
    public void removeAllBots() {
        List<UUID> toRemove = new ArrayList<>(bots.keySet());
        for (UUID uuid : toRemove) {
            removeBot(uuid);
        }
    }

    /**
     * Obtiene estadísticas de todos los bots
     */
    public String getAllStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== AI Bots Stats ===\n");
        sb.append("§fTotal bots: §a").append(bots.size()).append("\n");
        sb.append("§fTotal players (incl. bots): §a").append(getTotalPlayerCount()).append("\n");
        sb.append("§fMax players: §a").append(server.getPlayerList().getMaxPlayers()).append("\n\n");

        for (AIPlayer bot : bots.values()) {
            sb.append("§e").append(bot.getBotStats()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Tick para actualizar todos los bots (llamado desde el servidor)
     */
    public void tick() {
        // Bots muertos: quitarlos y programar su reaparicion
        bots.entrySet().removeIf(entry -> {
            AIPlayer bot = entry.getValue();
            if (bot.isRemoved() || !bot.isAlive()) {
                String name = bot.getName().getString();
                botsByName.remove(name.toLowerCase());
                removeBotFromPlayerList(bot);
                pendingRespawns.put(name, RESPAWN_DELAY_TICKS);
                return true;
            }
            return false;
        });

        for (String name : new ArrayList<>(pendingRespawns.keySet())) {
            int left = pendingRespawns.get(name) - 1;
            if (left > 0) {
                pendingRespawns.put(name, left);
            } else {
                pendingRespawns.remove(name);
                respawnBot(name);
            }
        }
    }

    private void respawnBot(String name) {
        try {
            ServerLevel level = server.overworld();
            createBot(level, level.getSharedSpawnPos(), name);
            PlayerBOT.LOGGER.info("Respawned bot {} at world spawn", name);
        } catch (Exception e) {
            PlayerBOT.LOGGER.error("Failed to respawn bot {}: {}", name, e.getMessage());
        }
    }

    /**
     * Envia la informacion y la entidad de todos los bots a un jugador que acaba de conectarse.
     */
    public void syncBotsTo(ServerPlayer target) {
        if (bots.isEmpty()) {
            return;
        }
        List<ServerPlayer> list = new ArrayList<ServerPlayer>(bots.values());
        target.connection.send(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(list));
    }
}

