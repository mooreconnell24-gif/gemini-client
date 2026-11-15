package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.ui.HudOverlay;
import gemini.util.ClientState;
import gemini.util.Hands;
import gemini.util.Inventories;
import gemini.util.Swing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * One key press, one pearl. Throws an ender pearl where the camera looks without leaving it in your hand: a pearl
 * in the offhand or hotbar is used through the same helper Middle Click Extra uses, and one in the main inventory
 * is swapped into the held slot, thrown, and swapped straight back, all in the same tick. Then the module turns
 * itself off again.
 */
public final class SilentPearl extends Module {
   /** A press that cannot be served yet (a busier module holds the hand, a screen is open) is retried this long. */
   private static final int MAX_WAIT_TICKS = 10;
   private final BooleanSetting fromInventory = this.addSetting(
      new BooleanSetting("Use Inventory", "Also take a pearl from the main inventory when the hotbar has none", true)
   );
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when throwing", true));
   private int waited;

   public SilentPearl() {
      super("Silent Pearl", "Throw an ender pearl from anywhere in your inventory with one key press.", Category.PLAYER);
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   protected boolean hasListOption() {
      return false;
   }

   @Override
   public boolean showInArrayList() {
      return false;
   }

   /** The self-test switches every module on; this one would throw the player across the map. */
   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onEnable() {
      this.waited = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (!ClientState.ready(client)) {
         this.setEnabled(false, false);
         return;
      }

      Result result = client.gui.screen() != null ? Result.BUSY : this.throwPearl(client, player);
      if (result == Result.BUSY && ++this.waited <= MAX_WAIT_TICKS) {
         return;
      }

      if (result.problem != null) {
         HudOverlay.toast("Silent Pearl", result.problem, HudOverlay.Severity.WARNING);
      }

      this.setEnabled(false, false);
   }

   private Result throwPearl(Minecraft client, LocalPlayer player) {
      boolean offhand = player.getOffhandItem().is(Items.ENDER_PEARL);
      int index = offhand ? -1 : Inventories.find(player, stack -> stack.is(Items.ENDER_PEARL), !this.fromInventory.get());
      if (!offhand && index < 0) {
         return Result.NO_PEARL;
      }

      ItemStack pearl = offhand ? player.getOffhandItem() : player.getInventory().getItem(index);
      if (player.getCooldowns().isOnCooldown(pearl)) {
         return Result.COOLDOWN;
      }

      if (offhand || index < 9) {
         // Offhand and hotbar: the shared helper selects the slot, uses the item and selects the old slot again.
         if (!PlayerHelpers.useHotbarItem(client, player, Items.ENDER_PEARL)) {
            return Result.BUSY;
         }

         if (this.swing.get()) {
            Swing.interact(player, offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
         }

         return Result.THROWN;
      }

      // Main inventory: borrow the held slot. This needs the player's own inventory menu, not a chest's.
      if (!Inventories.inventoryOpenOrClosed(player) || !player.inventoryMenu.getCarried().isEmpty() || !Hands.claimMainHand()) {
         return Result.BUSY;
      }

      int held = player.getInventory().getSelectedSlot();
      Inventories.moveToHotbar(client, player, index, held);
      try {
         if (player.getMainHandItem().is(Items.ENDER_PEARL)) {
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            if (this.swing.get()) {
               Swing.interact(player, InteractionHand.MAIN_HAND);
            }
         }
      } finally {
         // The same swap again puts the rest of the pearls and the item that was held back where they were.
         Inventories.moveToHotbar(client, player, index, held);
      }

      return Result.THROWN;
   }

   private enum Result {
      THROWN(null),
      NO_PEARL("No ender pearls in your inventory"),
      COOLDOWN("Ender pearls are on cooldown"),
      BUSY("Could not throw: your hand is busy");

      /** What to tell the player, or null when the pearl went out. */
      private final String problem;

      Result(String problem) {
         this.problem = problem;
      }
   }
}
