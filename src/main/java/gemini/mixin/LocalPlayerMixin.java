package gemini.mixin;

import gemini.module.modules.player.LiquidInteract;
import gemini.module.modules.render.NoRenderModule;
import gemini.util.MoveUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LocalPlayer.class})
public abstract class LocalPlayerMixin {
   @ModifyArg(
      method = {"pick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"
      )},
      index = 2
   )
   private static boolean gemini$targetLiquids(boolean includeFluids) {
      return includeFluids || LiquidInteract.active();
   }

   @Inject(
      method = {"aiStep"},
      at = {@At("HEAD")},
      require = 0
   )
   private void gemini$interpolateMovement(CallbackInfo ci) {
      MoveUtil.tickInterpolation((LocalPlayer)(Object)this);
   }

   /** Hides the totem-of-undying pop. In Minecraft 26.3 this moved from the screen-effect renderer to LocalPlayer. */
   @Inject(
      method = {"displayItemActivation"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noTotemPop(ItemStack stack, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideTotem() && stack.is(Items.TOTEM_OF_UNDYING)) {
         ci.cancel();
      }
   }
}
