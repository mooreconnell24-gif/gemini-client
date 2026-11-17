package gemini.module.modules.hud;

/**
 * Number formats shared by the HUD widgets, so every widget writes the same kind of number the same way: counts
 * with thousands separators, short durations in seconds with one decimal, longer ones as a clock. All of them are
 * locale-independent and pure (unit tested); callers format once per tick or refresh, never per frame.
 */
public final class HudFormat {
   private HudFormat() {
   }

   /** A whole number with comma thousands separators: 1234567 is "1,234,567". */
   public static String grouped(long value) {
      if (value > -1000L && value < 1000L) {
         return Long.toString(value);
      }

      String digits = Long.toString(Math.abs(value));
      StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3 + 1);
      if (value < 0L) {
         out.append('-');
      }

      int lead = digits.length() % 3;
      if (lead == 0) {
         lead = 3;
      }

      out.append(digits, 0, lead);
      for (int i = lead; i < digits.length(); i += 3) {
         out.append(',').append(digits, i, i + 3);
      }

      return out.toString();
   }

   /**
    * A remaining time for a cooldown: tenths of a second under ten seconds ("3.4"), whole seconds under a minute
    * ("42"), then "m:ss". The unit ("s") is drawn separately, faint, and is left out for the clock form.
    */
   public static String shortTime(double seconds) {
      double s = Math.max(0.0, seconds);
      if (s < 9.95) {
         long tenths = Math.round(s * 10.0);
         return tenths / 10 + "." + tenths % 10;
      }

      if (s < 59.5) {
         return Long.toString(Math.round(s));
      }

      return clock(Math.round(s));
   }

   /** Whether {@link #shortTime} writes a plain number of seconds (so an "s" unit belongs after it). */
   public static boolean shortTimeInSeconds(double seconds) {
      return Math.max(0.0, seconds) < 59.5;
   }

   /** Whole seconds as "m:ss", or "h:mm:ss" from an hour on. */
   public static String clock(long totalSeconds) {
      long s = Math.max(0L, totalSeconds);
      long hours = s / 3600L;
      long minutes = s / 60L % 60L;
      long rest = s % 60L;
      StringBuilder out = new StringBuilder(8);
      if (hours > 0L) {
         out.append(hours).append(':');
         if (minutes < 10L) {
            out.append('0');
         }
      }

      out.append(minutes).append(':');
      if (rest < 10L) {
         out.append('0');
      }

      return out.append(rest).toString();
   }

   /**
    * How much of an effect or cooldown is left, 0..1, given what is left now and the most that was ever left since
    * it started ({@code longest}). Unknown or infinite lengths count as full.
    */
   public static float remaining(int left, int longest) {
      if (left < 0 || longest <= 0) {
         return 1.0F;
      }

      return Math.min(1.0F, (float)left / longest);
   }
}
