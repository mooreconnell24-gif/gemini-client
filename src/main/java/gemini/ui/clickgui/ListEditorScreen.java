package gemini.ui.clickgui;

import gemini.Gemini;
import gemini.module.Module;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.module.setting.ListSetting;
import gemini.ui.GeminiMark;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class ListEditorScreen extends Screen {
   private static final int CELL = 20;
   private static final int ROW = 13;
   private static final int PAD = 10;
   private final Screen parent;
   private final Module module;
   private final ListSetting setting;
   private final List<ListEditorScreen.Entry> all = new ArrayList<>();
   private final List<ListEditorScreen.Entry> shown = new ArrayList<>();
   private String search = "";
   private String lastSearch = null;
   private int lastSize = -1;
   private float scroll;
   private int panelX;
   private int panelY;
   private int panelW;
   private int panelH;
   private int bodyTop;
   private int bodyBottom;
   private int columns;

   public ListEditorScreen(Screen parent, Module module, ListSetting setting) {
      super(Component.literal(setting.name()));
      this.parent = parent;
      this.module = module;
      this.setting = setting;
      this.buildEntries();
   }

   private boolean grid() {
      return this.setting.kind() == ListSetting.Kind.BLOCK || this.setting.kind() == ListSetting.Kind.ITEM;
   }

   private void buildEntries() {
      switch (this.setting.kind()) {
         case BLOCK:
            for (Block block : BuiltInRegistries.BLOCK) {
               Identifier key = BuiltInRegistries.BLOCK.getKey(block);
               if (key != null && block != Blocks.AIR && block != Blocks.CAVE_AIR && block != Blocks.VOID_AIR) {
                  Item itemx = block.asItem();
                  this.all.add(new ListEditorScreen.Entry(key.toString(), itemx == Items.AIR ? ItemStack.EMPTY : new ItemStack(itemx), -14800326));
               }
            }
            break;
         case ITEM:
            for (Item item : BuiltInRegistries.ITEM) {
               Identifier key = BuiltInRegistries.ITEM.getKey(item);
               if (key != null && item != Items.AIR) {
                  this.all.add(new ListEditorScreen.Entry(key.toString(), new ItemStack(item), -14800326));
               }
            }
            break;
         case ENTITY:
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
               Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
               if (key != null) {
                  this.all.add(new ListEditorScreen.Entry(key.toString(), ItemStack.EMPTY, -14800326));
               }
            }
      }

      this.all.sort(Comparator.comparing(ListEditorScreen.Entry::id));
   }

   protected void init() {
      this.panelW = Math.min(this.width - 24, 480);
      this.panelH = Math.min(this.height - 24, 360);
      this.panelX = (this.width - this.panelW) / 2;
      this.panelY = (this.height - this.panelH) / 2;
      this.bodyTop = this.panelY + 52;
      this.bodyBottom = this.panelY + this.panelH - 24;
      this.columns = Math.max(1, (this.panelW - 20 - 6) / 20);
      this.lastSearch = null;
   }

   private void refilter() {
      if (!this.search.equals(this.lastSearch) || this.setting.size() != this.lastSize) {
         this.lastSearch = this.search;
         this.lastSize = this.setting.size();
         this.shown.clear();
         String needle = this.search.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
         if (this.setting.kind() == ListSetting.Kind.TEXT) {
            for (String value : this.setting.get()) {
               if (needle.isEmpty() || value.toLowerCase(Locale.ROOT).contains(needle)) {
                  this.shown.add(new ListEditorScreen.Entry(value, ItemStack.EMPTY, -14800326));
               }
            }
         } else {
            List<ListEditorScreen.Entry> selected = new ArrayList<>();
            List<ListEditorScreen.Entry> rest = new ArrayList<>();

            for (ListEditorScreen.Entry entry : this.all) {
               if (needle.isEmpty() || entry.id().contains(needle)) {
                  (this.setting.contains(entry.id()) ? selected : rest).add(entry);
               }
            }

            this.shown.addAll(selected);
            this.shown.addAll(rest);
         }
      }
   }

   private int contentHeight() {
      return this.grid() ? (this.shown.size() + this.columns - 1) / this.columns * 20 : this.shown.size() * 13;
   }

   private float maxScroll() {
      return Math.max(0, this.contentHeight() - (this.bodyBottom - this.bodyTop));
   }

   private int accent() {
      ClickGuiModule style = Gemini.getInstance().modules().byClass(ClickGuiModule.class).orElse(null);
      return style == null ? -10696449 : style.accentColor();
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      this.refilter();
      this.scroll = Math.max(0.0F, Math.min(this.maxScroll(), this.scroll));
      int accent = this.accent();
      graphics.fill(0, 0, this.width, this.height, GeminiMark.withAlpha(-16447473, 190));
      graphics.fill(this.panelX - 1, this.panelY - 1, this.panelX + this.panelW + 1, this.panelY + this.panelH + 1, GeminiMark.withAlpha(accent, 70));
      graphics.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, -15722977);
      graphics.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + 24, -15195602);
      graphics.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + 2, accent);
      graphics.text(this.font, this.module.getName() + "  ·  " + this.setting.name(), this.panelX + 10, this.panelY + 8, -854277, false);
      String count = this.setting.size() + " selected";
      graphics.text(this.font, count, this.panelX + this.panelW - 10 - this.font.width(count), this.panelY + 8, accent, false);
      int fieldTop = this.panelY + 30;
      int doneW = this.font.width("Done") + 16;
      int clearW = this.font.width("Clear") + 16;
      int fieldRight = this.panelX + this.panelW - 10 - doneW - clearW - 8;
      graphics.fill(this.panelX + 10, fieldTop, fieldRight, fieldTop + 16, -586477288);
      graphics.fill(this.panelX + 10, fieldTop + 15, fieldRight, fieldTop + 16, accent);
      boolean blink = System.currentTimeMillis() / 420L % 2L == 0L;
      String hint = this.setting.kind() == ListSetting.Kind.TEXT ? "Type text and press Enter to add" : "Search " + this.setting.kind().noun() + "s...";
      String query = this.search.isEmpty() ? hint : this.search + (blink ? "|" : "");

      while (this.font.width(query) > fieldRight - this.panelX - 10 - 12 && query.length() > 1) {
         query = query.substring(1);
      }

      graphics.text(this.font, query, this.panelX + 10 + 5, fieldTop + 4, this.search.isEmpty() ? -9800056 : -1577226, false);
      int clearX = fieldRight + 4;
      int doneX = clearX + clearW + 4;
      this.button(graphics, clearX, fieldTop, clearW, "Clear", mouseX, mouseY, accent);
      this.button(graphics, doneX, fieldTop, doneW, "Done", mouseX, mouseY, accent);
      graphics.enableScissor(this.panelX, this.bodyTop, this.panelX + this.panelW, this.bodyBottom);
      ListEditorScreen.Entry hovered = null;
      int offset = Math.round(this.scroll);
      if (this.grid()) {
         int startRow = Math.max(0, offset / 20);
         int endRow = (offset + (this.bodyBottom - this.bodyTop)) / 20 + 1;

         for (int row = startRow; row <= endRow; row++) {
            for (int col = 0; col < this.columns; col++) {
               int index = row * this.columns + col;
               if (index >= this.shown.size()) {
                  break;
               }

               ListEditorScreen.Entry entry = this.shown.get(index);
               int x = this.panelX + 10 + col * 20;
               int y = this.bodyTop + row * 20 - offset;
               boolean selected = this.setting.contains(entry.id());
               boolean over = mouseX >= x && mouseX < x + 20 && mouseY >= Math.max(y, this.bodyTop) && mouseY < Math.min(y + 20, this.bodyBottom);
               graphics.fill(x + 1, y + 1, x + 20 - 1, y + 20 - 1, selected ? GeminiMark.withAlpha(accent, 110) : (over ? -14009777 : entry.tint()));
               if (selected) {
                  graphics.outline(x + 1, y + 1, 18, 18, accent);
               }

               if (entry.icon().isEmpty()) {
                  String letter = entry.id().substring(entry.id().indexOf(58) + 1, entry.id().indexOf(58) + 2).toUpperCase(Locale.ROOT);
                  graphics.text(this.font, letter, x + 7, y + 6, -4733999, false);
               } else {
                  graphics.fakeItem(entry.icon(), x + 2, y + 2);
               }

               if (over) {
                  hovered = entry;
               }
            }
         }
      } else {
         int startRow = Math.max(0, offset / 13);
         int endRow = (offset + (this.bodyBottom - this.bodyTop)) / 13 + 1;

         for (int row = startRow; row <= endRow && row < this.shown.size(); row++) {
            ListEditorScreen.Entry entryx = this.shown.get(row);
            int yx = this.bodyTop + row * 13 - offset;
            boolean selectedx = this.setting.contains(entryx.id());
            boolean overx = mouseX >= this.panelX + 10
               && mouseX < this.panelX + this.panelW - 10
               && mouseY >= Math.max(yx, this.bodyTop)
               && mouseY < Math.min(yx + 13, this.bodyBottom);
            if (selectedx || overx) {
               graphics.fill(this.panelX + 10 - 2, yx, this.panelX + this.panelW - 10, yx + 13 - 1, selectedx ? GeminiMark.withAlpha(accent, 90) : 822083583);
            }

            graphics.text(this.font, entryx.id(), this.panelX + 10 + 2, yx + 2, selectedx ? -1 : -4733999, false);
            if (overx) {
               hovered = entryx;
            }
         }
      }

      graphics.disableScissor();
      if (this.contentHeight() > this.bodyBottom - this.bodyTop) {
         int trackH = this.bodyBottom - this.bodyTop;
         int thumbH = Math.max(14, Math.round(trackH * ((float)trackH / this.contentHeight())));
         int thumbY = this.bodyTop + Math.round((trackH - thumbH) * (this.scroll / Math.max(1.0F, this.maxScroll())));
         graphics.fill(this.panelX + this.panelW - 5, this.bodyTop, this.panelX + this.panelW - 2, this.bodyBottom, 1073741824);
         graphics.fill(this.panelX + this.panelW - 5, thumbY, this.panelX + this.panelW - 2, thumbY + thumbH, GeminiMark.withAlpha(accent, 170));
      }

      if (this.shown.isEmpty()) {
         String empty = this.setting.kind() == ListSetting.Kind.TEXT ? "Nothing here yet." : "No match for \"" + this.search + "\"";
         graphics.text(this.font, empty, this.panelX + 10, this.bodyTop + 6, -9800056, false);
      }

      String footer = this.grid()
         ? "Click to toggle  ·  wheel scrolls  ·  type to search  ·  selected entries come first"
         : "Click an entry to toggle it  ·  Esc to close";
      graphics.text(this.font, footer, this.panelX + 10, this.panelY + this.panelH - 15, -10523522, false);
      if (hovered != null) {
         graphics.setTooltipForNextFrame(this.font, Component.literal(hovered.id()), mouseX, mouseY);
      }

      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
   }

   private void button(GuiGraphicsExtractor graphics, int x, int y, int w, String label, int mouseX, int mouseY, int accent) {
      boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
      graphics.fill(x, y, x + w, y + 16, over ? GeminiMark.withAlpha(accent, 100) : -14932426);
      graphics.fill(x, y + 15, x + w, y + 16, over ? accent : -14405567);
      graphics.text(this.font, label, x + (w - this.font.width(label)) / 2, y + 4, over ? -1 : -4733999, false);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      double mx = event.x();
      double my = event.y();
      int fieldTop = this.panelY + 30;
      int doneW = this.font.width("Done") + 16;
      int clearW = this.font.width("Clear") + 16;
      int fieldRight = this.panelX + this.panelW - 10 - doneW - clearW - 8;
      int clearX = fieldRight + 4;
      int doneX = clearX + clearW + 4;
      if (event.button() == 0 && my >= fieldTop && my < fieldTop + 16) {
         if (mx >= doneX && mx < doneX + doneW) {
            this.onClose();
            return true;
         }

         if (mx >= clearX && mx < clearX + clearW) {
            this.setting.clear();
            this.save();
            return true;
         }
      }

      if (!(mx < this.panelX) && !(mx > this.panelX + this.panelW) && !(my < this.panelY) && !(my > this.panelY + this.panelH)) {
         if (my >= this.bodyTop && my < this.bodyBottom && (event.button() == 0 || event.button() == 1)) {
            int offset = Math.round(this.scroll);
            ListEditorScreen.Entry clicked = null;
            if (this.grid()) {
               int col = (int)((mx - this.panelX - 10.0) / 20.0);
               int row = (int)((my - this.bodyTop + offset) / 20.0);
               if (mx >= this.panelX + 10 && col >= 0 && col < this.columns) {
                  int index = row * this.columns + col;
                  if (index >= 0 && index < this.shown.size()) {
                     clicked = this.shown.get(index);
                  }
               }
            } else {
               int row = (int)((my - this.bodyTop + offset) / 13.0);
               if (row >= 0 && row < this.shown.size()) {
                  clicked = this.shown.get(row);
               }
            }

            if (clicked != null) {
               this.setting.toggle(clicked.id());
               this.save();
               return true;
            }
         }

         return super.mouseClicked(event, doubled);
      } else {
         this.onClose();
         return true;
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      this.scroll = Math.max(0.0F, Math.min(this.maxScroll(), this.scroll - (float)scrollY * (this.grid() ? 40 : 39)));
      return true;
   }

   public boolean keyPressed(KeyEvent event) {
      if (event.key() == 256) {
         this.onClose();
         return true;
      } else if (event.key() == 259 && !this.search.isEmpty()) {
         this.search = this.search.substring(0, this.search.length() - 1);
         this.scroll = 0.0F;
         return true;
      } else if (event.key() != 257 && event.key() != 335) {
         return super.keyPressed(event);
      } else {
         if (!this.search.isBlank()
            && (this.setting.kind() == ListSetting.Kind.TEXT || this.shown.isEmpty())
            && (this.setting.kind() == ListSetting.Kind.TEXT || this.setting.isKnown(this.search))) {
            this.setting.add(this.search);
            this.search = "";
            this.save();
         }

         return true;
      }
   }

   public boolean charTyped(CharacterEvent event) {
      if (event.isAllowedChatCharacter() && this.search.length() < 48) {
         this.search = this.search + event.codepointAsString();
         this.scroll = 0.0F;
         return true;
      } else {
         return super.charTyped(event);
      }
   }

   private void save() {
      Gemini.getInstance().config().save();
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   public boolean isPauseScreen() {
      return false;
   }

   private record Entry(String id, ItemStack icon, int tint) {
   }
}
