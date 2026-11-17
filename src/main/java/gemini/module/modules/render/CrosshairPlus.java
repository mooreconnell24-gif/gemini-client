package gemini.module.modules.render;

import gemini.Gemini;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

public final class CrosshairPlus extends Module {
   private final NumberSetting gap = this.addSetting(new NumberSetting("Gap", "Gap in the centre, in pixels", 3.0, 1.0, 10.0, 1.0));
   private final NumberSetting length = this.addSetting(new NumberSetting("Length", "Arm length, in pixels", 5.0, 2.0, 14.0, 1.0));
   private final NumberSetting thickness = this.addSetting(new NumberSetting("Thickness", "Line thickness, in pixels", 1.0, 1.0, 4.0, 1.0));
   private final BooleanSetting dot = this.addSetting(new BooleanSetting("Dot", "Draw a dot in the centre", false));
   private final BooleanSetting outline = this.addSetting(new BooleanSetting("Outline", "Draw a dark edge around the lines so they show on bright backgrounds", true));
   private final BooleanSetting customColor = this.addSetting(new BooleanSetting("Custom Color", "Use your own colour instead of the theme colours", false));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Crosshair colour", 0, 0.0F, 1.0F).showIf(this.customColor::get));
   private final BooleanSetting entityColor = this.addSetting(
      new BooleanSetting("Highlight Targets", "Turn the crosshair red while it is over a mob or player", true)
   );
   private final BooleanSetting dynamic = this.addSetting(new BooleanSetting("Dynamic", "Widen while moving or swinging", true));
   private final BooleanSetting hideVanilla = this.addSetting(
      new BooleanSetting("Hide Vanilla", "Hide the vanilla crosshair while this one is drawn", true)
   );
   private static final int TARGET_COLOR = -43691;
   private static final int EDGE_COLOR = -1610612736;
   /** Read by the wrapped vanilla crosshair element every frame; written on the client thread. */
   private static volatile boolean replacingVanilla;

   public CrosshairPlus() {
      super("Crosshair", "Draws a custom crosshair: your size, colour and shape, widening while you move or swing.", Category.RENDER);
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
         int t = this.thickness.intValue();
         // The centre is t pixels wide, starting half a thickness before the middle of the screen.
         int c0x = cx - t / 2;
         int c0y = cy - t / 2;
         boolean onTarget = this.entityColor.get() && hud.client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity;
         int horizontal = onTarget ? TARGET_COLOR : this.customColor.get() ? this.color.argb() : hud.accent2;
         int vertical = onTarget ? TARGET_COLOR : this.customColor.get() ? this.color.argb() : hud.accent;
         boolean edge = this.outline.get();
         this.bar(hud, c0x - g - len, c0y, c0x - g, c0y + t, horizontal, edge);
         this.bar(hud, c0x + t + g, c0y, c0x + t + g + len, c0y + t, horizontal, edge);
         this.bar(hud, c0x, c0y - g - len, c0x + t, c0y - g, vertical, edge);
         this.bar(hud, c0x, c0y + t + g, c0x + t, c0y + t + g + len, vertical, edge);
         if (this.dot.get()) {
            this.bar(hud, c0x, c0y, c0x + t, c0y + t, vertical, edge);
         }
      }
   }

   private void bar(HudContext hud, int x0, int y0, int x1, int y1, int argb, boolean edge) {
      if (edge) {
         hud.graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, EDGE_COLOR);
      }

      hud.graphics.fill(x0, y0, x1, y1, argb);
   }
}
