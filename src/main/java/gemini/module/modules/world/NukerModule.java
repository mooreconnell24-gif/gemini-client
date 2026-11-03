package gemini.module.modules.world;

import gemini.util.Swing;
import gemini.util.BlockBreaking;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class NukerModule extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Break radius in blocks", 4.0, 1.0, 6.0, 0.5));
   private final NumberSetting perTick = this.addSetting(new NumberSetting("Blocks Per Tick", "Creative only: blocks started each tick", 3.0, 1.0, 12.0, 1.0));
   private final ModeSetting shape = this.addSetting(new ModeSetting("Shape", "Which blocks around you count", "Sphere", "Sphere", "Flat", "Above"));
   private final ModeSetting order = this.addSetting(new ModeSetting("Order", "Which block goes first", "Nearest", "Nearest", "Top Down"));
   private final BooleanSetting exposedOnly = this.addSetting(new BooleanSetting("Exposed Only", "Skip blocks fully buried inside others", true));
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand while breaking", true));
   private BlockPos current;

   public NukerModule() {
      super("Nuker", "Break nearby blocks around you.", Category.WORLD);
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      if (this.current != null && client.gameMode != null) {
         BlockBreaking.release();
         client.gameMode.stopDestroyBlock();
      }

      this.current = null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && client.gameMode != null && client.gui.screen() == null && !player.isSpectator()) {
         // The server refuses to break blocks beyond the player's block reach + 1, so going further only makes ghost blocks.
         double reach = Math.min(this.range.get(), player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0);
         Vec3 eye = player.getEyePosition();
         if (this.current != null) {
            BlockState state = client.level.getBlockState(this.current);
            if (!state.isAir() && !(eye.distanceToSqr(Vec3.atCenterOf(this.current)) > (reach + 1.0) * (reach + 1.0))) {
               BlockBreaking.keep();
               client.gameMode.continueDestroyBlock(this.current, face(player, this.current));
               if (this.swing.get()) {
                  Swing.attack(player, InteractionHand.MAIN_HAND);
               }

               return;
            }

            this.current = null;
         }

         List<BlockPos> targets = this.collect(client, player, eye, reach);
         if (!targets.isEmpty()) {
            int limit = player.isCreative() ? this.perTick.intValue() : 1;

            for (int i = 0; i < Math.min(limit, targets.size()); i++) {
               BlockPos pos = targets.get(i);
               BlockBreaking.keep();
               client.gameMode.startDestroyBlock(pos, face(player, pos));
               if (!player.isCreative()) {
                  this.current = pos;
               }
            }

            if (this.swing.get()) {
               Swing.attack(player, InteractionHand.MAIN_HAND);
            }
         }
      }
   }

   private List<BlockPos> collect(Minecraft client, LocalPlayer player, Vec3 eye, double reach) {
      List<BlockPos> result = new ArrayList<>();
      int r = (int)Math.ceil(reach);
      BlockPos origin = BlockPos.containing(eye);
      int feetY = player.blockPosition().getY();

      for (int x = -r; x <= r; x++) {
         for (int y = -r; y <= r; y++) {
            for (int z = -r; z <= r; z++) {
               BlockPos pos = origin.offset(x, y, z);
               if ((!this.shape.is("Flat") || pos.getY() == feetY - 1 || pos.getY() == feetY)
                  && (!this.shape.is("Above") || pos.getY() >= feetY)
                  && !(eye.distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach)) {
                  BlockState state = client.level.getBlockState(pos);
                  if (!state.isAir()
                     && state.getFluidState().isEmpty()
                     && !(state.getDestroySpeed(client.level, pos) < 0.0F)
                     && (!this.exposedOnly.get() || exposed(client, pos))) {
                     result.add(pos);
                  }
               }
            }
         }
      }

      Comparator<BlockPos> byDistance = Comparator.comparingDouble(posx -> eye.distanceToSqr(Vec3.atCenterOf(posx)));
      result.sort(this.order.is("Top Down") ? Comparator.<BlockPos>comparingInt(posx -> -posx.getY()).thenComparing(byDistance) : byDistance);
      return result;
   }

   private static boolean exposed(Minecraft client, BlockPos pos) {
      for (Direction direction : Direction.values()) {
         BlockState neighbour = client.level.getBlockState(pos.relative(direction));
         if (!neighbour.isSolidRender()) {
            return true;
         }
      }

      return false;
   }

   private static Direction face(LocalPlayer player, BlockPos pos) {
      Vec3 delta = player.getEyePosition().subtract(Vec3.atCenterOf(pos));
      return Direction.getApproximateNearest(delta.x, delta.y, delta.z);
   }
}
