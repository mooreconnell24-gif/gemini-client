package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.SpCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** Keeps the area around you clear by removing mobs (or dropped items) with the server's /kill command. */
public final class MobSweeper extends Module {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "How far around you to clear", 24.0, 4.0, 128.0, 1.0));
   private final NumberSetting interval = this.addSetting(new NumberSetting("Interval", "Ticks between sweeps", 20.0, 1.0, 200.0, 1.0));
   private final ModeSetting targets = this.addSetting(
      new ModeSetting("Targets", "What to remove", "Hostile", "Hostile", "All Mobs", "Items", "Mobs And Items")
   );
   private final NumberSetting perSweep = this.addSetting(new NumberSetting("Per Sweep", "Most entities removed in one sweep", 16.0, 1.0, 64.0, 1.0));
   private int timer;
   private int removed;

   public MobSweeper() {
      super("Mob Sweeper", "Remove hostile mobs, animals or dropped items around you with /kill.", Category.WORLD);
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onEnable() {
      this.timer = 0;
      this.removed = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.level == null || ++this.timer < this.interval.intValue()) {
         return;
      }

      this.timer = 0;
      double range = this.radius.get() * this.radius.get();
      int swept = 0;

      for (Entity entity : client.level.entitiesForRendering()) {
         if (entity != player && entity.isAlive() && !(entity instanceof Player) && this.matches(entity) && player.distanceToSqr(entity) <= range) {
            if (!SpCommands.run("kill " + entity.getUUID())) {
               return;
            }

            this.removed++;
            if (++swept >= this.perSweep.intValue()) {
               return;
            }
         }
      }
   }

   private boolean matches(Entity entity) {
      return switch (this.targets.get()) {
         case "All Mobs" -> entity instanceof LivingEntity;
         case "Items" -> entity instanceof ItemEntity;
         case "Mobs And Items" -> entity instanceof LivingEntity || entity instanceof ItemEntity;
         default -> entity instanceof Enemy;
      };
   }

   @Override
   public String info() {
      return this.removed > 0 ? this.targets.get() + " · " + this.removed : this.targets.get();
   }
}
