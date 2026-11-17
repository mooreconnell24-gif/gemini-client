package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.font.UiFont;
import gemini.util.AttributeBoost;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Your ender chest on screen without opening one. The client only learns the contents while an ender chest is
 * open, so the integrated server's copy is read every few seconds instead and drawn as a 9x3 grid.
 */
public final class EnderChestPreview extends HudWidget {
   private static final int COLUMNS = 9;
   private static final int SLOTS = 27;
   private static final int CELL = 18;
   private final NumberSetting refresh = this.addSetting(new NumberSetting("Refresh", "Seconds between reads of the ender chest", 2.0, 0.5, 10.0, 0.5));
   private final BooleanSetting title = this.addSetting(new BooleanSetting("Title", "Show a header with how many slots are used", true));
   private final BooleanSetting compact = this.addSetting(new BooleanSetting("Hide Empty Rows", "Leave out rows with nothing in them", false));
   private final BooleanSetting hideEmpty = this.addSetting(new BooleanSetting("Hide When Empty", "Hide the widget while the ender chest is empty", false));
   /** A copy taken on the server thread; replaced as a whole, never changed after publishing. */
   private volatile ItemStack[] contents;
   private String header = "Ender Chest";
   private ItemStack[] headerFor;
   private int poll;

   public EnderChestPreview() {
      super("Ender Chest Preview", "Shows what is in your ender chest without opening it.", "Bottom Left", 6, 90);
   }

   @Override
   public void onEnable() {
      this.contents = null;
      this.headerFor = null;
      this.poll = 0;
   }

   @Override
   public void onDisable() {
      this.contents = null;
      this.headerFor = null;
   }

   @Override
   public void onTick() {
      if (this.poll-- <= 0) {
         this.poll = Math.max(1, (int)Math.round(this.refresh.get() * 20.0));
         AttributeBoost.onServer(this::read);
      }
   }

   /** Runs on the server thread. */
   private void read(ServerPlayer player) {
      PlayerEnderChestContainer chest = player.getEnderChestInventory();
      ItemStack[] copy = new ItemStack[SLOTS];
      for (int i = 0; i < SLOTS; i++) {
         copy[i] = i < chest.getContainerSize() ? chest.getItem(i).copy() : ItemStack.EMPTY;
      }

      this.contents = copy;
   }

   private static boolean rowEmpty(ItemStack[] items, int row) {
      for (int column = 0; column < COLUMNS; column++) {
         if (!items[row * COLUMNS + column].isEmpty()) {
            return false;
         }
      }

      return true;
   }

   private int rows(ItemStack[] items) {
      if (!this.compact.get()) {
         return SLOTS / COLUMNS;
      }

      int rows = 0;
      for (int row = 0; row < SLOTS / COLUMNS; row++) {
         if (!rowEmpty(items, row)) {
            rows++;
         }
      }

      return rows;
   }

   private int used(ItemStack[] items) {
      int used = 0;
      for (ItemStack stack : items) {
         if (!stack.isEmpty()) {
            used++;
         }
      }

      return used;
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      ItemStack[] items = this.contents;
      if (items == null || this.hideEmpty.get() && this.used(items) == 0) {
         return 0;
      }

      return 8 + COLUMNS * CELL - 2;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      ItemStack[] items = this.contents;
      if (items == null) {
         return 0;
      }

      int rows = Math.max(this.compact.get() ? 0 : 1, this.rows(items));
      int top = this.title.get() ? 14 : 4;
      return top + rows * CELL + 2;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      ItemStack[] items = this.contents;
      if (items == null) {
         return;
      }

      int y = 4;
      if (this.title.get()) {
         if (this.headerFor != items) {
            // Rebuilt only when a new copy arrives, not every frame.
            this.headerFor = items;
            this.header = "Ender Chest  " + this.used(items) + "/" + SLOTS;
         }

         UiFont.draw(hud.graphics, this.header, 5, y + 1, gemini.ui.HudStyle.label(this.backdrop()), hud.shadow);
         y += 10;
      }

      for (int row = 0; row < SLOTS / COLUMNS; row++) {
         if (this.compact.get() && rowEmpty(items, row)) {
            continue;
         }

         for (int column = 0; column < COLUMNS; column++) {
            int x = 5 + column * CELL;
            Glass.roundRect(hud.graphics, x, y, x + 16, y + 16, 2, Glass.withAlpha(0xFFFFFF, 16));
            ItemStack stack = items[row * COLUMNS + column];
            if (!stack.isEmpty()) {
               hud.graphics.item(stack, x, y);
               hud.graphics.itemDecorations(hud.font, stack, x, y);
            }
         }

         y += CELL;
      }
   }
}
