package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class Collisions extends Module {
   private static Collisions instance;
   private final BooleanSetting magma = this.addSetting(new BooleanSetting("Magma", "Walk over magma as if it were a raised block", true));
   private final BooleanSetting cactus = this.addSetting(new BooleanSetting("Cactus", "Treat cacti as full solid blocks", true));
   private final BooleanSetting berries = this.addSetting(new BooleanSetting("Berry Bushes", "Treat sweet berry bushes as solid", true));
   private final BooleanSetting fire = this.addSetting(new BooleanSetting("Fire", "Treat fire and campfires as solid", true));
   private final BooleanSetting powderSnow = this.addSetting(new BooleanSetting("Powder Snow", "Walk on powder snow", true));
   private final BooleanSetting cobweb = this.addSetting(new BooleanSetting("Cobwebs", "Treat cobwebs as solid", false));
   private final BooleanSetting lava = this.addSetting(new BooleanSetting("Lava", "Treat lava as solid and walk over it", false));
   private final BooleanSetting ignoreSneak = this.addSetting(
      new BooleanSetting("Off While Sneaking", "Use normal block collisions while sneaking", true)
   );
   private static final VoxelShape RAISED = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.25, 1.0);

   public Collisions() {
      super("Collisions", "Make dangerous blocks solid so you can walk over them.", Category.WORLD);
      instance = this;
   }

   public static VoxelShape replacement(BlockState state) {
      Collisions module = instance;
      if (module != null && module.isEnabled()) {
         Minecraft client = Minecraft.getInstance();
         if (module.ignoreSneak.get() && client.player != null && client.player.isShiftKeyDown()) {
            return null;
         } else {
            Block block = state.getBlock();
            if (block == Blocks.MAGMA_BLOCK) {
               return module.magma.get() ? RAISED : null;
            } else if (block == Blocks.CACTUS || block == Blocks.CACTUS_FLOWER) {
               return module.cactus.get() ? Shapes.block() : null;
            } else if (block == Blocks.SWEET_BERRY_BUSH) {
               return module.berries.get() ? Shapes.block() : null;
            } else if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) {
               return module.fire.get() ? Shapes.block() : null;
            } else if (block == Blocks.POWDER_SNOW) {
               return module.powderSnow.get() ? Shapes.block() : null;
            } else if (block == Blocks.COBWEB) {
               return module.cobweb.get() ? Shapes.block() : null;
            } else if (block == Blocks.LAVA) {
               return module.lava.get() ? Shapes.block() : null;
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }
}
