package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudOverlay;
import gemini.ui.Theme;

public final class ClickGuiModule extends Module {
   private static final int[] SURFACE_OLED = {-16579836, -16316663, -15856110, -15263971};
   private static final int[] SURFACE_SLATE = {-15657182, -15064784, -14537923, -13879475};
   private static final int[] SURFACE_GRAPHITE = {-15856114, -15395563, -14869219, -14211289};
   private static final int[] SURFACE_PLUM = {-15726057, -15331553, -14739920, -13951166};
   private static final int[] SURFACE_DARK = {-15987178, -15723481, -15196629, -14604492};
   private final ModeSetting theme = this.addSetting(themeSetting());
   private final ModeSetting font = this.addSetting(
      new ModeSetting(
         "Font",
         "Smooth draws Gemini's menus and HUD in a clean sans-serif; Minecraft uses the game's pixel font",
         "Smooth",
         "Smooth",
         "Minecraft"
      )
   );
   private final ModeSetting surface = this.addSetting(
      new ModeSetting(
         "Surface",
         "Panel colour of the Custom theme: dark navy, pure black (OLED), blue slate, neutral graphite or plum",
         "Dark",
         "Dark",
         "OLED",
         "Slate",
         "Graphite",
         "Plum"
      ).showIf(() -> this.theme.is("Custom"))
   );
   private final ModeSetting layout = this.addSetting(
      new ModeSetting("Layout", "Window is one centred window with a sidebar; Panels is one draggable column per category", "Window", "Window", "Panels")
   );
   private final ColorSetting primary = this.addSetting(new ColorSetting("Primary", "Custom primary accent colour", 212).showIf(() -> this.theme.is("Custom")));
   private final ColorSetting secondary = this.addSetting(new ColorSetting("Secondary", "Custom secondary accent colour", 190).showIf(() -> this.theme.is("Custom")));
   private final BooleanSetting categoryTint = this.addSetting(
      new BooleanSetting("Category Tint", "Blend the accent towards the colour of the category you are looking at", false)
   );
   private final BooleanSetting animations = this.addSetting(new BooleanSetting("Animations", "Animate opening, pulses, scrolling and dropdowns", true));
   private final BooleanSetting glow = this.addSetting(new BooleanSetting("Glass Glow", "Faint glow around the hovered panel in the panel menu", false));
   private final BooleanSetting snapGrid = this.addSetting(new BooleanSetting("Grid Snap", "Snap panels and HUD widgets to an 8-pixel grid", true));
   private final BooleanSetting floatingTooltips = this.addSetting(
      new BooleanSetting("Slide Tooltips", "Show a tooltip card next to the module under the mouse", true)
   );
   private final BooleanSetting dim = this.addSetting(new BooleanSetting("Dim", "Darken the world behind the menu", true));
   private final BooleanSetting blur = this.addSetting(new BooleanSetting("Blur", "Blur the world behind the menu (uses the vanilla menu blur)", true));
   private final BooleanSetting particles = this.addSetting(new BooleanSetting("Particles", "Draw small drifting dots behind the panel menu", false));
   private final BooleanSetting tooltips = this.addSetting(new BooleanSetting("Tooltips", "Describe the module or setting under the mouse in the footer", true));
   private final NumberSetting scale = this.addSetting(new NumberSetting("Scale", "Menu size multiplier", 1.0, 0.5, 1.5, 0.05));
   private final BooleanSetting autoFit = this.addSetting(new BooleanSetting("Auto Fit", "Shrink the menu so every panel fits on screen", true));
   private final NumberSetting opacity = this.addSetting(new NumberSetting("Opacity", "Opacity of the panel background", 0.9, 0.45, 1.0, 0.01));
   private final NumberSetting scrollSpeed = this.addSetting(new NumberSetting("Scroll Speed", "Rows moved per mouse-wheel notch", 2.0, 0.5, 6.0, 0.5));
   private final BooleanSetting rememberSearch = this.addSetting(new BooleanSetting("Keep Search", "Keep the search text when the menu is reopened", false));
   private final ListSetting favorites = this.addSetting(
      new ListSetting("Favorites", "Starred modules; the star on a module card or the ★ filter shows them", ListSetting.Kind.TEXT)
   );

   public ClickGuiModule() {
      super("ClickGUI", "Sets the look of the menus and HUD: theme, font, layout, motion, scale and tooltips.", Category.CLIENT);
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

   /** The Theme setting: the names in {@link Theme#themeNames()}, with the retired names still accepted from old configs. */
   private static ModeSetting themeSetting() {
      ModeSetting setting = new ModeSetting(
         "Theme",
         "Colours of the menus and HUD: accents, panels and text. Custom uses the Primary, Secondary and Surface settings below",
         Theme.DEFAULT,
         Theme.themeNames()
      );
      for (String retired : Theme.retiredThemeNames()) {
         setting.alias(retired, Theme.canonicalTheme(retired));
      }

      return setting;
   }

   /** Whether Gemini's own text is drawn in the smooth font rather than the Minecraft font. */
   public boolean smoothFont() {
      return this.font.is("Smooth");
   }

   /** The Custom theme's surface choice (part of what {@link Theme} watches for changes). */
   public String surfaceName() {
      return this.surface.get();
   }

   /** The Custom theme's primary accent (live: follows the colour setting's rainbow or pulse). */
   public int customPrimary() {
      return this.primary.argb();
   }

   public int customSecondary() {
      return this.secondary.argb();
   }

   /** The Custom theme's panel colour for an elevation level 0..3. */
   public int customSurface(int level) {
      int[] shades = switch (this.surface.get()) {
         case "OLED" -> SURFACE_OLED;
         case "Slate" -> SURFACE_SLATE;
         case "Graphite" -> SURFACE_GRAPHITE;
         case "Plum" -> SURFACE_PLUM;
         default -> SURFACE_DARK;
      };
      return shades[Math.max(0, Math.min(3, level))];
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

   /** The theme's primary accent (crossfading while the theme changes). */
   public int accentColor() {
      return Theme.current().accent;
   }

   public int secondaryColor() {
      return Theme.current().accent2;
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

   /** The theme's panel colour for an elevation level: 0 is the backdrop, 3 the most raised control. */
   public int surface(int level) {
      return Theme.current().surface(level);
   }
}
