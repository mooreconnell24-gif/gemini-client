package gemini.mixin.lock;

import gemini.ui.SandboxUi;
import gemini.util.SingleplayerGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every way of joining a server ends up here; while the sandbox is on the join is replaced by an explanation. */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
   @Inject(method = "startConnecting", at = @At("HEAD"), cancellable = true)
   private static void gemini$sandboxGate(
      Screen parent, Minecraft minecraft, ServerAddress address, ServerData data, boolean quickPlay, TransferState transfer, CallbackInfo ci
   ) {
      if (SingleplayerGate.connectionsBlocked()) {
         ci.cancel();
         SandboxUi.showBlocked(minecraft, parent);
      }
   }
}
