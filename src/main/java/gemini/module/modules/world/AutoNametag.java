package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AutoNametag extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far away mobs are named", 4.5, 1.0, 6.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between name tags", 6.0, 0.0, 40.0, 1.0));
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
   private final Int2LongOpenHashMap nextTry = new Int2LongOpenHashMap();
   private int cooldown;
   private boolean warned;

   public AutoNametag() {
      super("Auto Nametag", "Name nearby unnamed mobs with a renamed name tag from your hotbar.", Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.nextTry.clear();
      this.warned = false;
   }

   private static boolean namedTag(ItemStack stack) {
      return stack.is(Items.NAME_TAG) && stack.has(DataComponents.CUSTOM_NAME);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (WorldHelpers.readyNoScreen(client)) {
         LocalPlayer player = client.player;
         if (BlockPlacer.pickSlot(player, AutoNametag::namedTag) < 0) {
            if (!this.warned) {
               this.warned = true;
               CommandManager.feedback("§bAuto Nametag §7needs a name tag you renamed in an anvil on your hotbar.");
            }

            this.cooldown = 20;
         } else {
            this.warned = false;
            ClientLevel level = client.level;
            long now = level.getGameTime();
            double reach = this.range.get();
            Set<EntityType<?>> allowed = this.entities.entityTypes();
            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;

            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach + 1.0))) {
               if (candidate != player
                  && !(candidate instanceof Player)
                  && candidate.isAlive()
                  && !candidate.hasCustomName()
                  && this.nextTry.get(candidate.getId()) <= now
                  && allowed.contains(candidate.getType())) {
                  double distance = candidate.getBoundingBox().distanceToSqr(player.getEyePosition());
                  if (distance <= reach * reach && distance < bestDistance) {
                     best = candidate;
                     bestDistance = distance;
                  }
               }
            }

            if (best == null) {
               this.cooldown = 10;
            } else {
               WorldHelpers.useOnEntity(client, player, best, AutoNametag::namedTag, this.swing.get());
               if (this.nextTry.size() > 1024) {
                  this.nextTry.clear();
               }

               this.nextTry.put(best.getId(), now + 60L);
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
