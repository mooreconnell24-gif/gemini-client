package gemini.mixin.lock;

import com.mojang.realmsclient.dto.RealmsServer;
import gemini.ui.SandboxUi;
import gemini.util.SingleplayerGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.realms.RealmsConnect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Realms joins do not go through the connect screen, so they get the same sandbox gate here. */
@Mixin(RealmsConnect.class)
public abstract class RealmsConnectMixin {
   @Shadow
   @Final
   private Screen onlineScreen;

   @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
   private void gemini$sandboxGate(RealmsServer server, ServerAddress address, CallbackInfo ci) {
      if (SingleplayerGate.connectionsBlocked()) {
         ci.cancel();
         SandboxUi.showBlocked(Minecraft.getInstance(), this.onlineScreen);
      }
   }
}
