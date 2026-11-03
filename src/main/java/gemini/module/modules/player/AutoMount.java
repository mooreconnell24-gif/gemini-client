package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.EntityHitResult;

public final class AutoMount extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How close a mount must be", 4.0, 1.5, 6.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between attempts", 10.0, 2.0, 40.0, 1.0));
   private final BooleanSetting boats = this.addSetting(new BooleanSetting("Boats", "Mount boats and rafts", true));
   private final BooleanSetting minecarts = this.addSetting(new BooleanSetting("Minecarts", "Mount minecarts", true));
   private final ListSetting animals = this.addSetting(
      new ListSetting(
         "Animals", "Rideable animals to mount", ListSetting.Kind.ENTITY, "horse", "donkey", "mule", "pig", "strider", "camel", "skeleton_horse", "llama"
      )
   );
   private int cooldown;

   public AutoMount() {
      super("Auto Mount", "Auto-mount nearby boats, minecarts, and animals.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (PlayerHelpers.ready(client) && !player.isPassenger() && client.gui.screen() == null) {
         if (this.cooldown > 0) {
            this.cooldown--;
         } else {
            double limit = this.range.get() * this.range.get();
            Entity best = null;
            double bestDistance = limit;

            for (Entity entity : client.level.entitiesForRendering()) {
               if (entity.isAlive() && entity.getPassengers().isEmpty() && this.mountable(entity)) {
                  double distance = player.distanceToSqr(entity);
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = entity;
                  }
               }
            }

            if (best != null) {
               client.gameMode.interact(player, best, new EntityHitResult(best), InteractionHand.MAIN_HAND);
               this.cooldown = this.delay.intValue();
            }
         }
      }
   }

   private boolean mountable(Entity entity) {
      if (entity instanceof AbstractBoat) {
         return this.boats.get();
      } else {
         return entity instanceof AbstractMinecart ? this.minecarts.get() : this.animals.entityTypes().contains(entity.getType());
      }
   }
}
