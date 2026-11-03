package gemini.mixin.lock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ConnectScreen.class})
public abstract class ConnectScreenMixin {
   @Inject(
      method = {"startConnecting"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void gemini$singleplayerOnly(
      Screen parent, Minecraft minecraft, ServerAddress address, ServerData data, boolean quickPlay, TransferState transfer, CallbackInfo ci
   ) {
      ci.cancel();
      minecraft.gui
         .setScreen(
            new DisconnectedScreen(
               parent,
               Component.literal("Gemini Client"),
               Component.literal(
                  "Gemini Client only runs in single-player worlds, so server connections are blocked while it is installed. Remove the Gemini jar from your mods folder to play on servers."
               )
            )
         );
   }
}
