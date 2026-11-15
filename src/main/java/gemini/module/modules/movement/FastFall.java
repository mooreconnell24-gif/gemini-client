package gemini.module.modules.movement;

import gemini.Gemini;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.modules.world.TimerModule;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.ClientState;
import gemini.util.Holes;
import gemini.util.SpCommands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gets the player to the ground faster. This is Fast Fall and the former ReverseStep in one module, because they
 * did the same thing at different moments: "Falling" speeds up any fall, "Ledges" pulls down the instant the
 * player walks off an edge, "Both" does either. Each keeps the settings it had as a separate module.
 *
 * <p>The ledge pull can be limited to drops that end in a hole, can ease in instead of snapping, and can run the
 * game clock faster for the length of the drop. The clock is put back the moment the pull ends, whatever ends it.
 */
public final class FastFall extends Module {
   /** A pull that has not landed after this many ticks is given up, so the clock can never stay fast. */
   private static final int MAX_PULL_TICKS = 60;
   private final ModeSetting when = this.addSetting(
      new ModeSetting(
         "When", "Falling speeds up any fall; Ledges snaps down the moment you walk off an edge (the old ReverseStep); Both does either", "Falling", "Falling", "Ledges", "Both"
      )
   );
   private final NumberSetting speed = this.addSetting(
      new NumberSetting("Fall Speed", "Downward speed in blocks per tick", 1.0, 0.3, 4.0, 0.1).showIf(() -> !this.when.is("Ledges"))
   );
   private final BooleanSetting onlySafe = this.addSetting(
      new BooleanSetting("Only When Safe", "Only speed up when the whole fall stays under three blocks", true).showIf(() -> !this.when.is("Ledges"))
   );
   private final NumberSetting ledgeHeight = this.addSetting(
      new NumberSetting("Ledge Height", "Largest drop to pull down, in blocks", 3.0, 1.0, 5.0, 0.5).showIf(() -> !this.when.is("Falling"))
   );
   private final NumberSetting ledgeSpeed = this.addSetting(
      new NumberSetting("Ledge Speed", "Downward speed while pulling down a ledge", 1.0, 0.2, 3.0, 0.1).showIf(() -> !this.when.is("Falling"))
   );
   private final ModeSetting pull = this.addSetting(
      new ModeSetting("Pull", "Instant jumps straight to Ledge Speed; Smooth builds up to it over a few ticks", "Instant", "Instant", "Smooth")
         .showIf(() -> !this.when.is("Falling"))
   );
   private final BooleanSetting holesOnly = this.addSetting(
      new BooleanSetting("Holes Only", "Only pull down when the landing spot is a safe hole", false).showIf(() -> !this.when.is("Falling"))
   );
   private final BooleanSetting timer = this.addSetting(
      new BooleanSetting("Timer Boost", "Run the game clock faster while a ledge pull is descending", false).showIf(() -> !this.when.is("Falling"))
   );
   private final NumberSetting timerRate = this.addSetting(
      new NumberSetting("Timer Rate", "Game ticks per second during the pull (20 is normal)", 40.0, 25.0, 100.0, 5.0)
         .showIf(() -> !this.when.is("Falling") && this.timer.get())
   );
   private boolean wasOnGround;
   private boolean pulling;
   private int pullTicks;
   private boolean timerApplied;

   public FastFall() {
      super("Fast Fall", "Drop to the ground faster, in any fall or just off ledges.", Category.MOVEMENT);
   }

