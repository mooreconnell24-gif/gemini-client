package gemini.module;

import com.mojang.blaze3d.platform.InputConstants;
import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.config.ConfigManager;
import gemini.event.EventBus;
import gemini.module.modules.bot.AutoExcavate;
import gemini.module.modules.bot.AutoExplore;
import gemini.module.modules.bot.AutoGoto;
import gemini.module.modules.bot.AutoHighway;
import gemini.module.modules.bot.AutoMineBot;
import gemini.module.modules.bot.AutoTunnel;
import gemini.module.modules.bot.BaritonePause;
import gemini.module.modules.bot.BaritoneStatus;
import gemini.module.modules.bot.FollowBot;
import gemini.module.modules.bot.PathRenderer;
import gemini.module.modules.combat.AimAssist;
import gemini.module.modules.combat.AnchorAura;
import gemini.module.modules.combat.ArrowDamageModule;
import gemini.module.modules.combat.ArrowDodge;
import gemini.module.modules.combat.AutoBlockModule;
import gemini.module.modules.combat.AutoClickerModule;
import gemini.module.modules.combat.AutoCrystal;
import gemini.module.modules.combat.AutoExp;
import gemini.module.modules.combat.AutoGappleModule;
import gemini.module.modules.combat.AutoPearl;
import gemini.module.modules.combat.AutoPot;
import gemini.module.modules.combat.AutoRod;
import gemini.module.modules.combat.AutoSoup;
import gemini.module.modules.combat.AutoTotemModule;
import gemini.module.modules.combat.AutoTrap;
import gemini.module.modules.combat.AutoWeapon;
import gemini.module.modules.combat.Backtrack;
import gemini.module.modules.combat.BedAura;
import gemini.module.modules.combat.BlockHitModule;
import gemini.module.modules.combat.BowSpam;
import gemini.module.modules.combat.Burrow;
import gemini.module.modules.combat.CriticalsModule;
import gemini.module.modules.combat.Hitboxes;
import gemini.module.modules.combat.HoleFiller;
import gemini.module.modules.combat.KeepRange;
import gemini.module.modules.combat.KeepSprintModule;
import gemini.module.modules.combat.KillAura;
import gemini.module.modules.combat.MaceKill;
import gemini.module.modules.combat.Offhand;
import gemini.module.modules.combat.ProjectileAimbot;
import gemini.module.modules.combat.Quiver;
import gemini.module.modules.combat.SelfTrap;
import gemini.module.modules.combat.ShieldBreaker;
import gemini.module.modules.combat.SuperKnockback;
import gemini.module.modules.combat.Surround;
import gemini.module.modules.combat.Teams;
import gemini.module.modules.combat.TriggerBot;
import gemini.module.modules.combat.VelocityModule;
import gemini.module.modules.combat.WTap;
import gemini.module.modules.combat.WebAura;
import gemini.module.modules.hud.AntiLagHud;
import gemini.module.modules.hud.ArmorHud;
import gemini.module.modules.hud.BiomeInfo;
import gemini.module.modules.hud.ChunkInfo;
import gemini.module.modules.hud.Clock;
import gemini.module.modules.hud.CombatItems;
import gemini.module.modules.hud.CompassBar;
import gemini.module.modules.hud.Cooldowns;
import gemini.module.modules.hud.Cps;
import gemini.module.modules.hud.CustomText;
import gemini.module.modules.hud.EntityCounter;
import gemini.module.modules.hud.Facing;
import gemini.module.modules.hud.Fps;
import gemini.module.modules.hud.FpsGraph;
import gemini.module.modules.hud.HeldItem;
import gemini.module.modules.hud.InventoryViewer;
import gemini.module.modules.hud.ItemCounter;
import gemini.module.modules.hud.KeybindList;
import gemini.module.modules.hud.Keystrokes;
import gemini.module.modules.hud.LagNotifier;
import gemini.module.modules.hud.LastDeath;
import gemini.module.modules.hud.LightLevel;
import gemini.module.modules.hud.LookingAt;
import gemini.module.modules.hud.Memory;
import gemini.module.modules.hud.Position;
import gemini.module.modules.hud.PotionTimers;
import gemini.module.modules.hud.Radar;
import gemini.module.modules.hud.SessionTimer;
import gemini.module.modules.hud.Speedometer;
import gemini.module.modules.hud.TargetHud;
import gemini.module.modules.hud.TotemCounter;
import gemini.module.modules.hud.Tps;
import gemini.module.modules.hud.Welcome;
import gemini.module.modules.misc.AntiLag;
import gemini.module.modules.misc.AutoGG;
import gemini.module.modules.misc.BetterChat;
import gemini.module.modules.misc.InfernoBypass;
import gemini.module.modules.misc.Aim;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.module.modules.misc.DeathCoords;
import gemini.module.modules.misc.FlagCheck;
import gemini.module.modules.misc.Macro;
import gemini.module.modules.misc.NameProtect;
import gemini.module.modules.misc.Notifier;
import gemini.module.modules.misc.PacketLogger;
import gemini.module.modules.misc.Panic;
import gemini.module.modules.misc.Presets;
import gemini.module.modules.misc.SoundBlocker;
import gemini.module.modules.movement.AirJumpModule;
import gemini.module.modules.movement.AnchorPull;
import gemini.module.modules.movement.AntiLevitation;
import gemini.module.modules.movement.AutoFirework;
import gemini.module.modules.movement.AutoJump;
import gemini.module.modules.movement.AutoWalkModule;
import gemini.module.modules.movement.BoatFly;
import gemini.module.modules.movement.ClickTp;
import gemini.module.modules.movement.EdgeJump;
import gemini.module.modules.movement.ElytraBoostModule;
import gemini.module.modules.movement.ElytraFly;
import gemini.module.modules.movement.EntitySpeed;
import gemini.module.modules.movement.FastFall;
import gemini.module.modules.movement.FastLadderModule;
import gemini.module.modules.movement.FastSwim;
import gemini.module.modules.movement.FlightModule;
import gemini.module.modules.movement.Freeze;
import gemini.module.modules.movement.Glide;
import gemini.module.modules.movement.HighJumpModule;
import gemini.module.modules.movement.HoleSnap;
import gemini.module.modules.movement.InventoryMoveModule;
import gemini.module.modules.movement.JesusModule;
import gemini.module.modules.movement.KeepY;
import gemini.module.modules.movement.LongJumpModule;
import gemini.module.modules.movement.NoFallModule;
import gemini.module.modules.movement.NoJumpDelayModule;
import gemini.module.modules.movement.NoSlowModule;
import gemini.module.modules.movement.NoWeb;
import gemini.module.modules.movement.ParkourModule;
import gemini.module.modules.movement.ReverseStepModule;
import gemini.module.modules.movement.SafeWalkModule;
import gemini.module.modules.movement.Slippy;
import gemini.module.modules.movement.Sneak;
import gemini.module.modules.movement.SpeedModule;
import gemini.module.modules.movement.SpiderModule;
import gemini.module.modules.movement.SprintAssist;
import gemini.module.modules.movement.StepModule;
import gemini.module.modules.movement.Strafe;
import gemini.module.modules.movement.TargetStrafe;
import gemini.module.modules.movement.TerrainSpeed;
import gemini.module.modules.movement.TimerRange;
import gemini.module.modules.movement.TridentBoost;
import gemini.module.modules.movement.WallClimb;
import gemini.module.modules.player.AntiBreak;
import gemini.module.modules.player.AntiVoidModule;
import gemini.module.modules.player.AutoArmorModule;
import gemini.module.modules.player.Clutch;
import gemini.module.modules.player.AutoDrop;
import gemini.module.modules.player.AutoEatModule;
import gemini.module.modules.player.AutoFishModule;
import gemini.module.modules.player.AutoMend;
import gemini.module.modules.player.AutoMount;
import gemini.module.modules.player.AutoReplenishModule;
import gemini.module.modules.player.AutoRespawnModule;
import gemini.module.modules.player.AutoSleep;
import gemini.module.modules.player.AutoToolModule;
import gemini.module.modules.player.AutoWindCharge;
import gemini.module.modules.player.ChestStealerModule;
import gemini.module.modules.player.ChestSwap;
import gemini.module.modules.player.Derp;
import gemini.module.modules.player.DurabilityAlert;
import gemini.module.modules.player.Eagle;
import gemini.module.modules.player.ExpThrower;
import gemini.module.modules.player.FakePlayer;
import gemini.module.modules.player.FastBreakModule;
import gemini.module.modules.player.FastPlaceModule;
import gemini.module.modules.player.FastUse;
import gemini.module.modules.player.Gamemode;
import gemini.module.modules.player.AutoHeal;
import gemini.module.modules.player.Kits;
import gemini.module.modules.player.GhostHand;
import gemini.module.modules.player.HotbarSorter;
import gemini.module.modules.player.InstantMineModule;
import gemini.module.modules.player.InventoryCleaner;
import gemini.module.modules.player.InventorySorter;
import gemini.module.modules.player.ItemMagnet;
import gemini.module.modules.player.LiquidInteract;
import gemini.module.modules.player.MiddleClickExtra;
import gemini.module.modules.player.NoHungerModule;
import gemini.module.modules.player.NoInteract;
import gemini.module.modules.player.NoPush;
import gemini.module.modules.player.NoRotateSet;
import gemini.module.modules.player.Phase;
import gemini.module.modules.player.PortalMenu;
import gemini.module.modules.player.PotionSpoof;
import gemini.module.modules.player.ReachModule;
import gemini.module.modules.player.RegenModule;
import gemini.module.modules.player.Rotation;
import gemini.module.modules.player.SmartEat;
import gemini.module.modules.player.Tower;
import gemini.module.modules.player.XCarry;
import gemini.module.modules.render.Animations;
import gemini.module.modules.render.AntiBlindModule;
import gemini.module.modules.render.BedPlates;
import gemini.module.modules.render.BetterTooltips;
import gemini.module.modules.render.BlockCounter;
import gemini.module.modules.render.BlockSelection;
import gemini.module.modules.render.Breadcrumbs;
import gemini.module.modules.render.BreakIndicators;
import gemini.module.modules.render.CameraTweaks;
import gemini.module.modules.render.ChinaHat;
import gemini.module.modules.render.ChunkBorders;
import gemini.module.modules.render.CoordinatesModule;
import gemini.module.modules.render.CrosshairPlus;
import gemini.module.modules.render.DamageParticles;
import gemini.module.modules.render.EntityCulling;
import gemini.module.modules.render.EntityOwner;
import gemini.module.modules.render.EspModule;
import gemini.module.modules.render.Fov;
import gemini.module.modules.render.FreeLook;
import gemini.module.modules.render.Freecam;
import gemini.module.modules.render.Fullbright;
import gemini.module.modules.render.HitEffect;
import gemini.module.modules.render.HoleEsp;
import gemini.module.modules.render.HudModule;
import gemini.module.modules.render.ItemEsp;
import gemini.module.modules.render.JumpEffect;
import gemini.module.modules.render.LightOverlay;
import gemini.module.modules.render.LogoffSpot;
import gemini.module.modules.render.Marker;
import gemini.module.modules.render.Nametags;
import gemini.module.modules.render.NoBobModule;
import gemini.module.modules.render.NoHurtCamModule;
import gemini.module.modules.render.NoRenderModule;
import gemini.module.modules.render.ParticleBlocker;
import gemini.module.modules.render.PearlTracker;
import gemini.module.modules.render.PlayerTrails;
import gemini.module.modules.render.ProjectileEsp;
import gemini.module.modules.render.Search;
import gemini.module.modules.render.SlimeChunks;
import gemini.module.modules.render.SoundLocator;
import gemini.module.modules.render.StorageEspModule;
import gemini.module.modules.render.TntTimer;
import gemini.module.modules.render.Tracers;
import gemini.module.modules.render.Trajectories;
import gemini.module.modules.render.TunnelEsp;
import gemini.module.modules.render.UnfocusedCpu;
import gemini.module.modules.render.VoidEsp;
import gemini.module.modules.render.XrayModule;
import gemini.module.modules.render.ZoomModule;
import gemini.module.modules.world.AirPlace;
import gemini.module.modules.world.AutoBreed;
import gemini.module.modules.world.AutoFarm;
import gemini.module.modules.world.AutoNametag;
import gemini.module.modules.world.AutoShearer;
import gemini.module.modules.world.AutoSign;
import gemini.module.modules.world.AutoTorch;
import gemini.module.modules.world.BedDefender;
import gemini.module.modules.world.BlockIn;
import gemini.module.modules.world.ChestAura;
import gemini.module.modules.world.Collisions;
import gemini.module.modules.world.EndermanLook;
import gemini.module.modules.world.Extinguish;
import gemini.module.modules.world.MobSweeper;
import gemini.module.modules.world.Summoner;
import gemini.module.modules.world.Flamethrower;
import gemini.module.modules.world.Fucker;
import gemini.module.modules.world.Home;
import gemini.module.modules.world.LawnMower;
import gemini.module.modules.world.LiquidFiller;
import gemini.module.modules.world.NukerModule;
import gemini.module.modules.world.PacketMine;
import gemini.module.modules.world.ScaffoldModule;
import gemini.module.modules.world.SeedModule;
import gemini.module.modules.world.SpawnProofer;
import gemini.module.modules.world.StrongholdFinder;
import gemini.module.modules.world.TickFreeze;
import gemini.module.modules.world.TimeControlModule;
import gemini.module.modules.world.TimerModule;
import gemini.module.modules.world.VeinMiner;
import gemini.module.modules.world.WaypointsModule;
import gemini.module.modules.world.WeatherControlModule;
import gemini.module.modules.world.WorldRules;
import gemini.ui.HudContext;
import gemini.ui.clickgui.Menus;
import gemini.util.BaritoneBridge;
import gemini.util.BlockBreaking;
import gemini.util.BuiltInBaritone;
import gemini.util.Hands;
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
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

