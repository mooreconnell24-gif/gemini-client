package gemini.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Quick Move turn the mouse position into a slot: slot positions are relative to the screen's top-left corner. */
@Mixin({AbstractContainerScreen.class})
public interface AbstractContainerScreenAccessor {
   @Accessor("leftPos")
   int geminiLeftPos();

   @Accessor("topPos")
   int geminiTopPos();
}
