package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Targets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class Hitboxes extends Module {
   public static Hitboxes instance;
   private final NumberSetting expand = this.addSetting(new NumberSetting("Expand", "Extra blocks added to target hitboxes", 0.25, 0.0, 1.5, 0.05));
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Expand player hitboxes", true));
   private final BooleanSetting hostile = this.addSetting(new BooleanSetting("Hostile", "Expand hostile mob hitboxes", false));
   private final BooleanSetting passive = this.addSetting(new BooleanSetting("Passive", "Expand animal hitboxes", false));

   public Hitboxes() {
      super("Hitboxes", "Expand entity hitboxes for easier melee hits.", Category.COMBAT);
      instance = this;
   }

   public float extra(Entity entity) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && entity != player && entity instanceof LivingEntity) {
         return !Targets.isCandidate(player, entity, this.players.get(), this.hostile.get(), this.passive.get(), true) ? 0.0F : this.expand.floatValue();
      } else {
         return 0.0F;
      }
   }

   @Override
   public String info() {
      return String.format("%.2f", this.expand.get());
   }
}
