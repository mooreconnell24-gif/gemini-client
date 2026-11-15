package gemini.module.modules.combat;

import gemini.event.ActionPriority;
import gemini.util.Hands;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import gemini.util.Swing;
import gemini.util.VersionBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * End-crystal placement rules and actions shared by the crystal-using modules added in 3.3.0 (Auto City, Hit
 * Crystal): where a crystal may go, putting one there with the hotbar slot restored, and setting it off.
 */
final class CrystalSpots {
   private CrystalSpots() {
   }

   static boolean isBase(BlockState state) {
      return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
   }

   /** The space a crystal on {@code base} takes up: the two blocks above it. */
   static AABB crystalSpace(BlockPos base) {
      BlockPos above = base.above();
      return new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0);
   }

   /** Room for a crystal above {@code base} (whatever the base is): air above it and no entity in the way. */
   static boolean roomAbove(Level level, BlockPos base) {
      return VersionBridge.canPlaceCrystalSpace(level, base)
         && level.getEntities((Entity)null, crystalSpace(base), entity -> entity.isAlive() && !entity.isSpectator()).isEmpty();
   }

   /** A crystal can be placed on {@code base} right now. */
   static boolean placeable(Level level, BlockPos base) {
      return isBase(level.getBlockState(base)) && roomAbove(level, base);
   }

   /** Where the explosion of a crystal on {@code base} happens. */
   static Vec3 center(BlockPos base) {
      return new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
   }

   static boolean hasCrystal(LocalPlayer player) {
      return player.getOffhandItem().is(Items.END_CRYSTAL) || Inventories.findHotbar(player, stack -> stack.is(Items.END_CRYSTAL)) >= 0;
   }

   /**
    * Puts a crystal on {@code base} from the offhand or the hotbar. A hotbar crystal is selected for the click only
    * and the slot that was held is selected again straight after. Returns whether the click went through.
    */
   static boolean place(Minecraft client, LocalPlayer player, BlockPos base, boolean silentAim, ActionPriority priority) {
      Vec3 hitVec = center(base);
      if (!RotationManager.request(player, RotationManager.toward(player, hitVec), 0.0F, silentAim, priority)) {
         return false;
      }

      BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, base, false);
      if (player.getOffhandItem().is(Items.END_CRYSTAL)) {
         boolean used = client.gameMode.useItemOn(player, InteractionHand.OFF_HAND, hit).consumesAction();
         Swing.interact(player, InteractionHand.OFF_HAND);
         return used;
      }

      int slot = Inventories.findHotbar(player, stack -> stack.is(Items.END_CRYSTAL));
      if (slot < 0 || !Hands.claimMainHand()) {
         return false;
      }

      boolean[] used = new boolean[1];
      Inventories.withHotbarSlot(player, slot, () -> {
         used[0] = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction();
         Swing.interact(player, InteractionHand.MAIN_HAND);
      });
      return used[0];
   }

   /** The crystal standing on {@code base}, or null. */
   static Entity crystalOn(Level level, BlockPos base) {
      for (Entity entity : level.getEntities((Entity)null, crystalSpace(base), e -> e.getType() == EntityTypes.END_CRYSTAL && e.isAlive())) {
         return entity;
      }

      return null;
   }

   /** Hits the crystal so it explodes. Returns whether the attack was sent. */
   static boolean detonate(Minecraft client, LocalPlayer player, Entity crystal, boolean silentAim, ActionPriority priority) {
      if (!RotationManager.request(player, RotationManager.toward(player, crystal), 0.0F, silentAim, priority)) {
         return false;
      }

      client.gameMode.attack(player, crystal);
      Swing.attack(player, InteractionHand.MAIN_HAND);
      return true;
   }
}
