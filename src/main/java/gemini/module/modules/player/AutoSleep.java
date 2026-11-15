package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.util.Swing;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class AutoSleep extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Blocks around you to look for a bed", 4.0, 1.0, 6.0, 0.5));
   private final BooleanSetting rotate = this.addSetting(new BooleanSetting("Silent Aim", "Aim at the bed server-side only", true));
   private final BooleanSetting disableAfter = this.addSetting(new BooleanSetting("Disable After", "Turn off once you are lying down", false));
   private int wait;

   public AutoSleep() {
      super("Auto Sleep", "Hop into the nearest bed when night falls.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         Level level = client.level;
         if (player.isSleeping()) {
            if (this.disableAfter.get()) {
               this.setEnabled(false, false);
            }
         } else if (level.dimension() == Level.OVERWORLD && level.isDarkOutside() && this.wait-- <= 0) {
            this.wait = 10;
            int r = (int)Math.ceil(this.range.get());
            BlockPos center = player.blockPosition();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;

            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 2, r))) {
               if (level.getBlockState(pos).is(BlockTags.BEDS) && BlockPlacer.inReach(player, pos, this.range.get())) {
                  double distance = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = pos.immutable();
                  }
               }
            }

            if (best != null && RotationManager.request(player, RotationManager.toward(player, best), 0.0F, this.rotate.get(), ActionPriority.WORLD_UTILITY)) {
               Vec3 hit = Vec3.atCenterOf(best).add(0.0, 0.5, 0.0);
               client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, best, false));
               Swing.interact(player, InteractionHand.MAIN_HAND);
            }
         }
      }
   }
}
