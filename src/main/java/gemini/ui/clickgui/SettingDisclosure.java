package gemini.ui.clickgui;

/**
 * The rule for how many of a module's settings the window menu shows before a "More settings" row: a module with
 * more than {@link #THRESHOLD} visible settings shows the first {@link #SHOWN} (modules declare their main settings
 * first), and the rest stay folded until the row is clicked. While a search is running everything is shown, so a
 * matched setting is never hidden. Pure arithmetic, so it is tested without a running game.
 */
public final class SettingDisclosure {
   /** Up to this many settings are always shown in full. */
   public static final int THRESHOLD = 8;
   /** How many settings a folded module shows. */
   public static final int SHOWN = 6;

   private SettingDisclosure() {
   }

   /** Whether a module with {@code total} visible settings gets a More row at all. */
   public static boolean foldable(int total) {
      return total > THRESHOLD;
   }

   /** How many settings to draw. */
   public static int shown(int total, boolean expanded, boolean searching) {
      return expanded || searching || !foldable(total) ? total : SHOWN;
   }

   /** How many settings are folded away (0 when none). */
   public static int hidden(int total, boolean expanded, boolean searching) {
      return total - shown(total, expanded, searching);
   }

   /** Whether the setting at {@code index} must unfold its module to be seen (e.g. the search jumped to it). */
   public static boolean needsUnfold(int index, int total) {
      return foldable(total) && index >= SHOWN;
   }
}
