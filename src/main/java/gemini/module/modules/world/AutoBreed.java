package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;

public final class AutoBreed extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far away animals are fed", 4.5, 1.0, 6.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between feedings", 3.0, 0.0, 20.0, 1.0));
   private final ModeSetting age = this.addSetting(new ModeSetting("Age", "Which animals get fed", "Adults", "Adults", "Babies", "Both"));
   private final ListSetting animals = this.addSetting(
      new ListSetting("Animals", "Only feed these (leave empty to feed every animal)", ListSetting.Kind.ENTITY)
   );
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when feeding", true));
   private final Int2LongOpenHashMap loveUntil = new Int2LongOpenHashMap();
   private final Int2LongOpenHashMap nextTry = new Int2LongOpenHashMap();
   private final Int2IntOpenHashMap misses = new Int2IntOpenHashMap();
   private int cooldown;

   public AutoBreed() {
      super("Auto Breed", "Feed nearby animals with food from your hotbar so they breed.", Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.loveUntil.clear();
      this.nextTry.clear();
      this.misses.clear();
   }

   @Override
   public boolean onReceivePacket(Packet<?> packet) {
      if (packet instanceof ClientboundEntityEventPacket event && event.getEventId() == 18) {
         ClientLevel level = Minecraft.getInstance().level;
         Entity entity = level == null ? null : event.getEntity(level);
         if (entity != null) {
            this.loveUntil.put(entity.getId(), level.getGameTime() + 600L);
            this.misses.remove(entity.getId());
         }
      }

      return false;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (WorldHelpers.readyNoScreen(client)) {
         LocalPlayer player = client.player;
         ClientLevel level = client.level;
         long now = level.getGameTime();
         double reach = this.range.get();
         Set<EntityType<?>> allowed = this.animals.entityTypes();
         Animal best = null;
         double bestDistance = Double.MAX_VALUE;

         for (Animal animal : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(reach + 1.0))) {
            int id = animal.getId();
            if (animal.isAlive()
               && this.loveUntil.get(id) <= now
               && this.nextTry.get(id) <= now
               && (!this.age.is("Adults") || !animal.isBaby())
               && (!this.age.is("Babies") || animal.isBaby())
               && (this.animals.isEmpty() || allowed.contains(animal.getType()))) {
               double distance = animal.getBoundingBox().distanceToSqr(player.getEyePosition());
               if (!(distance > reach * reach) && !(distance >= bestDistance) && BlockPlacer.pickSlot(player, animal::isFood) >= 0) {
                  best = animal;
                  bestDistance = distance;
               }
            }
         }

         if (best == null) {
            this.cooldown = 5;
         } else {
            WorldHelpers.useOnEntity(client, player, best, best::isFood, this.swing.get());
            int id = best.getId();
            int tries = this.misses.get(id);
            if (this.nextTry.size() > 1024) {
               this.nextTry.clear();
               this.loveUntil.clear();
            }

            this.nextTry.put(id, now + Math.min(6000L, 100L << Math.min(tries, 6)));
            this.misses.put(id, tries + 1);
            this.cooldown = this.delay.intValue();
            WorldHelpers.trim(this.loveUntil);
            WorldHelpers.trim(this.nextTry);
            if (this.misses.size() > 512) {
               this.misses.clear();
            }
         }
      }
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.loveUntil.clear();
      this.nextTry.clear();
      this.misses.clear();
   }
}
