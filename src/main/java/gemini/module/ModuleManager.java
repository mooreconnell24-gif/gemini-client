package gemini.module;

import com.mojang.blaze3d.platform.InputConstants;
import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.config.ConfigManager;
import gemini.module.modules.AirJumpModule;
import gemini.module.modules.AntiBlindModule;
import gemini.module.modules.AntiVoidModule;
import gemini.module.modules.AutoArmorModule;
import gemini.module.modules.AutoClickerModule;
import gemini.module.modules.AutoEatModule;
import gemini.module.modules.AutoFishModule;
import gemini.module.modules.AutoGappleModule;
import gemini.module.modules.AutoReplenishModule;
import gemini.module.modules.AutoRespawnModule;
import gemini.module.modules.AutoToolModule;
import gemini.module.modules.AutoTotemModule;
import gemini.module.modules.AutoWalkModule;
import gemini.module.modules.ChestStealerModule;
import gemini.module.modules.ClickGuiModule;
import gemini.module.modules.CoordinatesModule;
import gemini.module.modules.CriticalsModule;
import gemini.module.modules.ElytraBoostModule;
import gemini.module.modules.EspModule;
import gemini.module.modules.FastBreakModule;
import gemini.module.modules.FastLadderModule;
import gemini.module.modules.FastPlaceModule;
import gemini.module.modules.FlightModule;
import gemini.module.modules.Fullbright;
import gemini.module.modules.HighJumpModule;
import gemini.module.modules.HudModule;
import gemini.module.modules.InstantMineModule;
import gemini.module.modules.InventoryMoveModule;
import gemini.module.modules.JesusModule;
import gemini.module.modules.KeepSprintModule;
import gemini.module.modules.LongJumpModule;
import gemini.module.modules.NoBobModule;
import gemini.module.modules.NoFallModule;
import gemini.module.modules.NoHungerModule;
import gemini.module.modules.NoHurtCamModule;
import gemini.module.modules.NoJumpDelayModule;
import gemini.module.modules.NoRenderModule;
import gemini.module.modules.NoSlowModule;
import gemini.module.modules.NukerModule;
import gemini.module.modules.ParkourModule;
import gemini.module.modules.ReachModule;
import gemini.module.modules.RegenModule;
import gemini.module.modules.ReverseStepModule;
import gemini.module.modules.SafeWalkModule;
import gemini.module.modules.ScaffoldModule;
import gemini.module.modules.SeedModule;
import gemini.module.modules.SoloModules;
import gemini.module.modules.SpeedModule;
import gemini.module.modules.SpiderModule;
import gemini.module.modules.SprintAssist;
import gemini.module.modules.StepModule;
import gemini.module.modules.StorageEspModule;
import gemini.module.modules.TimeControlModule;
import gemini.module.modules.TimerModule;
import gemini.module.modules.VelocityModule;
import gemini.module.modules.WaypointsModule;
import gemini.module.modules.WeatherControlModule;
import gemini.module.modules.XrayModule;
import gemini.module.modules.ZoomModule;
import gemini.module.modules.bot.BaritoneModules;
import gemini.module.modules.combat.CombatExtras;
import gemini.module.modules.combat.CombatModules;
import gemini.module.modules.hud.HudWidgets;
import gemini.module.modules.misc.MiscExtras;
import gemini.module.modules.misc.MiscModules;
import gemini.module.modules.movement.MovementModules;
import gemini.module.modules.player.PlayerExtras;
import gemini.module.modules.player.PlayerModules;
import gemini.module.modules.render.CameraModules;
import gemini.module.modules.render.EntityRender;
import gemini.module.modules.render.RenderExtras;
import gemini.module.modules.render.WorldRender;
import gemini.module.modules.world.WorldExtras;
import gemini.module.modules.world.WorldModules;
import gemini.ui.HudContext;
import gemini.ui.clickgui.ClickGuiScreen;
import gemini.util.BaritoneBridge;
import gemini.util.BuiltInBaritone;
import gemini.util.RotationManager;
import gemini.util.Scheduler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;

