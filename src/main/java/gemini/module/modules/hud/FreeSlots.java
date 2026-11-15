package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudContext;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;

/** How many of the 36 inventory slots are empty, turning red when the inventory is nearly full. */
public final class FreeSlots extends Ticked {
   private final NumberSetting warnBelow = this.addSetting(new NumberSetting("Warn Below", "Turn red when this many slots or fewer are free", 3.0, 0.0, 36.0, 1.0));

   public FreeSlots() {
      super("Free Slots", "Empty inventory slots, red when you are nearly full.", "Top Left", 6, 148, 250);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      LocalPlayer player = hud.client.player;
      if (player == null) {
         return List.of();
      }

      int free = 0;
      for (var stack : player.getInventory().getNonEquipmentItems()) {
         if (stack.isEmpty()) {
            free++;
         }
      }

      int total = Inventory.INVENTORY_SIZE;
      int color = free <= this.warnBelow.intValue() ? -43691 : -6641995;
      return List.of(new TextHudWidget.Line("Free slots " + free + "/" + total, color));
   }
}
