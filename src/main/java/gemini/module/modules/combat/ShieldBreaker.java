package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public final class ShieldBreaker extends Module {
   private int cooldown;

   public ShieldBreaker() {
      super("ShieldBreaker", "Swap to an axe and strike when an enemy blocks.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (CombatHelpers.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         if (CombatHelpers.nearestEnemy(client, player, 4.2, false) instanceof Player targetPlayer && targetPlayer.isBlocking()) {
            CombatHelpers.withSlot(player, s -> s.is(ItemTags.AXES), () -> {
               client.gameMode.attack(player, targetPlayer);
               player.swing(InteractionHand.MAIN_HAND);
               this.cooldown = 6;
            });
         }
      }
   }
}
