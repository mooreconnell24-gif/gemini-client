package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public final class AutoFirework extends Module {
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Minimum ticks between rockets", 50.0, 10.0, 200.0, 5.0));
   private final BooleanSetting onlySlow = this.addSetting(new BooleanSetting("Only When Slow", "Skip rockets while you are already fast", true));
   private final NumberSetting minSpeed = this.addSetting(
      new NumberSetting("Min Speed", "Fire when slower than this (blocks per tick)", 1.0, 0.2, 3.0, 0.1).showIf(this.onlySlow::get)
   );
   private int cooldown;

   public AutoFirework() {
      super("Auto Firework", "Use firework rockets automatically while gliding.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (MovementHelpers.ready(client) && player.isFallFlying()) {
         if (this.cooldown > 0) {
            this.cooldown--;
         } else if (!this.onlySlow.get() || !(player.getDeltaMovement().length() >= this.minSpeed.get())) {
            if (player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
               client.gameMode.useItem(player, InteractionHand.OFF_HAND);
            } else {
               int slot = Inventories.findHotbar(player, stack -> stack.is(Items.FIREWORK_ROCKET));
               if (slot < 0) {
                  return;
               }

               int previous = player.getInventory().getSelectedSlot();
               player.getInventory().setSelectedSlot(slot);
               client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
               player.getInventory().setSelectedSlot(previous);
            }

            this.cooldown = this.delay.intValue();
         }
      }
   }
}
