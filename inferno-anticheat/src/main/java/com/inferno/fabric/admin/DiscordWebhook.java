package com.inferno.fabric.admin;

import com.inferno.fabric.InfernoConfig;
import com.inferno.fabric.InfernoMod;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DiscordWebhook {
   private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
   private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Inferno-Discord");
      t.setDaemon(true);
      return t;
   });

   private DiscordWebhook() {
   }

   public static void send(String content) {
      String url = InfernoConfig.get().discordWebhook;
      if (url != null && !url.isBlank()) {
         String safe = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
         if (safe.length() > 1800) {
            safe = safe.substring(0, 1800) + "...";
         }

         String body = "{\"content\":\"" + safe + "\"}";
         WORKER.execute(
            () -> {
               try {
                  HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                     .timeout(Duration.ofSeconds(8L))
                     .header("Content-Type", "application/json")
                     .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                     .build();
                  CLIENT.send(req, BodyHandlers.discarding());
               } catch (Exception e) {
                  InfernoMod.LOGGER.warn("Discord webhook failed: {}", e.getMessage());
               }
            }
         );
      }
   }
}
