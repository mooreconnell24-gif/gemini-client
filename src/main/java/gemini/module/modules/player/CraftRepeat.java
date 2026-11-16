package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudOverlay;
import gemini.util.InfoText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * Click a recipe in the recipe book once and keep crafting it: every time the result has been taken, the same
 * recipe is put into the grid again through the recipe book, and with Auto Take the result is shift-clicked into
 * your inventory too, until the ingredients run out, the inventory is full or the batch limit is reached.
 * Works in the crafting table and the 2x2 inventory grid. The loop itself is {@link CraftCycle}.
 */
public final class CraftRepeat extends Module {
   private final BooleanSetting autoTake = this.addSetting(
      new BooleanSetting("Auto Take", "Shift-click the result for you (off: only refill the grid after you take it)", true)
   );
   private final BooleanSetting maxPerBatch = this.addSetting(
      new BooleanSetting("Full Stacks", "Fill the grid with as many sets of ingredients as fit, like shift-clicking the recipe", true)
   );
   private final NumberSetting limit = this.addSetting(
      new NumberSetting("Batch Limit", "Stop after this many batches (0: until the ingredients run out)", 0.0, 0.0, 64.0, 1.0)
   );
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between the module's own clicks", 1.0, 0.0, 10.0, 1.0));
   private final CraftCycle cycle = new CraftCycle();
   private final InfoText infoText = new InfoText();
   private RecipeDisplayId recipe;
   private int menuId = -1;
   private boolean sending;

   public CraftRepeat() {
      super("Craft Repeat", "Repeat the last recipe-book recipe until the ingredients run out, taking each result for you.", Category.PLAYER);
   }

   @Override
   public void onDisable() {
      this.forget();
   }

   /** Recipe display ids are handed out per world session; one from another world means nothing here. */
   @Override
   public void onWorldChange() {
      this.forget();
   }

   private void forget() {
      this.recipe = null;
      this.menuId = -1;
      this.sending = false;
      this.cycle.disarm();
   }

   @Override
   public boolean onSendPacket(Packet<?> packet) {
      if (!this.sending && packet instanceof ServerboundPlaceRecipePacket place) {
         // A recipe placed by hand: remember it and start repeating it in this menu.
         this.recipe = place.recipe();
         this.menuId = place.containerId();
         this.cycle.arm();
      }

      return false;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      Screen open = client.gui == null ? null : client.gui.screen();
      if (player == null
         || client.gameMode == null
         || this.recipe == null
         || !(open instanceof AbstractContainerScreen<?> screen)
         || !(screen.getMenu() instanceof AbstractCraftingMenu menu)
         || menu.containerId != this.menuId) {
         // Closing the grid ends the run; the next one starts with the next recipe you click.
         this.cycle.disarm();
         return;
      }

      Slot result = menu.getResultSlot();
      CraftCycle.Action action = this.cycle.step(
         !result.hasItem(), menu.getCarried().isEmpty(), this.autoTake.get(), this.limit.intValue(), this.delay.intValue()
      );
      switch (action) {
         case PLACE -> {
            this.sending = true;
            try {
               client.gameMode.handlePlaceRecipe(menu.containerId, this.recipe, this.maxPerBatch.get());
            } finally {
               this.sending = false;
            }
         }
         case TAKE -> client.gameMode.handleContainerInput(menu.containerId, result.index, 0, ContainerInput.QUICK_MOVE, player);
         case OUT -> HudOverlay.toast("Craft Repeat", this.summary("Out of ingredients"), HudOverlay.Severity.INFO);
         case DONE -> HudOverlay.toast("Craft Repeat", this.summary("Batch limit reached"), HudOverlay.Severity.SUCCESS);
         case FULL -> HudOverlay.toast("Craft Repeat", this.summary("Inventory full"), HudOverlay.Severity.WARNING);
         case WAIT -> {
         }
      }
   }

   private String summary(String reason) {
      int batches = this.cycle.crafts();
      return reason + " after " + batches + (batches == 1 ? " batch" : " batches");
   }

   @Override
   public String info() {
      return this.cycle.armed() ? this.infoText.whole(this.cycle.crafts(), "") : null;
   }
}