public final class ModuleManager {
   private static volatile boolean activeDirty = true;
   private final ArrayList<Module> modules = new ArrayList<>();
   private final Map<Integer, Boolean> previousKeyState = new HashMap<>();
   private final Map<Class<?>, Module> byType = new HashMap<>();
   private final Map<String, Module> byKey = new HashMap<>();
   private volatile Module[] active = new Module[0];
   private ConfigManager configManager;
   private static final ArrayDeque<ModuleManager.Failure> FAILURES = new ArrayDeque<>();

   public static void invalidateActive() {
      activeDirty = true;
   }

   private static String key(String name) {
      return name.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
   }

   public Module[] active() {
      if (activeDirty) {
         synchronized (this) {
            if (activeDirty) {
               activeDirty = false;
               ArrayList<Module> running = new ArrayList<>();

               for (Module module : this.modules) {
                  if (module.isEnabled()) {
                     running.add(module);
                  }
               }

               this.active = running.toArray(new Module[0]);
            }
         }
      }

      return this.active;
   }

   static void recordFailure(Module module, String hook, Throwable exception) {
      synchronized (FAILURES) {
         if (FAILURES.size() >= 64) {
            FAILURES.removeFirst();
         }

         FAILURES.addLast(new ModuleManager.Failure(module.getName(), hook, exception.getClass().getSimpleName() + ": " + exception.getMessage()));
      }
   }

   public static List<ModuleManager.Failure> drainFailures() {
      synchronized (FAILURES) {
         List<ModuleManager.Failure> copy = new ArrayList<>(FAILURES);
         FAILURES.clear();
         return copy;
      }
   }

   private void disableAfterFailure(Module module, String hook, Throwable exception) {
      Gemini.LOGGER.error("Module {} crashed in {} and was disabled", new Object[]{module.getName(), hook, exception});
      recordFailure(module, hook, exception);
      module.setEnabled(false, false);
      CommandManager.feedback("§c" + module.getName() + " §7crashed in " + hook + " and was switched off. The log has the details.");
   }

   public void renderWorld(float partialTick) {
      BaritoneBridge.render3D();
      boolean timed = SelfTest.profiling();

      for (Module module : this.active()) {
         if (module.isEnabled()) {
            long start = timed ? System.nanoTime() : 0L;

            try {
               module.onRender3D(partialTick);
            } catch (StackOverflowError | RuntimeException | LinkageError var10) {
               this.disableAfterFailure(module, "onRender3D", var10);
            }

            if (timed) {
               SelfTest.record(module, System.nanoTime() - start);
            }
         }
      }
   }

   public void renderHud(HudContext hud) {
      boolean timed = SelfTest.profiling();

      for (Module module : this.active()) {
         if (module.isEnabled()) {
            long start = timed ? System.nanoTime() : 0L;

            try {
               module.onRenderHud(hud);
            } catch (StackOverflowError | RuntimeException | LinkageError var10) {
               this.disableAfterFailure(module, "onRenderHud", var10);
            }

            if (timed) {
               SelfTest.record(module, System.nanoTime() - start);
            }
         }
      }
   }

   public boolean sendPacket(Packet<?> packet) {
      if (RotationManager.onSendPacket(packet)) {
         return true;
      } else {
         boolean cancelled = false;

         for (Module module : this.active()) {
            if (module.isEnabled()) {
               try {
                  cancelled |= module.onSendPacket(packet);
               } catch (StackOverflowError | RuntimeException | LinkageError var8) {
                  this.disableAfterFailure(module, "onSendPacket", var8);
               }
            }
         }

         return cancelled;
      }
   }

   public boolean receivePacket(Packet<?> packet) {
      boolean cancelled = false;

      for (Module module : this.active()) {
         if (module.isEnabled()) {
            try {
               cancelled |= module.onReceivePacket(packet);
            } catch (StackOverflowError | RuntimeException | LinkageError var8) {
               this.disableAfterFailure(module, "onReceivePacket", var8);
            }
         }
      }

      return cancelled;
   }

