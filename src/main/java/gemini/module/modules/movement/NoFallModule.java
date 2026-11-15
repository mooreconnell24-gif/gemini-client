package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.util.Hands;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import gemini.event.ActionPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class NoFallModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting(
         "Mode",
         "Spoof marks falling moves as on-ground (safe at any speed); Packet sends an extra ground packet; MLG clutches with a water bucket",
         "Spoof",
         "Packet",
         "Spoof",
         "MLG"
      )
   );
   // Vanilla starts hurting at 3 blocks, so anything above that would let damage through instead of preventing it.
   private final NumberSetting distance = this.addSetting(
      new NumberSetting("Distance", "Start preventing damage after falling this far (vanilla hurts from 3)", 2.0, 0.5, 2.9, 0.1)
   );
   private boolean replacing;
   private int pickup;

   public NoFallModule() {
      super("NoFall", "Cancel fall damage.", Category.MOVEMENT);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.CRITICAL_SURVIVAL;
   }

   @Override
   public void onDisable() {
      this.pickup = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && player.connection != null && client.level != null && !player.getAbilities().flying && !player.isFallFlying()) {
         if (this.pickup <= 0) {
            // Send the ground packet before the next tick would push the server past the safe distance;
            // waiting until we are already past it is what made the old Packet mode still hurt.
            double nextDrop = Math.abs(Math.min(0.0, player.getDeltaMovement().y)) + 0.08;
            boolean aboutToHurt = player.fallDistance > 0.0F && player.fallDistance + nextDrop >= this.distance.get();
            if (aboutToHurt
               && player.getDeltaMovement().y < -0.1
               && (!this.mode.is("MLG") || !this.placeWater(client, player))
               && !this.mode.is("Spoof")) {
               player.connection.send(new StatusOnly(true, player.horizontalCollision));
               player.resetFallDistance();
            }
         } else {
            this.pickup--;
            if (player.isInWater() || wet(client.level, player.blockPosition())) {
               this.scoop(client, player);
               this.pickup = 0;
            }
         }
      }
   }

   @Override
   public boolean onSendPacket(Packet<?> packet) {
      if (this.mode.is("Spoof") && !this.replacing && packet instanceof ServerboundMovePlayerPacket move && !move.isOnGround()) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         ClientPacketListener connection = client.getConnection();
         if (player != null && connection != null && !(player.fallDistance <= this.distance.get()) && !player.getAbilities().flying) {
            this.replacing = true;

            try {
               if (move.hasPosition() && move.hasRotation()) {
                  connection.send(
                     new PosRot(
                        move.getX(player.getX()),
                        move.getY(player.getY()),
                        move.getZ(player.getZ()),
                        move.getYRot(player.getYRot()),
                        move.getXRot(player.getXRot()),
                        true,
                        move.horizontalCollision()
                     )
                  );
               } else if (move.hasPosition()) {
                  connection.send(new Pos(move.getX(player.getX()), move.getY(player.getY()), move.getZ(player.getZ()), true, move.horizontalCollision()));
               } else if (move.hasRotation()) {
                  connection.send(new Rot(move.getYRot(player.getYRot()), move.getXRot(player.getXRot()), true, move.horizontalCollision()));
               } else {
                  connection.send(new StatusOnly(true, move.horizontalCollision()));
               }
            } finally {
               this.replacing = false;
            }

            player.resetFallDistance();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean placeWater(Minecraft client, LocalPlayer player) {
      if (client.level != null && client.gui.screen() == null && Inventories.findHotbar(player, stack -> stack.is(Items.WATER_BUCKET)) >= 0) {
         int gap = 0;
         BlockPos feet = player.blockPosition();

         while (
            gap < 5
               && client.level.getBlockState(feet.below(gap)).getCollisionShape(client.level, feet.below(gap)).isEmpty()
               && !wet(client.level, feet.below(gap))
         ) {
            gap++;
         }

         if (gap != 0 && gap <= 4 && Hands.claimMainHand()) {
            RotationManager.request(player, player.getYRot(), 90.0F, 0.0F, true, ActionPriority.CRITICAL_SURVIVAL, 180.0F);
            int slot = Inventories.findHotbar(player, stack -> stack.is(Items.WATER_BUCKET));
            Inventories.withHotbarSlot(player, slot, () -> RotationManager.useWithAim(player, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND)));
            player.resetFallDistance();
            this.pickup = 10;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void scoop(Minecraft client, LocalPlayer player) {
      int slot = Inventories.findHotbar(player, stack -> stack.is(Items.BUCKET));
      if (slot >= 0 && client.gameMode != null && Hands.claimMainHand()) {
         RotationManager.request(player, player.getYRot(), 90.0F, 0.0F, true, ActionPriority.CRITICAL_SURVIVAL, 180.0F);
         Inventories.withHotbarSlot(player, slot, () -> RotationManager.useWithAim(player, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND)));
      }
   }

   private static boolean wet(Level level, BlockPos pos) {
      return level != null && level.getFluidState(pos).is(FluidTags.WATER);
   }

   @Override
   public String info() {
      return this.mode.get();
   }
}
