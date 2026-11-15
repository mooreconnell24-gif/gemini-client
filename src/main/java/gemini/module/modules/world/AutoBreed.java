package gemini.module.modules.world;

import gemini.module.AuraModuleBase;
import gemini.module.Category;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;

public final class AutoBreed extends AuraModuleBase<Animal> {
   private static final int MAX_TRACKED = 512;
   private final ModeSetting age = this.addSetting(new ModeSetting("Age", "Which animals get fed", "Adults", "Adults", "Babies", "Both"));
   private final ListSetting animals = this.addSetting(
      new ListSetting("Animals", "Only feed these (leave empty to feed every animal)", ListSetting.Kind.ENTITY)
   );
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when feeding", true));
   /** Game time until which an animal is known to be in love (the server's heart particles event). */
   private final Int2LongOpenHashMap loveUntil = new Int2LongOpenHashMap();
   /** Feedings that did not put the animal in love, for backing off from ones that cannot breed right now. */
   private final Int2IntOpenHashMap misses = new Int2IntOpenHashMap();
   private Set<EntityType<?>> allowed = Set.of();

   public AutoBreed() {
      super(
         "Auto Breed",
         "Feed nearby animals with food from your hotbar so they breed.",
         Category.WORLD,
         Animal.class,
         "How far away animals are fed",
         "Ticks between feedings",
         3.0,
         20.0
      );
   }

   private void forget() {
      this.loveUntil.clear();
      this.misses.clear();
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.forget();
      this.allowed = Set.of();
   }

   @Override
   public void onWorldChange() {
      super.onWorldChange();
      this.forget();
   }

   @Override
   public boolean onReceivePacket(Packet<?> packet) {
      if (packet instanceof ClientboundEntityEventPacket event && event.getEventId() == 18) {
         ClientLevel level = Minecraft.getInstance().level;
         Entity entity = level == null ? null : event.getEntity(level);
         if (entity != null) {
            if (this.loveUntil.size() >= MAX_TRACKED) {
               this.loveUntil.clear();
            }

            this.loveUntil.put(entity.getId(), level.getGameTime() + 600L);
            this.misses.remove(entity.getId());
         }
      }

      return false;
   }

   @Override
   protected boolean prepare(Minecraft client, LocalPlayer player) {
      this.allowed = this.animals.isEmpty() ? Set.of() : this.animals.entityTypes();
      return true;
   }

   @Override
   protected boolean accepts(LocalPlayer player, Animal animal, long gameTime) {
      return this.loveUntil.get(animal.getId()) <= gameTime
         && (!this.age.is("Adults") || !animal.isBaby())
         && (!this.age.is("Babies") || animal.isBaby())
         && (this.animals.isEmpty() || this.allowed.contains(animal.getType()));
   }

   @Override
   protected Predicate<ItemStack> itemFor(Animal target) {
      return target::isFood;
   }

   /** Doubles from five seconds up to five minutes for an animal that keeps taking food without falling in love. */
   @Override
   protected long retryTicks(Animal target) {
      return Math.min(6000L, 100L << Math.min(this.misses.get(target.getId()), 6));
   }

   @Override
   protected void afterUse(Animal target, long gameTime) {
      if (this.misses.size() >= MAX_TRACKED) {
         this.misses.clear();
      }

      this.misses.put(target.getId(), this.misses.get(target.getId()) + 1);
   }

   @Override
   protected int idleTicks() {
      return 5;
   }

   @Override
   protected boolean swings() {
      return this.swing.get();
   }
}
