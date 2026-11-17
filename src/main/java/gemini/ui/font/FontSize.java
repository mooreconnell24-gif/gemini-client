package gemini.ui.font;

/**
 * The five text sizes of the Gemini UI. Each has a smooth form (the bundled sans-serif at {@link #points} GUI units)
 * and a Minecraft-font form (the pixel font scaled by {@link #pixelScale}); the two are close enough in height and
 * width that one layout serves both.
 *
 * <p>Text is positioned by the top of its capital letters: a string drawn at {@code y} has its capitals between
 * {@code y} and {@code y + capHeight}, in both font modes.
 */
public enum FontSize {
   /** Captions, units, key hints. */
   SMALL("small", 8.0F, 1.0F, 6, 7, 9),
   /** Everything by default: labels, rows, values. */
   BODY("body", 9.5F, 1.0F, 7, 7, 11),
   /** Section and card headings. */
   LARGE("large", 13.0F, 1.5F, 9, 11, 16),
   /** Screen titles. */
   TITLE("title", 18.0F, 2.0F, 13, 14, 22),
   /** The one large word on a screen (the start screen name, a big number). */
   HERO("hero", 27.0F, 3.0F, 20, 21, 32);

   private final String id;
   private final float points;
   private final float pixelScale;
   private final int smoothCap;
   private final int pixelCap;
   private final int lineHeight;

   FontSize(String id, float points, float pixelScale, int smoothCap, int pixelCap, int lineHeight) {
      this.id = id;
      this.points = points;
      this.pixelScale = pixelScale;
      this.smoothCap = smoothCap;
      this.pixelCap = pixelCap;
      this.lineHeight = lineHeight;
   }

   /** The name used in the font definition files ({@code ui_<id>_<weight>_x<n>}). */
   public String id() {
      return this.id;
   }

   /** Em size of the smooth font in GUI units. */
   public float points() {
      return this.points;
   }

   /** How much the Minecraft font is scaled up for this size. */
   public float pixelScale() {
      return this.pixelScale;
   }

   /** Height of a capital letter in GUI units. */
   public int capHeight(boolean smooth) {
      return smooth ? this.smoothCap : this.pixelCap;
   }

   /** Distance between the tops of two consecutive lines of this size. */
   public int lineHeight() {
      return this.lineHeight;
   }
}
