package gemini.module.modules.combat;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.Targeting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

public final class Backtrack extends Module {
   private static final double SCAN_RANGE = 6.0;
   private static final double KEEP_RANGE_SQ = 49.0;
   private final NumberSetting maxTicks = this.addSetting(new NumberSetting("History Ticks", "Ticks of target positions to retain", 8.0, 2.0, 20.0, 1.0));
   private final BooleanSetting render = this.addSetting(new BooleanSetting("Render Box", "Draw delayed backtrack hitbox in 3D", true));
   // Stick by default: the history should follow one entity, as it always did.
   private final Targeting targeting = Targeting.builder(this).stick(true).build();
   private final ArrayDeque<AABB> history = new ArrayDeque<>();
   /** The entity you last hit: tracked ahead of the Priority pick while it stays alive and within 7 blocks. */
   private LivingEntity hit;
   private LivingEntity tracked;

   public Backtrack() {
      super("Backtrack", "Show where your combat target was a few ticks ago.", Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.history.clear();
      this.hit = null;
      this.tracked = null;
      this.targeting.clear();
   }

   @Override
   public String info() {
      return this.maxTicks.intValue() + "t";
   }

   @Override
   public void onAttack(Entity target) {
      if (target instanceof LivingEntity living) {
         this.hit = living;
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (!ClientState.ready(client)) {
         this.history.clear();
         this.tracked = null;
         this.targeting.clear();
      } else {
         LocalPlayer player = client.player;
         LivingEntity next;
         if (this.hit != null && this.hit.isAlive() && !this.hit.isRemoved() && this.hit.distanceToSqr(player) <= KEEP_RANGE_SQ) {
            next = this.hit;
            this.targeting.setTarget(next);
         } else {
            this.hit = null;
            next = this.targeting.pick(player, client.level.entitiesForRendering(), SCAN_RANGE, null);
         }

         if (next != this.tracked) {
            this.tracked = next;
            this.history.clear();
         }

         if (next != null) {
            this.history.addLast(next.getBoundingBox());

            while (this.history.size() > this.maxTicks.intValue()) {
               this.history.removeFirst();
            }
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
      if (this.render.get() && !this.history.isEmpty()) {
         Render3D.box(this.history.getFirst(), -16722689, true, true, 1.5F, 40, true);
      }
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.onDisable();
   }
}
