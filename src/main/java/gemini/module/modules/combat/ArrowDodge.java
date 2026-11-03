package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class ArrowDodge extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max arrow detection range", 24.0, 6.0, 48.0, 1.0));
   private final NumberSetting lookahead = this.addSetting(new NumberSetting("Lookahead", "Ticks of arrow flight to predict", 14.0, 4.0, 40.0, 1.0));
   private final NumberSetting strength = this.addSetting(new NumberSetting("Dodge Speed", "Horizontal dodge velocity", 0.55, 0.2, 1.5, 0.05));
   private final BooleanSetting inAir = this.addSetting(new BooleanSetting("While Airborne", "Also dodge while jumping or falling", true));

   public ArrowDodge() {
      super("Arrow Dodge", "Sidestep incoming arrows automatically.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (CombatHelpers.ready(client) && (this.inAir.get() || player.onGround())) {
         AABB hitbox = player.getBoundingBox().inflate(0.35);
         double limit = this.range.get() * this.range.get();
         Vec3 away = null;

         for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof AbstractArrow arrow && arrow.getOwner() != player && !(arrow.distanceToSqr(player) > limit)) {
               Vec3 motion = arrow.getDeltaMovement();
               if (!(motion.lengthSqr() < 0.1) && this.willHit(client, arrow, hitbox)) {
                  double length = Math.hypot(motion.x, motion.z);
                  if (!(length < 0.001)) {
                     Vec3 side = new Vec3(-motion.z / length, 0.0, motion.x / length);
                     Vec3 offset = player.position().subtract(arrow.position());
                     away = offset.dot(side) >= 0.0 ? side : side.scale(-1.0);
                     break;
                  }
               }
            }
         }

         if (away != null) {
            Vec3 current = player.getDeltaMovement();
            player.setDeltaMovement(away.x * this.strength.get(), current.y, away.z * this.strength.get());
         }
      }
   }

   private boolean willHit(Minecraft client, AbstractArrow arrow, AABB hitbox) {
      Vec3 position = arrow.position();
      Vec3 motion = arrow.getDeltaMovement();

      for (int tick = 0; tick < this.lookahead.intValue(); tick++) {
         Vec3 next = position.add(motion);
         if (hitbox.contains(next) || hitbox.clip(position, next).isPresent()) {
            return true;
         }

         if (client.level.clip(new ClipContext(position, next, Block.COLLIDER, Fluid.NONE, arrow)).getType() != Type.MISS) {
            return false;
         }

         position = next;
         motion = motion.scale(0.99).add(0.0, -0.05, 0.0);
      }

      return false;
   }
}
