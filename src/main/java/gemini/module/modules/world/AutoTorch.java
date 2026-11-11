package gemini.module.modules.world;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class AutoTorch extends Module {
   private final NumberSetting lightLevel = this.addSetting(
      new NumberSetting("Light Level", "Place a torch when block light is at or below this", 0.0, 0.0, 14.0, 1.0)
   );
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Blocks around you to check", 3.0, 1.0, 5.0, 1.0));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between torches", 5.0, 1.0, 40.0, 1.0));
   private final ModeSetting kind = this.addSetting(new ModeSetting("Torch", "Which torch to use", "Any", "Any", "Torch", "Soul Torch"));
   private final ListSetting avoid = this.addSetting(
      new ListSetting("Avoid", "Blocks never to place torches on", ListSetting.Kind.BLOCK, "farmland", "ice", "packed_ice", "blue_ice")
   );
   private final BooleanSetting onlyWhenStill = this.addSetting(new BooleanSetting("Only When Still", "Do not place while you are moving", false));
   private int wait;

   public AutoTorch() {
      super("Auto Torch", "Light up dark floors around you with torches.", Category.WORLD);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null && this.wait-- <= 0) {
         this.wait = this.delay.intValue();
         LocalPlayer player = client.player;
         Level level = client.level;
         if (!this.onlyWhenStill.get() || !MoveUtil.hasInput(player)) {
            int r = this.range.intValue();
            BlockPos center = player.blockPosition();
            BlockPos best = null;
            int darkest = Integer.MAX_VALUE;

            for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 1, r))) {
               BlockState state = level.getBlockState(cursor);
               BlockPos below = cursor.below();
               BlockState floor = level.getBlockState(below);
               if (state.isAir()
                  && floor.isFaceSturdy(level, below, Direction.UP)
                  && !this.avoid.blocks().contains(floor.getBlock())
                  && !cursor.equals(center)) {
                  int light = level.getBrightness(LightLayer.BLOCK, cursor);
                  if (light <= this.lightLevel.intValue() && BlockPlacer.inReach(player, cursor, 4.5) && !lit(level, cursor) && light < darkest) {
                     darkest = light;
                     best = cursor.immutable();
                  }
               }
            }

            if (best != null) {
               BlockPlacer.place(client, best, stack -> {
                  String s0$ = this.kind.get();

                  return switch (s0$) {
                     case "Torch" -> stack.is(Items.TORCH);
                     case "Soul Torch" -> stack.is(Items.SOUL_TORCH);
                     default -> stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH);
                  };
               }, true, true, false);
            }
         }
      }
   }

   private static boolean lit(Level level, BlockPos pos) {
      for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
         BlockState state = level.getBlockState(near);
         if (state.is(Blocks.TORCH) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
            return true;
         }
      }

      return false;
   }
}
