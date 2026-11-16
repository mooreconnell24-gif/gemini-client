package gemini.module.modules.render;

import gemini.input.Keys;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;

/**
 * Optic zoom. The zoom level is eased every frame (not every tick, which steps visibly at 20 Hz) and keeps easing
 * back out after the key is released, so the camera asks {@link #zooming()} rather than whether the module is on.
 */
public final class ZoomModule extends Module {
   /** How fast the zoom eases, per second: about the old 35 % of the gap per tick. */
   static final float EASE_RATE = 8.6F;
   private final NumberSetting factor = this.addSetting(new NumberSetting("Factor", "How far the view zooms in", 4.0, 1.5, 12.0, 0.5));
   private final BooleanSetting smooth = this.addSetting(new BooleanSetting("Smooth", "Ease in and out of the zoom", true));
   private float current = 1.0F;
   private long lastFrame;

   public ZoomModule() {
      super("Zoom", "Optic zoom. Bind it to a key and hold it.", Category.RENDER, Keys.fromGlfw('Z'));
      this.holdByDefault();
   }

   @Override
   public void onEnable() {
      if (this.current <= 1.001F) {
         // Not easing out of an earlier zoom: the first frame must not count the time since the last one.
         this.lastFrame = 0L;
      }

      if (!this.smooth.get()) {
         this.current = this.factor.floatValue();
      }
   }

   @Override
   public void onDisable() {
      if (!this.smooth.get()) {
         this.current = 1.0F;
      }
   }

   /** True while the view is zoomed at all: on, or still easing back out after being switched off. */
   public boolean zooming() {
      return this.isEnabled() || this.current > 1.001F;
   }

   /** The zoom divisor for this frame (1 = no zoom). Advances the easing; the camera calls it once per frame. */
   public float multiplier() {
      long now = System.nanoTime();
      float dt = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, Math.max(0.0F, (now - this.lastFrame) / 1.0E9F));
      this.lastFrame = now;
      float target = this.isEnabled() ? this.factor.floatValue() : 1.0F;
      this.current = this.smooth.get() ? ease(this.current, target, dt) : target;
      return Math.max(1.0F, this.current);
   }

   /** One frame of exponential easing from {@code current} toward {@code target}; snaps once it is within 0.001. */
   static float ease(float current, float target, float dt) {
      float next = current + (target - current) * (1.0F - (float)Math.exp(-EASE_RATE * Math.max(0.0F, dt)));
      return Math.abs(target - next) < 0.001F ? target : next;
   }
}
