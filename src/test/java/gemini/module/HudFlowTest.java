package gemini.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class HudFlowTest {
   private static final int GAP = HudFlow.DEFAULT_GAP;

   /** Screen sizes in HUD pixels: 1280x720, 1920x1080 and 2560x1440 at several GUI scales, plus tiny windows. */
   private static final int[][] SCREENS = {{640, 360}, {427, 240}, {480, 270}, {960, 540}, {640, 360}, {853, 480}, {320, 240}, {1280, 720}};

   private record Rect(int x, int y, int w, int h) {
      boolean overlaps(Rect o, int gap) {
         return this.x < o.x + o.w + gap && o.x < this.x + this.w + gap && this.y < o.y + o.h + gap && o.y < this.y + this.h + gap;
      }

      boolean inside(int width, int height) {
         return this.x >= 0 && this.y >= 0 && this.x + this.w <= width && this.y + this.h <= height;
      }
   }

   /** Roughly what the vanilla HUD and the watermark take on a screen of this size. */
   private static List<Rect> vanilla(int width, int height) {
      List<Rect> list = new ArrayList<>();
      list.add(new Rect(width / 2 - 122, height - 70, 244, 70));
      list.add(new Rect(0, height - 132, Math.min(332, width / 2), 132));
      list.add(new Rect(0, 0, 120, 20));
      return list;
   }

   @Test
   void manyWidgetsNeverOverlapOnAnyScreen() {
      Random random = new Random(1234L);
      for (int[] screen : SCREENS) {
         for (int round = 0; round < 30; round++) {
            int width = screen[0];
            int height = screen[1];
            HudFlow flow = new HudFlow();
            flow.begin(width, height);
            List<Rect> reserved = vanilla(width, height);
            for (Rect r : reserved) {
               flow.reserve(r.x, r.y, r.w, r.h);
            }

            int count = 4 + random.nextInt(18);
            List<Integer> ids = new ArrayList<>();
            List<int[]> sizes = new ArrayList<>();
            for (int i = 0; i < count; i++) {
               int w = 20 + random.nextInt(120);
               int h = 10 + random.nextInt(60);
               sizes.add(new int[]{w, h});
               ids.add(flow.add(random.nextInt(8), random.nextInt(5), w, h, 4));
            }

            flow.solve();
            List<Rect> placed = new ArrayList<>();
            for (int i = 0; i < count; i++) {
               int id = ids.get(i);
               if (!flow.fitted(id)) {
                  continue;
               }

               Rect r = new Rect(flow.x(id), flow.y(id), sizes.get(i)[0], sizes.get(i)[1]);
               assertTrue(r.inside(width, height), "off screen " + r + " on " + width + "x" + height);
               for (Rect v : reserved) {
                  assertFalse(r.overlaps(v, GAP), r + " covers vanilla " + v);
               }

               for (Rect other : placed) {
                  assertFalse(r.overlaps(other, GAP), r + " overlaps " + other + " on " + width + "x" + height);
               }

               placed.add(r);
            }
         }
      }
   }

   @Test
   void typicalSetAllFitsAtCommonSizes() {
      // the default spread with about twenty widgets enabled, like the screenshot that showed the overlap
      int[][] widgets = {
         {HudFlow.TOP_LEFT, 70, 26}, {HudFlow.TOP_LEFT, 90, 26}, {HudFlow.TOP_LEFT, 60, 16}, {HudFlow.TOP_LEFT, 110, 36},
         {HudFlow.TOP_LEFT, 120, 36}, {HudFlow.TOP_LEFT, 150, 26}, {HudFlow.TOP_LEFT, 76, 76}, {HudFlow.TOP_RIGHT, 80, 60},
         {HudFlow.TOP_RIGHT, 100, 26}, {HudFlow.RIGHT_CENTER, 60, 16}, {HudFlow.TOP_CENTER, 200, 22},
         {HudFlow.BOTTOM_CENTER, 84, 34}, {HudFlow.BOTTOM_CENTER, 164, 64}, {HudFlow.BOTTOM_RIGHT, 50, 16},
         {HudFlow.BOTTOM_RIGHT, 50, 16}, {HudFlow.BOTTOM_RIGHT, 60, 16}, {HudFlow.BOTTOM_RIGHT, 70, 46},
         {HudFlow.BOTTOM_LEFT, 72, 86}, {HudFlow.LEFT_CENTER, 106, 40}
      };
      for (int[] screen : new int[][]{{640, 360}, {960, 540}, {853, 480}}) {
         HudFlow flow = new HudFlow();
         flow.begin(screen[0], screen[1]);
         for (Rect r : vanilla(screen[0], screen[1])) {
            flow.reserve(r.x, r.y, r.w, r.h);
         }

         for (int i = 0; i < widgets.length; i++) {
            flow.add(widgets[i][0], i, widgets[i][1], widgets[i][2], 4);
         }

         flow.solve();
         for (int i = 0; i < widgets.length; i++) {
            assertTrue(flow.fitted(i), "widget " + i + " found no room on " + screen[0] + "x" + screen[1]);
         }
      }
   }

   @Test
   void topLeftStackGrowsDownInOrderBelowReservedArea() {
      HudFlow flow = new HudFlow();
      flow.begin(640, 360);
      flow.reserve(0, 0, 120, 20);
      int c = flow.add(HudFlow.TOP_LEFT, 3, 50, 10, 4);
      int a = flow.add(HudFlow.TOP_LEFT, 1, 80, 20, 4);
      int b = flow.add(HudFlow.TOP_LEFT, 2, 60, 30, 4);
      flow.solve();
      assertEquals(4, flow.x(a));
      assertEquals(20 + GAP, flow.y(a));
      assertEquals(flow.y(a) + 20 + GAP, flow.y(b));
      assertEquals(flow.y(b) + 30 + GAP, flow.y(c));
      assertEquals(4, flow.x(c));
   }

   @Test
   void bottomRightStackGrowsUpAndRightAligns() {
      HudFlow flow = new HudFlow();
      flow.begin(640, 360);
      int a = flow.add(HudFlow.BOTTOM_RIGHT, 0, 40, 16, 4);
      int b = flow.add(HudFlow.BOTTOM_RIGHT, 1, 70, 16, 4);
      flow.solve();
      assertEquals(640 - 4 - 40, flow.x(a));
      assertEquals(360 - 4 - 16, flow.y(a));
      assertEquals(640 - 4 - 70, flow.x(b));
      assertEquals(flow.y(a) - GAP - 16, flow.y(b));
   }

   @Test
   void fullColumnOpensANewColumnFurtherIn() {
      HudFlow flow = new HudFlow();
      flow.begin(400, 100);
      int[] ids = new int[4];
      for (int i = 0; i < ids.length; i++) {
         ids[i] = flow.add(HudFlow.TOP_LEFT, i, 50, 40, 4);
      }

      flow.solve();
      assertEquals(4, flow.x(ids[0]));
      assertEquals(4, flow.x(ids[1]));
      // two 40-high cards fill a 100-high column; the third starts a column to the right
      assertEquals(4 + 50 + GAP, flow.x(ids[2]));
      assertEquals(4, flow.y(ids[2]));
      assertTrue(flow.fitted(ids[3]));
   }

   @Test
   void bottomCenterFillsARowAboveTheHotbar() {
      HudFlow flow = new HudFlow();
      flow.begin(640, 360);
      flow.reserve(320 - 122, 360 - 70, 244, 70);
      int a = flow.add(HudFlow.BOTTOM_CENTER, 0, 80, 30, 4);
      int b = flow.add(HudFlow.BOTTOM_CENTER, 1, 80, 30, 4);
      flow.solve();
      assertEquals(360 - 70 - GAP - 30, flow.y(a));
      assertEquals(flow.y(a), flow.y(b), "second card sits beside the first, not on top of it");
      assertEquals(320 - 40, flow.x(a));
   }

   @Test
   void placedWidgetsAreRespected() {
      HudFlow flow = new HudFlow();
      flow.begin(640, 360);
      // a widget the user dragged to the top-left corner
      flow.reserve(0, 0, 100, 50);
      int a = flow.add(HudFlow.TOP_LEFT, 0, 60, 20, 4);
      flow.solve();
      Rect placed = new Rect(0, 0, 100, 50);
      Rect flowed = new Rect(flow.x(a), flow.y(a), 60, 20);
      assertFalse(flowed.overlaps(placed, GAP));
      assertEquals(50 + GAP, flow.y(a));
   }

   @Test
   void sameInputGivesSameLayout() {
      int[][] first = this.layout();
      int[][] second = this.layout();
      for (int i = 0; i < first.length; i++) {
         assertEquals(first[i][0], second[i][0]);
         assertEquals(first[i][1], second[i][1]);
      }
   }

   @Test
   void equalOrderKeepsAddOrder() {
      HudFlow flow = new HudFlow();
      flow.begin(640, 360);
      int a = flow.add(HudFlow.TOP_RIGHT, 5, 40, 10, 4);
      int b = flow.add(HudFlow.TOP_RIGHT, 5, 40, 10, 4);
      flow.solve();
      assertTrue(flow.y(a) < flow.y(b));
   }

   @Test
   void anchorNamesRoundTrip() {
      for (int i = 0; i < 8; i++) {
         assertEquals(i, HudFlow.anchor(HudFlow.anchorName(i)));
      }

      assertEquals(HudFlow.TOP_LEFT, HudFlow.anchor("nonsense"));
   }

   private int[][] layout() {
      HudFlow flow = new HudFlow();
      flow.begin(480, 270);
      flow.reserve(0, 200, 300, 70);
      Random random = new Random(7L);
      int[][] out = new int[12][];
      int[] ids = new int[12];
      for (int i = 0; i < 12; i++) {
         ids[i] = flow.add(random.nextInt(8), random.nextInt(3), 20 + random.nextInt(60), 10 + random.nextInt(30), 4);
      }

      flow.solve();
      for (int i = 0; i < 12; i++) {
         out[i] = new int[]{flow.x(ids[i]), flow.y(ids[i])};
      }

      return out;
   }
}
