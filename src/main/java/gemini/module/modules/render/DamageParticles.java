package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.render.Render3D;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class DamageParticles extends Module {
   private final Map<Integer, Float> lastHealth = new HashMap<>();
   private final ArrayDeque<DamageParticles.Popup> popups = new ArrayDeque<>();

   public DamageParticles() {
      super("DamageParticles", "Show floating 3D damage and heal numbers on hit.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.lastHealth.clear();
      this.popups.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.level != null && client.player != null) {
         for (Entity entity : client.level.entitiesForRendering()) {
            if (entity != client.player && entity instanceof LivingEntity living && entity.distanceToSqr(client.player) <= 400.0) {
               float hp = living.getHealth() + living.getAbsorptionAmount();
               Float prev = this.lastHealth.put(living.getId(), hp);
               if (prev != null && Math.abs(prev - hp) >= 0.5F) {
                  float diff = hp - prev;
                  String text = String.format(Locale.ROOT, "%s%.1f", diff < 0.0F ? "-" : "+", Math.abs(diff));
                  int color = diff < 0.0F ? -1548963 : -11086174;
                  this.popups
                     .addLast(
                        new DamageParticles.Popup(
                           text, living.position().add(0.0, living.getBbHeight() + 0.35, 0.0), color, System.currentTimeMillis()
                        )
                     );
                  if (this.popups.size() > 16) {
                     this.popups.removeFirst();
                  }
               }
            }
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      long now = System.currentTimeMillis();
      Iterator<DamageParticles.Popup> it = this.popups.iterator();

      while (it.hasNext()) {
         DamageParticles.Popup p = it.next();
         float age = (float)(now - p.createdAt) / 1100.0F;
         if (age >= 1.0F) {
            it.remove();
         } else {
            Render3D.text(p.text, p.pos.add(0.0, age * 0.65, 0.0), p.color, 0.9F, true);
         }
      }
   }

   private record Popup(String text, Vec3 pos, int color, long createdAt) {
   }
}
