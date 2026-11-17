package gemini.ui.clickgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SmoothScrollTest {
   private static float settle(SmoothScroll scroll, float dt, float max) {
      for (int i = 0; i < 2000 && (i == 0 || scroll.moving()); i++) {
         scroll.update(dt, max, true);
      }

      return scroll.value();
   }

   @Test
   void aPushTravelsItsDistanceAndStops() {
      SmoothScroll scroll = new SmoothScroll();
      scroll.scrollBy(120.0F);
      assertEquals(120.0F, scroll.target(), 0.001F);
      assertEquals(120.0F, settle(scroll, 1.0F / 60.0F, 1000.0F), 1.0F);
      assertFalse(scroll.moving());
   }

   @Test
   void theGlideIsTheSameAtAnyFrameRate() {
      SmoothScroll fast = new SmoothScroll();
      SmoothScroll slow = new SmoothScroll();
      fast.scrollBy(300.0F);
      slow.scrollBy(300.0F);
      for (int i = 0; i < 24; i++) {
         fast.update(1.0F / 240.0F, 1000.0F, true);
      }

      for (int i = 0; i < 3; i++) {
         slow.update(1.0F / 30.0F, 1000.0F, true);
      }

      assertEquals(fast.value(), slow.value(), 0.5F, "0.1 s of gliding lands in the same place at 240 and 30 fps");
      assertTrue(fast.value() > 100.0F && fast.value() < 300.0F, "part of the way after 0.1 s");
   }

   @Test
   void itEasesOutRatherThanMovingEvenly() {
      SmoothScroll scroll = new SmoothScroll();
      scroll.scrollBy(100.0F);
      float first = scroll.update(0.05F, 1000.0F, true);
      float second = scroll.update(0.05F, 1000.0F, true) - first;
      assertTrue(first > second, "fast at first, slower after");
   }

   @Test
   void withoutAnimationsThePushIsAppliedAtOnce() {
      SmoothScroll scroll = new SmoothScroll();
      scroll.scrollBy(80.0F);
      assertEquals(80.0F, scroll.update(0.016F, 1000.0F, false), 0.001F);
      assertFalse(scroll.moving());
   }

   @Test
   void theEndsStopTheGlide() {
      SmoothScroll scroll = new SmoothScroll();
      scroll.scrollBy(-50.0F);
      assertEquals(0.0F, scroll.update(0.016F, 100.0F, true), 0.0F, "cannot go above the top");
      assertFalse(scroll.moving());
      scroll.scrollBy(500.0F);
      assertEquals(100.0F, settle(scroll, 0.016F, 100.0F), 0.0F, "nor past the end");
      assertFalse(scroll.moving());
      scroll.update(0.016F, 40.0F, true);
      assertEquals(40.0F, scroll.value(), 0.0F, "content that got shorter pulls the position back");
      scroll.update(0.016F, -30.0F, true);
      assertEquals(0.0F, scroll.value(), 0.0F, "content shorter than the view cannot scroll");
   }

   @Test
   void aPushAwayFromAnEndIsNotSwallowed() {
      SmoothScroll scroll = new SmoothScroll();
      scroll.scrollBy(60.0F);
      scroll.update(0.016F, 200.0F, true);
      assertTrue(scroll.value() > 0.0F && scroll.moving(), "leaving the top keeps gliding");
   }

   @Test
   void revealMovesTheLeastDistance() {
      SmoothScroll scroll = new SmoothScroll();
      scroll.jumpTo(100.0F);
      scroll.reveal(120.0F, 160.0F, 200.0F);
      assertEquals(100.0F, scroll.target(), 0.001F, "already visible: stays");
      scroll.reveal(40.0F, 80.0F, 200.0F);
      assertEquals(40.0F, scroll.target(), 0.001F, "above the view: its top comes to the top");
      scroll.jumpTo(100.0F);
      scroll.reveal(280.0F, 340.0F, 200.0F);
      assertEquals(140.0F, scroll.target(), 0.001F, "below the view: its bottom comes to the bottom");
      scroll.jumpTo(0.0F);
      scroll.reveal(50.0F, 400.0F, 200.0F);
      assertEquals(50.0F, scroll.target(), 0.001F, "taller than the view: aligned to its top");
   }

   @Test
   void pixelsRoundsThePosition() {
      SmoothScroll scroll = new SmoothScroll();
      scroll.jumpTo(12.6F);
      assertEquals(13, scroll.pixels());
      assertEquals(12.6F, scroll.value(), 0.0001F);
   }
}