public final class ModuleManager {
   private final ArrayList<Module> modules = new ArrayList<>();
   private final Map<Integer, Boolean> previousKeyState = new HashMap<>();
   private final Map<Class<?>, Module> byType = new HashMap<>();
   private final Map<String, Module> byKey = new HashMap<>();
   private final EventBus bus = new EventBus(this::disableAfterFailure, new EventBus.Profiler() {
      @Override
      public boolean profiling() {
         return SelfTest.profiling();
      }

      @Override
      public void record(Module module, long nanos) {
         SelfTest.record(module, nanos);
      }
   });
   private ConfigManager configManager;
   private static final ArrayDeque<ModuleManager.Failure> FAILURES = new ArrayDeque<>();

   /** A module turned on or off: the bus rebuilds its subscriber lists before the next event. */
   public static void invalidateActive() {
      EventBus.invalidate();
   }

   private static String key(String name) {
      return name.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
   }

   public EventBus bus() {
      return this.bus;
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
      this.bus.render3D(partialTick);
   }

   public void renderHud(HudContext hud) {
      this.bus.renderHud(hud);
   }

   public boolean sendPacket(Packet<?> packet) {
      return RotationManager.onSendPacket(packet) || this.bus.sendPacket(packet);
   }

   public boolean receivePacket(Packet<?> packet) {
      return this.bus.receivePacket(packet);
   }

