package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;

public final class ClickGuiModule extends Module {
   private final ModeSetting theme = this.addSetting(
      new ModeSetting(
         "Theme",
         "Accent palette (LiquidBounce uses Nextgen electric blue & cyan)",
         "LiquidBounce",
         "LiquidBounce",
         "Neon",
         "Twin",
         "Aurora",
         "Ocean",
         "Ember",
         "Violet",
         "Mono",
         "Custom"
      )
   );
   private final ColorSetting primary = this.addSetting(new ColorSetting("Primary", "Custom primary accent", 212).showIf(() -> this.theme.is("Custom")));
   private final ColorSetting secondary = this.addSetting(new ColorSetting("Secondary", "Custom secondary accent", 190).showIf(() -> this.theme.is("Custom")));
   private final BooleanSetting animations = this.addSetting(new BooleanSetting("Animations", "Open motion, pulses, smooth scrolling and expand easing", true));
   private final BooleanSetting glow = this.addSetting(new BooleanSetting("Glass Glow", "Render glassmorphic outer glow and gradient borders", true));
   private final BooleanSetting snapGrid = this.addSetting(new BooleanSetting("Grid Snap", "Snap panels and HUD widgets to an 8px alignment grid", true));
   private final BooleanSetting floatingTooltips = this.addSetting(
      new BooleanSetting("Slide Tooltips", "Slide out contextual tooltip cards next to hovered modules", true)
   );
   private final BooleanSetting dim = this.addSetting(new BooleanSetting("Dim", "Darken the world behind the menu", true));
   private final BooleanSetting particles = this.addSetting(new BooleanSetting("Particles", "Soft floating particles in the menu", true));
   private final BooleanSetting tooltips = this.addSetting(new BooleanSetting("Tooltips", "Describe the hovered module or setting in the footer", true));
   private final NumberSetting scale = this.addSetting(new NumberSetting("Scale", "Menu size multiplier", 1.0, 0.5, 1.5, 0.05));
   private final BooleanSetting autoFit = this.addSetting(new BooleanSetting("Auto Fit", "Shrink the menu so every panel fits on screen", true));
   private final NumberSetting opacity = this.addSetting(new NumberSetting("Opacity", "Panel glass background opacity", 0.9, 0.45, 1.0, 0.01));
   private final NumberSetting scrollSpeed = this.addSetting(new NumberSetting("Scroll Speed", "Rows moved per mouse-wheel notch", 2.0, 0.5, 6.0, 0.5));
   private final BooleanSetting rememberSearch = this.addSetting(new BooleanSetting("Keep Search", "Keep the search text when the menu is reopened", false));

   public ClickGuiModule() {
      super("ClickGUI", "Menu look and feel.", Category.CLIENT);
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

   @Override
   protected boolean hasBindOptions() {
      return false;
   }

   public boolean animations() {
      return this.animations.get();
   }

   public boolean glow() {
      return this.glow.get();
   }

   public boolean snapGrid() {
      return this.snapGrid.get();
   }

   public boolean floatingTooltips() {
      return this.floatingTooltips.get();
   }

   public boolean dim() {
      return this.dim.get();
   }

   public boolean particles() {
      return this.particles.get();
   }

   public boolean tooltips() {
      return this.tooltips.get();
   }

   public float scale() {
      return this.scale.floatValue();
   }

   public boolean autoFit() {
      return this.autoFit.get();
   }

   public float opacity() {
      return this.opacity.floatValue();
   }

   public float scrollSpeed() {
      return this.scrollSpeed.floatValue();
   }

   public boolean keepSearch() {
      return this.rememberSearch.get();
   }

   public int accentColor() {
      String var1 = this.theme.get();

      return switch (var1) {
         case "LiquidBounce" -> -16748289;
         case "Neon" -> -6467875;
         case "Ocean" -> -11683841;
         case "Ember" -> -30132;
         case "Violet" -> -4160260;
         case "Aurora" -> -12714014;
         case "Mono" -> -2695962;
         case "Custom" -> this.primary.argb();
         default -> -10696449;
      };
   }

   public int secondaryColor() {
      String var1 = this.theme.get();

      return switch (var1) {
         case "LiquidBounce" -> -16722689;
         case "Neon" -> -16718337;
         case "Ocean" -> -8614657;
         case "Ember" -> -11444;
         case "Violet" -> -38187;
         case "Aurora" -> -6422678;
         case "Mono" -> -7892061;
         case "Custom" -> this.secondary.argb();
         default -> -45096;
      };
   }
}
