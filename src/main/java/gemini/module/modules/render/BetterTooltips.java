package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Map;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public final class BetterTooltips extends Module {
   private final BooleanSetting durability = this.addSetting(new BooleanSetting("Durability", "Exact remaining uses and percentage", true));
   private final BooleanSetting food = this.addSetting(new BooleanSetting("Food", "Hunger and saturation values", true));
   private final BooleanSetting storage = this.addSetting(new BooleanSetting("Storage", "Item totals inside containers", true));
   private final BooleanSetting itemId = this.addSetting(new BooleanSetting("Item Id", "The registry id of the item", false));
   private final NumberSetting storageLines = this.addSetting(
      new NumberSetting("Storage Lines", "Most item types listed", 6.0, 1.0, 27.0, 1.0).showIf(this.storage::get)
   );

   public BetterTooltips() {
      super("Better Tooltips", "Add durability, food and container details to item tooltips.", Category.RENDER);
      ItemTooltipCallback.EVENT.register((ItemTooltipCallback)(stack, context, flag, lines) -> {
         if (this.isEnabled()) {
            this.decorate(stack, lines);
         }
      });
   }

   private void decorate(ItemStack stack, List<Component> lines) {
      if (this.durability.get() && stack.isDamageableItem()) {
         int max = stack.getMaxDamage();
         int left = max - stack.getDamageValue();
         lines.add(
            Component.literal(String.format(Locale.ROOT, "Durability: %d / %d (%d%%)", left, max, Math.round(100.0F * left / Math.max(1, max))))
               .withStyle(ChatFormatting.GRAY)
         );
      }

      FoodProperties properties = (FoodProperties)stack.get(DataComponents.FOOD);
      if (this.food.get() && properties != null) {
         lines.add(
            Component.literal(String.format(Locale.ROOT, "Hunger: +%d   Saturation: +%.1f", properties.nutrition(), properties.saturation()))
               .withStyle(ChatFormatting.GOLD)
         );
      }

      ItemContainerContents contents = (ItemContainerContents)stack.get(DataComponents.CONTAINER);
      if (this.storage.get() && contents != null) {
         Map<String, Integer> totals = new LinkedHashMap<>();
         contents.nonEmptyItemCopyStream().forEach(item -> totals.merge(item.getHoverName().getString(), item.getCount(), Integer::sum));
         int shown = 0;

         for (Entry<String, Integer> entry : totals.entrySet()) {
            if (shown++ >= this.storageLines.intValue()) {
               lines.add(Component.literal("  and " + (totals.size() - this.storageLines.intValue()) + " more...").withStyle(ChatFormatting.DARK_GRAY));
               break;
            }

            lines.add(Component.literal("  " + entry.getValue() + "x " + entry.getKey()).withStyle(ChatFormatting.AQUA));
         }
      }

      if (this.itemId.get()) {
         lines.add(Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
      }
   }
}
