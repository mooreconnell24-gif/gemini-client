package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;

public final class HudModule extends Module {
   private final BooleanSetting watermark = this.addSetting(new BooleanSetting("Watermark", "Show Gemini branding", true));
   private final ModeSetting watermarkStyle = this.addSetting(
      new ModeSetting("Watermark Style", "Visual style of the top-left client watermark", "Nextgen Card", "Nextgen Card", "LiquidBounce Pill", "Minimal")
         .showIf(this.watermark::get)
   );
   private final BooleanSetting logo = this.addSetting(new BooleanSetting("Logo", "Show the Gemini logo next to the name", true).showIf(this.watermark::get));
   private final BooleanSetting fps = this.addSetting(new BooleanSetting("FPS", "Show the FPS counter", true).showIf(this.watermark::get));
   private final BooleanSetting ping = this.addSetting(new BooleanSetting("Ping", "Show latency when online", true).showIf(this.watermark::get));
   private final BooleanSetting speed = this.addSetting(new BooleanSetting("Speed", "Show your speed in blocks per second", true).showIf(this.watermark::get));
   private final BooleanSetting arraylist = this.addSetting(new BooleanSetting("ArrayList", "List enabled modules", true));
   private final ModeSetting listSide = this.addSetting(
      new ModeSetting("List Side", "Which side the list hugs", "Right", "Right", "Left").showIf(this.arraylist::get)
   );
   private final ModeSetting listAnchor = this.addSetting(
      new ModeSetting("List Anchor", "Top or bottom of the screen", "Top", "Top", "Bottom").showIf(this.arraylist::get)
   );
   private final ModeSetting sort = this.addSetting(
      new ModeSetting("Sort", "Order of the module list", "Length", "Length", "Alphabetical", "Category").showIf(this.arraylist::get)
   );
   private final ModeSetting colors = this.addSetting(
      new ModeSetting("Colors", "Module list coloring", "Pulsing Rainbow", "Pulsing Rainbow", "Neon Wave", "Gradient", "Rainbow", "Accent", "Category")
         .showIf(this.arraylist::get)
   );
   private final BooleanSetting background = this.addSetting(
      new BooleanSetting("Background", "Frosted glass box behind each entry", true).showIf(this.arraylist::get)
   );
   private final BooleanSetting sideBar = this.addSetting(
      new BooleanSetting("Side Bar", "Gradient accent bar on the screen-facing edge", true).showIf(this.arraylist::get)
   );
   private final BooleanSetting glowOutline = this.addSetting(
      new BooleanSetting("Glow Outline", "Draw a glowing contour outline around the ArrayList", true).showIf(this.arraylist::get)
   );
   private final BooleanSetting slideAnimations = this.addSetting(
      new BooleanSetting("Slide Animations", "Smoothly slide modules in and out when toggled", true).showIf(this.arraylist::get)
   );
   private final BooleanSetting shadow = this.addSetting(new BooleanSetting("Text Shadow", "Draw text with a shadow", true));
   private final BooleanSetting keys = this.addSetting(
      new BooleanSetting("Show Keys", "Show each module's keybind in the list", false).showIf(this.arraylist::get)
   );
   private final BooleanSetting info = this.addSetting(
      new BooleanSetting("Module Info", "Show a module's mode or status next to its name", true).showIf(this.arraylist::get)
   );
   private final ModeSetting tagStyle = this.addSetting(
      new ModeSetting("Tag Style", "Formatting style for module mode tags in ArrayList", "Plain", "Plain", "Brackets [Info]", "Dot · Info", "Angle <Info>")
         .showIf(this.arraylist::get)
   );
   private final BooleanSetting lowercase = this.addSetting(
      new BooleanSetting("Lowercase", "Render module names and tags in sleek lowercase", false).showIf(this.arraylist::get)
   );
   private final NumberSetting opacity = this.addSetting(
      new NumberSetting("Opacity", "List background opacity", 0.65, 0.1, 0.95, 0.05).showIf(this.arraylist::get)
   );
   private final NumberSetting scale = this.addSetting(new NumberSetting("Scale", "Overall HUD scale", 1.0, 0.6, 1.6, 0.05));

   public HudModule() {
      super("HUD", "In-game overlay style and layout.", Category.RENDER);
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

   public boolean arraylist() {
      return this.arraylist.get();
   }

   public boolean listRight() {
      return this.listSide.is("Right");
   }

   public boolean listBottom() {
      return this.listAnchor.is("Bottom");
   }

   public String sort() {
      return this.sort.get();
   }

   public String colors() {
      return this.colors.get();
   }

   public boolean background() {
      return this.background.get();
   }

   public boolean sideBar() {
      return this.sideBar.get();
   }

   public boolean glowOutline() {
      return this.glowOutline.get();
   }

   public boolean slideAnimations() {
      return this.slideAnimations.get();
   }

   public boolean shadow() {
      return this.shadow.get();
   }

   public boolean keys() {
      return this.keys.get();
   }

   public boolean showInfo() {
      return this.info.get();
   }

   public String tagStyle() {
      return this.tagStyle.get();
   }

   public boolean lowercase() {
      return this.lowercase.get();
   }

   public float opacity() {
      return this.opacity.floatValue();
   }

   public float scale() {
      return this.scale.floatValue();
   }
}
