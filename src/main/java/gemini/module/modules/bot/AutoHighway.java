package gemini.module.modules.bot;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;

public final class AutoHighway extends Module {
   private final NumberSetting width = this.addSetting(new NumberSetting("Width", "Highway floor width", 3.0, 1.0, 5.0, 1.0));
   private final BooleanSetting rails = this.addSetting(new BooleanSetting("Guard Rails", "Place raised side curb blocks", true));

   public AutoHighway() {
      super("Auto Highway", "Pave a multi-block highway with side curbs ahead.", Category.WORLD);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && client.gui.screen() == null) {
         Direction forward = player.getDirection();
         Direction right = forward.getClockWise();
         BlockPos base = player.blockPosition().below().relative(forward);
         int half = this.width.intValue() / 2;

         for (int w = -half; w <= half; w++) {
            BlockPos tile = base.relative(right, w);
            BlockPlacer.place(
               client,
               tile,
               stack -> stack.getItem() instanceof BlockItem bi
                  && (bi.getBlock() == Blocks.OBSIDIAN || bi.getBlock() == Blocks.NETHERRACK || bi.getBlock() == Blocks.COBBLESTONE),
               true,
               true,
               true
            );
         }

         if (this.rails.get()) {
            BlockPlacer.place(client, base.relative(right, -half - 1).above(), BlockPlacer::solidFiller, true, true, true);
            BlockPlacer.place(client, base.relative(right, half + 1).above(), BlockPlacer::solidFiller, true, true, true);
         }
      }
   }
}
