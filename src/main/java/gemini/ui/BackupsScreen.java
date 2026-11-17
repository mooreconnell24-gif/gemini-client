package gemini.ui;

import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import gemini.world.BackupNames;
import gemini.world.WorldBackups;
import gemini.world.WorldZip;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Lists the world backups on disk and restores one into a new world: one window in the shared chrome (see
 * {@link GlassWindow}) with the list, a status line and a row of buttons. Opened from the World Backup module
 * (menu action or {@code .worldbackup}), in a world or from the menus before one is loaded.
 */
public final class BackupsScreen extends Screen {
   private static final int ROW = GlassWindow.ROW_HEIGHT;
   private static final int STATUS = 14;
   private final Screen parent;
   private final IntSupplier keep;
   private final GlassWindow window = new GlassWindow();
   private final List<GlassButton> buttons = new ArrayList<>();
   private List<WorldBackups.Entry> entries = List.of();
   private boolean loading = true;
   private int selected = -1;
   private float scroll;
   private float scrollTarget;
   private WorldBackups.Job watched;
   private boolean watchedFinished;
   private GlassButton backUp;
   private GlassButton restore;
   private int panelX;
   private int panelY;
   private int panelWidth;
   private int panelHeight;
   private int listTop;
   private int listBottom;
   private int listLeft;
   private int listRight;

   /** {@code keep} is the module's "Keep" setting, read when a backup is started from here. */
   public BackupsScreen(Screen parent, IntSupplier keep) {
      super(Component.literal("World backups"));
      this.parent = parent;
      this.keep = keep;
   }

   @Override
   protected void init() {
      int[] panel = ScreenBounds.window(this.width, this.height, 440, 300);
      this.panelX = panel[0];
      this.panelY = panel[1];
      this.panelWidth = panel[2];
      this.panelHeight = panel[3];
      int pad = ScreenBounds.pad(this.panelWidth);
      this.listLeft = this.panelX + pad;
      this.listRight = this.panelX + this.panelWidth - pad;
      this.listTop = this.panelY + GlassWindow.HEADER;
      this.listBottom = this.panelY + this.panelHeight - GlassWindow.FOOTER - STATUS;
      int y = this.panelY + this.panelHeight - GlassWindow.FOOTER + Theme.SPACE_L;
      int[] x = ScreenBounds.row(this.listLeft, this.listRight, 4, Theme.SPACE_S);
      int w = ScreenBounds.rowWidth(this.listLeft, this.listRight, 4, Theme.SPACE_S);
      this.buttons.clear();
      this.backUp = this.button(x[0], y, w, "Back up now", "Save and copy the open world into a new backup", this::backUpNow).primary();
      this.restore = this.button(x[1], y, w, "Restore", "Unpack the selected backup as a new world; the original world is not changed", this::restoreSelected);
      this.button(x[2], y, w, "Open folder", "Show the backups folder in your file manager", () -> WorldBackups.openFolder(this.selectedEntry() == null ? null : this.selectedEntry().world()));
      this.button(x[3], y, this.listRight - x[3], CommonComponents.GUI_DONE.getString(), null, this::onClose);
      this.watched = WorldBackups.current();
      this.watchedFinished = this.watched == null || this.watched.finished();
      this.refresh();
      this.updateButtons();
   }

   private GlassButton button(int x, int y, int w, String label, String tooltip, Runnable action) {
      GlassButton button = new GlassButton(x, y, w, Theme.BUTTON_HEIGHT_SMALL, Component.literal(label), pressed -> action.run());
      if (tooltip != null) {
         button.setTooltip(UiFont.tooltip(Component.literal(tooltip)));
      }

      this.buttons.add(button);
      return this.addRenderableWidget(button);
   }

