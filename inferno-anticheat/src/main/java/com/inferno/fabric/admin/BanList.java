package com.inferno.fabric.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.inferno.fabric.InfernoMod;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.loader.api.FabricLoader;

public final class BanList {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("inferno-bans.json");
   private static final Type LIST_TYPE = (new TypeToken<List<BanList.BanEntry>>() {}).getType();
   private static final CopyOnWriteArrayList<BanList.BanEntry> ENTRIES = new CopyOnWriteArrayList<>();

   private BanList() {
   }

   public static synchronized void load() {
      ENTRIES.clear();

      try {
         if (!Files.exists(FILE)) {
            return;
         }

         try (BufferedReader r = Files.newBufferedReader(FILE)) {
            List<BanList.BanEntry> loaded = (List<BanList.BanEntry>)GSON.fromJson(r, LIST_TYPE);
            if (loaded != null) {
               ENTRIES.addAll(loaded);
            }
         }

         purgeExpired();
      } catch (Exception e) {
         InfernoMod.LOGGER.warn("Could not read bans: {}", e.getMessage());
      }
   }

   public static synchronized void save() {
      try {
         Files.createDirectories(FILE.getParent());

         try (BufferedWriter w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(new ArrayList<>(ENTRIES), w);
         }
      } catch (Exception e) {
         InfernoMod.LOGGER.warn("Could not save bans: {}", e.getMessage());
      }
   }

   public static void purgeExpired() {
      long now = System.currentTimeMillis();
      boolean changed = ENTRIES.removeIf(e -> e.expiresAt > 0L && e.expiresAt <= now);
      if (changed) {
         save();
      }
   }

   public static void ban(String uuid, String name, String reason, String by, long minutes) {
      unban(uuid);
      long expires = minutes <= 0L ? 0L : System.currentTimeMillis() + minutes * 60000L;
      ENTRIES.add(new BanList.BanEntry(uuid, name, reason != null && !reason.isBlank() ? reason : "Banned by Inferno", by, System.currentTimeMillis(), expires));
      save();
   }

   public static boolean unban(String uuid) {
      boolean removed = ENTRIES.removeIf(e -> e.uuid.equalsIgnoreCase(uuid));
      if (removed) {
         save();
      }

      return removed;
   }

   public static Optional<BanList.BanEntry> find(String uuid) {
      purgeExpired();
      return ENTRIES.stream().filter(e -> e.uuid.equalsIgnoreCase(uuid)).findFirst();
   }

   public static List<BanList.BanEntry> all() {
      purgeExpired();
      return new ArrayList<>(ENTRIES);
   }

   public static final class BanEntry {
      public String uuid;
      public String name;
      public String reason;
      public String by;
      public long createdAt;
      public long expiresAt;

      public BanEntry() {
      }

      public BanEntry(String uuid, String name, String reason, String by, long createdAt, long expiresAt) {
         this.uuid = uuid;
         this.name = name;
         this.reason = reason;
         this.by = by;
         this.createdAt = createdAt;
         this.expiresAt = expiresAt;
      }

      public boolean permanent() {
         return this.expiresAt <= 0L;
      }
   }
}
