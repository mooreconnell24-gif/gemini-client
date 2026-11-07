package com.inferno.fabric.mixin;

import com.inferno.fabric.checks.ClientBrands;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla servers read the client's brand ("vanilla", "fabric", ...) and throw it away. Keep it so the
 * ClientBrand check can look at it when the player joins. Runs on the netty thread; ClientBrands is
 * thread-safe.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonBrandMixin {
   @Shadow
   protected abstract GameProfile playerProfile();

   @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V", at = @At("HEAD"))
   private void inferno$brand(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
      if (packet.payload() instanceof BrandPayload brand) {
         GameProfile profile = this.playerProfile();
         if (profile != null) {
            ClientBrands.put(profile.id(), brand.brand());
         }
      }
   }
}
