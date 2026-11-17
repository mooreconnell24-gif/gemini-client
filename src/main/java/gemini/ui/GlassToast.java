package gemini.ui;

import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

/**
 * A notice from a Gemini screen, shown through the game's own toast stack (top-right, over any screen) but drawn
 * in Gemini's look: a dark rounded card with the title, up to two lines of text, an edge in the colour of its
 * severity and a thin bar that runs down while it is shown. Gemini's HUD toasts are only drawn in a world and
 * behind an open screen, so screens use this one (see {@link SandboxUi#notify}).
 *
 * <p>A notice with the same title as one already showing replaces its text and restarts its time instead of
 * stacking a second card.
 */
public final class GlassToast implements Toast {
   private static final int WIDTH = 168;
   private static final int PAD = 8;
   private static final int LINE = 10;
   private static final long DURATION_MS = 4000L;
   private final String title;
   private List<String> lines = List.of();
   private int color;
   private int height;
   private long startedAt = -1L;
   private long shownFor;
   private long duration = DURATION_MS;
   private boolean restart;
   private Toast.Visibility wanted = Toast.Visibility.SHOW;

   private GlassToast(String title) {
      this.title = title;
   }

   /** Shows (or refreshes) the notice titled {@code title}; {@code color} is its edge colour, e.g. {@link Glass#OK}. */
   public static void show(Minecraft client, String title, String body, int color) {
      ToastManager manager = client.gui.toastManager();
      String key = title == null ? "" : title;
      GlassToast toast = manager.getToast(GlassToast.class, key);
      boolean fresh = toast == null;
      if (fresh) {
         toast = new GlassToast(key);
      }

      toast.color = color;
      toast.lines = body == null || body.isBlank() ? List.of() : GlassWindow.wrap(body.strip(), WIDTH - 2 * PAD - 10, 2, FontSize.SMALL, FontWeight.REGULAR);
      toast.height = height(toast.lines.size());
      toast.restart = true;
      if (fresh) {
         manager.addToast(toast);
      }
   }

   /** Height of a card with {@code bodyLines} lines of text under its title. */
   static int height(int bodyLines) {
      return PAD + UiFont.capHeight(FontSize.BODY) + (bodyLines > 0 ? 5 + bodyLines * LINE - 3 : 0) + PAD + 2;
   }

   /** How much of the time bar is left (1..0) after {@code shownMs} of {@code durationMs}. */
   static float remaining(long shownMs, long durationMs) {
      return durationMs <= 0L ? 0.0F : Math.max(0.0F, Math.min(1.0F, 1.0F - shownMs / (float)durationMs));
   }

   @Override
   public Toast.Visibility getWantedVisibility() {
      return this.wanted;
   }

   @Override
   public void update(ToastManager manager, long timeMs) {
      if (this.restart || this.startedAt < 0L) {
         this.startedAt = timeMs;
         this.restart = false;
      }

      this.duration = Math.round(DURATION_MS * manager.getNotificationDisplayTimeMultiplier());
      this.shownFor = timeMs - this.startedAt;
      this.wanted = this.shownFor < this.duration ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
   }

   @Override
   public Object getToken() {
      return this.title;
   }

   @Override
   public int width() {
      return WIDTH;
   }

   @Override
   public int height() {
      return this.height;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, Font font, long timeMs) {
      // The stack slides the card in from the right edge; a small gap keeps it off the screen's corner.
      int x1 = 0;
      int y1 = 4;
      int x2 = WIDTH - 6;
      int y2 = this.height - 2;
      Glass.shadow(g, x1, y1, x2, y2, Theme.RADIUS_LARGE, 10, 3, 0.4F);
      Glass.roundRect(g, x1, y1, x2, y2, Theme.RADIUS_LARGE, Glass.withAlpha(Glass.surface(1), 244));
      Glass.roundOutline(g, x1, y1, x2, y2, Theme.RADIUS_LARGE, Glass.withAlpha(0xFFFFFF, 22));
      Glass.roundRect(g, x1 + 4, y1 + 5, x1 + 6, y2 - 5, 1, this.color);
      int x = x1 + PAD + 4;
      int y = y1 + PAD - 2;
      UiFont.draw(g, UiFont.ellipsize(this.title, x2 - PAD - x, FontSize.BODY, FontWeight.MEDIUM), x, y, Glass.text(), FontSize.BODY, FontWeight.MEDIUM, false);
      y += UiFont.capHeight(FontSize.BODY) + 5;
      for (String line : this.lines) {
         UiFont.draw(g, line, x, y, Glass.textDim(), FontSize.SMALL, FontWeight.REGULAR, false);
         y += LINE;
      }

      float left = remaining(this.shownFor, this.duration);
      int barLeft = x1 + Theme.RADIUS_LARGE;
      int barRight = x2 - Theme.RADIUS_LARGE;
      g.fill(barLeft, y2 - 2, barLeft + Math.round((barRight - barLeft) * left), y2 - 1, Glass.withAlpha(this.color, 150));
   }
}
