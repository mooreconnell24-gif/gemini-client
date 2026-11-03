package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class SpeedModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "How the speed boost is applied", "LowHop", "Strict", "Ground", "Bhop", "LowHop", "Strafe", "YPort")
   );
   private final NumberSetting multiplier = this.addSetting(new NumberSetting("Multiplier", "Horizontal speed multiplier", 1.25, 1.05, 4.0, 0.05));
   private final BooleanSetting inAir = this.addSetting(new BooleanSetting("Air Boost", "Maintain speed boost while airborne", true));
   private final BooleanSetting setbackPause = this.addSetting(new BooleanSetting("Setback Pause", "Pause boost briefly after server lagback", true));
   private final BooleanSetting sneakStops = this.addSetting(new BooleanSetting("Pause Sneak", "Disable speed boost while sneaking", true));
   private boolean shortenedHop;

   public SpeedModule() {
      super("Speed", "Move faster on foot.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null
         && player.input != null
         && !player.isInWater()
         && !player.isInLava()
         && !player.isFallFlying()
         && !player.getAbilities().flying
         && !player.onClimbable()
         && !player.isPassenger()
         && (!this.sneakStops.get() || !player.isCrouching())) {
         if (this.setbackPause.get() && MoveUtil.hasRecentSetback()) {
            this.shortenedHop = false;
            return;
         }

         if (!MoveUtil.hasInput(player)) {
            this.shortenedHop = false;
         } else {
            boolean hop = !this.mode.is("Ground") && !this.mode.is("Strict");
            if (hop && player.onGround() && client.gui.screen() == null) {
               player.jumpFromGround();
               this.shortenedHop = false;
            }

            if (this.mode.is("LowHop") && !player.onGround() && !this.shortenedHop && player.getDeltaMovement().y > 0.2) {
               player.setDeltaMovement(player.getDeltaMovement().x, 0.2, player.getDeltaMovement().z);
               this.shortenedHop = true;
            }

            if (this.mode.is("YPort") && !player.onGround() && player.getDeltaMovement().y < 0.15) {
               player.setDeltaMovement(player.getDeltaMovement().x, -0.42, player.getDeltaMovement().z);
            }

            boolean air = this.inAir.get() || this.mode.is("Strafe") || this.mode.is("LowHop") || this.mode.is("YPort");
            if (player.onGround() || air) {
               double boost = this.mode.is("Strict") ? Math.min(this.multiplier.get(), player.onGround() ? 1.32 : 1.14) : this.multiplier.get();
               MoveUtil.setHorizontal(player, MoveUtil.baseSpeed(player) * boost);
            }
         }
      }
   }

   @Override
   public String info() {
      return this.mode.get();
   }
}