   public void attack(Entity target) {
      for (Module module : this.active()) {
         if (module.isEnabled()) {
            try {
               module.onAttack(target);
            } catch (StackOverflowError | RuntimeException | LinkageError var7) {
               this.disableAfterFailure(module, "onAttack", var7);
            }
         }
      }
   }

   public void init() {
      this.register(new VelocityModule());
      this.register(new AutoTotemModule());
      this.register(new AutoGappleModule());
      this.register(new CriticalsModule());
      this.register(new AutoClickerModule());
      this.register(new KeepSprintModule());
      this.register(new FlightModule());
      this.register(new SprintAssist());
      this.register(new SpeedModule());
      this.register(new NoFallModule());
      this.register(new StepModule());
      this.register(new HighJumpModule());
      this.register(new SpiderModule());
      this.register(new JesusModule());
      this.register(new SafeWalkModule());
      this.register(new ParkourModule());
      this.register(new AirJumpModule());
      this.register(new LongJumpModule());
      this.register(new NoSlowModule());
      this.register(new InventoryMoveModule());
      this.register(new NoJumpDelayModule());
      this.register(new ElytraBoostModule());
      this.register(new ReverseStepModule());
      this.register(new AutoWalkModule());
      this.register(new FastLadderModule());
      this.register(new InstantMineModule());
      this.register(new FastPlaceModule());
      this.register(new FastBreakModule());
      this.register(new AutoToolModule());
      this.register(new AutoEatModule());
      this.register(new AutoArmorModule());
      this.register(new AutoFishModule());
      this.register(new AutoRespawnModule());
      this.register(new AntiVoidModule());
      this.register(new NoHungerModule());
      this.register(new RegenModule());
      this.register(new ReachModule());
      this.register(new AutoReplenishModule());
      this.register(new ChestStealerModule());
      this.register(new TimeControlModule());
      this.register(new WeatherControlModule());
      this.register(new WaypointsModule());
      this.register(new NukerModule());
      this.register(new ScaffoldModule());
      this.register(new TimerModule());
      this.register(new SeedModule());
      this.register(new Fullbright());
      this.register(new ZoomModule());
      this.register(new CoordinatesModule());
      this.register(new NoHurtCamModule());
      this.register(new NoBobModule());
      this.register(new AntiBlindModule());
      this.register(new EspModule());
      this.register(new StorageEspModule());
      this.register(new NoRenderModule());
      this.register(new XrayModule());
      this.register(new HudModule());
      this.register(new ClickGuiModule());
      SoloModules.register(this::register);
      HudWidgets.register(this::register);
      EntityRender.register(this::register);
      WorldRender.register(this::register);
      RenderExtras.register(this::register);
      CameraModules.register(this::register);
      CombatModules.register(this::register);
      CombatExtras.register(this::register);
      MovementModules.register(this::register);
      PlayerModules.register(this::register);
      PlayerExtras.register(this::register);
      WorldModules.register(this::register);
      WorldExtras.register(this::register);
      MiscModules.register(this::register);
      MiscExtras.register(this::register);
      BaritoneModules.register(this::register);
   }

   public void bindConfig(ConfigManager configManager) {
      this.configManager = configManager;

      for (Module module : this.modules) {
         module.attachConfig(configManager);
      }
   }

   private void register(Module module) {
      String key = key(module.getName());
      if (this.byKey.putIfAbsent(key, module) != null) {
         throw new IllegalStateException("Duplicate module name: " + module.getName());
      } else {
         this.modules.add(module);
         this.byType.put(module.getClass(), module);
      }
   }

   public List<Module> getModules() {
      return Collections.unmodifiableList(this.modules);
   }

   public List<Module> of(Category category) {
      List<Module> list = new ArrayList<>();

      for (Module module : this.modules) {
         if (module.getCategory() == category) {
            list.add(module);
         }
      }

      return list;
   }

   public List<Module> getEnabledModules() {
      List<Module> enabled = new ArrayList<>();

      for (Module module : this.modules) {
         if (module.isEnabled() && module.isListed()) {
            enabled.add(module);
         }
      }

      return enabled;
   }

   public Optional<Module> getByName(String name) {
      return Optional.ofNullable(this.byKey.get(key(name)));
   }

