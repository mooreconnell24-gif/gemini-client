package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class KeepRange extends Module {
   private final NumberSetting minDistance = this.addSetting(
      new NumberSetting("Min Distance", "Back up if target is closer than this", 2.6, 1.5, 4.0, 0.1)
   );
   private final NumberSetting maxDistance = this.addSetting(new NumberSetting("Max Distance", "Max engagement distance", 3.6, 2.5, 5.5, 0.1));
   private final BooleanSetting onlyDuringCooldown = this.addSetting(
      new BooleanSetting("Wait Cooldown", "Only back up while attack is recharging", true)
   );
   private boolean backingUp;

   public KeepRange() {
      super("KeepRange", "Keep optimal melee spacing from your combat target.", Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.release(Minecraft.getInstance());
   }

   private void release(Minecraft client) {
      if (this.backingUp) {
         client.options.keyDown.setDown(MoveUtil.physicalKeyDown(client.options.keyDown));
         this.backingUp = false;
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (CombatHelpers.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         LivingEntity enemy = CombatHelpers.nearestEnemy(client, player, this.maxDistance.get(), true);
         if (enemy != null) {
            double dist = player.distanceTo(enemy);
            boolean recharging = !this.onlyDuringCooldown.get() || player.getAttackStrengthScale(0.0F) < 0.85F;
            if (dist < this.minDistance.get() && recharging) {
               client.options.keyDown.setDown(true);
               this.backingUp = true;
               return;
            }
         }

         this.release(client);
      } else {
         this.release(client);
      }
   }

   @Override
   public String info() {
      return String.format(Locale.ROOT, "%.1fm", this.minDistance.get());
   }
}
