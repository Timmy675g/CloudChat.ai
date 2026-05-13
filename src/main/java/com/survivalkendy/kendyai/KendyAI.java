package com.survivalkendy.kendyai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;

import java.nio.file.Files;
import java.nio.file.Path;

public class KendyAI implements ModInitializer {
    private static final Gson GSON = new Gson(); // JSON parser for API request or response handling
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient(); // Shared HTTP client used for communicating with the AI Worker backend

    private static String AI_NAME = "CloudChat"; // Configurable branding and command settings
    private static String COMMAND_NAME = "cloudchat"; // AI backend configuration loaded from config or cloudchat or cloudchat.properties

    // Server awareness and guardrail settings
    private static boolean SAFE_MODE = true;

    private static boolean ALLOW_ONLINE_PLAYER_COUNT = true;
    private static boolean ALLOW_PLAYER_COORDINATES = false;
    private static boolean ALLOW_DIMENSION = true;
    private static boolean ALLOW_WORLD_TIME = true;

    private static String WORKER_URL = "";
    private static String API_SECRET = "";
    private static String SYSTEM_PROMPT = "You are CloudChat, a helpful Minecraft server assistant. Never invent server data, coordinates, player counts, structures, inventories, commands, or admin information. Only use data provided by the server context. If data is not provided, clearly say you do not have access to it.";
    private static long COOLDOWN_MS = 10_000;
    private static int MAX_MESSAGE_LENGTH = 300;

    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>(); // Simple per player cooldown tracking

    // Main mod initialization entry point
    @Override
    public void onInitialize() {
        loadConfig();

        System.out.println("[" + AI_NAME + "] Loaded!");

        // Register the configurable AI command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                Commands.literal(COMMAND_NAME)
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "message");
                            String player = context.getSource().getTextName();

                            if (WORKER_URL.isBlank() || API_SECRET.isBlank()) {
                                context.getSource().sendSuccess(
                                    () -> Component.literal("§c" + AI_NAME + " config missing. Check config/cloudchat/cloudchat.properties"),
                                    false
                                );
                                return 0;
                            }

                            if (message.length() > MAX_MESSAGE_LENGTH) {
                                context.getSource().sendSuccess(
                                    () -> Component.literal("§cYour message is too long. Max " + MAX_MESSAGE_LENGTH + " characters."),
                                    false
                                );
                                return 0;
                            }

                            long now = System.currentTimeMillis();
                            long lastUsed = COOLDOWNS.getOrDefault(player, 0L);
                            long remaining = COOLDOWN_MS - (now - lastUsed);

                            if (remaining > 0) {
                                long seconds = (remaining + 999) / 1000;

                                context.getSource().sendSuccess(
                                    () -> Component.literal("§cPlease wait " + seconds + "s before using " + AI_NAME + " again."),
                                    false
                                );
                                return 0;
                            }

                            COOLDOWNS.put(player, now);

                            int onlinePlayers = context.getSource().getServer().getPlayerCount();
                            int maxPlayers = context.getSource().getServer().getMaxPlayers();

                            String dimension = context.getSource()
                                    .getLevel()
                                    .dimension()
                                    .location()
                                    .toString();

                            long worldTime = context.getSource()
                                    .getLevel()
                                    .getDayTime();

                            context.getSource().sendSuccess(
                                () -> Component.literal("§e" + AI_NAME + " is thinking..."),
                                false
                            );

                            askAI(
                                player,
                                message,
                                onlinePlayers,
                                maxPlayers,
                                dimension,
                                worldTime,
                                reply -> {
                                context.getSource().getServer().execute(() -> {
                                    context.getSource().sendSuccess(
                                        () -> Component.literal("§b" + AI_NAME + ": §f" + reply),
                                        false
                                    );
                                });
                            });

                            return 1;
                        })
                    )
            );
        });
    }

