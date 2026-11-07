package com.inferno.scenario;

import com.inferno.fabric.Alerts;
import com.inferno.fabric.InfernoConfig;
import com.inferno.fabric.InfernoMod;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-game end-to-end tests for Inferno. Only active with -Dinferno.scenarios=true (./gradlew
 * runScenarioServer). Every scenario gets its own fake player on its own plot of a flat world and
 * replays the packets a client would send; legit scenarios must raise no flag at all, cheat scenarios
 * must raise their expected check. Results go to the log and to scenario-results.txt, then the server
 * stops.
 */
public final class ScenarioMod implements ModInitializer {
   static final Logger LOG = LoggerFactory.getLogger("InfernoScenarios");
   private static final int WARMUP = 120;
   private static final int COOLDOWN = 30;
   private final Map<UUID, Set<String>> flags = new ConcurrentHashMap<>();
   private final List<Scenario> running = new ArrayList<>();
   private int serverTicks;
   private boolean started;

   @Override
   public void onInitialize() {
      if (!Boolean.getBoolean("inferno.scenarios")) {
         return;
      }

      Alerts.listener = (player, check) -> this.flags.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(check.id());
      ServerLifecycleEvents.SERVER_STARTED.register(server -> LOG.info("[scenario] server started, waiting for the world to settle"));
      ServerTickEvents.START_SERVER_TICK.register(server -> {
         for (Scenario s : this.running) {
            if (!s.finished && s.tick >= WARMUP && s.tick < WARMUP + s.ticks) {
               s.script.early(s, s.tick - WARMUP);
            }
         }
      });
      ServerTickEvents.END_SERVER_TICK.register(this::tick);
   }

   private void tick(MinecraftServer server) {
      this.serverTicks++;
      if (!this.started) {
         if (this.serverTicks == 40) {
            this.setUp(server);
         }

         return;
      }

      boolean allDone = true;

      for (Scenario s : this.running) {
         if (!s.finished) {
            allDone = false;
            this.step(s);
         }
      }

      if (allDone && !this.running.isEmpty()) {
         this.report(server);
      }
   }

   private void setUp(MinecraftServer server) {
      InfernoConfig cfg = InfernoConfig.get();
      // Keep every fake player connected and in place so each scenario plays out in full.
      cfg.kickVl = 1000000;
      cfg.autoPunish = "none";
      cfg.kickLanCheaters = false;
      cfg.banOnKickVl = false;
      cfg.setbacks = false;
      cfg.hardLock = false;
      cfg.warnPlayers = false;
      cfg.combatMitigation = false;
      ServerLevel level = server.overworld();
      List<Scenario> all = Scenarios.all();

      for (int i = 0; i < all.size(); i++) {
         Scenario s = all.get(i);
         int bx = 4000 + i * 48;
         int bz = 4000;

         for (int cx = (bx - 16) >> 4; cx <= (bx + 16) >> 4; cx++) {
            for (int cz = (bz - 16) >> 4; cz <= (bz + 40) >> 4; cz++) {
               level.setChunkForced(cx, cz, true);
               level.getChunk(cx, cz);
            }
         }

         int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
         FakePlayer fake = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "scn" + i));
         fake.setGameMode(GameType.SURVIVAL);
         fake.snapTo(bx + 0.5, groundY, bz + 0.5, 0.0F, 0.0F);
         fake.getInventory().clearContent();
         fake.getInventory().setSelectedSlot(0);
         fake.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
         s.client = new Client(fake, bx + 0.5, groundY, bz + 0.5);
         s.bx = bx;
         s.bz = bz;
         s.groundY = groundY;
         InfernoMod.ENGINE.state(fake);
         s.script.setUp(s);
         this.running.add(s);
      }

      this.started = true;
      LOG.info("[scenario] {} scenarios set up", this.running.size());
   }

   private void step(Scenario s) {
      Client c = s.client;
      // Server-side half of the player's tick (effects, item use, fluids, elytra), as a connected player gets.
      // A real player's position only changes through its packets, so undo any physics the fake player's
      // tick applied (FakePlayer counts as server-controlled and would otherwise fall on its own).
      double px = c.p.getX();
      double py = c.p.getY();
      double pz = c.p.getZ();
      c.p.doTick();
      // ServerPlayer#tick (skipped by FakePlayer) is where the mining timer advances.
      c.p.gameMode.tick();
      if (c.p.getX() != px || c.p.getY() != py || c.p.getZ() != pz) {
         if (!s.vars.containsKey("drift")) {
            s.vars.put("drift", true);
            LOG.info("[scenario] {}: server tick moved the fake player by {} {} {} (undone)", s.name, c.p.getX() - px, c.p.getY() - py, c.p.getZ() - pz);
         }

         c.p.setPos(px, py, pz);
      }
      if (s.tick < WARMUP) {
         c.tickEnd();
      } else if (s.tick < WARMUP + s.ticks) {
         c.manualTickEnd = false;
         s.script.tick(s, s.tick - WARMUP);
         if (!c.manualTickEnd) {
            c.tickEnd();
         }
      } else if (s.tick < WARMUP + s.ticks + COOLDOWN) {
         c.tickEnd();
      } else {
         s.finished = true;
         s.flags = new TreeSet<>(this.flags.getOrDefault(c.p.getUUID(), Set.of()));
      }

      InfernoMod.ENGINE.tickPlayer(c.p);
      s.tick++;
   }

   private void report(MinecraftServer server) {
      List<String> lines = new ArrayList<>();
      int pass = 0;

      for (Scenario s : this.running) {
         boolean ok;
         String why;
         if (s.legit) {
            ok = s.flags.isEmpty();
            why = ok ? "no flags" : "FALSE POSITIVE " + s.flags;
         } else {
            ok = s.flags.containsAll(s.expect);
            why = ok ? "caught by " + s.expect + (s.flags.size() > s.expect.size() ? ", also " + minus(s.flags, s.expect) : "") : "MISSED " + minus(s.expect, s.flags) + " (flags: " + s.flags + ")";
         }

         if (ok) {
            pass++;
         }

         String line = (ok ? "PASS " : "FAIL ") + (s.legit ? "[legit] " : "[cheat] ") + s.name + ": " + why;
         lines.add(line);
         LOG.info("[scenario] {}", line);
      }

      String summary = "SCENARIOS: " + pass + "/" + this.running.size() + " passed";
      lines.add(summary);
      LOG.info("[scenario] {}", summary);

      try {
         Files.write(Path.of("scenario-results.txt"), lines);
      } catch (IOException e) {
         LOG.error("could not write results", e);
      }

      this.running.clear();
      server.halt(false);
   }

   private static Set<String> minus(Set<String> a, Set<String> b) {
      Set<String> out = new TreeSet<>(a);
      out.removeAll(b);
      return out;
   }
}
