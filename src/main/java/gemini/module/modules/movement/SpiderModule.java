package gemini.module.modules.movement;

import java.util.List;
import java.util.ArrayList;
import gemini.util.InfoText;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class SpiderModule extends Module {
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Climbing speed, in blocks per tick", 0.3, 0.1, 0.8, 0.05));
   private final BooleanSetting requireForward = this.addSetting(new BooleanSetting("Require Forward", "Only climb while moving into the wall", true));

   public SpiderModule() {
      super("Spider", "Lets you climb up walls.", Category.MOVEMENT);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("WallClimb");
      return names;
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null
         && player.horizontalCollision
         && !player.isInWater()
         && !player.isInLava()
         && (!this.requireForward.get() || player.input != null && player.input.hasForwardImpulse())) {
         Vec3 motion = player.getDeltaMovement();
         player.setDeltaMovement(motion.x, this.speed.get(), motion.z);
         player.resetFallDistance();
      }
   }

   private final InfoText infoText = new InfoText();

   @Override
   public String info() {
      return this.infoText.fixed(this.speed.get(), 2);
   }
}
