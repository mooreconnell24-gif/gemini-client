package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.render.Render3D;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class PearlTracker extends Module {
   private final BooleanSetting trajectory = this.addSetting(new BooleanSetting("Flight Path", "Draw predicted landing arc for thrown pearls", true));
   private final BooleanSetting landingBox = this.addSetting(new BooleanSetting("Landing Box", "Highlight predicted impact spot in 3D", true));
   private final BooleanSetting labels = this.addSetting(new BooleanSetting("Owner Label", "Display thrower name and impact countdown", true));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Pearl tracker accent hue", 285));

   public PearlTracker() {
      super("PearlTracker", "Track Ender Pearl arcs, owners, and landing spots.", Category.RENDER);
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      if (client.level != null && client.player != null) {
         int argb = this.color.argb();

         for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof Projectile pearl && entity.getType() == EntityTypes.ENDER_PEARL) {
               Vec3 pos = pearl.getPosition(partialTick);
               Vec3 vel = pearl.getDeltaMovement();
               Entity owner = pearl.getOwner();
               String ownerName = owner != null ? owner.getName().getString() : "Pearl";
               Render3D.boxGlow(Render3D.interpolatedBox(pearl, partialTick).inflate(0.08), argb, 1.8F, 70, true);
               Vec3 simPos = pos;
               Vec3 simVel = vel;
               Vec3 landing = null;
               int ticksToLand = 0;

               for (int step = 0; step < 120; step++) {
                  Vec3 next = simPos.add(simVel);
                  BlockHitResult hit = client.level.clip(new ClipContext(simPos, next, Block.COLLIDER, Fluid.NONE, pearl));
                  if (hit.getType() != Type.MISS) {
                     landing = hit.getLocation();
                     ticksToLand = step;
                     if (this.trajectory.get()) {
                        Render3D.line(simPos, landing, argb, 1.6F, true);
                     }
                     break;
                  }

                  if (this.trajectory.get()) {
                     Render3D.line(simPos, next, Render3D.alpha(argb, 185), 1.5F, true);
                  }

                  simPos = next;
                  simVel = simVel.scale(0.99).subtract(0.0, 0.03, 0.0);
               }

               if (landing != null && this.landingBox.get()) {
                  AABB spot = new AABB(landing.x - 0.3, landing.y - 0.1, landing.z - 0.3, landing.x + 0.3, landing.y + 0.5, landing.z + 0.3);
                  Render3D.boxGlow(spot, argb, 2.0F, 65, true);
                  if (this.labels.get()) {
                     String tag = String.format(Locale.ROOT, "%s (%.1fs)", ownerName, ticksToLand / 20.0F);
                     Render3D.text(tag, landing.add(0.0, 0.75, 0.0), argb, 0.85F, true);
                  }
               } else if (this.labels.get()) {
                  Render3D.text(ownerName, pos.add(0.0, 0.55, 0.0), argb, 0.85F, true);
               }
            }
         }
      }
   }
}
