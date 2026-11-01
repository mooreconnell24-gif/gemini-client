package gemini.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class GeminiMark {
   public static final Identifier LOGO = Identifier.fromNamespaceAndPath("gemini", "textures/gui/logo.png");

   private GeminiMark() {
   }

   public static void draw(GuiGraphicsExtractor graphics, int x, int y, int size, int color, float pulse) {
      graphics.blit(LOGO, x, y, x + size, y + size, 0.0F, 1.0F, 0.0F, 1.0F);
   }

   public static int withAlpha(int color, int alpha) {
      return Math.max(0, Math.min(255, alpha)) << 24 | color & 16777215;
   }
}