   public void attack(Entity target) {
      this.bus.attack(target);
   }

   public void blockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
      this.bus.blockUpdate(pos, oldState, newState);
   }

   public void init() {
      this.register(new VelocityModule());
      this.register(new AutoTotemModule());
      this.register(new AutoGappleModule());
      this.register(new CriticalsModule());
      this.register(new ArrowDamageModule());
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
      this.register(new Aim());
      this.register(new Home());
      this.register(new Gamemode());
      this.register(new ItemMagnet());
      this.register(new SlimeChunks());
      this.register(new Fps());
      this.register(new Tps());
      this.register(new Speedometer());
      this.register(new Memory());
      this.register(new SessionTimer());
      this.register(new Cps());
      this.register(new Position());
      this.register(new Facing());
      this.register(new BiomeInfo());
      this.register(new Clock());
      this.register(new LightLevel());
      this.register(new LastDeath());
      this.register(new ChunkInfo());
      this.register(new LookingAt());
      this.register(new CompassBar());
      this.register(new Welcome());
      this.register(new LagNotifier());
      this.register(new CustomText());
      this.register(new PotionTimers());
      this.register(new EntityCounter());
      this.register(new ArmorHud());
      this.register(new HeldItem());
      this.register(new ItemCounter());
      this.register(new TotemCounter());
      this.register(new InventoryViewer());
      this.register(new Keystrokes());
      this.register(new TargetHud());
      this.register(new Radar());
      this.register(new CombatItems());
      this.register(new FpsGraph());
      this.register(new KeybindList());
      this.register(new Cooldowns());
      this.register(new AntiLagHud());
      this.register(new Tracers());
      this.register(new Nametags());
      this.register(new Trajectories());
      this.register(new Breadcrumbs());
      this.register(new EntityOwner());
      this.register(new LightOverlay());
      this.register(new Search());
      this.register(new ChunkBorders());
      this.register(new BlockSelection());
      this.register(new Marker());
      this.register(new ItemEsp());
      this.register(new ProjectileEsp());
      this.register(new TunnelEsp());
      this.register(new VoidEsp());
      this.register(new Animations());
      this.register(new HoleEsp());
      this.register(new JumpEffect());
      this.register(new DamageParticles());
      this.register(new ChinaHat());
      this.register(new LogoffSpot());
      this.register(new TntTimer());
      this.register(new BedPlates());
      this.register(new CrosshairPlus());
      this.register(new EntityCulling());
      this.register(new PearlTracker());
      this.register(new BreakIndicators());
      this.register(new HitEffect());
      this.register(new SoundLocator());
      this.register(new PlayerTrails());
      this.register(new BlockCounter());
      this.register(new Freecam());
      this.register(new FreeLook());
      this.register(new CameraTweaks());
      this.register(new Fov());
      this.register(new UnfocusedCpu());
      this.register(new ParticleBlocker());
      this.register(new BetterTooltips());
      this.register(new KillAura());
      this.register(new AutoCrystal());
      this.register(new Surround());
      this.register(new AutoTrap());
      this.register(new AutoWeapon());
      this.register(new Hitboxes());
      this.register(new Offhand());
      this.register(new AutoBlockModule());
      this.register(new BlockHitModule());
      this.register(new BowSpam());
      this.register(new ArrowDodge());
      this.register(new ProjectileAimbot());
      this.register(new Quiver());
      this.register(new AutoRod());
      this.register(new AutoExp());
      this.register(new AutoPot());
      this.register(new TriggerBot());
      this.register(new SuperKnockback());
      this.register(new AimAssist());
      this.register(new Backtrack());
      this.register(new HoleFiller());
      this.register(new Burrow());
      this.register(new SelfTrap());
      this.register(new WebAura());
      this.register(new AnchorAura());
      this.register(new BedAura());
      this.register(new AutoSoup());
      this.register(new AutoPearl());
      this.register(new MaceKill());
      this.register(new ShieldBreaker());
      this.register(new WTap());
      this.register(new KeepRange());
      this.register(new ElytraFly());
      this.register(new Sneak());
      this.register(new Slippy());
      this.register(new AutoJump());
      this.register(new EntitySpeed());
      this.register(new BoatFly());
      this.register(new ClickTp());
      this.register(new Glide());
      this.register(new TridentBoost());
      this.register(new AutoFirework());
      this.register(new FastFall());
      this.register(new TargetStrafe());
      this.register(new Strafe());
      this.register(new Freeze());
      this.register(new NoWeb());
      this.register(new AntiLevitation());
      this.register(new TerrainSpeed());
      this.register(new HoleSnap());
      this.register(new TimerRange());
      this.register(new KeepY());
      this.register(new FastSwim());
      this.register(new EdgeJump());
      this.register(new AnchorPull());
      this.register(new WallClimb());
      this.register(new AutoDrop());
      this.register(new AutoMount());
      this.register(new ChestSwap());
      this.register(new FakePlayer());
      this.register(new LiquidInteract());
      this.register(new MiddleClickExtra());
      this.register(new NoInteract());
      this.register(new Rotation());
      this.register(new PotionSpoof());
      this.register(new ExpThrower());
      this.register(new AutoMend());
      this.register(new AntiBreak());
      this.register(new Eagle());
      this.register(new Tower());
      this.register(new Phase());
      this.register(new AutoSleep());
      this.register(new InventoryCleaner());
      this.register(new FastUse());
      this.register(new NoRotateSet());
      this.register(new AutoWindCharge());
      this.register(new GhostHand());
      this.register(new SmartEat());
      this.register(new Clutch());
      this.register(new NoPush());
      this.register(new HotbarSorter());
      this.register(new AirPlace());
      this.register(new AutoBreed());
      this.register(new AutoNametag());
      this.register(new AutoShearer());
      this.register(new LiquidFiller());
      this.register(new SpawnProofer());
      this.register(new VeinMiner());
      this.register(new EndermanLook());
      this.register(new AutoFarm());
      this.register(new ChestAura());
      this.register(new Flamethrower());
      this.register(new AutoSign());
      this.register(new Collisions());
      this.register(new AutoTorch());
      this.register(new Fucker());
      this.register(new BedDefender());
      this.register(new BlockIn());
      this.register(new Extinguish());
      this.register(new PacketMine());
      this.register(new StrongholdFinder());
      this.register(new LawnMower());
      this.register(new WorldRules());
      this.register(new TickFreeze());
      this.register(new Macro(1));
      this.register(new Macro(2));
      this.register(new Macro(3));
      this.register(new InventorySorter());
      this.register(new DurabilityAlert());
      this.register(new AutoHeal());
      this.register(new Kits());
      this.register(new MobSweeper());
      this.register(new Summoner());
      this.register(new AntiLag());
      this.register(new Panic());
      this.register(new BetterChat());
      this.register(new SoundBlocker());
      this.register(new XCarry());
      this.register(new PacketLogger());
      this.register(new NameProtect());
      this.register(new FlagCheck());
      this.register(new PortalMenu());
      this.register(new DeathCoords());
      this.register(new Presets());
      this.register(new InfernoBypass());
      this.register(new AutoGG());
      this.register(new Notifier());
      this.register(new Teams());
      this.register(new Derp());
      this.register(new BaritonePause());
      this.register(new BaritoneStatus());
      this.register(new AutoMineBot());
      this.register(new AutoGoto());
      this.register(new FollowBot());
      this.register(new AutoTunnel());
      this.register(new AutoExplore());
      this.register(new AutoExcavate());
      this.register(new AutoHighway());
      this.register(new PathRenderer());
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
         this.bus.register(module);
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
      BlockBreaking.tick();

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
         Hands.beginTick();
         this.pollKeybinds(client, client.gui.screen() == null);
         this.bus.tick();
         this.syncBaritonePause();
         BaritoneBridge.tick();
         RotationManager.endTick();
         SelfTest.tick(this);
      }
   }

   private void syncBaritonePause() {
      if (this.bus.wantsBaritonePause()) {
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
      BaritoneBridge.stopAll();
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
         if (Menus.isMenu(client.gui.screen())) {
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
               boolean pressed = InputConstants.isKeyDown(key);
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
         this.previousKeyState.put(key, InputConstants.isKeyDown(key));
      }

      this.previousKeyState.keySet().removeIf(keyx -> !seen.contains(keyx));
   }

   public record Failure(String module, String hook, String error) {
   }
}
