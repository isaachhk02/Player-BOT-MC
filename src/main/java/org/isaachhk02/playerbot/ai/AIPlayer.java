package org.isaachhk02.playerbot.ai;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.isaachhk02.playerbot.PlayerBOT;
import org.isaachhk02.playerbot.ai.tasks.*;
import org.isaachhk02.playerbot.network.BotConnection;
import org.isaachhk02.playerbot.util.BotNavigation;
import org.isaachhk02.playerbot.util.ProtectionManager;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jugador controlado por IA en el servidor.
 * Extiende ServerPlayer para tener todas las capacidades de un jugador real,
 * pero con comportamiento controlado por IA en lugar de un cliente humano.
 */
public class AIPlayer extends ServerPlayer {
    private static final UUID BOT_UUID_BASE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // Estado de la IA
    private AIState currentState = AIState.IDLE;
    private AITask currentTask = null;
    private final Queue<AITask> taskQueue = new ConcurrentLinkedQueue<>();
    private final List<AITask> availableTasks = new ArrayList<>();

    // Cooldown por tarea: evita que una tarea sin trabajo (que termina nada mas
    // empezar) monopolice la seleccion de tareas en cada tick e impida que otras
    // tareas (moverse, explorar, seguir, etc.) lleguen a ejecutarse.
    private final Map<AITask, Integer> taskCooldowns = new HashMap<>();
    private static final int TASK_RETRY_COOLDOWN = 100;
    private int taskStartTick = 0;

    // Objetivos y blancos
    private Entity target = null;
    private BlockPos targetPos = null;
    private Player followTarget = null;

    // Grupo y cooperación
    private final Set<UUID> allies = new HashSet<>();
    private UUID groupId = null;

    // Chat
    private final Queue<String> chatQueue = new ConcurrentLinkedQueue<>();
    private long lastChatTime = 0;
    // Chat muy reducido a propósito. Antes las tareas encolaban un mensaje por
    // tick (p. ej. "I can't break through here...") y el chat del servidor se
    // inundaba. Ahora: como mucho un mensaje cada 15 s por bot, sin repetir el
    // mismo mensaje en 2 minutos y con una cola de 3 mensajes.
    private static final long CHAT_COOLDOWN = 15000;        // 15 s entre mensajes
    private static final long SAME_MESSAGE_COOLDOWN = 120000; // 2 min por mensaje repetido
    private static final int MAX_CHAT_QUEUE = 3;
    private final Map<String, Long> recentMessages = new HashMap<>();

    // Estadísticas
    private int ticksSinceLastAction = 0;
    private float healthLastTick = 20.0f;
    private boolean isInCombat = false;
    private long combatStartTime = 0;

    // Inventario y equipamiento
    private ItemStack preferredWeapon = ItemStack.EMPTY;
    private ItemStack preferredTool = ItemStack.EMPTY;
    // Cada cuántos ticks se revisa si hay mejor armadura que equipar.
    private int armorCheckTicks = 0;

    // Movimiento y detección de atascos. Si la navegación quiere moverse pero el
    // bot no avanza horizontalmente durante un intervalo, se considera atascado
    // (hoyo, zanja, bajo tierra...) y se lanza la tarea de escape, que rompe
    // bloques hasta salir.
    private Vec3 lastPosition = Vec3.ZERO;
    private Vec3 stuckCheckPos = null;
    private int stuckCheckTicks = 0;
    private boolean stuck = false;
    // Si en la ventana de observación el bot intentó moverse (alguna tarea pidió
    // navegar). Así no confundimos "estaba minando/construyendo" con "atascado".
    private boolean triedToMove = false;
    private EscapeTask escapeTask;
    private static final int STUCK_CHECK_INTERVAL = 40;  // 2 segundos
    private static final double STUCK_MIN_MOVE = 0.75;   // bloques mínimos avanzados

    // Percepción
    private static final double SIGHT_RANGE = 32.0;
    private static final double FOLLOW_RANGE = 3.0;
    private static final double COMBAT_RANGE = 4.0;

    // Modo de combate del bot
    public enum CombatMode {
        PASSIVE,      // No ataca, solo huye cuando lo atacan
        DEFENSIVE,    // Solo se defiende (bloquea/esquiva), no inicia combate
        AGGRESSIVE    // Ataca monstruos cercanos automáticamente
    }

    private CombatMode combatMode = CombatMode.DEFENSIVE;

    // Marcador de bot (para tab list)
    private boolean isBot = false;

