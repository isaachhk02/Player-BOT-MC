package org.isaachhk02.playerbot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.isaachhk02.playerbot.PlayerBOT;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuración del mod Player BOT.
 * Permite ajustar el comportamiento de los bots IA.
 */
public class AIBotConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("player-bot.json");

    // Configuración general
    public boolean enableBots = true;
    public int maxBotsPerPlayer = 5;
    public int maxTotalBots = 20;

    // Configuración de IA
    public boolean botsCanChat = true;
    public int chatCooldownMs = 2000;
    public double botSightRange = 32.0;
    public double botFollowRange = 3.0;

    // Configuración de combate
    public boolean botsAutoDefend = true;
    public float fleeHealthThreshold = 0.3f;
    public boolean botsCallForHelp = true;
    public boolean botsAttackOnlyMonsters = true; // SIEMPRE true - seguridad
    public boolean botsAttackPlayers = false;     // NUNCA atacar jugadores humanos
    public boolean botsAttackAlliedBots = false;  // NUNCA atacar bots aliados
    public String defaultCombatMode = "defensive"; // passive, defensive, aggressive

    // Configuración de protección
    public boolean enableSpawnProtection = true;
    public int spawnProtectionRadius = 32;
    public boolean enablePlayerAreaProtection = true;
    public int playerProtectionRadius = 16;
    public boolean respectBuildProtection = true; // No destruir construcciones de jugadores
    public boolean respectSignProtection = true;  // Respetar letreros de propiedad

    // Configuración de recolección
    public boolean botsAutoGather = true;
    public boolean botsAutoCraft = true;
    public boolean botsAutoBuild = false; // Desactivado por defecto, requiere comando explícito

    // Configuración de cooperación
    public boolean botsCanFormGroups = true;
    public int maxGroupSize = 5;

    // Configuración de rendimiento
    public int botTickRate = 1; // Cada cuántos ticks se actualiza la IA (1 = cada tick)
    public boolean enableBotPathfindingCache = true;

    private static AIBotConfig instance;

    public static AIBotConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /**
     * Carga la configuración desde el archivo
     */
    public static AIBotConfig load() {
        AIBotConfig config = new AIBotConfig();

        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                config = GSON.fromJson(reader, AIBotConfig.class);
                PlayerBOT.LOGGER.info("Loaded config from {}", CONFIG_PATH);
            } catch (Exception e) {
                PlayerBOT.LOGGER.error("Failed to load config, using defaults", e);
            }
        } else {
            config.save();
            PlayerBOT.LOGGER.info("Created default config at {}", CONFIG_PATH);
        }

        return config;
    }

    /**
     * Guarda la configuración actual
     */
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            PlayerBOT.LOGGER.error("Failed to save config", e);
        }
    }

    /**
     * Recarga la configuración desde el archivo
     */
    public static void reload() {
        instance = load();
    }
}
