package gemini.module;

import gemini.util.AttributeBoost;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

/**
 * A module that is nothing but attribute modifiers held on the player while it is on: Gravity, Size, Extra Hearts
 * and the like. A subclass declares its modifiers once with {@link #modifier}; this class keeps them applied on the
 * client and the integrated server every tick (so slider changes take effect at once) and removes them on disable.
 */
public abstract class AttributeModuleBase extends Module {
   private final List<Entry> entries = new ArrayList<>();

   protected AttributeModuleBase(String name, String description, Category category) {
      super(name, description, category);
   }

   /**
    * Declares one modifier. {@code amount} is read every tick; while it returns 0 the modifier is removed, which is
    * how a subclass switches one off from a setting.
    */
   protected final void modifier(Holder<Attribute> attribute, String id, Operation operation, DoubleSupplier amount) {
      this.entries.add(new Entry(attribute, id, operation, amount));
   }

   @Override
   public void onTick() {
      for (Entry entry : this.entries) {
         double amount = entry.amount.getAsDouble();
         if (amount == 0.0) {
            AttributeBoost.clear(entry.attribute, entry.id);
         } else {
            AttributeBoost.apply(entry.attribute, entry.id, amount, entry.operation);
         }
      }
   }

   @Override
   public void onDisable() {
      for (Entry entry : this.entries) {
         AttributeBoost.clear(entry.attribute, entry.id);
      }
   }

   private record Entry(Holder<Attribute> attribute, String id, Operation operation, DoubleSupplier amount) {
   }
}
