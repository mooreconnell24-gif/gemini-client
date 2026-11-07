package com.inferno.fabric.checks;

import com.inferno.fabric.Alerts;
import com.inferno.fabric.InfernoConfig;
import com.inferno.fabric.InfernoMod;
import com.inferno.fabric.admin.AdminService;
import com.inferno.fabric.admin.ExemptStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

public final class CheckEngine {
   private final List<Check> checks = new ArrayList<>();
   private final TamperChecks.CommandInjection commandInjection = new TamperChecks.CommandInjection();
   private final Map<String, PlayerState> states = new ConcurrentHashMap<>();
   private long lastTick = System.currentTimeMillis();
   private long lagUntil;
   private long tickStart = System.currentTimeMillis();
   private int ticks;
   private final Random random = new Random();

   public CheckEngine() {
      this.checks.add(new CombatChecks.Reach());
      this.checks.add(new CombatChecks.Hitbox());
      this.checks.add(new CombatChecks.KillAuraMulti());
      this.checks.add(new CombatChecks.AutoClicker());
      this.checks.add(new CombatChecks.AimGcd());
      this.checks.add(new CombatChecks.AimSnap());
      this.checks.add(new MoreChecks.AimSilent());
      this.checks.add(new MoreChecks.AimPattern());
      this.checks.add(new CombatChecks2.NoSwing());
      this.checks.add(new CombatChecks2.KillAuraWalls());
      this.checks.add(new CombatChecks2.ClickConsistency());
      this.checks.add(new WorldChecks2.Criticals());
      this.checks.add(new KnockbackChecks.Velocity());
      this.checks.add(new ExtraChecks.AutoTotem());
      this.checks.add(new MovementChecks.Fly());
      this.checks.add(new MovementChecks.FlyGravity());
      this.checks.add(new MovementChecks.Speed());
      this.checks.add(new MovementChecks.SpeedAir());
      this.checks.add(new CombatChecks2.SpeedAverage());
      this.checks.add(new PredictionChecks.SpeedPrediction());
      this.checks.add(new PredictionChecks.FlyPrediction());
      this.checks.add(new MovementChecks.Timer());
      this.checks.add(new MovementChecks.Step());
      this.checks.add(new MovementChecks.NoFall());
      this.checks.add(new MovementChecks.Spider());
      this.checks.add(new MovementChecks.Jesus());
      this.checks.add(new MoreChecks.NoSlow());
      this.checks.add(new MoreChecks.OmniSprint());
      this.checks.add(new MoreChecks.FastClimb());
      this.checks.add(new MoreChecks.Phase());
      this.checks.add(new ExtraChecks.NoWeb());
      this.checks.add(new ExtraChecks.BadPackets());
      this.checks.add(new WorldChecks.ScaffoldFace());
      this.checks.add(new WorldChecks.FastPlace());
      this.checks.add(new WorldChecks.BlockReach());
      this.checks.add(new WorldChecks.Nuker());
      this.checks.add(new MoreChecks.Tower());
      this.checks.add(new WorldChecks2.AirPlace());
      this.checks.add(new WorldChecks2.XRay());
      this.checks.add(new ExtraChecks.NoSwingMining());
      this.checks.add(new ExtraChecks.InteractWalls());
      this.checks.add(new InventoryChecks.InvMove());
      this.checks.add(new InventoryChecks.FastClick());
      this.checks.add(new ExtraChecks.AutoFish());
      this.checks.add(new TamperChecks.FlightPermission());
      this.checks.add(new TamperChecks.AttributeTamper());
      this.checks.add(this.commandInjection);
      this.checks.add(new NewChecks.InvalidPitch());
      this.checks.add(new NewChecks.AimSmooth());
      this.checks.add(new NewChecks.Regen());
      this.checks.add(new NewChecks.FastUse());
      this.checks.add(new NewChecks.MultiAngle());
      this.checks.add(new NewChecks.GroundSpoof());
      this.checks.add(new NewChecks.Strafe());
      this.checks.add(new NewChecks.Blink());
      this.checks.add(new NewChecks.BoatFly());
      this.checks.add(new NewChecks.ElytraSpeed());
      this.checks.add(new NewChecks.InvalidMotion());
      this.checks.add(new NewChecks.FastBreak());
      this.checks.add(new NewChecks.InventoryClean());
      this.checks.add(new NewChecks.VehicleSteer());
      this.checks.add(new MaxSecurityChecks.PacketFly());
      this.checks.add(new MaxSecurityChecks.Clip());
      this.checks.add(new MaxSecurityChecks.MotionA());
      this.checks.add(new MaxSecurityChecks.Simulation());
      this.checks.add(new MaxSecurityChecks.KillAuraAngle());
      this.checks.add(new MaxSecurityChecks.KillAuraKeepSprint());
      this.checks.add(new MaxSecurityChecks.AutoBlock());
      this.checks.add(new MaxSecurityChecks.ScaffoldRot());
      this.checks.add(new MaxSecurityChecks.TimerB());
      this.checks.add(new MaxSecurityChecks.PingSpoof());
      this.checks.add(new MaxSecurityChecks.CriticalsB());
      // LiquidBounce coverage (see LiquidBounceCoverage for the module -> check map), grouped by category
      // so the panel shows one header per group.
      this.checks.add(new LbCombatChecks.CrystalAura());
      this.checks.add(new LbCombatChecks.MaceKill());
      this.checks.add(new LbCombatChecks.AutoBow());
      this.checks.add(new LbCombatChecks.TickAttacks());
      this.checks.add(new LbCombatChecks.SprintReset());
      this.checks.add(new LbMovementChecks.HighJump());
      this.checks.add(new LbMovementChecks.ReverseStep());
      this.checks.add(new LbMovementChecks.JumpDelay());
      this.checks.add(new LbMovementChecks.JumpInput());
      this.checks.add(new LbMovementChecks.InputMove());
      this.checks.add(new LbMovementChecks.SprintInvalid());
      this.checks.add(new LbMovementChecks.SneakSpeed());
      this.checks.add(new LbMovementChecks.SafeWalk());
      this.checks.add(new LbMovementChecks.LiquidSpeed());
      this.checks.add(new LbMovementChecks.AntiLevitation());
      this.checks.add(new LbMovementChecks.ElytraFly());
      this.checks.add(new LbMovementChecks.RiptideSpeed());
      this.checks.add(new LbMovementChecks.VehicleSpeed());
      this.checks.add(new LbWorldChecks.BreakAim());
      this.checks.add(new LbWorldChecks.BreakProgress());
      this.checks.add(new LbWorldChecks.LiquidPlace());
      this.checks.add(new LbWorldChecks.TickInteract());
      this.checks.add(new LbPlayerChecks.HotbarSwap());
      this.checks.add(new LbPlayerChecks.InventoryInput());
      this.checks.add(new LbPlayerChecks.InventoryBot());
      this.checks.add(new LbPlayerChecks.MoreCarry());
      this.checks.add(new LbPlayerChecks.AutoRespawn());
      this.checks.add(new LbExploitChecks.MultiMove());
      this.checks.add(new LbExploitChecks.TickTimer());
      this.checks.add(new LbExploitChecks.DigPackets());
      this.checks.add(new LbExploitChecks.PacketFlood());
      this.checks.add(new LbExploitChecks.BadState());
      this.checks.add(new LbExploitChecks.ClientBrand());
      this.checks.add(new LbExploitChecks.BookSpam());
      this.checks.add(new LbExploitChecks.ChatSpam());
   }

