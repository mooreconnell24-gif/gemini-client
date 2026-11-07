package com.inferno.fabric;

import com.inferno.fabric.checks.Check;

public final class SecurityProfile {
   private SecurityProfile() {
   }

   public static void applyMaximum(InfernoConfig cfg) {
      cfg.setbacks = true;
      cfg.cancelHits = true;
      cfg.cancelBlocks = true;
      cfg.combatMitigation = true;
      cfg.warnPlayers = true;
      cfg.hardLock = true;
      cfg.strictMode = true;
      cfg.kickLanCheaters = true;
      cfg.autoPunish = "kick";
      cfg.banOnKickVl = false;
      cfg.kickVl = 12;
      cfg.lockCleanTicks = 60;
      cfg.lockMinMs = 4000L;
      set(cfg, "reach.max-reach", 3.05);
      set(cfg, "speed.max-speed", 0.55);
      set(cfg, "speed-average.max-average", 0.38);
      set(cfg, "fly.max-air-ms", 280.0);
      set(cfg, "timer.max-ahead-ms", 120.0);
      set(cfg, "autoclicker.max-cps", 14.0);
      set(cfg, "fastplace.max-per-second", 12.0);
      set(cfg, "nuker.max-per-second", 12.0);
      set(cfg, "block-reach.max-distance", 4.8);
      set(cfg, "strafe.max-air-accel", 0.045);
      set(cfg, "blink.max-gap-ms", 700.0);
      set(cfg, "elytra-speed.max", 2.6);
      set(cfg, "invalid-motion.max", 6.0);
      set(cfg, "killaura-angle.max-deg", 55.0);
      set(cfg, "multi-angle.max-deg", 60.0);
      set(cfg, "aim-smooth.max-variance", 0.012);
      set(cfg, "inventory-clean.max-cps", 12.0);

      for (Check c : InfernoMod.ENGINE.all()) {
         cfg.checks.put(c.id(), true);
      }

      cfg.save();
   }

   private static void set(InfernoConfig cfg, String key, double v) {
      cfg.limits.put(key, v);
   }
}
