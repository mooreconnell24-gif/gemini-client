package gemini.module.modules.combat;

import gemini.event.ActionPriority;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.Targeting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.BallisticSolver;
import gemini.util.ClientState;
import gemini.util.InfoText;
import gemini.util.MoveUtil;
import gemini.util.RotationManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Aims bows, crossbows and tridents with the game's own projectile physics (see {@link BallisticSolver}): drag and
 * gravity per tick, the bow's draw power, the motion the shooter gives the projectile, and the target's motion over
 * the flight. The chosen arc is replayed against the blocks so targets behind cover are skipped, and the arc is drawn.
 */
public final class BowAimbot extends Module {
   private static final int MAX_TICKS = 300;
   private static final double[] AUTO_HEIGHTS = {0.92, 0.78, 0.64, 0.5, 0.36, 0.22, 0.1};
   private static final int CROSSBOW = 0;
   private static final int BOW = 1;
   private static final int TRIDENT = 2;

   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max target distance in blocks", 45.0, 10.0, 120.0, 2.0));
   private final BooleanSetting silent = this.addSetting(new BooleanSetting("Silent Aim", "Aim in packets only: the shot goes where the aimbot points while your camera stays put", true));
   private final BooleanSetting predict = this.addSetting(
      new BooleanSetting("Predict Motion", "Lead moving targets: aim where the target will be when the projectile arrives", true)
   );
   private final ModeSetting aimPoint = this.addSetting(
      new ModeSetting(
         "Aim Point",
         "Where on the target to aim. Auto picks the highest point of the hit box the shot can reach without hitting a block",
         "Auto",
         "Head",
         "Chest",
         "Feet",
         "Auto"
      )
   );
   private final ModeSetting arc = this.addSetting(
      new ModeSetting("Arc", "Low: the flat, fast arc. High: the lob over cover. Auto: the low arc unless blocks are in the way, then the high one", "Auto", "Low", "High", "Auto")
   );
   private final BooleanSetting wallCheck = this.addSetting(
      new BooleanSetting("Wall Check", "Replay the arc against the blocks and skip targets the shot cannot reach", true)
   );
   private final BooleanSetting showPath = this.addSetting(new BooleanSetting("Show Trajectory", "Draw the predicted flight path and where it lands", true));
   private final ColorSetting pathColor = this.addSetting(new ColorSetting("Trajectory Color", "Colour of the predicted flight path and impact marker", 190).showIf(this.showPath::get));
   private final BooleanSetting autoRelease = this.addSetting(
      new BooleanSetting("Auto Release", "Let go of a bow by itself once it is fully drawn and aimed within the tolerance", false)
   );
   private final NumberSetting tolerance = this.addSetting(
      new NumberSetting("Aim Tolerance", "How close (degrees) the aim must be to the solution, two ticks running, before Auto Release fires", 1.0, 0.2, 5.0, 0.1)
         .showIf(this.autoRelease::get)
   );
   private final BooleanSetting autoCharge = this.addSetting(
      new BooleanSetting("Auto Charge", "Start drawing the bow in your hand by itself whenever a target can be hit", false).showIf(this.autoRelease::get)
   );
   private final Targeting targeting = Targeting.builder(this).box("Outline", 190).build();

   private final BallisticSolver.Solution solution = new BallisticSolver.Solution();
   private final BallisticSolver.Solution scratch = new BallisticSolver.Solution();
   private final double[] path = new double[(MAX_TICKS + 2) * 3];
   private final double[] scratchPath = new double[(MAX_TICKS + 2) * 3];
   private final double[] point = new double[3];
   private final InfoText infoText = new InfoText();
   private int pathCount;
   private boolean hasShot;
   private double impactX;
   private double impactY;
   private double impactZ;
   private boolean aimedLastTick;
   private boolean forcingUse;

   public BowAimbot() {
      super("Bow Aimbot", "Aims bows, crossbows and tridents with real arrow physics: picks the target, leads it, avoids cover and shows the arc.", Category.COMBAT);
   }