   public List<Check> all() {
      return Collections.unmodifiableList(this.checks);
   }

   public PlayerState state(ServerPlayer p) {
      return this.states.computeIfAbsent(p.getStringUUID(), k -> new PlayerState());
   }

   public void join(ServerPlayer p) {
      PlayerState s = new PlayerState();
      s.exempt(4000L);
      this.states.put(p.getStringUUID(), s);
      if (!ExemptStore.isExempt(p.getStringUUID())) {
         for (Check c : this.checks) {
            if (c.enabled()) {
               c.onJoin(p, s);
            }
         }
      }
   }

   public void leave(ServerPlayer p) {
      this.states.remove(p.getStringUUID());
   }

   public void reset() {
      this.states.clear();
   }

   public boolean serverLagging() {
      return System.currentTimeMillis() < this.lagUntil;
   }

   public void startTick(MinecraftServer server) {
      this.tickStart = System.currentTimeMillis();
   }

   public long tickStart() {
      return this.tickStart;
   }

   public void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      if (now - this.lastTick > 150L) {
         this.lagUntil = now + 3000L;
      }

      this.lastTick = now;
      if (++this.ticks % 400 == 0) {
         Alerts.decay();
      }

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         this.tickPlayer(p);
      }
   }

   /** Per-player part of the server tick (public so players outside the player list can be ticked too). */
   public void tickPlayer(ServerPlayer p) {
      if (!ExemptStore.isExempt(p.getStringUUID())) {
         PlayerState s = this.state(p);
         pollScreen(p, s);

         for (Check c : this.checks) {
            if (c.enabled()) {
               c.onTick(p, s);
            }
         }
      }
   }

   public void beforeMove(ServerPlayer p, boolean look, float pitch, boolean hasPos, double x, double y, double z) {
      PlayerState s = this.state(p);
      s.rawHasPos = hasPos;
      s.rawX = x;
      s.rawY = y;
      s.rawZ = z;
      s.rawLook = look;
      s.rawPitch = pitch;
      s.fromX = p.getX();
      s.fromY = p.getY();
      s.fromZ = p.getZ();
      s.fromYaw = p.getYRot();
      s.fromPitch = p.getXRot();
      s.tick.moves++;
   }

   public void afterMove(ServerPlayer p, boolean clientGround) {
      if (!ExemptStore.isExempt(p.getStringUUID()) && !AdminService.isFrozen(p.getStringUUID())) {
         PlayerState s = this.state(p);
         ServerLevel w = p.level();
         AABB box = p.getBoundingBox();
         boolean ground = !w.noCollision(p, box.expandTowards(0.0, -1.01, 0.0));
         if (Enforcement.applyLock(p, s, ground)) {
            s.lastGround = ground;
            s.airTicks = ground ? 0 : s.airTicks + 1;
         } else {
            InfernoConfig cfg = InfernoConfig.get();
            boolean special = special(p);
            if (special) {
               s.exempt(cfg.strictMode ? 400L : 1500L);
            }

            if (p.hurtTime > 0) {
               s.exempt(cfg.strictMode ? 250L : 1000L);
            }

            if (blocksNear(w, box.expandTowards(0.0, -2.0, 0.0), "slime", "_bed", "honey")) {
               s.exempt(cfg.strictMode ? 800L : 2500L);
            }

            Move m = new Move(
               s,
               p.getX(),
               p.getY(),
               p.getZ(),
               p.getYRot(),
               p.getXRot(),
               ground,
               clientGround,
               special,
               s.exempt() || this.serverLagging(),
               p.isInWater() || p.isInLava() || blocksNear(w, box.inflate(0.1), "water", "lava", "bubble"),
               p.onClimbable(),
               blocksNear(w, box.inflate(0.1), "cobweb", "powder_snow", "honey", "sweet_berry", "scaffolding"),
               blocksNear(w, box.expandTowards(0.0, -2.0, 0.0), "ice"),
               blocksNear(w, box.expandTowards(0.0, -1.0, 0.0), "soul"),
               !w.noCollision(p, box.move(0.0, 0.6, 0.0))
            );

            for (Check c : this.checks) {
               if (c.enabled()) {
                  c.onMove(p, s, m);
               }
            }

            if (m.wantsSetback() && cfg.setbacks) {
               double sx = !Double.isNaN(s.safeX) ? s.safeX : m.fromX;
               double sy = !Double.isNaN(s.safeX) ? s.safeY : m.fromY;
               double sz = !Double.isNaN(s.safeX) ? s.safeZ : m.fromZ;
               p.connection.teleport(sx, sy, sz, p.getYRot(), p.getXRot());
               p.setDeltaMovement(0.0, 0.0, 0.0);
               s.lastSetback = System.currentTimeMillis();
               s.exempt(cfg.strictMode ? 50L : 200L);
               Enforcement.onMovementViolation(p, s, s.lastFlag.isEmpty() ? "movement" : s.lastFlag);
            } else if (ground && !special && m.moved && !m.wantsSetback()) {
               s.safeX = m.toX;
               s.safeY = m.toY;
               s.safeZ = m.toZ;
            }

            s.lastGround = ground;
            s.lastDy = m.dy;
            s.airTicks = ground ? 0 : s.airTicks + 1;
            if (m.moved) {
               s.lastPositionChange = m.time;
               s.lastHorizontal = m.horizontal;
            }

            if (m.rotated) {
               s.addRotation(m.yaw, m.pitch);
            }
         }
      }
   }

   public void packetSent(ServerPlayer p, Packet<?> packet) {
      if (packet instanceof ClientboundSetEntityMotionPacket v) {
         if (v.id() == p.getId()) {
            PlayerState s = this.state(p);
            s.lastVelocity = System.currentTimeMillis();

            for (Check c : this.checks) {
               if (c.enabled()) {
                  c.onVelocity(p, s, v.movement());
               }
            }
         }
      } else if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundExplodePacket) {
         this.state(p).velocityChange = System.currentTimeMillis();
      }
   }

   private static void pollScreen(ServerPlayer p, PlayerState s) {
      boolean open = p.containerMenu != null && p.containerMenu != p.inventoryMenu;
      if (open && !s.containerOpen) {
         s.containerOpenTime = System.currentTimeMillis();
         s.firstClickDone = false;
      }

      s.containerOpen = open;
   }

   public void clickSlot(ServerPlayer p) {
      if (!p.isCreative() && !p.isSpectator()) {
         PlayerState s = this.state(p);
         pollScreen(p, s);
         s.tick.clicks++;

         for (Check c : this.checks) {
            if (c.enabled()) {
               c.onClickSlot(p, s);
            }
         }

         s.lastClick = System.currentTimeMillis();
      }
   }

   public void totemPop(ServerPlayer p) {
      this.state(p).totemPop = System.currentTimeMillis();
   }

   public void digStart(ServerPlayer p, BlockPos pos) {
      if (!p.isCreative() && !p.isSpectator()) {
         PlayerState s = this.state(p);
         s.digPos = pos;
         s.digTime = System.currentTimeMillis();
      }
   }

   public void swing(ServerPlayer p) {
      this.state(p).lastSwing = System.currentTimeMillis();
   }

   public boolean attack(ServerPlayer p, Entity target) {
      if (!p.isCreative() && !p.isSpectator() && !ExemptStore.isExempt(p.getStringUUID())) {
         PlayerState s = this.state(p);
         if (!Enforcement.locked(s) && !AdminService.isFrozen(p.getStringUUID())) {
            boolean cancel = false;
            s.tick.attacks++;
            s.tick.attacksPerTarget.merge(target.getId(), 1, Integer::sum);

            for (Check c : this.checks) {
               if (c.enabled() && c.onAttack(p, s, target)) {
                  cancel = true;
               }
            }

            s.lastAttack = System.currentTimeMillis();
            s.lastTarget = target.getStringUUID();
            if (cancel && InfernoConfig.get().cancelHits) {
               return true;
            }

            double k = this.mitigation(p.getStringUUID());
            return k > 0.0 && this.random.nextDouble() < k * 0.5;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public int combatSuspicion(String uuid) {
      double score = 0.0;
      int limit = Math.max(1, InfernoConfig.get().kickVl);

      for (Entry<String, Integer> e : Alerts.violations(uuid).entrySet()) {
         if (!e.getKey().equals("aim-gcd")) {
            for (Check c : this.checks) {
               if (c.id().equals(e.getKey()) && c.category() == Check.Category.COMBAT) {
                  score += (double)e.getValue().intValue() / limit;
               }
            }
         }
      }

      return (int)Math.min(100L, Math.round(score * 100.0));
   }

   public double mitigation(String uuid) {
      if (!InfernoConfig.get().combatMitigation) {
         return 0.0;
      }

      PlayerState s = this.states.get(uuid);
      if (s != null && Enforcement.locked(s)) {
         return 1.0;
      }

      int sus = this.combatSuspicion(uuid);
      double floor = InfernoConfig.get().strictMode ? 15.0 : 25.0;
      return sus < floor ? 0.0 : Math.min(1.0, 0.35 + 0.65 * (sus - floor) / (100.0 - floor));
   }

   public void command(ServerPlayer p, String command) {
      this.commandInjection.onCommand(p, command, TamperChecks.typedByPlayer());
   }

   public void useItem(ServerPlayer p, InteractionHand hand) {
      if (!p.isCreative() && !p.isSpectator()) {
         PlayerState s = this.state(p);
         s.tick.uses++;
         if (p.getItemInHand(hand).is(Items.FIREWORK_ROCKET)) {
            s.lastFireworkUse = System.currentTimeMillis();
         }

         for (Check c : this.checks) {
            if (c.enabled()) {
               c.onUseItem(p, s, hand);
            }
         }

         s.lastItemUse = System.currentTimeMillis();
      }
   }

   public boolean useBlock(ServerPlayer p, BlockHitResult hit) {
      if (!p.isCreative() && !p.isSpectator()) {
         PlayerState s = this.state(p);
         boolean cancel = false;

         for (Check c : this.checks) {
            if (c.enabled() && c.onUseBlock(p, s, hit)) {
               cancel = true;
            }
         }

         return cancel && InfernoConfig.get().cancelBlocks;
      } else {
         return false;
      }
   }

   public boolean place(ServerPlayer p, BlockHitResult hit) {
      if (p.isCreative()) {
         return false;
      }

      PlayerState s = this.state(p);
      boolean cancel = false;
      s.tick.places++;

      for (Check c : this.checks) {
         if (c.enabled() && c.onPlace(p, s, hit)) {
            cancel = true;
         }
      }

      s.lastBlockPlace = System.currentTimeMillis();

      return cancel && InfernoConfig.get().cancelBlocks;
   }

   public boolean breakBlock(ServerPlayer p, BlockPos pos) {
      if (p.isCreative()) {
         return false;
      }

      PlayerState s = this.state(p);
      boolean cancel = false;

      for (Check c : this.checks) {
         if (c.enabled() && c.onBreak(p, s, pos)) {
            cancel = true;
         }
      }

      return cancel && InfernoConfig.get().cancelBlocks;
   }

   private boolean watched(ServerPlayer p) {
      return !ExemptStore.isExempt(p.getStringUUID()) && !AdminService.isFrozen(p.getStringUUID());
   }

   public void input(ServerPlayer p, Input before, Input now) {
      if (this.watched(p)) {
         PlayerState s = this.state(p);
         long time = System.currentTimeMillis();

         for (Check c : this.checks) {
            if (c.enabled()) {
               c.onInput(p, s, before, now);
            }
         }

         if (!now.equals(before)) {
            s.lastInputChange = time;
         }

         if (now.jump()) {
            s.lastJumpInput = time;
         }
      }
   }

   public void playerAction(ServerPlayer p, ServerboundPlayerActionPacket.Action action, BlockPos pos) {
      if (this.watched(p)) {
         PlayerState s = this.state(p);
         if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            s.tick.digStarts++;
         }

         for (Check c : this.checks) {
            if (c.enabled()) {
               c.onAction(p, s, action, pos);
            }
         }
      }
   }

   public void playerCommand(ServerPlayer p, ServerboundPlayerCommandPacket.Action action) {
      if (this.watched(p)) {
         PlayerState s = this.state(p);
         if (action == ServerboundPlayerCommandPacket.Action.START_SPRINTING || action == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
            s.tick.sprintCommands++;
         }

         for (Check c : this.checks) {
            if (c.enabled()) {
               c.onPlayerCommand(p, s, action);
            }
         }
      }
   }

   public void carriedSlot(ServerPlayer p, int from, int to) {
      if (this.watched(p)) {
         PlayerState s = this.state(p);
         s.tick.slotChanges++;

         for (Check c : this.checks) {
            if (c.enabled()) {
               c.onCarriedSlot(p, s, from, to);
            }
         }
      }
   }

   public void teleportAccepted(ServerPlayer p) {
      PlayerState s = this.state(p);
      s.lastTeleportAccept = System.currentTimeMillis();
      s.tick.teleportAccepted = true;
   }

   public void clientTickEnd(ServerPlayer p) {
      PlayerState s = this.state(p);
      long now = System.currentTimeMillis();
      if (this.watched(p) && s.lastTickEnd != 0L) {
         for (Check c : this.checks) {
            if (c.enabled()) {
               c.onClientTick(p, s, s.tick);
            }
         }
      }

      s.lastTickEnd = now;
      s.clientTicks++;
      s.tick.reset(now);
   }

   public void respawnRequest(ServerPlayer p) {
      if (this.watched(p)) {
         PlayerState s = this.state(p);

         for (Check c : this.checks) {
            if (c.enabled()) {
               c.onRespawnRequest(p, s);
            }
         }
      }
   }

   public void death(ServerPlayer p) {
      this.state(p).deathTime = System.currentTimeMillis();
   }

   public void respawned(ServerPlayer p) {
      this.state(p).deathTime = 0L;
   }

   public void bookEdit(ServerPlayer p, int pages) {
      if (this.watched(p)) {
         PlayerState s = this.state(p);

         for (Check c : this.checks) {
            if (c.enabled()) {
               c.onBookEdit(p, s, pages);
            }
         }
      }
   }

   public void chat(ServerPlayer p, String message) {
      if (this.watched(p)) {
         PlayerState s = this.state(p);

         for (Check c : this.checks) {
            if (c.enabled()) {
               c.onChat(p, s, message);
            }
         }
      }
   }

   public static boolean special(ServerPlayer p) {
      if (!p.isCreative() && !p.isSpectator() && !p.isFallFlying() && !p.isPassenger() && !p.isAutoSpinAttack() && !p.isSleeping() && !p.isDeadOrDying()) {
         for (MobEffectInstance effect : p.getActiveEffects()) {
            String e = effect.getDescriptionId().toLowerCase(Locale.ROOT);
            if (e.contains("speed") || e.contains("jump_boost") || e.contains("levitation") || e.contains("slow_falling") || e.contains("dolphins_grace")) {
               return true;
            }
         }

         return nonVanillaPhysics(p);
      } else {
         return true;
      }
   }

   public String notCheckedReason(ServerPlayer p) {
      if (p.isCreative()) {
         return "you are in Creative mode";
      }

      if (p.isSpectator()) {
         return "you are in Spectator mode";
      }

      if (p.isFallFlying()) {
         return "you are gliding with an elytra";
      }

      if (p.isPassenger()) {
         return "you are riding something";
      }

      if (p.isAutoSpinAttack()) {
         return "riptide";
      }

      if (!p.isSleeping() && !p.isDeadOrDying()) {
         for (MobEffectInstance effect : p.getActiveEffects()) {
            String e = effect.getDescriptionId().toLowerCase(Locale.ROOT);
            if (e.contains("speed") || e.contains("jump_boost") || e.contains("levitation") || e.contains("slow_falling") || e.contains("dolphins_grace")) {
               return "a movement effect (" + e.replace("effect.minecraft.", "") + ")";
            }
         }

         if (nonVanillaPhysics(p)) {
            return "a changed movement attribute (speed, gravity, jump, step, size, friction or drag)";
         }

         if (this.serverLagging()) {
            return "the game just lagged";
         }

         PlayerState s = this.states.get(p.getStringUUID());
         return s != null && s.exempt() ? "a short pause after joining, a teleport, knockback or a pull-back" : null;
      } else {
         return "sleeping or dead";
      }
   }

   public static boolean nonVanillaPhysics(ServerPlayer p) {
      double speed = TamperChecks.vanillaValue(p, Attributes.MOVEMENT_SPEED);
      double walking = p.isSprinting() ? speed / 1.3 : speed;
      return off(TamperChecks.vanillaValue(p, Attributes.GRAVITY), 0.08)
         || off(TamperChecks.vanillaValue(p, Attributes.JUMP_STRENGTH), 0.42F)
         || off(TamperChecks.vanillaValue(p, Attributes.STEP_HEIGHT), 0.6)
         || off(TamperChecks.vanillaValue(p, Attributes.SCALE), 1.0)
         || off(TamperChecks.vanillaValue(p, Attributes.FRICTION_MODIFIER), 1.0)
         || off(TamperChecks.vanillaValue(p, Attributes.AIR_DRAG_MODIFIER), 1.0)
         || off(TamperChecks.vanillaValue(p, Attributes.MOVEMENT_EFFICIENCY), 0.0)
         || walking > 0.10010000000000001;
   }

   private static boolean off(double value, double vanilla) {
      return Math.abs(value - vanilla) > 1.0E-4;
   }

   public static String blockId(BlockState state) {
      return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
   }

   public static boolean blocksNear(ServerLevel w, AABB box, String... names) {
      int minX = (int)Math.floor(box.minX);
      int maxX = (int)Math.floor(box.maxX);
      int minY = (int)Math.floor(box.minY);
      int maxY = (int)Math.floor(box.maxY);
      int minZ = (int)Math.floor(box.minZ);
      int maxZ = (int)Math.floor(box.maxZ);

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               String state = blockId(w.getBlockState(new BlockPos(x, y, z)));

               for (String n : names) {
                  if (state.contains(n)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private static double sq(double v) {
      return v * v;
   }

   public static MinecraftServer server() {
      return InfernoMod.server;
   }
}
