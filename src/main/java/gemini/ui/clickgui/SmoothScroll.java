package gemini.ui.clickgui;

import gemini.ui.UiMath;

/**
 * A scroll position with inertia: a wheel notch gives the list a push that decays exponentially, so it glides to a
 * stop instead of jumping, the same at every frame rate. With animations off the whole distance is applied at once.
 * Pure arithmetic (no game classes), so it is unit tested.
 */
public final class SmoothScroll {
   /** Decay rate of the glide; a push has settled after roughly a third of a second. */
   public static final float FRICTION = 11.0F;
   private float position;
   private float velocity;

   /** The current offset in pixels, 0 at the top. */
   public float value() {
      return this.position;
   }

   /** The offset rounded to whole pixels, for drawing. */
   public int pixels() {
      return Math.round(this.position);
   }

   /** Where the current glide will come to rest (before clamping). */
   public float target() {
      return this.position + this.velocity / FRICTION;
   }

   public boolean moving() {
      return this.velocity != 0.0F;
   }

   /** Pushes the list so that it travels {@code distance} more pixels (negative = up) before it stops. */
   public void scrollBy(float distance) {
      this.velocity += distance * FRICTION;
   }

   /** Glides to {@code target}, replacing any glide in progress. */
   public void glideTo(float target) {
      this.velocity = (target - this.position) * FRICTION;
   }

   /** Jumps to {@code target} and stops. */
   public void jumpTo(float target) {
      this.position = target;
      this.velocity = 0.0F;
   }

   /**
    * Glides the least distance that brings the span {@code top..bottom} (content coordinates) inside a viewport
    * {@code view} tall; a span taller than the viewport is aligned to its top.
    */
   public void reveal(float top, float bottom, float view) {
      float rest = this.target();
      if (top < rest || bottom - top > view) {
         this.glideTo(top);
      } else if (bottom > rest + view) {
         this.glideTo(bottom - view);
      }
   }

   /**
    * Advances the glide by {@code dt} seconds and keeps the position inside {@code 0..max}; hitting an end stops the
    * glide. Returns the new position.
    */
   public float update(float dt, float max, boolean animate) {
      float limit = Math.max(0.0F, max);
      if (this.velocity != 0.0F) {
         if (animate) {
            this.position = UiMath.glidePosition(this.position, this.velocity, FRICTION, dt);
            this.velocity = UiMath.glideVelocity(this.velocity, FRICTION, dt);
         } else {
            this.position += this.velocity / FRICTION;
            this.velocity = 0.0F;
         }
      }

      if (this.position <= 0.0F && this.velocity <= 0.0F) {
         this.position = 0.0F;
         this.velocity = 0.0F;
      } else if (this.position >= limit && this.velocity >= 0.0F) {
         this.position = limit;
         this.velocity = 0.0F;
      } else {
         this.position = Math.max(0.0F, Math.min(limit, this.position));
      }

      return this.position;
   }
}