   /** The module this absorbed stays findable by its old name. */
   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("ReverseStep");
      return names;
   }

   @Override
   public String info() {
      return this.when.get();
   }

   @Override
   public void onEnable() {
      this.wasOnGround = false;
      this.pulling = false;
      this.pullTicks = 0;
   }

   @Override
   public void onDisable() {
      this.endPull();
   }

   /** The clock belongs to the server of the world that was left; put it back before anything else. */
   @Override
   public void onWorldChange() {
      this.endPull();
      this.wasOnGround = false;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.level == null) {
         this.endPull();
         return;
      }

      boolean leftGround = this.wasOnGround && !player.onGround();
      this.wasOnGround = player.onGround();
      if (this.pulling) {
         if (this.when.is("Falling") || !this.canPull(player) || player.onGround() || ++this.pullTicks > MAX_PULL_TICKS) {
            this.endPull();
         } else {
            this.continuePull(player);
            return;
         }
      }

      if (!this.when.is("Falling") && leftGround && this.startPull(client, player)) {
         return;
      }

      if (!this.when.is("Ledges")) {
         this.speedUpFall(client, player);
      }
   }

   private boolean canPull(LocalPlayer player) {
      return !player.isInWater()
         && !player.isInLava()
         && !player.getAbilities().flying
         && !player.isFallFlying()
         && !player.onClimbable()
         && !player.isPassenger()
         && !player.isSpectator();
   }

   /** The former ReverseStep: on the tick the player leaves the ground with something to land on below, go down. */
   private boolean startPull(Minecraft client, LocalPlayer player) {
      Vec3 motion = player.getDeltaMovement();
      if (motion.y > 0.0 || !this.canPull(player)) {
         return false;
      }

      double drop = this.dropBelow(client, player);
      if (drop < 0.0) {
         return false;
      }

      if (this.holesOnly.get()) {
         BlockPos landing = BlockPos.containing(player.getX(), player.getY() - drop + 0.2, player.getZ());
         if (!Holes.isHole(client.level, landing)) {
            return false;
         }
      }

      this.pulling = true;
      this.pullTicks = 0;
      this.applyTimer(client);
      this.continuePull(player);
      return true;
   }

   /** How far below the player's feet the first thing to land on is, or -1 when the drop is deeper than "Ledge Height". */
   private double dropBelow(Minecraft client, LocalPlayer player) {
      double maxDrop = this.ledgeHeight.get();
      for (double dy = 0.5; dy <= maxDrop + 0.05; dy += 0.5) {
         AABB box = player.getBoundingBox().move(0.0, -dy, 0.0);
         if (!client.level.noCollision(player, box)) {
            return dy - 0.5;
         }
      }

      return -1.0;
   }

   private void continuePull(LocalPlayer player) {
      Vec3 motion = player.getDeltaMovement();
      double target = -this.ledgeSpeed.get();
      double y = this.pull.is("Smooth") ? smoothStep(motion.y, target) : Math.min(motion.y, target);
      player.setDeltaMovement(motion.x, y, motion.z);
   }

   /** One tick of the smooth pull: a third of the remaining way to the target speed, never slower than gravity alone. */
   static double smoothStep(double current, double target) {
      if (current <= target) {
         return current;
      }

      double eased = current + (target - current) * 0.34;
      return Math.max(target, Math.min(eased, current - 0.08));
   }

   private void applyTimer(Minecraft client) {
      if (!this.timer.get() || this.timerApplied) {
         return;
      }

      // The Timer module owns the clock while it is on; changing it here would leave its rate wrong afterwards.
      Gemini gemini = Gemini.getInstance();
      TimerModule owner = gemini == null ? null : gemini.modules().byClass(TimerModule.class).orElse(null);
      if (owner != null && owner.isEnabled()) {
         return;
      }

      if (ClientState.ready(client) && SpCommands.run("tick rate " + this.timerRate.intValue())) {
         this.timerApplied = true;
      }
   }

   /** Ends a ledge pull and puts the clock back to normal; safe to call at any time. */
   private void endPull() {
      this.pulling = false;
      this.pullTicks = 0;
      if (this.timerApplied) {
         this.timerApplied = false;
         SpCommands.run("tick rate 20");
      }
   }

   private void speedUpFall(Minecraft client, LocalPlayer player) {
      if (!ClientState.ready(client) || !MovementHelpers.airborne(player)) {
         return;
      }

      Vec3 motion = player.getDeltaMovement();
      if (motion.y > -0.2) {
         return;
      }

      double ground = MovementHelpers.groundDistance(client.level, player, 12);
      if (ground >= 0.0 && (!this.onlySafe.get() || player.fallDistance + ground <= 3.0)) {
         double fall = Math.min(this.speed.get(), Math.max(0.1, ground - 0.05));
         player.setDeltaMovement(motion.x, -fall, motion.z);
      }
   }
}
