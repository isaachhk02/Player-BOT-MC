# REQUERIMENTS — Requisitos del proyecto

Todo lo necesario para compilar, ejecutar y probar el mod **Player BOT** (`player-bot-1.0.0.jar`).

> Nota: el nombre del archivo mantiene la grafía pedida (`REQUERIMENTS.md`).

## 1. Requisitos para compilar

| Requisito | Versión exacta | Notas |
|-----------|----------------|-------|
| JDK | **Java 21** (probado con `jdk-21.0.12.1+1`) | `release = 21`, `source/target = 21` en `build.gradle` |
| Gradle | **Wrapper incluido** (`gradlew` / `gradlew.bat`) | No instalar Gradle aparte |
| Fabric Loom | `1.7-SNAPSHOT` | Definido en `gradle.properties` (`loom_version`) |
| Mappings | **Mojang oficiales** (`loom.officialMojangMappings()`) | Se descargan solos |
| Minecraft (dependencia) | `1.21.1` | `minecraft_version` en `gradle.properties` |
| Fabric Loader (dependencia) | `0.15.11` | `loader_version` en `gradle.properties` |
| Fabric API (dependencia) | `0.102.0+1.21.1` | `fabric_api_version` en `gradle.properties` |
| RAM para compilar | **2 GB** mínimo para Gradle | `org.gradle.jvmargs=-Xmx2G`; la compilación usa fork con `-Xmx2G` |
| Conexión a internet | Obligatoria (primer build) | Descarga Minecraft, mappings y dependencias de Maven (Mojang + Fabric) |
| SO | Windows / Linux / macOS | Probado en Windows (`win32`, PowerShell) |

### Compilar

```bash
# Windows (PowerShell) — con JAVA_HOME apuntando a un JDK 21
$env:JAVA_HOME = "C:\Java\jdk-21.0.12.1+1"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat build --console=plain --no-configuration-cache --no-daemon

# Linux / macOS
./gradlew build
```

El `.jar` resultante queda en `build/libs/`.

### Limpiar artefactos

```bash
.\gradlew.bat clean --console=plain --no-configuration-cache --no-daemon
```

(`build/` está en `.gitignore` y no se sube al repo.)

## 2. Requisitos para ejecutar el mod

| Requisito | Versión |
|-----------|---------|
| Minecraft | **1.21.1** (cliente o servidor dedicado) |
| Fabric Loader | **0.15.11** |
| Fabric API | **0.102.0+1.21.1** |
| Java (runtime) | **21+** |

Instalación: copiar el `.jar` de `build/libs/` a la carpeta `mods/` del servidor o cliente.

## 3. Requisitos del servidor de pruebas

Para probar los bots (`/bot create`, `/bot createat`, etc.):

| Requisito | Detalle |
|-----------|---------|
| Permisos | Comandos `/bot` requieren **nivel de operador 2+** |
| RCON (opcional, recomendado) | `enable-rcon=true`, puerto `25575` — permite controlar el servidor sin cliente |
| RAM del servidor | ` -Xmx4G -Xms2G` recomendado con varios bots activos |
| `spawn-protection` | Los bots **no pueden picar/construir** dentro del radio de protección del spawn (`16` por defecto) — alejar las pruebas de minería del spawn |
| `doMobSpawning` | Si está en `false`, no aparecen animales solos: invocar con `/summon` para probar la caza |
| Efecto `hunger` | Útil para pruebas de caza: `effect give @e[name=<bot>,limit=1] minecraft:hunger <seg> 150` baja la comida rápido (`/data modify` de `foodLevel` está bloqueado en jugadores) |

### Arrancar el servidor (ejemplo Windows)

```powershell
$env:JAVA_HOME = "C:\Java\jdk-21.0.12.1+1"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
java.exe -Xmx4G -Xms2G -jar fabric-server-launch.jar nogui
```

## 4. Resumen rápido

- **Compilar:** JDK 21 + wrapper de Gradle + internet (primera vez).
- **Jugar:** MC 1.21.1 + Loader 0.15.11 + Fabric API 0.102.0+1.21.1 + Java 21.
- **Probar bots:** operador nivel 2+, RCON opcional, ojo con `spawn-protection` y `doMobSpawning`.
