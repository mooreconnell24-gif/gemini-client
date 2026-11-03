package gemini.module.modules.player;

import gemini.module.Category;
import gemini.util.Hands;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import gemini.event.ActionPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class AutoClutch extends Module {
   private final NumberSetting minFall = this.addSetting(new NumberSetting("Min Fall", "Minimum fall distance before clutching", 3.5, 2.0, 12.0, 0.5));
   private final BooleanSetting pickupWater = this.addSetting(
      new BooleanSetting("Pickup Water", "Automatically scoop placed water back up on landing", true)
   );
   private final BooleanSetting silentAim = this.addSetting(new BooleanSetting("Silent Aim", "Aim downward only in packets", true));
   private BlockPos placedWaterPos;
   private int pickupTicks;

   public AutoClutch() {
      super("AutoClutch", "Auto MLG clutch with water, cobweb, slime, or hay.", Category.PLAYER);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.CRITICAL_SURVIVAL;
   }

   @Override
   public void onDisable() {
      this.placedWaterPos = null;
      this.pickupTicks = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (PlayerHelpers.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         Level level = client.level;
         if (this.placedWaterPos != null) {
            if (this.pickupTicks-- <= 0 || !this.pickupWater.get()) {
               this.placedWaterPos = null;
            } else if (player.onGround() || player.isInWater()) {
               int bucketSlot = Inventories.findHotbar(player, s -> s.is(Items.BUCKET));
               if (bucketSlot >= 0 && level.getBlockState(this.placedWaterPos).is(Blocks.WATER) && Hands.claimMainHand()) {
                  RotationManager.request(player, player.getYRot(), 90.0F, 180.0F, this.silentAim.get(), ActionPriority.CRITICAL_SURVIVAL, 2.0F);
                  int prev = player.getInventory().getSelectedSlot();
                  player.getInventory().setSelectedSlot(bucketSlot);
                  RotationManager.useWithAim(player, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND));
                  player.getInventory().setSelectedSlot(prev);
               }

               this.placedWaterPos = null;
            }
         } else {
            if (!player.onGround()
               && !player.isInWater()
               && !player.isFallFlying()
               && player.fallDistance >= this.minFall.floatValue()
               && player.getDeltaMovement().y < -0.25) {
               BlockPos feet = player.blockPosition();
               BlockPos ground = null;

               for (int dy = 1; dy <= 4; dy++) {
                  BlockPos check = feet.below(dy);
                  if (!level.getBlockState(check).getCollisionShape(level, check).isEmpty()) {
                     ground = check;
                     break;
                  }
               }

               if (ground != null) {
                  int clutchSlot = findClutchSlot(player, level.dimension() == Level.NETHER);
                  if (clutchSlot >= 0 && Hands.claimMainHand()) {
                     RotationManager.request(player, player.getYRot(), 90.0F, 180.0F, this.silentAim.get(), ActionPriority.CRITICAL_SURVIVAL, 3.0F);
                     ItemStack stack = player.getInventory().getItem(clutchSlot);
                     int prev = player.getInventory().getSelectedSlot();
                     player.getInventory().setSelectedSlot(clutchSlot);
                     if (!stack.is(Items.WATER_BUCKET) && !stack.is(Items.POWDER_SNOW_BUCKET)) {
                        Vec3 hit = Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0);
                        client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, ground, false));
                        player.swing(InteractionHand.MAIN_HAND);
                     } else {
                        RotationManager.useWithAim(player, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND));
                        if (stack.is(Items.WATER_BUCKET)) {
                           this.placedWaterPos = ground.above();
                           this.pickupTicks = 12;
                        }
                     }

                     player.getInventory().setSelectedSlot(prev);
                  }
               }
            }
         }
      }
   }

   private static int findClutchSlot(LocalPlayer player, boolean inNether) {
      if (!inNether) {
         int water = Inventories.findHotbar(player, s -> s.is(Items.WATER_BUCKET));
         if (water >= 0) {
            return water;
         }
      }

      int snow = Inventories.findHotbar(player, s -> s.is(Items.POWDER_SNOW_BUCKET));
      if (snow >= 0) {
         return snow;
      } else {
         int web = Inventories.findHotbar(player, s -> s.is(Items.COBWEB) || s.is(Items.SLIME_BLOCK) || s.is(Items.HAY_BLOCK));
         return web >= 0 ? web : Inventories.findHotbar(player, s -> s.is(Items.TWISTING_VINES) || s.is(Items.SCAFFOLDING));
      }
   }
}