   /** Found by its old name in search and as {@code .projectileaimbot}. */
   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("ProjectileAimbot");
      names.add("Projectile Aimbot");
      return names;
   }

   @Override
   public String info() {
      return this.hasShot ? this.infoText.fixed(this.solution.ticks / 20.0, 1, "s") : this.aimPoint.get();
   }

   @Override
   public void onDisable() {
      this.reset();
      this.releaseForcedUse(Minecraft.getInstance());
   }

   @Override
   public void onWorldChange() {
      this.reset();
      this.forcingUse = false;
   }

   private void reset() {
      this.targeting.clear();
      this.hasShot = false;
      this.pathCount = 0;
      this.aimedLastTick = false;
   }

   private void releaseForcedUse(Minecraft client) {
      if (this.forcingUse) {
         this.forcingUse = false;
         client.options.keyUse.setDown(MoveUtil.physicalKeyDown(client.options.keyUse));
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      this.hasShot = false;
      if (!ClientState.ready(client) || client.gui.screen() != null) {
         this.aimedLastTick = false;
         this.releaseForcedUse(client);
         this.targeting.clear();
         return;
      }

      LocalPlayer player = client.player;
      ItemStack using = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
      boolean usingBow = using.getItem() instanceof BowItem;
      boolean usingTrident = using.is(Items.TRIDENT);
      boolean crossbow = !player.isUsingItem() && (arrowCrossbow(player.getMainHandItem()) || arrowCrossbow(player.getOffhandItem()));
      boolean bowReady = !player.isUsingItem() && this.autoRelease.get() && this.autoCharge.get() && this.canDraw(player);
      int weapon;
      double speed;
      if (usingBow) {
         weapon = BOW;
         // With Auto Release the shot leaves at full draw; otherwise aim for the draw held right now.
         speed = this.autoRelease.get() ? BallisticSolver.BOW_SPEED : Math.max(0.1, BallisticSolver.bowPower(player.getTicksUsingItem())) * BallisticSolver.BOW_SPEED;
      } else if (usingTrident) {
         weapon = TRIDENT;
         speed = BallisticSolver.TRIDENT_SPEED;
      } else if (crossbow) {
         weapon = CROSSBOW;
         speed = BallisticSolver.CROSSBOW_SPEED;
      } else if (bowReady) {
         weapon = BOW;
         speed = BallisticSolver.BOW_SPEED;
      } else {
         this.aimedLastTick = false;
         this.releaseForcedUse(client);
         this.targeting.clear();
         return;
      }

      // Bows and tridents are launched with shootFromRotation, which adds the shooter's movement; crossbows are not.
      Vec3 known = player.getKnownMovement();
      double wx = weapon == CROSSBOW ? 0.0 : known.x;
      double wy = weapon == CROSSBOW || player.onGround() ? 0.0 : known.y;
      double wz = weapon == CROSSBOW ? 0.0 : known.z;
      double lx = player.getX();
      double ly = player.getEyeY() - BallisticSolver.LAUNCH_DROP;
      double lz = player.getZ();
      ClientLevel level = client.level;
      LivingEntity target = this.targeting
         .pick(player, level.entitiesForRendering(), this.range.get(), candidate -> !this.wallCheck.get() || this.plan(level, player, candidate, lx, ly, lz, wx, wy, wz, speed, this.scratch, this.scratchPath) >= 0);
      if (target == null || this.plan(level, player, target, lx, ly, lz, wx, wy, wz, speed, this.solution, this.path) < 0) {
         this.aimedLastTick = false;
         this.releaseForcedUse(client);
         return;
      }

      this.hasShot = true;
      float tol = this.autoRelease.get() ? this.tolerance.floatValue() : 2.5F;
      boolean aimed = RotationManager.request(
         player, Mth.wrapDegrees(this.solution.yaw), Mth.clamp(this.solution.pitch, -90.0F, 90.0F), 95.0F, this.silent.get(), ActionPriority.COMBAT, tol
      );
      if (this.autoRelease.get()) {
         this.handleRelease(client, player, usingBow, bowReady, aimed);
      }

      this.aimedLastTick = aimed;
   }

   /**
    * Auto Charge holds the use key while a target can be hit; Auto Release lets go once the bow is fully drawn and the
    * aim has been on the solution for this tick and the last (the server shoots along the rotation it last received).
    */
   private void handleRelease(Minecraft client, LocalPlayer player, boolean usingBow, boolean bowReady, boolean aimed) {
      if (this.autoCharge.get() && (bowReady || usingBow && this.forcingUse)) {
         this.forcingUse = true;
         client.options.keyUse.setDown(true);
      }

      if (usingBow && player.getTicksUsingItem() >= 20 && aimed && this.aimedLastTick) {
         client.gameMode.releaseUsingItem(player);
         if (!this.autoCharge.get()) {
            this.releaseForcedUse(client);
         }
      }
   }

   /** Holding a bow in the main hand (the use key tries it first) with ammunition, or in creative. */
   private boolean canDraw(LocalPlayer player) {
      ItemStack main = player.getMainHandItem();
      return main.getItem() instanceof BowItem && (player.hasInfiniteMaterials() || !player.getProjectile(main).isEmpty());
   }

   /** A loaded crossbow that fires arrows (fireworks fly differently and are left alone). */
   private static boolean arrowCrossbow(ItemStack stack) {
      if (!(stack.getItem() instanceof CrossbowItem) || !CrossbowItem.isCharged(stack)) {
         return false;
      }

      ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
      return charged == null || !charged.contains(Items.FIREWORK_ROCKET);
   }

   /**
    * Solves a shot at {@code target} into {@code out} and its path into {@code pathOut}; returns the number of path
    * points, or -1 when there is no shot (out of range, or every option is blocked while Wall Check is on). Aim Point
    * Auto walks down the hit box from the top and takes the first height with a clear arc.
    */
   private int plan(
      ClientLevel level,
      LocalPlayer player,
      LivingEntity target,
      double lx,
      double ly,
      double lz,
      double wx,
      double wy,
      double wz,
      double speed,
      BallisticSolver.Solution out,
      double[] pathOut
   ) {
      double height = target.getBbHeight();
      double mx = 0.0;
      double my = 0.0;
      double mz = 0.0;
      if (this.predict.get()) {
         mx = target.getX() - target.xo;
         my = target.onGround() ? 0.0 : target.getY() - target.yo;
         mz = target.getZ() - target.zo;
      }

      boolean auto = this.aimPoint.is("Auto");
      int heights = auto ? AUTO_HEIGHTS.length : 1;
      for (int i = 0; i < heights; i++) {
         double fraction;
         if (auto) {
            fraction = AUTO_HEIGHTS[i];
         } else if (this.aimPoint.is("Head")) {
            fraction = height <= 0.0 ? 0.9 : Math.min(0.95, target.getEyeHeight() / height);
         } else if (this.aimPoint.is("Feet")) {
            fraction = 0.12;
         } else {
            fraction = 0.62;
         }

         double px = target.getX() - lx;
         double py = target.getY() + height * fraction - ly;
         double pz = target.getZ() - lz;
         int count = this.tryArcs(level, player, px, py, pz, mx, my, mz, lx, ly, lz, wx, wy, wz, speed, out, pathOut, auto || this.wallCheck.get());
         if (count >= 0) {
            return count;
         }
      }

      if (auto && !this.wallCheck.get()) {
         // Nothing is clear, but the target is not skipped: aim at the chest.
         double px = target.getX() - lx;
         double py = target.getY() + height * 0.62 - ly;
         double pz = target.getZ() - lz;
         return this.tryArcs(level, player, px, py, pz, mx, my, mz, lx, ly, lz, wx, wy, wz, speed, out, pathOut, false);
      }

      return -1;
   }

   /** Low, high or (Auto) low then high; with {@code needClear} an arc that hits a block first does not count. */
   private int tryArcs(
      ClientLevel level,
      LocalPlayer player,
      double px,
      double py,
      double pz,
      double mx,
      double my,
      double mz,
      double lx,
      double ly,
      double lz,
      double wx,
      double wy,
      double wz,
      double speed,
      BallisticSolver.Solution out,
      double[] pathOut,
      boolean needClear
   ) {
      boolean auto = this.arc.is("Auto");
      int first = this.arc.is("High") ? BallisticSolver.HIGH : BallisticSolver.LOW;
      for (int attempt = 0; attempt < (auto ? 2 : 1); attempt++) {
         int which = attempt == 0 ? first : BallisticSolver.HIGH;
         if (!BallisticSolver.solve(px, py, pz, mx, my, mz, wx, wy, wz, speed, BallisticSolver.ARROW_GRAVITY, BallisticSolver.ARROW_DRAG, which, MAX_TICKS, out)) {
            continue;
         }

         int count = this.trace(level, player, out, speed, lx, ly, lz, wx, wy, wz, pathOut);
         if (count > 0 || !needClear) {
            return Math.abs(count);
         }
      }

      return -1;
   }

   /**
    * Replays the solved shot tick by tick, clipping every step against the blocks. Writes the path (ending at the
    * impact point) and returns its point count, negated when a block is hit before the target.
    */
   private int trace(
      ClientLevel level, LocalPlayer player, BallisticSolver.Solution shot, double speed, double lx, double ly, double lz, double wx, double wy, double wz, double[] pathOut
   ) {
      double ux = shot.dirX * speed + wx;
      double uy = shot.dirY * speed + wy;
      double uz = shot.dirZ * speed + wz;
      int whole = (int)Math.floor(shot.ticks);
      int count = BallisticSolver.simulate(lx, ly, lz, ux, uy, uz, BallisticSolver.ARROW_GRAVITY, BallisticSolver.ARROW_DRAG, whole + 1, pathOut);
      BallisticSolver.positionAt(ux, uy, uz, BallisticSolver.ARROW_GRAVITY, BallisticSolver.ARROW_DRAG, shot.ticks, this.point);
      int last = Math.min(count, pathOut.length / 3 - 1);
      pathOut[last * 3] = lx + this.point[0];
      pathOut[last * 3 + 1] = ly + this.point[1];
      pathOut[last * 3 + 2] = lz + this.point[2];
      count = last + 1;
      for (int i = 1; i < count; i++) {
         Vec3 from = new Vec3(pathOut[(i - 1) * 3], pathOut[(i - 1) * 3 + 1], pathOut[(i - 1) * 3 + 2]);
         Vec3 to = new Vec3(pathOut[i * 3], pathOut[i * 3 + 1], pathOut[i * 3 + 2]);
         HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
         if (hit.getType() != HitResult.Type.MISS) {
            pathOut[i * 3] = hit.getLocation().x;
            pathOut[i * 3 + 1] = hit.getLocation().y;
            pathOut[i * 3 + 2] = hit.getLocation().z;
            this.recordImpact(pathOut, i);
            return -(i + 1);
         }
      }

      this.recordImpact(pathOut, count - 1);
      return count;
   }

   private void recordImpact(double[] pathOut, int index) {
      if (pathOut == this.path) {
         this.impactX = pathOut[index * 3];
         this.impactY = pathOut[index * 3 + 1];
         this.impactZ = pathOut[index * 3 + 2];
         this.pathCount = index + 1;
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
      if (!this.hasShot || !this.showPath.get() || this.pathCount < 2) {
         return;
      }

      int color = this.pathColor.argb() | 0xFF000000;
      double[] p = this.path;
      for (int i = 1; i < this.pathCount; i++) {
         Render3D.line(p[(i - 1) * 3], p[(i - 1) * 3 + 1], p[(i - 1) * 3 + 2], p[i * 3], p[i * 3 + 1], p[i * 3 + 2], color, 2.0F, false);
      }

      double r = 0.18;
      Render3D.boxArgb(this.impactX - r, this.impactY - r, this.impactZ - r, this.impactX + r, this.impactY + r, this.impactZ + r, color, true, true, 1.5F, 70, true);
   }
}
