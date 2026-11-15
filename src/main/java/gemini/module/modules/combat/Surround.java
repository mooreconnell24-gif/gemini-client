package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.PlacementModuleBase;
import gemini.module.setting.BooleanSetting;
import gemini.util.BlockPlacer;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public final class Surround extends PlacementModuleBase {
   private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
   private final BooleanSetting center = this.addSetting(new BooleanSetting("Auto Center", "Snap to block center on enable", true));
   private final BooleanSetting support = this.addSetting(
      new BooleanSetting("Support Blocks", "Place support blocks under air edges", true)
   );
   private final BooleanSetting onlyGround = this.addSetting(new BooleanSetting("Only Ground", "Only place while standing on ground", true));
   private final BooleanSetting disableOnJump = this.addSetting(new BooleanSetting("Disable On Jump", "Disable when jumping out of hole", true));
   private int startY;

   public Surround() {
      super("Surround", "Surround your feet with obsidian to block crystal damage.", Category.COMBAT, 0, 4, 4.5);
   }

   @Override
   public void onEnable() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         this.startY = player.getBlockY();
         if (this.center.get() && player.onGround()) {
            double cx = Math.floor(player.getX()) + 0.5;
            double cz = Math.floor(player.getZ()) + 0.5;
            player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
            player.setPos(cx, player.getY(), cz);
         }
      }
   }

   @Override
   protected boolean shouldPlace(Minecraft client, LocalPlayer player) {
      if (this.disableOnJump.get() && player.getBlockY() > this.startY) {
         this.setEnabled(false);
         return false;
      }

      return !this.onlyGround.get() || player.onGround();
   }

   @Override
   protected void collectTargets(Minecraft client, LocalPlayer player, List<BlockPos> targets) {
      BlockPos feet = player.blockPosition();
      targets.add(feet.below());
      for (Direction dir : SIDES) {
         BlockPos side = feet.relative(dir);
         if (this.support.get() && BlockPlacer.findPlacement(client.level, side, false) == null) {
            targets.add(side.below());
         }

         targets.add(side);
      }
   }

   @Override
   protected Predicate<ItemStack> blocks() {
      return CombatHelpers.obsidianOrResistant();
   }
}
