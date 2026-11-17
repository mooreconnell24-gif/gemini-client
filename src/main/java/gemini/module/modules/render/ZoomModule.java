package gemini.module.modules.render;

import gemini.input.Keys;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.InfoText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Optic zoom. The zoom level is eased every frame (not every tick, which steps visibly at 20 Hz) and keeps easing
 * back out after the key is released, so the camera asks {@link #zooming()} rather than whether the module is on.
 * While zoomed the mouse wheel changes the zoom level and the mouse is slowed in proportion, so a small hand
 * movement stays a small movement on screen.
 */
public final class ZoomModule extends Module {
   /** How fast the zoom eases, per second: about the old 35 % of the gap per tick. */
   static final float EASE_RATE = 8.6F;
   static final float MIN_FACTOR = 1.5F;
   static final float MAX_FACTOR = 40.0F;
   private final NumberSetting factor = this.addSetting(new NumberSetting("Factor", "How far the view zooms in", 4.0, 1.5, 12.0, 0.5));
   private final BooleanSetting smooth = this.addSetting(new BooleanSetting("Smooth", "Ease in and out of the zoom", true));
   private final BooleanSetting scroll = this.addSetting(
      new BooleanSetting("Scroll To Zoom", "Use the mouse wheel to zoom further in or out while zoomed; it starts from Factor again each time", true)
   );
   private final BooleanSetting slowMouse = this.addSetting(
      new BooleanSetting("Slow Mouse", "Lower the mouse sensitivity in step with the zoom so aiming stays steady", true)
   );
   private final BooleanSetting cinematic = this.addSetting(
      new BooleanSetting("Cinematic", "Use the game's smooth camera while zoomed", false)
   );
   private final InfoText infoText = new InfoText();
   private float current = 1.0F;
   /** The zoom level being held: Factor, changed by the wheel until the key is let go. */
   private float held;
   private long lastFrame;
   private boolean changedCamera;
   private boolean previousSmoothCamera;

   public ZoomModule() {
      super("Zoom", "Zooms the view in while its key (Z by default) is held.", Category.RENDER, Keys.fromGlfw('Z'));
      this.holdByDefault();
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Cinematic Camera");
      return names;
   }

   @Override
   public void onEnable() {
      if (this.current <= 1.001F) {
         // Not easing out of an earlier zoom: the first frame must not count the time since the last one.
         this.lastFrame = 0L;
      }

      this.held = this.factor.floatValue();
      if (!this.smooth.get()) {
         this.current = this.held;
      }

      if (this.cinematic.get()) {
         Minecraft client = Minecraft.getInstance();
         this.previousSmoothCamera = client.options.smoothCamera;
         client.options.smoothCamera = true;
         this.changedCamera = true;
      }
   }

   @Override
   public void onDisable() {
      if (!this.smooth.get()) {
         this.current = 1.0F;
      }

      if (this.changedCamera) {
         Minecraft.getInstance().options.smoothCamera = this.previousSmoothCamera;
         this.changedCamera = false;
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
      float target = this.isEnabled() ? this.held : 1.0F;
      this.current = this.smooth.get() ? ease(this.current, target, dt) : target;
      return Math.max(1.0F, this.current);
   }

   /**
    * The mouse wheel moved by {@code amount} notches. Returns true when the zoom used it, so the hotbar does not
    * scroll as well.
    */
   public boolean onScroll(double amount) {
      if (!this.isEnabled() || !this.scroll.get() || amount == 0.0) {
         return false;
      }

      this.held = scrolled(this.held, amount);
      return true;
   }

   /** The zoom level after {@code notches} wheel steps: each step is 15 %, so it feels even at every level. */
   static float scrolled(float held, double notches) {
      float next = (float)(held * Math.pow(1.15, notches));
      return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, next));
   }

   /** What the mouse sensitivity is multiplied by this frame (1 = unchanged). */
   public double sensitivityScale() {
      return this.slowMouse.get() && this.current > 1.001F ? 1.0 / this.current : 1.0;
   }

   /** One frame of exponential easing from {@code current} toward {@code target}; snaps once it is within 0.001. */
   static float ease(float current, float target, float dt) {
      float next = current + (target - current) * (1.0F - (float)Math.exp(-EASE_RATE * Math.max(0.0F, dt)));
      return Math.abs(target - next) < 0.001F ? target : next;
   }

   @Override
   public String info() {
      return this.infoText.fixed(this.isEnabled() ? this.held : this.factor.get(), 1);
   }
}
