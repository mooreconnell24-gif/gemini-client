package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.ClientState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gets the player to the ground faster. This is Fast Fall and the former ReverseStep in one module, because they
 * did the same thing at different moments: "Falling" speeds up any fall, "Ledges" snaps down the instant the
 * player walks off an edge, "Both" does either. Each keeps the settings it had as a separate module.
 */
public final class FastFall extends Module {
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
      new NumberSetting("Ledge Height", "Largest drop to snap down", 3.0, 1.0, 5.0, 0.5).showIf(() -> !this.when.is("Falling"))
   );
   private final NumberSetting ledgeSpeed = this.addSetting(
      new NumberSetting("Ledge Speed", "Downward speed while snapping down a ledge", 1.0, 0.2, 3.0, 0.1).showIf(() -> !this.when.is("Falling"))
   );
   private boolean wasOnGround;

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
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.level == null) {
         return;
      }

      boolean leftGround = this.wasOnGround && !player.onGround();
      this.wasOnGround = player.onGround();
      if (!this.when.is("Falling") && leftGround && this.snapDownLedge(client, player)) {
         return;
      }

      if (!this.when.is("Ledges")) {
         this.speedUpFall(client, player);
      }
   }

   /** The former ReverseStep: on the tick the player leaves the ground with something to land on below, go down at once. */
   private boolean snapDownLedge(Minecraft client, LocalPlayer player) {
      Vec3 motion = player.getDeltaMovement();
      if (motion.y > 0.0 || player.isInWater() || player.isInLava() || player.getAbilities().flying || player.isFallFlying()) {
         return false;
      }

      double maxDrop = this.ledgeHeight.get();
      for (double dy = 0.5; dy <= maxDrop + 0.05; dy += 0.5) {
         AABB box = player.getBoundingBox().move(0.0, -dy, 0.0);
         if (!client.level.noCollision(player, box)) {
            player.setDeltaMovement(motion.x, -this.ledgeSpeed.get(), motion.z);
            return true;
         }
      }

      return false;
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
