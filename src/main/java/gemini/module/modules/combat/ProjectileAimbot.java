package gemini.module.modules.combat;

import gemini.util.Targets;
import gemini.util.ClientState;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class ProjectileAimbot extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max target distance", 45.0, 10.0, 80.0, 2.0));
   private final BooleanSetting silent = this.addSetting(new BooleanSetting("Silent Aim", "Aim in packets only", true));
   private final BooleanSetting predict = this.addSetting(new BooleanSetting("Predict Motion", "Lead moving targets by flight time", true));

   public ProjectileAimbot() {
      super("ProjectileAimbot", "Ballistic arc aimbot for Bows, Crossbows, and Tridents.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         boolean usingBow = player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem;
         boolean usingTrident = player.isUsingItem() && player.getUseItem().is(Items.TRIDENT);
         boolean holdingCrossbow = player.getMainHandItem().getItem() instanceof CrossbowItem && CrossbowItem.isCharged(player.getMainHandItem());
         if (usingBow || usingTrident || holdingCrossbow) {
            LivingEntity target = Targets.nearestEnemy(player, client.level.entitiesForRendering(), this.range.get(), true);
            if (target != null) {
               float velocity = usingBow
                  ? Math.max(0.6F, BowItem.getPowerForTime(player.getTicksUsingItem()) * 3.0F)
                  : (usingTrident ? 2.5F : 3.15F);
               double dist = player.distanceTo(target);
               double flightTicks = this.predict.get() ? dist / Math.max(0.5, velocity) : 0.0;
               Vec3 motion = target.getDeltaMovement().scale(flightTicks);
               Vec3 eye = player.getEyePosition();
               Vec3 aimPoint = new Vec3(
                  target.getX() + motion.x,
                  target.getY() + target.getBbHeight() * 0.72 + motion.y,
                  target.getZ() + motion.z
               );
               double dx = aimPoint.x - eye.x;
               double dy = aimPoint.y - eye.y;
               double dz = aimPoint.z - eye.z;
               double horizDist = Math.sqrt(dx * dx + dz * dz);
               float yaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
               float g = 0.05F;
               double v2 = velocity * velocity;
               double v4 = v2 * v2;
               double root = v4 - g * (g * horizDist * horizDist + 2.0 * dy * v2);
               float pitch;
               if (root >= 0.0 && horizDist > 1.0E-3) {
                  pitch = (float)(-Math.toDegrees(Math.atan((v2 - Math.sqrt(root)) / (g * horizDist))));
               } else {
                  pitch = (float)(-(Mth.atan2(dy, horizDist) * (180.0 / Math.PI)));
               }
               RotationManager.request(player, Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F), 95.0F, this.silent.get(), ActionPriority.COMBAT, 2.5F);
            }
         }
      }
   }
}
