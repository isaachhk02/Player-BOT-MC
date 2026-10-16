# Player BOT - AI Controlled Players for Minecraft

Un mod de Fabric para Minecraft 1.21.1 que permite crear jugadores controlados por IA con capacidades avanzadas:

- 🚶 **Movimiento inteligente** con pathfinding
- ⚔️ **Combate** contra mobs hostiles
- 🛡️ **Defensa** con escudos y esquives
- 🏗️ **Construcción** de estructuras (casas, torres, puentes, etc.)
- 🔨 **Crafteo** automático de herramientas y items
- ⛏️ **Minería** y recolección de recursos
- 💬 **Chat** con jugadores humanos y otros bots
- 🤝 **Cooperación** entre bots (grupos, ayuda mutua)
- 👥 **Seguimiento** de jugadores
- 🐄 **Caza** de animales (vacas, cerdos, ovejas, gallinas, conejos) cuando tiene hambre
- 🎒 **Empiezan con inventario vacío**: talan madera, minan piedra, craftean herramientas y se equipan solos

---

<!-- ENGLISH SECTION -->

## English

Player BOT - AI Controlled Players for Minecraft

A Fabric mod for Minecraft 1.21.1 that lets you create AI-controlled players with advanced capabilities:

- 🚶 Intelligent movement with pathfinding
- ⚔️ Combat against hostile mobs
- 🛡️ Defense with shields and dodging
- 🏗️ Building structures (houses, towers, bridges, etc.)
- 🔨 Automatic crafting of tools and items
- ⛏️ Mining and resource gathering
- 💬 Chat with human players and other bots
- 🤝 Cooperation between bots (groups, mutual assistance)
- 👥 Player tracking
- 🐄 Hunting animals (cows, pigs, sheep, chickens, rabbits) when hungry
- 🎒 Start with empty inventory: they chop wood, mine stone, craft tools and equip themselves

### Installation

