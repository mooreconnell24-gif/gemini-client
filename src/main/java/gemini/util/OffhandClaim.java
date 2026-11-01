package gemini.util;

import gemini.Gemini;
import gemini.module.modules.AutoTotemModule;
import gemini.module.modules.combat.CombatModules;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;

public final class OffhandClaim {
   private OffhandClaim() {
   }

   public static boolean totemOwns(LocalPlayer player) {
      if (player == null) {
         return false;
      } else {
         Gemini gemini = Gemini.getInstance();
         if (gemini == null) {
            return false;
         } else {
            AutoTotemModule totem = gemini.modules().byClass(AutoTotemModule.class).orElse(null);
            if (totem != null && totem.isEnabled() && totem.wantsOffhand(player)) {
               return true;
            } else {
               CombatModules.Offhand offhand = gemini.modules().byClass(CombatModules.Offhand.class).orElse(null);
               return offhand != null && offhand.isEnabled() && offhand.wantsTotem(player);
            }
         }
      }
   }

   public static boolean combatOwns(LocalPlayer player) {
      if (totemOwns(player)) {
         return true;
      } else {
         Gemini gemini = Gemini.getInstance();
         if (gemini != null && player != null) {
            CombatModules.Offhand offhand = gemini.modules().byClass(CombatModules.Offhand.class).orElse(null);
            return offhand != null && offhand.isEnabled() && !player.getOffhandItem().is(Items.AIR);
         } else {
            return false;
         }
      }
   }
}
