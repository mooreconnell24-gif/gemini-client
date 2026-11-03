package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

public final class Backtrack extends Module {
   private final NumberSetting maxTicks = this.addSetting(new NumberSetting("History Ticks", "Ticks of target positions to retain", 8.0, 2.0, 20.0, 1.0));
   private final BooleanSetting render = this.addSetting(new BooleanSetting("Render Box", "Draw delayed backtrack hitbox in 3D", true));
   private final ArrayDeque<AABB> history = new ArrayDeque<>();
   private LivingEntity tracked;

   public Backtrack() {
      super("Backtrack", "Show where your combat target was a few ticks ago.", Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.history.clear();
      this.tracked = null;
   }

   @Override
   public String info() {
      return this.maxTicks.intValue() + "t";
   }

   @Override
   public void onAttack(Entity target) {
      if (target instanceof LivingEntity living) {
         this.tracked = living;
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (!CombatHelpers.ready(client)) {
         this.history.clear();
      } else {
         if (this.tracked == null || !this.tracked.isAlive() || this.tracked.distanceToSqr(client.player) > 49.0) {
            this.tracked = CombatHelpers.nearestEnemy(client, client.player, 6.0, true);
            this.history.clear();
         }

         if (this.tracked != null) {
            this.history.addLast(this.tracked.getBoundingBox());

            while (this.history.size() > this.maxTicks.intValue()) {
               this.history.removeFirst();
            }
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.render.get() && !this.history.isEmpty()) {
         Render3D.box(this.history.getFirst(), -16722689, true, true, 1.5F, 40, true);
      }
   }
}
