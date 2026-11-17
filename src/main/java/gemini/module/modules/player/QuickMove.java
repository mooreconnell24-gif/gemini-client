package gemini.module.modules.player;

import com.mojang.blaze3d.platform.InputConstants;
import gemini.input.Keys;
import gemini.mixin.AbstractContainerScreenAccessor;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;

/**
 * Shift + drag in any container screen to shift-click every slot the mouse passes over, the way the Mouse Tweaks
 * mods do it. Vanilla handles the slot you pressed on; this module follows the drag ({@link DragPath}) and sends
 * the same quick-move click for each further slot, a few per tick.
 */
public final class QuickMove extends Module {
   private final NumberSetting perTick = this.addSetting(new NumberSetting("Clicks Per Tick", "Most slots moved in one tick", 6.0, 1.0, 20.0, 1.0));
   private final BooleanSetting sameItem = this.addSetting(
      new BooleanSetting("Same Item Only", "Only move slots holding the same item as the first slot dragged over", false)
   );
   private final BooleanSetting hotbar = this.addSetting(new BooleanSetting("Include Hotbar", "Also move items out of hotbar slots you drag over", true));
   private int[] slotX = new int[64];
   private int[] slotY = new int[64];
   private final int[] crossed = new int[64];
   private final boolean[] handled = new boolean[256];
   private final int[] queue = new int[256];
   private int queued;
   private boolean dragging;
   private double lastX;
   private double lastY;
   private Item anchor;
   private int menuId = -1;

   public QuickMove() {
      super("Quick Move", "Shift-clicks every slot you drag across while holding Shift.", Category.PLAYER);
   }

   @Override
   public void onDisable() {
      this.endDrag();
      this.menuId = -1;
   }

   @Override
   public void onWorldChange() {
      this.endDrag();
      this.menuId = -1;
   }

   private void endDrag() {
      if (this.dragging) {
         java.util.Arrays.fill(this.handled, false);
      }

      this.dragging = false;
      this.queued = 0;
      this.anchor = null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      Screen open = client.gui == null ? null : client.gui.screen();
      if (player == null || client.gameMode == null || !(open instanceof AbstractContainerScreen<?> screen) || open instanceof CreativeModeInventoryScreen) {
         this.endDrag();
         return;
      }

      AbstractContainerMenu menu = screen.getMenu();
      boolean shift = InputConstants.isKeyDown(Keys.LEFT_SHIFT) || InputConstants.isKeyDown(Keys.RIGHT_SHIFT);
      if (!shift || !client.mouseHandler.isLeftPressed() || !menu.getCarried().isEmpty() || menu.containerId != this.menuId && this.dragging) {
         this.endDrag();
         this.menuId = menu.containerId;
         return;
      }

      this.menuId = menu.containerId;
      List<Slot> slots = menu.slots;
      int count = Math.min(slots.size(), this.handled.length);
      if (this.slotX.length < count) {
         this.slotX = new int[count];
         this.slotY = new int[count];
      }

      AbstractContainerScreenAccessor bounds = (AbstractContainerScreenAccessor)screen;
      int left = bounds.geminiLeftPos();
      int top = bounds.geminiTopPos();
      for (int i = 0; i < count; i++) {
         Slot slot = slots.get(i);
         this.slotX[i] = left + slot.x;
         this.slotY[i] = top + slot.y;
      }

      double x = client.mouseHandler.getScaledXPos(client.getWindow());
      double y = client.mouseHandler.getScaledYPos(client.getWindow());
      if (!this.dragging) {
         // The press itself was a vanilla shift-click, usually on the slot under the mouse now. If that slot still
         // holds something (the mouse moved on before this tick, or there was no room), one more quick move on it
         // is harmless, so it is queued rather than assumed done; an emptied slot is skipped when the queue drains.
         this.dragging = true;
         int pressed = DragPath.slotAt(x, y, this.slotX, this.slotY, count);
         if (pressed >= 0) {
            this.handled[pressed] = true;
            this.queue[this.queued++] = pressed;
         }
      } else {
         int n = DragPath.crossed(this.lastX, this.lastY, x, y, this.slotX, this.slotY, count, this.crossed);
         for (int i = 0; i < n && this.queued < this.queue.length; i++) {
            int index = this.crossed[i];
            if (!this.handled[index]) {
               this.handled[index] = true;
               this.queue[this.queued++] = index;
            }
         }
      }

      this.lastX = x;
      this.lastY = y;
      this.drain(client, player, menu, slots, count);
   }

   private void drain(Minecraft client, LocalPlayer player, AbstractContainerMenu menu, List<Slot> slots, int count) {
      int budget = this.perTick.intValue();
      int read = 0;
      while (read < this.queued && budget > 0) {
         int index = this.queue[read++];
         if (index >= count) {
            continue;
         }

         Slot slot = slots.get(index);
         if (!slot.hasItem() || !slot.mayPickup(player)) {
            continue;
         }

         if (!this.hotbar.get() && slot.container instanceof Inventory && slot.getContainerSlot() < Inventory.getSelectionSize()) {
            continue;
         }

         Item item = slot.getItem().getItem();
         if (this.sameItem.get()) {
            if (this.anchor == null) {
               this.anchor = item;
            } else if (this.anchor != item) {
               continue;
            }
         }

         client.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, player);
         budget--;
      }

      // Whatever the budget did not reach waits for the next tick, in order.
      System.arraycopy(this.queue, read, this.queue, 0, this.queued - read);
      this.queued -= read;
   }
}
