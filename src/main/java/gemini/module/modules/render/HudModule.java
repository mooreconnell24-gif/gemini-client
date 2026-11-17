package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;

public final class HudModule extends Module {
   private final BooleanSetting watermark = this.addSetting(new BooleanSetting("Watermark", "Show the Gemini name in the top-left corner", true));
   private final ModeSetting watermarkStyle = this.addSetting(
      new ModeSetting(
            "Watermark Style",
            "Simple: the name and version as plain text; Bar: a small card with the name and a few stats; Logo: the Gemini logo alone",
            "Simple",
            "Simple",
            "Bar",
            "Logo"
         )
         .showIf(this.watermark::get)
   );
   private final NumberSetting watermarkSize = this.addSetting(
      new NumberSetting("Watermark Size", "Size of the watermark, on top of the HUD scale", 1.0, 0.4, 1.5, 0.05).showIf(this.watermark::get)
   );
   private final BooleanSetting fps = this.addSetting(new BooleanSetting("FPS", "Show frames per second in the watermark bar", true).showIf(this::barShown));
   private final BooleanSetting time = this.addSetting(new BooleanSetting("Time", "Show the time of day on your computer in the watermark bar", true).showIf(this::barShown));
   private final BooleanSetting world = this.addSetting(new BooleanSetting("World", "Show the name of the world in the watermark bar", true).showIf(this::barShown));
   private final BooleanSetting speed = this.addSetting(new BooleanSetting("Speed", "Show your speed in blocks per second in the watermark bar", false).showIf(this::barShown));
   private final ModeSetting backdrop = this.addSetting(
      new ModeSetting(
         "Backdrop",
         "Panel behind every HUD widget: Soft is a light tint, Solid a dark card with a shadow, None shows only the text (always with a shadow)",
         "Soft",
         gemini.ui.HudStyle.BACKDROPS
      )
   );
   private final BooleanSetting shadow = this.addSetting(new BooleanSetting("Text Shadow", "Draw text with a shadow", true));
   private final BooleanSetting toasts = this.addSetting(
      new BooleanSetting("Toasts", "Show notification cards for warnings, finished tasks and module toggles", true)
   );
   private final BooleanSetting toggleToasts = this.addSetting(
      new BooleanSetting("Module Toggles", "Show a short notification when a module is switched on or off", true).showIf(this.toasts::get)
   );
   private final ModeSetting toastPosition = this.addSetting(
      new ModeSetting("Toast Position", "Screen corner the notifications stack in", "Bottom Right", "Bottom Right", "Top Right", "Bottom Left", "Top Left")
         .showIf(this.toasts::get)
   );
   private final NumberSetting scale = this.addSetting(new NumberSetting("Scale", "Overall HUD scale", 1.0, 0.6, 1.6, 0.05));

   public HudModule() {
      super("HUD", "Shows the watermark and notifications and sets the overall HUD scale; the module list is the ArrayList widget.", Category.RENDER);
      this.loadState(true, this.getKeybind());
   }

   @Override
   protected boolean settingsOnly() {
      return true;
   }

   @Override
   public boolean showInArrayList() {
      return false;
   }

   private boolean barShown() {
      return this.watermark.get() && this.watermarkStyle.is("Bar");
   }

   public boolean watermark() {
      return this.watermark.get();
   }

   /** "Simple", "Bar" or "Logo". */
   public String watermarkStyle() {
      return this.watermarkStyle.get();
   }

   public float watermarkSize() {
      return this.watermarkSize.floatValue();
   }

   public boolean fps() {
      return this.fps.get();
   }

   public boolean time() {
      return this.time.get();
   }

   public boolean world() {
      return this.world.get();
   }

   public boolean speed() {
      return this.speed.get();
   }

   /** The HUD-wide backdrop as a {@link gemini.ui.HudStyle} constant. */
   public int backdrop() {
      return gemini.ui.HudStyle.backdrop(this.backdrop.get());
   }

   public boolean shadow() {
      return this.shadow.get();
   }

   public boolean toasts() {
      return this.toasts.get();
   }

   /** Whether switching a module on or off shows a notification. */
   public boolean toggleToasts() {
      return this.toggleToasts.get();
   }

   /** True when notifications stack against the right screen edge. */
   public boolean toastsRight() {
      return this.toastPosition.get().endsWith("Right");
   }

   /** True when notifications stack upward from the bottom of the screen. */
   public boolean toastsBottom() {
      return this.toastPosition.get().startsWith("Bottom");
   }

   public float scale() {
      return this.scale.floatValue();
   }
}
