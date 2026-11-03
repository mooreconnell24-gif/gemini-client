package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.phys.Vec3;

public final class StrongholdFinder extends Module {
   private int trackedId = -1;

   public StrongholdFinder() {
      super("StrongholdFinder", "Track Eye of Ender flight angles to locate strongholds.", Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.trackedId = -1;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.level != null && client.player != null) {
         for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof EyeOfEnder eye && eye.getId() != this.trackedId && eye.tickCount > 12) {
               this.trackedId = eye.getId();
               Vec3 delta = eye.getDeltaMovement();
               double angle = Math.toDegrees(Math.atan2(-delta.x, delta.z));
               CommandManager.feedback(
                  String.format(Locale.ROOT, "§b[StrongholdFinder] §7Eye heading: §f%.1f° §7from §f%d, %d", angle, eye.getBlockX(), eye.getBlockZ())
               );
            }
         }
      }
   }
}
