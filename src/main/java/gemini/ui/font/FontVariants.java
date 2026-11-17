package gemini.ui.font;

/**
 * Which rasterisation of the smooth font to use. Every size and weight is defined once per pixel density (real
 * pixels per GUI unit, 1 to {@link #MAX_DENSITY}); picking the density that matches the screen keeps each glyph
 * bitmap 1:1 with screen pixels, which is what makes the text crisp at every GUI scale.
 *
 * <p>Pure arithmetic and names: no game classes, unit tested.
 */
public final class FontVariants {
   /** Highest pixel density a font definition exists for. */
   public static final int MAX_DENSITY = 6;
   public static final String NAMESPACE = "gemini";

   private FontVariants() {
   }

   /**
    * The pixel density for text drawn at {@code guiScale} under a pose that scales by {@code poseScale}, within the
    * range that has definitions. When the real pixels per unit are (nearly) a whole number that is the density: a
    * menu scaled 1.5x at GUI scale 2 uses the 3x bitmaps and stays sharp. In between, the next density up is used
    * and scaled down, which keeps more detail than scaling a smaller bitmap up.
    */
   public static int density(int guiScale, float poseScale) {
      return density(guiScale, poseScale, true);
   }

   /**
    * As {@link #density(int, float)}; {@code smoothSampling} says whether the glyph textures are sampled with linear
    * filtering. Without it, scaling down drops pixels, so the nearest density is used instead of the next one up.
    */
   public static int density(int guiScale, float poseScale, boolean smoothSampling) {
      float effective = Math.max(1, guiScale) * (Float.isNaN(poseScale) || poseScale <= 0.0F ? 1.0F : poseScale);
      int nearest = Math.max(1, Math.round(effective));
      int density = nearest;
      if (smoothSampling && Math.abs(nearest / effective - 1.0F) > SNAP_TOLERANCE) {
         density = (int)Math.ceil(effective);
      }

      return Math.max(1, Math.min(MAX_DENSITY, density));
   }

   /** Path of the font definition (under {@code assets/gemini/font/}, without {@code .json}). */
   public static String path(FontSize size, FontWeight weight, int density) {
      return "ui_" + size.id() + "_" + weight.id() + "_x" + Math.max(1, Math.min(MAX_DENSITY, density));
   }

   /** Index of a (size, weight, density) triple in a flat table of {@link #count()} entries. */
   public static int index(FontSize size, FontWeight weight, int density) {
      int d = Math.max(1, Math.min(MAX_DENSITY, density)) - 1;
      return (size.ordinal() * FontWeight.values().length + weight.ordinal()) * MAX_DENSITY + d;
   }

   public static int count() {
      return FontSize.values().length * FontWeight.values().length * MAX_DENSITY;
   }

   /**
    * Offset of a text shadow in GUI units: about half a GUI unit, snapped to whole screen pixels (1 px at scale 1
    * and 2, 2 px at 3 and 4), so the shadow is as sharp as the text.
    */
   public static float shadowOffset(int density) {
      int d = Math.max(1, density);
      return Math.max(1, Math.round(d * 0.5F)) / (float)d;
   }

   /** The shadow colour for text of {@code color}: black at a little over half the text's own alpha. */
   public static int shadowColor(int color) {
      int alpha = Math.round((color >>> 24) * 0.6F);
      return alpha << 24;
   }

   /** How far (as a fraction) snapped text may differ in size or width from what the layout asked for. */
   public static final float SNAP_TOLERANCE = 0.045F;

   /**
    * The extra scale that makes glyph bitmaps of {@code density} land 1:1 on screen when the pose gives
    * {@code pixelsPerUnit} real pixels per unit, or 0 when snapping is not worth it: the scale is already whole, or
    * the snapped text would differ from the layout by more than {@link #SNAP_TOLERANCE} in size, or in width
    * ({@code ownWidth} is the width of that rasterisation, {@code measuredWidth} the width layouts were given).
    */
   public static float snapScale(float pixelsPerUnit, int density, int ownWidth, int measuredWidth) {
      if (!(pixelsPerUnit > 0.0F) || density < 1) {
         return 0.0F;
      }

      float scale = density / pixelsPerUnit;
      if (Math.abs(scale - 1.0F) > SNAP_TOLERANCE) {
         return 0.0F;
      }

      if (measuredWidth > 0 && Math.abs(ownWidth * scale / measuredWidth - 1.0F) > SNAP_TOLERANCE) {
         return 0.0F;
      }

      return scale;
   }

   /** Width of a string measured at scale 1 once it is drawn {@code scale} times larger, rounded up. */
   public static int scaledWidth(int width, float scale) {
      return scale == 1.0F ? width : (int)Math.ceil(width * scale - 1.0E-4F);
   }

   /** The y at which text of {@code capHeight} sits vertically centred in a box from {@code top} that is {@code height} tall. */
   public static int centerY(int top, int height, int capHeight) {
      return top + Math.floorDiv(height - capHeight, 2);
   }
}