   private void refresh() {
      this.loading = true;
      WorldBackups.list(list -> {
         WorldBackups.Entry before = this.selectedEntry();
         this.entries = list;
         this.loading = false;
         this.selected = -1;
         for (int i = 0; i < list.size(); i++) {
            if (before != null && list.get(i).file().equals(before.file())) {
               this.selected = i;
            }
         }

         if (this.selected < 0) {
            // Start on the newest backup of the open world, if it has any.
            String open = WorldBackups.openWorldFolder();
            for (int i = 0; i < list.size() && open != null; i++) {
               if (BackupNames.safeFolder(open).equals(list.get(i).world())) {
                  this.selected = i;
                  break;
               }
            }
         }

         this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, this.maxScroll());
         this.updateButtons();
      });
   }

   private WorldBackups.Entry selectedEntry() {
      return this.selected >= 0 && this.selected < this.entries.size() ? this.entries.get(this.selected) : null;
   }

   private void backUpNow() {
      WorldBackups.Job job = WorldBackups.backUp(this.keep.getAsInt(), false);
      if (job != null) {
         this.watched = job;
         this.watchedFinished = false;
      }

      this.updateButtons();
   }

   private void restoreSelected() {
      WorldBackups.Entry entry = this.selectedEntry();
      if (entry != null) {
         WorldBackups.Job job = WorldBackups.restore(entry);
         if (job != null) {
            this.watched = job;
            this.watchedFinished = false;
         }
      }

      this.updateButtons();
   }

   private void updateButtons() {
      boolean busy = WorldBackups.busy();
      if (this.backUp != null) {
         this.backUp.active = !busy && this.minecraft.getSingleplayerServer() != null;
      }

      if (this.restore != null) {
         this.restore.active = !busy && this.selectedEntry() != null;
      }
   }

   @Override
   public void tick() {
      if (this.watched != null && !this.watchedFinished && this.watched.finished()) {
         this.watchedFinished = true;
         this.refresh();
      }

      this.updateButtons();
   }

   private float maxScroll() {
      return Math.max(0, this.entries.size() * ROW - (this.listBottom - this.listTop));
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      this.window.frame();
      this.window.backdrop(g, this.width, this.height, mouseX, mouseY);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      float a = this.window.open();
      this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, this.maxScroll());
      this.scroll = Glass.approach(this.scroll, this.scrollTarget, 16.0F, this.window.delta());
      for (GlassButton button : this.buttons) {
         button.setAlpha(a);
      }

      int x1 = this.panelX;
      int y1 = this.panelY;
      int x2 = x1 + this.panelWidth;
      int y2 = y1 + this.panelHeight;
      this.window.frame(g, x1, y1, x2, y2);
      this.window.header(g, this.listLeft - GlassWindow.PAD, y1, this.listRight + GlassWindow.PAD, this.title.getString(), "Restoring makes a new world. Nothing is overwritten.", Glass.textDim());
      int textInset = 8;
      if (this.loading) {
         UiFont.draw(g, "Loading...", this.listLeft, this.listTop + 4, Glass.fade(Glass.textFaint(), a), false);
      } else if (this.entries.isEmpty()) {
         UiFont.draw(g, "No backups yet. \"Back up now\" makes the first one.", this.listLeft, this.listTop + 4, Glass.fade(Glass.textFaint(), a), false);
      } else {
         g.enableScissor(this.listLeft, this.listTop, this.listRight, this.listBottom);
         int offset = Math.round(this.scroll);
         int first = Math.max(0, offset / ROW);
         String lastWorld = first > 0 ? this.entries.get(first - 1).world() : null;
         for (int i = first; i < this.entries.size(); i++) {
            int y = this.listTop + i * ROW - offset;
            if (y >= this.listBottom) {
               break;
            }

            WorldBackups.Entry entry = this.entries.get(i);
            boolean hover = mouseX >= this.listLeft && mouseX < this.listRight - 6 && mouseY >= y && mouseY < y + ROW && mouseY < this.listBottom && mouseY >= this.listTop;
            this.window.row(g, this.listLeft, y, this.listRight - 6, y + ROW - 1, i == this.selected, hover);
            boolean newWorld = !entry.world().equals(lastWorld);
            lastWorld = entry.world();
            int textY = UiFont.centerY(y, ROW - 1);
            String size = entry.sizeText();
            int sizeX = this.listRight - 6 - textInset - UiFont.width(size);
            UiFont.draw(g, size, sizeX, textY, Glass.fade(Glass.textDim(), a), false);
            String time = entry.shownTime();
            int timeX = sizeX - 12 - UiFont.width(time);
            UiFont.draw(g, time, timeX, textY, Glass.fade(i == this.selected ? Glass.text() : Glass.textDim(), a), false);
            if (newWorld) {
               int room = timeX - 8 - (this.listLeft + textInset);
               UiFont.draw(g, UiFont.ellipsize(entry.world(), room, FontSize.BODY, FontWeight.MEDIUM), this.listLeft + textInset, textY, Glass.fade(Glass.text(), a), FontSize.BODY, FontWeight.MEDIUM, false);
            }
         }

         g.disableScissor();
         this.window.scrollbar(g, this.listRight - 2, this.listTop, this.listBottom, this.scroll, this.maxScroll());
      }

      WorldBackups.Job job = WorldBackups.current();
      int statusY = this.listBottom + (STATUS - UiFont.capHeight(FontSize.SMALL)) / 2;
      if (job != null && !job.finished()) {
         String label = (job.kind == WorldBackups.Kind.BACKUP ? "Backing up " : "Restoring ") + job.world + "  " + WorldZip.percent(job.progress.fraction());
         int labelRoom = (this.listRight - this.listLeft) * 3 / 5;
         label = UiFont.ellipsize(label, labelRoom, FontSize.SMALL, FontWeight.REGULAR);
         UiFont.draw(g, label, this.listLeft, statusY, Glass.fade(Glass.textDim(), a), FontSize.SMALL, FontWeight.REGULAR, false);
         int barX = this.listLeft + UiFont.width(label, FontSize.SMALL, FontWeight.REGULAR) + 8;
         if (barX < this.listRight - 20) {
            Glass.bar(g, barX, statusY + 2, this.listRight, statusY + 5, job.progress.fraction(), Glass.fade(Glass.withAlpha(0xFFFFFF, 26), a), Glass.fade(Glass.accent(), a), Glass.fade(Glass.accent2(), a));
         }
      } else if (job != null && job == this.watched) {
         String outcome = UiFont.ellipsize(job.outcome(), this.listRight - this.listLeft, FontSize.SMALL, FontWeight.REGULAR);
         UiFont.draw(g, outcome, this.listLeft, statusY, Glass.fade(job.failed() ? Glass.ERROR : Glass.textDim(), a), FontSize.SMALL, FontWeight.REGULAR, false);
      }

      this.window.divider(g, x1 + 1, x2 - 1, y2 - GlassWindow.FOOTER);
      super.extractRenderState(g, mouseX, mouseY, partialTick);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      double mx = event.x();
      double my = event.y();
      if (mx >= this.listLeft && mx < this.listRight - 6 && my >= this.listTop && my < this.listBottom && !this.entries.isEmpty()) {
         int index = (int)((my - this.listTop + this.scroll) / ROW);
         if (index >= 0 && index < this.entries.size()) {
            this.selected = index;
            this.updateButtons();
            return true;
         }
      }

      return super.mouseClicked(event, doubled);
   }

   @Override
   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      this.scrollTarget = Mth.clamp(this.scrollTarget - (float)scrollY * ROW * 3.0F, 0.0F, this.maxScroll());
      return true;
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
