package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.InfoText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

public final class EntityCulling extends Module {
   public static EntityCulling instance;
   private final BooleanSetting wallCull = this.addSetting(
      new BooleanSetting("Cull Behind Walls", "Hide vanilla name tags of entities behind walls", true)
   );
   private final NumberSetting maxDistance = this.addSetting(
      new NumberSetting("Nametag Distance", "Hide vanilla name tags farther away than this, in blocks", 28.0, 6.0, 64.0, 2.0)
   );
   private final BooleanSetting hideArmorStands = this.addSetting(
      new BooleanSetting("Hide Armor Stand Tags", "Hide the name tags of armour stands and holograms", true)
   );

   private final InfoText infoText = new InfoText();

   public EntityCulling() {
      super("EntityCulling", "Hides vanilla name tags behind walls and far away.", Category.RENDER);
      instance = this;
   }

   @Override
   public String info() {
      return this.infoText.whole(this.maxDistance.intValue(), "m");
   }

   public boolean shouldCullNametag(Entity entity, double distSq) {
      if (!this.isEnabled()) {
         return false;
      } else if (this.hideArmorStands.get() && entity instanceof ArmorStand) {
         return true;
      } else {
         double maxD = this.maxDistance.get();
         if (distSq > maxD * maxD) {
            return true;
         } else {
            if (this.wallCull.get()) {
               LocalPlayer self = Minecraft.getInstance().player;
               if (self != null && entity != self && !self.hasLineOfSight(entity)) {
                  return true;
               }
            }

            return false;
         }
      }
   }
}