1. Download the `.jar` file from `build/libs/`
2. Place it in your server or client `mods/` folder
3. Make sure you have [Fabric Loader](https://fabricmc.net/) and [Fabric API](https://modrinth.com/mod/fabric-api) installed

### Requirements

- Minecraft 1.21.1
- Fabric Loader >= 0.16.0
- Fabric API (recommended)
- Java 21+

### Commands

All commands require operator permissions (level 2+).

Creating bots

```bash
# Create a bot near you
/bot create <name>

# Create a bot at a specific position
/bot createat <name> <x> <y> <z>

# Create a bot with a specific game mode
/bot create <name> [survival|creative|adventure|spectator]
/bot createat <name> <x> <y> <z> [survival|creative|adventure|spectator]
```

Managing bots

```bash
# List all active bots
/bot list

# Show stats for all bots
/bot stats

# Show stats for a specific bot
/bot stats <name>

# Remove a bot
/bot remove <name>

# Remove all bots
/bot removeall
```

Controlling bots

```bash
# Make a bot speak
/bot say <name> <message>

# Make a bot follow a player
/bot follow <name> <player>

# Stop a bot
/bot stop <name>

# Stop all bots
/bot stopall

# Move a bot to a position
/bot goto <name> <x> <y> <z>

# Teleport a bot
/bot tp <name> <x> <y> <z>

# Make a bot come to you
/bot come <name>
```

Combat and building

```bash
# Make a bot attack an entity
/bot attack <name> <entity>

# Make a bot build a structure
/bot build <name> <type>
```

Available build types:
- `shelter` - Basic 3x3 shelter
- `tower` - Watch tower
- `wall` - Wall
- `house` - Simple 5x5 house
- `bridge` - Bridge
- `farm` - Basic farm

Cooperation

```bash
# Create a group of allied bots
/bot group <name1> <name2>
```

Area protection

Bots respect protected areas and will **NEVER** destroy other players' builds:

```bash
# Protect an area (bots will not build or mine here)
/bot protect <x1> <y1> <z1> <x2> <y2> <z2>

# Remove protection from an area
/bot unprotect <x1> <y1> <z1> <x2> <y2> <z2>

# Check if a position is protected
/bot check <x> <y> <z>
```

Combat modes

Bots have 3 combat modes to control aggression:

```bash
# PASSIVE mode: never attacks, only flees when attacked
/bot mode <name> passive

# DEFENSIVE mode (default): only defends, doesn't initiate combat
/bot mode <name> defensive

# AGGRESSIVE mode: automatically attacks nearby monsters
/bot mode <name> aggressive
```

Important safety rules:

- ✅ Bots ONLY attack hostile monsters (zombies, skeletons, creepers, etc.)
- ❌ Bots NEVER attack human players
- ❌ Bots NEVER attack allied bots
- 🛡️ Bots DO NOT destroy other players' builds
- 🏠 Bots DO NOT mine in protected areas (spawn, near players, claims)
- 🐄 Bots hunt farm animals ONLY when hungry (never for sport)
- 📋 Bots respect property signs ("private", "house", "keep out", etc.)

### AI Capabilities

States, priorities and behavior are the same as described in the Spanish section below. They include states like IDLE, MOVING, GATHERING, MINING, CRAFTING, BUILDING, FIGHTING, DEFENDING, FLEEING, FOLLOWING, EXPLORING, HEALING, HUNTING and CHATTING.

Task priorities example (higher value = higher priority):
1. FLEEING (100)
2. FIGHTING/DEFENDING (90)
3. HEALING (80)
4. HUNTING (75)
5. GATHERING/MINING (70/65)
6. BUILDING (60)
7. CRAFTING (50)
8. FOLLOWING (40)
9. MOVING/EXPLORING (30/25)
10. CHATTING (10)
11. IDLE (0)

### Configuration

The configuration file is `config/player-bot.json`.

Sample options (see Spanish section for full example):

```json
{
  "enableBots": true,
  "maxBotsPerPlayer": 5,
  "maxTotalBots": 20,
  "botsCanChat": true,
  "chatCooldownMs": 2000,
  "botSightRange": 32.0,
  "botsAutoDefend": true,
  "fleeHealthThreshold": 0.3,
  "botsCallForHelp": true,
  "botsAttackOnlyMonsters": true,
  "botsAttackPlayers": false,
  "botsAttackAlliedBots": false,
  "defaultCombatMode": "defensive",
  "enableSpawnProtection": true,
  "spawnProtectionRadius": 32,
  "enablePlayerAreaProtection": true,
  "playerProtectionRadius": 16,
  "respectBuildProtection": true,
  "respectSignProtection": true,
  "botsAutoGather": true,
  "botsAutoCraft": true,
  "botsAutoBuild": false
}
```

Security options like `botsAttackOnlyMonsters`, `botsAttackPlayers` and `respectBuildProtection` ensure bots behave safely on multiplayer servers.

### Client hotkeys

- **B** - Quickly create a bot
- **N** - List active bots

### Usage examples

Create a team of bots

```bash
# Create 3 bots
/bot create Warrior
/bot create Builder
/bot create Miner

# Group them as allies
/bot group Warrior Builder
/bot group Warrior Miner

# Make them follow you
/bot follow Warrior @p
/bot follow Builder @p
/bot follow Miner @p
```

Build a base

```bash
# Create a builder bot
/bot create Architect

# Give it materials (place items in its inventory or let it gather)

# Make it build a house
/bot build Architect house
```

Automatic defense: bots automatically defend themselves, nearby allies that ask for help, and players in their group.

### Development

Project structure and build instructions are provided in the Spanish section below. Main commands to compile:

```bash
# Windows
gradlew.bat build

# Linux/Mac
./gradlew build
```

The resulting `.jar` will be in `build/libs/`.

### License

MIT License - see LICENSE file for details.

### Credits

- Built with [Fabric](https://fabricmc.net/)
- Inspired by mods like Baritone and other Minecraft bots

---

<!-- ORIGINAL SPANISH SECTION -->

# Player BOT - AI Controlled Players for Minecraft

Un mod de Fabric para Minecraft 1.21.1 que permite crear jugadores controlados por IA con capacidades avanzadas:

- 🚶 **Movimiento inteligente** con pathfinding
- ⚔️ **Combate** contra mobs hostiles
- 🛡️ **Defensa** con escudos y esquives
- 🏗️ **Construcción** de estructuras (casas, torres, puentes, etc.)
- 🔨 **Crafteo** automático de herramientas y items
- ⛏️ **Minería** y recolección de recursos
- 💬 **Chat** con jugadores humanos y otros bots
- 🤝 **Cooperación** entre bots (grupos, ayuda mutua)
- 👥 **Seguimiento** de jugadores
- 🐄 **Caza** de animales (vacas, cerdos, ovejas, gallinas, conejos) cuando tiene hambre
- 🎒 **Empiezan con inventario vacío**: talan madera, minan piedra, craftean herramientas y se equipan solos

## Instalación

1. Descarga el archivo `.jar` de la carpeta `build/libs/`
2. Colócalo en la carpeta `mods/` de tu servidor o cliente de Minecraft
3. Asegúrate de tener instalado [Fabric Loader](https://fabricmc.net/) y [Fabric API](https://modrinth.com/mod/fabric-api)

## Requisitos

- Minecraft 1.21.1
- Fabric Loader >= 0.16.0
- Fabric API (recomendado)
- Java 21+

## Comandos

Todos los comandos requieren permisos de operador (nivel 2+).

### Crear Bots

```bash
# Crear un bot cerca de ti
/bot create <nombre>

# Crear un bot en una posición específica
/bot createat <nombre> <x> <y> <z>

# Crear un bot con un modo de juego específico
/bot create <nombre> [survival|creative|adventure|spectator]
/bot createat <nombre> <x> <y> <z> [survival|creative|adventure|spectator]
```

### Gestionar Bots

```bash
# Listar todos los bots activos
/bot list

# Ver estadísticas de todos los bots
/bot stats

# Ver estadísticas de un bot específico
/bot stats <nombre>

# Eliminar un bot
/bot remove <nombre>

# Eliminar todos los bots
/bot removeall
```

### Controlar Bots

```bash
# Hacer que un bot hable
/bot say <nombre> <mensaje>

# Hacer que un bot siga a un jugador
/bot follow <nombre> <jugador>

# Detener un bot
/bot stop <nombre>

# Detener todos los bots
/bot stopall

# Mover un bot a una posición
/bot goto <nombre> <x> <y> <z>

# Teletransportar un bot
/bot tp <nombre> <x> <y> <z>

# Hacer que un bot venga hacia ti
/bot come <nombre>
```

### Combate y Construcción

```bash
# Hacer que un bot ataque a una entidad
/bot attack <nombre> <entidad>

# Hacer que un bot construya una estructura
/bot build <nombre> <tipo>
```

**Tipos de construcción disponibles:**
- `shelter` - Refugio básico 3x3
- `tower` - Torre de vigilancia
- `wall` - Muralla
- `house` - Casa simple 5x5
- `bridge` - Puente
- `farm` - Granja básica

### Cooperación

```bash
# Crear un grupo de bots aliados
/bot group <nombre1> <nombre2>
```

### Protección de Áreas

Los bots respetan áreas protegidas y **NUNCA** destruyen las construcciones de otros jugadores:

```bash
# Proteger un área (los bots no construirán ni minarán aquí)
/bot protect <x1> <y1> <z1> <x2> <y2> <z2>

# Quitar protección de un área
/bot unprotect <x1> <y1> <z1> <x2> <y2> <z2>

# Verificar si una posición está protegida
/bot check <x> <y> <z>
```

### Modos de Combate

Los bots tienen 3 modos de combate para controlar su agresividad:

```bash
# Modo PASIVO: Nunca ataca, solo huye cuando lo atacan
/bot mode <nombre> passive

# Modo DEFENSIVO (por defecto): Solo se defiende, no inicia combate
/bot mode <nombre> defensive

# Modo AGRESIVO: Ataca monstruos cercanos automáticamente
/bot mode <nombre> aggressive
```

**IMPORTANTE - Reglas de Seguridad:**

- ✅ Los bots **SOLO atacan monstruos hostiles** (zombies, esqueletos, creepers, etc.)
- ❌ Los bots **NUNCA atacan jugadores humanos**
- ❌ Los bots **NUNCA atacan bots aliados**
- 🛡️ Los bots **NO destruyen construcciones de otros jugadores**
- 🏠 Los bots **NO minan en áreas protegidas** (spawn, cerca de jugadores, claims)
- 🐄 Los bots **cazan animales de granja SOLO cuando tienen hambre** (nunca por deporte)
- 📋 Los bots **respetan letreros de propiedad** ("private", "casa", "keep out", etc.)

## Capacidades de la IA

### Estados del Bot

| Estado | Descripción |
|--------|-------------|
| `IDLE` | Inactivo, esperando órdenes |
| `MOVING` | Moviéndose hacia un objetivo |
| `GATHERING` | Recolectando recursos |
| `MINING` | Minando bloques |
| `CRAFTING` | Crafteando items |
| `BUILDING` | Construyendo estructuras |
| `FIGHTING` | Combatiendo enemigos |
| `DEFENDING` | Defendiéndose de ataques |
| `FLEEING` | Huyando por baja salud |
| `FOLLOWING` | Siguiendo a un jugador |
| `EXPLORING` | Explorando el área |
| `HEALING` | Curándose comiendo |
| `HUNTING` | Cazando animales para comer |
| `CHATTING` | Comunicándose |

### Prioridades de Tareas

Las tareas se ejecutan según su prioridad:

1. **FLEEING** (100) - Huir cuando la salud es baja
2. **FIGHTING/DEFENDING** (90) - Combatir y defenderse
3. **HEALING** (80) - Curarse comiendo
4. **HUNTING** (75) - Cazar animales cuando tiene hambre
5. **GATHERING/MINING** (70/65) - Recolectar recursos
6. **BUILDING** (60) - Construir
7. **CRAFTING** (50) - Craftear
8. **FOLLOWING** (40) - Seguir jugadores
9. **MOVING/EXPLORING** (30/25) - Moverse y explorar
10. **CHATTING** (10) - Chat casual
11. **IDLE** (0) - Inactividad

## Configuración

El archivo de configuración se encuentra en `config/player-bot.json`:

```json
{
  "enableBots": true,
  "maxBotsPerPlayer": 5,
  "maxTotalBots": 20,
  "botsCanChat": true,
  "chatCooldownMs": 2000,
  "botSightRange": 32.0,
  "botsAutoDefend": true,
  "fleeHealthThreshold": 0.3,
  "botsCallForHelp": true,
  "botsAttackOnlyMonsters": true,
  "botsAttackPlayers": false,
  "botsAttackAlliedBots": false,
  "defaultCombatMode": "defensive",
  "enableSpawnProtection": true,
  "spawnProtectionRadius": 32,
  "enablePlayerAreaProtection": true,
  "playerProtectionRadius": 16,
  "respectBuildProtection": true,
  "respectSignProtection": true,
  "botsAutoGather": true,
  "botsAutoCraft": true,
  "botsAutoBuild": false
}
```

### Opciones de Seguridad Importantes

| Opción | Descripción | Valor por defecto |
|--------|-------------|-------------------|
| `botsAttackOnlyMonsters` | Solo atacar monstruos hostiles | `true` (siempre) |
| `botsAttackPlayers` | Permitir atacar jugadores humanos | `false` (nunca) |
| `botsAttackAlliedBots` | Permitir atacar bots aliados | `false` (nunca) |
| `defaultCombatMode` | Modo de combate por defecto | `"defensive"` |
| `enableSpawnProtection` | Proteger área del spawn | `true` |
| `spawnProtectionRadius` | Radio de protección del spawn | `32` bloques |
| `enablePlayerAreaProtection` | Proteger áreas cerca de jugadores | `true` |
| `playerProtectionRadius` | Radio de protección personal | `16` bloques |
| `respectBuildProtection` | No destruir construcciones | `true` |
| `respectSignProtection` | Respetar letreros de propiedad | `true` |

## Atajos de Teclado (Cliente)

- **B** - Crear un bot rápidamente
- **N** - Listar bots activos

## Ejemplos de Uso

### Crear un equipo de bots

```bash
# Crear 3 bots
/bot create Warrior
/bot create Builder
/bot create Miner

# Agruparlos como aliados
/bot group Warrior Builder
/bot group Warrior Miner

# Hacer que te sigan
/bot follow Warrior @p
/bot follow Builder @p
/bot follow Miner @p
```

### Construir una base

```bash
# Crear un bot constructor
/bot create Architect

# Darle materiales (ponlos en su inventario o déjalo recolectar)

# Hacer que construya una casa
/bot build Architect house
```

### Defensa automática

Los bots defienden automáticamente a:
- Sí mismos cuando son atacados
- Aliados cercanos cuando piden ayuda
- Jugadores en su grupo

## Desarrollo

### Estructura del Proyecto

```
src/main/java/org/isaachhk02/playerbot/
├── PlayerBOT.java              # Clase principal del mod
├── ai/
│   ├── AIPlayer.java           # Jugador IA principal
│   ├── AIPlayerManager.java    # Gestor de bots
│   ├── AIState.java            # Estados de la IA
│   ├── AITask.java             # Clase base para tareas
│   └── tasks/                  # Tareas específicas
│       ├── MoveTask.java       # Movimiento
│       ├── FightTask.java      # Combate
│       ├── DefendTask.java     # Defensa
│       ├── FleeTask.java       # Huida
│       ├── EscapeTask.java     # Escape de agujeros y cuevas
│       ├── HealTask.java       # Curación
│       ├── HuntTask.java       # Caza de animales con hambre
│       ├── GatherTask.java     # Recolección
│       ├── MineTask.java       # Minería
│       ├── CraftTask.java      # Crafteo
│       ├── BuildTask.java      # Construcción
│       ├── FollowTask.java     # Seguimiento
│       ├── ExploreTask.java    # Exploración
│       ├── ChatTask.java       # Chat
│       └── IdleTask.java       # Inactividad
├── commands/
│   └── AIBotCommands.java      # Comandos /bot
├── config/
│   └── AIBotConfig.java        # Configuración
├── client/
│   └── PlayerBOTClient.java    # Inicializador cliente
└── mixin/
    └── ServerPlayerMixin.java  # Mixin para ServerPlayer
```

### Compilar

```bash
# Windows
gradlew.bat build

# Linux/Mac
./gradlew build
```

El archivo `.jar` resultante estará en `build/libs/`.

## Licencia

MIT License - Ver archivo LICENSE para más detalles.

## Créditos

- Desarrollado con [Fabric](https://fabricmc.net/)
- Inspirado en mods como Baritone y otros bots de Minecraft
