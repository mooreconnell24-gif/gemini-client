package gemini.module.modules.combat;

import java.util.ArrayList;
import gemini.module.Category;
import gemini.module.PlacementModuleBase;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.util.BlockPlacer;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

public final class Surround extends PlacementModuleBase {
   private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
   private final ModeSetting cover = this.addSetting(
      new ModeSetting(
         "Cover",
         "Feet places blocks around and under your feet; Head boxes in your head; Full does both, sealing you in completely",
         "Feet",
         "Feet",
         "Head",
         "Full"
      )
   );
   private final BooleanSetting anyBlock = this.addSetting(
      new BooleanSetting("Any Block", "Use any solid block from your hotbar instead of only blast-proof ones", false)
   );
   private final BooleanSetting center = this.addSetting(new BooleanSetting("Auto Center", "Move to the centre of your block when turned on", true));
   private final BooleanSetting support = this.addSetting(
      new BooleanSetting("Support Blocks", "Place an extra block where a spot has nothing to attach to: under a side, or beside the block over your head", true)
   );
   private final BooleanSetting onlyGround = this.addSetting(new BooleanSetting("Only Ground", "Only place while standing on the ground", true));
   private final BooleanSetting disableOnJump = this.addSetting(new BooleanSetting("Disable On Jump", "Turn off when you jump out of the hole", true));
   private int startY;
   private int capWait;

   public Surround() {
      super("Surround", "Places obsidian around your feet, your head or both to shield you from explosions.", Category.COMBAT, 0, 4, 4.5);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("SelfTrap");
      names.add("BlockIn");
      names.add("Self Trap");
      names.add("Block In");
      return names;
   }

   @Override
   public String info() {
      return this.cover.get();
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

      if (this.onlyGround.get() && !player.onGround()) {
         return false;
      }

      if (!this.cover.is("Feet") && this.support.get()) {
         this.placeCap(client, player);
      }

      return true;
   }

   @Override
   protected void collectTargets(Minecraft client, LocalPlayer player, List<BlockPos> targets) {
      BlockPos feet = player.blockPosition();
      if (!this.cover.is("Head")) {
         targets.add(feet.below());
         for (Direction dir : SIDES) {
            BlockPos side = feet.relative(dir);
            if (this.support.get() && BlockPlacer.findPlacement(client.level, side, false) == null) {
               targets.add(side.below());
            }

            targets.add(side);
         }
      }

      if (!this.cover.is("Feet")) {
         BlockPos head = feet.above();
         for (Direction dir : SIDES) {
            BlockPos side = head.relative(dir);
            if (this.support.get() && BlockPlacer.findPlacement(client.level, side, false) == null) {
               targets.add(feet.relative(dir));
            }

            targets.add(side);
         }

         targets.add(head.above());
      }
   }

   /**
    * The block over your head. From inside the box it touches none of the four blocks around your head, and the
    * one extra block it can hang from has no face you can see either, so the usual placement (which only clicks
    * faces your aim really hits) can never finish the box. Once the four head blocks are there, this clicks the
    * hidden faces directly: first the block beside the top, then the top itself.
    */
   private void placeCap(Minecraft client, LocalPlayer player) {
      if (this.capWait > 0) {
         this.capWait--;
         return;
      }

      BlockPos head = player.blockPosition().above();
      BlockPos top = head.above();
      if (!BlockPlacer.replaceable(client.level, top)) {
         return;
      }

      for (Direction dir : SIDES) {
         if (BlockPlacer.replaceable(client.level, head.relative(dir))) {
            return;
         }
      }

      BlockPos target = BlockPlacer.findPlacement(client.level, top, false) != null ? top : top.relative(SIDES[0]);
      BlockHitResult hit = BlockPlacer.findPlacement(client.level, target, false);
      if (hit != null && BlockPlacer.placeWithHit(client, target, hit, this.blocks(), this.placer.swing.get(), true, true)) {
         this.fade.record(target);
         this.capWait = 2;
      }
   }

   @Override
   protected Predicate<ItemStack> blocks() {
      return this.anyBlock.get() ? BlockPlacer::solidFiller : CombatHelpers.obsidianOrResistant();
   }
}
