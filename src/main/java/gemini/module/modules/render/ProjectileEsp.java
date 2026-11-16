package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.phys.Vec3;

public final class ProjectileEsp extends Module {
   private static final int PATH_STEPS = 40;
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest projectile shown, in blocks", 48.0, 8.0, 128.0, 4.0));
   private final BooleanSetting arrows = this.addSetting(new BooleanSetting("Arrows", "Arrows and tridents fired by anything but you", true));
   private final BooleanSetting fireballs = this.addSetting(new BooleanSetting("Fireballs", "Ghast and blaze fireballs", true));
   private final BooleanSetting explosives = this.addSetting(new BooleanSetting("Explosives", "Primed TNT and falling anvils", true));
   private final BooleanSetting other = this.addSetting(new BooleanSetting("Other", "Pearls, potions, snowballs and the rest thrown by anything but you", true));
   private final BooleanSetting path = this.addSetting(
      new BooleanSetting("Path", "Draw where each moving projectile is heading, up to two seconds ahead or the first block", true)
   );
   private final BooleanSetting ownerLines = this.addSetting(new BooleanSetting("Owner Lines", "Draw a line back to whoever fired it", false));
   private final MutableBlockPos cursor = new MutableBlockPos();

   public ProjectileEsp() {
      super("Projectile ESP", "Highlights arrows, fireballs, primed TNT, falling anvils and thrown items, with their paths.", Category.RENDER);
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
                  Render3D.entityBox(entity, partialTick, 0.1, color, true, true, 1.5F, 60, true);
                  double cx = Render3D.entityX(entity, partialTick);
                  double cy = Render3D.entityY(entity, partialTick) + entity.getBbHeight() / 2.0;
                  double cz = Render3D.entityZ(entity, partialTick);
                  Vec3 velocity = entity.getDeltaMovement();
                  if (this.path.get() && velocity.lengthSqr() > 1.0E-4) {
                     this.drawPath(level, entity, cx, cy, cz, velocity, Render3D.alpha(color, 160));
                  }

                  if (this.ownerLines.get() && entity instanceof Projectile projectile) {
                     Entity owner = projectile.getOwner();
                     if (owner != null && owner != self) {
                        Render3D.lineArgb(
                           cx,
                           cy,
                           cz,
                           Render3D.entityX(owner, partialTick),
                           Render3D.entityY(owner, partialTick) + owner.getBbHeight() / 2.0,
                           Render3D.entityZ(owner, partialTick),
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
   }

   /** The flight ahead as line segments, a tick apart, until it reaches a block; coordinates only, no objects. */
   private void drawPath(ClientLevel level, Entity entity, double x, double y, double z, Vec3 velocity, int argb) {
      double mx = velocity.x;
      double my = velocity.y;
      double mz = velocity.z;
      double gravity = entity instanceof Fireball ? 0.0 : entity.getGravity();
      for (int step = 0; step < PATH_STEPS; step++) {
         double nx = x + mx;
         double ny = y + my;
         double nz = z + mz;
         Render3D.lineArgb(x, y, z, nx, ny, nz, argb, 1.0F, true);
         x = nx;
         y = ny;
         z = nz;
         mx *= 0.99;
         my = my * 0.99 - gravity;
         mz *= 0.99;
         this.cursor.set(nx, ny, nz);
         if (!level.getBlockState(this.cursor).isAir()) {
            break;
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
