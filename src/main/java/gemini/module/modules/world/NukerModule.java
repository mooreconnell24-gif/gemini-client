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
   private final ModeSetting shape = this.addSetting(new ModeSetting("Shape", "Which blocks count: Sphere = everything in range, Flat = your feet level and the layer below, Above = your feet level and up", "Sphere", "Sphere", "Flat", "Above"));
   private final ModeSetting order = this.addSetting(new ModeSetting("Order", "Which block goes first: the nearest, or the highest (so nothing falls on you)", "Nearest", "Nearest", "Top Down"));
   private final BooleanSetting exposedOnly = this.addSetting(new BooleanSetting("Exposed Only", "Skip blocks fully buried inside others", true));
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand while breaking", true));
   private BlockPos current;
   /** Ticks the current block has been mined, and how many it may take before it is given up as stuck. */
   private int currentTicks;
   private int currentLimit;
   /** Ticks to skip scanning after a scan that found nothing (2197 block reads at range 6). */
   private int idle;

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
      this.idle = 0;
   }

   /** The block being broken belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.current = null;
      this.idle = 0;
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
            if (!state.isAir()
               && ++this.currentTicks <= this.currentLimit
               && !(eye.distanceToSqr(Vec3.atCenterOf(this.current)) > (reach + 1.0) * (reach + 1.0))) {
               BlockBreaking.keep();
               client.gameMode.continueDestroyBlock(this.current, face(player, this.current));
               if (this.swing.get()) {
                  Swing.attack(player, InteractionHand.MAIN_HAND);
               }

               return;
            }

            this.current = null;
         }

         if (this.idle > 0) {
            this.idle--;
            return;
         }

         List<BlockPos> targets = this.collect(client, player, eye, reach);
         if (targets.isEmpty()) {
            this.idle = 5;
         } else {
            int limit = player.isCreative() ? this.perTick.intValue() : 1;

            for (int i = 0; i < Math.min(limit, targets.size()); i++) {
               BlockPos pos = targets.get(i);
               BlockBreaking.keep();
               client.gameMode.startDestroyBlock(pos, face(player, pos));
               if (!player.isCreative() && !client.level.getBlockState(pos).isAir()) {
                  // Not broken in one hit: keep at it, but give up after twice the expected time (the server may
                  // be refusing the break, or the tool changed) so the nuker never freezes on one block.
                  this.current = pos;
                  this.currentTicks = 0;
                  float progress = client.level.getBlockState(pos).getDestroyProgress(player, client.level, pos);
                  this.currentLimit = progress <= 0.0F ? 0 : (int)Math.min(6000.0F, 2.0F / progress + 20.0F);
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

      boolean flat = this.shape.is("Flat");
      boolean above = this.shape.is("Above");
      boolean exposedOnly = this.exposedOnly.get();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int x = -r; x <= r; x++) {
         for (int y = -r; y <= r; y++) {
            int blockY = origin.getY() + y;
            if (flat && blockY != feetY - 1 && blockY != feetY || above && blockY < feetY) {
               continue;
            }

            for (int z = -r; z <= r; z++) {
               pos.set(origin.getX() + x, blockY, origin.getZ() + z);
               if (!(eye.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > reach * reach)) {
                  BlockState state = client.level.getBlockState(pos);
                  if (!state.isAir()
                     && state.getFluidState().isEmpty()
                     && !(state.getDestroySpeed(client.level, pos) < 0.0F)
                     && (!exposedOnly || exposed(client, pos))) {
                     result.add(pos.immutable());
                  }
               }
            }
         }
      }

      Comparator<BlockPos> byDistance = Comparator.comparingDouble(posx -> eye.distanceToSqr(posx.getX() + 0.5, posx.getY() + 0.5, posx.getZ() + 0.5));
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
