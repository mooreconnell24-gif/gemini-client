package gemini.module.modules.movement;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.MoveUtil;
import gemini.util.Targets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class TargetStrafe extends Module {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Orbit distance around target", 2.6, 1.0, 5.0, 0.2));
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Orbit speed multiplier", 1.05, 0.5, 2.0, 0.05));
   private final BooleanSetting holdJump = this.addSetting(new BooleanSetting("Require Jump", "Only orbit while holding Jump", true));
   private final BooleanSetting renderRing = this.addSetting(new BooleanSetting("Render Ring", "Draw the 3D orbit ring around the target", true));
   private int dir = 1;
   private LivingEntity activeTarget;

   public TargetStrafe() {
      super("TargetStrafe", "Orbit smoothly around active combat targets.", Category.MOVEMENT);
   }

   @Override
   public void onDisable() {
      this.activeTarget = null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (!ClientState.ready(client)) {
         this.activeTarget = null;
      } else {
         LocalPlayer player = client.player;
         this.activeTarget = findNearestTarget(client, player, 6.0);
         if (this.activeTarget != null && (!this.holdJump.get() || client.options.keyJump.isDown())) {
            if (player.horizontalCollision) {
               this.dir = -this.dir;
            }

            double angle = Math.atan2(player.getZ() - this.activeTarget.getZ(), player.getX() - this.activeTarget.getX()) + this.dir * 0.26;
            double r = this.radius.get();
            double tx = this.activeTarget.getX() + Math.cos(angle) * r;
            double tz = this.activeTarget.getZ() + Math.sin(angle) * r;
            BlockPos nextGround = BlockPos.containing(tx, player.getY() - 1.0, tz);
            if (client.level.getBlockState(nextGround).is(Blocks.LAVA) || MovementHelpers.groundDistance(client.level, player, 5) < 0.0) {
               this.dir = -this.dir;
               angle = Math.atan2(player.getZ() - this.activeTarget.getZ(), player.getX() - this.activeTarget.getX()) + this.dir * 0.26;
               tx = this.activeTarget.getX() + Math.cos(angle) * r;
               tz = this.activeTarget.getZ() + Math.sin(angle) * r;
            }
            Vec3 delta = new Vec3(tx - player.getX(), 0.0, tz - player.getZ()).normalize().scale(MoveUtil.baseSpeed(player) * this.speed.get());
            player.setDeltaMovement(delta.x, player.getDeltaMovement().y, delta.z);
         }
      }
   }

   static LivingEntity findNearestTarget(Minecraft client, LocalPlayer self, double range) {
      LivingEntity best = null;
      double bestD = range * range;

      for (Entity e : client.level.entitiesForRendering()) {
         if (Targets.isCandidate(self, e, true, true, false, true)) {
            double d = self.distanceToSqr(e);
            if (d < bestD) {
               bestD = d;
               best = (LivingEntity)e;
            }
         }
      }

      return best;
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.renderRing.get() && this.activeTarget != null && this.activeTarget.isAlive()) {
         Vec3 c = this.activeTarget.getPosition(partialTick);
         double r = this.radius.get();
         int segs = 24;

         for (int i = 0; i < segs; i++) {
            double a1 = i * Math.PI * 2.0 / segs;
            double a2 = (i + 1) * Math.PI * 2.0 / segs;
            Render3D.line(
               new Vec3(c.x + Math.cos(a1) * r, c.y + 0.05, c.z + Math.sin(a1) * r),
               new Vec3(c.x + Math.cos(a2) * r, c.y + 0.05, c.z + Math.sin(a2) * r),
               -16722689,
               2.0F,
               false
            );
         }
      }
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.activeTarget = null;
   }
}
