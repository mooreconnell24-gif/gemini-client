package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.modules.misc.BypassModule;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.AttributeBoost;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class ReachModule extends Module {
   private final NumberSetting blocks = this.addSetting(new NumberSetting("Blocks", "Block interaction reach", 6.0, 4.5, 10.0, 0.1));
   private final BooleanSetting entityReach = this.addSetting(new BooleanSetting("Entity Reach", "Also extend attack reach", true));
   private final NumberSetting entities = this.addSetting(
      new NumberSetting("Entities", "Entity attack reach", 4.0, 3.0, 8.0, 0.1).showIf(this.entityReach::get)
   );

   public ReachModule() {
      super("Reach", "Interact and attack from farther away.", Category.PLAYER);
   }

   @Override
   public void onDisable() {
      AttributeBoost.clear(Attributes.BLOCK_INTERACTION_RANGE, "reach");
      AttributeBoost.clear(Attributes.ENTITY_INTERACTION_RANGE, "entity_reach");
   }

   @Override
   public void onTick() {
      AttributeBoost.apply(Attributes.BLOCK_INTERACTION_RANGE, "reach", BypassModule.blockReach(this.blocks.get()) - 4.5, Operation.ADD_VALUE);
      if (this.entityReach.get()) {
         AttributeBoost.apply(Attributes.ENTITY_INTERACTION_RANGE, "entity_reach", BypassModule.entityReach(this.entities.get()) - 3.0, Operation.ADD_VALUE);
      } else {
         AttributeBoost.clear(Attributes.ENTITY_INTERACTION_RANGE, "entity_reach");
      }
   }
}
