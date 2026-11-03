package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class WTap extends Module {
   private final NumberSetting holdTicks = this.addSetting(
      new NumberSetting("Release Ticks", "Ticks to release W after landing a hit", 2.0, 1.0, 5.0, 1.0)
   );
   private int releaseTimer;

   public WTap() {
      super("WTap", "Briefly re-tap W after melee hits for combo knockback.", Category.COMBAT);
   }

   @Override
   public void onDisable() {
      if (this.releaseTimer > 0) {
         Minecraft client = Minecraft.getInstance();
         client.options.keyUp.setDown(MoveUtil.physicalKeyDown(client.options.keyUp));
         this.releaseTimer = 0;
      }
   }

   @Override
   public void onAttack(Entity target) {
      Minecraft client = Minecraft.getInstance();
      if (target instanceof LivingEntity && client.player != null && client.player.isSprinting()) {
         this.releaseTimer = this.holdTicks.intValue();
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.releaseTimer > 0 && client.player != null && client.gui.screen() == null) {
         this.releaseTimer--;
         if (this.releaseTimer > 0) {
            client.options.keyUp.setDown(false);
            client.player.setSprinting(false);
         } else {
            boolean down = MoveUtil.physicalKeyDown(client.options.keyUp);
            client.options.keyUp.setDown(down);
            if (down) {
               client.player.setSprinting(true);
            }
         }
      }
   }

   @Override
   public String info() {
      return this.holdTicks.intValue() + "t";
   }
}
