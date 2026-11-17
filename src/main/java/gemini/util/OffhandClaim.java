package gemini.util;

import gemini.Gemini;
import gemini.module.modules.combat.AutoTotemModule;
import net.minecraft.client.player.LocalPlayer;

public final class OffhandClaim {
   private OffhandClaim() {
   }

   /** Whether Auto Totem needs the offhand for a totem right now. */
   public static boolean totemOwns(LocalPlayer player) {
      AutoTotemModule totem = totem(player);
      return totem != null && totem.wantsTotem(player);
   }

   /** Whether Auto Totem holds the offhand at all: for a totem, or for the item it keeps there. */
   public static boolean combatOwns(LocalPlayer player) {
      AutoTotemModule totem = totem(player);
      // The preferred item only holds the slot while it is there; an empty offhand is free for others.
      return totem != null && (totem.wantsTotem(player) || totem.keepsItem() && !player.getOffhandItem().isEmpty());
   }

   private static AutoTotemModule totem(LocalPlayer player) {
      Gemini gemini = Gemini.getInstance();
      if (player == null || gemini == null) {
         return null;
      }

      AutoTotemModule totem = gemini.modules().byClass(AutoTotemModule.class).orElse(null);
      return totem != null && totem.isEnabled() ? totem : null;
   }
}
