package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.ClientState;
import gemini.util.SpCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Tops the stack in your hand back up to a full stack whenever it runs low, so blocks and food never run out. */
public final class Refill extends Module {
   private static final int CHECK_TICKS = 5;
   private final NumberSetting below = this.addSetting(new NumberSetting("Below", "Refill once the stack has fewer than this many items", 16.0, 1.0, 64.0, 1.0));
   private final BooleanSetting offhand = this.addSetting(new BooleanSetting("Offhand", "Also keep the offhand stack full", false));
   private int wait;

   public Refill() {
      super("Refill", "Keep the stack you are holding topped up.", Category.PLAYER);
   }

   @Override
   public void onEnable() {
      this.wait = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.wait-- > 0 || !ClientState.ready(client)) {
         return;
      }

      this.wait = CHECK_TICKS;
      LocalPlayer player = client.player;
      this.top(player.getItemBySlot(EquipmentSlot.MAINHAND), "weapon.mainhand");
      if (this.offhand.get()) {
         this.top(player.getItemBySlot(EquipmentSlot.OFFHAND), "weapon.offhand");
      }
   }

   private void top(ItemStack stack, String slot) {
      int full = stack.getMaxStackSize();
      // Only stackable items: a sword or a filled bucket has nothing to top up.
      if (!stack.isEmpty() && full > 1 && stack.getCount() < Math.min(full, this.below.intValue())) {
         SpCommands.run("item modify entity @s " + slot + " {type:\"minecraft:set_count\",count:" + full + ",add:false}");
      }
   }
}
