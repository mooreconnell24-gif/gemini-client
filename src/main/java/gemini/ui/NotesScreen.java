package gemini.ui;

import gemini.world.NotesText;
import gemini.world.WorldNotes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * A plain notes pad for the open world: one text area and a Done button. The text is saved when the screen closes
 * (Done, Escape, or anything else that replaces it). Lines starting with "[ ]" are to-dos the Notes HUD widget lists.
 */
public final class NotesScreen extends Screen {
   private static final String HINT = "Start a line with [ ] for a to-do and [x] when it is done. Saved with the world.";
   private final Screen parent;
   private final String heading;
   private MultiLineEditBox box;
   private boolean filled;
   private String draft;
   /** When the pad opened: the key that opened it can still be held and auto-repeating for a moment. */
   private final long openedAt = net.minecraft.util.Util.getMillis();
   private static final long KEY_REPEAT_GRACE_MS = 350L;

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

      int width = Math.min(420, this.width - 32);
      int x = (this.width - width) / 2;
      int top = 40;
      int height = Math.max(40, this.height - top - 36);
      this.box = MultiLineEditBox.builder()
         .setX(x)
         .setY(top)
         .setPlaceholder(Component.literal("Coordinates, plans, things to gather..."))
         .build(this.font, width, height, Component.literal("Notes"));
      this.box.setCharacterLimit(NotesText.MAX_CHARS);
      this.addRenderableWidget(this.box);
      this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
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
      super.extractRenderState(g, mouseX, mouseY, partialTick);
      g.centeredText(this.font, this.heading, this.width / 2, 14, Glass.TEXT);
      g.centeredText(this.font, this.filled ? HINT : "Loading...", this.width / 2, 26, Glass.TEXT_DIM);
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
