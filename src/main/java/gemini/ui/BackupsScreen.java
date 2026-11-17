package gemini.ui;

import gemini.ui.font.UiFont;
import gemini.world.BackupNames;
import gemini.world.WorldBackups;
import gemini.world.WorldZip;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Lists the world backups on disk and restores one into a new world. Vanilla layout: title, a plain list, a row of
 * buttons. Opened from the World Backup module (menu action or {@code .worldbackup}), in a world or from the menus
 * before one is loaded.
 */
public final class BackupsScreen extends Screen {
   private static final int ROW = 13;
   private final Screen parent;
   private final IntSupplier keep;
   private List<WorldBackups.Entry> entries = List.of();
   private boolean loading = true;
   private int selected = -1;
   private float scroll;
   private WorldBackups.Job watched;
   private boolean watchedFinished;
   private Button backUp;
   private Button restore;
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
      int width = Math.min(420, this.width - 32);
      this.listLeft = (this.width - width) / 2;
      this.listRight = this.listLeft + width;
      this.listTop = 44;
      this.listBottom = this.height - 52;
      int gap = 4;
      int buttonWidth = (width - gap * 3) / 4;
      int y = this.height - 28;
      int x = this.listLeft;
      this.backUp = this.addRenderableWidget(
         Button.builder(UiFont.literal("Back up now"), b -> this.backUpNow())
            .bounds(x, y, buttonWidth, 20)
            .tooltip(UiFont.tooltip(Component.literal("Save and copy the open world into a new backup")))
            .build()
      );
      this.restore = this.addRenderableWidget(
         Button.builder(UiFont.literal("Restore"), b -> this.restoreSelected())
            .bounds(x + (buttonWidth + gap), y, buttonWidth, 20)
            .tooltip(UiFont.tooltip(Component.literal("Unpack the selected backup as a new world; the original world is not changed")))
            .build()
      );
      this.addRenderableWidget(
         Button.builder(UiFont.literal("Open folder"), b -> WorldBackups.openFolder(this.selectedEntry() == null ? null : this.selectedEntry().world()))
            .bounds(x + (buttonWidth + gap) * 2, y, buttonWidth, 20)
            .build()
      );
      this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds(x + (buttonWidth + gap) * 3, y, buttonWidth, 20).build());
      this.watched = WorldBackups.current();
      this.watchedFinished = this.watched == null || this.watched.finished();
      this.refresh();
      this.updateButtons();
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

         this.scroll = Mth.clamp(this.scroll, 0.0F, this.maxScroll());
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
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(g, mouseX, mouseY, partialTick);
      UiFont.drawCentered(g, this.title.getString(), this.width / 2, 14, Glass.text(), true);
      UiFont.drawCentered(g, "Restoring makes a new world. Nothing is overwritten.", this.width / 2, 27, Glass.textDim(), true);
      g.fill(this.listLeft, this.listTop - 2, this.listRight, this.listBottom + 2, 0x66000000);
      if (this.loading) {
         UiFont.drawCentered(g, "Loading...", this.width / 2, this.listTop + 8, Glass.textDim(), true);
      } else if (this.entries.isEmpty()) {
         UiFont.drawCentered(g, "No backups yet.", this.width / 2, this.listTop + 8, Glass.textDim(), true);
      } else {
         g.enableScissor(this.listLeft, this.listTop, this.listRight, this.listBottom);
         int first = Math.max(0, (int)(this.scroll / ROW));
         String lastWorld = first > 0 ? this.entries.get(first - 1).world() : null;
         for (int i = first; i < this.entries.size(); i++) {
            int y = this.listTop + i * ROW - Math.round(this.scroll);
            if (y >= this.listBottom) {
               break;
            }

            WorldBackups.Entry entry = this.entries.get(i);
            boolean hover = mouseX >= this.listLeft && mouseX < this.listRight && mouseY >= y && mouseY < y + ROW && mouseY < this.listBottom;
            if (i == this.selected) {
               g.fill(this.listLeft, y, this.listRight, y + ROW, Glass.withAlpha(Glass.accent(), 70));
            } else if (hover) {
               g.fill(this.listLeft, y, this.listRight, y + ROW, 0x22FFFFFF);
            }

            boolean newWorld = !entry.world().equals(lastWorld);
            lastWorld = entry.world();
            UiFont.draw(g, newWorld ? entry.world() : "", this.listLeft + 6, y + 3, Glass.text(), false);
            String size = entry.sizeText();
            int sizeX = this.listRight - 6 - UiFont.width(size);
            UiFont.draw(g, size, sizeX, y + 3, Glass.textDim(), false);
            String time = entry.shownTime();
            UiFont.draw(g, time, sizeX - 12 - UiFont.width(time), y + 3, Glass.textDim(), false);
         }

         g.disableScissor();
      }

      WorldBackups.Job job = WorldBackups.current();
      int statusY = this.listBottom + 8;
      if (job != null && !job.finished()) {
         String label = (job.kind == WorldBackups.Kind.BACKUP ? "Backing up " : "Restoring ") + job.world + "  " + WorldZip.percent(job.progress.fraction());
         UiFont.draw(g, label, this.listLeft, statusY, Glass.textDim(), false);
         int barX = this.listLeft + UiFont.width(label) + 8;
         if (barX < this.listRight - 20) {
            g.fill(barX, statusY + 3, this.listRight, statusY + 5, 0x33FFFFFF);
            g.fill(barX, statusY + 3, barX + Math.round((this.listRight - barX) * job.progress.fraction()), statusY + 5, Glass.accent());
         }
      } else if (job != null && job == this.watched) {
         UiFont.draw(g, job.outcome(), this.listLeft, statusY, job.failed() ? Glass.ERROR : Glass.textDim(), false);
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      double mx = event.x();
      double my = event.y();
      if (mx >= this.listLeft && mx < this.listRight && my >= this.listTop && my < this.listBottom && !this.entries.isEmpty()) {
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
      this.scroll = Mth.clamp(this.scroll - (float)scrollY * ROW * 3.0F, 0.0F, this.maxScroll());
      return true;
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
