package gemini.module.modules.combat;

import gemini.util.InfoText;
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

public final class ArrowDamageModule extends Module {
   public static ArrowDamageModule instance;
   private final NumberSetting damage = this.addSetting(
      new NumberSetting("Base Damage", "Base damage of your arrows (vanilla is 2; a full-draw hit does about three times this)", 50.0, 2.0, 1000.0, 1.0)
   );
   private final BooleanSetting tridents = this.addSetting(new BooleanSetting("Tridents", "Also raise the damage of tridents you throw", true));

   public ArrowDamageModule() {
      super("Arrow Damage", "Raises the damage of arrows you shoot.", Category.COMBAT);
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

   private final InfoText infoText = new InfoText();

   @Override
   public String info() {
      return this.infoText.fixed(this.damage.get(), 0);
   }
}
