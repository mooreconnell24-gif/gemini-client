package gemini.ui.font;

/**
 * Stroke weight of smooth text. The Minecraft font has one weight, so in that mode all three look the same and
 * hierarchy comes from size and brightness alone.
 */
public enum FontWeight {
   REGULAR("regular"),
   /** Slightly heavier: control labels, selected rows. */
   MEDIUM("medium"),
   /** Headings and the value that matters. */
   BOLD("bold");

   private final String id;

   FontWeight(String id) {
      this.id = id;
   }

   /** The name used in the font definition files. */
   public String id() {
      return this.id;
   }
}
