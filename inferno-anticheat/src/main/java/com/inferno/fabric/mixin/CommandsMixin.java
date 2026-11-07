package com.inferno.fabric.mixin;

import com.inferno.fabric.InfernoMod;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandsMixin {
   @Inject(method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V", at = @At("HEAD"))
   private void inferno$command(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
      try {
         if (((CommandSourceStack)parse.getContext().getSource()).getEntity() instanceof ServerPlayer player) {
            InfernoMod.ENGINE.command(player, command);
         }
      } catch (RuntimeException e) {
         InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
      }
   }
}
