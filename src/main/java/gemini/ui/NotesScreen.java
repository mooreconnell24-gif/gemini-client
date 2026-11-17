package gemini.ui;

import gemini.ui.font.UiFont;
import gemini.world.NotesText;
import gemini.world.WorldNotes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * A plain notes pad for the open world: one window in the shared chrome (see {@link GlassWindow}) with one text
 * area and a Done button. The text is saved when the screen closes
 * (Done, Escape, or anything else that replaces it). Lines starting with "[ ]" are to-dos the Notes HUD widget lists.
 */
public final class NotesScreen extends Screen {
   private static final String HINT = "Start a line with [ ] for a to-do and [x] when it is done. Saved with the world.";
   private final Screen parent;
   private final String heading;
   private final GlassWindow window = new GlassWindow();
   private MultiLineEditBox box;
   private GlassButton done;
   private int panelX;
   private int panelY;
   private int panelWidth;
   private int panelHeight;
   private boolean filled;
   private String draft;
   /** When the pad opened: the key that opened it can still be held and auto-repeating for a moment. */
   private final long openedAt = net.minecraft.util.Util.getMillis();
   private static final long KEY_REPEAT_GRACE_MS = 350L;
   /** Room under the text area for its character count. */
   private static final int COUNTER = 14;

   public NotesScreen(Screen parent, String worldName) {
      super(Component.literal("Notes"));
      this.parent = parent;
      this.heading = worldName == null || worldName.isBlank() ? "Notes" : "Notes: " + worldName;
   }

   @Override
   protected void init() {
      if (this.box != null && this.filled) {
         // Resized: keep what was typed.
         this.draft = this.box.getValue();
      }

      int[] panel = ScreenBounds.window(this.width, this.height, 440, 320);
      this.panelX = panel[0];
      this.panelY = panel[1];
      this.panelWidth = panel[2];
      this.panelHeight = panel[3];
      int pad = ScreenBounds.pad(this.panelWidth);
      int width = this.panelWidth - 2 * pad;
      int x = this.panelX + pad;
      int top = this.panelY + GlassWindow.HEADER;
      // The text area draws its character count under itself; leave that line free above the footer.
      int height = Math.max(24, this.panelHeight - GlassWindow.HEADER - GlassWindow.FOOTER - COUNTER);
      this.box = MultiLineEditBox.builder()
         .setX(x)
         .setY(top)
         .setPlaceholder(Component.literal("Coordinates, plans, things to gather..."))
         .setShowBackground(false)
         .setTextColor(Glass.text())
         .setTextShadow(false)
         .setCursorColor(Glass.accent2())
         .build(this.font, width, height, Component.literal("Notes"));
      this.box.setCharacterLimit(NotesText.MAX_CHARS);
      this.addRenderableWidget(this.box);
      this.done = this.addRenderableWidget(
         new GlassButton(
               x + width - 72,
               this.panelY + this.panelHeight - GlassWindow.FOOTER + Theme.SPACE_L,
               72,
               Theme.BUTTON_HEIGHT_SMALL,
               CommonComponents.GUI_DONE,
               b -> this.onClose()
            )
            .primary()
      );
      this.filled = false;
      this.fill();
      this.setInitialFocus(this.box);
   }

   private void fill() {
      if (!this.filled && WorldNotes.ensureLoaded()) {
         this.filled = true;
         this.box.setValue(this.draft != null ? this.draft : WorldNotes.text());
         this.box.active = true;
      } else if (!this.filled) {
         this.box.active = false;
      }
   }

   /** Drops characters typed in the first moments, so holding the open key doesn't type "nnn" into the notes. */
   @Override
   public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
      if (net.minecraft.util.Util.getMillis() - this.openedAt < KEY_REPEAT_GRACE_MS) {
         return true;
      }

      return super.charTyped(event);
   }

   @Override
   public void tick() {
      this.fill();
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      float a = this.window.open();
      int x1 = this.panelX;
      int y1 = this.panelY;
      int x2 = x1 + this.panelWidth;
      int y2 = y1 + this.panelHeight;
      int pad = ScreenBounds.pad(this.panelWidth);
      this.done.setAlpha(a);
      this.box.setAlpha(a);
      this.window.frame(g, x1, y1, x2, y2);
      this.window.header(g, x1 + pad - GlassWindow.PAD, y1, x2 - pad + GlassWindow.PAD, this.heading, this.filled ? HINT : "Loading...", Glass.textDim());
      this.window.divider(g, x1 + 1, x2 - 1, y2 - GlassWindow.FOOTER);
      // The field behind the text area, which is drawn without its own box; the edge lights up while typing.
      int fx1 = this.box.getX();
      int fy1 = this.box.getY();
      int fx2 = this.box.getRight();
      int fy2 = this.box.getBottom();
      Glass.roundRect(g, fx1, fy1, fx2, fy2, Theme.RADIUS_LARGE, Glass.fade(Glass.withAlpha(Glass.lerp(0xFF000000, Glass.surface(0), 0.5F), 200), a));
      Glass.roundOutline(g, fx1, fy1, fx2, fy2, Theme.RADIUS_LARGE, Glass.fade(this.box.isFocused() ? Glass.withAlpha(Glass.accent(), 130) : Glass.withAlpha(0xFFFFFF, 22), a));

      super.extractRenderState(g, mouseX, mouseY, partialTick);
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      this.window.frame();
      this.window.backdrop(g, this.width, this.height, mouseX, mouseY);
   }

   @Override
   public void removed() {
      if (this.filled && this.box != null) {
         WorldNotes.set(this.box.getValue());
      }
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
