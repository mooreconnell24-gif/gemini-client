package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.util.Hands;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MiddleClickExtra extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting(
         "Action",
         "What a middle click does: throw a pearl, fire a rocket, throw an experience bottle or eat a golden apple",
         "Ender Pearl",
         "Ender Pearl",
         "Firework",
         "XP Bottle",
         "Golden Apple"
      )
   );
   private boolean previous;
   /** Eating takes 32 ticks of held use, so a golden apple keeps the use key down until it is finished. */
   private boolean eating;
   private InteractionHand eatHand = InteractionHand.MAIN_HAND;
   private int eatSlot = -1;
   /** The hotbar slot to select again once eating ends, or -1. */
   private int restoreSlot = -1;

   public MiddleClickExtra() {
      super("Middle Click Extra", "Uses an item from your offhand or hotbar when you middle-click.", Category.PLAYER);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onDisable() {
      this.stopEating(Minecraft.getInstance());
      this.restoreSlot = -1;
      this.previous = false;
   }

   @Override
   public void onWorldChange() {
      // The slot to restore belonged to the player that just left.
      this.restoreSlot = -1;
      this.stopEating(Minecraft.getInstance());
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      boolean ready = ClientState.ready(client) && client.gui.screen() == null;
      if (this.eating) {
         boolean keep = ready
            && player.isUsingItem()
            && (this.eatHand == InteractionHand.OFF_HAND || player.getInventory().getSelectedSlot() == this.eatSlot && Hands.claimMainHand());
         if (keep) {
            client.options.keyUse.setDown(true);
         } else {
            this.stopEating(client);
         }
      } else if (this.restoreSlot >= 0 && player != null && Hands.claimMainHand()) {
         player.getInventory().setSelectedSlot(this.restoreSlot);
         this.restoreSlot = -1;
      }

      if (ready) {
         boolean pressed = client.mouseHandler.isMiddlePressed();
         if (pressed && !this.previous && !this.eating) {
            this.act(client, player);
         }

         this.previous = pressed;
      } else {
         this.previous = false;
      }
   }

   private void act(Minecraft client, LocalPlayer player) {
      String var3 = this.mode.get();
      switch (var3) {
         case "Golden Apple":
            this.startEating(client, player);
            break;
         case "Firework":
            PlayerHelpers.useHotbarItem(client, player, Items.FIREWORK_ROCKET);
            break;
         case "XP Bottle":
            PlayerHelpers.useHotbarItem(client, player, Items.EXPERIENCE_BOTTLE);
            break;
         default:
            PlayerHelpers.useHotbarItem(client, player, Items.ENDER_PEARL);
      }
   }

   private static boolean gapple(ItemStack stack) {
      return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
   }

   private void startEating(Minecraft client, LocalPlayer player) {
      if (gapple(player.getOffhandItem())) {
         client.gameMode.useItem(player, InteractionHand.OFF_HAND);
         if (player.isUsingItem()) {
            this.eating = true;
            this.eatHand = InteractionHand.OFF_HAND;
            client.options.keyUse.setDown(true);
         }

         return;
      }

      int slot = Inventories.findHotbar(player, MiddleClickExtra::gapple);
      if (slot < 0 || !Hands.claimMainHand()) {
         return;
      }

      int selected = player.getInventory().getSelectedSlot();
      player.getInventory().setSelectedSlot(slot);
      client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
      if (player.isUsingItem()) {
         this.eating = true;
         this.eatHand = InteractionHand.MAIN_HAND;
         this.eatSlot = slot;
         if (slot != selected) {
            this.restoreSlot = selected;
         }

         client.options.keyUse.setDown(true);
      } else {
         player.getInventory().setSelectedSlot(selected);
      }
   }

   /** Lets go of the use key and selects the old slot again (next tick if a busier module holds the hand now). */
   private void stopEating(Minecraft client) {
      if (!this.eating) {
         return;
      }

      this.eating = false;
      this.eatSlot = -1;
      client.options.keyUse.setDown(MoveUtil.physicalKeyDown(client.options.keyUse));
      LocalPlayer player = client.player;
      if (this.restoreSlot >= 0 && player != null && Hands.claimMainHand()) {
         player.getInventory().setSelectedSlot(this.restoreSlot);
         this.restoreSlot = -1;
      }
   }
}
