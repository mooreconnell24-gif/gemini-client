package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ProjectileEsp extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest projectile to draw", 48.0, 8.0, 128.0, 4.0));
   private final BooleanSetting arrows = this.addSetting(new BooleanSetting("Arrows", "Arrows and tridents", true));
   private final BooleanSetting fireballs = this.addSetting(new BooleanSetting("Fireballs", "Ghast and blaze fire", true));
   private final BooleanSetting explosives = this.addSetting(new BooleanSetting("Explosives", "Primed TNT and falling anvils", true));
   private final BooleanSetting other = this.addSetting(new BooleanSetting("Other", "Pearls, potions, snowballs and the rest", true));
   private final BooleanSetting path = this.addSetting(new BooleanSetting("Path", "Draw where the projectile is going", true));
   private final BooleanSetting ownerLines = this.addSetting(new BooleanSetting("Owner Lines", "Draw a line back to whoever fired it", false));

   public ProjectileEsp() {
      super("Projectile ESP", "Highlight incoming arrows.", Category.RENDER);
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer self = client.player;
      ClientLevel level = client.level;
      if (self != null && level != null) {
         double limit = this.range.get() * this.range.get();

         for (Entity entity : level.entitiesForRendering()) {
            if (!(entity.distanceToSqr(self) > limit)) {
               int color = this.classify(entity, self);
               if (color != 0) {
                  AABB box = Render3D.interpolatedBox(entity, partialTick).inflate(0.1);
                  Render3D.box(box, color, true, true, 1.5F, 60, true);
                  Vec3 velocity = entity.getDeltaMovement();
                  if (this.path.get() && velocity.lengthSqr() > 1.0E-4) {
                     Vec3 start = box.getCenter();
                     Vec3 point = start;
                     Vec3 motion = velocity;

                     for (int step = 0; step < 40; step++) {
                        Vec3 next = point.add(motion);
                        Render3D.line(point, next, Render3D.alpha(color, 160), 1.0F, true);
                        point = next;
                        motion = motion.scale(0.99).add(0.0, entity instanceof Fireball ? 0.0 : -0.05, 0.0);
                        if (!level.getBlockState(BlockPos.containing(next)).isAir()) {
                           break;
                        }
                     }
                  }

                  if (this.ownerLines.get() && entity instanceof Projectile projectile && projectile.getOwner() != null && projectile.getOwner() != self) {
                     Render3D.line(
                        box.getCenter(),
                        projectile.getOwner().getPosition(partialTick).add(0.0, projectile.getOwner().getBbHeight() / 2.0, 0.0),
                        Render3D.alpha(color, 120),
                        1.0F,
                        true
                     );
                  }
               }
            }
         }
      }
   }

   private int classify(Entity entity, LocalPlayer self) {
      if (entity instanceof AbstractArrow arrow) {
         return this.arrows.get() && arrow.getOwner() != self ? -41892 : 0;
      } else if (entity instanceof Fireball) {
         return this.fireballs.get() ? -26052 : 0;
      } else if (entity instanceof PrimedTnt) {
         return this.explosives.get() ? -50116 : 0;
      } else if (entity instanceof FallingBlockEntity falling) {
         return this.explosives.get() && falling.getBlockState().is(BlockTags.ANVIL) ? -4144960 : 0;
      } else if (!(entity instanceof Projectile projectile)) {
         return 0;
      } else {
         return this.other.get() && projectile.getOwner() != self ? -6641921 : 0;
      }
   }
}
