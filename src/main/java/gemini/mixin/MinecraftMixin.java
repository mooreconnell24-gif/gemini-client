package gemini.mixin;

import gemini.Gemini;
import gemini.module.modules.player.FastPlaceModule;
import gemini.module.modules.player.MultiTask;
import gemini.util.SingleplayerGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Minecraft.class})
public class MinecraftMixin {
   @Shadow
   private int rightClickDelay;

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")},
      require = 0
   )
   private void gemini$fastPlace(CallbackInfo ci) {
      Minecraft self = (Minecraft)(Object)this;
      SingleplayerGate.tick(self);
      if (Gemini.getInstance() != null) {
         Gemini.getInstance().modules().byClass(FastPlaceModule.class).ifPresent(module -> {
            if (module.isEnabled()) {
               int limit = module.limit(self);
               if (this.rightClickDelay > limit) {
                  this.rightClickDelay = limit;
               }
            }
         });
      }
   }

   /**
    * Multi Task, attacking: while the player uses an item vanilla takes the first branch of {@code handleKeybinds},
    * which releases the item when the use key is up and then throws every attack and use click away. Answering
    * "not using" sends it down the normal branch instead, where attack clicks reach {@code startAttack}. The two
    * things the skipped branch did that still matter are done here: releasing the item, and discarding use clicks
    * (the item is already in use, so a click must not start a second use).
    */
   @Redirect(
      method = {"handleKeybinds"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z",
         ordinal = 0
      ),
      require = 0
   )
   private boolean gemini$multiTaskAttack(LocalPlayer player) {
      boolean using = player.isUsingItem();
      if (!using || !MultiTask.attackWhileUsing()) {
         return using;
      }

      Minecraft self = (Minecraft)(Object)this;
      if (!self.options.keyUse.isDown() && self.gameMode != null) {
         self.gameMode.releaseUsingItem(player);
      }

      while (self.options.keyUse.consumeClick()) {
      }

      return false;
   }

   /** Multi Task, mining: {@code continueAttack} gives up on the block in progress while the player uses an item. */
   @Redirect(
      method = {"continueAttack"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"
      ),
      require = 0
   )
   private boolean gemini$multiTaskMine(LocalPlayer player) {
      return player.isUsingItem() && !MultiTask.mineWhileUsing();
   }
}
