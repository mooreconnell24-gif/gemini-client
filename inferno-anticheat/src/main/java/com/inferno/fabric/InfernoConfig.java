package com.inferno.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.inferno.fabric.admin.OpAuth;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;

public final class InfernoConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("inferno-anticheat.json");
   public Map<String, Boolean> checks = new LinkedHashMap<>();
   public boolean setbacks = true;
   public boolean cancelHits = true;
   public boolean cancelBlocks = true;
   public boolean chatAlerts = true;
   public boolean combatMitigation = true;
   public boolean warnPlayers = true;
   public boolean kickLanCheaters = true;
   public boolean debugHud = false;
   public int kickVl = 12;
   public Map<String, Double> limits = new LinkedHashMap<>();
   private static final Map<String, Double> DEFAULT_LIMITS = new LinkedHashMap<>();
   public int panelX = -1;
   public int panelY = -1;
   public String autoPunish = "kick";
   public boolean banOnKickVl = false;
   public int defaultBanMinutes = 60;
   public boolean maxSecurityApplied = false;
   public List<String> adminUuids = new ArrayList<>();
   public Map<String, String> opPasswords = new LinkedHashMap<>();
   public String discordWebhook = "";
   public boolean persistVlOnQuit = true;
   public boolean panelAnimations = true;
   public boolean hardLock = true;
   public int lockCleanTicks = 60;
   public long lockMinMs = 4000L;
   public boolean strictMode = true;
   private static InfernoConfig instance;

   public static double defaultLimit(String key) {
      return DEFAULT_LIMITS.getOrDefault(key, 0.0);
   }

   public double limit(String key) {
      Double v = this.limits.get(key);
      return v != null ? v : DEFAULT_LIMITS.getOrDefault(key, 0.0);
   }

   public static synchronized InfernoConfig get() {
      if (instance == null) {
         instance = load();
      }

      return instance;
   }

   public static synchronized void reload() {
      instance = load();
   }

   private static InfernoConfig load() {
      try {
         if (Files.exists(FILE)) {
            try (BufferedReader r = Files.newBufferedReader(FILE)) {
               InfernoConfig c = (InfernoConfig)GSON.fromJson(r, InfernoConfig.class);
               if (c != null) {
                  if (c.checks == null) {
                     c.checks = new LinkedHashMap<>();
                  }

                  if (c.limits == null) {
                     c.limits = new LinkedHashMap<>();
                  }

                  if (c.adminUuids == null) {
                     c.adminUuids = new ArrayList<>();
                  }

                  if (c.opPasswords == null) {
                     c.opPasswords = new LinkedHashMap<>();
                  }

                  OpAuth.ensureDefaults(c);
                  if (c.autoPunish == null || c.autoPunish.isBlank()) {
                     c.autoPunish = "none";
                  }

                  if (c.discordWebhook == null) {
                     c.discordWebhook = "";
                  }

                  boolean added = false;

                  for (Entry<String, Double> e : DEFAULT_LIMITS.entrySet()) {
                     if (!c.limits.containsKey(e.getKey())) {
                        c.limits.put(e.getKey(), e.getValue());
                        added = true;
                     }
                  }

                  if (added) {
                     c.save();
                  }

                  return c;
               }
            }
         }
      } catch (Exception e) {
         InfernoMod.LOGGER.warn("Could not read {}, using defaults: {}", FILE, e.getMessage());
      }

      InfernoConfig c = new InfernoConfig();
      c.limits.putAll(DEFAULT_LIMITS);
      c.opPasswords = new LinkedHashMap<>(OpAuth.defaultPasswords());
      SecurityProfile.applyMaximum(c);
      c.maxSecurityApplied = true;
      c.save();
      return c;
   }

   public synchronized void save() {
      try {
         Files.createDirectories(FILE.getParent());

         try (BufferedWriter w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(this, w);
         }
      } catch (Exception e) {
         InfernoMod.LOGGER.warn("Could not save {}: {}", FILE, e.getMessage());
      }
   }

   public boolean enabled(String check) {
      return this.checks.getOrDefault(check, true);
   }

   public void toggle(String check) {
      this.checks.put(check, !this.enabled(check));
      this.save();
   }

   static {
      DEFAULT_LIMITS.put("reach.max-reach", 3.05);
      DEFAULT_LIMITS.put("speed.max-speed", 0.55);
      DEFAULT_LIMITS.put("speed-average.max-average", 0.38);
      DEFAULT_LIMITS.put("fly.max-air-ms", 280.0);
      DEFAULT_LIMITS.put("timer.max-ahead-ms", 120.0);
      DEFAULT_LIMITS.put("autoclicker.max-cps", 14.0);
      DEFAULT_LIMITS.put("killaura-angle.max-deg", 55.0);
      DEFAULT_LIMITS.put("fastplace.max-per-second", 20.0);
      DEFAULT_LIMITS.put("nuker.max-per-second", 22.0);
      DEFAULT_LIMITS.put("block-reach.max-distance", 5.6);
      DEFAULT_LIMITS.put("autofish.max-reaction-ms", 120.0);
      DEFAULT_LIMITS.put("aim-smooth.max-variance", 0.02);
      DEFAULT_LIMITS.put("regen.min-interval-ms", 450.0);
      DEFAULT_LIMITS.put("fastuse.min-ticks", 10.0);
      DEFAULT_LIMITS.put("strafe.max-air-accel", 0.08);
      DEFAULT_LIMITS.put("blink.max-gap-ms", 1200.0);
      DEFAULT_LIMITS.put("boat-fly.max-air-ms", 800.0);
      DEFAULT_LIMITS.put("elytra-speed.max", 3.2);
      DEFAULT_LIMITS.put("invalid-motion.max", 10.0);
      DEFAULT_LIMITS.put("fastbreak.min-ms", 40.0);
      DEFAULT_LIMITS.put("inventory-clean.max-cps", 18.0);
      DEFAULT_LIMITS.put("multi-angle.max-deg", 85.0);
      DEFAULT_LIMITS.put("high-jump.max-height", 1.35);
      DEFAULT_LIMITS.put("liquid-speed.max", 0.45);
      DEFAULT_LIMITS.put("riptide.max-speed", 4.0);
      DEFAULT_LIMITS.put("vehicle-speed.boat", 1.0);
      DEFAULT_LIMITS.put("vehicle-speed.mount", 1.4);
      DEFAULT_LIMITS.put("break-progress.min-ratio", 0.85);
      DEFAULT_LIMITS.put("inventory-bot.max-deviation", 6.0);
      DEFAULT_LIMITS.put("auto-respawn.min-ms", 900.0);
      DEFAULT_LIMITS.put("tick-timer.max-ahead-ms", 250.0);
      DEFAULT_LIMITS.put("packet-flood.max-per-second", 600.0);
   }
}
