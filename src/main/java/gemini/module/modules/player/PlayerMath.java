package gemini.module.modules.player;

/** Pure decision rules behind the player modules, kept free of game classes so they can be unit tested. */
public final class PlayerMath {
   public static final int ALERT_NONE = 0;
   public static final int ALERT_WARN = 1;
   public static final int ALERT_CRITICAL = 2;

   private PlayerMath() {
   }

   /**
    * How loudly to warn about an item with {@code left} of {@code max} uses: {@link #ALERT_CRITICAL} at or below
    * {@code criticalUses} (whatever the percentage, so short-lived items still get it), {@link #ALERT_WARN} below
    * {@code percent}, otherwise {@link #ALERT_NONE}. Items without durability ({@code max <= 0}) never alert.
    */
   public static int durabilityAlert(int left, int max, double percent, int criticalUses) {
      if (max <= 0) {
         return ALERT_NONE;
      } else if (left <= criticalUses) {
         return ALERT_CRITICAL;
      } else {
         return 100.0 * left / max < percent ? ALERT_WARN : ALERT_NONE;
      }
   }
}
