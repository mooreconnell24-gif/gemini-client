package gemini.module.modules.world;

import gemini.util.BlockPlacer;
import gemini.util.Hands;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for the world modules (formerly statics on the module container classes). */
public final class WorldHelpers {
   private WorldHelpers() {
   }

   static boolean ready(Minecraft client) {
      LocalPlayer player = client.player;
      return player != null
         && client.level != null
         && client.gameMode != null
         && client.getConnection() != null
         && !player.isSpectator()
         && !player.isDeadOrDying();
   }

   /** Like {@link #ready} but also requires no screen to be open (the farming/placing modules). */
   static boolean readyNoScreen(Minecraft client) {
      LocalPlayer player = client.player;
      return player != null
         && client.level != null
         && client.gameMode != null
         && client.gui.screen() == null
         && !player.isSpectator()
         && !player.isDeadOrDying();
   }

   static boolean withinReach(LocalPlayer player, Entity entity, double reach) {
      return entity.getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach;
   }

   static boolean useOnEntity(Minecraft client, LocalPlayer player, Entity target, Predicate<ItemStack> filter, boolean swing) {
      int slot = BlockPlacer.pickSlot(player, filter);
      if (slot < 0 || !Hands.claimMainHand()) {
         return false;
      } else {
         int previous = player.getInventory().getSelectedSlot();
         if (slot != previous) {
            player.getInventory().setSelectedSlot(slot);
         }

         InteractionResult result = client.gameMode.interact(player, target, new EntityHitResult(target), InteractionHand.MAIN_HAND);
         if (swing) {
            player.swing(InteractionHand.MAIN_HAND);
         }

         if (slot != previous) {
            player.getInventory().setSelectedSlot(previous);
         }

         return result.consumesAction();
      }
   }

   static Direction faceToward(LocalPlayer player, BlockPos pos) {
      Vec3 eye = player.getEyePosition();
      return Direction.getApproximateNearest(eye.x - (pos.getX() + 0.5), eye.y - (pos.getY() + 0.5), eye.z - (pos.getZ() + 0.5));
   }

   static void trim(Int2LongOpenHashMap map) {
      if (map.size() > 512) {
         map.clear();
      }
   }
}
