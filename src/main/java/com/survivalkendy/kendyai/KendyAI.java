package com.survivalkendy.kendyai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class KendyAI implements ModInitializer {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static String WORKER_URL = "";
    private static String API_SECRET = "";

    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 10_000; // 10 seconds
    private static final int MAX_MESSAGE_LENGTH = 300;

    @Override
    public void onInitialize() {
        loadConfig();

        System.out.println("[KendyAI] Loaded!");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                Commands.literal("kendyai")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "message");
                            String player = context.getSource().getTextName();

                            if (WORKER_URL.isBlank() || API_SECRET.isBlank()) {
                                context.getSource().sendSuccess(
                                    () -> Component.literal("§cKendyAI config missing. Check config/kendyai.properties"),
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
                                    () -> Component.literal("§cPlease wait " + seconds + "s before using KendyAI again."),
                                    false
                                );
                                return 0;
                            }

                            COOLDOWNS.put(player, now);

                            context.getSource().sendSuccess(
                                () -> Component.literal("§eKendyAI is thinking..."),
                                false
                            );

                            askAI(player, message, reply -> {
                                context.getSource().getServer().execute(() -> {
                                    context.getSource().sendSuccess(
                                        () -> Component.literal("§aKendyAI: §f" + reply),
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

    private static void loadConfig() {
        File configFile = new File("config/kendyai.properties");

        if (!configFile.exists()) {
            System.out.println("[KendyAI] Missing config/kendyai.properties");
            return;
        }

        Properties props = new Properties();

        try (FileInputStream input = new FileInputStream(configFile)) {
            props.load(input);

            WORKER_URL = props.getProperty("worker_url", "").trim();
            API_SECRET = props.getProperty("api_secret", "").trim();

            System.out.println("[KendyAI] Config loaded.");
        } catch (IOException e) {
            System.out.println("[KendyAI] Failed to load config: " + e.getMessage());
        }
    }

    private static void askAI(String player, String message, ReplyCallback callback) {
        try {
            System.out.println("[KendyAI] Request from player: " + player);
            System.out.println("[KendyAI] Message: " + message);
            System.out.println("[KendyAI] Sending request to Worker...");

            JsonObject json = new JsonObject();
            json.addProperty("player", player);
            json.addProperty("message", message);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WORKER_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_SECRET)
                .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                .build();

            HTTP_CLIENT
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    System.out.println("[KendyAI] Worker status: " + response.statusCode());
                    return response.body();
                })
                .thenAccept(body -> {
                    System.out.println("[KendyAI] Raw response: " + body);

                    try {
                        JsonObject response = GSON.fromJson(body, JsonObject.class);

                        String reply;

                        if (response.has("reply")) {
                            reply = response.get("reply").getAsString();
                        } else if (response.has("error")) {
                            reply = "Worker error: " + response.get("error").getAsString();
                        } else {
                            reply = "Unexpected response from KendyAI.";
                        }

                        callback.reply(reply);
                    } catch (Exception e) {
                        callback.reply("Invalid response from KendyAI.");
                    }
                })
                .exceptionally(error -> {
                    System.out.println("[KendyAI] Request failed: " + error.getMessage());
                    callback.reply("KendyAI is offline right now.");
                    return null;
                });

        } catch (Exception e) {
            System.out.println("[KendyAI] Error: " + e.getMessage());
            callback.reply("KendyAI had an error.");
        }
    }

    interface ReplyCallback {
        void reply(String reply);
    }
}