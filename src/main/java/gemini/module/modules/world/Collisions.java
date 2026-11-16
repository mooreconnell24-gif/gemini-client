package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
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
      super("Collisions", "Make dangerous blocks solid for you (not for mobs or items) so you can walk over them.", Category.WORLD);
      instance = this;
   }

   /**
    * The shape {@code state} should have for this collision check, or null for the normal one. Only collisions of
    * you yourself change: the integrated server runs in the same game, so without the check every mob, item and
    * the server's own pathfinding would also walk on lava and magma. Your player on the server side (same id)
    * counts as you, so the server agrees with where your client puts you.
    */
   public static VoxelShape replacement(BlockState state, CollisionContext context) {
      Collisions module = instance;
      if (module != null && module.isEnabled() && context instanceof EntityCollisionContext entityContext) {
         Entity entity = entityContext.getEntity();
         LocalPlayer self = Minecraft.getInstance().player;
         if (entity == null || self == null || entity != self && !(entity instanceof Player && entity.getUUID().equals(self.getUUID()))) {
            return null;
         } else if (module.ignoreSneak.get() && entity.isShiftKeyDown()) {
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
