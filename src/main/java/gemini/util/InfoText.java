package gemini.util;

import java.util.Locale;

/**
 * A formatted number that is only formatted again when the number changes.
 *
 * <p>{@code Module.info()} is asked for every listed module on every frame, and labels drawn in the world are
 * rebuilt every frame too. A {@code String.format} there costs a formatter, a builder and a string per call, and
 * without a locale it prints "0,55" on systems that use a decimal comma. One of these per label keeps the last
 * text and hands it back until the value moves, and always formats with {@link Locale#ROOT}.
 */
public final class InfoText {
   private static final String[] PATTERNS = {"%.0f", "%.1f", "%.2f", "%.3f"};
   private double lastValue = Double.NaN;
   private int lastDecimals = -1;
   private String lastSuffix;
   private String text = "";

   /** The value with a fixed number of decimals (0 to 3). */
   public String fixed(double value, int decimals) {
      return this.fixed(value, decimals, "");
   }

   /** The value with a fixed number of decimals (0 to 3) followed by {@code suffix}. */
   public String fixed(double value, int decimals, String suffix) {
      int places = Math.max(0, Math.min(PATTERNS.length - 1, decimals));
      // The identity check on the suffix is deliberate: callers pass literals, and a miss only costs a reformat.
      if (value != this.lastValue || places != this.lastDecimals || suffix != this.lastSuffix) {
         this.lastValue = value;
         this.lastDecimals = places;
         this.lastSuffix = suffix;
         this.text = format(value, places) + (suffix == null ? "" : suffix);
      }

      return this.text;
   }

   /** A whole number followed by {@code suffix}. */
   public String whole(long value, String suffix) {
      return this.fixed(value, 0, suffix);
   }

   /** Seconds as "m:ss". */
   public String clock(int totalSeconds) {
      int seconds = Math.max(0, totalSeconds);
      if (seconds != this.lastValue || this.lastDecimals != -2) {
         this.lastValue = seconds;
         this.lastDecimals = -2;
         this.lastSuffix = null;
         int rest = seconds % 60;
         this.text = seconds / 60 + (rest < 10 ? ":0" : ":") + rest;
      }

      return this.text;
   }

   /** {@code value} with {@code decimals} places, independent of the system locale. NaN and infinities print as "-". */
   public static String format(double value, int decimals) {
      if (Double.isNaN(value) || Double.isInfinite(value)) {
         return "-";
      }

      return String.format(Locale.ROOT, PATTERNS[Math.max(0, Math.min(PATTERNS.length - 1, decimals))], value);
   }
}
