package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.util.PlaceSettings;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class SelfTrap extends Module {
   private final PlaceSettings placer = new PlaceSettings(this, 1, 3, 4.5);

   public SelfTrap() {
      super("SelfTrap", "Encase your upper body and head in obsidian.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (CombatHelpers.ready(client) && client.gui.screen() == null && this.placer.ready()) {
         BlockPos feet = client.player.blockPosition();
         List<BlockPos> positions = List.of(feet.above().north(), feet.above().south(), feet.above().east(), feet.above().west(), feet.above(2));
         this.placer.place(client, positions, CombatHelpers.obsidianOrResistant());
      }
   }
}
