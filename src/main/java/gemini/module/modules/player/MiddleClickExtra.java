package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;

public final class MiddleClickExtra extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Action", "What a middle click does", "Ender Pearl", "Ender Pearl", "Firework", "XP Bottle", "Golden Apple")
   );
   private boolean previous;

   public MiddleClickExtra() {
      super("Middle Click Extra", "Give the middle mouse button a useful job.", Category.PLAYER);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (PlayerHelpers.ready(client) && client.gui.screen() == null) {
         boolean pressed = client.mouseHandler.isMiddlePressed();
         if (pressed && !this.previous) {
            this.act(client, player);
         }

         this.previous = pressed;
      } else {
         this.previous = false;
      }
   }

   private void act(Minecraft client, LocalPlayer player) {
      String var3 = this.mode.get();
      switch (var3) {
         case "Golden Apple":
            PlayerHelpers.useHotbarItem(client, player, Items.GOLDEN_APPLE);
            break;
         case "Firework":
            PlayerHelpers.useHotbarItem(client, player, Items.FIREWORK_ROCKET);
            break;
         case "XP Bottle":
            PlayerHelpers.useHotbarItem(client, player, Items.EXPERIENCE_BOTTLE);
            break;
         default:
            PlayerHelpers.useHotbarItem(client, player, Items.ENDER_PEARL);
      }
   }
}
