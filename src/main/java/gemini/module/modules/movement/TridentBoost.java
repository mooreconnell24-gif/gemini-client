package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class TridentBoost extends Module {
   private final NumberSetting boost = this.addSetting(new NumberSetting("Boost", "Multiplier on riptide launch speed", 2.0, 1.0, 6.0, 0.25));
   private boolean spinning;

   public TridentBoost() {
      super("Trident Boost", "Launch much farther with a riptide trident.", Category.MOVEMENT);
   }

   @Override
   public String info() {
      return String.format("%.2fx", this.boost.get());
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         boolean now = player.isAutoSpinAttack();
         if (now && !this.spinning) {
            player.setDeltaMovement(player.getDeltaMovement().scale(this.boost.get()));
         }

         this.spinning = now;
      }
   }
}
