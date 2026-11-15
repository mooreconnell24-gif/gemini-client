package gemini.module.modules.world;

import gemini.module.AuraModuleBase;
import gemini.module.Category;
import gemini.module.setting.BooleanSetting;
import gemini.util.Inventories;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AutoShearer extends AuraModuleBase<LivingEntity> {
   private final BooleanSetting mooshrooms = this.addSetting(new BooleanSetting("Mooshrooms", "Also shear mooshrooms (this turns them into cows)", false));
   private final BooleanSetting preventBreaking = this.addSetting(
      new BooleanSetting("Prevent Breaking", "Stop before the shears are one use from breaking", true)
   );
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when shearing", true));
   private final Predicate<ItemStack> shears = this::usableShears;

   public AutoShearer() {
      super(
         "Auto Shearer",
         "Shear grown sheep with the shears in your hotbar.",
         Category.WORLD,
         LivingEntity.class,
         "How far away animals are sheared",
         "Ticks between shears",
         6.0,
         40.0
      );
   }

   private boolean usableShears(ItemStack stack) {
      return stack.is(Items.SHEARS) && (!this.preventBreaking.get() || !stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 1);
   }

   @Override
   protected boolean prepare(Minecraft client, LocalPlayer player) {
      return Inventories.findHotbar(player, this.shears) >= 0;
   }

   @Override
   protected boolean accepts(LocalPlayer player, LivingEntity candidate, long gameTime) {
      boolean shearable = candidate instanceof Sheep || this.mooshrooms.get() && candidate instanceof MushroomCow;
      return shearable && candidate instanceof Shearable target && target.readyForShearing();
   }

   @Override
   protected Predicate<ItemStack> itemFor(LivingEntity target) {
      return this.shears;
   }

   @Override
   protected long retryTicks(LivingEntity target) {
      return 40L;
   }

   @Override
   protected int idleTicks() {
      return 8;
   }

   @Override
   protected boolean swings() {
      return this.swing.get();
   }
}