    // Navegación del bot
    private final BotNavigation navigation;

    private final Random random = new Random();

    public AIPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, UUID botId) {
        super(server, level, profile, createDefaultClientInfo());

        // Inicializar navegación
        this.navigation = new BotNavigation(this);

        // Configurar como jugador de supervivencia por defecto
        this.setGameMode(GameType.SURVIVAL);

        // Inicializar tareas disponibles
        initializeTasks();

        // Los bots empiezan SIN inventario: deben juntar madera y piedra,
        // fabricar sus herramientas y cazar para conseguir comida y armadura.

        // Configurar conexion dummy para evitar NullPointerException
        setupDummyConnection(server, level);

        PlayerBOT.LOGGER.info("Created AI Player: {} ({})", profile.getName(), botId);
    }

    /**
     * Configura una conexion dummy para el bot.
     * Esto evita NullPointerException cuando el servidor intenta enviar paquetes.
     */
    private void setupDummyConnection(MinecraftServer server, ServerLevel level) {
        try {
            BotConnection connection = BotConnection.createDummy(server, this, level);
            this.connection = connection;
        } catch (Exception e) {
            PlayerBOT.LOGGER.error("Failed to setup dummy connection for bot", e);
        }
    }

    /**
     * Obtiene el sistema de navegación del bot
     */
    public BotNavigation getNavigation() {
        return navigation;
    }

    /**
     * Crea información de cliente por defecto para el bot
     */
    private static net.minecraft.server.level.ClientInformation createDefaultClientInfo() {
        return new net.minecraft.server.level.ClientInformation(
                "en_us",           // language
                8,                 // viewDistance
                net.minecraft.world.entity.player.ChatVisiblity.FULL, // chatVisibility
                true,              // chatColors
                0,                 // modelCustomisation
                net.minecraft.world.entity.HumanoidArm.RIGHT, // mainHand
                false,             // textFiltering
                true               // allowsListing
        );
    }

    private void initializeTasks() {
        availableTasks.add(new FleeTask(this));
        availableTasks.add(new DefendTask(this));
        availableTasks.add(new FightTask(this));
        availableTasks.add(new HealTask(this));
        availableTasks.add(new HuntTask(this));
        escapeTask = new EscapeTask(this);
        availableTasks.add(escapeTask);
        availableTasks.add(new GatherTask(this));
        availableTasks.add(new MineTask(this));
        availableTasks.add(new CraftTask(this));
        availableTasks.add(new BuildTask(this));
        availableTasks.add(new FollowTask(this));
        availableTasks.add(new ExploreTask(this));
        availableTasks.add(new ChatTask(this));
        availableTasks.add(new MoveTask(this));
        availableTasks.add(new IdleTask(this));
    }

    @Override
    public void tick() {
        // Los bots no tienen conexion de red, por lo que el servidor nunca llama a
        // ServerPlayer.doTick() para ellos. Ese metodo ejecuta Player.tick(), que es
        // donde viven la fisica (gravedad, movimiento, colisiones), el consumo de
        // comida y la regeneracion natural de salud. Sin el, los bots flotan, no
        // colisionan con los bloques y no se curan al comer.
        this.doTick();

        super.tick();

        // Actualizar percepción del entorno
        updatePerception();

        // Equipar la mejor armadura disponible (los bots pueden usar armadura).
        if (++armorCheckTicks >= 40) {
            armorCheckTicks = 0;
            equipBestArmor();
        }

        // Procesar cola de chat
        processChatQueue();

        // Reducir los cooldowns de tareas que se quedaron sin trabajo.
        if (!taskCooldowns.isEmpty()) {
            taskCooldowns.replaceAll((t, v) -> v - 1);
            taskCooldowns.values().removeIf(v -> v <= 0);
        }

        // Ejecutar tarea actual o buscar nueva
        if (currentTask == null || !currentTask.isActive()) {
            selectNextTask();
        }

        // Ejecutar tarea actual
        if (currentTask != null && currentTask.isActive()) {
            currentTask.tick();
        }

        // Descartar la tarea si terminó o falló, incluso si lo hizo nada mas
        // empezar (start() puede fallar y dejarla inactiva sin haberse tickeado).
        if (currentTask != null && (currentTask.isCompleted() || currentTask.hasFailed())) {
            // Si la tarea no duró ni un par de ticks significa que no tenía trabajo
            // que hacer: ponerla en espera para que no bloquee a las demás.
            if (this.tickCount - taskStartTick <= 2) {
                taskCooldowns.put(currentTask, TASK_RETRY_COOLDOWN);
            }
            currentTask.stop();
            currentTask = null;
        }

        // Aplicar el movimiento de navegacion. Fija los ejes de movimiento y el giro
        // que usara la fisica de vanilla en el siguiente tick.
        this.navigation.tick();
        if (this.navigation.isMoving()) {
            triedToMove = true;
        }

        // Detectar si está atascado
        checkIfStuck();

        // Actualizar estadísticas
        ticksSinceLastAction++;
        healthLastTick = this.getHealth();
    }

    private void updatePerception() {
        // Buscar enemigos cercanos SOLO si está en modo agresivo
        if (combatMode == CombatMode.AGGRESSIVE) {
            if (target == null || !target.isAlive()) {
                target = findNearestEnemy();
            }
        } else {
            // En modo pasivo o defensivo, no buscar enemigos nuevos
            // Solo mantener el objetivo actual si ya está en combate
            if (target != null && !target.isAlive()) {
                target = null;
            }
        }

        // Verificar salud
        if (this.getHealth() < this.getMaxHealth() * 0.3f && currentState != AIState.FLEEING) {
            // Considerar huir si la salud es baja
        }

        // Verificar si está en combate
        if (target != null && this.distanceTo(target) < COMBAT_RANGE) {
            if (!isInCombat) {
                isInCombat = true;
                combatStartTime = this.tickCount;
            }
        } else if (isInCombat && (target == null || this.distanceTo(target) > SIGHT_RANGE)) {
            isInCombat = false;
        }
    }

    /**
     * Busca el monstruo hostil más cercano.
     * SOLO busca monstruos del juego (zombies, esqueletos, creepers, etc.)
     * NUNCA selecciona jugadores humanos ni bots aliados como enemigos.
     */
    private Entity findNearestEnemy() {
        AABB searchBox = AABB.ofSize(this.position(), SIGHT_RANGE * 2, SIGHT_RANGE * 2, SIGHT_RANGE * 2);

        // SOLO buscar monstruos hostiles (Monster class)
        List<Monster> monsters = this.level().getEntitiesOfClass(Monster.class, searchBox,
                m -> m.isAlive() && this.hasLineOfSight(m));

        ProtectionManager protection = ProtectionManager.getInstance();

        // Filtrar solo monstruos válidos como objetivos
        List<Monster> validMonsters = monsters.stream()
                .filter(m -> protection.isValidAttackTarget(this, m))
                .toList();

        if (validMonsters.isEmpty()) {
            return null;
        }

        return validMonsters.stream()
                .min(Comparator.comparingDouble(this::distanceTo))
                .orElse(null);
    }

    private void selectNextTask() {
        // Ordenar tareas por prioridad y encontrar la más adecuada
        AITask bestTask = null;
        int bestPriority = Integer.MIN_VALUE;

        for (AITask task : availableTasks) {
            // Saltar tareas que acaban de quedarse sin trabajo.
            if (taskCooldowns.containsKey(task)) {
                continue;
            }
            if (task.canExecute()) {
                int priority = task.getPriority();
                if (priority > bestPriority) {
                    bestPriority = priority;
                    bestTask = task;
                }
            }
        }

        if (bestTask != null) {
            currentTask = bestTask;
            currentTask.start();
            taskStartTick = this.tickCount;
            currentState = bestTask.getState();
            ticksSinceLastAction = 0;
        }
    }

    private void processChatQueue() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastChatTime > CHAT_COOLDOWN && !chatQueue.isEmpty()) {
            String message = chatQueue.poll();
            if (message != null && !message.isEmpty()) {
                sendChatMessage(message);
                lastChatTime = currentTime;
            }
        }
    }

    /**
     * Detecta si el bot está atascado: la navegación quiere llevarlo a algún sitio
     * pero no consigue avanzar horizontalmente. En ese caso se cancela la tarea
     * actual para que la tarea de escape (romper bloques y subir hasta salir) tome
     * el control en el siguiente tick.
     */
    private void checkIfStuck() {
        // Mientras nada, el bot avanza despacio a propósito (es la velocidad del
        // agua), así que no debe considerarse atascado: si no, lanzaría la tarea
        // de escape y se pondría a romper bloques debajo del agua.
        if (this.isInWater()) {
            stuckCheckPos = this.position();
            stuckCheckTicks = 0;
            triedToMove = false;
            stuck = false;
            return;
        }

        stuckCheckTicks++;
        if (stuckCheckTicks < STUCK_CHECK_INTERVAL) {
            return;
        }

        if (stuckCheckPos == null) {
            stuckCheckPos = this.position();
        }

        double dx = this.getX() - stuckCheckPos.x;
        double dz = this.getZ() - stuckCheckPos.z;
        double moved = Math.sqrt(dx * dx + dz * dz);

        boolean wasStuck = stuck;
        if (triedToMove) {
            // Intentó moverse: si no avanzó nada, está atascado.
            stuck = moved < STUCK_MIN_MOVE;
        } else {
            // No intentó moverse (minando, construyendo, curándose...): quedarse
            // quieto es lo normal, no es un atasco.
            stuck = false;
        }

        stuckCheckPos = this.position();
        stuckCheckTicks = 0;
        triedToMove = false;

        if (stuck && !wasStuck) {
            PlayerBOT.LOGGER.info("Bot {} looks stuck at {} (moved {} blocks in {} ticks)",
                    this.getName().getString(), this.blockPosition(),
                    String.format("%.2f", moved), STUCK_CHECK_INTERVAL);
            // Abortar lo que estuviera haciendo para que el escape actúe.
            if (currentTask != null && currentTask != escapeTask) {
                currentTask.cancel();
                currentTask = null;
            }
        }

        lastPosition = this.position();
    }

    /**
     * Indica si el bot se ha quedado atascado (no consigue avanzar aunque lo
     * intente). Lo usa la tarea de escape para saber cuándo activarse.
     */
    public boolean isStuck() {
        return stuck;
    }

    // ==================== MÉTODOS PÚBLICOS DE CONTROL ====================

    /**
     * Añade un mensaje a la cola de chat
     */
    public void say(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        // No repetir el mismo mensaje demasiado seguido (esto es lo que evita que
        // tareas que dicen lo mismo cada tick llenen el chat).
        Long last = recentMessages.get(message);
        if (last != null && now - last < SAME_MESSAGE_COOLDOWN) {
            return;
        }
        // Cola acotada: si ya hay varios mensajes pendientes, descartar éste.
        if (chatQueue.size() >= MAX_CHAT_QUEUE) {
            return;
        }
        recentMessages.put(message, now);
        if (recentMessages.size() > 128) {
            recentMessages.clear();
        }
        chatQueue.offer(message);
    }

    /**
     * Envía un mensaje por chat de inmediato, sin pasar por el limitador.
     * Se usa para mensajes pedidos explícitamente por un comando (/bot say).
     */
    public void forceSay(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        sendChatMessage(message);
    }

    /**
     * Envía un mensaje de chat inmediatamente
     */
    public void sendChatMessage(String message) {
        Component text = Component.literal("[BOT] " + this.getName().getString() + ": " + message);
        this.server.getPlayerList().broadcastSystemMessage(text, false);

        // También enviar a otros bots cercanos para cooperación
        broadcastToNearbyBots(message);
    }

    private void broadcastToNearbyBots(String message) {
        AABB searchBox = AABB.ofSize(this.position(), 64, 64, 64);
        List<AIPlayer> nearbyBots = this.level().getEntitiesOfClass(AIPlayer.class, searchBox,
                bot -> bot != this && bot.isAlly(this));

        for (AIPlayer bot : nearbyBots) {
            bot.receiveBotMessage(this, message);
        }
    }

    /**
     * Recibe un mensaje de otro bot
     */
    public void receiveBotMessage(AIPlayer sender, String message) {
        ProtectionManager protection = ProtectionManager.getInstance();

        // Procesar mensajes de otros bots para coordinación
        if (message.startsWith("HELP")) {
            // SOLO ayudar si el objetivo del aliado es un monstruo hostil válido
            if (sender.target != null && protection.isValidAttackTarget(this, sender.target)) {
                this.target = sender.target;
                this.say("On my way, " + sender.getName().getString() + "!");
            } else {
                // El objetivo no es válido, solo seguir al aliado
                this.followTarget = sender;
                this.say("I'm coming, " + sender.getName().getString() + "!");
            }
        } else if (message.startsWith("FOLLOW")) {
            this.followTarget = sender;
            this.say("Following you, " + sender.getName().getString() + "!");
        } else if (message.startsWith("STOP")) {
            this.followTarget = null;
            this.currentTask = null;
        }
    }

    /**
     * Establece un objetivo de movimiento
     */
    public void moveTo(BlockPos pos) {
        this.targetPos = pos;
        this.followTarget = null;
    }

    /**
     * Establece un objetivo de seguimiento
     */
    public void follow(Player player) {
        this.followTarget = player;
        this.targetPos = null;
    }

    /**
     * Ordena al bot fijar una entidad como objetivo de combate.
     * SOLO acepta monstruos hostiles como objetivos válidos.
     * Rechaza jugadores humanos y bots aliados.
     *
     * <p>IMPORTANTE: este método NO inflige daño. Antes se llamaba
     * {@code attack(Entity)}, lo que sobrescribía sin querer
     * {@link Player#attack(Entity)}: los bots reproducían la animación y el sonido
     * de ataque, pero el daño de vanilla nunca se aplicaba porque esta versión de
     * la IA interceptaba la llamada. Para hacer daño real hay que invocar el
     * {@code attack(Entity)} heredado (lo hace {@code FightTask}).
     */
    public void setAttackTarget(Entity entity) {
        ProtectionManager protection = ProtectionManager.getInstance();

        // Validar que el objetivo sea un monstruo hostil válido
        if (protection.isValidAttackTarget(this, entity)) {
            this.target = entity;
        } else {
            // Si el objetivo no es válido, no hacer nada
            this.say("I can't attack that!");
        }
    }

    /**
     * Añade un aliado
     */
    public void addAlly(AIPlayer ally) {
        allies.add(ally.getUUID());
        ally.allies.add(this.getUUID());
    }

    /**
     * Verifica si otro bot es aliado
     */
    public boolean isAlly(AIPlayer other) {
        return allies.contains(other.getUUID()) || other.getUUID().equals(this.getUUID());
    }

    /**
     * Pide ayuda a los aliados
     */
    public void callForHelp() {
        sendChatMessage("HELP! I need assistance at " + blockPosition().toShortString());
    }

    /**
     * Obtiene la posición objetivo
     */
    public BlockPos getTargetPos() {
        if (followTarget != null) {
            return followTarget.blockPosition();
        }
        return targetPos;
    }

    /**
     * Obtiene el objetivo actual (entidad)
     */
    public Entity getTarget() {
        return target;
    }

    /**
     * Establece el objetivo.
     * SOLO acepta monstruos hostiles como objetivos válidos.
     */
    public void setTarget(Entity target) {
        ProtectionManager protection = ProtectionManager.getInstance();

        // Validar que el objetivo sea un monstruo hostil válido
        if (protection.isValidAttackTarget(this, target)) {
            this.target = target;
        }
    }

    /**
     * Obtiene el estado actual
     */
    public AIState getCurrentState() {
        return currentState;
    }

    /**
     * Establece el estado actual
     */
    public void setCurrentState(AIState state) {
        this.currentState = state;
    }

    /**
     * Obtiene la tarea actual
     */
    public AITask getCurrentTask() {
        return currentTask;
    }

    /**
     * Fuerza una tarea específica
     */
    public void forceTask(AITask task) {
        if (currentTask != null) {
            currentTask.cancel();
        }
        currentTask = task;
        currentTask.start();
        currentState = task.getState();
    }

    /**
     * Obtiene el objetivo de seguimiento
     */
    public Player getFollowTarget() {
        return followTarget;
    }

    /**
     * Obtiene todos los aliados
     */
    public Set<UUID> getAllies() {
        return Collections.unmodifiableSet(allies);
    }

    /**
     * Obtiene si está en combate
     */
    public boolean isInCombat() {
        return isInCombat;
    }

    /**
     * Marca si este jugador es un bot (para mostrar en tab list)
     */
    public void setBot(boolean isBot) {
        this.isBot = isBot;
    }

    /**
     * Verifica si es un bot
     */
    public boolean isBot() {
        return isBot;
    }

    /**
     * Obtiene el nombre para mostrar (con indicador de bot si aplica)
     */
    public Component getBotDisplayName() {
        if (isBot) {
            return Component.literal("🤖 " + getName().getString());
        }
        return getDisplayName();
    }

    /**
     * Obtiene el nombre para la tab list (con indicador de bot)
     */
    public Component getBotTabListName() {
        if (isBot) {
            return Component.literal("🤖 " + getName().getString()).withStyle(net.minecraft.ChatFormatting.AQUA);
        }
        return Component.literal(getName().getString());
    }

    /**
     * Obtiene el modo de combate actual
     */
    public CombatMode getCombatMode() {
        return combatMode;
    }

    /**
     * Establece el modo de combate
     */
    public void setCombatMode(CombatMode mode) {
        this.combatMode = mode;
        switch (mode) {
            case PASSIVE -> say("I'm now in passive mode. I won't attack anyone.");
            case DEFENSIVE -> say("I'm now in defensive mode. I'll only defend myself.");
            case AGGRESSIVE -> say("I'm now in aggressive mode. I'll attack monsters!");
        }
    }

    /**
     * Obtiene estadísticas del bot
     */
    public String getBotStats() {
        return String.format("%s | State: %s | Health: %.1f/%.1f | Target: %s | Task: %s",
                getName().getString(),
                currentState.getDisplayName(),
                getHealth(),
                getMaxHealth(),
                target != null ? target.getName().getString() : "none",
                currentTask != null ? currentTask.getName() : "none");
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);

        if (result && source.getEntity() instanceof LivingEntity attacker) {
            ProtectionManager protection = ProtectionManager.getInstance();

            // SOLO contratacar si el atacante es un monstruo hostil válido
            if (protection.isValidAttackTarget(this, attacker)) {
                // Es un monstruo hostil → contratacar
                this.target = attacker;
                this.say("Fighting back!");
            } else if (attacker instanceof Player player) {
                // Es un jugador (humano o bot) → NO contratacar, solo defenderse
                if (player instanceof AIPlayer aiPlayer && isAlly(aiPlayer)) {
                    // Es un aliado → no hacer nada, fue un accidente
                    this.say("Sorry! Was that you, " + player.getName().getString() + "?");
                } else {
                    // Es un jugador humano u otro bot → no atacar, solo huir o ignorar
                    this.say("Hey! Watch it!");
                    // NO establecer como objetivo de ataque
                }
            } else {
                // Otro tipo de entidad (animal, etc.) → ignorar
            }
        }

        return result;
    }

    private boolean isAlly(Player player) {
        if (player instanceof AIPlayer aiPlayer) {
            return isAlly(aiPlayer);
        }
        // Los jugadores humanos no son aliados por defecto, pero se pueden añadir
        return allies.contains(player.getUUID());
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.say("I'll be back!");
        PlayerBOT.LOGGER.info("AI Player {} died: {}", getName().getString(), source.getLocalizedDeathMessage(this).getString());
    }

    /**
     * Detiene todas las acciones del bot
     */
    public void stopAll() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        this.target = null;
        this.targetPos = null;
        this.followTarget = null;
        this.currentState = AIState.IDLE;
    }

    /**
     * Teletransporta el bot a una posición
     */
    public void teleportTo(BlockPos pos) {
        this.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    /**
     * Obtiene un item aleatorio del inventario
     */
    public ItemStack getRandomItem() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < this.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        return items.isEmpty() ? ItemStack.EMPTY : items.get(random.nextInt(items.size()));
    }

    /**
     * Busca en el inventario las mejores piezas de armadura y las equipa en sus
     * ranuras (casco, pechera, pantalones, botas), devolviendo al inventario la
     * armadura que llevaba puesta. Solo cambia una pieza si la nueva protege más.
     */
    public void equipBestArmor() {
        Inventory inv = this.getInventory();
        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        for (EquipmentSlot slot : armorSlots) {
            int currentScore = armorDefense(this.getItemBySlot(slot));
            int bestSlot = -1;
            int bestScore = currentScore;

            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor)) {
                    continue;
                }
                if (armor.getEquipmentSlot() != slot) {
                    continue;
                }
                int score = armor.getDefense();
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }

            if (bestSlot >= 0) {
                ItemStack newArmor = inv.getItem(bestSlot).copy();
                ItemStack old = this.getItemBySlot(slot).copy();
                this.setItemSlot(slot, newArmor);
                inv.setItem(bestSlot, old.isEmpty() ? ItemStack.EMPTY : old);
                PlayerBOT.LOGGER.info("Bot {} equipped armor: {}",
                        this.getName().getString(), newArmor.getHoverName().getString());
            }
        }
    }

    /**
     * Valor defensivo de una pieza de armadura (0 si no es armadura).
     */
    private int armorDefense(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem armor) {
            return armor.getDefense();
        }
        return 0;
    }

    /**
     * Swing with the main hand (attack / mine animation).
     * Vanilla LivingEntity.swing() already broadcasts ClientboundAnimatePacket to
     * every player that can see this entity, so no manual packets are required.
     */
    public void swingMainHand() {
        this.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * Swing with the off hand.
     */
    public void swingOffHand() {
        this.swing(InteractionHand.OFF_HAND);
    }
}
