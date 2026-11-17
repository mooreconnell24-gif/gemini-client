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
            "Card: logo, name and stats in a small card; Compact: one line in a slim card; Text: plain text, no card",
            "Card",
            "Card",
            "Compact",
            "Text"
         )
         .showIf(this.watermark::get)
   );
   private final NumberSetting watermarkSize = this.addSetting(
      new NumberSetting("Watermark Size", "Size of the watermark, on top of the HUD scale", 1.0, 0.4, 1.5, 0.05).showIf(this.watermark::get)
   );
   private final BooleanSetting logo = this.addSetting(new BooleanSetting("Logo", "Show the Gemini logo next to the name", true).showIf(this.watermark::get));
   private final BooleanSetting fps = this.addSetting(new BooleanSetting("FPS", "Show frames per second", true).showIf(this.watermark::get));
   private final BooleanSetting ping = this.addSetting(new BooleanSetting("Ping", "Show your latency; hidden in single player, where it is always zero", true).showIf(this.watermark::get));
   private final BooleanSetting speed = this.addSetting(new BooleanSetting("Speed", "Show your speed in blocks per second", false).showIf(this.watermark::get));
   private final ModeSetting backdrop = this.addSetting(
      new ModeSetting(
         "Backdrop",
         "Panel behind every HUD widget: Soft is a light tint, Solid a dark card, None shows only the text (always with a shadow)",
         "Soft",
         gemini.ui.HudStyle.BACKDROPS
      )
   );
   private final BooleanSetting shadow = this.addSetting(new BooleanSetting("Text Shadow", "Draw text with a shadow", true));
   private final BooleanSetting toasts = this.addSetting(
      new BooleanSetting("Toasts", "Show notifications for module toggles, warnings and finished tasks", true)
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

   public boolean watermark() {
      return this.watermark.get();
   }

   public String watermarkStyle() {
      return this.watermarkStyle.get();
   }

   public float watermarkSize() {
      return this.watermarkSize.floatValue();
   }

   public boolean logo() {
      return this.logo.get();
   }

   public boolean fps() {
      return this.fps.get();
   }

   public boolean ping() {
      return this.ping.get();
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
