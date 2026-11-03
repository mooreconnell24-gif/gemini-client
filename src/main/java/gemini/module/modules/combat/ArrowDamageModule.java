package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.Load;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ArrowDamageModule extends Module {
   public static ArrowDamageModule instance;
   private final NumberSetting damage = this.addSetting(
      new NumberSetting("Base Damage", "Arrow base damage (vanilla is 2; hits do about 3x this at full draw)", 50.0, 2.0, 1000.0, 1.0)
   );
   private final BooleanSetting tridents = this.addSetting(new BooleanSetting("Tridents", "Also boost thrown tridents", true));

   public ArrowDamageModule() {
      super("Arrow Damage", "Your arrows do crazy damage.", Category.COMBAT);
      instance = this;
      ServerEntityEvents.ENTITY_LOAD.register((Load)(entity, level) -> {
         ArrowDamageModule module = instance;
         if (module != null && module.isEnabled() && entity instanceof AbstractArrow arrow && module.boosts(arrow) && firedByLocalPlayer(arrow)) {
            arrow.setBaseDamage(module.damage.get());
            // Another mod listening to the same event (the Inferno anti-cheat resets arrows to 2) may run after
            // this one, so set the damage once more on the next server tick, after every listener has run.
            module.pending.add(arrow);
         }
      });
   }

   private final Queue<AbstractArrow> pending = new ConcurrentLinkedQueue<>();

   @Override
   public void onDisable() {
      this.pending.clear();
   }

   @Override
   public void onTick() {
      if (this.pending.isEmpty()) {
         return;
      }

      List<AbstractArrow> arrows = new ArrayList<>();
      AbstractArrow next;
      while ((next = this.pending.poll()) != null) {
         arrows.add(next);
      }

      IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
      double value = this.damage.get();
      if (server != null) {
         server.execute(() -> {
            for (AbstractArrow arrow : arrows) {
               if (!arrow.isRemoved()) {
                  arrow.setBaseDamage(value);
               }
            }
         });
      }
   }

   private boolean boosts(AbstractArrow arrow) {
      return this.tridents.get() || !arrow.getType().toString().contains("trident");
   }

   private static boolean firedByLocalPlayer(AbstractArrow arrow) {
      Minecraft client = Minecraft.getInstance();
      IntegratedServer server = client == null ? null : client.getSingleplayerServer();
      return server != null
         && !server.isPublished()
         && client.player != null
         && arrow.getOwner() instanceof Player shooter
         && shooter.getUUID().equals(client.player.getUUID());
   }

   @Override
   public String info() {
      return String.format("%.0f", this.damage.get());
   }
}