// Loads configuration and auto generates defaults if missing
private static void loadConfig() {
    try {
        Path configDir = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("cloudchat");

        Path configFile = configDir.resolve("cloudchat.properties");

        // Create folder if missing
        Files.createDirectories(configDir);

        Properties props = new Properties();

        // Generate default config if missing
        if (!Files.exists(configFile)) {

            props.setProperty("ai_name", "CloudChat");
            props.setProperty("command_name", "cloudchat");

            props.setProperty("worker_url", "https://your-worker.workers.dev");
            props.setProperty("api_secret", "replace_me");

            props.setProperty(
                "system_prompt",
                "You are a friendly Minecraft server assistant."
            );

            props.setProperty("cooldown_seconds", "10");
            props.setProperty("max_message_length", "300");

            props.setProperty("safe_mode", "true");

            props.setProperty("allow_online_player_count", "true");
            props.setProperty("allow_player_coordinates", "false");
            props.setProperty("allow_dimension", "true");
            props.setProperty("allow_world_time", "true");

            try (OutputStream output = Files.newOutputStream(configFile)) {
                props.store(output, "CloudChat Configuration");
            }

            System.out.println("[CloudChat] Generated default config.");
        }

        // Load config
        try (InputStream input = Files.newInputStream(configFile)) {
            props.load(input);
        }

        AI_NAME = props.getProperty("ai_name", "CloudChat").trim();
        COMMAND_NAME = props.getProperty("command_name", "cloudchat").trim();

        WORKER_URL = props.getProperty("worker_url", "").trim();
        API_SECRET = props.getProperty("api_secret", "").trim();

        SYSTEM_PROMPT = props.getProperty(
                "system_prompt",
                SYSTEM_PROMPT
        ).trim();

        COOLDOWN_MS = Long.parseLong(
                props.getProperty("cooldown_seconds", "10").trim()
        ) * 1000L;

        MAX_MESSAGE_LENGTH = Integer.parseInt(
                props.getProperty("max_message_length", "300").trim()
        );

        SAFE_MODE = Boolean.parseBoolean(
        props.getProperty("safe_mode", "true").trim()
        );

        ALLOW_ONLINE_PLAYER_COUNT = Boolean.parseBoolean(
                props.getProperty("allow_online_player_count", "true").trim()
        );

        ALLOW_PLAYER_COORDINATES = Boolean.parseBoolean(
                props.getProperty("allow_player_coordinates", "false").trim()
        );

        ALLOW_DIMENSION = Boolean.parseBoolean(
                props.getProperty("allow_dimension", "true").trim()
        );

        ALLOW_WORLD_TIME = Boolean.parseBoolean(
                props.getProperty("allow_world_time", "true").trim()
        );

        System.out.println("[" + AI_NAME + "] Config loaded.");

    } catch (Exception e) {
        System.out.println("[" + AI_NAME + "] Failed to load config: " + e.getMessage());
    }
}
    // Sends player messages to the AI Worker asynchronously
    private static void askAI(
        String player,
        String message,
        int onlinePlayers,
        int maxPlayers,
        String dimension,
        long worldTime,
        ReplyCallback callback
    ) {
        try {
            System.out.println("[" + AI_NAME + "] Request from player: " + player);
            System.out.println("[" + AI_NAME + "] Message: " + message);
            System.out.println("[" + AI_NAME + "] Sending request to Worker...");

            // Build JSON payload for Worker request
            JsonObject json = new JsonObject();
            json.addProperty("player", player);
            json.addProperty("message", message);
            json.addProperty("system_prompt", SYSTEM_PROMPT);

            json.addProperty("safe_mode", SAFE_MODE);

            json.addProperty("can_access_online_player_count", ALLOW_ONLINE_PLAYER_COUNT);
            json.addProperty("can_access_player_coordinates", ALLOW_PLAYER_COORDINATES);
            json.addProperty("can_access_dimension", ALLOW_DIMENSION);
            json.addProperty("can_access_world_time", ALLOW_WORLD_TIME);

            if (ALLOW_ONLINE_PLAYER_COUNT) {
                json.addProperty("online_players", onlinePlayers);
                json.addProperty("max_players", maxPlayers);
            }

            if (ALLOW_DIMENSION) {
                json.addProperty("dimension", dimension);
            }

            if (ALLOW_WORLD_TIME) {
                json.addProperty("world_time", worldTime);
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WORKER_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_SECRET)
                .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                .build();

            HTTP_CLIENT
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    System.out.println("[" + AI_NAME + "] Worker status: " + response.statusCode());
                    return response.body();
                })
                .thenAccept(body -> {
                    System.out.println("[" + AI_NAME + "] Raw response: " + body);

                    try {
                        JsonObject response = GSON.fromJson(body, JsonObject.class);

                        String reply;

                        if (response.has("reply")) {
                            reply = response.get("reply").getAsString();
                        } else if (response.has("error")) {
                            reply = "Worker error: " + response.get("error").getAsString();
                        } else {
                            reply = "Unexpected response from " + AI_NAME + ".";
                        }

                        callback.reply(reply);
                    } catch (Exception e) {
                        callback.reply("Invalid response from " + AI_NAME + ".");
                    }
                })
                .exceptionally(error -> {
                    System.out.println("[" + AI_NAME + "] Request failed: " + error.getMessage());
                    callback.reply(AI_NAME + " is offline right now.");
                    return null;
                });

        } catch (Exception e) {
            System.out.println("[" + AI_NAME + "] Error: " + e.getMessage());
            callback.reply(AI_NAME + " had an error.");
        }
    }

    // Simple callback interface used for async AI responses
    interface ReplyCallback {
        void reply(String reply);
    }
}