package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class NoFallModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Packet spoofs ground; Spoof rewrites moves; MLG clutches", "Packet", "Packet", "Spoof", "MLG")
   );
   private final NumberSetting distance = this.addSetting(new NumberSetting("Distance", "Start preventing damage after falling this far", 2.5, 1.5, 10.0, 0.5));
   private boolean replacing;
   private int pickup;

   public NoFallModule() {
      super("NoFall", "Cancels fall damage.", Category.MOVEMENT);
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
            if (!(player.fallDistance <= this.distance.get())
               && !(player.getDeltaMovement().y > -0.3)
               && (!this.mode.is("MLG") || !this.placeWater(client, player))
               && !this.mode.is("Spoof")) {
               player.connection.send(new StatusOnly(true, false));
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

         if (gap != 0 && gap <= 4) {
            RotationManager.request(player, player.getYRot(), 90.0F, 0.0F, true, 10, 180.0F);
            int slot = Inventories.findHotbar(player, stack -> stack.is(Items.WATER_BUCKET));
            int previous = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(slot);
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            player.getInventory().setSelectedSlot(previous);
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
      if (slot >= 0 && client.gameMode != null) {
         int previous = player.getInventory().getSelectedSlot();
         player.getInventory().setSelectedSlot(slot);
         client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
         player.getInventory().setSelectedSlot(previous);
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
