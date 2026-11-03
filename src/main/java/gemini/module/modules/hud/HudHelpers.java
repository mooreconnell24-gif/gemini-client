package gemini.module.modules.hud;

import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared helpers for the hud modules (formerly statics on the module container classes). */
public final class HudHelpers {
   private HudHelpers() {
   }

   static final int WHITE = -1511689;

   static final int GREY = -6641995;

   static final int GREEN = -11086174;

   static final int YELLOW = -14249;

   static final int RED = -1548963;

   static String pretty(String path) {
      StringBuilder out = new StringBuilder(path.length());
      boolean upper = true;

      for (int i = 0; i < path.length(); i++) {
         char c = path.charAt(i);
         if (c != '_' && c != '/') {
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
         } else {
            out.append(' ');
            upper = true;
         }
      }

      return out.toString();
   }

   static String ticksToClock(int ticks) {
      int seconds = Math.max(0, ticks) / 20;
      return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
   }

   static int ratioColor(float ratio) {
      return ratio > 0.6F ? -11086174 : (ratio > 0.3F ? -14249 : -1548963);
   }

   static String roman(int number) {
      return switch (number) {
         case 1 -> "I";
         case 2 -> "II";
         case 3 -> "III";
         case 4 -> "IV";
         case 5 -> "V";
         default -> Integer.toString(number);
      };
   }

   static int count(LocalPlayer player, Item item) {
      int total = 0;

      for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
         if (stack.is(item)) {
            total += stack.getCount();
         }
      }

      ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
      if (offhand.is(item)) {
         total += offhand.getCount();
      }

      return total;
   }
}
