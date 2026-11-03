package gemini.module.modules.combat;

import gemini.Gemini;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Damage;
import gemini.util.Inventories;
import gemini.event.ActionPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class Offhand extends Module {
   private final ModeSetting item = this.addSetting(
      new ModeSetting("Item", "Preferred offhand item", "Totem", "Totem", "Golden Apple", "Enchanted Golden Apple", "End Crystal", "Shield")
   );
   private final NumberSetting totemHealth = this.addSetting(
      new NumberSetting("Totem Health", "Force totem at or below this health", 10.0, 0.0, 20.0, 1.0)
   );
   private final BooleanSetting swordGapple = this.addSetting(
      new BooleanSetting("Sword Gapple", "Hold golden apple while right-clicking with sword", false)
   );
   private final BooleanSetting hotbarOnly = this.addSetting(new BooleanSetting("Hotbar Only", "Only pull items from hotbar slots", false));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between offhand swaps", 2.0, 0.0, 20.0, 1.0));
   private int cooldown;

   public Offhand() {
      super("Offhand", "Keep your offhand stocked with totems.", Category.COMBAT);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.CRITICAL_SURVIVAL;
   }

   @Override
   public String info() {
      return this.item.get();
   }

   public boolean wantsTotem(LocalPlayer player) {
      return this.totemHealth.get() > 0.0 && Damage.health(player) <= this.totemHealth.get();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (CombatHelpers.ready(client) && Inventories.inventoryOpenOrClosed(player)) {
         AutoTotemModule totem = Gemini.getInstance().modules().byClass(AutoTotemModule.class).orElse(null);
         if (totem == null || !totem.isEnabled() || !totem.wantsOffhand(player)) {
            if (this.cooldown > 0) {
               this.cooldown--;
            } else {
               Item wanted = this.wantedItem(client, player);
               if (!player.getOffhandItem().is(wanted)) {
                  Item chosen = wanted;
                  int index = Inventories.find(player, stack -> stack.is(wanted), this.hotbarOnly.get());
                  if (index < 0 && (wanted == Items.ENCHANTED_GOLDEN_APPLE || wanted == Items.GOLDEN_APPLE)) {
                     Item fallback = wanted == Items.GOLDEN_APPLE ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE;
                     chosen = fallback;
                     index = Inventories.find(player, stack -> stack.is(fallback), this.hotbarOnly.get());
                  }

                  if (index >= 0 && !player.getOffhandItem().is(chosen)) {
                     this.cooldown = this.delay.intValue();
                     Inventories.swapToOffhand(client, player, index);
                  }
               }
            }
         }
      }
   }

   private Item wantedItem(Minecraft client, LocalPlayer player) {
      if (this.totemHealth.get() > 0.0 && Damage.health(player) <= this.totemHealth.get()) {
         return Items.TOTEM_OF_UNDYING;
      } else if (this.swordGapple.get() && client.options.keyUse.isDown() && player.getMainHandItem().is(ItemTags.SWORDS)) {
         return Items.ENCHANTED_GOLDEN_APPLE;
      } else {
         String var3 = this.item.get();

         return switch (var3) {
            case "Golden Apple" -> Items.GOLDEN_APPLE;
            case "Enchanted Golden Apple" -> Items.ENCHANTED_GOLDEN_APPLE;
            case "End Crystal" -> Items.END_CRYSTAL;
            case "Shield" -> Items.SHIELD;
            default -> Items.TOTEM_OF_UNDYING;
         };
      }
   }
}
