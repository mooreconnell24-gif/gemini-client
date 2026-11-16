package gemini.module.modules.player;

/**
 * The decision loop behind Craft Repeat, kept free of game types so it can be tested.
 *
 * <p>It starts {@linkplain #arm armed} once you place a recipe with the recipe book. Whenever the result slot is
 * empty and nothing is on the cursor it asks for the recipe to be placed again; when a result shows up it either
 * takes it ({@code autoTake}) or waits for you to take it. A placement that produces no result within
 * {@link #PLACE_TIMEOUT} ticks means the ingredients ran out, and the loop stops.
 */
public final class CraftCycle {
   /** Ticks to wait for the server to fill the grid after a placement before calling the ingredients gone. */
   public static final int PLACE_TIMEOUT = 10;

   public enum Action {
      /** Do nothing this tick. */
      WAIT,
      /** Place the remembered recipe into the grid again. */
      PLACE,
      /** Shift-click the result slot. */
      TAKE,
      /** Placing produced nothing: out of ingredients. The cycle is disarmed. */
      OUT,
      /** The craft limit was reached. The cycle is disarmed. */
      DONE,
      /** Taking the result did nothing several times (no room in the inventory). The cycle is disarmed. */
      FULL
   }

   /** Unanswered result clicks in a row before the inventory is taken to be full. */
   public static final int MAX_TAKES = 3;

   private enum State {
      IDLE,
      ARMED,
      PLACED,
      READY
   }

   private State state = State.IDLE;
   private int waited;
   private int cooldown;
   private int crafts;
   private int takes;

   /** A recipe was placed by hand: start repeating it. */
   public void arm() {
      // The hand placement is on its way to the server: wait for its result like for one of our own.
      this.state = State.PLACED;
      this.waited = 0;
      this.cooldown = 0;
      this.crafts = 0;
      this.takes = 0;
   }

   public void disarm() {
      this.state = State.IDLE;
      this.waited = 0;
      this.cooldown = 0;
   }

   public boolean armed() {
      return this.state != State.IDLE;
   }

   /** Results taken since {@link #arm}, by you or by the cycle. */
   public int crafts() {
      return this.crafts;
   }

   /**
    * One tick. {@code limit} 0 means no limit; {@code delay} is the number of ticks to leave between the cycle's
    * own clicks.
    */
   public Action step(boolean resultEmpty, boolean cursorEmpty, boolean autoTake, int limit, int delay) {
      if (this.cooldown > 0) {
         this.cooldown--;
      }

      switch (this.state) {
         case IDLE:
            return Action.WAIT;
         case ARMED:
            if (!resultEmpty) {
               this.state = State.READY;
               return this.step(resultEmpty, cursorEmpty, autoTake, limit, delay);
            }

            if (limit > 0 && this.crafts >= limit) {
               this.disarm();
               return Action.DONE;
            }

            if (!cursorEmpty || this.cooldown > 0) {
               return Action.WAIT;
            }

            this.state = State.PLACED;
            this.waited = 0;
            this.cooldown = delay;
            return Action.PLACE;
         case PLACED:
            if (!resultEmpty) {
               this.state = State.READY;
               return Action.WAIT;
            }

            if (++this.waited > PLACE_TIMEOUT) {
               this.disarm();
               return Action.OUT;
            }

            return Action.WAIT;
         case READY:
         default:
            if (resultEmpty) {
               // Taken by hand (or by our click last tick): count it and go round again.
               this.crafts++;
               this.takes = 0;
               this.state = State.ARMED;
               return Action.WAIT;
            }

            if (!autoTake || !cursorEmpty || this.cooldown > 0) {
               return Action.WAIT;
            }

            if (limit > 0 && this.crafts >= limit) {
               this.disarm();
               return Action.DONE;
            }

            if (this.takes >= MAX_TAKES) {
               this.disarm();
               return Action.FULL;
            }

            this.takes++;
            this.cooldown = delay;
            return Action.TAKE;
      }
   }
}
