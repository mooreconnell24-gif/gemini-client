package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudOverlay;

public final class ClickGuiModule extends Module {
   private final ModeSetting theme = this.addSetting(
      new ModeSetting(
         "Theme",
         "Accent palette",
         "LiquidBounce",
         "LiquidBounce",
         "Neon",
         "Twin",
         "Aurora",
         "Ocean",
         "Ember",
         "Violet",
         "Mono",
         "Midnight",
         "Sunset",
         "Mint",
         "Rose",
         "Gold",
         "Nord",
         "Dracula",
         "Sakura",
         "Custom"
      )
   );
   private final ModeSetting surface = this.addSetting(
      new ModeSetting(
         "Surface",
         "Base colour of the menu panels: Dark navy, pure black OLED, blue Slate, neutral Graphite or Plum",
         "Dark",
         "Dark",
         "OLED",
         "Slate",
         "Graphite",
         "Plum"
      )
   );
   private final ModeSetting layout = this.addSetting(
      new ModeSetting("Layout", "Window is one centred window with a sidebar; Panels is the classic draggable column per category", "Window", "Window", "Panels")
   );
   private final ColorSetting primary = this.addSetting(new ColorSetting("Primary", "Custom primary accent", 212).showIf(() -> this.theme.is("Custom")));
   private final ColorSetting secondary = this.addSetting(new ColorSetting("Secondary", "Custom secondary accent", 190).showIf(() -> this.theme.is("Custom")));
   private final BooleanSetting categoryTint = this.addSetting(
      new BooleanSetting("Category Tint", "Blend the accent toward the colour of the category you are looking at", false)
   );
   private final BooleanSetting animations = this.addSetting(new BooleanSetting("Animations", "Smooth open motion, pulses, scrolling, and dropdowns", true));
   private final BooleanSetting glow = this.addSetting(new BooleanSetting("Glass Glow", "Render glassmorphic outer glow and gradient borders", true));
   private final BooleanSetting snapGrid = this.addSetting(new BooleanSetting("Grid Snap", "Snap panels and HUD widgets to an 8px alignment grid", true));
   private final BooleanSetting floatingTooltips = this.addSetting(
      new BooleanSetting("Slide Tooltips", "Slide out tooltip cards next to hovered modules", true)
   );
   private final BooleanSetting dim = this.addSetting(new BooleanSetting("Dim", "Darken the world behind the menu", true));
   private final BooleanSetting blur = this.addSetting(new BooleanSetting("Blur", "Blur the world behind the menu (uses the vanilla menu blur)", true));
   private final BooleanSetting particles = this.addSetting(new BooleanSetting("Particles", "Soft floating particles in the menu", true));
   private final BooleanSetting tooltips = this.addSetting(new BooleanSetting("Tooltips", "Describe the hovered module or setting in the footer", true));
   private final NumberSetting scale = this.addSetting(new NumberSetting("Scale", "Menu size multiplier", 1.0, 0.5, 1.5, 0.05));
   private final BooleanSetting autoFit = this.addSetting(new BooleanSetting("Auto Fit", "Shrink the menu so every panel fits on screen", true));
   private final NumberSetting opacity = this.addSetting(new NumberSetting("Opacity", "Panel glass background opacity", 0.9, 0.45, 1.0, 0.01));
   private final NumberSetting scrollSpeed = this.addSetting(new NumberSetting("Scroll Speed", "Rows moved per mouse-wheel notch", 2.0, 0.5, 6.0, 0.5));
   private final BooleanSetting rememberSearch = this.addSetting(new BooleanSetting("Keep Search", "Keep the search text when the menu is reopened", false));
   private final ListSetting favorites = this.addSetting(
      new ListSetting("Favorites", "Starred modules; the star on a module card or the ★ filter show them", ListSetting.Kind.TEXT)
   );

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

   public boolean panelLayout() {
      return this.layout.is("Panels");
   }

   public void setPanelLayout(boolean panels) {
      this.layout.set(panels ? "Panels" : "Window");
      this.save();
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

   public boolean blur() {
      return this.blur.get();
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

   public String themeName() {
      return this.theme.get();
   }

   public boolean isFavorite(Module module) {
      return module != null && this.favorites.contains(module.getName());
   }

   /** Stars or unstars a module; returns true when it is now a favourite. */
   public boolean toggleFavorite(Module module) {
      boolean starred = this.favorites.toggle(module.getName());
      this.save();
      return starred;
   }

   public int favoriteCount() {
      return this.favorites.size();
   }

   public int accentColor() {
      return switch (this.theme.get()) {
         case "LiquidBounce" -> -16748289;
         case "Neon" -> -6467875;
         case "Ocean" -> -11683841;
         case "Ember" -> -30132;
         case "Violet" -> -4160260;
         case "Aurora" -> -12714014;
         case "Mono" -> -2695962;
         case "Midnight" -> -10781446;
         case "Sunset" -> -34243;
         case "Mint" -> -12714064;
         case "Rose" -> -41846;
         case "Gold" -> -15043;
         case "Nord" -> -7814960;
         case "Dracula" -> -4353031;
         case "Sakura" -> -18491;
         case "Custom" -> this.primary.argb();
         default -> -10696449;
      };
   }

   public int secondaryColor() {
      return switch (this.theme.get()) {
         case "LiquidBounce" -> -16722689;
         case "Neon" -> -16718337;
         case "Ocean" -> -8614657;
         case "Ember" -> -11444;
         case "Violet" -> -38187;
         case "Aurora" -> -6422678;
         case "Mono" -> -7892061;
         case "Midnight" -> -6595329;
         case "Sunset" -> -49793;
         case "Mint" -> -13972993;
         case "Rose" -> -28751;
         case "Gold" -> -30147;
         case "Nord" -> -8281663;
         case "Dracula" -> -34362;
         case "Sakura" -> -36957;
         case "Custom" -> this.secondary.argb();
         default -> -45096;
      };
   }

   /** Primary accent, optionally blended toward the colour of the category being viewed. */
   public int accentFor(Category category) {
      int accent = this.accentColor();
      return this.categoryTint.get() && category != null ? HudOverlay.lerp(accent, category.accent(), 0.4F) : accent;
   }

   public int secondaryFor(Category category) {
      int accent = this.secondaryColor();
      return this.categoryTint.get() && category != null ? HudOverlay.lerp(accent, category.accent(), 0.25F) : accent;
   }

   /** Panel colour for an elevation level: 0 is the backdrop, 3 the most raised control. */
   public int surface(int level) {
      int[] shades = switch (this.surface.get()) {
         case "OLED" -> new int[]{-16579836, -16316663, -15856110, -15263971};
         case "Slate" -> new int[]{-15657182, -15064784, -14537923, -13879475};
         case "Graphite" -> new int[]{-15856114, -15395563, -14869219, -14211289};
         case "Plum" -> new int[]{-15726057, -15331553, -14739920, -13951166};
         default -> new int[]{-15987178, -15723481, -15196629, -14604492};
      };
      return shades[Math.max(0, Math.min(3, level))];
   }
}
