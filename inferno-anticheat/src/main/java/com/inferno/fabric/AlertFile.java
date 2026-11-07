package com.inferno.fabric;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;

final class AlertFile {
   private static final Path FILE = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("inferno-alerts.log");
   private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Inferno-AlertFile");
      t.setDaemon(true);
      return t;
   });

   private AlertFile() {
   }

   static void write(String line) {
      String stamped = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " | " + line + System.lineSeparator();
      WORKER.execute(() -> {
         try {
            Files.createDirectories(FILE.getParent());

            try (BufferedWriter w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
               w.write(stamped);
            }
         } catch (IOException e) {
            InfernoMod.LOGGER.warn("Could not write {}: {}", FILE, e.getMessage());
         }
      });
   }
}
