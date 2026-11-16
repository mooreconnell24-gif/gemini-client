package gemini.module.modules.render;

import gemini.Gemini;
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
   private final BooleanSetting hideVanilla = this.addSetting(
      new BooleanSetting("Hide Vanilla", "Hide the vanilla crosshair while this one is drawn, instead of drawing on top of it", true)
   );
   /** Read by the wrapped vanilla crosshair element every frame; written on the client thread. */
   private static volatile boolean replacingVanilla;

   public CrosshairPlus() {
      super("Crosshair", "Dynamic neon crosshair with movement expansion.", Category.RENDER);
   }

   /** Whether the vanilla crosshair should be skipped this frame. */
   public static boolean replacesVanilla() {
      return replacingVanilla;
   }

   @Override
   public void onEnable() {
      replacingVanilla = this.shouldReplace();
   }

   /** Only while Gemini's HUD is drawn: with the HUD module off this crosshair is not drawn, so vanilla's must stay. */
   private boolean shouldReplace() {
      if (!this.hideVanilla.get()) {
         return false;
      }

      Gemini gemini = Gemini.getInstance();
      HudModule hud = gemini == null ? null : gemini.modules().byClass(HudModule.class).orElse(null);
      return hud == null || hud.isEnabled();
   }

   @Override
   public void onDisable() {
      replacingVanilla = false;
   }

   @Override
   public void onTick() {
      // The setting can change while the module is on; following it once a tick is plenty.
      replacingVanilla = this.shouldReplace();
   }

   @Override
   public void onRenderHud(HudContext hud) {
      LocalPlayer player = hud.client.player;
      if (player != null) {
         int cx = hud.width / 2;
         int cy = hud.height / 2;
         double speed = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
         // Moving spreads the arms by up to a few pixels, a swing by up to four more at the top of the swing.
         int spread = Mth.floor(speed * 14.0) + Mth.floor(Mth.sin(player.getSwingAnimation(hud.partialTick) * Mth.PI) * 4.0F);
         int g = this.gap.intValue() + (this.dynamic.get() ? spread : 0);
         int len = this.length.intValue();
         hud.graphics.fill(cx - g - len, cy, cx - g, cy + 1, hud.accent2);
         hud.graphics.fill(cx + g + 1, cy, cx + g + 1 + len, cy + 1, hud.accent2);
         hud.graphics.fill(cx, cy - g - len, cx + 1, cy - g, hud.accent);
         hud.graphics.fill(cx, cy + g + 1, cx + 1, cy + g + 1 + len, hud.accent);
      }
   }
}
