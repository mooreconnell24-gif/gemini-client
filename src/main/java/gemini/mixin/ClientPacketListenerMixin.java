package gemini.mixin;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.modules.VelocityModule;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public abstract class ClientPacketListenerMixin {
   @Inject(
      method = {"sendChat"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$interceptSendChat(String message, CallbackInfo ci) {
      if (message != null && (message.startsWith(".") || message.startsWith("#")) && !CommandManager.rawSending()) {
         if (Gemini.getInstance() != null && Gemini.getInstance().commands() != null) {
            Gemini.getInstance().commands().handle(message);
         }

         ci.cancel();
      }
   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("RETURN")},
      require = 0
   )
   private void gemini$onServerPositionSetback(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      if (client.isSameThread() && client.player != null) {
         MoveUtil.onServerSetback(client.player);
      }
   }

   @Inject(
      method = {"handleSetEntityMotion"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$velocity(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      if (client.isSameThread() && Gemini.getInstance() != null && client.player != null && packet.id() == client.player.getId()) {
         VelocityModule velocity = Gemini.getInstance().modules().byClass(VelocityModule.class).orElse(null);
         if (velocity != null && velocity.isEnabled() && velocity.roll()) {
            if (velocity.jumpOnHit() && client.player.onGround() && !client.player.isInWater()) {
               client.player.jumpFromGround();
            }

            Vec3 movement = packet.movement();
            double horizontal = velocity.horizontalPercent();
            double vertical = velocity.verticalPercent();
            if (velocity.reverse()) {
               double strength = horizontal <= 0.0 ? 1.0 : horizontal;
               client.player.setDeltaMovement(-movement.x * strength, movement.y * vertical, -movement.z * strength);
               ci.cancel();
            } else if (horizontal <= 0.0 && vertical <= 0.0) {
               ci.cancel();
            } else if (horizontal < 1.0 || vertical < 1.0) {
               client.player.setDeltaMovement(movement.x * horizontal, movement.y * vertical, movement.z * horizontal);
               ci.cancel();
            }
         }
      }
   }

   @Inject(
      method = {"handleExplosion"},
      at = {@At("RETURN")},
      require = 0
   )
   private void gemini$explosionKnockback(ClientboundExplodePacket packet, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      if (client.isSameThread() && Gemini.getInstance() != null && client.player != null && !packet.playerKnockback().isEmpty()) {
         VelocityModule velocity = Gemini.getInstance().modules().byClass(VelocityModule.class).orElse(null);
         if (velocity != null && velocity.isEnabled() && velocity.roll()) {
            Vec3 knockback = (Vec3)packet.playerKnockback().get();
            client.player.setDeltaMovement(client.player.getDeltaMovement().subtract(knockback));
            double horizontal = velocity.horizontalPercent();
            double vertical = velocity.verticalPercent();
            if (velocity.reverse()) {
               double strength = horizontal <= 0.0 ? 1.0 : horizontal;
               client.player.addDeltaMovement(new Vec3(-knockback.x * strength, knockback.y * vertical, -knockback.z * strength));
            } else if (horizontal >= 1.0 && vertical >= 1.0) {
               client.player.addDeltaMovement(knockback);
            } else {
               client.player.addDeltaMovement(new Vec3(knockback.x * horizontal, knockback.y * vertical, knockback.z * horizontal));
               if (velocity.jumpOnHit() && client.player.onGround() && !client.player.isInWater()) {
                  client.player.jumpFromGround();
               }
            }
         }
      }
   }
}
