package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class CrosshairPlus extends Module {
   private final NumberSetting gap = this.addSetting(new NumberSetting("Gap", "Center gap in pixels", 3.0, 1.0, 10.0, 1.0));
   private final NumberSetting length = this.addSetting(new NumberSetting("Length", "Arm length in pixels", 5.0, 2.0, 14.0, 1.0));
   private final BooleanSetting dynamic = this.addSetting(new BooleanSetting("Dynamic", "Expand when moving or swinging", true));

   public CrosshairPlus() {
      super("Crosshair", "Dynamic neon crosshair with movement expansion.", Category.RENDER);
   }

   @Override
   public void onRenderHud(HudContext hud) {
      LocalPlayer player = hud.client.player;
      if (player != null) {
         int cx = hud.width / 2;
         int cy = hud.height / 2;
         double speed = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
         int g = this.gap.intValue() + (this.dynamic.get() ? Mth.floor(speed * 14.0) : 0);
         int len = this.length.intValue();
         hud.graphics.fill(cx - g - len, cy, cx - g, cy + 1, hud.accent2);
         hud.graphics.fill(cx + g + 1, cy, cx + g + 1 + len, cy + 1, hud.accent2);
         hud.graphics.fill(cx, cy - g - len, cx + 1, cy - g, hud.accent);
         hud.graphics.fill(cx, cy + g + 1, cx + 1, cy + g + 1 + len, hud.accent);
      }
   }
}
