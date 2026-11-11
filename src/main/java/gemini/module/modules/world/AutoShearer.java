package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AutoShearer extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far away animals are sheared", 4.5, 1.0, 6.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between shears", 6.0, 0.0, 40.0, 1.0));
   private final BooleanSetting mooshrooms = this.addSetting(new BooleanSetting("Mooshrooms", "Also shear mooshrooms (this turns them into cows)", false));
   private final BooleanSetting preventBreaking = this.addSetting(
      new BooleanSetting("Prevent Breaking", "Stop before the shears are one use from breaking", true)
   );
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when shearing", true));
   private final Int2LongOpenHashMap nextTry = new Int2LongOpenHashMap();
   private int cooldown;

   public AutoShearer() {
      super("Auto Shearer", "Shear grown sheep with the shears in your hotbar.", Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.nextTry.clear();
   }

   private boolean usableShears(ItemStack stack) {
      return !stack.is(Items.SHEARS) ? false : !this.preventBreaking.get() || !stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 1;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (WorldHelpers.readyNoScreen(client)) {
         LocalPlayer player = client.player;
         if (BlockPlacer.pickSlot(player, this::usableShears) < 0) {
            this.cooldown = 20;
         } else {
            ClientLevel level = client.level;
            long now = level.getGameTime();
            double reach = this.range.get();
            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;

            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach + 1.0))) {
               boolean shearable = candidate instanceof Sheep || this.mooshrooms.get() && candidate instanceof MushroomCow;
               if (shearable && candidate instanceof Shearable target && target.readyForShearing() && this.nextTry.get(candidate.getId()) <= now) {
                  double distance = candidate.getBoundingBox().distanceToSqr(player.getEyePosition());
                  if (distance <= reach * reach && distance < bestDistance) {
                     best = candidate;
                     bestDistance = distance;
                  }
               }
            }

            if (best == null) {
               this.cooldown = 8;
            } else {
               WorldHelpers.useOnEntity(client, player, best, this::usableShears, this.swing.get());
               if (this.nextTry.size() > 1024) {
                  this.nextTry.clear();
               }

               this.nextTry.put(best.getId(), now + 40L);
               this.cooldown = this.delay.intValue();
               WorldHelpers.trim(this.nextTry);
            }
         }
      }
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.nextTry.clear();
   }
}
