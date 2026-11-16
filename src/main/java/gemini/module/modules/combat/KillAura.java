package gemini.module.modules.combat;

import gemini.util.ClientState;
import gemini.util.Swing;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.module.Targeting;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.util.RotationManager;
import gemini.util.Rotations;
import gemini.util.Targets;
import gemini.util.CombatTarget;
import gemini.util.TargetCycle;
import gemini.util.TargetPriority;
import gemini.util.Hands;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class KillAura extends Module {
   private final ModeSetting targetMode = this.addSetting(
      new ModeSetting(
         "Target Mode",
         "Single attacks the best target by Priority (Stick To Target keeps it while it stays valid); Switch moves on to the next target every 6 ticks",
         "Switch",
         "Single",
         "Switch"
      )
   );
   // Lambdas, not method references: the filter settings are declared further down and are read only at pick time.
   private final Targeting targeting = Targeting.builder(this)
      .filters(() -> this.players.get(), () -> this.hostile.get(), () -> this.passive.get(), () -> true)
      .priority(TargetPriority.CLOSEST)
      .stick(true)
      .box("Both", 280)
      .build();
   private final ModeSetting versionMode = this.addSetting(
      new ModeSetting("Version", "Wait for the attack cooldown, or click at a fixed rate", "1.9+ Cooldown", "1.9+ Cooldown", "1.8 Legacy CPS")
   );
   private final NumberSetting legacyCps = this.addSetting(
      new NumberSetting("Legacy CPS", "Clicks per second in 1.8 Legacy CPS mode", 14.0, 4.0, 20.0, 1.0).showIf(() -> this.versionMode.is("1.8 Legacy CPS"))
   );
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Maximum attack reach", 4.2, 2.5, 6.0, 0.1));
   private final NumberSetting wallRange = this.addSetting(new NumberSetting("Wall Range", "Maximum reach through walls", 3.0, 0.0, 6.0, 0.1));
   private final NumberSetting fov = this.addSetting(new NumberSetting("FOV", "Field of view scan cone", 180.0, 30.0, 360.0, 5.0));
   private final NumberSetting turnSpeed = this.addSetting(
      new NumberSetting("Turn Speed", "Rotation speed in degrees per tick", 85.0, 15.0, 180.0, 5.0)
   );
   private final BooleanSetting silentRotate = this.addSetting(new BooleanSetting("Silent Aim", "Rotate in packets without moving camera", true));
   private final BooleanSetting raytrace = this.addSetting(new BooleanSetting("Raytrace Check", "Require clear line-of-sight to target", false));
   private final BooleanSetting autoCrit = this.addSetting(new BooleanSetting("Wait Crit", "Wait for falling window when airborne for crits", true));
   private final BooleanSetting keepSprint = this.addSetting(new BooleanSetting("Keep Sprint", "Maintain sprint state while attacking", true));
   private final BooleanSetting smartSwitch = this.addSetting(
      new BooleanSetting("Smart Switch", "Auto-select best sword or shield-breaking axe", true)
   );
   private final BooleanSetting lagComp = this.addSetting(
      new BooleanSetting("Lag Comp", "Lead moving targets by ping and velocity", true)
   );
   private final NumberSetting predictTicks = this.addSetting(
      new NumberSetting("Predict Ticks", "Base movement ticks to lead target", 2.0, 0.0, 8.0, 0.5).showIf(this.lagComp::get)
   );
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Attack players", true));
   private final BooleanSetting hostile = this.addSetting(new BooleanSetting("Hostile", "Attack hostile mobs", true));
   private final BooleanSetting passive = this.addSetting(new BooleanSetting("Passive", "Attack passive animals", false));
   private final TargetCycle cycle = new TargetCycle();
   private final Predicate<LivingEntity> attackable = this::attackable;
   private final Predicate<LivingEntity> attackableThisRound = living -> !this.cycle.visited(living.getId()) && this.attackable(living);
   private LivingEntity currentTarget;
   private LivingEntity namedTarget;
   private String targetName = "";
   private long lastLegacyAttackMs;
   // Per-tick inputs of the attackable check (set before each pick, so the predicates allocate nothing).
   private LocalPlayer scanPlayer;
   private double scanPrediction;
   private double eyeX;
   private double eyeY;
   private double eyeZ;

   public KillAura() {
      super("KillAura", "Automatically attack nearby mobs and players with smooth aim.", Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.forget();
   }

   private void forget() {
      this.currentTarget = null;
      this.scanPlayer = null;
      this.targeting.clear();
      this.cycle.reset();
      CombatTarget.release(this);
   }

   @Override
   public String info() {
      LivingEntity target = this.currentTarget;
      if (target == null) {
         return this.targetMode.get();
      }

      // The name is looked up once per new target, not every frame.
      if (target != this.namedTarget) {
         this.namedTarget = target;
         this.targetName = target.getName().getString();
      }

      return this.targetName;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         if (!player.isUsingItem()) {
            this.currentTarget = this.chooseTarget(client, player);
            CombatTarget.report(this, this.currentTarget);
            if (this.currentTarget != null) {
               double pred = this.scanPrediction;
               float[] rotation = Rotations.face(player, this.currentTarget, pred);
               boolean aimed = RotationManager.request(player, rotation[0], rotation[1], this.turnSpeed.floatValue(), this.silentRotate.get(), ActionPriority.COMBAT, 8.0F);
               if (aimed) {
                  boolean useLegacy = this.versionMode.is("1.8 Legacy CPS");
                  float cooldownRequired = 0.9F;
                  long nowMs = System.currentTimeMillis();
                  long minDelayMs = (long)Math.max(45.0, 1000.0 / Math.max(1.0, this.legacyCps.get()));
                  boolean readyToHit = useLegacy ? (nowMs - this.lastLegacyAttackMs >= minDelayMs) : (player.getAttackStrengthScale(0.0F) >= cooldownRequired);
                  if (readyToHit) {
                     if (this.autoCrit.get()
                        && !player.onGround()
                        && !player.isInWater()
                        && !player.onClimbable()
                        && player.getDeltaMovement().y > -0.03
                        && player.fallDistance < 1.2F) {
                        return;
                     }

                     this.lastLegacyAttackMs = nowMs;

                     int prevSlot = player.getInventory().getSelectedSlot();
                     if (this.smartSwitch.get()) {
                        int weaponSlot = this.pickSmartWeapon(player, this.currentTarget);
                        if (weaponSlot >= 0 && weaponSlot != prevSlot && Hands.claimMainHand()) {
                           player.getInventory().setSelectedSlot(weaponSlot);
                        }
                     }

                     boolean wasSprinting = player.isSprinting();
                     client.gameMode.attack(player, this.currentTarget);
                     Swing.attack(player, InteractionHand.MAIN_HAND);
                     if (this.keepSprint.get() && wasSprinting && MoveUtil.hasInput(player)) {
                        player.setSprinting(true);
                     }
                  }
               }
            }
         }
      } else {
         this.currentTarget = null;
         this.targeting.clear();
         CombatTarget.release(this);
      }
   }

   private double effectivePredictionTicks(Minecraft client, LocalPlayer player) {
      if (!this.lagComp.get()) {
         return 0.0;
      } else {
         double base = this.predictTicks.get();
         if (client.getConnection() != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(player.getUUID());
            if (info != null && info.getLatency() > 0) {
               base += Mth.clamp(info.getLatency() / 50.0, 0.0, 6.0);
            }
         }

         return base;
      }
   }

   /** Picks this tick's target through the shared Targeting; Switch mode walks the candidates round-robin. */
   private LivingEntity chooseTarget(Minecraft client, LocalPlayer player) {
      this.scanPlayer = player;
      this.scanPrediction = this.effectivePredictionTicks(client, player);
      this.eyeX = player.getX();
      this.eyeY = player.getEyeY();
      this.eyeZ = player.getZ();
      Iterable<Entity> entities = client.level.entitiesForRendering();
      // Reach is measured by the attackable check (lag-compensated, with the wall range), so Targeting's own range is open.
      if (!this.targetMode.is("Switch")) {
         this.cycle.reset();
         return this.targeting.pick(player, entities, Double.POSITIVE_INFINITY, this.attackable);
      }

      LivingEntity previous = this.currentTarget;
      if (this.cycle.tick(6) && previous != null) {
         this.cycle.visit(previous.getId());
      }

      LivingEntity next = this.targeting.pick(player, entities, Double.POSITIVE_INFINITY, this.attackableThisRound);
      if (next == null && this.cycle.midRound()) {
         this.cycle.newRound();
         next = this.targeting.pick(player, entities, Double.POSITIVE_INFINITY, this.attackable);
      }

      return next;
   }

   /** In the FOV cone, within Range (or Wall Range when not visible) of where Lag Comp expects it, and seen if Raytrace Check is on. */
   private boolean attackable(LivingEntity living) {
      LocalPlayer player = this.scanPlayer;
      if (player == null) {
         return false;
      }

      Vec3 motion = living.getDeltaMovement();
      double pred = this.scanPrediction;
      AABB box = living.getBoundingBox();
      double reach = boxDistance(
         this.eyeX, this.eyeY, this.eyeZ,
         box.minX + motion.x * pred, box.minY + motion.y * pred, box.minZ + motion.z * pred,
         box.maxX + motion.x * pred, box.maxY + motion.y * pred, box.maxZ + motion.z * pred
      );
      double maxRange = this.range.get();
      double maxWall = this.wallRange.get();
      if (reach > Math.max(maxRange, maxWall) || !Targets.inFov(player, living, this.fov.get())) {
         return false;
      }

      // Within both ranges the line of sight only matters for Raytrace Check, so skip the trace.
      if (reach <= Math.min(maxRange, maxWall) && !this.raytrace.get()) {
         return true;
      }

      boolean visible = Targets.visible(player, living);
      return (!this.raytrace.get() || visible) && reach <= (visible ? maxRange : maxWall);
   }

   /** Distance from a point to an axis-aligned box (0 inside it). */
   static double boxDistance(double x, double y, double z, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      double dx = Math.max(Math.max(minX - x, 0.0), x - maxX);
      double dy = Math.max(Math.max(minY - y, 0.0), y - maxY);
      double dz = Math.max(Math.max(minZ - z, 0.0), z - maxZ);
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private int pickSmartWeapon(LocalPlayer player, LivingEntity target) {
      if (target instanceof Player enemy && enemy.isBlocking()) {
         int axe = Inventories.findHotbar(player, stackx -> stackx.is(ItemTags.AXES));
         if (axe >= 0) {
            return axe;
         }
      }

      int best = -1;
      double bestScore = 0.0;

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = player.getInventory().getItem(slot);
         if (AutoWeapon.isWeapon(stack)) {
            double score = AutoWeapon.dps(stack);
            if (score > bestScore) {
               bestScore = score;
               best = slot;
            }
         }
      }

      return best;
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.forget();
   }
}
