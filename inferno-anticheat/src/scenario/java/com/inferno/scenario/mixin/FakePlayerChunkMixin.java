package com.inferno.scenario.mixin;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only: fake players aren't registered with the chunk map, so don't let it track their moves. */
@Mixin(ServerChunkCache.class)
public abstract class FakePlayerChunkMixin {
   @Inject(method = "move(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), cancellable = true)
   private void scenarios$skipFake(ServerPlayer player, CallbackInfo ci) {
      if (player instanceof FakePlayer) {
         ci.cancel();
      }
   }
}
