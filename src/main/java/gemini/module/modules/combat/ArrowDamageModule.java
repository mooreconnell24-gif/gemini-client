package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.Load;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

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
         }
      });
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