   public <T extends Module> Optional<T> byClass(Class<T> type) {
      Module exact = this.byType.get(type);
      if (exact != null) {
         return Optional.of(type.cast(exact));
      } else {
         for (Module module : this.modules) {
            if (type.isInstance(module)) {
               this.byType.put(type, module);
               return Optional.of(type.cast(module));
            }
         }

         return Optional.empty();
      }
   }

   public void onEndTick(Minecraft client) {
      for (Module module : this.modules) {
         module.applyRuntimeState();
      }

      if (client.player == null) {
         this.previousKeyState.clear();
         Scheduler.clear();
         RotationManager.clear();
         BuiltInBaritone.stop();
         BaritoneBridge.resumeAll();
         SelfTest.abort("you left the world");
      } else {
         Scheduler.tick();
         RotationManager.beginTick();
         this.pollKeybinds(client, client.gui.screen() == null);
         boolean timed = SelfTest.profiling();

         for (Module module : this.active()) {
            if (module.isEnabled()) {
               long start = timed ? System.nanoTime() : 0L;

               try {
                  module.onTick();
               } catch (StackOverflowError | RuntimeException | LinkageError var10) {
                  this.disableAfterFailure(module, "onTick", var10);
               }

               if (timed) {
                  SelfTest.record(module, System.nanoTime() - start);
               }
            }
         }

         this.syncBaritonePause();
         BaritoneBridge.tick();
         RotationManager.endTick();
         SelfTest.tick(this);
      }
   }

   private void syncBaritonePause() {
      boolean pause = false;

      for (Module module : this.active()) {
         if (module.isEnabled()) {
            try {
               if (module.wantsBaritonePause()) {
                  pause = true;
                  break;
               }
            } catch (RuntimeException | LinkageError var7) {
               this.disableAfterFailure(module, "wantsBaritonePause", var7);
            }
         }
      }

      if (pause) {
         BaritoneBridge.pause("combat");
      } else {
         BaritoneBridge.resume("combat");
      }
   }

   public void shutdown() {
      BuiltInBaritone.stop();

      for (Module module : this.modules) {
         module.forceDisableRuntime();
      }

      this.previousKeyState.clear();
   }

   public void panic() {
      BuiltInBaritone.stop();
      if (this.configManager != null) {
         this.configManager.setSavingPaused(true);
      }

      int shut = 0;

      try {
         for (Module module : this.modules) {
            if (!(module instanceof ClickGuiModule) && !(module instanceof HudModule) && (module.isSavedEnabled() || module.isEnabled())) {
               module.setEnabled(false, false);
               shut++;
            }
         }

         this.previousKeyState.clear();
         Minecraft client = Minecraft.getInstance();
         if (client.gui.screen() instanceof ClickGuiScreen) {
            client.gui.setScreen(null);
         }
      } finally {
         if (this.configManager != null) {
            this.configManager.setSavingPaused(false);
            this.configManager.save();
         }
      }

      Gemini.LOGGER.info("Panic disabled {} modules", shut);
   }

   private void pollKeybinds(Minecraft client, boolean inGame) {
      Set<Integer> seen = new HashSet<>();

      for (Module module : this.modules) {
         if (!(module instanceof ClickGuiModule)) {
            int key = module.getKeybind();
            if (key != -1 && key != 0) {
               boolean pressed = InputConstants.isKeyDown(client.getWindow(), key);
               boolean wasPressed = this.previousKeyState.getOrDefault(key, false);
               seen.add(key);
               if (module.isHoldBind()) {
                  if (inGame && pressed && !wasPressed) {
                     module.setHeld(true);
                  } else if (!pressed && wasPressed && module.isSavedEnabled()) {
                     module.setHeld(false);
                  }
               } else if (inGame && pressed && !wasPressed) {
                  module.toggle();
               }
            }
         }
      }

      for (int key : seen) {
         this.previousKeyState.put(key, InputConstants.isKeyDown(client.getWindow(), key));
      }

      this.previousKeyState.keySet().removeIf(keyx -> !seen.contains(keyx));
   }

   public record Failure(String module, String hook, String error) {
   }
}
