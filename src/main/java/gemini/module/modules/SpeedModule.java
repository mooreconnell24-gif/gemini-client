package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

public final class SpeedModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting(
         "Mode",
         "Movement bypass profile (Verus, Vulcan, GrimCollide, and Strafe mirror LiquidBounce Nextgen)",
         "Vulcan",
         "Vulcan",
         "Verus",
         "GrimCollide",
         "Strict",
         "Ground",
         "Bhop",
         "LowHop",
         "Strafe",
         "YPort"
      )
   );
   private final NumberSetting multiplier = this.addSetting(new NumberSetting("Multiplier", "Speed relative to normal movement", 1.25, 1.05, 4.0, 0.05));
   private final BooleanSetting inAir = this.addSetting(new BooleanSetting("Air Boost", "Keep the boost while airborne", true));
   private final BooleanSetting sneakStops = this.addSetting(new BooleanSetting("Not While Sneaking", "Disable while crouching", true));
   private boolean shortenedHop;

   public SpeedModule() {
      super("Speed", "Move faster with LiquidBounce Nextgen Verus, Vulcan, GrimCollide, and Strafe modes.", Category.MOVEMENT);
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
         if (!MoveUtil.hasInput(player)) {
            this.shortenedHop = false;
         } else {
            if (this.mode.is("GrimCollide") && client.level != null) {
               int collisions = 0;

               for (Entity entity : client.level.entitiesForRendering()) {
                  if (entity != player && entity.isAlive() && player.getBoundingBox().inflate(0.85).intersects(entity.getBoundingBox())) {
                     collisions++;
                  }
               }

               if (collisions > 0) {
                  MoveUtil.setHorizontal(player, MoveUtil.baseSpeed(player) * (1.0 + Math.min(0.32, collisions * 0.08)));
               }

               return;
            }

            boolean hop = !this.mode.is("Ground") && !this.mode.is("Strict");
            if (hop && player.onGround() && client.gui.screen() == null) {
               player.jumpFromGround();
               this.shortenedHop = false;
            }

            if ((this.mode.is("LowHop") || this.mode.is("Vulcan")) && !player.onGround() && !this.shortenedHop && player.getDeltaMovement().y > 0.2) {
               player.setDeltaMovement(player.getDeltaMovement().x, this.mode.is("Vulcan") ? 0.28 : 0.2, player.getDeltaMovement().z);
               this.shortenedHop = true;
            }

            if (this.mode.is("YPort") && !player.onGround()) {
               player.setDeltaMovement(player.getDeltaMovement().x, -1.0, player.getDeltaMovement().z);
            }

            boolean air = this.inAir.get() || this.mode.is("Strafe") || this.mode.is("LowHop") || this.mode.is("YPort") || this.mode.is("Verus");
            if (player.onGround() || air) {
               String var7 = this.mode.get();

               double boost = switch (var7) {
                  case "Strict" -> Math.min(this.multiplier.get(), player.onGround() ? 1.32 : 1.14);
                  case "Vulcan" -> player.onGround() ? 1.28 : 1.08;
                  case "Verus" -> player.onGround() ? 1.36 : 1.18;
                  default -> this.multiplier.get();
               };
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
