package gemini.ui.clickgui.controls;

import com.mojang.blaze3d.platform.InputConstants;
import gemini.input.Keys;
import gemini.ui.Glass;
import gemini.ui.font.UiFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small helpers shared by the setting controls and the screens that host them: modifier keys, the system
 * clipboard, faded text and a transparency checkerboard.
 */
public final class Controls {
   private Controls() {
   }

   public static boolean shiftDown() {
      return InputConstants.isKeyDown(Keys.LEFT_SHIFT) || InputConstants.isKeyDown(Keys.RIGHT_SHIFT);
   }

   public static boolean ctrlDown() {
      return InputConstants.isKeyDown(Keys.LEFT_CONTROL) || InputConstants.isKeyDown(Keys.RIGHT_CONTROL);
   }

   public static boolean altDown() {
      return InputConstants.isKeyDown(Keys.LEFT_ALT) || InputConstants.isKeyDown(Keys.RIGHT_ALT);
   }

   public static Font font() {
      return Minecraft.getInstance().font;
   }

   /** The system clipboard's text, never null. */
   public static String clipboard() {
      try {
         String text = Minecraft.getInstance().keyboardHandler.getClipboard();
         return text == null ? "" : text;
      } catch (RuntimeException e) {
         return "";
      }
   }

   public static void setClipboard(String text) {
      if (text != null) {
         try {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
         } catch (RuntimeException ignored) {
            // no clipboard on this platform: copying is simply unavailable
         }
      }
   }

   /**
    * Draws text whose alpha is multiplied by {@code opacity} (0..1). Nearly transparent text is skipped, because
    * the font renderer treats a very low alpha as "opaque".
    */
   public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, float opacity) {
      int alpha = Math.round((color >>> 24) * Math.max(0.0F, Math.min(1.0F, opacity)));
      if (text != null && alpha >= 10) {
         UiFont.draw(g, text, x, y, alpha << 24 | color & 0xFFFFFF, false);
      }
   }

   /** The text cut down with an ellipsis so it is no wider than {@code maxWidth} (see {@link UiFont#ellipsize}). */
   public static String fit(Font font, String text, int maxWidth) {
      return UiFont.ellipsize(text, maxWidth);
   }

   /** A light/dark checkerboard (the usual "this is transparent" backdrop) in at most a few dozen fills. */
   public static void checker(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int cell, float opacity) {
      if (x2 <= x1 || y2 <= y1) {
         return;
      }

      g.fill(x1, y1, x2, y2, Glass.fade(0xFFB8BCC6, opacity));
      int size = Math.max(2, cell);
      int dark = Glass.fade(0xFF6C7180, opacity);
      int row = 0;
      for (int y = y1; y < y2; y += size, row++) {
         for (int x = x1 + (row % 2 == 0 ? 0 : size); x < x2; x += size * 2) {
            g.fill(x, y, Math.min(x2, x + size), Math.min(y2, y + size), dark);
         }
      }
   }
}
