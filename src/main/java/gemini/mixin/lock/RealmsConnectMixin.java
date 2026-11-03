package gemini.mixin.lock;

import com.mojang.realmsclient.dto.RealmsServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsConnect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({RealmsConnect.class})
public abstract class RealmsConnectMixin {
   @Shadow
   @Final
   private Screen onlineScreen;

   @Inject(
      method = {"connect"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void gemini$singleplayerOnly(RealmsServer server, ServerAddress address, CallbackInfo ci) {
      ci.cancel();
      Minecraft.getInstance()
         .gui
         .setScreen(
            new DisconnectedScreen(
               this.onlineScreen,
               Component.literal("Gemini Client"),
               Component.literal(
                  "Gemini Client only runs in single-player worlds, so server connections are blocked while it is installed. Remove the Gemini jar from your mods folder to play on servers."
               )
            )
         );
   }
}
