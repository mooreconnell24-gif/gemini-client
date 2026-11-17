package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class SpeedModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting(
         "Mode",
         "Vanilla speeds up walking; Bunny Hop jumps for you as you run; Low Hop does the same with short, flat hops; Y-Port snaps back to the ground after each hop",
         "Bunny Hop",
         "Vanilla",
         "Bunny Hop",
         "Low Hop",
         "Y-Port"
      )
   );
   private final NumberSetting multiplier = this.addSetting(
      new NumberSetting("Multiplier", "Speed as a multiple of your normal walking or sprinting speed", 1.3, 1.0, 4.0, 0.05)
   );
   private final BooleanSetting inAir = this.addSetting(
      new BooleanSetting("Air Control", "Keep the speed and steer freely while in the air", true).showIf(() -> this.mode.is("Vanilla"))
   );
   private final BooleanSetting sneakStops = this.addSetting(new BooleanSetting("Pause Sneak", "Turn the boost off while sneaking", true));
   private boolean shortenedHop;

   public SpeedModule() {
      super("Speed", "Makes you move faster on foot.", Category.MOVEMENT);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Strafe");
      names.add("Bhop");
      return names;
   }

   @Override
   public void onEnable() {
      this.shortenedHop = false;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null
         || player.input == null
         || player.isInWater()
         || player.isInLava()
         || player.isFallFlying()
         || player.getAbilities().flying
         || player.onClimbable()
         || player.isPassenger()
         || this.sneakStops.get() && player.isCrouching()) {
         return;
      }

      if (!MoveUtil.hasInput(player)) {
         this.shortenedHop = false;
         return;
      }

      boolean hop = !this.mode.is("Vanilla");
      if (hop && player.onGround() && client.gui.screen() == null) {
         player.jumpFromGround();
         this.shortenedHop = false;
      }

      if (this.mode.is("Low Hop") && !player.onGround() && !this.shortenedHop && player.getDeltaMovement().y > 0.2) {
         player.setDeltaMovement(player.getDeltaMovement().x, 0.2, player.getDeltaMovement().z);
         this.shortenedHop = true;
      }

      if (this.mode.is("Y-Port") && !player.onGround() && player.getDeltaMovement().y < 0.15) {
         player.setDeltaMovement(player.getDeltaMovement().x, -0.42, player.getDeltaMovement().z);
      }

      // The hopping modes are about keeping speed through the air, so they always steer there.
      if (player.onGround() || hop || this.inAir.get()) {
         MoveUtil.setHorizontal(player, MoveUtil.baseSpeed(player) * this.multiplier.get());
      }
   }

   @Override
   public String info() {
      return this.mode.get();
   }
}
