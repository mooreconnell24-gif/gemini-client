package gemini.mixin;

import gemini.ui.GeminiTitleScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Shows {@link GeminiTitleScreen} wherever the vanilla title screen would appear.
 *
 * <p>The target is {@code Gui.setScreen} rather than {@code TitleScreen} itself because every way of reaching
 * the title screen ends there: the first screen after loading, "Back" and "Cancel" on other screens (they hand
 * over a title screen they kept or a new one), leaving a world, a disconnect, and closing a screen with no world
 * loaded (the method then makes a title screen itself). Swapping the argument before it becomes the current
 * screen means the vanilla screen is never initialised or drawn, so there is no flash and nothing to undo.
 *
 * <p>There is no replace loop: the swap only applies to the exact vanilla class, which the Gemini screen is not,
 * and the "Vanilla menu" link switches the option off before it opens the vanilla screen.
 * {@link gemini.ui.SandboxUi#onScreenInit} repeats the swap after the fact should either injection miss.
 */
@Mixin({Gui.class})
public abstract class TitleScreenMixin {
   /** A title screen handed to {@code setScreen}. */
   @ModifyVariable(
      method = {"setScreen"},
      at = @At("HEAD"),
      argsOnly = true,
      require = 0
   )
   private Screen gemini$swapTitleScreen(Screen screen) {
      return GeminiTitleScreen.swap(screen);
   }

   /** The title screen {@code setScreen(null)} creates by itself when no world is loaded (the first write to the argument). */
   @ModifyVariable(
      method = {"setScreen"},
      at = @At(value = "STORE", ordinal = 0),
      argsOnly = true,
      require = 0
   )
   private Screen gemini$swapDefaultTitleScreen(Screen screen) {
      return GeminiTitleScreen.swap(screen);
   }
}
