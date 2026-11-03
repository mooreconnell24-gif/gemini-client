package gemini.module.modules.movement;

import gemini.input.Keys;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class SprintAssist extends Module {
   private final BooleanSetting omni = this.addSetting(new BooleanSetting("Omni", "Sprint in every direction, not just forward", false));
   private final BooleanSetting ignoreHunger = this.addSetting(new BooleanSetting("Ignore Hunger", "Keep sprinting at low food", false));
   private final BooleanSetting whileUsing = this.addSetting(new BooleanSetting("While Using", "Keep sprinting while using an item", false));

   public SprintAssist() {
      super("Sprint", "Sprint automatically while moving.", Category.MOVEMENT, Keys.fromGlfw('V'));
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && player.input != null && !player.isCrouching() && !player.horizontalCollision) {
         boolean moving = this.omni.get() ? player.input.getMoveVector().lengthSquared() > 1.0E-4F : player.input.hasForwardImpulse();
         boolean fed = this.ignoreHunger.get() || player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly;
         boolean usingBlocked = player.isUsingItem() && !this.whileUsing.get();
         if (moving && fed && !usingBlocked && !player.isInWater()) {
            player.setSprinting(true);
         }
      }
   }
}
