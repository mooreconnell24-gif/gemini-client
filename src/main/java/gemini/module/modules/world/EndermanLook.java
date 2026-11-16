package gemini.module.modules.world;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class EndermanLook extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Look Away keeps endermen calm; Look At provokes them", "Look Away", "Look Away", "Look At")
   );
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest enderman to react to, in blocks", 32.0, 4.0, 64.0, 1.0));

   public EndermanLook() {
      super("Enderman Look", "Turns your view away from endermen so they do not get angry, or towards them to provoke them.", Category.WORLD);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   private static boolean lookingAt(LocalPlayer player, Enderman enderman, double margin) {
      Vec3 look = player.getViewVector(1.0F).normalize();
      Vec3 toward = new Vec3(enderman.getX() - player.getX(), enderman.getEyeY() - player.getEyeY(), enderman.getZ() - player.getZ());
      double distance = toward.length();
      return distance < 1.0E-4 ? true : look.dot(toward.scale(1.0 / distance)) > 1.0 - margin / distance && player.hasLineOfSight(enderman);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.readyNoScreen(client)) {
         LocalPlayer player = client.player;
         boolean away = this.mode.is("Look Away");
         if (!away || !player.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)) {
            Enderman nearest = null;
            double nearestDistance = Double.MAX_VALUE;

            for (Enderman enderman : client.level.getEntitiesOfClass(Enderman.class, player.getBoundingBox().inflate(this.range.get()))) {
               if (enderman.isAlive()) {
                  if (away) {
                     if (lookingAt(player, enderman, 0.12)) {
                        player.setXRot(90.0F);
                        return;
                     }
                  } else {
                     double distance = enderman.distanceToSqr(player);
                     if (distance < nearestDistance && player.hasLineOfSight(enderman)) {
                        nearest = enderman;
                        nearestDistance = distance;
                     }
                  }
               }
            }

            if (nearest != null) {
               float[] rotation = Rotations.face(player, nearest);
               player.setYRot(rotation[0]);
               player.setXRot(rotation[1]);
            }
         }
      }
   }
}
