package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.AuraModuleBase;
import gemini.module.Category;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.util.Inventories;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AutoNametag extends AuraModuleBase<LivingEntity> {
   private static final Predicate<ItemStack> NAMED_TAG = stack -> stack.is(Items.NAME_TAG) && stack.has(DataComponents.CUSTOM_NAME);
   private final ListSetting entities = this.addSetting(
      new ListSetting(
         "Entities",
         "Which mobs get a name tag",
         ListSetting.Kind.ENTITY,
         "minecraft:wolf",
         "minecraft:cat",
         "minecraft:horse",
         "minecraft:donkey",
         "minecraft:mule",
         "minecraft:llama",
         "minecraft:parrot",
         "minecraft:axolotl",
         "minecraft:fox",
         "minecraft:allay",
         "minecraft:camel",
         "minecraft:villager",
         "minecraft:iron_golem"
      )
   );
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when naming", true));
   /** The allowed types for the tick being evaluated; the list setting builds a new set on every call. */
   private Set<EntityType<?>> allowed = Set.of();
   private boolean warned;

   public AutoNametag() {
      super(
         "Auto Nametag",
         "Name nearby unnamed mobs with a renamed name tag from your hotbar.",
         Category.WORLD,
         LivingEntity.class,
         "How far away mobs are named",
         "Ticks between name tags",
         6.0,
         40.0
      );
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.warned = false;
      this.allowed = Set.of();
   }

   @Override
   protected boolean prepare(Minecraft client, LocalPlayer player) {
      if (Inventories.findHotbar(player, NAMED_TAG) < 0) {
         if (!this.warned) {
            this.warned = true;
            CommandManager.feedback("§bAuto Nametag §7needs a name tag you renamed in an anvil on your hotbar.");
         }

         return false;
      }

      this.warned = false;
      this.allowed = this.entities.entityTypes();
      return true;
   }

   @Override
   protected boolean accepts(LocalPlayer player, LivingEntity candidate, long gameTime) {
      return !(candidate instanceof Player) && !candidate.hasCustomName() && this.allowed.contains(candidate.getType());
   }

   @Override
   protected Predicate<ItemStack> itemFor(LivingEntity target) {
      return NAMED_TAG;
   }

   @Override
   protected long retryTicks(LivingEntity target) {
      return 60L;
   }

   @Override
   protected int idleTicks() {
      return 10;
   }

   @Override
   protected boolean swings() {
      return this.swing.get();
   }
}
